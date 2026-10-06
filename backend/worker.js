const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" }
});

const MAX_MESSAGE = 30000;
const MAX_STAGE_CONTEXT = 18000;

async function gemini(env, body) {
  if (!env.GEMINI_API_KEY) throw new Error("GEMINI_NOT_CONFIGURED");
  const model = env.GEMINI_MODEL || "gemini-3.8-flash";
  const url = "https://generativelanguage.googleapis.com/v1beta/models/" +
    encodeURIComponent(model) + ":generateContent?key=" + encodeURIComponent(env.GEMINI_API_KEY);
  const contents = [{ role: "user", parts: [{ text: String(body.message || "") }] }];
  const res = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ contents })
  });
  const data = await res.json();
  if (!res.ok) {
    const e = new Error("GEMINI_HTTP_" + res.status);
    e.status = res.status;
    e.details = data;
    throw e;
  }
  const text = data?.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("") || "";
  return { provider: "gemini", model, text, raw: data };
}

async function nineRouter(env, body, combo) {
  const base = env.NINEROUTER_BASE_URL;
  const token = env.NINEROUTER_API_KEY;
  const model = combo ? env.NINEROUTER_COMBO_MODEL : env.NINEROUTER_SMART_MODEL;
  if (!base || !token || !model) throw new Error("NINEROUTER_NOT_CONFIGURED");

  const res = await fetch(base.replace(/\/$/, "") + "/chat/completions", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "authorization": "Bearer " + token
    },
    body: JSON.stringify({
      model,
      messages: [{ role: "user", content: String(body.message || "") }]
    })
  });
  const data = await res.json();
  if (!res.ok) {
    const e = new Error("NINEROUTER_HTTP_" + res.status);
    e.status = res.status;
    e.details = data;
    throw e;
  }
  return {
    provider: "9router",
    model,
    text: data?.choices?.[0]?.message?.content || "",
    raw: data
  };
}

async function routedAi(env, body) {
  const mode = String(body.mode || "gemini").toLowerCase();

  if (mode === "9router-smart") {
    return nineRouter(env, body, false);
  }
  if (mode === "9router-combo") {
    return nineRouter(env, body, true);
  }

  try {
    return await gemini(env, body);
  } catch (first) {
    const fallback = env.ENABLE_NINEROUTER_FALLBACK === "true";
    const retryable = first?.status === 429 || first?.status === 500 ||
      first?.status === 502 || first?.status === 503 || first?.status === 504;

    if (!fallback || !retryable) throw first;
    return nineRouter(env, body, false);
  }
}

const SPECIALISTS = {
  requirements: {
    title: "Requirements Analyst",
    instruction: [
      "You are the Requirements Analyst for Scalper Pro.",
      "Turn the beginner's request into explicit build requirements.",
      "Identify instrument/market, timeframe, indicator vs strategy, entries or signals,",
      "visual outputs, alerts, repaint policy, session needs, parameters, and ambiguities.",
      "Do not invent missing trading rules. Mark assumptions clearly.",
      "Return a concise structured specification, not Pine code."
    ].join("\n")
  },
  indicator: {
    title: "Indicator Architect",
    instruction: [
      "You are Scalper Pro's Indicator Architect.",
      "Design a beginner-friendly TradingView indicator from the approved requirements.",
      "Specify inputs, calculations, plots, shapes, alerts, overlay/pane behavior,",
      "timeframe intent and confirmed-bar/repaint policy.",
      "Prefer clear parameters, grouped inputs, tooltips and safe defaults.",
      "Do not generate final Pine code yet."
    ].join("\n")
  },
  strategy: {
    title: "Strategy Strategist",
    instruction: [
      "You are Scalper Pro's Strategy Strategist.",
      "Convert requirements into explicit entry, exit, filter, session, stop, target,",
      "cooldown, long/short and risk assumptions suitable for a Strategy Specification.",
      "Avoid hindsight-only or discretionary wording. Flag rules that cannot be made deterministic.",
      "Do not generate final Pine code yet."
    ].join("\n")
  },
  pine: {
    title: "Pine Engineer",
    instruction: [
      "You are Scalper Pro's senior TradingView Pine Script engineer.",
      "Generate readable Pine Script v6 from the supplied approved design.",
      "Follow current Pine conventions. Avoid future leakage. For genuine higher-timeframe non-repainting requests, use the TradingView-confirmed historical-offset pattern (for example expression[1] with lookahead_on) when appropriate; never ban lookahead_on blindly.",
      "Treat request.security and higher-timeframe confirmation deliberately.",
      "Use explicit input groups/tooltips where useful, deterministic alerts and clear comments.",
      "Do not claim TradingView compilation. Output one complete code block followed by short notes."
    ].join("\n")
  },
  qa: {
    title: "TradingView QA Reviewer",
    instruction: [
      "You are Scalper Pro's TradingView QA reviewer.",
      "Audit the supplied Pine source without rewriting unrelated logic.",
      "Check syntax plausibility, repaint/lookahead risk, request.security semantics,",
      "bar confirmation, persistent var/state reset paths, alert timing, strategy execution assumptions,",
      "runtime-heavy loops and beginner usability.",
      "Separate ERROR, WARNING and INFO findings.",
      "Never say compiled successfully unless an actual TradingView compiler result is supplied."
    ].join("\n")
  },
  mql5: {
    title: "MQL5 Translator",
    instruction: [
      "You are Scalper Pro's MetaTrader 5 / MQL5 engineer.",
      "Translate the approved deterministic strategy semantics into MQL5-oriented code.",
      "Preserve risk, spread/slippage checks, duplicate-entry prevention, sessions, stop/target logic,",
      "and symbol/timeframe assumptions.",
      "Do not claim MetaEditor compilation unless a real compile result is supplied."
    ].join("\n")
  },
  coach: {
    title: "Beginner Coach",
    instruction: [
      "You are Scalper Pro's Beginner Coach.",
      "Explain the finished design/code and the QA findings in simple language.",
      "Tell the user what the tool does, which settings matter, what can repaint,",
      "how to test safely, and what is not verified.",
      "Do not promise profit or certainty."
    ].join("\n")
  }
};

