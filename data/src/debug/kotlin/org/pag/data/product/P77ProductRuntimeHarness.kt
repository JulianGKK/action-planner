package org.pag.data.product

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.Context
import org.pag.domain.WorkspaceMutationInvalidation
import org.pag.domain.UncachedWorkspaceMutations
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Isolated Android instrumentation entry point. It creates synthetic encrypted data inside
 * this target package's no-backup directory, then delegates the live path to ProductRuntime.
 * No method in normal/release source can construct this authority.
 */
class P77ProductRuntimeHarness private constructor(
    val runtime: ProductRuntime,
    private val fixture: P11SyntheticFixture,
    val observations: Observations,
    val mutations:UncachedWorkspaceMutations,
    val faults:P81RuntimeFaults,
    val cleanupFaults:P82CleanupFaults,
    val memory:P83MemoryDiagnostics,
    val p100:P100ResourceDiagnostics,
    val memoryFixture:String
) : AutoCloseable {
    class Observations internal constructor(private val faults:P81RuntimeFaults) : ActiveCoreLoadObservation {
        val tickets = AtomicInteger()
        val reads = AtomicInteger()
        val workspaceMaterializations=AtomicInteger()
        // Explicit TEST-only real scratch allocation, planned in the original receipt.
        var p107PaddingBytes=0
        var p107Padding:ByteArray?=null
        var p107Retained=0L;var p107Transition=0L
        fun p107Plan(base:org.pag.domain.ActiveCoreReservation,mode:String):org.pag.domain.ActiveCoreReservation {
            check(mode in setOf("r-exact","r-over","t-exact","t-over"))
            val retained=mode.startsWith("r-")
            val units=if(retained)base.retained else base.transition
            val cost=org.pag.domain.ActiveWorkspaceBudget.estimateUnits(units).second
            val target=(if(retained)org.pag.domain.ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES else org.pag.domain.ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)+(if(mode.endsWith("over"))1 else 0)
            p107PaddingBytes=Math.subtractExact(Math.subtractExact(target,cost),71).toInt();check(p107PaddingBytes>=0)
            val padded=CoreReadAdmission.add(units,org.pag.domain.ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=p107PaddingBytes.toLong()))
            val result=if(retained)base.copy(retained=padded) else base.copy(transition=padded)
            p107Retained=org.pag.domain.ActiveWorkspaceBudget.estimateUnits(result.retained).second
            p107Transition=org.pag.domain.ActiveWorkspaceBudget.estimateUnits(result.transition).second
            return result
        }
        val hitAttempts = AtomicInteger()
        val hitAccepted = AtomicInteger()
        val hitNanos=java.util.Collections.synchronizedList(mutableListOf<Long>())
        private val originalTicket = AtomicReference<WorkspaceMutationInvalidation.ActiveReadTicket?>()
        override fun ticketIssued(ticket:WorkspaceMutationInvalidation.ActiveReadTicket) {
            tickets.incrementAndGet();originalTicket.compareAndSet(null,ticket)
        }
        override fun readerStarted() { reads.incrementAndGet();if(faults.holdNextCoreMain.getAndSet(false))faults.main.holdNext() }
        override fun hitAuthorized(accepted:Boolean,elapsedNanos:Long) { hitAttempts.incrementAndGet();hitNanos.add(elapsedNanos);if(accepted)hitAccepted.incrementAndGet() }
        fun originalTicketLive()=originalTicket.get()?.isLive()==true
        fun hasSingleOriginalTicket() = tickets.get()==1 && originalTicket.get()?.isLive()==true
    }
    fun p104PrepareLegacyWhileLocked(){
        check(runtime.session.snapshot().state==org.pag.domain.SessionState.LOCKED)
        fixture.p104LegacyEleven()
    }
    fun p104AbortMigration():String {
        check(runtime.session.snapshot().state==org.pag.domain.SessionState.LOCKED)
        return fixture.p104AbortMigration().also{check(runtime.session.snapshot().state==org.pag.domain.SessionState.LOCKED)}
    }
    fun p104NativeRevisions()=fixture.p104NativeRevisions()
    fun p104VerifyMigration()=fixture.p104VerifyMigration()
    fun p106StoreWithoutEntry()=fixture.p106StoreWithoutEntry(runtime)
    fun p106Replacement(activity:Activity,worker:Executor,main:Executor)=fixture.p106Replacement(activity,worker,main)
    fun p101SeededVersion()=fixture.p101SeededVersion
    fun outstandingState()=runtime.activeCore.outstandingState().toString()
    val cleanupConfirmed=java.util.concurrent.atomic.AtomicBoolean(false)
    override fun close() {
        memory.release()
        faults.clear()
        runtime.close()
        fixture.close()
        cleanupConfirmed.set(true)
    }

    companion object {
        fun create(activity: Activity, worker: Executor, main: Executor, foreground: () -> Boolean, reuseEnabled:Boolean=true): P77ProductRuntimeHarness {
            check(activity.packageName == "org.pag.prototype.a.p77runtime")
            check(activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            val parent=activity.noBackupFilesDir.canonicalFile
            check(parent==parent.canonicalFile)
            parent.listFiles()?.filter { it.name.startsWith("p11-fixture-") }?.forEach { stale ->
                check(!java.nio.file.Files.isSymbolicLink(stale.toPath()) && stale.canonicalFile.parentFile==parent)
                P11FixtureRoot(parent.toPath(),stale.toPath()).close()
            }
            val base = File(parent, "p11-fixture-${UUID.randomUUID()}")
            val profile=activity.intent.getStringExtra("p83Fixture") ?: "default"
            check(profile in setOf("default","small","medium","large","roles32","roles100","stress","p86-small","p86-stress32","p86-stress100","p87-4999","p87-5000","p87-5001"))
            val people=when(profile){"small","p86-small","p87-4999","p87-5000","p87-5001"->20;"medium"->100;else->200}
            val tasks=when(profile){"small","p86-small","p87-4999","p87-5000","p87-5001"->40;"medium"->250;else->1000}
            val fixture = P11SyntheticFixture(activity, worker, main,
                taskCountPerAction = tasks, personCountPerAction = people, baseDirectory = base)
            if(profile!="default")fixture.seedMemoryAcceptance(profile,
                budgetRefusal=activity.intent.getBooleanExtra("p100Probe",false) && activity.intent.getBooleanExtra("p100BudgetRefused",false))
            if(activity.intent.getBooleanExtra("p104Migration11",false))fixture.p104LegacyEleven()
            if(activity.intent.getBooleanExtra("p101Probe",false) && activity.intent.getBooleanExtra("p101Migration10",false))fixture.p101LegacyTen()
            return try {
                val faults=P81RuntimeFaults()
                val cleanupFaults=P82CleanupFaults()
                val memory=P83MemoryDiagnostics()
                val p100=P100ResourceDiagnostics()
                val observations=Observations(faults)
                val mutations=UncachedWorkspaceMutations()
                val runtime = ProductRuntime(activity, faults.worker.wrap(worker), faults.main.wrap(main), mutations,
                    fixture.runtimeAuthority(reuseEnabled = reuseEnabled,observations,faults::wrap).copy(workspaceMaterializing={observations.workspaceMaterializations.incrementAndGet();if(observations.p107PaddingBytes>0)observations.p107Padding=ByteArray(observations.p107PaddingBytes)},workspaceReservation=activity.intent.getStringExtra("p107Budget")?.let{mode->{base->observations.p107Plan(base,mode)}},coreReadHooks=if(activity.intent.getBooleanExtra("p100Probe",false))p100.hooks else if(profile=="default")cleanupFaults.hooks else memory.hooks), foreground)
                if(activity.intent.getBooleanExtra("p101Probe",false)) {
                    // Align the existing document repository with this already-authorized synthetic
                    // runtime root. Normal product uses the same platform root for both already.
                    runtime.documents.javaClass.getDeclaredField("noBackupRoot").let{it.isAccessible=true;it.set(runtime.documents,base)}
                }
                p100.bindDatabase(runtime.inventory.database)
                P77ProductRuntimeHarness(runtime, fixture,observations,mutations,faults,cleanupFaults,memory,p100,profile)
            } catch (error: Throwable) {
                fixture.close()
                throw error
            }
        }
    }
}
