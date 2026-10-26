package org.pag.prototype.product

import org.pag.domain.*

/** One pre-admitted completion representation, not a writer/read authority or end receipt.
 * Held/lost/duplicate callbacks cannot recycle it. Token metadata never captures a board,
 * snapshot, draft, payload list or key. Text is produced under the writer's existing copy
 * receipt; the fixed copy allowance covers the existing constant/role-name feedback forms.
 */
internal class OriginalFeedbackCredit(private val access:WorkspaceAccess,private val actionId:String,
    private val generation:Long,private val current:(String,Long)->Boolean,
    private val updated:((String,Long)->Boolean)?=null,private val publish:(String)->Unit):(String)->Unit {
    private val consumed=java.util.concurrent.atomic.AtomicBoolean(false)
    override fun invoke(text:String){
        if(!consumed.compareAndSet(false,true))return
        if(!current(actionId,generation) || !access.heldAllowed())return
        // The refusal text reports only the confirmed writer outcome. A success label
        // additionally needs the newly bound original read authority, never the old ticket.
        if(text!=savedNotRefreshed && updated?.invoke(actionId,generation)==false)return
        access.consume(check){
            if(current(actionId,generation) && access.heldAllowed() &&
                (text==savedNotRefreshed || updated?.invoke(actionId,generation)!=false))publish(text)
        }
    }
    companion object {
        const val savedNotRefreshed="Gespeichert – Ansicht konnte noch nicht aktualisiert werden."
        val check=ActiveWorkspaceBudget.Units()
        val cost=ActiveWorkspaceBudget.Units(objects=16,strings=4,utf16Units=1024,references=64)
    }
}
internal typealias WorkspaceFeedbackFactory=()->((String)->Unit)?
