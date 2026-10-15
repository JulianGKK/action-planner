"""Small Schema11 host-SQLite delete checks. No Android SQLCipher runtime claim."""
import importlib.util
import pathlib
import re
import sqlite3
import sys
import unittest
import uuid

sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('document_fixture', ROOT / 'tools/p10b2a/check_document_database.py')
f = importlib.util.module_from_spec(spec)
spec.loader.exec_module(f)
source = (ROOT / 'data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt').read_text()
queries = re.findall(r'@Query\("([^"\n]+)"\)',source)
PARENT_DELETE = next(q for q in queries if q.startswith('DELETE FROM documents WHERE'))
PENDING = next(q for q in queries if q.startswith("UPDATE document_jobs SET phase='DELETE_FILE_PENDING'"))
JOB_DELETE = next(q for q in queries if q.startswith('DELETE FROM document_jobs WHERE') and "DELETE_FILE_PENDING" in q)
COUNT = next(q for q in queries if q.startswith('SELECT COUNT(*) FROM documents'))
LIST = next(q for q in queries if 'ORDER BY d.importedAt,d.id' in q)
GET = next(q for q in queries if q.startswith('SELECT d.id,') and 'd.id=:id' in q)


class DeleteSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        f.create_schema(self.db,f.NEW)
        for sql in f.TRIGGERS:
            self.db.execute(sql)
        f.action(self.db)
        self.doc = f.ready(self.db,7)
        self.job = f.delete_job(self.db,self.doc,7)
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def detach(self):
        self.assertEqual(1,self.db.execute(PARENT_DELETE,dict(actionId='a',documentId=self.doc['documentId'])).rowcount)
        self.assertEqual(1,self.db.execute(PENDING,dict(id=self.job)).rowcount)

    def test_reserved_hides_count_list_detail_without_persisting_key_or_name(self):
        self.assertEqual([(0,)],self.db.execute(COUNT,dict(a='a')).fetchall())
        self.assertEqual([],self.db.execute(LIST,dict(a='a')).fetchall())
        self.assertEqual([],self.db.execute(GET,dict(a='a',id=self.doc['documentId'])).fetchall())
        self.assertEqual([(None,None,None,None)],self.db.execute('SELECT fileKey,displayName,format,stagingName FROM document_jobs').fetchall())
        self.assertEqual([(1,)],self.db.execute('SELECT COUNT(*) FROM documents').fetchall())

    def test_pending_is_atomic_and_removes_live_key_but_preserves_capacity(self):
        with self.db:
            self.detach()
        self.assertEqual([(0,)],self.db.execute('SELECT COUNT(*) FROM documents').fetchall())
        self.assertEqual([('DELETE_FILE_PENDING',7,None)],self.db.execute('SELECT phase,plaintextSize,fileKey FROM document_jobs').fetchall())
        self.assertEqual([(1,7)],self.db.execute('SELECT COUNT(*),SUM(plaintextSize) FROM document_jobs').fetchall())
        with self.assertRaises(sqlite3.IntegrityError),self.db:
            self.db.execute("DELETE FROM actions WHERE id='a'")

    def test_failed_phase_change_rolls_back_parent_delete(self):
        self.db.execute("CREATE TRIGGER synthetic_pending_fault BEFORE UPDATE ON document_jobs BEGIN SELECT RAISE(ABORT,'synthetic'); END")
        with self.assertRaises(sqlite3.IntegrityError),self.db:
            self.detach()
        self.assertEqual([(32,)],self.db.execute('SELECT length(fileKey) FROM documents').fetchall())
        self.assertEqual([('DELETE_RESERVED',)],self.db.execute('SELECT phase FROM document_jobs').fetchall())

    def test_final_delete_failure_preserves_job_then_exact_delete_completes(self):
        with self.db:
            self.detach()
        self.db.execute("CREATE TRIGGER synthetic_finish_fault BEFORE DELETE ON document_jobs BEGIN SELECT RAISE(ABORT,'synthetic'); END")
        with self.assertRaises(sqlite3.IntegrityError),self.db:
            self.db.execute(JOB_DELETE,dict(id=self.job))
        self.assertEqual([('DELETE_FILE_PENDING',)],self.db.execute('SELECT phase FROM document_jobs').fetchall())
        self.db.execute('DROP TRIGGER synthetic_finish_fault')
        with self.db:
            self.assertEqual(1,self.db.execute(JOB_DELETE,dict(id=self.job)).rowcount)
        self.assertEqual([],self.db.execute('SELECT id FROM document_jobs').fetchall())
        self.assertEqual(0,self.db.execute(JOB_DELETE,dict(id=self.job)).rowcount)


if __name__ == '__main__':
    unittest.main()
