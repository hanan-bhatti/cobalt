#!/usr/bin/env python3
"""Research only: immutable UTF-8 pieces + persistent AVL roots in SQLite.

No app data is opened. Requires a NEW output directory. This deliberately simple
host prototype measures physical storage, not Android latency or memory. It keeps
the reference document and caches in RAM solely to verify every saved version.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import sqlite3
import statistics
import struct
import subprocess
import sys
import time
from dataclasses import dataclass

CHUNK = 65536


def digest(data):
    return hashlib.sha256(data).digest()


@dataclass(frozen=True)
class Node:
    left: int
    right: int
    blob: int
    offset: int
    count: int
    size: int
    height: int
    hash: bytes


class Store:
    def __init__(self, path, create=False):
        self.path = path
        self.db = sqlite3.connect(path)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA synchronous=FULL")
        self.db.execute("PRAGMA wal_autocheckpoint=1000")
        if create:
            self.db.executescript("""
                CREATE TABLE blobs(id INTEGER PRIMARY KEY, hash BLOB UNIQUE NOT NULL, data BLOB NOT NULL);
                CREATE TABLE nodes(id INTEGER PRIMARY KEY, l INTEGER, r INTEGER, b INTEGER,
                    off INTEGER, n INTEGER, size INTEGER, height INTEGER, hash BLOB NOT NULL);
                CREATE TABLE versions(rev INTEGER PRIMARY KEY, root INTEGER, expected BLOB NOT NULL);
                CREATE TABLE head(id INTEGER PRIMARY KEY CHECK(id=1), rev INTEGER, root INTEGER);
            """)
        self.nodes = {0: Node(0, 0, 0, 0, 0, 0, 0, bytes(32))}
        self.blob_hashes = {}

    def node(self, key):
        if key not in self.nodes:
            self.nodes[key] = Node(*self.db.execute(
                "SELECT l,r,b,off,n,size,height,hash FROM nodes WHERE id=?", (key,)).fetchone())
        return self.nodes[key]

    def put(self, node):
        key = self.db.execute("INSERT INTO nodes(l,r,b,off,n,size,height,hash) VALUES(?,?,?,?,?,?,?,?)",
                              tuple(node.__dict__.values())).lastrowid
        self.nodes[key] = node
        return key

    def leaf(self, blob, offset, count):
        if not count:
            return 0
        if blob not in self.blob_hashes:
            self.blob_hashes[blob] = self.db.execute("SELECT hash FROM blobs WHERE id=?", (blob,)).fetchone()[0]
        h = digest(b"leaf-v1" + self.blob_hashes[blob] + struct.pack(">QQ", offset, count))
        return self.put(Node(0, 0, blob, offset, count, count, 1, h))

    def branch(self, left, right):
        if not left or not right:
            return left or right
        a, b = self.node(left), self.node(right)
        h = digest(b"branch-v1" + a.hash + b.hash + struct.pack(">QQ", a.size, b.size))
        return self.put(Node(left, right, 0, 0, 0, a.size + b.size, max(a.height, b.height) + 1, h))

    def balance(self, left, right):
        a, b = self.node(left), self.node(right)
        if a.height > b.height + 1:
            al, ar = self.node(a.left), self.node(a.right)
            if al.height >= ar.height:
                return self.branch(a.left, self.branch(a.right, right))
            return self.branch(self.branch(a.left, ar.left), self.branch(ar.right, right))
        if b.height > a.height + 1:
            bl, br = self.node(b.left), self.node(b.right)
            if br.height >= bl.height:
                return self.branch(self.branch(left, b.left), b.right)
            return self.branch(self.branch(left, bl.left), self.branch(bl.right, b.right))
        return self.branch(left, right)

    def join(self, left, right):
        if not left or not right:
            return left or right
        a, b = self.node(left), self.node(right)
        if a.height > b.height + 1:
            return self.balance(a.left, self.join(a.right, right))
        if b.height > a.height + 1:
            return self.balance(self.join(left, b.left), b.right)
        return self.branch(left, right)

    def split(self, root, offset):
        n = self.node(root)
        assert 0 <= offset <= n.size
        if offset == 0:
            return 0, root
        if offset == n.size:
            return root, 0
        if n.blob:
            return self.leaf(n.blob, n.offset, offset), self.leaf(n.blob, n.offset + offset, n.count - offset)
        size = self.node(n.left).size
        if offset < size:
            a, b = self.split(n.left, offset)
            return a, self.join(b, n.right)
        a, b = self.split(n.right, offset - size)
        return self.join(n.left, a), b

    def pieces(self, data):
        leaves = []
        for start in range(0, len(data), CHUNK):
            part = data[start:start + CHUNK]
            h = digest(part)
            self.db.execute("INSERT OR IGNORE INTO blobs(hash,data) VALUES(?,?)", (h, part))
            key = self.db.execute("SELECT id FROM blobs WHERE hash=?", (h,)).fetchone()[0]
            self.blob_hashes[key] = h
            leaves.append(self.leaf(key, 0, len(part)))
        while len(leaves) > 1:
            leaves = [self.join(leaves[i], leaves[i + 1]) if i + 1 < len(leaves) else leaves[i]
                      for i in range(0, len(leaves), 2)]
        return leaves[0] if leaves else 0

    def replace(self, root, start, end, data):
        left, tail = self.split(root, start)
        _, right = self.split(tail, end - start)
        return self.join(self.join(left, self.pieces(data)), right)

    def publish(self, rev, root, expected, expected_rev=None):
        self.db.execute("INSERT INTO versions VALUES(?,?,?)", (rev, root, expected))
        if expected_rev is None:
            self.db.execute("INSERT INTO head VALUES(1,?,?)", (rev, root))
        else:
            assert self.db.execute("UPDATE head SET rev=?,root=? WHERE id=1 AND rev=?",
                                   (rev, root, expected_rev)).rowcount == 1, "stale save"

    def spans(self, root):
        if not root:
            return
        n = self.node(root)
        if n.blob:
            yield n.blob, n.offset, n.count
        else:
            yield from self.spans(n.left)
            yield from self.spans(n.right)

    def verify_all(self):
        # Reloaded DB and immutable content, independent from construction cache.
        payloads = {key: data for key, data in self.db.execute("SELECT id,data FROM blobs")}
        for key, expected in self.db.execute("SELECT id,hash FROM blobs"):
            assert digest(payloads[key]) == expected
        checked = set()

        def check(key):
            if not key or key in checked:
                return
            n = self.node(key)
            if n.blob:
                assert 0 <= n.offset < len(payloads[n.blob]) and 0 < n.count <= len(payloads[n.blob]) - n.offset
                assert n.size == n.count and n.height == 1
                h = digest(b"leaf-v1" + digest(payloads[n.blob]) + struct.pack(">QQ", n.offset, n.count))
            else:
                check(n.left); check(n.right)
                a, b = self.node(n.left), self.node(n.right)
                assert a.size + b.size == n.size and abs(a.height - b.height) <= 1
                assert n.height == max(a.height, b.height) + 1
                h = digest(b"branch-v1" + a.hash + b.hash + struct.pack(">QQ", a.size, b.size))
            assert h == n.hash
            checked.add(key)

        count = 0
        for _, root, expected in self.db.execute("SELECT * FROM versions ORDER BY rev"):
            check(root)
            actual = hashlib.sha256()
            for blob, offset, size in self.spans(root):
                actual.update(memoryview(payloads[blob])[offset:offset + size])
            assert actual.digest() == expected
            count += 1
        return count

    def physical(self):
        sizes = {}
        for suffix in ("", "-wal", "-shm"):
            path = Path(str(self.path) + suffix)
            sizes[suffix or "db"] = path.stat().st_size if path.exists() else 0
        sizes["total"] = sum(sizes.values())
        sizes["allocated"] = sum(Path(str(self.path) + suffix).stat().st_blocks * 512
                                 for suffix in ("", "-wal", "-shm") if Path(str(self.path) + suffix).exists())
        return sizes

    def checkpoint(self):
        result = self.db.execute("PRAGMA wal_checkpoint(TRUNCATE)").fetchone()
        assert result[0] == 0
        return self.physical()


def fixture(size, seed):
    rng = random.Random(seed)
    translate = bytes(32 + i % 95 for i in range(256))
    return b"".join(rng.randbytes(min(CHUNK, size - off)).translate(translate)
                    for off in range(0, size, CHUNK))


def scenario(out, name, base, edits):
    path = out / (name + ".sqlite")
    store = Store(path, create=True)
    ref = bytearray(base)
    with store.db:
        root = store.pieces(base)
        store.publish(0, root, digest(ref))
    import_physical = store.physical()
    baseline = store.checkpoint()
    times, samples = [], []
    flat_manifest_bytes = 0
    for rev, (start, end, data) in enumerate(edits, 1):
        # Reference bytes/hash work intentionally outside commit timing.
        assert 0 <= start <= end <= len(ref)
        ref[start:end] = data
        expected = digest(ref)
        began = time.perf_counter()
        with store.db:
            root = store.replace(root, start, end, data)
            store.publish(rev, root, expected, rev - 1)
        times.append((time.perf_counter() - began) * 1000)
        # Hypothetical fixed 24B descriptors, not another implemented store.
        flat_manifest_bytes += sum(1 for _ in store.spans(root)) * 24
        samples.append(store.physical())
        if rev % 100 == 0:
            print(f"{name}: committed {rev}", flush=True)
    before_checkpoint = store.physical()
    settled = store.checkpoint()
    expected_hashes = [row[0] for row in store.db.execute("SELECT expected FROM versions ORDER BY rev")]
    unchanged_commits = sum(a == b for a, b in zip(expected_hashes, expected_hashes[1:]))
    count_nodes = store.db.execute("SELECT count(*) FROM nodes").fetchone()[0]
    payload_bytes = store.db.execute("SELECT sum(length(data)) FROM blobs").fetchone()[0] or 0
    # Restore is a new head/event referring to an already stored root.
    old_root, old_hash = store.db.execute("SELECT root,expected FROM versions WHERE rev=0").fetchone()
    with store.db:
        store.publish(len(edits) + 1, old_root, old_hash, len(edits))
    restore_settled = store.checkpoint()
    store.db.close()
    reopened = Store(path)
    verified = reopened.verify_all()
    assert reopened.db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    reopened.db.close()
    result = dict(name=name, base_bytes=len(base), edits=len(edits), unchanged_body_commits=unchanged_commits,
                  inserted_bytes=sum(len(e[2]) for e in edits),
                  current_before_restore_bytes=len(ref), unique_payload_bytes=payload_bytes,
                  baseline=baseline, import_before_checkpoint=import_physical,
                  before_final_checkpoint=before_checkpoint, settled=settled,
                  settled_growth_bytes=settled["total"] - baseline["total"],
                  peak_edit_growth_bytes=max([baseline["total"]] + [s["total"] for s in samples]) - baseline["total"],
                  restore_settled_growth_bytes=restore_settled["total"] - settled["total"],
                  nodes_including_unreachable_intermediates=count_nodes,
                  hypothetical_flat_manifest_bytes=flat_manifest_bytes,
                  verified_versions_including_restore=verified,
                  host_commit_ms=dict(median=statistics.median(times), p95=sorted(times)[int((len(times)-1)*.95)], max=max(times)))
    print(json.dumps(result), flush=True)
    return result


def crash_child(path, after):
    store = Store(path)
    rev, root = store.db.execute("SELECT rev,root FROM head").fetchone()
    store.db.execute("BEGIN IMMEDIATE")
    changed = store.replace(root, 0, 0, b"CRASH-PROBE")
    # Hash oracle for this 5-byte dedicated crash fixture only.
    store.publish(rev + 1, changed, digest(b"CRASH-PROBEhello"), rev)
    if after:
        store.db.commit()
    os._exit(23)  # No orderly sqlite close; actual abrupt process exit.


def reliability(out):
    results = {}
    for after in (False, True):
        path = out / f"crash-{'after' if after else 'before'}.sqlite"
        store = Store(path, create=True)
        with store.db:
            root = store.pieces(b"hello")
            store.publish(0, root, digest(b"hello"))
        store.db.close()
        run = subprocess.run([sys.executable, __file__, "--crash-child", str(path), "--after" if after else "--before"], check=False)
        assert run.returncode == 23
        store = Store(path)
        assert store.db.execute("SELECT rev FROM head").fetchone()[0] == int(after)
        assert store.verify_all() == 1 + int(after)
        assert store.db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        # Failed CAS must roll back both revision and pieces.
        old_count = store.db.execute("SELECT count(*) FROM nodes").fetchone()[0]
        try:
            with store.db:
                changed = store.replace(root, 0, 0, b"STALE")
                store.publish(99, changed, digest(b"STALEhello"), -1)
        except AssertionError:
            pass
        else:
            raise AssertionError("stale writer accepted")
        assert store.db.execute("SELECT count(*) FROM nodes").fetchone()[0] == old_count
        assert store.db.execute("SELECT count(*) FROM versions WHERE rev=99").fetchone()[0] == 0
        store.db.close()
        results[f"exit_{'after' if after else 'before'}_commit"] = "passed"
    results["stale_CAS_rolls_back_objects_and_version"] = "passed"
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--crash-child", type=Path)
    parser.add_argument("--after", action="store_true")
    parser.add_argument("--before", action="store_true")
    args = parser.parse_args()
    if args.crash_child:
        crash_child(args.crash_child, args.after)
    if not args.out:
        parser.error("--out NEW_DIRECTORY required")
    args.out.mkdir(parents=True, exist_ok=False)
    base = fixture(50_000_000, 20261109)
    rng = random.Random(9901)
    cases = []
    for count in (100, 1000):
        cases.append((f"{count}-one-byte-edits", [(rng.randrange(len(base)), None, bytes([32 + i % 95])) for i in range(count)]))
        cases[-1] = (cases[-1][0], [(p, p + 1, b) for p, _, b in cases[-1][1]])
    groups = []
    size = len(base)
    for i in range(1000):
        pos = (0, size // 2, size)[i % 3]
        text = fixture(125, 10000 + i)
        groups.append((pos, pos, text)); size += len(text)
    cases.append(("1000-typing-groups-125B", groups))
    cases.append(("100-deletions-50KB", [(1000, 51000, b"") for _ in range(100)]))
    unicode = "\U0001f642 اردو 中文 e\u0301\r\n".encode()
    cases.append(("100-unicode-inserts", [(i * 100, i * 100, unicode) for i in range(100)]))
    cases.append(("10-pastes-1MB", [(1000, 1000, fixture(1_000_000, 700 + i)) for i in range(10)]))
    cases.append(("full-50MB-replacement", [(0, len(base), fixture(len(base), 20261110))]))
    results = [scenario(args.out, name, base, edits) for name, edits in cases]
    # Empty document and randomized varying-size replacement/deletion operations.
    random_edits, size = [], 0
    for _ in range(1000):
        start = rng.randrange(size + 1)
        end = rng.randrange(start, size + 1)
        data = fixture(rng.randrange(200), rng.randrange(1_000_000))
        random_edits.append((start, end, data)); size += len(data) - (end - start)
    results.append(scenario(args.out, "1000-random-empty-origin", b"", random_edits))
    report = dict(research_only=True, sqlite=sqlite3.sqlite_version, python=sys.version,
                  platform=sys.platform, chunk_bytes=CHUNK, compression=False,
                  timing_excludes_reference_hash_and_export=True, cases=results, reliability=reliability(args.out))
    (args.out / "results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
