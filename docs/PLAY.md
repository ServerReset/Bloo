# Publishing Bloo to Google Play

Everything the app and the CI need is in the repo. Everything that needs *your* account is in the
console, and is listed below as a checklist — none of it can be done from here, and none of it
should be: the Data safety form, the content rating and the "app access" answers are attestations
that only the account holder can make.

---

## 1. What ships, and what each build does

One codebase, two artifacts, selected by a `distribution` flavor:

| | `sideload` | `play` |
|---|---|---|
| Goes to | the rolling GitHub Release (`Bloo.apk`) | Google Play (an AAB) |
| Updates via | the CI-build check + its own APK installer (Shizuku where available) | Play's In-App Updates API |
| `REQUEST_INSTALL_PACKAGES` | yes | **no** |
| Shizuku permission | yes | **no** |
| `applicationId` | `com.bloo.bluelink` | `com.bloo.bluelink` |

They share a package name on purpose, so there is one app, one listing and one version sequence.
They do **not** share a signing certificate once Play App Signing is on — Play re-signs every uploaded
bundle with Google's app-signing key, so a Play-installed copy and a sideloaded copy are signed by
different keys and cannot update each other. Moving between them costs one uninstall, in whichever
direction. (This paragraph used to claim the two could move back and forth freely. That was true while
both were signed by the same key, and it stopped being true the day the first bundle was uploaded.)

The certificate matters beyond updates: the Wear OS Data Layer only connects apps that share both the
package name **and** the signing certificate. So there are two pairs that can talk to each other —
{Play phone, Play watch} on Google's key, and {sideload phone, sideload watch} on the upload key — and
crossing them fails silently. That is what the watch sitting on "Waiting for your phone…" was.

**But the artifact is not the whole story.** Play will happily *update* an app that was sideloaded
when the package name and signature match — and here they do. A copy installed from GitHub can
therefore become a Play-installed copy while still running the sideload build's code, which knows how
to update itself, and letting that happen is exactly what Play forbids.

So the decision is made at **runtime, from the installer**, in `UpdateRuntime`:

- **installed by Play** (`com.android.vending`) → `PlayUpdates` runs; the app's own update tile and
  installer stand down entirely, the CI-build check and its worker are skipped, and Play owns the
  download and the install.
- **installed by anything else** → the app's own updater runs, as it always has.

That is the one place either behaviour is decided. The flavor only decides what the *artifact* is
allowed to contain.

---

## 2. One-time console setup (you)

1. **Developer account** — play.google.com/console, the one-off $25.
2. **Create the app** — name "Bloo", the default language, "App" (not a game), free. The package
   name is claimed on the first upload, so it does not need to be typed now; make sure
   `com.bloo.bluelink` is not already taken by someone else.
3. **Play App Signing** — accept it. Google then holds the real app-signing key and yours becomes
   only an *upload* key, which means you can reset it from the console if it is ever lost. This is
   why losing the keystore below is survivable.
4. **Closed testing → Testers** — an email list of up to 100 Google accounts (or a Google Group, which
   has no cap). Testing tracks have **no review wait**, so a build is installable minutes after upload.

   The opt-in link is fixed by the package name, so nothing has to be generated for it:

   ```
   https://play.google.com/apps/testing/com.bloo.bluelink
   ```

   It is already linked from the Pages site and the README, which say access is by invitation — with an
   email list that link only works for accounts ON the list, so those pages must not imply otherwise.

   **The closed test is the one that counts:** a personal developer account needs **12+ testers opted in
   continuously for 14 days** before it can apply for production access, and *internal testing does not
   count toward that*. The CI uploads `major-` tag builds to this track (see section 4).

5. **A service account for CI** — Setup → API access → create/link a Google Cloud project → create a
   service account → grant it **Release to testing tracks** (and *Manage testing tracks* if you want
   the CI to edit the track) → create a JSON key. That JSON is `PLAY_SERVICE_ACCOUNT_JSON` below.

6. **Advanced settings → form factors → Wear OS.** The one thing the CI cannot do for itself, because a
   form factor is an app-level property with no API. Until it is set, the watch step warns and skips;
   the phone is unaffected. After it, the watch ships on every run with nothing further. See
   section 4.

7. **App content → Foreground service permissions.** Bloo uses one foreground service type,
   `connectedDevice`, for the walk-away auto-lock. The description and the shot list for the required
   30–60 second video are written out in **[FGS.md](FGS.md)** — it is a paste and a five-minute
   recording, not a form to think about. Testing tracks do not need this; production does.

