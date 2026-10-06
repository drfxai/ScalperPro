# Scalper Pro Engineering Decisions

1. Android package: `ai.drfx.scalperpro`.
2. Public version: V1.0.0 / versionCode 1.
3. Jetpack Compose is the application UI layer.
4. OpenGL ES 3.0 is used for the live neural-galaxy renderer to avoid a flat 2D imitation.
5. Secrets remain server-side.
6. Gemini default model is `gemini-3.8-flash`.
7. 9Router integration is OpenAI-compatible and server-configurable; Smart/Combo identifiers are not hardcoded because they are deployment-specific.
8. Market and signal values are not fabricated when no live provider is configured.
9. Strategy Specification is the source of truth for generated Pine/MQL5 foundations.
