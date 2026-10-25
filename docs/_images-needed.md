# Images the site is waiting for

Drop each file into `docs/img/` with exactly this name. Until a file exists the page shows a labelled placeholder in its slot, so
nothing looks broken; the moment the file lands the real picture replaces it. PNG or WebP, sRGB, no personal data (plate, VIN,
address, map location, account name). Sample data like the existing screenshots is ideal.

## Needed (these slots are empty today)

| File | What it is | Shape and size |
|---|---|---|
| `device-phone.png` | A phone screenshot for the Devices section: the garage with a real car photo on the hero card | portrait, 1080 x 2060 (9:17.4); a normal 9:19.5 capture also works, the sides crop a little |
| `device-fold.png` | An unfolded foldable showing two or three cars side by side (the multi-column layout) | landscape 4:3, about 2400 x 1800 |
| `device-watch.png` | The watch face with its four controls, ideally also the charge ring | square 1:1, about 800 x 800, cropped to the round display (the page masks it to a circle) |

## Nice to have (swap in better pictures, or unlock new sections)

| File | What it is | Shape and size |
|---|---|---|
| `og.png` | The picture shown when the site is shared in a chat | 1200 x 630 |
| `hero-left.png`, `hero-center.png`, `hero-right.png` | Three phone screenshots for the big hero image. Today the hero uses `garage.png`, `garage-dark.png` and `lock-dark.png`; tell me and I will point the slots at these instead | portrait, 1080 x 2060 each |
| `showcase-light.png`, `showcase-dark.png` | The same screen in light and dark, for the Look section's toggle (today `garage.png` and `garage-dark.png`) | portrait, 1080 x 2060 |
| `widget.png` | The home-screen widget with a car photo behind it | landscape or square, about 1200 x 800 |
| `watch-tile.png`, `watch-complications.png` | The watch tile and a watch face with the complications | square, 800 x 800 each |
| `hero-photo.png` | A close-up of the hero card showing the text switching to stay readable over a light and a dark photo (a side-by-side works) | landscape, about 1600 x 900 |

Short screen recordings (an MP4 of the lock switch, the climate dial, a page swipe) would also work well as autoplaying loops, if you want them.
