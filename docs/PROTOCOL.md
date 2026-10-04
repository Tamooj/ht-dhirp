# UV-5RM clone protocol and memory map

Target: Baofeng UV-5RM (CHIRP `BF5RM(UV17Pro)`, `baofeng_uv17Pro.py`).

## Provenance, and how far to trust each part

| Source | Confidence |
|---|---|
| A Go clone-protocol tool kept outside this repo (`bfctrl.go`) | Ran against real hardware: handshake, key selection, block read/write, crypt |
| Desktop-CHIRP `.img` of the owner's radio | Ground truth for the channel record layout (first records decoded by hand and matched) |
| CHIRP `baofeng_uv17Pro.py`, `baofeng_common.py`, `chirp_common.py` (CHIRP master, 2026-10-04) | **Read directly** (see "What we studied"). Facts below come from it; no code is copied |

## Link

115200 baud, 8N1, plain TTL cable (CH340 / FTDI preferred; counterfeit PL2303 is the usual trouble). The owner's cable enumerates on Windows 11 as a CH340 USB COM device (vendor ID `1A86`), which `usb-serial-for-android` supports.

## Handshake

| Step | Host sends | Radio replies |
|---|---|---|
| 1 | `PROGRAMBFNORMALU` (16 ASCII bytes) | `06` |
| 2 | `F` | 16 bytes (device info) |
| 3 | `M` | 15 bytes (model string) |
| 4 | `SEND` + 21 selector bytes (see `CloneSession.KEY_SELECT`) | `06` |

The key index is derived from the selector bytes: first byte `0x21` selects position 4, which holds `0x01`, so key index 1 (`"CO 7"`). This matches CHIRP's `_encrsym = 1` for the UV17Pro family.

## Blocks

64 bytes (`0x40`).

* Read: host `R hi lo 40`, radio replies `R hi lo 40` + 64 data bytes.
* Write: host `W hi lo 40` + 64 data bytes, radio replies `06`.

Data below `0xF000` is XOR-obfuscated per block with `WireCrypt` (cyclic 4-byte key; bytes `00`, `FF`, equal to the key byte, or whose XOR would be `FF` pass through; space key bytes pass through). It is its own inverse.

## Memory map

The radio address space is sparse. CHIRP reads four regions and packs them into a `0x8380`-byte image:

| Radio address | Size | Image offset | Contents |
|---|---|---|---|
| `0x0000` | `0x8040` | `0x0000` | 1000 channel records (`0x7D00` bytes), then VFO and settings from `0x8000` |
| `0x9000` | `0x0040` | `0x8040` | (unmapped by this project) |
| `0xA000` | `0x02C0` | `0x8080` | (unmapped by this project) |
| `0xD000` | `0x0040` | `0x8340` | (unmapped by this project) |

Channel record (32 bytes) is documented in `ChannelCodec.kt`. Names are 12 bytes, `0xFF`-padded. An empty slot starts with `0xFF`.

## Rules this project follows

1. **Read-modify-write.** The app always reads the radio first and edits only channel records. Everything else in the four regions is whatever the radio sent, and is written back byte for byte. We do not substitute CHIRP defaults or second-guess settings we don't own.
2. **Never touch `0xF000` and above.** Not read, not written. That sector holds calibration and region/regulatory settings; leave it exactly as the radio has it. `CloneSession` refuses any address outside the four regions.
3. **Whole regions on write, in address order.** The Go tool notes the flash erases when a block at a sector start (`0xF000`) is written. Assume the same for other sectors, so never write only the changed blocks.
4. **Always keep a backup** of the full image read from the radio before any write.
5. **Don't hard-block frequencies** using CHIRP's stock band table; the radio's real limits may differ. Warn, don't refuse.
6. **Out of scope:** changing region or regulatory settings.

## Field notes (CHIRP wiki, UV-5R / UV-82 page)

Mostly about the classic UV-5R and UV-82, so treat as hints for the 5RM, not guarantees:

* Seat the two-pin programming plug fully; some cables only work when pushed in hard, and some radio cases need the plug trimmed.
* The radio must be on, and the antenna off or the radio on a quiet channel: active signals can prevent connection.
* **The radio may key its transmitter when communication goes wrong.** The app should tell users to connect a dummy load or leave the antenna off, and never leave a session half-finished.
* Counterfeit Prolific chips are common in cheap cables. (Ours is a CH340.)
* On Windows each USB port gets its own COM number; irrelevant on Android.
* Classic UV-5R firmware generations (BFB290 vs BFB291+) use incompatible clone formats. Newer UV17Pro-family radios like the 5RM use the format in this document. Check for firmware-specific variants before adding other models.

