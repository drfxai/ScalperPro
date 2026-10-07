import { authenticate, validateDeployment, validateProvider } from "./security.js";
const json = (data, status = 200, requestId) => new Response(JSON.stringify(data), {
  status,
  headers: {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    ...(requestId ? { "x-request-id": requestId } : {})
  }
});

const MAX_MESSAGE = 30000;
const MAX_STAGE_CONTEXT = 18000;
const MAX_PROVIDER_RESPONSE = 2000000;
const PROVIDER_TIMEOUT_MS = 45000;
const VALID_MODES = new Set(["gemini", "9router-smart", "9router-combo"]);
const VALID_TASK_TYPES = new Set([
  "indicator_build",
  "strategy_build",
  "pine_review",
  "pine_repair",
  "mql5_translate",
  "explain"
]);

async function readJsonLimited(response, maximum = MAX_PROVIDER_RESPONSE) {
  const declaredLength = Number(response.headers.get("content-length") || 0);
  if (declaredLength > maximum) {
    throw new Error("PROVIDER_RESPONSE_TOO_LARGE");
  }

  if (!response.body) return {};
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let size = 0;
  let text = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > maximum) {
      await reader.cancel();
      throw new Error("PROVIDER_RESPONSE_TOO_LARGE");
    }
    text += decoder.decode(value, { stream: true });
  }
  text += decoder.decode();

  try {
    return text ? JSON.parse(text) : {};
  } catch {
    throw new Error("PROVIDER_INVALID_JSON");
  }
}

export async function fetchProvider(url, init) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), PROVIDER_TIMEOUT_MS);
  try {
    const response = await fetch(url, { ...init, redirect: "error", signal: controller.signal });
    const data = await readJsonLimited(response);
    return { ok: response.ok, status: response.status, data };
  } catch (error) {
    if (controller.signal.aborted) throw new Error("PROVIDER_TIMEOUT");
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function gemini(env, body) {
  if (!env.GEMINI_API_KEY) throw new Error("GEMINI_NOT_CONFIGURED");
  const model = env.GEMINI_MODEL;
  const url = "https://generativelanguage.googleapis.com/v1beta/models/" +
    encodeURIComponent(model) + ":generateContent";
  const contents = [{ role: "user", parts: [{ text: String(body.message || "") }] }];
  const res = await fetchProvider(url, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
    body: JSON.stringify({ contents })
  });
  const data = res.data;
  if (!res.ok) {
    const e = new Error("GEMINI_HTTP_" + res.status);
    e.status = res.status;
    e.details = data;
    throw e;
  }
  const text = data?.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("") || "";
  if (!text.trim()) throw new Error("PROVIDER_EMPTY_RESPONSE");
  return { provider: "gemini", model, text };
}

