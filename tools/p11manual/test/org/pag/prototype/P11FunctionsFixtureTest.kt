package org.pag.prototype

import org.pag.prototype.p11fixture.P11FunctionsFixture

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.prototype.product.ProductViewModel

class P11FunctionsFixtureTest {
    @Test fun syntheticEntryAndUnlockPublishTheActionAndWorkspace() {
        val session = P11FunctionsFixture.Session(syntheticUnlock = true).apply { lock() }
        val operations = P11FunctionsFixture.Ops()
        val model = ProductViewModel(session, P11FunctionsFixture.Actions(), ProductStorageState.READY_TO_OPEN,
            P11FunctionsFixture.People(operations), operationsRepository = operations,
            taskRepository = P11FunctionsFixture.Tasks()) { session.authenticate(Experiment.PER_OPERATION) }
        model.unlock()
        assertEquals(listOf("Übungsaktion"), model.actions.map { it.title })
        model.detail("synthetic")
        assertEquals(2, model.workspace!!.operations!!.persons.size)
        assertEquals(2, model.persons.size)
        model.lock()
        assertNull(model.workspace)
        model.unlock()
        assertEquals(SessionState.READY, model.snapshot.state)
        assertNull(model.selected)
        assertEquals(listOf("Übungsaktion"), model.actions.map { it.title })
    }
    @Test fun manualPersonListAndDetailContainExactlyTheTwoInventedPeople() {
        val people = P11FunctionsFixture.People(P11FunctionsFixture.Ops())
        people.listPersons("synthetic") {
            assertEquals(listOf("Motte", "Klee"), (it as PersonResult.Value).value.map { person -> person.pseudonym })
        }
        people.getPerson("synthetic", "q") { assertEquals("Klee", (it as PersonResult.Value).value!!.pseudonym) }
        people.listPersons("another-action") { assertTrue((it as PersonResult.Value).value.isEmpty()) }
        P11FunctionsFixture.People().listPersons("synthetic") { assertTrue((it as PersonResult.Value).value.isEmpty()) }
    }
    @Test fun bothPeopleCanChangeWithoutMutatingTheOtherAndResetIsFresh() {
        val ops = P11FunctionsFixture.Ops()
        val initial = ops.person
        val klee = ops.other.state!!
        ops.updateSnapshot("synthetic", "q", klee, OperationalStatus.ON_SITE, "plan", "") {
            assertTrue(it is OperationsResult.Value)
        }
        assertEquals(initial, ops.person)
        assertEquals("plan", ops.other.state!!.currentRoleId)
        ops.updateSnapshot("synthetic", "q", klee, OperationalStatus.IN_CUSTODY, null, "") {
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE), it)
        }
        ops.updateSnapshot("synthetic", "p", ops.person.state!!, OperationalStatus.IN_CUSTODY, "current", "") {
            assertTrue(it is OperationsResult.Value)
        }
        assertNull(ops.person.state!!.currentRoleId)
        val reset = P11FunctionsFixture.Ops()
        assertEquals("current", reset.person.state!!.currentRoleId)
        assertEquals(OperationalStatus.ON_SITE, reset.person.state!!.status)
        assertNull(reset.other.state!!.currentRoleId)
    }

    @Test fun manualUnlockIsExplicitAndUsesNoPlatformAuthentication() {
        val session = P11FunctionsFixture.Session(syntheticUnlock = true)
        var observed = session.snapshot()
        val subscription = session.observe { observed = it }
        session.lock()
        assertEquals(SessionState.LOCKED, observed.state)
        session.authenticate(Experiment.PER_OPERATION)
        assertEquals(SessionState.READY, observed.state)
        subscription.cancel()
        session.lock()
        assertEquals(SessionState.READY, observed.state)
        val automatic = P11FunctionsFixture.Session()
        assertThrows(IllegalStateException::class.java) { automatic.authenticate(Experiment.PER_OPERATION) }
    }
}
