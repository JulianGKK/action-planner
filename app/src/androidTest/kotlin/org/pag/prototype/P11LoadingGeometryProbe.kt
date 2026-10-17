package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.pag.prototype.product.PagLoadingState
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic geometry only, no repository/authentication or product storage access. */
class P11LoadingGeometryProbe:Instrumentation() {
    private var label="baseline"
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);label=arguments?.getString("p11Label")?:"baseline";start()}
    override fun onStart() {
        val result=Bundle();var activity:MainActivity?=null
        try {
            check(targetContext.packageName=="org.pag.perf.p11probe")
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val host=activity!!
            for(scale in listOf(1f,1.5f,2f,3.5f))repeat(10){index->
                val marker=AtomicInteger(-1)
                runOnMainSync{host.setContent{MaterialTheme{val density=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                        key(scale,index){Column(Modifier.width(300.dp)){PagLoadingState();Text("Synthetic marker",Modifier.onGloballyPositioned{marker.set(it.positionInRoot().y.toInt())})}}
                    }
                }}}
                waitForIdleSync();SystemClock.sleep(50);val before=marker.get();check(before>=0)
                SystemClock.sleep(250);waitForIdleSync();val after=marker.get()
                sendStatus(0,Bundle().apply{putString("stream","P11_GEOMETRY,$label,$scale,$index,$before,$after\n")})
            }
            result.putString("result","PASS");finish(Activity.RESULT_OK,result)
        }catch(_:Throwable){result.putString("result","FAIL");finish(Activity.RESULT_CANCELED,result)}
        finally{activity?.let{runOnMainSync{it.finish()}}}
    }
}
