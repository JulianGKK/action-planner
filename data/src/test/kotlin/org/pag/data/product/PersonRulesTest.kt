package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.Locale

class PersonRulesTest {
    @Test fun validPseudonymDoesNotRequireRealName() {
        assertTrue(PersonRules.valid(" synthetic-01 "))
        assertFalse(PersonRules.valid("")); assertFalse(PersonRules.valid(" \t\n"))
    }
    @Test fun normalizationTrimsAndIgnoresCase() {
        assertEquals("synthetic-01", PersonRules.normalized(" SyNtHeTiC-01 "))
    }
    @Test fun normalizationDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); assertEquals("i", PersonRules.normalized(" I ")) }
        finally { Locale.setDefault(previous) }
    }
    @Test fun onlyPreparationAllowsManagement() {
        ActionStatus.entries.forEach { assertEquals(it == ActionStatus.PREPARATION, PersonRules.mayManage(it)) }
    }
    @Test fun draftAndDeletionPendingDoNotExposePersons() {
        assertEquals(listOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED),
            ActionStatus.entries.filter(PersonRules::mayRead))
    }
}
