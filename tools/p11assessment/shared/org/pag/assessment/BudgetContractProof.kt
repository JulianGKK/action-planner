package org.pag.assessment

/** Test-only conservative charge model. Not a certified ART deep-size or production admission. */
class BudgetContractProof(private val limit:Long) {
    enum class Result { ADMITTED, BUDGET_REFUSED }
    var charged=0L;private set
    private var refused=false
    init{require(limit>=0)}
    fun charge(rows:Long,codePoints:Long,references:Long,collections:Long):Result {
        require(rows>=0 && codePoints>=0 && references>=0 && collections>=0) {"INVALID_AGGREGATE"}
        if(refused)return Result.BUDGET_REFUSED
        val next=try{
            // Inclusive overhead allowances, 4 payload bytes/codepoint, 8-byte refs, worst-case alignment.
            val objects=Math.multiplyExact(rows,128)
            val text=Math.multiplyExact(codePoints,4)
            val refs=Math.multiplyExact(references,8)
            val containers=Math.multiplyExact(collections,64)
            Math.addExact(charged,Math.addExact(Math.addExact(objects,text),Math.addExact(refs,containers)))
        }catch(_:ArithmeticException){refused=true;return Result.BUDGET_REFUSED}
        if(next>limit){refused=true;return Result.BUDGET_REFUSED}
        charged=next;return Result.ADMITTED
    }
    /** Called before fetching/materializing the next bounded row, not after loading a whole list. */
    fun admitRow(utf16Units:Long,fieldCount:Long,referenceCount:Long):Result {
        // 2 bytes/code unit <= 4 per codepoint; charging 4 per unit deliberately overestimates.
        require(utf16Units>=0 && fieldCount>=0 && referenceCount>=0){"INVALID_AGGREGATE"}
        val refs=try{Math.addExact(fieldCount,referenceCount)}catch(_:ArithmeticException){refused=true;return Result.BUDGET_REFUSED}
        return charge(1,utf16Units,refs,fieldCount)
    }
}
