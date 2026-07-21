const APP_CHECK_JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";
const MAX_JWKS_CACHE_SECONDS = 6 * 60 * 60;
const DEFAULT_JWKS_CACHE_SECONDS = 60 * 60;
const MAX_APP_CHECK_TOKEN_LENGTH = 16_384;
const MAX_FUTURE_IAT_SECONDS = 5 * 60;

type AppCheckJwtHeader = {
  alg?: unknown;
  typ?: unknown;
  kid?: unknown;
};

type AppCheckJwtClaims = {
  iss?: unknown;
  aud?: unknown;
  sub?: unknown;
  exp?: unknown;
  iat?: unknown;
  nbf?: unknown;
  [key: string]: unknown;
};

type AppCheckJwk = JsonWebKey & {
  kid?: string;
  alg?: string;
  use?: string;
};

type CachedJwks = {
  keysById: Map<string, AppCheckJwk>;
  expiresAtEpochMs: number;
};

export type VerifiedAppCheckClaims = {
  appId: string;
  claims: AppCheckJwtClaims;
};

export type VerifyFirebaseAppCheckOptions = {
  projectNumber: string;
  allowedAppIds?: ReadonlySet<string>;
  fetchImpl?: typeof fetch;
  nowEpochSeconds?: number;
};

export class AppCheckVerificationError extends Error {
  constructor(message = "Invalid Firebase App Check token") {
    super(message);
    this.name = "AppCheckVerificationError";
  }
}

export class AppCheckJwksUnavailableError extends Error {
  constructor(message = "Firebase App Check JWKS is unavailable") {
    super(message);
    this.name = "AppCheckJwksUnavailableError";
  }
}

let cachedJwks: CachedJwks | null = null;
let jwksRequest: Promise<CachedJwks> | null = null;

export function clearAppCheckJwksCache(): void {
  cachedJwks = null;
  jwksRequest = null;
}

export function parseAllowedAppIds(raw: string | undefined): Set<string> {
  return new Set(
    (raw ?? "")
      .split(/[;,\s]+/)
      .map((value) => value.trim())
      .filter(Boolean),
  );
}

export async function verifyFirebaseAppCheckToken(
  token: string,
  options: VerifyFirebaseAppCheckOptions,
): Promise<VerifiedAppCheckClaims> {
  const projectNumber = options.projectNumber.trim();
  if (!/^\d+$/.test(projectNumber)) {
    throw new AppCheckVerificationError("Invalid Firebase project number configuration");
  }
  if (!token || token.length > MAX_APP_CHECK_TOKEN_LENGTH) {
    throw new AppCheckVerificationError();
  }

  const segments = token.split(".");
  if (segments.length !== 3 || segments.some((segment) => segment.length === 0)) {
    throw new AppCheckVerificationError();
  }

  const [encodedHeader, encodedPayload, encodedSignature] = segments;
  const header = decodeJson<AppCheckJwtHeader>(encodedHeader);
  const claims = decodeJson<AppCheckJwtClaims>(encodedPayload);
  if (header.alg !== "RS256" || header.typ !== "JWT" || typeof header.kid !== "string" || !header.kid) {
    throw new AppCheckVerificationError();
  }

  const fetchImpl = options.fetchImpl ?? fetch;
  const nowEpochSeconds = options.nowEpochSeconds ?? Math.floor(Date.now() / 1000);
  let keys = await loadJwks(fetchImpl, nowEpochSeconds * 1000, false);
  let jwk = keys.keysById.get(header.kid);
  if (!jwk) {
    keys = await loadJwks(fetchImpl, nowEpochSeconds * 1000, true);
    jwk = keys.keysById.get(header.kid);
  }
  if (!jwk) {
    throw new AppCheckVerificationError();
  }

  const verified = await verifySignature(
    jwk,
    `${encodedHeader}.${encodedPayload}`,
    decodeBase64Url(encodedSignature),
  );
  if (!verified) {
    throw new AppCheckVerificationError();
  }

  validateClaims(claims, projectNumber, options.allowedAppIds, nowEpochSeconds);
  return { appId: claims.sub as string, claims };
}

async function loadJwks(
  fetchImpl: typeof fetch,
  nowEpochMs: number,
  forceRefresh: boolean,
): Promise<CachedJwks> {
  if (!forceRefresh && cachedJwks && cachedJwks.expiresAtEpochMs > nowEpochMs) {
    return cachedJwks;
  }
  if (!forceRefresh && jwksRequest) {
    return jwksRequest;
  }

  const request = fetchAndCacheJwks(fetchImpl, nowEpochMs);
  jwksRequest = request;
  try {
    return await request;
  } finally {
    if (jwksRequest === request) jwksRequest = null;
  }
}

