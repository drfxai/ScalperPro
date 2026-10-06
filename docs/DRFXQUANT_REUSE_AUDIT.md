# DrFXQuant Reuse Audit for Scalper Pro

Source repository:
- DrFXAi/DrFXQuant
- pinned source commit: caba017c1c1717a3ea5d0a4a7f00bee258cf2da4

Uploaded archive review:
- DrFXQuant-main.zip was inspected in parallel with the GitHub repository.
- Key source files matched the GitHub repository by Git blob hash, including:
  - public/quant-coder.js
  - services/quant-coder.js
  - routes/chats.js
  - services/ai-knowledge.js
  - services/mt5-bridge.js
  - mt5/DrFXQuant.mq5
  - GODMODE-v1.0.0-drfx.pine
  - Hyperion_Ai/Hyperion_Ai/agents.py

## Reuse policy

Scalper Pro will reuse the strongest engineering ideas and owned source from
DrFXQuant, but it will not copy unrelated wallet/payment/live-trading complexity
into a beginner-focused educational product.

### Reuse now

1. Quant Coder Pine runtime
   - lexer
   - Pratt parser
   - Pine v2-v6 normalization
   - per-bar series evaluator
   - history operator semantics
   - common ta.* and math.* functions
   - request.security higher-timeframe evaluation
   - plots, shapes, candle coloring, lines/boxes/labels where supported
   - input extraction
   - explicit warnings for unsupported constructs
   - synthetic-bar smoke testing
   - output-activity diagnostics
   - performance fuse / heavy-script policy
   - strategy/indicator category classifier

2. Hyperion multi-agent architecture
   - supervisor pattern
   - specialist agents
   - bounded routing/recursion
   - shared state
   - checkpoints
   - provider abstraction

   Scalper Pro adapts this concept into:
   - AI Supervisor
   - Requirements Analyst
   - Indicator Architect
   - Strategy Strategist
   - Pine Engineer
   - TradingView QA Reviewer
   - Backtest Analyst
   - MQL5 Translator
   - Beginner Coach

3. DrFX AI prompt composition
   - persona
   - product rules
   - relevant knowledge pages
   - current task context
   - user request

   This becomes contextual prompt assembly for Scalper AI instead of one giant
   static prompt.

4. GOD MODE Pine source as a quality corpus
   Reuse the engineering patterns, not only the visual indicator:
   - explicit presets
   - confirmed HTF logic
   - no-future-leak / confirmed-HTF discipline
   - session filters
   - ADX/volatility regime filters
   - loss cooldown
   - ATR targets/stops
   - state hygiene
   - bar-close alert discipline
   - diagnostic tags
   - beginner-facing tooltips

5. MetaTrader / MQL5 knowledge
   - symbol normalization lessons
   - one-instance/account safety lessons
   - WebRequest configuration guidance
   - order/risk/spread/slippage validation concepts
   - status/heartbeat/reporting architecture

   Scalper Pro initially uses these for code-generation QA and education.
   It does NOT enable automatic live trading in the educational V1 scope.

6. Trading chart work
   - DrFXQuant's in-app chart/indicator sandbox concept is retained.
   - Scalper Pro uses TradingView Lightweight Charts Android as the native chart
     presentation layer.
   - The Quant Coder runtime is hosted locally and will feed generated indicator
     outputs into the chart.

## Explicitly excluded

Do not migrate these into Scalper Pro unless product scope changes:

- QNTM ledger
- wallets
- payments
- withdrawals
- subscriptions/payment settlement logic
- internal exchange
- real-money Quant Option
- automatic execution/autotrade
- social/community plumbing not required by the learning product
- committed certificates or private keys
- deployment credentials

## Security boundary

The DrFXQuant tree contains security-sensitive historical/deployment material,
including a private-key-shaped file under Hyperion_Ai configuration. No
certificate, private key, API credential, token, database credential, or .env
secret may be copied into Scalper Pro.

Only source logic, tests, schemas, prompts, patterns and non-secret educational
assets may be reused.

## Architectural destination

Scalper Pro priority architecture:

Neural Galaxy Home
    -> Scalper AI Supervisor
        -> Requirements Analyst
        -> Indicator Architect / Strategy Strategist
        -> Pine Engineer
        -> TradingView QA
        -> Quant Coder Runtime
        -> In-App Chart Sandbox
        -> Backtest / Diagnostics
        -> MQL5 Translator
        -> Beginner Explanation

The source of truth remains structured domain data:

Natural-language idea
    -> Indicator/Strategy Specification
    -> Pine v6
    -> static QA
    -> Quant Coder compatibility/runtime test
    -> chart preview
    -> deterministic backtest where strategy semantics are supported
    -> optional MQL5 translation

## Important compatibility rule

The legacy Quant Coder intentionally does not simulate strategy.entry /
strategy.exit / strategy.close as a broker/backtest engine.

Scalper Pro therefore keeps two engines separate:

- Quant Coder Runtime: Pine-style indicator/chart rendering and diagnostics.
- Scalper Backtest Engine: deterministic Strategy Specification simulation.

This separation is intentional and prevents false claims that a locally rendered
Pine strategy has been compiled or backtested by TradingView.
