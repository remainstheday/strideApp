# Getting a run off the console

Where a run can go, live and afterwards, and what each end can and cannot
do. Surveyed in November 2026 for a NordicTrack X24, an Amazfit Balance 2 and
an iPhone; the limits are the other devices', so they will move as those do.

## In one table

| | Live | Afterwards |
|---|---|---|
| **Watch → console** | Heart rate, over Bluetooth | |
| **Console → watch** | Nothing a watch will take | |
| **Console → phone** | Speed, incline, distance, time, heart rate (FTMS) | |
| **Console → Strava** | Nothing: Strava has no live feed | The whole run, uploaded |
| **Console → TrailMate → Garmin** | | The whole run, uploaded |

So the console is the recorder. It has the belt's true speed and the deck's
incline, which nothing on a wrist can measure, and it takes the heart rate
from the wrist. One run, recorded once, sent on when it ends.

## Heart rate from a watch

`HeartRate.kt` reads any device that offers the standard Heart Rate Service
(`0x180D`), and a watch broadcasting its heart rate is one.

- **Amazfit (Zepp OS):** *Heart Rate Push*. On the watch under Settings, or
  in the Zepp app under the device's health monitoring. Some models only
  broadcast while a workout is running on the watch.
- **Garmin:** *Broadcast Heart Rate*.

Then on the console: Settings → Devices → SCAN, and pair it as the heart rate
strap. From then on it connects by itself when it is in range and
broadcasting.

If the watch has to run a workout of its own to broadcast, that workout is a
second record of the same run. Discard it on the watch, or turn off the
watch app's own Strava upload, or Strava will have the run twice.

## Why nothing goes to the watch

A watch takes outside data in two ways, and the Balance 2 takes neither for
a treadmill:

- **As a sensor.** It pairs heart rate straps, cycling sensors and Stryd. It
  does not pair a generic running foot pod (`0x1814`), so the console
  posing as one would not be found.
- **As a fitness machine.** Its FTMS support is for rowers, and on some
  models bikes. Not treadmills, and nothing reads incline.

Its treadmill mode estimates distance from the arm and asks for the true
figure when the workout is saved. That is the only way in, and it is by hand.

A Zepp OS mini app could connect to the console and show its numbers on the
wrist. It could not write them into the watch's own workout: that record is
read-only to apps.

## To Strava

`Strava.kt`, `StravaTcx.kt`. When a run ends for somebody who has connected
Strava, it is written as a TCX file and uploaded as an indoor run:

- every second's time, distance, speed, heart rate and cadence;
- no position, so Strava draws no map and keeps the file's distance;
- altitude worked out from the incline, which Strava may or may not use for
  a run with no map, so the climb is also written in the description;
- named for the plan when the run had one, "Treadmill run" otherwise.

Guests are not recorded, so are not sent. Runs wait on the console until
Strava has them, and a run Strava already has counts as sent.

### Connecting

Strava signs people in with a web page, and has nothing for a device without
a browser. So it is done from a desktop, in Treadmill Manager (Builder →
Integrations → Strava), which sends the result to the console over adb
([REMOTE.md](REMOTE.md), the `strava` op).

Once, beforehand: make an API application at
[strava.com/settings/api](https://www.strava.com/settings/api) with its
*Authorization Callback Domain* set to `localhost`. Its Client ID and Client
Secret are what Treadmill Manager asks for. Strava ties these to a developer
account, wants a Strava subscription to make one, and lets a new application
upload for its owner alone.

The console keeps the id, the secret and a refresh token, sealed with a key
that never leaves it (`Vault.kt`). Settings → Connections on the console
shows who is connected and lets them be disconnected.

### Not yet tried against Strava itself

The file's shape and the upload are built to Strava's documentation and
tested as far as a test can go without an account. The first real upload
will settle three things the documentation leaves open:

- whether the altitude is kept for a run with no map;
- whether cadence shows right, since TCX carries strides and Strava doubles
  them;
- how Strava decides a run is a duplicate.

## To a phone, live

`Ftms.kt`. The console advertises as a Bluetooth treadmill (Fitness Machine
Service, `0x1826`), telemetry only: speed, average speed, distance, incline,
heart rate and elapsed time, five times a second. Nothing can drive the belt
through it.

On an iPhone, any app that reads FTMS treadmills can follow the run: Zwift
(speed only), QZ, Kinomap and several smaller ones. The Strava app itself
does not: it pairs heart rate sensors and nothing else. Apple Watch's own
*GymKit* needs Apple's certification and an NFC reader, so no console like
this one can offer it.

An app that follows the run over FTMS and then saves it has made a second
record. Let one of them send it to Strava, not both.

## To Apple Health

Nothing writes to Apple Health from here. The way round is through Strava:
an iPhone app that imports from Strava and writes workouts to Health (RunGap
and HealthFit are the usual two) carries the run across with its distance.
Not tried.
