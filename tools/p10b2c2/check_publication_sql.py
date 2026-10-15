"""Focused host-SQLite publication transactions using unchanged Schema11 constraints.
Not an Android-SQLCipher/Room runtime test; no historical suite is executed.
"""
import importlib.util
import pathlib
import re
import sqlite3
import sys
import unittest

sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('document_fixture', ROOT / 'tools/p10b2a/check_document_database.py')
f = importlib.util.module_from_spec(spec)
spec.loader.exec_module(f)
source = (ROOT / 'data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt').read_text()
DELETE = re.search(r'@Query\("(DELETE FROM document_jobs WHERE id=:id[^"\n]+)"\)', source).group(1)


class PublicationSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        f.create_schema(self.db, f.NEW)
        for sql in f.TRIGGERS:
            self.db.execute(sql)
        f.action(self.db)
        self.j = f.reserve(self.db)
        self.db.execute("UPDATE document_jobs SET phase='IMPORT_WRITING' WHERE id=?",(self.j['id'],))
        self.db.execute("UPDATE document_jobs SET phase='IMPORT_CLOSED',plaintextSize=7 WHERE id=?",(self.j['id'],))
        self.db.execute("UPDATE document_jobs SET phase='FILE_PUBLISHED' WHERE id=?",(self.j['id'],))
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def insert(self):
        j=self.j
        self.db.execute('INSERT INTO documents VALUES(?,?,?,?,?,?,?,?)',
                        (j['documentId'],'a','synthetic','TEXT',7,42,j['blobName'],j['fileKey']))

    def test_atomic_key_transfer_preserves_parent_constraint_and_removes_job(self):
        with self.db:
            self.insert()
            self.assertEqual(1,self.db.execute(DELETE,dict(id=self.j['id'])).rowcount)
        self.assertEqual([(1,)],self.db.execute('SELECT COUNT(*) FROM documents').fetchall())
        self.assertEqual([],self.db.execute('SELECT id FROM document_jobs').fetchall())
        self.assertEqual([(32,)],self.db.execute('SELECT length(fileKey) FROM documents').fetchall())

    def test_jobdelete_failure_rolls_back_parent_insert(self):
        self.db.execute("CREATE TRIGGER synthetic_delete_fault BEFORE DELETE ON document_jobs BEGIN SELECT RAISE(ABORT,'synthetic'); END")
        with self.assertRaises(sqlite3.IntegrityError),self.db:
            self.insert();self.db.execute(DELETE,dict(id=self.j['id']))
        self.assertEqual([(0,)],self.db.execute('SELECT COUNT(*) FROM documents').fetchall())
        self.assertEqual([('FILE_PUBLISHED',)],self.db.execute('SELECT phase FROM document_jobs').fetchall())

    def test_insert_failure_and_late_commit_denial_preserve_published_job(self):
        self.db.execute("CREATE TRIGGER synthetic_insert_fault BEFORE INSERT ON documents BEGIN SELECT RAISE(ABORT,'synthetic'); END")
        with self.assertRaises(sqlite3.IntegrityError),self.db:
            self.insert()
        self.db.execute('DROP TRIGGER synthetic_insert_fault')
        with self.assertRaises(RuntimeError),self.db:
            self.insert();self.db.execute(DELETE,dict(id=self.j['id']));raise RuntimeError('simulated commit denial')
        self.assertEqual([(0,)],self.db.execute('SELECT COUNT(*) FROM documents').fetchall())
        self.assertEqual([('FILE_PUBLISHED',)],self.db.execute('SELECT phase FROM document_jobs').fetchall())

    def test_existing_import_parent_trigger_cannot_be_bypassed(self):
        self.db.execute('DELETE FROM document_jobs');self.db.commit()
        with self.assertRaises(sqlite3.IntegrityError),self.db:
            self.insert()
        self.assertEqual([(0,)],self.db.execute('SELECT COUNT(*) FROM documents').fetchall())


if __name__ == '__main__':
    unittest.main()
