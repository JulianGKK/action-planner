package org.pag.prototype
import android.app.Instrumentation
import android.os.*
import org.pag.domain.*

/** Isolated synthetic object graphs, excludes DB/bitmaps/real credentials. */
class AssessmentMemoryProbe:Instrumentation(){
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private var retained:List<WorkspaceRead>?=null
    private fun used()=Runtime.getRuntime().let{it.totalMemory()-it.freeMemory()}
    private fun gc(){System.gc();System.runFinalization();SystemClock.sleep(120)}
    private fun graph(n:Int,t:Int,m:Int,token:Int):WorkspaceRead {
        val a="synthetic-$token"
        val people=List(n){i->val p=Person("$a-person-$i",a,"Synthetic pseudonym $token $i",i.toLong());OperationalPerson(p,PersonRolePlan(emptySet(),null),PersonOperationalState(a,p.id,null,OperationalStatus.NO_STATUS,null,1))}
        val tasks=List(t){i->Task("$a-task-$i",a,"Synthetic task $token $i",TaskPriority.NORMAL,TaskStatus.OPEN,null,null,null,null,null,1,1,null)}
        val events=tasks.map{TaskEvent("${it.id}-created",a,it.id,TaskEventType.CREATED,1)}
        val material=List(m){i->MaterialItem("$a-mat-$i",a,"Synthetic item $token $i",null,null,null,null,null,1)}
        val expenses=List(m){i->Expense("$a-exp-$i",a,"Synthetic expense $token $i",100,listOf(ExpensePayment("Synthetic payer $token $i",100)),1)}
        return WorkspaceRead(a,ActionStatus.ACTIVE,1,setOf(WorkspacePart.TASKS,WorkspacePart.MATERIAL_BOARD,WorkspacePart.EXPENSE_BOARD),OperationalBoard(a,ActionStatus.ACTIVE,emptyList(),people),TaskBoard(a,tasks,events),materialBoard=MaterialBoard(a,material),expenseBoard=ExpenseBoard(a,expenses))
    }
    override fun onStart(){val out=Bundle();try{
        check(targetContext.packageName=="org.pag.perf.p11probe")
        for((size,c) in listOf("small" to Triple(5,10,5),"typical" to Triple(20,100,20),"large" to Triple(200,1000,200))) {
            repeat(20){r->retained=null;gc();val before=used();val start=System.nanoTime();retained=List(16){graph(c.first,c.second,c.third,r*16+it)};val built=System.nanoTime()-start;gc();val delta=used()-before
                sendStatus(0,Bundle().apply{putString("stream","MEMORY,$size,$r,${delta/16.0},${built/16e6}\n")})
                check(retained!!.sumOf{it.tasks!!.tasks.size}==16*c.second)
            }
        }
        retained=null;gc();out.putString("stream","MEMORY_PASS\n")
    }catch(_:Throwable){out.putString("stream","MEMORY_FAIL\n")}finally{retained=null}
    finish(if(out.getString("stream")=="MEMORY_PASS\n")0 else 1,out)}
}
