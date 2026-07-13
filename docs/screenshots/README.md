# Screenshots

## Device (ship)

| File | Notes |
|------|--------|
| `01-arsenal-fire7.png` | **Real** Fire 7 (`KFQUWI`) capture — arsenal home |

More tabs (HARDEN / GUARD / TERM / CYD) when tablet is on USB:

```powershell
.\tools\capture-ship-screens.ps1
```

Requires: `adb`, USB debugging, app installed (`com.halehoundforge.fire.debug`).

## Promo / mood (not device truth)

`promo/` holds stylized UI mood frames for marketing experiments.  
**Do not treat them as accurate product screenshots** — they invent chrome. Prefer device caps above for the README ship gallery.

## Capture tips (Fire OS)

- Unlock screen; keep brightness up for readable screencaps  
- Contrast was tuned for Fire LCD — dark TFT UI needs `screencap -p` not phone camera  
- After UI changes, re-run the script and commit new PNGs