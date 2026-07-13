# HaleHound Fire

> **UNOFFICIAL · NOT AFFILIATED · NOT ENDORSED**  
> This is an independent community **Fire OS companion**. It is **not** official HaleHound firmware, **not** affiliated with the official product line beyond interoperability, and **not** an endorsed or substitute for the official CYD stack.  
> **HaleHound™ is a trademark of Jesse Hale, used with permission for interoperability.**  
> Full legal relationship: **[NOTICE.md](NOTICE.md)**.

**Unofficial Fire OS companion** for the [HaleHound](https://halehound.com) ecosystem — Amazon Fire tablets (validated on **Fire 7 12th gen `KFQUWI`** / Fire OS 8.3.x).

> **This is not ESP32 firmware on a Kindle.**  
> The CYD stays the **radio edge** (tight flash/RAM, multi-protocol TX/RX).  
> The Fire is the **debug / storage / operator plane** — more screen, disk, and compute for long sessions and future telemetry/loot analysis.

**Status:** v1.1.0 · independent community project · MIT · [NOTICE.md](NOTICE.md) · [ARCHITECTURE.md](ARCHITECTURE.md) · [PRIVACY.md](PRIVACY.md) · [HARDWARE_ANTENNA.md](HARDWARE_ANTENNA.md)

**Authors of this companion:** [PascalKeeper](https://github.com/PascalKeeper) / Joseph Peransi  

**Official HaleHound CYD:** [@JesseCHale](https://github.com/JesseCHale) · [HaleHound-CYD](https://github.com/JesseCHale/HaleHound-CYD) · [flash.halehound.com](https://flash.halehound.com)

---

## Architecture (why Fire + CYD)

| Layer | Hardware | Role |
|-------|----------|------|
| **BRAIN — HaleHound Fire** | Kindle Fire / Fire OS | Debug UI, local Guardian, surveys, loot/storage offload (roadmap), CYD link |
| **EDGE — HaleHound CYD** | ESP32 CYD + modules | All radios, frame-level tools, standalone field kit |
| **LAB (optional)** | Windows PC | Flash only; optional Npcap — **not required in the field** |

```
 FIRE (space · compute · debug UI)  ◄── Wi‑Fi/USB ──►  CYD (radios · tight resources)
```

Full rationale and phased roadmap: **[ARCHITECTURE.md](ARCHITECTURE.md)**.

## Current features

- VALHALLA Protocol legal / authorization gate  
- CYD-style arsenal UI + locked radio tiles (honest capability matrix)  
- **Fully local** Network Guardian (no laptop)  
- Passive Wi‑Fi / BLE survey  
- CYD live telemetry + loot vault (TERM: `cyd status|loot|pull|vault`)  
- Salvage antenna guide for first CYD build — [HARDWARE_ANTENNA.md](HARDWARE_ANTENNA.md) (802.11a vs b/g, RP-SMA)  

## Build (Windows)

Prerequisites: JDK 17, Android SDK (`ANDROID_HOME`).

```powershell
git clone https://github.com/PascalKeeper/HaleHound-Fire.git
cd HaleHound-Fire
.\gradlew.bat :app:assembleDebug
```

APK:

```
app\build\outputs\apk\debug\app-debug.apk
```

## Install on Fire tablet (ADB)

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.halehoundforge.fire.debug/com.halehoundforge.fire.ui.ValhallaGateActivity
```

Or use `install-to-fire.bat` after adjusting the `adb` path.

## Legal / ethics

- **Authorized use only** (your gear, written scope, CTF/lab).  
- Stock Fire OS **cannot** perform monitor-mode 802.11 attacks; those modules require official CYD hardware.  
- See in-app VALHALLA gate and [NOTICE.md](NOTICE.md).  
- **Unofficial · not affiliated · not endorsed** by the official HaleHound product.  
- **HaleHound™ is a trademark of Jesse Hale, used with permission for interoperability.**

## Roadmap

- [ ] mDNS / broader subnet scan for CYD web UI  
- [ ] In-app WebView remote for CYD  
- [ ] USB serial bridge when CYD is OTG-connected (experimental)  
- [ ] Export survey CSVs  
- [x] Upstream awareness / trademark permission for interoperability (Jesse Hale / JMFH)  
- [ ] More devices + field testing with official CYD  

## Courtesy to creators

We open-sourced this as a **companion**, not a fork of the ESP32 firmware — no official bins, no phone-home, passive Blue Team on Fire OS. Jesse Hale reviewed the repo and is **good with it staying up as an unofficial companion**, with trademark wording as above. Support the official stack: [halehound.com](https://halehound.com) · [HaleHound-CYD](https://github.com/JesseCHale/HaleHound-CYD).

## References

- https://halehound.com  
- https://flash.halehound.com  
- https://github.com/JesseCHale/HaleHound-CYD  
- Amazon Fire OS 8 developer docs  

## License

MIT — see [LICENSE](LICENSE).

## Fully local Guardian (default)

**v0.5+ runs 100% on the Fire tablet** — no laptop, no Npcap, no backend URL.

GUARD uses on-device sensors only: disconnect storms, RSSI cliffs, BSSID churn, link collapse, gateway latency, SSID lock.

Stock Fire OS **cannot** capture raw 802.11 deauth frames. For true Dot11 Guardian, use a flashed **HaleHound CYD**. Optional PC backend remains under `windows-backend/` for advanced lab use only.
