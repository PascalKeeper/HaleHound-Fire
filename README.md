# HaleHound Fire

**Unofficial Fire OS companion** for the [HaleHound](https://halehound.com) ecosystem — Amazon Fire tablets (validated on **Fire 7 12th gen `KFQUWI`** / Fire OS 8.3.x).

> **This is not ESP32 firmware.** Full multi-radio HaleHound still runs on [Cheap Yellow Display (CYD)](https://github.com/JesseCHale/HaleHound-CYD) boards. This app is **Layer A**: passive Blue Team survey + CYD companion shell on a Fire tablet.

**Status:** independent community project · MIT · [NOTICE.md](NOTICE.md) (attribution & non-affiliation)

**Authors of this companion:** [PascalKeeper](https://github.com/PascalKeeper) / Joseph Peransi  

**Official HaleHound CYD:** [@JesseCHale](https://github.com/JesseCHale) · [HaleHound-CYD](https://github.com/JesseCHale/HaleHound-CYD) · [flash.halehound.com](https://flash.halehound.com)

---

## Architecture

| Layer | Hardware | Role |
|-------|----------|------|
| **A — HaleHound Fire** (this repo) | Kindle Fire / Fire OS tablet | VALHALLA gate, Wi‑Fi/BLE passive survey, CYD discovery |
| **B — HaleHound CYD** (official) | ESP32-2432S028 / E32Rxx + modules | Full multi-radio arsenal (flash via desktop Web Serial) |

```
┌─────────────────────┐         Wi‑Fi / HTTP          ┌──────────────────────┐
│  Amazon Fire tablet │  ◄──────────────────────────► │  ESP32 CYD + modules │
│  HaleHound Fire APK │     companion / softAP UI     │  Official firmware   │
└─────────────────────┘                               └──────────────────────┘
```

## v0.1 features

- VALHALLA Protocol legal / authorization gate  
- Host capability matrix (native Fire vs CYD-required)  
- Passive Wi‑Fi survey (`WifiManager`) — **no injection / deauth**  
- Passive BLE advertisement survey  
- CYD HTTP discovery (common softAP IPs) + open in Silk/browser  
- Fire OS 8 / API 30, portrait UI suited to 1024×600  

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
- HaleHound™ marks belong to their owners; this is an **unofficial** companion.

## Roadmap

- [ ] mDNS / broader subnet scan for CYD web UI  
- [ ] In-app WebView remote for CYD  
- [ ] USB serial bridge when CYD is OTG-connected (experimental)  
- [ ] Export survey CSVs  
- [ ] Collaboration with upstream if maintainers want integration  

## Courtesy to creators

We open-sourced this as a **companion**, not a fork of the ESP32 firmware. Official maintainers are notified via GitHub issue on `JesseCHale/HaleHound-CYD`. Feedback on naming, branding, or collaboration is welcome.

## References

- https://halehound.com  
- https://flash.halehound.com  
- https://github.com/JesseCHale/HaleHound-CYD  
- Amazon Fire OS 8 developer docs  

## License

MIT — see [LICENSE](LICENSE).
