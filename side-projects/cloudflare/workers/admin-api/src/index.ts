import {
  AppCheckJwksUnavailableError,
  AppCheckVerificationError,
  parseAllowedAppIds,
  verifyFirebaseAppCheckToken,
} from "./appCheck";
import {
  DeviceRegistrationDependencyUnavailableError,
  handleDeviceRegistration,
  type DeviceRegistrationRecord,
} from "./deviceRegistration";
import {
  handleHealthCheck,
  type WorkerVersionMetadata,
} from "./health";

export interface Env {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_PROJECT_NUMBER: string;
  FIREBASE_WEB_API_KEY: string;
  FIREBASE_APP_CHECK_ALLOWED_APP_IDS: string;
  ADMIN_ALLOWED_EMAILS?: string;
  ALLOWED_ADMIN_ORIGINS?: string;
  FIREBASE_SERVICE_ACCOUNT_JSON?: string;
  VERIFY_PURCHASE_REQUIRE_APP_CHECK?: string;
  REGISTER_DEVICE_REQUIRE_APP_CHECK?: string;
  ADMOB_CLIENT_ID?: string;
  ADMOB_CLIENT_SECRET?: string;
  ADMOB_REFRESH_TOKEN?: string;
  ADMOB_PUBLISHER_ID?: string;
  AD_HEALTH_MIN_REQUESTS?: string;
  AD_HEALTH_FILL_RATE_THRESHOLD?: string;
  AD_HEALTH_SHOW_RATE_THRESHOLD?: string;
  SERVICE_ENVIRONMENT?: string;
  SERVICE_GIT_SHA?: string;
  SERVICE_BUILD_TIMESTAMP?: string;
  CF_VERSION_METADATA?: WorkerVersionMetadata;
}

type FirebaseLookupUser = {
  localId: string;
  email?: string;
};

type GoogleServiceAccount = {
  client_email: string;
  private_key: string;
  token_uri?: string;
};

type CachedToken = {
  accessToken: string;
  expiresAtEpochMs: number;
};

type PurchaseType = "subs" | "inapp";

type VerifyPurchaseResponse = {
  verified: boolean;
  purchaseState: string;
  acknowledgementState: string;
  expiryTimeMillis: number | null;
  autoRenewing: boolean;
};

type VerifyPurchasePayload = {
  packageName: string;
  productId: string;
  purchaseToken: string;
  purchaseType: PurchaseType;
};

type FirestoreField = {
  nullValue?: null;
  booleanValue?: boolean;
  integerValue?: string;
  doubleValue?: number;
  timestampValue?: string;
  stringValue?: string;
  mapValue?: { fields?: Record<string, FirestoreField> };
  arrayValue?: { values?: FirestoreField[] };
};

type FirestoreDocument = {
  name?: string;
  fields?: Record<string, FirestoreField>;
};

type ResolvedAdmin = {
  uid: string;
  email?: string;
  authorized: boolean;
  source: "firestore" | "allowlist" | "none";
};

type DeviceCoverageItem = {
  packageName: string;
  activeDeviceCount: number;
  totalDeviceCount: number;
};

type DeviceRecord = {
  fcmToken?: string;
  locale?: string;
  packageName?: string;
  notificationsEnabled?: boolean;
  updatedAtMs: number;
};

type TestPushInput = {
  token?: string;
  installationId?: string;
  title?: string;
  body?: string;
  data?: Record<string, string>;
  useNotificationPayload?: boolean;
};

type RemoteConfigParameter = {
  defaultValue?: { value?: string };
  description?: string;
  valueType?: string;
};

type RemoteConfigTemplate = {
  parameters?: Record<string, RemoteConfigParameter>;
  parameterGroups?: Record<string, {
    description?: string;
    parameters?: Record<string, RemoteConfigParameter>;
  }>;
  conditions?: unknown[];
  version?: unknown;
};

type AdMobConfig = {
  clientId: string;
  clientSecret: string;
  refreshToken: string;
  preferredAccountName?: string;
};

type AdHealthCatalogEntry = {
  packageName: string;
  appName: string;
};

type NetworkRow = {
  appId: string;
  appLabel: string;
  format: string;
  earningsMicros: number;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
};

type VersionedNetworkRow = {
  appId: string;
  appLabel: string;
  versionName: string;
  format: string;
  earningsMicros: number;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
};

type AggregatedStat = {
  appId: string;
  appLabel: string;
  format: string;
  earningsMicros: number;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
};

type ReportAlert = {
  appId: string;
  appLabel: string;
  format: string;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
  fillRatePct: number;
  showRatePct: number;
  earningsTry: number;
  ecpmTry: number;
  reasons: string[];
};

type ReportStat = {
  appId: string;
  appLabel: string;
  format: string;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
  earningsTry: number;
  ecpmTry: number;
};

type AdTotals = {
  earningsTry: number;
  ecpmTry: number;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
  fillRatePct: number;
  showRatePct: number;
};

type AdFormatBreakdown = {
  format: string;
  earningsTry: number;
  ecpmTry: number;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
  fillRatePct: number;
  showRatePct: number;
};

type WeeklyReportPayload = {
  generatedAt: string;
  rangeStart: string;
  rangeEnd: string;
  source: "admob_api";
  status: "ok" | "misconfigured" | "error";
  thresholds: {
    minRequests: number;
    fillRatePct: number;
    showRatePct: number;
  };
  totals: AdTotals;
  formatBreakdown?: AdFormatBreakdown[];
  diagnosticReasonCounts?: Record<string, number>;
  stats?: ReportStat[];
  alerts: ReportAlert[];
  issue?: string;
  today?: TodayReportPayload;
};

type TodayReportPayload = {
  generatedAt: string;
  date: string;
  source: "admob_api";
  status: "ok" | "misconfigured" | "error";
  totals: AdTotals;
  formatBreakdown?: AdFormatBreakdown[];
  issue?: string;
};

type TodayLatestAppStat = {
  packageName: string;
  appLabel: string;
  liveVersionName: string;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
  earningsTry: number;
  ecpmTry: number;
  fillRatePct: number;
  showRatePct: number;
};

type TodayLatestReportPayload = {
  generatedAt: string;
  date: string;
  source: "admob_api";
  status: "ok" | "misconfigured" | "error";
  totals: AdTotals;
  liveVersionCount: number;
  filteredLegacyRows: number;
  unmappedRows: number;
  formatBreakdown?: AdFormatBreakdown[];
  apps: TodayLatestAppStat[];
  issue?: string;
};

type ScheduledEvent = {
  id: string;
  type: string;
  name: string;
  date?: string;
  recurrence?: string;
  localDeliveryTime: string;
  targetTimezones: string[];
  topic?: string;
  packages: string[];
  title: Record<string, string>;
  body: Record<string, string>;
  status: string;
  sentTimezones: string[];
  lastResetAtMs?: number;
};

const DEFAULT_ALLOWED_ORIGINS = [
  "https://admin.parsfilo.com",
  "https://parsfilo-admin.pages.dev",
  "http://localhost:5173",
  "http://localhost:4173",
];

const DEFAULT_PACKAGES = [
  "com.parsfilo.amenerrasulu",
  "com.parsfilo.ayetelkursi",
  "com.parsfilo.bereketduasi",
  "com.parsfilo.esmaulhusna",
  "com.parsfilo.fetihsuresi",
  "com.parsfilo.insirahsuresi",
  "com.parsfilo.ismiazamduasi",
  "com.parsfilo.kenzularsduasi",
  "com.parsfilo.kuran_kerim",
  "com.parsfilo.kible",
  "com.parsfilo.mucizedualar",
  "com.parsfilo.namazvakitleri",
  "com.parsfilo.namazsurelerivedualarsesli",
  "com.parsfilo.vakiasuresi",
  "com.parsfilo.nazarayeti",
  "com.parsfilo.yasinsuresi",
  "com.parsfilo.zikirmatik",
];

const FIREBASE_LOOKUP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:lookup";
const GOOGLE_OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
const GOOGLE_CLOUD_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
const GOOGLE_PLAY_ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const REMOTE_CONFIG_API_BASE = "https://firebaseremoteconfig.googleapis.com/v1";
const ADMOB_API_BASE = "https://admob.googleapis.com/v1";

const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_MAX_REQUESTS = 90;
const COVERAGE_DEFAULT_DAYS = 14;
const COVERAGE_MIN_DAYS = 1;
const COVERAGE_MAX_DAYS = 90;

const TOKEN_REGEX = /^[A-Za-z0-9:._-]{80,4096}$/;
const INSTALLATION_ID_REGEX = /^[A-Za-z0-9._:-]{8,200}$/;
const MAX_TITLE_LENGTH = 120;
const MAX_BODY_LENGTH = 500;
const MAX_DATA_ENTRIES = 20;
const MAX_DATA_KEY_LENGTH = 64;
const MAX_DATA_VALUE_LENGTH = 256;
const FCM_BATCH_SIZE = 500;
const PURCHASE_TOKEN_MAX_LENGTH = 2048;
const PRODUCT_ID_MAX_LENGTH = 256;
const PACKAGE_NAME_REGEX = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;

const REPORTS_COLLECTION = "ad_performance_reports";
const LATEST_REPORT_DOC_ID = "latest";
const HISTORY_DOC_PREFIX = "weekly_";
const DEFAULT_MIN_REQUESTS = 500;
const DEFAULT_FILL_THRESHOLD = 55;
const DEFAULT_SHOW_THRESHOLD = 20;

const SCHEDULE_CRON_DISPATCH_NOTIFICATIONS = "0 * * * *";
const SCHEDULE_CRON_WEEKLY_AD_REPORT = "15 7 * * 1";
const DELIVERY_WINDOW_MINUTES = 60;
const FIRESTORE_IN_QUERY_LIMIT = 10;

const ALL_TIMEZONES = [
  "Pacific/Pago_Pago",
  "Pacific/Honolulu",
  "America/Anchorage",
  "America/Los_Angeles",
  "America/Denver",
  "America/Chicago",
  "America/New_York",
  "America/Halifax",
  "America/Sao_Paulo",
  "Atlantic/South_Georgia",
  "Atlantic/Azores",
  "Europe/London",
  "Europe/Berlin",
  "Europe/Istanbul",
  "Asia/Dubai",
  "Asia/Karachi",
  "Asia/Dhaka",
  "Asia/Bangkok",
  "Asia/Shanghai",
  "Asia/Tokyo",
  "Australia/Sydney",
  "Pacific/Auckland",
  "Asia/Kolkata",
  "Asia/Riyadh",
  "Africa/Cairo",
  "Europe/Moscow",
  "Asia/Kuala_Lumpur",
  "Asia/Jakarta",
];

const rateMap = new Map<string, { count: number; windowStart: number }>();
const cachedGoogleTokens = new Map<string, CachedToken>();

function jsonResponse(
  payload: unknown,
  status = 200,
  extraHeaders: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...extraHeaders,
    },
  });
}

function normalizeOriginPatterns(raw: string | undefined): string[] {
  if (!raw) return DEFAULT_ALLOWED_ORIGINS;
  const patterns = raw
    .split(/[,\n;]+/g)
    .map((item) => item.trim())
    .filter(Boolean);
  return patterns.length > 0 ? patterns : DEFAULT_ALLOWED_ORIGINS;
}

function matchesOriginPattern(origin: string, pattern: string): boolean {
  if (!pattern.includes("*")) {
    return origin === pattern;
  }
  if (pattern.startsWith("https://*.")) {
    const suffix = pattern.slice("https://*".length);
    return origin.startsWith("https://") && origin.endsWith(suffix);
  }
  if (pattern.startsWith("http://*.")) {
    const suffix = pattern.slice("http://*".length);
    return origin.startsWith("http://") && origin.endsWith(suffix);
  }
  return false;
}

function isAllowedOrigin(origin: string | null, env: Env): boolean {
  if (!origin) return false;
  const patterns = normalizeOriginPatterns(env.ALLOWED_ADMIN_ORIGINS);
  return patterns.some((pattern) => matchesOriginPattern(origin, pattern));
}

function withCors(headers: Record<string, string>, origin: string | null, env: Env): Record<string, string> {
  if (!origin || !isAllowedOrigin(origin, env)) return headers;
  return {
    ...headers,
    "access-control-allow-origin": origin,
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type, authorization",
    "access-control-max-age": "86400",
    "vary": "Origin",
  };
}

function handlePreflight(request: Request, env: Env): Response | null {
  if (request.method !== "OPTIONS") return null;
  const origin = request.headers.get("origin");
  if (!isAllowedOrigin(origin, env)) {
    return jsonResponse({ error: "Origin not allowed" }, 403);
  }
  return new Response(null, {
    status: 204,
    headers: withCors({}, origin, env),
  });
}

function pruneRateMap(now: number): void {
  for (const [key, value] of rateMap.entries()) {
    if (now - value.windowStart > RATE_LIMIT_WINDOW_MS * 2) {
      rateMap.delete(key);
    }
  }
}

function isRateLimited(ip: string): boolean {
  const now = Date.now();
  pruneRateMap(now);
  const entry = rateMap.get(ip);
  if (!entry || now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
    rateMap.set(ip, { count: 1, windowStart: now });
    return false;
  }
  entry.count += 1;
  return entry.count > RATE_LIMIT_MAX_REQUESTS;
}

