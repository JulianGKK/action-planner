package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class MemorySectionFreezeTest {
    private fun section()=MemorySectionSave(1,linkedMapOf(
        "B_ENDING_MODE" to MemoryAnswerValue.Multi(mutableListOf("SELF_LEFT","AFTER_REQUEST")),
        "B_AGREEMENT_OR_CONDITION" to MemoryAnswerValue.Option("NO")),linkedSetOf("B_AGREEMENT_DETAILS","B_THREATS_DETAILS"))
    @Test fun frozenAnswerMapRejectsMutableCast() {
        val frozen=freezeMemorySection(section())
        assertThrows(UnsupportedOperationException::class.java){(frozen.answers as MutableMap).clear()}
    }
    @Test fun frozenConflictSetRejectsMutableCast() {
        val frozen=freezeMemorySection(section())
        assertThrows(UnsupportedOperationException::class.java){(frozen.retainedConflicts as MutableSet).clear()}
    }
    @Test fun frozenNestedMultiRejectsMutableCast() {
        val frozen=freezeMemorySection(section())
        assertThrows(UnsupportedOperationException::class.java){((frozen.answers.getValue("B_ENDING_MODE") as MemoryAnswerValue.Multi).keys as MutableList).clear()}
    }
    @Test fun originalMapSetAndMultiMutationCannotChangeSubmittedSnapshot() {
        val multi=mutableListOf("SELF_LEFT","AFTER_REQUEST")
        val answers=linkedMapOf<String,MemoryAnswerValue>("B_ENDING_MODE" to MemoryAnswerValue.Multi(multi))
        val conflicts=linkedSetOf("B_AGREEMENT_DETAILS","B_THREATS_DETAILS")
        val frozen=freezeMemorySection(MemorySectionSave(1,answers,conflicts))
        multi.clear();answers.clear();conflicts.clear()
        assertEquals(listOf("SELF_LEFT","AFTER_REQUEST"),(frozen.answers.getValue("B_ENDING_MODE") as MemoryAnswerValue.Multi).keys)
        assertEquals(setOf("B_AGREEMENT_DETAILS","B_THREATS_DETAILS"),frozen.retainedConflicts)
    }
}
