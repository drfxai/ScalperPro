# API Contracts

## POST /v1/ai/chat

Request:

```json
{
  "message": "Analyze XAUUSD",
  "task": "MARKET_ANALYSIS",
  "mode": "gemini"
}
```

Modes:

- `gemini`
- `9router-smart`
- `9router-combo`

Response:

```json
{
  "provider": "gemini",
  "model": "gemini-3.8-flash",
  "text": "..."
}
```

Provider secrets are never returned.
