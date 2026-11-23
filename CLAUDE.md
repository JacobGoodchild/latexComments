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

## Status
Working (tests + screenshots): scanner (20+ languages), finder, formula rendering + errors, editor rendering in
IDEA 2026.1 for C#, Java (doc comments, line, block, trailing), caret line raw, re-render on leaving, settings toggle.
20 tests pass (incl. 4 headless editor tests).

Next: hover preview check, Quick Documentation (Java/Kotlin, try C#), light + 200 % screenshots, big-file typing
check, icon, README/docs, CI, built-zip test, plugin verifier against Rider 2026.1/2/3.
