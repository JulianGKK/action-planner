package org.pag.prototype.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.pag.domain.*

fun interface DemoClock { fun now(): Long }
enum class DemoScenario { PREPARATION, ACTIVE, TOUR }
enum class DemoPage { OVERVIEW, PERSONS, TASKS, ROLES, HUB, FUNCTIONS, DETAILS }
enum class DemoEditor { PERSON, PLAN, LIVE, TASK, ROLE, ACTION }

/** Only immutable domain values in RAM. No session, repository, key, context or persistence capability. */
class DemoState(private val clock: DemoClock = DemoClock { System.currentTimeMillis() }) {
    var scenario by mutableStateOf<DemoScenario?>(null); private set
    var action by mutableStateOf<Action?>(null); private set
    var board by mutableStateOf<OperationalBoard?>(null); private set
    var taskBoard by mutableStateOf<TaskBoard?>(null); private set
    var page by mutableStateOf(DemoPage.OVERVIEW); private set
    var editor by mutableStateOf<DemoEditor?>(null); private set
    var personId by mutableStateOf<String?>(null); private set
    var taskId by mutableStateOf<String?>(null); private set
    var revision by mutableIntStateOf(0); private set
    var tourStep by mutableStateOf<Int?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    private var nextId = 0
    fun begin(value: DemoScenario) {
        end(); scenario = value
        val now = clock.now()
        val active = value != DemoScenario.PREPARATION
        val status = if (active) ActionStatus.ACTIVE else ActionStatus.PREPARATION
        action = Action("demo-action", "Demo-Aktion Innenstadt", "Fiktive Übungsaktion mit Beispieldaten. Alle Änderungen bleiben nur im Arbeitsspeicher.", if (active) now - 1_800_000 else now + 3_600_000, now + 14_400_000, now, status)
        val roles = listOf("Blockierende", "Support", "Hummel", "Gesa-Support").mapIndexed { i, name -> Role("r$i", "demo-action", name, now) }
        val people = listOf("Motte", "Meise", "Falter", "Amsel", "Klee", "Rabe").mapIndexed { i, name ->
            val role = roles[i % roles.size].id
            val state = if (!active) null else {
                val live = when (i) { 3 -> OperationalStatus.NO_STATUS; 4 -> OperationalStatus.NOT_REACHABLE; 5 -> OperationalStatus.IN_CUSTODY; else -> OperationalStatus.ON_SITE }
                PersonOperationalState("demo-action", "p$i", role.takeIf { OperationalRules.roleAllowed(live) }, live,
                    if (live == OperationalStatus.IN_CUSTODY) "Demo-Sammelpunkt" else null, if (live == OperationalStatus.NO_STATUS) 0 else now)
            }
            OperationalPerson(Person("p$i", "demo-action", name, now), PersonRolePlan(setOf(role), role), state)
        }
        board = OperationalBoard("demo-action", status, roles, people)
        val tasks = listOf("Kommunikationswege prüfen", "Material für den Treffpunkt vorbereiten", "Pausen abstimmen", "Planung besprechen").mapIndexed { i, title ->
            val state = if (active && i == 1) TaskStatus.IN_PROGRESS else if (active && i == 3) TaskStatus.DONE else TaskStatus.OPEN
            Task("t$i", "demo-action", title, if (i == 0) TaskPriority.HIGH else TaskPriority.NORMAL, state, null, roles[i % roles.size].id, null, null, null, now, now, now.takeIf { state == TaskStatus.DONE })
        }
        taskBoard = TaskBoard("demo-action", tasks, emptyList())
        page = if (active) DemoPage.HUB else DemoPage.OVERVIEW
        tourStep = 0.takeIf { value == DemoScenario.TOUR }
    }
    fun reset() { scenario?.let(::begin) }
    fun end() {
        action = null; board = null; taskBoard = null; scenario = null; page = DemoPage.OVERVIEW
        editor = null; personId = null; taskId = null; tourStep = null; error = null; nextId = 0; revision++
    }
    fun navigate(value: DemoPage) {
        val a = action ?: return
        if (a.status == ActionStatus.PREPARATION && value !in setOf(DemoPage.OVERVIEW, DemoPage.PERSONS, DemoPage.TASKS, DemoPage.ROLES)) return
        if (a.status == ActionStatus.ACTIVE && value in setOf(DemoPage.OVERVIEW, DemoPage.ROLES)) return
        editor = null; personId = null; taskId = null; error = null; page = value; revision++
    }
    fun edit(value: DemoEditor, id: String? = null) {
        val a = action ?: return
        val allowed = when (value) {
            DemoEditor.PERSON -> PersonRules.mayManage(a.status)
            DemoEditor.PLAN -> OperationalRules.mayPlan(a.status) && board?.persons?.any { it.person.id == id } == true
            DemoEditor.LIVE -> OperationalRules.mayUpdateSnapshot(a.status) && board?.persons?.any { it.person.id == id } == true
            DemoEditor.TASK -> TaskRules.mayWrite(a.status)
            DemoEditor.ROLE -> OperationalRules.mayCreateRole(a.status)
            DemoEditor.ACTION -> ActionLifecycle.mayEdit(a.status)
        }
        if (!allowed) return
        editor = value; error = null; revision++
        if (value in setOf(DemoEditor.PERSON, DemoEditor.PLAN, DemoEditor.LIVE)) personId = id
        if (value == DemoEditor.TASK) taskId = id
    }
    fun closeEditor() { editor = null; error = null; revision++ }
    fun viewPerson(id: String) { if (board?.persons?.any { it.person.id == id } == true) { personId = id; editor = null; revision++ } }
    fun viewTask(id: String) { if (taskBoard?.tasks?.any { it.id == id } == true) { taskId = id; editor = null; revision++ } }
    private fun reject() { error = "Bitte Eingaben und zulässigen Aktionsstatus prüfen." }
    fun savePerson(name: String) {
        val b = board ?: return
        if (!PersonRules.mayManage(b.status) || !PersonRules.valid(name)) { reject(); return }
        val id = personId ?: "new-p${++nextId}"
        val old = b.persons.find { it.person.id == id }
        if (b.persons.any { it.person.id != id && PersonRules.normalized(it.person.pseudonym) == PersonRules.normalized(name) }) { error = "Dieses Pseudonym ist schon vorhanden."; return }
        val p = old?.copy(person = old.person.copy(pseudonym = name.trim())) ?: OperationalPerson(Person(id, b.actionId, name.trim(), clock.now()), PersonRolePlan(emptySet(), null), null)
        board = b.copy(persons = if (old == null) b.persons + p else b.persons.map { if (it.person.id == id) p else it })
        personId = null; closeEditor()
    }
    fun savePlan(ids: Set<String>, primary: String?) {
        val b = board ?: return
        if (!OperationalRules.mayPlan(b.status) || !OperationalRules.validPlan(ids, primary) || ids.any { id -> b.roles.none { it.id == id } } || b.persons.none { it.person.id == personId }) { reject(); return }
        board = b.copy(persons = b.persons.map { if (it.person.id == personId) it.copy(plan = PersonRolePlan(ids, primary)) else it }); closeEditor()
    }
    fun addRole(name: String) {
        val b = board ?: return
        if (!OperationalRules.mayCreateRole(b.status) || !OperationalRules.validName(name)) { reject(); return }
        if (b.roles.any { OperationalRules.normalizedName(it.name) == OperationalRules.normalizedName(name) }) { error = "Diese Rolle ist schon vorhanden."; return }
        board = b.copy(roles = b.roles + Role("new-r${++nextId}", b.actionId, name.trim(), clock.now())); closeEditor()
    }
    fun saveLive(status: OperationalStatus, roleId: String?, place: String) {
        val b = board ?: return
        if (!OperationalRules.mayUpdateSnapshot(b.status) || !OperationalRules.validPlace(place) || b.persons.none { it.person.id == personId } ||
            (roleId != null && b.roles.none { it.id == roleId })) { reject(); return }
        board = b.copy(persons = b.persons.map { p -> if (p.person.id != personId) p else p.copy(state = PersonOperationalState(b.actionId, p.person.id,
            roleId.takeIf { OperationalRules.roleAllowed(status) }, status, OperationalRules.place(place, status), clock.now())) }); closeEditor()
    }
    fun saveTask(input: TaskInput) {
        val b = taskBoard ?: return
        val ops = board ?: return
        if (!TaskRules.mayWrite(action?.status ?: return) || !TaskRules.valid(input) ||
            (input.assigneePersonId != null && ops.persons.none { it.person.id == input.assigneePersonId }) ||
            (input.affectedPersonId != null && ops.persons.none { it.person.id == input.affectedPersonId }) ||
            (input.assigneeRoleId != null && ops.roles.none { it.id == input.assigneeRoleId })) { reject(); return }
        val now = clock.now(); val old = b.tasks.find { it.id == taskId }
        val t = Task(old?.id ?: "new-t${++nextId}", b.actionId, input.title.trim(), input.priority, old?.status ?: TaskStatus.OPEN,
            input.assigneePersonId, input.assigneeRoleId, input.affectedPersonId, input.dueAt, input.sourceTemplateKey, old?.createdAt ?: now, now, old?.completedAt)
        taskBoard = b.copy(tasks = if (old == null) b.tasks + t else b.tasks.map { if (it.id == old.id) t else it }); taskId = null; closeEditor()
    }
    fun transitionTask(id: String, status: TaskStatus) {
        val b = taskBoard ?: return
        if (!TaskRules.mayWrite(action?.status ?: return)) return
        val old = b.tasks.find { it.id == id } ?: return
        val now = clock.now(); val updated = TaskRules.transition(old, status, now) ?: return
        val event = TaskEvent("event-${++nextId}", b.actionId, id, TaskRules.event(old.status, status)!!, now)
        taskBoard = b.copy(tasks = b.tasks.map { if (it.id == id) updated else it }, events = b.events + event)
    }
    fun saveAction(input: NewAction) {
        val a = action ?: return
        if (!ActionLifecycle.mayEdit(a.status) || !input.valid()) { reject(); return }
        action = a.copy(title = input.title.trim(), description = input.description.trim(), startAt = input.startAt, expectedEndAt = input.expectedEndAt); closeEditor()
    }
    fun startAction() {
        val a = action ?: return; val b = board ?: return
        if (!ActionLifecycle.mayAdvance(a.status, ActionStatus.ACTIVE)) return
        action = a.copy(status = ActionStatus.ACTIVE)
        board = b.copy(status = ActionStatus.ACTIVE, persons = b.persons.map { it.copy(state = PersonOperationalState(b.actionId, it.person.id,
            it.plan.primaryRoleId, OperationalStatus.NO_STATUS, null, 0)) })
        page = DemoPage.HUB; editor = null; personId = null; taskId = null; revision++
    }
    fun nextTour() { tourStep = tourStep?.let { if (it < 3) it + 1 else null } }
    fun skipTour() { tourStep = null }
}
