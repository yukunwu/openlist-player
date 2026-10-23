# Pairing AES bundle

The Android build uses the checked-in `app/src/main/assets/forge.min.js`; Node is not needed to build the APK.
To regenerate the browser asset:

```sh
cd scripts/crypto
npm ci --ignore-scripts
npm run build
```

Dependencies and integrity hashes are pinned in `package-lock.json`. The emitted module list contains only
Forge's core, baseN, util, cipher, cipherModes and aes. RSA, ASN.1, PKI and Forge's random generator are not
included. Random IVs come from the browser's `crypto.getRandomValues`, which works on LAN HTTP without
requiring `crypto.subtle`. No fallback to Math.random is used.

The upstream 1.4.0 source release has advisory GHSA-86w9-cpqp-85rv in RSA signature verification. This
custom runtime bundle excludes that implementation; the source build dependency will still appear in
package-version scanners. This is not a claim that upstream 1.4.0 is patched or free of vulnerabilities.
The upstream BSD license is retained in `app/src/main/assets/forge-LICENSE.txt`.

After regeneration, run `scripts/test-pairing-browser.mjs` with Playwright/Chrome to verify actual
browser-to-Java AES-GCM interoperability. It uses synthetic credentials only.
