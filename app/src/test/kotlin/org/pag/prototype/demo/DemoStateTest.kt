package org.pag.prototype.demo

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.prototype.product.activeHubCards

class DemoStateTest {
    private var now = 1_800_000_000_000L
    private val model = DemoState { now }
    private fun prep() = model.begin(DemoScenario.PREPARATION)
    private fun active() = model.begin(DemoScenario.ACTIVE)
    @Test fun homeHasNoWorldOrSession() { assertNull(model.action); assertNull(model.board); assertNull(model.taskBoard); assertNull(model.scenario) }
    @Test fun preparationIsPreplannedButNotActive() {
        prep(); assertEquals(ActionStatus.PREPARATION, model.action!!.status); assertEquals(DemoPage.OVERVIEW, model.page)
        assertEquals(6, model.board!!.persons.size); assertTrue(model.board!!.persons.all { it.state == null && it.plan.primaryRoleId != null })
        assertTrue(model.taskBoard!!.active.isNotEmpty())
    }
    @Test fun quickstartShowsDifferentRealDomainStates() {
        active(); assertEquals(DemoPage.HUB, model.page)
        assertEquals(3, model.board!!.count(OperationalStatus.ON_SITE))
        assertEquals(1, model.board!!.count(OperationalStatus.NO_STATUS)); assertEquals(1, model.board!!.count(OperationalStatus.IN_CUSTODY)); assertEquals(1, model.board!!.count(OperationalStatus.NOT_REACHABLE))
        assertTrue(model.taskBoard!!.active.any { it.priority == TaskPriority.HIGH }); assertTrue(model.taskBoard!!.active.any { it.status == TaskStatus.IN_PROGRESS }); assertTrue(model.taskBoard!!.closed.any { it.status == TaskStatus.DONE })
    }
    @Test fun personCreationAndEditingAreLocalAndDuplicateSafe() {
        prep(); model.edit(DemoEditor.PERSON); model.savePerson("  Demo-Fink  ")
        val person = model.board!!.persons.last(); assertEquals("Demo-Fink", person.person.pseudonym)
        model.edit(DemoEditor.PERSON, person.person.id); model.savePerson("Demo-Spatz")
        assertEquals("Demo-Spatz", model.board!!.persons.last().person.pseudonym)
        model.edit(DemoEditor.PERSON); model.savePerson("motte"); assertNotNull(model.error); assertEquals(7, model.board!!.persons.size)
        val other = DemoState { now }; other.begin(DemoScenario.PREPARATION); assertEquals(6, other.board!!.persons.size)
    }
    @Test fun rolesAndTemplatesOnlyChangeThisWorld() {
        prep(); model.addRole("Presse"); assertTrue(model.board!!.roles.any { it.name == "Presse" })
        val count = model.board!!.roles.size; model.addRole(" presse "); assertEquals(count, model.board!!.roles.size); assertNotNull(model.error)
    }
    @Test fun plannedAndCurrentFunctionsStaySeparate() {
        prep(); val p = model.board!!.persons.first(); model.edit(DemoEditor.PLAN, p.person.id)
        model.savePlan(setOf("r1", "r2"), "r2"); model.startAction()
        val started = model.board!!.persons.first(); assertEquals("r2", started.plan.primaryRoleId); assertEquals("r2", started.state!!.currentRoleId)
        assertEquals(OperationalStatus.NO_STATUS, started.state!!.status); assertEquals(0, model.board!!.roleCount("r2"))
        model.edit(DemoEditor.LIVE, p.person.id); model.saveLive(OperationalStatus.ON_SITE, "r1", "")
        assertEquals("r2", model.board!!.persons.first().plan.primaryRoleId); assertEquals("r1", model.board!!.persons.first().state!!.currentRoleId)
    }
    @Test fun invalidPlansCannotChangeTheWorld() {
        prep(); val old = model.board; model.edit(DemoEditor.PLAN, "p0"); model.savePlan(setOf("r0"), "r1"); assertSame(old, model.board)
        model.savePlan(setOf("foreign"), "foreign"); assertSame(old, model.board)
    }
    @Test fun startingActionCreatesNoStatusSnapshotsAndPreservesTasks() {
        prep(); val tasks = model.taskBoard; model.startAction()
        assertEquals(ActionStatus.ACTIVE, model.action!!.status); assertEquals(DemoPage.HUB, model.page)
        assertTrue(model.board!!.persons.all { it.state!!.status == OperationalStatus.NO_STATUS && it.state!!.updatedAt == 0L && it.state!!.currentRoleId == it.plan.primaryRoleId })
        assertSame(tasks, model.taskBoard); val b = model.board; model.startAction(); assertSame(b, model.board)
    }
    @Test fun custodyRemovesCurrentFunctionAndKeepsPlan() {
        active(); val plan = model.board!!.persons.first().plan; model.edit(DemoEditor.LIVE, "p0"); model.saveLive(OperationalStatus.IN_CUSTODY, "r0", "Demo-Punkt")
        val p = model.board!!.persons.first(); assertNull(p.state!!.currentRoleId); assertEquals("Demo-Punkt", p.state!!.currentPlaceLabel); assertEquals(plan, p.plan)
    }
    @Test fun unreachableRemovesFunctionAndIrrelevantPlace() {
        active(); model.edit(DemoEditor.LIVE, "p0"); model.saveLive(OperationalStatus.NOT_REACHABLE, "r0", "Demo-Punkt")
        val p = model.board!!.persons.first(); assertNull(p.state!!.currentRoleId); assertNull(p.state!!.currentPlaceLabel)
    }
    @Test fun hubSummariesReactToPersonAndTaskChanges() {
        active(); fun cards() = activeHubCards(model.action!!, model.board!!, model.taskBoard!!)
        val old = cards(); model.edit(DemoEditor.LIVE, "p3"); model.saveLive(OperationalStatus.ON_SITE, "r1", "")
        assertNotEquals(old[0].summary, cards()[0].summary); assertNotEquals(old[2].summary, cards()[2].summary)
        model.transitionTask("t0", TaskStatus.DONE); assertNotEquals(old[1], cards()[1])
    }
    @Test fun taskDoneCancelledAndReopenReusePureP5a() {
        active(); now++; model.transitionTask("t0", TaskStatus.DONE)
        assertEquals(now, model.taskBoard!!.tasks.first().completedAt)
        model.transitionTask("t0", TaskStatus.OPEN); assertNull(model.taskBoard!!.tasks.first().completedAt)
        model.transitionTask("t0", TaskStatus.CANCELLED); assertNull(model.taskBoard!!.tasks.first().completedAt)
        model.transitionTask("t0", TaskStatus.IN_PROGRESS); assertEquals(TaskStatus.CANCELLED, model.taskBoard!!.tasks.first().status)
        model.transitionTask("t0", TaskStatus.OPEN); assertEquals(TaskStatus.OPEN, model.taskBoard!!.tasks.first().status)
        assertEquals(listOf(TaskEventType.COMPLETED, TaskEventType.REOPENED, TaskEventType.CANCELLED, TaskEventType.REOPENED), model.taskBoard!!.events.map { it.type })
    }
    @Test fun taskCreationEditingAndAssignmentValidation() {
        prep(); model.edit(DemoEditor.TASK); model.saveTask(TaskInput("Demo-Aufgabe", assigneeRoleId = "r1", affectedPersonId = "p2"))
        val t = model.taskBoard!!.tasks.last(); assertEquals(TaskStatus.OPEN, t.status)
        model.edit(DemoEditor.TASK, t.id); model.saveTask(t.input().copy(title = "Demo-geändert")); assertEquals("Demo-geändert", model.taskBoard!!.tasks.last().title)
        val old = model.taskBoard; model.saveTask(TaskInput("Ungültig", assigneePersonId = "p0", assigneeRoleId = "r0")); assertSame(old, model.taskBoard)
        model.saveTask(TaskInput("Ungültig", affectedPersonId = "foreign")); assertSame(old, model.taskBoard)
    }
    @Test fun activeActionAndPlanningAreReadOnly() {
        active(); val a = model.action; val b = model.board
        model.edit(DemoEditor.ACTION); assertNull(model.editor)
        model.saveAction(NewAction("Changed", "Changed", now, now)); assertSame(a, model.action)
        model.edit(DemoEditor.PERSON); assertNull(model.editor); model.savePerson("Changed"); assertSame(b, model.board)
        model.savePlan(setOf("r1"), "r1"); assertSame(b, model.board)
    }
    @Test fun prepActionDetailsCanChangeButInvalidTimesCannot() {
        prep(); model.edit(DemoEditor.ACTION); model.saveAction(NewAction("Demo-geändert", "Demo-Beschreibung", now, now + 1))
        assertEquals("Demo-geändert", model.action!!.title); val a = model.action
        model.saveAction(NewAction("x", "x", now, now - 1)); assertSame(a, model.action)
    }
    @Test fun resetRestoresOriginalScenarioAndClearsEditorsAndHistory() {
        active(); val a = model.action; val b = model.board; val tasks = model.taskBoard
        model.edit(DemoEditor.LIVE, "p0"); model.saveLive(OperationalStatus.LEFT, null, ""); model.transitionTask("t0", TaskStatus.DONE)
        model.edit(DemoEditor.TASK); model.reset()
        assertEquals(a, model.action); assertEquals(b, model.board); assertEquals(tasks, model.taskBoard)
        assertNull(model.editor); assertNull(model.personId); assertNull(model.taskId)
    }
    @Test fun resetAfterStartingPrepReturnsToPreparationScenario() { prep(); model.startAction(); model.reset(); assertEquals(ActionStatus.PREPARATION, model.action!!.status); assertEquals(DemoPage.OVERVIEW, model.page) }
    @Test fun endAndFreshProcessLikeInstanceDiscardWorld() {
        prep(); model.edit(DemoEditor.PERSON); model.savePerson("Demo-Neu"); model.end()
        assertNull(model.action); assertNull(model.board); assertNull(model.taskBoard); assertNull(model.scenario); assertNull(model.editor)
        val fresh = DemoState { now }; assertNull(fresh.action); fresh.begin(DemoScenario.PREPARATION); assertEquals(6, fresh.board!!.persons.size)
    }
    @Test fun scenariosReplaceEachOtherAndNavigationDiscardsForms() {
        prep(); model.edit(DemoEditor.PERSON); model.navigate(DemoPage.TASKS); assertNull(model.editor)
        model.begin(DemoScenario.ACTIVE); assertEquals(DemoPage.HUB, model.page)
        model.navigate(DemoPage.ROLES); assertEquals(DemoPage.HUB, model.page)
        model.navigate(DemoPage.DETAILS); assertEquals(DemoPage.DETAILS, model.page)
    }
    @Test fun tourIsOptionalNonBlockingAndRamOnly() {
        model.begin(DemoScenario.TOUR); assertEquals(0, model.tourStep)
        model.navigate(DemoPage.PERSONS); assertEquals(0, model.tourStep)
        repeat(3) { model.nextTour() }; assertEquals(3, model.tourStep); model.nextTour(); assertNull(model.tourStep)
        model.reset(); assertEquals(0, model.tourStep); model.skipTour(); assertNull(model.tourStep)
    }
    @Test fun seedTimesFollowInjectedClockAndResetRefreshesThem() {
        prep(); assertEquals(now + 3_600_000, model.action!!.startAt)
        now += 86_400_000; model.reset(); assertEquals(now + 3_600_000, model.action!!.startAt)
    }
}
