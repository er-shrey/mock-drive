# MockDrive

An Android test tool that fakes a vehicle **driving** a route, so location-tracking apps
(mileage logs, IFTA-style per-state distance, geofences) can be tested without driving.

Pick a start and destination (tap the map or search), set a speed, press **Start driving**.
The whole phone then reports a GPS position moving along real roads at that speed,
one fix per second, with heading and speed filled in.

**Download:** [MockDrive-0.1.0.apk](https://github.com/er-shrey/mock-drive/releases/download/v0.1.0/MockDrive-0.1.0.apk)

## Features
- OpenStreetMap map. Tap to add start → stops → destination, or search for a place.
- Road routing (OSRM) or straight lines ("Follow roads" off).
- Speed in mph or km/h (tap the unit to switch), changeable mid-drive.
- Time warp 1×–30× to cover long routes quickly. The reported speed stays at the set speed,
  but the position advances N× faster.
- Pause = parked at the current spot (speed 0, fixes keep flowing). On arrival it holds at
  the destination until Stop.
- One point only = "Fake location here" (static mock).
- The last route and settings survive restarts.
- Notification with Pause/Resume and Stop.

## What gets mocked
Every source an app normally reads:
- `LocationManager` GPS, network and fused (API 31+) test providers
- Google Play services `FusedLocationProviderClient` (mock mode)

Mocked fixes carry Android's mock flag (`Location.isMock()`). An app that rejects mock
locations must allow them in its debug/test builds.

## Setup on a phone (one time)
1. Install `MockDrive-<version>.apk`.
2. Turn on Developer options (Settings › About phone › tap Build number 7 times).
3. Developer options › **Select mock location app** › MockDrive.
4. Allow location and notifications when MockDrive asks.

Emulator / adb shortcut for step 3:

    adb shell appops set com.shrey.mockdrive android:mock_location allow

## Build
    ./gradlew testDebugUnitTest assembleRelease

The release APK is signed with the local debug key (it's a test tool, not a store app).
Map tiles, routing and search use free public OpenStreetMap servers, so keep usage light.
