package org.pag.domain

/** Scalar sizing only; no normalized text, intermediate collection or payload cache.
 * Reservations are cumulative on the caller's original owner; this object grants no authority. */
object AppConsumerCopies {
    class Footprint {
        var chars=0L;private set
        var items=0L;private set
        fun text(value:String?){if(value!=null){chars=Math.addExact(chars,value.length.toLong());item()}}
        fun item(count:Long=1){items=Math.addExact(items,count)}
        fun answer(value:MemoryAnswerValue?){when(value){
            is MemoryAnswerValue.Text->text(value.text)
            is MemoryAnswerValue.Option->text(value.key)
            is MemoryAnswerValue.Multi->{item();for(key in value.keys)text(key)}
            is MemoryAnswerValue.Count->item()
            null->Unit
        }}
        fun protocol(value:ProtocolInput){
            item(8);text(value.title);text(value.templateKey);text(value.description);text(value.reportedByPersonId)
            text(value.placeLabel);text(value.announcementText);text(value.statedLegalBasis)
            for(id in value.affectedPersonIds)text(id)
            for(id in value.officialIdentifiers){item();text(id.value)}
        }
        fun memory(value:MemorySectionSave){
            item(8);text(value.authorLabel)
            for((key,answer) in value.answers){text(key);this.answer(answer)}
            for(key in value.retainedConflicts)text(key)
        }
        fun units(extraText:String?=null):ActiveWorkspaceBudget.Units {
            val c=Math.addExact(chars,extraText?.length?.toLong()?:0)
            fun mul(n:Long)=Math.multiplyExact(items,n)
            return ActiveWorkspaceBudget.Units(objects=Math.addExact(64,mul(16)),strings=Math.addExact(16,mul(8)),
                utf16Units=Math.addExact(2048,Math.multiplyExact(c,16)),arrays=16,arrayPayloadBytes=4096,
                lists=16,listCapacity=Math.addExact(128,mul(32)),maps=16,mapCapacity=Math.addExact(64,mul(16)),
                references=Math.addExact(128,mul(64)))
        }
    }
}