CHIRP developer entry points: <https://chirpmyradio.com/projects/chirp/wiki/Developers> (see "Add a Radio" and the tone-modes notes), and the code at <https://github.com/kk7ds/chirp>.

## What we studied (CHIRP, for credit and provenance)

Read on 2026-10-04 for ideas and gotchas only; nothing was copied: `chirp/drivers/baofeng_uv17Pro.py` (the `UV17Pro` base class, `BF5RM`, `BaofengUV5RMPlus`, `_do_ident`, `_download`, `_upload`, `decode_tone`/`encode_tone`, `get_memory_common`/`set_memory`, `split_txfreq`), `chirp/drivers/baofeng_common.py` (I/O helpers, `_is_txinh`, timeouts) and `chirp/chirp_common.py` (`DTCS_CODES`, character sets, `is_split`).

## Gotchas found in CHIRP's driver (and what we did about them)

* **1000 channels, not 999.** `CHANNELS = 1000`; slot 999 (zero-based) is valid. Fixed.
* **TX-off has two encodings.** An all-`FF` *or* all-`00` TX frequency means no transmit. We now read both, and leave an existing record's encoding alone unless the value changes.
* **Three power levels on the 5RM:** 0 = high (8 W), 1 = low (1 W), 2 = medium (5 W); out-of-range reads as high. Other family members differ (UV-17Pro: 5 W / 1 W; UV-28Plus: 10 / 2 / 5).
* **Name character set** is letters, digits, space and ``!@#$%^&*()+-=[]:";'<>?,./`` only (12 chars). No `_`, `\`, `|`, `~`, braces or backtick. CHIRP shows `FF`/`00` name bytes as spaces. We reject other characters on write.
* **DCS table** is CHIRP's 104 standard codes plus `645`, sorted (105 entries), which is why the normal range tops out at `0x69`. Implemented as `DtcsCodes`.
* **New channel records** start as 16 zero bytes then 16 `FF` bytes, with scan on. Our blank record matches. (CHIRP overwrites the whole record on every edit; we deliberately preserve unmodelled bits instead.)
* **Key selection reply is not checked by CHIRP**, only that one byte arrives; the handshake ack must start with `06`. We now do the same and expose the value as `DeviceInfo.keySelectAck`.
* **Timeout:** CHIRP uses 1.5 s and flushes with a 5 ms read of up to 256 bytes first. Our default is now 1.5 s.
* **Several radios share the "5RM" name or protocol but differ in handshake.** Ident strings: `PROGRAMBFNORMALU` (UV-17Pro family and the 5RM), `PROGRAMCOLORPROU` (UV-17Pro GPS, UV-5R Mini), `PROGRAMBFGMRS05U` (GM-5RH), `PROGRAMBF5RTECHU` (BF-F8HP-PRO), `PROGRAMBF-K6PROU` (K6, UV-5RH). The **UV-5RM Plus** uses a different key-selection message and key index 13 (`WireCrypt` derives 13 from it; covered by a test). Rebadges of the 5RM include MaxTalker P15 and MT-5RM, and K5-Plus. `RadioVariant` holds the ident and key-selection message; only `UV5RM` is hardware-verified. Do not add others without a real radio to test.
* **No retry or resume** in CHIRP's clone; a partial failure just aborts. We should add read-back verification after write.
* **CHIRP asks users to save an unedited copy of the first successful download.** Our backup rule covers this.
* **CHIRP's instructions:** turn the radio **off**, connect the cable, then turn the radio **on**, then start. A missing reply is reported as "no contact, likely K1 connector": check the plug is fully seated.
* **Older `.img` files** end with the model name instead of the JSON trailer (`model_match`). `ChirpImageFile` parses both only if the JSON trailer exists; old-style files are treated as raw data with no metadata, which is acceptable for now.

## Open items

* Sector-erase behaviour for the three non-channel regions is still unknown (CHIRP simply rewrites everything it read). Whole-region writes remain the rule.
* Whether a channel-only change needs the three small regions (`0x9000`, `0xA000`, `0xD000`) rewritten at all.
* `sqmode`, `scramble`, `fhss`, `scode`, `pttid` semantics are exposed raw.
* Confirm the narrow-FM bit sense on hardware (bit set = narrow, per CHIRP; FRS entries in the owner's image have it set).
* Whether the first 5 records' high-order flag bits matter on other firmware revisions.
