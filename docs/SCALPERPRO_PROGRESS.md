# Scalper Pro V1.0.1 Progress

## Product priority

Current product-owner priority:

1. Neural Galaxy Home
2. Scalper AI
3. AI Indicator / Strategy Builder
4. Pine Script Lab
5. In-app Quant Runtime + Chart Sandbox
6. MQL5 / MetaTrader translation
7. News / Markets / Education after the core creation workflow is stable

The product remains beginner-focused and educational. Finance/account aggregation,
wallets, payments, portfolio accounting and automatic live trading are outside the
current scope.

## Stable foundation

- SP-P00-T01 Repository / Android baseline — COMPLETED
- SP-P01-T01 Design system and app shell — COMPLETED
- SP-P02-T01 3D OpenGL ES Neural Galaxy — IMPLEMENTED
- SP-P02-T02 Clockwise Y-axis auto orbit — IMPLEMENTED
- SP-P02-T03 X/Y/Z gestures + pinch zoom — IMPLEMENTED
- SP-P02-T04 Star field / neural links / glow — IMPLEMENTED
- SP-P02-T05 Functional node selection/navigation — IMPLEMENTED
- SP-P03-T01 Provider-agnostic AI gateway architecture — IMPLEMENTED
- SP-P03-T02 Gemini route — IMPLEMENTED / DEPLOYMENT_REQUIRED
- SP-P03-T03 9Router Smart/Combo routes — IMPLEMENTED / DEPLOYMENT_REQUIRED
- SP-P24-T01 Baseline Android unit tests — COMPLETED
- SP-P24-T02 Baseline lint — COMPLETED
- SP-P24-T03 Baseline installable debug APK build — COMPLETED
- SP-P25-T01 Existing V1.0.0 prototype GitHub Release — COMPLETED

Main commit `c76ff2465ce8ca086f4990aa84173d9597748081` passed Android CI after
the TradingView Lightweight Charts v5 integration fix.

## DrFXQuant reuse

Pinned source:
`DrFXAi/DrFXQuant@caba017c1c1717a3ea5d0a4a7f00bee258cf2da4`

- SP-PRIO-T01 DrFXQuant repository/archive audit — COMPLETED
- SP-PRIO-T02 Reuse/security boundary documented — COMPLETED
- SP-PRIO-T03 Quant Coder runtime reused as local Android asset — IMPLEMENTED
- SP-PRIO-T04 Hyperion supervisor/specialist architecture adapted — IMPLEMENTED
- SP-PRIO-T05 GODMODE engineering patterns integrated into Pine QA — IMPLEMENTED

Explicitly excluded from Scalper Pro:
wallet/payment/QNTM ledger logic, withdrawals, automatic live trading, deployment
credentials, certificates and private keys.

## Neural Galaxy

- SP-PRIO-T10 AI / Pine / Quant Lab / MQL5 priority clusters — IMPLEMENTED
- SP-PRIO-T11 Core/primary/secondary node tiers — IMPLEMENTED
- SP-PRIO-T12 Radial node glow and dense galaxy particles — IMPLEMENTED
- SP-PRIO-T13 Node selection routes into AI/Pine/Strategy/Chart/MQL5 — IMPLEMENTED
- SP-PRIO-T14 Home core-cluster shortcuts — IMPLEMENTED
- SP-PRIO-T15 Home AI prompt handoff — IMPLEMENTED
- SP-PRIO-T16 Damped XYZ inertia + user pause/play/reset orbit controls — IMPLEMENTED
- SP-PRIO-T17 Purpose-designed light/dark Galaxy palettes — IMPLEMENTED
- SP-PRIO-T18 Physical-device FPS/gesture/fallback validation — PENDING_DEVICE

## Scalper AI

- SP-PRIO-T20 Specialist workflow planner — IMPLEMENTED
- SP-PRIO-T21 Requirements / Indicator / Strategy specialists — IMPLEMENTED
- SP-PRIO-T22 Pine / TradingView QA / Quant Runtime specialists — IMPLEMENTED
- SP-PRIO-T23 Backtest / MQL5 / Beginner Coach specialists — IMPLEMENTED
- SP-PRIO-T24 Backend staged `/v1/ai/lab` workflow — IMPLEMENTED
- SP-PRIO-T25 Android trusted-HTTPS AI Gateway client — IMPLEMENTED
- SP-PRIO-T26 AI Builder route selector and staged result UI — IMPLEMENTED
- SP-PRIO-T27 AI-generated Pine handoff into Pine Lab — IMPLEMENTED
- SP-PRIO-T28 General Scalper AI conversational screen — IMPLEMENTED
- SP-PRIO-T29 Live provider execution — BLOCKED_BY_GATEWAY_DEPLOYMENT

