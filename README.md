<p align="center">
  <img src="docs/img/icon.png" width="104" alt="Bloo icon">
</p>

<h1 align="center">Bloo</h1>

<p align="center"><b>Lock it from the couch. Cool it from the car park. Charge it from bed.</b></p>

<p align="center">
  An independent app for Hyundai, Genesis and Kia cars, for phones, foldables, tablets and Wear OS.<br>
  Open source. No server of ours. Your password is encrypted on your phone.
</p>

<p align="center">
  <a href="https://serverreset.github.io/Bloo/"><b>Website</b></a> ·
  <a href="https://serverreset.github.io/Bloo/privacy.html"><b>Privacy policy</b></a> ·
  <a href="https://serverreset.github.io/Bloo/privacy-plain.html">Privacy (plain HTML)</a> ·
  <a href="https://github.com/ServerReset/Bloo/releases/latest">Latest release</a>
</p>

---

## 🚪 Get in

Two ways in. Both are the same app.

**Google Play (closed test)**

1. **Join the testing group:** [groups.google.com/g/bloo-testing](https://groups.google.com/g/bloo-testing). This puts your Google account on the tester list. The Play link only works after this step.
2. **Install from Google Play:** [play.google.com/store/apps/details?id=com.bloo.bluelink](https://play.google.com/store/apps/details?id=com.bloo.bluelink). Play keeps it updated from there.

Testing builds skip review, so new versions arrive the day they are built.

**Or the APK, straight from a release**

- **[Bloo.apk](https://github.com/ServerReset/Bloo/releases/latest/download/Bloo.apk)**: the phone app.
- **[Bloo-watch.apk](https://github.com/ServerReset/Bloo/releases/latest/download/Bloo-watch.apk)**: the watch app.

No Play account and no GitHub sign-in needed. Step-by-step instructions are under [Install](#-install).

### Play or APK? Pick a team

They are the same app, but they are **not interchangeable**. The package name matches, but the signing key does not. Play re-signs every upload with Google's app-signing key, so a Play copy and an APK copy cannot update each other. Switching costs one uninstall, and an uninstall takes the app's data with it. Export first, from **Settings → Export**.

| | Google Play | The APK |
|---|---|---|
| **Updates** | Play downloads and installs them | The app updates itself: silently with [Shizuku](https://shizuku.rikka.app/), otherwise through Android's installer |
| **Watch app** | Play installs it on the watch | The phone pairs with the watch's wireless debugging and pushes it across |
| **Needs** | A Google account on the tester list | Nothing but the file |
| **Size** | Smaller, no installer of its own | Larger, because it carries one |

The APK is the fuller of the two, and that is the reason it exists. It installs the watch app and updates itself, which Play does not allow an app to do. Neither is a "test build" in the unfinished sense. Both are the same release, published by the same pipeline at the same moment.

---

## 🚗 What it does

- **Control.** Lock and unlock, remote climate, charging, live location, full vehicle status, trips and weather.
- **On-device AI.** Gemini Nano summaries and plain-language commands. The model runs on your phone.
- **AutoLock.** Locks the car when you walk away from it.
- **Sync.** Settings and photos follow you across devices through your own Google Drive.
- **Big screens.** Adaptive layouts for foldables and tablets, with reorderable pebbles, custom palettes and an animated background.
- **Several accounts,** signed in at once.
- **Widgets.** A full home-screen widget (car photo behind it, corner shape, text size, colour by charge level, any size from 2×2 to 5×5), plus a slim 4×1 strip. Both have lock, climate and charge buttons.
- **Watch app.** The charge as a ring round the display, with four controls you choose from a dozen (lock, climate, charge, flash, horn, refresh and more), pull-to-refresh, a Tile, and a complication for any watch face.

---

## 🔐 Security, without the hand-waving

- **One encrypted vault.** Sign-in credentials, car service PINs, session tokens and the app-lock PIN record live in a single AES-256-GCM encrypted file. Nothing sensitive is stored anywhere else.
- **Two keys over one data key.** The vault's data key is sealed by a hardware-backed Android Keystore key, so background refresh, notifications and AutoLock keep working. If you set an app PIN, the same data key is also sealed under a key derived from your PIN (PBKDF2-HMAC-SHA256, 210,000 rounds). Entering the PIN recovers your accounts if the device key is ever lost.
- **App lock.** Biometric unlock, with an optional 4 to 8 digit PIN backup, a lockout after repeated wrong PINs, and a choice of when to re-lock.
- **Sealed after a restart.** After your phone reboots, Bloo hands out nothing and runs nothing (no alerts, AutoLock or refreshes) until you sign in with your account, then your fingerprint or PIN. A notification reminds you. You can turn this off in Settings.
- **Nothing leaves the device** except requests to the car brand's own servers. The app collects no data.

The full privacy policy is on the [website](https://serverreset.github.io/Bloo/privacy.html), and the same text is shown inside the app. It is also written in [plain HTML](https://serverreset.github.io/Bloo/privacy-plain.html), in case you prefer your privacy policies undecorated.

---

## 🛠️ Install

Bloo is in closed testing on Google Play (see [Get in](#-get-in)), or you can install it straight from a release.

1. On your phone, open the [latest release](https://github.com/ServerReset/Bloo/releases/latest) and, under **Assets**, download **`Bloo.apk`**. Wear OS owners: `Bloo-watch.apk` is there too.
2. Open the downloaded file. If you can't find it, pull down your notifications and tap the finished download, or open **Downloads**. In your browser it is usually behind the **⋮** or **☰** menu. Or use the **Files** app → **Downloads**.
3. Android will warn that Bloo isn't from a trusted source. What you tap depends on the warning:
   - **"Blocked by Play Protect" or "unsafe app":** tap **More details** (if you don't see it, look for a **⌄** or **⋮** on the warning sheet to expand it), then **Install anyway**.
   - **"For your security…" (unknown sources):** tap **Settings**, turn on **Allow from this source** for the app you are installing with (your browser, or Files), go **Back**, then tap **Install**.
4. Confirm with your **fingerprint, face, PIN, pattern or password**.

After that the app updates itself. Every push publishes a new release, and the build number is the version.

### If an update says "App not installed"

Bloo was signed with a new key in October 2026. Android will not install an update signed with a different key than the copy already on your phone, so a version installed before that change has to be replaced rather than updated. The download is fine. The installer just refuses it.

Export your settings first (**Settings → Backup & sync → Export**), uninstall Bloo, install the newest `Bloo.apk` from the [releases page](https://github.com/ServerReset/Bloo/releases), sign in again, and restore the backup. Until you do, the old copy keeps working exactly as it did. It just stops receiving updates.

---

## 🧑‍💻 For the curious

```bash
./gradlew assembleDebug
```

You need JDK 17 and Android Studio Meerkat or newer.

- **`:app`** is the UI, the workers and AutoLock.
- **`:shared`** holds the API clients, models and stores.
- **`:uicommon`** holds the shared Compose components.
- **`docs/`** is the website. Its privacy policy is copied into the app at build time, so editing the page updates what the app shows.

Every user-facing string follows one page of rules: **[docs/COPY.md](docs/COPY.md)**.

---

## ⚠️ Disclaimer

Bloo is unofficial and is not affiliated with Hyundai, Genesis or Kia. Remote commands physically operate your vehicle, so use them responsibly.
