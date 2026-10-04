# Ellipse Ride

Ellipse Ride plans bike rides of the length you ask for. Pick where you start, where you want to
finish (or the same place for a loop), choose how far you want to ride, and it finds a route of
about that distance, within 5%. Every tap gives you a different ride. When you like one, send it to
[OsmAnd](https://osmand.net) to navigate it.

The routing is done on your phone by [BRouter](https://github.com/abrensch/brouter), using
OpenStreetMap data, so it works without a connection once BRouter has its maps.

## What you need

- An Android phone with Android 8.0 or newer.
- BRouter (free, from [F-Droid](https://f-droid.org/packages/btools.routingapp/) or
  [Google Play](https://play.google.com/store/apps/details?id=btools.routingapp)).
  Open it once after installing and use its download manager to get the map squares covering the
  area you ride in. Ellipse Ride can't plan anything outside those squares.
- OsmAnd, if you want to navigate the ride. Any app that opens GPX files works too.

## Install

Download the latest `ellipse-ride-vX.Y.Z.apk` from the
[Releases page](https://github.com/groig/routing/releases/latest) and open it on your phone. Android
will ask you to allow installing apps from your browser or file manager the first time.

To hear about new versions, add `https://github.com/groig/routing` to
[Obtainium](https://github.com/ImranR98/Obtainium). It checks the Releases page and offers updates,
which install over the old version and keep your saved places.

## Using it

1. Tap **A** to set your start. You can drag a map until the pin sits on the spot, use where you
   are now, or pick one of your saved places. Typing coordinates works too.
2. Do the same for **B**, your finish. Use the same place as A for a loop.
3. Give a place a name when you set it and it is saved for next time.
4. Set the distance with the slider or the + and - buttons, and pick a riding style. Trekking mixes
   quiet roads and cycle paths and is a good default. Fast prefers smooth, direct roads, for road
   bikes. Quiet avoids traffic as much as it can.
5. Tap **Generate route**. It takes a few seconds to a minute, depending on the distance.
6. Check the ride on the map, then tap **Open in OsmAnd** or **Share**. Tap **Try another route**
   for a different one.

You can also share a location to Ellipse Ride from OsmAnd or a maps app (as a `geo:` link) and set
it as A or B.

## How it picks a route

Ellipse Ride scatters a few random waypoints on an ellipse around A and B and asks BRouter for the
best ride through them. If the ride comes out too long or too short, it shrinks or grows the ellipse
and asks again until the distance is within 5% of what you wanted.

Random waypoints can make odd rides, so it also tidies up after BRouter. Detours down a dead end and
straight back out are cut. Rides that use the same road twice get tried again with new waypoints,
and the one with the least repetition wins. The card under the map shows the distance, how far it
is from your target, the climbing, and how much of the ride repeats roads.

## Troubleshooting

If the error mentions "position not mapped" or "datafile not found", BRouter doesn't have the map
square for part of the area. Open BRouter's download manager and add the squares around A, B and
the ride.

If it says "Profile ... does not exists", your BRouter install is missing that riding style. Pick
another one, or reinstall BRouter.

If the ride always comes out much longer than you asked, the distance is shorter than the direct
route between A and B. Move the points closer together or ask for a longer ride.

If there's no map under the route, the map needs an internet connection. The route itself is
still fine and can be opened in OsmAnd.

## Privacy

Routing happens on your phone. The app has no accounts, ads or tracking. It uses the internet only
to load map tiles from openstreetmap.org, which sees which area you're looking at, the same as any
map app. Location access is optional and only used when you ask to use where you are.

## Building from source

See [BUILDING.md](BUILDING.md).
