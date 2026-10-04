# Ellipse Ride

A small Android app that makes cycling routes of a chosen length between two saved points, using
[BRouter](https://github.com/abrensch/brouter) on the phone through its `IBRouterService` AIDL
interface, and hands the result to [OsmAnd](https://osmand.net) as GPX.

## How it works

1. Tap A or B to choose a place: pick it on a map (drag until the pin sits on the spot), use your
   current location, pick one of your saved places, or type coordinates. Give a place a name and it
   is saved for next time. Opening a `geo:` link with the app (OsmAnd's "Share location" works)
   also sets A or B. If A and B are the same spot you get a loop.
2. Pick a target distance and a BRouter profile (`trekking`, `fastbike` or `safety`).
3. The app puts random via points on an ellipse whose foci are A and B, all on one side, ordered from A
   to B. It sizes the ellipse so the straight-line path A -> vias -> B is the target divided by a guessed
   detour factor, then asks BRouter for the route.
4. Random via points sometimes land at the end of a dead end, which makes BRouter ride in and straight
   back out. Those out-and-back detours are cut from the track before it is measured.
5. If the route is off by more than 5%, the ellipse is scaled by `target / actual` and routed again.
   Once a route is on target, the app checks how much of it rides the same road twice. Under 5% and
   it's done; otherwise it tries fresh via points and keeps the on-target route with the least
   repetition.
6. The result is drawn over OpenStreetMap tiles. "Open in OsmAnd" sends the GPX straight to OsmAnd
   (or OsmAnd+); "Share" offers any app.

For loops, the ellipse becomes a circle that passes through A.

## Requirements

- Android 8.0 or newer.
- BRouter installed, opened once, with the segments for your area downloaded. The `trekking`,
  `fastbike` and `safety` profiles ship with BRouter.
- OsmAnd, if you want the one-tap hand-off.
- A network connection for the map tiles (routing itself stays offline).

## Building

```sh
./gradlew testDebugUnitTest assembleRelease   # or assembleDebug
```

APKs land in `app/build/outputs/apk/<type>/`. Every push runs both builds on GitHub Actions and
uploads them as the `ellipse-ride-release` and `ellipse-ride-debug` artifacts.

The release build is minified with R8.

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
     (on macOS: `base64 -i ellipse-ride-release.jks`)
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

To get updates automatically, users can add the repository's URL to
[Obtainium](https://github.com/ImranR98/Obtainium), which watches GitHub releases.
