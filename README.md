# OpenList TV

Android and storage support: [Android / OpenList compatibility matrix](docs/COMPATIBILITY.md).
Pre-publication findings: [security, compatibility and UI review](docs/REVIEW-2026-10-23.md).

An independent, TV-focused OpenList file browser and player. Landscape layout, D-pad focus navigation,
Android TV and standard Android launcher entries, Android 6.0 / API 23 minimum. The standard launcher icon
allows phone testing; it opens the same TV interface, not a phone-specific layout.
Not affiliated with OpenList or any cloud provider.

## Features

- Configurable OpenList server, password/optional OTP login or guest browsing.
- TV manual entry or a phone-scanned one-time LAN configuration form, with explicit TV-side confirmation.
- Paginated directory browsing and direct `raw_url` resolution using the OpenList API.
- Media3 player with device-native decoders, playback controls, audio/subtitle selection via player UI.
- Tap a video to play internally; long-press its file row (or hold remote OK) for an anchored internal/external
  player menu. External playback is available only for sources needing no additional HTTP headers.
- Player header contains media metadata only; picture modes: original aspect, 4:3, 16:9 and stretch to fill.
- Picture ratio and playback speed are compact bottom-right actions with anchored selection menus.
- Phones use a left-side vertical swipe on empty video for playback-window brightness,
  or a right-side vertical drag for media volume. Controls may remain visible when the gesture starts;
  buttons and the timeline are excluded. No long hold is required. A swipe is consumed from down to up;
  only a stationary tap toggles playback controls. TV-mode devices do not enable these touch adaptations.
- The bottom-left lock action is icon-only: white open padlock while unlocked, red closed padlock while locked.
  The lock hit area is separate from the central left-side brightness region.
- Touch lock disables player controls and gestures. Its icon hides after 2.5 seconds; touching the video
  or pressing Back briefly reveals only the lock icon. A fresh tap on the visible icon unlocks playback;
  tapping its old position while hidden only reveals it, and holding the visible icon keeps it available.
- No embedded account, telemetry, advertising, cloud-drive credentials or NAS-side transcoding.
- Passwords are not saved. Session tokens are encrypted using an Android Keystore AES-GCM key, bound to
  the server address; Android backup is disabled. Logout removes the saved token. Login fields always
  start empty; no server or account is bundled. Enter your own address or explicitly import it by QR.
  Existing authenticated sessions still resume automatically. With no configured server, the app stays
  offline until you configure one. Prefer a dedicated read-only media account, not an administrator.

## Playback limits

The **比例** control remembers the selected picture mode. Original aspect fits the source without distortion;
4:3 and 16:9 reshape it into that aspect ratio (letterboxed as needed), while **拉伸铺满** fills the whole
screen and may distort the image. These controls do not alter video files or request a new stream.
Brightness gestures affect only the playback window, not the global brightness setting or a TV's physical backlight.
It resets to system brightness on a fresh player launch; activity recreation preserves the current value.
Media volume changes the device's music stream. `MODIFY_AUDIO_SETTINGS` permits that operation; it does
not grant microphone access. TV remote OK toggles playback and Left/Right seek by ten seconds;
Up/Down enters the controller so arrows and OK can navigate menus normally, and Back closes controls.
Back follows the same order for a remote key and Android gesture navigation: dismiss the open options
menu, hide the controller, then exit to the file list. While touch-locked it only reveals the lock icon.
Controller transitions are immediate so the key-navigation state cannot lag behind an exit animation;
remote menu Up/Down wraps within the popup and Left/Right cannot seek the underlying video.

4K support depends on the TV decoder, codec/profile/bit-depth/frame-rate and available bandwidth.
No promise of AV1, Dolby Vision, HDR output or lossless audio passthrough on old TVs. Media3 uses system
decoders; decoder fallback is enabled, but unsupported formats may fail. This is not a full Kodi replacement.
No scraping/poster database, directory-password support, external subtitle search or persistent watch history yet.

`raw_url` usually goes directly to cloud storage, but OpenList storage strategy may return a proxy address.
This app displays the initial media hostname; redirects can change the destination. It does not prove the
absence of a server-side proxy. HLS/DASH media manifests and segments must be valid HTTP(S) resources.
Media HTTP redirects strip all supplied headers across origins and reject HTTPS-to-HTTP downgrade. OpenList
session tokens are only sent to its own API origin, never automatically attached to cloud links.
The external player receives a temporary signed playback URL; only select an application you trust.

