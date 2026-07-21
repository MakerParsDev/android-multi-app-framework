/**
 * AdMob Server-Side Verification (SSV) callback handler.
 *
 * Flow:
 *   AdMob → GET /ssv?ad_network=...&key_id=...&signature=...&...
 *   Worker verifies ECDSA-SHA256 signature against Google's public keys.
 *   On success → 200 OK (AdMob marks reward as granted server-side).
 *   On failure → 400/403/500 (AdMob retries up to 3×, then skips SSV).
 *
 * Setup after deploy:
 *   1. npx wrangler kv:namespace create SSV_DEDUP
 *   2. Paste KV IDs into wrangler.toml
 *   3. Run the repository quality gate, then `npm run deploy` with SSV_CALLBACK_HEALTH_URL set.
 *   4. Register worker URL in AdMob console → Rewarded / Rewarded Interstitial
 *      → SSV server-side verification URL
 */

const GOOGLE_VERIFIER_KEYS_URL =
  'https://www.gstatic.com/admob/reward/verifier-keys.json';

interface GoogleVerifierKey {
  keyId: number;
  pem: string;
  base64: string;
}

interface GoogleVerifierKeys {
  keys: GoogleVerifierKey[];
}

interface Env {
  SSV_DEDUP: KVNamespace;
  SERVICE_ENVIRONMENT?: string;
  SERVICE_GIT_SHA?: string;
  SERVICE_BUILD_TIMESTAMP?: string;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === '/health') {
      if (request.method !== 'GET') {
        return new Response('Method Not Allowed', { status: 405 });
      }
      return Response.json({
        ok: true,
        service: 'contentapp-ssv-callback',
        environment: env.SERVICE_ENVIRONMENT?.trim() || 'unknown',
        gitSha: env.SERVICE_GIT_SHA?.trim() || 'unknown',
        builtAt: env.SERVICE_BUILD_TIMESTAMP?.trim() || 'unknown',
        timestamp: new Date().toISOString(),
      });
    }
    if (url.pathname !== '/ssv') {
      return new Response('Not Found', { status: 404 });
    }
    if (request.method !== 'GET') {
      return new Response('Method Not Allowed', { status: 405 });
    }

    const params = url.searchParams;

    const keyId = params.get('key_id');
    const signature = params.get('signature');
    const transactionId = params.get('transaction_id');
    const timestamp = params.get('timestamp');

    if (!keyId || !signature || !transactionId || !timestamp) {
      return new Response('Bad Request: missing required params', { status: 400 });
    }

    // Signed content = everything in the query string before &signature=
    // Google guarantees signature is the last parameter.
    const rawQuery = url.search.slice(1);
    const signedContent = rawQuery.split('&signature=')[0];

    let verified = false;
    try {
      verified = await verifySignature(signedContent, signature, parseInt(keyId, 10));
    } catch (e) {
      console.error('SSV verification error:', e);
      return new Response('Internal Server Error', { status: 500 });
    }

    if (!verified) {
      console.warn('SSV invalid signature transaction_id=%s key_id=%s', transactionId, keyId);
      return new Response('Forbidden: invalid signature', { status: 403 });
    }

    // Idempotent dedup: AdMob may retry on timeout; accept duplicate 200s.
    const dedupKey = `txn:${transactionId}`;
    const alreadySeen = await env.SSV_DEDUP.get(dedupKey);
    if (alreadySeen) {
      return new Response('OK', { status: 200 });
    }
    ctx.waitUntil(env.SSV_DEDUP.put(dedupKey, '1', { expirationTtl: 86400 }));

    console.log(
      JSON.stringify({
        event: 'ssv_reward_granted',
        transaction_id: transactionId,
        ad_unit_id: params.get('ad_unit_id'),
        ad_network: params.get('ad_network'),
        reward_item: params.get('reward_item'),
        reward_amount: params.get('reward_amount'),
        custom_data: params.get('custom_data'),
        timestamp,
      }),
    );

    return new Response('OK', { status: 200 });
  },
};

async function verifySignature(
  signedContent: string,
  signatureBase64Url: string,
  keyId: number,
): Promise<boolean> {
  const keysResp = await fetch(GOOGLE_VERIFIER_KEYS_URL, {
    cf: { cacheTtl: 3600, cacheEverything: true },
  });
  if (!keysResp.ok) {
    throw new Error(`Google verifier keys fetch failed: ${keysResp.status}`);
  }
  const keysJson: GoogleVerifierKeys = await keysResp.json();
  const keyEntry = keysJson.keys.find((k) => k.keyId === keyId);
  if (!keyEntry) {
    throw new Error(`key_id ${keyId} not found in Google verifier keys`);
  }

  const publicKey = await importEcPublicKey(keyEntry.pem);
  const sigDerBytes = base64UrlDecode(signatureBase64Url);
  const sigRawBytes = derToRawSignature(sigDerBytes);
  const contentBytes = new TextEncoder().encode(signedContent);

  return crypto.subtle.verify(
    { name: 'ECDSA', hash: { name: 'SHA-256' } },
    publicKey,
    sigRawBytes,
    contentBytes,
  );
}

function derToRawSignature(derBuffer: ArrayBuffer): ArrayBuffer {
  const der = new Uint8Array(derBuffer);
  let offset = 0;
  if (der[offset++] !== 0x30) throw new Error('Expected SEQUENCE');
  
  const len = der[offset++];
  if (len & 0x80) offset += (len & 0x7f); // skip long length form
  
  if (der[offset++] !== 0x02) throw new Error('Expected INTEGER (r)');
  const rLen = der[offset++];
  const rOffset = offset;
  offset += rLen;
  
  if (der[offset++] !== 0x02) throw new Error('Expected INTEGER (s)');
  const sLen = der[offset++];
  const sOffset = offset;
  
  let r = der.slice(rOffset, rOffset + rLen);
  let s = der.slice(sOffset, sOffset + sLen);
  
  // Remove leading zeros
  if (r.length > 32 && r[0] === 0x00) r = r.slice(1);
  if (s.length > 32 && s[0] === 0x00) s = s.slice(1);
  
  // Pad to exactly 32 bytes each
  const raw = new Uint8Array(64);
  raw.set(r, 32 - r.length);
  raw.set(s, 64 - s.length);
  return raw.buffer;
}

async function importEcPublicKey(pem: string): Promise<CryptoKey> {
  const pemBody = pem
    .replace('-----BEGIN PUBLIC KEY-----', '')
    .replace('-----END PUBLIC KEY-----', '')
    .replace(/\s/g, '');
  const derBuffer = base64Decode(pemBody);
  return crypto.subtle.importKey(
    'spki',
    derBuffer,
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['verify'],
  );
}

function base64UrlDecode(b64url: string): ArrayBuffer {
  const b64 = b64url.replace(/-/g, '+').replace(/_/g, '/');
  const padded = b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), '=');
  return base64Decode(padded);
}

function base64Decode(b64: string): ArrayBuffer {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}
