package org.pag.domain

import java.util.Collections

/** One immutable derived representation inside its admitted OperationalBoard. Lookups borrow
 * existing references; neither lookups nor UI disposal issue an allocation or end receipt. */
class OperationalPresentation internal constructor(private val roles:List<Role>,persons:List<OperationalPerson>) {
    val functions=ActiveFunctionContent(roles,persons)
    val identity=Any() // Opaque source identity; no payload getter/authority.
    val summary:List<String> = Collections.unmodifiableList(if(persons.isEmpty())listOf("Noch keine Personen") else buildList {
        add("${persons.size} insgesamt")
        fun addCount(status:OperationalStatus,label:String){
            val count=persons.count{(it.state?.status?:OperationalStatus.NO_STATUS)==status}
            if(count>0)add("$count $label")
        }
        addCount(OperationalStatus.ON_SITE,"vor Ort")
        addCount(OperationalStatus.IN_CUSTODY,"in Gewahrsam")
        addCount(OperationalStatus.NOT_REACHABLE,"nicht erreichbar")
        addCount(OperationalStatus.NO_STATUS,"ohne Lage")
    })
    val places:List<String> = Collections.unmodifiableList(persons.mapNotNull{it.state?.currentPlaceLabel}.distinct().sorted())
    /** Two passes over the original role order, with no additional complete role list. */
    private class Preferred(private val roles:List<Role>,private val planned:Set<String>):AbstractList<Role>() {
        override val size get()=roles.size
        override fun get(index:Int):Role {
            if(index<0 || index>=size)throw IndexOutOfBoundsException()
            var seen=0;var i=0
            while(i<roles.size){val r=roles[i++];if(r.id in planned && seen++==index)return r}
            i=0
            while(i<roles.size){val r=roles[i++];if(r.id !in planned && seen++==index)return r}
            throw IndexOutOfBoundsException()
        }
    }
    private val preferred:Map<String,List<Role>> = Collections.unmodifiableMap(buildMap {
        for(p in persons)put(p.person.id,Preferred(roles,p.plan.roleIds))
    })
    private val plans:Map<String,String> = Collections.unmodifiableMap(buildMap {
        for(p in persons){
            val text=StringBuilder()
            fun append(r:Role){if(text.isNotEmpty())text.append(", ");text.append(r.name)}
            for(r in roles)if(r.id==p.plan.primaryRoleId && r.id in p.plan.roleIds)append(r)
            for(r in roles)if(r.id!=p.plan.primaryRoleId && r.id in p.plan.roleIds)append(r)
            if(text.isNotEmpty())put(p.person.id,text.toString())
        }
    })
    fun preferredRoles(id:String):List<Role> = preferred[id]?:roles
    fun plannedRoles(id:String):String? = plans[id]
    companion object {
        /** SQL metadata supplies the sum of joined role-name UTF8 bytes before any names
         * are fetched. It bounds UTF16 incl malformed surrogate/NUL engineering allowance.
         * Builders, sorting/dedup scratch, map capacity and held results are additive. */
        fun cost(persons:Long,roles:Long,joinedNameUnits:Long):ActiveWorkspaceBudget.Units {
            require(persons>=0 && roles>=0 && joinedNameUnits>=0)
            val n=Math.addExact(persons,roles)
            return ActiveWorkspaceBudget.Units(objects=Math.addExact(32,Math.multiplyExact(n,8)),
                strings=Math.addExact(16,Math.multiplyExact(persons,4)),
                utf16Units=Math.addExact(512,Math.addExact(Math.multiplyExact(persons,64),Math.multiplyExact(joinedNameUnits,8))),
                lists=8,listCapacity=Math.addExact(32,Math.multiplyExact(persons,4)),
                maps=4,mapCapacity=Math.addExact(16,Math.multiplyExact(persons,6)),
                references=Math.addExact(64,Math.multiplyExact(n,16)))
        }
    }
}
