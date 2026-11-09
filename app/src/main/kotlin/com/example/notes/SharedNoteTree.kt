package com.example.notes

import com.example.data.local.AppDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.*
import java.security.MessageDigest
import java.util.LinkedHashMap

/** A range of an existing verified generation, or a bounded streaming addition. */
data class SharedNotePiece(val hash: String?, val offset: Long, val bytes: Long, val open: (() -> InputStream)? = null)

/**
 * Persistent AVL rope over immutable SQLite payloads. A root is published only after
 * every referenced object commits. Failed staging never changes saved_items. Explicit
 * GC shares the staging gate and derives liveness from ALL retained roots.
 */
internal class SharedNoteTree(private val database: AppDatabase) {
    private val db get() = database.openHelper.writableDatabase
    data class Root(val owner: String, val hash: String, val root: Long, val bytes: Long,
        val preview: String, val created: Long, val staged: Boolean)
    private data class Node(val id: Long, val left: Long, val right: Long, val chunk: Long,
        val offset: Long, val length: Long, val bytes: Long, val height: Int, val digest: ByteArray)
    companion object {
        const val CHUNK_BYTES = 64 * 1024
        // Every store instance and GC uses this gate; readers use generation leases.
        private val stagingGate = Any()
        private val empty = Node(0, 0, 0, 0, 0, 0, 0, 0, ByteArray(32))
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
    private fun <T> transaction(action: () -> T): T {
        val connection = db
        if (!connection.inTransaction()) connection.execSQL("PRAGMA synchronous=EXTRA")
        connection.beginTransaction()
        var writeFailure: Throwable? = null
        try {
            connection.query("PRAGMA synchronous").use { check(it.moveToFirst() && it.getInt(0) >= 2) { "Durable note writes require synchronized SQLite commits" } }
            return action().also { connection.setTransactionSuccessful() }
        } catch (failure: Throwable) {
            writeFailure = failure
            throw failure
        } finally {
            try { connection.endTransaction() }
            catch (rollbackFailure: Throwable) {
                // SQLITE_FULL can auto-roll back before Android ends its transaction.
                // Keep the original write error, including its storage-full type.
                val primary = writeFailure
                if (primary == null) throw rollbackFailure
                if (primary !== rollbackFailure) primary.addSuppressed(rollbackFailure)
            }
        }
    }
    fun root(owner: String, hash: String): Root? = db.query(
        "SELECT owner,hash,root,bytes,preview,created,staged FROM note_roots WHERE owner=? AND hash=?",
        arrayOf(owner, hash)).use { c -> if (!c.moveToFirst()) null else
            Root(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getString(4), c.getLong(5), c.getInt(6) != 0) }
    fun roots(): List<Root> = db.query("SELECT owner,hash,root,bytes,preview,created,staged FROM note_roots").use { c ->
        buildList { while (c.moveToNext()) add(Root(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getString(4), c.getLong(5), c.getInt(6) != 0)) }
    }
    private fun rawNode(id: Long): Node {
        if (id == 0L) return empty
        return db.query("SELECT id,`left`,`right`,chunk,`offset`,length,bytes,height,digest FROM note_nodes WHERE id=?", arrayOf(id)).use { c ->
            check(c.moveToFirst()) { "Note tree node missing; saved data retained" }
            Node(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5), c.getLong(6), c.getInt(7), c.getBlob(8))
        }
    }
    private fun chunkDigest(id: Long) = db.query("SELECT digest FROM note_chunks WHERE id=?", arrayOf(id)).use {
        check(it.moveToFirst()) { "Note payload missing; saved data retained" }; it.getBlob(0)
    }
    private fun nodeDigest(left: Node, right: Node, chunk: Long, offset: Long, length: Long): ByteArray {
        val out = ByteArrayOutputStream(96)
        DataOutputStream(out).use {
            if (chunk != 0L) { it.writeInt(1); it.write(chunkDigest(chunk)); it.writeLong(offset); it.writeLong(length) }
            else { it.writeInt(2); it.write(left.digest); it.write(right.digest); it.writeLong(left.bytes); it.writeLong(right.bytes) }
        }
        return sha(out.toByteArray())
    }
    private inner class Tree {
        val cache = object : LinkedHashMap<Long, Node>(256, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Node>?) = size > 4096
        }
        fun node(id: Long): Node = if (id == 0L) empty else cache[id] ?: rawNode(id).also { n ->
            check(n.bytes > 0 && n.height in 1..64) { "Invalid note tree bounds" }
            val a = rawNode(n.left); val b = rawNode(n.right)
            if (n.chunk != 0L) check(n.left == 0L && n.right == 0L && n.offset >= 0 && n.length == n.bytes && n.height == 1)
            else check(n.left != 0L && n.right != 0L && n.length == 0L && n.offset == 0L &&
                n.bytes == Math.addExact(a.bytes, b.bytes) && n.height == maxOf(a.height, b.height) + 1 && kotlin.math.abs(a.height - b.height) <= 1)
            check(n.digest.contentEquals(nodeDigest(a, b, n.chunk, n.offset, n.length))) { "Note tree checksum mismatch" }
            cache[id] = n
        }
        fun put(a: Node, b: Node, chunk: Long, offset: Long, length: Long): Long {
            val digest = nodeDigest(a, b, chunk, offset, length)
            db.query("SELECT id FROM note_nodes WHERE digest=?", arrayOf(digest)).use { if (it.moveToFirst()) return node(it.getLong(0)).id }
            val size = if (chunk != 0L) length else Math.addExact(a.bytes, b.bytes)
            val height = if (chunk != 0L) 1 else maxOf(a.height, b.height) + 1
            val stmt = db.compileStatement("INSERT INTO note_nodes(`left`,`right`,chunk,`offset`,length,bytes,height,digest) VALUES(?,?,?,?,?,?,?,?)")
            val id = stmt.use { s ->
                listOf(a.id,b.id,chunk,offset,length,size,height.toLong()).forEachIndexed { i,v -> s.bindLong(i+1,v) }
                s.bindBlob(8,digest); s.executeInsert()
            }
            cache[id] = Node(id,a.id,b.id,chunk,offset,length,size,height,digest)
            return id
        }
        fun leaf(chunk: Long, offset: Long, length: Long) = if (length == 0L) 0L else put(empty,empty,chunk,offset,length)
        fun branch(a: Long,b: Long): Long = if (a == 0L || b == 0L) a + b else put(node(a),node(b),0,0,0)
        fun balance(a: Long,b: Long): Long {
            val x=node(a); val y=node(b)
            if (x.height > y.height + 1) {
                val l=node(x.left); val r=node(x.right)
                return if (l.height >= r.height) branch(x.left,branch(x.right,b))
                    else branch(branch(x.left,r.left),branch(r.right,b))
            }
            if (y.height > x.height + 1) {
                val l=node(y.left); val r=node(y.right)
                return if (r.height >= l.height) branch(branch(a,y.left),y.right)
                    else branch(branch(a,l.left),branch(l.right,y.right))
            }
            return branch(a,b)
        }
        fun join(a: Long,b: Long): Long {
            if (a == 0L || b == 0L) return a + b
            val x=node(a); val y=node(b)
            if (x.height > y.height+1) return balance(x.left,join(x.right,b))
            if (y.height > x.height+1) return balance(join(a,y.left),y.right)
            return branch(a,b)
        }
        fun split(root: Long,offset: Long): Pair<Long,Long> {
            val n=node(root); require(offset in 0..n.bytes)
            if (offset == 0L) return 0L to root
            if (offset == n.bytes) return root to 0L
            if (n.chunk != 0L) return leaf(n.chunk,n.offset,offset) to leaf(n.chunk,n.offset+offset,n.length-offset)
            val size=node(n.left).bytes
            if (offset < size) { val (a,b)=split(n.left,offset); return a to join(b,n.right) }
            val (a,b)=split(n.right,offset-size); return join(n.left,a) to b
        }
        fun slice(root: Long,start: Long,length: Long): Long {
            require(start >= 0 && length >= 0 && start <= node(root).bytes && length <= node(root).bytes-start)
            val (_,tail)=split(root,start); return split(tail,length).first
        }
        fun payload(data: ByteArray): Long {
            val hash=sha(data)
            db.query("SELECT id FROM note_chunks WHERE digest=?", arrayOf(hash)).use { c ->
                if(c.moveToFirst()) { val id=c.getLong(0); check(readPayload(id).contentEquals(data)) { "Payload collision or corruption" }; return id }
            }
            return db.compileStatement("INSERT INTO note_chunks(digest,data) VALUES(?,?)").use { s -> s.bindBlob(1,hash);s.bindBlob(2,data);s.executeInsert() }
        }
    }
    private fun readPayload(id: Long): ByteArray = db.query("SELECT digest,data FROM note_chunks WHERE id=?", arrayOf(id)).use { c ->
        check(c.moveToFirst()) { "Note payload missing" }
        val data=c.getBlob(1)
        check(data.isNotEmpty() && data.size <= CHUNK_BYTES && sha(data).contentEquals(c.getBlob(0))) { "Note payload checksum mismatch" }
        data
    }
    /** Cursor retains at most four payloads and bounded tree metadata; skip seeks, never reconstructs. */
    fun reader(root: Root): NoteRangeReader {
        val tree=Tree()
        check(tree.node(root.root).bytes == root.bytes) { "Note root length mismatch" }
        val payloads=object : LinkedHashMap<Long,ByteArray>(4,.75f,true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?)=size>4
        }
        var closed=false
        return object : NoteRangeReader {
            override fun read(offset: Long,count: Int): ByteArray {
                check(!closed);require(offset>=0 && count in 0..NoteSizePolicy.IO_CHUNK_BYTES)
                val size=minOf(count.toLong(),(root.bytes-offset).coerceAtLeast(0)).toInt()
                val result=ByteArray(size)
                var position=offset;var copied=0
                while(copied<size) {
                    var n=tree.node(root.root);var local=position
                    while(n.chunk==0L) { val a=tree.node(n.left);n=if(local<a.bytes)a else { local-=a.bytes;tree.node(n.right) } }
                    val data=payloads[n.chunk] ?: readPayload(n.chunk).also { payloads[n.chunk]=it }
                    check(n.offset<=data.size && n.length<=data.size-n.offset) { "Invalid note payload range" }
                    val take=minOf((size-copied).toLong(),n.length-local).toInt();check(take>0)
                    data.copyInto(result,copied,(n.offset+local).toInt(),(n.offset+local+take).toInt())
                    copied+=take;position+=take
                }
                return result
            }
            override fun close() { closed=true;payloads.clear();tree.cache.clear() }
        }
    }
    fun open(root: Root): InputStream {
        val reader=reader(root)
        return object : InputStream() {
            private var position=0L
            override fun read(): Int { val one=ByteArray(1);return if(read(one)<0)-1 else one[0].toInt() and 255 }
            override fun read(bytes: ByteArray,off: Int,len: Int): Int {
                if(off<0 || len<0 || off>bytes.size-len)throw IndexOutOfBoundsException()
                if(len==0)return 0
                if(position==root.bytes)return -1
                val data=reader.read(position,minOf(len,NoteSizePolicy.IO_CHUNK_BYTES));check(data.isNotEmpty())
                data.copyInto(bytes,off);position+=data.size;return data.size
            }
            override fun skip(n: Long): Long { val count=minOf(n.coerceAtLeast(0),root.bytes-position);position+=count;return count }
            override fun close()=reader.close()
        }
    }
    /** Batches bound import transactions; the root publication is a final transaction. */
    fun write(owner: String,input: InputStream,allowOversize: Boolean,progress: (Long)->Unit,
        pieces: List<SharedNotePiece>? = null, ensureBase: ((String)->Root)? = null,
        checkpoint: (PrivateMarkdownStore.WriteStage)->Unit = {}): NoteBodyRef = synchronized(stagingGate) {
        checkpoint(PrivateMarkdownStore.WriteStage.JOURNAL_SYNCED)
        val hash=MessageDigest.getInstance("SHA-256");val prefix=ByteArrayOutputStream(4096);var bytes=0L
        val tree=Tree();var root=0L
        if(pieces != null) {
            // Full-body SHA keeps all existing stale-edit/export/recovery contracts intact.
            val buffer=ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
            while(true) { val n=input.read(buffer);if(n<0)break;if(n==0)continue
                bytes=Math.addExact(bytes,n.toLong());if(!allowOversize && bytes>NoteSizePolicy.IMPORT_MAX_BYTES)throw NoteSizeExceededException()
                hash.update(buffer,0,n);if(prefix.size()<4096)prefix.write(buffer,0,minOf(n,4096-prefix.size()));progress(bytes)
            }
            check(pieces.fold(0L){sum,p->Math.addExact(sum,p.bytes)}==bytes) { "Editor piece plan length mismatch" }
            for(piece in pieces) {
                progress(bytes)
                val part=if(piece.hash != null) {
                    val base=requireNotNull(ensureBase)(piece.hash)
                    transaction { tree.slice(base.root,piece.offset,piece.bytes) }
                } else requireNotNull(piece.open).invoke().use { source -> append(tree,source) {
                    progress(bytes); checkpoint(PrivateMarkdownStore.WriteStage.CHUNK_WRITTEN)
                } }
                root=transaction { tree.join(root,part) }
            }
        } else {
            root=append(tree,input) { data ->
                bytes=Math.addExact(bytes,data.size.toLong());if(!allowOversize && bytes>NoteSizePolicy.IMPORT_MAX_BYTES)throw NoteSizeExceededException()
                hash.update(data);if(prefix.size()<4096)prefix.write(data,0,minOf(data.size,4096-prefix.size()));progress(bytes)
                checkpoint(PrivateMarkdownStore.WriteStage.CHUNK_WRITTEN)
            }
        }
        check(tree.node(root).bytes==bytes)
        checkpoint(PrivateMarkdownStore.WriteStage.DATA_SYNCED)
        val bodyHash=hash.digest().joinToString(""){ "%02x".format(it.toInt() and 255) }
        val preview=NoteTextMetadata.preview(prefix.toByteArray().toString(Charsets.UTF_8).replace(Regex("[\\r\\n\\t]+")," ").replace("\u0000","")
            .replace(Regex("^\\s*[#>*-]+\\s*"),""))
        transaction {
            val previous=root(owner,bodyHash)
            if(previous == null) db.execSQL("INSERT INTO note_roots(owner,hash,root,bytes,preview,created,staged) VALUES(?,?,?,?,?,?,1)",
                arrayOf<Any>(owner,bodyHash,root,bytes,preview,System.currentTimeMillis()))
            else check(previous.bytes==bytes && open(previous).use { PrivateMarkdownStore.digest(it) }==bodyHash)
        }
        checkpoint(PrivateMarkdownStore.WriteStage.RENAMED)
        val written=requireNotNull(root(owner,bodyHash))
        check(open(written).use { PrivateMarkdownStore.digest(it) }==bodyHash) { "Shared body verification failed; original retained" }
        checkpoint(PrivateMarkdownStore.WriteStage.VERIFIED)
        NoteBodyRef(bodyHash,bytes,preview)
    }
    private fun append(tree: Tree,input: InputStream,consume: (ByteArray)->Unit): Long {
        var root=0L;val buffer=ByteArray(CHUNK_BYTES)
        val batch=ArrayList<ByteArray>(16)
        fun flush() { if(batch.isNotEmpty()) {
            root=transaction { var next=root;for(data in batch)next=tree.join(next,tree.leaf(tree.payload(data),0,data.size.toLong()));next }
            batch.clear()
        } }
        while(true) {
            var size=0
            while(size<buffer.size) { val n=input.read(buffer,size,buffer.size-size);if(n<0)break;if(n==0)continue;size+=n }
            if(size==0)break
            val data=buffer.copyOf(size);consume(data)
            batch.add(data);if(batch.size==16)flush()
        }
        flush()
        return root
    }
    fun committed(owner: String,hash: String) = synchronized(stagingGate) {
        transaction { db.execSQL("UPDATE note_roots SET staged=0 WHERE owner=? AND hash=?",arrayOf(owner,hash)) }
    }
    fun remove(owner: String,hash: String,created: Long? = null): Boolean = synchronized(stagingGate) {
        transaction {
            val root=root(owner,hash) ?: return@transaction false
            if(created!=null && root.created!=created)return@transaction false
            db.execSQL("DELETE FROM note_roots WHERE owner=? AND hash=?",arrayOf(owner,hash));true
        }
    }
    fun removeAll(owner: String) = synchronized(stagingGate) { transaction { db.execSQL("DELETE FROM note_roots WHERE owner=?",arrayOf(owner)) } }
    fun collect(): Unit = synchronized(stagingGate) {
        accounting(emptySet()) // Refuse pruning when ANY retained tree has invalid bounds/checksums.
        transaction {
            // UNION removes duplicates and terminates even if damaged metadata contains a cycle.
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS note_live_nodes(id INTEGER PRIMARY KEY)")
            db.execSQL("DELETE FROM note_live_nodes")
            db.execSQL("WITH RECURSIVE live(id) AS (SELECT root FROM note_roots WHERE root<>0 UNION SELECT CASE sides.side WHEN 0 THEN n.`left` ELSE n.`right` END FROM note_nodes n JOIN live ON n.id=live.id JOIN (SELECT 0 AS side UNION ALL SELECT 1) sides WHERE n.chunk=0) INSERT OR IGNORE INTO note_live_nodes SELECT id FROM live WHERE id<>0")
            db.execSQL("DELETE FROM note_nodes WHERE id NOT IN (SELECT id FROM note_live_nodes)")
            db.execSQL("DELETE FROM note_chunks WHERE id NOT IN (SELECT chunk FROM note_nodes WHERE chunk<>0)")
            db.execSQL("DROP TABLE note_live_nodes")
        }
    }
    /** Disjoint logical object ownership; remaining SQLite page/index/WAL overhead is reported separately. */
    fun accounting(current: Set<Pair<String,String>>): List<NoteStorageFile> = synchronized(stagingGate) {
        val nodes=HashSet<Long>();val chunks=HashSet<Long>();val tree=Tree()
        fun count(root: Long): Long {
            if(root==0L || !nodes.add(root))return 0
            val n=tree.node(root)
            return 84L + if(n.chunk!=0L) {
                if(chunks.add(n.chunk)) db.query("SELECT length(data) FROM note_chunks WHERE id=?",arrayOf(n.chunk)).use { check(it.moveToFirst());it.getLong(0) } else 0
            } else count(n.left)+count(n.right)
        }
        roots().sortedWith(compareByDescending<Root>{ it.owner to it.hash in current }.thenByDescending{it.created}).flatMap { r ->
            check(r.bytes >= 0 && tree.node(r.root).bytes == r.bytes) { "Invalid retained note root; storage cleanup disabled" }
            val size=count(r.root)+160+r.preview.toByteArray(Charsets.UTF_8).size
            buildList {
                add(NoteStorageFile(r.owner,"${r.hash}.tree",size,r.created))
                if(r.staged)add(NoteStorageFile(r.owner,"${r.hash}.stage",0,r.created))
            }
        }
    }
}
