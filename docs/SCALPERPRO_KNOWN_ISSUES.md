# Known Issues / Release Blockers

## Reconciled release state (2026-10-07)

- Main `22fc038` is CI-green after PR #12; no pull requests were open at reconciliation time.
- Public V1.0.0 still targets older commit `f31bf150`; it remains immutable rather than being retargeted.
- V1.0.1 workflow is prepared but must not run until its change is merged with green CI and
  `v1.0.1` is created from that green main commit.
- AI Gateway rate limiting is configured but requires deployment validation in the target
  Cloudflare account. Production user/session or edge authentication is still required.
- V1.0.1 is published from green commit `bce436a`; its unsigned release APK is not a
  substitute for production signing. The installable artifact uses CI debug signing.

## Core priority blockers

- PR #7 is merged; Android CI is green; V1.0.0 validation/build/tag alignment/release upload are complete. Physical-device validation remains pending.
- The trusted Scalper AI Gateway code path exists, but the default Android build has no
  `SCALPER_AI_GATEWAY_BASE_URL`. Live Gemini/9Router calls therefore remain safely unavailable
  until an HTTPS gateway is deployed/configured.
- TradingView Pine output is generated and statically reviewed but is not called
  compile-verified without an actual TradingView compiler/result.
- MQL5 output is generated and statically reviewed but is not called compile-verified
  until a real MetaEditor/MT5 compile worker is connected.
- The DrFXQuant Quant Coder is a local Pine-style compatibility/runtime engine, not the
  TradingView compiler and not a strategy-order simulator.
- Quant Runtime plots, plotshape signals and labels now map to the in-app chart. Advanced
  line/box/fill/table primitives are not yet mapped through the native chart layer.
- The Chart Sandbox uses deterministic sample candles unless a production/historical candle
  provider is later connected.
- Physical-device FPS, gesture, background/foreground and low-power validation for the
  Neural Galaxy remains pending.
- Production Android signing key is not stored in the repository.

## Secondary modules deferred by product priority

- Live market/news/signal providers are not configured.
- Chart Vision live multimodal submission and production market/news enrichment are pending.
- Voice recording/upload remains unimplemented.
- Journal encrypted persistence/account sync is incomplete.
- Notification/DataStore persistence is incomplete.
- Provider-backed global search is incomplete.

These secondary items are intentionally behind Neural Galaxy, Scalper AI and Quant Lab work.

## Licensing / attribution

TradingView Lightweight Charts Android remains configured with
`attributionLogo = true`; attribution must remain visible in release builds.

- The Strategy Specification Backtest Lab is merged and CI-green. It currently uses a deterministic synthetic research
  dataset until a production or local-import historical candle source is connected.
- Internal expression support is intentionally bounded to explicit deterministic constructs
  (common OHLC comparisons, EMA/SMA/RSI/ATR, boolean composition and crossover/crossunder).
  Unsupported structure/custom/trailing rules are blocked instead of approximated.
- Internal backtest results are not labeled TradingView Strategy Tester results.

- Local CSV historical-data import is implemented in PR #11 and intentionally remains an
  on-device Backtest Lab path. It is not a live market-data feed.
- The importer intentionally supports simple OHLC CSV layouts rather than a general spreadsheet
  engine. Complex quoted/multi-line CSV dialects are outside the V1 parser contract.
