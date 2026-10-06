# Known Issues / Release Blockers

- Production Android signing key is not stored in the repository. CI can produce
  an installable debug-signed APK and an unsigned release APK, but stable production
  update distribution requires a persistent signing key.
- The trusted AI gateway is included but must be deployed/configured before Android AI
  requests can become live.
- Live market/news/signal data providers are not configured; the UI explicitly shows
  unavailable values rather than inventing market data.
- Chart Vision now has Android image selection, MIME/size validation and a typed context
  contract. Live multimodal submission and production market/news enrichment are not yet connected.
- Voice capture remains a UI entry point; the recording/upload pipeline is not implemented yet.
- Trader Journal has domain/statistics logic and a session workflow, but encrypted Room
  persistence and account synchronization are not yet implemented.
- Pine and MQL5 outputs are generated/static-analyzed only until external compiler workers
  are configured.
- Backtest core is deterministic, but production historical-data ingestion and the
  Strategy Specification expression interpreter are still pending.
