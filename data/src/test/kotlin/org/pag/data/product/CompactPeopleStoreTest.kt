package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Real SQLite/schema 11. Engineering charges and host timings, NOT ART/native heap or
 * Android navigation. Authority and lease/cleanup use trusted simulations from the core proof.
 */
class CompactPeopleStoreTest {
    private fun page(r: CompactPeopleResult) = ((r as CompactPeopleResult.Value).payload as CompactPeoplePage)
    private fun detail(r: CompactPeopleResult) = ((r as CompactPeopleResult.Value).payload as CompactPersonDetail).person
    private fun denied(reason: BoundedActiveStatus,r: CompactPeopleResult)=assertEquals(CompactPeopleResult.Rejected(reason),r)
    private fun pages(h: ActiveCoreStoreTest.H,query: CompactPeopleQuery=CompactPeopleQuery.All): List<CompactPersonCard> {
        val result=ArrayList<CompactPersonCard>()
        var cursor: CompactPeopleCursor?=null
        var exactCount: Long?=null
        do {
            val p=page(h.peopleRead(query,cursor))
            assertEquals(h.people.toLong(),p.overall.total)
            assertTrue(p.items.size<=32)
            if(exactCount==null) exactCount=p.filteredCount else assertEquals(exactCount,p.filteredCount)
            result.addAll(p.items)
            assertEquals(result.size,result.map{it.id}.toSet().size)
            assertTrue(result.size<=h.people)
            cursor=p.next
        } while(cursor!=null)
        assertEquals(exactCount,result.size.toLong())
        return result
    }
    private fun expectedPerson(i: Int,roles: Int)=OperationalPerson(Person("p$i","a","Synthetic person $i",i.toLong(), editRevision = 0),
        PersonRolePlan(setOf("r${i%roles}","r${(i+1)%roles}"),"r${i%roles}", editRevision = 0),
        PersonOperationalState("a","p$i","r${i%roles}",OperationalStatus.ON_SITE,null,1, editRevision = 0))

