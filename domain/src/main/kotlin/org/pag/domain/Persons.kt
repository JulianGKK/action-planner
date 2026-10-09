package org.pag.domain

import java.util.Locale

data class Person(val id: String, val actionId: String, val pseudonym: String, val createdAt: Long)

/** Access rules for the P3a basis, not a new authentication or sensitivity system. */
object PersonRules {
    fun valid(pseudonym: String) = pseudonym.trim().isNotEmpty()
    fun normalized(pseudonym: String) = pseudonym.trim().lowercase(Locale.ROOT)
    fun mayRead(status: ActionStatus) = status in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED)
    fun mayManage(status: ActionStatus) = status == ActionStatus.PREPARATION
}

enum class PersonRejection { ACTION_NOT_FOUND, NOT_FOUND, NOT_ALLOWED, INVALID_PSEUDONYM, DUPLICATE_PSEUDONYM }
sealed interface PersonResult<out T> {
    data class Value<T>(val value: T) : PersonResult<T>
    data class Rejected(val reason: PersonRejection) : PersonResult<Nothing>
}
interface PersonRepository {
    fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit)
    fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit)
    fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit)
    fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit)
}
