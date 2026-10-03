# ht-dhirp

Kotlin library (and later Android app) for programming a Baofeng UV-5RM over a USB serial cable. See README.md and docs/PROTOCOL.md first.

## Rules that matter

* Read-modify-write only. Edit channel records; pass all other memory through exactly as the radio sent it.
* Never read or write radio addresses at or above `0xF000`. `CloneSession` enforces this; don't weaken it.
* Write whole regions in address order, never just changed blocks.
* Real radio images stay in `fixtures/local/` (gitignored). Never commit `.img` or `.bin` files outside `fixtures/synthetic/`.
* No tokens, keys or credentials in the repo or in chat.
* Commit identity is `htprog@highcentrality.com`; never use another personal address. Package namespace is `com.highcentrality.htdhirp`.
* Credit CHIRP (https://chirpmyradio.com) in the About screen and README.
* Protocol reference that has run on real hardware: a local Go tool, `../baofengCtrl/bfctrl.go` (not part of this repo). The CHIRP driver is `chirp/drivers/baofeng_uv17Pro.py`.

## Layout

* `core/`: pure Kotlin/JVM, no Android imports. Keep it that way so tests run on the desktop.
* Tests: `./gradlew :core:test`. `FakeRadio` (test sources) emulates the protocol; extend it rather than mocking.
