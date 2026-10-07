import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { fetchProvider } from './worker.js';
import { validateProvider } from './security.js';
const pair = await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5', modulusLength:2048, publicExponent:new Uint8Array([1,0,1]), hash:'SHA-256'}, true, ['sign','verify']);
const jwk = {...await crypto.subtle.exportKey('jwk', pair.publicKey), kid:'test-key'};
const env = {ACCESS_TEAM_DOMAIN:'https://test.cloudflareaccess.com', ACCESS_AUD:'a'.repeat(64), GEMINI_MODEL:'explicit-test-model', GEMINI_API_KEY:'test-only', AI_RATE_LIMITER:{limit:async()=>({success:true})}};
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
async function token(overrides={}, header={}) {
 const now = Math.floor(Date.now()/1000);
 const data = encode({alg:'RS256',kid:'test-key',...header})+'.'+encode({iss:env.ACCESS_TEAM_DOMAIN,aud:[env.ACCESS_AUD],sub:'user-1',email:'test@example.com',iat:now,exp:now+60,...overrides});
 return data+'.'+Buffer.from(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,new TextEncoder().encode(data))).toString('base64url');
}
const realFetch = globalThis.fetch;
let providerCalls=0, providerResponse, limiterKeys=[];
globalThis.fetch = async url => {
 if (String(url).endsWith('/cdn-cgi/access/certs')) return Response.json({keys:[jwk]});
 providerCalls++;
 return providerResponse?.() || Response.json({candidates:[{content:{parts:[{text:'Test-only output'}]}}], privateMetadata:'must not return'});
};
async function call({jwt, path='/v1/ai/chat', body={message:'Test request'}, config=env, headers={}}={}) {
 return worker.fetch(new Request('https://gateway.example'+path,{method:'POST',headers:{...(jwt?{'cf-access-jwt-assertion':jwt}:{}),...headers},body:JSON.stringify(body)}),config);
}
 test('authentication and configuration fail closed before provider calls', async()=>{
  providerCalls=0;
  assert.equal((await call()).status,401);
  assert.equal((await call({config:{}})).status,503);
  for(const claims of [{exp:0},{iss:'https://evil.example'},{aud:['wrong']},{nbf:9999999999},{email:undefined},{sub:''}]) assert.equal((await call({jwt:await token(claims)})).status,401);
  assert.equal((await call({jwt:await token({}, {alg:'none'})})).status,401);
  const valid=await token();
  assert.equal((await call({jwt:valid.slice(0,-10)+'AAAAAAAAAA'})).status,401);
  assert.equal(providerCalls,0);
 });
 test('verified user quota, limiter outage, request IDs and sanitized output',async()=>{
  const jwt=await token();
  const config={...env,AI_RATE_LIMITER:{limit:async({key})=>{limiterKeys.push(key);return {success:true};}}};
  const response=await call({jwt,config,headers:{'x-request-id':'test-id'}});
  assert.equal(response.status,200);
  assert.equal(response.headers.get('x-request-id'),'test-id');
  assert.equal(limiterKeys.at(-1),'user-1:/v1/ai/chat');
  assert.deepEqual(Object.keys(await response.json()).sort(),['model','provider','requestId','text']);
  const rejected=await call({jwt,config:{...env,AI_RATE_LIMITER:{limit:async()=>({success:false})}}});
  assert.equal(rejected.status,429); assert.equal(rejected.headers.get('retry-after'),'60');
  assert.equal((await call({jwt,config:{...env,AI_RATE_LIMITER:{limit:async()=>{throw new Error('sensitive');}}}})).status,503);
 });
 test('invalid routes, mode and lab task never execute upstream',async()=>{
  const jwt=await token(); providerCalls=0;
  for(const body of [{message:''},{message:'x',mode:'invalid'}]) assert.equal((await call({jwt,body})).status,400);
  assert.equal((await call({jwt,path:'/v1/ai/lab',body:{message:'x',taskType:'invalid'}})).status,400);
  assert.equal((await call({jwt,path:'/unknown'})).status,404);
  assert.equal(providerCalls,0);
 });
 test('provider preflight refuses absent and unsafe routes',async()=>{
  for(const base of ['http://example.com/v1','https://user:secret@example.com/v1','https://example.com/v1?secret=x']) assert.throws(()=>validateProvider({...env,NINEROUTER_BASE_URL:base,NINEROUTER_API_KEY:'test',NINEROUTER_SMART_MODEL:'smart'},'9router-smart'));
  const response=await call({jwt:await token(),config:{...env,GEMINI_MODEL:''}});
  assert.equal(response.status,503);
 });
 test('empty lab stages, malformed and oversized provider responses fail closed',async()=>{
  const jwt=await token();
  providerCalls=0; providerResponse=()=>Response.json({candidates:[]});
  const response=await call({jwt,path:'/v1/ai/lab'});
  assert.equal(response.status,502); assert.equal(providerCalls,1);
  assert.equal((await response.json()).code,'PROVIDER_EMPTY_RESPONSE');
  providerResponse=()=>new Response('not JSON'); assert.equal((await call({jwt})).status,502);
  providerResponse=()=>new Response('x'.repeat(2000001)); assert.equal((await call({jwt})).status,502);
  providerResponse=()=>{throw new Error('provider secret must not escape');};
  assert.equal((await (await call({jwt})).json()).code,'PROVIDER_UNAVAILABLE');
  providerResponse=undefined;
 });
 test('health is liveness only',async()=>{
  const response=await worker.fetch(new Request('https://gateway.example/health'),{});
  assert.equal(response.status,200); assert.equal((await response.json()).geminiModel,undefined);
 });
 test('readiness checks auth, binding and configuration without provider execution',async()=>{
  providerCalls=0;
  const jwt=await token();
  const request=new Request('https://gateway.example/ready',{headers:{'cf-access-jwt-assertion':jwt}});
  const response=await worker.fetch(request,env);
  assert.equal(response.status,200); assert.equal((await response.json()).validation,'CONFIGURATION_ONLY');
  assert.equal(providerCalls,0);
  assert.equal((await worker.fetch(request,{...env,AI_RATE_LIMITER:undefined})).status,503);
 });
 test('streamed oversized request is 413 and no upstream execution',async()=>{
  providerCalls=0;
  const response=await call({jwt:await token(),body:{message:'x'.repeat(130000)}});
  assert.equal(response.status,413); assert.equal((await response.json()).code,'REQUEST_TOO_LARGE');
  assert.equal(providerCalls,0);
 });
 test('deadline includes response body consumption',async t=>{
  t.mock.timers.enable({apis:['setTimeout']});
  const previous=globalThis.fetch;
  globalThis.fetch=async (url,init)=>new Response(new ReadableStream({start(controller){
    init.signal.addEventListener('abort',()=>controller.error(new DOMException('Aborted','AbortError')));
  }}));
  try {
    const pending=fetchProvider('https://example.com',{});
    await Promise.resolve();
    t.mock.timers.tick(45001);
    await assert.rejects(pending,/PROVIDER_TIMEOUT/);
  } finally {globalThis.fetch=previous;t.mock.timers.reset();}
 });
 test.after(()=>{globalThis.fetch=realFetch;});
