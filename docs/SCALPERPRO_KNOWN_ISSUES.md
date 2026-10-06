# Known Issues / Release Blockers

- Production Android signing key is not stored in the repository. CI can always produce
  an installable debug-signed APK and an unsigned release APK. A stable production
  signing secret should be configured before long-term update distribution.
- The trusted AI gateway is included but must be deployed/configured before Android AI
  requests can become live.
- Live market/news/signal data providers are not configured; the UI explicitly shows
  unavailable values rather than inventing market data.
- Voice capture and chart-image upload UI entry points exist conceptually but require
  the next implementation pass for full media pipeline handling.
