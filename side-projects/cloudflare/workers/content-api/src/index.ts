export interface Env {
  OTHER_APPS_JSON_URL: string;
  AUDIO_BUCKET: R2Bucket;
  GOOGLE_RECAPTCHA_SECRET_KEY: string;
  SERVICE_ENVIRONMENT?: string;
  SERVICE_GIT_SHA?: string;
  SERVICE_BUILD_TIMESTAMP?: string;
}

type AudioManifestItem = {
  key: string;
  url: string;
  size?: number;
  etag?: string;
  uploaded?: string;
};

type AudioManifest = {
  generatedAt: string;
  baseAudioUrl: string;
  packageAudio: Record<string, string>;
  files: AudioManifestItem[];
};

const PACKAGE_AUDIO: Record<string, string> = {
  "com.parsfilo.amenerrasulu": "amenerrasulu.mp3",
  "com.parsfilo.ayetelkursi": "ayetelkursi.mp3",
  "com.parsfilo.bereketduasi": "bereketduasi.mp3",
  "com.parsfilo.esmaulhusna": "esmaulhusna.mp3",
  "com.parsfilo.fetihsuresi": "fetihsuresi.mp3",
  "com.parsfilo.insirahsuresi": "insirahsuresi.mp3",
  "com.parsfilo.ismiazamduasi": "ismiazamduasi.mp3",
  "com.parsfilo.kenzularsduasi": "kenzularsduasi.mp3",
  "com.parsfilo.namazsurelerivedualarsesli": "fatiha.mp3",
  "com.parsfilo.nazarayeti": "nazarayeti.mp3",
  "com.parsfilo.vakiasuresi": "vakiasuresi.mp3",
  "com.parsfilo.yasinsuresi": "yasinsuresi.mp3",
};

/* ═══════════════════════════════════════
 *  Rate Limiter (in-memory, per-isolate)
 * ═══════════════════════════════════════ */
const RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute
const RATE_LIMIT_MAX_REQUESTS = 60;   // max 60 requests per IP per minute

type RateLimitEntry = { count: number; windowStart: number };
const rateLimitMap = new Map<string, RateLimitEntry>();

function isRateLimited(clientIp: string): boolean {
  const now = Date.now();
  const entry = rateLimitMap.get(clientIp);
  if (!entry || now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
    rateLimitMap.set(clientIp, { count: 1, windowStart: now });
    return false;
  }
  entry.count++;
  if (entry.count > RATE_LIMIT_MAX_REQUESTS) {
    return true;
  }
  return false;
}

// Periodically clean stale entries (limit map growth)
function pruneRateLimitMap() {
  const now = Date.now();
  for (const [key, entry] of rateLimitMap) {
    if (now - entry.windowStart > RATE_LIMIT_WINDOW_MS * 2) {
      rateLimitMap.delete(key);
    }
  }
}

/* ═══════════════════════════════════════
 *  Security & CORS Headers
 * ═══════════════════════════════════════ */
const ALLOWED_ORIGINS = [
  "https://admin-notifications.web.app",
  "https://admin-notifications.firebaseapp.com",
  "http://localhost:5173",
  "http://localhost:4173",
];

function getSecurityHeaders(origin?: string | null): Record<string, string> {
  const headers: Record<string, string> = {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "X-XSS-Protection": "0",
    "Referrer-Policy": "strict-origin-when-cross-origin",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
  };
  if (origin && ALLOWED_ORIGINS.includes(origin)) {
    headers["Access-Control-Allow-Origin"] = origin;
    headers["Vary"] = "Origin";
  }
  return headers;
}

function handleCorsPreflightRequest(request: Request): Response | null {
  if (request.method !== "OPTIONS") return null;
  const origin = request.headers.get("Origin");
  if (!origin || !ALLOWED_ORIGINS.includes(origin)) {
    return new Response(null, { status: 403 });
  }
  return new Response(null, {
    status: 204,
    headers: {
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Access-Control-Max-Age": "86400",
      "Vary": "Origin",
    },
  });
}

