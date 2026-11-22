# Research

Workspace for Township save-format investigation, APK analysis, and test samples.

## Current save samples

The uploaded ZIPs and extracted-payload-named files are stored here:

- `mGameInfo.txt.zip`
- `LocalInfo.txt.zip`
- `mGameInfo.txt.xml`
- `LocalInfo.txt.xml`

The original ZIPs are also mirrored in `incoming/`.

## Findings

See [Township save-file analysis](../docs/TOWNSHIP-SAVE-FILE-ANALYSIS.md) for the byte-level measurements, ZIP entry census, CRC validation, hashes, and conservative format probes.

**Current result:** both ZIPs are valid DEFLATE archives. Their inner files have `.xml` names but contain high-entropy binary payloads, not readable XML. No known compression/container signature or parseable game-state fields were identified. The exact serialization/encryption scheme remains unknown, so these samples have **not** been decoded.

See [TwnTool APK analysis](../docs/TOWNTOOL-APK-ANALYSIS.md) for evidence of the app's remote decode/encode API paths and its separate local XML transformation layer. The APK analysis identifies route names, not the private server's request schema or a usable decoder.

## Reproducible inspection

Read-only triage script: [`scripts/savefile_triage.py`](../scripts/savefile_triage.py).

## Privacy

These samples may contain account- or town-specific data. Keep this repository private. Do not publish raw saves or credentials; use redacted test copies for collaboration.
