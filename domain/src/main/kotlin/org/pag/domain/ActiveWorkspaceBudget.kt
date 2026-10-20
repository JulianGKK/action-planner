package org.pag.domain

/**
 * Accounting foundation, no payload and no storage/session authorization. One ledger
 * per future scope, confined to its owner thread. Charges are engineering allowances, NOT
 * certified ART deep sizes or an App/Native/OOM bound. DTO/capacity calibration is still needed.
 *
 * A reader must obtain conservative counts/text/capacity upper bounds BEFORE fetching strings
 * or unbounded row lists in the same authorized transaction. Charge each representation once
 * (including all temporary builders/rows/copies); actual units must not exceed its reservation.
 * Successful admission alone never makes a partial result complete or permits publication.
 */
class ActiveWorkspaceBudget {
    enum class Decision { ADMITTED, BUDGET_REFUSED, INVALID_DATA }

    /**
     * Additional, disjoint allocations. No dedup discount. UTF16 units, NOT codepoints/UTF8
     * bytes; a codepoint aggregate may conservatively reserve two units per codepoint using
     * checked multiplication. Capacities are allocated upper bounds, not collection sizes.
     * Objects exclude the separately counted String/array/collection/index allocations.
     * Arrays include backing arrays not already covered by list/map/index allowances.
     */
    data class Units(
        val objects: Long = 0, val strings: Long = 0, val utf16Units: Long = 0,
        val arrays: Long = 0, val arrayPayloadBytes: Long = 0,
        val lists: Long = 0, val listCapacity: Long = 0,
        val maps: Long = 0, val mapCapacity: Long = 0,
        val indexes: Long = 0, val indexCapacity: Long = 0, val references: Long = 0
    )
    class Representation internal constructor()
    data class Accounting(val chargedBytes: Long, val decision: Decision, val sealed: Boolean)
    private class Allocation(val ticket: Representation) {
        var chargedBytes = 0L
        var decision = Decision.ADMITTED
        var sealed = false
    }

    private val thread = Thread.currentThread()
    private var retained: Allocation? = null
    private var transition: Allocation? = null
    private var closed = false
    private fun owner() { check(Thread.currentThread() === thread) }
    private fun held(value: Representation): Allocation? = if (closed) null else
        retained?.takeIf { it.ticket === value } ?: transition?.takeIf { it.ticket === value }

    fun accounting(value: Representation): Accounting? {
        owner()
        return held(value)?.let { Accounting(it.chargedBytes, it.decision, it.sealed) }
    }

    /** Only one initial representation. No silent replacement, resize or budget reset. */
    fun beginRetained(): Representation? {
        owner()
        if (closed || retained != null) return null
        return Representation().also { retained = Allocation(it) }
    }

    /** At most one transition, independently limited to 8 MiB. No third graph allowance. */
    fun beginTransition(): Representation? {
        owner()
        if (closed || retained?.sealed != true || transition != null) return null
        return Representation().also { transition = Allocation(it) }
    }

    /** Must be called BEFORE the next bounded allocation/materialization, never after list(). */
    fun admit(value: Representation, units: Units): Decision {
        owner()
        val allocation = held(value) ?: return Decision.INVALID_DATA
        if (allocation.sealed) return Decision.INVALID_DATA
        val estimate = estimate(units)
        if (estimate.first == Decision.INVALID_DATA) {
            allocation.decision = Decision.INVALID_DATA
            return allocation.decision
        }
        if (allocation.decision != Decision.ADMITTED) return allocation.decision // sticky, no retry loop
        val next = try { Math.addExact(allocation.chargedBytes, estimate.second) }
            catch (_: ArithmeticException) { null }
        if (estimate.first == Decision.BUDGET_REFUSED || next == null || next > LIMIT_BYTES) {
            allocation.decision = Decision.BUDGET_REFUSED
        } else {
            allocation.chargedBytes = next
        }
        return allocation.decision
    }

    /** Reader must separately prove completeness, actual-unit bounds and storage cleanup. */
    fun seal(value: Representation): Boolean {
        owner()
        val allocation = held(value) ?: return false
        if (allocation.sealed || allocation.decision != Decision.ADMITTED) return false
        allocation.sealed = true
        return true
    }

    /**
     * Accounting only. Caller must drop ALL old payload/form/builder references before the next
     * transition can start. Does not publish, authorize, or physically wipe anything.
     */
    fun promote(value: Representation): Boolean {
        owner()
        val allocation = transition ?: return false
        if (closed || allocation.ticket !== value || !allocation.sealed || allocation.decision != Decision.ADMITTED) return false
        retained = allocation; transition = null
        return true
    }

    /** Terminal; cannot reopen this ledger. Payload cleanup belongs to its future owner. */
    fun close() { owner(); closed = true; retained = null; transition = null }

    companion object {
        const val LIMIT_BYTES = 8L * 1024 * 1024

        /** Pure engineering cost/validation, including values above LIMIT_BYTES.
         * ADMITTED here means structurally representable cost, NOT scope admission:
         * only admit()/seal() on ONE ledger can authorize materialization. No ledger created.
         */
        fun estimateUnits(units: Units): Pair<Decision, Long> = estimate(units)

        private fun estimate(u: Units): Pair<Decision, Long> {
            val counts = longArrayOf(u.objects, u.strings, u.utf16Units, u.arrays, u.arrayPayloadBytes,
                u.lists, u.listCapacity, u.maps, u.mapCapacity, u.indexes, u.indexCapacity, u.references)
            if (counts.any { it < 0 } || (u.strings == 0L && u.utf16Units != 0L) ||
                (u.arrays == 0L && u.arrayPayloadBytes != 0L) ||
                (u.lists == 0L && u.listCapacity != 0L) ||
                (u.maps == 0L && u.mapCapacity != 0L) ||
                (u.indexes == 0L && u.indexCapacity != 0L)) return Decision.INVALID_DATA to 0L
            return try {
                var total = 0L
                fun add(count: Long, weight: Long) { total = Math.addExact(total, Math.multiplyExact(count, weight)) }
                add(u.objects, 128) // inclusive DTO/field/alignment allowance
                add(u.strings, 64 + 7) // String + backing header + per-allocation alignment slack
                add(u.utf16Units, 4) // deliberately >= UTF16 payload, including surrogate pairs
                add(u.arrays, 64 + 7); add(u.arrayPayloadBytes, 1)
                add(u.lists, 128); add(u.listCapacity, 8)
                add(u.maps, 128); add(u.mapCapacity, 128) // nodes/table/slack allowance
                add(u.indexes, 128); add(u.indexCapacity, 128)
                add(u.references, 8)
                Decision.ADMITTED to total
            } catch (_: ArithmeticException) { Decision.BUDGET_REFUSED to 0L }
        }
    }
}
