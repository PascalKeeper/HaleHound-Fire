# Hardware · salvage antennas for CYD + Fire

**Fuck landfills.** Screw-on Wi‑Fi antennas from retired enterprise APs (and random router ducks) are still useful on a first **CYD radio edge** paired with this **Fire brain plane**.

Ethics: authorized / Blue Team only. External antennas improve *your* link quality — they do not change Fire’s stock-OS radio limits, and they do not authorize offensive TX.

---

## Quick map: 802.11a vs 802.11b/g

| Label on gear | IEEE | Band | CYD (classic ESP32) | Fire tablet Wi‑Fi |
|---------------|------|------|---------------------|-------------------|
| **802.11b / 802.11g** | b, g | **2.4 GHz** (~2.400–2.4835 GHz) | **YES — primary** | Yes (client only) |
| **802.11a** | a | **5 GHz** (~5.15–5.85 GHz, region-dependent) | **NO** (ESP32 has no 5 GHz Wi‑Fi) | Client only if Fire radio has 5 GHz |
| Dual “a/b/g” or “a/b/g/n” whip | multi | 2.4 **and** often 5 | **Use it** — 2.4 path is what ESP needs | Fine as spare / lab |

### How to read *your* Aruba (or any) whip

1. Look for printed text: `2.4GHz`, `5GHz`, `2.4/5`, `802.11a`, `802.11b/g`, dBi rating.  
2. **b/g only** → pure 2.4 → **best match for first CYD**.  
3. **a only** → pure 5 → **do not expect useful gain on ESP32**; keep for a future 5 GHz radio, SDR lab, or another AP that is actually 5 GHz.  
4. **a + b/g** (dual-band duck) → **screw it on the CYD**; the 2.4 resonator is what matters. 5 GHz half is just along for the ride on ESP32.

**Aruba AP-60 / AP-61 era:** external antennas on **AP-60** are **RP-SMA**, dual jacks for diversity. AP-61 is integral antennas. Salvaged screw-ons from that family are almost always **RP-SMA · 50 Ω** Wi‑Fi whips (often dual-band in the enterprise catalog). Treat unlabeled dumps as “probably dual / b-g capable” only after a visual label check — when in doubt, use a cheap known-2.4 duck first, then swap.

```
  802.11b/g  ──────────────►  2.4 GHz  ──►  CYD ESP32  ✅
  802.11a    ──────────────►  5 GHz    ──►  CYD ESP32  ❌ (wrong band)
  Dual a/b/g ──────────────►  both     ──►  CYD ESP32  ✅ (uses 2.4)
```

---

## Connector: RP-SMA (the “screw-on jigger”)

| Piece | Typical gender | Notes |
|-------|----------------|-------|
| Antenna whip (Aruba / router duck) | **RP-SMA male** (screw-on) | Outer threads + **center hole** (no pin) on many RP-SMA males |
| Chassis bulkhead / AP jack | **RP-SMA female** | Receives the whip |
| ESP pigtail bulkhead | **RP-SMA female** jack on case | What you mount on the CYD box |

### SMA vs RP-SMA (do not mix blindly)

Same outer thread family; **center contact polarity is reversed**.

- Wrong mate: may *feel* like it threads, then **no RF** or **bent pin**.  
- Aruba AP-60 external ports: **RP-SMA**.  
- Cheap “ESP32 Wi‑Fi antenna kits”: almost always **RP-SMA**.  
- Lab SMA (true SMA): different pin gender — needs an adapter (adapters = loss; prefer native RP-SMA).

**Rule:** if it came off an enterprise Wi‑Fi AP external port in the 2000s–2010s US market, start with **RP-SMA**.

Impedance: **50 Ω** end-to-end. Do not use 75 Ω TV coax as a long run.

---

## Target stack: first CYD ↔ Fire

```
┌─────────────────────┐         softAP / LAN          ┌──────────────────────┐
│  FIRE (brain)       │ ◄──── Wi‑Fi client ──────────► │  CYD (radio edge)    │
│  HaleHound Fire app │     often 192.168.4.1         │  ESP32 + web UI      │
│  telemetry · loot   │                                │  + external antenna  │
└─────────────────────┘                                └──────────┬───────────┘
                                                                  │
                                                    U.FL / IPEX (or mod)
                                                                  │
                                                    short pigtail 50 Ω
                                                                  │
                                                    RP-SMA bulkhead on case
                                                                  │
                                                    screw-on 802.11b/g (2.4)
                                                    or dual a/b/g whip
```

Fire stays stock (no external ant path you control). **All salvage antennas go on the CYD (or a future external radio), not the tablet.**

---

