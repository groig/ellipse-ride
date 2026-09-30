# Ellipse Ride

A small Android app that makes cycling routes of a chosen length between two saved points, using
[BRouter](https://github.com/abrensch/brouter) on the phone through its `IBRouterService` AIDL
interface, and hands the result to [OsmAnd](https://osmand.net) as GPX.

## How it works

1. Save a start (A) and a finish (B). Type coordinates, paste a `geo:` link, use your current
   location, or open a `geo:` link with the app (OsmAnd's "Share location" works). If A and B are the
   same spot you get a loop.
2. Pick a target distance and a BRouter profile (`trekking`, `fastbike` or `safety`).
3. The app puts random via points on an ellipse whose foci are A and B, all on one side, ordered from A
   to B. It sizes the ellipse so the straight-line path A-> vias-> B is the target divided by a guessed
   detour factor, then asks BRouter for the route.
4. If the route is off by more than 5%, the ellipse is scaled by `target / actual` and routed again.
   After a few passes without success it starts over with fresh random points, and keeps the closest
   route it found.
5. "Open in OsmAnd" sends the GPX straight to OsmAnd (or OsmAnd+); "Share" offers any app.

For loops, the ellipse becomes a circle that passes through A.

## Requirements

- Android 8.0 or newer.
- BRouter installed, opened once, with the segments for your area downloaded. The `trekking`,
  `fastbike` and `safety` profiles ship with BRouter.
- OsmAnd, if you want the one-tap hand-off.

## Building

```sh
./gradlew assembleRelease   # or assembleDebug
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