    @Test fun allRealisticSizesHaveCompletePagingDetailsAndSeparateMedianP95() {
        for((people,tasks,history) in listOf(Triple(20,40,12),Triple(100,250,20),Triple(200,1000,5))) ActiveCoreStoreTest.H(people,tasks,history).use { h ->
            val core=h.read() as ActiveCoreResult.Value
            assertEquals(people.toLong(),core.core.people.overall.total)
            assertEquals(tasks.toLong(),core.core.tasks.overall.total)
            assertTrue(core.retainedCharge<=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
            assertTrue(core.transitionCharge<=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
            val cards=pages(h)
            assertEquals((0 until people).map{"p$it"},cards.map{it.id})
            assertEquals(people,core.core.functions.items.sumOf{it.onSiteCount}.toInt())
            for(card in cards) assertEquals(h.expected.count{it.affectedPersonId==card.id && TaskRules.active(it.status)}.toLong(),card.affectedActiveTasks)
            val members=pages(h,CompactPeopleQuery.Function("r0"))
            assertEquals((0 until people).filter{it%6==0}.map{"p$it"},members.map{it.id})
            for(i in 0 until people) assertEquals(expectedPerson(i,6),detail(h.peopleRead(detail="p$i")))
            // Against the existing complete OperationalBoard where its unchanged bound admits it.
            if(people==20) {
                h.db.beforePrepare={}
                val legacy=runBlocking { BoundedActiveStore(h.db,h.context,{true}).read(h.requested.binding) }
                for(p in legacy.operations.persons) assertEquals(p,detail(h.peopleRead(detail=p.person.id)))
            }
            val first=(h.peopleRead() as CompactPeopleResult.Value).payload.let { (it as CompactPeoplePage).next }
            val groupFirst=page(h.peopleRead(CompactPeopleQuery.Function("r0"))).next
            val samples=linkedMapOf<String,MutableList<Long>>()
            fun measure(name: String,sample: Int,work:()->Unit) {
                val start=System.nanoTime();work();val n=System.nanoTime()-start
                if(sample>=2) samples.getOrPut(name){ArrayList()}.add(n)
            }
            repeat(22) { sample ->
                measure("CORE",sample){assertTrue(h.read() is ActiveCoreResult.Value)}
                if(sample>=2) for((metric,n) in h.metrics) samples.getOrPut(metric.name){ArrayList()}.add(n)
                // Small fixture has <32 people; explicit empty successor is measured with a
                // cursor at its final actual row, not mislabeled as a full second page.
                val next=first?:core.core.people.items.last().let{CompactPeopleCursor(h.requested,CompactPeopleQuery.All,it.createdAt,it.id)}
                measure("PEOPLE_NEXT",sample){page(h.peopleRead(after=next))}
                val memberQuery=CompactPeopleQuery.Function("r0")
                val groupNext=groupFirst?:members.last().let{CompactPeopleCursor(h.requested,memberQuery,it.createdAt,it.id)}
                measure("MEMBERS_NEXT",sample){page(h.peopleRead(memberQuery,groupNext))}
                measure("DETAIL",sample){assertEquals(expectedPerson(0,6),detail(h.peopleRead(detail="p0")))}
            }
            fun stats(values:List<Long>):String { val v=values.sorted();return "${(v[9]+v[10])/2.0/1e6}/${v[18]/1e6}" }
            println("PEOPLE_CORE people=$people tasks=$tasks storedEvents=${tasks*history} retained=${core.retainedCharge} transition=${core.transitionCharge} "+samples.entries.joinToString{(k,v)->"$k-ms-median/p95=${stats(v)}"})
            val firstPeople=h.peopleRead() as CompactPeopleResult.Value
            val peopleTail=h.peopleRead(after=first?:core.core.people.items.last().let{CompactPeopleCursor(h.requested,CompactPeopleQuery.All,it.createdAt,it.id)}) as CompactPeopleResult.Value
            val functionQuery=CompactPeopleQuery.Function("r0")
            val groupTail=h.peopleRead(functionQuery,groupFirst?:members.last().let{CompactPeopleCursor(h.requested,functionQuery,it.createdAt,it.id)}) as CompactPeopleResult.Value
            val personDetail=h.peopleRead(detail="p0") as CompactPeopleResult.Value
            println("PEOPLE_CHARGES people=$people first=${firstPeople.retainedCharge}/${firstPeople.transitionCharge} next=${peopleTail.retainedCharge}/${peopleTail.transitionCharge} membersNext=${groupTail.retainedCharge}/${groupTail.transitionCharge} detail=${personDetail.retainedCharge}/${personDetail.transitionCharge}")
            assertFalse(h.queries.any{it.contains("task_events")})
        }
    }

    @Test fun twoHundredMembersOfOneFunctionAndManyFunctionsHaveExactBoundedGroups() {
        for(roles in listOf(6,32,100)) ActiveCoreStoreTest.H(200,1000,5,roles).use { h ->
            h.exec("UPDATE person_operational_states SET currentRoleId='r0'")
            val read=h.read()
            val core=read as ActiveCoreResult.Value
            println("PEOPLE_CATALOG roles=$roles retained=${core.retainedCharge} transition=${core.transitionCharge} admitted")
            assertEquals(roles,core.core.functions.items.size)
            assertEquals(1L,core.core.functions.usedFunctions)
            assertEquals(200L,core.core.functions.items.single{it.role.id=="r0"}.onSiteCount)
            assertTrue(core.core.functions.items.filter{it.role.id!="r0"}.all{it.onSiteCount==0L})
            assertEquals((0 until 200).map{"p$it"},pages(h,CompactPeopleQuery.Function("r0")).map{it.id})
            assertTrue(pages(h,CompactPeopleQuery.Function("r1")).isEmpty())
            assertEquals(200,pages(h).size)
            assertFalse(h.queries.any{it.contains("task_events")})
        }
    }

    @Test fun allStatusesWithoutFunctionUnusedRolesAndPlannedRolesRemainDistinct()=ActiveCoreStoreTest.H(200,1000,5).use { h ->
        h.exec("UPDATE person_operational_states SET operationalStatus=CASE (SELECT createdAt%8 FROM persons WHERE id=personId) "+
            "WHEN 0 THEN 'NO_STATUS' WHEN 1 THEN 'ON_SITE' WHEN 2 THEN 'IDENTITY_CHECK' WHEN 3 THEN 'IN_CUSTODY' "+
            "WHEN 4 THEN 'NOT_REACHABLE' WHEN 5 THEN 'RELEASED' WHEN 6 THEN 'LEFT' ELSE 'UNCLEAR' END")
        h.exec("UPDATE person_operational_states SET currentRoleId=NULL WHERE operationalStatus NOT IN ('NO_STATUS','ON_SITE') OR personId IN ('p1','p9','p17')")
        h.exec("UPDATE person_operational_states SET currentPlaceLabel='Synthetic place',updatedAt=7 WHERE operationalStatus IN ('IDENTITY_CHECK','IN_CUSTODY','UNCLEAR')")
        val v=h.read() as ActiveCoreResult.Value
        for(status in OperationalStatus.entries) assertEquals(25L,v.core.people.overall.count(status))
        assertEquals(3L,v.core.functions.withoutFunction)
        assertEquals(25L,v.core.functions.onSiteCount)
        assertEquals(22L,v.core.functions.items.sumOf{it.onSiteCount})
        val all=pages(h)
        val without=pages(h,CompactPeopleQuery.Function(null))
        assertEquals(listOf("p1","p9","p17"),without.map{it.id})
        for(role in v.core.functions.items) assertEquals(all.filter{it.status==OperationalStatus.ON_SITE && it.currentRoleId==role.role.id}.map{it.id},pages(h,CompactPeopleQuery.Function(role.role.id)).map{it.id})
        for(i in listOf(0,1,2,3,199)) {
            val d=detail(h.peopleRead(detail="p$i"))
            assertEquals(expectedPerson(i,6).plan,d.plan)
            val card=all.single{it.id=="p$i"}
            assertEquals(card.status,d.state!!.status);assertEquals(card.currentRoleId,d.state!!.currentRoleId)
            assertEquals(card.currentPlaceLabel,d.state!!.currentPlaceLabel);assertEquals(card.updatedAt,d.state!!.updatedAt)
            assertEquals(h.expected.count{it.affectedPersonId==card.id && TaskRules.active(it.status)}.toLong(),card.affectedActiveTasks)
        }
        // A task assigned to p0 but affecting p1 counts for p1, NEVER for its assignee.
        val before=all.single{it.id=="p0"}.affectedActiveTasks
        h.exec("UPDATE tasks SET assigneeRoleId=NULL,assigneePersonId='p0',affectedPersonId='p1' WHERE status IN ('OPEN','IN_PROGRESS')")
        val changed=pages(h)
        assertEquals(0L,changed.single{it.id=="p0"}.affectedActiveTasks)
        assertTrue(before>0);assertEquals(h.expected.count{TaskRules.active(it.status)}.toLong(),changed.single{it.id=="p1"}.affectedActiveTasks)
    }

    @Test fun utf16IdTiesNulUnicodeLongNamesAndFullDetailPreserveValues()=ActiveCoreStoreTest.H(40,40).use { h ->
        h.exec("UPDATE persons SET createdAt=1")
        // No production PII: synthetic IDs include NUL and supplementary/private-use ties.
        h.exec("PRAGMA foreign_keys=OFF")
        val ids=listOf("char(0)||'a'","char(0)||'b'","char(0x10000)","char(0xE000)")
        ids.forEachIndexed { i,id ->
            for((table,column) in listOf("person_planned_roles" to "personId","person_role_plans" to "personId","person_operational_states" to "personId","tasks" to "assigneePersonId","tasks" to "affectedPersonId"))
                h.exec("UPDATE $table SET $column=$id WHERE $column='p$i'")
            h.exec("UPDATE persons SET id=$id,pseudonym='Synthetisch '||char(0)||char(0x1F9EA)||'$i',pseudonymNormalized='synthetisch '||char(0)||char(0x1F9EA)||'$i' WHERE id='p$i'")
        }
        h.exec("UPDATE persons SET pseudonym='Long '||printf('%01024d',0),pseudonymNormalized='long '||printf('%01024d',0) WHERE id='p4'")
        h.exec("UPDATE roles SET name='Function '||char(0)||char(0x1F9EA)||printf('%01024d',0),normalizedName='function '||char(0)||char(0x1F9EA)||printf('%01024d',0) WHERE id='r0'")
        val all=pages(h)
        val expectedIds=listOf("\u0000a","\u0000b","\uD800\uDC00","\uE000")+(4 until 40).map{"p$it"}
        assertEquals(expectedIds.sorted(),all.map{it.id})
        assertEquals(40,all.size)
        for(c in all) assertEquals(c.pseudonym,detail(h.peopleRead(detail=c.id)).person.pseudonym)
        val core=h.read() as ActiveCoreResult.Value
        assertEquals(6,core.core.functions.items.size)
        assertTrue(core.core.functions.items.single{it.role.id=="r0"}.role.name.contains('\u0000'))
        val cursor=page(h.peopleRead()).next!!
        val replay=page(h.peopleRead(after=cursor))
        assertEquals(replay,page(h.peopleRead(after=cursor))) // retries are explicit reads, never cached pages
    }

    @Test fun selectedEditorLoadsAllPlannedRolesAcrossBatchesWithoutOtherPeopleGraphs()=ActiveCoreStoreTest.H(200,1000,5,100).use { h ->
        h.exec("INSERT OR IGNORE INTO person_planned_roles SELECT 'a','p0',id FROM roles")
        val d=detail(h.peopleRead(detail="p0"))
        assertEquals((0 until 100).map{"r$it"}.toSet(),d.plan.roleIds)
        assertEquals("r0",d.plan.primaryRoleId)
        assertEquals(expectedPerson(0,100).state,d.state)
        val fetched=h.queries.filter{it.startsWith("SELECT rowid,")}
        assertTrue(fetched.count{it.contains(" FROM person_planned_roles ") }>=4)
        assertTrue(fetched.all{!it.contains(" FROM persons ") || it.contains("id=?")})
        assertFalse(h.queries.any{it.contains("task_events")})
    }

    @Test fun invalidReferencesAndUnknownRevokeMutationsNeverReturnOldPagesOrDetails() {
        for(change in listOf("mutation","action","generation","unknown","revoke")) ActiveCoreStoreTest.H(200,1000,5).use { h ->
            val cursor=page(h.peopleRead()).next!!
            when(change) {
                "mutation"->h.authority.set(h.requested.copy(mutationEpoch=1))
                "action"->h.authority.set(h.requested.copy(binding=h.requested.binding.copy(actionId="b")))
                "generation"->h.authority.set(h.requested.copy(binding=h.requested.binding.copy(generation=2)))
                "revoke"->h.ready.set(false)
                else->h.authority.set(null)
            }
            denied(BoundedActiveStatus.ACCESS_REVOKED,h.peopleRead(after=cursor))
            denied(BoundedActiveStatus.ACCESS_REVOKED,h.peopleRead(detail="p0"))
        }
        ActiveCoreStoreTest.H(40,40).use { h ->
            val cursor=page(h.peopleRead()).next!!
            denied(BoundedActiveStatus.ACCESS_REVOKED,h.peopleRead(CompactPeopleQuery.Function("r0"),cursor))
            denied(BoundedActiveStatus.ACCESS_REVOKED,h.peopleRead(after=cursor.copy(state=h.requested.copy(mutationEpoch=99))))
            h.exec("DELETE FROM person_operational_states WHERE personId='p39'")
            denied(BoundedActiveStatus.INVALID_DATA,h.peopleRead())
        }
        for(sql in listOf("UPDATE person_planned_roles SET roleId='foreign' WHERE roleId='r0'", "UPDATE person_role_plans SET primaryRoleId='foreign'", "UPDATE person_operational_states SET currentRoleId='foreign'")) ActiveCoreStoreTest.H().use { h ->
            h.exec("PRAGMA foreign_keys=OFF")
            h.exec("INSERT INTO actions VALUES('b','Synthetic','Synthetic',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            h.exec("INSERT INTO roles(id,actionId,name,normalizedName,createdAt) VALUES('foreign','b','Synthetic','synthetic',0)")
            h.exec(sql)
            denied(BoundedActiveStatus.INVALID_DATA,h.peopleRead());assertEquals(0,h.db.blobReads)
        }
    }

    @Test fun pageDetailBudgetsFailBeforePayloadAndAllLifecycleFailuresFailClosed() {
        for(field in listOf("pseudonym","name","currentPlaceLabel")) ActiveCoreStoreTest.H(40,40).use { h ->
            when(field) {
                "pseudonym"->h.exec("UPDATE persons SET pseudonym=printf('%01000000d',0) WHERE id='p0'")
                "name"->h.exec("UPDATE roles SET name=printf('%01000000d',0) WHERE id='r0'")
                else->{ h.exec("UPDATE person_operational_states SET operationalStatus='IN_CUSTODY',currentRoleId=NULL,currentPlaceLabel=printf('%01000000d',0) WHERE personId='p0'") }
            }
            denied(BoundedActiveStatus.BUDGET_REFUSED,h.peopleRead())
            denied(BoundedActiveStatus.BUDGET_REFUSED,h.peopleRead(detail="p0"))
            assertEquals(0,h.db.blobReads);assertEquals(0,h.blocked)
        }
        for(detail in listOf(false,true)) for(phase in ActiveCoreStore.Phase.entries.filter{it!=ActiveCoreStore.Phase.TASK_ROW}) ActiveCoreStoreTest.H().use { h ->
            h.phase={if(it==phase) h.ready.set(false)}
            denied(BoundedActiveStatus.ACCESS_REVOKED,h.peopleRead(detail=if(detail) "p0" else null))
            assertEquals(0,h.publicationAttempts);assertEquals(0,h.db.openStatements)
        }
        for(at in listOf("database","lease","publication","after")) ActiveCoreStoreTest.H().use { h ->
            val error={error("SYNTHETIC_CLEANUP_FAILURE")}
            when(at) {"database"->h.afterDatabaseCleanup=error;"lease"->h.afterLeaseCleanup=error;"publication"->h.beforePublication={h.authority.set(null)};else->h.afterPublication={h.authority.set(null)}}
            denied(if(at=="database"||at=="lease") BoundedActiveStatus.STORAGE_FAILURE else BoundedActiveStatus.ACCESS_REVOKED,h.peopleRead())
        }
        ActiveCoreStoreTest.H().use { h ->h.exec("PRAGMA foreign_keys=OFF");h.exec("DROP TABLE roles");denied(BoundedActiveStatus.STORAGE_FAILURE,h.peopleRead())}
    }
}
