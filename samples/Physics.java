package samples;

/**
 * Simple harmonic motion.
 * <p>The position is $x(t) = A \cos(\omega t + \varphi)$ with $\omega = \sqrt{k/m}$.</p>
 * $$E = \tfrac{1}{2} m v^2 + \tfrac{1}{2} k x^2$$
 */
public class Physics {
    /** Period $T = 2\pi \sqrt{m/k}$. */
    static double period(double m, double k) {
        return 2 * Math.PI * Math.sqrt(m / k); // $T = 2\pi/\omega$
    }
}
