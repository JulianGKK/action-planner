package org.pag.prototype
import android.app.Instrumentation
import android.os.Bundle
import org.pag.domain.*

/** Method CPU comparison only, not UI latency. Frozen pre-fix algorithm vs unsorted scan. */
class ProjectionProfileProbe:Instrumentation(){
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){val result=Bundle();try{
        check(targetContext.packageName=="org.pag.perf.p11probe")
        val b=TaskBoard("synthetic",List(1000){i->Task("task-$i","synthetic","Synthetic",TaskPriority.entries[i%3],TaskStatus.entries[i%4],null,null,if(i%7==0)"person-${i%200}" else null,null,null,i.toLong(),i.toLong(),null)},emptyList())
        fun old():Int=(0 until 200).sumOf{i->b.tasks.filter{TaskRules.active(it.status)}.sortedWith(TaskRules.order).count{it.affectedPersonId=="person-$i"}}
        fun scan():Int=(0 until 200).sumOf{i->b.tasks.count{TaskRules.active(it.status)&&it.affectedPersonId=="person-$i"}}
        repeat(5){check(old()==scan())}
        repeat(20){r->for(kind in if(r%2==0)listOf("SORT_EACH","SCAN") else listOf("SCAN","SORT_EACH")){
            val start=System.nanoTime();val n=if(kind=="SORT_EACH")old() else scan();val ms=(System.nanoTime()-start)/1e6
            check(n==old())
            sendStatus(0,Bundle().apply{putString("stream","PROJECTION,$kind,$r,$ms\n")})
        }}
        result.putString("stream","PROJECTION_PASS\n")
    }catch(_:Throwable){result.putString("stream","PROJECTION_FAIL\n")};finish(0,result)}
}
