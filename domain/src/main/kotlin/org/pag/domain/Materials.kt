package org.pag.domain

data class MaterialInput(val name: String, val quantityLabel: String? = null, val placeLabel: String? = null,
    val responsiblePersonId: String? = null, val linkedTaskId: String? = null, val sourceTemplateKey: String? = null)
data class MaterialItem(val id: String, val actionId: String, val name: String, val quantityLabel: String?,
    val placeLabel: String?, val responsiblePersonId: String?, val linkedTaskId: String?,
    val sourceTemplateKey: String?, val createdAt: Long) {
    fun input() = MaterialInput(name, quantityLabel, placeLabel, responsiblePersonId, linkedTaskId, sourceTemplateKey)
}
data class MaterialTemplate(val key: String, val name: String) {
    /** Selection creates only a normal draft, never a write. */
    fun input() = MaterialInput(name, sourceTemplateKey = key)
}
data class MaterialSummary(val count: Int)
data class MaterialBoard(val actionId: String, val items: List<MaterialItem>)
object MaterialRules {
    private fun optional(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
    fun normalized(input: MaterialInput) = input.copy(name = input.name.trim(),
        quantityLabel = optional(input.quantityLabel), placeLabel = optional(input.placeLabel))
    private fun length(value: String?) = value?.let { Character.codePointCount(it, 0, it.length) } ?: 0
    fun valid(input: MaterialInput): Boolean {
        val i = normalized(input)
        return i.name.isNotEmpty() && length(i.name) <= 120 && length(i.quantityLabel) <= 40 && length(i.placeLabel) <= 80
    }
    fun mayRead(status: ActionStatus) = status in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED)
    fun mayWrite(status: ActionStatus) = status == ActionStatus.PREPARATION || status == ActionStatus.ACTIVE
    fun mayDelete(status: ActionStatus) = mayWrite(status)
    val order = compareBy<MaterialItem> { it.createdAt }.thenBy { it.id }
    val templates = listOf(
        MaterialTemplate("GLUE", "Klebstoff"), MaterialTemplate("DUCT_TAPE", "Gewebeband"),
        MaterialTemplate("SIGNS", "Schilder / Plakate"), MaterialTemplate("BANNER", "Banner"),
        MaterialTemplate("MARKERS", "Marker / Stifte"), MaterialTemplate("SAFETY_VESTS", "Warnwesten"),
        MaterialTemplate("FIRST_AID", "Erste-Hilfe-Set"), MaterialTemplate("WATER", "Wasser"),
        MaterialTemplate("POWER_BANKS", "Powerbanks"), MaterialTemplate("CHARGING_CABLES", "Ladekabel"),
        MaterialTemplate("ACTION_PHONE", "Aktionshandy"), MaterialTemplate("RAIN_PROTECTION", "Regenschutz"))
}
/** INTERNAL, action-local SQLCipher storage through the existing READY session. */
interface MaterialRepository {
    fun summary(actionId: String, publish: (OperationsResult<MaterialSummary>) -> Unit)
    fun board(actionId: String, publish: (OperationsResult<MaterialBoard>) -> Unit)
    fun create(actionId: String, input: MaterialInput, publish: (OperationsResult<MaterialItem>) -> Unit)
    fun edit(actionId: String, expected: MaterialItem, input: MaterialInput, publish: (OperationsResult<MaterialItem>) -> Unit)
    fun delete(actionId: String, expected: MaterialItem, publish: (OperationsResult<Unit>) -> Unit)
}
