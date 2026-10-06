# Known Issues / Release Blockers

- Production Android signing key is not stored in the repository. CI can produce
  an installable debug-signed APK and an unsigned release APK, but stable production
  update distribution requires a persistent signing key.
- The trusted AI gateway is included but must be deployed/configured before Android AI
  requests can become live.
- Live market/news/signal data providers are not configured; the UI explicitly shows
  unavailable values rather than inventing market data.
- Chart Vision has Android image selection, MIME/size validation and a typed context
  contract. Live multimodal submission and production market/news enrichment are pending.
- Voice capture remains a UI entry point; recording/upload is not implemented.
- Trader Journal has domain/statistics logic and a session workflow, but encrypted Room
  persistence and account synchronization are not implemented.
- Notification preferences and provider dashboard values are session/local-state only
  until DataStore/backend synchronization is added.
- Global search currently indexes local modules and Traderpedia catalog entries; provider
  and persistent user-content federation remains pending.
- Diagnostics are structured, redacted and bounded in memory. Persistent redacted export
  with device/build metadata is pending.
- Pine and MQL5 outputs are generated/static-analyzed only until external compiler workers
  are configured.
- Backtest core is deterministic, but production historical-data ingestion and the
  Strategy Specification expression interpreter are pending.
- Security threat modeling is documented, but backend authentication/authorization,
  production rate limiting and server-side upload validation still require implementation.
