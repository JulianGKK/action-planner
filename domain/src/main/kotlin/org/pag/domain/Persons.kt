package org.pag.domain

import java.util.Locale

data class Person(val id: String, val actionId: String, val pseudonym: String, val createdAt: Long, val editRevision: Long)

/** Access rules for the P3a basis, not a new authentication or sensitivity system. */
object PersonRules {
    fun valid(pseudonym: String) = pseudonym.trim().isNotEmpty()
    fun normalized(pseudonym: String) = pseudonym.trim().lowercase(Locale.ROOT)
    fun mayRead(status: ActionStatus) = status in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED)
    fun mayManage(status: ActionStatus) = status == ActionStatus.PREPARATION
}

enum class PersonRejection { ACTION_NOT_FOUND, NOT_FOUND, NOT_ALLOWED, INVALID_PSEUDONYM, DUPLICATE_PSEUDONYM, STALE, REVISION_EXHAUSTED, OWNER_UNCONFIRMED, BUDGET_REFUSED }
sealed interface PersonResult<out T> {
    data class Value<T>(val value: T) : PersonResult<T>
    data class Rejected(val reason: PersonRejection) : PersonResult<Nothing>
}
interface PersonRepository {
    fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit)
    fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit)
    fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit)
    /** Compare the complete editor-open snapshot inside the write transaction. Persistent editRevision is part of the original expected snapshot. */
    fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit)
}
