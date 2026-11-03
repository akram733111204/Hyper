/** Optional lookup proxy. Only use with authorized SFDA access and approved API contract.
 * This adapter is defensive: the authority's actual response shape/credentials MUST be verified.
 * Production: deploy behind authenticated ingress, TLS, rate limits, monitoring, and managed secrets.
 */
import http from 'node:http';

const PORT = Number(process.env.PORT || 8080);
const SFDA_API_URL = process.env.SFDA_API_URL || '';
const SFDA_API_KEY = process.env.SFDA_API_KEY || '';
const TIMEOUT = 7000;
const windows = new Map();
const clean = (v) => typeof v === 'string' ? v.trim().slice(0, 240) : '';
const read = (obj, keys) => keys.map(k => clean(obj?.[k])).find(Boolean) || '';

function extract(json, barcode) {
  const records = Array.isArray(json) ? json :
    Array.isArray(json?.data) ? json.data :
    Array.isArray(json?.results) ? json.results :
    Array.isArray(json?.items) ? json.items :
    (json?.data && typeof json.data === 'object') ? [json.data] : [];
  const found = records.find(p => String(p?.barcode ?? p?.Barcode ?? '') === barcode);
  if (!found) return null;
  return {
    nameAr: read(found, ['tradeNameAr', 'tradeName', 'itemDescriptionAr']),
    nameEn: read(found, ['tradeNameEn', 'itemDescriptionEn']),
    brand: read(found, ['brandName', 'brandNameAr']),
    size: read(found, ['itemWeight', 'weight']),
    category: 'غذاء',
    sourceUrl: 'https://www.sfda.gov.sa/ar/products'
  };
}

const server = http.createServer(async (req, res) => {
  const send = (code, data) => {
    res.writeHead(code, {'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
    res.end(JSON.stringify(data));
  };
  if (req.method !== 'GET') return send(405, {error:'method not allowed'});
  const match = /^\/v1\/products\/([0-9]{8,14})$/.exec(req.url || '');
  if (!match) return send(404, {error:'not found'});
  const ip = req.socket.remoteAddress || '';
  const now=Date.now();const old=windows.get(ip)||{start:now,count:0};
  const state=(now-old.start>=60_000)?{start:now,count:1}:{start:old.start,count:old.count+1};
  windows.set(ip,state);
  if(windows.size>5000){for(const [k,v] of windows) if(now-v.start>60_000)windows.delete(k)}
  if(state.count>30)return send(429,{error:'rate limited'});
  if(!SFDA_API_KEY || !SFDA_API_URL)return send(503,{error:'Official SFDA connector is not configured'});
  try {
    const origin = new URL(SFDA_API_URL);
    if(origin.protocol!=='https:')throw new Error('SFDA API URL must be HTTPS');
    const target = new URL(origin.href);
    target.searchParams.set('Barcode',match[1]);
    target.searchParams.set('apikey',SFDA_API_KEY);
    const response = await fetch(target,{signal:AbortSignal.timeout(TIMEOUT),headers:{'Accept':'application/json'}});
    if(!response.ok)return send(502,{error:'SFDA gateway rejected request'});
    const text=await response.text();
    if(text.length>500_000)return send(502,{error:'upstream response too large'});
    const product=extract(JSON.parse(text),match[1]);
    return send(product?200:404,{product});
  } catch(err) {
    console.error('Product lookup unavailable:',err?.name || 'error');
    return send(502,{error:'external service unavailable'});
  }
});
server.listen(PORT,'127.0.0.1',()=>console.log(`SFDA adapter listening on 127.0.0.1:${PORT}`));
