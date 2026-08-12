import assert from "node:assert/strict";
import { afterEach, mock, test } from "node:test";
import { webcrypto } from "node:crypto";

import { verifyAccessRequest } from "../.test-dist/index.js";

const TEAM_DOMAIN = "makerpars.cloudflareaccess.com";
const AUD = "8f6e1c2b9a7d4e3f0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a";
const CERTS_URL = `https://${TEAM_DOMAIN}/cdn-cgi/access/certs`;

function env(overrides = {}) {
  return {
    ADMIN_ACCESS_TEAM_DOMAIN: TEAM_DOMAIN,
    ADMIN_ACCESS_AUD: AUD,
    ...overrides,
  };
}

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
    jwk: { kty: jwk.kty, n: jwk.n, e: jwk.e, kid },
    async sign(payloadOverrides = {}, headerOverrides = {}) {
      const nowSeconds = Math.floor(Date.now() / 1000);
      const header = { alg: "RS256", typ: "JWT", kid, ...headerOverrides };
      const payload = {
        iss: `https://${TEAM_DOMAIN}`,
        aud: [AUD],
        email: "admin@parsfilo.com",
        exp: nowSeconds + 3600,
        iat: nowSeconds - 60,
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

function requestWithToken(token) {
  const headers = new Headers();
  if (token !== undefined) headers.set("Cf-Access-Jwt-Assertion", token);
  return new Request("https://admin-api.parsfilo.com/adminGetRemoteConfig", { headers });
}

function mockCerts(keys, { status = 200 } = {}) {
  let calls = 0;
  mock.method(globalThis, "fetch", async (url) => {
    calls += 1;
    assert.equal(String(url), CERTS_URL);
    return new Response(JSON.stringify({ keys }), {
      status,
      headers: { "content-type": "application/json" },
    });
  });
  return { calls: () => calls };
}

afterEach(() => {
  mock.reset();
});

test("returns null when the Cf-Access-Jwt-Assertion header is missing", async () => {
  const result = await verifyAccessRequest(requestWithToken(undefined), env());
  assert.equal(result, null);
});

test("resolves the email claim for a correctly signed token", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockCerts([signer.jwk]);

  const result = await verifyAccessRequest(requestWithToken(token), env());

  assert.deepEqual(result, { email: "admin@parsfilo.com" });
});

test("rejects a token with a tampered signature", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  const [header, payload, signature] = token.split(".");
  const tamperedPayload = base64Url(JSON.stringify({
    iss: `https://${TEAM_DOMAIN}`,
    aud: [AUD],
    email: "attacker@example.com",
    exp: Math.floor(Date.now() / 1000) + 3600,
  }));
  const tampered = `${header}.${tamperedPayload}.${signature}`;
  mockCerts([signer.jwk]);

  await assert.rejects(() => verifyAccessRequest(requestWithToken(tampered), env()));
});

test("rejects a token whose issuer does not match the configured team domain", async () => {
  const signer = await createSigner();
  const token = await signer.sign({ iss: "https://someone-else.cloudflareaccess.com" });
  mockCerts([signer.jwk]);

  await assert.rejects(
    () => verifyAccessRequest(requestWithToken(token), env()),
    /issuer/,
  );
});

test("rejects a token whose audience does not match ADMIN_ACCESS_AUD", async () => {
  const signer = await createSigner();
  const token = await signer.sign({ aud: ["some-other-audience"] });
  mockCerts([signer.jwk]);

  await assert.rejects(
    () => verifyAccessRequest(requestWithToken(token), env()),
    /audience/,
  );
});

test("accepts a token with a string (non-array) audience claim", async () => {
  const signer = await createSigner();
  const token = await signer.sign({ aud: AUD });
  mockCerts([signer.jwk]);

  const result = await verifyAccessRequest(requestWithToken(token), env());

  assert.deepEqual(result, { email: "admin@parsfilo.com" });
});

test("rejects an expired token", async () => {
  const signer = await createSigner();
  const token = await signer.sign({ exp: Math.floor(Date.now() / 1000) - 60 });
  mockCerts([signer.jwk]);

  await assert.rejects(
    () => verifyAccessRequest(requestWithToken(token), env()),
    /expired/,
  );
});

test("rejects a token missing an exp claim", async () => {
  const signer = await createSigner();
  const token = await signer.sign({ exp: undefined });
  mockCerts([signer.jwk]);

  await assert.rejects(
    () => verifyAccessRequest(requestWithToken(token), env()),
    /expired/,
  );
});

test("rejects a token missing the email claim", async () => {
  const signer = await createSigner();
  const token = await signer.sign({ email: undefined });
  mockCerts([signer.jwk]);

  await assert.rejects(
    () => verifyAccessRequest(requestWithToken(token), env()),
    /email/,
  );
});

test("rejects when the token's kid is not present in the JWKS", async () => {
  const signer = await createSigner("key-1");
  const otherSigner = await createSigner("key-2");
  const token = await signer.sign();
  mockCerts([otherSigner.jwk]);

  await assert.rejects(() => verifyAccessRequest(requestWithToken(token), env()));
});

test("fails closed when the Cloudflare Access certs endpoint is unavailable", async () => {
  const signer = await createSigner();
  const token = await signer.sign();
  mockCerts([signer.jwk], { status: 503 });

  await assert.rejects(() => verifyAccessRequest(requestWithToken(token), env()));
});
