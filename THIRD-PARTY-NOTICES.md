# Third-party notices

latexComments is MIT-licensed (see `LICENSE`). The plugin ships one third-party library, unmodified:

| Component | Used for | Licence | Copyright |
|---|---|---|---|
| JLaTeXMath 1.0.7 (`org.scilab.forge:jlatexmath`, main jar only) | Drawing the maths | GPL-2.0-or-later with the classpath (linking) exception | © 2009-2011 DENIZET Calixte; © Kris Coolsaet, Nico Van Cleemput, Kurt Vermeulen; © 2004-2007 Universiteit Gent |
| Fonts inside JLaTeXMath | Maths glyphs | Computer Modern: Knuth licence; eufb10, eufm10, msam10, msbm10: SIL Open Font License 1.1 (© American Mathematical Society); dsrom10: free licence; stmary10, rsfs10: public domain | As stated in each licence file |

The classpath exception lets JLaTeXMath be combined with independent modules (this plugin's own code) under their
own licence terms. JLaTeXMath's full licence (`LICENSE`, the GPL text `COPYING`) and the font licences are in the
plugin's `licenses/JLaTeXMath` folder. Its source code is at https://github.com/opencollab/jlatexmath (tag 1.0.7) and in
the `jlatexmath-1.0.7-sources.jar` on Maven Central. The optional Greek and Cyrillic font packages of JLaTeXMath
(plain GPL) are not included.

## AsciiMath

The easy-maths notation is AsciiMath (https://asciimath.org). The converter from AsciiMath to LaTeX is this plugin's
own code (MIT), written from the published syntax; no AsciiMath implementation is copied or shipped. (The one JVM
port found, ASCIIMathTeXImg for Java, is GPL-3.0 without a linking exception, so it was not used.)
