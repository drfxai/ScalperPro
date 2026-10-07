# Scalper Pro Engineering Decisions

1. Android package: `ai.drfx.scalperpro`.
2. Next public version: V1.0.1 / versionCode 2. V1.0.0 remains immutable.
3. Jetpack Compose is the application UI layer.
4. OpenGL ES 3.0 is used for the live neural-galaxy renderer to avoid a flat 2D imitation.
5. Secrets remain server-side.
6. Gemini default model is `gemini-3.8-flash`.
7. 9Router integration is OpenAI-compatible and server-configurable; Smart/Combo identifiers are not hardcoded because they are deployment-specific.
8. Market and signal values are not fabricated when no live provider is configured.
9. Strategy Specification is the source of truth for generated Pine/MQL5 foundations.
10. Product priority is Neural Galaxy + Scalper AI + beginner Quant Lab; News/Markets/Education are secondary until this core is stable.
11. DrFXQuant reuse is pinned to commit `caba017c1c1717a3ea5d0a4a7f00bee258cf2da4`; reusable code/patterns may be migrated, but finance/payment/autotrade systems and secrets are excluded.
12. The legacy DrFXQuant Quant Coder is reused locally for Pine-style chart/runtime compatibility diagnostics. It is not presented as TradingView compilation.
13. Pine visual/runtime compatibility and deterministic strategy backtesting remain separate engines because the reused Quant Coder does not simulate `strategy.entry/exit/close` order execution.
14. TradingView Lightweight Charts Android 5.2.0 is the in-app chart presentation layer; required TradingView attribution must be visible in the final product.
15. Scalper AI uses a supervisor + specialist-agent workflow inspired by DrFXQuant Hyperion, but live model calls remain behind the trusted backend.
16. Release tags and assets are immutable. V1.0.1 is created only from its fixed tag after validation; existing releases are never retargeted or overwritten.
17. A shared Gateway secret is never embedded in the APK. Production authentication must use user/session identity or an edge access policy.

