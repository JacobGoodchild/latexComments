# Publishing (maintainer only)

Nothing is published automatically. GitHub → Actions → CI → Run workflow → tick "verify" builds the signed zip and
runs JetBrains' plugin checker against Rider 2026.1, 2026.2 and 2026.3. It needs these repository secrets
(Settings → Secrets and variables → Actions), never stored in the repository: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
`PRIVATE_KEY_PASSWORD`. Upload the `latexcomments-marketplace` artifact by hand on the first release.

The plugin depends only on the shared IntelliJ Platform, so the Marketplace offers it for every JetBrains IDE.
Name: "LaTeX Comments". If the Marketplace asks for another name, use "Maths in Comments".
