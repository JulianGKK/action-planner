package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** UI-port lifecycle proofs; transaction evidence remains the unchanged writer boundary's job. */
class CreateRoleModelTest {
    private class Repo:OperationsRepository {
        val callbacks=ArrayList<(OperationsResult<Role>)->Unit>();val names=ArrayList<String>()
        var status=ActionStatus.ACTIVE
        var readFailure=false
        override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit){
            val person=OperationalPerson(Person("p",actionId,"Synthetic",0, editRevision = 0),PersonRolePlan(emptySet(),null, editRevision = 0),PersonOperationalState(actionId,"p",null,OperationalStatus.ON_SITE,null,0, editRevision = 0))
            publish(if(readFailure)OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED) else OperationsResult.Value(OperationalBoard(actionId,status,listOf(Role("r",actionId,"Synthetic role",0, editRevision = 0)),listOf(person))))
        }
        override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit){names.add(name);callbacks.add(publish)}
        override fun renameRole(actionId:String,expected:Role,name:String,publish:(OperationsResult<Role>)->Unit)=error("No rename")
        override fun deleteRole(actionId:String,expected:Role,publish:(OperationsResult<Unit>)->Unit)=error("No delete")
        override fun planRoles(actionId:String,personId:String,expected:ExpectedPersonRolePlan,plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit)=error("No plan")
        override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit)=error("No snapshot")
    }
    private var session=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","Synthetic","Synthetic action",1,2,0,ActionStatus.ACTIVE)
    private val repo=Repo();private val messages=ArrayList<String>()
    private var onInteraction:()->Unit={}
    private val model=OperationsModel(repo,{session},{action},{onInteraction()},{},feedback={messages.add(it)})
    private fun open(){model.newFunction();model.changeNewFunctionName("Draft")}
    private fun success(name:String="Draft")=OperationsResult.Value(Role("new","a",name,1, editRevision = 0))
    private fun surface(surface:RoleCreateSurface,active:Boolean=true){
        onInteraction={};model.clear();messages.clear();repo.names.clear();repo.callbacks.clear();repo.readFailure=false
        action=action.copy(id="a",status=if(surface==RoleCreateSurface.PREPARATION || !active)ActionStatus.PREPARATION else ActionStatus.ACTIVE)
        repo.status=action.status;session=SessionSnapshot(SessionState.READY,session.generation+1);model.refresh()
        when(surface){
            RoleCreateSurface.ROLES->model.roles()
            RoleCreateSurface.PREPARATION->model.beginRoleCreate(surface)
            RoleCreateSurface.INLINE->{model.update("p");model.beginRoleCreate(surface)}
            RoleCreateSurface.NEW_FUNCTION->model.newFunction()
        }
        model.changeRoleCreateName("Draft")
        assertEquals(surface,model.createSurface)
    }
    @Test fun everyTextSurfaceKeepsDraftForPendingAndAllRejections(){
        for(surface in RoleCreateSurface.entries)for(reason in listOf(OperationsFailure.INVALID,OperationsFailure.DUPLICATE,OperationsFailure.NOT_ALLOWED)){
            surface(surface);model.saveRoleCreate();model.saveRoleCreate()
            assertEquals(listOf("Draft"),repo.names);assertEquals("Draft",model.createName);assertEquals(surface,model.createSurface);assertTrue(model.busy)
            repo.callbacks.single()(OperationsResult.Rejected(reason))
            assertEquals("Draft",model.createName);assertEquals(surface,model.createSurface);assertEquals(reason,model.createError)
            assertFalse(model.busy);assertTrue(messages.isEmpty());assertEquals(1,repo.names.size)
        }
    }
    @Test fun confirmedSuccessOnlyCompletesOwningFormAndFeedbackOnce(){
        for(surface in RoleCreateSurface.entries){
            surface(surface);val screen=model.screen;val person=model.personId;val status=model.liveStatus
            model.saveRoleCreate();val callback=repo.callbacks.single();callback(success());callback(success())
            assertEquals("",model.createName);assertFalse(model.busy);assertEquals(1,messages.size)
            assertEquals(if(surface==RoleCreateSurface.ROLES)surface else null,model.createSurface)
            if(surface==RoleCreateSurface.INLINE){assertEquals(screen,model.screen);assertEquals(person,model.personId);assertEquals(status,model.liveStatus)}
        }
    }
    @Test fun bothPreparationSurfacesUseSameOutcomeContract(){
        for(surface in listOf(RoleCreateSurface.ROLES,RoleCreateSurface.PREPARATION)){
            surface(surface,active=false);model.saveRoleCreate()
            assertEquals("Draft",model.createName)
            repo.callbacks.single()(OperationsResult.Rejected(OperationsFailure.DUPLICATE))
            assertEquals("Draft",model.createName);assertEquals(surface,model.createSurface)
            model.saveRoleCreate();repo.callbacks.last()(success());assertEquals("",model.createName)
            assertEquals(listOf("Rolle „Draft“ angelegt"),messages)
        }
    }
    @Test fun noCallbackNeverClosesOrAutomaticallyRepeatsAnyTextSurface(){
        for(surface in RoleCreateSurface.entries){surface(surface);model.saveRoleCreate();model.refresh();model.saveRoleCreate()
            assertEquals("Draft",model.createName);assertEquals(surface,model.createSurface);assertTrue(model.busy)
            assertEquals(1,repo.names.size);assertTrue(messages.isEmpty())
        }
    }
    @Test fun oldPendingCallbackCannotTouchNewFormAcrossLifecycleAndSameActionClear(){
        for(kind in listOf("lock","blocked","generation","action","same-action")){
            surface(RoleCreateSurface.NEW_FUNCTION);model.saveRoleCreate();val old=repo.callbacks.single()
            when(kind){"lock"->session=SessionSnapshot(SessionState.LOCKED,session.generation+1);"blocked"->session=SessionSnapshot(SessionState.BLOCKED,session.generation+1);"generation"->session=SessionSnapshot(SessionState.READY,session.generation+1);"action"->action=action.copy(id="b");else->{}}
            model.clear();old(success());assertEquals("",model.createName);assertTrue(messages.isEmpty())
            session=SessionSnapshot(SessionState.READY,session.generation+1);action=action.copy(id="a")
            model.newFunction();model.changeNewFunctionName("New draft");old(success())
            assertEquals("New draft",model.createName);assertEquals(RoleCreateSurface.NEW_FUNCTION,model.createSurface);assertTrue(messages.isEmpty())
        }
    }
    @Test fun oldJobAfterRejectionCannotFinishSecondJobOnSameForm(){
        surface(RoleCreateSurface.PREPARATION);model.saveRoleCreate();val first=repo.callbacks.single()
        first(OperationsResult.Rejected(OperationsFailure.DUPLICATE));model.changeRoleCreateName("Second input");model.saveRoleCreate()
        first(success());assertTrue(model.busy);assertEquals("Second input",model.createName);assertTrue(messages.isEmpty())
        repo.callbacks.last()(success("Second input"));assertNull(model.createSurface);assertEquals(1,messages.size)
    }
    @Test fun templateCallbacksNeverResetAnOpenDraftOrRenameEditor(){
        surface(RoleCreateSurface.ROLES,active=false);model.createRole("Template")
        repo.callbacks.single()(OperationsResult.Rejected(OperationsFailure.DUPLICATE))
        assertEquals("Draft",model.createName);assertNull(model.createError);assertNull(model.error)
        assertEquals(OperationsFailure.DUPLICATE,model.templateCreateError)
        model.createRole("Another template");val callback=repo.callbacks.last();callback(success("Another template"));callback(success())
        assertEquals("Draft",model.createName);assertEquals(RoleCreateSurface.ROLES,model.createSurface);assertEquals(1,messages.size)
        val role=Role("r","a","Synthetic role",0, editRevision = 0);model.editRole(role);model.changeRoleName("Rename draft")
        model.createRole("Third template");repo.callbacks.last()(success("Third template"))
        assertEquals(role,model.editingRole);assertEquals("Rename draft",model.roleName)
    }
    @Test fun suppliedWorkspaceReadAndReadFailureDoNotConsumeCreateResult(){
        var reads=0
        val supplied=OperationsModel(repo,{session},{action},{},{},suppliedBoard={null},refreshWorkspace={reads++},feedback={messages.add(it)})
        supplied.newFunction();supplied.changeNewFunctionName("Draft");supplied.saveNewFunction();supplied.refresh()
        assertEquals("Draft",supplied.newFunctionName);repo.callbacks.single()(success())
        assertFalse(supplied.busy);assertEquals(OperationsScreen.OVERVIEW,supplied.screen);assertEquals(2,reads);assertEquals(1,messages.size)
        surface(RoleCreateSurface.ROLES);model.saveRoleCreate();repo.readFailure=true;model.refresh()
        assertEquals("Draft",model.createName);repo.callbacks.single()(success());assertEquals("",model.createName);assertFalse(model.busy)
    }
    @Test fun synchronousRevokeBeforeQueueCannotPublishOrSubmit(){
        surface(RoleCreateSurface.PREPARATION)
        onInteraction={session=SessionSnapshot(SessionState.LOCKED,session.generation+1);model.clear()}
        model.saveRoleCreate();assertTrue(repo.names.isEmpty());assertNull(model.createSurface);assertEquals("",model.createName);assertTrue(messages.isEmpty())
    }
    @Test fun noSubmissionCannotBeMistakenForPreparationSuccess(){
        action=action.copy(status=ActionStatus.PREPARATION)
        val absent=OperationsModel(null,{session},{action},{},{})
        absent.beginRoleCreate(RoleCreateSurface.PREPARATION);absent.changeRoleCreateName("Unsubmitted draft");absent.saveRoleCreate()
        assertEquals("Unsubmitted draft",absent.createName);assertEquals(RoleCreateSurface.PREPARATION,absent.createSurface);assertFalse(absent.busy)
    }
    @Test fun leavingInlineStatusDiscardsOnlyTheChildCreateDraft(){
        surface(RoleCreateSurface.INLINE);val person=model.personId
        model.status(OperationalStatus.IN_CUSTODY)
        assertNull(model.createSurface);assertEquals("",model.createName);assertEquals(person,model.personId)
        assertEquals(OperationalStatus.IN_CUSTODY,model.liveStatus)
        model.status(OperationalStatus.ON_SITE);model.beginRoleCreate(RoleCreateSurface.INLINE)
        assertEquals("",model.createName);assertTrue(repo.names.isEmpty())
    }
    @Test fun aHiddenCreateFormCannotSubmitOverTheRenameEditor(){
        surface(RoleCreateSurface.ROLES,active=false);val role=Role("r","a","Synthetic role",0, editRevision = 0)
        model.editRole(role);model.changeRoleName("Rename draft");model.saveRoleCreate()
        assertTrue(repo.names.isEmpty());assertEquals(role,model.editingRole);assertEquals("Rename draft",model.roleName)
        model.cancelRoleEditor();model.saveRoleCreate();assertEquals(listOf("Draft"),repo.names)
    }
    @Test fun firstLateCallbackCannotCloseNewInstanceWithinSameReadyGeneration(){
        surface(RoleCreateSurface.NEW_FUNCTION);model.saveRoleCreate();val old=repo.callbacks.single()
        val generation=session.generation
        model.clear();model.newFunction();model.changeNewFunctionName("Replacement draft")
        assertEquals(generation,session.generation)
        old(success())
        assertEquals("Replacement draft",model.createName);assertEquals(RoleCreateSurface.NEW_FUNCTION,model.createSurface)
        assertFalse(model.busy);assertTrue(messages.isEmpty())
    }
    @Test fun changedScopeWithoutReplyNeverPublishesSuccess(){
        for(kind in listOf("generation","action","revoke")){
            surface(RoleCreateSurface.NEW_FUNCTION);model.saveRoleCreate();val old=repo.callbacks.single()
            when(kind){"generation"->session=SessionSnapshot(SessionState.READY,session.generation+1);"action"->action=action.copy(id="b");else->session=SessionSnapshot(SessionState.LOCKED,session.generation+1)}
            old(success());assertTrue(messages.isEmpty());assertEquals("Draft",model.createName)
            model.clear();assertEquals("",model.createName)
        }
    }
    @Test fun rejectedNavigationOrUnrelatedToggleCannotDiscardDraft(){
        surface(RoleCreateSurface.NEW_FUNCTION)
        model.focusFunction("missing-role");model.toggleMoreFunctions()
        assertEquals("Draft",model.createName);assertEquals(RoleCreateSurface.NEW_FUNCTION,model.createSurface)
        assertEquals(OperationsScreen.NEW_FUNCTION,model.screen);assertTrue(repo.names.isEmpty())
    }
    @Test fun normalReadRefreshCannotLoseConfirmedCreateCallback(){
        open();model.saveNewFunction();model.refresh();repo.callbacks.single()(success())
        assertFalse(model.busy);assertEquals(OperationsScreen.OVERVIEW,model.screen)
        assertEquals("",model.newFunctionName);assertEquals(1,messages.size)
    }
    @Test fun callbackAfterRejectionCannotCloseLaterFormInstance(){
        open();model.saveNewFunction();val old=repo.callbacks.single()
        old(OperationsResult.Rejected(OperationsFailure.DUPLICATE))
        model.newFunction();model.changeNewFunctionName("Later draft")
        old(success())
        assertEquals(OperationsScreen.NEW_FUNCTION,model.screen);assertEquals("Later draft",model.newFunctionName);assertTrue(messages.isEmpty())
    }
    @Test fun missingCallbackKeepsNameAndDoesNotRetry(){
        open();model.saveNewFunction();model.saveNewFunction()
        assertEquals("Draft",model.newFunctionName);assertEquals(OperationsScreen.NEW_FUNCTION,model.screen)
        assertTrue(model.busy);assertEquals(listOf("Draft"),repo.names);assertTrue(messages.isEmpty())
    }
    @Test fun rejectedNewFunctionAlreadyKeepsDraft(){
        open();model.saveNewFunction();repo.callbacks.single()(OperationsResult.Rejected(OperationsFailure.DUPLICATE))
        assertEquals("Draft",model.newFunctionName);assertEquals(OperationsScreen.NEW_FUNCTION,model.screen);assertTrue(messages.isEmpty())
    }
}
