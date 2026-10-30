# Changelog

Dated, newest first. The privacy policy keeps its own dated log of the changes that touch your data.

## Unreleased

### 30 October 2026

**Security**
- The app no longer has its own PIN. It unlocks with the phone's own lock: fingerprint, face, PIN, pattern or password.
- The watch no longer has its own PIN either. Its own screen lock holds it after a restart, and commands run as they are tapped. The phone's "Ask the watch for my PIN" setting is gone.
- Sign-in credentials, car service PINs and session tokens are in a vault sealed by a key that needs the phone to be unlocked. That key stays usable for 30 days after each unlock, and it survives a new fingerprint being enrolled.
- Background work (status, alerts, AutoLock, the watch) keeps using a second vault sealed by a key that needs no unlock.
- An app PIN and its lockout counters from an older version are deleted the first time this version opens the vault.
- After a restart, signing in asks for your phone's lock before it checks your details, because the sign-ins stay locked until then.
- The sign-in vault is not replaced when it is locked. A save to a locked vault is refused, and the file is left as it was.

**Map and watch**
- Map: each car's pin shows its photo, and tapping a pin selects that car. Cars that sit together share one pill of photos.
- Map: the top bar folds to a handle or a single line, dismisses from the line, and grows on one timeline.
- Map: a single tap on another car switches the bar and centres the map. Expanding a car selects it.
- Map: a pinch across a zoom step keeps the previous zoom's tiles on screen, so the map does not go blank.
- Watch: swipe up and down between cars, and left and right through each car's sections. The watch remembers where you were.
- Watch: charging shows on the watch face and in Recents, as a live ongoing activity.
- Map: a car that leaves the screen springs from its place to the edge, and fades; coming back reverses it. Off-screen pins take no taps, so a tap on a visible car reaches that car.
- Map: the previous zoom level stays under the map until the new level's tiles have arrived, within 3 seconds at most. Tiles are kept for 256MB (up from 100MB) and kept past the server's expiry, so a map looked at recently draws offline.
- Climate: a preset edits in place under its own name, with no pop-up, and saving a preset names it under the controls. While climate runs, the presets and controls fold away to a read-only summary, which folds back when climate stops. The seats are full-width sliders.
- Smart climate: the target weighs humidity, wind, day or night, the next few hours, and your past settings, and says why it chose its target.

**Shared code**
- Shared UI moved to `uicommon`: the photo tile, the status card, the pull-to-refresh state and its over-pull rule, the page dots, the list merge and the displayed-items helper.
