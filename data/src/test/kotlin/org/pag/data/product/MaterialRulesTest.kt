package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class MaterialRulesTest {
    @Test fun trimsNameAndOptionalBlanksWithoutTruncation() {
        assertEquals(MaterialInput("Klebstoff"), MaterialRules.normalized(MaterialInput(" Klebstoff ", "\n", "\t")))
        assertFalse(MaterialRules.valid(MaterialInput(" \n ")))
    }
    @Test fun nameBoundaryCountsSupplementaryCodepoints() {
        assertTrue(MaterialRules.valid(MaterialInput(" 🦋".trim().repeat(120))))
        assertFalse(MaterialRules.valid(MaterialInput("🦋".repeat(121))))
    }
    @Test fun quantityIsOptionalBoundedTextWithNoNumericRule() {
        for (quantity in listOf(null, "", " ", "3", "2 Rollen", "1 Kiste", "🦋".repeat(40))) assertTrue(MaterialRules.valid(MaterialInput("x", quantity)))
        assertFalse(MaterialRules.valid(MaterialInput("x", "🦋".repeat(41))))
    }
    @Test fun placeUsesOptionalTrimmedEightyCodepoints() {
        for (place in listOf(null, " ", " ${"🦋".repeat(80)} ")) assertTrue(MaterialRules.valid(MaterialInput("x", placeLabel = place)))
        assertFalse(MaterialRules.valid(MaterialInput("x", placeLabel = "🦋".repeat(81))))
    }
    @Test fun exactReadWriteDeletePhases() {
        for (phase in ActionStatus.entries) {
            assertEquals(phase in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED), MaterialRules.mayRead(phase))
            assertEquals(phase in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE), MaterialRules.mayWrite(phase))
            assertEquals(MaterialRules.mayWrite(phase), MaterialRules.mayDelete(phase))
        }
    }
    @Test fun exactTwelveTemplatesOnlyCreateProvenanceInputs() {
        val expected = listOf("GLUE" to "Klebstoff", "DUCT_TAPE" to "Gewebeband", "SIGNS" to "Schilder / Plakate", "BANNER" to "Banner", "MARKERS" to "Marker / Stifte", "SAFETY_VESTS" to "Warnwesten", "FIRST_AID" to "Erste-Hilfe-Set", "WATER" to "Wasser", "POWER_BANKS" to "Powerbanks", "CHARGING_CABLES" to "Ladekabel", "ACTION_PHONE" to "Aktionshandy", "RAIN_PROTECTION" to "Regenschutz")
        assertEquals(expected, MaterialRules.templates.map { it.key to it.name })
        expected.forEachIndexed { n, (key, name) -> assertEquals(MaterialInput(name, sourceTemplateKey = key), MaterialRules.templates[n].input()) }
    }
    @Test fun deterministicCreationOrderNeverSortsByName() {
        fun item(id: String, name: String, time: Long) = MaterialItem(id, "a", name, null, null, null, null, null, time)
        val items = listOf(item("z", "A", 1), item("a", "Z", 1), item("old", "X", 0))
        assertEquals(listOf("old", "a", "z"), items.sortedWith(MaterialRules.order).map { it.id })
        assertEquals(items, MaterialBoard("a", items).items)
    }
    @Test fun modelHasOnlyTheNineContractFieldsAndCountProjection() {
        assertEquals(setOf("id", "actionId", "name", "quantityLabel", "placeLabel", "responsiblePersonId", "linkedTaskId", "sourceTemplateKey", "createdAt"), MaterialItem::class.java.declaredFields.map { it.name }.toSet())
        assertEquals(setOf("count"), MaterialSummary::class.java.declaredFields.map { it.name }.toSet())
    }
}
