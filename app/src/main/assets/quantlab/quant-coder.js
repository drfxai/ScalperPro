/* ============================================================================
 * DrFX Quant — QUANT CODER  (window.dqQuantCoder)
 * ----------------------------------------------------------------------------
 * A Pine-Script-style indicator COMPILER + runtime + converter studio.
 *
 * ENGINE (the core): lexer → Pratt parser → per-bar series evaluator.
 *   · Statements: `x = expr`, `var x = expr`, `x := expr`, `[a,b,c] = call()`,
 *     indicator()/study()/strategy() headers, plot(), plotshape()/plotchar(),
 *     plotcandle() (repaints the chart's candles — the "Color Candles" switch in
 *     SMC/structure scripts), hline(),
 *     input.*() (defaults captured; values editable per saved indicator).
 *   · Expressions: full precedence (?:, or/and/not, comparisons, + - * / %,
 *     unary, parentheses), the history operator expr[n], na/nz/iff.
 *   · Series semantics: EVERY AST node keeps a per-bar value cache, so
 *     history access and windowed ta.* math are exact, not approximated.
 *   · ta.* library: sma ema rma wma hma rsi atr tr stdev dev highest lowest
 *     change mom roc cum sum crossover crossunder cross macd(tuple) vwap-less.
 *   · math.*: abs max min round floor ceil sqrt pow exp log sign avg.
 *   · Builtins: open high low close hl2 hlc3 ohlc4 bar_index na (volume → na).
 *
 * HONEST CONVERSION: constructs outside the subset (tables, labels, alerts,
 * request.security, block if/for, strategy.*) are SKIPPED and each is listed
 * in the conversion report as a warning — the engine never silently renders
 * wrong math. What compiles is exact; what doesn't is named.
 *
 * STUDIO: paste source → Convert → live report (plots/inputs/warnings) →
 * Save to "My Indicators" (server-stored source; server never executes it).
 * Admin can Publish an indicator to every user's "Public" tab.
 *
 * CHART: quant-option.js calls dqQuantCoder.drawActive(...) each frame; runs
 * are cached per (indicator, symbol, timeframe, last-candle) so repaints are
 * free. overlay=false scripts render in a translucent inset pane, autoscaled.
 * ==========================================================================*/
