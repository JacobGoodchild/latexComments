package dev.latexcomments.asciimath

/**
 * Converts AsciiMath (asciimath.org: "maths without LaTeX", e.g. `sum_(i=1)^n x_i^2`, `sqrt(x)`, `a/b`) to LaTeX
 * for JLaTeXMath. Written for this plugin from the published AsciiMath syntax:
 *
 *     S ::= v | l E r | u S | b S S       simple expression (symbol, bracketed group, unary, binary)
 *     I ::= S_S | S^S | S_S^S | S         with subscript / superscript
 *     E ::= I E | I/I                     sequence; a/b is a fraction
 *
 * It never throws: unbalanced brackets are closed, unknown characters are passed through.
 */
object AsciiMath {
    private enum class Kind { CONST, UNARY, BINARY, LEFT, RIGHT, BAR, TEXT, INFIX_DIVIDE, SUB, SUP, COMMA }

    private class Sym(val kind: Kind, val tex: String)

    private val symbols: Map<String, Sym> = buildMap {
        fun c(tex: String, vararg names: String) { for (n in names) put(n, Sym(Kind.CONST, tex)) }
        fun u(tex: String, vararg names: String) { for (n in names) put(n, Sym(Kind.UNARY, tex)) }
        fun b(tex: String, vararg names: String) { for (n in names) put(n, Sym(Kind.BINARY, tex)) }
        // Greek letters.
        for (g in listOf("alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta", "vartheta",
                "iota", "kappa", "lambda", "mu", "nu", "xi", "pi", "rho", "sigma", "tau", "upsilon", "phi", "varphi",
                "chi", "psi", "omega", "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Phi", "Psi", "Omega"))
            c("\\$g", g)
        // Operators.
        c("+", "+"); c("-", "-"); c("\\cdot", "*", "cdot"); c("\\ast", "**"); c("\\star", "***"); c("/", "//")
        c("\\backslash", "\\\\", "backslash"); c("\\times", "xx", "times"); c("\\ltimes", "|><"); c("\\rtimes", "><|")
        c("\\bowtie", "|><|"); c("\\div", "-:", "div"); c("\\circ", "@", "circ"); c("\\oplus", "o+", "oplus")
        c("\\otimes", "ox", "otimes"); c("\\odot", "o.", "odot"); c("\\sum", "sum"); c("\\prod", "prod")
        c("\\wedge", "^^", "wedge"); c("\\bigwedge", "^^^", "bigwedge"); c("\\vee", "vv", "vee"); c("\\bigvee", "vvv", "bigvee")
        c("\\cap", "nn", "cap"); c("\\bigcap", "nnn", "bigcap"); c("\\cup", "uu", "cup"); c("\\bigcup", "uuu", "bigcup")
        // Relations.
        c("=", "="); c("\\ne", "!=", "ne"); c(":=", ":="); c("<", "<", "lt"); c(">", ">", "gt"); c("\\le", "<=", "le", "leq")
        c("\\ge", ">=", "ge", "geq"); c("\\prec", "-<", "prec"); c("\\preceq", "-<=", "preceq"); c("\\succ", ">-", "succ")
        c("\\succeq", ">-=", "succeq"); c("\\in", "in"); c("\\notin", "!in", "notin"); c("\\subset", "sub", "subset")
        c("\\supset", "sup", "supset"); c("\\subseteq", "sube", "subseteq"); c("\\supseteq", "supe", "supseteq")
        c("\\equiv", "-=", "equiv"); c("\\cong", "~=", "cong"); c("\\approx", "~~", "approx"); c("\\propto", "prop", "propto")
        c("\\sim", "~", "sim"); c("\\ll", "<<<", "ll"); c("\\gg", ">>>", "gg")
        // Logic.
        c("\\text{ and }", "and"); c("\\text{ or }", "or"); c("\\neg", "not", "neg"); c("\\Rightarrow", "=>", "implies")
        c("\\text{ if }", "if"); c("\\Leftrightarrow", "<=>", "iff"); c("\\forall", "AA", "forall"); c("\\exists", "EE", "exists")
        c("\\bot", "_|_", "bot"); c("\\top", "TT", "top"); c("\\vdash", "|--", "vdash"); c("\\models", "|==", "models")
        // Miscellaneous.
        c("\\int", "int"); c("\\oint", "oint"); c("\\partial", "del", "partial"); c("\\nabla", "grad", "nabla")
        c("\\pm", "+-", "pm"); c("\\mp", "-+", "mp"); c("\\emptyset", "O/", "emptyset"); c("\\infty", "oo", "infty")
        c("\\aleph", "aleph"); c("\\therefore", ":.", "therefore"); c("\\because", ":'", "because")
        c("\\ldots", "...", "ldots"); c("\\cdots", "cdots"); c("\\vdots", "vdots"); c("\\ddots", "ddots")
        c("\\ ", "\\ "); c("\\quad", "quad"); c("\\qquad", "qquad"); c("\\angle", "/_", "angle"); c("\\frown", "frown")
        c("\\triangle", "/_\\", "triangle"); c("\\diamond", "diamond"); c("\\square", "square")
        c("\\lfloor", "|__", "lfloor"); c("\\rfloor", "__|", "rfloor"); c("\\lceil", "|~", "lceil"); c("\\rceil", "~|", "rceil")
        c("\\mathbb{C}", "CC"); c("\\mathbb{N}", "NN"); c("\\mathbb{Q}", "QQ"); c("\\mathbb{R}", "RR"); c("\\mathbb{Z}", "ZZ")
        c("\\hbar", "hbar"); c("\\ell", "ell"); c("'", "'"); c("!", "!"); c("{:d x:}", "dx"); c("{:d y:}", "dy")
        c("{:d z:}", "dz"); c("{:d t:}", "dt")
        // Functions (upright names).
        for (f in listOf("sin", "cos", "tan", "sec", "csc", "cot", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
                "coth", "exp", "log", "ln", "det", "dim", "gcd", "lim", "max", "min", "sup", "inf", "arg", "deg", "Pr"))
            c("\\$f", f)
        c("\\operatorname{sech}", "sech"); c("\\operatorname{csch}", "csch"); c("\\operatorname{lcm}", "lcm")
        c("\\operatorname{lub}", "lub"); c("\\operatorname{glb}", "glb"); c("\\operatorname{mod}", "mod")
        c("\\operatorname{sign}", "sgn", "sign"); c("\\liminf", "liminf"); c("\\limsup", "limsup")
        c("\\mathop{\\mathrm{argmin}}", "argmin"); c("\\mathop{\\mathrm{argmax}}", "argmax")
        // Arrows.
        c("\\uparrow", "uarr", "uparrow"); c("\\downarrow", "darr", "downarrow"); c("\\rightarrow", "rarr", "rightarrow")
        c("\\to", "->", "to"); c("\\rightarrowtail", ">->"); c("\\twoheadrightarrow", "->>")
        c("\\mapsto", "|->", "mapsto"); c("\\leftarrow", "larr", "leftarrow"); c("\\leftrightarrow", "harr", "leftrightarrow")
        c("\\Rightarrow", "rArr", "Rightarrow"); c("\\Leftarrow", "lArr", "Leftarrow"); c("\\Leftrightarrow", "hArr", "Leftrightarrow")
        // Unary: fonts, accents, roots, text.
        u("\\sqrt", "sqrt"); u("\\mathbf", "bb"); u("\\mathbb", "bbb"); u("\\mathcal", "cc"); u("\\mathtt", "tt")
        u("\\mathfrak", "fr"); u("\\mathsf", "sf"); u("\\mathrm", "rm"); u("\\hat", "hat"); u("\\overline", "bar", "overline")
        u("\\underline", "ul", "underline"); u("\\vec", "vec"); u("\\dot", "dot"); u("\\ddot", "ddot"); u("\\tilde", "tilde")
        u("\\overbrace", "obrace", "overbrace"); u("\\underbrace", "ubrace", "underbrace"); u("\\cancel", "cancel")
        u("abs", "abs"); u("floor", "floor"); u("ceil", "ceil"); u("norm", "norm")
        put("text", Sym(Kind.TEXT, "\\text")); put("mbox", Sym(Kind.TEXT, "\\text"))
        // Binary.
        b("\\frac", "frac"); b("\\sqrt", "root"); b("\\stackrel", "stackrel", "overset"); b("\\underset", "underset")
        b("\\textcolor", "color")
        // Brackets.
        for ((n, t) in listOf("(" to "(", "[" to "[", "{" to "\\{", "(:" to "\\langle", "<<" to "\\langle", "{:" to ".", "langle" to "\\langle"))
            put(n, Sym(Kind.LEFT, t))
        for ((n, t) in listOf(")" to ")", "]" to "]", "}" to "\\}", ":)" to "\\rangle", ">>" to "\\rangle", ":}" to ".", "rangle" to "\\rangle"))
            put(n, Sym(Kind.RIGHT, t))
        put("|", Sym(Kind.BAR, "|")); put("||", Sym(Kind.BAR, "\\|"))
        put("/", Sym(Kind.INFIX_DIVIDE, "/")); put("_", Sym(Kind.SUB, "_")); put("^", Sym(Kind.SUP, "^"))
        put(",", Sym(Kind.COMMA, ","))
    }
    private val maxNameLength = symbols.keys.maxOf { it.length }

