# Peugeot FM — Local Wi-Fi direct connection

This branch does not use Peugeot VPS. No changes to AASink, CANBOX, camera, FOCAL amplifier or OEM head unit configuration.

## Apps
- **Peugeot FM AndroidAuto** (install on the secondary phone). Implements Android Auto media browsing with FM presets and previous/next buttons.
- **CMZX FM Bridge** (install on Android head unit). Runs a local network listener at port 38765; responds to a one-time pairing PIN and accepts authorized FM commands over the local wireless network.

## Setup
1. Install CMZX FM Bridge on head unit, open app, write down a private IPv4 address and generated 6-digit PIN.
2. Connect phone to wireless Android Auto normally.
3. On secondary phone install Peugeot FM AndroidAuto. Enter the headunit IPv4 address in the phone app.
4. Tap “Wi-Fi connection test”. It must return the CMZX /health response. Being paired over Bluetooth is not sufficient for an arbitrary third-party IP connection.
5. Enter PIN, tap Pair.
6. Tap “FM Binder status”. A positive result must report IRadioService. If binder is missing or blocked, a normal APK cannot control it without a supported privileged bridge.
7. In Android Auto media launcher choose Peugeot FM and a station. Test only while parked.

## Important limitations
- Android Auto may not show a sideloaded media app unless developer settings permit unknown sources; compatibility varies by phone/AA version.
- Android Auto MediaBrowserService is designed for media providers. This build exposes local FM commands but does not itself stream audio from the tuner through the phone; eligibility and audio focus coexistence require real vehicle tests.
- Headunit's Android Auto receiver may continue to mute physical FM while projected audio is active. A network bridge does NOT solve FM/AA audio mixing alone.
- Reglink Binder transactions and audio routing are reverse-engineered and must be verified on the actual CMZX hardware. No success claim before test.
- Local HTTP only works on a mutually routable private Wi-Fi segment; some wireless AA implementations isolate clients or route phones differently.
- The generated pairing code and random bearer token are not committed to GitHub. Local commands are authenticated but plaintext on the local link; only use the trusted AA private Wi-Fi network.
- The FM application is not intended for public Play distribution without adherence to current Android Auto media app rules.

## Diagnostics
- Wi-Fi probe failed: inspect Wi-Fi network interface IP and routing of the head unit; do not change vehicle CAN settings.
- Binder NOT_FOUND: system/privileged permission issue; gather logcat for the bridge package.
- Wi-Fi + binder OK but audio silent: AASink audio focus/arbitration issue; investigate audio focus independently.
- Seek/Tune error: inspect CMZX Binder transaction and frequency scaling (frequency in 10 kHz units).