async function fetchAndCacheJwks(fetchImpl: typeof fetch, nowEpochMs: number): Promise<CachedJwks> {
  let response: Response;
  try {
    response = await fetchImpl(APP_CHECK_JWKS_URL, {
      headers: { accept: "application/json" },
    });
  } catch {
    throw new AppCheckJwksUnavailableError();
  }
  if (!response.ok) {
    throw new AppCheckJwksUnavailableError(`Firebase App Check JWKS returned HTTP ${response.status}`);
  }

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    throw new AppCheckJwksUnavailableError("Firebase App Check JWKS returned invalid JSON");
  }
  if (!isRecord(payload) || !Array.isArray(payload.keys)) {
    throw new AppCheckJwksUnavailableError("Firebase App Check JWKS payload is invalid");
  }

  const keysById = new Map<string, AppCheckJwk>();
  for (const rawKey of payload.keys) {
    if (!isRecord(rawKey)) continue;
    const key = rawKey as AppCheckJwk;
    if (
      typeof key.kid === "string" &&
      key.kid &&
      key.kty === "RSA" &&
      (key.alg == null || key.alg === "RS256") &&
      (key.use == null || key.use === "sig")
    ) {
      keysById.set(key.kid, key);
    }
  }
  if (keysById.size === 0) {
    throw new AppCheckJwksUnavailableError("Firebase App Check JWKS contains no usable RSA keys");
  }

  const cacheSeconds = parseCacheSeconds(response.headers.get("cache-control"));
  cachedJwks = {
    keysById,
    expiresAtEpochMs: nowEpochMs + cacheSeconds * 1000,
  };
  return cachedJwks;
}

async function verifySignature(
  jwk: AppCheckJwk,
  signingInput: string,
  signature: Uint8Array,
): Promise<boolean> {
  try {
    const key = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    const signatureBuffer = Uint8Array.from(signature).buffer;
    return crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      key,
      signatureBuffer,
      new TextEncoder().encode(signingInput),
    );
  } catch {
    throw new AppCheckVerificationError();
  }
}

function validateClaims(
  claims: AppCheckJwtClaims,
  projectNumber: string,
  allowedAppIds: ReadonlySet<string> | undefined,
  nowEpochSeconds: number,
): void {
  const expectedIssuer = `https://firebaseappcheck.googleapis.com/${projectNumber}`;
  const expectedAudience = `projects/${projectNumber}`;
  const audiences = typeof claims.aud === "string"
    ? [claims.aud]
    : Array.isArray(claims.aud)
      ? claims.aud.filter((value): value is string => typeof value === "string")
      : [];

  if (claims.iss !== expectedIssuer || !audiences.includes(expectedAudience)) {
    throw new AppCheckVerificationError();
  }
  if (typeof claims.exp !== "number" || !Number.isFinite(claims.exp) || claims.exp <= nowEpochSeconds) {
    throw new AppCheckVerificationError();
  }
  if (claims.iat != null && (
    typeof claims.iat !== "number" ||
    !Number.isFinite(claims.iat) ||
    claims.iat > nowEpochSeconds + MAX_FUTURE_IAT_SECONDS
  )) {
    throw new AppCheckVerificationError();
  }
  if (claims.nbf != null && (
    typeof claims.nbf !== "number" ||
    !Number.isFinite(claims.nbf) ||
    claims.nbf > nowEpochSeconds + MAX_FUTURE_IAT_SECONDS
  )) {
    throw new AppCheckVerificationError();
  }
  if (typeof claims.sub !== "string" || !claims.sub.trim()) {
    throw new AppCheckVerificationError();
  }
  if (allowedAppIds && allowedAppIds.size > 0 && !allowedAppIds.has(claims.sub)) {
    throw new AppCheckVerificationError();
  }
}

function decodeJson<T>(encoded: string): T {
  try {
    return JSON.parse(new TextDecoder().decode(decodeBase64Url(encoded))) as T;
  } catch {
    throw new AppCheckVerificationError();
  }
}

function decodeBase64Url(encoded: string): Uint8Array {
  try {
    const normalized = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const binary = atob(padded);
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    throw new AppCheckVerificationError();
  }
}

function parseCacheSeconds(cacheControl: string | null): number {
  const match = cacheControl?.match(/(?:^|,)\s*max-age\s*=\s*(\d+)/i);
  const parsed = match ? Number(match[1]) : DEFAULT_JWKS_CACHE_SECONDS;
  if (!Number.isFinite(parsed) || parsed <= 0) return DEFAULT_JWKS_CACHE_SECONDS;
  return Math.min(Math.floor(parsed), MAX_JWKS_CACHE_SECONDS);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === "object" && !Array.isArray(value);
}