function parseBearerToken(request: Request): string | null {
  const auth = request.headers.get("authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return null;
  const token = auth.slice("Bearer ".length).trim();
  return token.length > 0 ? token : null;
}

function looksLikeJson(contentTypeHeader: string | null): boolean {
  if (!contentTypeHeader) return false;
  return contentTypeHeader.toLowerCase().includes("application/json");
}

async function verifyFirebaseIdToken(idToken: string, apiKey: string): Promise<FirebaseLookupUser> {
  const response = await fetch(`${FIREBASE_LOOKUP_URL}?key=${encodeURIComponent(apiKey)}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ idToken }),
  });

  if (!response.ok) {
    throw new Error("Invalid Firebase Auth token");
  }

  const payload = (await response.json()) as { users?: Array<{ localId?: string; email?: string }> };
  const user = payload.users?.[0];
  const localId = user?.localId?.trim();
  if (!localId) {
    throw new Error("Missing Firebase user id in token payload");
  }

  return {
    localId,
    email: user?.email?.trim(),
  };
}

function parseAllowedEmails(raw: string | undefined): Set<string> {
  const set = new Set<string>();
  if (!raw) return set;
  raw
    .split(/[,\n;\s]+/g)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)
    .forEach((email) => set.add(email));
  return set;
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const base64 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function base64UrlEncode(data: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < data.length; i += 1) {
    binary += String.fromCharCode(data[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function textBase64Url(value: string): string {
  return base64UrlEncode(new TextEncoder().encode(value));
}

async function signJwt(serviceAccount: GoogleServiceAccount, scope: string): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const tokenUri = serviceAccount.token_uri?.trim() || GOOGLE_OAUTH_TOKEN_URL;
  const payload = {
    iss: serviceAccount.client_email,
    sub: serviceAccount.client_email,
    aud: tokenUri,
    scope,
    iat: now,
    exp: now + 3600,
  };

  const encodedHeader = textBase64Url(JSON.stringify(header));
  const encodedPayload = textBase64Url(JSON.stringify(payload));
  const unsigned = `${encodedHeader}.${encodedPayload}`;

  const pkcs8 = pemToArrayBuffer(serviceAccount.private_key);
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    pkcs8,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const signatureBuffer = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(unsigned),
  );

  return `${unsigned}.${base64UrlEncode(new Uint8Array(signatureBuffer))}`;
}

async function getGoogleAccessToken(env: Env, scope = GOOGLE_CLOUD_SCOPE): Promise<string | null> {
  const cached = cachedGoogleTokens.get(scope) ?? null;
  if (cached && Date.now() < cached.expiresAtEpochMs - 60_000) {
    return cached.accessToken;
  }

  const rawJson = env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
  if (!rawJson) return null;

  let serviceAccount: GoogleServiceAccount;
  try {
    serviceAccount = JSON.parse(rawJson) as GoogleServiceAccount;
  } catch (error) {
    console.error("[admin-api] service account json parse failed", error);
    return null;
  }

  if (!serviceAccount.client_email || !serviceAccount.private_key) {
    return null;
  }

  const assertion = await signJwt(serviceAccount, scope);
  const tokenUri = serviceAccount.token_uri?.trim() || GOOGLE_OAUTH_TOKEN_URL;
  const form = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion,
  });

  const response = await fetch(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: form,
  });
  if (!response.ok) {
    const text = await response.text();
    console.error("[admin-api] token exchange failed", response.status, text);
    return null;
  }

  const tokenPayload = (await response.json()) as { access_token?: string; expires_in?: number };
  if (!tokenPayload.access_token) {
    return null;
  }

  cachedGoogleTokens.set(scope, {
    accessToken: tokenPayload.access_token,
    expiresAtEpochMs: Date.now() + (tokenPayload.expires_in ?? 3600) * 1000,
  });
  return tokenPayload.access_token;
}

function firestoreDocumentUrl(env: Env, collectionPath: string): string {
  const projectId = env.FIREBASE_PROJECT_ID.trim();
  return `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collectionPath}`;
}

function parseStringField(fields: Record<string, FirestoreField> | undefined, key: string): string | undefined {
  const field = fields?.[key];
  if (!field || !("stringValue" in field)) return undefined;
  const value = field.stringValue;
  return typeof value === "string" ? value : undefined;
}

function parseBooleanField(fields: Record<string, FirestoreField> | undefined, key: string): boolean | undefined {
  const field = fields?.[key];
  if (!field || !("booleanValue" in field)) return undefined;
  return typeof field.booleanValue === "boolean" ? field.booleanValue : undefined;
}

function parseTimestampFieldMs(fields: Record<string, FirestoreField> | undefined, key: string): number {
  const field = fields?.[key];
  if (!field || !("timestampValue" in field)) return 0;
  const raw = field.timestampValue;
  if (!raw) return 0;
  const parsed = Date.parse(raw);
  return Number.isNaN(parsed) ? 0 : parsed;
}

function parseFirestoreValue(value: FirestoreField | undefined): unknown {
  if (!value) return null;
  if (value.stringValue !== undefined) return value.stringValue;
  if (value.booleanValue !== undefined) return value.booleanValue;
  if (value.integerValue !== undefined) {
    const parsed = Number(value.integerValue);
    return Number.isFinite(parsed) ? parsed : value.integerValue;
  }
  if (value.doubleValue !== undefined) return value.doubleValue;
  if (value.timestampValue !== undefined) return value.timestampValue;
  if (value.mapValue?.fields) {
    const result: Record<string, unknown> = {};
    for (const [key, nested] of Object.entries(value.mapValue.fields)) {
      result[key] = parseFirestoreValue(nested);
    }
    return result;
  }
  if (value.arrayValue?.values) {
    return value.arrayValue.values.map((item) => parseFirestoreValue(item));
  }
  return null;
}

function parseFirestoreDocument(doc: FirestoreDocument | null): Record<string, unknown> {
  const fields = doc?.fields ?? {};
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(fields)) {
    result[key] = parseFirestoreValue(value);
  }
  return result;
}

function parseMillis(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim().length > 0) {
    const parsedNumber = Number(value);
    if (Number.isFinite(parsedNumber)) return parsedNumber;
    const parsedDate = Date.parse(value);
    return Number.isNaN(parsedDate) ? null : parsedDate;
  }
  return null;
}

async function getFirestoreDoc(env: Env, collectionPath: string): Promise<FirestoreDocument | null> {
  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) return null;

  const response = await fetch(firestoreDocumentUrl(env, collectionPath), {
    method: "GET",
    headers: { authorization: `Bearer ${accessToken}` },
  });
  if (response.status === 404) return null;
  if (!response.ok) {
    const text = await response.text();
    console.error("[admin-api] firestore get failed", response.status, text);
    return null;
  }
  return (await response.json()) as FirestoreDocument;
}

async function upsertFirestoreDoc(
  env: Env,
  collectionPath: string,
  fields: Record<string, unknown>,
  updateMaskFieldPaths?: string[],
): Promise<boolean> {
  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) return false;

  const mask = (updateMaskFieldPaths ?? [])
    .map((field) => `updateMask.fieldPaths=${encodeURIComponent(field)}`)
    .join("&");
  const suffix = mask ? `?${mask}` : "";

  const response = await fetch(`${firestoreDocumentUrl(env, collectionPath)}${suffix}`, {
    method: "PATCH",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ fields }),
  });

  if (!response.ok) {
    const text = await response.text();
    console.error("[admin-api] firestore upsert failed", collectionPath, response.status, text);
    return false;
  }
  return true;
}

async function deleteFirestoreDoc(env: Env, collectionPath: string): Promise<boolean> {
  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) return false;

  const response = await fetch(firestoreDocumentUrl(env, collectionPath), {
    method: "DELETE",
    headers: { authorization: `Bearer ${accessToken}` },
  });
  if (response.status === 404) return true;
  if (!response.ok) {
    const text = await response.text();
    console.warn("[admin-api] firestore delete failed", collectionPath, response.status, text);
    return false;
  }
  return true;
}

async function upsertAdminDoc(env: Env, uid: string, email?: string): Promise<void> {
  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) return;

  const updateMask = [
    "email",
    "role",
    "enabled",
    "source",
    "updatedAt",
  ].map((field) => `updateMask.fieldPaths=${encodeURIComponent(field)}`).join("&");

  const fields: Record<string, unknown> = {
    role: { stringValue: "admin" },
    enabled: { booleanValue: true },
    source: { stringValue: "admin_allowed_emails" },
    updatedAt: { timestampValue: new Date().toISOString() },
  };
  if (email && email.trim().length > 0) {
    fields.email = { stringValue: email.trim().toLowerCase() };
  }

  const response = await fetch(`${firestoreDocumentUrl(env, `admins/${encodeURIComponent(uid)}`)}?${updateMask}`, {
    method: "PATCH",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ fields }),
  });

  if (!response.ok) {
    const text = await response.text();
    console.warn("[admin-api] admin doc upsert failed", response.status, text);
  }
}

async function ensureAdminAccess(request: Request, env: Env): Promise<ResolvedAdmin | null> {
  const bearer = parseBearerToken(request);
  if (!bearer) return null;

  const apiKey = env.FIREBASE_WEB_API_KEY?.trim();
  if (!apiKey) {
    throw new Error("Missing FIREBASE_WEB_API_KEY");
  }

  const user = await verifyFirebaseIdToken(bearer, apiKey);
  const uid = user.localId;
  const email = user.email?.toLowerCase();
  const allowedEmails = parseAllowedEmails(env.ADMIN_ALLOWED_EMAILS);

  const adminDoc = await getFirestoreDoc(env, `admins/${encodeURIComponent(uid)}`);
  if (adminDoc) {
    return { uid, email, authorized: true, source: "firestore" };
  }

  if (email && allowedEmails.has(email)) {
    await upsertAdminDoc(env, uid, email);
    return { uid, email, authorized: true, source: "allowlist" };
  }

  return { uid, email, authorized: false, source: "none" };
}

async function handleAdminAccessCheck(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  try {
    const admin = await ensureAdminAccess(request, env);
    if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);

    return jsonResponse({
      authorized: admin.authorized,
      source: admin.source,
      uid: admin.uid,
      email: admin.email ?? null,
    });
  } catch (error) {
    console.warn("[admin-api] adminAccessCheck failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
}

function parsePackages(raw: unknown): string[] {
  if (!Array.isArray(raw)) return DEFAULT_PACKAGES;
  const values = raw
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter(Boolean);
  return values.length > 0 ? Array.from(new Set(values)) : DEFAULT_PACKAGES;
}

function parseCoverageDays(raw: unknown): number {
  const numeric = Number(raw);
  if (!Number.isFinite(numeric)) return COVERAGE_DEFAULT_DAYS;
  return Math.max(COVERAGE_MIN_DAYS, Math.min(COVERAGE_MAX_DAYS, Math.floor(numeric)));
}

async function runFirestoreQuery(env: Env, structuredQuery: unknown): Promise<FirestoreDocument[]> {
  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) return [];

  const projectId = env.FIREBASE_PROJECT_ID.trim();
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:runQuery`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ structuredQuery }),
  });
  if (!response.ok) {
    const text = await response.text();
    console.error("[admin-api] runQuery failed", response.status, text);
    return [];
  }

  const payload = (await response.json()) as Array<{ document?: FirestoreDocument }>;
  return payload
    .map((item) => item.document)
    .filter((doc): doc is FirestoreDocument => Boolean(doc && doc.fields));
}

async function listCollectionDocuments(
  env: Env,
  collectionId: string,
  pageSize = 500,
): Promise<FirestoreDocument[]> {
  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) return [];

  const docs: FirestoreDocument[] = [];
  const projectId = env.FIREBASE_PROJECT_ID.trim();
  let pageToken: string | undefined;

  do {
    const params = new URLSearchParams({
      pageSize: String(pageSize),
    });
    if (pageToken) params.set("pageToken", pageToken);

    const url =
      `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collectionId}?${params.toString()}`;
    const response = await fetch(url, {
      method: "GET",
      headers: { authorization: `Bearer ${accessToken}` },
    });

    if (!response.ok) {
      const text = await response.text();
      console.error("[admin-api] list docs failed", collectionId, response.status, text);
      break;
    }

    const payload = (await response.json()) as {
      documents?: FirestoreDocument[];
      nextPageToken?: string;
    };
    if (Array.isArray(payload.documents)) {
      docs.push(...payload.documents.filter((doc) => Boolean(doc.fields)));
    }
    pageToken = payload.nextPageToken?.trim() || undefined;
  } while (pageToken);

  return docs;
}

async function queryDevicesByPackage(env: Env, packageName: string): Promise<DeviceRecord[]> {
  const docs = await runFirestoreQuery(env, {
    from: [{ collectionId: "devices" }],
    where: {
      fieldFilter: {
        field: { fieldPath: "packageName" },
        op: "EQUAL",
        value: { stringValue: packageName },
      },
    },
  });

  return docs.map((doc) => {
    const fields = doc.fields;
    return {
      fcmToken: parseStringField(fields, "fcmToken"),
      locale: parseStringField(fields, "locale"),
      packageName: parseStringField(fields, "packageName"),
      notificationsEnabled: parseBooleanField(fields, "notificationsEnabled"),
      updatedAtMs: parseTimestampFieldMs(fields, "updatedAt"),
    };
  });
}

async function handleDeviceCoverageReport(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  let admin: ResolvedAdmin | null;
  try {
    admin = await ensureAdminAccess(request, env);
  } catch (error) {
    console.warn("[admin-api] deviceCoverageReport auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
  if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);
  if (!admin.authorized) return jsonResponse({ error: "User is not in admins whitelist" }, 403);

  const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
  const days = parseCoverageDays(body.days);
  const packages = parsePackages(body.packages);
  const cutoffMs = Date.now() - days * 24 * 60 * 60 * 1000;

  const byPackage: DeviceCoverageItem[] = [];
  for (const packageName of packages) {
    const devices = await queryDevicesByPackage(env, packageName);
    const totalDeviceCount = devices.length;
    const activeDeviceCount = devices.filter((device) => device.updatedAtMs >= cutoffMs).length;
    byPackage.push({ packageName, activeDeviceCount, totalDeviceCount });
  }

  const missingPackages = byPackage
    .filter((item) => item.totalDeviceCount === 0)
    .map((item) => item.packageName);
  const stalePackages = byPackage
    .filter((item) => item.totalDeviceCount > 0 && item.activeDeviceCount === 0)
    .map((item) => item.packageName);

  return jsonResponse({
    days,
    generatedAt: new Date().toISOString(),
    byPackage,
    missingPackages,
    stalePackages,
  });
}

function sanitizeOptionalText(value: unknown, maxLength: number): string | undefined {
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return trimmed.slice(0, maxLength);
}

function parseTestPushInput(raw: Record<string, unknown>): { ok: true; value: TestPushInput } | { ok: false; error: string } {
  const token = typeof raw.token === "string" ? raw.token.trim() : "";
  const installationId = typeof raw.installationId === "string" ? raw.installationId.trim() : "";

  if (token && installationId) {
    return { ok: false, error: "Provide either token or installationId, not both" };
  }
  if (!token && !installationId) {
    return { ok: false, error: "Either token or installationId is required" };
  }
  if (token && !TOKEN_REGEX.test(token)) {
    return { ok: false, error: "Invalid device token" };
  }
  if (installationId && !INSTALLATION_ID_REGEX.test(installationId)) {
    return { ok: false, error: "Invalid installationId" };
  }

  const value: TestPushInput = {
    token: token || undefined,
    installationId: installationId || undefined,
    title: sanitizeOptionalText(raw.title, MAX_TITLE_LENGTH),
    body: sanitizeOptionalText(raw.body, MAX_BODY_LENGTH),
    useNotificationPayload: typeof raw.useNotificationPayload === "boolean" ? raw.useNotificationPayload : false,
  };

  if (raw.data != null) {
    if (typeof raw.data !== "object" || Array.isArray(raw.data)) {
      return { ok: false, error: "data must be an object<string,string>" };
    }
    const entries = Object.entries(raw.data as Record<string, unknown>);
    if (entries.length > MAX_DATA_ENTRIES) {
      return { ok: false, error: `data supports at most ${MAX_DATA_ENTRIES} entries` };
    }
    const normalized: Record<string, string> = {};
    for (const [key, val] of entries) {
      const normalizedKey = key.trim();
      if (!normalizedKey) return { ok: false, error: "data keys cannot be empty" };
      if (normalizedKey.length > MAX_DATA_KEY_LENGTH) {
        return { ok: false, error: `data key too long: ${normalizedKey}` };
      }
      if (typeof val !== "string") {
        return { ok: false, error: `data value must be string for key: ${normalizedKey}` };
      }
      if (val.length > MAX_DATA_VALUE_LENGTH) {
        return { ok: false, error: `data value too long for key: ${normalizedKey}` };
      }
      normalized[normalizedKey] = val;
    }
    value.data = normalized;
  }

  return { ok: true, value };
}

async function resolveTokenFromInstallationId(env: Env, installationId: string): Promise<{
  token: string;
  packageName?: string;
  locale?: string;
}> {
  const doc = await getFirestoreDoc(env, `devices/${encodeURIComponent(installationId)}`);
  if (!doc?.fields) {
    throw new Error(`Device not found for installationId: ${installationId}`);
  }
  const token = parseStringField(doc.fields, "fcmToken")?.trim() ?? "";
  if (!TOKEN_REGEX.test(token)) {
    throw new Error(`Device record has no valid fcmToken (installationId: ${installationId})`);
  }
  return {
    token,
    packageName: parseStringField(doc.fields, "packageName"),
    locale: parseStringField(doc.fields, "locale"),
  };
}

async function sendFcmMessage(
  env: Env,
  token: string,
  payload: { title: string; body: string; data: Record<string, string>; useNotificationPayload: boolean },
): Promise<string> {
  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) {
    throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON for FCM delivery");
  }
  const projectId = env.FIREBASE_PROJECT_ID.trim();
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  const message: Record<string, unknown> = {
    token,
    data: {
      title: payload.title,
      body: payload.body,
      ...payload.data,
    },
    android: {
      priority: "HIGH",
      notification: payload.useNotificationPayload
        ? { channelId: "app_notifications", priority: "DEFAULT" }
        : undefined,
    },
  };
  if (payload.useNotificationPayload) {
    message.notification = { title: payload.title, body: payload.body };
  }

  const response = await fetch(url, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ message }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`FCM send failed (${response.status}): ${text.slice(0, 240)}`);
  }
  const responseBody = (await response.json()) as { name?: string };
  return responseBody.name ?? "";
}

