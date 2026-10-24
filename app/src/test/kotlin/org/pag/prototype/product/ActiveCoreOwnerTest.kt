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
        var current=true;var falseAfter=false;var duringInstall:(()->Unit)?=null
        var duringPublication:((()->Unit)->Unit)?=null
        var publications=0
        var hits=0;var observations=0;var disposals=0
        override fun compositionObserved(){observations++}
        override fun compositionDisposed(){disposals++}
        override fun authorizeHit(actionId:String):Boolean {hits++;return publish {}}
        override fun isCurrent()=live.get() && current
        override fun publish(install:()->Unit):Boolean {
            publications++
            if(!live.get())return false
            install();duringPublication?.invoke(install);duringInstall?.invoke()
            return !falseAfter && live.get()
        }
        override fun onInvalidated(invalidate:()->Unit):Subscription {invalidator=invalidate;if(!live.get())invalidate();return Subscription{invalidator=null}}
        override fun close(){if(live.getAndSet(false))invalidator?.invoke();invalidator=null}
    }
    private class Port(override val reuseEnabled:Boolean):ActiveCoreLoadPort {
        val pending=mutableListOf<Pair<String,(ActiveCoreResult,ActiveCoreLifetime?)->Unit>>()
        var reads=0;var fullViews=0;var refuseLoads=false;var lastDeliveredActionId:String?=null
        override fun fullViewStarting(){fullViews++}
        override fun loadOwned(actionId:String,callback:(ActiveCoreResult,ActiveCoreLifetime?)->Unit):Boolean {
            reads++;if(refuseLoads)return false
            pending.add(actionId to callback);return true
        }
        fun deliver(value:ActiveCoreResult?=null,lifetime:Lifetime=Lifetime()):Lifetime {
            val (id,callback)=pending.removeAt(0);val result=value ?: core(id)
            lastDeliveredActionId=(result as? ActiveCoreResult.Value)?.core?.action?.id
            callback(result,lifetime);return lifetime
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
    /** Tests observe only the current route's handle token, never CoreSlot/value/payload. */
    private fun presentedIdentity(model:ProductViewModel):Any? {
        val handle:Any?=when(model.activeArea) {
            ActiveArea.HUB->model.compactHubPresentation;ActiveArea.PERSONS->model.compactPersonsPresentation
            ActiveArea.TASKS->model.compactTasksPresentation;ActiveArea.FUNCTIONS->model.compactFunctionsPresentation
            else->null
        }
        return handle?.javaClass?.getDeclaredField("identity")?.let{it.isAccessible=true;it.get(handle)}
    }
    @Test fun productViewModelHasNoCoreBorrowOrCoreObservationSurface() {
        val methods=ProductViewModel::class.java.methods
        assertFalse(methods.any{it.name=="getActiveCore" || it.name=="observeActiveCoreComposition"})
        val roots=setOf(ActiveCorePayload::class.java,ActiveCorePeoplePage::class.java,
            ActiveCoreTaskPage::class.java,CompactFunctions::class.java)
        assertTrue(methods.filterNot{it.isSynthetic}.none{it.returnType in roots || it.parameterTypes.any{p->p in roots}})
        assertTrue(ProductViewModel::class.java.fields.none{it.type in roots})
    }
    private fun personsFixture(size:Int=32):ActiveCoreResult.Value {
        val value=core()
        val items=(0 until size).map { CompactPersonCard("p$it","Synthetic Ä界🦋 $it",it.toLong(),
            OperationalStatus.ON_SITE,null,null,null,1,0) }
        return value.copy(core=value.core.copy(people=value.core.people.copy(
            overall=CompactPeopleCounts(0,200,0,0,0,0,0,0),items=items)))
    }
    private fun personsOwner():Triple<H,Lifetime,CompactPersonsPresentation> {
        val h=H() // Gate off: switch BEFORE the first pending candidate is exposed.
        h.model.activeArea(ActiveArea.PERSONS)
        h.port.deliver() // Obsolete Main callback cannot install the cancelled Hub candidate.
        assertNull(presentedIdentity(h.model))
        val life=h.port.deliver(personsFixture())
        return Triple(h,life,checkNotNull(h.model.compactPersonsPresentation))
    }
    @Test fun personsPresentationIsOneIdentityOnlyHandleWithBoundedCorrectScalars() {
        val (h,life,view)=personsOwner();val reads=h.port.reads;val interactions=h.session.interactions
        assertSame(view,h.model.compactPersonsPresentation)
        assertEquals(32,view.shown());assertEquals(200L,view.total());assertEquals(true,view.hasMore())
        assertEquals(200L,view.count(OperationalStatus.ON_SITE))
        repeat(32){assertEquals("Synthetic Ä界🦋 $it",view.pseudonym(it))}
        assertNull(view.pseudonym(-1));assertNull(view.pseudonym(32));assertNull(view.pseudonym(Int.MAX_VALUE))
        assertEquals(reads,h.port.reads);assertEquals(interactions,h.session.interactions)
        val fields=view.javaClass.declaredFields.filterNot {java.lang.reflect.Modifier.isStatic(it.modifiers)}
        assertTrue(life.hits>=36);assertEquals(2,fields.size)
        assertTrue(fields.all {it.type==Any::class.java || it.type==ProductViewModel::class.java})
        view.compositionObserved();view.compositionDisposed();assertEquals(1,life.observations);assertEquals(1,life.disposals)
        assertEquals("Synthetic Ä界🦋 0",view.pseudonym(0)) // Dispose is no authorization/retirement signal.
    }
    @Test fun oldPersonsAccessCannotResolveAfterLockRevokeGenerationActionOrStatusChange() {
        for(reason in listOf("lock","revoke","generation","action","status","context","fresh-session")) {
            val (h,life,view)=personsOwner()
            when(reason) {
                "lock"->h.model.lock()
                "revoke"->life.close()
                "context"->{life.current=false}
                "generation"->h.session.change(SessionSnapshot(SessionState.READY,2))
                "action"->{h.repo.action=h.repo.action.copy(id="other");h.model.detail("other")}
                "status"->{h.repo.action=h.repo.action.copy(status=ActionStatus.COMPLETED);h.model.detail("a")}
                else->h.session.state=SessionSnapshot(SessionState.LOCKED,2) // No cached observer update.
            }
            val reads=h.port.reads
            repeat(3){assertNull(reason,view.pseudonym(0));assertNull(view.shown());assertNull(view.total());assertNull(view.hasMore());assertNull(view.count(OperationalStatus.ON_SITE))}
            assertEquals(reads,h.port.reads)
            life.current=true;h.session.state=SessionSnapshot(SessionState.READY,1)
            assertNull("A denied handle must not revive",view.pseudonym(0))
        }
    }
    @Test fun personsAccessDeniesAuthorityRaceForeignThreadAndCannotFollowReplacementSlot() {
        val (h,life,view)=personsOwner();val hits=life.hits
        val t=Thread{assertNull(view.pseudonym(0))};t.start();t.join(3000);assertFalse(t.isAlive);assertEquals(hits,life.hits)
        life.duringInstall={life.close()};assertNull(view.pseudonym(0));assertNull(h.model.compactPersonsPresentation)
        h.model.activeArea(ActiveArea.PERSONS);h.port.deliver(personsFixture())
        val next=checkNotNull(h.model.compactPersonsPresentation)
        assertNotSame(view,next);assertNull(view.pseudonym(0));assertEquals("Synthetic Ä界🦋 0",next.pseudonym(0))
        val ownerField=view.javaClass.getDeclaredField("owner").also{it.isAccessible=true}
        assertNull(ownerField.get(view)) // Old metadata handle no longer roots the VM either.
    }
    @Test fun oversizedPersonsPageNeverCreatesPresentationHandle() {
        val h=H();h.model.activeArea(ActiveArea.PERSONS);h.port.deliver()
        val life=h.port.deliver(personsFixture(33))
        assertNull(h.model.compactPersonsPresentation);assertFalse(life.live.get())
    }

    private fun tasksFixture(size:Int=32):ActiveCoreResult.Value {
        val value=core();val counts=CompactTaskCounts(640,200,17,100,60)
        val items=(0 until size).map { CompactTaskItem("t$it","Synthetic task Ä界🦋 $it",TaskPriority.NORMAL,
            TaskStatus.OPEN,null,null,null,null,it.toLong(),1) }
        return value.copy(core=value.core.copy(tasks=value.core.tasks.copy(overall=counts,filtered=counts,items=items)))
    }
    private fun tasksOwner():Triple<H,Lifetime,CompactTasksPresentation> {
        val h=H();h.model.activeArea(ActiveArea.TASKS);h.port.deliver()
        assertNull(presentedIdentity(h.model))
        val life=h.port.deliver(tasksFixture())
        return Triple(h,life,checkNotNull(h.model.compactTasksPresentation))
    }
    @Test fun tasksHandleResolvesOnlyThirtyTwoUnicodeTitlesAndExactExistingCounters() {
        val (h,life,view)=tasksOwner();val reads=h.port.reads;val interactions=h.session.interactions
        assertSame(view,h.model.compactTasksPresentation);assertNull(h.model.compactPersonsPresentation)
        assertEquals(32,view.shown());assertEquals(840L,view.activeCount());assertEquals(true,view.hasMore())
        assertEquals(640L,view.count(TaskStatus.OPEN));assertEquals(200L,view.count(TaskStatus.IN_PROGRESS))
        assertEquals(100L,view.count(TaskStatus.DONE));assertEquals(60L,view.count(TaskStatus.CANCELLED))
        repeat(32){assertEquals("Synthetic task Ä界🦋 $it",view.title(it))}
        val hits=life.hits
        for(index in listOf(-1,32,Int.MIN_VALUE,Int.MAX_VALUE))assertNull(view.title(index))
        assertEquals(hits,life.hits);assertEquals(reads,h.port.reads);assertEquals(interactions,h.session.interactions)
        val fields=view.javaClass.declaredFields.filterNot {java.lang.reflect.Modifier.isStatic(it.modifiers)}
        assertEquals(2,fields.size);assertTrue(fields.all{it.type==Any::class.java || it.type==ProductViewModel::class.java})
        view.compositionObserved();view.compositionDisposed();assertEquals(1,life.observations);assertEquals(1,life.disposals)
        assertEquals("Synthetic task Ä界🦋 0",view.title(0)) // No retirement grant.
    }
    @Test fun oldTasksHandlesDenyLockRevokeGenerationActionStatusAndContextWithoutNewRead() {
        for(reason in listOf("lock","revoke","generation","action","status","context","fresh-session")) {
            val (h,life,view)=tasksOwner()
            when(reason) {
                "lock"->h.model.lock();"revoke"->life.close();"context"->{life.current=false}
                "generation"->h.session.change(SessionSnapshot(SessionState.READY,2))
                "action"->{h.repo.action=h.repo.action.copy(id="other");h.model.detail("other")}
                "status"->{h.repo.action=h.repo.action.copy(status=ActionStatus.COMPLETED);h.model.detail("a")}
                else->h.session.state=SessionSnapshot(SessionState.LOCKED,2)
            }
            val reads=h.port.reads
            repeat(2){assertNull(reason,view.title(0));assertNull(view.shown());assertNull(view.activeCount());assertNull(view.hasMore());assertNull(view.count(TaskStatus.OPEN))}
            life.current=true;h.session.state=SessionSnapshot(SessionState.READY,1)
            assertNull(view.title(0));assertEquals(reads,h.port.reads)
        }
    }
    @Test fun revokeBetweenTaskScalarsPreservesAlreadyReturnedTextButDeniesEveryLaterLookup() {
        val (h,life,view)=tasksOwner();val first=view.title(0)
        assertEquals("Synthetic task Ä界🦋 0",first)
        life.close() // Controlled boundary between two actual UI-style scalar calls.
        assertNull(view.title(1));assertNull(view.count(TaskStatus.OPEN));assertNull(view.shown())
        assertEquals("Synthetic task Ä界🦋 0",first) // No recall or atomic whole-frame claim.
        assertNull(view.javaClass.getDeclaredField("owner").also{it.isAccessible=true}.get(view))
        assertNull(h.model.compactTasksPresentation)
    }
    @Test fun taskWriterPendingUnknownAndLateMainCannotRestoreOldHandleOrResolveSql() {
        val h=H();h.model.activeArea(ActiveArea.TASKS);h.port.deliver()
        val callback=h.port.pending.single().second
        val life=h.port.deliver(tasksFixture());val view=checkNotNull(h.model.compactTasksPresentation)
        val mutations=UncachedWorkspaceMutations();val ticket=checkNotNull(mutations.beforeActiveRead("a",1))
        ticket.onInvalidated{life.close()}
        assertNotNull(view.title(0));val reads=h.port.reads
        val pending=checkNotNull(mutations.beforeTaskQueue("a",1))
        assertNull(view.title(1));assertNull(view.activeCount())
        assertTrue(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
        assertNull(mutations.beforeActiveRead("a",1));assertNull(view.title(2))
        repeat(2){callback(tasksFixture(),life)}
        assertNull(h.model.compactTasksPresentation);assertEquals(reads,h.port.reads)
    }
    @Test fun taskHandleRejectsForeignThreadPublicationRaceReplacementAndOversizedPage() {
        val (h,life,view)=tasksOwner();val result=java.util.concurrent.atomic.AtomicReference<String?>("not denied")
        val hits=life.hits;val t=Thread{result.set(view.title(0))};t.start();t.join(3000)
        assertFalse(t.isAlive);assertNull(result.get());assertEquals(hits,life.hits)
        life.duringInstall={life.close()};assertNull(view.title(0))
        h.model.activeArea(ActiveArea.TASKS);h.port.deliver(tasksFixture())
        val next=checkNotNull(h.model.compactTasksPresentation)
        assertNotSame(view,next);assertNull(view.title(0));assertNotNull(next.title(0))
        val oversized=H();oversized.model.activeArea(ActiveArea.TASKS);oversized.port.deliver()
        val rejected=oversized.port.deliver(tasksFixture(33))
        assertFalse(rejected.live.get());assertNull(oversized.model.compactTasksPresentation)
    }

    private fun hubOwner():Triple<H,Lifetime,CompactHubPresentation> {
        val h=H();val life=h.port.deliver()
        return Triple(h,life,checkNotNull(h.model.compactHubPresentation))
    }
    @Test fun hubHasExactlyThreeMetadataCardsCorrectScalarsAndOriginalNavigation() {
        val (h,life,view)=hubOwner();val reads=h.port.reads;val interactions=h.session.interactions
        assertSame(view,h.model.compactHubPresentation);assertEquals(3,view.size())
        val areas=listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS)
        val values=listOf(100L to 100L,0L to 100L,40L to 10L)
        val lines=listOf(listOf("100 insgesamt","100 vor Ort"),listOf("0 Funktionen","100 Personen vor Ort"),listOf("40 offen","10 in Arbeit"))
        repeat(3){index->
            val card=checkNotNull(view.card(index));assertEquals(areas[index],card.area)
            assertTrue(card.summary.isEmpty());assertSame(card,view.card(index))
            assertEquals(values[index].first,view.value(index,0));assertEquals(values[index].second,view.value(index,1))
            repeat(2){line->assertEquals(lines[index][line],compactHubSummary(index,line,view.value(index,line)!!))}
            assertSame(view.navigation(card.area),view.navigation(card.area))
        }
        assertEquals(reads,h.port.reads);assertEquals(interactions,h.session.interactions)
        val hits=life.hits
        for(index in listOf(-1,3,Int.MAX_VALUE)){assertNull(view.card(index));assertNull(view.value(index,0))}
        assertNull(view.value(0,-1));assertNull(view.value(0,2));assertNull(view.navigation(ActiveArea.HUB))
        assertEquals(hits,life.hits)
        val renderer=Class.forName("org.pag.prototype.product.ActiveWorkspaceKt").declaredMethods.single {
            it.name=="CompactActiveContent" && it.parameterTypes.contains(CompactHubPresentation::class.java)
        }
        assertFalse(renderer.parameterTypes.contains(ActiveCorePayload::class.java))
        val fields=view.javaClass.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}
        assertEquals(11,fields.size)
        assertTrue(fields.all{it.type==Any::class.java || it.type==ProductViewModel::class.java || it.type==List::class.java || it.type==kotlin.jvm.functions.Function0::class.java})
        for(area in areas) {
            val (owner,_,hub)=hubOwner();val click=checkNotNull(hub.navigation(area))
            click();assertEquals(area,owner.model.activeArea);assertEquals(2,owner.port.reads)
            val count=owner.port.reads;click();assertEquals(count,owner.port.reads);assertNull(hub.value(0,0))
        }
    }
    @Test fun oldHubHandleAndCallbacksDenySessionActionGenerationLockAndRevoke() {
        for(reason in listOf("lock","revoke","generation","action","status","context","fresh-session")) {
            val (h,life,hub)=hubOwner();val click=checkNotNull(hub.navigation(ActiveArea.PERSONS))
            when(reason) {
                "lock"->h.model.lock();"revoke"->life.close();"context"->{life.current=false}
                "generation"->h.session.change(SessionSnapshot(SessionState.READY,2))
                "action"->{h.repo.action=h.repo.action.copy(id="other");h.model.detail("other")}
                "status"->{h.repo.action=h.repo.action.copy(status=ActionStatus.COMPLETED);h.model.detail("a")}
                else->h.session.state=SessionSnapshot(SessionState.LOCKED,2)
            }
            val reads=h.port.reads
            assertNull(reason,hub.value(0,0));assertNull(hub.card(0));click();assertEquals(reads,h.port.reads)
            life.current=true;h.session.state=SessionSnapshot(SessionState.READY,1)
            assertNull(hub.value(1,0));click();assertEquals(reads,h.port.reads)
        }
    }
    @Test fun revokeBetweenHubScalarsDoesNotRecallEarlierValueAndDeniesFurtherResolutions() {
        val (h,life,hub)=hubOwner();val first=hub.value(0,0);val text=compactHubSummary(0,0,first!!)
        assertEquals(100L,first);life.close()
        assertNull(hub.value(0,1));assertNull(hub.value(1,0));assertNull(hub.card(2))
        assertEquals("100 insgesamt",text) // Prior framework text is not recalled; no atomic frame.
        val reads=h.port.reads;hub.navigation(ActiveArea.TASKS)!!();assertEquals(reads,h.port.reads)
        assertNull(hub.javaClass.getDeclaredField("owner").also{it.isAccessible=true}.get(hub))
    }
    @Test fun hubWriterPendingUnknownLateMainAndForeignThreadCannotResolveOrNavigate() {
        val h=H();val callback=h.port.pending.single().second
        val life=h.port.deliver();val hub=checkNotNull(h.model.compactHubPresentation)
        val hits=life.hits;val foreign=java.util.concurrent.atomic.AtomicReference<Long?>(-1)
        val t=Thread{foreign.set(hub.value(0,0));hub.navigation(ActiveArea.PERSONS)!!()}
        t.start();t.join(3000);assertFalse(t.isAlive);assertNull(foreign.get());assertEquals(hits,life.hits)
        val mutations=UncachedWorkspaceMutations();val ticket=checkNotNull(mutations.beforeActiveRead("a",1))
        ticket.onInvalidated{life.close()}
        val pending=checkNotNull(mutations.beforeTaskQueue("a",1));val reads=h.port.reads
        assertNull(hub.value(0,0));hub.navigation(ActiveArea.FUNCTIONS)!!();assertEquals(reads,h.port.reads)
        assertTrue(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
        assertNull(mutations.beforeActiveRead("a",1));assertNull(hub.value(2,0))
        repeat(2){callback(core(),life)};assertNull(h.model.compactHubPresentation);assertEquals(reads,h.port.reads)
    }
    @Test fun hubOldIdentityCannotFollowReplacementAndPublicationRaceDeniesValuesAndCallbacks() {
        val (h,_,old)=hubOwner();val click=checkNotNull(old.navigation(ActiveArea.TASKS))
        h.model.detail("a");val life=h.port.deliver();val next=checkNotNull(h.model.compactHubPresentation)
        assertNotSame(old,next);assertNull(old.value(0,0));assertNotNull(next.value(0,0))
        val reads=h.port.reads;click();assertEquals(reads,h.port.reads)
        val invalid=Any();assertNull(h.model.compactHubValue(invalid,0,0))
        h.model.compactHubOpen(invalid,ActiveArea.TASKS);assertEquals(reads,h.port.reads)
        assertNotNull(next.value(0,0)) // Invalid identity cannot revoke the current original slot.
        life.duringInstall={life.close()};assertNull(next.value(0,0));assertNull(h.model.compactHubPresentation)
        next.navigation(ActiveArea.PERSONS)!!();assertEquals(reads,h.port.reads)
    }

    private fun functionsFixture(size:Int,repetitions:Int=15):ActiveCoreResult.Value {
        val value=core()
        val items=(0 until size).map{CompactFunction(Role("r$it","a","Ä界🦋".repeat(repetitions)+" function $it",it.toLong()),(it%7).toLong())}
        val sum=items.sumOf{it.onSiteCount}+7
        return value.copy(core=value.core.copy(functions=CompactFunctions(items,7,sum,items.count{it.onSiteCount>0}.toLong())))
    }
    private fun functionsOwner(size:Int=6,repetitions:Int=15):Triple<H,Lifetime,CompactFunctionsPresentation> {
        val h=H();h.model.activeArea(ActiveArea.FUNCTIONS);h.port.deliver()
        val life=h.port.deliver(functionsFixture(size,repetitions))
        return Triple(h,life,checkNotNull(h.model.compactFunctionsPresentation))
    }
    @Test fun functionsExposeCompleteSixThirtyTwoAndHundredCataloguesWithExactUnicodeNamesAndCounts() {
        for(size in listOf(0,6,32,100)) {
            val repetitions=if(size==32)80 else 15
            val (h,life,view)=functionsOwner(size,repetitions);val reads=h.port.reads;val interactions=h.session.interactions
            assertSame(view,h.model.compactFunctionsPresentation);assertEquals(size,view.size());assertEquals(7L,view.withoutFunction())
            assertEquals((0 until size).sumOf{(it%7).toLong()}+7,view.onSite())
            val names=HashSet<String>()
            repeat(size){index->val name="Ä界🦋".repeat(repetitions)+" function $index"
                assertEquals(name,view.name(index));assertEquals((index%7).toLong(),view.count(index))
                assertEquals("$name: ${index%7} vor Ort",view.line(index));assertTrue(names.add(view.name(index)!!))}
            assertEquals(size,names.size);val hits=life.hits
            for(index in listOf(-1,size,size+1,Int.MIN_VALUE,Int.MAX_VALUE)) {
                assertNull(view.name(index));assertNull(view.count(index));assertNull(view.line(index))}
            assertEquals(hits,life.hits);assertEquals(reads,h.port.reads);assertEquals(interactions,h.session.interactions)
            val fields=view.javaClass.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}
            assertEquals(3,fields.size);assertTrue(fields.all{it.type==Any::class.java || it.type==ProductViewModel::class.java || it.type==Int::class.javaPrimitiveType})
        }
        val renderers=Class.forName("org.pag.prototype.product.ActiveWorkspaceKt").declaredMethods.filter{it.name=="CompactActiveContent"}
        assertEquals(4,renderers.size);assertTrue(renderers.none{it.parameterTypes.contains(ActiveCorePayload::class.java)})
    }
    @Test fun functionsOldHandlesDenyLockRevokeGenerationActionAndContextWithoutNewRead() {
        for(reason in listOf("lock","revoke","generation","action","status","context","fresh-session")) {
            val (h,life,view)=functionsOwner()
            when(reason) {
                "lock"->h.model.lock();"revoke"->life.close();"context"->{life.current=false}
                "generation"->h.session.change(SessionSnapshot(SessionState.READY,2))
                "action"->{h.repo.action=h.repo.action.copy(id="other");h.model.detail("other")}
                "status"->{h.repo.action=h.repo.action.copy(status=ActionStatus.COMPLETED);h.model.detail("a")}
                else->h.session.state=SessionSnapshot(SessionState.LOCKED,2)
            }
            val reads=h.port.reads
            assertNull(view.name(0));assertNull(view.count(0));assertNull(view.line(0));assertNull(view.size());assertNull(view.onSite());assertNull(view.withoutFunction())
            life.current=true;h.session.state=SessionSnapshot(SessionState.READY,1)
            assertNull(view.name(0));assertEquals(reads,h.port.reads)
        }
    }
    @Test fun revokeBetweenFunctionLookupsLeavesPriorTextButDeniesLaterNamesCountsAndLines() {
        val (h,life,view)=functionsOwner();val first=view.line(0);assertNotNull(first)
        life.close();assertNull(view.line(1));assertNull(view.name(0));assertNull(view.count(0))
        assertEquals("Ä界🦋".repeat(15)+" function 0: 0 vor Ort",first)
        assertNull(view.javaClass.getDeclaredField("owner").also{it.isAccessible=true}.get(view))
        assertNull(h.model.compactFunctionsPresentation)
    }
    @Test fun functionWriterPendingUnknownAndLateMainCannotRestoreOldCatalogue() {
        val h=H();h.model.activeArea(ActiveArea.FUNCTIONS);h.port.deliver()
        val callback=h.port.pending.single().second;val life=h.port.deliver(functionsFixture(32))
        val view=checkNotNull(h.model.compactFunctionsPresentation)
        val mutations=UncachedWorkspaceMutations();val ticket=checkNotNull(mutations.beforeActiveRead("a",1))
        ticket.onInvalidated{life.close()};assertNotNull(view.line(0));val reads=h.port.reads
        val pending=checkNotNull(mutations.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.OPERATIONS,"a"),1))
        assertNull(view.name(1));assertNull(view.size())
        assertTrue(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
        repeat(2){callback(functionsFixture(100),life)}
        assertNull(view.line(2));assertNull(h.model.compactFunctionsPresentation);assertEquals(reads,h.port.reads)
    }
    @Test fun functionsDenyForeignThreadForeignIdentityPublicationRaceAndReplacement() {
        val (h,life,view)=functionsOwner();val hits=life.hits
        val result=java.util.concurrent.atomic.AtomicReference<String?>("not denied")
        val t=Thread{result.set(view.line(0))};t.start();t.join(3000);assertFalse(t.isAlive)
        assertNull(result.get());assertEquals(hits,life.hits)
        val invalid=Any();assertNull(h.model.compactFunctionName(invalid,0));assertNotNull(view.name(0))
        life.duringInstall={life.close()};assertNull(view.line(0))
        h.model.activeArea(ActiveArea.FUNCTIONS);h.port.deliver(functionsFixture(32))
        val next=checkNotNull(h.model.compactFunctionsPresentation)
        assertNotSame(view,next);assertNull(view.name(0));assertEquals(32,next.size());assertNotNull(next.line(31))
        next.compositionObserved();next.compositionDisposed();assertNotNull(next.name(0))
    }

    private fun referenceField(o:Any,name:String):Any?=o.javaClass.getDeclaredField(name).let{it.isAccessible=true;it.get(o)}
    @Test fun p97OriginalFourHandleLinksEndButRunningScalarClosureStillHoldsOriginalSlot() {
        val (h,life,view)=functionsOwner()
        var testSlotReference:Any?=(referenceField(h.model,"coreSlot") as java.util.concurrent.atomic.AtomicReference<*>).get()
        val handles=listOf("hub","persons","tasks","functions").map{referenceField(testSlotReference!!,it)!!}
        assertTrue(handles.all{referenceField(it,"owner")===h.model})
        var witnessed=false
        life.duringPublication={install->
            val captures=install.javaClass.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}.map{it.isAccessible=true;it.get(install)}
            if(captures.any{it===testSlotReference}) {
                witnessed=true;life.close()
                assertNull((referenceField(h.model,"coreSlot") as java.util.concurrent.atomic.AtomicReference<*>).get())
                assertTrue(handles.all{referenceField(it,"owner")==null})
                assertTrue(captures.any{it===testSlotReference}) // Actual production install parameter still holds the slot in the running publisher/resolver scope.
            }
        }
        assertNull(view.line(0));assertTrue(witnessed);assertNull(life.invalidator)
        life.duringPublication=null;testSlotReference=null // Explicit TEST inspection alias, never a product owner.
        assertNull(view.line(1))
        println("P97 VM fields/4 handle links CONFIRMED after-revoke; scalar-local BLOCKED until-call-return; no global retirement receipt")
    }
    @Test fun p97SlotReplacementAndFullviewKeepExplicitOldTestReferenceUnconfirmed() {
        val h=H();val callback=h.port.pending.first().second;val oldLife=h.port.deliver()
        var testSlotReference:Any?=(referenceField(h.model,"coreSlot") as java.util.concurrent.atomic.AtomicReference<*>).get()
        val oldIdentity=presentedIdentity(h.model)
        h.model.detail("a");h.port.deliver();val nextIdentity=presentedIdentity(h.model)
        assertNotSame(oldIdentity,nextIdentity);repeat(2){callback(core(),oldLife)};assertSame(nextIdentity,presentedIdentity(h.model))
        assertNotNull(referenceField(testSlotReference!!,"value")) // Explicit TEST holder pins original slot/value.
        h.model.useFullActiveView();assertNull(presentedIdentity(h.model));assertTrue(h.port.fullViews>0)
        assertNotNull(referenceField(testSlotReference!!,"value"));testSlotReference=null
        println("P97 old-slot TEST reference BLOCKED while-held; fake-port replacement is not production readmission; fullview/form OUT_OF_SCOPE")
    }

    // P98 test-only aliases inspect metadata. They are deliberately NOT owner-end evidence.
    private fun p98Slot(model:ProductViewModel):Any =
        checkNotNull((referenceField(model,"coreSlot") as java.util.concurrent.atomic.AtomicReference<*>).get())
    private fun p98Scopes(slot:Any):Any=checkNotNull(referenceField(slot,"scopes"))
    private fun p98Call(scopes:Any,name:String):Any?=scopes.javaClass.getDeclaredMethod(name).let{it.isAccessible=true;it.invoke(scopes)}
    private fun p98Diagnosis(scopes:Any)=p98Call(scopes,"diagnosis")
    private fun p98Count(scopes:Any)=referenceField(scopes,"active") as Long
    private fun p98Captures(install:()->Unit,slot:Any)=install.javaClass.declaredFields
        .filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}.any{it.isAccessible=true;it.get(install)===slot}
    private fun p98AssertDetached(model:ProductViewModel,slot:Any) {
        assertNull((referenceField(model,"coreSlot") as java.util.concurrent.atomic.AtomicReference<*>).get())
        for(name in listOf("hub","persons","tasks","functions"))assertNull(referenceField(referenceField(slot,name)!!,"owner"))
    }
    @Test fun p98LastResolverReturnOnlyAcknowledgesLocalScopes() {
        val (h,life,view)=functionsOwner();val testSlot=p98Slot(h.model);val scopes=p98Scopes(testSlot)
        assertEquals(0L,p98Count(scopes));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes))
        var witnessed=false
        life.duringPublication={install->if(p98Captures(install,testSlot)) {
            witnessed=true;life.close();p98AssertDetached(h.model,testSlot)
            assertEquals(1L,p98Count(scopes));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes))
        }}
        assertNull(view.line(0));assertTrue(witnessed)
        assertEquals(0L,p98Count(scopes));assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopes))
        assertNotNull(referenceField(testSlot,"value")) // Local quiescence is expressly NOT owner end.
        println("P98 resolver: detached+four-links-revoked while active=1 NOT_QUIESCENT; actual scope return => VM_SCOPES_QUIESCENT only")
    }
    @Test fun p98NestedResolversEndExactlyOnceInReverseOrder() {
        val (h,life,view)=functionsOwner();val slot=p98Slot(h.model);val scopes=p98Scopes(slot);var depth=0
        life.duringPublication={install->if(p98Captures(install,slot)) {
            depth++
            if(depth==1) {
                assertEquals(1L,p98Count(scopes));assertNull(view.line(1))
                assertEquals(1L,p98Count(scopes));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes))
            } else {assertEquals(2L,p98Count(scopes));life.close();assertEquals(2L,p98Count(scopes))}
            depth--
        }}
        assertNull(view.line(0));assertEquals(0,depth);assertEquals(0L,p98Count(scopes))
        assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopes))
        println("P98 nesting: active=1/2/1/0; no transferable end token")
    }
    @Test fun p98ForeignInvalidatorRemainsCountedAfterSynchronousRevoke() {
        val (h,life,view)=functionsOwner();val slot=p98Slot(h.model);val scopes=p98Scopes(slot)
        val reached=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val dispatch:((()->Unit)->Unit)={reached.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
        h.model.javaClass.getDeclaredField("ownerDispatch").let{it.isAccessible=true;it.set(h.model,dispatch)}
        val worker=Thread {try{life.close()}catch(t:Throwable){failure.set(t)}}
        worker.start()
        try {
            assertTrue(reached.await(5,java.util.concurrent.TimeUnit.SECONDS))
            p98AssertDetached(h.model,slot);assertEquals(1L,p98Count(scopes))
            assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes));assertNull(view.name(0))
            h.session.lock() // Must return while foreign callback is deliberately paused.
            assertEquals(SessionState.LOCKED,h.model.snapshot.state)
            assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes))
        } finally {release.countDown();worker.join(5000)}
        assertFalse(worker.isAlive);assertNull(failure.get());assertEquals(0L,p98Count(scopes))
        assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopes))
        println("P98 foreign invalidator: handles revoked synchronously; lock returned before foreign callback; quiescent only after return")
    }
    @Test fun p98MissingOldInvalidatorEndCannotChangeNewSlotDiagnosis() {
        val h=H();val lifeA=h.port.deliver();val slotA=p98Slot(h.model);val scopesA=p98Scopes(slotA)
        val reached=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val dispatch:((()->Unit)->Unit)={reached.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
        val dispatchField=h.model.javaClass.getDeclaredField("ownerDispatch").apply{isAccessible=true}
        dispatchField.set(h.model,dispatch)
        val worker=Thread {try{lifeA.close()}catch(t:Throwable){failure.set(t)}}
        worker.start();var scopesB:Any?=null
        try {
            assertTrue(reached.await(5,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("NOT_QUIESCENT",p98Diagnosis(scopesA))
            // Fake port permits replacement solely to test identity separation, NOT product readmission.
            h.model.detail("a");h.port.deliver();scopesB=p98Scopes(p98Slot(h.model))
            assertEquals(1L,p98Count(scopesA));assertEquals(0L,p98Count(scopesB))
            assertEquals("NOT_QUIESCENT",p98Diagnosis(scopesB))
        } finally {release.countDown();worker.join(5000)}
        assertFalse(worker.isAlive);assertNull(failure.get())
        assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopesA))
        assertEquals(0L,p98Count(scopesB!!));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopesB!!))
        println("P98 pending A scope: B diagnosis independent before/after actual A callback return; fake replacement only")
    }
    @Test fun p98InvalidationDispatchExceptionStillAcknowledgesOriginalScope() {
        val (h,life,_)=functionsOwner();val slot=p98Slot(h.model);val scopes=p98Scopes(slot)
        var observedCount=-1L;var observedDiagnosis:Any?=null
        val dispatch:((()->Unit)->Unit)={
            observedCount=p98Count(scopes);observedDiagnosis=p98Diagnosis(scopes)
            throw IllegalStateException("synthetic dispatch failure")
        }
        h.model.javaClass.getDeclaredField("ownerDispatch").let{it.isAccessible=true;it.set(h.model,dispatch)}
        life.close();p98AssertDetached(h.model,slot)
        assertEquals(1L,observedCount);assertEquals("NOT_QUIESCENT",observedDiagnosis)
        assertEquals(0L,p98Count(scopes));assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopes))
        println("P98 invalidation dispatch exception: existing runCatching policy unchanged; finally ends scope")
    }
    @Test fun p98ScopeMetadataAndInvalidatorAddNoPayloadCapture() {
        val h=H();val life=h.port.deliver();val slot=p98Slot(h.model);val scopes=p98Scopes(slot)
        assertTrue(scopes.javaClass.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}.all{it.type.isPrimitive})
        val captures=life.invalidator!!.javaClass.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}
            .map{it.isAccessible=true;it.get(life.invalidator)}
        assertFalse(captures.any{it===slot || it is ActiveCoreResult.Value || it is ActiveCorePayload})
        assertTrue(captures.any{it===h.model}) // Existing mutable VM owner edge is expressly NOT globally retired.
        println("P98 reference shape: scope object primitives only; invalidator captures original metadata identity/VM, no CoreSlot/Value/Payload")
    }
    @Test fun p98ResolverExceptionEndsScopeWithoutEndingPublication() {
        val (h,life,view)=functionsOwner();val slot=p98Slot(h.model);val scopes=p98Scopes(slot)
        life.duringPublication={install->if(p98Captures(install,slot))throw IllegalStateException("synthetic scope failure")}
        try{view.line(0);fail("Expected controlled exception")}catch(expected:IllegalStateException){assertEquals("synthetic scope failure",expected.message)}
        assertEquals(0L,p98Count(scopes));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes))
        life.duringPublication=null;assertNotNull(view.name(0))
        h.session.lock();assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopes))
        println("P98 resolver exception: finally acknowledged; published slot remains NOT_QUIESCENT until revoke")
    }
    @Test fun p98OriginalInstallationPublicationAndRejectionAreCounted() {
        for(reject in listOf(false,true)) {
            val h=H();val life=Lifetime();life.falseAfter=reject
            var slot:Any?=null;var scopes:Any?=null
            life.duringPublication={
                slot=p98Slot(h.model);scopes=p98Scopes(slot!!)
                assertEquals(1L,p98Count(scopes!!));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes!!))
                if(!reject)life.close()
                if(!reject){p98AssertDetached(h.model,slot!!);assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes!!))}
            }
            h.port.deliver(lifetime=life)
            p98AssertDetached(h.model,slot!!);assertEquals(0L,p98Count(scopes!!))
            assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopes!!))
        }
        println("P98 original installation: revoke and rejected publication retain initial scope until callback finally")
    }
    @Test fun p98InstallationExceptionAlsoAcknowledgesInitialScope() {
        val h=H();val life=Lifetime();var slot:Any?=null
        life.duringPublication={slot=p98Slot(h.model);throw IllegalStateException("synthetic install failure")}
        try{h.port.deliver(lifetime=life);fail("Expected controlled exception")}catch(expected:IllegalStateException){assertEquals("synthetic install failure",expected.message)}
        val scopes=p98Scopes(slot!!);assertEquals(0L,p98Count(scopes));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes))
        life.duringPublication=null;h.session.lock();p98AssertDetached(h.model,slot!!)
        assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopes))
        println("P98 install exception: initial scope ended; published authority still requires revoke")
    }
    @Test fun p98OldDuplicateCallbacksAndWrongIdentityCannotAcknowledgeNewSlot() {
        val h=H();val callbackA=h.port.pending.first().second;val lifeA=h.port.deliver()
        val slotA=p98Slot(h.model);val scopesA=p98Scopes(slotA);val lateInvalidator=checkNotNull(lifeA.invalidator)
        h.model.detail("a");val lifeB=h.port.deliver();val slotB=p98Slot(h.model);val scopesB=p98Scopes(slotB)
        assertNotSame(slotA,slotB);assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopesA))
        repeat(3){lateInvalidator();callbackA(core(),lifeA)}
        val oldIdentity=referenceField(slotA,"identity")!!
        assertNull(h.model.compactHubValue(oldIdentity,0,0))
        assertSame(slotB,p98Slot(h.model));assertEquals(0L,p98Count(scopesB));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopesB))
        assertEquals(0L,p98Count(scopesA));assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopesA))
        assertNotNull(h.model.compactHubPresentation);lifeB.close()
        assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopesB))
        println("P98 identity: late duplicate A callbacks cannot detach/acknowledge/reopen B; no exposed receipt API")
    }
    @Test fun p98CounterCorruptionAndOverflowFailClosedWithoutBlockingRevoke() {
        val (h,life,view)=functionsOwner();val slot=p98Slot(h.model);val scopes=p98Scopes(slot)
        scopes.javaClass.getDeclaredField("active").let{it.isAccessible=true;it.setLong(scopes,Long.MAX_VALUE)} // TEST fault injection only.
        assertNull(view.name(0));assertEquals(Long.MAX_VALUE,p98Count(scopes))
        life.close();p98AssertDetached(h.model,slot)
        assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes)) // Sticky broken metadata is never a receipt.
        val type=scopes.javaClass;val fresh=type.getDeclaredConstructor().let{it.isAccessible=true;it.newInstance()}
        p98Call(fresh,"end");assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(fresh))
        p98Call(fresh,"end");assertEquals(0L,p98Count(fresh));assertEquals("NOT_QUIESCENT",p98Diagnosis(fresh))
        assertEquals(false,p98Call(fresh,"enter"));assertEquals(0L,p98Count(fresh))
        println("P98 fault injection: overflow/duplicate end never wrap or reopen; broken diagnosis sticky; synchronous revoke still works")
    }
    @Test fun p98ComposeSignalsDoNotEndPublicationOrGrantAnyReceipt() {
        val (h,life,view)=functionsOwner();val scopes=p98Scopes(p98Slot(h.model))
        repeat(3){view.compositionObserved();view.compositionDisposed()}
        assertEquals(0L,p98Count(scopes));assertEquals("NOT_QUIESCENT",p98Diagnosis(scopes))
        assertNotNull(view.name(0));life.close();assertEquals("VM_SCOPES_QUIESCENT",p98Diagnosis(scopes))
        println("P98 Compose observed/disposed cannot end publication; no OutstandingCoreRead release connection")
    }

    @Test fun oneCoreReplacesFullWorkspaceAndOnlyHostPortAllowsNavigationReuse() {
        val h=H(true);h.port.deliver();val first=presentedIdentity(h.model)
        assertNotNull(first);assertNull(h.model.workspace)
        for(area in listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.HUB)) {
            h.model.activeArea(area);assertSame(first,presentedIdentity(h.model));assertNull(h.model.workspace)
        }
        assertEquals(1,h.port.reads)
        val product=H();val old=product.port.deliver();product.model.activeArea(ActiveArea.PERSONS)
        assertEquals(2,product.port.reads);assertNull(presentedIdentity(product.model));assertFalse(old.live.get())
    }
    /** §74 gap retained by name, now the positive §75 contract. Runtime proof is in data. */
    @Test fun gapHostReuseHasNoPerHitPublicationAndReportsInteraction() {
        val h=H(true);val life=h.port.deliver()
        val before=h.session.interactions;val publications=life.publications
        val core=presentedIdentity(h.model)
        for(area in listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.HUB)) {
            h.model.activeArea(area);assertSame(core,presentedIdentity(h.model))
        }
        assertEquals(publications+4,life.publications);assertEquals(4,life.hits)
        assertEquals(before,h.session.interactions);assertEquals(1,h.port.reads)
        life.falseAfter=true
        h.model.activeArea(ActiveArea.TASKS)
        assertNull(presentedIdentity(h.model));assertFalse(life.live.get())
        assertEquals(publications+5,life.publications)
        assertEquals(before,h.session.interactions)
        assertEquals(2,h.port.reads) // Safe fresh load, no reuse or interaction on denial.
    }
    @Test fun hitPublicationHasNoEarlyNavigationAndLockDuringHitCannotRestoreSlot() {
        val h=H(true);val life=h.port.deliver();val interactions=h.session.interactions
        life.duringInstall={
            assertEquals(ActiveArea.HUB,h.model.activeArea)
            assertEquals(interactions,h.session.interactions)
            h.session.lock()
        }
        h.model.activeArea(ActiveArea.TASKS)
        assertNull(presentedIdentity(h.model));assertFalse(life.live.get())
        assertEquals(interactions,h.session.interactions);assertEquals(1,h.port.reads)
        assertNotEquals(ActiveArea.TASKS,h.model.activeArea)
    }
    @Test fun revokeImmediatelyAfterHitAndStatusChangeRemoveOriginalAuthority() {
        val h=H(true);val life=h.port.deliver();h.model.activeArea(ActiveArea.PERSONS)
        val t=Thread{life.close()};t.start();t.join(3000);assertFalse(t.isAlive)
        assertNull(presentedIdentity(h.model))
        h.dispatch.toList().forEach{it()};assertNull(presentedIdentity(h.model))
        val before=h.session.interactions
        h.model.activeArea(ActiveArea.TASKS)
        assertEquals(before,h.session.interactions);assertEquals(2,h.port.reads)
        val changed=H(true);val old=changed.port.deliver()
        changed.repo.action=changed.repo.action.copy(status=ActionStatus.COMPLETED,completedAt=1)
        changed.model.detail("a");changed.model.activeArea(ActiveArea.TASKS)
        assertNull(presentedIdentity(changed.model));assertFalse(old.live.get())
        assertEquals(0,old.hits)
    }
    @Test fun inactiveForeignAndOverBudgetCandidatesNeverInstall() {
        for(status in listOf(ActionStatus.PREPARATION,ActionStatus.COMPLETED)) {val h=H(status=status);assertEquals(0,h.port.reads);assertNull(presentedIdentity(h.model))}
        for(value in listOf(core("other"),core(generation=2),core(status=ActionStatus.COMPLETED),core().copy(retainedCharge=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES+1),core().copy(transitionCharge=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES+1))) {
            val h=H();val life=h.port.deliver(value);assertNull(presentedIdentity(h.model));assertFalse(life.live.get())
        }
    }
    @Test fun correctlyChargedCandidateUsesRetainedEightAndTransitionSixteenLimits() {
        val r=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES
        val t=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
        fun units(bytes:Long)=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=bytes-71)
        for((retained,transition) in listOf(r to t,(r+1) to t,r to (t+1))) {
            val h=H()
            val value=core().copy(reservation=ActiveCoreReservation(units(retained),units(transition)),
                retainedCharge=retained,transitionCharge=transition)
            val life=h.port.deliver(value)
            if(retained==r && transition==t) {
                assertNotNull(presentedIdentity(h.model));assertTrue(life.live.get())
            } else {
                assertNull(presentedIdentity(h.model));assertFalse(life.live.get())
            }
        }
    }

    @Test fun falseReturnAndRevokeDuringInstallLeaveNoRetention() {
        val h=H();val falseLife=Lifetime().also{it.falseAfter=true};h.port.deliver(lifetime=falseLife)
        assertNull(presentedIdentity(h.model));assertFalse(falseLife.live.get())
        val raced=H();val life=Lifetime();life.duringInstall={life.close()};raced.port.deliver(lifetime=life)
        assertNull(presentedIdentity(raced.model))
    }
    @Test fun workerInvalidationDropsAuthorityBeforeOwnerNotificationAndLockClears() {
        val h=H(true);val life=h.port.deliver();assertNotNull(presentedIdentity(h.model))
        val worker=Thread{life.close()};worker.start();worker.join()
        assertNull(presentedIdentity(h.model));assertEquals(1,h.dispatch.size)
        h.dispatch.removeAt(0).invoke();assertNull(presentedIdentity(h.model))
        val locked=H();locked.port.deliver();locked.session.lock();assertNull(presentedIdentity(locked.model))
        val blocked=H();blocked.port.deliver();blocked.session.change(SessionSnapshot(SessionState.BLOCKED,2));assertNull(presentedIdentity(blocked.model))
    }
    @Test fun actionSwitchBackFullViewAndOldCallbacksCannotRestoreSlot() {
        val h=H();h.repo.action=h.repo.action.copy(id="b");h.model.detail("b")
        val oldLife=h.port.deliver();assertFalse(oldLife.live.get());assertNull(presentedIdentity(h.model))
        h.port.deliver();assertEquals("b",h.model.selected?.id);assertNotNull(presentedIdentity(h.model));assertEquals("b",h.port.lastDeliveredActionId)
        h.model.back();assertNull(presentedIdentity(h.model))
        val full=H();full.port.deliver();full.model.useFullActiveView();assertNull(presentedIdentity(full.model));assertFalse(full.model.compactActiveMode)
    }
    @Test fun compactCountsAndMoreAreExplicitAndLostCallbackHasNoSlot() {
        val h=H(true);assertNull(presentedIdentity(h.model));h.port.deliver()
        val identity=checkNotNull(presentedIdentity(h.model))
        assertEquals(100L,h.model.compactHubPresentation!!.value(0,0))
        h.model.activeArea(ActiveArea.PERSONS)
        val people=checkNotNull(h.model.compactPersonsPresentation)
        assertEquals(100L,people.total());assertEquals(true,people.hasMore())
        h.model.activeArea(ActiveArea.TASKS)
        val tasks=checkNotNull(h.model.compactTasksPresentation)
        assertEquals(40L,tasks.count(TaskStatus.OPEN));assertEquals(true,tasks.hasMore())
        assertSame(identity,presentedIdentity(h.model))
        assertEquals("32 von 100 angezeigt · weitere Einträge verfügbar",compactPageLabel(32,100,true))
        assertEquals("100 insgesamt",compactHubSummary(0,0,100))
    }
    @Test fun actualProductRouterSelectsCompactRendererForEveryCompactActiveArea() {
        val h=H(true);h.port.deliver()
        assertTrue(h.model.compactActiveMode)
        assertNull(h.model.workspace)
        for(area in listOf(ActiveArea.HUB,ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS)) {
            h.model.activeArea(area)
            assertNotNull(presentedIdentity(h.model))
            assertEquals(area,activeWorkspaceShownArea(h.model))
            assertFalse("$area must stay on ActiveWorkspace/CompactActiveContent",productEntityListRoute(h.model))
        }
    }
    @Test fun revokedCompactCoreDoesNotFallBackToFullEntityList() {
        val h=H(true);val lifetime=h.port.deliver()
        h.model.activeArea(ActiveArea.PERSONS)
        assertFalse(productEntityListRoute(h.model))
        lifetime.close()
        assertTrue(h.model.compactActiveMode)
        assertNull(presentedIdentity(h.model))
        assertNull(h.model.workspace)
        assertFalse("Missing compact payload must render loading/error, never the full list",productEntityListRoute(h.model))
    }
    @Test fun fullActiveAndPreparationEntityListsKeepTheirExistingRoute() {
        for(area in listOf(ActiveArea.PERSONS,ActiveArea.TASKS)) {
            val active=H(true);active.port.deliver();active.model.activeArea(area);active.model.useFullActiveView()
            assertFalse(active.model.compactActiveMode)
            assertTrue("Full ACTIVE $area retains ProductEntityList",productEntityListRoute(active.model))
        }
        val preparation=H(status=ActionStatus.PREPARATION)
        preparation.model.preparationArea(PreparationArea.PERSONS)
        assertTrue(productEntityListRoute(preparation.model))
        preparation.model.preparationArea(PreparationArea.TASKS)
        assertTrue(productEntityListRoute(preparation.model))
    }
    @Test fun workerSessionObserverDropsSlotBeforeComposeDispatchAndOnClearedClosesLifetime() {
        val h=H();val life=h.port.deliver()
        val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val thread=Thread{try{h.session.lock()}catch(error:Throwable){failure.set(error)}}
        thread.start();thread.join()
        assertNull(failure.get());assertFalse(life.live.get());assertNull(presentedIdentity(h.model))
        assertEquals(SessionState.READY,h.model.snapshot.state)
        h.dispatch.toList().forEach{it()};assertEquals(SessionState.LOCKED,h.model.snapshot.state)
        val cleared=H();val held=cleared.port.deliver()
        val store=androidx.lifecycle.ViewModelStore();store.put("core",cleared.model);store.clear()
        assertFalse(held.live.get());assertNull(presentedIdentity(cleared.model))
    }
    @Test fun duplicateCallbackCannotInstallSecondPayloadOrInvalidateTheConsumedSlot() {
        val h=H();val callback=h.port.pending.first().second
        val life=h.port.deliver();val first=presentedIdentity(h.model)
        callback(core(),life);assertSame(first,presentedIdentity(h.model))
        val extra=Lifetime();callback(core(),extra)
        assertFalse(extra.live.get());assertSame(first,presentedIdentity(h.model))
    }
    @Test fun clearingSlotAndRepeatedDisposeCannotEraseAnAlreadyHeldProducerOrConsumerReference() {
        val h=H();val candidate=core() // Producer-owned test input, NOT an output borrowed from VM.
        val life=h.port.deliver(candidate);val handle=checkNotNull(h.model.compactHubPresentation)
        val escaped=compactHubSummary(0,0,handle.value(0,0)!!)
        handle.compositionObserved();h.model.back()
        repeat(3){life.compositionDisposed();life.close()}
        assertNull(presentedIdentity(h.model));assertFalse(life.isCurrent());assertNull(handle.value(0,0))
        handle.compositionObserved();assertEquals("a",candidate.core.action.id)
        assertEquals("100 insgesamt",escaped) // API removal is no physical wipe/end proof.
        assertEquals(1,life.observations);assertEquals(3,life.disposals)
    }
    @Test fun fullViewReturnSurfacesAdmissionRefusalAndCannotReplayTheOldCore() {
        val h=H();val oldCallback=h.port.pending.first().second;val life=h.port.deliver()
        h.model.useFullActiveView()
        assertTrue(h.port.fullViews>0);assertNull(presentedIdentity(h.model));assertFalse(life.isCurrent())
        // Actual sticky gate is tested with the production loader in OutstandingCoreReadTest.
        h.port.refuseLoads=true
        h.model.activeArea(ActiveArea.HUB)
        assertTrue(h.model.compactActiveMode);assertTrue(h.model.workspaceError)
        assertFalse(h.model.personsLoading);assertNull(h.model.workspace);assertNull(presentedIdentity(h.model))
        oldCallback(core(),life)
        assertNull(presentedIdentity(h.model));assertTrue(h.model.workspaceError)
    }
    @Test fun preparationAlsoAnnouncesFullViewBeforeAnyCompactRead() {
        val h=H(status=ActionStatus.PREPARATION)
        assertEquals(0,h.port.reads);assertTrue(h.port.fullViews>0)
        h.port.refuseLoads=true;h.repo.action=h.repo.action.copy(status=ActionStatus.ACTIVE)
        h.model.detail("a")
        assertEquals(1,h.port.reads);assertNull(presentedIdentity(h.model));assertTrue(h.model.workspaceError)
    }
    @Test fun generationChangeRejectsOldPendingCallbackWithoutRevokingTheNewSlot() {
        val h=H();val oldCallback=h.port.pending.first().second
        h.session.change(SessionSnapshot(SessionState.READY,2));h.model.detail("a")
        val stale=h.port.deliver();assertFalse(stale.isCurrent());assertNull(presentedIdentity(h.model))
        val current=h.port.deliver(core(generation=2));val value=presentedIdentity(h.model)
        assertNotNull(value)
        repeat(3){oldCallback(core(),stale);stale.close();stale.compositionDisposed()}
        assertSame(value,presentedIdentity(h.model));assertTrue(current.isCurrent())
    }
}
