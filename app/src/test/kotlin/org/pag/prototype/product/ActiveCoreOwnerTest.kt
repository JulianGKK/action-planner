package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.atomic.AtomicBoolean

/** ViewModel/Domain-port evidence only; real shared-ticket lifetimes are tested in data. */
class ActiveCoreOwnerTest {
    private class Session:SessionPort {
        var state=SessionSnapshot(SessionState.READY,1)
        var interactions=0
        private val observers=mutableListOf<(SessionSnapshot)->Unit>()
        override fun snapshot()=state
        override fun observe(observer:(SessionSnapshot)->Unit):Subscription {observers.add(observer);observer(state);return Subscription{observers.remove(observer)}}
        fun change(next:SessionSnapshot){state=next;observers.toList().forEach{it(next)}}
        override fun authenticate(experiment:Experiment){}
        override fun testAccess(){}
        override fun userInteraction(){interactions++}
        override fun lock(){change(SessionSnapshot(SessionState.LOCKED,state.generation+1))}
    }
    private class Lifetime:ActiveCoreLifetime {
        val live=AtomicBoolean(true);var invalidator:(()->Unit)?=null
        var falseAfter=false;var duringInstall:(()->Unit)?=null
        var publications=0
        override fun isCurrent()=live.get()
        override fun publish(install:()->Unit):Boolean {
            publications++
            if(!live.get())return false
            install();duringInstall?.invoke()
            return !falseAfter && live.get()
        }
        override fun onInvalidated(invalidate:()->Unit):Subscription {invalidator=invalidate;if(!live.get())invalidate();return Subscription{invalidator=null}}
        override fun close(){if(live.getAndSet(false))invalidator?.invoke();invalidator=null}
    }
    private class Port(override val reuseEnabled:Boolean):ActiveCoreLoadPort {
        val pending=mutableListOf<Pair<String,(ActiveCoreResult,ActiveCoreLifetime?)->Unit>>()
        var reads=0
        override fun loadOwned(actionId:String,callback:(ActiveCoreResult,ActiveCoreLifetime?)->Unit):Boolean {
            reads++;pending.add(actionId to callback);return true
        }
        fun deliver(value:ActiveCoreResult?=null,lifetime:Lifetime=Lifetime()):Lifetime {
            val (id,callback)=pending.removeAt(0);callback(value ?: core(id),lifetime);return lifetime
        }
    }
    private class Repo(var action:Action):ActionRepository {
        override fun listActions(publish:(List<Action>)->Unit)=publish(listOf(action))
        override fun getAction(id:String,publish:(Action?)->Unit)=publish(action.takeIf{it.id==id})
        override fun createAction(action:NewAction,publish:(Action)->Unit)=error("unexpected write")
        override fun updateAction(expected:Action,changes:NewAction,publish:(ActionChangeResult)->Unit)=error("unexpected write")
        override fun advanceActionStatus(id:String,expected:ActionStatus,publish:(ActionChangeResult)->Unit)=error("unexpected write")
    }
    private class H(reuse:Boolean=false,status:ActionStatus=ActionStatus.ACTIVE) {
        val session=Session();val repo=Repo(Action("a","Synthetic","Synthetic",0,0,0,status))
        val dispatch=mutableListOf<()->Unit>();val port=Port(reuse)
        val people=object:PersonRepository {
            override fun listPersons(actionId:String,publish:(PersonResult<List<Person>>)->Unit)=publish(PersonResult.Value(emptyList()))
            override fun getPerson(actionId:String,personId:String,publish:(PersonResult<Person?>)->Unit)=publish(PersonResult.Value(null))
            override fun createPerson(actionId:String,pseudonym:String,publish:(PersonResult<Person>)->Unit)=error("unexpected write")
            override fun updatePerson(actionId:String,expected:Person,pseudonym:String,publish:(PersonResult<Person>)->Unit)=error("unexpected write")
        }
        val model=ProductViewModel(session,repo,ProductStorageState.READY_TO_OPEN,people){}
        init {model.bindActiveCore(port){dispatch.add(it)};model.detail("a")}
    }
    companion object {
        private fun core(id:String="a",generation:Long=1,status:ActionStatus=ActionStatus.ACTIVE):ActiveCoreResult.Value {
            val state=CompactTaskState(P3bFreeActiveCoreBinding(id,generation),0)
            val counts=CompactTaskCounts(40,10,5,7,2)
            return ActiveCoreResult.Value(ActiveCorePayload(state,Action(id,"Synthetic","Synthetic",0,0,0,status),
                ActiveCorePeoplePage(state,CompactPeopleCounts(0,100,0,0,0,0,0,0),100,emptyList(),true),
                CompactFunctions(emptyList(),100,100,0),ActiveCoreTaskPage(state,counts,counts,emptyList(),true)),
                ActiveCoreReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units()),0,0)
        }
    }
    @Test fun oneCoreReplacesFullWorkspaceAndOnlyHostPortAllowsNavigationReuse() {
        val h=H(true);h.port.deliver();val first=h.model.activeCore
        assertNotNull(first);assertNull(h.model.workspace)
        for(area in listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.HUB)) {
            h.model.activeArea(area);assertSame(first,h.model.activeCore);assertNull(h.model.workspace)
        }
        assertEquals(1,h.port.reads)
        val product=H();val old=product.port.deliver();product.model.activeArea(ActiveArea.PERSONS)
        assertEquals(2,product.port.reads);assertNull(product.model.activeCore);assertFalse(old.live.get())
    }
    /** RED prerequisite characterization, NOT permission to enable the product gate.
     * The actual VM bypasses even lifetime.publish on its host-only reuse branch and
     * reports interaction. A future hit must acquire/check/publish without renewing idle.
     */
    @Test fun gapHostReuseHasNoPerHitPublicationAndReportsInteraction() {
        val h=H(true);val life=h.port.deliver()
        val before=h.session.interactions;val publications=life.publications
        life.falseAfter=true
        h.model.activeArea(ActiveArea.TASKS)
        assertNotNull(h.model.activeCore)
        assertEquals(publications,life.publications)
        assertEquals(before+1,h.session.interactions)
        assertEquals(1,h.port.reads)
    }
    @Test fun inactiveForeignAndOverBudgetCandidatesNeverInstall() {
        for(status in listOf(ActionStatus.PREPARATION,ActionStatus.COMPLETED)) {val h=H(status=status);assertEquals(0,h.port.reads);assertNull(h.model.activeCore)}
        for(value in listOf(core("other"),core(generation=2),core(status=ActionStatus.COMPLETED),core().copy(retainedCharge=ActiveWorkspaceBudget.LIMIT_BYTES+1))) {
            val h=H();val life=h.port.deliver(value);assertNull(h.model.activeCore);assertFalse(life.live.get())
        }
    }
    @Test fun falseReturnAndRevokeDuringInstallLeaveNoRetention() {
        val h=H();val falseLife=Lifetime().also{it.falseAfter=true};h.port.deliver(lifetime=falseLife)
        assertNull(h.model.activeCore);assertFalse(falseLife.live.get())
        val raced=H();val life=Lifetime();life.duringInstall={life.close()};raced.port.deliver(lifetime=life)
        assertNull(raced.model.activeCore)
    }
    @Test fun workerInvalidationDropsAuthorityBeforeOwnerNotificationAndLockClears() {
        val h=H(true);val life=h.port.deliver();assertNotNull(h.model.activeCore)
        val worker=Thread{life.close()};worker.start();worker.join()
        assertNull(h.model.activeCore);assertEquals(1,h.dispatch.size)
        h.dispatch.removeAt(0).invoke();assertNull(h.model.activeCore)
        val locked=H();locked.port.deliver();locked.session.lock();assertNull(locked.model.activeCore)
        val blocked=H();blocked.port.deliver();blocked.session.change(SessionSnapshot(SessionState.BLOCKED,2));assertNull(blocked.model.activeCore)
    }
    @Test fun actionSwitchBackFullViewAndOldCallbacksCannotRestoreSlot() {
        val h=H();h.repo.action=h.repo.action.copy(id="b");h.model.detail("b")
        val oldLife=h.port.deliver();assertFalse(oldLife.live.get());assertNull(h.model.activeCore)
        h.port.deliver();assertEquals("b",h.model.activeCore!!.action.id)
        h.model.back();assertNull(h.model.activeCore)
        val full=H();full.port.deliver();full.model.useFullActiveView();assertNull(full.model.activeCore);assertFalse(full.model.compactActiveMode)
    }
    @Test fun compactCountsAndMoreAreExplicitAndLostCallbackHasNoSlot() {
        val h=H();assertNull(h.model.activeCore);h.port.deliver()
        val value=h.model.activeCore!!
        assertEquals(100L,value.people.overall.total);assertTrue(value.people.hasMore)
        assertEquals(40L,value.tasks.overall.open);assertTrue(value.tasks.hasMore)
        assertEquals("32 von 100 angezeigt · weitere Einträge verfügbar",compactPageLabel(32,100,true))
        assertEquals("100 insgesamt",activeCompactHubCards(value).first().summary.first())
    }
    @Test fun workerSessionObserverDropsSlotBeforeComposeDispatchAndOnClearedClosesLifetime() {
        val h=H();val life=h.port.deliver()
        val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val thread=Thread{try{h.session.lock()}catch(error:Throwable){failure.set(error)}}
        thread.start();thread.join()
        assertNull(failure.get());assertFalse(life.live.get());assertNull(h.model.activeCore)
        assertEquals(SessionState.READY,h.model.snapshot.state)
        h.dispatch.toList().forEach{it()};assertEquals(SessionState.LOCKED,h.model.snapshot.state)
        val cleared=H();val held=cleared.port.deliver()
        val store=androidx.lifecycle.ViewModelStore();store.put("core",cleared.model);store.clear()
        assertFalse(held.live.get());assertNull(cleared.model.activeCore)
    }
    @Test fun duplicateCallbackCannotInstallSecondPayloadOrInvalidateTheConsumedSlot() {
        val h=H();val callback=h.port.pending.first().second
        val life=h.port.deliver();val first=h.model.activeCore
        callback(core(),life);assertSame(first,h.model.activeCore)
        val extra=Lifetime();callback(core(),extra)
        assertFalse(extra.live.get());assertSame(first,h.model.activeCore)
    }
}
