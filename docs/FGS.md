# The foreground-service declaration

> **Not needed as things stand.** Bloo ships on Play's **testing tracks only** and is not submitted for
> production review, and this declaration is required for exactly one thing: submitting a build for
> **review**. Until that happens the form does not appear in the console at all — which is why it cannot
> be found — and nothing is blocked by its absence. This page is kept because it is correct and complete
> if that ever changes.

Play asks for this once, for any foreground-service type with policy attached. Bloo uses exactly one:
`connectedDevice`, on `autolock.AutoLockService`. Everything below is written to be pasted rather than
thought about.

Testing tracks do not need it; production does.

---

## What the app actually does

Bloo locks your cars when you walk away from them. The trigger is the car's Bluetooth disconnecting
from the phone. That disconnect starts `AutoLockService`, which runs the lock evaluation for that one
car, shows progress in the car's notification, and stops itself the moment the evaluation finishes.
Nothing stays running and there is no polling loop.

The facts Play's form is checking for:

| | |
|---|---|
| **Service type** | Connected device (`connectedDevice`) |
| **Class** | `com.bloo.bluelink.autolock.AutoLockService` |
| **Started by** | the car's Bluetooth disconnecting. `AutoLockBluetoothReceiver` is a **manifest-registered** receiver on `ACL_CONNECTED` / `ACL_DISCONNECTED`, so it wakes the app even when it is closed. |
| **Lifetime** | stops itself — `stopSelf()` when the evaluation reaches a terminal state. Not persistent. |
| **Notification** | one per car, progress plus a Stop action. `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` are all declared. |
| **Not used for** | location, media, microphone, camera, projection, data sync, or background polling. |

**Why starting it from the background is legitimate:** Android exempts a foreground-service start that
follows a Bluetooth broadcast from an app holding `BLUETOOTH_CONNECT`, which Bloo declares and uses for
exactly this. This is that case, not a workaround.

---

## The description — paste this

> Bloo connects to your car over Bluetooth to show its status and send commands such as lock, unlock
> and climate start. When that Bluetooth connection drops — you have walked away, or the car has gone
> to sleep — the app evaluates whether to lock the car for you, a feature the user switches on per car.
>
> That evaluation has to finish while the phone is idle and the app is closed, so it runs in a
> short-lived foreground service of the connected-device type, with a progress notification the user
> can see and stop. The service stops itself as soon as the evaluation completes. It is never used for
> location, media, or background polling.

---

## The video — 30 to 60 seconds, screen-recorded

Play wants to see the feature working, notification included. Six shots, in order:

| Time | Shot |
|---|---|
| 0:00–0:08 | The Bloo car screen, car connected, status showing. |
| 0:08–0:15 | Settings → that car → **AutoLock switched on**. Say: *"this is on for this car."* |
| 0:15–0:25 | Pull down the notification shade and hold it a beat: **no Bloo notification yet.** |
| 0:25–0:40 | Walk out of range, or turn the car's Bluetooth off. The **progress notification appears.** Linger here — this is the shot the reviewer is looking for. It names the car and says it is locking. |
| 0:40–0:50 | The notification resolves and **disappears on its own**. The car is locked. |
| 0:50–1:00 | Reopen the app: the car shows locked. |

**Keep out of frame:** plate, VIN, address, the car's location, and any personal notification content.
Battery saver on is fine and does not affect this service.

---

## Where it lives

**Play Console → Policy and programs → App content → Foreground service permissions.** Play sometimes
gives this its own task on the dashboard rather than burying it under App content, so check both.

One time only. It applies to every future release.
