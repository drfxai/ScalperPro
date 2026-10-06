# Scalper Pro Pine / TradingView Engineering Standard

This document is the project-level quality contract for Pine Script generated,
reviewed or repaired by Scalper Pro.

Primary references are the current TradingView Pine Script documentation:

- https://www.tradingview.com/pine-script-docs/concepts/repainting/
- https://www.tradingview.com/pine-script-docs/concepts/other-timeframes-and-data/
- https://www.tradingview.com/pine-script-docs/concepts/strategies/
- https://www.tradingview.com/pine-script-docs/concepts/inputs/
- https://www.tradingview.com/pine-script-docs/writing/style-guide/
- https://www.tradingview.com/pine-script-docs/writing/limitations/
- https://www.tradingview.com/pine-script-docs/writing/profiling-and-optimization/

## 1. Version and declaration

New Scalper Pro code targets Pine Script v6 unless the user explicitly needs
legacy compatibility.

Every generated script must have one clear declaration:

- indicator(...)
- strategy(...)
- library(...)

Scalper Pro never calls generated source "TradingView compile verified" unless a
real TradingView compilation result is available.

## 2. Repainting and realtime behavior

Every signal design must state whether it intentionally uses:

- confirmed bars only
- intrabar/realtime values
- visual-only values that may move before the bar closes

For confirmed-bar trading signals, prefer an explicit bar-close contract such as
barstate.isconfirmed where appropriate.

Intrabar behavior is not automatically an error, but it must be deliberate and
explained to the user.

## 3. request.security and higher timeframes

A blanket ban on barmerge.lookahead_on is incorrect.

For a genuinely higher-timeframe request, TradingView documents the robust
non-repainting pattern as:

- request the previous confirmed HTF value using a historical offset such as [1]
- combine it with lookahead = barmerge.lookahead_on

Example concept:

request.security(symbol, higherTimeframe, close[1],
    lookahead = barmerge.lookahead_on)

Using lookahead_on on an HTF request without an appropriate historical offset can
leak future information into historical bars and must be flagged.

Using lookahead_off prevents that historical future leak, but an unoffset current
HTF value can still fluctuate on realtime bars and later repaint when the HTF bar
becomes confirmed. Scalper Pro therefore reports this as a repaint review, not as
future leakage.

If a timeframe can be selected by the user, code should validate that the
assumptions about higher/lower timeframe usage remain true.

For lower-timeframe intrabars, prefer request.security_lower_tf() when the use
case needs all available intrabars. Review array size and realtime incompleteness.

## 4. Strategy execution assumptions

TradingView strategies normally create orders on one tick and can fill them on a
later available tick. process_orders_on_close = true changes this model and can
allow same-closing-tick fills.

Scalper Pro defaults new Strategy Specifications to the more conservative
process_orders_on_close = false unless the user intentionally chooses otherwise.

If process_orders_on_close = true is used, QA must surface the assumption.

calc_on_every_tick = true must also be surfaced because historical and realtime
behavior can diverge.

## 5. Costs and backtest realism

Generated strategies should define or explicitly request realistic assumptions
for:

- commission
- slippage
- spread or equivalent execution cost where the platform/model allows it
- session restrictions
- stop and target semantics

Scalper Pro must not interpret a frictionless backtest as realistic performance.

## 6. Alerts

Alert timing must agree with signal timing.

For bar-close/confirmed signals, alerts should use a confirmed-bar contract and,
for alert() calls where applicable, a bar-close frequency.

Strategies have different recalculation and order-fill alert behavior. The
generated explanation must tell users whether alerts are signal alerts or order
fill alerts.

## 7. Inputs and beginner usability

Input declarations belong near the beginning of the script.

For multiple related inputs:

- use clear titles
- use safe min/max ranges
- group related settings
- add concise tooltips
- use inline layout where it improves readability
- avoid exposing internal implementation details as user settings

Scalper Pro prioritizes beginner-understandable names over cryptic abbreviations.

## 8. Naming and organization

Prefer TradingView style guidance:

- camelCase for variables/functions
- upper snake case for constants where useful
- descriptive suffixes when they communicate type/provenance

Recommended high-level order:

1. license/comments where required
2. version
3. declaration
4. imports/constants
5. inputs
6. functions
7. calculations
8. strategy calls
9. visuals
10. alerts

## 9. Runtime and limits

Pine runs in a constrained cloud environment.

Generated/repaired scripts should:

- prefer built-ins over avoidable manual loops
- avoid uncontrolled nested loops
- bound array/object history
- reuse calculations
- avoid repeated expensive request.* calls
- use the Pine Profiler for complex scripts
- consider explicit historical-buffer sizing only when justified

TradingView currently documents a per-loop execution limit and plan-dependent
total execution limits, so performance findings are part of correctness for
large scripts.

## 10. State hygiene

Persistent var state must be reviewed across:

- entry
- partial exit
- full exit
- stop
- target
- invalidation
- reversal
- replacement/recalculation paths

State must not survive after the condition that made it valid has ended unless
that persistence is intentional.

## 11. Scalper Pro verification levels

GENERATED_UNVERIFIED
- generated text only

STATIC_ANALYZED
- Scalper Pro QA rules executed
- local DrFXQuant runtime may have executed supported chart logic
- still not a TradingView compiler result

COMPILE_VERIFIED
- reserved for a genuine external compilation result from the relevant platform

The UI must never blur these levels.

## 12. Local Quant Runtime boundary

The reused DrFXQuant Quant Coder is valuable for local visualization and
compatibility diagnostics, but it is not TradingView's compiler and it does not
replace TradingView strategy execution.

Scalper Pro therefore separates:

- Pine visual/runtime compatibility
- deterministic internal Strategy Specification backtesting
- external TradingView compilation/Strategy Tester verification
- MQL5/MetaEditor compilation

This separation is mandatory.
