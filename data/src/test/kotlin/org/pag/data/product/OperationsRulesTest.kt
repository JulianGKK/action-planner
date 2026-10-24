package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.Locale

class OperationsRulesTest {
    @Test fun templatesAreOnlyNames(){assertEquals(listOf("Blockierende","Support","Hummel","Gesa-Support","Presse","Aktionsleitung"),OperationalRules.templates)}
    @Test fun namesRequireNonblankTrim(){assertFalse(OperationalRules.validName(" \t"));assertTrue(OperationalRules.validName(" Eigene Rolle "));assertEquals("eigene rolle",OperationalRules.normalizedName(" Eigene Rolle "))}
    @Test fun normalizationDoesNotDependOnDefaultLocale(){val old=Locale.getDefault();try{Locale.setDefault(Locale.forLanguageTag("tr"));assertEquals("identity",OperationalRules.normalizedName("IDENTITY"))}finally{Locale.setDefault(old)}}
    @Test fun planningOnlyPreparation(){ActionStatus.entries.forEach{assertEquals(it==ActionStatus.PREPARATION,OperationalRules.mayPlan(it))}}
    @Test fun creationAlsoActiveButNeverRenamePermission(){assertTrue(OperationalRules.mayCreateRole(ActionStatus.ACTIVE));assertFalse(OperationalRules.mayPlan(ActionStatus.ACTIVE));assertFalse(OperationalRules.mayCreateRole(ActionStatus.COMPLETED))}
    @Test fun operationalUpdatesOnlyActive(){ActionStatus.entries.forEach{assertEquals(it==ActionStatus.ACTIVE,OperationalRules.mayUpdateSnapshot(it))}}
    @Test fun draftAndDeletionHaveNoReadPath(){assertFalse(OperationalRules.mayRead(ActionStatus.DRAFT));assertFalse(OperationalRules.mayRead(ActionStatus.DELETION_PENDING));assertTrue(OperationalRules.mayRead(ActionStatus.COMPLETED))}
    @Test fun mainFunctionMustBeAnAssignment(){assertTrue(OperationalRules.validPlan(setOf("a","b"),"a"));assertTrue(OperationalRules.validPlan(emptySet(),null));assertFalse(OperationalRules.validPlan(setOf("a"),"b"))}
    @Test fun unavailableStatusesCannotHoldCurrentRoles(){listOf(OperationalStatus.IDENTITY_CHECK,OperationalStatus.IN_CUSTODY,OperationalStatus.NOT_REACHABLE,OperationalStatus.LEFT,OperationalStatus.UNCLEAR,OperationalStatus.RELEASED).forEach{assertFalse(OperationalRules.roleAllowed(it))};assertTrue(OperationalRules.roleAllowed(OperationalStatus.ON_SITE));assertTrue(OperationalRules.roleAllowed(OperationalStatus.NO_STATUS))}
    @Test fun placeOnlyForSupportRelatedStatuses(){OperationalStatus.entries.forEach{assertEquals(it in setOf(OperationalStatus.IDENTITY_CHECK,OperationalStatus.IN_CUSTODY,OperationalStatus.UNCLEAR),OperationalRules.placeRelevant(it))}}
    @Test fun placeTrimsAndBlankMeansAbsent(){assertEquals("Gesa 1",OperationalRules.place(" Gesa 1 ",OperationalStatus.IN_CUSTODY));assertNull(OperationalRules.place(" ",OperationalStatus.IN_CUSTODY));assertNull(OperationalRules.place("Gesa 1",OperationalStatus.ON_SITE))}
    @Test fun placeBoundCountsUnicode(){assertTrue(OperationalRules.validPlace("😀".repeat(80)));assertFalse(OperationalRules.validPlace("😀".repeat(81)))}
    private fun board():OperationalBoard {
        val role=Role("r","a","Blockierende",0, editRevision = 0)
        fun p(id:String,status:OperationalStatus,place:String?,roleId:String?)=OperationalPerson(Person(id,"a",id,0, editRevision = 0),PersonRolePlan(setOf("r"),"r", editRevision = 0),PersonOperationalState("a",id,roleId,status,place,1, editRevision = 0))
        return OperationalBoard("a",ActionStatus.ACTIVE,listOf(role),listOf(p("Motte",OperationalStatus.IN_CUSTODY,"Gesa 1",null),p("Meise",OperationalStatus.IN_CUSTODY,"Gesa 2",null),p("Supporter",OperationalStatus.ON_SITE,null,"r")))
    }
    @Test fun countsDoNotCountCustodyAsCurrentFunction(){assertEquals(2,board().count(OperationalStatus.IN_CUSTODY));assertEquals(1,board().roleCount("r"));assertEquals(listOf("Gesa 1","Gesa 2"),board().placeSuggestions)}
    @Test fun preferredRolesFirst(){val b=board();val other=Role("s","a","Andere",0, editRevision = 0);assertEquals(listOf(b.roles[0],other),b.copy(roles=listOf(other)+b.roles).preferredRoles("Motte"))}
}