(function () {
  "use strict";
  if (window.dqQuantCoder) return;

  /* ── helpers shared with the host page ─────────────────────────────────── */
  function API(p, o) { return (typeof api === "function") ? api(p, o) : Promise.reject(new Error("offline")); }

  /* STUDIO PALETTE — fixed, and deliberately not the app theme.
   *
   * TH() used to `return t` (the app's theme object). But `t` carries only text
   * and surface tokens — t1..t4, bd, pr — and has NO named accents. So every
   * `c.blue` / `c.green` / `c.red` / `c.gold` in this file evaluated to
   * `undefined`, producing CSS like:
   *
   *     background: linear-gradient(180deg, undefined, #1d4ed8)   ← invalid
   *
   * An invalid declaration is DROPPED by the browser, so the element fell back
   * to default button chrome: that is why Convert and Ok rendered as bare white
   * boxes, and why the library-row buttons had no colour. It was a bug wearing
   * the costume of a design choice.
   *
   * The palette is fixed dark on purpose. Every surface this file paints (the
   * studio overlay, the config sheet, the colour popup) is hardcoded dark, so
   * pulling TEXT colours from a light app theme would put near-black type on a
   * near-black panel. Only the brand accent is allowed in from the theme. */
  var QC_PAL = {
    panel: "#101a2e", panel2: "#0d1626", inp: "#0b1120",
    t1: "#e9f0fc", t2: "#9fb0cc", t3: "#6f819e", t4: "#55647f",
    bd: "rgba(120,140,190,.22)", bdSoft: "rgba(120,140,190,.12)",
    blue: "#3d8bff", blueD: "#1d4ed8",
    green: "#22c55e", red: "#ef4444", gold: "#f5b942", violet: "#8b5cf6"
  };
  function TH() {
    var c = {};
    for (var k in QC_PAL) c[k] = QC_PAL[k];
    // let the app's primary accent through, but only if it's a real colour
    try { if (typeof t === "object" && t && typeof t.pr === "string" && /^#|^rgb/.test(t.pr)) c.blue = t.pr; } catch (e) {}
    return c;
  }
  function ESC(s) { return String(s == null ? "" : s).replace(/[&<>"]/g, function (c) { return ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]; }); }
  // comfort notices: never surface the word "error" as toast body text
  function toast2(m, k) { try { if (typeof showToast === "function") showToast(m, k === "error" ? "Heads-up" : k === "ok" ? "" : k || ""); } catch (e) {} }

  var COLORS = {
    "color.red": "#ef4444", "color.green": "#22c55e", "color.blue": "#1c84ff", "color.orange": "#f97316",
    "color.yellow": "#eab308", "color.purple": "#a855f7", "color.aqua": "#22d3ee", "color.teal": "#14b8a6",
    "color.white": "#ffffff", "color.black": "#0b0f1a", "color.gray": "#9ca3af", "color.silver": "#cbd5e1",
    "color.lime": "#84cc16", "color.maroon": "#9f1239", "color.navy": "#1e3a8a", "color.fuchsia": "#d946ef",
    "color.olive": "#a3a832",
  };
  // v2–v4 bare function names → namespaced equivalents (checked AFTER user
  // functions, so a script defining its own sma() keeps its definition)
  var BARE_ALIAS = {
    atr: "ta.atr", tr: "ta.tr", rsi: "ta.rsi", cci: "ta.cci", stoch: "ta.stoch",
    linreg: "ta.linreg", roc: "ta.roc", mom: "ta.change", cum: "ta.cum", sum: "ta.sum",
    vwma: "ta.vwma", hma: "ta.hma", crossover: "ta.crossover", crossunder: "ta.crossunder",
    cross: "ta.cross", barssince: "ta.barssince", valuewhen: "ta.valuewhen",
    pivothigh: "ta.pivothigh", pivotlow: "ta.pivotlow", rising: "ta.rising", falling: "ta.falling",
    wpr: "ta.wpr", variance: "ta.stdev",
    abs: "math.abs", min: "math.min", max: "math.max", round: "math.round",
    floor: "math.floor", ceil: "math.ceil", sqrt: "math.sqrt", pow: "math.pow",
    exp: "math.exp", log: "math.log", sign: "math.sign", avg: "math.avg",
  };

  /* ════════════════════════ LEXER ════════════════════════ */
  function lex(src) {
    var lines = String(src || "").replace(/\r/g, "").split("\n");
    var logical = [];
    for (var li = 0; li < lines.length; li++) {
      var raw = lines[li];
      var noCom = "";
      var inStr = null;
      for (var ci = 0; ci < raw.length; ci++) {
        var ch = raw[ci];
        if (inStr) { noCom += ch; if (ch === inStr && raw[ci - 1] !== "\\") inStr = null; continue; }
        if (ch === '"' || ch === "'") { inStr = ch; noCom += ch; continue; }
        if (ch === "/" && raw[ci + 1] === "/") break;
        noCom += ch;
      }
      if (!noCom.trim()) continue;
      // expression-continuation START tokens: Pine allows breaking BEFORE the
      // operator — `x = cond ? a\n  : b`, `? size.tiny\n : size.small`, and
      // multi-line `and`/`or` chains. Those lines can't be statements, so join.
      if (logical.length) {
        var trimJ = noCom.trim();
        if (/^([?)\],]|:(?!=)|and\b|or\b)/.test(trimJ)) {
          logical[logical.length - 1].text += " " + trimJ;
          continue;
        }
      }
      // continuation: Pine wraps args across lines; join while parens unbalanced
      if (logical.length) {
        var prev = logical[logical.length - 1];
        var bal = (prev.text.match(/[([]/g) || []).length - (prev.text.match(/[)\]]/g) || []).length;
        if (bal > 0 || /[,+\-*/=?:(\[]\s*$/.test(prev.text)) { prev.text += " " + noCom.trim(); continue; }
      }
      var ind = (raw.match(/^[ \t]*/) || [""])[0].replace(/\t/g, "    ").length;
      logical.push({ line: li + 1, indent: ind, text: noCom });
    }
    return logical;
  }

  function tokenize(text) {
    var toks = [], i = 0, n = text.length;
    var isId = function (c) { return /[A-Za-z0-9_.]/.test(c); };
    while (i < n) {
      var c = text[i];
      if (c === " " || c === "\t") { i++; continue; }
      if (c === '"' || c === "'") {
        var q = c, s = ""; i++;
        while (i < n && text[i] !== q) {
          if (text[i] === "\\") { var esc = text[++i]; s += esc === "n" ? "\n" : esc === "t" ? "\t" : esc; }
          else s += text[i];
          i++;
        }
        i++; toks.push({ t: "str", v: s }); continue;
      }
      if (/[0-9]/.test(c) || (c === "." && /[0-9]/.test(text[i + 1]))) {
        var num = ""; while (i < n && /[0-9._eE]/.test(text[i]) && !(text[i] === "." && !/[0-9]/.test(text[i + 1] || ""))) { num += text[i]; i++; }
        toks.push({ t: "num", v: parseFloat(num.replace(/_/g, "")) }); continue;
      }
      if (c === "#" && /[0-9a-fA-F]/.test(text[i + 1] || "")) {
        var hx = "#"; i++; while (i < n && /[0-9a-fA-F]/.test(text[i])) { hx += text[i]; i++; }
        toks.push({ t: "color", v: hx.length === 9 ? hx.slice(0, 7) : hx }); continue;   // strip alpha byte
      }
      if (/[A-Za-z_]/.test(c)) {
        var id = ""; while (i < n && isId(text[i])) { id += text[i]; i++; }
        toks.push({ t: "id", v: id }); continue;
      }
      var two = text.substr(i, 2);
      if ([":=", "==", "!=", ">=", "<=", "=>", "+=", "-=", "*=", "/="].indexOf(two) >= 0) { toks.push({ t: "op", v: two }); i += 2; continue; }
      if ("+-*/%<>=?:,()[]".indexOf(c) >= 0) { toks.push({ t: "op", v: c }); i++; continue; }
      i++; // skip unknown char
    }
    return toks;
  }

  /* ════════════════════════ PARSER (Pratt) ════════════════════════ */
  function Parser(toks) { this.toks = toks; this.p = 0; this.nodeSeq = { n: 0 }; }
  Parser.prototype.peek = function () { return this.toks[this.p] || { t: "eof" }; };
  Parser.prototype.next = function () { return this.toks[this.p++] || { t: "eof" }; };
  Parser.prototype.eat = function (v) { var tk = this.peek(); if (tk.t === "op" && tk.v === v) { this.p++; return true; } return false; };
  Parser.prototype.expect = function (v) { if (!this.eat(v)) throw new Error("expected '" + v + "' near token " + JSON.stringify(this.peek())); };
  Parser.prototype.node = function (o) { o.ix = this.nodeSeq.n++; return o; };

  var PREC = { "?": 1, "or": 2, "and": 3, "==": 4, "!=": 4, "<": 5, ">": 5, "<=": 5, ">=": 5, "+": 6, "-": 6, "*": 7, "/": 7, "%": 7 };

  Parser.prototype.expr = function (minP) {
    minP = minP || 0;
    var left = this.unary();
    for (;;) {
      var tk = this.peek();
      var op = (tk.t === "op" && PREC[tk.v] != null) ? tk.v : (tk.t === "id" && (tk.v === "and" || tk.v === "or")) ? tk.v : null;
      if (op == null || PREC[op] < minP) break;
      this.next();
      if (op === "?") {
        var a = this.expr(0); this.expect(":"); var b = this.expr(0);
        left = this.node({ k: "tern", c: left, a: a, b: b });
        continue;
      }
      var right = this.expr(PREC[op] + 1);
      left = this.node({ k: "bin", op: op, l: left, r: right });
    }
    return left;
  };
  Parser.prototype.unary = function () {
    var tk = this.peek();
    if (tk.t === "op" && tk.v === "-") { this.next(); return this.node({ k: "neg", v: this.unary() }); }
    if (tk.t === "id" && tk.v === "not") { this.next(); return this.node({ k: "not", v: this.unary() }); }
    return this.postfix();
  };
  Parser.prototype.postfix = function () {
    var e = this.primary();
    for (;;) {
      if (this.eat("[")) { var idx = this.expr(0); this.expect("]"); e = this.node({ k: "hist", v: e, n: idx }); continue; }
      // chained method calls on an EXPRESSION receiver: arr.first().set_x2(x)
      var pk = this.peek();
      if (pk.t === "id" && pk.v.charCodeAt(0) === 46 && this.toks[this.p + 1] && this.toks[this.p + 1].t === "op" && this.toks[this.p + 1].v === "(") {
        var mnm = this.next().v.slice(1);
        this.next();   // consume "("
        e = this.node({ k: "mcall", recv: e, name: mnm, args: this.args() });
        continue;
      }
      break;
    }
    return e;
  };
  Parser.prototype.args = function () {
    var out = { pos: [], named: {} };
    if (this.eat(")")) return out;
    for (;;) {
      var save = this.p;
      var tk = this.peek();
      if (tk.t === "id" && this.toks[this.p + 1] && this.toks[this.p + 1].t === "op" && this.toks[this.p + 1].v === "=") {
        var nm = this.next().v; this.next();
        out.named[nm] = this.expr(0);
      } else { this.p = save; out.pos.push(this.expr(0)); }
      if (this.eat(",")) continue;
      this.expect(")"); break;
    }
    return out;
  };
  Parser.prototype.primary = function () {
    var tk = this.next();
    if (tk.t === "num") return this.node({ k: "num", v: tk.v });
    if (tk.t === "str") return this.node({ k: "str", v: tk.v });
    if (tk.t === "color") return this.node({ k: "colorlit", v: tk.v });
    if (tk.t === "op" && tk.v === "[") {   // tuple literal (function returns): [a, b]
      var items = [];
      if (!this.eat("]")) { for (;;) { items.push(this.expr(0)); if (this.eat(",")) continue; this.expect("]"); break; } }
      return this.node({ k: "tuplelit", items: items });
    }
    if (tk.t === "op" && tk.v === "(") { var e = this.expr(0); this.expect(")"); return e; }
    if (tk.t === "id") {
      if (this.peek().t === "op" && this.peek().v === "(") { this.next(); var a = this.args(); return this.node({ k: "call", name: tk.v, args: a }); }
      return this.node({ k: "name", v: tk.v });
    }
    throw new Error("unexpected token " + JSON.stringify(tk));
  };

  /* ════════════════════════ COMPILER (block-aware) ════════════════════════ */
  // `strategy.*` (entry/exit/close/order) is the BACKTEST engine — correctly
  // skipped, since this engine charts scripts, it does not fill orders.
  // But bare `strategy(...)` is just the HEADER, the strategy equivalent of
  // indicator(): it carries the title and overlay flag. Skipping it threw away
  // the script's name (every strategy converted as "Custom") and forced overlay
  // to be guessed. Only the dotted form is a trade call — the header is read.
  // plotcandle() is now compiled (see candleSpec) — it is how SMC/structure
  // scripts repaint the chart's own candles by trend, and skipping it silently
  // dropped a headline feature ("Color Candles") from every one of them.
  // plotbar() stays out: it draws OHLC bars, a different primitive.
  var SKIP_HEADS = /^(matrix|strategy\.|plotbar|polyline|runtime|max_bars_back|library|import|export)\b/;
  var FN_HEAD = /^([A-Za-z_][\w.]*)\s*\(([^)]*)\)\s*=>\s*(.*)$/;

  // ── Pine VERSION NORMALIZER: v2–v6 sources are rewritten to the engine's
  // internal (v5-shaped) dialect before lexing. Structural renames only; bare
  // v2–v4 function names (sma, rsi, crossover, …) are resolved at RUNTIME via
  // BARE_ALIAS so user-defined functions with the same names still win.
  function normalizePine(src) {
    var m = /@version\s*=\s*(\d+)/.exec(src);
    var ver = m ? +m[1] : 4;   // no pragma → v1–v4 era script
    var s = src;
    if (ver < 5) {
      s = s.replace(/(^|[^.\w])study\s*\(/g, "$1indicator(");
      s = s.replace(/(^|[^.\w])security\s*\(/g, "$1request.security(");
      s = s.replace(/(^|[^.\w])tostring\s*\(/g, "$1str.tostring(");
      s = s.replace(/(^|[^.\w])input_price\s*\(/g, "$1input(");
    }
    /* Strip generic type parameters: `array.new<BAR>(…)` → `array.new(…)`.
     * Scanned rather than regexed, because the old `<[^>]*>` stopped at the FIRST
     * `>` and mangled a nested generic — `array.new<array<float>>(…)` became
     * `array.new>(…)`, a parse error on a line that is perfectly good Pine. Maps
     * and matrices take the same form, so they are stripped here too. */
    var GEN = /\b(?:array|map|matrix)\.new</g, gm, gOut = "", gLast = 0;
    while ((gm = GEN.exec(s))) {
      var lt = gm.index + gm[0].length - 1;      // index of the '<'
      var d = 0, q = lt, ok = false;
      for (; q < s.length; q++) {
        var ch = s[q];
        if (ch === "<") d++;
        else if (ch === ">") { d--; if (d === 0) { q++; ok = true; break; } }
        else if (ch === "\n") break;             // unbalanced — not a type param, leave it alone
      }
      gOut += s.slice(gLast, lt);                // keep `array.new` / `map.new`
      gLast = ok ? q : lt;                       // and drop the balanced <…>
      GEN.lastIndex = gLast;
    }
    s = gOut + s.slice(gLast);
    return { src: s, ver: ver };
  }

  function compile(src) {
    var out = { title: "Custom", overlay: true, stmts: [], plots: [], shapes: [], candles: [], hlines: [], inputs: [], funcs: {}, types: {}, methods: {}, plotByName: {}, fills: [], bgs: [], barcs: [], warnings: [], nodeCount: 0, lineCount: 0, pineVersion: 5 };
    var norm = normalizePine(src);
    out.pineVersion = norm.ver;
    out.usesLabels = /label\.new\s*\(/.test(norm.src);
    out.usesLines = /line\.new\s*\(/.test(norm.src);
    out.usesBoxes = /box\.new\s*\(/.test(norm.src);
    out.usesTables = /table\.new\s*\(/.test(norm.src);
    var logical = lex(norm.src);
    out.lineCount = logical.length;
    var seq = { n: 0 };

    /* ONE tail parser for comma chains, whatever the head was.
     *
     * Pine lets a line carry several statements separated by commas, and the two
     * shapes both appear in real scripts:
     *     lineA = cond ? line.new(…) : na, line.delete(lineA[1])   // assign head
     *     label.delete(lbl), lbl := label.new(…)                   // call head
     * A tail can therefore be an assignment OR a side-effecting call, regardless
     * of which one led. Keeping two half-loops is how the first silent drop got
     * in; this is the single place that decides.
     *
     * Anything we still cannot read is NAMED, never quietly dropped — a skipped
     * `label.delete` means the script's own cleanup never runs, and the user has
     * to be able to see that in the report. */
    // what we could not read, quoted back — a chain must never lose a tail in silence
    function warnRest(P, L) {
      var rest = "";
      for (var w = P.p; w < P.toks.length && rest.length < 80; w++) {
        var tk = P.toks[w];
        if (!tk || tk.t === "eof") break;
        rest += (rest ? " " : "") + (tk.t === "str" ? JSON.stringify(tk.v) : String(tk.v));
      }
      if (rest.length > 60) rest = rest.slice(0, 57) + "…";
      out.warnings.push("line " + L.line + ": could not read the rest of this line — skipped (`" + (rest || "…") + "`)");
    }

    function chainTail(P, L, allowOutputs, chain) {
      while (P.eat(",")) {
        if (P.peek().t === "eof") break;                 // trailing comma: nothing to keep
        var save = P.p, st = null;
        try {
          var iv = false;
          var t = P.next();
          if (t.t === "id" && (t.v === "var" || t.v === "varip")) { iv = true; t = P.next(); }
          if (t.t === "id" && (/^(float|int|bool|color|string|label|line|box|table)$/.test(t.v) || out.types[t.v]) && P.peek().t === "id") t = P.next();
          var o = P.peek();
          if (t.t === "id" && o.t === "op" && (o.v === "=" || o.v === ":=" || o.v === "+=" || o.v === "-=" || o.v === "*=" || o.v === "/=")) {
            var opT = P.next().v;
            var rx = P.expr(0);
            if (opT.length === 2 && opT !== ":=") rx = P.node({ k: "bin", op: opT[0], l: P.node({ k: "name", v: t.v }), r: rx });
            st = assignStmt(t.v, rx, iv, L, allowOutputs);
          } else {
            // not an assignment — rewind and classify it exactly as a standalone
            // line would be, so `line.delete(x[1])`, `plot(b)`, `bgcolor(c)` … all
            // keep their real meaning instead of decaying into a dead expression
            P.p = save;
            st = exprStmt(P.expr(0), L, allowOutputs);
          }
        } catch (e) {
          // a broken tail must not take the statements we already parsed with it.
          // Rewind to where the tail began so the quote shows the part we failed
          // on, not wherever the parser happened to die inside it.
          P.p = save;
          warnRest(P, L);
          return chain;
        }
        if (st) chain.push(st);
      }
      if (P.peek().t !== "eof") warnRest(P, L);
      return chain;
    }

    /* Does this expression DO anything, or does it only compute a value?
     * `label.new(…)` draws. `cond ? label.new(…) : na` — the standard Pine idiom
     * for a conditional draw — also draws, and a test for "is the top node a
     * call" says no and throws it away. Scripts were losing their labels to that,
     * so the question is asked of the whole tree: a call anywhere means keep it.
     * A pure computation costs one wasted evaluation; a dropped draw costs the
     * user their signal. */
    function hasCall(e, dep) {
      if (!e || typeof e !== "object") return false;
      if (e.k === "call" || e.k === "mcall") return true;
      if (e.k === "rawref") return false;          // holds a LIVE value, not AST — never walk it
      // Deep, not shallow: `bin` is left-recursive, so a long `a or b or … or z`
      // chain buries its FIRST term deepest — and that is exactly where the call
      // tends to be (`ta.crossover(a,b) or f1 or f2 …`). A cap that trips here
      // does not just lose a warning, it DELETES the statement. Cheap insurance.
      if ((dep || 0) > 200) return false;
      for (var kk in e) {
        if (!Object.prototype.hasOwnProperty.call(e, kk)) continue;
        if (kk.charAt(0) === "_") continue;        // _syn / _msyn: runtime caches, not source
        var v = e[kk];
        if (!v || typeof v !== "object") continue;
        if (Array.isArray(v)) { for (var q = 0; q < v.length; q++) if (hasCall(v[q], (dep || 0) + 1)) return true; }
        else if (hasCall(v, (dep || 0) + 1)) return true;
      }
      return false;
    }

    /* head + any comma tail → one statement. Every path out of parseStmt ends
     * here, so there is exactly one place where a chain can be missed — and it
     * isn't missed. */
    function finish(head, P, L, allowOutputs) {
      if (!(P.peek().t === "op" && P.peek().v === ",")) {
        // Tokens left over with no comma = a line we could not fully read. Say so
        // — unless this is a top-level dead computation, where the caller already
        // warns and quotes the whole line: two warnings for one line is noise, and
        // the accuracy score counts them. Inside a body (allowOutputs false) there
        // is no such caller, so the warning must still come from here.
        var dupWarn = !!head && allowOutputs && head.kind === "expr" && !hasCall(head.expr);
        if (head && P.peek().t !== "eof" && !dupWarn) warnRest(P, L);
        return head;
      }
      var chain = chainTail(P, L, allowOutputs, head ? [head] : []);
      if (chain.length > 1) return { kind: "multi", stmts: chain, line: L.line };
      return chain.length ? chain[0] : null;
    }

    /* `name = rhs` → a statement. input()/plot() on the right-hand side mean far
     * more than an assignment (a Settings control, a named plot handle), and that
     * has to be true wherever the assignment appears — on its own line OR riding
     * a comma. Hence one function, called from both. */
    function assignStmt(name, rhs, isVar, L, allowOutputs) {
      if (rhs.k === "call" && rhs.name === "input.source") {
        // pass the SOURCE SERIES through, not a constant
        var srcDef = rhs.args.named.defval || rhs.args.pos[0] || { k: "name", v: "close", ix: rhs.ix };
        return { kind: "assign", name: name, expr: srcDef, isVar: isVar, line: L.line };
      }
      if (rhs.k === "call" && /^input(\.|$)/.test(rhs.name)) {
        var def = rhs.args.named.defval || rhs.args.pos[0];
        var ttl = rhs.args.named.title || rhs.args.pos[1];
        // input(SOME_VARIABLE, …) — e.g. input(GREEN,…) where GREEN is a
        // color constant defined above: pass the VARIABLE through so the
        // value stays correct (the one input loses dialog editability)
        if (def && def.k === "name" && def.v !== "true" && def.v !== "false" && !COLORS[def.v] && !COLORS["color." + def.v]) {
          return { kind: "assign", name: name, expr: def, isVar: false, line: L.line };
        }
        var idef = def ? constOf(def) : 0;
        // control type: explicit from input.bool/int/float/string, else inferred
        var ikind = rhs.name === "input" ? "" : rhs.name.slice(6);
        var itype =
          ikind === "bool" ? "bool" :
          ikind === "int" ? "int" :
          ikind === "float" ? "float" :
          ikind === "string" ? "string" :
          (ikind === "color" || ikind === "timeframe" || ikind === "session" || ikind === "symbol") ? "fixed" :
          (def && def.k === "name" && (def.v === "true" || def.v === "false")) ? "bool" :
          (def && def.k === "colorlit") ? "fixed" :
          (typeof idef === "string") ? "string" : "float";
        // dropdown options: named options= or a positional tuple literal
        var iopts = null, optN = rhs.args.named.options || null;
        if (!optN) for (var oi2 = 2; oi2 < rhs.args.pos.length; oi2++) { var pn2 = rhs.args.pos[oi2]; if (pn2 && pn2.k === "tuplelit") { optN = pn2; break; } }
        if (optN && optN.k === "tuplelit") iopts = optN.items.map(constOf);
        var numTy = itype === "int" || itype === "float";
        var iMin = rhs.args.named.minval ? constOf(rhs.args.named.minval) : (numTy && rhs.args.pos[2] && rhs.args.pos[2].k !== "tuplelit" ? constOf(rhs.args.pos[2]) : null);
        var iMax = rhs.args.named.maxval ? constOf(rhs.args.named.maxval) : (numTy && rhs.args.pos[3] && rhs.args.pos[3].k !== "tuplelit" ? constOf(rhs.args.pos[3]) : null);
        var iStep = rhs.args.named.step ? constOf(rhs.args.named.step) : null;
        // color inputs become real color pickers
        if (def && def.k === "colorlit") { itype = "color"; idef = def.v; }
        // input(color.white, …) — named color constants stay real color inputs
        if (def && def.k === "name" && (COLORS[def.v] || COLORS["color." + def.v])) { itype = "color"; idef = COLORS[def.v] || COLORS["color." + def.v]; }
        var grpN = rhs.args.named.group, tipN = rhs.args.named.tooltip, inlN = rhs.args.named.inline;
        out.inputs.push({
          name: name, title: (ttl && ttl.k === "str" && ttl.v.trim()) ? ttl.v.trim() : name,
          def: idef, type: itype, options: iopts, min: iMin, max: iMax, step: iStep,
          group: (grpN && grpN.k === "str") ? grpN.v : "",
          tip: (tipN && tipN.k === "str") ? tipN.v : "",
          inline: (inlN && inlN.k === "str") ? inlN.v : "",
        });
        return { kind: "input", name: name, idx: out.inputs.length - 1, line: L.line };
      }
      if (allowOutputs && rhs.k === "call" && rhs.name === "plot") {
        // p1 = plot(...) — named plot handle (used by fill)
        var specN = plotSpec(rhs, out); out.plots.push(specN);
        out.plotByName[name] = out.plots.length - 1;
        return { kind: "plot", spec: specN, line: L.line };
      }
      return { kind: "assign", name: name, expr: rhs, isVar: isVar, line: L.line };
    }

    /* a bare expression → a statement: the output calls (plot, plotshape, hline,
     * bgcolor, fill …) are what make a Pine line draw anything, so this is the
     * classifier a comma tail has to reuse. Returns null for lines that register
     * something and emit nothing (hline, fill). */
    function exprStmt(e2, L, allowOutputs) {
      if (!e2) return null;
      if (allowOutputs && e2.k === "call" && e2.name === "plot") {
        var spec = plotSpec(e2, out); out.plots.push(spec);
        return { kind: "plot", spec: spec, line: L.line };
      }
      if (allowOutputs && e2.k === "call" && (e2.name === "plotshape" || e2.name === "plotchar" || e2.name === "plotarrow")) {
        // plotchar(x, title, "", …) with an EMPTY char is Pine's idiom for a
        // hidden data-window value — render nothing
        if (e2.name === "plotchar") {
          var chN = e2.args.named.char || e2.args.pos[2];
          if (chN && chN.k === "str" && chN.v === "") return null;
        }
        var sp = shapeSpec(e2); out.shapes.push(sp);
        return { kind: "shape", spec: sp, line: L.line };
      }
      if (allowOutputs && e2.k === "call" && e2.name === "plotcandle") {
        var cd = candleSpec(e2, out); out.candles.push(cd);
        return { kind: "candle", spec: cd, line: L.line };
      }
      if (allowOutputs && e2.k === "call" && e2.name === "hline") {
        var hv = e2.args.pos[0] ? constOf(e2.args.pos[0]) : null;
        if (hv != null && isFinite(hv)) out.hlines.push({ v: hv, color: colorArg(e2.args.named.color) || "#6f819e" });
        return null;
      }
      if (allowOutputs && e2.k === "call" && e2.name === "bgcolor") {
        out.bgs.push({ expr: e2.args.pos[0] });
        return { kind: "bg", idx: out.bgs.length - 1, line: L.line };
      }
      if (allowOutputs && e2.k === "call" && e2.name === "barcolor") {
        out.barcs.push({ expr: e2.args.pos[0] });
        return { kind: "barc", idx: out.barcs.length - 1, line: L.line };
      }
      if (allowOutputs && e2.k === "call" && e2.name === "fill") {
        // accepts BOTH named handles (p1 = plot(…)) and INLINE plot(…) args
        var an = e2.args.pos[0], bn = e2.args.pos[1];
        var ia = null, ib = null, spA = null, spB = null;
        if (an && an.k === "call" && an.name === "plot") { spA = plotSpec(an, out); out.plots.push(spA); ia = out.plots.length - 1; }
        else if (an && an.k === "name" && out.plotByName[an.v] != null) ia = out.plotByName[an.v];
        if (bn && bn.k === "call" && bn.name === "plot") { spB = plotSpec(bn, out); out.plots.push(spB); ib = out.plots.length - 1; }
        else if (bn && bn.k === "name" && out.plotByName[bn.v] != null) ib = out.plotByName[bn.v];
        if (ia != null && ib != null) {
          var fCol = e2.args.named.color || e2.args.pos[2];
          out.fills.push({
            a: ia, b: ib,
            color: colorArg(fCol) || "rgba(120,150,255,.12)",
            colorExpr: (fCol && !colorArg(fCol)) ? fCol : null,   // per-bar dynamic fill color
          });
          var mfPl = [];
          if (spA) mfPl.push({ kind: "plot", spec: spA, line: L.line });
          if (spB) mfPl.push({ kind: "plot", spec: spB, line: L.line });
          if (mfPl.length === 1) return mfPl[0];
          if (mfPl.length) return { kind: "multi", stmts: mfPl, line: L.line };
        } else out.warnings.push("line " + L.line + ": fill() needs plot handles or inline plot(...) args");
        return null;
      }
      return { kind: "expr", expr: e2, line: L.line };
    }

    // parse one logical line into a statement (shared by main + fn/if bodies)
    function parseStmt(L, allowOutputs) {
      var text = L.text.trim();
      if (/^\/\//.test(text) || /^@/.test(text)) return null;
      if (SKIP_HEADS.test(text)) {
        var skName = text.split("(")[0].trim();
        out.warnings.push("line " + L.line + ": unsupported call skipped (" + skName + ")");
        if (/^strategy\./.test(skName)) out.strategyOrders = (out.strategyOrders || 0) + 1;
        return null;
      }
      var P = new Parser(tokenize(text)); P.nodeSeq = seq;
      if (P.peek().t === "op" && P.peek().v === "[") {   // [a,b] = expr  (or trailing tuple return)
        var save = P.p;
        P.next();
        var names = [], okTuple = true;
        for (;;) {
          var nk = P.next();
          if (nk.t !== "id") { okTuple = false; break; }
          names.push(nk.v);
          if (P.eat(",")) continue;
          if (!P.eat("]")) okTuple = false;
          break;
        }
        if (okTuple && P.peek().t === "op" && P.peek().v === "=") {
          P.next();
          // `[a, b] = ta.dmi(len), plot(adx)` — a tuple can carry a tail too
          return finish({ kind: "tuple", names: names, expr: P.expr(0), line: L.line }, P, L, allowOutputs);
        }
        P.p = save;   // tuple literal expression (function return value)
        return finish({ kind: "expr", expr: P.expr(0), line: L.line }, P, L, allowOutputs);
      }
      var isVar = false, save0 = P.p;
      var first = P.next();
      if (first.t === "id" && (first.v === "var" || first.v === "varip")) { isVar = true; first = P.next(); }
      // optional TYPE token — with or without var: `var int direction = na`,
      // `label lbl = …`, `color css = na`, `var line ln = …` all declare a name
      if (first.t === "id" && (/^(float|int|bool|color|string|label|line|box|table)$/.test(first.v) || out.types[first.v]) && P.peek().t === "id") {
        first = P.next();   // built-in OR user-defined type token: `BAR bar = …`
      }
      /* Typed collection declarations: `array<float> zones = array.new_float()`,
       * `map<string, float> m = …`, `float[] pivots = na`. The `<`/`[` is not an
       * operator here, it is part of the TYPE — but the expression parser reads
       * `array < float > zones` as two comparisons, stops dead at the `=`, and the
       * declaration never happens: the variable stays undefined and every later
       * `array.push(zones, …)` silently does nothing. SMC/zone scripts are full of
       * these. Recognise the type, keep the name. */
      if (first.t === "id" && P.peek().t === "op" && (P.peek().v === "<" || P.peek().v === "[")) {
        var saveT = P.p, dep = 0, tkT;
        if (P.peek().v === "<") {
          while (P.peek().t !== "eof") {
            tkT = P.next();
            if (tkT.t === "op" && tkT.v === "<") dep++;
            else if (tkT.t === "op" && tkT.v === ">") { dep--; if (dep <= 0) break; }
          }
        } else { P.next(); if (!P.eat("]")) P.p = saveT; }   // `[]` only — `arr[0]` is an index, leave it alone
        var idT = P.peek(), opT2 = P.toks[P.p + 1];
        if (P.p !== saveT && idT.t === "id" && opT2 && opT2.t === "op" && (opT2.v === "=" || opT2.v === ":=")) first = P.next();
        else P.p = saveT;   // not a declaration after all — put every token back
      }
      var nx = P.peek();
      if (first.t === "id" && nx.t === "op" && (nx.v === "=" || nx.v === ":=" || nx.v === "+=" || nx.v === "-=" || nx.v === "*=" || nx.v === "/=")) {
        var opTok = P.next().v;
        var rhs = P.expr(0);
        if (opTok.length === 2 && opTok !== ":=") {   // compound: x += e → x = x + e
          rhs = P.node({ k: "bin", op: opTok[0], l: P.node({ k: "name", v: first.v }), r: rhs });
        }
        /* Comma-chained statements: `a := b, c := d, e = f` — and, crucially,
         * `x = cond ? line.new(…) : na, line.delete(x[1])`, which is THE standard
         * Pine idiom for "draw this level and remove last bar's copy".
         *
         * This used to abandon the rest of the line — tokens unparsed, no
         * statement, and NO WARNING — so every `line.delete(…)` / `label.delete(…)`
         * riding on a comma vanished and the script's own cleanup never ran: one
         * new TP/SL rail per bar, none ever removed, a chart slowly filling with
         * stale lines. A silent drop is the one thing this engine is not allowed
         * to do: if a construct is unsupported we name it, and if it IS supported
         * (as this is) we must actually run it. `finish` is now the single exit,
         * so head and tail are read by the same rules whichever one led. */
        return finish(assignStmt(first.v, rhs, isVar, L, allowOutputs), P, L, allowOutputs);
      }
      P.p = save0;
      return finish(exprStmt(P.expr(0), L, allowOutputs), P, L, allowOutputs);
    }

    // collect an indented block starting after index i (indent > baseIndent)
    function collectBlock(i, baseIndent) {
      var body = [], j = i;
      while (j < logical.length && logical[j].indent > baseIndent) { body.push(logical[j]); j++; }
      return { lines: body, next: j };
    }
    // parse a statement list (recursively handles nested if/else)
    function parseBody(lines, allowOutputs) {
      var stmts = [], k = 0;
      while (k < lines.length) {
        var L = lines[k], text = L.text.trim();
        try {
          var mIf = /^if\s+(.*)$/.exec(text);
          if (mIf) {
            var branches = [];
            var Pc = new Parser(tokenize(mIf[1])); Pc.nodeSeq = seq;
            var blk = subBlock(lines, k + 1, L.indent);
            branches.push({ cond: Pc.expr(0), body: parseBody(blk.lines, false) });
            k = blk.next;
            while (k < lines.length && /^else(\s+if\b|$|\s*$)/.test(lines[k].text.trim()) && lines[k].indent === L.indent) {
              var et = lines[k].text.trim();
              var mEi = /^else\s+if\s+(.*)$/.exec(et);
              var blk2 = subBlock(lines, k + 1, L.indent);
              if (mEi) { var Pe = new Parser(tokenize(mEi[1])); Pe.nodeSeq = seq; branches.push({ cond: Pe.expr(0), body: parseBody(blk2.lines, false) }); }
              else branches.push({ cond: null, body: parseBody(blk2.lines, false) });
              k = blk2.next;
            }
            stmts.push({ kind: "ifblk", branches: branches, line: L.line });
            continue;
          }
          // switch — both forms: `x = switch subj` / bare `switch [subj]`,
          // with `pattern => expr` arms and a bare `=> expr` default arm
          var mSw = /^(?:([A-Za-z_]\w*)\s*=\s*)?switch\b\s*(.*)$/.exec(text);
          if (mSw) {
            var subjN = null;
            if (mSw[2].trim()) { var Psub = new Parser(tokenize(mSw[2].trim())); Psub.nodeSeq = seq; subjN = Psub.expr(0); }
            var blkS = subBlock(lines, k + 1, L.indent);
            var arms = [], defB = null;
            for (var qa = 0; qa < blkS.lines.length; qa++) {
              var al = blkS.lines[qa].text.trim();
              var ar = al.indexOf("=>");
              if (ar < 0) continue;
              var patT = al.slice(0, ar).trim(), bodT = al.slice(ar + 2).trim();
              var bodN = null;
              if (bodT) { var Pbo = new Parser(tokenize(bodT)); Pbo.nodeSeq = seq; bodN = Pbo.expr(0); }
              if (!patT) defB = bodN;
              else { var Ppa = new Parser(tokenize(patT)); Ppa.nodeSeq = seq; arms.push({ test: Ppa.expr(0), body: bodN }); }
            }
            stmts.push({ kind: "switchblk", name: mSw[1] || null, subj: subjN, arms: arms, def: defB, line: L.line });
            k = blkS.next; continue;
          }
          var mWh = /^while\s+(.+)$/.exec(text);
          if (mWh) {
            var Pw = new Parser(tokenize(mWh[1])); Pw.nodeSeq = seq;
            var blkW = subBlock(lines, k + 1, L.indent);
            stmts.push({ kind: "whileblk", cond: Pw.expr(0), body: parseBody(blkW.lines, false), line: L.line });
            k = blkW.next; continue;
          }
          var mFor = /^for\s+([A-Za-z_]\w*)\s*=\s*(.+?)\s+to\s+(.+)$/.exec(text);
          if (mFor) {
            var Pa = new Parser(tokenize(mFor[2])); Pa.nodeSeq = seq;
            var Pb = new Parser(tokenize(mFor[3])); Pb.nodeSeq = seq;
            var blkFor = subBlock(lines, k + 1, L.indent);
            stmts.push({ kind: "forblk", v: mFor[1], from: Pa.expr(0), to: Pb.expr(0), body: parseBody(blkFor.lines, false), line: L.line });
            k = blkFor.next; continue;
          }
          // for … in — iterate a Pine array (snapshot, so removals are safe)
          var mFi = /^for\s+([A-Za-z_]\w*)\s+in\s+(.+)$/.exec(text);
          if (mFi) {
            var Pfi = new Parser(tokenize(mFi[2])); Pfi.nodeSeq = seq;
            var blkFi = subBlock(lines, k + 1, L.indent);
            stmts.push({ kind: "forin", v: mFi[1], expr: Pfi.expr(0), body: parseBody(blkFi.lines, false), line: L.line });
            k = blkFi.next; continue;
          }
          if (/^(for|while)\b/.test(text)) {
            out.warnings.push("line " + L.line + ": " + text.split(/\s/)[0] + " loop skipped (unsupported form)");
            var skipB = subBlock(lines, k + 1, L.indent); k = skipB.next; continue;
          }
          var st = parseStmt(L, allowOutputs);
          if (st) stmts.push(st);
        } catch (err) { out.warnings.push("line " + L.line + ": " + (err && err.message || "parse error")); }
        k++;
      }
      return stmts;
    }
    function subBlock(lines, from, baseIndent) {
      var body = [], j = from;
      while (j < lines.length && lines[j].indent > baseIndent) { body.push(lines[j]); j++; }
      return { lines: body, next: j };
    }

    var i = 0;
    while (i < logical.length) {
      var L = logical[i];
      var text = L.text.trim();
      try {
        if (/^\/\//.test(text) || /^@/.test(text)) { i++; continue; }
        // strategy() is read as a header, exactly like indicator(): same title and
        // overlay arguments. Its ORDERS (strategy.entry/exit/…) are still skipped.
        if (/^(indicator|study|strategy)\s*\(/.test(text)) {
          var pI = new Parser(tokenize(text)); pI.nodeSeq = seq;
          var hdr = pI.expr(0);
          if (hdr.k === "call") {
            if (hdr.args.pos[0] && hdr.args.pos[0].k === "str") out.title = hdr.args.pos[0].v;
            if (hdr.args.named.title && hdr.args.named.title.k === "str") out.title = hdr.args.named.title.v;
            // overlay may be named OR positional: indicator(title, short, overlay, …)
            var ov = hdr.args.named.overlay || hdr.args.pos[2];
            if (ov && ov.k === "name") out.overlay = ov.v !== "false";
            if (hdr.name === "strategy") out.isStrategy = true;
          }
          i++; continue;
        }
        // user-defined type: `type NAME` + indented `TYPE field [= default]` lines
        var mTy = /^type\s+([A-Za-z_]\w*)\s*$/.exec(text);
        if (mTy) {
          var blkT = collectBlock(i + 1, L.indent);
          var fields = [];
          for (var ft = 0; ft < blkT.lines.length; ft++) {
            var fl = blkT.lines[ft].text.trim();
            if (!fl || /^\/\//.test(fl)) continue;
            var mF = /^([A-Za-z_][\w.<>\[\]]*)\s+([A-Za-z_]\w*)(?:\s*=\s*(.+))?$/.exec(fl);
            if (!mF) continue;
            var defN = null;
            if (mF[3]) { try { var Pd = new Parser(tokenize(mF[3])); Pd.nodeSeq = seq; defN = Pd.expr(0); } catch (eT) {} }
            fields.push({ name: mF[2], def: defN });
          }
          out.types[mTy[1]] = fields;
          i = blkT.next; continue;
        }
        // method NAME(TYPE this, …) => body — Pine v5 methods; dispatched via
        // obj.NAME(args) with `this` bound to the receiver
        var mMe = /^method\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*=>\s*(.*)$/.exec(text);
        if (mMe) {
          var mparams = mMe[2].split(",").map(function (s) { var w = s.trim().split(/\s+/); return w[w.length - 1]; }).filter(Boolean);
          var mInline = mMe[3].trim(), mBody;
          if (mInline) { var Pm = new Parser(tokenize(mInline)); Pm.nodeSeq = seq; mBody = [{ kind: "expr", expr: Pm.expr(0), line: L.line }]; i++; }
          else { var blkM = collectBlock(i + 1, L.indent); mBody = parseBody(blkM.lines, false); i = blkM.next; }
          out.methods[mMe[1]] = { params: mparams, body: mBody, name: mMe[1] };
          continue;
        }
        // user function: name(a, b) => body   (inline expr or indented block)
        var mFn = FN_HEAD.exec(text);
        if (mFn && !/^(if|for|while)\b/.test(text)) {
          // param names: take the LAST word — strips series/simple/float/UDT qualifiers
          var params = mFn[2].split(",").map(function (s) { var w = s.trim().split(/\s+/); return w[w.length - 1]; }).filter(Boolean);
          var inline = mFn[3].trim();
          var bodyStmts;
          if (inline) {
            var Pf = new Parser(tokenize(inline)); Pf.nodeSeq = seq;
            bodyStmts = [{ kind: "expr", expr: Pf.expr(0), line: L.line }];
            i++;
          } else {
            var blkF = collectBlock(i + 1, L.indent);
            bodyStmts = parseBody(blkF.lines, false);
            i = blkF.next;
          }
          out.funcs[mFn[1]] = { params: params, body: bodyStmts, name: mFn[1] };
          continue;
        }
        // top-level if / loops / switch — including `x = switch subj` assigns —
        // are block constructs: hand the head + its indented block to parseBody
        if (/^(if|for|while|switch|else)\b/.test(text) || /^[A-Za-z_]\w*\s*=\s*switch\b/.test(text)) {
          var blkAll = collectBlock(i + 1, L.indent);
          var chunk = [L].concat(blkAll.lines);
          // pull any trailing else-chains at the same indent
          var j2 = blkAll.next;
          while (j2 < logical.length && /^else\b/.test(logical[j2].text.trim()) && logical[j2].indent === L.indent) {
            var blkE = collectBlock(j2 + 1, L.indent);
            chunk = chunk.concat([logical[j2]]).concat(blkE.lines);
            j2 = blkE.next;
          }
          var parsed = parseBody(chunk, false);
          for (var pz = 0; pz < parsed.length; pz++) out.stmts.push(parsed[pz]);
          i = j2; continue;
        }
        var st2 = parseStmt(L, true);
        // Same rule inside a comma chain: drop the children that only compute a
        // value (they draw nothing), keep the ones that can do something. Without
        // this, a chain slipped past the check below and ran dead work per bar.
        if (st2 && st2.kind === "multi" && st2.stmts) {
          var keepM = [];
          for (var km = 0; km < st2.stmts.length; km++) {
            var chM = st2.stmts[km];
            if (chM.kind === "expr" && !hasCall(chM.expr)) continue;
            keepM.push(chM);
          }
          if (!keepM.length) st2 = { kind: "expr", expr: null, line: L.line };   // fall into the warning below
          else if (keepM.length === 1) st2 = keepM[0];
          else st2.stmts = keepM;
        }
        if (st2) {
          // A top-level expression with no call in it computes a value and throws
          // it away — Pine allows it, it draws nothing, and dropping it is right.
          // But "line 243: statement had no effect" tells the user nothing they
          // can act on: they cannot see WHICH statement, so they cannot judge
          // whether we quietly ate something that mattered. Quote it.
          //
          // hasCall(), not "is a call": `cond ? label.new(…) : na` draws, and the
          // old top-node test skipped it — that is what those "no effect" lines in
          // the Diamond Algo report were. Anything with a call in it now runs.
          if (st2.kind === "expr" && !hasCall(st2.expr)) {
            var snip = String(L.text || "").trim().replace(/\s+/g, " ");
            if (snip.length > 60) snip = snip.slice(0, 57) + "…";
            out.warnings.push("line " + L.line + ": statement computes a value that is never used — skipped (`" + snip + "`)");
          }
          else out.stmts.push(st2);   // side-effect calls (label.new, …) execute per bar
        }
        i++;
      } catch (err) {
        out.warnings.push("line " + L.line + ": " + (err && err.message ? err.message : "parse error"));
        i++;
      }
    }
    out.nodeCount = seq.n;
    if (!out.plots.length && !out.shapes.length && !out.candles.length && !out.hlines.length
      && !out.usesLabels && !out.usesLines && !out.usesBoxes && !out.bgs.length && !out.barcs.length) {
      out.warnings.push("no renderable output found (plot/plotshape/plotcandle/hline/label/line/box/bgcolor/barcolor)");
    }
    return out;
  }
  function constOf(node) {
    if (!node) return 0;
    if (node.k === "num") return node.v;
    if (node.k === "str") return node.v;
    if (node.k === "name") return node.v === "true" ? 1 : node.v === "false" ? 0 : 0;
    if (node.k === "neg") { var c = constOf(node.v); return typeof c === "number" ? -c : 0; }
    return 0;
  }
  function colorArg(node) {
    if (!node) return null;
    if (node.k === "colorlit") return node.v;
    if (node.k === "name" && COLORS[node.v]) return COLORS[node.v];
    if (node.k === "name" && COLORS["color." + node.v]) return COLORS["color." + node.v];   // v2–v4 bare colors (red, lime, …)
    if (node.k === "call" && node.name === "color.new") {
      // honor the TRANSPARENCY argument: constant → bake into rgba here;
      // variable (e.g. showBands ? 70 : 100) → return null so the plot gets a
      // runtime colorExpr and per-bar alpha (TV hides transp-100 plots fully)
      var cnB = colorArg(node.args.pos[0] || (node.args.named && node.args.named.color));
      if (cnB == null) return null;
      var cnTn = node.args.pos[1] || (node.args.named && node.args.named.transp);
      if (!cnTn) return cnB;
      var cnTc = (cnTn.k === "num") ? cnTn.v : (cnTn.k === "neg" && cnTn.v && cnTn.v.k === "num") ? -cnTn.v.v : null;
      if (cnTc == null) return null;               // non-constant transparency → runtime
      if (cnTc <= 0) return cnB;
      if (/^rgba/.test(cnB)) return null;          // already-alpha base → resolve at runtime
      var cnH6 = toHex6(cnB);
      return "rgba(" + parseInt(cnH6.slice(1, 3), 16) + "," + parseInt(cnH6.slice(3, 5), 16) + "," + parseInt(cnH6.slice(5, 7), 16) + "," + Math.max(0, Math.min(1, (100 - cnTc) / 100)).toFixed(2) + ")";
    }
    if (node.k === "call" && node.name === "color.rgb") {
      var r = Math.round(constOf(node.args.pos[0])) || 0, g = Math.round(constOf(node.args.pos[1])) || 0, b = Math.round(constOf(node.args.pos[2])) || 0;
      var tr2 = node.args.pos[3] ? constOf(node.args.pos[3]) : 0;
      return "rgba(" + r + "," + g + "," + b + "," + Math.max(0, Math.min(1, (100 - tr2) / 100)).toFixed(2) + ")";
    }
    return null;
  }
  function plotSpec(call, out) {
    var titleN = call.args.named.title || call.args.pos[1];
    var stl = call.args.named.style;
    // display=display.none → data-carrier plot (feeds fill()), never stroked
    var dispN = call.args.named.display;
    var hiddenP = !!(dispN && dispN.k === "name" && /none/.test(dispN.v));
    return {
      expr: call.args.named.series || call.args.pos[0],
      hidden: hiddenP,
      title: (titleN && titleN.k === "str") ? titleN.v : ("Plot " + (out.plots.length + 1)),
      color: colorArg(call.args.named.color) || colorArg(call.args.pos[2]) || "#1c84ff",
      colorExpr: (call.args.named.color && !colorArg(call.args.named.color)) ? call.args.named.color : null,
      width: (call.args.named.linewidth && call.args.named.linewidth.k === "num") ? call.args.named.linewidth.v : 1.5,
      style: (stl && stl.k === "name")
        ? (/histogram|columns/.test(stl.v) ? "hist" : /circles|cross/.test(stl.v) ? "dots" : /area/.test(stl.v) ? "area" : "line")
        : "line",
    };
  }
  function shapeSpec(call) {
    // plotshape(series, title, style, location, color, …) — positional too
    var loc = call.args.named.location || call.args.pos[3];
    var style = call.args.named.style || call.args.pos[2];
    var txt = call.args.named.text;
    var colN = call.args.named.color || call.args.pos[4];
    var ttlS = call.args.named.title || call.args.pos[1];
    return {
      cond: call.args.named.series || call.args.pos[0],
      title: (ttlS && ttlS.k === "str") ? ttlS.v : "",
      below: !(loc && loc.k === "name" && /abovebar/.test(loc.v)),
      color: colorArg(colN) || "#f5b942",
      colorExpr: (colN && !colorArg(colN)) ? colN : null,   // e.g. color = someInputColorVar
      glyph: style && style.k === "name" && /down/.test(style.v) ? "▼" : style && style.k === "name" && /circle/.test(style.v) ? "●" : "▲",
      text: (txt && txt.k === "str") ? txt.v : "",
    };
  }
  /* plotcandle(open, high, low, close, title, color, wickcolor, bordercolor)
   *
   * Structure/SMC scripts use this to REPAINT the chart's own candles by trend —
   * it is the "Color Candles" switch in half the smart-money indicators. The
   * colours are almost always series (`trend_css`, na until a trend exists), so
   * each of the three is kept as an expression and evaluated per bar; a `na`
   * colour means Pine draws nothing for that bar, which is exactly how these
   * scripts turn the feature off. */
  // "rgba(…, 0)" / "rgba(…, 0.00)" — Pine's color.new(css, 100)
  function isTransparent(col) {
    if (!col) return true;
    var m = /^rgba\([^)]*,\s*(\d*\.?\d+)\s*\)$/.exec(String(col));
    return !!(m && Number(m[1]) <= 0.005);
  }
  function candleSpec(call, out) {
    var A = call.args;
    var ttl = A.named.title || A.pos[4];
    // Pine's positional signature is (o, h, l, c, title, color, wickcolor,
    // bordercolor). Reading colours from named args ONLY looked harmless — until
    // you notice what it does to a script that passes an `na` colour
    // positionally: colN would be undefined, hasColor would be false, and the
    // default green/red would be force-painted over a chart whose author
    // explicitly asked for nothing. The positional form is rare; the failure it
    // causes is not subtle. Read both.
    var colN = A.named.color || A.pos[5];
    var wickN = A.named.wickcolor || A.pos[6];
    var bordN = A.named.bordercolor || A.pos[7];
    return {
      o: A.named.open || A.pos[0],
      h: A.named.high || A.pos[1],
      l: A.named.low || A.pos[2],
      c: A.named.close || A.pos[3],
      title: (ttl && ttl.k === "str") ? ttl.v : ("Candles " + (out.candles.length + 1)),
      color: colorArg(colN), colorExpr: (colN && !colorArg(colN)) ? colN : null,
      wick: colorArg(wickN), wickExpr: (wickN && !colorArg(wickN)) ? wickN : null,
      border: colorArg(bordN), borderExpr: (bordN && !colorArg(bordN)) ? bordN : null,
      // THE DISTINCTION THAT MATTERS: "no colour argument given" is not the same
      // as "a colour argument that evaluated to na".
      //   · No `color` argument at all → Pine paints its default green/red body.
      //   · A `color` argument that is na on this bar → Pine paints nothing. This
      //     is exactly how `color = trend_css` (na until the user switches Color
      //     Candles on) turns the whole feature off.
      // Collapsing the two would either force the feature permanently ON for every
      // SMC script, or silently drop a plain plotcandle() entirely. The test is
      // per-channel and specifically on `color`, because Pine still defaults the
      // BODY when a script colours only the wick.
      hasBody: !!colN,
    };
  }

  /* ════════════════════════ RUNTIME ════════════════════════ */
  // Evaluate the program against candles [{t,o,h,l,c}]. Every AST node keeps a
  // per-bar cache so the history operator and windowed math are exact.
  function nowMs() {
    try { return (window.performance && performance.now) ? performance.now() : Date.now(); }
    catch (e) { return Date.now(); }
  }
  // budgetMs > 0 → abort the run if it overruns. See the bar loop below.
  function run(prog, candles, inputVals, budgetMs) {
    var n = candles.length;
    var _runT0 = nowMs();
    var _budget = Number(budgetMs) > 0 ? Number(budgetMs) : 0;
    // CD/N: the ACTIVE series context. request.security() temporarily swaps
    // these to a resampled higher-timeframe candle array, so every base-series
    // read, node cache, and windowed calc inside the security expression runs
    // natively on HTF bars — Pine's real model, not an approximation.
    var CD = candles, N = n;
    // CONTEXT-KEYED caches: user functions are inlined per CALL SITE, so two
    // calls to the same function (e.g. two supertrends with different factors)
    // get fully independent node caches and `var` state — exactly Pine's model.
    var cacheMap = Object.create(null);
    var vars = Object.create(null);
    var state = Object.create(null);
    var bar = 0;
    var curCtx = "0";
    var frames = [];   // function-call scope stack: {params:{name:{node,ctx,depth}}, vars:{}, ctx}
    var loopVars = []; // for-loop iterator scopes (innermost last)
    var loopDepth = 0; // >0 → node caches are BYPASSED (values change per iteration)
    var labelSeq = 0, labelById = {};   // label HANDLES: label.new returns an id
    var lineSeq = 0, lineById = {}, linesOut = [];   // line objects (BOS/CHoCH, TP/SL rails, …)
    var boxSeq = 0, boxById = {}, boxesOut = [];     // box objects (order blocks, FVGs, zones)
    var linefillsOut = [];                            // shaded bands between two lines
    var tableSeq = 0, tableById = {}, tablesOut = []; // dashboards (corner-anchored grids)
    // evaluate a color argument: compile-time constant OR runtime string value
    function evColor(nodeC, i) {
      if (!nodeC) return null;
      var cc = colorArg(nodeC);
      if (cc) return cc;
      try { var vv = ev(nodeC, i); return typeof vv === "string" ? vv : null; } catch (eEC) { return null; }
    }
    function tIdx(t) {   // time(ms) → bar index in the ACTIVE series (nearest ≤)
      if (!N) return 0;
      if (t <= CD[0].t) return 0;
      if (t >= CD[N - 1].t) return N - 1;
      var loT = 0, hiT = N - 1;
      while (loT < hiT - 1) { var midT = (loT + hiT) >> 1; if (CD[midT].t <= t) loT = midT; else hiT = midT; }
      return loT;
    }
    function styleDash(nodeS) {
      var sv = nodeS && nodeS.k === "name" ? nodeS.v : "";
      if (/dashed/.test(sv)) return [6, 5];
      if (/dotted/.test(sv)) return [2, 4];
      return null;
    }
    function extOf(nodeE) {
      var sv = nodeE && nodeE.k === "name" ? nodeE.v : "";
      if (/both/.test(sv)) return "b";
      if (/right/.test(sv)) return "r";
      if (/left/.test(sv)) return "l";
      return "n";
    }
    function isTimeX(nodeX) { return !!(nodeX && nodeX.k === "name" && /bar_time/.test(nodeX.v)); }

    function srs(node) {
      var key = curCtx + "|" + node.ix;
      return cacheMap[key] || (cacheMap[key] = new Array(N));
    }
    function num(v) { return (v == null || v !== v) ? null : v; }

    function ev(node, i) {
      // inside a for-loop body the iterator changes every pass, so per-bar
      // node caches would freeze the first iteration's values — bypass them
      if (loopDepth > 0) return evRaw(node, i);
      var S = srs(node);
      if (S[i] !== undefined) return S[i];
      var v = evRaw(node, i);
      S[i] = v;
      return v;
    }
    function evRaw(node, i) {
      switch (node.k) {
        case "num": return node.v;
        case "str": return node.v;
        case "colorlit": return node.v;
        case "name": return name(node.v, i);
        case "neg": { var a = ev(node.v, i); return a == null ? null : -a; }
        case "not": { var b = ev(node.v, i); return b == null ? null : (b ? 0 : 1); }
        case "hist": {
          var off = Math.round(Number(ev(node.n, i)) || 0);
          var j = i - off;
          if (j < 0) return null;
          return ev(node.v, j);
        }
        case "tern": { var c = ev(node.c, i); return c ? ev(node.a, i) : ev(node.b, i); }
        case "bin": {
          var l = ev(node.l, i), r;
          if (node.op === "and") { if (!l) return 0; r = ev(node.r, i); return r ? 1 : 0; }
          if (node.op === "or") { if (l) return 1; r = ev(node.r, i); return r ? 1 : 0; }
          r = ev(node.r, i);
          if (l == null || r == null) {
            if (node.op === "==") return l === r ? 1 : 0;
            if (node.op === "!=") return l !== r ? 1 : 0;
            return null;
          }
          switch (node.op) {
            case "+": return l + r; case "-": return l - r; case "*": return l * r;
            case "/": return r === 0 ? null : l / r; case "%": return r === 0 ? null : l % r;
            case "==": return l === r ? 1 : 0; case "!=": return l !== r ? 1 : 0;
            case "<": return l < r ? 1 : 0; case ">": return l > r ? 1 : 0;
            case "<=": return l <= r ? 1 : 0; case ">=": return l >= r ? 1 : 0;
          }
          return null;
        }
        case "call":
          if (node.name === "request.security") return secEval(node, i);
          return callFn(node, i);
        case "rawref": return node.v;   // literal runtime value (method-chain receiver)
        case "mcall": {
          // expression-receiver method call: dispatch on the receiver's TYPE
          var rv9 = ev(node.recv, i);
          if (rv9 == null) return null;
          if (Array.isArray(rv9)) return arrayFn(node.name, node, i, rv9);
          var ns9 = node._syn;
          if (!ns9) {
            ns9 = node._syn = {
              k: "call", ix: node.ix, name: "",
              args: { pos: [{ k: "rawref", v: null, ix: "R" + node.ix }].concat(node.args.pos), named: node.args.named },
            };
          }
          ns9.args.pos[0].v = rv9;
          if (typeof rv9 === "string" && rv9[0] === "L") ns9.name = "line." + node.name;
          else if (typeof rv9 === "string" && rv9[0] === "B") ns9.name = "box." + node.name;
          else if (typeof rv9 === "number") ns9.name = "label." + node.name;
          else if (typeof rv9 === "object" && prog.methods && prog.methods[node.name]) return callUser(prog.methods[node.name], ns9, i);
          else return null;
          return callFn(ns9, i);
        }
      }
      return null;
    }
    function name(id, i) {
      var K = CD[i];
      switch (id) {
        case "close": return K.c; case "open": return K.o; case "high": return K.h; case "low": return K.l;
        case "hl2": return (K.h + K.l) / 2; case "hlc3": return (K.h + K.l + K.c) / 3;
        case "ohlc4": return (K.o + K.h + K.l + K.c) / 4;
        case "volume": return null;
        case "bar_index": return i; case "na": return null;
        case "true": return 1; case "false": return 0;
        case "time": return K.t;
        case "syminfo.tickerid": case "syminfo.ticker": case "syminfo.symbol": return "SELF";
        case "timeframe.period": return "";
        case "chart.bg_color": return "#180527";
        case "chart.fg_color": return "#e9f0fc";
        case "barstate.islast": return i === N - 1 ? 1 : 0;
        case "barstate.isfirst": return i === 0 ? 1 : 0;
        case "barstate.isconfirmed": return 1;
        case "math.pi": return Math.PI;
        case "ta.tr": {   // no-arg VARIABLE form: true range of the current bar
          if (i === 0) return K.h - K.l;
          var pc9 = CD[i - 1].c;
          return Math.max(K.h - K.l, Math.abs(K.h - pc9), Math.abs(K.l - pc9));
        }
      }
      if (COLORS[id]) return COLORS[id];
      // for-loop iterators shadow everything (innermost first)
      for (var lv2 = loopVars.length - 1; lv2 >= 0; lv2--) {
        if (Object.prototype.hasOwnProperty.call(loopVars[lv2], id)) return loopVars[lv2][id];
      }
      // scope chain: innermost function frame (params → locals) → globals
      for (var f = frames.length - 1; f >= 0; f--) {
        var fr = frames[f];
        if (fr.params[id]) {
          var pm = fr.params[id];
          // evaluate the arg in the CALLER's world. CRITICAL: splice+push-back,
          // NOT length truncation — `frames.length = old` after a truncation
          // refills with undefined and corrupts every outer frame (this
          // silently broke all nested user-function scripts).
          var oldCtx = curCtx;
          var savedFr = frames.splice(pm.depth);
          curCtx = pm.ctx;
          var pv;
          try { pv = ev(pm.node, i); }
          finally { curCtx = oldCtx; for (var sf = 0; sf < savedFr.length; sf++) frames.push(savedFr[sf]); }
          return pv;
        }
        if (fr.vars[id]) return readSeries(fr.vars[id], i);
        break;   // only the innermost frame is visible (Pine functions don't close over outer fns)
      }
      var V = vars[id];
      if (V) return readSeries(V, i);
      // dotted names: UDT field access — obj.price, sw.top.y, …
      var dot9 = id.indexOf(".");
      if (dot9 > 0) {
        var head9 = name(id.slice(0, dot9), i);
        if (head9 && typeof head9 === "object" && !Array.isArray(head9)) {
          var parts9 = id.slice(dot9 + 1).split(".");
          var cur9 = head9;
          for (var p9 = 0; p9 < parts9.length; p9++) { if (cur9 == null || typeof cur9 !== "object") return null; cur9 = cur9[parts9[p9]]; }
          return cur9 === undefined ? null : cur9;
        }
      }
      // v2–v4 bare colors — LAST, so script variables named red/green/… win
      if (COLORS["color." + id]) return COLORS["color." + id];
      // Pine enum constants (position.top_right, size.tiny, text.align_left, …)
      // evaluate to their own name so they survive storage in variables and
      // ternaries, and consumers (tables/labels) can pattern-match the string
      if (/^(position|size|text|shape|location|xloc|yloc|extend|display|format|barmerge|currency)\./.test(id)) return id;
      return null;
    }
    function readSeries(V, i) {
      if (V[i] !== undefined) return V[i];
      for (var j = i - 1; j >= 0; j--) if (V[j] !== undefined) return V[j];   // var persistence
      return null;
    }
    // `:=` semantics: assign to wherever the name already lives (frame → global)
    function setVar(id, i, v) {
      // dotted target → UDT field write (objects are by-reference, like Pine)
      var dotW = id.indexOf(".");
      if (dotW > 0) {
        var objW = name(id.slice(0, dotW), i);
        if (objW && typeof objW === "object" && !Array.isArray(objW)) {
          var pw = id.slice(dotW + 1).split(".");
          for (var w9 = 0; w9 < pw.length - 1; w9++) { objW = objW[pw[w9]]; if (objW == null || typeof objW !== "object") return; }
          objW[pw[pw.length - 1]] = v;
        }
        return;
      }
      if (frames.length) {
        var fr = frames[frames.length - 1];
        if (fr.vars[id] || !vars[id]) { (fr.vars[id] || (fr.vars[id] = new Array(N)))[i] = v; return; }
      }
      (vars[id] || (vars[id] = new Array(N)))[i] = v;
    }
    function childSeries(node, i, len, pick) {
      // materialize child's values over [i-len+1 .. i] via its cache
      var vals = [], any = false;
      for (var j = Math.max(0, i - len + 1); j <= i; j++) { var v = ev(node, j); vals.push(v); if (v != null) any = true; }
      return any ? vals : null;
    }
    function windowed(node, i, len, fn) {
      if (i < len - 1) return null;
      var vals = childSeries(node, i, len, null);
      if (!vals) return null;
      for (var k = 0; k < vals.length; k++) if (vals[k] == null) return null;
      return fn(vals);
    }
    function stKey(node, extra) { return curCtx + "|" + node.ix + ":" + (extra || ""); }

    /* ── Pine arrays: JS-array backed, by-reference like Pine ────────────── */
    /* map.* — a JS Map behind Pine's collection API. Without this every map call
     * fell through to the default branch and returned null, so a script that kept
     * its zones/levels in a map compiled "cleanly" and then drew nothing at all:
     * the worst kind of failure, because it looks like the script is just wrong.
     * `var` persistence works exactly as it does for arrays — the same object
     * reference is carried forward bar to bar. */
    function mapFn(op, node, i, self) {
      var p = node.args.pos;
      if (!self && op.indexOf("new") === 0) return new Map();
      var M2 = self || ev(p[0], i);
      if (!(M2 instanceof Map)) return null;
      var off = self ? 0 : 1;
      var k1 = p[off] !== undefined ? ev(p[off], i) : null;
      var v1 = p[off + 1] !== undefined ? ev(p[off + 1], i) : null;
      switch (op) {
        case "put": { var old = M2.has(k1) ? M2.get(k1) : null; M2.set(k1, v1); return old; }
        case "get": return M2.has(k1) ? M2.get(k1) : null;
        case "contains": return M2.has(k1) ? 1 : 0;
        case "remove": { var rv = M2.has(k1) ? M2.get(k1) : null; M2["delete"](k1); return rv; }
        case "size": return M2.size;
        case "clear": M2.clear(); return null;
        case "keys": { var ks = []; M2.forEach(function (_v, k) { ks.push(k); }); return ks; }
        case "values": { var vs = []; M2.forEach(function (v) { vs.push(v); }); return vs; }
        case "copy": { var cp = new Map(); M2.forEach(function (v, k) { cp.set(k, v); }); return cp; }
        case "put_all": { var src = v1 || k1; if (src instanceof Map) src.forEach(function (v, k) { M2.set(k, v); }); return null; }
      }
      return null;
    }
    function arrayFn(op, node, i, self) {
      var p = node.args.pos;
      if (!self && op.indexOf("new") === 0) {   // new / new_float / new_int / new_box / …
        var sz = p[0] ? Math.max(0, Math.round(Number(ev(p[0], i)) || 0)) : 0;
        var iv = p[1] !== undefined ? ev(p[1], i) : null;
        var mk = []; for (var q = 0; q < sz; q++) mk.push(iv);
        return mk;
      }
      if (!self && op === "from") { var af = []; for (var q2 = 0; q2 < p.length; q2++) af.push(ev(p[q2], i)); return af; }
      var A2 = self || ev(p[0], i);
      if (!Array.isArray(A2)) return null;
      var off = self ? 0 : 1;
      var x1 = p[off] !== undefined ? ev(p[off], i) : null;
      var x2 = p[off + 1] !== undefined ? ev(p[off + 1], i) : null;
      switch (op) {
        case "push": A2.push(x1); return null;
        case "unshift": A2.unshift(x1); return null;
        case "pop": return A2.length ? A2.pop() : null;
        case "shift": return A2.length ? A2.shift() : null;
        case "get": { var gi = Math.round(Number(x1)); return (gi >= 0 && gi < A2.length) ? A2[gi] : null; }
        case "set": { var si5 = Math.round(Number(x1)); if (si5 >= 0 && si5 < A2.length) A2[si5] = x2; return null; }
        case "size": return A2.length;
        case "remove": { var ri = Math.round(Number(x1)); return (ri >= 0 && ri < A2.length) ? A2.splice(ri, 1)[0] : null; }
        case "insert": { var ni = Math.round(Number(x1)); A2.splice(Math.max(0, ni), 0, x2); return null; }
        case "clear": A2.length = 0; return null;
        case "indexof": return A2.indexOf(x1);
        case "includes": return A2.indexOf(x1) >= 0 ? 1 : 0;
        case "first": return A2.length ? A2[0] : null;
        case "last": return A2.length ? A2[A2.length - 1] : null;
        case "reverse": A2.reverse(); return null;
        case "copy": case "slice": return op === "copy" ? A2.slice() : A2.slice(Math.round(Number(x1)) || 0, x2 != null ? Math.round(Number(x2)) : undefined);
        case "avg": case "sum": case "min": case "max": {
          var s6 = 0, mn6 = Infinity, mx6 = -Infinity, c6 = 0;
          for (var q6 = 0; q6 < A2.length; q6++) { var v6 = Number(A2[q6]); if (A2[q6] == null || !isFinite(v6)) continue; s6 += v6; c6++; if (v6 < mn6) mn6 = v6; if (v6 > mx6) mx6 = v6; }
          if (!c6) return null;
          return op === "sum" ? s6 : op === "avg" ? s6 / c6 : op === "min" ? mn6 : mx6;
        }
      }
      return null;
    }

    /* ── request.security: same-symbol HTF via candle resampling ────────── */
    function tfMinutes(tf) {
      tf = String(tf || "").trim().toUpperCase();
      if (!tf) return 0;
      if (/^\d+$/.test(tf)) return +tf;                       // plain minutes
      if (/^\d+S$/.test(tf)) return Math.max(1, Math.round(parseInt(tf, 10) / 60));
      var mm = /^(\d*)([DWM])$/.exec(tf);
      if (mm) { var mult = +(mm[1] || 1); return mm[2] === "D" ? 1440 * mult : mm[2] === "W" ? 10080 * mult : 43200 * mult; }
      return 0;
    }
    function bucketOf(t, tf) {
      var up = String(tf).trim().toUpperCase();
      var mm = /^(\d*)([DWM])$/.exec(up);
      if (mm) {
        var mult2 = +(mm[1] || 1);
        if (mm[2] === "D") return Math.floor(Math.floor(t / 86400000) / mult2);
        if (mm[2] === "W") return Math.floor(Math.floor((Math.floor(t / 86400000) + 3) / 7) / mult2);   // Monday-aligned weeks
        var d = new Date(t);
        return Math.floor((d.getUTCFullYear() * 12 + d.getUTCMonth()) / mult2);
      }
      var mins = tfMinutes(up) || 1;
      return Math.floor(t / (mins * 60000));
    }
    function secData(tf) {
      var key = "SECDATA|" + tf + "|" + N;
      if (state[key]) return state[key];
      var hc = [], map = new Array(N), lastB = null;
      for (var q = 0; q < N; q++) {
        var Kq = CD[q], b = bucketOf(Kq.t, tf);
        if (b !== lastB) { hc.push({ t: Kq.t, o: Kq.o, h: Kq.h, l: Kq.l, c: Kq.c }); lastB = b; }
        else { var Kx = hc[hc.length - 1]; if (Kq.h > Kx.h) Kx.h = Kq.h; if (Kq.l < Kx.l) Kx.l = Kq.l; Kx.c = Kq.c; }
        map[q] = hc.length - 1;
      }
      return (state[key] = { candles: hc, map: map });
    }
    function secEval(node, i) {
      var p = node.args.pos;
      var exprN = node.args.named.expression || p[2];
      if (!exprN) return null;
      var tfN = node.args.named.timeframe || p[1];
      var tfV = tfN ? (tfN.k === "str" ? tfN.v : ev(tfN, i)) : "";
      if (!tfV || !String(tfV).trim()) return ev(exprN, i);   // "" → chart timeframe: identity
      var R = secData(String(tfV).trim());
      var hi = R.map[i];
      if (hi == null) return null;
      var oldCD = CD, oldN = N, oldCtx = curCtx;
      CD = R.candles; N = R.candles.length;
      curCtx = "S" + node.ix + "»" + oldCtx;   // isolated cache namespace per call site
      try { return ev(exprN, hi); }
      finally { CD = oldCD; N = oldN; curCtx = oldCtx; }
    }
    function rmaCalc(node, srcNode, lenNode, i) {
      var len = Math.max(1, Math.round(Number(ev(lenNode, i)) || 1));
      var key = stKey(node, len);
      var st = state[key] || (state[key] = { last: null, at: -1 });
      if (st.at === i) return st.lastOut;
      var v = ev(srcNode, i);
      var out;
      if (v == null) out = st.last;
      else if (st.last == null) {
        // seed with SMA when enough bars
        out = windowed(srcNode, i, len, function (vals) { var s = 0; for (var k = 0; k < vals.length; k++) s += vals[k]; return s / vals.length; });
        if (out == null && i >= 0) out = v;
      } else out = (st.last * (len - 1) + v) / len;
      st.last = out; st.at = i; st.lastOut = out;
      return out;
    }
    function emaCalc(node, srcNode, lenNode, i) {
      var len = Math.max(1, Math.round(Number(ev(lenNode, i)) || 1));
      var key = stKey(node, "e" + len);
      var st = state[key] || (state[key] = { last: null, at: -1 });
      if (st.at === i) return st.lastOut;
      var v = ev(srcNode, i), out;
      var k2 = 2 / (len + 1);
      if (v == null) out = st.last;
      else if (st.last == null) out = v;
      else out = v * k2 + st.last * (1 - k2);
      st.last = out; st.at = i; st.lastOut = out;
      return out;
    }
    function trueRange(i) {
      var K = candles[i];
      if (i === 0) return K.h - K.l;
      var pc = candles[i - 1].c;
      return Math.max(K.h - K.l, Math.abs(K.h - pc), Math.abs(K.l - pc));
    }
    var TRNODE = { ix: prog.nodeCount };       // synthetic node id for atr's rma source
    var trArr = new Array(N);                  // tr is context-free — one shared cache
    function trAt(i) {
      if (trArr[i] === undefined) trArr[i] = trueRange(i);
      return trArr[i];
    }

    function callFn(node, i) {
      var a = node.args, p = a.pos, nm = node.name;
      switch (nm) {
        case "nz": { var v0 = ev(p[0], i); return v0 == null ? (p[1] ? ev(p[1], i) : 0) : v0; }
        case "na": { return ev(p[0], i) == null ? 1 : 0; }
        case "iff": { return ev(p[0], i) ? ev(p[1], i) : ev(p[2], i); }
        case "color": return p[0] ? ev(p[0], i) : null;   // color(na) / v3 color(red)
        case "timeframe.change": {
          var tfc = p[0] ? (p[0].k === "str" ? p[0].v : ev(p[0], i)) : "";
          if (!tfc || !String(tfc).trim()) return 1;   // chart tf changes every bar
          if (i === 0) return 1;
          return bucketOf(CD[i].t, tfc) !== bucketOf(CD[i - 1].t, tfc) ? 1 : 0;
        }
        case "timeframe.in_seconds": { var tfs = p[0] ? (p[0].k === "str" ? p[0].v : ev(p[0], i)) : ""; return tfMinutes(tfs) * 60 || null; }
        case "time": {
          // time(timeframe[, session, timezone]) — bar open time in UNIX ms.
          // With a "HHMM-HHMM" session arg, returns na outside the session
          // (timezone offset honored); a bare timezone arg doesn't shift the
          // returned epoch value — exactly Pine's semantics.
          var sesS = p[1] && p[1].k === "str" ? p[1].v : null;
          var mSes = sesS && /^(\d{2})(\d{2})-(\d{2})(\d{2})/.exec(sesS);
          if (mSes) {
            var tzOff = 0;
            var tzS = p[2] && p[2].k === "str" ? p[2].v : null;
            var mTz = tzS && /(?:UTC|GMT)([+-])(\d+)(?::(\d+))?/.exec(tzS);
            if (mTz) tzOff = (mTz[1] === "-" ? -1 : 1) * ((+mTz[2]) * 60 + (+(mTz[3] || 0)));
            var modM = ((CD[i].t / 60000) + tzOff) % 1440; if (modM < 0) modM += 1440;
            var sFrom = (+mSes[1]) * 60 + (+mSes[2]), sTo = (+mSes[3]) * 60 + (+mSes[4]);
            var inSes = sFrom <= sTo ? (modM >= sFrom && modM <= sTo) : (modM >= sFrom || modM <= sTo);
            return inSes ? CD[i].t : null;
          }
          return CD[i].t;
        }
        case "timestamp": {
          // timestamp("YYYY-MM-DD HH:MM:SS±HH:MM") or timestamp([tz,] y, m, d[, h, min])
          if (p[0] && p[0].k === "str" && p.length === 1) {
            var tsP = Date.parse(p[0].v.trim().replace(" ", "T"));
            return isFinite(tsP) ? tsP : null;
          }
          var tOff = (p[0] && p[0].k === "str") ? 1 : 0;   // leading timezone arg: components read as UTC
          var yT = Number(ev(p[tOff], i));
          if (!isFinite(yT)) return null;
          var moT = p[tOff + 1] ? Number(ev(p[tOff + 1], i)) : 1;
          var dT = p[tOff + 2] ? Number(ev(p[tOff + 2], i)) : 1;
          var hT = p[tOff + 3] ? Number(ev(p[tOff + 3], i)) : 0;
          var miT = p[tOff + 4] ? Number(ev(p[tOff + 4], i)) : 0;
          return Date.UTC(yT, (moT || 1) - 1, dT || 1, hT || 0, miT || 0, 0);
        }
        case "fixnan": { var fv = ev(p[0], i); if (fv != null) return fv; for (var fj = i - 1; fj >= 0; fj--) { var pv = ev(p[0], fj); if (pv != null) return pv; } return null; }
        case "color.new": {
          var cnBase = colorArg(node);
          if (cnBase) return cnBase;
          var cnV = p[0] ? ev(p[0], i) : null;   // runtime color value (variable)
          if (typeof cnV !== "string") return null;
          var cnT = p[1] ? Number(ev(p[1], i)) || 0 : 0;
          if (cnT <= 0) return cnV;
          var cnH = toHex6(cnV);
          return "rgba(" + parseInt(cnH.slice(1, 3), 16) + "," + parseInt(cnH.slice(3, 5), 16) + "," + parseInt(cnH.slice(5, 7), 16) + "," + Math.max(0, Math.min(1, (100 - cnT) / 100)).toFixed(2) + ")";
        }
        case "color.rgb": {   // runtime form (args may be variables)
          var crR = Math.round(Number(ev(p[0], i))) || 0, crG = Math.round(Number(ev(p[1], i))) || 0, crB = Math.round(Number(ev(p[2], i))) || 0;
          var crT = p[3] ? Number(ev(p[3], i)) || 0 : 0;
          return "rgba(" + crR + "," + crG + "," + crB + "," + Math.max(0, Math.min(1, (100 - crT) / 100)).toFixed(2) + ")";
        }
        case "math.abs": return mval(p[0], i, Math.abs);
        case "math.sqrt": return mval(p[0], i, Math.sqrt);
        case "math.floor": return mval(p[0], i, Math.floor);
        case "math.ceil": return mval(p[0], i, Math.ceil);
        case "math.round": { var rv = ev(p[0], i); if (rv == null) return null; var dg = p[1] ? Number(ev(p[1], i)) : 0; var m = Math.pow(10, dg || 0); return Math.round(rv * m) / m; }
        case "math.exp": return mval(p[0], i, Math.exp);
        case "math.log": return mval(p[0], i, Math.log);
        case "math.sign": return mval(p[0], i, Math.sign);
        case "math.pow": { var b1 = ev(p[0], i), e1 = ev(p[1], i); return (b1 == null || e1 == null) ? null : Math.pow(b1, e1); }
        case "math.max": return redu(p, i, Math.max);
        case "math.min": return redu(p, i, Math.min);
        case "math.avg": { var s1 = redu(p, i, function () { var s = 0; for (var q = 0; q < arguments.length; q++) s += arguments[q]; return s; }); return s1 == null ? null : s1 / p.length; }
        case "ta.sma": case "sma": return windowed(p[0], i, len_(p[1], i), function (vals) { var s = 0; for (var k = 0; k < vals.length; k++) s += vals[k]; return s / vals.length; });
        case "ta.ema": case "ema": return emaCalc(node, p[0], p[1], i);
        case "ta.rma": case "rma": return rmaCalc(node, p[0], p[1], i);
        case "ta.wma": case "wma": return windowed(p[0], i, len_(p[1], i), function (vals) { var s = 0, w = 0; for (var k = 0; k < vals.length; k++) { s += vals[k] * (k + 1); w += k + 1; } return s / w; });
        case "ta.stdev": case "stdev": return windowed(p[0], i, len_(p[1], i), function (vals) { var m = 0, k; for (k = 0; k < vals.length; k++) m += vals[k]; m /= vals.length; var s = 0; for (k = 0; k < vals.length; k++) s += (vals[k] - m) * (vals[k] - m); return Math.sqrt(s / vals.length); });
        case "ta.dev": case "dev": return windowed(p[0], i, len_(p[1], i), function (vals) { var m = 0, k; for (k = 0; k < vals.length; k++) m += vals[k]; m /= vals.length; var s = 0; for (k = 0; k < vals.length; k++) s += Math.abs(vals[k] - m); return s / vals.length; });
        case "ta.highest": case "highest": return hilo(p, i, Math.max);
        case "ta.lowest": case "lowest": return hilo(p, i, Math.min);
        case "ta.sum": case "math.sum": return windowed(p[0], i, len_(p[1], i), function (vals) { var s = 0; for (var k = 0; k < vals.length; k++) s += vals[k]; return s; });
        case "ta.alma": {
          var alLen = Math.max(1, Math.round(Number(ev(p[1], i)) || 9));
          var alOff = p[2] ? Number(ev(p[2], i)) : 0.85;
          var alSig = p[3] ? Number(ev(p[3], i)) : 6;
          return windowed(p[0], i, alLen, function (vals) {
            var m2 = alOff * (vals.length - 1), s2 = vals.length / alSig, ws = 0, sm2 = 0;
            for (var k7 = 0; k7 < vals.length; k7++) { var w7 = Math.exp(-((k7 - m2) * (k7 - m2)) / (2 * s2 * s2)); ws += w7; sm2 += vals[k7] * w7; }
            return ws ? sm2 / ws : null;
          });
        }
        case "ta.cum": { var ck = stKey(node); var cs = state[ck] || (state[ck] = { s: 0, at: -1 }); if (cs.at !== i) { var cv = ev(p[0], i); cs.s += (cv || 0); cs.at = i; } return cs.s; }
        case "ta.mom":
        case "ta.change": case "change": { var chl = p[1] ? len_(p[1], i) : 1; if (i < chl) return null; var c1 = ev(p[0], i), c0 = ev(p[0], i - chl); return (c1 == null || c0 == null) ? null : c1 - c0; }
        case "ta.mom": return callFn({ k: "call", name: "ta.change", args: node.args, ix: node.ix }, i);
        case "ta.roc": { var rl = len_(p[1], i); if (i < rl) return null; var rc = ev(p[0], i), r0 = ev(p[0], i - rl); return (rc == null || r0 == null || r0 === 0) ? null : (rc - r0) / r0 * 100; }
        case "ta.tr": case "tr": return trAt(i);
        case "ta.atr": case "atr": return rmaCalc(node, { k: "call", name: "ta.tr", args: { pos: [], named: {} }, ix: TRNODE.ix }, p[0], i);
        case "ta.rsi": case "rsi": {
          var rk = stKey(node); var rs = state[rk] || (state[rk] = { g: null, l: null, at: -1, out: null });
          if (rs.at === i) return rs.out;
          var rlen = len_(p[1], i);
          var cur = ev(p[0], i), prev = i > 0 ? ev(p[0], i - 1) : null;
          if (cur == null || prev == null) { rs.at = i; return rs.out = null; }
          var up = Math.max(cur - prev, 0), dn = Math.max(prev - cur, 0);
          rs.g = rs.g == null ? up : (rs.g * (rlen - 1) + up) / rlen;
          rs.l = rs.l == null ? dn : (rs.l * (rlen - 1) + dn) / rlen;
          rs.at = i;
          return rs.out = rs.l === 0 ? 100 : 100 - 100 / (1 + rs.g / rs.l);
        }
        case "ta.crossover": case "crossover": return crossDir(p, i, 1);
        case "ta.crossunder": case "crossunder": return crossDir(p, i, -1);
        case "ta.cross": case "cross": { var cd = crossDir(p, i, 1) || crossDir(p, i, -1); return cd ? 1 : 0; }
        case "ta.macd": case "macd": return null;   // consumed via tuple destructure below
        default:
          // user-defined types: TYPE.new(args…) → object (positional or named)
          var dotT = nm.lastIndexOf(".");
          if (dotT > 0 && nm.slice(dotT + 1) === "new" && prog.types && prog.types[nm.slice(0, dotT)]) {
            var TY = prog.types[nm.slice(0, dotT)], objN = {};
            for (var tf3 = 0; tf3 < TY.length; tf3++) {
              var fld = TY[tf3];
              var argN = a.named[fld.name] !== undefined ? a.named[fld.name] : p[tf3];
              objN[fld.name] = argN ? ev(argN, i) : (fld.def ? ev(fld.def, i) : null);
            }
            return objN;
          }
          if (nm.indexOf("array.") === 0) return arrayFn(nm.slice(6), node, i);
          if (nm.indexOf("map.") === 0) return mapFn(nm.slice(4), node, i);
          if (prog.funcs && prog.funcs[nm]) return callUser(prog.funcs[nm], node, i);
          // v2–v4 bare names: resolve once, then permanently redirect this node
          if (nm.indexOf(".") < 0 && BARE_ALIAS[nm]) { node.name = BARE_ALIAS[nm]; return callFn(node, i); }
          // method sugar: arr.push(x) → array.push(arr, x); obj.method(args) →
          // user method with `this` bound to the receiver
          if (dotT > 0) {
            var headNmM = nm.slice(0, dotT), tailM = nm.slice(dotT + 1);
            var headM = name(headNmM, i);
            if (Array.isArray(headM)) return arrayFn(tailM, node, i, headM);
            if (headM instanceof Map) return mapFn(tailM, node, i, headM);
            if (headM && typeof headM === "object" && prog.methods && prog.methods[tailM]) {
              // synthetic call node cached per site so caches/`var` state stay stable
              var synM = node._msyn || (node._msyn = {
                k: "call", name: tailM, ix: node.ix,
                args: { pos: [{ k: "name", v: headNmM, ix: "M" + node.ix }].concat(node.args.pos), named: node.args.named },
              });
              return callUser(prog.methods[tailM], synM, i);
            }
          }
          break;
      }
      switch (nm) {
        case "str.tostring": {
          var sv6 = ev(p[0], i);
          if (sv6 == null) return "na";
          if (typeof sv6 === "string") return sv6;
          var fmt6 = p[1] ? (p[1].k === "str" ? p[1].v : ev(p[1], i)) : null;
          if (typeof fmt6 === "string" && /^format\./.test(fmt6)) return String(parseFloat(Number(sv6).toFixed(6)));
          if (typeof fmt6 === "string" && fmt6.indexOf(".") >= 0) {
            var dec6 = (fmt6.split(".")[1] || "").length;
            return Number(sv6).toFixed(Math.min(10, dec6));
          }
          return String(Math.round(Number(sv6) * 10000) / 10000);
        }
        case "label.new": {
          if (loopDepth === 0) {
            // Pine positional signature: (x, y, text, xloc, yloc, color, style, textcolor, size)
            var yv5 = Number(ev(p[1], i));
            var txt5 = a.named.text || p[2];
            var stl5 = a.named.style || p[6];
            var txtV = "";
            if (txt5) { var tvv = (txt5.k === "str") ? txt5.v : ev(txt5, i); txtV = tvv == null ? "" : String(tvv); }
            if (labelsOut.length >= 1500) labelsOut.shift();   // deep-history signal review: keep the most recent 1500
            var lbObj = {
              id: labelSeq++,
              i: i, y: isFinite(yv5) ? yv5 : null,
              text: txtV,
              color: colorArg(a.named.color) || colorArg(p[5]) || "#f5b942",
              tcolor: colorArg(a.named.textcolor) || colorArg(p[7]) || "#0b0f1a",
              up: !(stl5 && stl5.k === "name" && /down/.test(stl5.v)),
            };
            // pointer direction from label.style_* — drives TV-style tag geometry
            var stlV5 = stl5 ? (stl5.k === "name" ? stl5.v : String(ev(stl5, i) || "")) : "";
            lbObj.dir = /right/.test(stlV5) ? "right" : /left/.test(stlV5) ? "left" : /down/.test(stlV5) ? "down" : /up/.test(stlV5) ? "up" : (lbObj.up ? "up" : "down");
            // colors may be VARIABLES (input colors) — resolve at runtime too
            var lcRun = evColor(a.named.color || p[5], i);
            if (lcRun) lbObj.color = lcRun;
            var ltRun = evColor(a.named.textcolor || p[7], i);
            if (ltRun) lbObj.tcolor = ltRun;
            labelById[lbObj.id] = lbObj;
            labelsOut.push(lbObj);
            return lbObj.id;   // a real handle: label.delete / label.set_* work on it
          }
          return null;
        }
        case "label.delete": { var idD = ev(p[0], i); if (idD != null && labelById[idD]) labelById[idD].del = 1; return null; }
        /* ── ALERTS: recorded per bar; the live bar's fires become on-chart popups ── */
        case "alert": {
          if (loopDepth === 0) {
            var aMsg = a.named.message || p[0];
            var aVal = aMsg ? (aMsg.k === "str" ? aMsg.v : ev(aMsg, i)) : "";
            if (alertsOut.length >= 400) alertsOut.shift();
            alertsOut.push({ i: i, t: CD[i] ? CD[i].t : null, msg: aVal == null ? "" : String(aVal) });
          }
          return null;
        }
        case "alertcondition": {
          if (loopDepth === 0) {
            var acN = a.named.condition || p[0];
            var acV = acN ? ev(acN, i) : null;
            if (acV) {
              var acM = a.named.message || p[2] || a.named.title || p[1];
              var acS = acM ? (acM.k === "str" ? acM.v : ev(acM, i)) : "Alert";
              if (alertsOut.length >= 400) alertsOut.shift();
              alertsOut.push({ i: i, t: CD[i] ? CD[i].t : null, msg: acS == null ? "Alert" : String(acS) });
            }
          }
          return null;
        }
        /* ── LINE objects: full Pine API on real handles ─────────────────── */
        case "line.new": {
          // line.new(x1, y1, x2, y2, xloc, extend, color, style, width)
          var lT = isTimeX(a.named.xloc || p[4]);
          var L9 = {
            id: "L" + (lineSeq++),
            x1: Number(ev(p[0], i)), y1: Number(ev(p[1], i)),
            x2: Number(ev(p[2], i)), y2: Number(ev(p[3], i)),
            t: lT ? 1 : 0,
            c: evColor(a.named.color || p[6], i) || "#8ea2c9",
            dash: styleDash(a.named.style || p[7]),
            w: Math.max(1, Number((a.named.width ? ev(a.named.width, i) : (p[8] ? ev(p[8], i) : 1))) || 1),
            ext: extOf(a.named.extend || p[5]),
          };
          lineById[L9.id] = L9;
          if (linesOut.length >= 500) linesOut.shift();
          linesOut.push(L9);
          return L9.id;
        }
        case "line.delete": { var lDl = lineById[ev(p[0], i)]; if (lDl) lDl.del = 1; return null; }
        case "linefill.new": {
          var lfA = ev(p[0], i), lfB = ev(p[1], i);
          if (lfA == null || lfB == null) return null;
          if (linefillsOut.length >= 200) linefillsOut.shift();
          linefillsOut.push({ a: lfA, b: lfB, c: evColor(a.named.color || p[2], i) || "rgba(120,150,255,.12)" });
          return null;
        }
        case "line.set_xy1": { var l1 = lineById[ev(p[0], i)]; if (l1) { l1.x1 = Number(ev(p[1], i)); l1.y1 = Number(ev(p[2], i)); } return null; }
        case "line.set_xy2": { var l2 = lineById[ev(p[0], i)]; if (l2) { l2.x2 = Number(ev(p[1], i)); l2.y2 = Number(ev(p[2], i)); } return null; }
        case "line.set_x1": { var l3 = lineById[ev(p[0], i)]; if (l3) l3.x1 = Number(ev(p[1], i)); return null; }
        case "line.set_x2": { var l4 = lineById[ev(p[0], i)]; if (l4) l4.x2 = Number(ev(p[1], i)); return null; }
        case "line.set_y1": { var l5 = lineById[ev(p[0], i)]; if (l5) l5.y1 = Number(ev(p[1], i)); return null; }
        case "line.set_y2": { var l6 = lineById[ev(p[0], i)]; if (l6) l6.y2 = Number(ev(p[1], i)); return null; }
        case "line.set_color": { var l7 = lineById[ev(p[0], i)]; if (l7) l7.c = evColor(p[1], i) || l7.c; return null; }
        case "line.set_width": { var l8 = lineById[ev(p[0], i)]; if (l8) l8.w = Math.max(1, Number(ev(p[1], i)) || 1); return null; }
        case "line.set_style": { var l10 = lineById[ev(p[0], i)]; if (l10) l10.dash = styleDash(p[1]); return null; }
        case "line.set_extend": { var l11 = lineById[ev(p[0], i)]; if (l11) l11.ext = extOf(p[1]); return null; }
        case "line.get_x1": { var g1 = lineById[ev(p[0], i)]; return g1 ? g1.x1 : null; }
        case "line.get_x2": { var g2 = lineById[ev(p[0], i)]; return g2 ? g2.x2 : null; }
        case "line.get_y1": { var g3 = lineById[ev(p[0], i)]; return g3 ? g3.y1 : null; }
        case "line.get_y2": { var g4 = lineById[ev(p[0], i)]; return g4 ? g4.y2 : null; }
        case "line.get_price": {
          var gp = lineById[ev(p[0], i)]; if (!gp) return null;
          var xq = Number(ev(p[1], i));
          var ga = gp.t ? tIdx(gp.x1) : gp.x1, gb = gp.t ? tIdx(gp.x2) : gp.x2;
          if (!isFinite(ga) || !isFinite(gb) || !isFinite(gp.y1) || !isFinite(gp.y2)) return null;
          if (gb === ga) return gp.y1;
          return gp.y1 + (gp.y2 - gp.y1) * ((xq - ga) / (gb - ga));
        }
        /* ── BOX objects: full Pine API on real handles ──────────────────── */
        case "box.new": {
          // box.new(left, top, right, bottom, border_color, border_width,
          //         border_style, extend, xloc, bgcolor, …)
          var bT = isTimeX(a.named.xloc || p[8]);
          var B9 = {
            id: "B" + (boxSeq++),
            left: Number(ev(a.named.left || p[0], i)), top: Number(ev(a.named.top || p[1], i)),
            right: Number(ev(a.named.right || p[2], i)), bottom: Number(ev(a.named.bottom || p[3], i)),
            t: bT ? 1 : 0,
            bc: evColor(a.named.border_color || p[4], i),
            bg: evColor(a.named.bgcolor || p[9], i),
            dash: styleDash(a.named.border_style || p[6]),
            ext: extOf(a.named.extend || p[7]),
          };
          boxById[B9.id] = B9;
          if (boxesOut.length >= 500) boxesOut.shift();
          boxesOut.push(B9);
          return B9.id;
        }
        case "box.delete": { var bDl = boxById[ev(p[0], i)]; if (bDl) bDl.del = 1; return null; }
        case "box.set_lefttop": { var b1 = boxById[ev(p[0], i)]; if (b1) { b1.left = Number(ev(p[1], i)); b1.top = Number(ev(p[2], i)); } return null; }
        case "box.set_rightbottom": { var b2 = boxById[ev(p[0], i)]; if (b2) { b2.right = Number(ev(p[1], i)); b2.bottom = Number(ev(p[2], i)); } return null; }
        case "box.set_left": { var b3 = boxById[ev(p[0], i)]; if (b3) b3.left = Number(ev(p[1], i)); return null; }
        case "box.set_right": { var b4 = boxById[ev(p[0], i)]; if (b4) b4.right = Number(ev(p[1], i)); return null; }
        case "box.set_top": { var b5 = boxById[ev(p[0], i)]; if (b5) b5.top = Number(ev(p[1], i)); return null; }
        case "box.set_bottom": { var b6 = boxById[ev(p[0], i)]; if (b6) b6.bottom = Number(ev(p[1], i)); return null; }
        case "box.set_bgcolor": { var b7 = boxById[ev(p[0], i)]; if (b7) b7.bg = evColor(p[1], i) || b7.bg; return null; }
        case "box.set_border_color": { var b8 = boxById[ev(p[0], i)]; if (b8) b8.bc = evColor(p[1], i) || b8.bc; return null; }
        case "box.set_border_style": { var b10 = boxById[ev(p[0], i)]; if (b10) b10.dash = styleDash(p[1]); return null; }
        case "box.set_extend": { var b11 = boxById[ev(p[0], i)]; if (b11) b11.ext = extOf(p[1]); return null; }
        case "box.get_top": { var bg1 = boxById[ev(p[0], i)]; return bg1 ? bg1.top : null; }
        case "box.get_bottom": { var bg2 = boxById[ev(p[0], i)]; return bg2 ? bg2.bottom : null; }
        case "box.get_left": { var bg3 = boxById[ev(p[0], i)]; return bg3 ? bg3.left : null; }
        case "box.get_right": { var bg4 = boxById[ev(p[0], i)]; return bg4 ? bg4.right : null; }
        /* ── TABLE objects: corner-anchored dashboards ───────────────────── */
        case "table.new": {
          // table.new(position, columns, rows, bgcolor, frame_color,
          //           frame_width, border_color, border_width)
          var tpN = a.named.position || p[0];
          var tpV = tpN ? ev(tpN, i) : "position.top_right";
          var T9 = {
            id: "T" + (tableSeq++),
            pos: typeof tpV === "string" ? tpV : "position.top_right",
            bg: evColor(a.named.bgcolor || p[3], i),
            frameC: evColor(a.named.frame_color || p[4], i),
            fw: Number((a.named.frame_width ? ev(a.named.frame_width, i) : (p[5] ? ev(p[5], i) : 1))) || 1,
            borderC: evColor(a.named.border_color || p[6], i),
            bw: Number((a.named.border_width ? ev(a.named.border_width, i) : (p[7] ? ev(p[7], i) : 1))) || 1,
            cells: {},
          };
          tableById[T9.id] = T9;
          if (tablesOut.length >= 4) tablesOut.shift();
          tablesOut.push(T9);
          return T9.id;
        }
        case "table.cell": {
          // table.cell(table, column, row, text, width, height, text_color,
          //            text_halign, text_valign, text_size, bgcolor, tooltip)
          var TC = tableById[ev(p[0], i)]; if (!TC) return null;
          var tcC = Math.round(Number(ev(p[1], i))) || 0, tcR = Math.round(Number(ev(p[2], i))) || 0;
          var tcTxtN = a.named.text || p[3];
          var tcTxtV = tcTxtN ? ev(tcTxtN, i) : "";
          var haV = (a.named.text_halign ? ev(a.named.text_halign, i) : (p[7] ? ev(p[7], i) : "")) || "";
          var szV = (a.named.text_size ? ev(a.named.text_size, i) : (p[9] ? ev(p[9], i) : "")) || "";
          TC.cells[tcC + "," + tcR] = {
            t: tcTxtV == null ? "" : String(tcTxtV),
            tc: evColor(a.named.text_color || p[6], i) || "#e9f0fc",
            bg: evColor(a.named.bgcolor || p[10], i),
            ha: /left/.test(String(haV)) ? "left" : /right/.test(String(haV)) ? "right" : "center",
            sz: String(szV).replace(/^size\./, "") || "small",
          };
          return null;
        }
        case "table.cell_set_bgcolor": { var TB1 = tableById[ev(p[0], i)]; if (TB1) { var ck1 = (Math.round(Number(ev(p[1], i))) || 0) + "," + (Math.round(Number(ev(p[2], i))) || 0); (TB1.cells[ck1] = TB1.cells[ck1] || { t: "", tc: "#e9f0fc", ha: "center", sz: "small" }).bg = evColor(p[3], i); } return null; }
        case "table.cell_set_text": { var TB2 = tableById[ev(p[0], i)]; if (TB2) { var ck2 = (Math.round(Number(ev(p[1], i))) || 0) + "," + (Math.round(Number(ev(p[2], i))) || 0); (TB2.cells[ck2] = TB2.cells[ck2] || { t: "", tc: "#e9f0fc", ha: "center", sz: "small" }).t = String(ev(p[3], i) == null ? "" : ev(p[3], i)); } return null; }
        case "table.cell_set_text_color": { var TB3 = tableById[ev(p[0], i)]; if (TB3) { var ck3 = (Math.round(Number(ev(p[1], i))) || 0) + "," + (Math.round(Number(ev(p[2], i))) || 0); if (TB3.cells[ck3]) TB3.cells[ck3].tc = evColor(p[3], i) || TB3.cells[ck3].tc; } return null; }
        case "table.clear": {
          var TB4 = tableById[ev(p[0], i)]; if (!TB4) return null;
          var c1T = p[1] ? Math.round(Number(ev(p[1], i))) || 0 : 0, r1T = p[2] ? Math.round(Number(ev(p[2], i))) || 0 : 0;
          var c2T = p[3] ? Math.round(Number(ev(p[3], i))) || 0 : 99, r2T = p[4] ? Math.round(Number(ev(p[4], i))) || 0 : 99;
          Object.keys(TB4.cells).forEach(function (k9) {
            var pr9 = k9.split(",");
            if (+pr9[0] >= c1T && +pr9[0] <= c2T && +pr9[1] >= r1T && +pr9[1] <= r2T) delete TB4.cells[k9];
          });
          return null;
        }
        case "table.delete": { var TB5 = tableById[ev(p[0], i)]; if (TB5) TB5.del = 1; return null; }
        case "table.merge_cells": case "table.set_position": return null;
        case "label.set_y": { var idY = ev(p[0], i); if (idY != null && labelById[idY]) { var nyV = Number(ev(p[1], i)); if (isFinite(nyV)) labelById[idY].y = nyV; } return null; }
        case "label.set_text": { var idT = ev(p[0], i); if (idT != null && labelById[idT]) { var ntV = ev(p[1], i); labelById[idT].text = ntV == null ? "" : String(ntV); } return null; }
        case "label.set_xy": { var idXY = ev(p[0], i); if (idXY != null && labelById[idXY]) { var nxyV = Number(ev(p[2], i)); if (isFinite(nxyV)) labelById[idXY].y = nxyV; } return null; }
        case "label.set_x": return null;   // x stays at creation bar (time-based shifts aren't representable)
        case "label.get_x": { var idG = ev(p[0], i); return (idG != null && labelById[idG]) ? labelById[idG].i : null; }
        case "label.get_y": { var idG2 = ev(p[0], i); return (idG2 != null && labelById[idG2]) ? labelById[idG2].y : null; }
        case "ta.stoch": {   // 100 * (src - lowest(low,len)) / (highest(high,len) - lowest(low,len))
          var sl = len_(p[3] || p[1], i);
          var lo5 = windowed(p[2] || p[0], i, sl, function (v) { return Math.min.apply(null, v); });
          var hi5 = windowed(p[1] || p[0], i, sl, function (v) { return Math.max.apply(null, v); });
          var sv5 = ev(p[0], i);
          return (lo5 == null || hi5 == null || sv5 == null || hi5 === lo5) ? null : 100 * (sv5 - lo5) / (hi5 - lo5);
        }
        case "ta.cci": {
          var cl5 = len_(p[1], i);
          var sm5 = windowed(p[0], i, cl5, function (v) { var s = 0; for (var q = 0; q < v.length; q++) s += v[q]; return s / v.length; });
          var dv5 = windowed(p[0], i, cl5, function (v) { var m = 0, q; for (q = 0; q < v.length; q++) m += v[q]; m /= v.length; var s = 0; for (q = 0; q < v.length; q++) s += Math.abs(v[q] - m); return s / v.length; });
          var cv5 = ev(p[0], i);
          return (sm5 == null || dv5 == null || cv5 == null || dv5 === 0) ? null : (cv5 - sm5) / (0.015 * dv5);
        }
        case "ta.wpr": {
          var wl5 = len_(p[0], i);
          var wh = windowed({ k: "name", v: "high", ix: "wh" + node.ix }, i, wl5, function (v) { return Math.max.apply(null, v); });
          var wl = windowed({ k: "name", v: "low", ix: "wl" + node.ix }, i, wl5, function (v) { return Math.min.apply(null, v); });
          var wc = candles[i].c;
          return (wh == null || wl == null || wh === wl) ? null : (wh - wc) / (wh - wl) * -100;
        }
        case "ta.linreg": {
          var ll5 = len_(p[1], i), off5 = p[2] ? Math.round(Number(ev(p[2], i)) || 0) : 0;
          return windowed(p[0], i, ll5, function (v) {
            var m2 = v.length, sx = 0, sy = 0, sxy = 0, sxx = 0, q;
            for (q = 0; q < m2; q++) { sx += q; sy += v[q]; sxy += q * v[q]; sxx += q * q; }
            var slope = (m2 * sxy - sx * sy) / (m2 * sxx - sx * sx || 1);
            var icpt = (sy - slope * sx) / m2;
            return icpt + slope * (m2 - 1 - off5);
          });
        }
        case "ta.pivothigh": case "ta.pivotlow": {
          var isHi = nm === "ta.pivothigh";
          var srcP = p.length >= 3 ? p[0] : { k: "name", v: isHi ? "high" : "low", ix: "pv" + node.ix };
          var lft = len_(p.length >= 3 ? p[1] : p[0], i), rgt = len_(p.length >= 3 ? p[2] : p[1], i);
          var ctr = i - rgt;
          if (ctr - lft < 0) return null;
          var cv6 = ev(srcP, ctr);
          if (cv6 == null) return null;
          for (var q6 = ctr - lft; q6 <= ctr + rgt; q6++) {
            if (q6 === ctr) continue;
            var ov6 = ev(srcP, q6);
            if (ov6 == null) return null;
            if (isHi ? ov6 >= cv6 : ov6 <= cv6) return null;
          }
          return cv6;
        }
        case "ta.barssince": {
          var bk = stKey(node, "bs"); var bs = state[bk] || (state[bk] = { last: -1, at: -1 });
          if (bs.at !== i) { if (ev(p[0], i)) bs.last = i; bs.at = i; }
          return bs.last < 0 ? null : i - bs.last;
        }
        case "ta.valuewhen": {
          var vk = stKey(node, "vw"); var vw = state[vk] || (state[vk] = { hist: [], at: -1 });
          if (vw.at !== i) { if (ev(p[0], i)) vw.hist.push(ev(p[1], i)); vw.at = i; }
          var occ = p[2] ? Math.round(Number(ev(p[2], i)) || 0) : 0;
          var hidx = vw.hist.length - 1 - occ;
          return hidx >= 0 ? vw.hist[hidx] : null;
        }
        case "ta.rising": case "ta.falling": {
          var rl6 = len_(p[1], i);
          if (i < rl6) return 0;
          var up6 = nm === "ta.rising";
          for (var q7 = i - rl6 + 1; q7 <= i; q7++) {
            var aa = ev(p[0], q7), bb = ev(p[0], q7 - 1);
            if (aa == null || bb == null) return 0;
            if (up6 ? aa <= bb : aa >= bb) return 0;
          }
          return 1;
        }
        case "ta.vwma": return null;   // needs volume — the feed has none (na)
        case "ta.hma": {
          var hl3 = len_(p[1], i);
          var w1 = windowed(p[0], i, Math.max(1, Math.round(hl3 / 2)), wmaFn), w2 = windowed(p[0], i, hl3, wmaFn);
          if (w1 == null || w2 == null) return null;
          var raw = 2 * w1 - w2;
          var hk = stKey(node, "h"); var hs = state[hk] || (state[hk] = { buf: [] });
          hs.buf[i] = raw;
          var sq = Math.max(1, Math.round(Math.sqrt(hl3)));
          if (i < sq - 1) return null;
          var s = 0, w = 0;
          for (var q = 0; q < sq; q++) { var vq = hs.buf[i - sq + 1 + q]; if (vq == null) return null; s += vq * (q + 1); w += q + 1; }
          return s / w;
        }
      }
      return null;   // unknown fn → na (already warned at compile time when top-level)
    }
    function wmaFn(vals) { var s = 0, w = 0; for (var k = 0; k < vals.length; k++) { s += vals[k] * (k + 1); w += k + 1; } return s / w; }
    function mval(nd, i, f) { var v = ev(nd, i); return v == null ? null : f(v); }
    function redu(p, i, f) { var vs = []; for (var k = 0; k < p.length; k++) { var v = ev(p[k], i); if (v == null) return null; vs.push(v); } return f.apply(null, vs); }
    function len_(nd, i) { return Math.max(1, Math.round(Number(nd ? ev(nd, i) : 1) || 1)); }
    function hilo(p, i, f) {
      // single-arg form ta.highest(len) defaults to high/low — its synthetic
      // node needs a UNIQUE cache key (string ix) so it never collides with
      // the length node's own cache
      var src = p.length > 1 ? p[0] : { k: "name", v: f === Math.max ? "high" : "low", ix: "hl" + p[0].ix };
      var ln = len_(p.length > 1 ? p[1] : p[0], i);
      return windowed(src, i, ln, function (vals) { return f.apply(null, vals); });
    }
    function crossDir(p, i, dir) {
      if (i === 0) return 0;
      var a1 = ev(p[0], i), b1 = ev(p[1], i), a0 = ev(p[0], i - 1), b0 = ev(p[1], i - 1);
      if (a1 == null || b1 == null || a0 == null || b0 == null) return 0;
      return dir > 0 ? (a1 > b1 && a0 <= b0 ? 1 : 0) : (a1 < b1 && a0 >= b0 ? 1 : 0);
    }

    // MACD tuple support
    function macdAt(callNode, i) {
      var p = callNode.args.pos;
      var fast = emaCalc({ ix: callNode.ix, args: callNode.args }, p[0], p[1], i);
      var kSlow = stKey(callNode, "slow"); // separate EMA state
      var slowNode = { ix: callNode.ix + 0.5 };
      var slow = emaCalcRaw(kSlow, p[0], p[2], i);
      if (fast == null || slow == null) return [null, null, null];
      var line = fast - slow;
      var kSig = stKey(callNode, "sig");
      var sig = emaScalar(kSig, line, p[3] ? len_(p[3], i) : 9);
      return [line, sig, sig == null ? null : line - sig];
    }
    function emaCalcRaw(key, srcNode, lenNode, i) {
      var st = state[key] || (state[key] = { last: null, at: -1 });
      if (st.at === i) return st.lastOut;
      var len = len_(lenNode, i), v = ev(srcNode, i), out;
      var k2 = 2 / (len + 1);
      if (v == null) out = st.last; else if (st.last == null) out = v; else out = v * k2 + st.last * (1 - k2);
      st.last = out; st.at = i; st.lastOut = out;
      return out;
    }
    function emaScalar(key, v, len) {
      var st = state[key] || (state[key] = { last: null });
      var k2 = 2 / (len + 1);
      var out = v == null ? st.last : st.last == null ? v : v * k2 + st.last * (1 - k2);
      st.last = out;
      return out;
    }
    // ta.bb(src, len, mult) → [basis, upper, lower]
    function bbAt(callNode, i) {
      var p = callNode.args.pos;
      var ln = len_(p[1], i);
      var basis = windowed(p[0], i, ln, function (v) { var s = 0; for (var q = 0; q < v.length; q++) s += v[q]; return s / v.length; });
      var sd = windowed(p[0], i, ln, function (v) { var m = 0, q; for (q = 0; q < v.length; q++) m += v[q]; m /= v.length; var s = 0; for (q = 0; q < v.length; q++) s += (v[q] - m) * (v[q] - m); return Math.sqrt(s / v.length); });
      var mult = p[2] ? Number(ev(p[2], i)) || 2 : 2;
      if (basis == null || sd == null) return [null, null, null];
      return [basis, basis + mult * sd, basis - mult * sd];
    }
    // ta.supertrend(factor, atrPeriod) → [value, direction]  (Pine: dir<0 = uptrend)
    function supertrendAt(callNode, i) {
      var p = callNode.args.pos;
      var key = stKey(callNode, "st");
      var st = state[key] || (state[key] = { up: null, dn: null, dir: 1, at: -1, out: [null, null] });
      if (st.at === i) return st.out;
      var factor = Number(ev(p[0], i)) || 3;
      var atrNode = { k: "call", name: "ta.tr", args: { pos: [], named: {} }, ix: "sttr" + callNode.ix };
      var atrV = rmaCalc({ ix: "strma" + callNode.ix }, atrNode, p[1], i);
      var K = CD[i], mid = (K.h + K.l) / 2;
      if (atrV == null) { st.at = i; return st.out = [null, null]; }
      var up = mid + factor * atrV, dn = mid - factor * atrV;
      var pc = i > 0 ? CD[i - 1].c : K.c;
      if (st.dn != null) dn = (dn > st.dn || pc < st.dn) ? dn : st.dn;
      if (st.up != null) up = (up < st.up || pc > st.up) ? up : st.up;
      var dir = st.dir;
      if (st.up == null) dir = 1;
      else if (st.lastVal === st.up) dir = K.c > up ? -1 : 1;
      else dir = K.c < dn ? 1 : -1;
      var val = dir === -1 ? dn : up;
      st.up = up; st.dn = dn; st.dir = dir; st.lastVal = val; st.at = i;
      return st.out = [val, dir];
    }

    // input values
    var inputByIdx = {};
    for (var q = 0; q < prog.inputs.length; q++) {
      inputByIdx[q] = (inputVals && inputVals[q] != null) ? inputVals[q] : prog.inputs[q].def;
    }

    var plotOut = prog.plots.map(function () { return new Array(N); });
    var shapeOut = prog.shapes.map(function () { return []; });
    var candleOut = (prog.candles || []).map(function () { return new Array(N); });   // plotcandle()
    // WHY a shape stayed silent — the difference between "your script said no" and
    // "we couldn't work out what your script said". A condition that evaluated to
    // FALSE on every bar is the strategy's own logic (a threshold not reached on
    // this data). A condition that was NA on every bar never really ran — it
    // depends on something the engine didn't compute, which IS a conversion loss.
    // Reporting "fired 0×" without that distinction sends people hunting for a bug
    // in a script that is behaving exactly as written.
    var shapeNa = prog.shapes.map(function () { return 0; });
    var shapeFalse = prog.shapes.map(function () { return 0; });
    var bgOut = new Array(N);      // bgcolor per-bar tint
    var barcOut = new Array(N);    // barcolor per-bar strip
    var labelsOut = [];            // label.new records (capped)
    var alertsOut = [];            // alert()/alertcondition() fires (capped) — drives on-chart alarm popups

    // user-defined function call: inlined per call site with isolated caches,
    // isolated `var` state, and call-by-name params (history on params works)
    function callUser(fn, callNode, i) {
      var ctxId = curCtx + ">" + callNode.ix;
      var storeKey = "F|" + ctxId;
      var store = state[storeKey] || (state[storeKey] = { vars: Object.create(null) });
      var frame = { params: Object.create(null), vars: store.vars, ctx: ctxId };
      var depth = frames.length;
      for (var p3 = 0; p3 < fn.params.length; p3++) {
        frame.params[fn.params[p3]] = { node: callNode.args.pos[p3] || { k: "num", v: null, ix: -1000 - p3 }, ctx: curCtx, depth: depth };
      }
      var oldCtx = curCtx;
      frames.push(frame); curCtx = ctxId;
      var ret = null;
      try { ret = execBody(fn.body, i); }
      finally { frames.pop(); curCtx = oldCtx; }
      return ret;
    }

    function execStmt(st, i) {
      switch (st.kind) {
        case "input": (vars[st.name] || (vars[st.name] = new Array(N)))[i] = inputByIdx[st.idx]; return null;
        case "assign": {
          if (st.isVar) {
            // Pine `var` = initialize ONCE, then persist. Vital for arrays and
            // objects: re-running the initializer would reset them every bar.
            var store9 = (frames.length && frames[frames.length - 1].vars[st.name]) || vars[st.name];
            if (store9) { var prev9 = readSeries(store9, i); if (prev9 !== null) { store9[i] = prev9; return prev9; } }
          }
          var v = ev(st.expr, i); setVar(st.name, i, v); return v;
        }
        case "tuple": {
          var tv = null;
          if (st.expr.k === "call" && prog.funcs && prog.funcs[st.expr.name]) tv = callUser(prog.funcs[st.expr.name], st.expr, i);
          else if (st.expr.k === "call" && /macd$/.test(st.expr.name)) tv = macdAt(st.expr, i);
          else if (st.expr.k === "call" && /(^|\.)bb$/.test(st.expr.name)) tv = bbAt(st.expr, i);
          else if (st.expr.k === "call" && /supertrend$/.test(st.expr.name)) tv = supertrendAt(st.expr, i);
          else tv = ev(st.expr, i);   // generic: request.security tuples, switch results, …
          if (!Array.isArray(tv)) tv = [];
          for (var tn = 0; tn < st.names.length; tn++) setVar(st.names[tn], i, tv[tn] != null ? tv[tn] : null);
          return tv;
        }
        case "ifblk": {
          for (var b2 = 0; b2 < st.branches.length; b2++) {
            var br = st.branches[b2];
            if (!br.cond || ev(br.cond, i)) return execBody(br.body, i);
          }
          return null;
        }
        case "bg": { var bc = ev(prog.bgs[st.idx].expr, i); if (typeof bc === "string") bgOut[i] = bc; return null; }
        case "barc": { var kc = ev(prog.barcs[st.idx].expr, i); if (typeof kc === "string") barcOut[i] = kc; return null; }
        case "whileblk": {
          var wg = 0;
          loopDepth++;
          try { while (ev(st.cond, i)) { if (wg++ > 5000) break; execBody(st.body, i); } }
          finally { loopDepth--; }
          return null;
        }
        case "multi": {
          /* Siblings on one comma line are independent statements, so a throw in
           * one must not swallow the rest — `label.delete(old)` failing cannot be
           * allowed to also skip the `label.new(…)` beside it. Isolate each, and
           * account for the failure the same way a block body does. */
          var lastM = null;
          for (var m8 = 0; m8 < st.stmts.length; m8++) {
            try { lastM = execStmt(st.stmts[m8], i); }
            catch (e8) { recErr(st.stmts[m8], e8); }
          }
          return lastM;
        }
        case "forin": {
          var arrV = ev(st.expr, i);
          if (!Array.isArray(arrV)) return null;
          var snap = arrV.slice();   // iterate a snapshot: bodies often remove elements
          var scopeF = {};
          loopVars.push(scopeF); loopDepth++;
          try {
            for (var e7 = 0; e7 < snap.length && e7 < 5000; e7++) { scopeF[st.v] = snap[e7]; execBody(st.body, i); }
          } finally { loopVars.pop(); loopDepth--; }
          return null;
        }
        case "forblk": {
          var a4 = Math.round(Number(ev(st.from, i)) || 0), b4 = Math.round(Number(ev(st.to, i)) || 0);
          var step = a4 <= b4 ? 1 : -1, guard = 0;
          var scope4 = {};
          loopVars.push(scope4); loopDepth++;
          try {
            for (var k4 = a4; step > 0 ? k4 <= b4 : k4 >= b4; k4 += step) {
              if (guard++ > 5000) break;   // runaway-loop guard
              scope4[st.v] = k4;
              execBody(st.body, i);
            }
          } finally { loopVars.pop(); loopDepth--; }
          return null;
        }
        case "plot": {
          var pi = prog.plots.indexOf(st.spec);
          // CRITICAL: preserve na — Number(null) is 0, which would turn hidden
          // plots (`toggle ? v : na`) into phantom flat lines at price 0
          var pvRaw = ev(st.spec.expr, i);
          plotOut[pi][i] = pvRaw == null ? null : num(Number(pvRaw));
          if (st.spec.colorExpr) {
            var dc = ev(st.spec.colorExpr, i);
            if (typeof dc === "string") (st.spec.dynColor || (st.spec.dynColor = new Array(N)))[i] = dc;
          }
          return null;
        }
        case "candle": {
          var cIx = prog.candles.indexOf(st.spec);
          var kO = ev(st.spec.o, i), kH = ev(st.spec.h, i), kL = ev(st.spec.l, i), kC = ev(st.spec.c, i);
          if (kO == null || kH == null || kL == null || kC == null) { candleOut[cIx][i] = null; return null; }
          var kCol = st.spec.color, kWick = st.spec.wick, kBord = st.spec.border;
          if (st.spec.colorExpr)  { var dC1 = ev(st.spec.colorExpr, i);  kCol  = (typeof dC1 === "string") ? dC1 : null; }
          if (st.spec.wickExpr)   { var dC2 = ev(st.spec.wickExpr, i);   kWick = (typeof dC2 === "string") ? dC2 : null; }
          if (st.spec.borderExpr) { var dC3 = ev(st.spec.borderExpr, i); kBord = (typeof dC3 === "string") ? dC3 : null; }
          if (!st.spec.hasBody) {
            // No `color` argument at all — Pine's default green/red body.
            kCol = kCol || (Number(kC) >= Number(kO) ? "#22c55e" : "#ef4444");
          }
          // A colour argument that is na on this bar → draw nothing (this is the
          // "Color Candles = off" path; it must NOT fall back to a default).
          // Fully transparent is Pine's OTHER way of saying "invisible"
          // (color.new(css, 100)). Treat alpha-0 as absent, or we would fill an
          // invisible rectangle and — worse — report it in the diagnostics as a
          // repainted bar, which is a lie that costs someone an afternoon.
          if (isTransparent(kCol)) kCol = null;
          if (isTransparent(kWick)) kWick = null;
          if (isTransparent(kBord)) kBord = null;
          if (!kCol && !kWick && !kBord) { candleOut[cIx][i] = null; return null; }
          candleOut[cIx][i] = {
            o: num(Number(kO)), h: num(Number(kH)), l: num(Number(kL)), c: num(Number(kC)),
            col: kCol, wick: kWick || kCol, bord: kBord || kCol,
          };
          return null;
        }
        case "shape": {
          var sIx = prog.shapes.indexOf(st.spec);
          var sCv = ev(st.spec.cond, i);
          if (sCv) shapeOut[sIx].push(i);
          else if (sCv == null || (typeof sCv === "number" && !isFinite(sCv))) shapeNa[sIx]++;   // na — never actually evaluated
          else shapeFalse[sIx]++;                                                                 // false — the script said no
          return null;
        }
        case "switchblk": {
          // subject form: arm matches when test === subject; conditional form:
          // arm matches when its test is truthy. Bare `=>` arm is the default.
          var swv = st.subj ? ev(st.subj, i) : null;
          var swr = null, swHit = false;
          for (var sa = 0; sa < st.arms.length; sa++) {
            var swt = ev(st.arms[sa].test, i);
            if (st.subj ? (swv === swt) : !!swt) { swr = st.arms[sa].body ? ev(st.arms[sa].body, i) : null; swHit = true; break; }
          }
          if (!swHit && st.def) swr = ev(st.def, i);
          if (st.name) setVar(st.name, i, swr);
          return swr;
        }
        case "expr": {
          if (st.expr.k === "tuplelit") { var arr2 = []; for (var q2 = 0; q2 < st.expr.items.length; q2++) arr2.push(ev(st.expr.items[q2], i)); return arr2; }
          return ev(st.expr, i);
        }
      }
      return null;
    }
    function execBody(stmts, i) {
      var last = null;
      for (var s3 = 0; s3 < stmts.length; s3++) { try { last = execStmt(stmts[s3], i); } catch (e) { recErr(stmts[s3], e); } }
      return last;
    }
    // per-statement exception accounting: silent degradation is diagnosable
    var errStats = {};
    function recErr(st2, e2) {
      var k2 = "line " + (st2 && st2.line || "?");
      var rec = errStats[k2] || (errStats[k2] = { n: 0, msg: "" });
      rec.n++;
      if (!rec.msg) rec.msg = String((e2 && e2.message) || e2).slice(0, 160);
    }

    /* ── THE RUN BUDGET ──────────────────────────────────────────────────────
     * This engine is a tree-walking interpreter: every Pine operation costs
     * orders of magnitude more than native code. A script with nested per-bar
     * loops (a pivot scan of `for x = 0 to 284` inside `for xx = 0 to 284` is
     * ~80,000 interpreted ops PER BAR) run across 1000 candles is tens of
     * billions of operations. It does not hang — it finishes. Eventually.
     *
     * Measured in the wild: 43 SECONDS of blocked main thread, with four such
     * indicators active. JavaScript is single-threaded, so during that time the
     * app is not slow — it is DEAD. No taps, no timers, no repaints. And because
     * the run cache is keyed on the last candle's timestamp, it happened again on
     * every new bar: a 43-second freeze every single minute.
     *
     * No amount of render-side cleverness fixes that; the only real answer is to
     * refuse to spend that long. The check sits in the BAR loop, deliberately
     * outside the per-statement try/catch below — that catch exists to let one
     * broken statement degrade gracefully, and it would happily swallow this too.
     */
    for (bar = 0; bar < n; bar++) {
      if (_budget && (bar & 7) === 0) {
        var _el = nowMs() - _runT0;
        if (_el > _budget) {
          var be = new Error("QC_BUDGET");
          be.qcBudget = true; be.ms = _el; be.bars = bar; be.total = n;
          throw be;
        }
      }
      for (var s2 = 0; s2 < prog.stmts.length; s2++) {
        try { execStmt(prog.stmts[s2], bar); } catch (e) { recErr(prog.stmts[s2], e); }
      }
    }
    // dynamic fill colors: evaluate each fill's color expression per bar
    var fillCols = prog.fills.map(function (FL) {
      if (!FL.colorExpr) return null;
      var colsF = new Array(N);
      for (var fb = 0; fb < n; fb++) {
        try { var cf = ev(FL.colorExpr, fb); if (typeof cf === "string") colsF[fb] = cf; } catch (e) {}
      }
      return colsF;
    });
    // shape colors driven by variables (usually input.color) — resolve post-run
    var shapeCols = prog.shapes.map(function (sp) {
      if (!sp.colorExpr) return null;
      try { var cS = ev(sp.colorExpr, n - 1); return typeof cS === "string" ? cS : null; } catch (e) { return null; }
    });
    return { plots: plotOut, shapes: shapeOut, shapeNa: shapeNa, shapeFalse: shapeFalse, candles: candleOut, bg: bgOut, barc: barcOut, labels: labelsOut, lines: linesOut, boxes: boxesOut, linefills: linefillsOut, tables: tablesOut, fillCols: fillCols, shapeCols: shapeCols, alerts: alertsOut, errStats: errStats };
  }

  /* ════════════════════════ CHART BRIDGE ════════════════════════ */
  var _runCache = {};   // key -> {res, prog}
  var LEGHIT = [];      // on-chart legend hit-rects: {x,y,w,h,id}

  /* ── per-indicator input + style overrides (persisted per device) ──── */
  function cfgKey(id) { return "dq_qc_cfg_" + id; }
  function styleKey(id) { return "dq_qc_style_" + id; }
  function hydrateCfg(ind) {
    try { var v = JSON.parse(localStorage.getItem(cfgKey(ind.id)) || "null"); if (v && v.length) ind._inputVals = v; } catch (e) {}
    try { var s = JSON.parse(localStorage.getItem(styleKey(ind.id)) || "null"); if (s) ind._styleOv = s; } catch (e) {}
  }
  // any css color → "#rrggbb" (for <input type=color> and override comparison)
  function toHex6(col) {
    if (!col) return "#1c84ff";
    col = String(col);
    if (col[0] === "#") return col.length >= 7 ? col.slice(0, 7) : ("#" + col.slice(1).split("").map(function (ch) { return ch + ch; }).join("")).slice(0, 7);
    var m = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/.exec(col);
    if (m) { var h = function (x) { return ("0" + (+x).toString(16)).slice(-2); }; return "#" + h(m[1]) + h(m[2]) + h(m[3]); }
    return "#1c84ff";
  }
  function clearRunFor(id) {
    Object.keys(_runCache).forEach(function (k) { if (k.indexOf(id + ":") === 0) delete _runCache[k]; });
    // A memoised failure and a stale compile are both invalidated by an edit —
    // otherwise fixing a script in the studio would leave it permanently marked
    // broken on the chart.
    try {
      if (LIB.list) {
        LIB.list.forEach(function (x) {
          if (x && x.id === id) { x._failed = null; x._errAt = null; x._prog = null; }
        });
      }
    } catch (e) {}
  }
  function repaintChart() { try { window.dispatchEvent(new CustomEvent("dq-qc-repaint")); } catch (e) {} }

  // tap on a chart legend row → open that indicator's settings. Returns true if consumed.
  // per-indicator on-chart alarms: 🔔 in the legend toggles them (default ON)
  function alarmsOn(id) { try { return localStorage.getItem("dq_qc_alarm_" + id) !== "0"; } catch (e) { return true; } }
  function setAlarmsOn(id, on) { try { localStorage.setItem("dq_qc_alarm_" + id, on ? "1" : "0"); } catch (e) {} }
  function legendHit(x, y) {
    for (var i = 0; i < LEGHIT.length; i++) {
      var r = LEGHIT[i];
      if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) {
        if (r.err) { toast2("Indicator error: " + r.err.slice(0, 220), "error"); return true; }
        if (r.bell) {   // 🔔 toggle: turn this indicator's chart alarms on/off
          var on2 = !alarmsOn(r.id);
          setAlarmsOn(r.id, on2);
          toast2(on2 ? "🔔 Alarms on" : "🔕 Alarms off", on2 ? "This indicator's alerts will pop on the chart" : "No popups from this indicator");
          try { window.dispatchEvent(new Event("dq-qc-repaint")); } catch (e2) {}
          return true;
        }
        var ind = (LIB.list || []).filter(function (q) { return q.id === r.id; })[0];
        if (ind) { openSettings(ind); return true; }
      }
    }
    return false;
  }

  // Pine has no boolean dropdown, so scripts fake switches with string options.
  // Recognise the common spellings and let the select paint itself on/off.
  function onOffClass(v) {
    var s = String(v == null ? "" : v).trim().toLowerCase();
    if (/^(on|true|yes|enable[d]?|show)$/.test(s)) return "is-on";
    if (/^(off|false|no|disable[d]?|hide|none)$/.test(s)) return "is-off";
    return "";
  }

  /* ── TradingView-style COLOR PALETTE popup (swatch grid + opacity %) ── */
  function qcColorPop(anchorEl, cur, onPick) {
    var c = TH();
    var old = document.getElementById("qc-colorpop"); if (old) { old.remove(); if (old.__a === anchorEl) return; }
    var shade = function (hex, f) {
      var r = parseInt(hex.slice(1, 3), 16), g = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
      var t = f > 0 ? 255 : 0, aF = Math.abs(f);
      var mv = function (v) { return Math.round(v + (t - v) * aF); };
      var hx = function (v) { return ("0" + v.toString(16)).slice(-2); };
      return "#" + hx(mv(r)) + hx(mv(g)) + hx(mv(b));
    };
    var HUES = ["#f23645", "#ff9800", "#ffeb3b", "#4caf50", "#089981", "#00bcd4", "#2962ff", "#673ab7", "#9c27b0", "#e91e63"];
    var rows = [[], HUES];
    for (var gI = 0; gI < 10; gI++) { var gv = Math.round(255 * (1 - gI / 9)); var gh = ("0" + gv.toString(16)).slice(-2); rows[0].push("#" + gh + gh + gh); }
    [0.6, 0.4, 0.2, -0.2, -0.4].forEach(function (f2) { rows.push(HUES.map(function (h2) { return shade(h2, f2); })); });
    var curA = 1; var mA = /rgba\([^,]+,[^,]+,[^,]+,\s*([\d.]+)/.exec(String(cur || "")); if (mA) curA = Math.max(0, Math.min(1, +mA[1]));
    var baseHex = toHex6(cur || "#2962ff");
    var pop = document.createElement("div");
    pop.id = "qc-colorpop"; pop.__a = anchorEl;
    var br = anchorEl.getBoundingClientRect();
    pop.style.cssText = "position:fixed;z-index:6600;left:" + Math.max(8, Math.min(window.innerWidth - 246, br.left - 100)) + "px;top:" + Math.min(window.innerHeight - 300, br.bottom + 6) + "px;width:238px;background:#161c2c;border:1px solid " + c.bd + ";border-radius:12px;padding:10px;box-shadow:0 12px 32px rgba(0,0,0,.6)";
    var gridH = rows.map(function (rw) {
      return '<div style="display:flex;gap:4px;margin-bottom:4px">' + rw.map(function (col) {
        return '<button type="button" class="qc-cp-sw" data-c="' + col + '" style="width:18px;height:18px;border-radius:4px;border:1px solid rgba(255,255,255,.14);background:' + col + ';cursor:pointer;padding:0' + (col.toLowerCase() === baseHex.toLowerCase() ? ';outline:2px solid #fff' : '') + '"></button>';
      }).join("") + '</div>';
    }).join("");
    pop.innerHTML = gridH +
      '<div style="display:flex;align-items:center;gap:8px;margin-top:4px;border-top:1px solid ' + (c.bdSoft || "rgba(120,140,190,.12)") + ';padding-top:8px">' +
        '<button type="button" id="qc-cp-plus" title="Custom color" style="width:24px;height:24px;border-radius:6px;border:1px dashed ' + c.bd + ';background:transparent;color:' + c.t3 + ';cursor:pointer;font-size:14px;line-height:1">+</button>' +
        '<input type="color" id="qc-cp-cust" value="' + baseHex + '" style="position:absolute;opacity:0;width:1px;height:1px">' +
        '<span style="flex:1"></span></div>' +
      '<div style="margin-top:8px"><div style="font-size:10px;color:' + c.t3 + ';margin-bottom:3px">Opacity</div>' +
        '<div style="display:flex;align-items:center;gap:8px">' +
          '<input type="range" id="qc-cp-a" min="0" max="100" value="' + Math.round(curA * 100) + '" style="flex:1;accent-color:' + baseHex + '">' +
          '<span id="qc-cp-av" style="font-size:10.5px;color:' + c.t2 + ';width:34px;text-align:right">' + Math.round(curA * 100) + '%</span>' +
        '</div></div>';
    document.body.appendChild(pop);
    var curHex = baseHex;
    var emit = function () {
      var aV = (+pop.querySelector("#qc-cp-a").value) / 100;
      pop.querySelector("#qc-cp-av").textContent = Math.round(aV * 100) + "%";
      var out = aV >= 0.995 ? curHex
        : "rgba(" + parseInt(curHex.slice(1, 3), 16) + "," + parseInt(curHex.slice(3, 5), 16) + "," + parseInt(curHex.slice(5, 7), 16) + "," + aV.toFixed(2) + ")";
      onPick(out);
    };
    pop.querySelectorAll(".qc-cp-sw").forEach(function (b) { b.onclick = function () { curHex = b.getAttribute("data-c"); emit(); }; });
    pop.querySelector("#qc-cp-a").oninput = emit;
    var custI = pop.querySelector("#qc-cp-cust");
    pop.querySelector("#qc-cp-plus").onclick = function () { custI.click(); };
    custI.oninput = function () { curHex = custI.value; emit(); };
    setTimeout(function () {
      var closer = function (e) { if (!pop.contains(e.target) && e.target !== anchorEl) { if (pop.parentNode) pop.remove(); document.removeEventListener("click", closer, true); } };
      document.addEventListener("click", closer, true);
    }, 0);
  }

  /* ── TradingView-style settings dialog: INPUTS tab + STYLE tab ──────── */
  function openSettings(ind) {
    var c = TH();
    ensureCSS();   // the sheet opens straight from the chart, without the studio
    var prog;
    try { prog = ind._prog || (ind._prog = compile(ind.source)); } catch (e) { toast2("Indicator failed to compile", "error"); return; }
    var vals = ind._inputVals || (ind.config && ind.config.inputs) || [];
    var sOv = ind._styleOv || {};
    var old = document.getElementById("dq-qc-cfg"); if (old) old.remove();
    var soft = c.bdSoft || "rgba(120,140,190,.12)";
    var inpBg = "rgba(120,140,190,.08)";
    var rowCss = 'display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 0;border-bottom:1px solid ' + soft;
    var selCss = 'padding:7px 10px;border-radius:8px;border:1px solid ' + c.bd + ';background:' + inpBg + ';color:' + c.t1 + ';font-size:12px;font-family:inherit';

    /* — INPUTS pane: grouped sections + inline rows + tooltips, like TV — */
    var rows = "";
    var editable = 0;
    var curGroup = null, curInline = null;
    function lab(inp) {
      return '<div style="font-size:12px;color:' + c.t2 + ';min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"' + (inp.tip ? ' title="' + ESC(inp.tip) + '"' : '') + '>' + ESC(inp.title) + (inp.tip ? ' <span style="color:' + c.t4 + ';font-size:10px">ⓘ</span>' : '') + '</div>';
    }
    for (var i = 0; i < prog.inputs.length; i++) {
      var inp = prog.inputs[i];
      var v = vals[i] != null ? vals[i] : inp.def;
      var ctl;
      // group section headers FIRST (TV's uppercase group captions)
      if ((inp.group || "") !== curGroup) {
        curGroup = inp.group || "";
        curInline = null;
        if (curGroup) rows += '<div style="font-size:10px;letter-spacing:.8px;font-weight:800;color:' + c.t4 + ';margin:14px 0 2px;text-transform:uppercase">' + ESC(curGroup) + '</div>';
      }
      if (inp.type === "bool") {
        // TV-style: checkbox BEFORE the label, whole row clickable
        editable++;
        var on = !!Number(v);
        rows += '<div style="' + rowCss + '"><label style="display:flex;align-items:center;gap:9px;cursor:pointer;min-width:0"' + (inp.tip ? ' title="' + ESC(inp.tip) + '"' : '') + '>' +
          '<input type="checkbox" class="qc-cfg-b2" data-i="' + i + '"' + (on ? ' checked' : '') + ' style="accent-color:' + c.blue + ';width:16px;height:16px">' +
          '<span style="font-size:12px;color:' + c.t1 + ';font-weight:600">' + ESC(inp.title) + '</span></label>' +
          (inp.tip ? '<span style="color:' + c.t4 + ';font-size:11px">ⓘ</span>' : '') + '</div>';
        curInline = null;
        continue;
      } else if (inp.type === "string" && inp.options && inp.options.length) {
        editable++;
        // Scripts express their switches as string options ("ON"/"OFF"), so this
        // select IS the toggle the user sees. Colour it by state — a live green
        // for ON, receding grey for OFF — so a settings sheet with 20 rows can be
        // read at a glance instead of one label at a time.
        ctl = '<select class="qc-cfg-s qc-sel ' + onOffClass(v) + '" data-i="' + i + '" style="max-width:150px">' +
          inp.options.map(function (op) { return '<option value="' + ESC(String(op)) + '"' + (String(op) === String(v) ? " selected" : "") + '>' + ESC(String(op)) + '</option>'; }).join("") + '</select>';
      } else if (inp.type === "int" || inp.type === "float") {
        editable++;
        ctl = '<input class="qc-cfg-n" data-i="' + i + '" data-ty="' + inp.type + '" type="number" inputmode="decimal" value="' + ESC(String(v)) + '"' +
          (inp.min != null ? ' min="' + inp.min + '"' : '') + (inp.max != null ? ' max="' + inp.max + '"' : '') +
          ' step="' + (inp.step != null ? inp.step : (inp.type === "int" ? 1 : "any")) + '"' +
          ' style="' + selCss + ';width:92px;text-align:right">';
      } else if (inp.type === "color") {
        // swatch → TradingView palette popup (with opacity)
        editable++;
        ctl = '<button type="button" class="qc-cfg-colb" data-i="' + i + '" data-c="' + ESC(String(v || "#2962ff")) + '" style="width:34px;height:28px;border-radius:7px;border:1px solid ' + c.bd + ';background:' + ESC(String(v || "#2962ff")) + ';cursor:pointer"></button>';
      } else if (inp.type === "string") {
        editable++;
        ctl = '<input class="qc-cfg-t" data-i="' + i + '" type="text" value="' + ESC(String(v)) + '" style="' + selCss + ';width:120px">';
      } else {
        ctl = '<span style="font-size:11px;color:' + c.t4 + '">—</span>';   // timeframe/session/etc
      }
      // inline inputs share one row, like TV's inline="..." arguments
      var inlKey = inp.inline ? (curGroup + "|" + inp.inline) : null;
      if (inlKey && inlKey === curInline) {
        rows = rows.replace(/<\/div>\s*$/, "") + '<span style="display:inline-flex;align-items:center;gap:6px;margin-left:8px">' + ctl + '</span></div>';
        continue;
      }
      curInline = inlKey;
      rows += '<div style="' + rowCss + '">' + lab(inp) + '<span style="display:inline-flex;align-items:center;gap:6px;flex-wrap:wrap;justify-content:flex-end">' + ctl + '</span></div>';
    }
    if (!prog.inputs.length) rows = '<div style="font-size:12px;color:' + c.t3 + ';padding:14px 0">This indicator has no editable inputs.</div>';

    /* — STYLE pane: per-plot color/width/style/visibility + output toggles — */
    var st = "";
    var eye = function (cls, idx, hidden) {
      return '<label style="display:inline-flex;align-items:center;gap:5px;cursor:pointer"><input type="checkbox" class="' + cls + '" data-i="' + idx + '"' + (hidden ? '' : ' checked') + ' style="accent-color:' + c.blue + ';width:15px;height:15px">👁</label>';
    };
    for (var pi2 = 0; pi2 < prog.plots.length; pi2++) {
      var ps = prog.plots[pi2], po2 = (sOv.p && sOv.p[pi2]) || {};
      st += '<div style="' + rowCss + '">' +
        '<div style="font-size:12px;color:' + c.t2 + ';min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + ESC(ps.title || ("Plot " + (pi2 + 1))) + '</div>' +
        '<span style="display:inline-flex;align-items:center;gap:7px">' +
          '<button type="button" class="qc-st-pcb" data-i="' + pi2 + '" data-c="' + ESC(String(po2.c || ps.color)) + '" style="width:30px;height:26px;border-radius:6px;border:1px solid ' + c.bd + ';background:' + ESC(String(po2.c || ps.color)) + ';cursor:pointer"></button>' +
          '<select class="qc-st-pw" data-i="' + pi2 + '" style="' + selCss + ';padding:6px 6px">' + [1, 2, 3, 4].map(function (w) { var cur = Math.round(po2.w != null ? po2.w : (ps.width || 1.5)); return '<option value="' + w + '"' + (w === Math.max(1, Math.min(4, cur)) ? " selected" : "") + '>' + w + 'px</option>'; }).join("") + '</select>' +
          '<select class="qc-st-ps" data-i="' + pi2 + '" style="' + selCss + ';padding:6px 6px">' + [["line", "Line"], ["area", "Area"], ["hist", "Columns"], ["dots", "Circles"]].map(function (o2) { return '<option value="' + o2[0] + '"' + ((po2.s || ps.style) === o2[0] ? " selected" : "") + '>' + o2[1] + '</option>'; }).join("") + '</select>' +
          eye("qc-st-pv", pi2, po2.h) +
        '</span></div>';
    }
    for (var si2 = 0; si2 < prog.shapes.length; si2++) {
      var ss = prog.shapes[si2], so2 = (sOv.s && sOv.s[si2]) || {};
      var locCur = so2.loc || (ss.below ? "below" : "above");
      // TV layout: [✓] Title   [glyph] [Below bar ▾]  +  indented "Color 0" swatch
      st += '<div style="' + rowCss + '">' +
        '<label style="display:flex;align-items:center;gap:8px;min-width:0;cursor:pointer">' +
          '<input type="checkbox" class="qc-st-sv2" data-i="' + si2 + '"' + (so2.h ? '' : ' checked') + ' style="accent-color:' + c.blue + ';width:16px;height:16px">' +
          '<span style="font-size:12px;color:' + c.t1 + ';font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + ESC(ss.title || ("Shape " + (si2 + 1))) + '</span></label>' +
        '<span style="display:inline-flex;align-items:center;gap:7px">' +
          '<span style="width:28px;height:28px;border:1px solid ' + c.bd + ';border-radius:7px;display:inline-flex;align-items:center;justify-content:center;font-size:12px;color:' + c.t2 + '">' + ss.glyph + '</span>' +
          '<select class="qc-st-sl" data-i="' + si2 + '" style="' + selCss + ';padding:6px 6px">' +
            '<option value="below"' + (locCur === "below" ? " selected" : "") + '>Below bar</option>' +
            '<option value="above"' + (locCur === "above" ? " selected" : "") + '>Above bar</option>' +
          '</select>' +
        '</span></div>' +
        '<div style="display:flex;align-items:center;gap:10px;padding:5px 0 10px 25px;border-bottom:1px solid ' + soft + '">' +
          '<span style="font-size:11.5px;color:' + c.t3 + '">Color 0</span>' +
          '<button type="button" class="qc-st-scb" data-i="' + si2 + '" data-c="' + ESC(String(so2.c || ss.color)) + '" style="width:30px;height:24px;border-radius:6px;border:1px solid ' + c.bd + ';background:' + ESC(String(so2.c || ss.color)) + ';cursor:pointer"></button>' +
        '</div>';
    }
    for (var fi2 = 0; fi2 < (prog.fills || []).length; fi2++) {
      st += '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Fill ' + (fi2 + 1) + '</div>' + eye("qc-st-fv", fi2, sOv.f && sOv.f[fi2] && sOv.f[fi2].h) + '</div>';
    }
    if ((prog.bgs || []).length) st += '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Background highlights</div>' +
      '<span style="display:inline-flex;align-items:center;gap:8px">' +
        '<input type="range" class="qc-st-bga" min="0" max="100" value="' + Math.round((sOv.bg && sOv.bg.a != null ? sOv.bg.a : 1) * 100) + '" style="width:90px;accent-color:' + c.blue + '" title="Background opacity">' +
        eye("qc-st-bg", 0, sOv.bg && sOv.bg.h) + '</span></div>';
    if ((prog.barcs || []).length) st += '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Bar colors</div>' + eye("qc-st-bc", 0, sOv.bc && sOv.bc.h) + '</div>';
    if ((prog.candles || []).length) st += '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Repainted candles (plotcandle)</div>' + eye("qc-st-cd", 0, sOv.cd && sOv.cd.h) + '</div>';
    if (prog.usesLines) st += '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Lines (structure / levels)</div>' + eye("qc-st-ln", 0, sOv.ln && sOv.ln.h) + '</div>';
    if (prog.usesTables) st += '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Dashboard (table)</div>' + eye("qc-st-tb", 0, sOv.tb && sOv.tb.h) + '</div>';
    if (prog.usesBoxes) st += '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Boxes (zones / order blocks)</div>' + eye("qc-st-bx", 0, sOv.bx && sOv.bx.h) + '</div>';
    st += '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Labels</div>' + eye("qc-st-lb", 0, sOv.lb && sOv.lb.h) + '</div>';
    // OUTPUT VALUES section (TV): precision of on-chart values
    st += '<div style="font-size:10px;letter-spacing:.8px;font-weight:800;color:' + c.t4 + ';margin:14px 0 2px;text-transform:uppercase">Output values</div>' +
      '<div style="' + rowCss + '"><div style="font-size:12px;color:' + c.t2 + '">Precision</div>' +
      '<input type="number" id="qc-st-prec" min="0" max="8" value="' + (sOv.prec != null ? sOv.prec : 2) + '" style="' + selCss + ';width:70px;text-align:right"></div>';
    if (!st) st = '<div style="font-size:12px;color:' + c.t3 + ';padding:14px 0">This indicator draws no styled outputs.</div>';
    // VISIBILITY pane: per-timeframe display, like TV's third tab
    var visOv = sOv.vis || {};
    var visPane = '<div style="font-size:11px;color:' + c.t3 + ';margin:4px 0 10px">Show this indicator on:</div>' +
      ["1m", "3m", "5m", "15m", "4h"].map(function (tfV) {
        return '<div style="' + rowCss + '"><label style="display:flex;align-items:center;gap:9px;cursor:pointer">' +
          '<input type="checkbox" class="qc-vs-tf" data-tf="' + tfV + '"' + (visOv[tfV] === 0 ? '' : ' checked') + ' style="accent-color:' + c.blue + ';width:16px;height:16px">' +
          '<span style="font-size:12px;color:' + c.t1 + ';font-weight:600">' + tfV.toUpperCase() + ' timeframe</span></label></div>';
      }).join("");

    var tabBtn = function (id, txt, on) {
      return '<button type="button" id="' + id + '" style="flex:1;padding:10px 0;border:none;border-bottom:2px solid ' + (on ? c.blue : "transparent") + ';background:transparent;color:' + (on ? c.t1 : c.t3) + ';font-weight:800;font-size:12.5px;cursor:pointer;font-family:inherit">' + txt + '</button>';
    };
    // customizable panel background (color + opacity), persisted per device
    var shPref = { c: "#101a2e", a: 0.97 };
    try { var spL = JSON.parse(localStorage.getItem("dq_qc_sheet_style") || "null"); if (spL && spL.c && isFinite(spL.a)) shPref = spL; } catch (eSP) {}
    var sheetBg = function (hx, al) {
      hx = toHex6(hx);
      return "rgba(" + parseInt(hx.slice(1, 3), 16) + "," + parseInt(hx.slice(3, 5), 16) + "," + parseInt(hx.slice(5, 7), 16) + "," + Math.max(0.2, Math.min(1, al)) + ")";
    };
    var ov = document.createElement("div");
    ov.id = "dq-qc-cfg";
    ov.style.cssText = "position:fixed;inset:0;z-index:6400;background:rgba(3,6,14,.6)";
    ov.innerHTML = '<div id="qc-cfg-panel" style="position:absolute;left:0;right:0;bottom:0;background:' + sheetBg(shPref.c, shPref.a) + ';border-top:1px solid ' + c.bd + ';border-radius:16px 16px 0 0;padding:16px 16px calc(20px + env(safe-area-inset-bottom,0px));max-height:80vh;overflow-y:auto;-webkit-overflow-scrolling:touch;max-width:560px;margin:0 auto">' +
      '<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:4px">' +
        '<div style="min-width:0"><div style="font-size:14px;font-weight:800;color:' + c.t1 + ';overflow:hidden;text-overflow:ellipsis;white-space:nowrap">⚙ ' + ESC(ind.name || prog.title) + '</div>' +
        '<div style="font-size:10px;color:' + c.t4 + ';margin-top:2px">Pine v' + (prog.pineVersion || 5) + ' · changes apply instantly and are remembered on this device</div></div>' +
        '<button type="button" id="qc-cfg-x" style="width:30px;height:30px;flex:0 0 auto;border-radius:8px;border:1px solid ' + c.bd + ';background:transparent;color:' + c.t3 + ';cursor:pointer;font-family:inherit">✕</button></div>' +
      '<div style="display:flex;border-bottom:1px solid ' + soft + ';margin:8px 0 2px">' + tabBtn("qc-tab-in", "Inputs", true) + tabBtn("qc-tab-st", "Style", false) + tabBtn("qc-tab-vs", "Visibility", false) + '</div>' +
      '<div id="qc-pane-in" style="margin-top:4px">' + rows + '</div>' +
      '<div id="qc-pane-st" style="margin-top:4px;display:none">' + st + '</div>' +
      '<div id="qc-pane-vs" style="margin-top:4px;display:none">' + visPane + '</div>' +
      // Footer: Defaults ▾ on the left · Cancel / Apply on the right. The apply
      // button was a flat white slab (#e9f0fc) — the loudest thing on the panel,
      // fighting the chart it sits over. It now reads as the primary action in
      // the app's own blue, and Cancel recedes the way a cancel should.
      '<div style="display:flex;gap:10px;margin-top:14px;align-items:center;flex-wrap:wrap">' +
        '<select id="qc-cfg-def" class="qc-sel" style="padding:9px 10px"><option value="">Defaults</option><option value="inputs">Reset inputs</option><option value="style">Reset style</option><option value="all">Reset all</option></select>' +
        '<span style="flex:1"></span>' +
        '<button type="button" id="qc-cfg-cancel" class="qcb">Cancel</button>' +
        '<button type="button" id="qc-cfg-apply" class="qcb pri" style="padding:11px 26px;font-size:13px">Apply to chart</button>' +
      '</div>' +
      '<div style="display:flex;align-items:center;gap:8px;margin-top:10px;justify-content:flex-end">' +
        '<span style="font-size:10px;color:' + c.t4 + '">Panel background</span>' +
        '<input type="color" id="qc-cfg-bgc" value="' + toHex6(shPref.c) + '" style="width:32px;height:24px;padding:1px;border-radius:6px;border:1px solid ' + c.bd + ';background:transparent;cursor:pointer">' +
        '<input type="range" id="qc-cfg-bga" min="30" max="100" value="' + Math.round(shPref.a * 100) + '" style="width:90px;accent-color:' + c.blue + '" title="Panel opacity">' +
      '</div></div>';
    document.body.appendChild(ov);
    var close = function () { ov.remove(); };
    ov.addEventListener("click", function (e) { if (e.target === ov) close(); });
    ov.querySelector("#qc-cfg-x").onclick = close;
    // tab switching (Inputs / Style / Visibility)
    var tabsQ = [["qc-tab-in", "qc-pane-in"], ["qc-tab-st", "qc-pane-st"], ["qc-tab-vs", "qc-pane-vs"]];
    function selTab(which) {
      tabsQ.forEach(function (tq) {
        var tb = ov.querySelector("#" + tq[0]), pn = ov.querySelector("#" + tq[1]);
        var onT = tq[0] === which;
        if (pn) pn.style.display = onT ? "" : "none";
        if (tb) { tb.style.borderBottomColor = onT ? c.blue : "transparent"; tb.style.color = onT ? c.t1 : c.t3; }
      });
    }
    tabsQ.forEach(function (tq) { var tb = ov.querySelector("#" + tq[0]); if (tb) tb.onclick = function () { selTab(tq[0]); }; });
    // swatch buttons → TradingView palette popup (live preview on the swatch)
    ov.querySelectorAll(".qc-cfg-colb, .qc-st-pcb, .qc-st-scb").forEach(function (sw) {
      sw.onclick = function () {
        qcColorPop(sw, sw.getAttribute("data-c"), function (col) {
          sw.setAttribute("data-c", col); sw.style.background = col;
        });
      };
    });
    // ON/OFF selects repaint themselves as you flip them — the green is the
    // feedback, so it has to track the value, not just the initial render.
    ov.querySelectorAll(".qc-cfg-s").forEach(function (sel) {
      sel.addEventListener("change", function () {
        sel.classList.remove("is-on", "is-off");
        var cls = onOffClass(sel.value);
        if (cls) sel.classList.add(cls);
      });
    });
    // Defaults ▾ / Cancel
    var defSel = ov.querySelector("#qc-cfg-def");
    if (defSel) defSel.onchange = function () {
      var wh = defSel.value; defSel.value = "";
      if (!wh) return;
      try {
        if (wh === "inputs" || wh === "all") { localStorage.removeItem(cfgKey(ind.id)); ind._inputVals = null; }
        if (wh === "style" || wh === "all") { localStorage.removeItem(styleKey(ind.id)); ind._styleOv = null; }
      } catch (eDR) {}
      clearRunFor(ind.id); repaintChart(); close();
      toast2(wh === "all" ? "All settings reset" : ("Defaults restored: " + wh), "");
    };
    var cnBtn = ov.querySelector("#qc-cfg-cancel"); if (cnBtn) cnBtn.onclick = close;
    // live panel-background customization (applies instantly, persists per device)
    var bgcEl = ov.querySelector("#qc-cfg-bgc"), bgaEl = ov.querySelector("#qc-cfg-bga"), panEl = ov.querySelector("#qc-cfg-panel");
    var applySheet = function () {
      shPref = { c: bgcEl.value, a: (+bgaEl.value) / 100 };
      if (panEl) panEl.style.background = sheetBg(shPref.c, shPref.a);
      try { localStorage.setItem("dq_qc_sheet_style", JSON.stringify(shPref)); } catch (eAS) {}
    };
    if (bgcEl && bgaEl) { bgcEl.oninput = applySheet; bgaEl.oninput = applySheet; }
    ov.querySelectorAll(".qc-cfg-b").forEach(function (b) {
      b.onclick = function () {
        var on2 = b.getAttribute("data-on") !== "1";
        b.setAttribute("data-on", on2 ? "1" : "0"); b.textContent = on2 ? "ON" : "OFF";
        b.style.borderColor = on2 ? "rgba(34,197,94,.55)" : c.bd;
        b.style.background = on2 ? "rgba(34,197,94,.16)" : inpBg;
        b.style.color = on2 ? c.green : c.t3;
      };
    });
    function commit(newVals, newStyle) {
      ind._inputVals = newVals;
      ind._styleOv = newStyle;
      try {
        if (newVals) localStorage.setItem(cfgKey(ind.id), JSON.stringify(newVals)); else localStorage.removeItem(cfgKey(ind.id));
        if (newStyle) localStorage.setItem(styleKey(ind.id), JSON.stringify(newStyle)); else localStorage.removeItem(styleKey(ind.id));
      } catch (e) {}
      clearRunFor(ind.id);
      repaintChart();
      close();
    }
    var rsOld = ov.querySelector("#qc-cfg-reset"); if (rsOld) rsOld.onclick = function () { commit(null, null); toast2("Defaults restored", ""); };
    ov.querySelector("#qc-cfg-apply").onclick = function () {
      var nv = prog.inputs.map(function (x) { return x.def; });
      ov.querySelectorAll(".qc-cfg-b2").forEach(function (b) { nv[Number(b.getAttribute("data-i"))] = b.checked ? 1 : 0; });
      ov.querySelectorAll(".qc-cfg-s").forEach(function (s) { nv[Number(s.getAttribute("data-i"))] = s.value; });
      ov.querySelectorAll(".qc-cfg-t").forEach(function (s) { nv[Number(s.getAttribute("data-i"))] = s.value; });
      ov.querySelectorAll(".qc-cfg-colb").forEach(function (s) { nv[Number(s.getAttribute("data-i"))] = s.getAttribute("data-c"); });
      var bad = null;
      ov.querySelectorAll(".qc-cfg-n").forEach(function (s) {
        var ii = Number(s.getAttribute("data-i")), inp2 = prog.inputs[ii];
        var num = Number(s.value);
        if (!isFinite(num)) { bad = inp2.title; return; }
        if (inp2.type === "int") num = Math.round(num);
        if (inp2.min != null && num < inp2.min) num = inp2.min;
        if (inp2.max != null && num > inp2.max) num = inp2.max;
        nv[ii] = num;
      });
      if (bad) { toast2('"' + bad + '" is not a number', "error"); return; }
      // collect the STYLE tab → override object (only real deviations stored)
      var sv2 = { p: {}, s: {}, f: {}, bg: {}, bc: {}, lb: {} };
      ov.querySelectorAll(".qc-st-pcb").forEach(function (s) {
        var ii2 = Number(s.getAttribute("data-i")), spec2 = prog.plots[ii2];
        var e2 = (sv2.p[ii2] = sv2.p[ii2] || {});
        var cPick = s.getAttribute("data-c") || "";
        if (cPick && cPick.toLowerCase() !== String(spec2.color).toLowerCase()) e2.c = cPick;
      });
      ov.querySelectorAll(".qc-st-pw").forEach(function (s) { var ii2 = Number(s.getAttribute("data-i")); (sv2.p[ii2] = sv2.p[ii2] || {}).w = +s.value; });
      ov.querySelectorAll(".qc-st-ps").forEach(function (s) { var ii2 = Number(s.getAttribute("data-i")); (sv2.p[ii2] = sv2.p[ii2] || {}).s = s.value; });
      ov.querySelectorAll(".qc-st-pv").forEach(function (s) { var ii2 = Number(s.getAttribute("data-i")); if (!s.checked) (sv2.p[ii2] = sv2.p[ii2] || {}).h = 1; });
      ov.querySelectorAll(".qc-st-scb").forEach(function (s) {
        var ii2 = Number(s.getAttribute("data-i")), ss2 = prog.shapes[ii2];
        var e3 = (sv2.s[ii2] = sv2.s[ii2] || {});
        var cPick2 = s.getAttribute("data-c") || "";
        if (cPick2 && cPick2.toLowerCase() !== String(ss2.color).toLowerCase()) e3.c = cPick2;
      });
      ov.querySelectorAll(".qc-st-sv2").forEach(function (s) { var ii2 = Number(s.getAttribute("data-i")); if (!s.checked) (sv2.s[ii2] = sv2.s[ii2] || {}).h = 1; });
      ov.querySelectorAll(".qc-st-sl").forEach(function (s) {
        var ii2 = Number(s.getAttribute("data-i")), ss3 = prog.shapes[ii2];
        var defLoc = ss3 && ss3.below ? "below" : "above";
        if (s.value !== defLoc) (sv2.s[ii2] = sv2.s[ii2] || {}).loc = s.value;
      });
      var prE = ov.querySelector("#qc-st-prec"); if (prE && +prE.value !== 2) sv2.prec = Math.max(0, Math.min(8, Math.round(+prE.value) || 0));
      // visibility tab → per-timeframe display map
      var visM = {};
      ov.querySelectorAll(".qc-vs-tf").forEach(function (s) { if (!s.checked) visM[s.getAttribute("data-tf")] = 0; });
      if (Object.keys(visM).length) sv2.vis = visM;
      ov.querySelectorAll(".qc-st-fv").forEach(function (s) { var ii2 = Number(s.getAttribute("data-i")); if (!s.checked) (sv2.f[ii2] = { h: 1 }); });
      var bgE = ov.querySelector(".qc-st-bg"); if (bgE && !bgE.checked) sv2.bg.h = 1;
      var bgAE = ov.querySelector(".qc-st-bga"); if (bgAE) { var aV = (+bgAE.value) / 100; if (aV < 1) sv2.bg.a = aV; }
      var bcE = ov.querySelector(".qc-st-bc"); if (bcE && !bcE.checked) sv2.bc.h = 1;
      var cdE = ov.querySelector(".qc-st-cd"); if (cdE && !cdE.checked) sv2.cd = { h: 1 };   // plotcandle
      var lbE = ov.querySelector(".qc-st-lb"); if (lbE && !lbE.checked) sv2.lb.h = 1;
      var lnE = ov.querySelector(".qc-st-ln"); if (lnE && !lnE.checked) sv2.ln = { h: 1 };
      var bxE = ov.querySelector(".qc-st-bx"); if (bxE && !bxE.checked) sv2.bx = { h: 1 };
      var tbE = ov.querySelector(".qc-st-tb"); if (tbE && !tbE.checked) sv2.tb = { h: 1 };
      commit(nv, sv2);
      toast2("Indicator updated ✓", "success");
    };
  }
  /* ══════════════════ INDICATOR PERFORMANCE POLICY ══════════════════════════
   *
   * The old policy was prevention: any script over 800ms was aborted mid-run and
   * thrown off the chart. That is safe and wrong. It refused work the user asked
   * for, and it judged an indicator on a single measurement — the same reading a
   * background GC pause or a moment of thermal throttling can produce. It removed
   * indicators without proof.
   *
   * The policy now:
   *
   *   ONE AT A TIME.  The real cause of the 43-second freeze was four heavy
   *     scripts recomputing together on every new bar. One at a time makes the
   *     worst case the cost of a single script, and makes that cost attributable:
   *     when the chart hitches, you know exactly which indicator did it.
   *
   *   HEAVY IS ALLOWED — AND SAID OUT LOUD.  A script over HEAVY_MS runs, but the
   *     user is told once, plainly, that it is heavy and the chart will hitch on
   *     each new bar. Informed is not the same as blocked.
   *
   *   REMOVAL NEEDS EVIDENCE.  An indicator is only pulled when it has actually
   *     harmed the UI, and harm must REPEAT: a run over HARM_MS twice in a row
   *     (HARM_STRIKES). One slow run is noise — a phone can stall for a second on
   *     anything. Two in a row is a property of the script.
   *
   *   THE FUSE IS FOR RUNAWAYS, NOT FOR HEAVY.  A run that cannot finish in
   *     FUSE_MS is not "heavy", it is unbounded — 43 seconds of a dead app is not
   *     a trade-off anyone chooses. It is aborted and removed on the FIRST hit,
   *     because letting it finish just to prove the point costs the user their
   *     session. That is the one and only pre-emptive limit that remains.
   *
   * The studio (convert / diagnostics) runs with NO limit at all: there the user
   * is deliberately waiting for a result and nothing else is on screen. */
  var MAX_ACTIVE = 1;          // one indicator on the chart at a time
  var HEAVY_MS = 400;          // above this: tell the user it's heavy (still runs)
  var HARM_MS = 2000;          // a run this long is a visible freeze
  var HARM_STRIKES = 2;        // ...twice in a row = a real, repeatable problem
  var FUSE_MS = 6000;          // runaway: abort + remove on the first hit

  var QC_DEAD = {};            // ids removed this session — checked before every run
  var STRIKES = {};            // id -> consecutive harmful runs

  function runKeyFor(ind, candles, sym, tf) {
    return ind.id + ":" + sym + ":" + tf + ":" + candles.length + ":" + (candles.length ? candles[candles.length - 1].t : 0);
  }

  /* ── what we remember about an indicator's cost, per device ───────────────── */
  function costKey(id) { return "dq_qc_cost_" + id; }      // last measured run, ms
  function noticeKey(id) { return "dq_qc_heavynote_" + id; } // heavy notice already shown
  function costOf(id) { try { return Number(localStorage.getItem(costKey(id))) || 0; } catch (e) { return 0; } }
  function setCost(id, ms) { try { localStorage.setItem(costKey(id), String(Math.round(ms))); } catch (e) {} }
  function noticed(id) { try { return localStorage.getItem(noticeKey(id)) === "1"; } catch (e) { return false; } }
  function markNoticed(id) { try { localStorage.setItem(noticeKey(id), "1"); } catch (e) {} }
  function forgetCost(id) {
    try { localStorage.removeItem(costKey(id)); localStorage.removeItem(noticeKey(id)); } catch (e) {}
    delete STRIKES[id];
  }
  function isHeavy(id) { return costOf(id) >= HEAVY_MS; }

  // "This one is heavy" — said once per indicator per device, not on every bar.
  function heavyNotice(ind, ms) {
    if (noticed(ind.id)) return;
    markNoticed(ind.id);
    toast2("“" + (ind.name || "Indicator") + "” is a heavy indicator (~" + Math.round(ms) +
           "ms per update). It will run, but the chart may hitch briefly on each new bar. " +
           "Only one indicator can be on the chart at a time.", "warn");
  }

  // REMOVAL. Only ever called with a concrete, stated reason.
  function removeInd(ind, reason, detail) {
    QC_DEAD[ind.id] = 1;   // FIRST: the draw pass holds a stale snapshot of the
                           // active list, and setActiveIds() clears the run cache,
                           // so without this the same pass would re-run the very
                           // script we just removed.
    try { setActiveIds(activeIds().filter(function (x) { return x !== ind.id; })); } catch (e) {}
    var nm = ind.name || "Indicator";
    toast2("“" + nm + "” was removed from the chart — " + reason, "error");
    try {
      if (window.dqErrors && window.dqErrors.report) {
        window.dqErrors.report(
          new Error("Indicator auto-removed: " + nm + " (id " + ind.id + ") — " + reason +
                    (detail ? " · " + detail : "")),
          "quant-coder");
      }
    } catch (e) {}
    deferRepaint();   // NOT synchronous — see deferRepaint
  }

  // repaintChart() dispatches a synchronous event that calls drawChart(), which
  // calls drawActive() — so calling it from inside a draw re-enters the draw, on
  // the same frame, from inside a catch block. With four heavy scripts that
  // nests four deep and costs ~10 budget aborts (~10s) instead of four. Deferring
  // to the next task breaks the recursion: each frame pays for at most one abort,
  // and the user gets a responsive frame in between.
  var _deferPending = false;
  function deferRepaint() {
    if (_deferPending) return;
    _deferPending = true;
    setTimeout(function () { _deferPending = false; repaintChart(); }, 0);
  }

  function ensureRun(ind, candles, sym, tf) {
    var key = runKeyFor(ind, candles, sym, tf);
    var hit = _runCache[key];
    if (hit) return hit;
    var prog = ind._prog || (ind._prog = compile(ind.source));
    var t0 = nowMs();
    var res;
    try {
      // The fuse — not a performance budget. It exists so that a script with an
      // unbounded loop cannot take the whole app down with it; every legitimate
      // indicator, including genuinely heavy ones, finishes far inside it.
      res = run(prog, candles, ind._inputVals || (ind.config && ind.config.inputs), FUSE_MS);
    } catch (e) {
      if (e && e.qcBudget) {
        setCost(ind.id, e.ms);
        removeInd(ind,
          "it could not finish a single update in " + Math.round(FUSE_MS / 1000) + " seconds, which freezes the app on every new bar",
          "aborted at bar " + e.bars + "/" + e.total + " after " + Math.round(e.ms) + "ms");
        var friendly = new Error("Runaway indicator — no update could finish within " + Math.round(FUSE_MS / 1000) +
          "s (reached bar " + e.bars + "/" + e.total + "). Removed from the chart. Open it in Quant Coder: the usual cause is a nested per-bar loop.");
        friendly.qcBudget = true;
        throw friendly;
      }
      throw e;
    }

    var cost = nowMs() - t0;
    ind._runMs = Math.round(cost);   // surfaced in the studio diagnostics
    setCost(ind.id, cost);

    /* JUDGEMENT, not reflex.
     *
     * A single slow reading is not evidence: a phone that is thermal-throttling,
     * paging in the app, or running a GC will make an ordinary script look awful
     * exactly once. Pulling an indicator a trader is relying on because of one
     * bad sample is a worse failure than the hitch it prevents.
     *
     * So harm has to REPEAT before we act, and the strike counter resets the
     * moment the script behaves — which is what makes a removal a statement about
     * the script rather than about the moment it was measured. */
    if (cost >= HARM_MS) {
      STRIKES[ind.id] = (STRIKES[ind.id] || 0) + 1;
      if (STRIKES[ind.id] >= HARM_STRIKES) {
        removeInd(ind,
          "it froze the chart for about " + (cost / 1000).toFixed(1) + "s on " + HARM_STRIKES +
          " updates in a row. The chart recomputes every indicator on each new bar, so this would keep happening.",
          "run cost " + Math.round(cost) + "ms · strikes " + STRIKES[ind.id]);
        delete STRIKES[ind.id];
        var harmErr = new Error("Removed: this indicator froze the chart for ~" + (cost / 1000).toFixed(1) +
          "s on every update. Open it in Quant Coder to lighten it (usually a nested per-bar loop).");
        harmErr.qcBudget = true;   // treated like a budget stop by the draw loop
        throw harmErr;
      }
      // First strike: warn, don't punish. Say what happens if it repeats.
      toast2("“" + (ind.name || "Indicator") + "” froze the chart for " + (cost / 1000).toFixed(1) +
             "s on this update. If it happens again it will be removed from the chart.", "warn");
    } else {
      STRIKES[ind.id] = 0;              // behaved — the slate is clean
      if (cost >= HEAVY_MS) heavyNotice(ind, cost);
    }

    var out = { prog: prog, res: res };
    Object.keys(_runCache).forEach(function (k) { if (k.indexOf(ind.id + ":") === 0) delete _runCache[k]; });
    _runCache[key] = out;
    return out;
  }

  // called from quant-option drawChart: draw all ACTIVE indicators
  /* ── on-chart ALARMS: alert()/alertcondition() fires on the live bar become
        TradingView-style popup cards docked at the bottom-left of the chart ── */
  var ALERT_SEEN = Object.create(null), ALERT_SEEN_N = 0;
  function escAH(s) { return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }
  function alertPops(ind, res, o) {
    if (!res.alerts || !res.alerts.length) return;
    if (!alarmsOn(ind.id)) return;   // 🔕 legend toggle silences this indicator
    ensureCSS();   // alert cards animate with qcAlertIn, and the chart can fire
                   // one without the studio ever having been opened
    var lastI = o.full.length - 1;
    for (var aq = res.alerts.length - 1; aq >= 0 && aq >= res.alerts.length - 6; aq--) {
      var AL = res.alerts[aq];
      if (AL.i < lastI - 1) break;   // only the live bar (or the bar that just closed)
      var key = ind.id + "|" + (AL.t || AL.i) + "|" + String(AL.msg).slice(0, 60);
      if (ALERT_SEEN[key]) continue;
      ALERT_SEEN[key] = 1;
      if (++ALERT_SEEN_N > 400) { ALERT_SEEN = Object.create(null); ALERT_SEEN_N = 1; ALERT_SEEN[key] = 1; }
      alertPopup(o.sym, ind.name, AL.msg);
    }
  }
  function alertPopup(sym, indName, msg) {
    var wrap = document.querySelector(".qo-chartwrap"); if (!wrap) return;
    var dock = document.getElementById("qc-alertdock");
    if (!dock || dock.parentNode !== wrap) {
      if (dock && dock.parentNode) dock.parentNode.removeChild(dock);
      dock = document.createElement("div"); dock.id = "qc-alertdock";
      dock.style.cssText = "position:absolute;left:8px;bottom:24px;z-index:60;display:flex;flex-direction:column;gap:6px;max-width:76%;pointer-events:none";
      wrap.appendChild(dock);
    }
    while (dock.children.length >= 3) dock.removeChild(dock.firstChild);
    // Telegram-formatted messages are long: show the meaningful first lines only
    var lines = String(msg || "").split("\n").filter(function (l) {
      return l.trim() && !/^[━─\-—=_\s]+$/.test(l.trim());
    }).slice(0, 3);
    if (!lines.length) lines = ["Alert"];
    var card = document.createElement("div");
    card.style.cssText = "pointer-events:auto;background:#1e222d;color:#e9edf5;border:1px solid rgba(255,255,255,.09);border-radius:10px;padding:9px 28px 9px 10px;font:600 11px Outfit,sans-serif;box-shadow:0 6px 22px rgba(0,0,0,.5);position:relative;animation:qcAlertIn .28s cubic-bezier(.34,1.4,.64,1) both";
    card.innerHTML =
      '<div style="display:flex;gap:8px;align-items:flex-start">' +
        '<div style="flex:0 0 auto;width:22px;height:22px;border-radius:50%;background:#2962ff;display:flex;align-items:center;justify-content:center;font-size:12px">⏰</div>' +
        '<div style="min-width:0">' +
          '<div style="font-weight:800;font-size:11.5px">Alert on <span style="background:rgba(255,255,255,.12);border-radius:9px;padding:1px 7px">' + escAH(sym) + '</span></div>' +
          lines.map(function (l) { return '<div style="margin-top:3px;color:#cdd6e4;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:260px">' + escAH(l.trim()) + '</div>'; }).join("") +
          '<div style="margin-top:4px;color:#7c8698;font-size:10px">' + escAH(indName || "") + ' · ' + new Date().toLocaleTimeString() + '</div>' +
        '</div></div>' +
      '<span style="position:absolute;top:5px;right:9px;cursor:pointer;color:#9aa4b5;font-size:14px" data-x>×</span>';
    card.querySelector("[data-x]").onclick = function () { if (card.parentNode) card.parentNode.removeChild(card); };
    dock.appendChild(card);
    setTimeout(function () {
      if (!card.parentNode) return;
      card.style.transition = "opacity .4s"; card.style.opacity = "0";
      setTimeout(function () { if (card.parentNode) card.parentNode.removeChild(card); }, 450);
    }, 12000);
  }

  function drawActive(o) {
    // o: {ctx, c(theme), candles(visible), full, baseIdx, Y, xAt, area{padL,padT,plotW,plotH}, sym, tf, dp}
    var act = activeList();   // at most MAX_ACTIVE (1) — enforced in setActiveIds/activeIds
    if (!act.length) return;
    var ctx = o.ctx, A = o.area;
    // TWO widths, and the difference is the whole bug:
    //   A.plotW — where the BARS live (ends at the newest candle)
    //   CW      — the whole plot, including the host's right margin
    // Series geometry must use plotW; anything that reaches for the right EDGE of
    // the chart (an extended line, a box with extend=right, an hline, the
    // watermark) must use CW, or it stops dead at the live price.
    var CW = A.fullW || A.plotW;
    var oscState = null;
    var legendY = A.padT + 24;   // stacked identity rows, below the built-in legend
    LEGHIT.length = 0;           // tappable legend hit-rects, rebuilt every frame
    for (var ai = 0; ai < act.length; ai++) {
      var ind = ai < act.length ? act[ai] : null;
      if (!ind || !ind.source) continue;
      if (QC_DEAD[ind.id]) continue;   // disabled this session for overrunning its budget
      // Visibility tab: per-timeframe display (TV parity)
      var visG = ind._styleOv && ind._styleOv.vis;
      if (visG && visG[o.tf] === 0) continue;
      // a failing indicator must be VISIBLE, not silently absent: draw a red
      // ⚠ row where its legend would be; tapping it shows the full error
      var errRow = function (msg) {
        try {
          ctx.save();
          ctx.font = "700 9px Outfit, sans-serif"; ctx.textAlign = "left"; ctx.textBaseline = "middle";
          var eTxt = "⚠ " + (ind.name || "indicator") + " — error (tap for details)";
          var eW = Math.min(A.plotW - 12, ctx.measureText(eTxt).width + 14);
          ctx.fillStyle = "rgba(40,10,16,.82)";
          ctx.fillRect(A.padL + 4, legendY - 8, eW, 16);
          ctx.fillStyle = "#ff8585";
          ctx.fillText(eTxt, A.padL + 11, legendY);
          ctx.restore();
          LEGHIT.push({ x: A.padL + 4, y: legendY - 12, w: eW, h: 24, id: ind.id, err: String(msg || "unknown error") });
          legendY += 18;
        } catch (x) {}
      };

      // MEMOISED FAILURE. A script that cannot compile throws on EVERY pass, and
      // nothing caches a failure — so it would re-throw forever, and (worse) it
      // would consume the one guaranteed fresh-run slot below on every pass,
      // starving the indicators behind it and spinning the deferred repaint at
      // 100% CPU. Remember the failure and cost it nothing from then on. Cleared
      // by clearRunFor() when the script or its settings change.
      if (ind._failed) { errRow(ind._failed); continue; }

      var R;
      try { R = ensureRun(ind, o.full, o.sym, o.tf); }
      catch (e) {
        if (ind._errAt !== (e && e.message)) { ind._errAt = e && e.message; try { console.warn("[quant-coder] '" + ind.name + "' failed:", e); } catch (x) {} }
        errRow(e && e.message);
        // Removed (runaway or repeated harm) — it is already off the active list,
        // so stop this pass and let the deferred repaint redraw a clean chart.
        if (e && e.qcBudget) { deferRepaint(); break; }
        // Any OTHER failure is permanent until the script changes — remember it,
        // so it costs nothing on the next pass and cannot spin the repaint.
        ind._failed = (e && e.message) || "indicator failed";
        continue;
      }
      var prog = R.prog, res = R.res;
      try { alertPops(ind, res, o); } catch (eAP) {}   // live-bar alarms → popup cards
      var overlay = prog.overlay;
      var oscBox = null;
      if (!overlay) {
        // translucent inset pane on the lower third, autoscaled per indicator
        if (!oscState) {
          oscState = { top: A.padT + A.plotH * 0.66, h: A.plotH * 0.34 };
          ctx.save();
          ctx.fillStyle = "rgba(10,16,31,.45)";
          ctx.fillRect(A.padL, oscState.top, CW, oscState.h);   // pane spans the chart, margin included
          ctx.restore();
        }
        oscBox = oscState;
      }
      try { drawOne(ctx, o, prog, res, oscBox, ind.name, ind._styleOv); }
      catch (eD) {
        if (ind._errAt !== (eD && eD.message)) { ind._errAt = eD && eD.message; try { console.warn("[quant-coder] '" + ind.name + "' draw failed:", eD); } catch (x) {} }
        errRow(eD && eD.message);
        continue;
      }
      // ── on-chart identity: indicator NAME + its SETTINGS (first inputs) ──
      try {
        var vals = ind._inputVals || (ind.config && ind.config.inputs) || [];
        var setStr = "";
        for (var si = 0; si < Math.min(prog.inputs.length, 5); si++) {
          var iv = vals[si] != null ? vals[si] : prog.inputs[si].def;
          if (typeof iv === "number" || (typeof iv === "string" && iv.length <= 12)) {
            setStr += (setStr ? " · " : "") + String(prog.inputs[si].title).slice(0, 16) + "=" + iv;
          }
        }
        if (prog.inputs.length > 5) setStr += " · +" + (prog.inputs.length - 5) + " more";
        // live signal activity: per-shape fire counts over loaded history
        var fireS = "";
        if (prog.shapes.length && res.shapes) fireS = res.shapes.map(function (h) { return h.length; }).join("/");
        // full detail: current value of each plot (last computed bar)
        var valS = "";
        for (var vp = 0; vp < Math.min(prog.plots.length, 3); vp++) {
          var arrV = res.plots[vp]; if (!arrV) continue;
          var precV = (ind._styleOv && ind._styleOv.prec != null) ? ind._styleOv.prec : 2;
          for (var vq = arrV.length - 1; vq >= 0; vq--) {
            if (arrV[vq] != null) { valS += (valS ? " · " : "") + String(prog.plots[vp].title).slice(0, 12) + " " + Number(arrV[vq]).toFixed(precV); break; }
          }
        }
        ctx.save();
        ctx.font = "700 9px Outfit, sans-serif"; ctx.textAlign = "left"; ctx.textBaseline = "middle";
        // MOBILE: keep the chart clean — name + settings gear only; the full
        // detail (inputs, values, fire counts) lives on wide screens & in ⚙
        var compactLeg = A.plotW < 560;
        var lgTxt = compactLeg
          ? "◆ " + String(ind.name || prog.title).slice(0, 24) + "  ⚙"
          : "◆ " + (ind.name || prog.title) + (setStr ? "   " + setStr : "") + (valS ? "   ▸ " + valS : "") + (fireS ? "   ✎ " + fireS : "") + "  ⚙";
        var lgW = Math.min(A.plotW - 34, ctx.measureText(lgTxt).width + 14);
        ctx.fillStyle = "rgba(10,16,31,.72)";
        ctx.fillRect(A.padL + 4, legendY - 8, lgW, 16);
        ctx.fillStyle = "rgba(185,205,245,.95)";
        ctx.fillText(lgTxt, A.padL + 11, legendY);
        // 🔔 alarm toggle pill right after the legend row (its own tap target)
        var bellOn = alarmsOn(ind.id), bellX = A.padL + 4 + lgW + 4;
        ctx.fillStyle = "rgba(10,16,31,.72)";
        ctx.fillRect(bellX, legendY - 8, 18, 16);
        ctx.fillStyle = bellOn ? "#ffd75a" : "rgba(140,155,185,.6)";
        ctx.fillText(bellOn ? "🔔" : "🔕", bellX + 3, legendY);
        ctx.restore();
        // tap targets (generous padding for fingers)
        LEGHIT.push({ x: A.padL + 4, y: legendY - 12, w: lgW, h: 24, id: ind.id });
        LEGHIT.push({ x: bellX, y: legendY - 12, w: 24, h: 24, id: ind.id, bell: 1 });
        legendY += 18;
      } catch (e) {}
    }
  }
  function drawOne(ctx, o, prog, res, osc, label, styleOv) {
    var A = o.area, vis = o.candles.length, base = o.baseIdx;
    // TWO widths, and the difference is the whole point:
    //   A.plotW — where the BARS are (ends at the newest candle) → bar spacing
    //   CW      — the whole plot, including the host's right margin → chart edge
    // drawOne is a sibling of drawActive, not a closure inside it, so it needs its
    // own CW: anything reaching for the right EDGE (extended lines, extend=right
    // boxes, hlines, the watermark, the clip) must use CW or it stops dead at the
    // live price. Series geometry (colW below) stays on A.plotW.
    var CW = A.fullW || A.plotW;
    var SO = styleOv || {};
    var yFor;
    if (osc) {
      var mn = Infinity, mx = -Infinity, pi, k;
      for (pi = 0; pi < res.plots.length; pi++) {
        for (k = 0; k < vis; k++) { var vv = res.plots[pi][base + k]; if (vv != null && isFinite(vv)) { if (vv < mn) mn = vv; if (vv > mx) mx = vv; } }
      }
      if (!(mx > mn)) { mn = 0; mx = 1; }
      var pad2 = (mx - mn) * 0.1; mn -= pad2; mx += pad2;
      yFor = function (v) { return osc.top + osc.h - ((v - mn) / (mx - mn)) * osc.h; };
    } else yFor = o.Y;

    ctx.save();
    // Clip to the FULL plot (CW), including the host's right margin. Indicators
    // speak loudest about the live bar — entry/TP/SL tags, the "GOD MODE ▲ LONG"
    // card, projected levels, trend-tracer lines — and clipping at the data edge
    // cut every one of them in half.
    ctx.beginPath(); ctx.rect(A.padL, A.padT, CW, A.plotH); ctx.clip();
    var colW = A.plotW / Math.max(1, vis);
    // bgcolor: translucent vertical bands (Style tab controls extra opacity)
    if (res.bg && !(SO.bg && SO.bg.h)) {
      var bgAl = (SO.bg && SO.bg.a != null) ? SO.bg.a : 1;
      if (bgAl > 0.01) {
        ctx.save(); ctx.globalAlpha = bgAl;
        for (var g2 = 0; g2 < vis; g2++) {
          var bgc = res.bg[base + g2];
          if (bgc) { ctx.fillStyle = bgc; ctx.fillRect(A.padL + g2 * colW, A.padT, colW + 0.5, A.plotH); }
        }
        ctx.restore();
      }
    }
    // fill() between named plots
    if (prog.fills && prog.fills.length) {
      for (var f2 = 0; f2 < prog.fills.length; f2++) {
        var FL = prog.fills[f2], aArr = res.plots[FL.a], bArr = res.plots[FL.b];
        if (!aArr || !bArr) continue;
        if (SO.f && SO.f[f2] && SO.f[f2].h) continue;   // Style tab: fill hidden
        var dynF = res.fillCols && res.fillCols[f2];
        if (dynF) {
          // per-bar colored strips (fill(p1, p2, color = cond ? a : b))
          for (var s5 = 0; s5 < vis; s5++) {
            var av2 = aArr[base + s5], bv2 = bArr[base + s5];
            if (av2 == null || bv2 == null || !isFinite(av2) || !isFinite(bv2)) continue;
            ctx.fillStyle = dynF[base + s5] || FL.color;
            var yA = yFor(av2), yB = yFor(bv2);
            ctx.fillRect(A.padL + s5 * colW, Math.min(yA, yB), colW + 0.5, Math.max(1, Math.abs(yA - yB)));
          }
        } else {
          ctx.beginPath(); var fStarted = false, k5;
          for (k5 = 0; k5 < vis; k5++) { var av = aArr[base + k5]; if (av == null || !isFinite(av)) continue; var fx = o.xAt(k5), fy = yFor(av); if (!fStarted) { ctx.moveTo(fx, fy); fStarted = true; } else ctx.lineTo(fx, fy); }
          for (k5 = vis - 1; k5 >= 0; k5--) { var bv = bArr[base + k5]; if (bv == null || !isFinite(bv)) continue; ctx.lineTo(o.xAt(k5), yFor(bv)); }
          if (fStarted) { ctx.closePath(); ctx.fillStyle = FL.color; ctx.fill(); }
        }
      }
    }
    for (var p2 = 0; p2 < prog.plots.length; p2++) {
      var spec = prog.plots[p2], arr = res.plots[p2];
      // Style-tab overrides: hide / recolor / rewidth / restyle this plot.
      // A user-picked color also disables dynamic per-bar coloring so the
      // chosen color actually wins (exactly TradingView's behavior).
      if (spec.hidden) continue;   // display.none data-carrier (still feeds fills)
      var PO = SO.p && SO.p[p2];
      if (PO) {
        if (PO.h) continue;
        spec = {
          title: spec.title, expr: spec.expr,
          color: PO.c || spec.color,
          width: PO.w != null ? PO.w : spec.width,
          style: PO.s || spec.style,
          dynColor: PO.c ? null : spec.dynColor,
        };
      }
      if (spec.dynColor) {
        // per-bar colored segments (plot(x, color = cond ? a : b) pattern)
        ctx.lineWidth = spec.width || 1.5; ctx.lineJoin = "round";
        var px0 = null, py0 = null;
        for (var d2 = 0; d2 < vis; d2++) {
          var dv = arr[base + d2];
          if (dv == null || !isFinite(dv)) { px0 = null; continue; }
          var dx2 = o.xAt(d2), dy2 = yFor(dv);
          if (px0 != null) {
            var dSc = spec.dynColor[base + d2] || spec.color;
            if (!/,\s*0(\.0+)?\)\s*$/.test(dSc)) {   // alpha-0 (TV transp 100) → draw nothing
              ctx.strokeStyle = dSc;
              ctx.beginPath(); ctx.moveTo(px0, py0); ctx.lineTo(dx2, dy2); ctx.stroke();
            }
          }
          px0 = dx2; py0 = dy2;
        }
      } else if (spec.style === "hist") {
        var zeroY = Math.max(A.padT, Math.min(A.padT + A.plotH, yFor(0)));
        ctx.fillStyle = spec.color;
        for (var h5 = 0; h5 < vis; h5++) {
          var hv5 = arr[base + h5];
          if (hv5 == null || !isFinite(hv5)) continue;
          var hx5 = o.xAt(h5), hy5 = yFor(hv5);
          ctx.fillRect(hx5 - colW * 0.3, Math.min(hy5, zeroY), colW * 0.6, Math.max(1, Math.abs(zeroY - hy5)));
        }
      } else if (spec.style === "dots") {
        ctx.fillStyle = spec.color;
        for (var d5 = 0; d5 < vis; d5++) {
          var dv5b = arr[base + d5];
          if (dv5b == null || !isFinite(dv5b)) continue;
          ctx.beginPath(); ctx.arc(o.xAt(d5), yFor(dv5b), Math.max(1.5, (spec.width || 1.5)), 0, 6.2832); ctx.fill();
        }
      } else {
        ctx.beginPath(); var started = false;
        for (var i2 = 0; i2 < vis; i2++) {
          var v2 = arr[base + i2];
          if (v2 == null || !isFinite(v2)) { started = false; continue; }
          var x = o.xAt(i2), y = yFor(v2);
          if (!started) { ctx.moveTo(x, y); started = true; } else ctx.lineTo(x, y);
        }
        if (spec.style === "area" && started) {
          ctx.lineTo(o.xAt(vis - 1), A.padT + A.plotH); ctx.lineTo(o.xAt(0), A.padT + A.plotH); ctx.closePath();
          ctx.fillStyle = spec.color.indexOf("rgba") === 0 ? spec.color : spec.color + "33"; ctx.fill();
          ctx.beginPath(); started = false;
          for (var i3 = 0; i3 < vis; i3++) { var v3 = arr[base + i3]; if (v3 == null || !isFinite(v3)) { started = false; continue; } var x3 = o.xAt(i3), y3 = yFor(v3); if (!started) { ctx.moveTo(x3, y3); started = true; } else ctx.lineTo(x3, y3); }
        }
        ctx.strokeStyle = spec.color; ctx.lineWidth = spec.width || 1.5; ctx.lineJoin = "round"; ctx.stroke();
      }
    }
    // barcolor: thin strip along the bottom of the plot
    if (res.barc && !(SO.bc && SO.bc.h)) {
      for (var bc2 = 0; bc2 < vis; bc2++) {
        var bcc = res.barc[base + bc2];
        if (bcc) { ctx.fillStyle = bcc; ctx.fillRect(A.padL + bc2 * colW, A.padT + A.plotH - 4, colW + 0.5, 4); }
      }
    }

    /* plotcandle(): repaint the candles in the script's own colours.
     *
     * Drawn on the price scale only (an oscillator pane has no candles to
     * recolour), and only for bars the script actually coloured — a `na` colour
     * means "don't draw", which is how these scripts implement their Color
     * Candles switch. We paint OVER the host's candles rather than replacing
     * them, so a bar the script leaves untouched keeps the terminal's own
     * red/green and nothing goes missing. */
    if (!osc && res.candles && res.candles.length && !(SO.cd && SO.cd.h)) {
      var cwB = Math.max(1, colW * 0.62);
      for (var cq = 0; cq < res.candles.length; cq++) {
        var cArr = res.candles[cq]; if (!cArr) continue;
        for (var cb = 0; cb < vis; cb++) {
          var K = cArr[base + cb];
          if (!K || !isFinite(K.o) || !isFinite(K.h) || !isFinite(K.l) || !isFinite(K.c)) continue;
          var xK = o.xAt(cb);
          var yHi = o.Y(K.h), yLo = o.Y(K.l), yOp = o.Y(K.o), yCl = o.Y(K.c);
          if (K.wick) {
            ctx.strokeStyle = K.wick; ctx.lineWidth = 1;
            ctx.beginPath(); ctx.moveTo(xK, yHi); ctx.lineTo(xK, yLo); ctx.stroke();
          }
          var bTop = Math.min(yOp, yCl);
          var bH = Math.max(1, Math.abs(yCl - yOp));   // doji: still a visible line
          if (K.col) { ctx.fillStyle = K.col; ctx.fillRect(xK - cwB / 2, bTop, cwB, bH); }
          if (K.bord && K.bord !== K.col) {
            ctx.strokeStyle = K.bord; ctx.lineWidth = 1;
            ctx.strokeRect(xK - cwB / 2, bTop, cwB, bH);
          }
        }
      }
    }
    // ── line & box OBJECTS (order blocks, FVGs, structure, TP/SL rails) ──
    var fullT = o.full;
    var tIdxD = function (t) {   // time(ms) → index in the FULL series
      var nF = fullT.length; if (!nF) return 0;
      if (t <= fullT[0].t) return 0;
      if (t >= fullT[nF - 1].t) return nF - 1;
      var loD = 0, hiD = nF - 1;
      while (loD < hiD - 1) { var mD = (loD + hiD) >> 1; if (fullT[mD].t <= t) loD = mD; else hiD = mD; }
      return loD;
    };
    var objX = function (v, isT) { return o.xAt((isT ? tIdxD(v) : v) - base); };
    if (res.boxes && res.boxes.length && !(SO.bx && SO.bx.h)) {
      for (var bq = 0; bq < res.boxes.length; bq++) {
        var BX = res.boxes[bq];
        if (BX.del || !isFinite(BX.top) || !isFinite(BX.bottom) || !isFinite(BX.left) || !isFinite(BX.right)) continue;
        var xL = objX(BX.left, BX.t), xR = objX(BX.right, BX.t);
        // extend=right means "to the edge of the CHART" — CW, not the last bar
        if (BX.ext === "r" || BX.ext === "b") xR = A.padL + CW;
        if (BX.ext === "l" || BX.ext === "b") xL = A.padL;
        if (!isFinite(xL) || !isFinite(xR)) continue;
        var yT2 = yFor(BX.top), yB2 = yFor(BX.bottom);
        var rx2 = Math.min(xL, xR), rw2 = Math.max(1, Math.abs(xR - xL));
        var ry2 = Math.min(yT2, yB2), rh2 = Math.max(1, Math.abs(yB2 - yT2));
        if (rx2 > A.padL + CW || rx2 + rw2 < A.padL) continue;   // cull against the FULL plot
        if (BX.bg) { ctx.fillStyle = BX.bg; ctx.fillRect(rx2, ry2, rw2, rh2); }
        if (BX.bc) {
          ctx.strokeStyle = BX.bc; ctx.lineWidth = 1;
          if (BX.dash) ctx.setLineDash(BX.dash);
          ctx.strokeRect(rx2, ry2, rw2, rh2); ctx.setLineDash([]);
        }
      }
    }
    // linefill: shaded quad between two line segments (TP/SL bands)
    if (res.linefills && res.linefills.length && !(SO.ln && SO.ln.h)) {
      var lnMap = {};
      (res.lines || []).forEach(function (L5) { lnMap[L5.id] = L5; });
      for (var fq2 = 0; fq2 < res.linefills.length; fq2++) {
        var LF = res.linefills[fq2], LA = lnMap[LF.a], LB2 = lnMap[LF.b];
        if (!LA || !LB2 || LA.del || LB2.del) continue;
        var ax1 = objX(LA.x1, LA.t), ax2 = objX(LA.x2, LA.t);
        var bx1 = objX(LB2.x1, LB2.t), bx2 = objX(LB2.x2, LB2.t);
        var ay1 = yFor(LA.y1), ay2 = yFor(LA.y2), by1 = yFor(LB2.y1), by2 = yFor(LB2.y2);
        if (!(isFinite(ax1) && isFinite(ax2) && isFinite(bx1) && isFinite(bx2) && isFinite(ay1) && isFinite(ay2) && isFinite(by1) && isFinite(by2))) continue;
        ctx.fillStyle = LF.c;
        ctx.beginPath(); ctx.moveTo(ax1, ay1); ctx.lineTo(ax2, ay2); ctx.lineTo(bx2, by2); ctx.lineTo(bx1, by1); ctx.closePath(); ctx.fill();
      }
    }
    if (res.lines && res.lines.length && !(SO.ln && SO.ln.h)) {
      for (var lq = 0; lq < res.lines.length; lq++) {
        var LN = res.lines[lq];
        if (LN.del || !isFinite(LN.y1) || !isFinite(LN.y2) || !isFinite(LN.x1) || !isFinite(LN.x2)) continue;
        var lx1 = objX(LN.x1, LN.t), lx2 = objX(LN.x2, LN.t);
        var ly1 = yFor(LN.y1), ly2 = yFor(LN.y2);
        if (!isFinite(lx1) || !isFinite(lx2)) continue;
        // Extend along the slope to the plot edges. A level that says "extend
        // right" means to the right edge of the CHART — this is the TP/SL and
        // support/resistance line that used to stop at the live candle.
        var slp = (lx2 - lx1) ? (ly2 - ly1) / (lx2 - lx1) : 0;
        if (LN.ext === "r" || LN.ext === "b") { var xr2 = A.padL + CW; ly2 = ly2 + slp * (xr2 - lx2); lx2 = xr2; }
        if (LN.ext === "l" || LN.ext === "b") { var xl2 = A.padL; ly1 = ly1 + slp * (xl2 - lx1); lx1 = xl2; }
        if (Math.max(lx1, lx2) < A.padL || Math.min(lx1, lx2) > A.padL + CW) continue;
        ctx.strokeStyle = LN.c; ctx.lineWidth = LN.w || 1;
        if (LN.dash) ctx.setLineDash(LN.dash);
        ctx.beginPath(); ctx.moveTo(lx1, ly1); ctx.lineTo(lx2, ly2); ctx.stroke();
        ctx.setLineDash([]);
      }
    }
    // label.new records
    if (res.labels && res.labels.length && !(SO.lb && SO.lb.h)) {
      ctx.font = "700 8.5px Outfit, sans-serif"; ctx.textBaseline = "middle"; ctx.textAlign = "center";
      for (var lb2 = 0; lb2 < res.labels.length; lb2++) {
        var LB = res.labels[lb2], li2 = LB.i - base;
        if (LB.del) continue;   // label.delete'd — TV keep-only-latest patterns
        if (li2 < 0 || li2 >= vis) continue;
        var lx2 = o.xAt(li2);
        var ly2 = LB.y != null ? yFor(LB.y) : (LB.up ? o.Y(o.candles[li2].l) + 4 : o.Y(o.candles[li2].h) - 4);
        ctx.fillStyle = LB.color;
        if (LB.text) {
          // ── TV-quality pointer tags: rounded body + arrow tip at the anchor ──
          var lbLines = String(LB.text).split("\n"), lnH2 = 11;
          var lbW = 0;
          for (var q9 = 0; q9 < lbLines.length; q9++) { var w9b = ctx.measureText(lbLines[q9]).width; if (w9b > lbW) lbW = w9b; }
          var txtW = lbW + 14, boxH2 = lbLines.length * lnH2 + 7;
          var d4 = LB.dir || (LB.up ? "up" : "down"), tip4 = 7, rd4 = 4;
          var bx4, by4;   // body top-left; the arrow tip sits exactly at (lx2, ly2)
          if (d4 === "right")     { bx4 = lx2 - tip4 - txtW; by4 = ly2 - boxH2 / 2; }
          else if (d4 === "left") { bx4 = lx2 + tip4;        by4 = ly2 - boxH2 / 2; }
          else if (d4 === "up")   { bx4 = lx2 - txtW / 2;    by4 = ly2 + tip4; }
          else                    { bx4 = lx2 - txtW / 2;    by4 = ly2 - tip4 - boxH2; }
          ctx.save();
          ctx.shadowColor = "rgba(0,0,0,0.5)"; ctx.shadowBlur = 6; ctx.shadowOffsetY = 1;
          ctx.beginPath();
          if (ctx.roundRect) ctx.roundRect(bx4, by4, txtW, boxH2, rd4); else ctx.rect(bx4, by4, txtW, boxH2);
          ctx.fill();
          ctx.shadowColor = "transparent";
          ctx.beginPath();   // arrow tip
          if (d4 === "right")     { ctx.moveTo(lx2, ly2); ctx.lineTo(bx4 + txtW - 0.5, ly2 - 5); ctx.lineTo(bx4 + txtW - 0.5, ly2 + 5); }
          else if (d4 === "left") { ctx.moveTo(lx2, ly2); ctx.lineTo(bx4 + 0.5, ly2 - 5); ctx.lineTo(bx4 + 0.5, ly2 + 5); }
          else if (d4 === "up")   { ctx.moveTo(lx2, ly2); ctx.lineTo(lx2 - 5, by4 + 0.5); ctx.lineTo(lx2 + 5, by4 + 0.5); }
          else                    { ctx.moveTo(lx2, ly2); ctx.lineTo(lx2 - 5, by4 + boxH2 - 0.5); ctx.lineTo(lx2 + 5, by4 + boxH2 - 0.5); }
          ctx.closePath(); ctx.fill();
          ctx.restore();
          ctx.fillStyle = LB.tcolor;
          var yLn = by4 + boxH2 / 2 - ((lbLines.length - 1) * lnH2) / 2;
          var xCn = bx4 + txtW / 2;
          for (var q9c = 0; q9c < lbLines.length; q9c++) ctx.fillText(lbLines[q9c], xCn, yLn + q9c * lnH2);
        } else { ctx.beginPath(); ctx.arc(lx2, ly2, 3, 0, 6.2832); ctx.fill(); }
      }
    }
    for (var s3 = 0; s3 < prog.shapes.length; s3++) {
      var sp = prog.shapes[s3], hits = res.shapes[s3];
      var SHO = SO.s && SO.s[s3];
      if (SHO && SHO.h) continue;
      ctx.font = "700 10px Outfit, sans-serif"; ctx.textAlign = "center"; ctx.textBaseline = "middle";
      for (var h3 = 0; h3 < hits.length; h3++) {
        var bi = hits[h3] - base;
        if (bi < 0 || bi >= vis) continue;
        var K3 = o.candles[bi];
        var belowS = (SHO && SHO.loc) ? SHO.loc === "below" : sp.below;   // Style tab location override
        var sy = belowS ? o.Y(K3.l) + 12 : o.Y(K3.h) - 12;
        ctx.fillStyle = (SHO && SHO.c) || (res.shapeCols && res.shapeCols[s3]) || sp.color;
        ctx.fillText(sp.glyph, o.xAt(bi), sy);
        if (sp.text) { ctx.font = "700 8px Outfit, sans-serif"; ctx.fillText(sp.text, o.xAt(bi), sy + (sp.below ? 10 : -10)); ctx.font = "700 10px Outfit, sans-serif"; }
      }
    }
    // ── TABLE dashboards: corner-anchored grids (GOD MODE / Smart Panel) ──
    // PINNED to the true chart edges: escape the (possibly gap-compressed)
    // plot clip and anchor to the FULL plot width, so scrolling/panning and
    // the right-gap never move the dashboard.
    if (res.tables && res.tables.length && !(SO.tb && SO.tb.h)) {
      ctx.restore(); ctx.save();   // pop the plot clip (re-save keeps the final restore balanced)
      var FW = A.fullW || A.plotW;
      var SZT = { tiny: 8, small: 9, normal: 10.5, large: 12, huge: 14 };
      ctx.textBaseline = "middle";
      for (var tq = 0; tq < res.tables.length; tq++) {
        var TB = res.tables[tq];
        if (TB.del) continue;
        var keysT = Object.keys(TB.cells); if (!keysT.length) continue;
        var colWd = [], rowSet = {}, maxCq = 0;
        keysT.forEach(function (k9) {
          var pr9 = k9.split(","), cI = +pr9[0], rI = +pr9[1];
          if (cI > maxCq) maxCq = cI;
          rowSet[rI] = 1;
          var cell = TB.cells[k9], fpx = SZT[cell.sz] || 9;
          ctx.font = "700 " + fpx + "px Outfit, sans-serif";
          var w9 = ctx.measureText(cell.t || "").width + 12;
          if (!colWd[cI] || w9 > colWd[cI]) colWd[cI] = w9;
        });
        var rowsT = Object.keys(rowSet).map(Number).sort(function (a9, b9) { return a9 - b9; });
        var rowH = 16, totW = 0, totH = rowsT.length * rowH;
        for (var cq = 0; cq <= maxCq; cq++) { colWd[cq] = Math.min(colWd[cq] || 18, FW * 0.4); totW += colWd[cq]; }
        totW = Math.min(totW, FW * 0.72);
        var tx0 = /right/.test(TB.pos) ? A.padL + FW - totW - 6 : /center/.test(TB.pos) ? A.padL + (FW - totW) / 2 : A.padL + 6;
        var ty0 = /bottom/.test(TB.pos) ? A.padT + A.plotH - totH - 6 : /middle/.test(TB.pos) ? A.padT + (A.plotH - totH) / 2 : A.padT + 26;
        if (TB.bg) { ctx.fillStyle = TB.bg; ctx.fillRect(tx0, ty0, totW, totH); }
        var yAcc = ty0;
        for (var rq = 0; rq < rowsT.length; rq++) {
          var xAcc = tx0;
          for (var cq2 = 0; cq2 <= maxCq; cq2++) {
            var cell2 = TB.cells[cq2 + "," + rowsT[rq]];
            if (cell2) {
              if (cell2.bg) { ctx.fillStyle = cell2.bg; ctx.fillRect(xAcc, yAcc, colWd[cq2], rowH); }
              if (cell2.t) {
                var fpx2 = SZT[cell2.sz] || 9;
                ctx.font = "700 " + fpx2 + "px Outfit, sans-serif";
                ctx.fillStyle = cell2.tc || "#e9f0fc";
                ctx.textAlign = cell2.ha === "left" ? "left" : cell2.ha === "right" ? "right" : "center";
                ctx.save(); ctx.beginPath(); ctx.rect(xAcc, yAcc, colWd[cq2], rowH); ctx.clip();
                ctx.fillText(cell2.t, cell2.ha === "left" ? xAcc + 5 : cell2.ha === "right" ? xAcc + colWd[cq2] - 5 : xAcc + colWd[cq2] / 2, yAcc + rowH / 2);
                ctx.restore();
              }
            }
            if (TB.borderC) { ctx.strokeStyle = TB.borderC; ctx.lineWidth = Math.min(2, TB.bw || 1); ctx.strokeRect(xAcc + 0.5, yAcc + 0.5, colWd[cq2] - 1, rowH - 1); }
            xAcc += colWd[cq2];
          }
          yAcc += rowH;
        }
        if (TB.frameC) { ctx.strokeStyle = TB.frameC; ctx.lineWidth = Math.min(3, TB.fw || 1); ctx.strokeRect(tx0, ty0, totW, totH); }
      }
      ctx.textAlign = "left";
    }
    if (!osc) {
      for (var hl4 = 0; hl4 < prog.hlines.length; hl4++) {
        var H4 = prog.hlines[hl4], hy = o.Y(H4.v);
        ctx.strokeStyle = H4.color; ctx.setLineDash([3, 4]); ctx.lineWidth = 1;
        ctx.beginPath(); ctx.moveTo(A.padL, hy); ctx.lineTo(A.padL + CW, hy); ctx.stroke(); ctx.setLineDash([]);   // hline spans the chart
      }
    }
    // small label (watermark) — pinned to the chart's right edge, not the data's
    ctx.font = "700 8.5px Outfit, sans-serif"; ctx.textAlign = "right"; ctx.textBaseline = "top";
    ctx.fillStyle = "rgba(160,180,220,.55)";
    var ly = osc ? osc.top + 3 : A.padT + 3 + (drawOne._lbl = (drawOne._lbl || 0));
    ctx.fillText(String(label || prog.title), A.padL + CW - 4, ly);
    ctx.restore();
  }

  /* ════════════════════════ LIBRARY STATE ════════════════════════ */
  var LIB = { list: null, at: 0 };
  // Clamped on READ as well as on write: a device that still has a multi-indicator
  // list saved from before this rule (or from a future change) must not be able to
  // put four heavy scripts back on the chart.
  function activeIds() {
    try { return (JSON.parse(localStorage.getItem("dq_qc_active") || "[]") || []).slice(-MAX_ACTIVE); }
    catch (e) { return []; }
  }
  /* THE choke point. Every path that changes what is on the chart — the studio
   * library, the chart's Indicators sheet, anything added later — goes through
   * here, so the two invariants are enforced in exactly one place rather than
   * re-implemented (and eventually forgotten) at each call site:
   *
   *   1. AT MOST ONE indicator is active. If a caller hands us more, the LAST one
   *      wins — that is the one the user just tapped. Enforcing it here means the
   *      UI cannot drift out of policy no matter which surface adds an indicator.
   *
   *   2. Adding an id back REVIVES it. Without this, an indicator removed for
   *      harming the UI would stay in QC_DEAD for the session: the button would
   *      say "ON CHART" and nothing would draw — no plot, no error row, no toast.
   *      Silently invisible is the worst outcome available; it reads as "broken"
   *      rather than "too slow". If it really does harm the UI again, it will be
   *      removed again, loudly, with the reason.
   */
  function setActiveIds(ids) {
    var list = (ids || []).slice(-MAX_ACTIVE);   // keep the most recent selection
    try { list.forEach(function (id) { delete QC_DEAD[id]; delete STRIKES[id]; }); } catch (e) {}
    try { localStorage.setItem("dq_qc_active", JSON.stringify(list)); } catch (e) {}
    _runCache = {};
  }
  function activeList() {
    if (!LIB.list) return [];
    var ids = activeIds();
    return LIB.list.filter(function (x) { return ids.indexOf(x.id) >= 0; });
  }
  function loadLib(force) {
    if (!force && LIB.list && Date.now() - LIB.at < 60000) return Promise.resolve(LIB.list);
    return API("/quantoption/indicators").then(function (r) {
      LIB.list = (r && r.indicators) || [];
      LIB.at = Date.now();
      LIB.list.forEach(function (x) { try { x.config = x.config ? JSON.parse(x.config) : null; } catch (e) { x.config = null; } });
      LIB.list.forEach(hydrateCfg);   // apply device-saved input overrides
      return LIB.list;
    });
  }

  /* ════════════════════════ QUANT CODER STUDIO UI ════════════════════════ */
  var OV = "qc-ov";

  /* One stylesheet for every control in the studio. Buttons used to be ~15 hand-
   * written inline style strings that drifted apart from each other; now there is
   * one vocabulary — .qcb (base) + a role modifier — so a button's colour states
   * its JOB: primary = the thing you came here to do, gold = publishing, red =
   * destructive, blue-on = already on the chart.
   *
   * Injected by BOTH open() and openSettings(), because the settings sheet can be
   * opened straight from the chart without the studio ever being opened. */
  function ensureCSS() {
    if (document.getElementById("qc-anim-css")) return;
    var stC = document.createElement("style"); stC.id = "qc-anim-css";
    var c = TH();
    stC.textContent = [
      "@keyframes qcRing{to{stroke-dashoffset:var(--qcDash)}}",
      "@keyframes qcPop{0%{transform:scale(.3);opacity:0}70%{transform:scale(1.15)}100%{transform:scale(1);opacity:1}}.qc-pop{animation:qcPop .5s cubic-bezier(.34,1.56,.64,1) both}",
      "@keyframes qcBob{0%,100%{transform:translateY(0) rotate(-3deg)}50%{transform:translateY(-10px) rotate(3deg)}}",
      "@keyframes qcScan{0%{left:6%}50%{left:86%}100%{left:6%}}",
      "@keyframes qcConf{0%{transform:translateY(-8px) rotate(0);opacity:1}100%{transform:translateY(130px) rotate(540deg);opacity:0}}",
      "@keyframes qcDone{0%{transform:scale(.4);opacity:0}60%{transform:scale(1.25)}100%{transform:scale(1)}}",
      "@keyframes qcAlertIn{0%{transform:translateY(14px) scale(.92);opacity:0}100%{transform:translateY(0) scale(1);opacity:1}}",

      /* nothing in the studio may push the page sideways */
      "#qc-ov,#dq-qc-cfg{overflow-x:hidden}",
      "#qc-ov *{box-sizing:border-box;min-width:0}",

      /* ── buttons ── */
      ".qcb{display:inline-flex;align-items:center;justify-content:center;gap:6px;padding:9px 14px;border-radius:10px;",
      "border:1px solid " + c.bd + ";background:rgba(120,140,190,.06);color:" + c.t2 + ";font-family:inherit;font-size:12.5px;",
      "font-weight:800;line-height:1;white-space:nowrap;cursor:pointer;transition:.15s;-webkit-appearance:none}",
      ".qcb:hover{border-color:rgba(120,140,190,.42);color:" + c.t1 + ";background:rgba(120,140,190,.11)}",
      ".qcb:active{transform:translateY(1px)}",
      ".qcb[disabled]{opacity:.45;pointer-events:none}",
      ".qcb.sm{padding:7px 11px;font-size:11px;border-radius:9px}",
      /* primary — the convert/apply action */
      ".qcb.pri{border-color:transparent;background:linear-gradient(180deg," + c.blue + "," + c.blueD + ");color:#fff;box-shadow:0 6px 18px rgba(29,78,216,.35)}",
      ".qcb.pri:hover{filter:brightness(1.09);border-color:transparent;color:#fff}",
      /* AI */
      ".qcb.ai{border-color:transparent;background:linear-gradient(135deg,#7c3aed,#4f46e5);color:#fff;box-shadow:0 6px 18px rgba(79,70,229,.32)}",
      ".qcb.ai:hover{filter:brightness(1.09);color:#fff}",
      ".qcb.violet{border-color:rgba(124,58,237,.45);background:rgba(124,58,237,.10);color:#b79cff}",
      ".qcb.violet:hover{background:rgba(124,58,237,.18);color:#cbb6ff}",
      /* state: already drawn on the chart */
      ".qcb.on{border-color:rgba(61,139,255,.55);background:rgba(61,139,255,.16);color:#7cbcff}",
      ".qcb.on:hover{background:rgba(61,139,255,.24);color:#a6d0ff}",
      /* publish */
      ".qcb.gold{border-color:rgba(245,185,66,.38);background:rgba(245,185,66,.08);color:" + c.gold + "}",
      ".qcb.gold:hover{background:rgba(245,185,66,.17);color:#ffd88a}",
      /* destructive */
      ".qcb.danger{border-color:rgba(239,68,68,.32);background:rgba(239,68,68,.07);color:#ff8ba0}",
      ".qcb.danger:hover{background:rgba(239,68,68,.16);color:#ffa7b6}",

      /* ── library rows: wrap instead of overflowing the phone ── */
      ".qc-lrow{border:1px solid " + c.bd + ";border-radius:12px;padding:10px 12px;background:rgba(16,26,46,.6);transition:.15s}",
      ".qc-lrow:hover{border-color:rgba(120,140,190,.36)}",
      ".qc-lrow.on{border-color:rgba(61,139,255,.5);background:rgba(61,139,255,.07)}",
      ".qc-lhead{display:flex;align-items:center;gap:8px;flex-wrap:wrap;row-gap:9px}",
      ".qc-lname{flex:1 1 160px;min-width:0;font-size:13px;font-weight:800;color:" + c.t1 + ";overflow:hidden;text-overflow:ellipsis;white-space:nowrap}",
      ".qc-lacts{display:flex;align-items:center;gap:6px;flex:0 0 auto;flex-wrap:wrap;justify-content:flex-end}",
      "@media (max-width:560px){.qc-lacts{flex:1 1 100%;justify-content:flex-start}.qc-lacts .qcb{flex:1 1 auto}}",

      /* ── option selects (ON/OFF etc): state you can read at a glance ── */
      ".qc-sel{padding:7px 10px;border-radius:9px;border:1px solid " + c.bd + ";background:rgba(120,140,190,.08);",
      "color:" + c.t1 + ";font-size:12px;font-family:inherit;font-weight:700;cursor:pointer}",
      ".qc-sel.is-on{border-color:rgba(34,197,94,.5);background:rgba(34,197,94,.13);color:#4ade80}",
      ".qc-sel.is-off{border-color:" + c.bdSoft + ";background:rgba(120,140,190,.05);color:" + c.t3 + "}"
    ].join("");
    document.head.appendChild(stC);
  }

  function open() {
    if (document.getElementById(OV)) return;
    var c = TH();
    ensureCSS();
    var ov = document.createElement("div");
    ov.id = OV;
    ov.style.cssText = "position:fixed;inset:0;z-index:6000;display:flex;flex-direction:column;background:#070c18;font-family:Outfit,system-ui,sans-serif;padding-top:env(safe-area-inset-top,0)";
    ov.innerHTML =
      '<div style="display:flex;align-items:center;gap:11px;padding:12px 14px;border-bottom:1px solid ' + c.bd + ';flex-shrink:0">' +
        '<button id="qc-x" type="button" style="width:34px;height:34px;border-radius:10px;border:1px solid ' + c.bd + ';background:transparent;color:' + c.t2 + ';cursor:pointer;font-size:15px">‹</button>' +
        '<div style="flex:1"><div style="font-size:16px;font-weight:800;color:' + c.t1 + '">⚗️ Quant Coder</div>' +
        '<div style="font-size:11px;color:' + c.t3 + '">Paste indicator source → convert → chart it · Pine-style engine</div></div>' +
      '</div>' +
      '<div id="qc-body" style="flex:1;min-height:0;overflow-y:auto;-webkit-overflow-scrolling:touch;padding:14px;display:grid;gap:12px;grid-template-columns:1fr;max-width:1200px;margin:0 auto;width:100%">' +
        '<div>' +
          '<div style="display:flex;gap:8px;margin-bottom:8px;align-items:center;flex-wrap:wrap">' +
            '<input id="qc-name" placeholder="Indicator name" style="flex:1 1 160px;min-width:0;padding:10px 12px;border-radius:9px;background:' + c.inp + ';border:1px solid ' + c.bd + ';color:' + c.t1 + ';font-family:inherit;font-size:13px;outline:none">' +
            '<button id="qc-run" class="qcb pri" type="button">⚡ Convert</button>' +
            '<button id="qc-save" class="qcb" type="button">💾 Save</button>' +
          '</div>' +
          '<div style="display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap">' +
            '<button id="qc-ai" class="qcb ai" type="button" style="flex:1 1 200px">🤖 AI Assistant — convert · fix · categorize</button>' +
            '<button id="qc-bulk" class="qcb violet" type="button">📁 Bulk import</button>' +
          '</div>' +
          '<textarea id="qc-src" spellcheck="false" placeholder="// Paste Pine-style indicator source here, e.g.\n//@version=5\nindicator(&quot;My EMA Cross&quot;, overlay=true)\nfast = ta.ema(close, 12)\nslow = ta.ema(close, 26)\nplot(fast, color=color.green)\nplot(slow, color=color.red)\nplotshape(ta.crossover(fast, slow), style=shape.triangleup, location=location.belowbar, color=color.green)"' +
            ' style="width:100%;height:300px;resize:vertical;padding:12px;border-radius:10px;background:#0a1120;border:1px solid ' + c.bd + ';color:#cfe3ff;font-family:ui-monospace,Menlo,monospace;font-size:12px;line-height:1.55;outline:none;tab-size:4"></textarea>' +
          '<div id="qc-report" style="margin-top:8px;font-size:12px;line-height:1.6;color:' + c.t3 + '">Conversion report appears here.</div>' +
        '</div>' +
        '<div>' +
          '<div style="font-size:11px;letter-spacing:.6px;font-weight:800;color:' + c.t4 + ';margin-bottom:8px">MY INDICATORS · PUBLIC LIBRARY</div>' +
          '<div id="qc-lib" style="display:grid;gap:8px">Loading…</div>' +
        '</div>' +
      '</div>';
    document.body.appendChild(ov);
    if (window.innerWidth > 980) {
      var body = ov.querySelector("#qc-body");
      body.style.gridTemplateColumns = "3fr 2fr";
    }
    ov.querySelector("#qc-x").onclick = function () { ov.remove(); };
    ov.querySelector("#qc-run").onclick = function () { convertNow(ov); };
    ov.querySelector("#qc-save").onclick = function () { saveNow(ov); };
    ov.querySelector("#qc-ai").onclick = function () { aiRun(ov); };
    ov.querySelector("#qc-bulk").onclick = function () { bulkImport(ov); };
    renderLib(ov);
  }

  function convertNow(ov) {
    var src = ov.querySelector("#qc-src").value;
    var rep = ov.querySelector("#qc-report");
    var c = TH();
    var t0 = Date.now();
    var prog;
    try { prog = compile(src); } catch (e) {
      rep.innerHTML = '<span style="color:' + c.red + '">✗ Compile failed: ' + ESC(e.message) + '</span>';
      return null;
    }
    // smoke-run on synthetic candles to catch runtime issues
    var demo = [];
    var px = 100;
    var tNow = Date.now();   // real timestamps: session/time-window scripts behave plausibly
    // choppy walk: enough direction flips that pattern/engulfing strategies fire
    for (var i = 0; i < 260; i++) { var mv = Math.sin(i / 7) * 1.4 + (Math.random() - 0.5) * 2.6; var o2 = px; px = Math.max(1, px + mv); demo.push({ t: tNow - (260 - i) * 60000, o: o2, h: Math.max(o2, px) + Math.random() * 0.6, l: Math.min(o2, px) - Math.random() * 0.6, c: px }); }
    var ranOk = true, runErr = "", actHtml = "", deadOut = false, diagSelf = "";
    try {
      var resD = run(prog, demo, null);
      // OUTPUT-ACTIVITY SELF-TEST: does each declared output actually produce
      // data? This is the difference between "compiled" and "will display".
      var pAct = resD.plots.map(function (arr) { var nn = 0; for (var q3 = 0; q3 < arr.length; q3++) if (arr[q3] != null) nn++; return nn; });
      var sAct = resD.shapes.map(function (h) { return h.length; });
      var bgN = 0, bcN = 0;
      for (var q4 = 0; q4 < demo.length; q4++) { if (resD.bg && resD.bg[q4]) bgN++; if (resD.barc && resD.barc[q4]) bcN++; }
      // "dead" = NOTHING visible at all: labels, bar colors and bg tints are
      // first-class outputs too (many indicators draw ONLY labels + barcolor)
      var actLb = activeLabels(resD);
      // plotcandle bars that actually got a colour (a `na` colour = not drawn)
      var cdN = 0;
      (resD.candles || []).forEach(function (arrC) {
        for (var q5 = 0; q5 < arrC.length; q5++) if (arrC[q5]) cdN++;
      });
      var dead = !pAct.some(function (x) { return x > 0; })
        && !sAct.some(function (x) { return x > 0; })
        && !actLb && !bgN && !bcN && !cdN
        && !(resD.lines && resD.lines.length) && !(resD.boxes && resD.boxes.length)
        && (prog.plots.length + prog.shapes.length + prog.candles.length > 0 || prog.usesLabels || prog.usesLines || prog.usesBoxes);
      deadOut = dead;
      diagSelf = "self-test(260 synthetic bars): plot values [" + pAct.join(",") + "] · shape fires [" + sAct.join(",") + "] · labels " + actLb + " visible/" + ((resD.labels && resD.labels.length) || 0) + " created · bg " + bgN + " · barc " + bcN + (prog.candles.length ? " · plotcandle bars " + cdN : "") + (dead ? " · ⚠ ALL OUTPUTS EMPTY" : "");
      actHtml = '<div style="margin-top:6px;color:' + c.t3 + '">Self-test (260 synthetic bars): ' +
        (prog.plots.length ? 'plots with data <b>' + pAct.filter(function (x) { return x > 0; }).length + '/' + prog.plots.length + '</b> · values/plot [' + pAct.join(", ") + '] ' : '') +
        (prog.shapes.length ? '· shape fires <b>[' + sAct.join(", ") + ']</b> ' : '') +
        (resD.labels && resD.labels.length ? '· labels <b>' + activeLabels(resD) + '</b> visible ' : '') +
        (bgN ? '· bg bars ' + bgN + ' ' : '') + (bcN ? '· barcolor bars ' + bcN : '') +
        (dead ? '<b style="color:' + c.gold + '"> — ⚠ outputs produced NO data on the test run: the logic computes na/false throughout. Check the warnings list for a skipped construct this script depends on.</b>' : '') +
        '</div>';
    } catch (e) { ranOk = false; runErr = (e && e.message) || "unknown runtime error"; }
    var ms = Date.now() - t0;
    // ── accuracy score + error/skip separation ─────────────────────────────
    // The score measures CHART fidelity — how much of what the script draws we
    // reproduce. strategy.entry/exit are order-book calls: they draw nothing, so
    // skipping them costs the chart nothing and must not be scored as a loss.
    // Docking a perfectly-converted strategy for not being a backtester was the
    // score lying about its own subject. They are still listed under SUBSET SKIPS.
    var sw = splitWarnings(prog.warnings);
    var units = Math.max(1, prog.lineCount);
    var chartSkips = Math.max(0, sw.skips.length - (prog.strategyOrders || 0));
    var acc = Math.round(100 * Math.max(0, units - sw.errs.length * 2 - chartSkips) / units);
    QCSTATE.accBase = Math.max(2, Math.min(100, acc));   // pre-cap score (live test may restore it)
    if (!ranOk) acc = Math.min(acc, 15);
    else if (deadOut) acc = Math.min(acc, 45);
    acc = Math.max(2, Math.min(100, acc));
    QCSTATE.accuracy = acc;
    var cat = categorize(src);
    QCSTATE.category = cat;
    var errHtml = sw.errs.length
      ? '<div style="margin-top:8px;padding:10px 12px;border:1px solid rgba(239,68,68,.4);border-radius:10px;background:rgba(239,68,68,.07)">' +
        '<div style="color:' + c.red + ';font-weight:800;font-size:12px">🚨 Source errors — ' + sw.errs.length + ' statement(s) failed to parse (🤖 AI Assistant can auto-repair):</div>' +
        '<div style="max-height:110px;overflow-y:auto;color:#ff9d9d;font-family:ui-monospace,monospace;font-size:11px;margin-top:4px">' + sw.errs.slice(0, 30).map(ESC).join("<br>") + (sw.errs.length > 30 ? "<br>…" : "") + '</div></div>'
      : '';
    var gSkips = groupSkips(sw.skips);
    var sNote = strategyNote(prog);
    var warnHtml = sw.skips.length
      ? (sNote ? '<div style="margin-top:8px;padding:9px 11px;border:1px solid rgba(124,58,237,.35);border-radius:10px;background:rgba(124,58,237,.08);color:#c4b5fd;font-size:11.5px;line-height:1.5">🧠 ' + ESC(sNote) + '</div>' : '') +
        '<div style="margin-top:6px;color:' + c.gold + '">⚠ ' + sw.skips.length + ' construct(s) outside the engine subset, grouped into ' + gSkips.length + ' (skipped, named — never silently wrong):</div>' +
        '<div style="max-height:110px;overflow-y:auto;color:' + c.t4 + ';font-family:ui-monospace,monospace;font-size:11px">' + gSkips.slice(0, 40).map(ESC).join("<br>") + (gSkips.length > 40 ? "<br>…" : "") + '</div>'
      : (sw.errs.length ? '' : '<div style="margin-top:6px;color:' + c.green + '">✓ Every statement compiled — full-fidelity conversion.</div>');
    rep.innerHTML =
      '<span style="color:' + (ranOk ? c.green : c.red) + ';font-weight:800">' + (ranOk ? "✓ Converted" : "✗ Runtime failed: " + ESC(runErr)) + '</span>' +
      ' · <b>' + ESC(prog.title) + '</b> · ' + (prog.overlay ? "overlay" : "oscillator pane") +
      ' · Pine v' + (prog.pineVersion || 5) + ' · ' + prog.plots.length + ' plot(s), ' + prog.shapes.length + ' shape(s), ' +
      (prog.candles.length ? prog.candles.length + ' candle-paint, ' : '') + prog.inputs.length + ' input(s)' +
      ' · ' + prog.lineCount + ' lines → compiled in ' + ms + 'ms' +
      ' · <span style="color:#b79cff;font-weight:700">' + cat.emoji + ' ' + ESC(cat.cat) + '</span>' +
      '<span id="qc-ringwrap">' + ringHTML(acc, c) + '</span>' + actHtml + errHtml + warnHtml +
      (prog.inputs.length ? '<div style="margin-top:6px;color:' + c.t3 + '">Inputs (defaults captured, editable after saving): ' + prog.inputs.map(function (x) { return ESC(x.title) + "=" + ESC(String(x.def)); }).join(" · ") + '</div>' : '') +
      // ── copyable diagnostics panel (fills in when the live chart test lands) ──
      '<div style="margin-top:10px;border:1px solid ' + c.bd + ';border-radius:12px;background:#0a1120;overflow:hidden">' +
        '<div style="display:flex;align-items:center;justify-content:space-between;padding:8px 12px;border-bottom:1px solid ' + c.bd + '">' +
          '<span style="font-size:11px;font-weight:800;letter-spacing:.5px;color:' + c.t2 + '">🧪 DIAGNOSTICS — copy & send for troubleshooting</span>' +
          '<button id="qc-diag-copy" type="button" style="padding:6px 12px;border-radius:7px;border:1px solid ' + c.bd + ';background:rgba(28,132,255,.12);color:' + c.blue + ';font-weight:800;font-size:11px;cursor:pointer;font-family:inherit">📋 Copy report</button></div>' +
        '<pre id="qc-diag-text" style="margin:0;padding:10px 12px;max-height:230px;overflow:auto;font-size:10.5px;line-height:1.55;color:#9fb0cc;white-space:pre-wrap;user-select:text;-webkit-user-select:text">Running live chart test…</pre>' +
      '</div>';
    if (acc >= 95 && ranOk) confettiBurst(rep);
    // assemble the plain-text report head + kick the automatic live chart test
    var sw2 = sw;
    var diagBase = "=== QUANT CODER DIAGNOSTIC REPORT ===\n" + ENGINE_TAG + "\n" +
      "title: " + prog.title + " · Pine v" + (prog.pineVersion || 5) + " · " + (prog.overlay ? "overlay" : "oscillator pane") + "\n" +
      "accuracy " + acc + "% · category " + cat.cat + " · " + prog.plots.length + " plot(s) · " + prog.shapes.length + " shape(s) · " +
      (prog.candles.length ? prog.candles.length + " candle-paint · " : "") + prog.inputs.length + " input(s) · " + prog.lineCount + " lines\n" +
      (ranOk ? diagSelf : "SYNTHETIC RUN FAILED: " + runErr) + "\n" +
      (strategyNote(prog) ? "\nNOTE: " + strategyNote(prog) + "\n" : "") +
      (sw2.errs.length ? "\n=== PARSE ERRORS (" + sw2.errs.length + ") ===\n" + sw2.errs.join("\n") + "\n" : "") +
      // grouped, not one line per call: 14 strategy.exit()s are ONE fact, not 14
      (sw2.skips.length ? "\n=== SUBSET SKIPS (" + sw2.skips.length + " call(s), grouped) ===\n" + groupSkips(sw2.skips).join("\n") + "\n" : "");
    LAST_DIAG = diagBase + "\n(live chart test running…)";
    var cpBtn = rep.querySelector("#qc-diag-copy");
    if (cpBtn) cpBtn.onclick = function () {
      var t2 = LAST_DIAG || "";
      var okC = function () { toast2("Diagnostic report copied 📋", "success"); };
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t2).then(okC, function () { fallbackCopy(t2); okC(); });
      else { fallbackCopy(t2); okC(); }
    };
    try { chartTest(ov, prog, diagBase); } catch (eCT) { finishDiag(ov, diagBase + "\nlive test could not start: " + ((eCT && eCT.message) || eCT)); }
    return prog;
  }

  /* ═══════════ DIAGNOSTICS: copyable report + live chart test ═══════════ */
  var LAST_DIAG = "";
  var ENGINE_TAG = "quant-coder engine 20260712-13";
  function fallbackCopy(t) {
    try {
      var ta = document.createElement("textarea");
      ta.value = t; ta.style.cssText = "position:fixed;opacity:0";
      document.body.appendChild(ta); ta.select(); document.execCommand("copy"); ta.remove();
    } catch (e) {}
  }
  function activeLabels(res) { var nA = 0; (((res && res.labels) || [])).forEach(function (L) { if (!L.del) nA++; }); return nA; }

  /* ─── WHY an output is empty ────────────────────────────────────────────────
   *
   * "produced NO values" and "fired 0×" are facts, not diagnoses, and on a big
   * script they arrive twenty at a time. Told only the fact, a user reasonably
   * concludes the conversion is broken — and starts hunting a bug that does not
   * exist. Almost always the truth is one of three things, and the engine already
   * knows which:
   *
   *   1. The output is behind the script's OWN visibility switch, and the switch
   *      is off. `plot(showTrendLine ? line : na)` with showTrendLine=false is not
   *      a failure; it is the script doing exactly what it was told.
   *   2. It depends on strategy.* state (position size, average price). We chart
   *      scripts, we don't fill orders, so that state is always na — expected,
   *      and not something the user can fix in settings.
   *   3. Neither — the condition genuinely never came true on this data.
   *
   * Only (3) is worth a trader's attention. Saying "widen the input, or test
   * another period" for cases (1) and (2) is confidently wrong advice.        */
  // Every name mentioned anywhere in an expression. Deliberately blunt — used only
  // for the HEDGED verdict, never for a definite one.
  function collectNames(node, acc, depth) {
    acc = acc || Object.create(null);
    if (!node || typeof node !== "object" || (depth || 0) > 60) return acc;
    if (node.k === "rawref") return acc;   // holds a LIVE runtime value, not AST — do not walk it
    if (node.k === "name" && typeof node.v === "string") acc[node.v] = 1;
    for (var key in node) {
      if (!Object.prototype.hasOwnProperty.call(node, key)) continue;
      if (key.charAt(0) === "_") continue;   // engine scratch (_syn/_msyn), not source
      var v = node[key];
      if (!v || typeof v !== "object") continue;
      if (Array.isArray(v)) { for (var i = 0; i < v.length; i++) collectNames(v[i], acc, (depth || 0) + 1); }
      else collectNames(v, acc, (depth || 0) + 1);
    }
    return acc;
  }

  /* GATES — the names that, being false, force this whole expression off.
   *
   * POLARITY IS THE WHOLE GAME. A blunt "does a false input appear anywhere in
   * this expression" test gets `plotshape(rare and not disableSignals)` exactly
   * backwards: `disableSignals=false` is what makes the shape ENABLED, and the
   * naive check would cheerfully tell the user to "turn Disable Signals on to see
   * it". A confidently wrong diagnosis is worse than a vague one — it sends people
   * to change the one setting that will make things worse.
   *
   * So we only follow positions where FALSE genuinely kills the output:
   *   · both operands of `and`   (either being false kills it)
   *   · the CONDITION of `x ? … : na`  (false takes the na branch)
   * and we refuse to descend into `not`, `or`, comparisons, or the branches of a
   * ternary, where a false value proves nothing. */
  function gateNames(node, acc, depth) {
    acc = acc || Object.create(null);
    if (!node || typeof node !== "object" || (depth || 0) > 40) return acc;
    if (node.k === "name" && typeof node.v === "string") { acc[node.v] = 1; return acc; }
    if (node.k === "bin" && node.op === "and") {   // parser: {k:"bin", op, l, r}
      gateNames(node.l, acc, (depth || 0) + 1);
      gateNames(node.r, acc, (depth || 0) + 1);
      return acc;
    }
    // A ternary only proves a gate when the FALSE branch is `na` — i.e. the idiom
    // `plot(showX ? value : na)`. `useAlt ? altLine : mainLine` is a value CHOICE:
    // useAlt being false picks a perfectly real line, so blaming it for an empty
    // output would be a confidently wrong claim — and, because a claimed gate is
    // filed under "NOT A FAULT", it would also SUPPRESS the real finding.
    if (node.k === "tern") {                                       // {k:"tern", c, a, b}
      if (node.b && node.b.k === "name" && node.b.v === "na") gateNames(node.c, acc, (depth || 0) + 1);
      return acc;
    }
    return acc;   // anything else: a false value here does not imply "off"
  }

  // Like collectNames, but refuses to enter a `not` subtree. Used for the HEDGED
  // bool check: inside `not disableSignals`, a FALSE value is what ENABLES the
  // output, so naming that input — even as a "maybe" — points the user at the one
  // switch that would make things worse.
  function collectPositive(node, acc, depth) {
    acc = acc || Object.create(null);
    if (!node || typeof node !== "object" || (depth || 0) > 60) return acc;
    if (node.k === "rawref" || node.k === "not") return acc;
    if (node.k === "name" && typeof node.v === "string") acc[node.v] = 1;
    for (var key in node) {
      if (!Object.prototype.hasOwnProperty.call(node, key)) continue;
      if (key.charAt(0) === "_") continue;
      var v = node[key];
      if (!v || typeof v !== "object") continue;
      if (Array.isArray(v)) { for (var i = 0; i < v.length; i++) collectPositive(v[i], acc, (depth || 0) + 1); }
      else collectPositive(v, acc, (depth || 0) + 1);
    }
    return acc;
  }
  // Bool inputs whose CURRENT value is false. Returns the INDEX too, because the
  // index is what lets us stop guessing and actually test the switch.
  function falseBools(prog, names, vals) {
    var off = [];
    for (var i = 0; i < prog.inputs.length; i++) {
      var inp = prog.inputs[i];
      if (!inp.name || !names[inp.name] || inp.type !== "bool") continue;
      var v = (vals && vals[i] != null) ? vals[i] : inp.def;
      if (!Number(v)) off.push({ i: i, title: inp.title || inp.name });
    }
    return off;
  }
  function titlesOf(sw) { var t = []; for (var i = 0; i < sw.length; i++) t.push(sw[i].title); return t; }

  /* ── THE PROBE ────────────────────────────────────────────────────────────
   * Static analysis can tell you a switch FEEDS an output. It cannot reliably
   * tell you that flipping the switch would bring the output back, and the
   * difference matters enormously to whoever is reading the report.
   *
   * `canTrade = enableBacktestLimit ? isWithinBacktest : true` is the trap: the
   * input is genuinely upstream of the signal, and it is genuinely off — so a
   * name-matching check says "turn on Enable Date Limit". But OFF is the
   * permissive state here. Turning it on can only ever remove bars. The advice is
   * not merely unhelpful, it is backwards, and it sends someone to the settings
   * to break something that was fine.
   *
   * We have an interpreter and we have the candles. So flip the switch, re-run,
   * and look. A claim we can test is a claim we should not be guessing about.
   * Bounded: at most a handful of extra runs, inside a wall-clock budget, and a
   * run that overruns throws QC_BUDGET — which we treat as "unknown", never as
   * evidence of absence. */
  function makeProbe(prog, candles, vals, budgetMs) {
    var base = [];
    for (var i = 0; i < prog.inputs.length; i++) base.push((vals && vals[i] != null) ? vals[i] : prog.inputs[i].def);
    var cache = Object.create(null), spent = 0, MAXMS = budgetMs > 0 ? budgetMs : 1500, runs = 0;
    return {
      spent: function () { return Math.round(spent); },
      runs: function () { return runs; },
      // Re-run with these input indices forced ON. null = we could not find out.
      withOn: function (idxs) {
        var key = idxs.join(",");
        if (key in cache) return cache[key];
        var left = MAXMS - spent;
        if (left < 60 || runs >= 8) return (cache[key] = null);
        var v = base.slice();
        for (var q = 0; q < idxs.length; q++) v[idxs[q]] = 1;
        var t0 = nowMs(), r = null;
        try { r = run(prog, candles, v, left); }   // QC_BUDGET (or any throw) → unknown
        catch (e) { r = null; }
        spent += nowMs() - t0; runs++;
        return (cache[key] = r);
      },
    };
  }
  // How much this output produced in a probe run. null = we never found out.
  function yieldOf(r, kind, ix) {
    if (!r) return null;
    if (kind === "p") {
      var a = r.plots[ix] || [], n = 0;
      for (var q = 0; q < a.length; q++) if (a[q] != null) n++;
      return n;
    }
    return (r.shapes[ix] || []).length;
  }
  /* Verdict on a set of candidate switches for ONE empty output:
   *   fixed   — turning these on brings it back (we watched it happen)
   *   nofix   — we turned them on and it stayed empty: the switch is NOT the reason
   *   unknown — we ran out of budget; fall back to the static guess, hedged as such */
  function verifySwitch(probe, sw, kind, ix) {
    if (!probe || !sw.length) return { status: "unknown" };
    var cands = sw.slice(0, 5), capped = sw.length > cands.length, idxs = [], k;
    for (k = 0; k < cands.length; k++) {
      var r1 = probe.withOn([cands[k].i]);
      if (r1 === null) return { status: "unknown" };
      var y1 = yieldOf(r1, kind, ix);
      if (y1 > 0) return { status: "fixed", by: [cands[k]], n: y1 };
      idxs.push(cands[k].i);
    }
    if (idxs.length > 1) {   // maybe it takes more than one switch
      var rAll = probe.withOn(idxs);
      if (rAll === null) return { status: "unknown" };
      var yAll = yieldOf(rAll, kind, ix);
      if (yAll > 0) return { status: "fixed", by: cands, n: yAll };
    }
    return { status: "nofix", tested: cands, capped: capped, total: sw.length };
  }
  /* Follow the script's own variables. `sig = cond and enableX` means a gate on
   * `sig` is really a gate on enableX — scripts are written in layers, and the
   * switch is almost never adjacent to the plot it silences.
   *
   * `walk` decides WHICH names each hop contributes (gateNames for the definite
   * verdict, collectPositive/collectNames for the softer ones). `hops` bounds the
   * chase: the visited set makes cycles impossible, and the depth cap keeps a
   * 1000-line script's dependency graph from being crawled on every report. */
  /* Assignments are not all at the top level: a comma chain (`a = …, b = …`) is
   * stored as one `multi` node, so a flat scan over prog.stmts would step right
   * past `b` and lose the trail. Flatten once, cache on the program. */
  function assignStmts(prog) {
    if (prog.__assigns) return prog.__assigns;
    var flat = [];
    (function walkList(list) {
      for (var i = 0; i < list.length; i++) {
        var s = list[i];
        if (!s) continue;
        if (s.kind === "assign" && s.name) flat.push(s);
        else if (s.kind === "multi" && s.stmts) walkList(s.stmts);
      }
    })(prog.stmts);
    // non-enumerable so the cache can never leak into a serialised program
    try { Object.defineProperty(prog, "__assigns", { value: flat, enumerable: false, writable: true, configurable: true }); }
    catch (e) { try { prog.__assigns = flat; } catch (e2) { return flat; } }
    return flat;
  }
  /* Which assignments of `name` still govern the value an output sees?
   *
   * The last one always does. An earlier one does too — but ONLY if the later
   * assignment carries the old value forward in a GATE position:
   *
   *     sig = enableA and cond
   *     sig := sig and enableB      // old sig is an `and` operand → enableA still gates
   *     sig := sig or forceBull     // old sig is an `or` operand  → enableA gates NOTHING
   *
   * Both re-read `sig`; only the first keeps the earlier gate alive. Reading the
   * self-reference and stopping there would lose enableA (a missed gate, merely
   * hedged); chasing it blindly would resurrect it through the `or` and produce
   * the invented gate this whole path exists to avoid. So chase it exactly as far
   * as the polarity allows, and no further. */
  /* Names written inside a CONDITIONAL block (`if cond` → `sig := true`, a for
   * body, a switch arm). We do not track those assignments, and we cannot: whether
   * they run depends on the data. So a name that any block writes has an escape
   * hatch we cannot see, and it must never carry the DEFINITE "this output IS
   * switched off by X" claim — the block may be the very thing turning it back on.
   * Such names fall to the hedged path, where the probe tests them for real. */
  function blockWritten(prog) {
    if (prog.__blockW) return prog.__blockW;
    var set = Object.create(null);
    function stmts(list) {
      for (var i = 0; i < (list || []).length; i++) {
        var s = list[i];
        if (!s) continue;
        if (s.kind === "assign" && s.name) set[s.name] = 1;
        else if (s.kind === "tuple" && s.names) { for (var t = 0; t < s.names.length; t++) set[s.names[t]] = 1; }
        else if (s.kind === "multi") stmts(s.stmts);
      }
    }
    function scan(list, inBlock) {
      for (var i = 0; i < (list || []).length; i++) {
        var s = list[i];
        if (!s) continue;
        if (inBlock) stmts([s]);
        if (s.kind === "ifblk" && s.branches) { for (var b = 0; b < s.branches.length; b++) scan(s.branches[b].body, true); }
        else if (s.kind === "whileblk" || s.kind === "forblk" || s.kind === "forin") scan(s.body, true);
        else if (s.kind === "switchblk" && s.name) set[s.name] = 1;
        else if (s.kind === "multi") scan(s.stmts, inBlock);
      }
    }
    scan(prog.stmts, false);
    try { Object.defineProperty(prog, "__blockW", { value: set, enumerable: false, writable: true, configurable: true }); }
    catch (e) { try { prog.__blockW = set; } catch (e2) { /* frozen: recompute next time */ } }
    return set;
  }
  function gateChain(prog, name) {
    if (blockWritten(prog)[name]) return [];   // a block can overwrite it — no definite claim
    var cache = prog.__gateChain;
    if (!cache) {
      cache = Object.create(null);   // non-enumerable: a cache must never leak into a serialised program
      try { Object.defineProperty(prog, "__gateChain", { value: cache, enumerable: false, writable: true, configurable: true }); }
      catch (e) { try { prog.__gateChain = cache; } catch (e2) { /* frozen prog: just don't cache */ } }
    }
    if (cache[name]) return cache[name];
    var list = assignStmts(prog), mine = [], i;
    for (i = 0; i < list.length; i++) if (list[i].name === name) mine.push(list[i]);
    var res = [];
    if (mine.length) {
      res.push(mine[mine.length - 1]);
      for (var j = mine.length - 1; j > 0; j--) {
        var g = gateNames(mine[j].expr, Object.create(null), 0);
        if (!g[name]) break;          // this assignment does not gate on its own old value
        res.push(mine[j - 1]);        // …so the previous one's gates survive into it
      }
    }
    return (cache[name] = res);
  }
  /* `gateOnly` matters for the STRICT (gate) walk, and only there.
   *
   *     bull = useFilter and rsi < 30     // gate: useFilter
   *     bull := bull or forceBull         // `or` — no gate at all
   *
   * Unioning both assignments would claim `useFilter` gates the plot. It does not:
   * `forceBull` alone can turn `bull` on, so "this output IS gated by Use Filter"
   * would be a confident falsehood — and it is now the sentence with the strongest
   * wording in the report. Following only the assignment that actually survives to
   * the output means the worst case is a MISSED gate (hedged as a "maybe"), never
   * an invented one. The soft walks keep the union: over-collecting there only
   * widens a guess we already label as a guess. */
  // `gateOnly` is ONLY valid with walk === gateNames: the chain it follows is
  // chosen by gate polarity, so pairing it with a different walk would mix two
  // different questions. The soft walks pass it falsy and keep the full scan.
  function expandVars(prog, names, walk, hops, gateOnly) {
    var out = Object.create(null), frontier = names, k, h, sub, k2;
    var list = assignStmts(prog);
    for (k in names) out[k] = 1;
    for (h = 0; h < (hops || 1); h++) {
      var next = Object.create(null), grew = false;
      if (gateOnly) {
        for (k in frontier) {
          var chain = gateChain(prog, k);
          for (var c = 0; c < chain.length; c++) {
            sub = walk(chain[c].expr, Object.create(null), 0);
            for (k2 in sub) if (!out[k2]) { out[k2] = 1; next[k2] = 1; grew = true; }
          }
        }
      } else {
        for (var i = 0; i < list.length; i++) {
          var st = list[i];
          if (!frontier[st.name]) continue;
          sub = walk(st.expr, Object.create(null), 0);
          for (k2 in sub) if (!out[k2]) { out[k2] = 1; next[k2] = 1; grew = true; }
        }
      }
      if (!grew) break;
      frontier = next;
    }
    return out;
  }
  // strategy.* reads (position_size, position_avg_price, netprofit …) — the
  // backtest state we deliberately do not simulate. Chased further than the other
  // checks (`canTrade = strategy.position_size == 0` is routinely two or three
  // hops from the plot that depends on it), because this is the verdict that runs
  // first and the only bucket the user genuinely cannot fix.
  function strategyReads(prog, expr) {
    var names = expandVars(prog, collectNames(expr, Object.create(null), 0), collectNames, 4);
    var out = [];
    for (var k in names) if (/^strategy\./.test(k)) out.push(k);
    return out;
  }

  /* The verdict, in descending order of confidence. Order matters:
   * backtest-state FIRST, because that bucket is the one the user cannot fix —
   * telling them to flip a switch that will not help is the worst answer here. */
  function emptyReason(prog, expr, vals) {
    var sr = strategyReads(prog, expr);
    if (sr.length) {
      return { kind: "bt", switches: [],
        text: "waits on " + sr.slice(0, 3).join(", ") + " — backtest state. Quant Coder charts scripts, it does not fill orders, so this stays empty on the chart. Expected for a strategy() script; no setting will change it." };
    }
    // DEFINITE: a false switch sits in a position where false forces the output off.
    var strict = falseBools(prog, expandVars(prog, gateNames(expr, Object.create(null), 0), gateNames, 3, true), vals);
    if (strict.length) {
      var ts = titlesOf(strict);
      return { kind: "off", sw: strict, switches: ts,
        text: 'is SWITCHED OFF by its own input — "' + ts.join('", "') + '" ' + (ts.length > 1 ? "are" : "is") + ' off, so this output is na by design. Not a conversion loss: turn it on in ⚙ settings.' };
    }
    // HEDGED: a false switch is involved, but not in a position that proves it.
    // Say "check", not "this is why" — we are guessing, and we admit it.
    var loose = falseBools(prog, expandVars(prog, collectPositive(expr, Object.create(null), 0), collectPositive, 2), vals);
    if (loose.length) {
      var tl = titlesOf(loose);
      return { kind: "maybe", sw: loose, switches: tl,
        text: 'may be switched off — input "' + tl.slice(0, 3).join('", "') + '" ' + (tl.length > 1 ? "are" : "is") + ' currently off and feeds it. Check ⚙ settings before concluding the setup simply never occurred.' };
    }
    return null;
  }
  // the INTELLIGENT part: turn raw run results into named findings
  // `vals` = the input values the run actually used (null → the script's defaults)
  function analyzeRun(prog, res, candles, vals, probe) {
    var finds = [], n2 = candles.length;
    // Explained-empty outputs are collapsed into ONE finding each. A big script
    // can have a dozen switched-off outputs; a dozen near-identical warnings reads
    // like a dozen problems and buries the one finding that is real.
    var offRows = [];    // switched off by an input — VERIFIED: we turned it on and the output came back
    var offGuess = [];   // switched off by an input — static read only, probe budget ran out
    var btRows = [];     // waiting on backtest state we don't simulate
    var ruled = "";      // set when a switch was tested and did NOT restore the output
    var noteEmpty = function (label, expr, kind, ix) {
      ruled = "";
      var why = emptyReason(prog, expr, vals);
      if (!why) return false;
      if (why.kind === "bt") { btRows.push(label); return true; }

      var v = verifySwitch(probe, why.sw || [], kind, ix);
      if (v.status === "fixed") {
        offRows.push(label + ' ← "' + titlesOf(v.by).join('", "') + '" (verified: ' + v.n + (kind === "p" ? " values" : "×") + " when on)");
        return true;
      }
      if (v.status === "nofix") {
        /* The experiment came back negative — but WHAT that proves depends on how
         * the switch was named, and conflating the two would just replace one
         * confident error with another.
         *
         *  · "off"   — the input is a real gate (`sig = cond and enableX`). Turning
         *              it on alone did not restore the output, so it is NECESSARY
         *              BUT NOT SUFFICIENT: something else is also holding it down.
         *              Saying "not the reason" here would be false.
         *  · "maybe" — the input merely appears upstream. Flipping it changed
         *              nothing, so it is not the reason, and the user should not be
         *              sent to the settings at all. This is the `enableBacktestLimit
         *              ? isWithinBacktest : true` case, where OFF is the permissive
         *              state and the old advice was exactly backwards.
         * Either way the output goes back to the real analysis below, with a receipt. */
        var tt = titlesOf(v.tested);
        var nm = '"' + tt.slice(0, 3).join('", "') + '"' + (tt.length > 3 ? " (+" + (tt.length - 3) + " more)" : "");
        ruled = why.kind === "off"
          ? "it IS gated by " + nm + ", which " + (tt.length > 1 ? "are" : "is") + " off — but I turned " + (tt.length > 1 ? "them" : "it") +
            " on and re-ran, and it still produced nothing, so the switch is necessary and NOT sufficient: something else is holding it back too"
          : "I re-ran it with " + nm + " turned ON and it still produced nothing, so " +
            (tt.length > 1 ? "those inputs are" : "that input is") + " NOT the reason (it feeds the output, but its OFF state is not what silenced it)";
        if (v.capped) ruled += " (tested the first " + v.tested.length + " of " + v.total + " candidate switches)";
        return false;
      }
      // unknown — the probe ran out of budget. Fall back to the static read, and
      // say plainly that it is a read and not a measurement.
      if (why.kind === "off") { offGuess.push(label + ' ← "' + why.switches.join('", "') + '"'); return true; }
      finds.push(label + " produced nothing and " + why.text);
      return true;
    };

    for (var p = 0; p < prog.plots.length; p++) {
      var arr = res.plots[p], nn = 0, mn = Infinity, mx = -Infinity, bad = 0;
      for (var i = 0; i < n2; i++) {
        var v = arr[i];
        if (v != null) { nn++; if (!isFinite(v)) bad++; else { if (v < mn) mn = v; if (v > mx) mx = v; } }
      }
      if (!nn) {
        var lblP = 'plot #' + (p + 1) + ' "' + prog.plots[p].title + '"';
        if (!noteEmpty(lblP, prog.plots[p].expr, "p", p)) {
          finds.push(lblP + ' produced NO values — ' + (ruled ? ruled + '. ' : 'no input switch that I can see explains it (I do not track assignments made inside if/for blocks), and it reads no backtest state. ') +
            'Remaining suspects: a numeric input set past what this data reaches, more history than ' + n2 +
            ' bars (a long MA/pivot warm-up), or a construct in the SUBSET SKIPS list.');
        }
      }
      else {
        if (bad) finds.push('plot #' + (p + 1) + ' produced ' + bad + ' non-finite values — division by zero somewhere in the math');
        if (prog.overlay && n2 && isFinite(mn)) {
          var pc = candles[n2 - 1].c || 1;
          if (mx < pc * 0.4 || mn > pc * 1.8) finds.push('plot #' + (p + 1) + ' range [' + mn.toFixed(4) + ' … ' + mx.toFixed(4) + '] is far from price ~' + pc.toFixed(4) + ' while overlay=true — this indicator probably belongs in an oscillator pane (overlay=false in the indicator() header)');
        }
      }
    }
    for (var s = 0; s < prog.shapes.length; s++) {
      if (res.shapes[s].length) continue;
      var spS = prog.shapes[s] || {};
      var nameS = 'shape #' + (s + 1) + (spS.title ? ' "' + spS.title + '"' : ' (signal marker)');
      var naC = (res.shapeNa && res.shapeNa[s]) || 0;
      var faC = (res.shapeFalse && res.shapeFalse[s]) || 0;
      // A shape behind an OFF switch is false on every bar — indistinguishable
      // from "the setup never occurred" unless you look at WHY. Look first.
      if (noteEmpty(nameS, spS.cond, "s", s)) continue;
      var pre = ruled ? ruled + " — " : "";
      // The whole point of the honesty contract: separate "your script said no"
      // from "we never worked out what your script said".
      if (naC && !faC) {
        finds.push(nameS + ' fired 0× — ' + pre + 'its condition was NA on all ' + n2 + ' bars, i.e. it never actually evaluated. Something it depends on was not computed: check the SUBSET SKIPS list for a construct feeding this condition. THIS IS A CONVERSION LOSS, not the script being quiet.');
      } else if (faC && !naC) {
        finds.push(nameS + ' fired 0× — ' + pre + 'its condition evaluated cleanly on all ' + n2 + ' bars and was simply false every time. The conversion is fine; the script\'s own threshold was never reached on this symbol/timeframe. Widen the input, or test a period that actually contains the setup.');
      } else if (faC && naC) {
        finds.push(nameS + ' fired 0× — ' + pre + 'condition false on ' + faC + ' bar(s) and NA on ' + naC + ' (NA usually = a warm-up window, e.g. a long EMA/pivot lookback). Mostly healthy; if the NA count is large, check the SUBSET SKIPS list.');
      } else {
        finds.push(nameS + ' fired 0× on ' + n2 + ' bars — ' + pre + 'its condition never became true here.');
      }
    }
    if (prog.plots.length + prog.shapes.length === 0 && !(res.labels && res.labels.length)
      && !(res.lines && res.lines.length) && !(res.boxes && res.boxes.length))
      finds.push("script declares NO plots/shapes and produced no labels/lines/boxes — its visuals may rely on unsupported table.* objects");
    if (prog.usesLabels && !activeLabels(res))
      finds.push("script creates label.new signals but produced 0 visible labels on " + n2 + " bars — either the signal condition never fired here, or a runtime exception is blocking the signal chain (see RUNTIME EXCEPTIONS)");

    // These two go FIRST and are phrased as reassurance, because that is what they
    // are: the script obeying its own settings. They are listed, not hidden — the
    // user still gets to see exactly which outputs are dark and why.
    if (btRows.length) {
      finds.unshift("NOT A FAULT · " + btRows.length + " output(s) wait on backtest state (strategy.position_size / position_avg_price), which is not simulated — Quant Coder charts scripts, it does not fill orders. These stay empty on the chart and no setting will change that: " +
        btRows.join(" · "));
    }
    if (offGuess.length) {
      finds.unshift("LIKELY NOT A FAULT · " + offGuess.length + " output(s) look SWITCHED OFF by the script's own inputs. I could not re-run to confirm (probe budget spent on a heavy script), so this one is a read of the code, not a measurement: " +
        offGuess.join(" · "));
    }
    if (offRows.length) {
      finds.unshift("NOT A FAULT · " + offRows.length + " output(s) are SWITCHED OFF by the script's own inputs — this is the script doing what it was told, not a conversion loss. Each was re-run with the switch ON and came back. Turn it on in ⚙ settings to see them: " +
        offRows.join(" · "));
    }
    return finds;
  }
  function finishDiag(ov, txt) {
    LAST_DIAG = txt;
    var el = ov.querySelector("#qc-diag-text"); if (el) el.textContent = txt;
  }
  // AUTOMATIC LIVE TEST: fetch the REAL charted market's candles, run the
  // indicator, try an offscreen render, and write findings into the report
  function chartTest(ov, prog, baseTxt) {
    var st = window.dqQoLastChart || {};
    var sym = st.symbol || "BTCUSDT", tf = st.tf || "1m";
    var out = baseTxt + "\n=== LIVE CHART TEST ===\nsymbol " + sym + " · tf " + tf + "\n";
    API("/quantoption/chart?symbol=" + encodeURIComponent(sym) + "&limit=500&tf=" + encodeURIComponent(tf)).then(function (r) {
      var cs = ((r && r.candles) || []).map(function (k) { return { t: (Number(k.time) || 0) * 1000, o: +k.open, h: +k.high, l: +k.low, c: +k.close }; });
      if (!cs.length) { finishDiag(ov, out + "chart data unavailable (market closed / feed budget) — live test skipped\n"); return; }
      out += "bars " + cs.length + "\n";
      var res2 = null;
      // Time the run — the studio's most useful number after "does it work", and
      // the one that decides whether it can live on the chart at all. No budget
      // here: the user is deliberately waiting on a result. But the chart WILL
      // enforce one, so measure now and say so plainly.
      var _ct0 = nowMs();
      try { res2 = run(prog, cs, null); }
      catch (e2) {
        out += "RUNTIME ERROR: " + ((e2 && e2.message) || e2) + "\n" + String((e2 && e2.stack) || "").split("\n").slice(0, 3).join("\n") + "\n";
        finishDiag(ov, out); return;
      }
      var runMs = Math.round(nowMs() - _ct0);
      // The live chart loads 1000 bars, not 500, and re-runs on EVERY new bar.
      var projected = Math.round(runMs * (1000 / Math.max(1, cs.length)));
      out += "run cost: " + runMs + "ms for " + cs.length + " bars · ~" + projected + "ms projected for the 1000-bar live chart" +
        (projected >= FUSE_MS
          ? "  ⛔ RUNAWAY — cannot finish an update within " + Math.round(FUSE_MS / 1000) + "s; the chart will abort and remove it"
          : projected >= HARM_MS
            ? "  ⛔ HARMFUL — freezes the chart ~" + (projected / 1000).toFixed(1) + "s on every new bar; the chart removes it after " + HARM_STRIKES + " such updates in a row"
            : projected >= HEAVY_MS
              ? "  ⚠ HEAVY — it will run, but expect a hitch on each new bar (one indicator at a time)"
              : "  ✓ light") + "\n";
      res2.plots.forEach(function (arr, pi) {
        var nn = 0, last = null;
        for (var q = 0; q < arr.length; q++) if (arr[q] != null) { nn++; last = arr[q]; }
        out += 'plot#' + (pi + 1) + ' "' + prog.plots[pi].title + '": ' + nn + "/" + cs.length + " values" + (last != null ? " · last=" + Number(last).toFixed(6) : "") + "\n";
      });
      res2.shapes.forEach(function (h, si) { out += "shape#" + (si + 1) + ": fired " + h.length + "×" + (h.length ? " (last bars: " + h.slice(-3).join(", ") + ")" : "") + "\n"; });
      (res2.candles || []).forEach(function (arrC, ci2) {
        var painted = 0;
        for (var q6 = 0; q6 < arrC.length; q6++) if (arrC[q6]) painted++;
        out += 'plotcandle#' + (ci2 + 1) + ' "' + ((prog.candles[ci2] && prog.candles[ci2].title) || "Candles") + '": repainted ' + painted + "/" + cs.length + " bars" +
          (painted ? "" : "  (colour is na on every bar — its toggle is off, e.g. “Color Candles”)") + "\n";
      });
      if (res2.labels) out += "labels: " + activeLabels(res2) + " visible (" + res2.labels.length + " created, " + (res2.labels.length - activeLabels(res2)) + " deleted by script)\n";
      if (res2.lines && res2.lines.length) { var lnA = 0; res2.lines.forEach(function (L4) { if (!L4.del) lnA++; }); out += "lines: " + lnA + " visible (" + res2.lines.length + " created)\n"; }
      if (res2.boxes && res2.boxes.length) { var bxA = 0; res2.boxes.forEach(function (B4) { if (!B4.del) bxA++; }); out += "boxes: " + bxA + " visible (" + res2.boxes.length + " created)\n"; }
      if (res2.tables && res2.tables.length) { var tcN = 0; res2.tables.forEach(function (T4) { if (!T4.del) tcN += Object.keys(T4.cells).length; }); out += "tables: " + res2.tables.length + " (" + tcN + " cells)\n"; }
      if (res2.alerts && res2.alerts.length) out += "alerts: " + res2.alerts.length + " fired over history (live-bar fires popup on chart)\n";
      // per-statement exception ledger — the invisible failures, made visible
      var eks = Object.keys(res2.errStats || {});
      if (eks.length) {
        out += "\n--- RUNTIME EXCEPTIONS (by statement) ---\n";
        eks.sort(function (a, b) { return res2.errStats[b].n - res2.errStats[a].n; }).slice(0, 10).forEach(function (k3) {
          out += k3 + ": threw " + res2.errStats[k3].n + "× — " + res2.errStats[k3].msg + "\n";
        });
      }
      // offscreen RENDER pass — catches draw-stage exceptions without the chart
      try {
        var cv = document.createElement("canvas"); cv.width = 320; cv.height = 160;
        var cx2 = cv.getContext("2d");
        var vis = cs.slice(-200), base2 = cs.length - vis.length;
        var lo = Infinity, hi = -Infinity;
        for (var q2 = 0; q2 < vis.length; q2++) { if (vis[q2].l < lo) lo = vis[q2].l; if (vis[q2].h > hi) hi = vis[q2].h; }
        var o2 = {
          ctx: cx2, candles: vis, full: cs, baseIdx: base2,
          area: { padL: 4, padT: 4, plotW: 312, plotH: 152 },
          Y: function (v) { return 156 - ((v - lo) / Math.max(1e-9, hi - lo)) * 152; },
          xAt: function (ii) { return 4 + (312 / vis.length) * (ii + 0.5); },
        };
        drawOne(cx2, o2, prog, res2, prog.overlay ? null : { top: 100, h: 56 }, "diag", null);
        out += "render: OK — no draw-stage exceptions\n";
      } catch (eR) {
        out += "RENDER ERROR: " + ((eR && eR.message) || eR) + "\n" + String((eR && eR.stack) || "").split("\n").slice(0, 3).join("\n") + "\n";
      }
      // if the synthetic self-test was too quiet but LIVE data produced real
      // outputs, the dead-output accuracy cap was wrong — re-score from live
      var liveOut = res2.plots.some(function (arr9) { for (var q9 = 0; q9 < arr9.length; q9++) if (arr9[q9] != null) return true; return false; })
        || res2.shapes.some(function (h9) { return h9.length > 0; })
        || activeLabels(res2) > 0
        || (res2.lines && res2.lines.length > 0) || (res2.boxes && res2.boxes.length > 0);
      if (liveOut && QCSTATE.accBase > QCSTATE.accuracy) {
        QCSTATE.accuracy = QCSTATE.accBase;
        var rw9 = ov.querySelector("#qc-ringwrap");
        if (rw9) rw9.innerHTML = ringHTML(QCSTATE.accBase, TH());
        out += "\n(self-test data was too quiet for this strategy — accuracy re-scored from LIVE results: " + QCSTATE.accBase + "%)\n";
      }
      // null vals = the live test ran on the script's own defaults, which is what
      // emptyReason() must judge the switches against. The probe gets a wall-clock
      // budget scaled to what one run actually costs — a handful of re-runs is a
      // fair price for replacing a guess with a measurement, but a heavy script
      // must not turn "explain this" into a ten-second stall.
      var probeBudget = Math.min(2500, Math.max(500, runMs * 6));
      var probe = makeProbe(prog, cs, null, probeBudget);
      var finds = analyzeRun(prog, res2, cs, null, probe);
      if (probe.runs()) out += "switch probe: " + probe.runs() + " re-run(s), " + probe.spent() +
        "ms — claims marked (verified) below were tested by actually flipping the switch and re-running\n";
      // Cost is a finding, not a footnote. A script that computes perfectly and
      // freezes the phone for 40 seconds every minute is a broken script.
      if (projected >= HARM_MS) {
        finds.unshift("FREEZES THE CHART — ~" + (projected / 1000).toFixed(1) + "s per update on 1000 bars. The chart recomputes the active " +
          "indicator on EVERY new bar, so the app would lock up for that long, once a bar. It is allowed onto the chart, but if it does this " +
          (projected >= FUSE_MS ? "at all it is aborted and removed immediately (it cannot finish inside the " + Math.round(FUSE_MS / 1000) + "s fuse)."
                                : "on " + HARM_STRIKES + " updates in a row it is removed automatically.") +
          " Usual cause: a nested per-bar loop (e.g. a pivot/S-R scan of `for x = 0 to 200+` inside another loop) — cut the lookback, or hoist it out of the bar loop.");
      } else if (projected >= HEAVY_MS) {
        finds.push("heavy: ~" + projected + "ms per update on 1000 bars. It will run and it will not be removed — but it recomputes on every new bar, " +
          "so expect a brief hitch each bar. Only one indicator runs at a time, so this is the whole cost of your chart.");
      }
      // "16 findings" on a script whose only sin is having its own toggles off is a
      // lie told by a counter. The reassurance rows are printed, but they are not
      // FINDINGS — the headline number must mean "things that need your attention".
      var realN = finds.filter(function (f) { return !/^(NOT A FAULT|LIKELY NOT A FAULT)\b/.test(String(f)); }).length;
      out += finds.length
        ? "\n--- AUTO-ANALYSIS: " + realN + " finding(s)" +
            (realN !== finds.length ? " (+" + (finds.length - realN) + " explained, not faults)" : "") + " ---\n" +
            finds.map(function (f) { return "• " + f; }).join("\n") + "\n"
        : "\n--- AUTO-ANALYSIS ---\n• no blocking issues found — outputs computed and rendered. If nothing shows on the chart, toggle the indicator OFF/ON in ⚗ Indicators and hard-refresh.\n";
      finishDiag(ov, out);
    }).catch(function (eF) { finishDiag(ov, out + "chart fetch failed: " + ((eF && eF.message) || eF) + "\n"); });
  }

  /* ════════════════ AI ASSISTANT · ACCURACY · CATEGORIZATION ═══════════════ */
  var QCSTATE = { accuracy: 0, category: null };
  var QC_EXPAND = {};   // library groups the user expanded

  // strategy-type classifier: weighted keyword evidence over the raw source
  var CAT_DEFS = [
    { cat: "Smart Money / Structure", emoji: "🏦", w: 3, re: /\b(bos|choch|order\s*blocks?|fvg|fair\s*value|liquidity|market\s*structure|smc|swing\s*(high|low)|premium|discount|eqh|eql)\b/gi },
    { cat: "Volume", emoji: "🔊", w: 3, re: /\b(volume|obv|vwap|cvd|delta|accumulation|distribution)\b/gi },
    { cat: "Oscillator / Momentum", emoji: "📈", w: 2, re: /\b(rsi|stoch(astic)?|macd|momentum|oscillators?|cci|williams|wpr|mfi|awesome|divergence)\b/gi },
    { cat: "Volatility / Bands", emoji: "🌊", w: 2, re: /\b(bollinger|keltner|donchian|volatility|bands?|channels?|squeeze)\b/gi },
    { cat: "Support / Resistance", emoji: "🧱", w: 2, re: /\b(support|resistance|pivots?|levels?|zones?|fib(onacci)?|breakout)\b/gi },
    { cat: "Trend Following", emoji: "🎯", w: 1, re: /\b(supertrend|trend|ichimoku|adx|psar|parabolic|hull|moving\s*averages?|[sew]ma|vwma|cross(over|under)?)\b/gi },
  ];
  function categorize(src) {
    var best = null, bestScore = 0;
    for (var i = 0; i < CAT_DEFS.length; i++) {
      var m = src.match(CAT_DEFS[i].re);
      var sc = (m ? m.length : 0) * CAT_DEFS[i].w;
      if (sc > bestScore) { bestScore = sc; best = CAT_DEFS[i]; }
    }
    var hasSignals = /\b(buy|sell|signal|entry|exit)\b/i.test(src) && /plotshape|plotchar|plotarrow|label\.new/i.test(src);
    if (!best) best = hasSignals ? { cat: "Signals / Strategy", emoji: "⚡" } : { cat: "General", emoji: "🧩" };
    return { cat: best.cat, emoji: best.emoji, signals: hasSignals };
  }
  // parse ERRORS (broken source) vs subset SKIPS (unsupported constructs)
  function splitWarnings(warnings) {
    var errs = [], skips = [];
    (warnings || []).forEach(function (w) { (/parse error|unexpected token|expected '/i.test(w) ? errs : skips).push(w); });
    return { errs: errs, skips: skips };
  }
  // Collapse the skip list by construct. A strategy with 14 strategy.exit() calls
  // produced 14 near-identical warning lines, which reads like 14 problems and
  // buries the one that matters. Same information, one line per construct.
  function groupSkips(skips) {
    var by = {}, order = [];
    (skips || []).forEach(function (w) {
      var m = /^line (\d+): unsupported call skipped \((.+)\)$/.exec(w);
      if (!m) { if (!by[w]) { by[w] = { name: w, lines: [] }; order.push(w); } return; }
      var k = m[2];
      if (!by[k]) { by[k] = { name: k, lines: [] }; order.push(k); }
      by[k].lines.push(m[1]);
    });
    return order.map(function (k) {
      var g = by[k];
      if (!g.lines.length) return g.name;
      var head = g.lines.slice(0, 6).join(", ") + (g.lines.length > 6 ? ", +" + (g.lines.length - 6) + " more" : "");
      return g.name + "() — " + g.lines.length + " call(s) skipped · line" + (g.lines.length > 1 ? "s " : " ") + head;
    });
  }
  // One plain-English sentence about what skipping the trade engine actually costs.
  function strategyNote(prog) {
    if (!prog || !prog.strategyOrders) return "";
    return "strategy() script: the BACKTEST engine (entry/exit/close — " + prog.strategyOrders +
      " call(s)) is not simulated. Quant Coder charts scripts, it does not fill orders. " +
      "Everything visual — plots, shapes, labels, TP/SL lines, boxes — converts and is unaffected.";
  }
  // animated conversion-accuracy ring with tiered reactions
  function ringHTML(acc, c) {
    var col = acc >= 95 ? "#22c55e" : acc >= 80 ? "#8bd450" : acc >= 60 ? "#f5b942" : "#ef4444";
    var dash = 163.4 - (163.4 * acc / 100);
    var face = acc >= 95 ? "🏆" : acc >= 80 ? "🎯" : acc >= 60 ? "⚙️" : "🧪";
    var msg = acc >= 95 ? "Precision conversion — TradingView-grade fidelity!"
      : acc >= 80 ? "Great conversion — only minor constructs skipped"
      : acc >= 60 ? "Solid core captured — some features degraded"
      : "Rough conversion — let the 🤖 AI Assistant repair it";
    return '<div style="display:flex;align-items:center;gap:14px;padding:12px;border:1px solid ' + c.bd + ';border-radius:12px;background:rgba(16,26,46,.55);margin-top:8px">' +
      '<div style="position:relative;width:64px;height:64px;flex:0 0 auto">' +
        '<svg width="64" height="64" viewBox="0 0 64 64">' +
          '<circle cx="32" cy="32" r="26" fill="none" stroke="rgba(120,140,190,.18)" stroke-width="6"/>' +
          '<circle cx="32" cy="32" r="26" fill="none" stroke="' + col + '" stroke-width="6" stroke-linecap="round" transform="rotate(-90 32 32)" stroke-dasharray="163.4" stroke-dashoffset="163.4" style="animation:qcRing 1.1s cubic-bezier(.22,.8,.3,1) .15s forwards;--qcDash:' + dash.toFixed(1) + '"/></svg>' +
        '<div class="qc-pop" style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:900;color:' + col + '">' + acc + '%</div>' +
      '</div>' +
      '<div><div class="qc-pop" style="font-size:22px">' + face + '</div>' +
      '<div style="font-size:12px;font-weight:700;color:' + c.t1 + '">Conversion accuracy</div>' +
      '<div style="font-size:11.5px;color:' + c.t3 + '">' + msg + '</div></div>' +
    '</div>';
  }
  function confettiBurst(host) {
    var wrap = document.createElement("div");
    wrap.style.cssText = "position:relative;height:0;overflow:visible;pointer-events:none";
    var cols = ["#f5b942", "#22c55e", "#1c84ff", "#a855f7", "#ef4444", "#22d3ee"];
    for (var i = 0; i < 26; i++) {
      var s = document.createElement("span");
      s.style.cssText = "position:absolute;top:0;left:" + (2 + Math.random() * 92).toFixed(1) + "%;width:7px;height:" + (5 + Math.random() * 6).toFixed(0) + "px;border-radius:2px;background:" + cols[i % cols.length] + ";animation:qcConf " + (0.9 + Math.random() * 0.9).toFixed(2) + "s ease-in " + (Math.random() * 0.25).toFixed(2) + "s both";
      wrap.appendChild(s);
    }
    host.insertBefore(wrap, host.firstChild);
    setTimeout(function () { wrap.remove(); }, 2400);
  }

  // fee consent popup → server wallet charge → onPaid()
  function feePopup(kind, onPaid) {
    var c = TH();
    API("/quantoption/indicators/ai/fee").catch(function () { return { fee: 1 }; }).then(function (fr) {
      var fee = (fr && fr.fee != null) ? Number(fr.fee) : 1;
      var old = document.getElementById("qc-fee"); if (old) old.remove();
      var ov = document.createElement("div"); ov.id = "qc-fee";
      ov.style.cssText = "position:fixed;inset:0;z-index:6500;background:rgba(3,6,14,.7);display:flex;align-items:center;justify-content:center";
      ov.innerHTML = '<div class="qc-pop" style="width:min(92vw,400px);background:' + c.panel + ';border:1px solid ' + c.bd + ';border-radius:16px;padding:20px;text-align:center">' +
        '<div style="font-size:42px;animation:qcBob 1.4s ease-in-out infinite;display:inline-block">🤖</div>' +
        '<div style="font-size:15px;font-weight:800;color:' + c.t1 + ';margin:6px 0">AI Assistant</div>' +
        '<div style="font-size:12px;color:' + c.t3 + ';line-height:1.6">Fully automatic: converts your Pine Script, repairs broken statements, and categorizes the indicator by strategy type.</div>' +
        '<div style="margin:12px 0;padding:10px;border-radius:10px;background:rgba(245,185,66,.1);border:1px solid rgba(245,185,66,.35);font-size:12px;color:#f5b942;font-weight:700">' +
          (fee > 0 ? ('Service fee: $' + fee.toFixed(2) + ' — charged from your wallet tokens' + (kind === "bulk" ? '. One fee covers the entire batch.' : '.')) : 'Free during launch 🎉') + '</div>' +
        '<div style="display:flex;gap:10px">' +
          '<button id="qc-fee-no" type="button" style="flex:1;padding:11px 0;border-radius:9px;border:1px solid ' + c.bd + ';background:transparent;color:' + c.t3 + ';font-weight:800;font-size:12px;cursor:pointer;font-family:inherit">Cancel</button>' +
          '<button id="qc-fee-go" type="button" style="flex:2;padding:11px 0;border-radius:9px;border:none;background:linear-gradient(135deg,#7c3aed,#4f46e5);color:#fff;font-weight:800;font-size:13px;cursor:pointer;font-family:inherit">' + (fee > 0 ? 'Pay $' + fee.toFixed(2) + ' & Run 🤖' : 'Run 🤖') + '</button>' +
        '</div></div>';
      document.body.appendChild(ov);
      ov.addEventListener("click", function (e) { if (e.target === ov) ov.remove(); });
      ov.querySelector("#qc-fee-no").onclick = function () { ov.remove(); };
      ov.querySelector("#qc-fee-go").onclick = function () {
        var b = ov.querySelector("#qc-fee-go"); b.disabled = true; b.textContent = "Processing…";
        API("/quantoption/indicators/ai", { method: "POST", body: { kind: kind } })
          .then(function () { ov.remove(); onPaid(); })
          .catch(function (e) {
            ov.remove();
            toast2("Payment failed: " + ((e && e.error && (e.error.message || e.error)) || e.message || "not enough tokens in your wallet"), "error");
          });
      };
    });
  }

  // animated robot progress overlay
  function robotShow(title) {
    var c = TH();
    var old = document.getElementById("qc-robot"); if (old) old.remove();
    var ov = document.createElement("div"); ov.id = "qc-robot";
    ov.style.cssText = "position:fixed;inset:0;z-index:6600;background:rgba(3,6,14,.82);display:flex;align-items:center;justify-content:center";
    ov.innerHTML = '<div class="qc-pop" style="width:min(92vw,430px);background:' + c.panel + ';border:1px solid ' + c.bd + ';border-radius:16px;padding:22px;text-align:center">' +
      '<div id="qc-robot-face" style="font-size:52px;display:inline-block;animation:qcBob 1.2s ease-in-out infinite">🤖</div>' +
      '<div style="position:relative;height:4px;background:rgba(120,140,190,.15);border-radius:3px;margin:14px 10%"><div id="qc-robot-scan" style="position:absolute;top:-2px;left:6%;width:9%;height:8px;border-radius:4px;background:linear-gradient(90deg,#1c84ff,#7c3aed);animation:qcScan 1.3s ease-in-out infinite"></div></div>' +
      '<div style="font-size:13px;font-weight:800;color:' + c.t1 + ';margin-bottom:8px">' + ESC(title) + '</div>' +
      '<div id="qc-robot-log" style="font-size:11px;color:' + c.t3 + ';font-family:ui-monospace,monospace;text-align:left;min-height:72px;max-height:150px;overflow-y:auto;line-height:1.7"></div></div>';
    document.body.appendChild(ov);
    var logEl = ov.querySelector("#qc-robot-log");
    return {
      log: function (m) { var d = document.createElement("div"); d.className = "qc-pop"; d.textContent = "› " + m; logEl.appendChild(d); logEl.scrollTop = 1e9; },
      done: function (m) {
        var f = ov.querySelector("#qc-robot-face");
        if (f) { f.textContent = "🤖✨"; f.style.animation = "qcDone .6s cubic-bezier(.34,1.56,.64,1) both"; }
        var sc = ov.querySelector("#qc-robot-scan"); if (sc) sc.style.display = "none";
        this.log(m || "Done!");
        setTimeout(function () { if (ov.parentNode) ov.remove(); }, 1900);
      },
      close: function () { if (ov.parentNode) ov.remove(); },
    };
  }

  // AUTO-REPAIR: iteratively disable unparseable lines until the source compiles clean
  function aiFix(src) {
    var fixes = [], prog = null;
    for (var pass = 0; pass < 12; pass++) {
      try { prog = compile(src); } catch (e) { return { src: src, prog: null, fixes: fixes, fatal: (e && e.message) || "compile failed" }; }
      var errLines = [];
      splitWarnings(prog.warnings).errs.forEach(function (w) { var m = /^line (\d+):/.exec(w); if (m) errLines.push(+m[1]); });
      if (!errLines.length) break;
      var lines = src.split("\n"), changed = false;
      errLines.forEach(function (ln) {
        var ix = ln - 1;
        if (ix >= 0 && ix < lines.length && !/^\s*\/\//.test(lines[ix])) {
          fixes.push("line " + ln + ": auto-disabled unparseable statement");
          lines[ix] = "// [AI] auto-disabled: " + lines[ix];
          changed = true;
        }
      });
      if (!changed) break;
      src = lines.join("\n");
    }
    return { src: src, prog: prog, fixes: fixes };
  }

  // 🤖 single-script assist: fee → repair → categorize → convert → celebrate
  function aiRun(ov) {
    var srcEl = ov.querySelector("#qc-src");
    var src = srcEl.value;
    if (!src.trim()) { toast2("Paste a Pine Script source first", "error"); return; }
    feePopup("single", function () {
      var R = robotShow("AI Assistant is working…");
      setTimeout(function () {
        R.log("Scanning " + src.split("\n").length + " lines of Pine Script…");
        var fx = aiFix(src);
        R.log(fx.fixes.length ? ("Repaired " + fx.fixes.length + " broken statement(s) 🔧") : "No broken statements — source is clean ✓");
        var cat = categorize(fx.src);
        R.log("Strategy type: " + cat.emoji + " " + cat.cat);
        srcEl.value = fx.src;
        QCSTATE.category = cat;
        var nameEl = ov.querySelector("#qc-name");
        if (!nameEl.value.trim() && fx.prog && fx.prog.title && fx.prog.title !== "Custom") nameEl.value = fx.prog.title.slice(0, 60);
        setTimeout(function () {
          convertNow(ov);
          R.log("Converted, self-tested & scored ✓");
          R.done("All done, boss — review the report and hit Save! 🚀");
        }, 400);
      }, 450);
    });
  }

  // 📁 BULK IMPORT: many .pine/.txt files → repair → categorize → save, one fee
  function bulkImport(ov) {
    feePopup("bulk", function () {
      var inp = document.createElement("input");
      inp.type = "file"; inp.multiple = true; inp.accept = ".pine,.txt,.ps,.pinescript,text/plain";
      inp.onchange = function () {
        var files = Array.prototype.slice.call(inp.files || []);
        if (!files.length) return;
        var R = robotShow("Bulk import — " + files.length + " file(s)");
        var okN = 0, fixN = 0, failN = 0, idx = 0;
        var usedNames = {};
        (LIB.list || []).forEach(function (x) { if (x.mine) usedNames[x.name] = 1; });
        function nextFile() {
          if (idx >= files.length) {
            R.log("Finished: saved " + okN + " · repaired " + fixN + " · failed " + failN);
            R.done("Library updated — " + okN + " indicators categorized and ready! 🚀");
            loadLib(true).then(function () { renderLib(ov); });
            return;
          }
          var f = files[idx++];
          var rd = new FileReader();
          rd.onload = function () {
            try {
              var fx = aiFix(String(rd.result || ""));
              if (fx.fixes.length) fixN++;
              var cat = categorize(fx.src);
              var base = ((fx.prog && fx.prog.title && fx.prog.title !== "Custom") ? fx.prog.title : f.name.replace(/\.\w+$/, "")).trim().slice(0, 52) || "Indicator";
              var nm = base, n2 = 2;
              while (usedNames[nm]) nm = base.slice(0, 46) + " (" + (n2++) + ")";
              usedNames[nm] = 1;
              if (idx === 1 || idx === files.length || idx % 16 === 0) R.log(idx + "/" + files.length + " · " + cat.emoji + " " + nm);
              API("/quantoption/indicators", { method: "POST", body: { name: nm, source: fx.src, config: { inputs: fx.prog ? fx.prog.inputs.map(function (q) { return q.def; }) : [], category: cat.cat, catEmoji: cat.emoji, aiFixed: fx.fixes.length } } })
                .then(function () { okN++; setTimeout(nextFile, 0); })
                .catch(function () { failN++; setTimeout(nextFile, 0); });
            } catch (e) { failN++; setTimeout(nextFile, 0); }
          };
          rd.onerror = function () { failN++; setTimeout(nextFile, 0); };
          rd.readAsText(f);
        }
        nextFile();
      };
      inp.click();
    });
  }

  function saveNow(ov) {
    var name = ov.querySelector("#qc-name").value.trim();
    var src = ov.querySelector("#qc-src").value;
    if (!name) { toast2("Give your indicator a name first", "error"); return; }
    var prog = convertNow(ov);
    if (!prog) return;
    var catS = QCSTATE.category || categorize(src);
    API("/quantoption/indicators", { method: "POST", body: { name: name, source: src, config: { inputs: prog.inputs.map(function (x) { return x.def; }), category: catS.cat, catEmoji: catS.emoji, accuracy: QCSTATE.accuracy } } })
      .then(function () { toast2("Saved to My Indicators"); loadLib(true).then(function () { renderLib(ov); }); })
      .catch(function (e) { toast2("Save failed: " + ((e && e.error && (e.error.message || e.error)) || e.message || ""), "error"); });
  }

  function renderLib(ov) {
    var host = ov.querySelector("#qc-lib"); if (!host) return;
    var c = TH();
    loadLib(true).then(function (list) {
      if (!ov.isConnected) return;
      var isAdmin = false; try { isAdmin = S && S.user && S.user.role === "admin"; } catch (e) {}
      var ids = activeIds();
      // One row = name + actions. The actions WRAP to their own line under the
      // name on a phone; previously they were forced onto one line, which pushed
      // the row wider than the screen and gave the whole studio a sideways scroll.
      var rowHtml = function (x) {
        var on = ids.indexOf(x.id) >= 0;
        var em = (x.config && x.config.catEmoji) || "";
        var tags =
          (x.isPublic ? ' <span style="font-size:9px;font-weight:800;color:' + c.green + ';background:rgba(34,197,94,.14);padding:2px 6px;border-radius:5px;vertical-align:middle">PUBLIC</span>' : '') +
          (x.mine ? '' : ' <span style="font-size:9px;color:' + c.t4 + '">community</span>') +
          (x.config && x.config.aiFixed ? ' <span title="AI-repaired" style="font-size:9px;color:#b79cff">🤖</span>' : '');
        return '<div class="qc-lrow' + (on ? " on" : "") + '">' +
          '<div class="qc-lhead">' +
            '<div class="qc-lname">' + (em ? em + " " : "") + ESC(x.name) + tags + '</div>' +
            '<div class="qc-lacts">' +
              '<button class="qc-tgl qcb sm' + (on ? " on" : "") + '" data-id="' + x.id + '" type="button">' + (on ? "ON CHART" : "ADD") + '</button>' +
              (x.mine ? '<button class="qc-load qcb sm" data-id="' + x.id + '" type="button">Edit</button>' : '') +
              (x.mine ? '<button class="qc-del qcb sm danger" data-id="' + x.id + '" type="button" title="Delete">✕</button>' : '') +
              (isAdmin && x.mine ? '<button class="qc-pub qcb sm gold" data-id="' + x.id + '" data-on="' + (x.isPublic ? 0 : 1) + '" type="button">' + (x.isPublic ? "Unpublish" : "Publish") + '</button>' : '') +
            '</div>' +
          '</div></div>';
      };
      // group the library by strategy category (bulk-import scale: 3000+)
      var groups = {}, gOrder = [];
      list.forEach(function (x) {
        var g = (x.config && x.config.category) || "Uncategorized";
        if (!groups[g]) { groups[g] = []; gOrder.push(g); }
        groups[g].push(x);
      });
      gOrder.sort(function (a, b) { return groups[b].length - groups[a].length; });
      host.innerHTML = list.length ? gOrder.map(function (g) {
        var items = groups[g];
        var em2 = (items[0].config && items[0].config.catEmoji) || "🧩";
        var expanded = QC_EXPAND[g] || items.length <= 60;
        var shown = expanded ? items : items.slice(0, 60);
        return '<div style="font-size:10.5px;letter-spacing:.6px;font-weight:800;color:' + c.t4 + ';margin:10px 0 2px;text-transform:uppercase">' + em2 + ' ' + ESC(g) + ' · ' + items.length + '</div>' +
          shown.map(rowHtml).join("") +
          (!expanded ? '<button class="qc-more qcb sm" data-g="' + ESC(g) + '" type="button" style="width:100%;border-style:dashed">Show all ' + items.length + ' ↓</button>' : '');
      }).join("") : '<div style="color:' + c.t4 + ';font-size:12px">Nothing saved yet — convert something above, then hit Save. Or 📁 bulk-import your whole collection.</div>';
      host.querySelectorAll(".qc-more").forEach(function (b) {
        b.onclick = function () { QC_EXPAND[b.getAttribute("data-g")] = 1; renderLib(ov); };
      });
      host.querySelectorAll(".qc-tgl").forEach(function (b) {
        b.onclick = function () {
          var id = Number(b.getAttribute("data-id"));
          var cur = activeIds();
          var wasOn = cur.indexOf(id) >= 0;
          var prev = cur.length ? (list.filter(function (q) { return q.id === cur[0]; })[0] || null) : null;

          // ONE AT A TIME: adding replaces whatever was there. Say so — an
          // indicator vanishing from the chart with no explanation is exactly the
          // kind of silent behaviour that makes people distrust the tool.
          setActiveIds(wasOn ? [] : [id]);
          renderLib(ov);

          if (wasOn) { toast2("Removed from chart"); return; }

          var nm = (list.filter(function (q) { return q.id === id; })[0] || {}).name || "Indicator";
          if (prev && prev.id !== id) {
            toast2("“" + nm + "” is on the chart. “" + (prev.name || "the previous indicator") +
                   "” was taken off — only one indicator runs at a time.");
          } else {
            toast2("Added to chart — open the Trade tab");
          }
          // Known-heavy from a previous measurement on this device: tell them now,
          // before they wonder why the chart hitches on every bar.
          if (isHeavy(id)) {
            toast2("Heads-up: “" + nm + "” is a heavy indicator (~" + costOf(id) +
                   "ms per update). It will run, but expect a brief hitch on each new bar.", "warn");
            markNoticed(id);   // already said it — don't repeat after the first run
          }
        };
      });
      host.querySelectorAll(".qc-load").forEach(function (b) {
        b.onclick = function () {
          var x = list.filter(function (q) { return q.id === Number(b.getAttribute("data-id")); })[0];
          if (!x) return;
          ov.querySelector("#qc-name").value = x.name;
          ov.querySelector("#qc-src").value = x.source;
          convertNow(ov);
        };
      });
      host.querySelectorAll(".qc-del").forEach(function (b) {
        b.onclick = function () {
          if (!confirm("Delete this indicator?")) return;
          API("/quantoption/indicators/" + b.getAttribute("data-id"), { method: "DELETE" })
            .then(function () { loadLib(true).then(function () { renderLib(ov); }); })
            .catch(function () { toast2("Delete failed", "error"); });
        };
      });
      host.querySelectorAll(".qc-pub").forEach(function (b) {
        b.onclick = function () {
          API("/quantoption/indicators/" + b.getAttribute("data-id") + "/publish", { method: "POST", body: { on: b.getAttribute("data-on") === "1" } })
            .then(function () { loadLib(true).then(function () { renderLib(ov); }); })
            .catch(function () { toast2("Publish failed (admin only)", "error"); });
        };
      });
    }).catch(function () { host.innerHTML = '<div style="color:' + c.red + ';font-size:12px">Could not load the library.</div>'; });
  }

  window.dqQuantCoder = {
    open: open,
    compile: compile,
    run: run,
    drawActive: drawActive,
    loadLib: loadLib,
    activeIds: activeIds,
    setActiveIds: setActiveIds,
    libState: LIB,
    legendHit: legendHit,
    openSettings: openSettings,
    // performance policy, exposed so the chart's own Indicators sheet can label a
    // heavy script BEFORE it is chosen instead of after it misbehaves
    costOf: costOf,
    isHeavy: isHeavy,
    forgetCost: forgetCost,
    limits: function () { return { maxActive: MAX_ACTIVE, heavyMs: HEAVY_MS, harmMs: HARM_MS, harmStrikes: HARM_STRIKES, fuseMs: FUSE_MS }; },
  };
  window.openQuantCoder = open;
})();