async function handleSendTestNotification(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  let admin: ResolvedAdmin | null;
  try {
    admin = await ensureAdminAccess(request, env);
  } catch (error) {
    console.warn("[admin-api] sendTestNotification auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
  if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);
  if (!admin.authorized) return jsonResponse({ error: "User is not in admins whitelist" }, 403);

  const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
  if (!body) return jsonResponse({ error: "Invalid JSON body" }, 400);

  const parsed = parseTestPushInput(body);
  if (!parsed.ok) return jsonResponse({ error: parsed.error }, 400);

  const input = parsed.value;
  const title = input.title ?? "Test Bildirim";
  const bodyText = input.body ?? "Admin panel test bildirimi";

  try {
    let resolvedToken = input.token ?? "";
    let targetType: "token" | "installationId" = "token";
    let packageName: string | undefined;
    let locale: string | undefined;

    if (input.installationId) {
      const resolved = await resolveTokenFromInstallationId(env, input.installationId);
      resolvedToken = resolved.token;
      packageName = resolved.packageName;
      locale = resolved.locale;
      targetType = "installationId";
    }

    const messageId = await sendFcmMessage(env, resolvedToken, {
      title,
      body: bodyText,
      data: input.data ?? {},
      useNotificationPayload: Boolean(input.useNotificationPayload),
    });

    return jsonResponse({
      success: true,
      messageId,
      mode: input.useNotificationPayload ? "notification+data" : "data-only",
      targetType,
      installationId: input.installationId ?? null,
      packageName: packageName ?? null,
      locale: locale ?? null,
    });
  } catch (error) {
    console.error("[admin-api] sendTestNotification failed", error);
    return jsonResponse({ error: error instanceof Error ? error.message : "Failed to send test push" }, 500);
  }
}

function extractDocumentId(doc: FirestoreDocument): string {
  const name = doc.name ?? "";
  const parts = name.split("/");
  return parts.length > 0 ? parts[parts.length - 1] : "";
}

function asNumber(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function asBoolean(value: unknown): boolean {
  return value === true;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function sanitizeText(value: unknown, maxLength: number): string {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, maxLength);
}

function parseExpiryMillis(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return Math.floor(value);
  }
  if (typeof value !== "string" || value.trim().length === 0) {
    return null;
  }
  const asNumber = Number(value);
  if (Number.isFinite(asNumber) && asNumber > 0) {
    return Math.floor(asNumber);
  }
  const asDate = Date.parse(value);
  return Number.isNaN(asDate) ? null : asDate;
}

function toInAppPurchaseState(value: unknown): string {
  switch (Number(value)) {
    case 0:
      return "PURCHASED";
    case 1:
      return "CANCELED";
    case 2:
      return "PENDING";
    default:
      return "UNKNOWN";
  }
}

function toInAppAcknowledgementState(value: unknown): string {
  switch (Number(value)) {
    case 1:
      return "ACKNOWLEDGED";
    case 0:
      return "PENDING";
    default:
      return "UNKNOWN";
  }
}

function parseVerifyPurchasePayload(body: Record<string, unknown>): {
  ok: true;
  value: VerifyPurchasePayload;
} | {
  ok: false;
  error: string;
} {
  const packageName = sanitizeText(body.packageName, PRODUCT_ID_MAX_LENGTH);
  const productId = sanitizeText(body.productId, PRODUCT_ID_MAX_LENGTH);
  const purchaseToken = sanitizeText(body.purchaseToken, PURCHASE_TOKEN_MAX_LENGTH);
  const purchaseTypeRaw = sanitizeText(body.purchaseType, 16).toLowerCase();
  const purchaseType: PurchaseType = purchaseTypeRaw === "inapp" ? "inapp" : "subs";

  if (!packageName || !PACKAGE_NAME_REGEX.test(packageName)) {
    return { ok: false, error: "Invalid packageName" };
  }
  if (!productId) {
    return { ok: false, error: "Invalid productId" };
  }
  if (!purchaseToken) {
    return { ok: false, error: "Invalid purchaseToken" };
  }

  return {
    ok: true,
    value: { packageName, productId, purchaseToken, purchaseType },
  };
}

async function verifyPurchaseAppCheckHeader(request: Request, env: Env): Promise<{
  ok: true;
  appId: string | null;
} | {
  ok: false;
  statusCode: number;
  error: string;
}> {
  const requireAppCheck = (env.VERIFY_PURCHASE_REQUIRE_APP_CHECK?.trim().toLowerCase() ?? "true") === "true";
  const appCheckToken = request.headers.get("x-firebase-appcheck")?.trim() ?? "";

  if (!appCheckToken) {
    return requireAppCheck
      ? { ok: false, statusCode: 401, error: "Missing App Check token" }
      : { ok: true, appId: null };
  }

  const projectNumber = env.FIREBASE_PROJECT_NUMBER?.trim() ?? "";
  const allowedAppIds = parseAllowedAppIds(env.FIREBASE_APP_CHECK_ALLOWED_APP_IDS);
  if (!/^\d+$/.test(projectNumber) || allowedAppIds.size === 0) {
    console.error("[admin-api] App Check verifier configuration is incomplete");
    return { ok: false, statusCode: 500, error: "App Check verifier is not configured" };
  }

  try {
    const verified = await verifyFirebaseAppCheckToken(appCheckToken, {
      projectNumber,
      allowedAppIds,
    });
    return { ok: true, appId: verified.appId };
  } catch (error) {
    if (error instanceof AppCheckJwksUnavailableError) {
      console.error("[admin-api] App Check JWKS unavailable");
      return { ok: false, statusCode: 503, error: "App Check verification unavailable" };
    }
    if (error instanceof AppCheckVerificationError) {
      console.warn("[admin-api] Invalid App Check token");
      return { ok: false, statusCode: 401, error: "Invalid App Check token" };
    }
    console.error("[admin-api] Unexpected App Check verifier failure");
    return { ok: false, statusCode: 503, error: "App Check verification unavailable" };
  }
}

async function handleRegisterDevice(request: Request, env: Env): Promise<Response> {
  const requireAppCheck =
    (env.REGISTER_DEVICE_REQUIRE_APP_CHECK?.trim().toLowerCase() ?? "true") === "true";

  return handleDeviceRegistration(request, {
    requireAppCheck,
    verifyAppCheck: async (token) => {
      const projectNumber = env.FIREBASE_PROJECT_NUMBER?.trim() ?? "";
      const allowedAppIds = parseAllowedAppIds(env.FIREBASE_APP_CHECK_ALLOWED_APP_IDS);
      if (!/^\d+$/.test(projectNumber) || allowedAppIds.size === 0) {
        console.error("[admin-api] device registration App Check configuration is incomplete");
        throw new DeviceRegistrationDependencyUnavailableError();
      }
      try {
        return await verifyFirebaseAppCheckToken(token, { projectNumber, allowedAppIds });
      } catch (error) {
        if (error instanceof AppCheckJwksUnavailableError) {
          console.error("[admin-api] device registration App Check JWKS unavailable");
          throw new DeviceRegistrationDependencyUnavailableError();
        }
        if (error instanceof AppCheckVerificationError) {
          throw error;
        }
        console.error("[admin-api] unexpected device registration App Check failure");
        throw new DeviceRegistrationDependencyUnavailableError();
      }
    },
    upsertDevice: async (installationId, record) =>
      upsertDeviceRegistrationRecord(env, installationId, record),
  });
}

async function upsertDeviceRegistrationRecord(
  env: Env,
  installationId: string,
  record: DeviceRegistrationRecord,
): Promise<boolean> {
  const fields: Record<string, FirestoreField> = {};
  for (const [key, value] of Object.entries(record)) {
    fields[key] = key === "updatedAt"
      ? { timestampValue: String(value) }
      : toFirestoreValue(value);
  }
  return upsertFirestoreDoc(
    env,
    `devices/${encodeURIComponent(installationId)}`,
    fields,
    Object.keys(fields),
  );
}

async function sha256Hex(value: string): Promise<string> {
  const buffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(buffer))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

async function verifyInAppPurchase(
  accessToken: string,
  payload: VerifyPurchasePayload,
): Promise<VerifyPurchaseResponse> {
  const url = [
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications",
    encodeURIComponent(payload.packageName),
    "purchases/products",
    encodeURIComponent(payload.productId),
    "tokens",
    encodeURIComponent(payload.purchaseToken),
  ].join("/");
  const response = await fetch(url, {
    method: "GET",
    headers: { authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`In-app verification failed (${response.status}): ${body.slice(0, 200)}`);
  }
  const data = (await response.json()) as {
    purchaseState?: number;
    acknowledgementState?: number;
  };
  const purchaseState = toInAppPurchaseState(data.purchaseState);
  return {
    verified: purchaseState === "PURCHASED",
    purchaseState,
    acknowledgementState: toInAppAcknowledgementState(data.acknowledgementState),
    expiryTimeMillis: null,
    autoRenewing: false,
  };
}

async function verifySubscriptionPurchase(
  accessToken: string,
  payload: VerifyPurchasePayload,
): Promise<VerifyPurchaseResponse> {
  const url = [
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications",
    encodeURIComponent(payload.packageName),
    "purchases/subscriptionsv2/tokens",
    encodeURIComponent(payload.purchaseToken),
  ].join("/");
  const response = await fetch(url, {
    method: "GET",
    headers: { authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`Subscription verification failed (${response.status}): ${body.slice(0, 200)}`);
  }

  const data = (await response.json()) as {
    subscriptionState?: string;
    acknowledgementState?: string;
    lineItems?: Array<{
      expiryTime?: string;
      autoRenewingPlan?: { autoRenewEnabled?: boolean };
    }>;
  };
  const lineItem = data.lineItems?.[0];
  const subscriptionState = data.subscriptionState ?? "SUBSCRIPTION_STATE_UNSPECIFIED";
  return {
    verified: subscriptionState === "SUBSCRIPTION_STATE_ACTIVE" ||
      subscriptionState === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    purchaseState: subscriptionState,
    acknowledgementState: data.acknowledgementState ?? "ACKNOWLEDGEMENT_STATE_UNSPECIFIED",
    expiryTimeMillis: parseExpiryMillis(lineItem?.expiryTime),
    autoRenewing: Boolean(lineItem?.autoRenewingPlan?.autoRenewEnabled),
  };
}

async function verifyPurchaseOnPlay(
  env: Env,
  payload: VerifyPurchasePayload,
): Promise<VerifyPurchaseResponse> {
  const accessToken = await getGoogleAccessToken(env, GOOGLE_PLAY_ANDROID_PUBLISHER_SCOPE);
  if (!accessToken) {
    throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON for Play verification");
  }
  if (payload.purchaseType === "inapp") {
    return verifyInAppPurchase(accessToken, payload);
  }
  return verifySubscriptionPurchase(accessToken, payload);
}

async function upsertPurchaseVerificationRecord(
  env: Env,
  record: {
    uid: string;
    email?: string;
    payload: VerifyPurchasePayload;
    response: VerifyPurchaseResponse;
    appCheckAppId: string | null;
  },
): Promise<void> {
  const docHash = await sha256Hex(
    `${record.uid}|${record.payload.packageName}|${record.payload.productId}|${record.payload.purchaseToken}|${record.payload.purchaseType}`,
  );
  const tokenHash = await sha256Hex(record.payload.purchaseToken);

  const fields: Record<string, FirestoreField> = {
    uid: { stringValue: record.uid },
    email: record.email ? { stringValue: record.email.toLowerCase() } : { nullValue: null },
    packageName: { stringValue: record.payload.packageName },
    productId: { stringValue: record.payload.productId },
    purchaseType: { stringValue: record.payload.purchaseType },
    appCheckAppId: record.appCheckAppId
      ? { stringValue: record.appCheckAppId }
      : { nullValue: null },
    verified: { booleanValue: record.response.verified },
    purchaseState: { stringValue: record.response.purchaseState },
    acknowledgementState: { stringValue: record.response.acknowledgementState },
    expiryTimeMillis: record.response.expiryTimeMillis == null
      ? { nullValue: null }
      : { integerValue: String(Math.floor(record.response.expiryTimeMillis)) },
    autoRenewing: { booleanValue: record.response.autoRenewing },
    tokenHash: { stringValue: tokenHash },
    updatedAt: { timestampValue: new Date().toISOString() },
  };

  const success = await upsertFirestoreDoc(env, `purchase_verifications/${docHash}`, fields, [
    "uid",
    "email",
    "packageName",
    "productId",
    "purchaseType",
    "appCheckAppId",
    "verified",
    "purchaseState",
    "acknowledgementState",
    "expiryTimeMillis",
    "autoRenewing",
    "tokenHash",
    "updatedAt",
  ]);
  if (!success) {
    console.warn("[admin-api] purchase verification record upsert failed");
  }
}

async function handleVerifyPurchase(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  const bearerToken = parseBearerToken(request);
  if (!bearerToken) {
    return jsonResponse({ error: "Missing Bearer token" }, 401);
  }
  const apiKey = env.FIREBASE_WEB_API_KEY?.trim();
  if (!apiKey) {
    return jsonResponse({ error: "Missing FIREBASE_WEB_API_KEY" }, 500);
  }

  let user: FirebaseLookupUser;
  try {
    user = await verifyFirebaseIdToken(bearerToken, apiKey);
  } catch (error) {
    console.warn("[admin-api] verifyPurchase auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }

  const appCheckResult = await verifyPurchaseAppCheckHeader(request, env);
  if (!appCheckResult.ok) {
    return jsonResponse({ error: appCheckResult.error }, appCheckResult.statusCode);
  }

  const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
  if (!body) {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }
  const parsed = parseVerifyPurchasePayload(body);
  if (!parsed.ok) {
    return jsonResponse({ error: parsed.error }, 400);
  }

  try {
    const verification = await verifyPurchaseOnPlay(env, parsed.value);
    await upsertPurchaseVerificationRecord(env, {
      uid: user.localId,
      email: user.email,
      payload: parsed.value,
      response: verification,
      appCheckAppId: appCheckResult.appId,
    });
    return jsonResponse(verification);
  } catch (error) {
    console.error("[admin-api] verifyPurchase failed", {
      uid: user.localId,
      packageName: parsed.value.packageName,
      productId: parsed.value.productId,
      purchaseType: parsed.value.purchaseType,
      error,
    });
    return jsonResponse(
      {
        verified: false,
        purchaseState: "ERROR",
        acknowledgementState: "UNKNOWN",
        expiryTimeMillis: null,
        autoRenewing: false,
      } satisfies VerifyPurchaseResponse,
      502,
    );
  }
}

function zeroAdTotals(): AdTotals {
  return {
    earningsTry: 0,
    ecpmTry: 0,
    adRequests: 0,
    matchedRequests: 0,
    impressions: 0,
    fillRatePct: 0,
    showRatePct: 0,
  };
}

function buildFormatBreakdownFromRows<
  T extends {
    format: string;
    earningsMicros: number;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
  },
>(rows: T[]): AdFormatBreakdown[] {
  const grouped = new Map<string, {
    format: string;
    earningsMicros: number;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
  }>();

  for (const row of rows) {
    const key = row.format || "unknown";
    const current = grouped.get(key) ?? {
      format: key,
      earningsMicros: 0,
      adRequests: 0,
      matchedRequests: 0,
      impressions: 0,
    };
    current.earningsMicros += row.earningsMicros;
    current.adRequests += row.adRequests;
    current.matchedRequests += row.matchedRequests;
    current.impressions += row.impressions;
    grouped.set(key, current);
  }

  return Array.from(grouped.values())
    .map((item) => ({
      format: item.format,
      earningsTry: round4(item.earningsMicros / 1_000_000),
      ecpmTry: round4(calculateEcpmTry(item.earningsMicros, item.impressions)),
      adRequests: item.adRequests,
      matchedRequests: item.matchedRequests,
      impressions: item.impressions,
      fillRatePct: round2(calculateRate(item.matchedRequests, item.adRequests)),
      showRatePct: round2(calculateRate(item.impressions, item.matchedRequests)),
    }))
    .sort((left, right) => right.adRequests - left.adRequests || right.earningsTry - left.earningsTry);
}

function countAlertReasons(alerts: ReportAlert[]): Record<string, number> {
  const counts = new Map<string, number>();
  for (const alert of alerts) {
    for (const reason of alert.reasons) {
      counts.set(reason, (counts.get(reason) ?? 0) + 1);
    }
  }
  return Object.fromEntries(
    Array.from(counts.entries()).sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0])),
  );
}

function readThresholdEnv(envValue: string | undefined, fallback: number): number {
  const raw = Number(envValue);
  if (!Number.isFinite(raw)) return fallback;
  return Math.max(0, Math.floor(raw));
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

function round4(value: number): number {
  return Math.round(value * 10_000) / 10_000;
}

function calculateRate(numerator: number, denominator: number): number {
  if (denominator <= 0) return 0;
  return (numerator / denominator) * 100;
}

function calculateEcpmTry(earningsMicros: number, impressions: number): number {
  if (impressions <= 0) return 0;
  const earningsTry = earningsMicros / 1_000_000;
  return (earningsTry / impressions) * 1000;
}

function toAdMobDate(date: Date): { year: number; month: number; day: number } {
  return {
    year: date.getUTCFullYear(),
    month: date.getUTCMonth() + 1,
    day: date.getUTCDate(),
  };
}

function formatDate(date: Date): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function addDays(base: Date, days: number): Date {
  const copy = new Date(base);
  copy.setUTCDate(copy.getUTCDate() + days);
  return copy;
}

function parseMetricNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function readMetric(row: Record<string, unknown>, key: string): number {
  const metricValues = (row.metricValues ?? {}) as Record<string, Record<string, unknown>>;
  const metric = metricValues[key] ?? {};
  const micros = parseMetricNumber(metric.microsValue);
  if (micros != null) return micros;
  const integer = parseMetricNumber(metric.integerValue);
  if (integer != null) return integer;
  const doubleValue = parseMetricNumber(metric.doubleValue);
  if (doubleValue != null) return doubleValue;
  return 0;
}

async function loadLatestCoverageSnapshotFromFirestore(env: Env): Promise<{
  generatedAt: string;
  byPackage: Array<{ packageName: string; activeDeviceCount: number; totalDeviceCount: number }>;
} | null> {
  const docs = await listCollectionDocuments(env, "coverage_reports", 200);
  if (docs.length === 0) return null;

  const parsed = docs
    .map((doc) => parseFirestoreDocument(doc))
    .map((item) => ({
      days: asNumber(item.days),
      generatedAt: asString(item.generatedAt),
      byPackage: Array.isArray(item.byPackage)
        ? item.byPackage
            .map((entry) => (entry && typeof entry === "object" ? (entry as Record<string, unknown>) : null))
            .filter((entry): entry is Record<string, unknown> => Boolean(entry && asString(entry.packageName)))
            .map((entry) => ({
              packageName: asString(entry.packageName),
              activeDeviceCount: asNumber(entry.activeDeviceCount),
              totalDeviceCount: asNumber(entry.totalDeviceCount),
            }))
        : [],
    }))
    .filter((item) => item.generatedAt);

  parsed.sort((left, right) => Date.parse(right.generatedAt) - Date.parse(left.generatedAt));
  return parsed.find((item) => item.days === 30) ?? parsed[0] ?? null;
}

async function handleAdminGetFlavorHubSummary(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  let admin: ResolvedAdmin | null;
  try {
    admin = await ensureAdminAccess(request, env);
  } catch (error) {
    console.warn("[admin-api] adminGetFlavorHubSummary auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
  if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);
  if (!admin.authorized) return jsonResponse({ error: "User is not in admins whitelist" }, 403);

  const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
  const packages = parsePackages(body.packages);

  const latestCoverage = await loadLatestCoverageSnapshotFromFirestore(env);
  if (latestCoverage) {
    const coverage = Object.fromEntries(
      latestCoverage.byPackage.map((item) => [
        item.packageName,
        { active: item.activeDeviceCount, total: item.totalDeviceCount },
      ]),
    );
    return jsonResponse({
      loadedAt: latestCoverage.generatedAt,
      source: "coverage_reports",
      coverage,
    });
  }

  const coverageFromDevices = await Promise.all(
    packages.map(async (packageName) => {
      const devices = await queryDevicesByPackage(env, packageName);
      const active = devices.filter(
        (item) => item.updatedAtMs >= Date.now() - 30 * 24 * 60 * 60 * 1000,
      ).length;
      return [packageName, { active, total: devices.length }] as const;
    }),
  );
  return jsonResponse({
    loadedAt: new Date().toISOString(),
    source: "devices",
    coverage: Object.fromEntries(coverageFromDevices),
  });
}

async function loadRecentCoverageReportsFromFirestore(
  env: Env,
): Promise<Array<{
  id: string;
  days: number;
  generatedAt: string;
  packageCount: number;
  totalActiveDevices: number;
  totalDevices: number;
}>> {
  const docs = await listCollectionDocuments(env, "coverage_reports", 200);
  const parsed = docs
    .map((doc) => ({ id: extractDocumentId(doc), data: parseFirestoreDocument(doc) }))
    .map(({ id, data }) => {
      const byPackage = Array.isArray(data.byPackage) ? data.byPackage : [];
      const packageRows = byPackage
        .map((item) => (item && typeof item === "object" ? (item as Record<string, unknown>) : null))
        .filter((item): item is Record<string, unknown> => Boolean(item));
      return {
        id,
        days: asNumber(data.days),
        generatedAt: asString(data.generatedAt),
        packageCount: packageRows.length,
        totalActiveDevices: packageRows.reduce((sum, row) => sum + asNumber(row.activeDeviceCount), 0),
        totalDevices: packageRows.reduce((sum, row) => sum + asNumber(row.totalDeviceCount), 0),
      };
    })
    .filter((item) => item.generatedAt);

  parsed.sort((left, right) => Date.parse(right.generatedAt) - Date.parse(left.generatedAt));
  return parsed.slice(0, 5);
}

function computeAgeHours(isoValue: string | undefined | null): number | null {
  if (!isoValue) return null;
  const parsed = Date.parse(isoValue);
  if (!Number.isFinite(parsed)) return null;
  return round2((Date.now() - parsed) / (60 * 60 * 1000));
}

type RuntimeAdFunnel = {
  format: string;
  placement?: string;
  requestSent: number;
  loaded: number;
  loadFailed: number;
  showIntent: number;
  showBlocked: number;
  showNotLoaded: number;
  showStarted: number;
  showImpression: number;
  showDismissed: number;
  showFailed: number;
  paidEvent: number;
};

function createRuntimeAdFunnel(format: string, placement?: string): RuntimeAdFunnel {
  return {
    format,
    ...(placement ? { placement } : {}),
    requestSent: 0,
    loaded: 0,
    loadFailed: 0,
    showIntent: 0,
    showBlocked: 0,
    showNotLoaded: 0,
    showStarted: 0,
    showImpression: 0,
    showDismissed: 0,
    showFailed: 0,
    paidEvent: 0,
  };
}

type RuntimeAdFunnelCounter = Exclude<keyof RuntimeAdFunnel, "format" | "placement">;

const RUNTIME_AD_EVENT_COUNTERS: Readonly<Record<string, RuntimeAdFunnelCounter>> = {
  request_sent: "requestSent",
  loaded: "loaded",
  load_failed: "loadFailed",
  show_intent: "showIntent",
  show_blocked: "showBlocked",
  show_not_loaded: "showNotLoaded",
  show_started: "showStarted",
  show_impression: "showImpression",
  show_dismissed: "showDismissed",
  show_failed: "showFailed",
  paid_event: "paidEvent",
};

function addRuntimeAdFunnelEvent(
  funnel: RuntimeAdFunnel,
  event: string,
  count: number,
): void {
  const counter = RUNTIME_AD_EVENT_COUNTERS[event];
  if (!counter || count <= 0) return;
  funnel[counter] += count;
}

function parsePlacementRuntimeEvent(eventKey: string): { event: string; placement: string } | null {
  const separatorIndex = eventKey.indexOf("@");
  if (separatorIndex <= 0 || separatorIndex >= eventKey.length - 1) return null;
  return {
    event: eventKey.slice(0, separatorIndex),
    placement: eventKey.slice(separatorIndex + 1),
  };
}

function withRuntimeAdFunnelRates(funnel: RuntimeAdFunnel): RuntimeAdFunnel & {
  loadedButNotShown: number;
  loadedToShownPct: number;
  intentToShownPct: number;
} {
  return {
    ...funnel,
    loadedButNotShown: Math.max(funnel.loaded - funnel.showImpression, 0),
    loadedToShownPct: funnel.loaded > 0 ? round2((funnel.showImpression / funnel.loaded) * 100) : 0,
    intentToShownPct: funnel.showIntent > 0 ? round2((funnel.showImpression / funnel.showIntent) * 100) : 0,
  };
}

async function handleAdminGetAnalyticsSummary(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  let admin: ResolvedAdmin | null;
  try {
    admin = await ensureAdminAccess(request, env);
  } catch (error) {
    console.warn("[admin-api] adminGetAnalyticsSummary auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
  if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);
  if (!admin.authorized) return jsonResponse({ error: "User is not in admins whitelist" }, 403);

  const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
  const packages = parsePackages(body.packages);

  const devicesDocs = await listCollectionDocuments(env, "devices", 1000);
  const devices = devicesDocs.map((doc) => parseFirestoreDocument(doc));
  const activeSinceMs = Date.now() - 30 * 24 * 60 * 60 * 1000;
  const recentlySyncedSinceMs = Date.now() - 24 * 60 * 60 * 1000;
  const staleSyncSinceMs = Date.now() - 7 * 24 * 60 * 60 * 1000;

  const packageCounts = new Map<string, number>();
  const registrationHealthByPackage = new Map<string, {
    packageName: string;
    totalDevices: number;
    withToken: number;
    withoutToken: number;
    recentlySynced24h: number;
    stale7d: number;
  }>();
  const runtimeFunnelByFormat = new Map<string, RuntimeAdFunnel>();
  const runtimeFunnelByPlacement = new Map<string, RuntimeAdFunnel>();
  const runtimeSuppressReasonCounts = new Map<string, number>();
  const runtimeSuppressReasonByPackage = new Map<string, Map<string, number>>();
  let totalDevices = 0;
  let activeDevices30d = 0;
  let notificationsEnabled30d = 0;
  let devicesWithToken = 0;
  let devicesWithoutToken = 0;
  let recentlySynced24h = 0;
  let staleRegistration7d = 0;

  for (const device of devices) {
    totalDevices += 1;
    const packageName = asString(device.packageName);
    if (packageName) {
      packageCounts.set(packageName, (packageCounts.get(packageName) ?? 0) + 1);
    }
    const updatedAtMs = parseMillis(device.updatedAt) ?? 0;
    const hasToken =
      asBoolean(device.hasToken) ||
      asString(device.fcmToken).trim().length >= 80;
    const syncReferenceMs =
      parseMillis(device.lastRegistrationSuccessAt) ??
      parseMillis(device.syncedAtEpochMs) ??
      updatedAtMs;

    if (hasToken) {
      devicesWithToken += 1;
    } else {
      devicesWithoutToken += 1;
    }
    if (syncReferenceMs >= recentlySyncedSinceMs) {
      recentlySynced24h += 1;
    }
    if (syncReferenceMs > 0 && syncReferenceMs < staleSyncSinceMs) {
      staleRegistration7d += 1;
    }

    if (packageName) {
      const current = registrationHealthByPackage.get(packageName) ?? {
        packageName,
        totalDevices: 0,
        withToken: 0,
        withoutToken: 0,
        recentlySynced24h: 0,
        stale7d: 0,
      };
      current.totalDevices += 1;
      if (hasToken) {
        current.withToken += 1;
      } else {
        current.withoutToken += 1;
      }
      if (syncReferenceMs >= recentlySyncedSinceMs) {
        current.recentlySynced24h += 1;
      }
      if (syncReferenceMs > 0 && syncReferenceMs < staleSyncSinceMs) {
        current.stale7d += 1;
      }
      registrationHealthByPackage.set(packageName, current);
    }

    const deviceFunnel = asRecord(device.adRuntimeFunnelCounts);
    for (const [format, rawCounts] of Object.entries(deviceFunnel)) {
      const counts = asRecord(rawCounts);
      const formatFunnel = runtimeFunnelByFormat.get(format) ?? createRuntimeAdFunnel(format);
      for (const [eventKey, rawCount] of Object.entries(counts)) {
        const count = asNumber(rawCount);
        const placementEvent = parsePlacementRuntimeEvent(eventKey);
        if (placementEvent) {
          const placementKey = `${format}@${placementEvent.placement}`;
          const placementFunnel =
            runtimeFunnelByPlacement.get(placementKey) ??
            createRuntimeAdFunnel(format, placementEvent.placement);
          addRuntimeAdFunnelEvent(placementFunnel, placementEvent.event, count);
          runtimeFunnelByPlacement.set(placementKey, placementFunnel);
        } else {
          addRuntimeAdFunnelEvent(formatFunnel, eventKey, count);
        }
      }
      runtimeFunnelByFormat.set(format, formatFunnel);
    }

    const deviceSuppressCounts = asRecord(device.adRuntimeSuppressReasonCounts);
    for (const [reason, rawCount] of Object.entries(deviceSuppressCounts)) {
      const count = asNumber(rawCount);
      if (count <= 0) continue;
      runtimeSuppressReasonCounts.set(
        reason,
        (runtimeSuppressReasonCounts.get(reason) ?? 0) + count,
      );
      if (packageName) {
        const packageReasonCounts = runtimeSuppressReasonByPackage.get(packageName) ?? new Map<string, number>();
        packageReasonCounts.set(reason, (packageReasonCounts.get(reason) ?? 0) + count);
        runtimeSuppressReasonByPackage.set(packageName, packageReasonCounts);
      }
    }

    if (updatedAtMs >= activeSinceMs) {
      activeDevices30d += 1;
      if (asBoolean(device.notificationsEnabled)) notificationsEnabled30d += 1;
    }
  }

  const devicesByPackage = packages
    .map((packageName) => ({
      packageName,
      count: packageCounts.get(packageName) ?? 0,
    }))
    .filter((item) => item.count > 0)
    .sort((left, right) => right.count - left.count);

  const runtimeSuppressByPackage = packages
    .map((packageName) => {
      const reasonCounts = runtimeSuppressReasonByPackage.get(packageName) ?? new Map<string, number>();
      let totalSuppressions = 0;
      let topReason = "";
      let topReasonCount = 0;
      for (const [reason, count] of reasonCounts.entries()) {
        totalSuppressions += count;
        if (count > topReasonCount) {
          topReason = reason;
          topReasonCount = count;
        }
      }
      if (totalSuppressions <= 0) return null;
      return {
        packageName,
        totalSuppressions,
        topReason,
        topReasonCount,
      };
    })
    .filter((value): value is NonNullable<typeof value> => Boolean(value))
    .sort((left, right) => right.totalSuppressions - left.totalSuppressions);

  const runtimeHealthByPackage = packages
    .map((packageName) => {
      const registration = registrationHealthByPackage.get(packageName);
      if (!registration) return null;

      const suppressions = runtimeSuppressByPackage.find((item) => item.packageName === packageName);
      const tokenCoveragePct = registration.totalDevices > 0
        ? (registration.withToken / registration.totalDevices) * 100
        : 0;
      const syncCoveragePct = registration.totalDevices > 0
        ? (registration.recentlySynced24h / registration.totalDevices) * 100
        : 0;
      const stalePct = registration.totalDevices > 0
        ? (registration.stale7d / registration.totalDevices) * 100
        : 0;
      const totalSuppressions = suppressions?.totalSuppressions ?? 0;
      const topReason = suppressions?.topReason ?? "";
      const topReasonCount = suppressions?.topReasonCount ?? 0;
      const notes: string[] = [];
      let healthStatus: "healthy" | "warning" | "critical" = "healthy";
      const applyHealthStatus = (candidate: "warning" | "critical") => {
        if (candidate === "critical" || healthStatus === "healthy") {
          healthStatus = candidate;
        }
      };

      if (tokenCoveragePct < 60) {
        applyHealthStatus("critical");
        notes.push(`Low token coverage (${tokenCoveragePct.toFixed(1)}%)`);
      } else if (tokenCoveragePct < 85) {
        applyHealthStatus("warning");
        notes.push(`Token coverage below target (${tokenCoveragePct.toFixed(1)}%)`);
      }

      if (stalePct >= 40) {
        applyHealthStatus("critical");
        notes.push(`Many stale registrations (${stalePct.toFixed(1)}%)`);
      } else if (stalePct >= 20) {
        applyHealthStatus("warning");
        notes.push(`Stale registrations need attention (${stalePct.toFixed(1)}%)`);
      }

      if (syncCoveragePct < 40 && registration.totalDevices >= 5) {
        applyHealthStatus("warning");
        notes.push(`Low recent sync coverage (${syncCoveragePct.toFixed(1)}%)`);
      }

      if (totalSuppressions > 0) {
        const normalizedReason = topReason.toLowerCase();
        if ((normalizedReason === "not_loaded" || normalizedReason === "content_in_progress") &&
          totalSuppressions >= Math.max(10, registration.totalDevices * 2)) {
          applyHealthStatus("warning");
          notes.push(`Top suppress reason is ${topReason} (${topReasonCount})`);
        } else if (topReason) {
          notes.push(`Top suppress reason: ${topReason} (${topReasonCount})`);
        }
      }

      if (notes.length === 0) {
        notes.push("No immediate runtime health concern detected.");
      }

      return {
        packageName,
        totalDevices: registration.totalDevices,
        withToken: registration.withToken,
        withoutToken: registration.withoutToken,
        tokenCoveragePct: Number(tokenCoveragePct.toFixed(1)),
        recentlySynced24h: registration.recentlySynced24h,
        syncCoveragePct: Number(syncCoveragePct.toFixed(1)),
        stale7d: registration.stale7d,
        stalePct: Number(stalePct.toFixed(1)),
        totalSuppressions,
        topReason,
        topReasonCount,
        healthStatus,
        healthNotes: notes,
      };
    })
    .filter((value): value is NonNullable<typeof value> => Boolean(value))
    .sort((left, right) => {
      const severityRank = { critical: 0, warning: 1, healthy: 2 } as const;
      return severityRank[left.healthStatus] - severityRank[right.healthStatus] ||
        right.totalSuppressions - left.totalSuppressions ||
        right.totalDevices - left.totalDevices;
    });

  const totalSuppressionsAll = Array.from(runtimeSuppressReasonCounts.values())
    .reduce((sum, count) => sum + count, 0);
  const noConsent = runtimeSuppressReasonCounts.get("no_consent") ?? 0;
  const consentError = runtimeSuppressReasonCounts.get("consent_error") ?? 0;
  const consentMissing = runtimeSuppressReasonCounts.get("consent_missing") ?? 0;
  const consentRetryBackoff = runtimeSuppressReasonCounts.get("consent_retry_backoff") ?? 0;
  const consentBlockedTotal = noConsent + consentError + consentMissing + consentRetryBackoff;
  const consentHealth = {
    totalSuppressions: totalSuppressionsAll,
    consentBlockedTotal,
    noConsent,
    consentError,
    consentMissing,
    consentRetryBackoff,
    errorOrMissing: consentError + consentMissing,
    consentBlockedPct: totalSuppressionsAll > 0
      ? round2((consentBlockedTotal / totalSuppressionsAll) * 100)
      : 0,
    errorOrMissingPct: totalSuppressionsAll > 0
      ? round2(((consentError + consentMissing) / totalSuppressionsAll) * 100)
      : 0,
  };
  const suppressMix = Array.from(runtimeSuppressReasonCounts.entries())
    .sort((left, right) => right[1] - left[1])
    .map(([reason, count]) => ({
      reason,
      count,
      sharePct: totalSuppressionsAll > 0 ? round2((count / totalSuppressionsAll) * 100) : 0,
    }));
  const rewardedFunnel = Array.from(runtimeFunnelByFormat.values())
    .filter((item) => item.format === "rewarded" || item.format === "rewarded_interstitial")
    .map((item) => ({
      ...item,
      impressionRatePct: item.showIntent > 0
        ? round2((item.showImpression / item.showIntent) * 100)
        : 0,
      blockedRatePct: item.showIntent > 0
        ? round2((item.showBlocked / item.showIntent) * 100)
        : 0,
      notLoadedRatePct: item.showIntent > 0
        ? round2((item.showNotLoaded / item.showIntent) * 100)
        : 0,
    }))
    .sort((left, right) => right.showIntent - left.showIntent);
  const stalePackages = runtimeHealthByPackage
    .filter((item) =>
      item.healthStatus !== "healthy" ||
      item.stalePct >= 20 ||
      item.syncCoveragePct < 60 ||
      item.totalSuppressions > 0
    )
    .slice(0, 12);
  const recentCoverageReports = await loadRecentCoverageReportsFromFirestore(env);
  return jsonResponse({
    totalDevices,
    activeDevices30d,
    notificationsEnabled30d,
    devicesWithToken,
    devicesWithoutToken,
    recentlySynced24h,
    staleRegistration7d,
    devicesByPackage,
    registrationHealthByPackage:
      packages
        .map((packageName) => registrationHealthByPackage.get(packageName))
        .filter((value): value is NonNullable<typeof value> => Boolean(value))
        .sort((left, right) => right.totalDevices - left.totalDevices),
    runtimeFunnelByFormat:
      Array.from(runtimeFunnelByFormat.values())
        .map(withRuntimeAdFunnelRates)
        .sort((left, right) => right.showIntent - left.showIntent),
    runtimeFunnelByPlacement:
      Array.from(runtimeFunnelByPlacement.values())
        .map(withRuntimeAdFunnelRates)
        .sort((left, right) => right.loadedButNotShown - left.loadedButNotShown),
    runtimeSuppressReasonCounts:
      Object.fromEntries(
        Array.from(runtimeSuppressReasonCounts.entries())
          .sort((left, right) => right[1] - left[1]),
      ),
    runtimeSuppressByPackage,
    runtimeHealthByPackage,
    consentHealth,
    suppressMix,
    rewardedFunnel,
    stalePackages,
    recentCoverageReports,
    loadedAt: new Date().toISOString(),
  });
}

function inferRevenueTry(record: Record<string, unknown>): number {
  const candidates = [
    record.priceAmountTry,
    record.amountTry,
    record.revenueTry,
    record.localizedPriceTry,
  ];
  for (const candidate of candidates) {
    const parsed = asNumber(candidate);
    if (parsed > 0) return parsed;
  }
  return 0;
}

async function getLatestAdPerformanceReport(
  env: Env,
): Promise<Record<string, unknown> | null> {
  const doc = await getFirestoreDoc(env, `${REPORTS_COLLECTION}/${LATEST_REPORT_DOC_ID}`);
  if (!doc) return null;
  return parseFirestoreDocument(doc);
}

function loadAdMobConfigFromEnv(env: Env): AdMobConfig | null {
  const clientId = env.ADMOB_CLIENT_ID?.trim() ?? "";
  const clientSecret = env.ADMOB_CLIENT_SECRET?.trim() ?? "";
  const refreshToken = env.ADMOB_REFRESH_TOKEN?.trim() ?? "";
  const publisher = env.ADMOB_PUBLISHER_ID?.trim() ?? "";

  if (!clientId || !clientSecret || !refreshToken) {
    return null;
  }

  const preferredAccountName = publisher
    ? (publisher.startsWith("accounts/") ? publisher : `accounts/${publisher}`)
    : undefined;

  return { clientId, clientSecret, refreshToken, preferredAccountName };
}

async function fetchAdMobAccessToken(config: AdMobConfig): Promise<string> {
  const body = new URLSearchParams({
    client_id: config.clientId,
    client_secret: config.clientSecret,
    refresh_token: config.refreshToken,
    grant_type: "refresh_token",
  });

  const response = await fetch(GOOGLE_OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  });

  const payload = (await response.json()) as { access_token?: string; error?: string; error_description?: string };
  if (!response.ok || !payload.access_token) {
    const message = payload.error_description || payload.error || `HTTP ${response.status}`;
    throw new Error(`AdMob token refresh failed: ${message}`);
  }

  return payload.access_token;
}

async function fetchAccessibleAccountNames(accessToken: string): Promise<string[]> {
  const response = await fetch(`${ADMOB_API_BASE}/accounts`, {
    method: "GET",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
  });

  const payload = (await response.json()) as
    | { account?: Array<{ name?: string }> }
    | { error?: { message?: string } };

  if (!response.ok) {
    const message = "error" in payload ? payload.error?.message : undefined;
    throw new Error(`AdMob accounts fetch failed: ${message || `HTTP ${response.status}`}`);
  }

  const accountEntries = Array.isArray((payload as { account?: Array<{ name?: string }> }).account)
    ? (payload as { account: Array<{ name?: string }> }).account
    : [];

  const accountNames = accountEntries
    .map((item: { name?: string }) => item.name?.trim() ?? "")
    .filter((value: string) => value.length > 0);

  if (accountNames.length === 0) {
    throw new Error("AdMob accounts fetch succeeded but no accessible account was returned");
  }

  return accountNames;
}

async function resolveAdMobAccountName(config: AdMobConfig, accessToken: string): Promise<string> {
  const accessibleAccounts = await fetchAccessibleAccountNames(accessToken);
  if (!config.preferredAccountName) return accessibleAccounts[0];
  if (accessibleAccounts.includes(config.preferredAccountName)) return config.preferredAccountName;
  console.warn("[admin-api] ADMOB_PUBLISHER_ID inaccessible for token, using fallback", {
    preferredAccount: config.preferredAccountName,
    fallbackAccount: accessibleAccounts[0],
  });
  return accessibleAccounts[0];
}

async function fetchAdMobNetworkRows(
  accountName: string,
  accessToken: string,
  startDate: Date,
  endDate: Date,
): Promise<NetworkRow[]> {
  const response = await fetch(`${ADMOB_API_BASE}/${accountName}/networkReport:generate`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      reportSpec: {
        dateRange: {
          startDate: toAdMobDate(startDate),
          endDate: toAdMobDate(endDate),
        },
        dimensions: ["APP", "FORMAT"],
        metrics: ["ESTIMATED_EARNINGS", "AD_REQUESTS", "MATCHED_REQUESTS", "IMPRESSIONS", "SHOW_RATE"],
      },
    }),
  });

  const payload = (await response.json()) as Array<Record<string, unknown>> | { error?: { message?: string } };
  if (!response.ok || !Array.isArray(payload)) {
    const errorMessage = !Array.isArray(payload) ? payload.error?.message : undefined;
    throw new Error(`AdMob report fetch failed: ${errorMessage || `HTTP ${response.status}`}`);
  }

  const rows: NetworkRow[] = [];
  for (const item of payload) {
    if (!("row" in item)) continue;
    const row = (item.row ?? {}) as Record<string, unknown>;
    const dimensions = (row.dimensionValues ?? {}) as Record<string, Record<string, unknown>>;
    rows.push({
      appId: String(dimensions.APP?.value ?? ""),
      appLabel: String(dimensions.APP?.displayLabel ?? dimensions.APP?.value ?? "unknown"),
      format: String(dimensions.FORMAT?.value ?? "unknown"),
      earningsMicros: readMetric(row, "ESTIMATED_EARNINGS"),
      adRequests: readMetric(row, "AD_REQUESTS"),
      matchedRequests: readMetric(row, "MATCHED_REQUESTS"),
      impressions: readMetric(row, "IMPRESSIONS"),
    });
  }
  return rows;
}

async function fetchAdMobAppVersionRows(
  accountName: string,
  accessToken: string,
  startDate: Date,
  endDate: Date,
): Promise<VersionedNetworkRow[]> {
  const response = await fetch(`${ADMOB_API_BASE}/${accountName}/networkReport:generate`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      reportSpec: {
        dateRange: {
          startDate: toAdMobDate(startDate),
          endDate: toAdMobDate(endDate),
        },
        dimensions: ["APP", "APP_VERSION_NAME", "FORMAT"],
        metrics: ["ESTIMATED_EARNINGS", "AD_REQUESTS", "MATCHED_REQUESTS", "IMPRESSIONS"],
      },
    }),
  });

  const payload = (await response.json()) as Array<Record<string, unknown>> | { error?: { message?: string } };
  if (!response.ok || !Array.isArray(payload)) {
    const errorMessage = !Array.isArray(payload) ? payload.error?.message : undefined;
    throw new Error(`AdMob app-version report fetch failed: ${errorMessage || `HTTP ${response.status}`}`);
  }

  const rows: VersionedNetworkRow[] = [];
  for (const item of payload) {
    if (!("row" in item)) continue;
    const row = (item.row ?? {}) as Record<string, unknown>;
    const dimensions = (row.dimensionValues ?? {}) as Record<string, Record<string, unknown>>;
    rows.push({
      appId: String(dimensions.APP?.value ?? ""),
      appLabel: String(dimensions.APP?.displayLabel ?? dimensions.APP?.value ?? "unknown"),
      versionName: String(dimensions.APP_VERSION_NAME?.value ?? ""),
      format: String(dimensions.FORMAT?.value ?? "unknown"),
      earningsMicros: readMetric(row, "ESTIMATED_EARNINGS"),
      adRequests: readMetric(row, "AD_REQUESTS"),
      matchedRequests: readMetric(row, "MATCHED_REQUESTS"),
      impressions: readMetric(row, "IMPRESSIONS"),
    });
  }
  return rows;
}

