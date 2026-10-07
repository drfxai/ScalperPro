# Scalper AI Gateway

This Cloudflare Worker is the trusted server-side AI gateway for Scalper Pro.

## Supported routes

- Gemini Direct — default model `gemini-3.8-flash`
- 9Router Smart — server-configured route/model identifier
- 9Router Combo — server-configured combo identifier
- optional Gemini -> 9Router fallback for retryable provider failures
- bounded provider responses and a 45-second upstream timeout
- validated route/task values and end-to-end `X-Request-ID` correlation
- per-IP, per-route Cloudflare rate limiting (20 requests/minute/location)

## Security

Provider API credentials must never be committed or embedded in the APK.

Configure them as Worker secrets:

```bash
wrangler secret put GEMINI_API_KEY
wrangler secret put NINEROUTER_API_KEY
```

9Router is OpenAI-compatible. Configure `NINEROUTER_BASE_URL` to a remotely reachable
9Router endpoint/tunnel and configure the Smart/Combo route identifiers supported by
the deployed 9Router instance.

The Android V1.0.0 client intentionally ships without provider secrets.

The rate-limit namespace ID is account-local and may be changed if `1001` is already
used by another Worker. Authentication remains a deployment requirement: protect the
production route with the product's user/session authentication or Cloudflare Access.
Do not embed a shared gateway credential in the Android APK.
