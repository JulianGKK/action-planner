package org.pag.prototype.product

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ProductTimeTest {
    private val zone=ZoneId.of("Europe/Berlin")
    @Test fun normalActionAndTaskTimeUseSameUniqueOffset() {
        val local=LocalDateTime.of(2026,3,15,14,30)
        val expected=local.toInstant(java.time.ZoneOffset.ofHours(1)).toEpochMilli()
        assertEquals(expected,selectedInstant(local,zone))
        assertEquals(selectedInstant(local,zone),parseTaskDue("15.03.2026 14:30",zone))
    }
    @Test fun daylightSavingGapIsRejectedWithoutShifting() {
        assertNull(selectedInstant(LocalDateTime.of(2026,3,29,2,30),zone))
        assertNull(parseTaskDue("29.03.2026 02:30",zone))
        assertNotNull(parseTaskDue("29.03.2026 03:30",zone))
    }
    @Test fun daylightSavingOverlapIsRejectedWithoutChoosingOffset() {
        assertNull(selectedInstant(LocalDateTime.of(2026,10,25,2,30),zone))
        assertNull(parseTaskDue("25.10.2026 02:30",zone))
    }
    @Test fun invalidCalendarDateAndOptionalEmptyTimeRemainRejected() {
        assertNull(parseTaskDue("31.02.2026 14:30",zone))
        assertNull(parseTaskDue("",zone))
        assertNull(selectedInstant(null,zone))
    }
}
