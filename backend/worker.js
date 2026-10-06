const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: { "content-type": "application/json; charset=utf-8" }
});

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

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return json({
        ok: true,
        product: "Scalper Pro",
        version: "1.0.0",
        geminiModel: env.GEMINI_MODEL || "gemini-3.8-flash"
      });
    }

    if (request.method !== "POST" || url.pathname !== "/v1/ai/chat") {
      return json({ error: "NOT_FOUND" }, 404);
    }

    let body;
    try { body = await request.json(); }
    catch { return json({ error: "INVALID_JSON" }, 400); }

    if (!body?.message || String(body.message).length > 30000) {
      return json({ error: "INVALID_MESSAGE" }, 400);
    }

    const mode = String(body.mode || "gemini").toLowerCase();

    try {
      if (mode === "9router-smart") return json(await nineRouter(env, body, false));
      if (mode === "9router-combo") return json(await nineRouter(env, body, true));

      try {
        return json(await gemini(env, body));
      } catch (first) {
        const fallback = env.ENABLE_NINEROUTER_FALLBACK === "true";
        const retryable = first?.status === 429 || first?.status === 500 ||
          first?.status === 502 || first?.status === 503 || first?.status === 504;
        if (!fallback || !retryable) throw first;
        return json(await nineRouter(env, body, false));
      }
    } catch (e) {
      return json({
        error: "AI_PROVIDER_ERROR",
        code: e?.message || "UNKNOWN",
        requestId: crypto.randomUUID()
      }, 502);
    }
  }
};
