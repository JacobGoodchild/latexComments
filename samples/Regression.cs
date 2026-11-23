using System;

namespace Samples;

/// <summary>
/// Least-squares line fit. The loss is
/// $$L(\phi) = \sum_{i=1}^{I} (\phi_0 + \phi_1 x_i - y_i)^2$$
/// and its minimum has $\frac{\partial L}{\partial \phi} = 0$.
/// </summary>
public static class Regression
{
    // $L = \sum_i (\phi_0 + \phi_1 x_i - y_i)^2$
    public static double Loss(double[] x, double[] y, double phi0, double phi1)
    {
        double total = 0; // running sum of $r_i^2$
        for (int i = 0; i < x.Length; i++)
        {
            double r = phi0 + phi1 * x[i] - y[i]; // residual $r_i = \hat{y}_i - y_i$
            total += r * r;
        }
        return total;
    }

    /* The gradient, written out:
     *   $$\nabla L = 2 \sum_i r_i \begin{pmatrix} 1 \\ x_i \end{pmatrix}$$
     */
    public static (double, double) Gradient(double[] x, double[] y, double phi0, double phi1) => (0, 0);

    // Softmax: $\sigma(z)_j = \frac{e^{z_j}}{\sum_k e^{z_k}}$ for $j = 1, \dots, K$.
    // A typo shows a short note: $\frac{1}{\sqroot{2}}$
    // Prices like $5 and $10 stay as they are.
    public const string Url = "http://example.com/$notmaths$";
}
