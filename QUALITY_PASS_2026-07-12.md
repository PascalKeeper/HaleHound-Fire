# Quality & performance pass — 2026-07-12

## Research (cutting-edge / current best practice)

| Source | Takeaway applied |
|--------|------------------|
| Android Baseline Profiles / Startup Profiles (2025–2026) | Hot-path awareness; release R8 minify; document profile stubs for future AOT |
| Duolingo / Meta / Google (~25–40% startup wins with profiles) | Ship lean debug APK now; enable R8 on release builds |
| Low-RAM device guidance | DiffUtil lists, UI coalesce, bounded concurrency on Fire 7 (~1.8GB) |
| Velora latency stack | Profiles (ultra/balanced), budgets, parallel-with-cap, skip redundant UI, tcpNoDelay, shorter timeouts |

## Velora → Fire mapping

| Velora idea | Fire implementation |
|-------------|---------------------|
| Latency profiles | `LatencyProfiles.ULTRA` / `BALANCED` |
| Budget parallel work | Semaphore on harden port probes (max 6 ultra) |
| Don’t block wait threads | BLE scan uses `delay()` not `Thread.sleep` |
| Coalesce updates | Guardian `uiSignature()` skips identical frames |
| Fast timeouts field mode | Ultra connect/probe timeouts tuned for MT8168 |
| Soft-ack | Existing “AUDITING…” / terminal progress strings |

## Code quality fixes this pass

1. BLE coroutine wait (no raw Thread)  
2. HardeningEngine bounded probes + tcpNoDelay  
3. Guardian gateway probe cadence slower than radio sample  
4. DiffUtil `ListAdapter` for scan rows  
5. Release minify enabled  
6. TERM: `perf ultra|balanced|status`  

## Device side (already applied)

- Fire OS debloat (web+ops profile), anim 0.5, ~150 disabled pkgs, ~0.8–1GB MemAvailable  

## Not over-engineered (intentionally skipped)

- Full macrobenchmark module (needs device lab + compose/UI automation)  
- Full Baseline Profile Gradle plugin (heavy for debug sideload workflow)  
- Kotlin multiplatform rewrite  

## Verification

- `./gradlew :app:assembleDebug` must succeed  
- Install + TERM `perf` / `harden` smoke on KFQUWI  
