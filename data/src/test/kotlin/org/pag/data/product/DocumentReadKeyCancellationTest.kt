package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

class DocumentReadKeyCancellationTest {
    private var allowed=true
    private val authority=object:StorageContext {
        override val generation=1L;override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean {if(!allowed)return false;result();return true}
    }
    @Test fun closingViewerAfterDbStepDoesNotInvalidateLiveSessionOwner() {
        val owner=SessionDbKeyOwner.bind(authority,ByteArray(32){1});val request=DocumentTextRequest(1)
        val c=documentReadContext(authority,request);lateinit var lease:ByteArray
        try {
            assertThrows(StorageAccessRevoked::class.java){withDocumentReadKey(owner,c){lease=it;request.cancel();Unit}}
            assertTrue(lease.all{it==0.toByte()})
            assertEquals(32,owner.withDbKey(authority){it.size})
        }finally {owner.invalidate()}
    }
    @Test fun cancelledRequestCannotAcquireAKeyOrInvalidateOwner() {
        val owner=SessionDbKeyOwner.bind(authority,ByteArray(32){1});val request=DocumentTextRequest(1);request.cancel();var called=false
        try {
            assertThrows(StorageAccessRevoked::class.java){withDocumentReadKey(owner,documentReadContext(authority,request)){called=true}}
            assertFalse(called);assertEquals(32,owner.withDbKey(authority){it.size})
        }finally {owner.invalidate()}
    }
    @Test fun genuineSessionRevokeStillInvalidatesOwnerAndWipesLease() {
        val owner=SessionDbKeyOwner.bind(authority,ByteArray(32){1});val request=DocumentTextRequest(1);lateinit var lease:ByteArray
        assertThrows(ProductStorageFailure::class.java){withDocumentReadKey(owner,documentReadContext(authority,request)){lease=it;allowed=false;Unit}}
        assertTrue(lease.all{it==0.toByte()});allowed=true // Test fake only: owner must stay invalidated.
        assertThrows(ProductStorageFailure::class.java){owner.withDbKey(authority){error("No resurrected owner")}}
    }
}
