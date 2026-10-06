# Scalper Pro Security Threat Model

## Scope

This threat model covers the Android client, the trusted Scalper AI Gateway,
market/news/signal provider adapters, chart-image upload, strategy/code artifacts,
journal data, notifications and future administration surfaces.

## Protected assets

- authentication/session tokens
- Gemini and 9Router credential references
- user strategies and generated code
- Trader Journal content
- chart screenshots
- signal history
- provider configuration
- diagnostic data

## Trust boundaries

1. Android device -> Scalper backend / AI Gateway
2. Scalper backend -> Gemini
3. Scalper backend -> 9Router
4. Scalper backend -> market/news/signal providers
5. user-selected local files -> upload validation pipeline
6. future admin client -> privileged administration API

## Threats and required controls

### Embedded secrets

Threat:
Provider API credentials extracted from APK, source code or logs.

Controls:
- never place provider secrets in Android resources/build config/source
- keep secrets in trusted backend secret storage
- Android receives only configured/not-configured and masked references
- diagnostic redaction for authorization/token/key/password fields

### Unsafe file upload

Threat:
Oversized, unexpected or executable payloads uploaded through chart-analysis flows.

Controls:
- explicit MIME allowlist
- bounded file size
- no uploaded binary execution
- server-side content validation must repeat client-side validation
- future storage must use generated object identifiers, not user-controlled file paths

### Cross-provider data disclosure

Threat:
A user disables fallback but content is silently sent to another AI provider.

Controls:
- fallback is opt-in/configurable
- routing policy must reject cross-provider fallback when disabled
- provider used should be observable in diagnostics/provider dashboard

### Stale market data presented as live

Threat:
Offline or cached quotes create false trading context.

Controls:
- cached data carries capture timestamp and source
- explicit FRESH / STALE / EXPIRED classification
- expired data must never be labeled live

### Signal-history manipulation

Threat:
Losing signals removed from statistics.

Controls:
- append-only signal audit events
- terminal signal states cannot be reopened arbitrarily
- performance aggregation must include closed losses

### Diagnostic leakage

Threat:
Tokens or private request data retained in logs.

Controls:
- bounded diagnostics buffer
- redact sensitive keys and credential-like text
- do not log raw images, API secrets or authorization headers
- export pipeline must apply the same redaction policy

### Arbitrary code execution

Threat:
Generated Pine/MQL or uploaded binaries executed on production infrastructure.

Controls:
- generated Pine/MQL are text artifacts
- do not execute arbitrary user code in the production backend
- future compiler workers must be isolated/sandboxed with resource limits
- user-uploaded binaries remain prohibited

### Administrative API exposure

Threat:
Unauthenticated source/feature-flag/signal management.

Controls:
- no public admin mutation endpoint without strong authentication and authorization
- least-privilege roles
- audit administrative changes
- secret references, not raw secrets, in admin configuration

## Open security work

- backend authentication and authorization implementation
- production rate limiting
- encrypted persistent Journal storage
- server-side upload validation and malware/content scanning as appropriate
- secure production signing key
- external penetration/security review before a production trading-data deployment
