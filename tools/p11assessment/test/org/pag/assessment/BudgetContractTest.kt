package org.pag.assessment
import org.junit.Assert.*
import org.junit.Test
class BudgetContractTest {
    @Test fun exactBoundaryAndNextRowRefusedBeforeMaterialization(){val b=BudgetContractProof(128);assertEquals(BudgetContractProof.Result.ADMITTED,b.charge(1,0,0,0));assertEquals(BudgetContractProof.Result.BUDGET_REFUSED,b.admitRow(0,0,0));assertEquals(128L,b.charged)}
    @Test fun overflowRefusesInsteadOfWrapping(){val b=BudgetContractProof(Long.MAX_VALUE);assertEquals(BudgetContractProof.Result.BUDGET_REFUSED,b.charge(Long.MAX_VALUE,0,0,0));assertEquals(0L,b.charged)}
    @Test fun perRowReferenceOverflowRefuses(){assertEquals(BudgetContractProof.Result.BUDGET_REFUSED,BudgetContractProof(Long.MAX_VALUE).admitRow(0,Long.MAX_VALUE,1))}
    @Test fun refusalIsStickyAndNotIntegrityException(){val b=BudgetContractProof(0);assertEquals(BudgetContractProof.Result.BUDGET_REFUSED,b.charge(1,0,0,0));assertEquals(BudgetContractProof.Result.BUDGET_REFUSED,b.charge(0,0,0,0))}
    @Test fun invalidNegativeAggregateIsNotBudgetPressure(){assertThrows(IllegalArgumentException::class.java){BudgetContractProof(100).charge(-1,0,0,0)}}
    @Test fun supplementaryUnicodeChargeIsConservative(){val value="A\uD83E\uDD8B";assertEquals(3,value.length);val b=BudgetContractProof(4096);b.admitRow(value.length.toLong(),1,1);assertTrue(b.charged>=128+value.toByteArray(Charsets.UTF_16LE).size+16+64)}
    @Test fun duplicateObjectsChargedWithoutDedupAssumption(){val b=BudgetContractProof(4096);b.admitRow(10,1,1);val first=b.charged;b.admitRow(10,1,1);assertEquals(first*2,b.charged)}
}
