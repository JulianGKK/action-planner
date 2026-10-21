package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.util.concurrent.atomic.AtomicReference

/** Regression for expiry discovered by StorageContext.check() on a read worker. */
class ActiveReadSessionThreadTest {
    private class Harness {
        var now=0L
        val authentication=object:AuthenticationExperiment {
            override fun start(mode:KeyExperiment,complete:(AuthenticationOutcome)->Unit):Cancellation {
                complete(AuthenticationOutcome.Granted(object:SyntheticOperation {
                    override fun perform()=OperationResult.SUCCEEDED
                    override fun release(){}
                },now))
                return Cancellation{}
            }
        }
        val session=SessionCoordinator(authentication,Maintenance { done -> done(true);Subscription{} },
            MonotonicTime { now },ProductSessionTiming.policy,{true})
        val port=UncachedWorkspaceMutations()
        val initialGeneration:Long
        val ticket:WorkspaceMutationInvalidation.ActiveReadTicket
        val context:StorageContext
        init {
            session.foreground(true);session.authenticate(Experiment.PER_OPERATION)
            assertEquals(SessionState.READY,session.snapshot().state)
            initialGeneration=session.snapshot().generation
            ticket=port.beforeActiveRead("action",initialGeneration)!!
            session.observe { state ->
                if(state.state==SessionState.LOCKED || state.state==SessionState.BLOCKED)
                    port.revokeActiveRead()
            }
            context=session.acquire(AccessScope.READY)!!
        }
    }

    @Test fun idleExpiryDiscoveredOnWorkerSynchronouslyRevokesTicketWithoutThreadViolation() {
        val h=Harness();h.now=30_000L
        val failure=AtomicReference<Throwable?>();val checkResult=AtomicReference<Boolean?>()
        val worker=Thread {
            try { checkResult.set(h.context.check(AccessPoint.STEP)) }
            catch(error:Throwable) { failure.set(error) }
        }
        worker.start();worker.join()
        assertNull("observer must not throw on the expiry caller",failure.get())
        assertEquals(SessionState.LOCKED,h.session.snapshot().state)
        assertEquals(false,checkResult.get())
        assertFalse(h.ticket.isLive())
        assertFalse(h.port.isCurrentActiveRead(h.ticket))
        assertEquals(h.initialGeneration,h.ticket.generation)
        assertNull("locked session must not issue a new READY context",h.session.acquire(AccessScope.READY))
        assertEquals("Read revocation must not advance the writer epoch",0L,h.port.mutationEpoch)
    }

    @Test fun absoluteExpiryDiscoveredOnWorkerSynchronouslyRevokesTicket() {
        val h=Harness();h.now=180_000L
        val failure=AtomicReference<Throwable?>()
        val worker=Thread { try { h.context.check(AccessPoint.STEP) } catch(error:Throwable) { failure.set(error) } }
        worker.start();worker.join()
        assertNull(failure.get());assertEquals(SessionState.LOCKED,h.session.snapshot().state)
        assertFalse(h.ticket.isLive());assertFalse(h.port.isCurrentActiveRead(h.ticket))
        assertEquals(0L,h.port.mutationEpoch)
    }

    @Test fun blockedDiscoveredOnWorkerSynchronouslyRevokesTicket() {
        val h=Harness();val failure=AtomicReference<Throwable?>()
        val worker=Thread { try { h.session.storageFailure() } catch(error:Throwable) { failure.set(error) } }
        worker.start();worker.join()
        assertNull(failure.get());assertEquals(SessionState.BLOCKED,h.session.snapshot().state)
        assertFalse(h.ticket.isLive());assertFalse(h.port.isCurrentActiveRead(h.ticket))
        assertNull(h.session.acquire(AccessScope.READY));assertEquals(0L,h.port.mutationEpoch)
    }

    @Test fun ownerLockAndRepeatedReentrantObserverRevokesOnlyOnce() {
        val h=Harness();var calls=0
        val nested=h.session.observe { state ->
            if(state.state==SessionState.LOCKED) { calls++;h.port.revokeActiveRead();h.port.revokeActiveRead() }
        }
        h.session.lock()
        assertTrue(calls>=1);assertEquals(SessionState.LOCKED,h.session.snapshot().state)
        assertFalse(h.ticket.isLive());assertEquals(0L,h.port.mutationEpoch)
        nested.cancel()
    }

    @Test fun revokeRacingOwnerWriterAdmissionDoesNotMoveWriterStateOffOwner() {
        val h=Harness();val failure=AtomicReference<Throwable?>()
        val worker=Thread { try { h.port.revokeActiveRead() } catch(error:Throwable) { failure.set(error) } }
        worker.start();worker.join()
        val pending=h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),h.initialGeneration)
        assertNull(failure.get());assertFalse(h.ticket.isLive());assertNotNull(pending)
        assertEquals(1L,h.port.mutationEpoch)
        assertTrue(pending!!.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
        assertEquals(1L,h.port.mutationEpoch)
    }
}
