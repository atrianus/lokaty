package pl.lokaty.engine

/** Minimalny parser JSON bez zewnętrznych bibliotek (silnik jest czystym modułem JVM). Liczby zwraca jako String, żeby nie tracić dokładności dziesiętnej. */
class MiniJson(private val s: String) {
    private var i = 0
    fun parse(): Any? { val v = value(); ws(); require(i == s.length) { "śmieci po JSON na $i" }; return v }
    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    private fun value(): Any? { ws()
        return when (s[i]) {
            '{' -> obj(); '[' -> arr(); '"' -> str()
            else -> { val st = i; while (i < s.length && s[i] !in ",]} \n\r\t") i++
                val t = s.substring(st, i); if (t == "null") null else if (t == "true") true else if (t == "false") false else t }
        } }
    private fun obj(): Map<String, Any?> { i++; val m = LinkedHashMap<String, Any?>(); ws()
        if (s[i] == '}') { i++; return m }
        while (true) { ws(); val k = str(); ws(); require(s[i++] == ':'); m[k] = value(); ws()
            if (s[i] == ',') { i++; continue }; require(s[i++] == '}'); return m } }
    private fun arr(): List<Any?> { i++; val l = ArrayList<Any?>(); ws()
        if (s[i] == ']') { i++; return l }
        while (true) { l += value(); ws(); if (s[i] == ',') { i++; continue }; require(s[i++] == ']'); return l } }
    private fun str(): String { require(s[i++] == '"'); val sb = StringBuilder()
        while (s[i] != '"') { val c = s[i++]
            if (c == '\\') { when (val e = s[i++]) { 'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }; else -> sb.append(e) } } else sb.append(c) }
        i++; return sb.toString() }
}
