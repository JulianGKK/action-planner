package org.pag.data.product
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.EuroMoney
class EuroMoneyTest {
    @Test fun acceptedForms() { for((s,n) in mapOf("1" to 100L,"1,5" to 150L,"1,50" to 150L,"1.5" to 150L,"1.50" to 150L,"0,01" to 1L,"120,00" to 12000L,"120" to 12000L,"120,5" to 12050L,"120.50" to 12050L))assertEquals(s,n,EuroMoney.parse(s)) }
    @Test fun ambiguousIncompleteNonPositiveOrCurrencyRejected() { for(s in listOf("","0","0,00","-1","+1","1,234","1.234","1,2,3","1.2.3","1,23.4","1.200,00","€ 5","5 €","1e2","12,",",50","1 200","NaN","Infinity"))assertNull(s,EuroMoney.parse(s)) }
    @Test fun exactLongBoundaryAndOverflow() { assertEquals(Long.MAX_VALUE,EuroMoney.parse("92233720368547758,07"));for(s in listOf("92233720368547758,08","92233720368547759","9999999999999999999999999999"))assertNull(EuroMoney.parse(s)) }
    @Test fun formatterAndRoundtrip() { assertEquals("0,01 €",EuroMoney.format(1));assertEquals("1,50 €",EuroMoney.format(150));assertEquals("120,00 €",EuroMoney.format(12000));assertEquals("0,00 €",EuroMoney.format(0));for(n in listOf(1L,50,150,12000,Long.MAX_VALUE))assertEquals(n,EuroMoney.parse(EuroMoney.input(n))) }
}
