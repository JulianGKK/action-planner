package org.pag.data.product

import org.pag.domain.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Metadata of exactly the admitted read. It never holds a payload or mints a ticket. */
internal class ProductActiveCoreLifetime(
    private val access: StorageAccess,
    private val context: StorageContext,
    private val mutations: WorkspaceMutationInvalidation,
    private val ticket: WorkspaceMutationInvalidation.ActiveReadTicket,
    private val laneTicket: Long,
    private val owner: Thread,
    private val loadCurrent: () -> Boolean,
    private val hitObservation: (Boolean,Long) -> Unit = {_,_->},
    private val memory:OutstandingCoreRead.Read?=null
) : ActiveCoreLifetime {
    private val closed=AtomicBoolean(false)
    private val listener=AtomicReference<(() -> Unit)?>(null)
    private val registration=AtomicReference<Subscription?>(null)
    init {
        val subscription=ticket.onInvalidated { close() }
        registration.set(subscription)
        if(closed.get()) registration.getAndSet(null)?.cancel()
    }
    override fun isCurrent(): Boolean {
        if(Thread.currentThread() !== owner || closed.get() || !ticket.isLive()) return false
        return loadCurrent() && mutations.isCurrentActiveRead(ticket) &&
            context.scope==AccessScope.READY && context.generation==ticket.generation &&
            context.check(AccessPoint.PUBLICATION) && !closed.get() && ticket.isLive() &&
            DocumentNamespaceLane.isCurrent(laneTicket)
    }
    override fun publish(install: () -> Unit): Boolean {
        if(!isCurrent()) return false
        var entered=false
        val accepted=runCatching { context.publish { if(isCurrent()) { entered=true;install() } } }.getOrDefault(false)
        val valid=accepted && entered && isCurrent()
        if(!valid) close()
        return valid
    }
    override fun authorizeHit(actionId: String): Boolean {
        // No owner fields or central acquisition from a foreign thread.
        if(Thread.currentThread() !== owner) return false
        val started=System.nanoTime()
        val valid=runCatching {
            if(actionId!=ticket.actionId || !isCurrent()) return@runCatching false
            val fresh=access.acquire(AccessScope.READY) ?: return@runCatching false
            fun current() = fresh.scope==AccessScope.READY && fresh.generation==ticket.generation &&
                fresh.check(AccessPoint.PUBLICATION) && actionId==ticket.actionId && isCurrent()
            if(!current()) return@runCatching false
            var entered=false
            // Only a local receipt inside the gate: no slot/UI changes before its return.
            val accepted=fresh.publish { if(current()) entered=true }
            accepted && entered && current()
        }.getOrDefault(false)
        hitObservation(valid,System.nanoTime()-started)
        if(!valid) close()
        return valid
    }
    override fun onInvalidated(invalidate: () -> Unit): Subscription {
        check(listener.compareAndSet(null,invalidate))
        if(closed.get() || !ticket.isLive()) listener.getAndSet(null)?.invoke()
        return Subscription { listener.compareAndSet(invalidate,null) }
    }
    override fun compositionObserved(){memory?.compositionObserved()}
    override fun compositionDisposed(){memory?.compositionDisposed()}
    override fun close() {
        if(closed.compareAndSet(false,true)) {
            mutations.revokeActiveRead(ticket)
            listener.getAndSet(null)?.let { runCatching(it) }
            memory?.revoke()
        }
        registration.getAndSet(null)?.cancel()
    }
}