---

## 3. The upload key

Generate it once, on your machine:

```bash
keytool -genkeypair -v \
  -keystore bloo-upload.jks \
  -alias bloo-upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype JKS
```

Then give the CI these repository secrets (Settings → Secrets and variables → Actions):

| Secret | What it is |
|---|---|
| `BLOO_UPLOAD_KEYSTORE_BASE64` | the whole `.jks`, base64'd: `base64 -w0 bloo-upload.jks` |
| `BLOO_UPLOAD_STORE_PASSWORD` | the store password you just chose |
| `BLOO_UPLOAD_KEY_ALIAS` | `bloo-upload` |
| `BLOO_UPLOAD_KEY_PASSWORD` | the key password (same as the store password unless you set another) |
| `PLAY_SERVICE_ACCOUNT_JSON` | the service-account JSON from step 5 above |

**Back the `.jks` up somewhere safe** (a password manager). It is not in the repo and never should
be — `.gitignore` blocks `*.keystore`, `*.jks` and `play-service-account.json` so it cannot be
committed by accident. If you do lose it, reset the upload key in the console; Play App Signing
means that is an inconvenience, not a disaster.

**There is no fallback.** A release artifact is signed with this key or it is not signed at all — the
build comes out unsigned, which installs nowhere and uploads nowhere, and the workflow fails before it
even starts rather than publishing one. It used to fall back to the checked-in debug key, and that is
exactly what produced a release signed with a certificate anyone can reproduce, and every install of
it becoming unupdatable. A build that cannot be signed correctly fails loudly now.

---

## 4. Cutting a release

An ordinary push is a **CI build**, not a release. It goes to GitHub as usual and does nothing else.

A **major** is a tag, and the tag is the trigger:

```bash
git tag major-1
git push origin major-1
```

That builds both artifacts, publishes the GitHub Release exactly as a normal push does (so sideload
users get the same build by the same route), and **additionally uploads the bundles to Play's closed
testing track** (the API calls it `alpha`).

**Every push goes somewhere on Play too**, which is the part worth knowing: an ordinary push publishes
both bundles to **internal testing**, rolled out, so there is always a build on a phone that is minutes
old. Only a `major-N` tag touches the **closed** track -- that is the one the 12-testers-for-14-days
clock runs on, and it should hold releases rather than every push. A manual run can name a track
(`play_track`), which is how a single build gets pushed somewhere on its own. Nothing is promoted to production by the CI — that stays a deliberate click in the
console.

### Form factors: the one thing the pipeline cannot set up for itself

The phone publishes itself with no involvement. The watch needs **one setting turned on once**, because
a form factor is an app-level property rather than something a build can declare.

Play names the setting itself, in as many words, when the upload tries to write to a form-factor track:

```
400 FAILED_PRECONDITION
"Form factor 'wear' is not configured. Configure this form factor in 'Advanced settings' page on Play
 Console."
```

**Play Console → Advanced settings → form factors → Wear OS.** Once, ever. There is no API for it, which
is the only reason the CI cannot do it.

After that, the upload creates the `wear:` tracks itself and the watch ships on every run next to the
phone, on whichever track the run is for. Until then the watch step warns and stops, and the phone is
unaffected — they are committed in separate edits, so one cannot roll the other back. The watch bundle
still uploads on every run regardless, so the store's bundle library always holds a current one.

Every upload prints the tracks Play has, at the start and again at the end. That is how you can tell
from a log alone whether the form factor is configured yet.

There is deliberately no semantic version. `versionCode` is the CI run number, which is monotonic by
construction (all Play requires), and `versionName` reads `major 3 · build 812` on a major and
`build 812` otherwise. The app is identified by which build it is; a major only says "this one is a
release".

---

## 5. The store listing (you)

Required before the app can go beyond testing. The icon, feature graphic, screenshots and
description text are all in **`play/`** — see `play/README.md` for the two format rules Play enforces
(8 bits per channel, and no alpha on screenshots; both bit the last set of captures).

**These are pushed from the repo, not typed into the console.** The **Play listing** workflow
(`.github/workflows/play-listing.sh` behind it) uploads everything in `play/` through the Play
Developer API: icon, feature graphic, phone screenshots, title and both descriptions. It runs with
`dry_run` on by default — a dry run does the whole thing inside an uncommitted edit, which is
invisible on the listing, so it proves the key and the permission without touching anything live.
Turn `dry_run` off to actually publish it.

