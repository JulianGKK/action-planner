package org.pag.domain

import java.time.LocalDate

enum class SensitiveArea { IDENTITY, CONTACT, EMERGENCY_CONTACT }
enum class SensitiveState { UNINITIALIZED, READY, PARTIAL_OR_INVALID, INCOMPLETE_SETUP, DELETE_PENDING }
data class SensitiveInventory(val state: SensitiveState, val areas: Set<SensitiveArea> = emptySet())
sealed interface SensitiveData {
    data class Identity(val fullName: String? = null, val birthDate: LocalDate? = null) : SensitiveData
    data class Contact(val phone: String? = null) : SensitiveData
    data class EmergencyContact(val name: String? = null, val phone: String? = null, val relationship: String? = null) : SensitiveData
}
object LeadershipRules {
    fun codePointCount(chars:CharSequence):Int = Character.codePointCount(chars,0,chars.length)
    fun validPassphrase(chars: CharArray): Boolean = codePointCount(java.nio.CharBuffer.wrap(chars)) in 16..128
    fun mayRead(status: ActionStatus) = PersonRules.mayRead(status)
    fun mayWrite(status: ActionStatus) = PersonRules.mayManage(status)
    fun area(data: SensitiveData) = when(data) {
        is SensitiveData.Identity -> SensitiveArea.IDENTITY
        is SensitiveData.Contact -> SensitiveArea.CONTACT
        is SensitiveData.EmergencyContact -> SensitiveArea.EMERGENCY_CONTACT
    }
}
/** An opaque, revocable capability, not key material or a second session. */
interface SensitiveGrant {
    val actionId: String
    val personId: String
    val area: SensitiveArea
    fun valid(): Boolean
    fun cancel()
}
enum class SensitiveFailure { DENIED, INVALID_PASSPHRASE, UNAVAILABLE, CONFLICT }
sealed interface SensitiveResult<out T> {
    data class Value<T>(val value: T) : SensitiveResult<T>
    data class Rejected(val reason: SensitiveFailure) : SensitiveResult<Nothing>
}
data class SensitiveOpened(val grant: SensitiveGrant, val data: SensitiveData)
/** Single-use command intent, not SessionAccess, a key or an authorization grant.
 * Preparation precedes a fresh normal DeviceCredential session; execution additionally
 * requires explicit confirmation. Unsupported providers deny both operations. */
interface LeadershipRecoveryRequest {
    val actionId: String
    val state: SensitiveState
    fun cancel()
}
interface LeadershipRepository {
    fun prepareRecovery(actionId:String,publish:(SensitiveResult<LeadershipRecoveryRequest>)->Unit) {
        publish(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE))
    }
    fun recover(request:LeadershipRecoveryRequest,publish:(SensitiveResult<Unit>)->Unit) {
        request.cancel();publish(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE))
    }

    /** Protected action metadata only: no person, payload or grant. Unsupported providers deny. */
    fun actionState(actionId: String, publish: (SensitiveResult<SensitiveState>) -> Unit) {
        publish(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE))
    }
    fun workspaceInventory(actionId:String,personId:String,access:WorkspaceAccess,publish:(SensitiveResult<SensitiveInventory>)->Unit) {
        publish(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE))
    }
    fun inventory(actionId: String, personId: String, publish: (SensitiveResult<SensitiveInventory>) -> Unit)
    /** Consumers take ownership of arrays and wipe them, including on refusal. */
    fun setup(actionId: String, passphrase: CharArray, confirmation: CharArray, publish: (SensitiveResult<Unit>) -> Unit)
    fun open(actionId: String, personId: String, area: SensitiveArea, passphrase: CharArray, publish: (SensitiveResult<SensitiveOpened>) -> Unit)
    fun save(grant: SensitiveGrant, data: SensitiveData, publish: (SensitiveResult<Unit>) -> Unit)
    fun deleteArea(grant: SensitiveGrant, publish: (SensitiveResult<Unit>) -> Unit)
    fun deletePerson(actionId: String, personId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit)
    fun deleteAction(actionId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit)
    fun revoke()
}
