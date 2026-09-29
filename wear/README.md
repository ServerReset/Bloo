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

- **Swipe left/right** between cars (a `HorizontalPager` over the snapshot's vehicle list).
- Per car, one scrolling page ([`ScalingLazyColumn`](https://developer.android.com/training/wearables/compose) so pebbles shrink toward the round edges and scroll with the crown) of **two
  pebbles**: **Controls** (lock/unlock) and **Climate** (on/off). Adding a pebble is one more
  `WearPebble` in `WearCarPage`, not a new screen.
- Wrapped in Wear's own `AppScaffold` + `ScreenScaffold` + `TimeText`, so it honours the
  round face and shows the time like a watch app should.

Everything is scaffolded, not finished: the UI is intentionally tiny so the shape is
reviewable before more is built.

## How the phone shows it

The watch registers through the SAME Google Drive sync as a phone (its `SettingsStore`
`selfSyncDevice` stamps `kind = "watch"` from `PackageManager.FEATURE_WATCH`), so it lands
in the phone's "Synced devices" registry. Settings renders it as a **companion nested under
its phone**, not a reorderable peer — see `WearCompanionRow` in `DriveSyncUi.kt`. A watch
can never be the primary (source of truth) device.

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