function json(data: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "public, max-age=300",
      ...headers,
    },
  });
}


// ── Other Apps Feed (Play Store icon enrichment) ──

type OtherApp = {
  appName: string;
  packageName: string;
  appIcon?: string;
  isNew?: boolean;
};

type IconCacheEntry = { iconUrl: string; expiresAt: number };
type FeedCacheEntry = { data: OtherApp[]; expiresAt: number };

const PLAY_DETAILS_URL = "https://play.google.com/store/apps/details?id=";
const PLAY_USER_AGENT =
  "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
  "(KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36";
const ICON_TTL_MS = 24 * 60 * 60 * 1000;
const FEED_TTL_MS = 10 * 60 * 1000;
const ICON_SIZE_SUFFIX = "w48-h48-rw";

const iconCache = new Map<string, IconCacheEntry>();
let feedCache: FeedCacheEntry | null = null;

function isOtherApp(value: unknown): value is OtherApp {
  if (!value || typeof value !== "object") return false;
  const c = value as Record<string, unknown>;
  return typeof c.appName === "string" && c.appName.trim().length > 0 &&
    typeof c.packageName === "string" && c.packageName.trim().length > 0;
}

function normalizeIconUrl(url?: string): string {
  if (!url) return "";
  const trimmed = url.trim();
  if (!trimmed.includes("play-lh.googleusercontent.com")) return trimmed;
  if (/=w\d+-h\d+-rw$/i.test(trimmed)) return trimmed.replace(/=w\d+-h\d+-rw$/i, `=${ICON_SIZE_SUFFIX}`);
  if (/=s\d+(-[a-z0-9]+)?$/i.test(trimmed)) return trimmed.replace(/=s\d+(-[a-z0-9]+)?$/i, `=${ICON_SIZE_SUFFIX}`);
  if (trimmed.includes("=")) return `${trimmed.split("=")[0]}=${ICON_SIZE_SUFFIX}`;
  return `${trimmed}=${ICON_SIZE_SUFFIX}`;
}

function decodeHtmlEntities(value: string): string {
  return value.replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'");
}

