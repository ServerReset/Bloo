# Google Play store assets

The source of truth for what goes into the Play Console's store listing. They live here rather than
only on whichever machine happened to do the uploading, because a listing has to stay updatable: an
asset that exists nowhere is an asset nobody can re-upload.

| File | Console slot | What it is |
|---|---|---|
| `icon-512.png` | App icon | 512×512, 8-bit RGB PNG, no alpha |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500, 8-bit RGB PNG |
| `screenshots/01-welcome-light.png` | Phone screenshot 1 | 1080×1012, 8-bit RGB PNG |
| `screenshots/02-welcome-dark.png` | Phone screenshot 2 | 1080×1012, 8-bit RGB PNG |
| `screenshots/03-login.png` | Phone screenshot 3 | 1800×1012, 8-bit RGB PNG |
| `short-description.txt` | Short description | 80 characters or fewer |
| `full-description.txt` | Full description | 4000 characters or fewer |

## The two format rules that bite

1. **8 bits per channel.** All four images here were 16-bit PNGs when they were handed over, and Play
   refuses those. Everything in this directory is 8-bit.
2. **No alpha on screenshots.** Play's spec is "JPEG or 24-bit PNG (no alpha)". A screenshot carrying
   an alpha channel is rejected *even when the channel is fully opaque* — which is what every one of
   the originals did.

Check both before adding a new file: `identify -format "%w x %h  %[bit-depth]bit %[channels]\n" <file>`
should print `8bit` and `srgb` — not `srgba`.

## The icon

`icon-512.png` is a raster of the adaptive launcher icon, not a file the app ships:
`app/src/main/res/mipmap-anydpi/ic_launcher.xml` (background `#0B5FFF`, white foreground). An adaptive
icon cannot be uploaded to Play, so this is the version that gets uploaded — regenerate it if the icon
ever changes, or the listing will advertise an icon the app no longer has.

## Screenshots

The garage with a car loaded is the one that sells this app. The login screen is the weakest slot in
any listing and is here only to have a third.

**No screenshot may show the vehicle's location, an address, or anything else that says where the car
or its owner is.** OCR any new capture before adding it — that is how the current three were checked.