function normalizeAppLabel(value: string): string {
  return value
    .toLocaleLowerCase("tr-TR")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "");
}

function parseAdHealthCatalog(body: Record<string, unknown>): AdHealthCatalogEntry[] {
  const rawEntries = Array.isArray(body.catalog) ? body.catalog : [];
  return rawEntries
    .map((entry) => {
      if (!entry || typeof entry !== "object") return null;
      const packageName = asString((entry as Record<string, unknown>).packageName);
      const appName = asString((entry as Record<string, unknown>).appName);
      if (!packageName || !PACKAGE_NAME_REGEX.test(packageName) || !appName) return null;
      return { packageName, appName };
    })
    .filter((entry): entry is AdHealthCatalogEntry => entry !== null);
}

function resolvePackageForAdMobLabel(
  appLabel: string,
  catalog: AdHealthCatalogEntry[],
): string | null {
  const normalizedLabel = normalizeAppLabel(appLabel);
  if (!normalizedLabel) return null;

  const exact = catalog.find((entry) => normalizeAppLabel(entry.appName) === normalizedLabel);
  if (exact) return exact.packageName;

  const partial = catalog.find((entry) => {
    const normalizedAppName = normalizeAppLabel(entry.appName);
    return normalizedAppName.length > 0 &&
      (normalizedLabel.includes(normalizedAppName) || normalizedAppName.includes(normalizedLabel));
  });
  return partial?.packageName ?? null;
}

