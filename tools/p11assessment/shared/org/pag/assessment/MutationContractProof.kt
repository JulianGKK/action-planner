package org.pag.assessment

import org.pag.domain.*

/** Test-only protocol, no domain payload or production mutation integration. Main-thread confined. */
class MutationContractProof(private val access:StorageAccess,private val binding:()->Binding?) {
    data class Binding(val action:String,val generation:Long,val permissionRevision:Long)
    enum class Part { ACTION, PEOPLE, OPERATIONS, TASKS, TASK_COUNTS, MATERIAL, EXPENSES, PROTOCOL_HEADERS, MEMORY_HEADERS, DOCUMENT_HEADERS }
    enum class Outcome { COMMITTED, ROLLED_BACK, UNKNOWN }
    class Write internal constructor(internal val epoch:Long,internal val binding:Binding,internal val parts:Set<Part>)
    class Read internal constructor(internal val epoch:Long,internal val binding:Binding)
    private var epoch=0L
    private var pending:Write?=null
    private var committed:Write?=null
    private var scope:Binding?=null
    private var activeRead:Read?=null
    private val clean=mutableSetOf<Part>()
    private fun context():StorageContext? {
        val c=access.acquire(AccessScope.READY) ?: run{revoke();return null}
        val b=binding() ?: run{revoke();return null}
        if(scope!=null && scope!=b){revoke();return null}
        if(c.generation!=b.generation){revoke();return null}
        if(scope==null)scope=b
        return c
    }
    fun revoke(){epoch=Math.addExact(epoch,1);pending=null;committed=null;scope=null;activeRead=null;clean.clear()}
    fun begin(parts:Set<Part>):Write? {
        val c=context() ?: return null
        if(pending!=null || parts.isEmpty() || !c.check(AccessPoint.OPERATION))return null
        epoch=Math.addExact(epoch,1);clean.removeAll(parts);committed=null;activeRead=null
        return Write(epoch,scope!!,parts.toSet()).also{pending=it}
    }
    /** Trusted Data notification AFTER real transaction outcome, never an authorization/commit gate. */
    fun outcome(write:Write,outcome:Outcome):Boolean {
        if(pending!==write || epoch!=write.epoch || scope!=write.binding)return false
        pending=null
        when(outcome){Outcome.COMMITTED->committed=write;Outcome.ROLLED_BACK->committed=null;Outcome.UNKNOWN->{clean.clear();committed=null}}
        return true
    }
    fun startRead():Read? {context() ?: return null;if(pending!=null)return null;return activeRead ?: Read(epoch,scope!!).also{activeRead=it}}
    fun publishRead(read:Read,parts:Set<Part>):Boolean {
        val c=context() ?: return false
        if(activeRead!==read || read.epoch!=epoch || read.binding!=scope || pending!=null)return false
        return c.publish{if(activeRead===read && read.epoch==epoch && read.binding==scope && pending==null){clean.addAll(parts);committed=null;activeRead=null}}
    }
    fun canHit(part:Part):Boolean {context() ?: return false;return pending==null && part in clean}
    /** Full confirmed projection patch only; partial/late patches cannot mark dirty data current. */
    fun publishCommit(write:Write,complete:Set<Part>,apply:()->Unit):Boolean {
        val c=context() ?: return false
        if(committed!==write || epoch!=write.epoch || scope!=write.binding || complete!=write.parts)return false
        return c.publish{if(committed===write && epoch==write.epoch && scope==write.binding){apply();clean.addAll(complete);committed=null}}
    }
}
