package org.pag.data.product

import android.app.Activity
import android.os.SystemClock
import androidx.room3.withWriteTransaction
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Activity-owned composition. No global key/database registry, no retained session. */
class ProductRuntime(activity: Activity, private val worker: Executor, private val main: Executor,
    foreground: () -> Boolean) {
    private val root = File(activity.noBackupFilesDir.canonicalFile, "pag-db-v1")
    private val envelope = ProductDbEnvelope(root, "org.pag.product.database.rsa.v1")
    val inventory = ProductInventory(root, envelope::aliasPresent, envelope::structurallyValid)
    private val database = ActionDatabaseAccess(activity.applicationContext)
    private val owner = AtomicReference<SessionDbKeyOwner?>()
    val authentication = ProductCredentialAuthentication(activity, envelope, worker, foreground,
        inventory::reserve, { inventory.state() == ProductStorageState.READY_TO_OPEN })
    private var initializing = false // Explicit request mode, never an authorization bit.
    val session = SessionCoordinator(authentication, Maintenance { Subscription {} },
        MonotonicTime { SystemClock.elapsedRealtime() }, ProductSessionTiming.policy, foreground,
        authorizedMaintenance = { context, operation, done ->
            val cancelled = AtomicBoolean(false)
            worker.execute {
                var success = false
                try {
                    val result = operation.useUnwrappedKey({ !cancelled.get() && context.check(AccessPoint.STEP) }) { key ->
                        if (initializing) inventory.prepareDatabaseDirectory()
                        else if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
                        database.use(inventory.database, key, context, initializing) { db ->
                            // No product content publication during MAINTENANCE.
                            db.actions().list().forEach { it.model() }
                        }
                        if (initializing) {
                            database.use(inventory.database, key, context) { db -> db.actions().list().forEach { it.model() } }
                            inventory.finish(context)
                        }
                        if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
                        val next = SessionDbKeyOwner.bind(context, key)
                        if (!context.publish { owner.getAndSet(next)?.invalidate() }) {
                            next.invalidate(); throw ProductStorageFailure()
                        }
                    }
                    success = result == OperationResult.SUCCEEDED && context.check(AccessPoint.STEP)
                } catch (_: Exception) { success = false }
                finally { operation.release(); if (!success) owner.getAndSet(null)?.invalidate() }
                main.execute { if (!cancelled.get()) done(success) }
            }
            Subscription { cancelled.set(true); owner.getAndSet(null)?.invalidate() }
        })
    private val keyLifetime = session.observe { state ->
        if (state.state in setOf(SessionState.LOCKED, SessionState.BLOCKED)) owner.getAndSet(null)?.invalidate()
    }
    fun start(initialize: Boolean) {
        val expected = if (initialize) ProductStorageState.EMPTY else ProductStorageState.READY_TO_OPEN
        if (inventory.state() != expected) { session.storageFailure(); return }
        initializing = initialize
        session.authenticate(if (initialize) Experiment.STORAGE_INITIALIZE else Experiment.STORAGE_UNWRAP)
    }
    fun close() { session.lock(); keyLifetime.cancel(); leadershipLifetime.cancel(); leadership.revoke(); owner.getAndSet(null)?.invalidate() }
    val actions: ActionRepository = ProductActionRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure)
    val persons: PersonRepository = ProductPersonRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure)
    val tasks: TaskRepository = ProductTaskRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure)
    val protocol: ProtocolRepository = ProductProtocolRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure)
    val material: MaterialRepository = ProductMaterialRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure)
    val operations: OperationsRepository = ProductOperationsRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure)
    val leadership: LeadershipRepository = ProductLeadershipRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, MonotonicTime { SystemClock.elapsedRealtime() })
    private val leadershipLifetime = session.observe { state ->
        if (state.state != SessionState.READY) leadership.revoke()
    }
}
