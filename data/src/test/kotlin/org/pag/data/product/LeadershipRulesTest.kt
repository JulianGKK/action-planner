package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class LeadershipRulesTest {
    @Test fun exactUnicodeLengthAndWhitespaceRules() {
        assertFalse(LeadershipRules.validPassphrase("short secret".toCharArray()))
        assertTrue(LeadershipRules.validPassphrase("synthetic phrase ".toCharArray()))
        assertFalse(LeadershipRules.validPassphrase(CharArray(129){'x'}))
        assertTrue(LeadershipRules.validPassphrase("😀".repeat(128).toCharArray()))
        assertTrue(LeadershipRules.validPassphrase(CharArray(16){' '}))
    }
    @Test fun onlyPreparationWritesWhileActiveAndCompletedRead() {
        ActionStatus.entries.forEach{assertEquals(it==ActionStatus.PREPARATION,LeadershipRules.mayWrite(it));assertEquals(PersonRules.mayRead(it),LeadershipRules.mayRead(it))}
    }
    @Test fun grantExpiresExactlyWithoutExtensionAndCannotRevive() {
        var now=0L;var allowed=true;val context=object:StorageContext{
            override val scope=AccessScope.READY;override val generation=1L
            override fun check(point:AccessPoint)=allowed
            override fun <T> admitKey(validUntilMillis:Long,acquire:(()->Boolean)->T?):T? =
                synchronized(this){acquire{allowed && now<validUntilMillis}}
            override fun publish(result:()->Unit):Boolean{if(!allowed)return false;result();return true}
        }
        val grant=LeadershipGrant("a","p",SensitiveArea.IDENTITY,context,MonotonicTime{now},ByteArray(32),"epoch",0,byteArrayOf(),true)
        repeat(6){now=it*10000L;assertTrue(grant.valid())}
        now=60000;assertFalse(grant.valid());now=0;assertFalse(grant.valid())
        assertThrows(SensitiveProblem::class.java){grant.consume{}}
    }
    @Test fun revocationAndConsumerCopiesAreWiped() {
        var allowed=true;val context=object:StorageContext{
            override val scope=AccessScope.READY;override val generation=4L
            override fun check(point:AccessPoint)=allowed
            override fun <T> admitKey(validUntilMillis:Long,acquire:(()->Boolean)->T?):T? =
                synchronized(this){acquire{allowed && 0L<validUntilMillis}}
            override fun publish(result:()->Unit)=false
        }
        val source=ByteArray(32){8};val g=LeadershipGrant("a","p",SensitiveArea.CONTACT,context,MonotonicTime{0},source,"epoch",0,byteArrayOf(),true)
        var copy:ByteArray?=null;g.consume{copy=it};assertTrue(copy!!.all{it==0.toByte()})
        assertTrue(source.all{it==8.toByte()});allowed=false;assertFalse(g.valid());allowed=true;assertFalse(g.valid())
    }
    @Test fun allOptionalPayloadsRoundTripAndAreaMismatchRefused() {
        val values=listOf(SensitiveData.Identity(),SensitiveData.Identity("synthetic",java.time.LocalDate.of(2000,1,2)),SensitiveData.Contact("synthetic-phone"),SensitiveData.EmergencyContact("synthetic",null,"synthetic relation"))
        values.forEach{v->val bytes=SensitiveCodec.encode(v);try{assertEquals(v,SensitiveCodec.decode(LeadershipRules.area(v),bytes));assertThrows(Exception::class.java){SensitiveCodec.decode(LeadershipRules.area(v),bytes+byteArrayOf(1))}}finally{bytes.fill(0)}}
    }
}
