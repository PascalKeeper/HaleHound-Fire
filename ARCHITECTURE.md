# Architecture: Fire as debug plane, CYD as radio edge

## Why this port exists

The **ESP32 CYD** is an excellent multi-radio *edge* device, but it is intentionally constrained:

| Constraint | CYD reality | Fire tablet advantage |
|------------|-------------|------------------------|
| **Storage** | ~MB flash + optional microSD | GBs free for logs, captures, builds, notes |
| **Compute** | Dual-core MCU, tight RAM/heap | AArch64 tablet SoC, background apps, larger UI |
| **Display / UX** | 240×320 or 320×480 TFT, touch menus | 7″ 1024×600, readable logs, multi-pane debug |
| **I/O** | UART/SPI radios, limited USB host | USB host, ADB, files, browser, keyboard/BT |
| **Dev loop** | Rebuild firmware, reflash, serial | Sideload APK, iterate UI/logic without reflashing radios |
| **Radios** | Wi‑Fi TX/RX raw, BLE, + CC1101/NRF24/PN532/GPS | **No** monitor-mode / SubGHz / NRF24 — must stay on CYD |

**Porting to Fire is not “run HaleHound firmware on Amazon hardware.”**  
It is: **use the Fire as the large brain / debugger / loot store / UI shell**, while the **CYD remains the radio front-end**.

That is the correct long-term split for debugging and field work.

```
                    AUTHORIZED LAB / RED TEAM SCOPE ONLY
  ┌─────────────────────────────────────────────────────────────┐
  │  FIRE TABLET  —  control · debug · storage · analysis       │
  │  • Live telemetry / serial mirror                           │
  │  • Loot browser (handshakes, .sub, wardrive CSV)            │
  │  • Session notes, screenshots, export                       │
  │  • Local Blue Team Guardian (OS sensors)                    │
  │  • Future: filter logs, map, replay analysis UI             │
  └───────────────────────────┬─────────────────────────────────┘
                              │ Wi‑Fi / HTTP / BT SPP / USB OTG
                              │ (softAP or same LAN)
  ┌───────────────────────────▼─────────────────────────────────┐
  │  CYD + MODULES  —  radios · TX/RX · frame-level tools        │
  │  • WiFi arsenal, BLE, SubGHz, NRF24, NFC, GPS               │
  │  • Jam Detect / WiFi Guardian (real Dot11 frames)           │
  │  • Small UI for standalone field use                        │
  │  • Streams status + loot upward when paired                 │
  └─────────────────────────────────────────────────────────────┘
```

## Role definitions

### Layer EDGE — HaleHound CYD (official firmware)
- Own all **transmit** and **raw capture** paths that need special radios.
- Stay lean: do the attack/detect work, push artifacts out.
- Keep a usable touch UI when no companion is present (standalone kit).

### Layer BRAIN — HaleHound Fire (this app)
- **Debug console**: richer status than 320px firmware menus.
- **Storage plane**: pull loot off SD/softAP so CYD flash doesn’t fill.
- **Compute plane**: parse, search, annotate, export — offload ESP32 heap.
- **Operator UX**: big screen for long sessions, notes, multi-module overview.
- **Blue Team local**: on-device Guardian without PC (v0.5+).
- Never pretend Fire can replace CC1101/NRF24/monitor-mode Wi‑Fi.

### Layer LAB (optional) — Windows
- Flash only (Web Serial / esptool).
- Optional Npcap backend for desktop-class 802.11 analysis.
- Not required for day-to-day Fire ↔ CYD companion use.

## Why this helps debugging

1. **Serial / UART0 conflict on CYD** (e.g. GPS vs USB serial) — Fire can host a serial bridge UI once OTG/USB or Wi‑Fi console exists, so you debug without fighting the only UART.
2. **Heap and log volume** — ESP32 serial floods are hard to scroll on-device; Fire can buffer, filter, and save ring buffers to disk.
3. **Firmware iteration** — radio code stays on CYD; companion UX iterates as APK (faster than full firmware + flash cycles).
4. **Field forensics** — after a session, CYD holds loot; Fire is the place to triage before a laptop is available.
5. **Operator training** — large UI can show *what the CYD is doing* while radios run, reducing mis-taps on the tiny TFT.

## Roadmap toward “debug companion” (ordered)

| Phase | Deliverable | Unblocks |
|-------|-------------|----------|
| **P0** (now) | Local Guardian, surveys, CYD discover/open URL, arsenal UI | Standalone Fire + discover path |
| **P1** | Stable CYD link protocol (HTTP JSON status + loot list) | Live debug without scraping HTML |
| **P2** | Telemetry dashboard (RSSI, module armed, heap, mode, VALHALLA state) | Real-time field debug |
| **P3** | Loot pull + local browser (EAPOL, wardrive CSV, .sub index) | Storage offload from CYD SD |
| **P4** | Optional USB/OTG serial console viewer | Deep firmware debug without PC |
| **P5** | Session recorder (notes + timestamps + CYD events → zip) | After-action / bug reports |

Upstream cooperation (JesseCHale / HaleHound-CYD) may expose cleaner companion APIs; until then we stay read-only consumers of whatever the device already serves (softAP web, SD paths, etc.).

## Non-goals

- Running full offensive radio stacks on Fire OS.
- Replacing official CYD firmware.
- Requiring a laptop in the field (Fire + CYD kit should stand alone).

## Ethics

Same VALHALLA rules: authorized scope only. Fire as a debug plane does **not** relax legal constraints on CYD TX modules.
