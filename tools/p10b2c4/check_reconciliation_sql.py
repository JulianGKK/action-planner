"""Focused Schema11 host-SQLite recovery checks; no SQLCipher/Android runtime claim."""
import importlib.util
import pathlib
import re
import sqlite3
import sys
import unittest

sys.dont_write_bytecode=True
ROOT=pathlib.Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('document_fixture',ROOT/'tools/p10b2a/check_document_database.py')
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
source=(ROOT/'data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt').read_text()
ABORT=re.search(r'@Query\("(DELETE FROM document_jobs WHERE id=:id AND phase=:phase[^"\n]+)"\)',source).group(1)

class ReconciliationSqlTest(unittest.TestCase):
    def setUp(self):
        self.db=sqlite3.connect(':memory:');f.create_schema(self.db,f.NEW)
        for sql in f.TRIGGERS:self.db.execute(sql)
        f.action(self.db);self.db.commit()
    def tearDown(self):self.db.close()
    def test_abort_expected_phase_and_rollback(self):
        j=f.reserve(self.db);self.db.commit()
        self.assertEqual(0,self.db.execute(ABORT,dict(id=j['id'],phase='IMPORT_WRITING')).rowcount)
        self.assertEqual(1,self.db.execute(ABORT,dict(id=j['id'],phase='IMPORT_RESERVED')).rowcount)
        self.db.rollback()
        self.assertEqual([('IMPORT_RESERVED',)],self.db.execute('SELECT phase FROM document_jobs').fetchall())
        with self.db:self.assertEqual(1,self.db.execute(ABORT,dict(id=j['id'],phase='IMPORT_RESERVED')).rowcount)
        self.assertEqual([],self.db.execute('SELECT id FROM document_jobs').fetchall())
    def test_abort_cannot_remove_delete_job(self):
        d=f.ready(self.db,9);j=f.delete_job(self.db,d,9);self.db.commit()
        self.assertEqual(0,self.db.execute(ABORT,dict(id=j,phase='DELETE_RESERVED')).rowcount)
        self.assertEqual([('DELETE_RESERVED',)],self.db.execute('SELECT phase FROM document_jobs').fetchall())
    def test_all_six_phases_materialize_with_original_constraints(self):
        for phase in ['IMPORT_RESERVED','IMPORT_WRITING','IMPORT_CLOSED','FILE_PUBLISHED']:
            j=f.reserve(self.db)
            if phase!='IMPORT_RESERVED':self.db.execute("UPDATE document_jobs SET phase='IMPORT_WRITING' WHERE id=?",(j['id'],))
            if phase in ['IMPORT_CLOSED','FILE_PUBLISHED']:self.db.execute("UPDATE document_jobs SET phase='IMPORT_CLOSED',plaintextSize=9 WHERE id=?",(j['id'],))
            if phase=='FILE_PUBLISHED':self.db.execute("UPDATE document_jobs SET phase='FILE_PUBLISHED' WHERE id=?",(j['id'],))
        d=f.ready(self.db,9);f.delete_job(self.db,d,9)
        d=f.ready(self.db,9);j=f.delete_job(self.db,d,9)
        self.db.execute('DELETE FROM documents WHERE id=?',(d['documentId'],))
        self.db.execute("UPDATE document_jobs SET phase='DELETE_FILE_PENDING' WHERE id=?",(j,))
        self.assertEqual(6,self.db.execute('SELECT COUNT(DISTINCT phase) FROM document_jobs').fetchone()[0])
        self.assertEqual([],self.db.execute('PRAGMA foreign_key_check').fetchall())

if __name__=='__main__':unittest.main()
