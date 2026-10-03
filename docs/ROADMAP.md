# Roadmap

Rough part-time estimates; protocol work against real hardware is the least predictable.

| Milestone | Scope | Estimate |
|---|---|---|
| **M0a** (now) | `core` codec + protocol, tested offline against a real `.img` and a fake radio | days |
| **M0b** | Android spike: `usb-serial-for-android`, open the cable, handshake, read a block on the owner's phone. Compare the full read with a desktop CHIRP read | a weekend |
| **M1** | Android app: read radio, list/edit channels, automatic backup, write-back with read-back verification | 1–2 weeks |
| **M2** | CHIRP-CSV import with a fit-and-preview step (name length, tones, free slots, target bank/range) | 1 week |
| **M3** | Repeater planner app: route to corridor to ordered repeater list, offline cache, CSV export | 2–3 weeks |
| **M4** | Second radio family (other Baofeng models sharing the protocol), beta with other hams | 2+ weeks |

## Decisions pending

* About screen: thank the CHIRP team and link to https://chirpmyradio.com (the CHIRP maintainers have been contacted; update this note with their reply).

* RepeaterBook API terms (registration, rate limits, bulk use) must be checked before the planner is designed around it. CSV import is the fallback.
* Slot policy for imported channels: leave `Location` blank and let the programmer pick free slots, with an option to confine writes to a range.

## Risks to retire early

* Cable chipset behaviour on the owner's phone at 115200 baud (prefer CH340 or FTDI).
* Write-path correctness: flash erase behaviour, partial writes. Always read back and compare after a write.
