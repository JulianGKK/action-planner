package org.pag.prototype.product

import org.pag.domain.*
import java.util.Collections

/** Bounded derived member of the existing draft, admitted by its original copy gate before
 * construction. Source identities are immutable maps/sets already held by that draft.
 * Recomposition borrows; replacing this member never refunds any previous booking. */
internal class MemoryDraftPresentation(val saved:Map<String,MemoryAnswerValue>,val draft:Map<String,MemoryAnswerValue>,
    val decisions:Set<String>,val section:Int) {
    val combined:Map<String,MemoryAnswerValue> = Collections.unmodifiableMap(buildMap {
        for((key,value) in saved)if(MemoryQuestionnaireV1.question(key)?.sectionOrdinal!=section)put(key,value)
        putAll(draft)
    })
    val conflicts:Set<String> = Collections.unmodifiableSet(draft.keys.filter{MemoryQuestionnaireV1.question(it)?.dependency?.active(combined)==false}.toSet())
    val unresolved:Set<String> = Collections.unmodifiableSet(conflicts-decisions)
    val valid:Map<String,Boolean> = Collections.unmodifiableMap(draft.mapValues{(key,value)->MemoryAnswerCodec.encode(key,value)!=null})
    val texts:Map<String,String> = Collections.unmodifiableMap(draft.mapValues{(_,value)->when(value){is MemoryAnswerValue.Text->value.text;is MemoryAnswerValue.Count->value.count.toString();else->""}})
    val labels:Map<String,String> = Collections.unmodifiableMap(combined.mapValues{(key,value)->MemoryAnswerCodec.label(key,value)})
    fun matches(saved:Map<String,MemoryAnswerValue>,draft:Map<String,MemoryAnswerValue>,decisions:Set<String>,section:Int)=
        this.saved===saved && this.draft===draft && this.decisions===decisions && this.section==section
}
