# UV-5RM clone protocol and memory map

Target: Baofeng UV-5RM (CHIRP `BF5RM(UV17Pro)`, `baofeng_uv17Pro.py`).

## Provenance, and how far to trust each part

| Source | Confidence |
|---|---|
| A Go clone-protocol tool kept outside this repo (`bfctrl.go`) | Ran against real hardware: handshake, key selection, block read/write, crypt |
| Desktop-CHIRP `.img` of the owner's radio | Ground truth for the channel record layout (first records decoded by hand and matched) |
| CHIRP driver details quoted below | Taken from a summarising fetch of the driver source, **not yet read directly**. Re-check before relying on them |

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
| `0x0000` | `0x8040` | `0x0000` | 1000 channel records (999 used) then VFO / settings start |
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

## Open items

* Re-read `baofeng_uv17Pro.py` directly and confirm: region table, sector erase behaviour for the three non-channel regions, whether `0x8040–0x9000` etc. need reading at all for a channel-only write.
* DTCS code table: `Tone.Dcs` stores an index only; add the standard 104-code list from CHIRP.
* `sqmode`, `scramble`, `fhss`, `scode`, `pttid` semantics are exposed raw.
* Confirm the narrow-FM bit sense on hardware (bit set = narrow, per CHIRP; FRS entries in the owner's image have it set).
* Whether the first 5 records' high-order flag bits matter on other firmware revisions.
