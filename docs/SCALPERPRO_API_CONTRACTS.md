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
  "text": "...",
  "requestId": "client-or-gateway-generated UUID"
}
```

Provider secrets are never returned.
Clients send `X-Request-ID`; the Gateway echoes it in the response header and JSON body.
Invalid modes/tasks return HTTP 400, rate limits return HTTP 429, and provider timeout,
invalid JSON, oversized or empty responses fail closed as HTTP 502.
