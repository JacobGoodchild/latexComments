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

- `CustomFoldRegion` is marked experimental (the plugin checker lists 16 "experimental API" usages, all of it); it
  is what the platform itself uses for rendered doc comments, present since 2021.3. The stable alternative (block
  inlays above an emptied line) leaves a blank line under every formula, so the experimental API is used.
- Quick Documentation: the new doc viewer ignores `getLocalImageForElement`, so formulas are embedded as
  `data:image/png` pictures drawn at 2x (sharp on scaled screens), with the LaTeX as `alt` text.

## Status (21:50 UTC, 23 Nov)
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

Ideas for later: more LaTeX coverage checks against textbook formulas; Markdown files; reader mode integration.
