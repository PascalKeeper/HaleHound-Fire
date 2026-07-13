#!/usr/bin/env python3
# Copyright Joseph Peransi / HaleHound Fire companion — Blue Team deauth radar backend
"""
HaleHound Fire — Windows Virtual Backend
========================================
Runs on the PC with Npcap + Scapy to capture 802.11 deauth frames, then
exposes them over HTTP so the Fire tablet (or any LAN client) can display
real detections.

Usage (Admin recommended for sniffer):
  python guardian_backend.py
  python guardian_backend.py --port 8765 --host 0.0.0.0
  python guardian_backend.py --virtual          # pipeline demo without RF
  python guardian_backend.py --iface "Wi-Fi"

Fire app: set backend URL to http://<this-pc-lan-ip>:8765

AUTHORIZED / BLUE TEAM ONLY — detection & monitoring of your own networks.
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys
import threading
import time
import traceback
from collections import deque
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Deque, Dict, List, Optional
from urllib.parse import parse_qs, urlparse

# ---------------------------------------------------------------------------
# Optional deps
# ---------------------------------------------------------------------------

try:
    import psutil
    PSUTIL = True
except ImportError:
    PSUTIL = False

SCAPY = False
Dot11Deauth = None  # type: ignore
Dot11 = None  # type: ignore
scapy_all = None

try:
    import scapy.all as scapy_all  # type: ignore
    from scapy.layers.dot11 import Dot11Deauth, Dot11  # type: ignore

    SCAPY = True
except Exception:
    SCAPY = False

NPCAP = os.path.exists(
    os.path.join(os.environ.get("SystemRoot", r"C:\Windows"), "System32", "wpcap.dll")
)

# ---------------------------------------------------------------------------
# Shared state
# ---------------------------------------------------------------------------

class BackendState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.started_at = time.time()
        self.mode = "init"  # sniff | virtual | limited
        self.sniffer_ok = False
        self.sniffer_error = ""
        self.iface: Optional[str] = None
        self.total_deauths = 0
        self.total_packets = 0
        self.alerts: Deque[Dict[str, Any]] = deque(maxlen=200)
        self.last_deauth_at: Optional[str] = None
        self.virtual = False
        self.tx_mbps = 0.0
        self.rx_mbps = 0.0
        self.host_ips: List[str] = []

    def snapshot(self) -> Dict[str, Any]:
        with self.lock:
            return {
                "service": "halehound-fire-guardian-backend",
                "version": "1.0.0",
                "mode": self.mode,
                "virtual": self.virtual,
                "sniffer_ok": self.sniffer_ok,
                "sniffer_error": self.sniffer_error,
                "npcap": NPCAP,
                "scapy": SCAPY,
                "psutil": PSUTIL,
                "iface": self.iface,
                "uptime_s": int(time.time() - self.started_at),
                "total_deauths": self.total_deauths,
                "total_packets": self.total_packets,
                "last_deauth_at": self.last_deauth_at,
                "tx_mbps": round(self.tx_mbps, 3),
                "rx_mbps": round(self.rx_mbps, 3),
                "host_ips": list(self.host_ips),
                "alert_count": len(self.alerts),
                "ethics": "BLUE_TEAM_DETECTION_ONLY",
            }

    def list_alerts(self, limit: int = 50) -> List[Dict[str, Any]]:
        with self.lock:
            items = list(self.alerts)
        items.reverse()  # newest first
        return items[: max(1, min(limit, 200))]

    def push_alert(
        self,
        *,
        source: str,
        dest: str,
        reason: Any,
        channel: str = "?",
        kind: str = "deauth",
        note: str = "",
    ) -> None:
        ts = datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M:%S")
        iso = datetime.now(timezone.utc).isoformat()
        entry = {
            "ts": ts,
            "ts_iso": iso,
            "kind": kind,
            "source": source,
            "dest": dest,
            "reason": str(reason),
            "channel": channel,
            "note": note,
            "message": f"[{ts}] DEAUTH {source} -> {dest} reason={reason}",
        }
        with self.lock:
            self.alerts.append(entry)
            self.total_deauths += 1
            self.last_deauth_at = ts


STATE = BackendState()

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def local_ips() -> List[str]:
    found: List[str] = []
    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127.") and ip not in found:
                found.append(ip)
    except Exception:
        pass
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if ip not in found:
            found.insert(0, ip)
    except Exception:
        pass
    if PSUTIL:
        try:
            for name, addrs in psutil.net_if_addrs().items():
                for a in addrs:
                    if getattr(a, "family", None) == socket.AF_INET:
                        ip = a.address
                        if ip and not ip.startswith("127.") and ip not in found:
                            found.append(ip)
        except Exception:
            pass
    return found


def bandwidth_loop() -> None:
    if not PSUTIL:
        return
    try:
        last = psutil.net_io_counters()
        last_t = time.perf_counter()
        while True:
            time.sleep(1.0)
            cur = psutil.net_io_counters()
            now = time.perf_counter()
            dt = max(now - last_t, 0.001)
            with STATE.lock:
                STATE.tx_mbps = ((cur.bytes_sent - last.bytes_sent) * 8 / 1_000_000) / dt
                STATE.rx_mbps = ((cur.bytes_recv - last.bytes_recv) * 8 / 1_000_000) / dt
            last, last_t = cur, now
    except Exception:
        pass


def sniffer_loop(iface: Optional[str]) -> None:
    """Capture Dot11Deauth when Npcap + Scapy + capable adapter allow it."""
    if not SCAPY or scapy_all is None:
        with STATE.lock:
            STATE.mode = "virtual" if STATE.virtual else "limited"
            STATE.sniffer_ok = False
            STATE.sniffer_error = "scapy not installed"
        return

    if not NPCAP:
        with STATE.lock:
            STATE.mode = "virtual" if STATE.virtual else "limited"
            STATE.sniffer_ok = False
            STATE.sniffer_error = "Npcap wpcap.dll not found"
        return

    def handler(pkt: Any) -> None:
        try:
            with STATE.lock:
                STATE.total_packets += 1
            if pkt.haslayer(Dot11Deauth):
                dot = pkt.getlayer(Dot11)
                addr1 = getattr(dot, "addr1", None) or "?"
                addr2 = getattr(dot, "addr2", None) or "?"
                reason = pkt.getlayer(Dot11Deauth).reason
                STATE.push_alert(
                    source=str(addr2),
                    dest=str(addr1),
                    reason=reason,
                    note="npcap/scapy",
                )
        except Exception:
            pass

    try:
        with STATE.lock:
            STATE.mode = "sniff"
            STATE.iface = iface
            STATE.sniffer_ok = True
            STATE.sniffer_error = ""
        # store=0 — never buffer frames in RAM
        if iface:
            scapy_all.sniff(iface=iface, prn=handler, store=0)
        else:
            scapy_all.sniff(prn=handler, store=0)
    except Exception as e:
        with STATE.lock:
            STATE.sniffer_ok = False
            STATE.sniffer_error = str(e)
            if STATE.virtual:
                STATE.mode = "virtual"
            else:
                STATE.mode = "limited"
        print(f"[!] Sniffer stopped: {e}", flush=True)


def virtual_radar_loop(interval: float = 12.0) -> None:
    """
    Synthetic deauth events so the Fire ↔ backend pipeline always works
    even without monitor-mode hardware. Marked kind=virtual_deauth.
    """
    n = 0
    while True:
        time.sleep(interval)
        if not STATE.virtual and STATE.sniffer_ok:
            # real sniffer alive — only inject occasional heartbeat note, not fake deauths
            continue
        n += 1
        STATE.push_alert(
            source=f"aa:bb:cc:dd:ee:{n % 256:02x}",
            dest="ff:ff:ff:ff:ff:ff",
            reason=7,
            kind="virtual_deauth",
            note="virtual backend demo (no RF) — enable real sniffer for live frames",
        )


# ---------------------------------------------------------------------------
# HTTP API
# ---------------------------------------------------------------------------

class Handler(BaseHTTPRequestHandler):
    server_version = "HaleHoundFireGuardian/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _cors(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json(self, code: int, payload: Any) -> None:
        body = json.dumps(payload, indent=2).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _text(self, code: int, text: str, ctype: str = "text/plain; charset=utf-8") -> None:
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        qs = parse_qs(parsed.query)

        if path in ("/", "/index.html"):
            self._text(200, INDEX_HTML, "text/html; charset=utf-8")
            return
        if path in ("/health", "/api/health", "/api/v1/health"):
            self._json(200, {"ok": True, "service": "halehound-fire-guardian-backend"})
            return
        if path in ("/api/status", "/api/v1/status"):
            self._json(200, STATE.snapshot())
            return
        if path in ("/api/alerts", "/api/v1/alerts"):
            try:
                limit = int(qs.get("limit", ["50"])[0])
            except ValueError:
                limit = 50
            self._json(
                200,
                {
                    "ok": True,
                    "mode": STATE.snapshot()["mode"],
                    "total_deauths": STATE.total_deauths,
                    "alerts": STATE.list_alerts(limit),
                },
            )
            return
        if path in ("/api/stream", "/api/v1/stream"):
            # simple long-poll style dump for dumb clients
            self._json(
                200,
                {
                    "status": STATE.snapshot(),
                    "alerts": STATE.list_alerts(30),
                },
            )
            return

        self._json(404, {"ok": False, "error": "not found", "path": path})

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        length = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            data = json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            data = {}

        if path in ("/api/simulate", "/api/v1/simulate"):
            # Lab pipeline test — inject one synthetic alert
            src = data.get("source", "de:ad:be:ef:00:01")
            dst = data.get("dest", "ff:ff:ff:ff:ff:ff")
            reason = data.get("reason", 7)
            STATE.push_alert(
                source=str(src),
                dest=str(dst),
                reason=reason,
                kind="virtual_deauth",
                note="manual simulate",
            )
            self._json(200, {"ok": True, "injected": True})
            return

        self._json(404, {"ok": False, "error": "not found"})


INDEX_HTML = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>HaleHound Fire Guardian Backend</title>
<style>
body{background:#050805;color:#e8ffe8;font-family:Consolas,monospace;padding:20px}
h1{color:#ffcc00} a{color:#00ff41} .ok{color:#00ff41} .bad{color:#ff3344}
pre{background:#141a14;border:1px solid #2e3a2e;padding:12px;overflow:auto}
</style></head><body>
<h1>HALEHOUND-FIRE · GUARDIAN BACKEND</h1>
<p>Blue Team deauth radar for the Fire tablet companion.</p>
<ul>
<li><a href="/api/v1/status">/api/v1/status</a></li>
<li><a href="/api/v1/alerts">/api/v1/alerts</a></li>
<li><a href="/api/v1/health">/api/v1/health</a></li>
</ul>
<p>Point the Fire app backend URL at this host (LAN IP + port).</p>
<pre id="s">loading…</pre>
<script>
async function tick(){
  const r=await fetch('/api/v1/stream');
  const j=await r.json();
  document.getElementById('s').textContent=JSON.stringify(j,null,2);
}
tick(); setInterval(tick,2000);
</script>
</body></html>
"""


