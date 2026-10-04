# ht-dhirp

Program ham handheld radios from an Android phone over a USB-OTG serial cable. Target radio: **Baofeng UV-5RM**. The goal is a small, focused tool, not a CHIRP replacement.

## Plan

Two apps joined by a CSV file in CHIRP's CSV format:

1. **Repeater planner**: pick a route, find repeaters along it (RepeaterBook or imported lists), export CSV.
2. **Radio programmer** (this repo): read the radio, import that CSV into free channel slots, preview what fits, write back.

The CSV is the contract, so the programmer also works with CHIRP exports and club lists.

## Status

Scaffold only. `core/` is a pure Kotlin/JVM library with no Android dependencies:

* `ChannelCodec`, `RadioImage`: 32-byte channel records, edit-in-place
* `WireCrypt`, `CloneSession`, `SerialTransport`: the clone protocol, ported from a Go implementation that was verified against real hardware
* `ChirpImageFile`: CHIRP `.img` container with its metadata trailer

Tests run against a `FakeRadio` that speaks the protocol, plus golden records from a real CHIRP read. See [docs/PROTOCOL.md](docs/PROTOCOL.md) for the protocol, the safety rules, and open items, and [docs/ROADMAP.md](docs/ROADMAP.md) for what is next.

## Build and test

Needs JDK 17 or newer (JDK 25 also works; the Kotlin target is pinned to 17). The Gradle wrapper is checked in:

```bash
./gradlew :core:test
```

## Android spike (M0b)

`app/` is a minimal, **read-only** Android app that connects to the cable, does the handshake, reads the radio, and saves a CHIRP-compatible `.img` to Downloads. Build it with:

```bash
./gradlew :app:assembleDebug   # needs the Android SDK (ANDROID_HOME)
```

The APK lands in `app/build/outputs/apk/debug/`. The phone's one USB-C port is busy with the programming cable, so install either by copying the APK to the phone, or over Wi-Fi with Android's *Wireless debugging* (`adb pair`, `adb connect`, then `adb install -r app-debug.apk`).

Safety before testing: radio on, plug seated fully, antenna off or a dummy load attached (a radio can key its transmitter if communication goes wrong).

## Fixtures

Real radio images contain personal channel lists and are never committed. Put a CHIRP `.img` in `fixtures/local/` (gitignored) or point `HT_FIXTURE_IMG` at one. Tests that need it are skipped when it is absent. Synthetic fixtures go in `fixtures/synthetic/`.

## Safety rules

* Read the radio before writing; keep the backup.
* Only channel records are edited; all other memory is passed through exactly as the radio sent it.
* Never read or write `0xF000` and above.

## Acknowledgements

This project stands on the work of the [CHIRP](https://chirpmyradio.com) team and community. CHIRP's radio drivers document the memory layouts and clone protocols that make a tool like this possible, and its CSV format is the interchange format used here. Please use and support CHIRP on the desktop. The app's About screen will credit CHIRP and link back to https://chirpmyradio.com.

## License

GNU GPL v3 (see [LICENSE](LICENSE); acknowledgements in [NOTICE](NOTICE)). The protocol knowledge here comes partly from CHIRP's GPLv3 drivers, and the goal is to release these tools freely to the ham community.
