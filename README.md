# Scalper Pro

**Scalper Pro V1.0.0** — AI Trading Intelligence by **DrFXAi**.

- Company: DrFXAi
- Telegram: DrFXAi
- YouTube: DrFXAi
- Android package: `ai.drfx.scalperpro`

## V1.0.0 foundation

The Android application includes a native OpenGL ES 3.0 **Neural Galaxy** home screen
with a living node network, Milky Way-style star field, slow clockwise Y-axis auto-orbit,
X/Y drag rotation, two-finger Z-axis twist, pinch zoom, node selection/focus, and an
AI assistant dock integrated into the first screen.

The codebase also includes:

- Markets, Signals, AI, Lab and Learn application shell
- Strategy Specification model
- Pine Script and MQL5 generation foundation
- deterministic risk calculations with tests
- secure server-side Gemini 3.8 Flash gateway
- 9Router Smart / Combo compatible gateway architecture
- engineering checkpoints and recovery documentation
- GitHub Actions Android build, lint, tests and release automation

## AI security

Provider API keys are **not** stored in the APK or repository. Deploy the trusted
`backend/` gateway and configure Gemini / 9Router credentials as server-side secrets.

## Build status

Android unit tests, lint and debug APK build are validated by GitHub Actions.

## Release signing

The automated V1.0.0 release publishes an installable CI debug-signed APK and an
unsigned release APK. A persistent production signing key must be configured before
long-term production update distribution.
