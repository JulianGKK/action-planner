package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.room3.useWriterConnection
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** No unscoped queries. Status and ownership are checked in the same transaction. */
class ProductPersonRepository(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val worker: Executor, private val main: Executor, mutations:WorkspaceMutationInvalidation,
    private val blocked: () -> Unit) : PersonRepository {
    constructor(app:Context,access:StorageAccess,inventory:ProductInventory,owner:()->SessionDbKeyOwner?,
        worker:Executor,main:Executor,blocked:()->Unit):this(app,access,inventory,owner,worker,main,UncachedWorkspaceMutations(),blocked)
    private val mutationBoundary=WorkspaceWriteBoundary(access,worker,main,mutations,blocked)
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
        write(actionId,null,pseudonym,publish)
    override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) =
        write(actionId,expected,pseudonym,publish)

    private fun write(actionId:String,expected:Person?,pseudonym:String,publish:(PersonResult<Person>)->Unit) =
        mutationBoundary.submit<PersonResult<Person>>(WorkspaceMutation(WorkspaceWriterFamily.PERSON,actionId),publish,
            {PersonResult.Rejected(PersonRejection.NOT_ALLOWED)},{it is PersonResult.Value}) { c,p ->
            if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
            (owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c){db->
                db.useWriterConnection { tx -> confirmedWorkspaceTransaction(tx,c,p,{it is PersonResult.Rejected}) {
                    PersonWriterStore(RoomCoreWriterData(db),c).change(actionId,expected,pseudonym)
                } }
            }}
        }
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
    writing: Boolean): PersonResult.Rejected? = permitted(RoomCoreWriterData(db),actionId,context,writing)
private suspend fun permitted(data:CoreWriterData,actionId:String,context:StorageContext,
    writing:Boolean):PersonResult.Rejected? {
    requirePersonAccess(context)
    val action = data.actions.get(actionId)?.model()
        ?: return PersonResult.Rejected(PersonRejection.ACTION_NOT_FOUND)
    val allowed = if (writing) PersonRules.mayManage(action.status) else PersonRules.mayRead(action.status)
    return if (allowed) null else PersonResult.Rejected(PersonRejection.NOT_ALLOWED)
}
/** Existing storage proofs keep their transaction wrapper. Product boundary owns the outer T. */
internal suspend fun ActionDatabase.changePerson(actionId:String,expected:Person?,pseudonym:String,context:StorageContext):PersonResult<Person> =
    withWriteTransaction { PersonWriterStore(RoomCoreWriterData(this@changePerson),context).change(actionId,expected,pseudonym) }
internal class PersonWriterStore(private val data:CoreWriterData,private val context:StorageContext,
    private val now:()->Long=System::currentTimeMillis) {
    suspend fun change(actionId:String,expected:Person?,pseudonym:String):PersonResult<Person> {
        permitted(data, actionId, context, true)?.let { return it }
        if (!PersonRules.valid(pseudonym)) return PersonResult.Rejected(PersonRejection.INVALID_PSEUDONYM)
        requirePersonAccess(context)
        val existing = if (expected == null) null else {
            if (expected.actionId != actionId) return PersonResult.Rejected(PersonRejection.STALE)
            val row = data.persons.get(actionId, expected.id)
                ?: return PersonResult.Rejected(PersonRejection.NOT_FOUND)
            // Same outer IMMEDIATE transaction as UPDATE; includes every domain identity field.
            if (row.model() != expected) return PersonResult.Rejected(PersonRejection.STALE)
            row
        }
        val normalized = PersonRules.normalized(pseudonym)
        requirePersonAccess(context)
        val duplicate = data.persons.samePseudonym(actionId, normalized)?.also { it.model() }
        if (duplicate != null && duplicate.id != existing?.id)
            return PersonResult.Rejected(PersonRejection.DUPLICATE_PSEUDONYM)
        val row = existing?.copy(pseudonym = pseudonym, pseudonymNormalized = normalized)
            ?: PersonRow(UUID.randomUUID().toString(), actionId, pseudonym, normalized, now())
        requirePersonAccess(context)
        if (existing == null) data.persons.insert(row)
        else if (data.persons.rename(actionId, existing.id, pseudonym, normalized) != 1) throw ProductStorageFailure()
        if (!context.check(AccessPoint.COMMIT)) throw StorageAccessRevoked()
        return PersonResult.Value(row.model())
    }
}
