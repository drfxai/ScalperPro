const fail = code => { throw new Error(code); };
export function validateDeployment(env) {
  if (!/^https:\/\/[a-z0-9-]+\.cloudflareaccess\.com$/.test(env.ACCESS_TEAM_DOMAIN || '') ||
      !/^[a-f0-9]{64}$/.test(env.ACCESS_AUD || '')) fail('AUTH_NOT_CONFIGURED');
  if (!env.AI_RATE_LIMITER?.limit) fail('RATE_LIMIT_NOT_CONFIGURED');
}
export function validateProvider(env, mode) {
  const nine = mode !== 'gemini' || env.ENABLE_NINEROUTER_FALLBACK === 'true';
  if (mode === 'gemini' && (!env.GEMINI_API_KEY || !/^[a-zA-Z0-9._-]{1,120}$/.test(env.GEMINI_MODEL || ''))) fail('PROVIDER_NOT_CONFIGURED');
  if (nine) {
    let url;
    try { url = new URL(env.NINEROUTER_BASE_URL); } catch { fail('PROVIDER_NOT_CONFIGURED'); }
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash ||
        !env.NINEROUTER_API_KEY || !(mode === '9router-combo' ? env.NINEROUTER_COMBO_MODEL : env.NINEROUTER_SMART_MODEL)) fail('PROVIDER_NOT_CONFIGURED');
  }
}
function decode(part) {
  if (!/^[A-Za-z0-9_-]+$/.test(part)) fail('UNAUTHORIZED');
  return Uint8Array.from(atob(part.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
}
let keyCache;
export async function authenticate(request, env) {
  const token = request.headers.get('cf-access-jwt-assertion');
  if (!token || token.length > 16000) fail('UNAUTHORIZED');
  let header, claims, parts;
  try {
    parts = token.split('.');
    if (parts.length !== 3) fail('UNAUTHORIZED');
    header = JSON.parse(new TextDecoder().decode(decode(parts[0])));
    claims = JSON.parse(new TextDecoder().decode(decode(parts[1])));
  } catch { fail('UNAUTHORIZED'); }
  const now = Math.floor(Date.now() / 1000);
  if (header.alg !== 'RS256' || typeof header.kid !== 'string' ||
      claims.iss !== env.ACCESS_TEAM_DOMAIN || !Array.isArray(claims.aud) || !claims.aud.includes(env.ACCESS_AUD) ||
      !Number.isFinite(claims.exp) || claims.exp <= now ||
      !Number.isFinite(claims.iat) || claims.iat > now + 30 ||
      (claims.nbf !== undefined && (!Number.isFinite(claims.nbf) || claims.nbf > now + 30)) ||
      typeof claims.sub !== 'string' || !claims.sub || claims.sub.length > 256 ||
      typeof claims.email !== 'string' || !claims.email) fail('UNAUTHORIZED');
  const issuer = env.ACCESS_TEAM_DOMAIN;
  if (!keyCache || keyCache.issuer !== issuer || keyCache.expires <= Date.now()) {
    try {
      const response = await fetch(issuer + '/cdn-cgi/access/certs', {
        signal: AbortSignal.timeout(5000), redirect: 'error'
      });
      if (!response.ok) fail('AUTH_UNAVAILABLE');
      // Bound key discovery; never use JWT-supplied URLs or certificates.
      const reader = response.body.getReader();
      let size = 0, text = '', decoder = new TextDecoder();
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > 64000) { await reader.cancel(); fail('AUTH_UNAVAILABLE'); }
        text += decoder.decode(value, { stream: true });
      }
      const data = JSON.parse(text + decoder.decode());
      if (!Array.isArray(data.keys) || data.keys.length > 20) fail('AUTH_UNAVAILABLE');
      keyCache = { issuer, expires: Date.now() + 60000, keys: data.keys };
    } catch { fail('AUTH_UNAVAILABLE'); }
  }
  const jwk = keyCache.keys.find(k => k.kid === header.kid && k.kty === 'RSA' && (!k.alg || k.alg === 'RS256'));
  if (!jwk) fail('UNAUTHORIZED');
  try {
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    if (!await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, decode(parts[2]), new TextEncoder().encode(parts[0] + '.' + parts[1]))) fail('UNAUTHORIZED');
  } catch { fail('UNAUTHORIZED'); }
  return claims.sub;
}
