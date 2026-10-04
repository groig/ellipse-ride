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

The release build is minified with R8. To sign it with your own key, create a keystore:

```sh
keytool -genkeypair -v -keystore release.jks -alias ellipse-ride \
  -keyalg RSA -keysize 4096 -validity 10000
```

then add these repository secrets under Settings > Secrets and variables > Actions:

- `RELEASE_KEYSTORE_BASE64`: output of `base64 -w0 release.jks`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS` (`ellipse-ride` above)
- `RELEASE_KEY_PASSWORD`

Locally, set the same variables, with `RELEASE_KEYSTORE` pointing at the `.jks` file. Without them
the release APK is signed with the debug key: it installs fine, but Android won't let you update it
in place with a build signed by a different key later.

`app/src/main/aidl/btools/routingapp/IBRouterService.aidl` is copied verbatim from the BRouter
repository; keep its package name, since that is what the service binder expects.