No Gemini/9Router API secret is stored in the APK. The Android build receives only an
optional non-secret HTTPS gateway URL through `SCALPER_AI_GATEWAY_BASE_URL`.

## Indicator / Pine / Strategy Lab

- SP-PRIO-T30 Modular tools / strategy archetypes / timeframe catalog — IMPLEMENTED
- SP-PRIO-T31 Typed Indicator Specification — IMPLEMENTED
- SP-PRIO-T32 Pine v6 indicator generator — IMPLEMENTED
- SP-PRIO-T33 TradingView QA static analyzer — IMPLEMENTED
- SP-PRIO-T34 TradingView-aligned future-leak / confirmed-HTF review — IMPLEMENTED
- SP-PRIO-T35 Strategy Specification explicit short-entry rules — IMPLEMENTED
- SP-PRIO-T36 Strategy execution assumptions (commission/slippage) — IMPLEMENTED
- SP-PRIO-T37 Pine strategy session/cooldown/confirmed-bar generation — IMPLEMENTED
- SP-PRIO-T38 Pine ATR/fixed stop + fixed-R/fixed target generation — IMPLEMENTED
- SP-PRIO-T39 Safe long/short `strategy.exit` generation — IMPLEMENTED
- SP-PRIO-T40 Unsupported structure/custom/trailing logic is reported, never invented — IMPLEMENTED
- SP-PRIO-T41 Official TradingView Pine engineering standard documented — IMPLEMENTED
- SP-PRIO-T42 Conservative next-tick strategy fill default; same-bar fill is explicit — IMPLEMENTED

## Quant Runtime / Chart Sandbox

- SP-PRIO-T50 Local DrFXQuant Quant Coder WebView bridge — IMPLEMENTED
- SP-PRIO-T51 Compile/run diagnostics and activity report — IMPLEMENTED
- SP-PRIO-T52 TradingView Lightweight Charts Android 5.2.0 — IMPLEMENTED
- SP-PRIO-T53 Runtime candle/plot mapping — IMPLEMENTED
- SP-PRIO-T54 Runtime `plotshape` mapping to chart markers — IMPLEMENTED
- SP-PRIO-T55 Runtime label mapping to chart markers — IMPLEMENTED
- SP-PRIO-T56 TradingView attribution logo kept visible — IMPLEMENTED
- SP-PRIO-T57 line/box/fill advanced primitive mapping — NOT_STARTED
- SP-PRIO-T58 Production historical candle provider — NOT_STARTED

The local Quant Coder is a Pine-style compatibility/runtime engine. It is not called
the TradingView compiler and it does not simulate TradingView strategy orders.

## MQL5 / MetaTrader

- SP-PRIO-T60 Conservative Pine-condition translator — IMPLEMENTED
- SP-PRIO-T61 EMA/SMA/RSI/ATR/OHLC confirmed-bar operand mapping — IMPLEMENTED
- SP-PRIO-T62 CTrade EA scaffold — IMPLEMENTED
- SP-PRIO-T63 new-bar / spread / duplicate-position guards — IMPLEMENTED
- SP-PRIO-T64 deterministic tick-value based risk sizing — IMPLEMENTED
- SP-PRIO-T65 CopyBuffer indicator helper path — IMPLEMENTED
- SP-PRIO-T66 fixed/ATR stop and fixed-R/fixed target mapping — IMPLEMENTED
- SP-PRIO-T67 unsupported condition/stop/target translation fails closed — IMPLEMENTED
- SP-PRIO-T68 MetaEditor compiler worker — NOT_STARTED

Generated MQL5 remains STATIC_ANALYZED until a real MetaEditor worker returns a compile result.

## Merge / release status

- PR #7 — Complete Scalper AI and advanced Quant Lab core — MERGED
- PR #7 final Android CI — PASSED
- Main Android CI after merge — PASSED
- Main merged commit: `b8817a32485f4d1bdfac5e23a480821ff4632486`
- V1.0.0 release asset refresh — PASSED

## Release refresh verification

