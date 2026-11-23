# ToT Tools — Android

Native Android app (Kotlin + Jetpack Compose), application ID `com.totemoflol.tottools`.

- App name: ToT Tools
- Minimum Android: Android 10 (API 29)
- Initial release: 1.0.0
- Import: ZIP exports containing one XML entry, encoded 0x79 XML files, and already-decoded XML files
- Decoder: MurmurHash2-derived 727-byte table, differential XOR transform, exact inverse round-trip verification, zero-padding validation, and LZ4 raw-block decompression
- Output: XML is parsed and can be exported as a separate decoded copy
- Privacy: offline processing; no network permission; source files are never overwritten

This is a read-only save inspection utility. It does not modify game balances/progression or connect to Township servers.

## Build

The GitHub Actions workflow **Build ToT Tools APK** builds a debug APK. Download the `ToT-Tools-Android-APK` artifact from the completed workflow run.
