package org.pag.prototype.product

import java.time.LocalDateTime
import java.time.ZoneId

internal fun selectedInstant(value: LocalDateTime?, zone: ZoneId): Long? {
    if (value == null) return null
    val offsets = zone.rules.getValidOffsets(value)
    // Do not silently shift nonexistent times or guess an offset at an overlap.
    if (offsets.size != 1) return null
    return value.toInstant(offsets.single()).toEpochMilli()
}
