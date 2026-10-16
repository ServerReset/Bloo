# Google Play store assets

The source of truth for what goes into the Play Console's store listing. They live here rather than
only on whichever machine happened to do the uploading, because a listing has to stay updatable: an
asset that exists nowhere is an asset nobody can re-upload.

| File | Console slot | What it is |
|---|---|---|
| `icon-512.png` | App icon | 512×512, 8-bit RGB PNG, no alpha |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500, 8-bit RGB PNG |
| `screenshots/01-garage.png` | Phone screenshot 1 | 1080×1920 (9:16), 8-bit RGB PNG |
| `screenshots/02-lock.png` | Phone screenshot 2 | 1080×1920 (9:16), 8-bit RGB PNG |
| `screenshots/03-garage-dark.png` | Phone screenshot 3 | 1080×1920 (9:16), 8-bit RGB PNG |
| `screenshots/04-lock-dark.png` | Phone screenshot 4 | 1080×1920 (9:16), 8-bit RGB PNG |
| `screenshots-wear/01-watch.png` | **Wear OS screenshot** | 360×360 (1:1), 8-bit RGB PNG |
| `short-description.txt` | Short description | 80 characters or fewer |
| `full-description.txt` | Full description | 4000 characters or fewer |

The Wear shot is the watch app's **own** slot (`wearScreenshots`), not a phone screenshot — Play will not
carry a watch app without at least one image shaped for a watch, which is the same kind of per-form-factor
requirement as the form factor itself. It is a real capture off the Wear emulator (360×360, circular),
not a mock, and it shows the companion's honest state: *"Waiting for your phone…"*, because a Wear app
with `standalone=false` has nothing to show until the phone has paired and pushed a snapshot.

**Worth replacing** the moment there is a paired watch to capture: the same command against a real
device, with the phone app signed in, gives the populated screen instead. A "waiting" shot satisfies the
requirement but does not sell anything.

## Screenshots are currently short

All four are captures of the real UI with a **fabricated car** — see "The sample garage" below. Earlier
files were committed by mistake and removed: two were captures of the *emulator's homescreen*, and the
first sign-in shot was replaced once there was a screen worth showing.

What is missing, and where it has to come from:

- **The garage with a car loaded.** This is the screenshot that sells the app and there is no way to
  produce it without a signed-in Bluelink account, so it has to come off a real phone. Capture it
  portrait, make sure no address or location text is visible, and add it here.
- Two or three more of the signed-in app — pebbles, climate, charge, the widget — same rule.

A capture of the sign-in screen is the weakest slot in any listing. It is here to fill the space and
should be replaced as soon as there is a real screen to put in its place.

## The two format rules that bite

1. **8 bits per channel.** The images that arrived were 16-bit PNGs, and Play refuses those.
2. **No alpha on screenshots.** Play's spec is "JPEG or 24-bit PNG (no alpha)" — and a fully-opaque
   alpha channel is still an alpha channel, which every original had.

Check both before adding a file:

```sh
identify -format "%w x %h  %[bit-depth]bit %[channels]\n" <file>   # want: 8bit srgb
```

Keep the aspect ratio between 16:9 and 9:16, and 320–3840 px on every side. A 900×1900 emulator
capture is **2.11:1** and gets rejected — the phone frame has to be widened or the capture padded.

## The icon

`icon-512.png` is a raster of the adaptive launcher icon, not a file the app ships:
`app/src/main/res/mipmap-anydpi/ic_launcher.xml` (background `#0B5FFF`, white foreground). An adaptive
icon cannot be uploaded to Play, so this is the version that gets uploaded — regenerate it if the icon
ever changes, or the listing will advertise an icon the app no longer has.

## Before committing any screenshot

OCR it. That is how the junk above was caught, that is how the current one was checked for location
text, and it is the only check available here — there is no vision tool in this session.

## The sample garage

The garage cannot be photographed on a device that has never signed in: `coldStartAutoLogin` sends any
build with no saved login straight to `Screen.Login`, and a saved login lives in the Keystore-backed
vault. Signing in, meanwhile, is the one thing that would put a real car — its name, its state and
its location — on a public listing.

So `DebugSampleGarage` (app/src/main, behind `BuildConfig.DEBUG`) fabricates the *state* instead: one
car that belongs to nobody, and **no location at all** — `lat`/`lon` and `placeNames` are left unset,
which is what leaves the Location pebble reading "Parked" with no address. The lock shot adds a sample
PIN record through the app's own store, because the lock renders a dead end without one.

Trigger it on a debug build (see the receiver's own doc):

```sh
adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd garage   # garage
adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd lock     # + lock screen
adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd off
```

It is inert in a release build: the flag reads false at the single call site and R8 drops the object.
Every capture is OCR-checked for an address, a coordinate, a plate or a VIN before it is committed.

## Dark frames

No fixture work was needed for these: the app's stored display default is `system`, so
`adb shell cmd uimode night yes` followed by a force-stop and relaunch is the whole trick. Each frame
is verified dark by measuring the mean luminance of its middle band, not by trusting the screenshot.