function workflowFor(taskType) {
  switch (String(taskType || "").toLowerCase()) {
    case "strategy_build":
      return ["requirements", "strategy", "pine", "qa", "coach"];
    case "pine_review":
      return ["requirements", "qa", "coach"];
    case "pine_repair":
      return ["qa", "pine", "qa", "coach"];
    case "mql5_translate":
      return ["requirements", "strategy", "mql5", "coach"];
    case "explain":
      return ["coach"];
    case "indicator_build":
    default:
      return ["requirements", "indicator", "pine", "qa", "coach"];
  }
}

function bounded(value, max = MAX_STAGE_CONTEXT) {
  const text = String(value || "");
  return text.length <= max ? text : text.slice(text.length - max);
}

async function runLabWorkflow(env, body) {
  const workflow = workflowFor(body.taskType);
  const originalRequest = String(body.message || "");
  const userContext = bounded(body.context || "", 6000);
  const stages = [];
  let shared = "";

  for (let index = 0; index < workflow.length; index++) {
    const key = workflow[index];
    const specialist = SPECIALISTS[key];

    const prompt = [
      "SCALPER PRO — SPECIALIST WORKFLOW",
      "Stage " + (index + 1) + " of " + workflow.length,
      "Role: " + specialist.title,
      "",
      specialist.instruction,
      "",
      "ORIGINAL USER REQUEST:",
      originalRequest,
      userContext ? "\nAPP CONTEXT:\n" + userContext : "",
      shared ? "\nPRIOR APPROVED/REVIEW OUTPUT:\n" + bounded(shared) : "",
      "",
      "Stay within this specialist role. Be precise and beginner-friendly."
    ].join("\n");

    const result = await routedAi(env, {
      mode: body.mode,
      message: prompt
    });

    const stage = {
      role: key,
      title: specialist.title,
      provider: result.provider,
      model: result.model,
      text: result.text
    };
    stages.push(stage);

    shared = bounded(
      shared +
      "\n\n--- " + specialist.title + " ---\n" +
      result.text
    );
  }

  return {
    taskType: String(body.taskType || "indicator_build"),
    stages,
    final: stages[stages.length - 1]?.text || "",
    providerSequence: stages.map(stage => ({
      role: stage.role,
      provider: stage.provider,
      model: stage.model
    }))
  };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return json({
        ok: true,
        product: "Scalper Pro",
        version: "1.0.0",
        geminiModel: env.GEMINI_MODEL || "gemini-3.8-flash",
        labWorkflow: true
      });
    }

    if (request.method !== "POST") {
      return json({ error: "NOT_FOUND" }, 404);
    }

    if (
      url.pathname !== "/v1/ai/chat" &&
      url.pathname !== "/v1/ai/lab"
    ) {
      return json({ error: "NOT_FOUND" }, 404);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "INVALID_JSON" }, 400);
    }

    if (!body?.message || String(body.message).length > MAX_MESSAGE) {
      return json({ error: "INVALID_MESSAGE" }, 400);
    }

    try {
      if (url.pathname === "/v1/ai/lab") {
        return json(await runLabWorkflow(env, body));
      }

      return json(await routedAi(env, body));
    } catch (e) {
      return json({
        error: "AI_PROVIDER_ERROR",
        code: e?.message || "UNKNOWN",
        requestId: crypto.randomUUID()
      }, 502);
    }
  }
};
