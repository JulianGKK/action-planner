package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executor

class P81RuntimeFaultsTest {
    @Test fun wrapperCannotGrantMissingContextOrRejectedPublication() {
        val f=P81RuntimeFaults()
        assertNull(f.wrap(object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext?=null}).acquire())
        var calls=0
        var allowed=true
        val access=object:StorageAccess {
            override fun acquire(scope:AccessScope)=object:StorageContext {
                override val scope=scope
                override val generation=7L
                override fun check(point:AccessPoint)=allowed
                override fun publish(result:()->Unit):Boolean {if(!allowed)return false;result();calls++;return true}
            }
        }
        val c=checkNotNull(f.wrap(access).acquire())
        assertEquals(7L,c.generation)
        f.falseAfterCallback.set(true)
        assertFalse(c.publish{})
        assertTrue(f.callbackEntered.get());assertEquals(1,calls)
        allowed=false
        assertFalse(c.check(AccessPoint.STEP));assertFalse(c.publish{})
        assertEquals(1,calls)
    }
    @Test fun gateHoldsExactlyOneRealRunnableAndClearsWithoutReplay() {
        var calls=0
        val g=P81RuntimeFaults.Gate()
        val executor=g.wrap(Executor{it.run()})
        g.holdNext();executor.execute{calls++}
        assertTrue(g.isHeld());assertEquals(0,calls)
        assertThrows(IllegalStateException::class.java){g.holdNext()}
        g.release();assertEquals(1,calls);assertFalse(g.isHeld())
        assertThrows(IllegalStateException::class.java){g.take()}
        g.holdNext();executor.execute{calls++};g.discard()
        assertEquals(1,calls);assertFalse(g.isHeld())
        executor.execute{calls++};assertEquals(2,calls)
    }
}
