<p align="center">
  <img src="docs/img/icon.png" width="96" alt="Bloo icon">
</p>

<h1 align="center">Bloo</h1>

<p align="center"><b>Lock it from the couch. Cool it from the car park. Charge it from bed.</b></p>

<p align="center">
  An independent app for Hyundai, Genesis and Kia cars, for phones, foldables, tablets and Wear OS.<br>
  Open source. No server of ours. Your password is encrypted on your phone.
</p>

<p align="center">
  <a href="https://serverreset.github.io/Bloo/"><img alt="Visit the website" src="https://img.shields.io/badge/Visit%20the%20website-serverreset.github.io%2FBloo-2f4fd1?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://serverreset.github.io/Bloo/privacy.html"><b>Privacy policy</b></a> ·
  <a href="https://serverreset.github.io/Bloo/privacy-plain.html">Privacy (plain HTML)</a> ·
  <a href="https://groups.google.com/g/bloo-testing"><b>Join the testers</b></a> ·
  <a href="https://play.google.com/store/apps/details?id=com.bloo.bluelink"><b>Google Play</b></a> ·
  <a href="https://github.com/ServerReset/Bloo/releases"><b>Releases</b></a>
</p>

---

## 🌐 Start on the website

The website is the easiest place to read about Bloo, and it is where the **privacy policy** lives. Everything below is also on it, with the friendlier layout.

**[serverreset.github.io/Bloo](https://serverreset.github.io/Bloo/)**

- The **[privacy policy](https://serverreset.github.io/Bloo/privacy.html)** explains what is stored, where it goes, what happens to a transaction, and what to do if a pirate takes the wheel. It is also in the app, under the sign-in screen and in Settings.
- Prefer plain text? The **[plain HTML version](https://serverreset.github.io/Bloo/privacy-plain.html)** is the same policy with no styling at all.

---

## 🚪 Get it

There are two ways in. They are the same app, and they are **not interchangeable**, so pick one and stick with it.

### Option 1: Google Play (closed testing)

Play is the easiest route. It keeps the app up to date for you.

1. **Join the testing group** with the Google account you use on your phone: **[groups.google.com/g/bloo-testing](https://groups.google.com/g/bloo-testing)**. This puts the account on the tester list. The Play link only works once you are on it.
2. **Open the Play listing:** **[play.google.com/store/apps/details?id=com.bloo.bluelink](https://play.google.com/store/apps/details?id=com.bloo.bluelink)**, tap **Become a tester**, then **Install**.

### Option 2: the APK, straight from a release

No Play account and no GitHub sign-in are needed.

- **[Bloo.apk](https://github.com/ServerReset/Bloo/releases/latest/download/Bloo.apk)**: the phone app.
- **[Bloo-watch.apk](https://github.com/ServerReset/Bloo/releases/latest/download/Bloo-watch.apk)**: the Wear OS app.
- **[All releases](https://github.com/ServerReset/Bloo/releases)**: each build is listed with its number.

Step-by-step instructions are under [Install](#-install).

Pick one route and stick with it. Moving between them means an uninstall, and an uninstall removes the app's data, so export your settings first from **Settings → Backup & sync → Export**.

---

## 🚗 What it does

- **Live status.** Charge or fuel, range, lock state, tyre pressure and trips, straight from your car's own service.
- **Control.** Lock and unlock, remote climate, charging, live location and weather.
- **Smart climate.** Start the cabin with a preset, or let it pick a temperature for the weather where the car is parked.
- **AutoLock.** Locks the car when you walk away from it.
- **Several cars.** Sign in to more than one account and swipe between every car you own.
- **Widgets.** A home-screen widget you can style (car photo, corners, text size, colour by charge level), plus a slim strip for quick controls.
- **Watch app.** The charge as a ring round the display, four controls you choose from a dozen, pull-to-refresh, a Tile, and a complication for any watch face.
- **On-device AI.** Gemini Nano summaries and plain-language commands. The model runs on your phone.
- **Sync.** Settings and photos follow you across devices through a file in your own Google Drive.
- **Big screens.** Adaptive layouts for foldables and tablets, with reorderable pebbles, custom palettes and an animated background.
- **Accessible.** Reduce motion, keyboard shortcuts (Ctrl + / lists them), and labels for screen readers.

---

## 🔐 Security, without the hand-waving

- **One encrypted vault.** Sign-in credentials, car service PINs, session tokens and the app-lock PIN record live in a single AES-256-GCM encrypted file. Nothing sensitive is stored anywhere else.
- **Two keys over one data key.** The vault's data key is sealed by a hardware-backed Android Keystore key, so background refresh, notifications and AutoLock keep working. If you set an app PIN, the same data key is also sealed under a key derived from your PIN (PBKDF2-HMAC-SHA256, 210,000 rounds), so entering the PIN recovers your accounts if the device key is ever lost.
- **App lock.** Biometric unlock, with an optional 4 to 8 digit PIN backup, a lockout after repeated wrong PINs, and a choice of when to re-lock.
- **After a restart.** Bloo waits until you unlock it before its background work resumes.
- **Nothing leaves the device** except requests to the car brand's own servers, and the update check. The app collects no data.

The full policy is on the [website](https://serverreset.github.io/Bloo/privacy.html), and the same text is shown inside the app.

---

## 🛠️ Install

The steps for each option are above. For the APK:

1. On your phone, open the [latest release](https://github.com/ServerReset/Bloo/releases/latest) and, under **Assets**, download **`Bloo.apk`**. Wear OS owners: `Bloo-watch.apk` is there too.
2. Open the downloaded file. If you can't find it, pull down your notifications and tap the finished download, or open **Downloads**. In your browser it is usually behind the **⋮** or **☰** menu. Or use the **Files** app → **Downloads**.
3. Android will warn that Bloo isn't from a trusted source. What you tap depends on the warning:
   - **"Blocked by Play Protect" or "unsafe app":** tap **More details** (if you don't see it, look for a **⌄** or **⋮** on the warning sheet to expand it), then **Install anyway**.
   - **"For your security…" (unknown sources):** tap **Settings**, turn on **Allow from this source** for the app you are installing with (your browser, or Files), go **Back**, then tap **Install**.
4. Confirm with your **fingerprint, face, PIN, pattern or password**.

Bloo can check for and install its own updates. With [Shizuku](https://shizuku.rikka.app/) running, it can do so without asking. Otherwise Android's installer asks you to confirm.

### If an update says "App not installed"

Android refuses an update whose signature differs from the copy already on your phone. If that happens, export your settings (**Settings → Backup & sync → Export**), uninstall Bloo, install the newest `Bloo.apk` from the [releases page](https://github.com/ServerReset/Bloo/releases), sign in again, and restore the backup.

---

## 🧑‍💻 For the curious

```bash
./gradlew assembleDebug
```

You need JDK 17 and Android Studio Meerkat or newer.

- **`:app`** is the UI, the workers and AutoLock.
- **`:shared`** holds the API clients, models and stores.
- **`:uicommon`** holds the shared Compose components.
- **`:wear`** is the Wear OS app.
- **`docs/`** is the website. Its privacy policy is copied into the app at build time, so editing the page updates what the app shows.

Every user-facing string follows one page of rules: **[docs/COPY.md](docs/COPY.md)**.

---

## ⚠️ Disclaimer

Bloo is unofficial and is not affiliated with Hyundai, Genesis or Kia. Remote commands physically operate your vehicle, so use them responsibly.
