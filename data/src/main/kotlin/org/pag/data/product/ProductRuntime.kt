package org.pag.data.product

import android.app.Activity
import android.util.Log
import android.os.SystemClock
import androidx.room3.withWriteTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Activity-owned composition. No global key/database registry, no retained session. */
class ProductRuntime private constructor(activity: Activity, private val worker: Executor, private val main: Executor,
    private val workspaceMutations: WorkspaceMutationInvalidation,
    private val testAuthority: ProductRuntimeTestAuthority?, foreground: () -> Boolean, @Suppress("UNUSED_PARAMETER") marker: Unit) {
    constructor(activity: Activity, worker: Executor, main: Executor,
        workspaceMutations: WorkspaceMutationInvalidation = UncachedWorkspaceMutations(), foreground: () -> Boolean) :
        this(activity, worker, main, workspaceMutations, null, foreground, Unit)

    internal constructor(activity: Activity, worker: Executor, main: Executor,
        workspaceMutations: WorkspaceMutationInvalidation, testAuthority: ProductRuntimeTestAuthority,
        foreground: () -> Boolean) : this(activity, worker, main, workspaceMutations,
        requireP77TestAuthority(activity.packageName, activity.applicationInfo.flags, testAuthority), foreground, Unit)
    private val noBackupRoot = testAuthority?.noBackupRoot ?: productNoBackupRoot(activity.noBackupFilesDir)
    private val root = File(noBackupRoot, "pag-db-v1")
    private val envelope = ProductDbEnvelope(root, "org.pag.product.database.rsa.v1")
    val inventory = ProductInventory(root, testAuthority?.aliasPresent ?: envelope::aliasPresent,
        testAuthority?.envelopeValid ?: envelope::structurallyValid)
    private val owner = AtomicReference<SessionDbKeyOwner?>()
    val authentication = testAuthority?.authentication ?: ProductCredentialAuthentication(activity, envelope, worker, foreground,
        inventory::reserve, { inventory.state() == ProductStorageState.READY_TO_OPEN })
    private var initializing = false // Explicit request mode, never an authorization bit.
    val session = SessionCoordinator(authentication, Maintenance { Subscription {} },
        MonotonicTime { SystemClock.elapsedRealtime() }, ProductSessionTiming.policy, foreground,
        authorizedMaintenance = { context, operation, done ->
            // Maintenance/migration/file recovery have no READY writer ticket. End the original
            // read authority before their worker can perform any side effect; Session owns rebinding.
            workspaceMutations.revokeActiveRead()
            val cancelled = AtomicBoolean(false)
            worker.execute {
                val probe = MaintenanceProbe(maintenanceProbeEnabled(activity.applicationInfo.flags)) {
                    Log.w(MaintenanceProbe.TAG, it)
                }
                var failureCode = MaintenanceFailureCode.KEY_OPERATION_INCOMPLETE
                var success = false
                try {
                    var maintenanceError:Exception?=null
                    val result = operation.useUnwrappedKey({ !cancelled.get() && context.check(AccessPoint.STEP) }) { key ->
                        try {
                            reconcileProductDocuments(activity.applicationContext,inventory,noBackupRoot,context,key,initializing,probe::enter) {
                                probe.enter(MaintenancePhase.M07_READY_OWNER)
                                if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
                                val next=SessionDbKeyOwner.bind(context,key)
                                if (!context.publish { owner.getAndSet(next)?.invalidate() }) {
                                    next.invalidate();throw ProductStorageFailure()
                                }
                            }
                        } catch(error:Exception) { maintenanceError=error;throw error }
                    }
                    // The envelope reports FAILED for consumer exceptions. Preserve their actual
                    // type: unexpected Room faults must never become a fabricated session revoke.
                    maintenanceError?.let { throw it }
                    success = result == OperationResult.SUCCEEDED && context.check(AccessPoint.STEP)
                    if (!success && result == OperationResult.SUCCEEDED) failureCode = MaintenanceFailureCode.SESSION_REVOKED
                } catch (error: StorageAccessRevoked) { success = false; failureCode = maintenanceFailureCode(error) }
                  catch (error: Exception) { success = false; failureCode = maintenanceFailureCode(error) } // valid MAINTENANCE + failure -> existing BLOCKED
                finally {
                    if (!success) probe.failed(failureCode)
                    operation.release(); if (!success) owner.getAndSet(null)?.invalidate()
                }
                main.execute { if (!cancelled.get()) done(success) }
            }
            Subscription { cancelled.set(true); owner.getAndSet(null)?.invalidate() }
        })
    private val keyLifetime = session.observe { state ->
        if (state.state in setOf(SessionState.LOCKED, SessionState.BLOCKED)) {
            workspaceMutations.revokeActiveRead()
            owner.getAndSet(null)?.invalidate()
        }
    }
    fun start(initialize: Boolean) {
        val expected = if (initialize) ProductStorageState.EMPTY else ProductStorageState.READY_TO_OPEN
        if (inventory.state() != expected) { session.storageFailure(); return }
        initializing = initialize
        session.authenticate(if (initialize) Experiment.STORAGE_INITIALIZE else Experiment.STORAGE_UNWRAP)
    }
    fun authenticationForegroundAvailable() { (authentication as? ProductCredentialAuthentication)?.foregroundAvailable() }
    fun close() { session.lock(); keyLifetime.cancel(); leadershipLifetime.cancel(); leadership.revoke();
        workspaceMutations.revokeActiveRead(); activeCore.close(); owner.getAndSet(null)?.invalidate() }
    private val workspaceMemory=testAuthority?.workspaceMemory ?: OutstandingCoreRead.product
    val actions: ActionRepository = ProductActionRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, workspaceMutations, session::storageFailure)
    val workspace:WorkspaceRepository=ProductWorkspaceRepository(activity.applicationContext,session,inventory,
        {owner.get()},worker,main,session::storageFailure,workspaceMemory,testAuthority?.workspaceMaterializing,workspaceMutations,testAuthority?.workspaceReservation)
    val persons: PersonRepository = ProductPersonRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, workspaceMutations, session::storageFailure).also{it.bindWorkspaceMemory(workspaceMemory)}
    val tasks: TaskRepository = ProductTaskRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure, mutations = workspaceMutations).also{it.bindWorkspaceMemory(workspaceMemory)}
    val protocol: ProtocolRepository = ProductProtocolRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure, mutations = workspaceMutations).also{it.bindWorkspaceMemory(workspaceMemory)}
    val material: MaterialRepository = ProductMaterialRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure, mutations = workspaceMutations).also{it.bindWorkspaceMemory(workspaceMemory)}
    val memoryReports: MemoryReportRepository = ProductMemoryReportRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure, mutations = workspaceMutations).also{it.bindWorkspaceMemory(workspaceMemory)}
    val expenses: ExpenseRepository = ProductExpenseRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure, mutations = workspaceMutations).also{it.bindWorkspaceMemory(workspaceMemory)}
    val documents: DocumentRepository = ProductDocumentRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, mutations = workspaceMutations, blocked = session::storageFailure)
    val operations: OperationsRepository = ProductOperationsRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, session::storageFailure, mutations = workspaceMutations).also{it.bindWorkspaceMemory(workspaceMemory)}
    val leadership: LeadershipRepository = ProductLeadershipRepository(activity.applicationContext, session, inventory,
        { owner.get() }, worker, main, MonotonicTime { SystemClock.elapsedRealtime() }, mutations=workspaceMutations, blocked=session::storageFailure)
    private val leadershipLifetime = session.observe { state ->
        if (state.state != SessionState.READY) leadership.revoke()
    }
    /** Bounded P3b-free core read through the same SessionCoordinator and writer/ticket owner.
     * This is a transient candidate path only; ProductViewModel retention/RAM hits remain off.
     */
    private val activeCoreReader = ProductActiveCoreReader(activity.applicationContext,session,inventory,
        {owner.get()},blocked=session::storageFailure,testHooks=testAuthority?.coreReadHooks)
    val activeCore = ProductActiveCoreLoader(testAuthority?.coreAccess?.invoke(session) ?: session,workspaceMutations,worker,main,{c,t,l,m->activeCoreReader.readForTicket(c,t,l,outstanding=m)},
        reuseEnabled = testAuthority?.reuseEnabled == true, observation = testAuthority?.observation,outstanding=workspaceMemory)
}

