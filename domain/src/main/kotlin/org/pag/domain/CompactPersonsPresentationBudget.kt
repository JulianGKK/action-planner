package org.pag.domain

/** Disjoint allowance for §92a's one metadata handle and bounded scalar-to-Text handoff.
 * No page/list/String cache. At most one owner-thread resolver runs at a time. Nine formatted
 * lines (page label + eight statuses), each <=128 UTF16 units. Reserve additional
 * concatenation/number strings, builder growth buffers (each <=256 UTF16 units), and
 * scalar/closure/reference scratch before materialization; not just the nine final strings.
 * Pseudonyms alias already charged strings.
 * Framework-owned text/layout/render/accessibility copies require separate assessment.
 */
object CompactPersonsPresentationBudget {
    val reservation = ActiveCoreReservation(
        ActiveWorkspaceBudget.Units(objects=1, references=3),
        ActiveWorkspaceBudget.Units(objects=32, references=128, strings=32, utf16Units=32*128L,
            arrays=64, arrayPayloadBytes=64*256L*2))
}