To run it, the service account needs the app-level **Manage store presence** permission. "Release to
testing tracks" covers builds only; without Manage store presence every listing call returns 403.

- **App icon** — 512×512 PNG, 32-bit, no alpha.
- **Feature graphic** — 1024×500 PNG or JPEG.
- **Phone screenshots** — at least 2, 16:9 or 9:16, 1080p+. The garage with a car loaded is the one
  that sells it; a before/after of the widget is the second.
- **Short description** — 80 characters. e.g. *"Lock, climate, charge and live status for your
  Hyundai, Genesis or Kia — without the slow official app."*
- **Full description** — 4000 characters. Cover: multiple cars with a swipe, remote lock/unlock,
  climate and charge, the widgets, the watch app, the on-device AI summary, and that nothing leaves
  the device except what the car's own service needs.
- **Privacy policy URL** — required, and it must be a real page. It has to say what the app stores
  (the Bluelink credentials, encrypted on-device), that they go only to the manufacturer's own
  service, and how to delete everything (sign out, or Settings → delete data).
- **Data safety form** — this one needs care, because the app handles credentials:
  - *Personal info → Email addresses* — collected, not shared, encrypted in transit, required for
    the app's core function, deletable on request.
  - *Personal info → User IDs / other info* — the Bluelink account and its service PIN.
  - Everything else the app touches (location, trips, charge state) is either on-device or fetched
    from the manufacturer's API on the user's behalf.
  - Nothing is sold or shared with third parties. There are no ads and no analytics SDKs.
- **Content rating** — the questionnaire; the app has no user-generated content, no ads, no
  gambling, and no location *sharing*, so it lands at the lowest rating.
- **Ads** — no.
- **App access** — say that a Bluelink account is required and provide reviewer credentials in the
  "Instructions" box, or the review will be rejected for being un-testable.
- **"Not affiliated with Hyundai"** — put it in the full description. It is not a policy
  requirement, but it is the honest thing and it pre-empts the question.

---

## 6. Android developer verification (the registration Google is asking for)

Google now requires every Android app — **including the ones distributed outside Play** — to be
registered with its package name and the certificates it is signed with, for it to install on
certified devices in the participating countries. From 2027 it is global. This is an account-level
step in your Play Console (**Home → Android developer verification**, or the link in the email) and
it has to be done by you; what I can do is tell you exactly what to register.

**Package name — one entry covers everything:**

```
com.bloo.bluelink
```