async function createPlayEdit(accessToken: string, packageName: string): Promise<string> {
  const response = await fetch(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/edits`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${accessToken}`,
        "content-type": "application/json",
      },
      body: "{}",
    },
  );
  const payload = (await response.json()) as { id?: string; error?: { message?: string } };
  if (!response.ok || !payload.id) {
    throw new Error(payload.error?.message || `Play edit create failed (${response.status})`);
  }
  return payload.id;
}

async function deletePlayEdit(accessToken: string, packageName: string, editId: string): Promise<void> {
  await fetch(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/edits/${encodeURIComponent(editId)}`,
    {
      method: "DELETE",
      headers: { authorization: `Bearer ${accessToken}` },
    },
  ).catch(() => undefined);
}

async function fetchPlayLiveVersionNames(
  env: Env,
  packages: string[],
): Promise<Map<string, string>> {
  const accessToken = await getGoogleAccessToken(env, GOOGLE_PLAY_ANDROID_PUBLISHER_SCOPE);
  if (!accessToken) {
    throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON for Play latest-version lookup");
  }

  const liveVersionNames = new Map<string, string>();
  for (const packageName of packages) {
    let editId = "";
    try {
      editId = await createPlayEdit(accessToken, packageName);
      const response = await fetch(
        `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/edits/${encodeURIComponent(editId)}/tracks`,
        {
          method: "GET",
          headers: { authorization: `Bearer ${accessToken}` },
        },
      );
      const payload = (await response.json()) as {
        tracks?: Array<{ releases?: Array<{ versionCodes?: Array<string | number> }> }>;
        error?: { message?: string };
      };
      if (!response.ok) {
        throw new Error(payload.error?.message || `Play tracks fetch failed (${response.status})`);
      }
      const versionCodes = (payload.tracks ?? [])
        .flatMap((track) => track.releases ?? [])
        .flatMap((release) => release.versionCodes ?? [])
        .map((value) => Number(value))
        .filter((value) => Number.isFinite(value) && value > 0);
      if (versionCodes.length === 0) {
        console.warn("[admin-api] Play latest version lookup returned no versionCodes", { packageName });
        continue;
      }
      const maxVersionCode = Math.max(...versionCodes);
      liveVersionNames.set(packageName, `1.0.${maxVersionCode}`);
    } catch (error) {
      console.warn("[admin-api] Play latest version lookup failed", { packageName, error });
    } finally {
      if (editId) {
        await deletePlayEdit(accessToken, packageName, editId);
      }
    }
  }
  return liveVersionNames;
}

async function loadWeeklyEcpmBaseline(env: Env, weeks: number): Promise<Map<string, number>> {
  const docs = await listCollectionDocuments(env, REPORTS_COLLECTION, 200);
  const historyDocs = docs
    .map((doc) => ({ id: extractDocumentId(doc), data: parseFirestoreDocument(doc) }))
    .filter((entry) => entry.id.startsWith(HISTORY_DOC_PREFIX))
    .sort((left, right) => right.id.localeCompare(left.id))
    .slice(0, 60);

  let consideredWeeks = 0;
  const accumulator = new Map<string, { total: number; count: number }>();
  for (const entry of historyDocs) {
    const stats = Array.isArray(entry.data.stats)
      ? entry.data.stats
      : (Array.isArray(entry.data.alerts) ? entry.data.alerts : []);
    if (stats.length === 0) continue;
    consideredWeeks += 1;

    for (const stat of stats) {
      if (!stat || typeof stat !== "object") continue;
      const row = stat as Record<string, unknown>;
      const appId = asString(row.appId);
      const format = asString(row.format);
      if (!appId || !format) continue;

      let ecpm = asNumber(row.ecpmTry);
      if (!Number.isFinite(ecpm) || ecpm <= 0) {
        const earningsTry = asNumber(row.earningsTry);
        const impressions = asNumber(row.impressions);
        if (!Number.isFinite(earningsTry) || !Number.isFinite(impressions) || impressions <= 0) continue;
        ecpm = (earningsTry / impressions) * 1000;
      }
      if (!Number.isFinite(ecpm) || ecpm <= 0) continue;

      const key = `${appId}::${format}`;
      const current = accumulator.get(key) ?? { total: 0, count: 0 };
      current.total += ecpm;
      current.count += 1;
      accumulator.set(key, current);
    }

    if (consideredWeeks >= weeks) break;
  }

  const baseline = new Map<string, number>();
  for (const [key, value] of accumulator.entries()) {
    if (value.count > 0) baseline.set(key, value.total / value.count);
  }
  return baseline;
}

function buildWeeklyReport(
  rangeStart: string,
  rangeEnd: string,
  thresholds: WeeklyReportPayload["thresholds"],
  rows: NetworkRow[],
  ecpmBaselineByKey: Map<string, number>,
): WeeklyReportPayload {
  const grouped = new Map<string, AggregatedStat>();
  for (const row of rows) {
    const key = `${row.appId}::${row.format}`;
    const current = grouped.get(key) ?? {
      appId: row.appId,
      appLabel: row.appLabel,
      format: row.format,
      earningsMicros: 0,
      adRequests: 0,
      matchedRequests: 0,
      impressions: 0,
    };
    current.earningsMicros += row.earningsMicros;
    current.adRequests += row.adRequests;
    current.matchedRequests += row.matchedRequests;
    current.impressions += row.impressions;
    grouped.set(key, current);
  }

  const stats = Array.from(grouped.values());
  const reportStats: ReportStat[] = stats.map((item) => ({
    appId: item.appId,
    appLabel: item.appLabel,
    format: item.format,
    adRequests: item.adRequests,
    matchedRequests: item.matchedRequests,
    impressions: item.impressions,
    earningsTry: round4(item.earningsMicros / 1_000_000),
    ecpmTry: round4(calculateEcpmTry(item.earningsMicros, item.impressions)),
  }));

  const totalsRaw = stats.reduce(
    (acc, item) => {
      acc.earningsMicros += item.earningsMicros;
      acc.adRequests += item.adRequests;
      acc.matchedRequests += item.matchedRequests;
      acc.impressions += item.impressions;
      return acc;
    },
    { earningsMicros: 0, adRequests: 0, matchedRequests: 0, impressions: 0 },
  );

  const alerts: ReportAlert[] = stats
    .filter((item) => item.adRequests >= thresholds.minRequests)
    .map((item) => {
      const fillRatePct = calculateRate(item.matchedRequests, item.adRequests);
      const showRatePct = calculateRate(item.impressions, item.matchedRequests);
      const ecpmTry = calculateEcpmTry(item.earningsMicros, item.impressions);
      const reasons: string[] = [];
      if (fillRatePct < thresholds.fillRatePct) reasons.push("low_fill_rate");
      if (showRatePct < thresholds.showRatePct) reasons.push("low_show_rate");
      if (item.format === "rewarded" && showRatePct < 1) reasons.push("rewarded_not_shown");
      if (item.adRequests > 0 && item.impressions === 0) reasons.push("requests_without_impressions");

      const key = `${item.appId}::${item.format}`;
      const baselineEcpmTry = ecpmBaselineByKey.get(key);
      if (baselineEcpmTry != null && baselineEcpmTry > 0 && ecpmTry <= baselineEcpmTry * 0.6) {
        reasons.push("ecpm_drop_spike");
      }

      return {
        appId: item.appId,
        appLabel: item.appLabel,
        format: item.format,
        adRequests: item.adRequests,
        matchedRequests: item.matchedRequests,
        impressions: item.impressions,
        fillRatePct: round2(fillRatePct),
        showRatePct: round2(showRatePct),
        earningsTry: round4(item.earningsMicros / 1_000_000),
        ecpmTry: round4(ecpmTry),
        reasons,
      };
    })
    .filter((item) => item.reasons.length > 0)
    .sort((left, right) => right.adRequests - left.adRequests);

  const formatBreakdown = buildFormatBreakdownFromRows(stats);
  const diagnosticReasonCounts = countAlertReasons(alerts);

  return {
    generatedAt: new Date().toISOString(),
    rangeStart,
    rangeEnd,
    source: "admob_api",
    status: "ok",
    thresholds,
    totals: {
      earningsTry: round4(totalsRaw.earningsMicros / 1_000_000),
      ecpmTry: round4(calculateEcpmTry(totalsRaw.earningsMicros, totalsRaw.impressions)),
      adRequests: totalsRaw.adRequests,
      matchedRequests: totalsRaw.matchedRequests,
      impressions: totalsRaw.impressions,
      fillRatePct: round2(calculateRate(totalsRaw.matchedRequests, totalsRaw.adRequests)),
      showRatePct: round2(calculateRate(totalsRaw.impressions, totalsRaw.matchedRequests)),
    },
    formatBreakdown,
    diagnosticReasonCounts,
    stats: reportStats,
    alerts,
  };
}

function toFirestoreValue(value: unknown): FirestoreField {
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number" && Number.isFinite(value)) {
    if (Number.isInteger(value)) return { integerValue: String(value) };
    return { doubleValue: value };
  }
  if (Array.isArray(value)) {
    return {
      arrayValue: {
        values: value.map((entry) => toFirestoreValue(entry)),
      },
    };
  }
  if (value && typeof value === "object") {
    const fields: Record<string, FirestoreField> = {};
    for (const [key, nested] of Object.entries(value as Record<string, unknown>)) {
      fields[key] = toFirestoreValue(nested);
    }
    return { mapValue: { fields } };
  }
  return { nullValue: null };
}

async function persistAdPerformanceReport(
  env: Env,
  report: WeeklyReportPayload,
  source: "scheduler" | "manual",
  actor?: { uid: string; email?: string },
): Promise<void> {
  const generatedDate = report.generatedAt.replace(/[:.]/g, "-");
  const metadata: Record<string, unknown> = {
    generatedBy: source,
    actorUid: actor?.uid ?? null,
    actorEmail: actor?.email ?? null,
    updatedAt: new Date().toISOString(),
  };
  const merged = { ...report, ...metadata };
  const fields: Record<string, FirestoreField> = {};
  for (const [key, value] of Object.entries(merged)) {
    fields[key] = toFirestoreValue(value);
  }

  await Promise.all([
    upsertFirestoreDoc(env, `${REPORTS_COLLECTION}/${LATEST_REPORT_DOC_ID}`, fields),
    upsertFirestoreDoc(env, `${REPORTS_COLLECTION}/${HISTORY_DOC_PREFIX}${generatedDate}`, fields),
  ]);
}

async function generateTodayReport(env: Env): Promise<TodayReportPayload> {
  const now = new Date();
  const reportBase: Omit<TodayReportPayload, "status" | "totals"> = {
    generatedAt: now.toISOString(),
    date: formatDate(now),
    source: "admob_api",
  };

  try {
    const config = loadAdMobConfigFromEnv(env);
    if (!config) {
      return {
        ...reportBase,
        status: "misconfigured",
        totals: zeroAdTotals(),
        formatBreakdown: [],
        issue: "Missing AdMob env (ADMOB_CLIENT_ID/SECRET/REFRESH_TOKEN)",
      };
    }

    const accessToken = await fetchAdMobAccessToken(config);
    const accountName = await resolveAdMobAccountName(config, accessToken);
    const rows = await fetchAdMobNetworkRows(accountName, accessToken, now, now);
    const formatBreakdown = buildFormatBreakdownFromRows(rows);

    const totalsRaw = rows.reduce(
      (acc, item) => {
        acc.earningsMicros += item.earningsMicros;
        acc.adRequests += item.adRequests;
        acc.matchedRequests += item.matchedRequests;
        acc.impressions += item.impressions;
        return acc;
      },
      { earningsMicros: 0, adRequests: 0, matchedRequests: 0, impressions: 0 },
    );

    return {
      ...reportBase,
      status: "ok",
      totals: {
        earningsTry: round4(totalsRaw.earningsMicros / 1_000_000),
        ecpmTry: round4(calculateEcpmTry(totalsRaw.earningsMicros, totalsRaw.impressions)),
        adRequests: totalsRaw.adRequests,
        matchedRequests: totalsRaw.matchedRequests,
        impressions: totalsRaw.impressions,
        fillRatePct: round2(calculateRate(totalsRaw.matchedRequests, totalsRaw.adRequests)),
        showRatePct: round2(calculateRate(totalsRaw.impressions, totalsRaw.matchedRequests)),
      },
      formatBreakdown,
    };
  } catch (error) {
    return {
      ...reportBase,
      status: "error",
      totals: zeroAdTotals(),
      formatBreakdown: [],
      issue: error instanceof Error ? error.message : "Unknown report generation error",
    };
  }
}

async function generateTodayLatestLiveReport(
  env: Env,
  catalog: AdHealthCatalogEntry[],
): Promise<TodayLatestReportPayload> {
  const now = new Date();
  const reportBase: Omit<TodayLatestReportPayload, "status" | "totals" | "liveVersionCount" | "filteredLegacyRows" | "unmappedRows" | "apps"> = {
    generatedAt: now.toISOString(),
    date: formatDate(now),
    source: "admob_api",
  };

  try {
    if (catalog.length === 0) {
      return {
        ...reportBase,
        status: "misconfigured",
        totals: zeroAdTotals(),
        liveVersionCount: 0,
        filteredLegacyRows: 0,
        unmappedRows: 0,
        formatBreakdown: [],
        apps: [],
        issue: "Missing app catalog for latest-version report",
      };
    }

    const config = loadAdMobConfigFromEnv(env);
    if (!config) {
      return {
        ...reportBase,
        status: "misconfigured",
        totals: zeroAdTotals(),
        liveVersionCount: 0,
        filteredLegacyRows: 0,
        unmappedRows: 0,
        formatBreakdown: [],
        apps: [],
        issue: "Missing AdMob env (ADMOB_CLIENT_ID/SECRET/REFRESH_TOKEN)",
      };
    }

    const accessToken = await fetchAdMobAccessToken(config);
    const accountName = await resolveAdMobAccountName(config, accessToken);
    const [rows, liveVersionNames] = await Promise.all([
      fetchAdMobAppVersionRows(accountName, accessToken, now, now),
      fetchPlayLiveVersionNames(env, catalog.map((entry) => entry.packageName)),
    ]);

    const grouped = new Map<string, {
      packageName: string;
      appLabel: string;
      liveVersionName: string;
      earningsMicros: number;
      adRequests: number;
      matchedRequests: number;
      impressions: number;
    }>();
    let filteredLegacyRows = 0;
    let unmappedRows = 0;
    const matchedRows: VersionedNetworkRow[] = [];

    for (const row of rows) {
      const packageName = resolvePackageForAdMobLabel(row.appLabel, catalog);
      if (!packageName) {
        unmappedRows += 1;
        continue;
      }
      const liveVersionName = liveVersionNames.get(packageName);
      if (!liveVersionName) continue;
      if (row.versionName != liveVersionName) {
        filteredLegacyRows += 1;
        continue;
      }
      matchedRows.push(row);

      const current = grouped.get(packageName) ?? {
        packageName,
        appLabel: row.appLabel,
        liveVersionName,
        earningsMicros: 0,
        adRequests: 0,
        matchedRequests: 0,
        impressions: 0,
      };
      current.earningsMicros += row.earningsMicros;
      current.adRequests += row.adRequests;
      current.matchedRequests += row.matchedRequests;
      current.impressions += row.impressions;
      grouped.set(packageName, current);
    }

    const apps = Array.from(grouped.values())
      .map((item) => ({
        packageName: item.packageName,
        appLabel: item.appLabel,
        liveVersionName: item.liveVersionName,
        adRequests: item.adRequests,
        matchedRequests: item.matchedRequests,
        impressions: item.impressions,
        earningsTry: round4(item.earningsMicros / 1_000_000),
        ecpmTry: round4(calculateEcpmTry(item.earningsMicros, item.impressions)),
        fillRatePct: round2(calculateRate(item.matchedRequests, item.adRequests)),
        showRatePct: round2(calculateRate(item.impressions, item.matchedRequests)),
      }))
      .sort((left, right) => right.adRequests - left.adRequests);

    const totalsRaw = apps.reduce(
      (acc, item) => {
        acc.earningsTry += item.earningsTry;
        acc.adRequests += item.adRequests;
        acc.matchedRequests += item.matchedRequests;
        acc.impressions += item.impressions;
        return acc;
      },
      { earningsTry: 0, adRequests: 0, matchedRequests: 0, impressions: 0 },
    );
    const formatBreakdown = buildFormatBreakdownFromRows(matchedRows);

    return {
      ...reportBase,
      status: "ok",
      totals: {
        earningsTry: round4(totalsRaw.earningsTry),
        ecpmTry: round4(calculateEcpmTry(Math.round(totalsRaw.earningsTry * 1_000_000), totalsRaw.impressions)),
        adRequests: totalsRaw.adRequests,
        matchedRequests: totalsRaw.matchedRequests,
        impressions: totalsRaw.impressions,
        fillRatePct: round2(calculateRate(totalsRaw.matchedRequests, totalsRaw.adRequests)),
        showRatePct: round2(calculateRate(totalsRaw.impressions, totalsRaw.matchedRequests)),
      },
      liveVersionCount: liveVersionNames.size,
      filteredLegacyRows,
      unmappedRows,
      formatBreakdown,
      apps,
    };
  } catch (error) {
    return {
      ...reportBase,
      status: "error",
      totals: zeroAdTotals(),
      liveVersionCount: 0,
      filteredLegacyRows: 0,
      unmappedRows: 0,
      formatBreakdown: [],
      apps: [],
      issue: error instanceof Error ? error.message : "Unknown latest-version report generation error",
    };
  }
}

async function generateAndStoreWeeklyReport(
  env: Env,
  source: "scheduler" | "manual",
  actor?: { uid: string; email?: string },
): Promise<WeeklyReportPayload> {
  const now = new Date();
  const rangeEndDate = addDays(now, -1);
  const rangeStartDate = addDays(rangeEndDate, -6);
  const thresholds = {
    minRequests: readThresholdEnv(env.AD_HEALTH_MIN_REQUESTS, DEFAULT_MIN_REQUESTS),
    fillRatePct: readThresholdEnv(env.AD_HEALTH_FILL_RATE_THRESHOLD, DEFAULT_FILL_THRESHOLD),
    showRatePct: readThresholdEnv(env.AD_HEALTH_SHOW_RATE_THRESHOLD, DEFAULT_SHOW_THRESHOLD),
  };

  try {
    const config = loadAdMobConfigFromEnv(env);
    if (!config) {
      const report: WeeklyReportPayload = {
        generatedAt: now.toISOString(),
        rangeStart: formatDate(rangeStartDate),
        rangeEnd: formatDate(rangeEndDate),
        source: "admob_api",
        status: "misconfigured",
        thresholds,
        totals: zeroAdTotals(),
        alerts: [],
        issue: "Missing AdMob env (ADMOB_CLIENT_ID/SECRET/REFRESH_TOKEN)",
      };
      await persistAdPerformanceReport(env, report, source, actor);
      return report;
    }

    const accessToken = await fetchAdMobAccessToken(config);
    const accountName = await resolveAdMobAccountName(config, accessToken);
    const rows = await fetchAdMobNetworkRows(accountName, accessToken, rangeStartDate, rangeEndDate);
    const ecpmBaselineByKey = await loadWeeklyEcpmBaseline(env, 4);
    const report = buildWeeklyReport(
      formatDate(rangeStartDate),
      formatDate(rangeEndDate),
      thresholds,
      rows,
      ecpmBaselineByKey,
    );
    report.today = await generateTodayReport(env);
    await persistAdPerformanceReport(env, report, source, actor);
    return report;
  } catch (error) {
    const report: WeeklyReportPayload = {
      generatedAt: now.toISOString(),
      rangeStart: formatDate(rangeStartDate),
      rangeEnd: formatDate(rangeEndDate),
      source: "admob_api",
      status: "error",
      thresholds,
      totals: zeroAdTotals(),
      alerts: [],
      issue: error instanceof Error ? error.message : "Unknown report generation error",
    };
    report.today = await generateTodayReport(env);
    await persistAdPerformanceReport(env, report, source, actor);
    return report;
  }
}

async function handleAdminGetRevenueSummary(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  let admin: ResolvedAdmin | null;
  try {
    admin = await ensureAdminAccess(request, env);
  } catch (error) {
    console.warn("[admin-api] adminGetRevenueSummary auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
  if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);
  if (!admin.authorized) return jsonResponse({ error: "User is not in admins whitelist" }, 403);

  const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
  const catalog = parseAdHealthCatalog(body);
  const purchaseDocs = await listCollectionDocuments(env, "purchase_verifications", 1000);
  const verifiedRecords = purchaseDocs
    .map((doc) => parseFirestoreDocument(doc))
    .filter((item) => asBoolean(item.verified));
  const latestReportData = await getLatestAdPerformanceReport(env);
  const liveTodayLatestReport = await generateTodayLatestLiveReport(env, catalog);

  const monthStart = new Date();
  monthStart.setDate(1);
  monthStart.setHours(0, 0, 0, 0);
  const nowMs = Date.now();

  const purchasesByPackage = new Map<string, { count: number; revenueTry: number }>();
  let activeSubscriptions = 0;
  let verifiedSubscriptions = 0;
  let verifiedInAppPurchases = 0;
  let monthlyVerifiedPurchases = 0;
  let monthlyVerifiedRevenueTry = 0;

  for (const record of verifiedRecords) {
    const packageName = asString(record.packageName) || "unknown";
    const purchaseType = asString(record.purchaseType) || "inapp";
    const updatedAt = parseMillis(record.updatedAt);
    const expiryTimeMillis = parseMillis(record.expiryTimeMillis);
    const revenueTry = inferRevenueTry(record);

    if (purchaseType === "subs") {
      verifiedSubscriptions += 1;
      if (expiryTimeMillis !== null && expiryTimeMillis > nowMs) {
        activeSubscriptions += 1;
      }
    } else {
      verifiedInAppPurchases += 1;
    }

    if (updatedAt !== null && updatedAt >= monthStart.getTime()) {
      monthlyVerifiedPurchases += 1;
      monthlyVerifiedRevenueTry += revenueTry;
    }

    const current = purchasesByPackage.get(packageName) ?? { count: 0, revenueTry: 0 };
    current.count += 1;
    current.revenueTry += revenueTry;
    purchasesByPackage.set(packageName, current);
  }

  const totals = latestReportData && typeof latestReportData.totals === "object"
    ? (latestReportData.totals as Record<string, unknown>)
    : null;
  const alerts = Array.isArray(latestReportData?.alerts) ? latestReportData.alerts : [];
  const adRevenueRangeTry = totals ? asNumber(totals.earningsTry) : 0;
  const adRevenueTodayTry = asNumber(liveTodayLatestReport.totals.earningsTry);
  const weeklyGeneratedAt = asString(latestReportData?.generatedAt) || undefined;
  const liveTodayGeneratedAt = liveTodayLatestReport.generatedAt;
  const liveTodayAgeHours = computeAgeHours(liveTodayGeneratedAt);
  const liveTodayFreshWithinHours = liveTodayAgeHours !== null && liveTodayAgeHours <= 24;
  const sourceFreshnessStatus =
    liveTodayLatestReport.status === "ok"
      ? (liveTodayFreshWithinHours ? "live" : "stale")
      : "diagnostics_only";
  const sourceFreshnessIssues: string[] = [];
  if (liveTodayLatestReport.status !== "ok" && liveTodayLatestReport.issue) {
    sourceFreshnessIssues.push(liveTodayLatestReport.issue);
  }
  if (!weeklyGeneratedAt) {
    sourceFreshnessIssues.push("Weekly AdMob rollup document missing.");
  }

  return jsonResponse({
    activeSubscriptions,
    verifiedSubscriptions,
    verifiedInAppPurchases,
    monthlyVerifiedPurchases,
    monthlyVerifiedRevenueTry,
    adRevenueTodayTry,
    adRevenueRangeTry,
    totalTrackedRevenueTry: monthlyVerifiedRevenueTry + adRevenueRangeTry,
    purchasesByPackage: Array.from(purchasesByPackage.entries())
      .map(([packageName, value]) => ({
        packageName,
        count: value.count,
        revenueTry: value.revenueTry,
      }))
      .sort((left, right) => right.revenueTry - left.revenueTry || right.count - left.count)
      .slice(0, 12),
    adAlerts: alerts,
    reportGeneratedAt: asString(latestReportData?.generatedAt) || undefined,
    reportRangeLabel:
      asString(latestReportData?.rangeStart) && asString(latestReportData?.rangeEnd)
        ? `${asString(latestReportData?.rangeStart)} → ${asString(latestReportData?.rangeEnd)}`
        : undefined,
    revenueSource: "admob_network_report",
    latestAdmobTodayDate: liveTodayLatestReport.date,
    deliveryRatiosByPackage: liveTodayLatestReport.apps,
    sourceFreshness: {
      status: sourceFreshnessStatus,
      liveTodayGeneratedAt,
      liveTodayStatus: liveTodayLatestReport.status,
      liveTodayAgeHours,
      liveTodayFreshWithinHours,
      weeklyReportGeneratedAt: weeklyGeneratedAt,
      weeklyRangeLabel:
        asString(latestReportData?.rangeStart) && asString(latestReportData?.rangeEnd)
          ? `${asString(latestReportData?.rangeStart)} → ${asString(latestReportData?.rangeEnd)}`
          : undefined,
      issues: sourceFreshnessIssues,
    },
    loadedAt: new Date().toISOString(),
  });
}

function normalizeWeeklyReport(
  raw: Record<string, unknown> | null,
): Record<string, unknown> {
  if (!raw) {
    return {
      generatedAt: new Date().toISOString(),
      rangeStart: "",
      rangeEnd: "",
      status: "misconfigured",
      thresholds: { minRequests: 500, fillRatePct: 55, showRatePct: 20 },
      totals: zeroAdTotals(),
      formatBreakdown: [],
      diagnosticReasonCounts: {},
      alerts: [],
      issue: "No ad performance report found yet.",
    };
  }

  return {
    generatedAt: asString(raw.generatedAt) || new Date().toISOString(),
    rangeStart: asString(raw.rangeStart),
    rangeEnd: asString(raw.rangeEnd),
    status: (asString(raw.status) as "ok" | "misconfigured" | "error") || "error",
    thresholds: (raw.thresholds && typeof raw.thresholds === "object")
      ? raw.thresholds
      : { minRequests: 500, fillRatePct: 55, showRatePct: 20 },
    totals: (raw.totals && typeof raw.totals === "object")
      ? raw.totals
      : zeroAdTotals(),
    formatBreakdown: Array.isArray(raw.formatBreakdown) ? raw.formatBreakdown : [],
    diagnosticReasonCounts:
      (raw.diagnosticReasonCounts && typeof raw.diagnosticReasonCounts === "object")
        ? raw.diagnosticReasonCounts
        : {},
    alerts: Array.isArray(raw.alerts) ? raw.alerts : [],
    issue: asString(raw.issue) || undefined,
    today: raw.today,
  };
}


async function handleAdPerformance(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  let admin: ResolvedAdmin | null;
  try {
    admin = await ensureAdminAccess(request, env);
  } catch (error) {
    console.warn("[admin-api] adPerformance auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
  if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);
  if (!admin.authorized) return jsonResponse({ error: "User is not in admins whitelist" }, 403);

  const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
  const requestType = asString(body.type) || "report";

  if (requestType === "today") {
    const todayReport = await generateTodayReport(env);
    return jsonResponse(todayReport);
  }

  if (requestType === "today_latest") {
    const latestLiveReport = await generateTodayLatestLiveReport(env, parseAdHealthCatalog(body));
    return jsonResponse(latestLiveReport);
  }

  if (requestType === "refresh") {
    const weeklyReport = await generateAndStoreWeeklyReport(env, "manual", {
      uid: admin.uid,
      email: admin.email,
    });
    return jsonResponse(weeklyReport);
  }

  const latestRaw = await getLatestAdPerformanceReport(env);
  return jsonResponse(normalizeWeeklyReport(latestRaw));
}

function parseDeliveryTime(timeStr: string): { hour: number; minute: number } {
  const [hourStr, minuteStr = "0"] = timeStr.split(":");
  const hour = Number.parseInt(hourStr, 10);
  const minute = Number.parseInt(minuteStr, 10);
  if (!Number.isFinite(hour) || hour < 0 || hour > 23) {
    throw new Error(`Invalid delivery hour: ${timeStr}`);
  }
  if (!Number.isFinite(minute) || minute < 0 || minute > 59) {
    throw new Error(`Invalid delivery minute: ${timeStr}`);
  }
  return { hour, minute };
}

function getLocalClock(timezoneId: string, now: Date): { hour: number; minute: number } {
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: timezoneId,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = formatter.formatToParts(now);
  const hour = Number.parseInt(parts.find((part) => part.type === "hour")?.value ?? "", 10);
  const minute = Number.parseInt(parts.find((part) => part.type === "minute")?.value ?? "", 10);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    throw new Error(`Failed to resolve local clock for timezone: ${timezoneId}`);
  }
  return { hour, minute };
}

function getLocalDate(timezoneId: string, now: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: timezoneId,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

function getLocalDayOfWeek(timezoneId: string, now: Date): number {
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: timezoneId,
    weekday: "short",
  });
  const dayMap: Record<string, number> = {
    Sun: 0,
    Mon: 1,
    Tue: 2,
    Wed: 3,
    Thu: 4,
    Fri: 5,
    Sat: 6,
  };
  return dayMap[formatter.format(now)] ?? -1;
}

function getMatchingTimezones(deliveryTime: string, now: Date, windowMinutes = 60): string[] {
  const { hour: targetHour, minute: targetMinute } = parseDeliveryTime(deliveryTime);
  const targetTotalMinutes = targetHour * 60 + targetMinute;
  const safeWindow = Math.max(1, windowMinutes);

  return ALL_TIMEZONES.filter((timezoneId) => {
    const localClock = getLocalClock(timezoneId, now);
    const localTotalMinutes = localClock.hour * 60 + localClock.minute;
    if (localTotalMinutes < targetTotalMinutes) {
      return false;
    }
    const elapsedMinutes = localTotalMinutes - targetTotalMinutes;
    return elapsedMinutes < safeWindow;
  });
}

function recurrencePeriodMillis(recurrence: string): number | null {
  if (recurrence === "daily") return 24 * 60 * 60 * 1000;
  if (recurrence.startsWith("weekly:")) return 7 * 24 * 60 * 60 * 1000;
  return null;
}

function isRecurrenceResetDue(
  recurrence: string,
  lastResetMillis: number | undefined,
  nowMillis: number,
): boolean {
  const periodMillis = recurrencePeriodMillis(recurrence);
  if (periodMillis == null || lastResetMillis == null) return false;
  return nowMillis - lastResetMillis >= periodMillis;
}

function matchesRecurrence(recurrence: string, timezone: string, now: Date): boolean {
  if (recurrence === "daily") {
    return true;
  }
  if (recurrence.startsWith("weekly:")) {
    const dayName = recurrence.split(":")[1];
    const dayMap: Record<string, number> = {
      sunday: 0,
      monday: 1,
      tuesday: 2,
      wednesday: 3,
      thursday: 4,
      friday: 5,
      saturday: 6,
    };
    const targetDay = dayMap[dayName];
    const currentDay = getLocalDayOfWeek(timezone, now);
    return targetDay === currentDay;
  }
  return false;
}

function normalizeTargetTimezones(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return Array.from(
    new Set(
      value
        .filter((item): item is string => typeof item === "string")
        .map((item) => item.trim())
        .filter((item) => item.length > 0),
    ),
  );
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter(Boolean);
}

function asStringMap(value: unknown): Record<string, string> {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const result: Record<string, string> = {};
  for (const [key, mapValue] of Object.entries(value as Record<string, unknown>)) {
    if (typeof mapValue === "string") {
      result[key] = mapValue;
    }
  }
  return result;
}

async function listScheduledEvents(env: Env): Promise<ScheduledEvent[]> {
  const docs = await runFirestoreQuery(env, {
    from: [{ collectionId: "scheduled_events" }],
    where: {
      fieldFilter: {
        field: { fieldPath: "status" },
        op: "EQUAL",
        value: { stringValue: "scheduled" },
      },
    },
  });

  return docs.map((doc) => {
    const item = parseFirestoreDocument(doc);
    return {
      id: extractDocumentId(doc),
      type: asString(item.type),
      name: asString(item.name),
      date: asString(item.date) || undefined,
      recurrence: asString(item.recurrence) || undefined,
      localDeliveryTime: asString(item.localDeliveryTime) || "09:00",
      targetTimezones: normalizeTargetTimezones(item.targetTimezones),
      topic: asString(item.topic) || undefined,
      packages: asStringArray(item.packages),
      title: asStringMap(item.title),
      body: asStringMap(item.body),
      status: asString(item.status),
      sentTimezones: asStringArray(item.sentTimezones),
      lastResetAtMs: parseMillis(item.lastResetAt) ?? undefined,
    };
  });
}

async function resetEventSentTimezonesIfNeeded(
  env: Env,
  event: ScheduledEvent,
  now: Date,
): Promise<ScheduledEvent> {
  if (!event.recurrence) return event;

  const nowMillis = now.getTime();
  const shouldReset = isRecurrenceResetDue(event.recurrence, event.lastResetAtMs, nowMillis);
  if (!shouldReset && event.lastResetAtMs) {
    return event;
  }

  const fields: Record<string, FirestoreField> = {
    lastResetAt: { timestampValue: new Date(nowMillis).toISOString() },
  };
  const updateMask: string[] = ["lastResetAt"];
  if (shouldReset) {
    fields.sentTimezones = toFirestoreValue([] as unknown[]);
    updateMask.push("sentTimezones");
  }

  await upsertFirestoreDoc(env, `scheduled_events/${encodeURIComponent(event.id)}`, fields, updateMask);
  return {
    ...event,
    sentTimezones: shouldReset ? [] : event.sentTimezones,
    lastResetAtMs: nowMillis,
  };
}

async function queryDevicesByTimezones(
  env: Env,
  timezones: string[],
): Promise<Array<{ token: string; locale: string; packageName: string }>> {
  const devices: Array<{ token: string; locale: string; packageName: string }> = [];
  const seenTokens = new Set<string>();
  for (let i = 0; i < timezones.length; i += FIRESTORE_IN_QUERY_LIMIT) {
    const timezoneChunk = timezones.slice(i, i + FIRESTORE_IN_QUERY_LIMIT);
    const docs = await runFirestoreQuery(env, {
      from: [{ collectionId: "devices" }],
      where: {
        compositeFilter: {
          op: "AND",
          filters: [
            {
              fieldFilter: {
                field: { fieldPath: "timezone" },
                op: "IN",
                value: { arrayValue: { values: timezoneChunk.map((tz) => ({ stringValue: tz })) } },
              },
            },
            {
              fieldFilter: {
                field: { fieldPath: "notificationsEnabled" },
                op: "EQUAL",
                value: { booleanValue: true },
              },
            },
          ],
        },
      },
    });

    for (const doc of docs) {
      const fields = doc.fields;
      const token = parseStringField(fields, "fcmToken")?.trim() ?? "";
      if (!TOKEN_REGEX.test(token) || seenTokens.has(token)) continue;
      seenTokens.add(token);
      devices.push({
        token,
        locale: parseStringField(fields, "locale") ?? "tr-TR",
        packageName: parseStringField(fields, "packageName") ?? "",
      });
    }
  }
  return devices;
}

async function getDevicesForTimezones(
  env: Env,
  timezones: string[],
  packages: string[],
): Promise<Array<{ token: string; locale: string }>> {
  const hasPackageFilter = packages.length > 0 && !packages.includes("*");
  const allowedPackages = hasPackageFilter ? new Set(packages) : null;
  const rawDevices = await queryDevicesByTimezones(env, timezones);
  return rawDevices
    .filter((device) => !allowedPackages || allowedPackages.has(device.packageName))
    .map((device) => ({ token: device.token, locale: device.locale }));
}

function groupByLocale(devices: Array<{ token: string; locale: string }>): Record<string, string[]> {
  const groups: Record<string, string[]> = {};
  for (const device of devices) {
    const locale = device.locale || "tr-TR";
    if (!groups[locale]) groups[locale] = [];
    groups[locale].push(device.token);
  }
  return groups;
}

function isInvalidTokenError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? "");
  const lower = message.toLowerCase();
  return lower.includes("registration-token-not-registered")
    || lower.includes("invalid-registration-token")
    || lower.includes("invalid argument")
    || lower.includes("unregistered");
}

async function sendToTokens(
  env: Env,
  tokens: string[],
  notification: { title: string; body: string },
  data: Record<string, string>,
): Promise<{ successCount: number; failureCount: number; invalidTokens: string[] }> {
  if (tokens.length === 0) {
    return { successCount: 0, failureCount: 0, invalidTokens: [] };
  }

  let totalSuccess = 0;
  let totalFailure = 0;
  const invalidTokens: string[] = [];

  for (let i = 0; i < tokens.length; i += FCM_BATCH_SIZE) {
    const batch = tokens.slice(i, i + FCM_BATCH_SIZE);
    for (const token of batch) {
      try {
        await sendFcmMessage(env, token, {
          title: notification.title,
          body: notification.body,
          data,
          useNotificationPayload: true,
        });
        totalSuccess += 1;
      } catch (error) {
        totalFailure += 1;
        if (isInvalidTokenError(error)) invalidTokens.push(token);
      }
    }
  }

  return { successCount: totalSuccess, failureCount: totalFailure, invalidTokens };
}

async function cleanupInvalidTokens(env: Env, invalidTokens: string[]): Promise<void> {
  for (const token of invalidTokens) {
    const docs = await runFirestoreQuery(env, {
      from: [{ collectionId: "devices" }],
      where: {
        fieldFilter: {
          field: { fieldPath: "fcmToken" },
          op: "EQUAL",
          value: { stringValue: token },
        },
      },
    });
    await Promise.all(
      docs.map((doc) => deleteFirestoreDoc(env, `devices/${encodeURIComponent(extractDocumentId(doc))}`)),
    );
  }
}

async function markEventTimezonesAsSent(
  env: Env,
  event: ScheduledEvent,
  newlySent: string[],
): Promise<void> {
  const mergedSent = Array.from(new Set([...(event.sentTimezones ?? []), ...newlySent]));
  const targetTimezones = normalizeTargetTimezones(event.targetTimezones);
  const requiredTimezoneCount =
    targetTimezones.length > 0 ? targetTimezones.length : ALL_TIMEZONES.length;

  const fields: Record<string, FirestoreField> = {
    sentTimezones: toFirestoreValue(mergedSent),
  };
  const updateMask = ["sentTimezones"];

  if (!event.recurrence && mergedSent.length >= requiredTimezoneCount) {
    fields.status = { stringValue: "sent" };
    updateMask.push("status");
  }

  await upsertFirestoreDoc(env, `scheduled_events/${encodeURIComponent(event.id)}`, fields, updateMask);
}

async function processScheduledEvent(env: Env, event: ScheduledEvent): Promise<void> {
  const now = new Date();
  const effectiveEvent = await resetEventSentTimezonesIfNeeded(env, event, now);
  const targetTimezones = normalizeTargetTimezones(effectiveEvent.targetTimezones);

  const matchingTimezones = getMatchingTimezones(
    effectiveEvent.localDeliveryTime,
    now,
    DELIVERY_WINDOW_MINUTES,
  ).filter((tz) => targetTimezones.length === 0 || targetTimezones.includes(tz));

  const unsentTimezones = matchingTimezones.filter((tz) => !effectiveEvent.sentTimezones.includes(tz));
  if (unsentTimezones.length === 0) return;

  const eligibleTimezones = unsentTimezones.filter((timezone) => {
    if (effectiveEvent.date) return getLocalDate(timezone, now) === effectiveEvent.date;
    if (effectiveEvent.recurrence) return matchesRecurrence(effectiveEvent.recurrence, timezone, now);
    return true;
  });
  if (eligibleTimezones.length === 0) return;

  const devices = await getDevicesForTimezones(env, eligibleTimezones, effectiveEvent.packages);
  if (devices.length === 0) {
    await markEventTimezonesAsSent(env, effectiveEvent, eligibleTimezones);
    return;
  }

  const localeGroups = groupByLocale(devices);
  const invalidTokens: string[] = [];
  for (const [locale, tokens] of Object.entries(localeGroups)) {
    const lang = locale.split("-")[0];
    const title = effectiveEvent.title[lang] ?? effectiveEvent.title.tr ?? effectiveEvent.name;
    const body = effectiveEvent.body[lang] ?? effectiveEvent.body.tr ?? "";
    const result = await sendToTokens(env, tokens, { title, body }, {
      type: effectiveEvent.type,
      eventId: effectiveEvent.id,
    });
    invalidTokens.push(...result.invalidTokens);
  }

  if (invalidTokens.length > 0) {
    await cleanupInvalidTokens(env, Array.from(new Set(invalidTokens)));
  }

  await markEventTimezonesAsSent(env, effectiveEvent, eligibleTimezones);
}

async function dispatchScheduledNotifications(env: Env): Promise<void> {
  const events = await listScheduledEvents(env);
  if (events.length === 0) {
    console.info("[admin-api] dispatchNotifications: no scheduled events");
    return;
  }

  for (const event of events) {
    try {
      await processScheduledEvent(env, event);
    } catch (error) {
      console.error("[admin-api] dispatchNotifications event failed", { eventId: event.id, error });
    }
  }
}

function cloneParameterGroups(
  source: RemoteConfigTemplate["parameterGroups"],
): NonNullable<RemoteConfigTemplate["parameterGroups"]> {
  const result: NonNullable<RemoteConfigTemplate["parameterGroups"]> = {};
  for (const [groupName, group] of Object.entries(source ?? {})) {
    result[groupName] = {
      ...group,
      parameters: { ...(group.parameters ?? {}) },
    };
  }
  return result;
}

function resolveParameterLocation(
  template: RemoteConfigTemplate,
  key: string,
  requestedGroupName: string,
): { groupName?: string; parameter: RemoteConfigParameter } {
  if (requestedGroupName) {
    const requestedGroup = template.parameterGroups?.[requestedGroupName];
    return {
      groupName: requestedGroupName,
      parameter: requestedGroup?.parameters?.[key] ?? {},
    };
  }

  const rootParameter = template.parameters?.[key];
  if (rootParameter) {
    return { parameter: rootParameter };
  }

  for (const [groupName, group] of Object.entries(template.parameterGroups ?? {})) {
    const existing = group.parameters?.[key];
    if (existing) {
      return { groupName, parameter: existing };
    }
  }

  return { parameter: {} };
}

async function fetchRemoteConfigTemplate(
  env: Env,
  accessToken: string,
): Promise<{ response: Response; etag?: string }> {
  const projectId = env.FIREBASE_PROJECT_ID.trim();
  const response = await fetch(`${REMOTE_CONFIG_API_BASE}/projects/${projectId}/remoteConfig`, {
    method: "GET",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "accept-encoding": "gzip",
    },
  });
  return { response, etag: response.headers.get("etag") ?? undefined };
}

async function handleAdminGetRemoteConfig(request: Request, env: Env): Promise<Response> {
  if (request.method !== "GET") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  let admin: ResolvedAdmin | null;
  try {
    admin = await ensureAdminAccess(request, env);
  } catch (error) {
    console.warn("[admin-api] adminGetRemoteConfig auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
  if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);
  if (!admin.authorized) return jsonResponse({ error: "User is not in admins whitelist" }, 403);

  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) {
    return jsonResponse({ error: "Missing FIREBASE_SERVICE_ACCOUNT_JSON" }, 500);
  }

  const { response, etag } = await fetchRemoteConfigTemplate(env, accessToken);
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    console.error("[admin-api] Remote Config fetch failed", response.status, body);
    return jsonResponse({ error: `Remote Config API: HTTP ${response.status}` }, 502);
  }

  const template = (await response.json()) as RemoteConfigTemplate;
  return jsonResponse({
    parameters: template.parameters ?? {},
    parameterGroups: template.parameterGroups ?? {},
    conditions: template.conditions ?? [],
    version: template.version ?? null,
    etag: etag ?? null,
  });
}

async function handleAdminUpdateRemoteConfig(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  let admin: ResolvedAdmin | null;
  try {
    admin = await ensureAdminAccess(request, env);
  } catch (error) {
    console.warn("[admin-api] adminUpdateRemoteConfig auth failed", error);
    return jsonResponse({ error: "Invalid Firebase Auth token" }, 401);
  }
  if (!admin) return jsonResponse({ error: "Missing Bearer token" }, 401);
  if (!admin.authorized) return jsonResponse({ error: "User is not in admins whitelist" }, 403);

  const body = (await request.json().catch(() => ({}))) as Record<string, unknown>;
  const key = typeof body.key === "string" ? body.key.trim() : "";
  const value = typeof body.value === "string" ? body.value : "";
  const description = typeof body.description === "string" ? body.description : undefined;
  const requestedGroupName = typeof body.groupName === "string" ? body.groupName.trim() : "";

  if (!key) {
    return jsonResponse({ error: "key is required" }, 400);
  }
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) {
    return jsonResponse({ error: "Invalid Remote Config key format" }, 400);
  }

  const accessToken = await getGoogleAccessToken(env);
  if (!accessToken) {
    return jsonResponse({ error: "Missing FIREBASE_SERVICE_ACCOUNT_JSON" }, 500);
  }

  const prefetched = await fetchRemoteConfigTemplate(env, accessToken);
  if (!prefetched.response.ok) {
    const bodyText = await prefetched.response.text().catch(() => "");
    console.error("[admin-api] Remote Config prefetch failed", prefetched.response.status, bodyText);
    return jsonResponse({ error: `Remote Config API: HTTP ${prefetched.response.status}` }, 502);
  }

  const etag = prefetched.etag ?? "*";
  const currentTemplate = (await prefetched.response.json()) as RemoteConfigTemplate;
  const parameters = { ...(currentTemplate.parameters ?? {}) };
  const parameterGroups = cloneParameterGroups(currentTemplate.parameterGroups);
  const resolved = resolveParameterLocation(currentTemplate, key, requestedGroupName);

  const updatedParameter: RemoteConfigParameter = {
    ...resolved.parameter,
    ...(description !== undefined ? { description } : {}),
    defaultValue: { value },
  };

  if (resolved.groupName) {
    const existingGroup = parameterGroups[resolved.groupName] ?? {};
    parameterGroups[resolved.groupName] = {
      ...existingGroup,
      parameters: {
        ...(existingGroup.parameters ?? {}),
        [key]: updatedParameter,
      },
    };
    delete parameters[key];
  } else {
    parameters[key] = updatedParameter;
  }

  const projectId = env.FIREBASE_PROJECT_ID.trim();
  const updateResponse = await fetch(`${REMOTE_CONFIG_API_BASE}/projects/${projectId}/remoteConfig`, {
    method: "PUT",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json; charset=utf-8",
      "if-match": etag,
    },
    body: JSON.stringify({
      parameters,
      parameterGroups,
      conditions: currentTemplate.conditions ?? [],
    }),
  });

  if (!updateResponse.ok) {
    const bodyText = await updateResponse.text().catch(() => "");
    console.error("[admin-api] Remote Config update failed", updateResponse.status, bodyText);
    return jsonResponse({ error: `Remote Config API: HTTP ${updateResponse.status}` }, 502);
  }

  return jsonResponse({ success: true, key, groupName: resolved.groupName ?? null });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const preflight = handlePreflight(request, env);
    if (preflight) return preflight;

    const origin = request.headers.get("origin");
    const clientIp = request.headers.get("cf-connecting-ip") ?? "unknown";
    if (isRateLimited(clientIp)) {
      return jsonResponse(
        { error: "Too many requests. Please try again later." },
        429,
        withCors({ "retry-after": "60" }, origin, env),
      );
    }

    const path = new URL(request.url).pathname;
    let response: Response;

    try {
      if (path === "/healthCheck" || path === "/health") {
        response = handleHealthCheck(request, {
          environment: env.SERVICE_ENVIRONMENT,
          gitSha: env.SERVICE_GIT_SHA,
          builtAt: env.SERVICE_BUILD_TIMESTAMP,
          workerVersion: env.CF_VERSION_METADATA,
        });
      } else if (path === "/adminAccessCheck") {
        response = await handleAdminAccessCheck(request, env);
      } else if (path === "/adminGetFlavorHubSummary") {
        response = await handleAdminGetFlavorHubSummary(request, env);
      } else if (path === "/adminGetAnalyticsSummary") {
        response = await handleAdminGetAnalyticsSummary(request, env);
      } else if (path === "/adminGetRevenueSummary") {
        response = await handleAdminGetRevenueSummary(request, env);
      } else if (path === "/adPerformance") {
        response = await handleAdPerformance(request, env);
      } else if (path === "/adminGetRemoteConfig") {
        response = await handleAdminGetRemoteConfig(request, env);
      } else if (path === "/adminUpdateRemoteConfig") {
        response = await handleAdminUpdateRemoteConfig(request, env);
      } else if (path === "/sendTestNotification") {
        response = await handleSendTestNotification(request, env);
      } else if (path === "/deviceCoverageReport") {
        response = await handleDeviceCoverageReport(request, env);
      } else if (path === "/registerDevice" || path === "/register-device") {
        response = await handleRegisterDevice(request, env);
      } else if (
        path === "/verifyPurchase" ||
        path === "/verify-purchase" ||
        path === "/api/verify-purchase"
      ) {
        response = await handleVerifyPurchase(request, env);
      } else {
        response = jsonResponse({ error: "Not found" }, 404);
      }
    } catch (error) {
      console.error("[admin-api] unhandled error", error);
      response = jsonResponse({ error: "Internal server error" }, 500);
    }

    const headers = new Headers(response.headers);
    const corsHeaders = withCors({}, origin, env);
    Object.entries(corsHeaders).forEach(([key, value]) => headers.set(key, value));
    headers.set("x-content-type-options", "nosniff");
    headers.set("x-frame-options", "DENY");
    headers.set("referrer-policy", "strict-origin-when-cross-origin");
    return new Response(response.body, {
      status: response.status,
      headers,
    });
  },
  async scheduled(controller: ScheduledController, env: Env): Promise<void> {
    try {
      if (controller.cron === SCHEDULE_CRON_DISPATCH_NOTIFICATIONS) {
        await dispatchScheduledNotifications(env);
        return;
      }
      if (controller.cron === SCHEDULE_CRON_WEEKLY_AD_REPORT) {
        await generateAndStoreWeeklyReport(env, "scheduler");
      }
    } catch (error) {
      console.error("[admin-api] scheduled handler failed", { cron: controller.cron, error });
    }
  },
};
