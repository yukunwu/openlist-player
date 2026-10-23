# OpenList TV

- Standalone TV-only Android client; do not add a phone launcher/layout without user request.
- Plain Java, minSdk23, Media3 1.3.1, ZXing 3.5.3, AGP8.5.2/JDK17, compile/target35.
- Build: `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease`.
- Keep keys, real passwords, tokens, signed media URLs, raw logs and device identifiers out of source/docs.
  `work/`, `local.properties`, Gradle caches and build outputs are ignored.
- Never auto-attach OpenList authorization to media origins. Preserve strict TLS verification, redirect
  credential stripping, no-backup session storage and transient pairing-server lifecycle.
- Pairing serves local HTTP; do not claim it prevents active MITM. Preserve the trusted-LAN disclosure.
- Do not install to a TV, switch Home, uninstall apps or clear data without the user's applicable authorization.
- Review/verify changes without subagents unless the user explicitly requests delegation.