def open_firewall(port: int) -> None:
    """Best-effort Windows firewall rule (needs Admin)."""
    if sys.platform != "win32":
        return
    try:
        import subprocess

        name = "HaleHound Fire Guardian Backend"
        subprocess.run(
            [
                "netsh",
                "advfirewall",
                "firewall",
                "add",
                "rule",
                f"name={name}",
                "dir=in",
                "action=allow",
                "protocol=TCP",
                f"localport={port}",
            ],
            capture_output=True,
            text=True,
            check=False,
        )
    except Exception:
        pass


def main() -> int:
    ap = argparse.ArgumentParser(description="HaleHound Fire Guardian Backend")
    ap.add_argument("--host", default="0.0.0.0", help="Bind address (default 0.0.0.0)")
    ap.add_argument("--port", type=int, default=8765, help="HTTP port (default 8765)")
    ap.add_argument("--iface", default=None, help="Scapy interface name / description")
    ap.add_argument(
        "--virtual",
        action="store_true",
        help="Always inject virtual deauth events (pipeline demo)",
    )
    ap.add_argument(
        "--virtual-interval",
        type=float,
        default=12.0,
        help="Seconds between virtual events (default 12)",
    )
    ap.add_argument(
        "--no-sniff",
        action="store_true",
        help="Do not start Scapy sniffer (API + virtual only)",
    )
    args = ap.parse_args()

    STATE.virtual = bool(args.virtual) or args.no_sniff or not (SCAPY and NPCAP)
    STATE.host_ips = local_ips()

    print("=" * 64, flush=True)
    print("  HALEHOUND-FIRE  GUARDIAN BACKEND  (Blue Team)", flush=True)
    print("=" * 64, flush=True)
    print(f"  Npcap     : {'yes' if NPCAP else 'NO'}", flush=True)
    print(f"  Scapy     : {'yes' if SCAPY else 'NO — pip install scapy'}", flush=True)
    print(f"  Virtual   : {STATE.virtual}", flush=True)
    print(f"  Bind      : http://{args.host}:{args.port}/", flush=True)
    for ip in STATE.host_ips:
        print(f"  Fire URL  : http://{ip}:{args.port}/", flush=True)
    print("=" * 64, flush=True)
    print("  Ethics: detection only · authorized networks · no TX attacks", flush=True)
    print("=" * 64, flush=True)

    open_firewall(args.port)

    threading.Thread(target=bandwidth_loop, daemon=True).start()

    if not args.no_sniff and SCAPY and NPCAP:
        threading.Thread(target=sniffer_loop, args=(args.iface,), daemon=True).start()
    else:
        with STATE.lock:
            STATE.mode = "virtual" if STATE.virtual else "limited"
            STATE.sniffer_ok = False
            if not SCAPY:
                STATE.sniffer_error = "scapy missing"
            elif not NPCAP:
                STATE.sniffer_error = "npcap missing"
            else:
                STATE.sniffer_error = "sniff disabled"

    if STATE.virtual or args.virtual:
        threading.Thread(
            target=virtual_radar_loop, args=(args.virtual_interval,), daemon=True
        ).start()
        # seed one alert immediately so Fire shows data on first poll
        STATE.push_alert(
            source="de:ad:00:00:00:01",
            dest="ff:ff:ff:ff:ff:ff",
            reason=7,
            kind="virtual_deauth",
            note="backend online — virtual seed event",
        )

    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[*] Shutting down…", flush=True)
        httpd.shutdown()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception:
        traceback.print_exc()
        raise SystemExit(1)
