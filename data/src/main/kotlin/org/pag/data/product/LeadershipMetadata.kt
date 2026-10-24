package org.pag.data.product

import org.pag.domain.*
import org.pag.security.LeadershipKdfParameters

/** Shared structural classification for action state and person inventory; no decryption. */
internal object LeadershipMetadata {
    fun validate(row:LeadershipRow){
        if(row.state!="READY" || row.cryptoVersion!=1 || row.epoch.isBlank() || row.salt.size!=16 || row.envelope.size!=88)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
        LeadershipKdfParameters(row.algorithm,row.kdfVersion,row.memoryKiB,row.iterations,row.parallelism).validate()
    }
    fun validateArea(row:SensitiveRow,actionId:String) {
        if(row.actionId!=actionId || row.personId.isBlank() || row.cryptoVersion!=1 || row.revision<=0 ||
            row.keyEnvelope.size!=60 || row.payload.size<33 ||
            SensitiveArea.entries.none{it.name==row.area})throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
    }
    fun classification(row:LeadershipRow?,rows:List<SensitiveRow>,present:Boolean,usable:Boolean):SensitiveState {
        if(row==null)return if(rows.isEmpty()&&!present)SensitiveState.UNINITIALIZED else SensitiveState.PARTIAL_OR_INVALID
        return try{
            runCatching { LeadershipRecoveryChecks.state(row,rows,present,usable) }.getOrNull()?.let{return it}
            validate(row);if(!present||!usable)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE);rows.forEach{validateArea(it,row.actionId)};SensitiveState.READY}
        catch(_:Exception){SensitiveState.PARTIAL_OR_INVALID}
    }
}