- Release validation — PASSED
- Installable APK build — PASSED
- Unsigned release APK build — PASSED
- Release upload — PASSED
- Refreshed installable APK SHA-256 — `f591070754bb232b8563e60f9f44c0554594b065649bf065059321e74d17d218`
- Refreshed unsigned APK SHA-256 — `a6d7fa233105dbafccc2833d21d0b4b93124d0264b049ae9eb918d9e1de41dee`
- Release metadata/tag alignment — FINALIZED_BY_VALIDATED_WORKFLOW

## Exact next action

1. Align the public `v1.0.0` tag/release target with this validated merged build.
2. Verify final release assets and source-code tag alignment.
3. Perform physical-device Neural Galaxy + Quant Lab validation.
4. Keep live AI disabled until the trusted HTTPS gateway is deployed/configured.
5. Only after the AI/creation core is stable, resume News/Markets/Education completion.
  
## Strategy Specification Backtest integration

- SP-PRIO-T70 Strategy expression engine for EMA/SMA/RSI/ATR/OHLC — COMPLETED
- SP-PRIO-T71 Boolean AND/OR + crossover/crossunder support — COMPLETED
- SP-PRIO-T72 Strategy Specification compatibility checker — COMPLETED
- SP-PRIO-T73 Deterministic Strategy Specification backtest adapter — COMPLETED
- SP-PRIO-T74 Backtest strategy lifecycle reset/callback contract — COMPLETED
- SP-PRIO-T75 Synthetic beginner research dataset + explicit costs — COMPLETED
- SP-PRIO-T76 Backtest metrics / recent-trades Quant Lab UI — COMPLETED
- SP-PRIO-T77 Unsupported structure/custom/trailing semantics fail closed — COMPLETED
- SP-PRIO-T78 PR #8 initial merge — MERGED_WITH_COMPILE_ERROR
- SP-PRIO-T79 PR #9 compile/parser hotfix — COMPLETED
- SP-PRIO-T80 PR #9 Android unit tests/lint/APK — PASSED
- SP-PRIO-T81 Main Android CI after hotfix merge — PASSED
- SP-PRIO-T82 V1.0.0 release refresh with Backtest Lab — COMPLETED

Main recovery commit after PR #9:
`785039e8651f18cd4f51396a65baaf1233596bdd`

The Backtest Lab remains intentionally bounded. Unsupported structure/custom/trailing
semantics are blocked rather than approximated, and the bundled dataset is explicitly
synthetic research data rather than live/historical provider data.

## Exact next action

1. Refresh V1.0.0 release assets from the green Backtest-Lab main.
2. Verify tag/release target and SHA256 assets.
3. Continue Quant Lab with local historical-data import / robustness tooling.
4. Physical-device Neural Galaxy + Quant Lab validation remains required.
5. Live AI remains disabled until the trusted HTTPS gateway is deployed/configured.



## Local historical-data workflow — PR #11

- SP-PRIO-T83 Local-only OHLC CSV parser — IMPLEMENTED
- SP-PRIO-T84 Bounded Android Storage Access Framework loader — IMPLEMENTED
- SP-PRIO-T85 CSV timestamp/ohlc/volume validation — IMPLEMENTED
- SP-PRIO-T86 Duplicate timestamp rejection + time sorting warning — IMPLEMENTED
- SP-PRIO-T87 UTF-8 BOM + decimal-comma handling — IMPLEMENTED
- SP-PRIO-T88 Backtest Lab local CSV / sample dataset switch — IMPLEMENTED
- SP-PRIO-T89 Local-file privacy labeling — IMPLEMENTED
- SP-PRIO-T90 Historical CSV unit tests — IMPLEMENTED
- SP-PRIO-T91 PR #11 Android CI — TESTING

Important boundary:
The local historical-data importer reads the user-selected file through Android's
Storage Access Framework and feeds candles directly into the on-device Backtest Lab.
This path does not upload the CSV to the Scalper backend or AI Gateway.

Current branch:
`codex/local-historical-data-lab`

Current PR:
#11 — Add private local historical CSV testing to Quant Lab

Exact next action:
Run/fix PR #11 unit tests, lint and APK build. Merge only when green. Then verify main CI
and refresh V1.0.0 only after the complete local-import wave is present on main.

## 2026-10-07 state reconciliation and AI Gateway hardening

