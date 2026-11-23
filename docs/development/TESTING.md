# Testing

- `./gradlew test`: scanner (many languages, strings, edge cases, 50,000-line speed), maths finder (Markdown-maths
  rules), formula rendering (baseline, 2x sharpness, errors), documentation pages, and headless editor tests
  (rendered regions, caret line raw, typing, toggle, Java/Python, 40,000-line file).
  `LATEXCOMMENTS_SHOT_DIR=/dir ./gradlew test` also saves the rendered formulas as pictures.
- UI checks run in the free IntelliJ IDEA on a virtual screen (the sandbox Rider needs a login):
  `OPENFILE=/path/file tools/run-idea.sh <folder> <log>`, `tools/theme.sh light|dark` before starting, `UISCALE=2` for
  a 200 % screen, `tools/shot.sh out.png [scale%] [crop]`. `tools/run-zip.sh <folder>` installs the built zip into a
  fresh IDE profile instead of the development sandbox. These scripts are for a throwaway test machine.
- `samples/` holds C#, Java, Kotlin and Python files with maths in every kind of comment.