async function nineRouter(env, body, combo) {
  const base = env.NINEROUTER_BASE_URL;
  const token = env.NINEROUTER_API_KEY;
  const model = combo ? env.NINEROUTER_COMBO_MODEL : env.NINEROUTER_SMART_MODEL;
  if (!base || !token || !model) throw new Error("NINEROUTER_NOT_CONFIGURED");

  const res = await fetchProvider(base.replace(/\/$/, "") + "/chat/completions", {
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
  const data = res.data;
  if (!res.ok) {
    const e = new Error("NINEROUTER_HTTP_" + res.status);
    e.status = res.status;
    e.details = data;
    throw e;
  }
  if (typeof data?.choices?.[0]?.message?.content !== "string" || !data.choices[0].message.content.trim()) throw new Error("PROVIDER_EMPTY_RESPONSE");
  return {
    provider: "9router",
    model,
    text: data?.choices?.[0]?.message?.content || ""
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

const handler = {
  async fetch(request, env) {
    const url = new URL(request.url);
    const incomingRequestId = request.headers.get("x-request-id")?.trim();
    const requestId = incomingRequestId && /^[a-zA-Z0-9_-]{1,128}$/.test(incomingRequestId)
      ? incomingRequestId
      : crypto.randomUUID();

    if (url.pathname === "/health") {
      return json({
        ok: true,
        product: "Scalper Pro",
        requestId
      }, 200, requestId);
    }

    if (request.method !== "POST" && !(request.method === "GET" && url.pathname === "/ready")) {
      return json({ error: "NOT_FOUND", requestId }, 404, requestId);
    }

    if (
      url.pathname !== "/v1/ai/chat" &&
      url.pathname !== "/v1/ai/lab" && url.pathname !== "/ready"
    ) {
      return json({ error: "NOT_FOUND", requestId }, 404, requestId);
    }

    let actor;
    try {
      validateDeployment(env);
      actor = await authenticate(request, env);
      const { success } = await env.AI_RATE_LIMITER.limit({ key: actor + ":" + url.pathname });
      if (!success) return json({ error: "RATE_LIMITED", code: "RATE_LIMITED", requestId }, 429, requestId);
    } catch (error) {
      const code = error.message === "UNAUTHORIZED" ? "UNAUTHORIZED" :
        ["AUTH_NOT_CONFIGURED", "AUTH_UNAVAILABLE", "RATE_LIMIT_NOT_CONFIGURED"].includes(error.message) ? error.message : "RATE_LIMIT_UNAVAILABLE";
      return json({ error: code, code, requestId }, code === "UNAUTHORIZED" ? 401 : 503, requestId);
    }

    if (url.pathname === "/ready") {
      const mode = url.searchParams.get("mode") || "gemini";
      if (!VALID_MODES.has(mode)) return json({ error: "INVALID_MODE", requestId }, 400, requestId);
      try { validateProvider(env, mode); } catch {
        return json({ error: "PROVIDER_NOT_CONFIGURED", code: "PROVIDER_NOT_CONFIGURED", requestId }, 503, requestId);
      }
      return json({ ok: true, mode, validation: "CONFIGURATION_ONLY", requestId }, 200, requestId);
    }

    let body;
    try {
      body = await readJsonLimited(request, 128000);
    } catch (error) {
      const code = error.message === "PROVIDER_RESPONSE_TOO_LARGE" ? "REQUEST_TOO_LARGE" : "INVALID_JSON";
      return json({ error: code, code, requestId }, code === "REQUEST_TOO_LARGE" ? 413 : 400, requestId);
    }

    const message = typeof body?.message === "string" ? body.message.trim() : "";
    if (!message || message.length > MAX_MESSAGE) {
      return json({ error: "INVALID_MESSAGE", requestId }, 400, requestId);
    }

    const mode = String(body.mode || "gemini").toLowerCase();
    if (!VALID_MODES.has(mode)) {
      return json({ error: "INVALID_MODE", requestId }, 400, requestId);
    }

    if (
      url.pathname === "/v1/ai/lab" &&
      !VALID_TASK_TYPES.has(String(body.taskType || "indicator_build").toLowerCase())
    ) {
      return json({ error: "INVALID_TASK_TYPE", requestId }, 400, requestId);
    }

    try { validateProvider(env, mode); } catch {
      return json({ error: "PROVIDER_NOT_CONFIGURED", code: "PROVIDER_NOT_CONFIGURED", requestId }, 503, requestId);
    }
    body = { ...body, message, mode };

    try {
      if (url.pathname === "/v1/ai/lab") {
        return json({ ...(await runLabWorkflow(env, body)), requestId }, 200, requestId);
      }

      const result = await routedAi(env, body);
      if (!result.text?.trim()) {
        throw new Error("PROVIDER_EMPTY_RESPONSE");
      }
      return json({ ...result, requestId }, 200, requestId);
    } catch (e) {
      return json({
        error: "AI_PROVIDER_ERROR",
        code: ["PROVIDER_TIMEOUT", "PROVIDER_RESPONSE_TOO_LARGE", "PROVIDER_INVALID_JSON", "PROVIDER_EMPTY_RESPONSE"].includes(e?.message) || /^(GEMINI|NINEROUTER)_HTTP_\d{3}$/.test(e?.message || "") ? e.message : "PROVIDER_UNAVAILABLE",
        requestId
      }, 502, requestId);
    }
  }
};

export default {
  async fetch(request, env) {
    const started = Date.now();
    let response = await handler.fetch(request, env);
    if (response.status === 429) response.headers.set("retry-after", "60");
    if (response.status === 401) response.headers.set("www-authenticate", 'Bearer realm="Scalper Pro Access"');
    let code;
    if (response.status >= 400) {
      const failure = await response.clone().json();
      code = failure.code || failure.error;
      if (!failure.code) {
        response = json({ ...failure, code }, response.status, response.headers.get("x-request-id"));
      }
    }
    console.log(JSON.stringify({ event: "gateway_request", requestId: response.headers.get("x-request-id"),
      route: ["/health", "/ready", "/v1/ai/chat", "/v1/ai/lab"].includes(new URL(request.url).pathname) ? new URL(request.url).pathname : "unknown",
      status: response.status, code, durationMs: Date.now() - started }));
    return response;
  }
};
