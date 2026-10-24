package org.pag.domain

/** Disjoint metadata for four handles plus ONE shared owner-thread scalar/formatting scratch.
 * PERSONS (nine lines), TASKS (two lines) and HUB (six summaries/three labels) are mutually exclusive routes; neither caches
 * values. FUNCTIONS headers share scratch; variable full-catalogue row formatting is
 * charged separately from existing SQL metadata via functionsFormatting(). Fixed formatted lines <=128 UTF16 units; reserve concatenation/number strings,
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
    // Four disjoint handles plus one metadata-only VM scope counter (no per-entry allocation).
    // Counter object and slot reference charged in both representations before construction.
    // Common formatting/resolution scratch is charged once, not once per route.
    val vmScopes = ActiveWorkspaceBudget.Units(objects=1, references=1)
    val reservation = ActiveCoreReservation(
        ActiveWorkspaceBudget.Units(objects=17, lists=1, listCapacity=3, references=50),
        sharedTransition.copy(objects=49, lists=1, listCapacity=3, references=178))
    /** Additional FUNCTIONS row formatting, disjoint from shared fixed/header scratch.
     * Existing role metadata bytes (all text fields) conservatively bound UTF16 name units.
     * One complete catalogue pass; name + ': ' + Long + ' vor Ort' uses <=nameUnits+32 per row.
     * Charge output/intermediate strings and geometrically grown builder buffers. No list copy.
     * Checked arithmetic, before payload/handle/format allocation. Not a physical ART bound.
     */
    fun functionsFormatting(rows:Long,roleUtf8Bytes:Long):ActiveWorkspaceBudget.Units {
        require(rows>=0 && roleUtf8Bytes>=0)
        val units=Math.addExact(roleUtf8Bytes,Math.multiplyExact(rows,32))
        return ActiveWorkspaceBudget.Units(objects=Math.multiplyExact(rows,8),
            strings=Math.multiplyExact(rows,4),utf16Units=Math.multiplyExact(units,4),
            arrays=Math.multiplyExact(rows,8),arrayPayloadBytes=Math.multiplyExact(units,16),
            references=Math.multiplyExact(rows,16))
    }

}
