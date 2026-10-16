package org.pag.prototype.product

/** Layout fragments of one bounded page. Concatenation is exact, without surrogate/CRLF cuts.
 * A finite lazy viewport prevents newline-heavy or very long pages exceeding layout limits. */
internal fun documentTextRows(text:String):List<String> {
    val rows=ArrayList<String>()
    val characters=java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT).apply {setText(text)}
    var start=0
    while(start<text.length) {
        var end=minOf(start+256,text.length)
        if(end<text.length) {
            // Prefer a complete platform grapheme. An arbitrarily long cluster may exceed
            // the hard row limit; then retain scalar boundaries instead of unbounded layout.
            if(!characters.isBoundary(end))characters.preceding(end).takeIf {it>=start+128}?.let {end=it}
            if(Character.isHighSurrogate(text[end-1]) && Character.isLowSurrogate(text[end]))end--
            if(text[end-1]=='\r' && text[end]=='\n')end--
        }
        rows+=text.substring(start,end);start=end
    }
    return rows
}
