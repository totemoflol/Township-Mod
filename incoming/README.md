# incoming/

Drop-in folder for analysis targets.

Place a tool artifact here for offline analysis — e.g. the ~11 MB
TwnTool APK. This folder is tracked by git (there is a folder-specific
exception in `.gitignore`), so committing a new file in here makes it
appear on GitHub.

## Conventions

- Analysis targets only. No secrets, no account credentials, no
  personal data.
- The large game workspace binaries (`extracted/`, `mod-base/`,
  `original/`, ~1.2 GB) stay ignored and out of git; `incoming/` is
  for small drop-in files.

When a tool APK lands here, run the offline pipeline described in
`docs/TOWNTOOL-APK-ANALYSIS.md` (§A.0) and
`docs/TOWNSHIP-SAVE-FILE-ANALYSIS.md` (§B.2).
