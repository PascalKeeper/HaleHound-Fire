# Privacy & ninja posture — HaleHound Fire

## Defaults (field ninjas)

| Control | Default | Meaning |
|---------|---------|---------|
| Phone home / telemetry | **OFF** | No vendor analytics, no automatic sensei sync |
| Call-home to PC | **OFF** | Must explicitly enable rare edge-case sync |
| Home over internet | **HTTPS only** | No cleartext to public hosts |
| Scrub PII on export | **ON** | MAC/IP/serial redaction in shared reports |
| Encrypt exports at rest | **ON** | AES-GCM (`HHF1.…`) for .bat / report files |
| Sensitive prefs | **EncryptedSharedPreferences** | Keystore-backed |
| App update check | **OFF until you tap** | About → CHECK or TERM `update` → GitHub Releases only |

## Wi‑Fi updates (not dojo USB)

Runtime learning / Guardian / harden **do not need** the laptop. Only **new APK builds** need delivery.

- **Opt-in:** no background poll, no silent download  
- **Endpoint:** public `api.github.com` releases for this repo (HTTPS)  
- **Install:** user confirms system package installer; Fire “unknown sources” may be required once  
- Attach a `.apk` asset to a GitHub Release for one-tap DOWNLOAD + INSTALL  

## Personal markers we hide

When scrub is on: MAC/BSSID partial redact, IPv4 → `a.b.x.x`, serial lines, emails.

SSID may remain (ops need network name); disable scrub only if you need full forensic detail **on your own secure channel**.

## Calls home (edge case only)

Sensei PC is optional. If an operator **must** reach home:

1. Enable “allow call-home” in privacy settings  
2. Prefer **HTTPS** endpoint on the dojo  
3. Cleartext only for **localhost / RFC1918** (USB adb reverse, lab LAN, CYD softAP)  
4. App **refuses** cleartext to public addresses when HTTPS-only is set  

CYD softAP (`192.168.4.1` etc.) stays local companion traffic — not “identity to the masses.”

## What we never do

- No crash beacons to third parties  
- No ad/analytics SDKs  
- No silent upload of harden reports  
- No requirement that the PC be online for field use  

## Operator controls

- **About → Privacy** toggles (scrub / encrypt / allow home / HTTPS)  
- **Wipe secure prefs** clears Keystore-backed markers  
- TERM: `privacy` shows posture; `privacy wipe` clears secure store  

## Legal

Privacy features protect **your** operational data. They do not authorize attacking others or hiding evidence of crime. VALHALLA / authorized use still applies.
