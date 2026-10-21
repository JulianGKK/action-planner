package org.pag.data.product

import android.os.Bundle
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** PREPARED ONLY. Metrics are observations, never a leak-free tolerance assertion. */
class P11ActiveCoreNativeTest {
    @Test fun productiveB2StatementCloseReleasesCursor_GREEN32() {
        P11SyntheticFixture(app).use { it.productStatementCleanupProbe() }
    }
    /** Expected RED with pinned 4.19.1: close only sets closed; reset closes cursor.
     * Do not ignore/skip this assertion or treat connection close as cursor evidence. */
    @Test fun statementCloseMustReleaseQueryCursor_RED32() {
        P11SyntheticFixture(app).use { assertTrue("#32 statement.close leaves cursor open",it.statementCloseReleasesCursor()) }
    }
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.also {
        check(it.packageName=="org.pag.data.unlockprobe")
    }
    @Test fun repeatedRealCoreReads() {
        for ((people,tasks) in listOf(4 to 8,64 to 200,200 to 1000)) {
            P11SyntheticFixture(app,taskCountPerAction=tasks,personCountPerAction=people).use { f ->
                val loader=f.activeCore()
                repeat(22) { sample ->
                    val start=System.nanoTime()
                    var value:ActiveCoreResult?=null
                    assertTrue(loader.load(f.activeId) { value=it })
                    val core=(value as ActiveCoreResult.Value).core
                    assertEquals(people.toLong(),core.people.filteredCount)
                    assertEquals(minOf(people,32),core.people.items.size)
                    assertEquals(people>32,core.people.hasMore)
                    assertEquals(minOf(tasks,32),core.tasks.items.size)
                    assertEquals(tasks>32,core.tasks.hasMore)
                    val memory=Debug.MemoryInfo().also(Debug::getMemoryInfo)
                    instrumentation.sendStatus(0,Bundle().apply {
                        putString("stream","P11_CORE,$people,$tasks,${sample-2},${System.nanoTime()-start},${File("/proc/self/fd").list()?.size},${Debug.getNativeHeapAllocatedSize()},${memory.totalPss},${Debug.getRuntimeStat("art.gc.gc-count")}\n")
                    })
                }
            }
        }
    }
    @Test fun realReaderRejectsNonActiveAndRevokeBeforePublication() {
        val main=ArrayDeque<Runnable>()
        P11SyntheticFixture(app).use { f ->
            val direct=f.activeCore()
            for(id in listOf(f.preparationId,f.completedId,"missing-synthetic")) {
                var result:ActiveCoreResult?=null
                assertTrue(direct.load(id){result=it})
                assertTrue(result is ActiveCoreResult.Rejected)
            }
            var callbacks=0
            val queued=f.activeCore(main=Executor{main.add(it)})
            assertTrue(queued.load(f.activeId){callbacks++})
            f.revoke()
            while(main.isNotEmpty())main.removeFirst().run()
            assertEquals(0,callbacks)
        }
    }
}
