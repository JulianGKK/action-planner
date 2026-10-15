"""Focused host-SQLite checks using production migration/constraint SQL and Room exports.
No Android/SQLCipher engine claim. Synthetic in-memory databases; temporary DB only
for concurrent writers. No document files or keys are printed.
"""
import json
import pathlib
import re
import sqlite3
import tempfile
import threading
import unittest
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = (ROOT / 'data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt').read_text()
SCHEMAS = ROOT / 'data/schemas/org.pag.data.product.ActionDatabase'
OLD = json.loads((SCHEMAS / '10.json').read_text())['database']
NEW = json.loads((SCHEMAS / '11.json').read_text())['database']


def sql_list(name):
    block = SOURCE.split('internal val ' + name + ' = listOf(', 1)[1].split('\n)', 1)[0]
    return re.findall(r'^\s*"([^"]+)"', block, re.M)


CONSTANTS = dict(re.findall(r'internal const val (DOCUMENT_\w+) = "([^"]+)"', SOURCE))
MIGRATION = sql_list('DOCUMENT_SCHEMA_SQL')
TRIGGERS = sql_list('DOCUMENT_CONSTRAINT_SQL')
installer = SOURCE.split('internal suspend fun installDocumentConstraints(', 1)[1]
for sql in re.findall(r'connection.prepare\("([^"]+)"\)', installer):
    for operation in (['INSERT', 'UPDATE'] if '${operation.lowercase()}' in sql else [None]):
        expanded = sql
        if operation:
            expanded = expanded.replace('${operation.lowercase()}', operation.lower()).replace('$operation', operation)
        for key, value in CONSTANTS.items():
            expanded = expanded.replace('$' + key, value)
        assert '$' not in expanded
        TRIGGERS.append(expanded)


