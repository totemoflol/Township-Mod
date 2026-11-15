# Analysis environment

The Windows Server 2025 workspace is `C:\Township-Mod`.

## Installed tools
- Eclipse Temurin JDK 21.0.13+9 (portable, under `tools/jdk`)
- JADX 1.5.6 (GUI/runtime bundle; CLI jar at `tools/jadx/lib/jadx-gui-1.5.6-all.jar`)
- Apktool 3.0.3 (`tools/apktool.jar`)
- Android Platform Tools / ADB 37.0.1
- 7-Zip standalone 26.04 (`tools/7zr.exe`)

All five tools were launched or version-checked successfully on the VPS. Tool archives and APKs are intentionally excluded from this public repository.

## Local helper scripts
- `scripts/decode-apk.ps1 -Apk <path> [-OutDir <path>]`
- `scripts/decompile-apk.ps1 -Apk <path> [-OutDir <path>]`

## Next steps
1. Transfer the original APK bundle and MOD APK to the VPS without publishing them.
2. Record SHA-256 hashes and archive metadata.
3. Compare package manifests, resources, DEX files, and signing certificates.
4. Document findings before making any local UI/QOL patch.

No APK comparison has been performed yet; the input APK files have not been confirmed on the VPS.
