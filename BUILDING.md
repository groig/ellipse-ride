# Building and releasing

Notes for working on Ellipse Ride itself. If you only want to use the app, see the
[README](README.md).

## Building

```sh
./gradlew testDebugUnitTest assembleRelease   # or assembleDebug
```

APKs land in `app/build/outputs/apk/<type>/`. Every push runs both builds on GitHub Actions and
uploads them as the `ellipse-ride-release` and `ellipse-ride-debug` artifacts. The release build is
minified with R8.

`app/src/main/aidl/btools/routingapp/IBRouterService.aidl` is copied unchanged from the BRouter
repository. Keep its package name: BRouter's service checks the interface descriptor.

## Signing and releases

Android only installs an update over an existing app when both are signed with the same key and the
new version code is higher. So releases are signed with one permanent key, and the version comes
from the git tag.

### One-time setup: the release key

1. Create the key (`keytool` comes with any JDK, including Android Studio's):

   ```sh
   keytool -genkeypair -v -keystore ellipse-ride-release.jks -storetype PKCS12 \
     -alias ellipse-ride -keyalg RSA -keysize 4096 -validity 36500
   ```

   With PKCS12 the key has the same password as the keystore.
2. Back up `ellipse-ride-release.jks` and its password somewhere safe, such as a password manager.
   If you lose either, no future build can update installed copies; everyone has to uninstall first.
   Never commit the file.
3. In the repository on GitHub, open Settings > Secrets and variables > Actions and add:
   - `RELEASE_KEYSTORE_BASE64`: the output of `base64 -w0 ellipse-ride-release.jks`
     (on macOS: `base64 -i ellipse-ride-release.jks`). If zsh prints a `%` at the end, that only
     marks the missing newline; don't copy it.
   - `RELEASE_KEYSTORE_PASSWORD`: the password
   - `RELEASE_KEY_ALIAS`: `ellipse-ride`
   - `RELEASE_KEY_PASSWORD`: the same password

Builds from `build.yml` use the key too once the secrets exist. Without them they fall back to the
debug key, which is fine for testing but not for sharing.

### Publishing a version

```sh
git tag v0.2.0
git push origin v0.2.0
```

Or open the Actions tab, pick "Release", click "Run workflow" and enter the version (`0.2.0`); the
tag is created for you. Either way, `release.yml` builds and tests that commit, signs the APK and publishes it as a GitHub release
with notes generated from the commits. Anyone can download it from the repository's Releases page
without a GitHub account. Each tag must be higher than the last (the version code is
`major * 10000 + minor * 100 + patch`).
