# Kitchen Secrets — run-by list (no withholding)

Rule: if a feature would help **this ship**, **future builds**, or the **end user**, surface it here for a `haleeee yea` / pass.

Ethics gate still applies: Blue Team / authorized only. No offensive TX from Fire as product identity.

---

## SHIPPED IN 1.1.0 (this pass)

| # | Secret | Why it helps |
|---|--------|--------------|
| S0 | **CYD live telemetry** | Probe softAP HTTP paths, latency, heap/mode hints, auto-refresh 8s |
| S1 | **Loot vault on Fire** | Offload CYD SD → tablet; session folders + optional `.hhf` encrypt |
| S2 | **TERM: `cyd status\|loot\|pull\|vault`** | Grok-friendly ops without tapping UI |
| S3 | **Private-host first policy** | SoftAP/LAN always OK; public only via HomeCallPolicy + HTTPS |

---

## NEXT-WAVE (say haleeee yea)

### A — Field ops (high ship value)

1. **One-tap “Ninja Field Pack” export**  
   Zip vault session + scrubbed telemetry + harden score → share sheet / USB. End user leaves site with one artifact.

2. **CYD link health widget on HOME**  
   Tiny ●/○ + latency on arsenal home so you never dig into CYD tab mid-run.

3. **Vault retention policy**  
   Auto-prune sessions older than N days / over size budget so Fire storage doesn’t die after a week of pulls.

4. **Pull progress + cancel**  
   Per-file progress bar + cancel mid-pull (ESP can be slow; 8MB cap already).

5. **Known-firmware path packs**  
   JSON packs for HaleHound / Marauder-ish / generic ESP web UI so probes hit the right routes first (faster + more loot).

### B — OPSEC / privacy (ninja identity)

6. **“Scrub on share” default**  
   Any Share/export runs PiiScrubber + optional encrypt; never plain personal markers in mail/USB.

7. **Session kill-switch**  
   One button: wipe vault + secure prefs cyd URL + clear last telemetry (leave-no-trace after a job).

8. **Airplane-mode companion mode**  
   Detect no upstream + only softAP: hide any cloudy UI chrome; show “edge only” banner.

### C — Quality / latency (Velora habits)

9. **DiffUtil loot list**  
   RecyclerView for loot instead of TextView walls — snappier on Fire 7 when loot > 40.

10. **Probe budget profile per firmware**  
    Ultra: top 8 paths only. Balanced: full list. Saves ESP and tablet radio time.

11. **Cold-start ART tip in About**  
    “Open once after install, leave 30s” for speed-profile warm — end users feel day-2 snappiness.

### D — Ecosystem / multi-build leverage

12. **Shared `hhf-protocol` module skeleton**  
    Common Telemetry/LootEntry DTOs + path packs so Fire / future Android / desktop companion speak one language.

13. **Windows netsh bat “apply + verify” pair**  
    Already generate bat; add companion `verify-firewall.ps1` that checks HHF-* rules exist after apply.

14. **Grok agent cookbook in TERM**  
    `agent cookbook` prints a 10-line mission script (status→harden→cyd→pull→vault) for overnight agents.

15. **Local Guardian → CYD correlation**  
    When Wi-Fi drops + BSSID churn while CYD softAP expected, surface “lost CYD link” instead of generic storm.

### E — End-user delight

16. **Silk “ops only” shortcut strip**  
    Home chips: codersguild.net · flash.halehound.com · local vault path open-in-files.

17. **Haptic + amber flash on first ONLINE**  
    Tiny sensory confirm when probe succeeds — field muscle memory.

18. **Offline help cards**  
    No network needed: “Join CYD AP steps” + softAP diagram text in CYD tab.

---

## PARKED (not now unless you pull)

- Official CYD firmware companion API (needs upstream firmware work)
- BLE UART bridge to ESP (hardware/firmware dependent)
- Mesh multi-CYD roster (cool, later)

---

Reply with numbers (`haleeee yea 1 2 6 7 14`) or `all A` / `all B` and we queue them.