HTTP is allowed for explicitly configured servers and media links (not restricted to LAN); it is unencrypted. HTTPS certificate
validation is not bypassed. A rooted device, malicious keyboard or compromised OS is outside this app's boundary.

## File browser

Visual direction and offline interaction prototypes live in `design/` (see `design/UI-SPEC.md`).
Generated-image availability is tracked separately in `design/generated/README.md`; a prototype is not an
AI-generated image or a claim of on-device validation.

- Compact 84dp navigation rail with labeled vector icons; server login and logout live in **服务器**.
- Neutral charcoal background, translucent black/gray layers, white remote-focus outlines and restrained
  red playback accents; separate filename/type/size fields. No live blur or transparent app window.
- Current-directory heading, abbreviated breadcrumb, page count, empty state and focused long-name marquee.
- Built for landscape TV navigation; no poster scraping, synthetic thumbnails or phone-specific layout.

## Configure from your phone

On the TV, open **服务器 → 连接服务器 / 登录 → 手机扫码填写配置**. Connect your phone and TV to the same trusted LAN,
scan the displayed QR code into a browser, and enter your OpenList URL, username, password and optional OTP.
Verify the pairing identifier matches the TV. Send, then approve the displayed server/account on the TV;
the manual login form is filled, and you press **登录**. No phone companion app is required.

- The TV starts a temporary HTTP receiver bound to one private IPv4 interface on a random port. It is
  only present while the pairing screen is active, for at most 3 minutes and one accepted submission.
- The QR encodes the local URL and a random 256-bit AES-GCM key in the URL fragment, not account data.
  The browser removes the fragment from its current history entry and uses it to encrypt the submitted
  payload with a fresh nonce. Session identity is authenticated as AAD. The server rejects wrong origins,
  host headers, oversized requests and unauthenticated payloads. No third-party scripts or relay service.
- This is **not authenticated HTTPS**: the form and bundled crypto JavaScript are loaded over local HTTP.
  Payload encryption protects against passive observation, not an active LAN adversary replacing the form.
  Use manual TV entry on untrusted/public networks. Anyone who sees the QR has its temporary capability;
  do not photograph/share it. Screenshots are disabled on the Android pairing and login windows.
- Guest Wi-Fi isolation, VPN routes, IPv6-only networks or browser local-network restrictions can prevent
  pairing. Reconnect on a shared private IPv4 LAN or use manual entry. No firewall/router change is performed.
- Passwords/OTP remain in memory only, are excluded from view state and are not saved to preferences.
  They are sent to the chosen OpenList server only after you press Login; use HTTPS for that server.

An AES-only bundle derived from `node-forge` 1.4.0 is used because `crypto.subtle` is not generally available
on LAN HTTP. RSA/ASN.1/PKI are excluded; random IVs use browser `crypto.getRandomValues`. Rebuild instructions
and the upstream advisory boundary are in `scripts/crypto/README.md`. Its upstream license is retained at `app/src/main/assets/forge-LICENSE.txt`.
Android QR encoding uses ZXing core 3.5.3. Player uses AndroidX Media3 1.3.1. Dependency versions are pinned.
Playback requests media audio focus and pauses on audio-becoming-noisy events (such as unplugging headphones).

## Build

Use JDK 17, Android SDK platform 35 and an SDK path in the ignored `local.properties`:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. The debug signer is for local testing only.
### Signed release

Release builds use the private properties file at `~/.config/openlist-tv/signing.properties`, or the path
selected by `OPENLIST_SIGNING_PROPERTIES`. It contains `storeFile` (absolute keystore path),
`storePassword`, `keyAlias` and `keyPassword`. Without that file, releases remain unsigned.
Never commit the file, passwords or keystore; keep the directory private and back it up securely.

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Signed APK: `app/build/outputs/apk/release/app-release.apk`. Release uses R8/resource shrinking and is not
debuggable. Verify with Android SDK `apksigner verify --verbose --print-certs <apk>` before distribution.
All future releases must reuse the same signing key and increase `versionCode` for upgrades.
The release certificate differs from the debug certificate: it cannot replace the currently installed
debug app in place. Do not uninstall or clear app data just to test a release without explicit consent.

