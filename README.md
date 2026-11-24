# LaTeX Comments

A JetBrains IDE plugin that shows maths in code comments as real formulas, right in the editor.
Write it in LaTeX, or the easy way without LaTeX, and read it the way a book prints it.

![C# comments with rendered formulas](docs/screenshot.png)

## What it does

- `$...$` and `\(...\)` inline, `$$...$$` and `\[...\]` display maths, in line comments, block comments and
  documentation comments (`///`, `/** */`, Python docstrings).
- The line under the caret shows the raw LaTeX, so editing is as easy as before.
- Hover over a formula for a larger preview. In Java and Kotlin, formulas also show in Quick Documentation.
- Works in C#, Java, Kotlin, C and C++, Python, JavaScript and TypeScript, Go, Rust, F#, Scala, Swift, R, Julia,
  Lua, SQL, MATLAB and more.
- A mistake in the LaTeX shows as a short note, never an error dialog. Prices like "$5 and $10" are left alone.
- Sharp on scaled screens, in the comment colour of your light or dark theme.

## Requirements

Rider 2026.1 or newer, or any other JetBrains IDE 2026.1 or newer.

## Install

Settings → Plugins → ⚙ → Install Plugin from Disk… → pick `latexcomments-<version>.zip` (from the CI build, or
`./gradlew buildPlugin` → `build/distributions/`).

## Use

LaTeX, between dollar signs:

```csharp
// The loss: $L = \sum_i (\phi_0 + \phi_1 x_i - y_i)^2$
```

Easy maths, no LaTeX needed: start the comment with `=` and type the formula
([AsciiMath](https://asciimath.org): `x^2`, `x_i`, `sqrt(x)`, `a/b`, `sum_(i=1)^n`, Greek letters by name):

```csharp
//= L = sum_i (phi_0 + phi_1 x_i - y_i)^2
```

| Comment | Marker |
|---|---|
| Line comment | `//= ...`, `#= ...` (Python, R, shell), `--= ...` (SQL, Lua), `%= ...` (MATLAB) |
| Doc comment line | `///= ...` |
| Block comment | `/*= ... */` makes every line of the block a formula |
| Line inside a `/** */` doc comment | ` * = ...` |

Optional: Settings → Tools → LaTeX Comments → "Also show other comment lines as maths when they clearly are"
(off by default) shows unmarked lines such as `// x^2 + y^2 = r^2`, and leaves anything that looks like code, a
name or a note alone. Hover over a formula to see how it was read.

Turn it on or off with View → Render LaTeX in Comments (also in the editor's right-click menu). Formula size and
other options: Settings → Tools → LaTeX Comments. More: [user guide](docs/USER-GUIDE.md).

## Credits

MIT licence (see [LICENSE](LICENSE)). Formulas are drawn by [JLaTeXMath](https://github.com/opencollab/jlatexmath)
(GPL 2 with the classpath exception) with its Computer Modern and AMS fonts. Details:
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
