"""Three focused host-SQLite checks for the new DAO phase UPDATE, not Android SQLCipher.
Reuses P10b2a fixture setup only; does not execute its historical test class.
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
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
source = (ROOT / 'data/src/main/kotlin/org/pag/data/product/DocumentDatabase.kt').read_text()
UPDATE = re.search(r'@Query\("(UPDATE document_jobs SET phase=:next[^"\n]+)"\)', source).group(1)


class ImportStagingSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        fixture.create_schema(self.db, fixture.NEW)
        for sql in fixture.TRIGGERS:
            self.db.execute(sql)
        fixture.action(self.db)
        self.job = fixture.reserve(self.db)
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def phase(self, old, next, size=None):
        return self.db.execute(UPDATE, dict(id=self.job['id'],old=old,next=next,size=size)).rowcount

    def test_only_expected_phase_changes_and_closed_shrinks_reservation(self):
        self.assertEqual(1,self.phase('IMPORT_RESERVED','IMPORT_WRITING'))
        self.db.commit()
        self.assertEqual(0,self.phase('IMPORT_RESERVED','IMPORT_WRITING'))
        self.assertEqual(1,self.phase('IMPORT_WRITING','IMPORT_CLOSED',7))
        self.db.commit()
        self.assertEqual([('IMPORT_CLOSED',7)],self.db.execute('SELECT phase,plaintextSize FROM document_jobs').fetchall())
        self.assertEqual([(0,)],self.db.execute('SELECT COUNT(*) FROM documents').fetchall())
        self.assertEqual([],self.db.execute("SELECT id FROM document_jobs WHERE phase='FILE_PUBLISHED'").fetchall())

    def test_closed_update_can_roll_back_without_shrinking_budget(self):
        self.phase('IMPORT_RESERVED','IMPORT_WRITING');self.db.commit()
        self.phase('IMPORT_WRITING','IMPORT_CLOSED',0)
        self.db.rollback()
        self.assertEqual([('IMPORT_WRITING',None)],self.db.execute('SELECT phase,plaintextSize FROM document_jobs').fetchall())
        self.assertEqual([(26214400,)],self.db.execute("SELECT CASE WHEN phase IN ('IMPORT_RESERVED','IMPORT_WRITING') THEN 26214400 ELSE plaintextSize END FROM document_jobs").fetchall())

    def test_skip_and_oversize_rejected_by_existing_schema11_constraints(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.phase('IMPORT_RESERVED','IMPORT_CLOSED',0)
        self.db.rollback()
        self.phase('IMPORT_RESERVED','IMPORT_WRITING');self.db.commit()
        with self.assertRaises(sqlite3.IntegrityError):
            self.phase('IMPORT_WRITING','IMPORT_CLOSED',26214401)
        self.db.rollback()
        self.assertEqual([('IMPORT_WRITING',None)],self.db.execute('SELECT phase,plaintextSize FROM document_jobs').fetchall())


if __name__ == '__main__':
    unittest.main()
