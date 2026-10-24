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
            if(activity.intent.getBooleanExtra("p101Probe",false) && activity.intent.getBooleanExtra("p101Migration10",false))fixture.p101LegacyTen()
            return try {
                val faults=P81RuntimeFaults()
                val cleanupFaults=P82CleanupFaults()
                val memory=P83MemoryDiagnostics()
                val p100=P100ResourceDiagnostics()
                val observations=Observations(faults)
                val mutations=UncachedWorkspaceMutations()
                val runtime = ProductRuntime(activity, faults.worker.wrap(worker), faults.main.wrap(main), mutations,
                    fixture.runtimeAuthority(reuseEnabled = reuseEnabled,observations,faults::wrap).copy(coreReadHooks=if(activity.intent.getBooleanExtra("p100Probe",false))p100.hooks else if(profile=="default")cleanupFaults.hooks else memory.hooks), foreground)
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