The watch app deliberately shares it (see the wear module's `applicationId`), which is what makes the
Wearable Data Layer work at all, so there is one package to register, not two.

**Certificates — register both, because the two artifacts are signed by different keys:**

1. **The upload key** — the one you generate in section 3. Its fingerprint is what Play shows you
   under *App integrity*, and you can print it yourself any time with:

   ```bash
   keytool -list -v -keystore bloo-upload.jks -alias bloo-upload | grep SHA256
   ```

2. **The Play App Signing key** — Play Console → your app → *Test and release → App integrity →
   App signing key certificate*. **This is the one that matters for the Play build**, because that is
   the certificate the installed app actually carries; the upload key only signs what you send.

   Until you have enrolled in Play App Signing there is no second key, and registering just the
   upload key is right.

**What is currently signing the sideloaded builds** — the upload key from section 3. There is no
fallback to anything else any more:

```bash
keytool -list -v -keystore bloo-upload.jks -alias bloo-upload | grep SHA256
```

The checked-in debug key (`1D:38:A5:28:…`) still signs **debug** builds, which is all it is for, and
it no longer signs anything a user can install.

Note the practical consequence: **a key that changes is a key that must be re-registered.** Whatever
you sign with, register that fingerprint, and if you ever rotate the key, update the registration — a
build whose certificate is not registered will not install on certified devices, even by sideload.

---

## 7. The signing-key change, and why the old installs cannot update

Every Bloo build published before the upload key existed was signed with the **checked-in debug
key** — the standard Android development key, published in every copy of the SDK:

```
SHA-256: 1D:38:A5:28:CB:A7:1C:34:D2:B5:95:B1:60:19:DF:89:2A:EE:56:6C:57:03:7A:15:D5:B6:D7:C4:0E:59:39:B1
```

Android will not install an update signed with a different key than the installed copy, so the day CI
started signing with the upload key, **every install that predates it stopped being updatable**. The
symptom is not something anyone can act on: the download completes, the system installer says *"App
not installed"*, and it reads as a broken build rather than a signature mismatch. Nothing on the app's
side can work around it, and only uninstalling clears it.

**That can no longer happen, because there is no signing fallback left.** A release artifact is
signed with the upload key or it is not signed at all, and the workflow refuses to publish an
unsigned one (see the "Require the real signing key" step). The checked-in debug key is confined to
debug builds, and the watch module — which used to sign its releases with that same public key — uses
the upload key too. Changing the watch's key is itself a one-time break: a watch app installed from
an earlier build has to be uninstalled **on the watch** once before the phone can push the new one.

---

## 7b. Which build leans on what

One codebase, two builds, and the rule between them: **the Play build uses Google's services where they
are better, and the sideload build uses what is open and installable without them.** The flavor decides,
so the two cannot drift at runtime.

| | Play build | Sideload build |
|---|---|---|
| Updates | Play In-App Updates (`app-update-ktx`) | the CI-build check, its own installer, Shizuku |
| Watch install | Google Play installs it on the watch | the phone sideloads it over Wireless debugging |
| Non-SDK reflection | absent (`hiddenapibypass` is not in the artifact) | present, for the installer's `PackageInstaller` calls |
| Maps | Google Maps (once a key is supplied) | key-free OpenStreetMap tiles, hand-rolled (`CarMap`) |
| Location | Play services fused location | Play services fused location |

Two things are deliberately the same in both:

- **The AI.** Gemini Nano via ML Kit runs on-device and sends nothing anywhere. It is Google's library
  rather than an open one, and there is no phone-sized open equivalent, so splitting it would mean
  deleting a feature rather than replacing it. It stays in both.
- **The watch's data link.** The Wearable Data Layer is a Google service, and replacing it means
  Bluetooth or Wi-Fi Direct and a rewrite of everything that crosses between the two devices.

And one Google service is deliberately in neither: **crash reporting.** Firebase Crashlytics would be
the obvious pick, and it collects crash data -- which the app's privacy policy and its Play Data safety
answers both say it does not do.

## 8. How an install on the old key is told what to do

Those installs read the Releases list with a rule compiled into them that cannot be changed:

> the newest of the 10 most recent Releases that is not a draft and not a prerelease, whose tag
> starts with `build-`, and which carries a **ready** asset named exactly **`Bloo.apk`**

Two consequences, and they are why this is a text problem rather than a build problem:

1. **The asset name is what selects the release**, so `Bloo.apk` can only ever mean one thing — and
   it means the ordinary build. Those installs are therefore offered every new build, and every
   install of one fails.
2. **Only the first page is read** — ten releases. Anything older than ten pushes is invisible to
   them, whatever it says.

What does reach them is the text. The frozen code renders *our* release strings in two places, on
every build, with nothing to maintain:

| Surface | Comes from | What it says |
|---|---|---|
| the 12-hourly "update available" notification | the release **name** | *"Build #2950 · reinstall if the update won't install. Open Bloo to download and install."* |
| the update tile's headline | the release **name** | the same line |
| the update tile's "What's new" | everything after `### What's changed` | export first, uninstall, install the newest APK, sign in, restore |

Both are produced in `android.yml`: the "Pick the release title" step and the "Generate changelog"
step. The changelog paragraph is deliberately markdown-free, because that same string is the release
page *and* the tile body, and the tile renders it verbatim.

The **bridge** is the graceful path whenever it happens to be the newest release: an install on the
old key updates in place, and the app then walks the user through the export and the reinstall with a
button. It is published and immutable. **It can no longer be rebuilt** — the build mode that forced
the dev key is deliberately gone, because no build from this repo may be signed with the development
key again. If that release were ever lost, the text channel above still reaches everyone.

---

## 9. The two policy points to keep in mind

- **The self-updater must not reach the Play artifact.** That is what the `play` flavor and the
  runtime check above exist for. If a future change puts `REQUEST_INSTALL_PACKAGES` back into
  `src/main/AndroidManifest.xml`, the Play build inherits it and the submission is at risk.
- **`REQUEST_INSTALL_PACKAGES` is a restricted permission.** It is absent from the Play artifact by
  construction (see `app/src/sideload/AndroidManifest.xml`), so there is no declaration to file and
  nothing to justify.
