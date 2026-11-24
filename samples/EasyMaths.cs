namespace Samples;

// Easy maths: no LaTeX needed. Start a comment with //= and write the formula as you would type it.
public static class EasyMaths
{
    //= L = sum_i (phi_0 + phi_1 x_i - y_i)^2
    public static double Loss(double[] x, double[] y, double phi0, double phi1) => 0;

    //= sigma = sqrt(1/N sum_(i=1)^N (x_i - mu)^2)
    public static double StdDev(double[] x) => 0;

    ///= hat y = 1/(1 + e^(-z))
    public static double Sigmoid(double z) => 1 / (1 + System.Math.Exp(-z)); //= (d sigma)/(dz) = sigma (1 - sigma)

    /*= theta := theta - eta grad_theta L
        p(x) = 1/(sigma sqrt(2 pi)) e^(-(x - mu)^2 / (2 sigma^2)) */

    // With "Also show other comment lines as maths" turned on, this line is shown as a formula:
    // x^2 + y^2 = r^2
    // ...and these are left alone, because they look like code or notes:
    // flags ^ mask, max_value, i++, TODO: fix x_1, see https://example.com/a_b
}