    private sealed class Tok {
        class Symbol(val name: String, val sym: Sym) : Tok()
        class Number(val text: String) : Tok()
        class Letter(val ch: String) : Tok()
        class Quoted(val text: String) : Tok()
        class Word(val text: String) : Tok()
    }

    private fun tokenize(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isWhitespace()) { i++; continue }
            if (c == '"') {
                val close = s.indexOf('"', i + 1)
                val end = if (close < 0) s.length else close
                out.add(Tok.Quoted(s.substring(i + 1, end)))
                i = end + 1
                continue
            }
            if (c.isDigit() || (c == '.' && i + 1 < s.length && s[i + 1].isDigit())) {
                var k = i
                var dot = false
                while (k < s.length && (s[k].isDigit() || (s[k] == '.' && !dot && k + 1 < s.length && s[k + 1].isDigit()))) {
                    if (s[k] == '.') dot = true
                    k++
                }
                out.add(Tok.Number(s.substring(i, k)))
                i = k
                continue
            }
            // A run of letters: a known name (phi, sum, sqrt), a name plus one letter (dotx, ulA), a word of three or
            // more letters shown upright (softmax, MSE), or single letters (xy is x times y).
            if (c.isLetter()) {
                var k = i
                while (k < s.length && s[k].isLetter()) k++
                val run = s.substring(i, k)
                val name = (minOf(run.length, maxNameLength) downTo 1).map { run.substring(0, it) }
                    .firstOrNull { symbols.containsKey(it) && (it.length == 1 || run.length - it.length <= 1) }
                if (name != null && (name.length > 1 || run.length < 3)) {
                    out.add(Tok.Symbol(name, symbols.getValue(name))); i += name.length; continue
                }
                if (run.length >= 3) { out.add(Tok.Word(run)); i = k; continue }
                out.add(Tok.Letter(c.toString())); i++; continue
            }
            // The longest symbol name that matches here.
            var matched: String? = null
            for (len in minOf(maxNameLength, s.length - i) downTo 1) {
                val cand = s.substring(i, i + len)
                if (symbols.containsKey(cand) && !(cand.first().isLetter())) { matched = cand; break }
            }
            if (matched != null) { out.add(Tok.Symbol(matched, symbols.getValue(matched))); i += matched.length; continue }
            out.add(Tok.Letter(c.toString()))
            i++
        }
        return out
    }

    /** A piece of output. [inner] is set for bracketed groups, so (a+b)/2 gives \frac{a+b}{2}. */
    private class Piece(val tex: String, val inner: String? = null, val rows: List<List<String>>? = null,
                        val open: String? = null, val close: String? = null, val cells: List<String>? = null) {
        val stripped get() = inner ?: tex
    }

    private class Parser(val toks: List<Tok>) {
        var pos = 0
        fun peek() = toks.getOrNull(pos)
        fun peekSym() = (peek() as? Tok.Symbol)?.sym
        fun isRight() = peekSym()?.kind == Kind.RIGHT

        /** E: a sequence of I, with a/b as fractions. Stops at a closing bracket (or a comma when [stopAtComma]). */
        fun expression(stopAtComma: Boolean = false, closingBar: Boolean = false): List<Piece> {
            val out = ArrayList<Piece>()
            while (pos < toks.size) {
                val s = peekSym()
                if (s?.kind == Kind.RIGHT) break
                if (closingBar && s?.kind == Kind.BAR) break
                if (stopAtComma && s?.kind == Kind.COMMA) break
                val i = intermediate() ?: break
                if (peekSym()?.kind == Kind.INFIX_DIVIDE) {
                    pos++
                    val den = intermediate() ?: Piece("")
                    out.add(Piece("\\frac{${i.stripped}}{${den.stripped}}"))
                } else out.add(i)
            }
            return out
        }

        /** I: S with an optional subscript and superscript (either order). */
        fun intermediate(): Piece? {
            val base = simple() ?: return null
            var sub: Piece? = null
            var sup: Piece? = null
            repeat(2) {
                when (peekSym()?.kind) {
                    Kind.SUB -> if (sub == null) { pos++; sub = script() }
                    Kind.SUP -> if (sup == null) { pos++; sup = script() }
                    else -> {}
                }
            }
            if (sub == null && sup == null) return base
            val sb = StringBuilder(braceIfNeeded(base.tex))
            sub?.let { sb.append("_{").append(it.stripped).append('}') }
            sup?.let { sb.append("^{").append(it.stripped).append('}') }
            return Piece(sb.toString())
        }

        private fun braceIfNeeded(t: String) = if (t.isEmpty()) "{}" else t

        /** A sub- or superscript: a simple expression, allowing a leading minus sign (x^-1). */
        fun script(): Piece {
            val t = peek()
            if (t is Tok.Symbol && t.name == "-") {
                pos++
                val s = simple() ?: return Piece("-")
                return Piece("-" + s.stripped)
            }
            return simple() ?: Piece("")
        }

        /** S: a symbol, a bracketed group, a unary or binary function applied to its arguments. */
        fun simple(): Piece? {
            val t = peek() ?: return null
            pos++
            return when (t) {
                is Tok.Number -> Piece(t.text)
                is Tok.Letter -> Piece(escape(t.ch))
                is Tok.Quoted -> Piece("\\text{${textEscape(t.text)}}")
                is Tok.Word -> Piece("\\mathrm{${t.text}}")
                is Tok.Symbol -> when (t.sym.kind) {
                    Kind.CONST -> Piece(constant(t.sym.tex))
                    Kind.LEFT -> group(t.sym.tex, t.name)
                    Kind.BAR -> barGroup(t.sym.tex)
                    Kind.UNARY -> unary(t.sym.tex)
                    Kind.BINARY -> binary(t.sym.tex)
                    Kind.TEXT -> textArg()
                    Kind.RIGHT -> Piece(if (t.sym.tex == ".") "" else t.sym.tex)
                    Kind.COMMA -> Piece(",")
                    Kind.INFIX_DIVIDE -> Piece("/")
                    Kind.SUB -> Piece("\\_")
                    Kind.SUP -> Piece("\\hat{}")
                }
            }
        }

        /** A command name ending in a letter gets a space, so it doesn't run into the next letter (\\pi r). */
        private fun constant(tex: String): String = when {
            tex.startsWith("{:") -> tex.removePrefix("{:").removeSuffix(":}") + " " // dx -> d x
            tex.startsWith("\\") && tex.last().isLetter() -> "$tex "
            else -> tex
        }

        /** l E r. A group of groups like ((a,b),(c,d)) becomes a matrix. */
        fun group(open: String, openName: String): Piece {
            val items = ArrayList<List<Piece>>()
            items.add(expression(stopAtComma = true))
            while (peekSym()?.kind == Kind.COMMA) { pos++; items.add(expression(stopAtComma = true)) }
            var close = ")"
            val closeTok = peek()
            if (closeTok is Tok.Symbol && closeTok.sym.kind == Kind.RIGHT) { pos++; close = closeTok.sym.tex }
            else close = matching(openName)
            val cells = items.map { it.joinToString("") { p -> p.tex }.trim() }
            matrixRows(items)?.let { rows ->
                val body = rows.joinToString(" \\\\ ") { it.joinToString(" & ") }
                return Piece("\\left$open \\begin{matrix} $body \\end{matrix} \\right$close", rows = rows)
            }
            val inside = cells.joinToString(",")
            val tex = if (open == "." && close == ".") inside else "\\left$open $inside \\right$close"
            val strip = openName == "(" || openName == "[" || openName == "{" || openName == "{:"
            return Piece(tex, inner = if (strip) inside else null, rows = null, open = open, close = close, cells = cells)
        }

        private fun matching(open: String) = when (open) { "[" -> "]"; "{" -> "\\}"; "{:" -> "."; "(:", "<<", "langle" -> "\\rangle"; else -> ")" }

        /** Rows when every comma-separated item is a single bracketed group with the same number of cells. */
        private fun matrixRows(items: List<List<Piece>>): List<List<String>>? {
            if (items.isEmpty()) return null
            val rows = ArrayList<List<String>>()
            for (it in items) {
                val p = it.singleOrNull() ?: return null
                if (p.open !in listOf("(", "[") || p.inner == null) return null
                rows.add(splitCells(p))
            }
            if (rows.size < 2 && rows.firstOrNull()?.size?.let { it < 2 } != false) return null
            if (rows.map { it.size }.distinct().size != 1) return null
            return rows
        }

        private fun splitCells(p: Piece): List<String> = p.cells ?: listOf(p.stripped)

        fun barGroup(bar: String): Piece {
            // |x| as absolute value when a closing bar follows; a lone | is just a bar.
            val start = pos
            val inside = expression(closingBar = true)
            if (peekSym()?.kind == Kind.BAR) {
                pos++
                val tex = inside.joinToString("") { it.tex }
                return Piece("\\left$bar $tex \\right$bar")
            }
            pos = start
            return Piece(if (bar == "|") "\\mid " else "\\| ")
        }

        fun unary(tex: String): Piece {
            val arg = simple() ?: Piece("")
            val a = arg.stripped
            return Piece(when (tex) {
                "abs" -> "\\left| $a \\right|"
                "floor" -> "\\left\\lfloor $a \\right\\rfloor"
                "ceil" -> "\\left\\lceil $a \\right\\rceil"
                "norm" -> "\\left\\| $a \\right\\|"
                else -> "$tex{$a}"
            })
        }

        fun binary(tex: String): Piece {
            val a = simple() ?: Piece("")
            val b = simple() ?: Piece("")
            return Piece(when (tex) {
                "\\sqrt" -> "\\sqrt[${a.stripped}]{${b.stripped}}"
                "\\textcolor" -> "\\textcolor{${a.stripped.removePrefix("\\mathrm{").removeSuffix("}").filter { it.isLetterOrDigit() || it == '#' }}}{${b.stripped}}"
                else -> "$tex{${a.stripped}}{${b.stripped}}"
            })
        }

        /** text(...) keeps the words exactly as written. */
        fun textArg(): Piece {
            val t = peek()
            if (t is Tok.Symbol && t.sym.kind == Kind.LEFT) {
                // Rebuild the raw words between the brackets.
                pos++
                val sb = StringBuilder()
                var depth = 0
                while (pos < toks.size) {
                    val k = toks[pos]
                    if (k is Tok.Symbol && k.sym.kind == Kind.RIGHT && depth == 0) { pos++; break }
                    if (k is Tok.Symbol && k.sym.kind == Kind.LEFT) depth++
                    if (k is Tok.Symbol && k.sym.kind == Kind.RIGHT) depth--
                    if (sb.isNotEmpty() && k !is Tok.Symbol) sb.append(' ')
                    sb.append(when (k) { is Tok.Symbol -> k.name; is Tok.Number -> k.text; is Tok.Letter -> k.ch; is Tok.Quoted -> k.text; is Tok.Word -> k.text })
                    pos++
                }
                return Piece("\\text{${textEscape(sb.toString())}}")
            }
            val s = simple() ?: return Piece("")
            return Piece("\\text{${textEscape(s.stripped)}}")
        }
    }

    private fun escape(ch: String) = when (ch) {
        "#", "$", "%", "&" -> "\\$ch"
        "{" -> "\\{"; "}" -> "\\}"
        else -> ch
    }

    private fun textEscape(s: String) = s.replace("\\", "").replace("{", "").replace("}", "")
        .replace("$", "\\$").replace("%", "\\%").replace("&", "\\&").replace("#", "\\#")

    /**
     * The LaTeX for an AsciiMath formula. Never throws; anything it doesn't know is passed through as letters.
     */
    fun toLatex(asciiMath: String): String {
        val p = Parser(tokenize(asciiMath.trim()))
        val out = StringBuilder()
        while (p.pos < p.toks.size) {
            out.append(p.expression().joinToString("") { it.tex })
            // A stray closing bracket at the top level: show it and go on.
            if (p.pos < p.toks.size) {
                val t = p.toks[p.pos]
                if (t is Tok.Symbol && t.sym.kind == Kind.RIGHT && t.sym.tex != ".") out.append(t.sym.tex)
                p.pos++
            }
        }
        return out.toString().replace(Regex(" {2,}"), " ").trim()
    }
}
