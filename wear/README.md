# Bloo for Wear OS

A small, lean companion watch app. It is a pure **auxiliary** surface: it never talks to the
network or Google Drive, and never runs car commands itself. Everything it shows is pushed from
the phone over the Wearable Data Layer, and every command it wants is forwarded to the phone,
which runs it through its normal command path (`CarCommandRunner`) and reports back.

It reuses the phone's own shared modules so the two can never drift:

- **`:shared`** — `SnapshotStore`/`VehicleSnapshot` for state, `CarCommand`/`CarAction` for the
  command vocabulary, `PinRecord`/`PinCrypto`/`PinLockout` for the PIN, and the pure
  `WatchPinPolicy` + `WatchSyncProtocol`/`WatchSyncPayload` that define the sync contract.
- **`:uicommon`** — the foundation-only `MorphButtonCore`, so the watch's tap targets morph and
  press like every other Bloo button.

## What it does

- **Swipe left/right** between cars; each car is a scrolling `ScalingLazyColumn` of pebbles.
- **Pebbles:** Controls (lock/unlock) and Climate (on/off). The car's state line (locked, %, charging) rides above them. Adding a pebble is one more `WearPebble`, not a new screen.
- Wrapped in Wear's own `AppScaffold` + `ScreenScaffold` + `TimeText`, so it honours the round
  face and shows the time.

## Real-time sync (phone → watch)

See `WearDataLayerSync` (watch) and `PhoneWatchSyncService`/`WatchPresence` (phone). The phone
pushes a `WatchSyncPayload` on every snapshot write; the watch mirrors it. Commands go the other
way and are run on the phone.

## PIN gate

`WatchPinStore` verifies against the phone-sent `PinRecord` (PBKDF2; the watch never holds the
PIN). When it asks is decided by the phone's Settings → **Ask the watch for my PIN** (Off /
Opening / Commands / Both), which only appears when a watch is paired. The rules are pure and
exhaustively unit-tested in `WatchPinPolicyTest`.

## Updates

The phone (which has the network) downloads the watch APK and pushes its bytes to the watch; the
watch writes it and hands it to the system installer, so the user stays on the watch. A URL
fallback exists if the Data Layer push is unavailable, and a one-time "allow installs" grant is
surfaced in-app. `Bloo-watch.apk` is published on every GitHub Release.

## Build

```
./gradlew :wear:assembleDebug     # -> wear/build/outputs/apk/debug/Bloo-watch.apk
```

`minSdk 30` (Wear OS 3.0, the first with a real Compose runtime); `:shared`/`:uicommon` stay at 26
so both the phone and the watch can use them.
