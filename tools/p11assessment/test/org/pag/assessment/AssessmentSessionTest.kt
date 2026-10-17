package org.pag.assessment
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*

/** Uses the existing coordinator, not a second deadline or authentication policy. */
class AssessmentSessionTest {
    private class Harness {
        var now=0L;var environment=true
        val auth=object:AuthenticationExperiment {
            override fun start(mode:KeyExperiment,complete:(AuthenticationOutcome)->Unit):Cancellation {
                complete(AuthenticationOutcome.Granted(object:SyntheticOperation {
                    override fun perform()=OperationResult.SUCCEEDED
                    override fun release(){}
                },now));return Cancellation{}
            }
        }
        val session=SessionCoordinator(auth,Maintenance{it(true);Subscription{}},MonotonicTime{now},ProductSessionTiming.policy,{environment})
        var binding:AssessmentStore.Binding?=null
        val store=AssessmentStore(session,{binding},1024)
        val subscription=session.observe{if(it.state!=SessionState.READY)store.clear()}
        fun ready(){session.foreground(true);session.authenticate(Experiment.PER_OPERATION);assertEquals(SessionState.READY,session.snapshot().state);binding=AssessmentStore.Binding("synthetic",session.snapshot().generation,ActionStatus.ACTIVE,0)}
        fun request()=WorkspaceReadRequest("synthetic",ActionStatus.ACTIVE,binding!!.generation,emptySet())
        fun install(){val b=binding!!;assertTrue(store.install(b,store.ticket(),WorkspaceRead(b.actionId,b.status,b.generation,emptySet(),OperationalBoard(b.actionId,b.status,emptyList(),emptyList())),100))}
        fun cleared(){assertNull(store.read(request()));assertFalse(store.retainedForTest())}
    }
    @Test fun lockedDenies(){val h=Harness();assertNull(h.store.read(WorkspaceReadRequest("synthetic",ActionStatus.ACTIVE,0,emptySet())))}
    @Test fun explicitLockClears(){val h=Harness();h.ready();h.install();h.session.lock();h.cleared()}
    @Test fun idleExpiry(){val h=Harness();h.ready();h.install();h.now=30000;h.cleared()}
    @Test fun absoluteDespiteActivity(){val h=Harness();h.ready();h.install();for(i in 1..18){h.now=i*10000L;h.session.userInteraction()};h.cleared()}
    @Test fun backgroundClears(){val h=Harness();h.ready();h.install();h.session.foreground(false);h.cleared()}
    @Test fun environmentScreenLockClears(){val h=Harness();h.ready();h.install();h.environment=false;h.session.checkExpiry();h.cleared()}
    @Test fun blockedClears(){val h=Harness();h.ready();h.install();h.session.storageFailure();assertEquals(SessionState.BLOCKED,h.session.snapshot().state);h.cleared()}
    @Test fun noAutoRestoreAfterReauth(){val h=Harness();h.ready();h.install();val old=h.request();h.session.lock();h.ready();assertNull(h.store.read(old));assertFalse(h.store.retainedForTest())}
    @Test fun lateLoadAfterLockCannotPublish(){val h=Harness();h.ready();val b=h.binding!!;val t=h.store.ticket();h.session.lock();assertFalse(h.store.install(b,t,WorkspaceRead(b.actionId,b.status,b.generation,emptySet(),OperationalBoard(b.actionId,b.status,emptyList(),emptyList())),100))}
}
