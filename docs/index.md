<p align="center"><img src="img/icon.png" width="104" alt="Bloo"></p>

<h1 align="center">Bloo</h1>

<p align="center"><b>Lock, climate, charge and live status for your Hyundai, Genesis or Kia - without the slow official app.</b></p>

An independent Android client for the Bluelink / Connected Car services (US, Canada, Hyundai Europe).
Phones, foldables, tablets and Wear OS. Jetpack Compose, Material 3 Expressive, and every screen
talking to live servers.

---

## Join the test

Bloo is in **closed testing on Google Play** while it goes through review. Two steps, and both use the
Google account you use on your phone.

**1. Join the testing group.** This is what puts you on the tester list.

### **[→ Join the group](https://groups.google.com/g/bloo-testing)**

**2. Install from Google Play.** Once you are in the group the listing below resolves, and Play keeps
the app up to date from there.

### **[→ Get Bloo on Google Play](https://play.google.com/store/apps/details?id=com.bloo.bluelink)**

Tap **Become a tester**, then install from the Play Store link it gives you. Builds on a testing track
skip review, so new versions arrive the day they are built.

**Not on Play, or a device without Play services?** Install the APK straight from the newest release -
**[Bloo.apk](https://github.com/ServerReset/Bloo/releases/latest/download/Bloo.apk)** for the phone,
**[Bloo-watch.apk](https://github.com/ServerReset/Bloo/releases/latest/download/Bloo-watch.apk)** for the
watch. Instructions are in the [install notes](https://github.com/ServerReset/Bloo#install).

### Which one should I use?

Both are the same features, but they are **not interchangeable** - the package name is the same and the
**signing key is not**, because Play re-signs every upload with Google's app-signing key. A Play copy and
an APK copy cannot update each other, so moving between them costs one uninstall, and an uninstall takes
the app's data with it. What differs is how each one updates and how the watch gets its app:

| | Google Play | The APK |
|---|---|---|
| **Updates** | Play installs them | the app does it itself, silently with [Shizuku](https://shizuku.rikka.app/) |
| **Watch app** | Play installs it on the watch | the phone pushes it over wireless debugging |
| **Needs** | a Google account on the tester list | nothing but the file |

The APK is the fuller one: it carries its own installer, which is exactly why it cannot go on Play -
Play does not allow an app to install other apps. Neither is a test build in the "unfinished" sense.

---

## Screenshots

<p align="center">
  <img src="img/garage.png" width="23%" alt="The garage">
  <img src="img/lock.png" width="23%" alt="The app lock">
  <img src="img/garage-dark.png" width="23%" alt="The garage, dark">
  <img src="img/lock-dark.png" width="23%" alt="The app lock, dark">
</p>

<p align="center"><sub>A car that belongs to nobody: these are captures of the real UI with sample data, so no plate, no address and no location.</sub></p>

---

## What it does

- **Control** - lock and unlock, remote climate, charging limits, live location, full vehicle status, trips, weather.
- **On-device AI** - Gemini Nano summaries and natural-language commands, run on the phone. Nothing is sent anywhere for them.
- **AutoLock** - locks the car when you walk away from it.
- **Sync** - settings and photos follow you across devices through your own Google Drive.
- **Layouts** - adaptive for foldables and tablets, with reorderable pebbles, custom palettes and an animated background.
- **Multiple accounts**, signed in at once.

## Privacy

Credentials, service PINs, session tokens and the app-lock PIN all live in **one AES-256-GCM vault**,
whose key is sealed by the hardware-backed Android Keystore. Nothing leaves the device except requests
to the manufacturer's own servers, and the app collects no data at all.

**[Read the privacy policy](privacy.html)**

---

<p align="center"><sub>Unofficial. Not affiliated with, endorsed by, or operated by Hyundai Motor Company, Genesis, Kia, or any of their subsidiaries.</sub></p>
