# Bloo

An independent client for Hyundai, Genesis and Kia Bluelink / Connected Car services.

## Installing an update

Bloo is signed with a new key as of October 2026. Android refuses to install an update signed with a
different key than the copy already on the phone, so a version installed **before** that change
cannot be updated in place — the download finishes and the installer says **"App not installed"**.
Nothing is wrong with the download.

Export your settings first (**Settings → Backup & sync → Export**), then uninstall Bloo, install the
newest **`Bloo.apk`** from the [releases page](https://github.com/ServerReset/Bloo/releases), sign in
again, and restore the backup. Everything keeps working until you do this; it just stops receiving
updates.

- [Privacy policy](privacy.html)

Not affiliated with, endorsed by, or operated by Hyundai Motor Company, Genesis, Kia, or any of
their subsidiaries.
