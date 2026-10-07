# Scalper Pro Architecture

## V1.0.1

Android: Kotlin + Jetpack Compose with a dedicated OpenGL ES 3.0 neural-galaxy renderer.

Layers:

UI -> domain/core -> repositories/providers.

The current Android shell contains deterministic Strategy Specification utilities,
Pine/MQL5 generation foundations and risk math. Live market/news/signal services are
provider interfaces to be connected without fabricating data.

AI:

Android -> trusted Scalper AI Gateway -> Gemini 3.8 Flash or 9Router Smart/Combo.

No provider secret belongs in the APK.
The Gateway validates route/task inputs, bounds provider responses, applies upstream
timeouts and Cloudflare rate limiting, and propagates request IDs. Production access
still requires user/session authentication or an edge access policy at deployment.

Home:

Compose overlays + native GLSurfaceView/OpenGL ES renderer. The graph auto-orbits
clockwise around Y and supports X/Y drag, pinch zoom and two-finger Z twist.

## Trusted Gateway pilot

Custom domain + Cloudflare Access -> Worker verifies RS256 human JWT -> required subject/route limiter -> bounded provider adapter. `/health` is liveness; authenticated `/ready` is configuration only. Native Android session integration and global budgets remain pending. See backend/README.md for deployment gates.