## GitHub Actions builds

The [Android APK workflow](https://github.com/yukunwu/openlist-player/actions/workflows/android.yml)
runs on pushes to `main`, pull requests, version tags (`v*`) and manual **Run workflow** requests.
It runs both variants' unit tests and Lint, then builds debug and optimized unsigned release APKs.
Download `openlist-tv-apks` under a successful run's **Artifacts** (retained for 14 days).
Trusted `main` and version-tag builds additionally produce `openlist-tv-signed-release`, containing an
installable production APK signed with the same certificate as local official releases.

Pushing a version tag publishes the signed production APK, public certificate fingerprints and SHA-256 checksums to
[GitHub Releases](https://github.com/yukunwu/openlist-player/releases). Update `versionName` and increment
`versionCode` in `app/build.gradle` before tagging the matching version, e.g. `v0.1.9`.

- `*-debug.apk` is installable for testing, but CI uses a disposable debug signing key. It may not upgrade
  a local debug installation or a previous CI build. Do not uninstall or clear data to force installation.
- `*-release.apk` in `openlist-tv-signed-release` is the installable official build. Unsigned APKs remain
  available in the build artifact for independent signing, but cannot be installed as-is.
- No required checks, branch protections or approval gates are configured by this workflow.

### CI signing secrets

The official keystore and passwords are stored as GitHub Actions repository secrets:
`ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
Base64 is transport encoding, not encryption; GitHub Secrets provides encrypted storage.
The keystore is decoded only into the signing runner's temporary directory and removed after signing.
It is never committed, cached or included in artifacts. Signing runs in a separate job without checking
out or executing repository code. Pull requests never run that job or receive the signing secrets.

Keep a separate secure backup of the original signing key and passwords. Repository administrators and
people able to change trusted workflows must be trusted: Secrets does not prevent a malicious authorized
workflow from using or leaking a key. Public forks must supply their own keys and cannot produce updates
signed as this project. Google Play distribution can instead use Play App Signing with a separate upload key;
this project currently distributes APKs directly through GitHub.

## Verification status

- Version 0.1.9 passes 40 JVM tests in both debug and release, Lint (zero errors), APK signature
  verification (v1/v2, including API 23 compatibility) and zip alignment. Dedicated RSA-3072 certificate;
  no debug flag. Signed-release installation/playback has not been tested.
- Desktop Chrome at mobile viewport passed actual form submission against the Java receiver with synthetic
  data: JavaScript AES-GCM to Java decryption, no outside requests, no plaintext credentials on the wire.
- Unit coverage includes URL/origin rules, path validation, GCM tampering, wrong Origin, expiry, single-use
  acceptance and replay refusal. Browser test: `scripts/test-pairing-browser.mjs` (requires Playwright/Chrome).
- The initial UI was installed and launched on an authorized Android 16 USB phone without clearing data.
  The redesigned file browser was subsequently installed in place and launched successfully on that phone;
  a screenshot confirms the new layout and directory list with the existing session preserved.
  Real-phone QR scanning, authenticated playback, physical TV D-pad and 4K playback remain **unverified**.
  Do not interpret build/test success as completed on-TV validation.
- Player metadata header was observed during actual H.264/AAC playback on the phone. The instrumented
  device smoke test compiles, but its helper APK installation was refused by the phone, so measured aspect
  changes, immediate brightness swipes, volume, timed lock visibility, remote keys and file-row menus remain unverified. To run with a
  synthetic local HTTP video (never a private signed URL), install the debug and androidTest APKs and run
  `adb shell am instrument -w -e mediaUrl <test-video-url> site.openlist.player.test/site.openlist.player.PlayerUiInstrumentation`.

## Install on a TV

```sh
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device> shell am start -n site.openlist.player/.MainActivity
```

Open **OpenList TV** from the TV application list. Use arrows to move, OK to open, Back for the parent folder.
Both Leanback and standard launcher entries open the same TV-focused landscape interface.
Installation on an existing same-ID app requires a matching signer; never uninstall to work around a conflict
without considering app data. Permissions are network/network-state and media audio-settings control only.
