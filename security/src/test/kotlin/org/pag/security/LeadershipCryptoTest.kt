package org.pag.security

import org.junit.Assert.*
import org.junit.Test

class LeadershipCryptoTest {
    @Test fun argon2idReproducesAndSaltSeparates() {
        val chars="synthetic KDF fixture only".toCharArray();val salt=ByteArray(16){it.toByte()}
        val a=LeadershipCrypto.derive(chars,salt,LeadershipKdfParameters())
        val b=LeadershipCrypto.derive(chars,salt,LeadershipKdfParameters())
        val c=LeadershipCrypto.derive(chars,salt.copyOf().also{it[0]=99},LeadershipKdfParameters())
        try{assertTrue(a.contentEquals(b));assertFalse(a.contentEquals(c));assertEquals(32,a.size)}finally{chars.fill('\u0000');a.fill(0);b.fill(0);c.fill(0)}
    }
    @Test fun noWeakerOrUnknownParameterProfile() {
        for(p in listOf(LeadershipKdfParameters(memoryKiB=32768),LeadershipKdfParameters(iterations=2),LeadershipKdfParameters(version=16),LeadershipKdfParameters(parallelism=4)))
            assertThrows(LeadershipCryptoFailure::class.java){p.validate()}
    }
    @Test fun aeadRejectsWrongContextTamperingAndKey() {
        val key=LeadershipCrypto.random(32);val plain=byteArrayOf(1,2,3);val aad=byteArrayOf(4)
        try{
            val cipher=LeadershipCrypto.encrypt(key,plain,aad)
            assertArrayEquals(plain,LeadershipCrypto.decrypt(key,cipher,aad))
            assertThrows(Exception::class.java){LeadershipCrypto.decrypt(key,cipher,byteArrayOf(5))}
            assertThrows(Exception::class.java){LeadershipCrypto.decrypt(key,cipher.copyOf().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()},aad)}
            assertThrows(Exception::class.java){LeadershipCrypto.decrypt(ByteArray(32),cipher,aad)}
        }finally{key.fill(0);plain.fill(0)}
    }
    @Test fun malformedSurrogateRefusedWithoutReplacement() {
        assertThrows(LeadershipCryptoFailure::class.java){LeadershipCrypto.derive(charArrayOf('\uD800'),ByteArray(16),LeadershipKdfParameters())}
    }
}
