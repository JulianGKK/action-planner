package org.pag.data.product

import org.pag.domain.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Isolated P77 test controls. Never an authority: all successful checks/publications/key
 * admissions still come from the original SessionCoordinator context. Faults only deny.
 */
class P81RuntimeFaults {
    val worker=Gate()
    val main=Gate()
    val holdNextCoreMain=AtomicBoolean(false)
    val beforeAcquire=AtomicReference<(() -> Unit)?>(null)
    val beforePublish=AtomicReference<(() -> Unit)?>(null)
    val afterPublish=AtomicReference<(() -> Unit)?>(null)
    val falseAfterCallback=AtomicBoolean(false)
    val callbackEntered=AtomicBoolean(false)
    fun wrap(access:StorageAccess)=object:StorageAccess {
        override fun acquire(scope:AccessScope):StorageContext? {
            beforeAcquire.getAndSet(null)?.invoke()
            val c=access.acquire(scope) ?: return null
            return object:StorageContext {
                override val scope get()=c.scope
                override val generation get()=c.generation
                override fun check(point:AccessPoint)=c.check(point)
                override fun <T> admitKey(validUntilMillis:Long,acquire:(()->Boolean)->T?):T?=c.admitKey(validUntilMillis,acquire)
                override fun publish(result:()->Unit):Boolean {
                    beforePublish.getAndSet(null)?.invoke() // Outside coordinator monitor.
                    val deny=falseAfterCallback.getAndSet(false)
                    val accepted=c.publish { callbackEntered.set(true);result() }
                    afterPublish.getAndSet(null)?.invoke() // Outside coordinator monitor.
                    return accepted && !deny
                }
            }
        }
    }
    class Gate {
        private val armed=AtomicBoolean(false)
        private val held=AtomicReference<Runnable?>(null)
        private val executor=AtomicReference<Executor?>(null)
        fun holdNext(){check(held.get()==null && armed.compareAndSet(false,true))}
        fun wrap(delegate:Executor):Executor {
            check(executor.compareAndSet(null,delegate))
            return Executor { task ->
            if(armed.compareAndSet(true,false)) check(held.compareAndSet(null,task)) else delegate.execute(task)
            }
        }
        fun release(){checkNotNull(executor.get()).execute(take())}
        fun isHeld()=held.get()!=null
        fun heldTaskForObservation():Runnable?=held.get()
        fun take():Runnable=checkNotNull(held.getAndSet(null))
        fun discard(){armed.set(false);held.set(null)}
    }
    fun clear(){worker.discard();main.discard();beforeAcquire.set(null);beforePublish.set(null);afterPublish.set(null)}
}
