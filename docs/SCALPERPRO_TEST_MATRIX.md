# Scalper Pro Test Matrix

| Area | Test | Status |
|---|---|---|
| Baseline | Android unit tests | CI_REQUIRED_CURRENT_BRANCH |
| Baseline | Android lint | CI_REQUIRED_CURRENT_BRANCH |
| Baseline | Debug APK build | CI_REQUIRED_CURRENT_BRANCH |
| Core | Risk math deterministic unit test | IMPLEMENTED |
| Markets | Default watchlist integrity | IMPLEMENTED |
| News | FACT vs AI ASSESSMENT model separation | IMPLEMENTED |
| Signals | Valid lifecycle transition appends audit event | IMPLEMENTED |
| Signals | STOPPED is terminal | IMPLEMENTED |
| Strategy | Valid Strategy Specification passes validation | IMPLEMENTED |
| Strategy | Excessive risk is rejected | IMPLEMENTED |
| Pine | Generated artifact never claims compile verification | IMPLEMENTED |
| Pine | lookahead_on is flagged | IMPLEMENTED |
| MQL5 | Generated artifact never claims compile verification | IMPLEMENTED |
| Backtest | Same input produces identical result | IMPLEMENTED |
| Backtest | Stop wins ambiguous same-bar stop+target collision | IMPLEMENTED |
| Chart Vision | Valid PNG request contract passes | IMPLEMENTED |
| Chart Vision | Image above 10 MB is rejected | IMPLEMENTED |
| Risk | XAUUSD deterministic sizing fixture | IMPLEMENTED |
| Journal | Closed-trade statistics calculation | IMPLEMENTED |
| Journal | Small samples suppress strong behavior claims | IMPLEMENTED |
| Search | Result preserves category/source | IMPLEMENTED |
| Notifications | Quiet hours suppress normal notifications | IMPLEMENTED |
| Notifications | Critical risk alerts bypass quiet hours | IMPLEMENTED |
| Diagnostics | Sensitive attributes/text are redacted | IMPLEMENTED |
| Diagnostics | Buffer remains capacity bounded | IMPLEMENTED |
| Offline | Cache freshness FRESH/STALE/EXPIRED | IMPLEMENTED |
| Performance | Retry delay is bounded | IMPLEMENTED |
| Security | Unsupported upload MIME is rejected | IMPLEMENTED |
| AI Routing | Cross-provider fallback requires explicit enablement | IMPLEMENTED |
| Learning | Tool deep-link mapping | PENDING_UI_TEST |
| Home | OpenGL ES renderer initializes | PENDING_DEVICE |
| Home | Default Y-axis auto-orbit | IMPLEMENTED / PENDING_DEVICE |
| Home | X/Y drag | IMPLEMENTED / PENDING_DEVICE |
| Home | Pinch zoom | IMPLEMENTED / PENDING_DEVICE |
| Home | Z twist | IMPLEMENTED / PENDING_DEVICE |
| AI | Gemini gateway path | PENDING_DEPLOYMENT |
| AI | 9Router Smart/Combo path | PENDING_DEPLOYMENT |
| Priority AI | Agent workflow planner routes indicator build specialists | PENDING_UNIT_TEST |
| Priority AI | Agent workflow planner routes strategy build specialists | PENDING_UNIT_TEST |
| Indicator | Valid Indicator Specification generates Pine v6 | PENDING_UNIT_TEST |
| Indicator | Invalid Indicator Specification returns validation findings | PENDING_UNIT_TEST |
| Quant Runtime | Local engine asset loads | CI / DEVICE_PENDING |
| Quant Runtime | DrFXQuant Pine smoke compile/run report | DEVICE_PENDING |
| Quant Runtime | Unsupported constructs are surfaced, not hidden | DEVICE_PENDING |
| Chart Lab | TradingView Lightweight Charts 5.2.0 resolves/builds | CI_PENDING |
| Chart Lab | Candlestick + EMA sample renders | DEVICE_PENDING |
| Galaxy | AI/Pine/Quant priority clusters render | DEVICE_PENDING |
| Galaxy | Point-sprite glow renders without corrupting line edges | DEVICE_PENDING |
| Navigation | Selected AI/Pine/Strategy/Chart/MQL5 nodes route correctly | PENDING_UI_TEST |

