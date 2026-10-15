"""Focused real host SQLite projections; not Android SQLCipher evidence."""
import importlib.util
import pathlib
import re
import sqlite3
import sys
import unittest

sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('foundation', ROOT / 'tools/p10b2a/check_document_database.py')
f = importlib.util.module_from_spec(spec)
spec.loader.exec_module(f)
queries = re.findall(r'@Query\("([^"\n]+)"\)', f.SOURCE)
COUNT = next(q for q in queries if q.startswith('SELECT COUNT(*) FROM documents'))
LIST = next(q for q in queries if 'ORDER BY d.importedAt,d.id' in q)


class ProductProjectionTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        f.create_schema(self.db, f.NEW)
        for sql in f.TRIGGERS:
            self.db.execute(sql)
        f.action(self.db)

    def ready_at(self, at, size):
        j = f.reserve(self.db)
        self.db.execute("UPDATE document_jobs SET phase='IMPORT_WRITING' WHERE id=?", (j['id'],))
        self.db.execute("UPDATE document_jobs SET phase='IMPORT_CLOSED',plaintextSize=? WHERE id=?", (size, j['id']))
        self.db.execute("UPDATE document_jobs SET phase='FILE_PUBLISHED' WHERE id=?", (j['id'],))
        self.db.execute('INSERT INTO documents VALUES(?,?,?,?,?,?,?,?)',
                        (j['documentId'], 'a', j['displayName'], j['format'], size, at, j['blobName'], j['fileKey']))
        self.db.execute('DELETE FROM document_jobs WHERE id=?', (j['id'],))
        return j

    def tearDown(self):
        self.db.close()

    def test_key_free_list_and_count_never_read_file_keys_or_locators(self):
        for sql in (COUNT, LIST):
            self.assertNotIn('fileKey', sql)
            self.assertNotIn('blobName', sql)
            self.assertNotIn('SELECT *', sql)
        f.ready(self.db, 7)
        self.assertEqual([(1,)], self.db.execute(COUNT, {'a': 'a'}).fetchall())
        cursor = self.db.execute(LIST, {'a': 'a'})
        self.assertEqual(['id', 'actionId', 'displayName', 'format', 'plaintextSize', 'importedAt'], [d[0] for d in cursor.description])
        self.assertEqual(1, len(cursor.fetchall()))

    def test_database_order_is_import_time_then_id_and_hidden_delete_is_not_counted(self):
        jobs = [self.ready_at(at, n) for n, at in ((1, 2), (2, 1), (3, 1))]
        rows = self.db.execute(LIST, {'a': 'a'}).fetchall()
        self.assertEqual(sorted(rows, key=lambda r: (r[5], r[0])), rows)
        f.delete_job(self.db, jobs[1], 2)
        self.assertEqual([(2,)], self.db.execute(COUNT, {'a': 'a'}).fetchall())
        self.assertNotIn(jobs[1]['documentId'], [r[0] for r in self.db.execute(LIST, {'a': 'a'})])


if __name__ == '__main__':
    unittest.main()
