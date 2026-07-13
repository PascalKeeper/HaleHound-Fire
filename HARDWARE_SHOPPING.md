# Hardware shopping map — Jesse shop vs Temu / salvage

Budget + earth edition for pairing a first **CYD radio edge** with **HaleHound Fire** (brain).

**Live catalogs (check before buy — stock moves):**

| Shop | URL | What it is |
|------|-----|------------|
| **Arsenal / parts** | [halehound.com](https://halehound.com) → Collections | CYD boards, DIY kits, cases, Cinder Ferret companion boards |
| **Apparel only** | [merch.halehound.com](https://merch.halehound.com) | Tees, hoodies, socks, mugs — **not electronics** |
| **Flash free** | [flash.halehound.com](https://flash.halehound.com) | Official firmware — browser flash |
| **Build PDF** | [Build Guide v1.4.1](https://halehound.com/cdn/shop/t/4/assets/HaleHound-CYD-Build-Guide-v141.pdf) | Wiring / modules / power |
| **Firmware BOM** | [HaleHound-CYD README](https://github.com/JesseCHale/HaleHound-CYD) | Required modules list |

Prices below snapped from the live shop catalog (~2026-07-12). Shipping/tax extra. Always re-check the product page.

---

## TL;DR for you

| Path | Ballpark | Vibe |
|------|----------|------|
| **A — Comp Jesse (full DIY kit)** | **$139** (2.8") / **$149** (3.5") | One cart, known-good modules, case, harness, power — supports the dev who builds the firmware you flash |
| **B — Hybrid (recommended thrifty)** | **~$40–70** + solder time | Board from Jesse *or* Temu · radios/GPS/NFC from Temu/Ali · case free STL / Jesse case · **Aruba RP-SMA salvage for 2.4** |
| **C — Bare minimum Fire pair** | **~$12–25** | Just a supported CYD + data USB + microSD · flash · softAP · Fire telemetry/loot works **without** CC1101/NRF/PN532 |

**Apparel merch does not buy you a radio.**  
**Stickers are cool; they don’t probe 192.168.4.1.**

---

## What’s on halehound.com right now (hardware-ish)

### Full DIY kits (the “comp us bro” route)

| Product | Price | Stock* | What’s in the box (shop listing) |
|---------|-------|--------|----------------------------------|
| **[DIY 2.8 HaleHound KIT](https://halehound.com/products/diy-halehound-kit)** | **$139** | In stock | 3D case (holds NTAG216), **2.8" E32R28T exposed SPI CYD**, **E07 CC1101**, **E01 NRF27sx**, wiring harness 8+12 pin JST, perf board, **PN532**, female JST PCB connectors, **ATGM336H GPS + ceramic ant**, boost converter, **3.3V buck**, M3×16 hardware |
| **[DIY HaleHound 3.5" kit](https://halehound.com/products/diy-halehound-3-5-kit)** | **$149** | In stock | Same idea with **3.5" E32R35T** + case; listing notes shipping cost bump 4/25 |

\* `available=true` on products.json at last check.

**Why kits win even if Temu is cheaper part-by-part:** correct **exposed SPI** board variant, harnesses that match Jesse’s pinout, **independent 3.3V buck** (required for PA modules — README screams this), GPS that actually fits the case story, fewer “wrong pin / wrong CYD clone” tears.

### Boards only (start small)

| Product | Price | Notes |
|---------|-------|-------|
| **[2.8" CYD E32R28T Exposed SPI](https://halehound.com/products/2-8-cyd-e32r28t-exposed-spi)** | **$15.25** | Official flash target `esp32-e32r28t` · best “first Fire pair” board from Jesse |
| **[3.5" CYD Exposed SPI](https://halehound.com/products/3-5-cyd-exposed-spi)** | **$18.00** | `esp32-e32r35t` · bigger UI |

### Cases (Jesse / collabs)

| Product | Price | Notes |
|---------|-------|-------|
| **CASE 2.8"** | $30 | Pick variant for **E32R28T** or classic **ESP32-2432S028R** |
| **3.5" CASE!** | $30 | PETG for E32R35T |
| Slimer / 90’s / Sasquatch Bone Pile / Sasquatch 3.5 case | $30–45 | **Often sold out** (limited drops) |

### Companion boards (less soldering)

| Product | Price | Notes |
|---------|-------|-------|
| **[Cinder Ferret](https://halehound.com/products/cinder-ferret)** | **$30** | Plug-and-play for **CC1101 + NRF24**, soldered + harnesses |
| **[Cinder Ferret V2](https://halehound.com/products/cinder-ferret-v2)** | **$40** | + **ATGM336H GPS** on board (1.25 mm JST), soldered, harnesses for 3 modules — Midwest Gadgets collab |

### Not electronics

Stickers ~$6 · DefCON SAO (sold out) · merch.halehound.com = clothes/mugs only.

---

## What firmware actually needs (official BOM)

From [HaleHound-CYD README](https://github.com/JesseCHale/HaleHound-CYD):

| Module | Role | Fire pair? |
|--------|------|------------|
| **CYD board** (supported list) | Brain of the edge | **Required** for any companion work |
| **CC1101** (HW-863 or E07-433M20S) | SubGHz | Optional later |
| **NRF24L01+PA+LNA** (or E01) | 2.4 GHz external | Optional later |
| **PN532 V3 SPI** | NFC/RFID | Optional later |
| **GPS** (GT-U7 / NEO-6M / ATGM336H) | Wardrive / geo | Optional later |
| **5V→3.3V buck (500 mA+)** | Independent VCC for Ebyte PA modules | **Required if you use E07/E01 PA** |
| MicroSD FAT32 | Loot / .sub / wardrive | **Strongly recommended** (Fire can offload later) |
| 10 µF on NRF24 VCC | Stability | Cheap insurance |

**WiFi + BLE softAP features use the ESP32 onboard radio** — no external module required for Fire to PROBE / pull loot over softAP.

---

## Path A — “Comp Jesse” (one box)

**Buy:** DIY 2.8 kit **$139** *or* DIY 3.5 kit **$149**.

**You still need (usually not in kit listing):**

| Item | Source |
|------|--------|
| Data-capable USB cable (not charge-only) | Junk drawer / Temu |
| MicroSD ≤32 GB FAT32 (loot) | Temu / thrift |
| CH340 driver on flash PC | Free from WCH |
| Desktop Chrome/Edge for [flash.halehound.com](https://flash.halehound.com) | Free |
| Optional: Aruba **802.11b/g** RP-SMA for better softAP range | **Trash can (you)** + U.FL pigtail from Temu — see [HARDWARE_ANTENNA.md](HARDWARE_ANTENNA.md) |

**Skip Temu radios** if kit already has E07 + E01 + PN532 + GPS + buck.

---

## Path B — Hybrid thrift (Earth + wallet)

### Buy from Jesse (high leverage)

1. **2.8" E32R28T Exposed SPI — $15.25** (or 3.5" $18)  
   *Why not pure Temu for the board?* Wrong clones, capacitive vs resistive, **no exposed SPI**, pin maps that don’t match firmware. Jesse’s listing matches flash targets.

2. Optional later: **Cinder Ferret ($30)** or **V2 ($40)** if you hate wiring harness chaos.

3. Optional: **CASE 2.8" $30** *or* free community STL ([Printables HaleHound modular cases](https://www.printables.com)) if you have a printer / library printer.

### Outsource (Temu / Ali / Amazon / scrap)

Search terms that match the README (verify photos + pin headers):

| Need | Search / part | Notes |
|------|---------------|-------|
| SubGHz | `CC1101 HW-863` or `E07-433M20S` | E07 = PA = **must** have separate 3.3V buck |
| 2.4 external | `NRF24L01+ PA LNA` or `E01-2G4M27SX` | 10 µF across VCC/GND |
| NFC | `PN532 Elechouse V3` + set DIP **SPI** | CH1 OFF, CH2 ON per README |
| GPS | `ATGM336H` or `GT-U7` / `NEO-6M` | Ceramic antenna OK |
| Power | `AMS1117 3.3` or better **buck 5V→3.3 ≥500 mA** | **Do not** starve PA off CYD 3.3V rail |
| Boost | `MT3608` or similar for battery builds | Kit includes one |
| Wiring | Dupont / JST 1.25 / 2.0 kits | Measure what your board headers want |
| Antenna | `U.FL to RP-SMA bulkhead pigtail` **short** | Screw on your **b/g** Aruba salvage |
| SD | MicroSD FAT32 | Empty folders are fine |

**Rough DIY-parts cart (no case, no Cinder):** ~$25–55 on Temu depending on shipping luck + whether you buy E07 PA vs cheap HW-863.

### Salvage (you)

| Salvage | Use |
|---------|-----|
| Aruba **802.11b/g** or dual-band RP-SMA | CYD external 2.4 after U.FL mod/pigtail |
| Aruba **802.11a-only** | Shelf — not ESP32 Wi‑Fi |
| Random 5V USB bricks | Bench power only if solid |
| Perfboard / wire / screws from old PCs | Build aesthetics |

---

## Path C — Absolute minimum to pair with Fire tablet

Goal: softAP + web surface so Fire **CYD tab** / `cyd status|loot|pull` works.

| Item | ~$ | Source |
|------|-----|--------|
| Supported CYD (E32R28T preferred) | 15–25 | **Jesse $15.25** or careful Temu “ESP32-2432S028” classic |
| Data USB cable | 0–3 | Drawer / Temu |
| Flash from PC once | 0 | flash.halehound.com |
| Optional microSD | 3–8 | Temu |

**You do not need** CC1101, NRF, PN532, or GPS for Fire brain-plane telemetry/loot over Wi‑Fi.  
Those modules unlock SubGHz / MouseJack / NFC / wardrive **on the edge** later.

---

## merch.halehound.com (Fourthwall)

Tees ~$25–28 · hoodie ~$50 · socks/mugs ~$15 · hats ~$32.

**Support channel** if you want to throw the creator a bone **without** another radio.  
**Does not replace** a CYD order on [halehound.com](https://halehound.com).

---

## Decision tree (money mode)

```
Need Fire ↔ CYD link this month, max thrift?
  → Path C: E32R28T $15.25 (Jesse) + cable + flash
  → Aruba b/g later for range

Want full arsenal without shopping 12 Temu listings?
  → Path A: DIY 2.8 kit $139 (or 3.5 $149)
  → "comp us bro" + least frustration

Like building, hate wrong clones?
  → Path B: Jesse board $15–18
       + Temu radios/GPS/NFC/buck
       + free STL case or Jesse case $30
       + Cinder Ferret $30–40 if wiring is the boss fight

Have 3D printer + iron + patience?
  → Path B hardcore: Temu CYD only if photos match
       E32R28T / 2432S028 / E32R35T flash targets
```

---

## Fire tablet side (you already own)

| Need | Status |
|------|--------|
| HaleHound Fire app | Installed (v1.1.0 telemetry + vault) |
| SoftAP join / same LAN | Your field process |
| External antennas on Fire | **No** — antennas go on CYD |
| Laptop in field | **Not required** after flash |

---

## Ethics / reality check

- HaleHound firmware is multi-protocol and includes **offensive** modules gated by VALHALLA.  
- This companion project stays **Blue Team / authorized** on Fire identity.  
- Buy / build only for **owned gear, written scope, lab/CTF**.  
- Salvage antennas = good planet; illegal TX = bad planet.

---

## Suggested first order (if you ask me to pick)

**Thrifty + supports Jesse a little:**

1. **2.8" CYD E32R28T Exposed SPI — $15.25** on halehound.com  
2. MicroSD + data USB from Temu / junk  
3. Flash → join softAP → Fire `cyd status`  
4. When cash allows: **Cinder Ferret V2 $40** *or* Temu CC1101+NRF+buck, then full kit later if you fall in love  
5. Screw **b/g Aruba** onto U.FL→RP-SMA pigtail when you’re ready ([HARDWARE_ANTENNA.md](HARDWARE_ANTENNA.md))

**One-and-done / less tears:** DIY 2.8 kit **$139**.

---

*I am not Jesse; product lines and stock change. Double-check [halehound.com/collections/all](https://halehound.com/collections/all) before paying. Comp the creator when you can — firmware doesn’t write itself.*
