"""Host-only real SQLite, in memory, schema 11 from the repository. No product data/files.

Line protocol used by SqliteHostConnection; no repository/DAO responses are mocked.
Python is test infrastructure only, never shipped to Android.
"""
import base64
import json
import sqlite3
import sys

db = sqlite3.connect(":memory:", isolation_level=None)
if len(sys.argv) > 2:
    if sys.argv[2] not in ("UTF-8", "UTF-16le"):
        raise ValueError("Unsupported test encoding")
    db.execute("PRAGMA encoding='" + sys.argv[2] + "'")
db.execute("PRAGMA foreign_keys=ON")
with open(sys.argv[1], encoding="utf-8") as schema:
    for entity in json.load(schema)["database"]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
statements = {}


def decode(value):
    if value == "N":
        return None
    tag, value = value.split(":", 1)
    if tag == "I":
        return int(value)
    if tag == "F":
        return float(value)
    raw = base64.b64decode(value)
    return raw if tag == "B" else raw.decode("utf-8", errors="strict")


def encode(value):
    if value is None:
        return "N"
    if isinstance(value, int):
        return "I:" + str(value)
    if isinstance(value, float):
        return "F:" + str(value)
    if isinstance(value, bytes):
        return "B:" + base64.b64encode(value).decode("ascii")
    return "S:" + base64.b64encode(value.encode("utf-8", errors="strict")).decode("ascii")


for line in sys.stdin:
    try:
        fields = line.rstrip("\n").split("\t")
        op = fields[0]
        if op == "Q":
            sql = base64.b64decode(fields[2]).decode("utf-8")
            statements[fields[1]] = db.execute(sql, tuple(decode(x) for x in fields[3:]))
            print("OK", flush=True)
        elif op == "S":
            row = statements[fields[1]].fetchone()
            print("END" if row is None else "ROW\t" + "\t".join(map(encode, row)), flush=True)
        elif op == "C":
            cursor = statements.pop(fields[1], None)
            if cursor is not None:
                cursor.close()
            print("OK", flush=True)
        elif op == "X":
            for cursor in statements.values():
                cursor.close()
            db.close()
            print("OK", flush=True)
            break
        else:
            raise ValueError("Invalid protocol")
    except Exception as error:
        # No SQL/text payload in failures.
        print("ERROR:" + type(error).__name__, flush=True)
