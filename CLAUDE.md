# CLAUDE.md — latexComments developer notes

Read this file and `git log` first. The time budget for the current build session is in TorchSharpGraph's CLAUDE.md
(start 2026-11-23 20:10:26 UTC, hard stop 2026-11-24 03:10 UTC). Keep "Status" current; commit after each step.

## What this is
A JetBrains plugin (Kotlin) that renders LaTeX maths written in code comments (`$...$`, `$$...$$`, `\(...\)`,
`\[...\]`) as real formulas in the editor, with JLaTeXMath.

## How it works
- `scan/CommentScanner`: text-based comment finder per language family (`CommentSyntax.forFileName`), skipping
  strings (C# verbatim/raw/interpolated, Java/Kotlin text blocks, Python triple quotes, JS backticks, Rust lifetimes).
  Rider's frontend has no PSI comments for C# (the .NET backend parses C#), so a scanner is used for every language.
- `scan/MathFinder`: `$..$`, `$$..$$`, `\(..\)`, `\[..\]` with Markdown-maths rules (no "$5 and $10" false hits).
- `editor/RenderPlan`: comment-only lines -> `Lines` (a `CustomFoldRegion` drawing the comment text plus full-size
  formulas; display maths may span lines); maths after code -> `Inline` (empty-placeholder fold + inline inlay,
  formula shrunk to the line height). Pure, unit-tested.
- `editor/MathEditorController`: per editor; scans in the background after a 300 ms pause, keeps regions only for the
  visible lines (+ margin), never on caret lines (raw text for editing), re-keys regions after edits so only real
  changes redraw. Inlays are added/removed outside the folding batch (the platform forbids it inside).
- `render/Formula`: JLaTeXMath typesetting, LRU cache, errors -> "LaTeX error: unknown command \foo".
- Settings > Tools > LaTeX Comments; View > Render LaTeX in Comments (also editor right-click menu).
- Easy maths (0.2.0): `asciimath/AsciiMath` converts AsciiMath to LaTeX (then the same JLaTeXMath path, cache and
  error notes). `MathFinder.markedLine`: a comment line starting with "= " right after the marker (`//=`, `///=`, `#=`,
  `--=`, `%=`) or, in block/doc comments, after the decoration (` * = `); `/*=` makes every line of that block a
  formula. `MathGuess`: the optional automatic detector (off by default). `MathSpan.source`/`original` feed the
  hover readout (AsciiMath + LaTeX, plus a note for automatic finds).

## Decisions
- JLaTeXMath 1.0.7 (GPL-2.0+ with classpath exception, checked in the jar's META-INF/LICENSE) over KaTeX/MathJax in
  JCEF: draws vector shapes straight into the editor's Graphics (sharp at 200 %, no browser per formula, fast).
  Only the main jar ships (Greek/Cyrillic add-ons are plain GPL). Licence files copied from the jar into the zip;
  the build fails if one is missing.
- Name "LaTeX Comments" (LaTeX is not a company trademark); id `dev.latexcomments`. Fallback: "Maths in Comments".
- Existing plugins: none renders LaTeX inline in code comments (TeXiFy is for .tex files; JetBrains issues
  CPP-9210 / IDEA-213075 are open requests).
- Google's Maven Central mirror is listed first in the build: Maven Central rate-limits this build machine (429).
- Formula colour = the comment colour of the scheme; size = editor font size x 125 % (setting).

- `CustomFoldRegion` is marked experimental (the plugin checker lists 18 "experimental API" usages, all of it); it
  is what the platform itself uses for rendered doc comments, present since 2021.3. The stable alternative (block
  inlays above an emptied line) leaves a blank line under every formula, so the experimental API is used.
- Quick Documentation: the new doc viewer ignores `getLocalImageForElement`, so formulas are embedded as
  `data:image/png` pictures drawn at 2x (sharp on scaled screens), with the LaTeX as `alt` text.

## Easy maths decisions (24 Nov)
- Own AsciiMath converter, written from the published grammar (S/I/E) and symbol list on asciimath.org. The only
  JVM port found (ASCIIMathTeXImg for Java) is GPL-3.0 without a linking exception, so not usable in an MIT plugin;
  its code was not read. Tested against the asciimath.org examples (all render, picture looked at).
- Deliberate differences from AsciiMath: unknown letter runs of 3+ letters show upright as names (`softmax`, `MSE`);
  a known name must be the whole run or the run plus one letter (`dotx`, `ulA`), so `index` is not "in dex".
  `cancel` is drawn with `\not` (JLaTeXMath has no `\cancel`); `argmin` uses `\mathop{\mathrm{argmin}}`.
- Marker needs a space after "=" (so `//=====` separators are left alone). A marked line containing `$` stays LaTeX,
  so the `$...$` behaviour is exactly as before. Julia's `#=` is its block comment, so no marker there.
- Marked lines use display style; AsciiMath after code uses inline style so it isn't shrunk too much.
- Automatic mode: off by default; any code clue (operators, calls, `snake_case`/`camelCase`, paths, links,
  TODO/Note:, tags, list items) or two ordinary words rejects a line; needs strong maths signs (Greek names,
  sum/sqrt/int/lim, powers of numbers or brackets) plus a relation, or several signs. A found formula that wouldn't
  render is skipped. Weak cases (`y = W x + b`, `a_(n+1) = a_n + d`) are missed on purpose (tested).
- Quick Documentation only renders `$...$` LaTeX, not easy-maths lines (they need the comment's line structure).

## Status (24 Nov, 0.2.0)
Easy maths done and checked: 70+ new tests (converter incl. asciimath.org examples, markers in every comment style,
separators, LaTeX unchanged, 60+ real non-maths comments left alone, 11 formulas found, headless editor test);
IDE screenshots with the built 0.2.0 zip: marked lines, `/*=` block, after-code formula, hover readout, settings
page, automatic mode on (`docs/screenshots/easy-maths-*`, `settings-easy-maths-*`). Plugin checker on the 0.2.0
build: Compatible with Rider 2026.1/2026.2/2026.3 (same 18 `CustomFoldRegion` experimental-API notes, nothing new).
Not checked in real Rider: easy maths in Rider's C# editor (same IDE-side editor, so it should match IDEA).

## Status (final for the first session, 22:00 UTC, 23 Nov)
Every point of "What working means" is met and checked (tests + screenshots looked at, `docs/screenshots/`):
- `$..$` and `$$..$$` (also `\(..\)`, `\[..\]`) in `//`, `/* */`, `///` in C#; Java, Kotlin, Python checked too;
  20+ languages in the scanner tests.
- Rendered in place; caret lines show raw text; moving away re-renders (IDE + headless tests).
- Hover preview (IDE, dark). Quick Documentation formulas for Java doc comments (IDE, dark).
- View > Render LaTeX in Comments toggle (IDE: off and on), settings page (size %, trailing comments, hover).
  Formulas follow editor font size changes.
- Bad LaTeX: short plain note ("LaTeX error: unknown command \sqroot"), never an exception (tests + IDE).
- Big files: scan of 50,000 lines in 0.3 s; 40,000-line file renders only near-screen lines; typing < 20 ms/key.
- Sharp at 200 % (light theme screenshot); light and dark colours follow the scheme's comment colour.
- Built zip installed in a clean IDE profile works. Plugin checker: Compatible with Rider 2026.1/2026.2/2026.3.
- CI green (tests + zip + licence-file check); signed build + checker on manual dispatch.
- 26 tests (scanner 8, finder 3, render 6, docs 1, editor 6... see `./gradlew test`).

Not checked (needs real Rider): Quick Documentation for C# (Rider's C# docs come from its .NET backend; the provider
is registered for language "C#" in case Rider asks the IDE side, unverified); look of rendered lines in Rider's
C# editor (the IDE-side editor is the same component, so it should match IDEA).

Added in the quality round: `\\argmin`, `\\argmax`, `\\bm`, `\\coloneqq`, `\\color` defined (33 textbook formulas
render without errors, tested); maths inside `<c>`, `<code>`, `<pre>` is left as code; final plugin checker run on
the last build: Compatible with Rider 2026.1/2026.2/2026.3 (18 experimental-API usages, all `CustomFoldRegion`).

Ideas for later: Markdown files; reader mode integration; check Quick Documentation for C# in real Rider.