function extractOgImageUrl(html: string): string | null {
  const match =
    html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["'][^>]*>/i) ??
    html.match(/<meta\s+content=["']([^"']+)["']\s+property=["']og:image["'][^>]*>/i);
  if (!match || !match[1]) return null;
  return decodeHtmlEntities(match[1]);
}

async function fetchPlayIcon(packageName: string): Promise<string | null> {
  try {
    const response = await fetch(
      `${PLAY_DETAILS_URL}${encodeURIComponent(packageName)}&hl=tr&gl=TR`,
      { headers: { "User-Agent": PLAY_USER_AGENT, "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8" } },
    );
    if (!response.ok) return null;
    const html = await response.text();
    const ogImage = extractOgImageUrl(html);
    return ogImage ? normalizeIconUrl(ogImage) : null;
  } catch {
    return null;
  }
}

async function resolveIconForApp(app: OtherApp, forceRefresh: boolean): Promise<OtherApp> {
  const packageName = app.packageName;
  const cached = iconCache.get(packageName);
  const now = Date.now();

  if (!forceRefresh && cached && cached.expiresAt > now) {
    return { ...app, appIcon: normalizeIconUrl(cached.iconUrl) };
  }

  const fetchedIcon = await fetchPlayIcon(packageName);
  if (fetchedIcon) {
    iconCache.set(packageName, { iconUrl: fetchedIcon, expiresAt: now + ICON_TTL_MS });
    return { ...app, appIcon: fetchedIcon };
  }

  if (cached) return { ...app, appIcon: normalizeIconUrl(cached.iconUrl) };
  return { ...app, appIcon: normalizeIconUrl(app.appIcon) };
}

async function handleOtherApps(env: Env, forceRefresh: boolean): Promise<Response> {
  if (!forceRefresh && feedCache && feedCache.expiresAt > Date.now()) {
    return json(feedCache.data, 200, { "cache-control": "public, max-age=300, s-maxage=1800" });
  }

  try {
    const seedResponse = await fetch(env.OTHER_APPS_JSON_URL, {
      headers: { Accept: "application/json", "User-Agent": PLAY_USER_AGENT },
    });
    if (!seedResponse.ok) return json({ error: `Seed fetch failed: ${seedResponse.status}` }, 502);

    const parsed = await seedResponse.json() as unknown;
    if (!Array.isArray(parsed)) return json({ error: "Seed app list is not an array" }, 502);

    const seedApps: OtherApp[] = parsed.filter(isOtherApp).map((a) => ({
      appName: a.appName.trim(),
      packageName: a.packageName.trim(),
      appIcon: normalizeIconUrl(a.appIcon?.trim()),
      isNew: Boolean(a.isNew),
    }));

    const resolved = await Promise.all(seedApps.map((a) => resolveIconForApp(a, forceRefresh)));
    feedCache = { data: resolved, expiresAt: Date.now() + FEED_TTL_MS };

    return json(resolved, 200, { "cache-control": "public, max-age=300, s-maxage=1800" });
  } catch {
    return json({ error: "Unable to build app list feed" }, 500);
  }
}

async function buildAudioManifest(requestUrl: string, env: Env): Promise<AudioManifest> {
  const files: AudioManifestItem[] = [];
  let cursor: string | undefined;

  do {
    const listed = await env.AUDIO_BUCKET.list({ cursor, limit: 1000 });
    for (const obj of listed.objects) {
      if (!obj.key.toLowerCase().endsWith(".mp3")) continue;
      files.push({
        key: obj.key,
        url: `${requestUrl.replace(/\/$/, "")}/api/audio/${encodeURIComponent(obj.key)}`,
        size: obj.size,
        etag: obj.etag,
        uploaded: obj.uploaded?.toISOString(),
      });
    }
    cursor = listed.truncated ? listed.cursor : undefined;
  } while (cursor);

  files.sort((a, b) => a.key.localeCompare(b.key));

  return {
    generatedAt: new Date().toISOString(),
    baseAudioUrl: `${requestUrl.replace(/\/$/, "")}/api/audio`,
    packageAudio: PACKAGE_AUDIO,
    files,
  };
}

// ── reCAPTCHA Verify ──

const RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
const RECAPTCHA_TOKEN_REGEX = /^[A-Za-z0-9._-]{20,4096}$/;

const ALLOWED_HOSTS = [
  "localhost", "127.0.0.1",
  "parsfilo.com", "mobildev.site",
];

function isAllowedOrigin(origin: string | null, referer: string | null): boolean {
  const host = extractHost(origin) || extractHost(referer);
  if (!host) return false;
  return ALLOWED_HOSTS.some(
    (allowed) => host === allowed || host.endsWith(`.${allowed}`),
  );
}

function extractHost(urlLike: string | null): string {
  if (!urlLike) return "";
  try {
    return new URL(urlLike).hostname.toLowerCase();
  } catch {
    return "";
  }
}

async function handleRecaptchaVerify(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const origin = request.headers.get("origin");
  const referer = request.headers.get("referer");
  if (!isAllowedOrigin(origin, referer)) {
    return json({ error: "Origin not allowed" }, 403);
  }

  const contentType = request.headers.get("content-type") || "";
  if (!contentType.toLowerCase().includes("application/json")) {
    return json({ error: "Content-Type must be application/json" }, 415);
  }

  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  const token = typeof body.token === "string" ? body.token.trim() : "";
  const expectedAction = typeof body.action === "string" ? body.action.trim() : "";
  if (!token || !RECAPTCHA_TOKEN_REGEX.test(token)) {
    return json({ error: "Invalid recaptcha token" }, 400);
  }

  if (!env.GOOGLE_RECAPTCHA_SECRET_KEY) {
    return json({ error: "Server recaptcha secret is not configured" }, 500);
  }

  const payload = new URLSearchParams({
    secret: env.GOOGLE_RECAPTCHA_SECRET_KEY,
    response: token,
    remoteip: request.headers.get("cf-connecting-ip") || "",
  });

  const verifyResponse = await fetch(RECAPTCHA_VERIFY_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: payload,
  });

  if (!verifyResponse.ok) {
    return json({ success: false, error: "reCAPTCHA provider error" }, 502);
  }

  const verifyJson = await verifyResponse.json() as {
    success: boolean;
    score?: number;
    action?: string;
    hostname?: string;
    "error-codes"?: string[];
  };

  const actionMatches = !expectedAction || verifyJson.action === expectedAction;
  const success = Boolean(verifyJson.success && actionMatches);

  return json({
    success,
    score: verifyJson.score ?? 0,
    action: verifyJson.action ?? null,
    hostname: verifyJson.hostname ?? null,
    errorCodes: verifyJson["error-codes"] ?? [],
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // ── CORS pre-flight ──
    const corsResponse = handleCorsPreflightRequest(request);
    if (corsResponse) return corsResponse;

    // ── Rate limiting ──
    pruneRateLimitMap();
    const clientIp = request.headers.get("CF-Connecting-IP") || "unknown";
    if (isRateLimited(clientIp)) {
      return json(
        { error: "Too many requests. Please try again later." },
        429,
        { "Retry-After": "60" },
      );
    }

    const origin = request.headers.get("Origin");
    const secHeaders = getSecurityHeaders(origin);
    const url = new URL(request.url);

    // ── Routes ──
    let response: Response;

    if (url.pathname === "/health") {
      response = json({
        ok: true,
        service: "contentapp-content-api",
        environment: env.SERVICE_ENVIRONMENT?.trim() || "unknown",
        gitSha: env.SERVICE_GIT_SHA?.trim() || "unknown",
        builtAt: env.SERVICE_BUILD_TIMESTAMP?.trim() || "unknown",
        timestamp: new Date().toISOString(),
      });
    } else if (url.pathname === "/api/other-apps") {
      const forceRefresh = url.searchParams.get("force") === "1";
      response = await handleOtherApps(env, forceRefresh);
    } else if (url.pathname === "/api/audio-manifest") {
      const manifest = await buildAudioManifest(url.origin, env);
      response = json(manifest, 200, { "cache-control": "public, max-age=300" });
    } else if (url.pathname.startsWith("/api/audio/")) {
      const key = decodeURIComponent(url.pathname.replace("/api/audio/", ""));
      if (!key || key.includes("..") || key.includes("/")) {
        response = json({ error: "Invalid audio key" }, 400);
      } else {
        const object = await env.AUDIO_BUCKET.get(key);
        if (!object) {
          response = json({ error: "Not found" }, 404);
        } else {
          const headers = new Headers();
          object.writeHttpMetadata(headers);
          headers.set("etag", object.httpEtag);
          headers.set("cache-control", "public, max-age=31536000, immutable");
          if (!headers.has("content-type")) {
            headers.set("content-type", "audio/mpeg");
          }
          // Add security headers to binary response
          for (const [k, v] of Object.entries(secHeaders)) {
            headers.set(k, v);
          }
          return new Response(object.body, { headers });
        }
      }
    } else if (url.pathname === "/api/recaptcha-verify") {
      if (request.method !== "POST") {
        response = json({ error: "Method not allowed" }, 405);
      } else {
        response = await handleRecaptchaVerify(request, env);
      }
    } else {
      response = json({ error: "Not found" }, 404);
    }

    // ── Apply security headers to JSON responses ──
    const finalHeaders = new Headers(response.headers);
    for (const [k, v] of Object.entries(secHeaders)) {
      finalHeaders.set(k, v);
    }
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: finalHeaders,
    });
  },
};
