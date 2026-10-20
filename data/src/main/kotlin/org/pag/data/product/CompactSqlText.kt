package org.pag.data.product

/** Scalar UTF8 validation: no JVM payload, including embedded NUL. */
internal object CompactSqlText {
    /** SQLite length(TEXT) stops at NUL. Validate bounded UTF8/UTF16 semantics with a
     * scalar BLOB recursion instead; no full text reaches the JVM. The ASCII branch avoids
     * recursion for the normal case. Whitespace is exactly Kotlin Char.isWhitespace.
     */
    fun validText(column: String, title: Boolean): String {
        val b=(1..4).map { "hex(substr(rest,$it,1))" }
        val continuation={ i:Int -> "${b[i-1]} BETWEEN '80' AND 'BF'" }
        val valid="(${b[0]} BETWEEN '00' AND '7F' OR (${b[0]} BETWEEN 'C2' AND 'DF' AND ${continuation(2)}) OR "+
            "(${b[0]} BETWEEN 'E0' AND 'EF' AND ${continuation(2)} AND ${continuation(3)} AND (${b[0]}!='E0' OR ${b[1]}>='A0') AND (${b[0]}!='ED' OR ${b[1]}<='9F')) OR "+
            "(${b[0]} BETWEEN 'F0' AND 'F4' AND ${continuation(2)} AND ${continuation(3)} AND ${continuation(4)} AND (${b[0]}!='F0' OR ${b[1]}>='90') AND (${b[0]}!='F4' OR ${b[1]}<='8F')))"
        val width="CASE WHEN ${b[0]}<'80' THEN 1 WHEN ${b[0]}<'E0' THEN 2 WHEN ${b[0]}<'F0' THEN 3 ELSE 4 END"
        val cp="COALESCE(unicode(CAST(rest AS TEXT)),0)"
        val ascii=if(title) "length($column) BETWEEN 1 AND 120 AND length(trim($column,char(9,10,11,12,13,28,29,30,31,32)))=length($column)" else "1"
        val semantic=if(title) " AND n BETWEEN 1 AND 120 AND first NOT IN ($WHITESPACE) AND last NOT IN ($WHITESPACE)" else ""
        return "CASE WHEN length(CAST($column AS BLOB))=length($column) AND $column NOT GLOB ('*[^'||char(1)||'-'||char(127)||']*') THEN ($ascii) ELSE "+
            "(WITH RECURSIVE textcheck(rest,n,ok,first,last) AS (VALUES(CAST($column AS BLOB),0,1,0,0) UNION ALL "+
            "SELECT substr(rest,($width)+1),n+1,ok AND $valid,CASE WHEN n=0 THEN $cp ELSE first END,$cp FROM textcheck WHERE length(rest)>0) "+
            "SELECT (ok$semantic) FROM textcheck WHERE length(rest)=0) END"
    }

    private val WHITESPACE=sequenceOf(9..13,28..32,160..160,5760..5760,8192..8202,8232..8233,8239..8239,8287..8287,12288..12288).flatMap { it.asSequence() }.joinToString(",")
}
