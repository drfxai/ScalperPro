# Scalper AI Gateway

Server-side Cloudflare Worker for chat and staged AI Lab workflows. Provider and
shared Gateway secrets never belong in the APK. This wave implements an Access
protected pilot; it does not implement native Android sign-in or prove cloud deployment.

## Authentication and configuration

Every AI request and `/ready` requires a signed human Cloudflare Access application
JWT in `Cf-Access-Jwt-Assertion`. RS256 signature, pinned issuer, application audience,
expiry, issued-at, not-before, subject and human email claims are checked against keys
from the configured team domain. Spoofed email headers and service tokens are not accepted.
Keys are cached for 60 seconds per isolate. Unknown keys fail closed; rotation may cause
a short rejection window until the cache expires. Key service outages return 503.

1. Configure a Cloudflare Access self-hosted application for the Gateway custom domain
   and an explicit allowed-user identity policy. Do not use an Access bypass policy.
2. Set `ACCESS_TEAM_DOMAIN` to the HTTPS team domain and `ACCESS_AUD` to the application's
   64-character audience tag. These are non-secret server configuration values.
3. Add the actual custom domain route to Wrangler configuration. `workers_dev` and
   preview URLs are disabled to avoid extra public origins. No domain is invented here.
4. Set `GEMINI_MODEL` to an identifier verified against the actual provider/account.
   The previous `gemini-3.8-flash` assumption is no longer an active default.
5. Set provider credentials only using `wrangler secret put GEMINI_API_KEY` and/or
   `wrangler secret put NINEROUTER_API_KEY`. Configure 9Router HTTPS base URL and supported
   Smart/Combo models. Credentials in URLs and redirects are rejected.
6. Review the account-local rate-limit namespace ID (`1001`) and configure a separate
   namespace for staging. Deploy the reviewed green commit with Wrangler.
7. Run the read-only deployment validator below and record the deployment version,
   commit, timestamp and JSON evidence. Do not enable Android AI based on liveness alone.

`GET /health` is liveness only. Authenticated `GET /ready?mode=gemini` validates local
configuration and limiter presence, not provider credentials, model availability or
actual quota behavior. Smart/Combo modes are independently selectable.

## Deployment validation

With `GATEWAY_URL` (HTTPS origin), `ACCESS_SESSION_JWT` (short-lived human Access session)
and optional `GATEWAY_MODES=gemini,9router-smart,9router-combo` in the shell environment:

```bash
node backend/validate-deployment.mjs
```

The script rejects anonymous access, verifies authenticated configuration, then submits
empty messages until chat quota rejection is observed. It never invokes a provider.
It consumes the test user's chat burst allowance; use a dedicated staging user/location.
Tokens are not printed. Obtain the session through Access login; never share or commit it.
Access may redirect anonymous clients to login before the Worker, so an edge rejection
can be HTML/302; authenticated Worker errors are JSON with a request ID.

The binding limits verified subject + route to 20 requests/60 seconds per Cloudflare
location. It is permissive/eventually consistent, not a global billing quota. Lab requests
can make up to five provider calls and must be included in cost planning. Global budgets
and native Android session acquisition/refresh remain separate production blockers.

## Failure and observability contract

- 401: missing, invalid or expired Access identity.
- 400/413: invalid or oversized request (128 KB bounded body).
- 429: burst quota, `Retry-After: 60`; missing/failing limiter returns 503.
- 503: authentication service/configuration or provider configuration unavailable.
- 502: upstream failure, timeout, invalid/oversized/empty provider output. Lab stops at
  the first failed stage and returns no partially approved artifact.

Provider reads are bounded to 2 MB with a 45-second deadline including body consumption.
Only selected text/model/provider fields are returned; raw metadata is excluded. No
automatic client retries should repeat paid Lab workflows. Gemini fallback is explicitly
opt-in and only for retryable upstream HTTP errors.

Structured completion logs contain only bounded route, request ID, status, failure code
and elapsed time. No subject, email, JWT, provider credential, prompt or generated text
is deliberately logged. Review account log retention/access before launch.

## Verification boundaries

`npm test --prefix backend` uses locally signed test identities and mocked providers;
`npm run check --prefix backend` checks syntax. Neither proves real Access policy,
Cloudflare quota enforcement, live Gemini/9Router credentials or model availability.
The existing Android client has no Access login/token flow, so leave its Gateway URL
unset until native session integration is implemented and validated. No shared token workaround.

Official references:
- https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json/
- https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/
