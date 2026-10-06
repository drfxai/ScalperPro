# Scalper Pro Test Matrix

| Area | Test | Status |
|---|---|---|
| Baseline | Main Android CI at c76ff246 | PASSED |
| Core merge | Main Android CI at b8817a3 | PASSED |
| Release refresh | V1.0.0 validation/build/upload | PASSED |
| Release refresh | v1.0.0 tag/metadata alignment | PASSED |
| PR #7 | Android unit tests | PASSED |
| PR #7 | Android lint | PASSED |
| PR #7 | Installable debug APK build | PASSED |
| Galaxy | OpenGL ES renderer initializes | PENDING_DEVICE |
| Galaxy | Default clockwise Y auto-orbit | IMPLEMENTED / PENDING_DEVICE |
| Galaxy | X/Y drag, pinch zoom, Z twist | IMPLEMENTED / PENDING_DEVICE |
| Galaxy | Damped rotational inertia | IMPLEMENTED / PENDING_DEVICE |
| Galaxy | Auto-orbit pause/play/reset | IMPLEMENTED / PENDING_DEVICE |
| Galaxy | Purpose-designed dark/light palettes | IMPLEMENTED / PENDING_DEVICE |
| Galaxy | AI/Pine/Quant/MQL5 node routing | IMPLEMENTED / PENDING_UI_TEST |
| Galaxy | Home core-cluster shortcuts | IMPLEMENTED |
| AI | Specialist workflow planner: indicator | IMPLEMENTED / UNIT_TEST |
| AI | Specialist workflow planner: strategy | IMPLEMENTED / UNIT_TEST |
| AI | HTTPS gateway required | IMPLEMENTED |
| AI | Gemini route | PENDING_DEPLOYMENT |
| AI | 9Router Smart route | PENDING_DEPLOYMENT |
| AI | 9Router Combo route | PENDING_DEPLOYMENT |
| AI | Home draft reaches AI composer | IMPLEMENTED / PENDING_UI_TEST |
| Indicator | Starter Indicator Specification validation | IMPLEMENTED / UNIT_TEST |
| Indicator | Pine v6 generation | IMPLEMENTED / UNIT_TEST |
| Pine QA | lookahead_on error | IMPLEMENTED / UNIT_TEST |
| Pine QA | HTF confirmation review | IMPLEMENTED / UNIT_TEST |
| Pine QA | confirmed HTF [1] + lookahead_on pattern recognized | IMPLEMENTED / UNIT_TEST |
| Pine QA | unsafe/unoffset lookahead context surfaced | IMPLEMENTED / UNIT_TEST |
| Pine QA | strategy cost review | IMPLEMENTED / UNIT_TEST |
| Strategy | excessive risk rejection | IMPLEMENTED / UNIT_TEST |
| Strategy | no invented short entries | IMPLEMENTED / UNIT_TEST |
| Strategy | commission/slippage/session/cooldown generation | IMPLEMENTED / UNIT_TEST |
| Strategy | long/short stop/target exits | IMPLEMENTED / UNIT_TEST |
| Quant Runtime | DrFXQuant local engine asset loads | PENDING_DEVICE |
| Quant Runtime | compile/run report | PENDING_DEVICE |
| Quant Runtime | unsupported constructs surfaced | PENDING_DEVICE |
| Chart Lab | TradingView Lightweight Charts resolves | PASSED_ON_MAIN / CURRENT_CI |
| Chart Lab | runtime plot mapping | IMPLEMENTED / PENDING_DEVICE |
| Chart Lab | plotshape markers | IMPLEMENTED / PENDING_DEVICE |
| Chart Lab | label markers | IMPLEMENTED / PENDING_DEVICE |
| Chart Lab | TradingView attribution visible | IMPLEMENTED / PENDING_DEVICE |
| MQL5 | generated artifact never claims compile verification | IMPLEMENTED / UNIT_TEST |
| MQL5 | EMA condition translation | IMPLEMENTED / UNIT_TEST |
| MQL5 | risk sizing/spread/position/new-bar guards | IMPLEMENTED / UNIT_TEST |
| MQL5 | CopyBuffer helper path | IMPLEMENTED / UNIT_TEST |
| Backtest | deterministic same-input result | IMPLEMENTED / UNIT_TEST |
| Backtest | conservative ambiguous stop/target resolution | IMPLEMENTED / UNIT_TEST |

| Backtest Lab | expression engine EMA/SMA/RSI/ATR/OHLC | IMPLEMENTED / UNIT_TEST |
| Backtest Lab | boolean AND/OR | IMPLEMENTED / UNIT_TEST |
| Backtest Lab | crossover/crossunder | IMPLEMENTED / UNIT_TEST |
| Backtest Lab | unsupported semantics fail closed | IMPLEMENTED / UNIT_TEST |
| Backtest Lab | repeated Strategy Specification run is deterministic | IMPLEMENTED / UNIT_TEST |
| Backtest Lab | synthetic dataset clearly labeled non-live | IMPLEMENTED / UI |
| Backtest Lab | PR #8 Android unit tests/lint/APK | CI_RUNNING |
