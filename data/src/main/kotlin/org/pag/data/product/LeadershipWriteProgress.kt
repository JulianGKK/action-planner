package org.pag.data.product

import androidx.room3.Transactor
import org.pag.domain.*

/** Sequential multi-phase P3b evidence in the ORIGINAL writer attempt. No keys, payloads,
 * aliases, second epoch or authority. A phase commit is never reclassified as rollback.
 * WorkspaceWriteCompletion is aggregate: partial/uncertain attempts finish UNKNOWN.
 */
internal class LeadershipWriteProgress(private val progress:WorkspaceWriteProgress) {
    private var scopes=0
    private var cleanupFailed=false
    private var uncertainWrite=false
    private var externalPending=false
    private var externalEffect=false
    var committedPhases=0;private set
    fun opening() { check(scopes<8);scopes++;progress.opening() }
    fun closed(confirmed:Boolean) {
        check(scopes>0);scopes--
        if(!confirmed)cleanupFailed=true
        if(scopes==0 && !cleanupFailed && !externalPending)progress.cleaned()
    }
    fun external(work:()->Unit) {
        externalPending=true;progress.opening()
        work() // Throw leaves UNKNOWN and unconfirmed cleanup, never a claimed Vendor rollback.
        externalPending=false;externalEffect=true
        if(scopes==0 && !cleanupFailed)progress.cleaned()
    }
    suspend fun transaction(tx:Transactor,c:StorageContext,work:suspend()->Unit) {
        uncertainWrite=true
        val problem=confirmedWorkspaceTransaction<SensitiveProblem?>(tx,c,progress,{it!=null}) {
            try{work();null}catch(p:SensitiveProblem){if(p.suppressed.isNotEmpty())throw p;p}
        }
        uncertainWrite=false
        if(problem==null)committedPhases=Math.incrementExact(committedPhases)
        if(problem!=null) {
            if(committedPhases>0 || externalEffect)progress.startingTransaction()
            throw problem
        }
    }
    fun rejectedWithoutEffect() {
        if(scopes!=0 || cleanupFailed || externalPending || externalEffect || uncertainWrite || committedPhases>0)
            throw ProductStorageFailure()
        progress.ended(WorkspaceWriteOutcome.ROLLED_BACK)
        progress.cleaned()
    }
    fun complete() {
        check(scopes==0 && !cleanupFailed && !uncertainWrite && !externalPending && committedPhases>0)
        progress.ended(WorkspaceWriteOutcome.COMMITTED)
        progress.cleaned()
    }
}
