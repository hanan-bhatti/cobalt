#!/usr/bin/env python3
"""Small host-SQLite checks of actual DAO/migration SQL. Not an Android/Kotlin test."""
from pathlib import Path
import hashlib
import re
import sqlite3


def main():
    root = Path(__file__).resolve().parents[1]
    source = (root / "app/src/main/kotlin/com/example/data/local/SavedItemDao.kt").read_text()
    queries = re.findall(r'@Query\("([^"\n]+)"\)', source)
    metadata = next(sql for sql in queries if sql.startswith("SELECT id, COALESCE"))
    slice_sql = next(sql for sql in queries if sql.startswith("SELECT coalesce(substr(CAST(content AS BLOB)"))
    slice_sql = slice_sql.replace("${NoteSizePolicy.IO_CHUNK_BYTES}", str(16 * 1024))
    commit = next(sql for sql in queries if sql.startswith("UPDATE saved_items SET content = ''"))
    projection = next(sql for sql in queries if sql.startswith("SELECT id, type, title,") and "WHERE isDeleted = 0 ORDER BY" in sql)
    search = next(sql for sql in queries if "note_search MATCH :query" in sql)
    entity = (root / "app/src/main/kotlin/com/example/data/local/SavedItemEntity.kt").read_text().split("    fun toDomainModel")[0]
    properties = re.findall(r'\bval (\w+): (String|Long|Int|Double|Boolean)(\?)?', entity)
    added = {"bodyLocation", "contentHash", "byteLength", "formatVersion", "preview", "bodyMissing"}
    columns = []
    for name, kind, nullable in properties:
        if name in added:
            continue
        sql_type = "TEXT" if kind == "String" else "REAL" if kind == "Double" else "INTEGER"
        default = "NULL" if nullable else "''" if kind == "String" else "0"
        columns.append(f'"{name}" {sql_type} DEFAULT {default}')
    connection = sqlite3.connect(":memory:")
    connection.execute("CREATE TABLE saved_items (" + ", ".join(columns) + ")")
    bodies = {
        "links": "# Original\n" + "a" * 500 + " needle_after_preview\n" + "\n\n".join(f"[Link {i}](https://example.org/{i})" for i in range(5_000)),
        "unicode": "😀漢字\u0000\r\nplain\n" * 500,
        "one-line": "one line",
        "empty": "",
    }
    for key, body in bodies.items():
        connection.execute("INSERT INTO saved_items (id,title,content,type,isDeleted,timestamp) VALUES (?, ?, ?, 'TEXT', 0, 1)", (key, "Title" * 1_000, body))
    connection.execute("INSERT INTO saved_items (id,title,content,type,isDeleted,timestamp) VALUES ('hidden','Hidden','x','TEXT',1,1)")
    connection.execute("INSERT INTO saved_items (id,title,content,type,isDeleted,timestamp) VALUES ('link','URL','https://example.org','LINK',0,1)")
    database_source = (root / "app/src/main/kotlin/com/example/data/local/AppDatabase.kt").read_text()
    migration = database_source.split("val MIGRATION_8_9", 1)[1].split("fun getDatabase", 1)[0]
    statements = re.findall(r'db.execSQL\("([^"\n]+)"\)', migration)
    assert len(statements) == 7
    for sql in statements:
        connection.execute(sql)
    for key, original in bodies.items():
        assert connection.execute("SELECT content FROM saved_items WHERE id=?", (key,)).fetchone()[0] == original
    rows = connection.execute(metadata).fetchall()
    assert {row[0] for row in rows} == set(bodies)
    for key, title, byte_length in rows:
        original = bodies[key].encode("utf-8")
        assert len(title) == 160 and byte_length == len(original)
        huge = connection.execute(slice_sql, {"id": key, "start": 1, "count": 2**31 - 1}).fetchone()[0]
        assert len(huge) <= 16 * 1024
        chunks = [connection.execute(slice_sql, {"id": key, "start": start + 1, "count": 16 * 1024}).fetchone()[0]
                  for start in range(0, byte_length, 16 * 1024)]
        assert hashlib.sha256(b"".join(chunks)).digest() == hashlib.sha256(original).digest()
        args = {"id": key, "hash": hashlib.sha256(original).hexdigest(), "bytes": byte_length,
                "preview": bodies[key][:200], "expectedUpdatedAt": 99, "changedAt": 1}
        assert connection.execute(commit, args).rowcount == 0
        assert connection.execute("SELECT content FROM saved_items WHERE id=?", (key,)).fetchone()[0] == bodies[key]
        args["expectedUpdatedAt"] = 0
        assert connection.execute(commit, args).rowcount == 1
        assert connection.execute("SELECT content,bodyLocation,byteLength FROM saved_items WHERE id=?", (key,)).fetchone() == ("", "FILE", byte_length)
        connection.execute("INSERT INTO note_search(noteId,title,bodyPrefix) VALUES (?,?,?)", (key, title, bodies[key][:32000].replace("\u0000", " ")))
    projected = connection.execute(projection).fetchall()
    assert all(len(row[3]) <= 200 for row in projected if row[0] in bodies)
    assert connection.execute(search, {"query": '"needle_after_preview"*'}).fetchone()[0] == "links"
    connection.close()
    print("PASS: actual v8->v9 additive SQL preserves legacy source bodies")
    print("PASS: bounded slices and byte-exact 5k-link/UTF-8/NUL/CRLF/one-line/empty cases")
    print("PASS: body-reference CAS, preview-only rows and bounded-prefix FTS4 search")
    print("NOT RUN: Kotlin compilation, Android Room validation, filesystem/Compose tests or performance")


if __name__ == "__main__":
    main()
