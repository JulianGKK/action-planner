package org.pag.data.product

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.pag.domain.*

/** Reproducible synthetic SQLCipher baseline; wall times are not functional-test assertions. */
class P11DatabaseBenchmark {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val label=InstrumentationRegistry.getArguments().getString("p11Label","baseline").also{check(it in setOf("baseline","after"))}
    private fun emit(group:String,index:Int,nanos:Long) {
        instrumentation.sendStatus(0,Bundle().apply{putString("stream","P11,$label,$group,$index,${nanos/1000000.0}\n")})
    }
    @Test fun databasePhasesAndAreaReads() {
        check(instrumentation.targetContext.packageName=="org.pag.data.unlockprobe")
        P11SyntheticFixture(instrumentation.targetContext).use{fixture->
            repeat(22){i->
                val start=System.nanoTime()
                fixture.databaseRead{phase,nanos->emit("DB_$phase",i-2,nanos)}
                emit("DB_TOTAL",i-2,System.nanoTime()-start)
            }
            val queries:List<Pair<String,()->Unit>> = listOf(
                "ACTION_LIST" to {fixture.actions.listActions{assertEquals(3,it.size)}},
                "ACTION_DETAIL" to {fixture.actions.getAction(fixture.activeId){assertNotNull(it)}},
                "PERSONS" to {fixture.persons.listPersons(fixture.activeId){assertTrue(it is PersonResult.Value)}},
                "TASKS" to {fixture.tasks.board(fixture.activeId){assertTrue(it is OperationsResult.Value)}},
                "ROLES" to {fixture.operations.board(fixture.activeId){assertTrue(it is OperationsResult.Value)}},
                "PROTOCOL" to {fixture.protocol.listTimeline(fixture.activeId){assertTrue(it is ProtocolResult.Value)}},
                "MATERIAL" to {fixture.material.board(fixture.activeId){assertTrue(it is OperationsResult.Value)}},
                "COSTS" to {fixture.expenses.board(fixture.activeId){assertTrue(it is OperationsResult.Value)}},
                "MEMORY" to {fixture.memoryReports.list(fixture.activeId){assertTrue(it is OperationsResult.Value)}},
                "DOCUMENT_LIST" to {fixture.documents.list(fixture.activeId){assertTrue(it is DocumentResult.Value)}}
            )
            repeat(10){i->for((name,query) in queries){val start=System.nanoTime();query();emit(name,i,System.nanoTime()-start)}}
        }
    }
}