- PR #11 merged, then PR #12 fixed its typed-expression compile regression.
- Current verified main is `22fc038202980e100065bd1995c0bc56128f84a7`.
- Android CI on that main commit passed; there are no open pull requests.
- Public `v1.0.0` still targets older commit `f31bf150d42960c85a6c16cf6680c73532e5e2e7`.
- V1.0.0 is no longer mutated. The next release is V1.0.1 / versionCode 2.
- SP-PRIO-T91 PR #11/#12 recovery and main CI — COMPLETED.
- SP-PRIO-T92 Gateway request correlation and strict route/task validation — IMPLEMENTED.
- SP-PRIO-T93 Gateway provider timeout, bounded response parsing and empty-response rejection — IMPLEMENTED.
- SP-PRIO-T94 Cloudflare per-IP/per-route AI rate limiting and observability — IMPLEMENTED.
- SP-PRIO-T95 Immutable V1.0.1 tag-triggered release workflow — COMPLETED.
- Local Android unit tests, lint and debug APK build — PASSED.
- Worker JavaScript syntax and repository diff checks — PASSED.

Exact next action:
Push the Gateway/release-hardening branch, open a PR, wait for Android CI, and merge only
when green. Create the immutable `v1.0.1` tag from the resulting green main commit; do not
retarget or overwrite `v1.0.0`. Gateway deployment/authentication and physical-device
Neural Galaxy validation remain separate required tasks.

## V1.0.1 completion checkpoint

- PR #13 — MERGED after green Android CI.
- Green main/release commit — `bce436a154e998c91299e1db7caaf93fe1fce537`.
- Main Android CI — PASSED.
- Immutable annotated tag `v1.0.1` — CREATED at the green merge commit.
- Release V1.0.1 validation, unit tests, lint, debug APK and unsigned release APK — PASSED.
- GitHub Release V1.0.1 with `SHA256SUMS` — PUBLISHED.

Exact next action:
Deploy and validate the trusted Gateway with real user/session or edge authentication,
then perform physical-device Neural Galaxy FPS/gesture/lifecycle validation. Do not claim
live Gemini/9Router, TradingView compile, or MetaEditor compile verification until those
external systems return real results.

## Trusted Gateway authentication wave — 2026-10-07

Authoritative checkpoint superseding historical next-action sections above:
main `4e0d7a2f58a4b5408f5da5e1df70f79d7ae6e802`, PR #14 merged, no open PRs
at verification; main Android CI 37594349247 passed. V1.0.1 release CI 37593390276
passed at `bce436a`; V1.0.0/V1.0.1 tags/assets remain untouched.

- SP-PRIO-T96 Signed human Cloudflare Access JWT authentication — IMPLEMENTED / LOCAL_TEST_PASSED.
- SP-PRIO-T97 Required identity-based limiter and deployment configuration preflight — IMPLEMENTED / LOCAL_TEST_PASSED.
- SP-PRIO-T98 Sanitized structured failures/logs and provider body deadline — IMPLEMENTED / LOCAL_TEST_PASSED.
- SP-PRIO-T99 Gateway CI and no-provider cloud validation script/runbook — IMPLEMENTED / CLOUD_VALIDATION_PENDING.

Nine Node tests passed with locally signed test JWTs and mocked provider/key endpoints.
Cloud deployment is blocked by absent target account/domain/Access policy/provider
configuration; no external provider execution is claimed. Native Android Access session
acquisition/refresh is not implemented and the default Gateway URL must remain unset.

Gateway PR: #15, branch `codex/trusted-gateway-auth`.

Exact next action: verify Gateway and Android CI, merge only after
both pass; then configure staging Access/domain/provider models/secrets and record the
cloud validation script evidence. Global spending budgets and Android session integration
remain required before production app enablement. Physical-device validation remains pending.

### Gateway implementation completion checkpoint — PR #15

Implementation commit `291e7f77babd557c4be33b0f93f2d906f65f8367` passed Gateway CI
37597066808 and Android CI 37597066813 (unit tests, lint, installable APK).
SP-PRIO-T96/T97/T98 implementation is complete; SP-PRIO-T99 cloud validation remains
BLOCKED_CONFIGURATION. This checkpoint also tightens the cloud validator to use empty
messages for anonymous probes, guaranteeing that an accidentally unprotected route still
cannot reach the provider through a validation probe. Final PR revision must be green
before merge; GitHub PR #15 retains exact check/merge evidence.

Next work is staging account/domain/Access policy/provider configuration and recorded
cloud validation, followed by native Android session flow, Lab timing contract and global
provider spending budgets. No release/tag changes; no real provider/compiler/device result.
