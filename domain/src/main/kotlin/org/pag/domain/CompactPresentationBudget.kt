package org.pag.domain

/** Disjoint metadata for BOTH handles plus ONE shared owner-thread scalar/formatting scratch.
 * PERSONS (nine lines) and TASKS (two lines) are mutually exclusive routes; neither caches
 * values. Final formatted lines <=128 UTF16 units; reserve concatenation/number strings,
 * builder growth buffers <=256 units, closures/boxes/references, not only final strings.
 * Pseudonyms/titles alias the already charged core. Framework copies are assessed separately.
 */
object CompactPresentationBudget {
    val handle = ActiveWorkspaceBudget.Units(objects=1, references=3)
    val sharedTransition = ActiveWorkspaceBudget.Units(objects=32, references=128,
        strings=32, utf16Units=32*128L, arrays=64, arrayPayloadBytes=64*256L*2)
    // Two disjoint retained handles; both constructions also count in transition.
    // Common formatting/resolution scratch is charged once, not once per route.
    val reservation = ActiveCoreReservation(
        ActiveWorkspaceBudget.Units(objects=2, references=6),
        sharedTransition.copy(objects=34, references=134))
}
