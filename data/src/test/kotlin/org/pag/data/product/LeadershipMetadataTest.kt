package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** The exact shared production classifier; deliberately no crypto or person lookup. */
class LeadershipMetadataTest {
    private val root = LeadershipRow("synthetic-action", "READY", "synthetic-epoch",
        1, "Argon2id", 19, 65536, 3, 1, ByteArray(16), ByteArray(88))
    private val area = SensitiveRow(root.actionId, "synthetic-person", "IDENTITY",
        1, 1, ByteArray(60), ByteArray(33))
    private fun state(row: LeadershipRow? = root, rows: List<SensitiveRow> = emptyList(),
        present: Boolean = true, usable: Boolean = true) =
        LeadershipMetadata.classification(row, rows, present, usable)

    @Test fun noRootNoAreasNoAliasIsUninitialized() {
        assertEquals(SensitiveState.UNINITIALIZED, state(null, present = false, usable = false))
    }
    @Test fun validRootWithoutPersonOrAreaIsReady() {
        assertEquals(SensitiveState.READY, state())
    }
    @Test fun orphanAliasOrAreaIsPartial() {
        assertEquals(SensitiveState.PARTIAL_OR_INVALID, state(null))
        assertEquals(SensitiveState.PARTIAL_OR_INVALID, state(null, listOf(area), false, false))
    }
    @Test fun missingOrUnusableDeviceKeyIsPartial() {
        assertEquals(SensitiveState.PARTIAL_OR_INVALID, state(present = false))
        assertEquals(SensitiveState.PARTIAL_OR_INVALID, state(usable = false))
    }
    @Test fun invalidRootMetadataRemainsPartial() {
        listOf(root.copy(state = "RESERVED"), root.copy(state = "DELETE_PENDING"),
            root.copy(epoch = ""), root.copy(cryptoVersion = 2), root.copy(salt = ByteArray(15)),
            root.copy(envelope = ByteArray(87)), root.copy(memoryKiB = 1)).forEach {
            assertEquals(SensitiveState.PARTIAL_OR_INVALID, state(it))
        }
    }
    @Test fun everyAreaMustBeStructurallyValidForActionReady() {
        assertEquals(SensitiveState.READY, state(rows = listOf(area)))
        listOf(area.copy(actionId = "synthetic-other-action"), area.copy(personId = ""),
            area.copy(area = "UNKNOWN"), area.copy(cryptoVersion = 2), area.copy(revision = 0),
            area.copy(keyEnvelope = ByteArray(59)), area.copy(payload = ByteArray(32))).forEach {
            assertEquals(SensitiveState.PARTIAL_OR_INVALID, state(rows = listOf(area, it)))
        }
    }
    @Test fun unsupportedProviderDoesNotPretendUninitialized() {
        val provider = object : LeadershipRepository {
            override fun inventory(actionId:String,personId:String,publish:(SensitiveResult<SensitiveInventory>)->Unit) = error("unused")
            override fun setup(actionId:String,passphrase:CharArray,confirmation:CharArray,publish:(SensitiveResult<Unit>)->Unit) = error("unused")
            override fun open(actionId:String,personId:String,area:SensitiveArea,passphrase:CharArray,publish:(SensitiveResult<SensitiveOpened>)->Unit) = error("unused")
            override fun save(grant:SensitiveGrant,data:SensitiveData,publish:(SensitiveResult<Unit>)->Unit) = error("unused")
            override fun deleteArea(grant:SensitiveGrant,publish:(SensitiveResult<Unit>)->Unit) = error("unused")
            override fun deletePerson(actionId:String,personId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit) = error("unused")
            override fun deleteAction(actionId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit) = error("unused")
            override fun revoke() = Unit
        }
        var result: SensitiveResult<SensitiveState>? = null
        provider.actionState(root.actionId) { result = it }
        assertEquals(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE), result)
    }
}
