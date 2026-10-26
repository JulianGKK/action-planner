package org.pag.domain

import java.util.Collections

/** One admitted immutable representation in OperationalBoard, in original role/person order. */
data class FunctionGroup(val role:Role,val people:List<OperationalPerson>) {
    val heading="${role.name} · ${people.size}"
    val hubLabel="${people.size} ${role.name}"
    val semantics="${role.name} öffnen"
    val labels=people.joinToString(" · "){it.person.pseudonym}
    val count="${people.size} ${if(people.size==1)"Person" else "Personen"} aktuell vor Ort"
}
class ActiveFunctionContent internal constructor(roles:List<Role>,persons:List<OperationalPerson>) {
    private class People(private val persons:List<OperationalPerson>,private val roleId:String?):AbstractList<OperationalPerson>() {
        override val size=persons.count{it.state?.status==OperationalStatus.ON_SITE && it.state.currentRoleId==roleId}
        override fun get(index:Int):OperationalPerson {
            if(index<0 || index>=size)throw IndexOutOfBoundsException()
            var i=0;var seen=0
            while(i<persons.size){val p=persons[i++];if(p.state?.status==OperationalStatus.ON_SITE && p.state.currentRoleId==roleId && seen++==index)return p}
            throw IndexOutOfBoundsException()
        }
    }
    private val byRole:Map<String,FunctionGroup> = Collections.unmodifiableMap(buildMap {
        for(role in roles){val people=People(persons,role.id);if(people.isNotEmpty())put(role.id,FunctionGroup(role,people))}
    })
    val groups:List<FunctionGroup> = Collections.unmodifiableList(byRole.values.toList())
    val without:List<OperationalPerson> = People(persons,null)
    val other:List<Role> = Collections.unmodifiableList(roles.filter{it.id !in byRole})
    private val onSite=persons.count{it.state?.status==OperationalStatus.ON_SITE}
    private val with=persons.count{it.state?.status==OperationalStatus.ON_SITE && it.state.currentRoleId!=null}
    val summary:List<String> = Collections.unmodifiableList(if(onSite==0)listOf("Niemand aktuell vor Ort") else listOf(
        "$onSite ${if(onSite==1)"Person" else "Personen"} vor Ort","$with mit Funktion","${onSite-with} ohne Funktion","${byRole.size} aktuell genutzte Funktionen"))
    val withoutHeading="Ohne Funktion · ${without.size}"
    val withoutLabels=without.joinToString(" · "){it.person.pseudonym}
    val withoutCount="${without.size} ${if(without.size==1)"Person" else "Personen"} aktuell vor Ort"
    val moreCollapsed="Weitere Funktionen (${other.size}) ▾"
    val moreExpanded="Weitere Funktionen (${other.size}) ▴"
    fun people(id:String?):List<OperationalPerson> = if(id==null)without else byRole[id]?.people?:emptyList()
    fun group(id:String)=byRole[id]
    companion object {
        fun cost(persons:Long,roles:Long,nameUnits:Long):ActiveWorkspaceBudget.Units {
            require(persons>=0 && roles>=0 && nameUnits>=0)
            val n=Math.addExact(persons,roles);val groups=Math.addExact(n,1)
            return ActiveWorkspaceBudget.Units(objects=Math.addExact(32,Math.multiplyExact(groups,16)),strings=Math.addExact(16,Math.multiplyExact(groups,8)),
                utf16Units=Math.addExact(1024,Math.addExact(Math.multiplyExact(nameUnits,8),Math.multiplyExact(groups,128))),
                arrays=16,arrayPayloadBytes=Math.addExact(4096,Math.multiplyExact(n,16)),lists=8,listCapacity=Math.addExact(16,Math.multiplyExact(roles,4)),
                maps=1,mapCapacity=Math.addExact(4,Math.multiplyExact(roles,2)),references=Math.addExact(64,Math.multiplyExact(n,16)))
        }
    }
}
