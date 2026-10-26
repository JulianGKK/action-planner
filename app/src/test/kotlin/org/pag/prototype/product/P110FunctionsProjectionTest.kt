package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P110FunctionsProjectionTest {
    @Test fun heldFunctionGenerationsBorrowOriginalViewsAndJoinedTextsInOriginalOrder(){
        val role=Role("r","a","Synthetic role",0,0)
        val people=(0 until 40).map{n->OperationalPerson(Person("p-$n","a","Synthetic $n",n.toLong(),0),PersonRolePlan(emptySet(),null,0),PersonOperationalState("a","p-$n","r",OperationalStatus.ON_SITE,null,1,0))}
        val board=OperationalBoard("a",ActionStatus.ACTIVE,listOf(role),people)
        val original=activeFunctionContent(board)
        for(n in listOf(100,1000,32768)){
            val aliases=List(n){activeFunctionContent(board)}
            for(alias in aliases){assertSame(original,alias);assertSame(original.groups[0].labels,alias.groups[0].labels);assertSame(original.groups[0].people,activeFunctionPeople(board,"r"))}
        }
        assertSame(people[0],original.groups[0].people[0]);assertSame(people[39],original.groups[0].people[39])
        assertEquals("Synthetic role · 40",original.groups[0].heading)
        assertEquals(40,original.groups[0].people.size)
    }
}