/** Test-only synthetic credentials and storage binding. Construction is guarded above by an exact
 * isolated application ID and debuggable package. Normal ProductRuntime always uses the device
 * credential CryptoObject and its product envelope.
 */
internal data class ProductRuntimeTestAuthority(
    val noBackupRoot: File,
    val authentication: org.pag.security.AuthenticationExperiment,
    val aliasPresent: () -> Boolean,
    val envelopeValid: () -> Boolean,
    val reuseEnabled: Boolean = false,
    val observation: ActiveCoreLoadObservation? = null,
    val coreAccess: ((StorageAccess) -> StorageAccess)? = null,
    val coreReadHooks: CoreReadTestHooks? = null,
    val workspaceMemory:OutstandingCoreRead?=null,
    val workspaceMaterializing:(()->Unit)?=null,
    val workspaceReservation:((ActiveCoreReservation)->ActiveCoreReservation)?=null
)

/** Runs as a secondary-constructor argument, before the primary constructor initializes resources. */
internal fun requireP77TestAuthority(packageName: String, applicationFlags: Int,
    authority: ProductRuntimeTestAuthority): ProductRuntimeTestAuthority {
    check(packageName == "org.pag.prototype.a.p77runtime" &&
        applicationFlags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        "Synthetic Runtime authority is restricted to the isolated P77 debug package"
    }
    return authority
}
