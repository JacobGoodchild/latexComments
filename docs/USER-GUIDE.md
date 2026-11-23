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
