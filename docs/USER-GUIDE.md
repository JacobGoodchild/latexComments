# User guide

## Writing maths

| You write | Shows as |
|---|---|
| `$x^2$` or `\(x^2\)` | inline maths, in the line of text |
| `$$\frac{a}{b}$$` or `\[\frac{a}{b}\]` | display maths (larger); may run over several comment lines |
| `\$` | a dollar sign |
| `` `$x$` `` | code, left as it is |

A `$` only opens maths when the next character isn't a space, and only closes it when the character before isn't a
space and the next one isn't a digit, so "costs $5 and $10" and "$HOME" stay as text.

## Easy maths (no LaTeX)

Start a comment with `=` and type the formula the way you would in a calculator or a chat. The notation is
AsciiMath (asciimath.org):

| You type | Means |
|---|---|
| `x^2`, `x_i`, `x_i^2`, `phi_0` | powers, subscripts (use brackets for more than one character: `x_(i+1)`, `e^(-x)`) |
| `a/b`, `(a+b)/2` | fractions (brackets around the top or bottom are dropped) |
| `sqrt(x)`, `root(3)(x)`, `abs(x)`, `norm(x)` | roots, absolute value, norm |
| `sum_(i=1)^n`, `prod_i`, `int_0^1 f(x) dx`, `lim_(n->oo)` | sums, products, integrals, limits |
| `alpha`, `phi`, `Sigma`, `oo`, `RR`, `in`, `<=`, `!=`, `~~`, `->`, `xx`, `grad` | Greek letters and symbols |
| `hat y`, `bar x`, `vec v`, `bb(x)` | accents and bold |
| `[[a,b],[c,d]]`, `((1,0),(0,1))` | matrices |
| `"text"`, `text(if)` | words |

Words of three letters or more that aren't known names (`softmax`, `MSE`) are shown upright, as names.

Where to put the `=`:

- `//= formula`, `#= formula`, `--= formula`, `%= formula`: the line is a formula.
- `///= formula`: a documentation comment line that is a formula.
- `/*= ... */`: every line of the block is a formula.
- ` * = formula`: one formula line inside a `/** */` documentation comment.
- A space must follow the `=`, so separator lines like `//=====` are left alone. A line with `$` in it is LaTeX.
- Julia's `#= ... =#` is a block comment, so the `#=` marker doesn't apply in Julia files.

**Automatic mode** (Settings → Tools → LaTeX Comments, off by default) also shows comment lines that clearly are
maths without a marker, such as `// x^2 + y^2 = r^2` or `// sigma = sqrt(v)`. It is cautious on purpose: lines with
signs of code (`++`, `==`, `;`, calls, `snake_case` or `camelCase` names), paths, links, notes (`TODO`, `Note:`), or
ordinary words are left alone, and so are weak cases like `y = W x + b`. Hovering over any easy-maths formula shows
the AsciiMath and the LaTeX it became.

## In the editor

- Comment lines that contain maths and no code are drawn with the formulas at full size; the line grows as tall as
  the formula needs.
- Maths in a comment after code on the same line is shrunk to fit the line (Settings can turn this off).
- Put the caret on a line (click it, or use the arrow keys) to see and edit the raw text. Moving away draws it again.
- Hover over a formula for a larger preview.
- Quick Documentation (Ctrl+Q / F1) shows formulas in Java and Kotlin documentation comments.

## Settings

Settings → Tools → LaTeX Comments:

- Show LaTeX in comments as formulas (also View → Render LaTeX in Comments, and the editor's right-click menu).
- Formula size, as a percentage of the editor font (default 125 %).
- Also in comments after code on the same line.
- Show a larger preview when the mouse is over a formula.

## How comments are found

The plugin reads the text of the file and knows each language's comment and string syntax, so `//` inside a string
isn't taken for a comment. This works the same in every IDE, including for C# in Rider, where the code is
understood by Rider's .NET backend rather than by the IDE side.

## Privacy

The plugin makes no network connections and writes no files.
