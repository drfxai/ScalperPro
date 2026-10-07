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

## Authenticated Gateway contract

AI routes and GET `/ready?mode=gemini|9router-smart|9router-combo` require `Cf-Access-Jwt-Assertion` containing a valid signed human Access application session. Do not send provider keys or shared Gateway tokens. Native Android acquisition/refresh is pending. `/health` is liveness only. Readiness reports `validation: CONFIGURATION_ONLY`, never live-provider verification.

All Worker errors include `error`, `code`, `requestId`; X-Request-ID accepts 1–128 ASCII alphanumeric/underscore/hyphen characters or is replaced with a generated UUID. 401 UNAUTHORIZED; 503 AUTH_NOT_CONFIGURED/AUTH_UNAVAILABLE/RATE_LIMIT_NOT_CONFIGURED/RATE_LIMIT_UNAVAILABLE/PROVIDER_NOT_CONFIGURED; 413 REQUEST_TOO_LARGE; 429 RATE_LIMITED with Retry-After 60; 502 AI_PROVIDER_ERROR with bounded provider codes. Access edge rejection may precede Worker JSON. No raw upstream metadata or exception details are returned. Lab failure stops the workflow and returns no partial approved stages.
