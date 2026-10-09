package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** No unscoped queries. Status and ownership are checked in the same transaction. */
class ProductPersonRepository(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val worker: Executor, private val main: Executor, private val blocked: () -> Unit) : PersonRepository {
    private val database = ActionDatabaseAccess(app.applicationContext)
    override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) =
        run(publish) { db, context -> db.withReadTransaction {
            permitted(db, actionId, context, false)?.let { return@withReadTransaction it }
            requirePersonAccess(context)
            PersonResult.Value(db.persons().list(actionId).map { it.model() })
        } }
    override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) =
        run(publish) { db, context -> db.withReadTransaction {
            permitted(db, actionId, context, false)?.let { return@withReadTransaction it }
            requirePersonAccess(context)
            PersonResult.Value(db.persons().get(actionId, personId)?.model())
        } }
    override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) =
        run(publish) { db, context -> db.changePerson(actionId, null, pseudonym, context) }
    override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) =
        run(publish) { db, context -> db.changePerson(actionId, personId, pseudonym, context) }

    private fun <T> run(publish: (T) -> Unit, work: suspend (ActionDatabase, StorageContext) -> T) {
        val context = access.acquire(AccessScope.READY) ?: return
        val keyOwner = owner() ?: run { blocked(); return }
        worker.execute {
            try {
                if (context.scope != AccessScope.READY || inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
                val result = keyOwner.withDbKey(context) { key -> database.use(inventory.database, key, context) { db -> work(db, context) } }
                main.execute { context.publish { publish(result) } }
            } catch (_: Exception) {
                main.execute { if (context.check(AccessPoint.STEP)) blocked() }
            }
        }
    }
}

private fun requirePersonAccess(context: StorageContext) {
    if (context.scope != AccessScope.READY || !context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
}
private suspend fun permitted(db: ActionDatabase, actionId: String, context: StorageContext,
    writing: Boolean): PersonResult.Rejected? {
    requirePersonAccess(context)
    val action = db.actions().get(actionId)?.model()
        ?: return PersonResult.Rejected(PersonRejection.ACTION_NOT_FOUND)
    val allowed = if (writing) PersonRules.mayManage(action.status) else PersonRules.mayRead(action.status)
    return if (allowed) null else PersonResult.Rejected(PersonRejection.NOT_ALLOWED)
}
/** Internal seam also exercised by deterministic commit-revocation tests. */
internal suspend fun ActionDatabase.changePerson(actionId: String, personId: String?, pseudonym: String,
    context: StorageContext): PersonResult<Person> = withWriteTransaction {
    permitted(this@changePerson, actionId, context, true)?.let { return@withWriteTransaction it }
    if (!PersonRules.valid(pseudonym)) return@withWriteTransaction PersonResult.Rejected(PersonRejection.INVALID_PSEUDONYM)
    requirePersonAccess(context)
    val existing = if (personId == null) null else persons().get(actionId, personId)?.also { it.model() }
        ?: return@withWriteTransaction PersonResult.Rejected(PersonRejection.NOT_FOUND)
    val normalized = PersonRules.normalized(pseudonym)
    requirePersonAccess(context)
    val duplicate = persons().samePseudonym(actionId, normalized)?.also { it.model() }
    if (duplicate != null && duplicate.id != existing?.id)
        return@withWriteTransaction PersonResult.Rejected(PersonRejection.DUPLICATE_PSEUDONYM)
    val row = existing?.copy(pseudonym = pseudonym, pseudonymNormalized = normalized)
        ?: PersonRow(UUID.randomUUID().toString(), actionId, pseudonym, normalized, System.currentTimeMillis())
    requirePersonAccess(context)
    if (existing == null) persons().insert(row)
    else if (persons().rename(actionId, personId!!, pseudonym, normalized) != 1) throw ProductStorageFailure()
    if (!context.check(AccessPoint.COMMIT)) throw StorageAccessRevoked()
    PersonResult.Value(row.model())
}
