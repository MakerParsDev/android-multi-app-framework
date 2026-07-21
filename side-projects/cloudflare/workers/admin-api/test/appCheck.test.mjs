import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { webcrypto } from "node:crypto";

import {
  AppCheckJwksUnavailableError,
  AppCheckVerificationError,
  clearAppCheckJwksCache,
  parseAllowedAppIds,
  verifyFirebaseAppCheckToken,
} from "../.test-dist/appCheck.js";

const PROJECT_NUMBER = "462747480927";
const APP_ID = "1:462747480927:android:bca425acab83705d11bcea";
const JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";

beforeEach(() => clearAppCheckJwksCache());

function base64Url(input) {
  return Buffer.from(input)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

async function createSigner(kid = "key-1") {
  const pair = await webcrypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  );
  const jwk = await webcrypto.subtle.exportKey("jwk", pair.publicKey);
  return {
    kid,
    jwk: { ...jwk, kid, alg: "RS256", use: "sig" },
    async sign(payloadOverrides = {}, headerOverrides = {}) {
      const now = 1_800_000_000;
      const header = { alg: "RS256", typ: "JWT", kid, ...headerOverrides };
      const payload = {
        iss: `https://firebaseappcheck.googleapis.com/${PROJECT_NUMBER}`,
        aud: [`projects/${PROJECT_NUMBER}`],
        sub: APP_ID,
        iat: now - 60,
        exp: now + 3600,
        ...payloadOverrides,
      };
      const encodedHeader = base64Url(JSON.stringify(header));
      const encodedPayload = base64Url(JSON.stringify(payload));
      const signingInput = `${encodedHeader}.${encodedPayload}`;
      const signature = await webcrypto.subtle.sign(
        "RSASSA-PKCS1-v1_5",
        pair.privateKey,
        new TextEncoder().encode(signingInput),
      );
      return `${signingInput}.${base64Url(new Uint8Array(signature))}`;
    },
  };
}

function jwksFetch(keys, { status = 200, cacheControl = "public, max-age=21600" } = {}) {
  let calls = 0;
  const fetchImpl = async (url) => {
    calls += 1;
    assert.equal(String(url), JWKS_URL);
    return new Response(JSON.stringify({ keys }), {
      status,
      headers: { "content-type": "application/json", "cache-control": cacheControl },
    });
  };
  return { fetchImpl, calls: () => calls };
}

async function verify(token, fetchImpl, overrides = {}) {
  return verifyFirebaseAppCheckToken(token, {
    projectNumber: PROJECT_NUMBER,
    allowedAppIds: new Set([APP_ID]),
    fetchImpl,
    nowEpochSeconds: 1_800_000_000,
    ...overrides,
  });
}

test("accepts a correctly signed Firebase App Check token", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  const jwks = jwksFetch([signer.jwk]);

  const claims = await verify(token, jwks.fetchImpl);

  assert.equal(claims.appId, APP_ID);
  assert.equal(jwks.calls(), 1);
});

test("rejects tampered signatures", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  const [header, payload, signature] = token.split(".");
  const tampered = `${header}.${base64Url(JSON.stringify({ sub: APP_ID }))}.${signature}`;
  const jwks = jwksFetch([signer.jwk]);

  await assert.rejects(() => verify(tampered, jwks.fetchImpl), AppCheckVerificationError);
});

test("rejects wrong algorithm and token type", async () => {
  const signer = await createSigner();
  const jwks = jwksFetch([signer.jwk]);

  await assert.rejects(
    async () => verify(await signer.sign({}, { alg: "HS256" }), jwks.fetchImpl),
    AppCheckVerificationError,
  );
  await assert.rejects(
    async () => verify(await signer.sign({}, { typ: "NOT-JWT" }), jwks.fetchImpl),
    AppCheckVerificationError,
  );
});

test("accepts tokens without iat and with a string audience", async () => {
  const signer = await createSigner();
  const token = await signer.sign({ iat: undefined, aud: `projects/${PROJECT_NUMBER}` });
  const jwks = jwksFetch([signer.jwk]);

  const claims = await verify(token, jwks.fetchImpl);

  assert.equal(claims.appId, APP_ID);
});

test("rejects expired, future, wrong issuer, and wrong audience claims", async () => {
  const signer = await createSigner();
  const jwks = jwksFetch([signer.jwk]);
  const cases = [
    { exp: 1_799_999_999 },
    { iat: 1_800_000_301 },
    { iss: "https://firebaseappcheck.googleapis.com/999" },
    { aud: ["projects/999"] },
  ];

  for (const claims of cases) {
    await assert.rejects(
      async () => verify(await signer.sign(claims), jwks.fetchImpl),
      AppCheckVerificationError,
    );
  }
});

test("rejects app IDs outside the allowlist", async () => {
  const signer = await createSigner();
  const token = await signer.sign({ sub: "1:462747480927:android:not-allowed" });
  const jwks = jwksFetch([signer.jwk]);

  await assert.rejects(() => verify(token, jwks.fetchImpl), AppCheckVerificationError);
});

test("caches JWKS and refreshes once when a new kid appears", async () => {
  const oldSigner = await createSigner("old-key");
  const newSigner = await createSigner("new-key");
  const responses = [
    { keys: [oldSigner.jwk] },
    { keys: [oldSigner.jwk, newSigner.jwk] },
  ];
  let calls = 0;
  const fetchImpl = async () => {
    const body = responses[Math.min(calls, responses.length - 1)];
    calls += 1;
    return new Response(JSON.stringify(body), {
      headers: { "content-type": "application/json", "cache-control": "max-age=21600" },
    });
  };

  await verify(await oldSigner.sign(), fetchImpl);
  await verify(await oldSigner.sign(), fetchImpl);
  await verify(await newSigner.sign(), fetchImpl);

  assert.equal(calls, 2);
});

test("fails closed when JWKS cannot be fetched", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  const jwks = jwksFetch([], { status: 503 });

  await assert.rejects(() => verify(token, jwks.fetchImpl), AppCheckJwksUnavailableError);
});

test("parses and deduplicates configured app IDs", () => {
  assert.deepEqual(
    [...parseAllowedAppIds(` ${APP_ID},${APP_ID};other-app\nthird-app `)],
    [APP_ID, "other-app", "third-app"],
  );
});
