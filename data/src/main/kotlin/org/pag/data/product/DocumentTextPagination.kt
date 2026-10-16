package org.pag.data.product

import org.pag.domain.*

/** Streaming scalar-boundary pagination. No String or whole-document buffer. */
internal class DocumentTextPagination(private val requested:Int,private val check:()->Unit):AutoCloseable {
    private val selected=CharArray(DocumentTextLimits.PAGE_BYTES)
    private var index=0;private var bytes=0;private var used=0
    private var high:Char?=null;private var cr=false
    init {if(requested !in 0 until DocumentTextLimits.MAX_PAGES)throw DocumentTextUnavailable()}
    fun accept(chars:CharArray,count:Int) {
        check()
        for(i in 0 until count) {
            if(i%256==0)check()
            val c=chars[i];val pending=high
            if(pending!=null) {
                if(!Character.isLowSurrogate(c))throw ProductStorageFailure()
                high=null;scalar(pending,c,4)
            } else when {
                Character.isHighSurrogate(c)->high=c
                Character.isLowSurrogate(c)->throw ProductStorageFailure()
                else->scalar(c,null,when {c.code<128->1;c.code<2048->2;else->3})
            }
        }
        check()
    }
    private fun scalar(first:Char,second:Char?,size:Int) {
        if(cr) {
            cr=false
            if(first=='\n' && second==null) {unit('\r','\n',2);return}
            unit('\r',null,1)
        }
        if(first=='\r' && second==null)cr=true else unit(first,second,size)
    }
    private fun unit(first:Char,second:Char?,size:Int) {
        if(bytes+size>DocumentTextLimits.PAGE_BYTES) {index=Math.addExact(index,1);bytes=0}
        bytes+=size
        if(index==requested) {selected[used++]=first;if(second!=null)selected[used++]=second}
    }
    fun finish():DocumentTextPage {
        check()
        if(high!=null)throw ProductStorageFailure()
        if(cr) {cr=false;unit('\r',null,1)}
        if(requested>index)throw DocumentTextUnavailable()
        val chars=selected.copyOf(used)
        try {check();return DocumentTextPage(requested,index+1,chars)}
        catch(failure:Throwable) {chars.fill('\u0000');throw failure}
    }
    override fun close() {selected.fill('\u0000');high=null;cr=false;used=0}
}
internal class DocumentTextUnavailable:RuntimeException()
/** Only reported after the ciphertext still reached authenticated EOF and closed successfully. */
internal class DocumentTextFormatFailure:RuntimeException()
