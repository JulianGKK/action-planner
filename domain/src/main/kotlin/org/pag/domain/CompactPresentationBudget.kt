package org.pag.domain

/** Disjoint metadata for three handles plus ONE shared owner-thread scalar/formatting scratch.
 * PERSONS (nine lines), TASKS (two lines) and HUB (six summaries/three labels) are mutually exclusive routes; neither caches
 * values. Final formatted lines <=128 UTF16 units; reserve concatenation/number strings,
 * builder growth buffers <=256 units, closures/boxes/references, not only final strings.
 * Pseudonyms/titles alias the already charged core. Framework copies are assessed separately.
 */
object CompactPresentationBudget {
    val handle = ActiveWorkspaceBudget.Units(objects=1, references=3)
    val sharedTransition = ActiveWorkspaceBudget.Units(objects=32, references=128,
        strings=32, utf16Units=32*128L, arrays=64, arrayPayloadBytes=64*256L*2)
    // HUB: handle + three metadata HubCards + eight original click closures + empty-list
    // allowance, one three-entry card list/backing capacity and conservative reference slack.
    // No resolved summaries in cards and no chunked/second card list. Both handle shapes
    // count in retained AND construction transition; common scratch counts only once.
    val hub = ActiveWorkspaceBudget.Units(objects=13, lists=1, listCapacity=3, references=40)
    // Three disjoint handles; all constructions also count in transition.
    // Common formatting/resolution scratch is charged once, not once per route.
    val reservation = ActiveCoreReservation(
        ActiveWorkspaceBudget.Units(objects=15, lists=1, listCapacity=3, references=46),
        sharedTransition.copy(objects=47, lists=1, listCapacity=3, references=174))
}
