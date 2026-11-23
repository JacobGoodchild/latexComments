package samples

/**
 * Newton's method: $x_{n+1} = x_n - \frac{f(x_n)}{f'(x_n)}$.
 */
fun newton(f: (Double) -> Double, df: (Double) -> Double, x0: Double): Double {
    var x = x0
    // Stops when $|x_{n+1} - x_n| < 10^{-12}$
    repeat(50) { x -= f(x) / df(x) }
    return x
}
