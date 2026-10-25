package org.pag.domain

/** Validation counts a trimmed range without allocating a second app-owned string. */
internal fun trimmedCodePoints(value:String):Int {
    var start=0;var end=value.length
    while(start<end && value[start].isWhitespace())start++
    while(end>start && value[end-1].isWhitespace())end--
    return Character.codePointCount(value,start,end)
}
