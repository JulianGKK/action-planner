package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** UI port proofs only. Commit/cleanup evidence is proved by the real SQLite boundary tests. */
class RoleEditorModelTest {
    private class Repo:OperationsRepository {
        var role=Role("r","a","Original",1)
        var rename:((OperationsResult<Role>)->Unit)?=null
        var delete:((OperationsResult<Unit>)->Unit)?=null
        var reload:((OperationsResult<OperationalBoard>)->Unit)?=null
        var deferRead=false;var reads=0;var writes=0
        var expected:Role?=null;var draft:String?=null
        fun board()=OperationalBoard("a",ActionStatus.PREPARATION,listOf(role),emptyList())
        override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit){reads++;if(deferRead)reload=publish else publish(OperationsResult.Value(board()))}
        override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit)=error("No create in this slice")
        override fun renameRole(actionId:String,expected:Role,name:String,publish:(OperationsResult<Role>)->Unit){writes++;this.expected=expected;draft=name;rename=publish}
        override fun deleteRole(actionId:String,expected:Role,publish:(OperationsResult<Unit>)->Unit){writes++;this.expected=expected;delete=publish}
        override fun planRoles(actionId:String,personId:String,expected:ExpectedPersonRolePlan,plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit)=error("No plan")
        override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit)=error("No live write")
    }
    private var session=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","Synthetic","Synthetic action",1,2,0,ActionStatus.PREPARATION)
    private val repo=Repo();private val messages=ArrayList<String>()
    private val model=OperationsModel(repo,{session},{action},{},{},feedback={messages.add(it)})
    private fun open(delete:Boolean=false){model.refresh();model.editRole(repo.role,delete)}
    @Test fun renameUsesOpeningSnapshotAndKeepsDraftUntilActualSuccessCallback(){
        open();val original=repo.role;val reads=repo.reads
        model.changeRoleName("My draft");repo.role=original.copy(name="Other editor")
        model.saveRoleRename()
        assertEquals(original,repo.expected);assertEquals("My draft",repo.draft);assertEquals(reads,repo.reads)
        assertTrue(model.busy);assertEquals(original,model.editingRole);assertEquals("My draft",model.roleName)
        repo.rename!!(OperationsResult.Rejected(OperationsFailure.STALE))
        assertFalse(model.busy);assertEquals(original,model.editingRole);assertEquals("My draft",model.roleName)
        assertEquals(OperationsFailure.STALE,model.error);assertTrue(messages.isEmpty());assertEquals(1,repo.writes)
        model.reloadRoleEditor()
        assertEquals(repo.role,model.editingRole);assertEquals("Other editor",model.roleName)
        model.changeRoleName("New draft");model.saveRoleRename();assertEquals(repo.role,repo.expected)
        repo.rename!!(OperationsResult.Value(repo.role.copy(name="New draft")))
        assertNull(model.editingRole);assertEquals("",model.roleName)
    }
    @Test fun deleteConfirmationSurvivesStaleInUseAndPendingUntilSuccess(){
        open(delete=true);val original=repo.role;repo.role=original.copy(name="Renamed")
        model.confirmRoleDelete();assertTrue(model.busy);assertEquals(original,model.deletingRole)
        repo.delete!!(OperationsResult.Rejected(OperationsFailure.STALE))
        assertEquals(original,model.deletingRole);assertEquals(OperationsFailure.STALE,model.error)
        model.reloadRoleEditor();assertEquals(repo.role,model.deletingRole)
        assertEquals(1,repo.writes) // Reload never automatically deletes.
        model.confirmRoleDelete();repo.delete!!(OperationsResult.Rejected(OperationsFailure.IN_USE))
        assertEquals(repo.role,model.deletingRole);assertEquals(OperationsFailure.IN_USE,model.error)
        model.confirmRoleDelete();assertNotNull(model.deletingRole)
        repo.delete!!(OperationsResult.Value(Unit));assertNull(model.deletingRole);assertTrue(messages.isEmpty())
    }
    @Test fun failedReloadAndDuplicateReloadCannotDiscardNewDraft(){
        open();val original=repo.role;model.changeRoleName("Draft");model.saveRoleRename()
        repo.rename!!(OperationsResult.Rejected(OperationsFailure.STALE));repo.deferRead=true;model.reloadRoleEditor()
        val old=repo.reload!!;old(OperationsResult.Rejected(OperationsFailure.NOT_FOUND))
        assertEquals(original,model.editingRole);assertEquals("Draft",model.roleName)
        old(OperationsResult.Value(repo.board()));assertEquals("Draft",model.roleName)
        model.saveRoleRename();repo.rename!!(OperationsResult.Rejected(OperationsFailure.STALE))
        model.reloadRoleEditor();val fresh=repo.reload!!;fresh(OperationsResult.Value(repo.board()))
        model.changeRoleName("After reload");fresh(OperationsResult.Value(repo.board()))
        assertEquals("After reload",model.roleName)
    }
    @Test fun oldAndDuplicateMutationCallbacksCannotCloseAnotherEditor(){
        for(delete in listOf(false,true)){
            model.clear();open(delete)
            if(delete)model.confirmRoleDelete() else {model.changeRoleName("Old");model.saveRoleRename()}
            val rename=repo.rename;val removal=repo.delete
            if(delete)removal!!(OperationsResult.Rejected(OperationsFailure.STALE)) else rename!!(OperationsResult.Rejected(OperationsFailure.STALE))
            model.cancelRoleEditor();model.editRole(repo.role);model.changeRoleName("New editor draft")
            if(delete)removal!!(OperationsResult.Value(Unit)) else rename!!(OperationsResult.Value(repo.role))
            assertEquals("New editor draft",model.roleName);assertNotNull(model.editingRole);assertNull(model.deletingRole)
        }
    }
    @Test fun lifecycleClearRejectsOldWriteAndReloadCallbacks(){
        for(at in listOf("lock","generation","action","permission"))for(reload in listOf(false,true)){
            session=SessionSnapshot(SessionState.READY,1);action=action.copy(id="a",status=ActionStatus.PREPARATION)
            repo.deferRead=false;model.clear();open();model.changeRoleName("Sensitive draft");model.saveRoleRename()
            val oldWrite=repo.rename!!
            if(reload){oldWrite(OperationsResult.Rejected(OperationsFailure.STALE));repo.deferRead=true;model.reloadRoleEditor()}
            val oldRead=repo.reload
            when(at){"lock"->session=SessionSnapshot(SessionState.LOCKED,2);"generation"->session=SessionSnapshot(SessionState.READY,2);"action"->action=action.copy(id="b");else->session=SessionSnapshot(SessionState.BLOCKED,2)}
            model.clear() // Existing ProductViewModel lifecycle hook, not a new authority.
            oldWrite(OperationsResult.Value(repo.role));if(reload)oldRead!!(OperationsResult.Value(repo.board()))
            assertNull(model.editingRole);assertNull(model.deletingRole);assertEquals("",model.roleName);assertFalse(model.busy)
        }
    }
    @Test fun unresolvedWriteDoesNotOptimisticallyFinishOrRetry(){
        open(delete=true);model.confirmRoleDelete();model.confirmRoleDelete()
        assertEquals(1,repo.writes);assertTrue(model.busy);assertNotNull(model.deletingRole);assertTrue(messages.isEmpty())
        model.clear();assertNull(model.deletingRole)
    }
}
