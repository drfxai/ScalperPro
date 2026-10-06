# Known Issues / Release Blockers

## Core priority blockers

- PR #7 must pass Android unit tests, lint and installable APK build before merge.
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
