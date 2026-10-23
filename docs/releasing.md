# Releasing the TV app

A release is a signed APK, `tablero-tv-<version>.apk`, attached to a
[GitHub release](https://github.com/vgt3j4d4/tablero/releases). Pushing a `v<version>` tag on `main` runs
`.github/workflows/release.yml`: it builds and signs the release APK, checks it against the tag and drafts the
release. You test the draft's APK on a stick, then publish it.

## The version

`appVersionName` at the top of `tv-app/build.gradle.kts` is the only number to bump (`major.minor.patch`, minor and
patch under 100). `versionCode` follows from it (major × 10000 + minor × 100 + patch, so 0.1.0 is 100): Android
only installs an update with a higher `versionCode`. The workflow fails if the tag isn't `v<appVersionName>`.

## The signing key (once)

Android only installs an update signed with the same key as the installed app. **Losing the key** means nobody can
update tablero any more: every stick would have to uninstall it and install a newly signed one. **Leaking it** lets
anyone ship an "update" that installs over tablero. Keep it out of the repo (`*.jks` is ignored) and back it up.

1. Create the keystore. `keytool` asks for a password; a PKCS12 keystore uses it for the key too.

   ```bash
   mkdir -p ~/keys
   keytool -genkeypair -v -keystore ~/keys/tablero-release.jks -alias tablero \
     -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=tablero"
   ```

2. Back up the `.jks` file and its password, e.g. both in a password manager, plus a second copy of the file off
   this computer.

3. Give them to the release workflow as repository secrets. `gh secret set` asks for the value when none is piped in.

   ```bash
   base64 -i ~/keys/tablero-release.jks | gh secret set TABLERO_KEYSTORE_BASE64
   gh secret set TABLERO_KEYSTORE_PASSWORD
   gh secret set TABLERO_KEY_ALIAS --body tablero
   gh secret set TABLERO_KEY_PASSWORD          # the same password
   ```

4. Optional, to sign release builds on your computer too: add the same values to `~/.gradle/gradle.properties`
   (outside the repo; `chmod 600` it). Without them `assembleRelease` builds `tv-app-release-unsigned.apk`, which
   doesn't install.

   ```properties
   TABLERO_KEYSTORE_FILE=/Users/<you>/keys/tablero-release.jks
   TABLERO_KEYSTORE_PASSWORD=…
   TABLERO_KEY_ALIAS=tablero
   TABLERO_KEY_PASSWORD=…
   ```

## Cutting a release

1. On a branch from `develop`, bump `appVersionName` in `tv-app/build.gradle.kts` and open a PR against `develop`.
2. Merge `develop` into `main`.
3. Once CI has passed on `main` (the release workflow doesn't run the tests again), tag it and push the tag:

   ```bash
   git switch main && git pull
   git tag v0.1.0
   git push origin v0.1.0
   ```

4. Wait for the workflow (`gh run watch`); it drafts the release with the APK and notes from the merged PRs.
5. Download the draft's APK (`gh release download v0.1.0 --pattern '*.apk'`), install it on a stick as in the
   README → Install, and test it. Release builds are minified by R8, so check what could break there: the board and
   buzzer, the menu, a language change, and the game coming back after the app is killed
   (`adb shell am force-stop net.pladum.tablero.tv`). The release build is `net.pladum.tablero.tv`, the debug build
   `net.pladum.tablero.tv.debug`: they install side by side, each with its own game and settings. With the kiosk both
   are home apps; uninstall the debug one (`adb uninstall net.pladum.tablero.tv.debug`) only once the release one is
   in (`docs/stick-setup.md` → 3. Opening tablero).
6. Publish it: `gh release edit v0.1.0 --draft=false`. It becomes the latest release.

If the build or the test fails, delete the draft and its tag (`gh release delete v0.1.0 --cleanup-tag`, then
`git tag -d v0.1.0`), fix it on `develop` and go again from step 2. The version can be reused, since nobody
installed the draft.