## BOM — put trash-can Aruba to work

| # | Part | Why |
|---|------|-----|
| 1 | CYD / ESP32 board | Prefer modules with **U.FL / IPEX** footprint, or documented PCB-ant cutover |
| 2 | **U.FL (IPEX) → RP-SMA** bulkhead pigtail, **as short as practical** | Long coax = loss at 2.4 GHz |
| 3 | Salvaged **RP-SMA** antenna marked **b/g**, **2.4**, or **dual a/b/g** | The free part |
| 4 | Case hole + nut for bulkhead | Strain relief so you don’t rip the U.FL pad |
| 5 | Optional: second identical whip | AP-60 diversity pair = spare / second radio later |

### Board notes (generic)

- Some ESP modules ship with **PCB antenna only** → need a **documented antenna mod** (0 Ω / resistor move, or U.FL solder) — board-dependent; follow *your* module silkscreen / Marauder-CYD style guides.  
- If the module already has U.FL: connect pigtail, select external antenna per module docs, screw on whip.  
- Never run high-power PA into a disconnected antenna jack for long (VSWR / PA stress on some radios).

---

## Field checklist

- [ ] Confirm whip is **RP-SMA** and screws fully home without force.  
- [ ] Confirm band: **b/g or dual** for CYD; **a-only** → shelf for 5 GHz gear.  
- [ ] Pigtail short; bulkhead nut tight; U.FL fully clicked.  
- [ ] Power CYD, join softAP from Fire, run **CYD PROBE** / `cyd status` — compare latency before/after if you care.  
- [ ] Label the case: `RP-SMA · 2.4 GHz · 50Ω · salvage OK`.  
- [ ] Keep TX power at firmware defaults; gain antennas are for **link budget / placement**, not “illegal EIRP flex.”

---

## What improves (and what doesn’t)

| Improves | Does not change |
|----------|-----------------|
| SoftAP range Fire ↔ CYD | Fire stock OS monitor-mode limits |
| Survey / softAP stability at distance | Need for authorized use only |
| Loot pull reliability over flaky PCB ant | Official HaleHound firmware features |
| Spare parts ecosystem (any RP-SMA 2.4 duck) | 5 GHz on classic ESP32 |

---

## Band physics (operator-friendly)

- **2.4 GHz (b/g):** longer range through walls, more crowded (Bluetooth, microwaves, neighbors). CYD home band.  
- **5 GHz (a):** more channels, shorter range, worse through walls. **Not** on classic ESP32 Wi‑Fi.  
- A dual-band rubber duck is two resonators in one plastic stick; ESP only drives **2.4**.

---

## Future builds (same thread ecosystem)

Once the case has an **RP-SMA female bulkhead**, you can swap without resoldering:

- Other enterprise salvage (same RP-SMA)  
- Higher-gain 2.4 omni or panel (mind EIRP / placement)  
- Short “stock” duck for travel, long whip for fixed lab  

Adapters (RP-SMA ↔ SMA, N-type, etc.) exist for weird trash finds — **minimize adapters**, keep runs short.

Multi-radio dream kit (later): one salvage whip per radio chain; Fire still only sees **one** softAP/LAN brain link.

---

## Repo touchpoints

| Doc / code | Role |
|------------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Fire = brain, CYD = edge |
| [FEATURE_MAP.md](FEATURE_MAP.md) | CYD companion matrix |
| App **CYD** tab + TERM `cyd status\|loot\|pull` | Validate link after antenna change |
| [KITCHEN_SECRETS.md](KITCHEN_SECRETS.md) | Future UI “antenna tips” offline card |

---

## Sources / era facts (salvage context)

- Aruba **AP-60**: dual **RP-SMA** external antenna interfaces (diversity).  
- Aruba **AP-61**: integral antennas (no screw-ons on the unit itself).  
- 802.11 **a** → 5 GHz; 802.11 **b/g** → 2.4 GHz (IEEE band split still taught the same way in 2026).  
- Community CYD / Marauder builds commonly use **U.FL → RP-SMA + 2.4 GHz external**.

---

## TL;DR

| You have | Do this |
|----------|---------|
| Screw-on labeled **802.11b/g** or **2.4** | **First choice for CYD** |
| Screw-on labeled **802.11a** only | **Not for ESP32 Wi‑Fi** — save for 5 GHz hardware |
| Dual **a + b/g** | **Screw on CYD** — 2.4 path works |
| Connector feels like Aruba AP-60 external | **RP-SMA · 50 Ω** → U.FL pigtail to case jack |

**Trash → RP-SMA → 2.4 GHz → better Fire↔CYD link.** Landfill gets nothing.
