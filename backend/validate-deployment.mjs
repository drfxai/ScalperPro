// Read-only cloud checks: never invokes an AI provider. Short-lived human session only.
const base = process.env.GATEWAY_URL;
const token = process.env.ACCESS_SESSION_JWT;
if (!base || !token) throw new Error('Set GATEWAY_URL and ACCESS_SESSION_JWT in the shell environment');
const url = new URL(base);
if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('GATEWAY_URL must be an HTTPS origin');
const results = [];
async function probe(path, auth, method='GET', body) {
 const response = await fetch(new URL(path,url), { method, redirect:'manual', signal:AbortSignal.timeout(10000),
 headers:{...(auth?{'cf-access-jwt-assertion':token,'cookie':'CF_Authorization='+token}:{}), ...(body?{'content-type':'application/json'}:{})},
 ...(body?{body:JSON.stringify(body)}:{}) });
 const data = response.headers.get('content-type')?.includes('application/json') ? await response.json() : {};
 return { status:response.status, code:data.code, requestId:data.requestId, retryAfter:response.headers.get('retry-after'), validation:data.validation };
}
const anonymous=await probe('/v1/ai/chat',false,'POST',{message:''});
if (![401,403,302].includes(anonymous.status)) throw new Error('Anonymous route is not protected');
results.push({check:'anonymous access rejected',...anonymous});
for (const mode of (process.env.GATEWAY_MODES || 'gemini').split(',')) {
 const ready=await probe('/ready?mode='+encodeURIComponent(mode),true);
 if (ready.status!==200 || ready.validation!=='CONFIGURATION_ONLY') throw new Error('Authenticated configuration preflight failed for '+mode+': HTTP '+ready.status);
 results.push({check:'configuration '+mode,...ready});
}
let limited;
for(let i=0;i<30;i++) {
 const result=await probe('/v1/ai/chat',true,'POST',{message:''});
 if(result.status===429 && result.code==='RATE_LIMITED' && result.retryAfter==='60') {limited=result;break;}
 if(result.status!==400 || result.code!=='INVALID_MESSAGE') throw new Error('Unexpected quota probe response: HTTP '+result.status);
}
if (!limited) throw new Error('Did not observe Worker quota enforcement at this location');
results.push({check:'per-user chat quota',...limited});
console.log(JSON.stringify({providerExecuted:false,results},null,2));
