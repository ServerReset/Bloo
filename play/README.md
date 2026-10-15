# Google Play store assets

The source of truth for what goes into the Play Console's store listing. They live here rather than
only on whichever machine happened to do the uploading, because a listing has to stay updatable: an
asset that exists nowhere is an asset nobody can re-upload.

| File | Console slot | What it is |
|---|---|---|
| `icon-512.png` | App icon | 512×512, 8-bit RGB PNG, no alpha |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500, 8-bit RGB PNG |
| `screenshots/03-login.png` | Phone screenshot | 1800×1012, 8-bit RGB PNG |
| `short-description.txt` | Short description | 80 characters or fewer |
| `full-description.txt` | Full description | 4000 characters or fewer |

## Screenshots are currently short

Play wants **at least two** phone screenshots and there is **one** here. Two earlier files were
committed by mistake and have been removed: they were captures of the *emulator's homescreen*, not
Bloo. The tell was in the OCR — they contained no text at all — and it should have been checked
before they went in rather than after.

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
