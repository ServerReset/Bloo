# Bloo for Wear OS

A minimal companion watch app. It reuses the phone's own shared modules so it can never
drift from what the phone shows or does:

- **`:shared`** — reads the same on-disk `SnapshotStore` the phone's background workers
  mirror into, and sends commands through the phone's own `CarCommandRunner`
  (`CarCommand` / `CarAction`), the exact path the phone's notification buttons and
  AutoLock already use.
- **`:uicommon`** — the foundation-only `MorphButtonCore`, so the watch's one tap target
  morphs and presses like every other Bloo button rather than a watch-only look.

## What it does today

- **Swipe up/down** between cars (a `HorizontalPager` over the snapshot's vehicle list).
- Per car, one scrollable page of **two pebbles**: **Controls** (lock/unlock) and
  **Climate** (on/off). Adding a pebble is one more `WearPebble` in `WearCarPage`, not a
  new screen.

Everything is scaffolded, not finished: the UI is intentionally tiny so the shape is
reviewable before more is built.

## Live sync

Both processes read the same `SnapshotStore`, which is what makes the watch "live sync"
in the same-device/emulator case. For a real paired watch, `WearDataLayerSync` is the
seam where the Wearable Data Layer bridge (`com.google.android.gms:play-services-wearable`,
`DataClient.putDataItem` on the phone → `OnDataChangedListener` on the watch) drops in;
`WearSnapshotRepository` and the UI do not need to change.

## Build

```
./gradlew :wear:assembleDebug
```

`minSdk 30` (Wear OS 3.0, the first with a real Compose runtime); `:shared`/`:uicommon`
stay at 26 so they remain usable by both the phone and the watch.
