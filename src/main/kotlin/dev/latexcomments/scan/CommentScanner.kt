package dev.latexcomments.scan

/** What kind of comment: `//`, `/* */`, or a documentation comment (`///`, `/** */`, Python docstrings). */
enum class CommentKind { LINE, BLOCK, DOC }

/** A piece of a comment's text (document offsets, end exclusive), without comment markers or line decoration. */
data class Segment(val start: Int, val end: Int)

/**
 * One comment. Consecutive line comments that each start their own line are merged into one comment, so maths can
 * run across lines. [trailing]: the comment follows code on its first line.
 */
data class Comment(
    val start: Int, val end: Int, val kind: CommentKind, val segments: List<Segment>, val trailing: Boolean,
    /** Made of `//`-style line comments (rather than a block). */
    val lineStyle: Boolean = false,
)

/**
 * How comments and strings look in one family of languages. Strings matter only so that `//` or `#` inside them
 * isn't taken for a comment.
 */
class CommentSyntax(
    val lineMarkers: List<String>,
    val docLineMarkers: List<String> = emptyList(),
    val blocks: List<Pair<String, String>> = emptyList(),
    val docBlockStarts: List<String> = emptyList(),
    val nestedBlocks: Boolean = false,
    /** Quote characters that start ordinary strings with backslash escapes. */
    val quotes: String = "\"",
    /** `'` starts a short character literal ('a', '\n') rather than a string; a lone `'` (Rust lifetimes) is ignored. */
    val charLiterals: Boolean = true,
    /** Triple-quoted strings ("""...""", and '''...''' when `'` is a quote). */
    val tripleQuotes: Boolean = false,
    /** C# verbatim and raw strings (@"...", """..."""). */
    val csharpStrings: Boolean = false,
    /** Strings in triple quotes at the start of a line are documentation (Python docstrings). */
    val docstrings: Boolean = false,
) {
    companion object {
        val C_LIKE = CommentSyntax(listOf("//"), listOf("///", "//!"), listOf("/*" to "*/"), listOf("/**", "/*!"))
        val CSHARP = CommentSyntax(listOf("//"), listOf("///"), listOf("/*" to "*/"), listOf("/**"), csharpStrings = true)
        val JAVA = CommentSyntax(listOf("//"), emptyList(), listOf("/*" to "*/"), listOf("/**"), tripleQuotes = true)
        val KOTLIN = CommentSyntax(listOf("//"), emptyList(), listOf("/*" to "*/"), listOf("/**"), nestedBlocks = true, tripleQuotes = true)
        val SCALA_SWIFT = CommentSyntax(listOf("//"), listOf("///"), listOf("/*" to "*/"), listOf("/**"), nestedBlocks = true, tripleQuotes = true)
        val RUST = CommentSyntax(listOf("//"), listOf("///", "//!"), listOf("/*" to "*/"), listOf("/**", "/*!"), nestedBlocks = true)
        val GO = CommentSyntax(listOf("//"), emptyList(), listOf("/*" to "*/"), quotes = "\"`")
        val JS = CommentSyntax(listOf("//"), emptyList(), listOf("/*" to "*/"), listOf("/**"), quotes = "\"'`", charLiterals = false)
        val FSHARP = CommentSyntax(listOf("//"), listOf("///"), listOf("(*" to "*)"), nestedBlocks = true, tripleQuotes = true)
        val PYTHON = CommentSyntax(listOf("#"), quotes = "\"'", charLiterals = false, tripleQuotes = true, docstrings = true)
        val HASH = CommentSyntax(listOf("#"), quotes = "\"'", charLiterals = false)
        val JULIA = CommentSyntax(listOf("#"), blocks = listOf("#=" to "=#"), nestedBlocks = true, tripleQuotes = true, docstrings = true)
        val R = CommentSyntax(listOf("#"), listOf("#'"), quotes = "\"'", charLiterals = false)
        val LUA = CommentSyntax(listOf("--"), listOf("---"), listOf("--[[" to "]]"), quotes = "\"'", charLiterals = false)
        val SQL = CommentSyntax(listOf("--"), blocks = listOf("/*" to "*/"), quotes = "'", charLiterals = false)
        val HASKELL = CommentSyntax(listOf("--"), listOf("-- |"), listOf("{-" to "-}"), nestedBlocks = true)
        val MATLAB = CommentSyntax(listOf("%"), blocks = listOf("%{" to "%}"), quotes = "\"", charLiterals = false)

        private val byExtension: Map<String, CommentSyntax> = buildMap {
            for (e in listOf("cs", "csx")) put(e, CSHARP)
            for (e in listOf("java")) put(e, JAVA)
            for (e in listOf("kt", "kts")) put(e, KOTLIN)
            for (e in listOf("scala", "sc", "swift", "groovy", "gradle")) put(e, SCALA_SWIFT)
            for (e in listOf("c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "m", "mm", "cu", "cuh", "glsl", "hlsl", "frag", "vert", "proto", "d", "dart", "php")) put(e, C_LIKE)
            put("rs", RUST); put("go", GO)
            for (e in listOf("js", "mjs", "cjs", "jsx", "ts", "tsx", "mts", "cts")) put(e, JS)
            for (e in listOf("fs", "fsi", "fsx")) put(e, FSHARP)
            for (e in listOf("py", "pyi", "pyw")) put(e, PYTHON)
            for (e in listOf("rb", "sh", "bash", "zsh", "pl", "pm", "yaml", "yml", "toml", "cmake", "ps1", "nim", "tcl")) put(e, HASH)
            put("jl", JULIA); put("r", R)
            put("lua", LUA); put("sql", SQL)
            for (e in listOf("hs", "lhs", "elm")) put(e, HASKELL)
        }

        /** The syntax for a file name, or null for files that aren't code (plain text, Markdown, ...). */
        fun forFileName(name: String): CommentSyntax? {
            val lower = name.lowercase()
            if (lower == "cmakelists.txt" || lower == "makefile" || lower == "dockerfile") return HASH
            val ext = lower.substringAfterLast('.', "")
            return byExtension[ext]
        }
    }
}

/**
 * Finds comments by reading the text, skipping strings. Works for every language the same way and needs no
 * parser (Rider's C# code is parsed by its .NET backend, so the IDE side has no comment tree for it).
 */
object CommentScanner {
    fun scan(text: CharSequence, syntax: CommentSyntax): List<Comment> {
        val raw = ArrayList<Comment>()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            // Block comments (doc blocks first: "/**" also starts "/*").
            val block = syntax.blocks.firstOrNull { (open, _) -> text.startsWith(open, i) }
            if (block != null && !lineMarkerHere(text, i, syntax, preferLonger = block.first)) {
                val end = blockEnd(text, i, block, syntax.nestedBlocks)
                // "/**/" is an empty block comment, not the start of a doc comment.
                val docStart = syntax.docBlockStarts.firstOrNull { text.startsWith(it, i) }
                    ?.takeIf { end - i > it.length + 1 }
                val openLen = docStart?.length ?: block.first.length
                val closed = end - block.second.length >= i + openLen && text.startsWith(block.second, end - block.second.length)
                raw.add(Comment(i, end, if (docStart != null) CommentKind.DOC else CommentKind.BLOCK,
                    blockSegments(text, i + openLen, if (closed) end - block.second.length else end),
                    trailing = !onlySpaceBefore(text, i)))
                i = end
                continue
            }
            val marker = syntax.lineMarkers.firstOrNull { text.startsWith(it, i) }
            if (marker != null) {
                val doc = syntax.docLineMarkers.filter { text.startsWith(it, i) }.maxByOrNull { it.length }
                val lineEnd = lineEnd(text, i)
                val contentStart = i + (doc ?: marker).length
                raw.add(Comment(i, lineEnd, if (doc != null) CommentKind.DOC else CommentKind.LINE,
                    listOf(Segment(contentStart, lineEnd)), trailing = !onlySpaceBefore(text, i), lineStyle = true))
                i = lineEnd
                continue
            }
            if (syntax.docstrings && syntax.tripleQuotes && (text.startsWith("\"\"\"", i) || text.startsWith("'''", i)) && onlySpaceBefore(text, i)) {
                val q = text.substring(i, i + 3)
                val close = indexOf(text, q, i + 3)
                val end = if (close < 0) n else close + 3
                raw.add(Comment(i, end, CommentKind.DOC, blockSegments(text, i + 3, if (close < 0) n else close, starDecoration = false), trailing = false))
                i = end
                continue
            }
            val skip = skipString(text, i, syntax)
            if (skip > i) { i = skip; continue }
            i++
        }
        return mergeLineComments(text, raw)
    }

    /** True when a line marker that is longer than [preferLonger] starts here (e.g. Lua "---" vs "--[["). */
    private fun lineMarkerHere(text: CharSequence, i: Int, syntax: CommentSyntax, preferLonger: String): Boolean =
        syntax.lineMarkers.any { it.length > preferLonger.length && text.startsWith(it, i) }

    private fun CharSequence.startsWith(s: String, at: Int): Boolean {
        if (at < 0 || at + s.length > length) return false
        for (k in s.indices) if (this[at + k] != s[k]) return false
        return true
    }

    private fun indexOf(text: CharSequence, s: String, from: Int): Int {
        var k = from
        while (k <= text.length - s.length) { if (text.startsWith(s, k)) return k; k++ }
        return -1
    }

    private fun lineEnd(text: CharSequence, from: Int): Int {
        var k = from
        while (k < text.length && text[k] != '\n' && text[k] != '\r') k++
        return k
    }

    private fun onlySpaceBefore(text: CharSequence, at: Int): Boolean {
        var k = at - 1
        while (k >= 0 && text[k] != '\n' && text[k] != '\r') { if (!text[k].isWhitespace()) return false; k-- }
        return true
    }

    private fun blockEnd(text: CharSequence, start: Int, block: Pair<String, String>, nested: Boolean): Int {
        val (open, close) = block
        var depth = 0
        var k = start
        while (k < text.length) {
            if (nested || depth == 0) {
                if (text.startsWith(open, k)) { depth++; k += open.length; continue }
            }
            if (text.startsWith(close, k)) {
                depth--
                k += close.length
                if (depth <= 0) return k
                continue
            }
            k++
        }
        return text.length // unterminated: to the end of the file
    }

    /** The text lines of a block comment, without the leading " * " decoration of each following line. */
    private fun blockSegments(text: CharSequence, from: Int, to: Int, starDecoration: Boolean = true): List<Segment> {
        val out = ArrayList<Segment>()
        var lineFrom = from
        var first = true
        while (lineFrom <= to) {
            var lineTo = lineFrom
            while (lineTo < to && text[lineTo] != '\n' && text[lineTo] != '\r') lineTo++
            var s = lineFrom
            if (!first) {
                while (s < lineTo && (text[s] == ' ' || text[s] == '\t')) s++
                if (starDecoration && s < lineTo && text[s] == '*' && !(s + 1 < text.length && text[s + 1] == '/')) s++
            }
            out.add(Segment(s, lineTo))
            first = false
            if (lineTo >= to) break
            lineFrom = lineTo + (if (text[lineTo] == '\r' && lineTo + 1 < text.length && text[lineTo + 1] == '\n') 2 else 1)
        }
        return out
    }

    /** Returns the index after a string or character literal starting at [i], or [i] if none starts there. */
    private fun skipString(text: CharSequence, i: Int, s: CommentSyntax): Int {
        val n = text.length
        val c = text[i]
        if (s.csharpStrings) {
            // Raw strings: three or more quotes, closed by the same number.
            var q = 0
            var k = i
            while (k < n && text[k] == '$') k++
            val afterDollars = k
            while (k < n && text[k] == '"') { q++; k++ }
            if (q >= 3) {
                val closer = "\"".repeat(q)
                val close = indexOf(text, closer, k)
                return if (close < 0) n else close + q
            }
            // Verbatim strings: @"...", $@"...", @$"..." with "" as an escaped quote.
            var v = i
            var at = false
            while (v < n && (text[v] == '@' || text[v] == '$')) { if (text[v] == '@') at = true; v++ }
            if (at && v < n && text[v] == '"') {
                var m = v + 1
                while (m < n) {
                    if (text[m] == '"') { if (m + 1 < n && text[m + 1] == '"') { m += 2; continue }; return m + 1 }
                    m++
                }
                return n
            }
            if (afterDollars > i && afterDollars < n && text[afterDollars] == '"') return quoted(text, afterDollars, '"')
        }
        if (s.tripleQuotes && (text.startsWith("\"\"\"", i) || (s.quotes.contains('\'') && text.startsWith("'''", i)))) {
            val q = text.substring(i, i + 3)
            val close = indexOf(text, q, i + 3)
            return if (close < 0) n else close + 3
        }
        if (c in s.quotes) return quoted(text, i, c)
        if (c == '\'' && s.charLiterals) {
            // 'a', '\n', 'A': a short literal closed on the same line; otherwise not a literal (Rust lifetimes).
            var m = i + 1
            while (m < n && m <= i + 10 && text[m] != '\n') {
                if (text[m] == '\\') { m += 2; continue }
                if (text[m] == '\'') return if (m > i + 1) m + 1 else i
                m++
            }
            return i
        }
        return i
    }

    /** A string with backslash escapes. Ordinary strings end at the line end; backtick strings may span lines. */
    private fun quoted(text: CharSequence, i: Int, q: Char): Int {
        var m = i + 1
        while (m < text.length) {
            val ch = text[m]
            if (ch == '\\') { m += 2; continue }
            if (ch == q) return m + 1
            if ((ch == '\n' || ch == '\r') && q != '`') return m
            m++
        }
        return text.length
    }

    /** Joins line comments of the same kind on consecutive lines (each starting its own line) into one comment. */
    private fun mergeLineComments(text: CharSequence, comments: List<Comment>): List<Comment> {
        val out = ArrayList<Comment>()
        for (c in comments) {
            val last = out.lastOrNull()
            if (last != null && c.kind == last.kind && c.lineStyle && last.lineStyle && !c.trailing && !last.trailing &&
                onlyOneLineBreakBetween(text, last.end, c.start)) {
                out[out.size - 1] = Comment(last.start, c.end, last.kind, last.segments + c.segments, false, lineStyle = true)
            } else out.add(c)
        }
        return out
    }

    private fun onlyOneLineBreakBetween(text: CharSequence, from: Int, to: Int): Boolean {
        var breaks = 0
        var k = from
        while (k < to) {
            val ch = text[k]
            if (ch == '\n') breaks++
            else if (ch != '\r' && ch != ' ' && ch != '\t') return false
            k++
        }
        return breaks == 1
    }
}
