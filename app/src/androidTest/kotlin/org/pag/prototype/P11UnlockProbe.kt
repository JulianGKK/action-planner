package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import org.pag.data.product.P11SyntheticFixture
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

/** Real coordinator/product maintenance; synthetic authentication and private synthetic keys only. */
class P11UnlockProbe:Instrumentation() {
    private var label="baseline"
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);label=arguments?.getString("p11Label")?:"baseline";check(label in setOf("baseline","after"));start()}
    private fun emit(name:String,index:Int,nanos:Long){sendStatus(0,Bundle().apply{putString("stream","P11,$label,$name,$index,${nanos/1000000.0}\n")})}
    override fun onStart() {
        val result=Bundle();var phase="guard";var activity:MainActivity?=null
        val worker=Executors.newSingleThreadExecutor()
        try {
            check(targetContext.packageName=="org.pag.perf.p11probe")
            P11SyntheticFixture(targetContext,worker,targetContext.mainExecutor).use{fixture->
                activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                val host=activity!!
                repeat(10){index->
                    phase="unlock"
                    fixture.unlockProbe(worker,targetContext.mainExecutor){name,ns->emit(name,index,ns)}.use{probe->
                        val ready=AtomicLong();val publication=AtomicLong();val processing=AtomicLong();val commit=AtomicLong();val layout=AtomicLong();val compositions=AtomicLong()
                        val drawn=CountDownLatch(1);val frame=AtomicLong()
                        val port=object:ActionRepository by probe.actions {
                            override fun listActions(publish:(List<Action>)->Unit)=probe.actions.listActions{rows->
                                val start=System.nanoTime();publish(rows);processing.set(System.nanoTime()-start);publication.set(System.nanoTime())
                            }
                        }
                        lateinit var model:ProductViewModel
                        lateinit var listener:ViewTreeObserver.OnDrawListener
                        runOnMainSync {
                            model=ProductViewModel(probe.session,port,ProductStorageState.READY_TO_OPEN,fixture.persons){}
                            host.setContent{CompositionLocalProvider(LocalProductObservation provides {name->
                                when(name){"COMPOSITION"->compositions.incrementAndGet();"LIST_COMMIT"->commit.compareAndSet(0,System.nanoTime());"LIST_LAYOUT"->layout.compareAndSet(0,System.nanoTime())}
                            }){ProductScreen(model)}}
                            listener=ViewTreeObserver.OnDrawListener {
                                if(layout.get()!=0L && frame.compareAndSet(0,System.nanoTime()))drawn.countDown()
                            }
                            host.window.decorView.viewTreeObserver.addOnDrawListener(listener)
                        }
                        val observation=probe.session.observe{if(it.state==SessionState.READY)ready.compareAndSet(0,System.nanoTime())}
                        runOnMainSync{
                            check(host.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                            (probe.session as SessionLifecycle).foreground(true)
                            probe.session.authenticate(Experiment.STORAGE_UNWRAP)
                        }
                        check(drawn.await(90,TimeUnit.SECONDS));val auth=probe.authenticatedNanos;check(auth>0)
                        emit("AUTH_TO_READY",index,ready.get()-auth)
                        emit("AUTH_TO_LIST_PUBLICATION",index,publication.get()-auth)
                        emit("VM_PROCESS",index,processing.get())
                        emit("PUBLICATION_TO_COMMIT",index,commit.get()-publication.get())
                        emit("PUBLICATION_TO_LAYOUT",index,layout.get()-publication.get())
                        emit("AUTH_TO_LIST_DRAW",index,frame.get()-auth)
                        emit("READY_TO_LIST_DRAW",index,frame.get()-ready.get())
                        runOnMainSync{host.window.decorView.viewTreeObserver.removeOnDrawListener(listener);probe.session.lock()}
                        check(model.selected==null && model.workspace==null && model.documents.pdfPage==null)
                        observation.cancel()
                    }
                }
            }
            result.putString("result","PASS");finish(Activity.RESULT_OK,result)
        }catch(_:Throwable){result.putString("result","FAIL");result.putString("phase",phase);finish(Activity.RESULT_CANCELED,result)}
        finally{activity?.let{runOnMainSync{it.finish()}};worker.shutdownNow();worker.awaitTermination(5,TimeUnit.SECONDS)}
    }
}
