# Peugeot 5008 FrontCamera v13 handler probe

Date: 2026-09-22

## Why v13

The v12 live-car transition test reached its 7-second completion path, but usable front-camera video did not appear. The next diagnostic split is therefore:

1. identify the actual Activity that handles `com.reglink.action.FrontCamera`;
2. compare an implicit action launch with an explicit resolved-component launch;
3. only then repeat the R-exit path using `isReversing() true -> false`.

This avoids returning to the already-unreliable `gear_d` path.

## Build

- branch: `build/frontcam-v13-handlerprobe`
- package: `kr.song.peugeot.frontprobev13`
- versionCode: `13`
- versionName: `13.0-handler-probe`
- minSdk: 21
- targetSdk: 27
- GitHub Actions run: `35684710173`
- conclusion: success
- APK SHA-256: `efaa3dbc832f028506acb19376ef480a5052d3cb488df96bbb25446778787273`
- signing: APK v1 PASS / v2 PASS, Android Debug signer

## Probe functions

- Query `com.reglink.apps.camera`: version, sourceDir, system-app flags, declared activities.
- Resolve and enumerate handlers for `com.reglink.action.FrontCamera`.
- Manual implicit FrontCamera one-shot.
- Manual explicit resolved-handler one-shot.
- Live R-exit test: bind existing `DroidCarService`, use `ICarService.isReversing()`, wait 300 ms after true -> false, then re-request the explicit FrontCamera handler about every 850 ms for up to 7 seconds.

## No vehicle-setting writes

- no CANBox write/rawWrite
- no SharedVar set
- no FrontGearStart change
- no profile/decoder/MCU change

## Live test order

1. With the vehicle stopped and parking brake applied, run **1. handler/camera app query** and save the log.
2. Run **2. implicit one-shot** and note: front image / black-no-signal / immediate return / no visible change.
3. Refresh and save the log.
4. Run **3. explicit one-shot**, observe the same four outcomes, then refresh/save the log.
5. Run **4. P -> R (3-5 s) -> D**, observe the same outcome and save the complete log.

Do not merge this experimental branch into main until the live result is classified.
