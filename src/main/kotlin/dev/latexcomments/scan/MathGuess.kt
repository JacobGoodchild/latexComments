package dev.latexcomments.scan

/**
 * Decides whether an unmarked comment line is clearly a formula (only used when automatic reading is turned on).
 * It is deliberately cautious: comments are full of things that look like maths but aren't (C#'s a^b is XOR,
 * max_value is a name, i++, paths, links, TODO notes), and showing a formula by mistake is worse than missing one.
 *
 * A line is read as maths only when it has no sign of code or prose, and strong signs of maths: Greek letter
 * names, sum/sqrt/int/lim, powers of numbers or brackets (x^2, (a+b)^2), single-letter subscripts (x_i, phi_0),
 * with a relation (=, <=, ~~ ...) or several of those signs together.
 */
object MathGuess {
    private val greek = setOf("alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta",
        "vartheta", "iota", "kappa", "lambda", "mu", "nu", "xi", "pi", "rho", "sigma", "tau", "upsilon", "phi", "varphi",
        "chi", "psi", "omega", "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Phi", "Psi", "Omega")
    private val mathWords = greek + setOf("sum", "prod", "int", "oint", "sqrt", "root", "lim", "sin", "cos", "tan",
        "log", "ln", "exp", "max", "min", "argmin", "argmax", "det", "frac", "grad", "del", "oo", "inf", "sup",
        "abs", "norm", "hat", "bar", "vec", "dot", "text", "in", "and", "or", "if", "for", "RR", "NN", "ZZ", "QQ", "CC")
    private val strongWords = greek + setOf("sum", "prod", "int", "sqrt", "root", "lim", "argmin", "argmax", "grad")

    /** Signs of code, notes or links: any one of them and the line is left alone. */
    private val codeClues = listOf(
        Regex("\\+\\+|--|==|!==|&&|\\|\\||;|::|=>|\\?\\?|\\?\\.|\\$\\{"),      // operators of code, not maths
        Regex("[{}#@\"'`\\\\]"),                                              // braces, strings, escapes, attributes
        Regex("\\w\\.\\w+\\s*\\("),                                           // method calls: a.b(...)
        Regex("\\b[A-Za-z]\\w*\\s*\\(\\s*\\)"),                               // empty calls: f()
        Regex("\\b[A-Za-z]{2,}_[A-Za-z]{2,}"),                                // snake_case names: max_value
        Regex("_[A-Za-z0-9]*_"),                                              // several underscores
        Regex("[a-z][A-Z]"),                                                  // camelCase names
        Regex("\\w+://|www\\.|\\.(com|org|net|io|md|cs|kt|java|py|txt|json|xml|dll|exe)\\b", RegexOption.IGNORE_CASE),
        Regex("(^|\\s)(/|\\./|\\.\\./|~/)?[\\w.-]+/[\\w.-]+/"),                // paths a/b/c
        Regex("\\b(TODO|FIXME|HACK|XXX|NOTE|BUG|WARNING|DEPRECATED|SEE|NB)\\b", RegexOption.IGNORE_CASE),
        Regex("^\\s*(return|var|val|let|const|int|long|double|float|bool|string|char|new|public|private|static|if|else|for|while|foreach|using|import|class|def)\\b.*[=;(]"),
        Regex("<[A-Za-z/!]"),                                                 // tags and generics: <summary>, List<int>
        Regex("\\[\\s*\\]"),                                                  // arrays: int[]
        Regex("\\w\\s*\\[\\s*\\w+\\s*]\\s*="),                                // indexing assignment: a[i] = ...
        Regex("^[-*]\\s"),                                                    // list items
        Regex(":\\s*$|^\\s*\\w+:\\s"),                                        // "Note:", "Example: ..."
    )

    private val power = Regex("(\\w|\\))\\^(\\d|\\(|-\\d)|\\)\\^\\w")         // x^2, (a+b)^2, x^-1, (..)^n
    private val subscript = Regex("(^|[^A-Za-z])([A-Za-z]|${greek.joinToString("|")})_(\\w|\\()")  // x_i, phi_0, a_(n+1)
    private val relation = Regex("(^|[^=<>!])(=|<=|>=|!=|~~|-=|~=|:=)(?![=>])|\\b(in|sub|sube|prop)\\b")

    fun looksLikeMaths(line: String): Boolean {
        val s = line.trim()
        if (s.length < 3 || s.length > 160) return false
        if (codeClues.any { it.containsMatchIn(s) }) return false
        // Prose: two or more ordinary words of three letters or more ("returns the sum of x").
        val words = Regex("[A-Za-z]{3,}").findAll(s).map { it.value }.filter { it !in mathWords }.toList()
        if (words.size >= 2) return false
        // A lone long word next to an equals sign is usually a variable name ("result = x^2" is fine, but
        // "counter = total^2" with two long words was already rejected above).
        var score = 0
        val tokens = Regex("[A-Za-z]+").findAll(s).map { it.value }.toList()
        if (tokens.any { it in strongWords }) score += 2
        if (power.containsMatchIn(s)) score += 2
        if (subscript.containsMatchIn(s)) score += 1
        if (Regex("(sqrt|root|abs|norm)\\s*\\(").containsMatchIn(s)) score += 1
        val hasRelation = relation.containsMatchIn(s)
        if (hasRelation) score += 1
        // Needs strong evidence, and either a relation or at least two different signs of maths.
        return score >= 3 && (hasRelation || score >= 4)
    }
}