def create_schema(db, schema):
    db.execute('PRAGMA foreign_keys=ON')
    for entity in schema['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))


def migrate(db):
    for sql in MIGRATION + TRIGGERS:
        db.execute(sql)


def action(db, id='a'):
    db.execute("INSERT INTO actions(id,title,description,startAt,expectedEndAt,createdAt,status,sensitivity) VALUES(?,?,?,1,2,0,'ACTIVE','SENSITIVE')", (id, 'synthetic', 'synthetic'))


def reserve(db, a='a', document=None, blob=None, key=None):
    j = dict(id=str(uuid.uuid4()), actionId=a, documentId=document or str(uuid.uuid4()),
             operation='IMPORT', phase='IMPORT_RESERVED', stagingName=str(uuid.uuid4()),
             blobName=blob or str(uuid.uuid4()), displayName='synthetic', format='TEXT',
             plaintextSize=None, fileKey=bytes(32) if key is None else key)
    db.execute('INSERT INTO document_jobs VALUES(' + ','.join(['?'] * 11) + ')', tuple(j.values()))
    return j


def ready(db, size=0):
    j = reserve(db)
    db.execute("UPDATE document_jobs SET phase='IMPORT_WRITING' WHERE id=?", (j['id'],))
    db.execute("UPDATE document_jobs SET phase='IMPORT_CLOSED',plaintextSize=? WHERE id=?", (size, j['id']))
    db.execute("UPDATE document_jobs SET phase='FILE_PUBLISHED' WHERE id=?", (j['id'],))
    db.execute('INSERT INTO documents VALUES(?,?,?,?,?,?,?,?)',
               (j['documentId'], 'a', j['displayName'], j['format'], size, 1, j['blobName'], j['fileKey']))
    db.execute('DELETE FROM document_jobs WHERE id=?', (j['id'],))
    return j


def delete_job(db, j, size):
    id = str(uuid.uuid4())
    db.execute("INSERT INTO document_jobs VALUES(?,?,?,'DELETE','DELETE_RESERVED',NULL,?,NULL,NULL,?,NULL)",
               (id, 'a', j['documentId'], j['blobName'], size))
    return id


class DocumentDatabaseChecks(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        create_schema(self.db, OLD)
        migrate(self.db)
        action(self.db)

    def tearDown(self):
        self.db.close()

    def test_export_and_additive_migration_preserve_old_entities_rows_and_trigger(self):
        old = {e['tableName']: e for e in OLD['entities']}
        new = {e['tableName']: e for e in NEW['entities']}
        self.assertEqual(11, NEW['version'])
        self.assertEqual({'documents', 'document_jobs'}, new.keys() - old.keys())
        self.assertEqual(old, {k: new[k] for k in old})
        db = sqlite3.connect(':memory:')
        try:
            create_schema(db, OLD)
            action(db)
            db.execute("CREATE TRIGGER synthetic_old_trigger AFTER UPDATE ON actions BEGIN SELECT 1; END")
            before = list(db.execute("SELECT type,name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name"))
            before_row = list(db.execute('SELECT * FROM actions'))
            migrate(db)
            for type, name, sql in before:
                self.assertEqual([(type, name, sql)], list(db.execute('SELECT type,name,sql FROM sqlite_master WHERE name=?', (name,))))
            self.assertEqual(before_row, list(db.execute('SELECT * FROM actions')))
            self.assertEqual([], list(db.execute('PRAGMA foreign_key_check')))
            fresh = sqlite3.connect(':memory:')
            try:
                create_schema(fresh, NEW)
                for sql in TRIGGERS:
                    fresh.execute(sql)
                for table in ['documents', 'document_jobs']:
                    self.assertEqual(list(db.execute('PRAGMA table_info(' + table + ')')), list(fresh.execute('PRAGMA table_info(' + table + ')')))
                    self.assertEqual(list(db.execute('PRAGMA foreign_key_list(' + table + ')')), list(fresh.execute('PRAGMA foreign_key_list(' + table + ')')))
            finally:
                fresh.close()
        finally:
            db.close()

    def test_restrict_for_both_documents_and_jobs(self):
        j = reserve(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("DELETE FROM actions WHERE id='a'")
        self.db.execute('DELETE FROM document_jobs WHERE id=?', (j['id'],))
        ready(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("DELETE FROM actions WHERE id='a'")
        for table in ['documents', 'document_jobs']:
            self.assertEqual('RESTRICT', list(self.db.execute('PRAGMA foreign_key_list(' + table + ')'))[0][6])

    def test_unique_job_names_and_same_action_delete(self):
        j = reserve(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            reserve(self.db, document=j['documentId'])
        with self.assertRaises(sqlite3.IntegrityError):
            reserve(self.db, blob=j['stagingName'])
        action(self.db, 'b')
        j = ready(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO document_jobs VALUES(?, 'b',?,'DELETE','DELETE_RESERVED',NULL,?,NULL,NULL,0,NULL)",
                            (str(uuid.uuid4()), j['documentId'], j['blobName']))

    def test_keys_phases_and_ready_import_guard(self):
        for n in [0, 31, 33]:
            with self.assertRaises(sqlite3.IntegrityError):
                reserve(self.db, key=bytes(n))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute('INSERT INTO documents VALUES(?,?,?,?,?,?,?,?)',
                            (str(uuid.uuid4()), 'a', 'synthetic', 'TEXT', 0, 1, str(uuid.uuid4()), bytes(32)))
        j = reserve(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE document_jobs SET phase='FILE_PUBLISHED',plaintextSize=0 WHERE id=?", (j['id'],))
        j = ready(self.db, 3)
        delete_job(self.db, j, 3)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE document_jobs SET fileKey=? WHERE operation='DELETE'", (bytes(32),))

    def test_delete_visibility_and_capacity_persists_after_live_key_delete(self):
        j = ready(self.db, 8)
        query = re.search(r'@Query\("([^"]+)"\)\s*abstract suspend fun count', SOURCE).group(1).replace(':a', '?')
        self.assertEqual((1,), self.db.execute(query, ('a',)).fetchone())
        job = delete_job(self.db, j, 8)
        self.assertEqual((0,), self.db.execute(query, ('a',)).fetchone())
        capacity = CONSTANTS['DOCUMENT_RESERVED_CAPACITY_SQL'].replace('NEW.actionId', "'a'")
        self.assertEqual((1, 8), self.db.execute(capacity).fetchone())
        self.db.execute('DELETE FROM documents WHERE id=?', (j['documentId'],))
        self.db.execute("UPDATE document_jobs SET phase='DELETE_FILE_PENDING' WHERE id=?", (job,))
        self.assertEqual((1, 8), self.db.execute(capacity).fetchone())
        self.assertEqual([], list(self.db.execute('SELECT fileKey FROM documents')))
        self.assertEqual((None,), self.db.execute('SELECT fileKey FROM document_jobs WHERE id=?', (job,)).fetchone())

    def test_capacity_hundred_slots_and_ten_unknown_sizes(self):
        for _ in range(100):
            ready(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            reserve(self.db)
        self.db.execute('DELETE FROM documents')
        for _ in range(10):
            reserve(self.db)
        with self.assertRaises(sqlite3.IntegrityError):
            reserve(self.db)

    def test_two_concurrent_last_slot_writers_cannot_overbook(self):
        # Exercises actual production reservation trigger under two SQLite writers.
        with tempfile.TemporaryDirectory(prefix='pag-p10-db-test-') as directory:
            path = pathlib.Path(directory) / 'synthetic.db'
            db = sqlite3.connect(path)
            create_schema(db, OLD)
            migrate(db)
            action(db)
            for _ in range(9):
                reserve(db)
            db.commit()
            db.close()
            barrier = threading.Barrier(2)
            results = []
            def writer():
                connection = sqlite3.connect(path, timeout=5)
                connection.execute('PRAGMA foreign_keys=ON')
                barrier.wait()
                try:
                    connection.execute('BEGIN IMMEDIATE')
                    reserve(connection)
                    connection.commit()
                    results.append('admitted')
                except sqlite3.IntegrityError:
                    connection.rollback()
                    results.append('denied')
                finally:
                    connection.close()
            threads = [threading.Thread(target=writer) for _ in range(2)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            self.assertCountEqual(['admitted', 'denied'], results)
            db = sqlite3.connect(path)
            self.assertEqual((10,), db.execute('SELECT COUNT(*) FROM document_jobs').fetchone())
            db.close()


if __name__ == '__main__':
    unittest.main()
