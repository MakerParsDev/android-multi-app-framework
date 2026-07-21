const INSTALLATION_ID_REGEX = /^[A-Za-z0-9._:-]{8,200}$/;
const FCM_TOKEN_REGEX = /^[A-Za-z0-9:._-]{80,4096}$/;
const PACKAGE_NAME_REGEX = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
const LOCALE_REGEX = /^[A-Za-z]{2,8}(?:[-_][A-Za-z0-9]{2,8}){0,2}$/;
const MAP_KEY_REGEX = /^[A-Za-z0-9_.:-]{1,64}$/;
const MAX_TEXT_LENGTH = 160;
const MAX_MAP_ENTRIES = 64;
const MAX_FUTURE_SKEW_MS = 24 * 60 * 60 * 1000;

export type DeviceRegistrationRecord = {
  fcmToken: string;
  timezone: string;
  locale: string;
  packageName: string;
  notificationsEnabled: boolean;
  appVersion: string;
  deviceModel: string;
  reason: string;
  syncedAtEpochMs: number | null;
  tokenHash: string;
  hasToken: boolean;
  lastRegistrationAttemptAt: number | null;
  lastRegistrationSuccessAt: number | null;
  lastRegistrationFailureReason: string | null;
  adRuntimeWindowStartAt: number | null;
  adRuntimeLastUpdatedAt: number | null;
  adRuntimeFunnelCounts: Record<string, Record<string, number>>;
  adRuntimeSuppressReasonCounts: Record<string, number>;
  appCheckAppId: string | null;
  updatedAt: string;
};

export type DeviceRegistrationDependencies = {
  requireAppCheck?: boolean;
  nowEpochMs?: () => number;
  verifyAppCheck: (token: string) => Promise<{ appId: string }>;
  upsertDevice: (installationId: string, record: DeviceRegistrationRecord) => Promise<boolean>;
};

export class DeviceRegistrationDependencyUnavailableError extends Error {
  constructor() {
    super("Device registration dependency unavailable");
    this.name = "DeviceRegistrationDependencyUnavailableError";
  }
}

type ParsedDeviceRegistration = {
  installationId: string;
  record: DeviceRegistrationRecord;
};

export async function handleDeviceRegistration(
  request: Request,
  dependencies: DeviceRegistrationDependencies,
): Promise<Response> {
  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }
  if (!looksLikeJson(request.headers.get("content-type"))) {
    return jsonResponse({ error: "Content-Type must be application/json" }, 415);
  }

  const body = await request.json().catch(() => null);
  if (!isPlainObject(body)) {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  const appCheckToken = request.headers.get("x-firebase-appcheck")?.trim() ?? "";
  const requireAppCheck = dependencies.requireAppCheck ?? true;
  let appCheckAppId: string | null = null;
  if (!appCheckToken) {
    if (requireAppCheck) {
      return jsonResponse({ error: "Missing App Check token" }, 401);
    }
  } else {
    try {
      const verified = await dependencies.verifyAppCheck(appCheckToken);
      appCheckAppId = verified.appId;
    } catch (error) {
      if (error instanceof DeviceRegistrationDependencyUnavailableError) {
        return jsonResponse({ error: "App Check verification unavailable" }, 503);
      }
      return jsonResponse({ error: "Invalid App Check token" }, 401);
    }
  }

  const nowEpochMs = dependencies.nowEpochMs?.() ?? Date.now();
  const parsed = parseDeviceRegistration(body, appCheckAppId, nowEpochMs);
  if (!parsed) {
    return jsonResponse({ error: "Invalid device registration payload" }, 400);
  }

  try {
    const written = await dependencies.upsertDevice(parsed.installationId, parsed.record);
    if (!written) {
      return jsonResponse({ error: "Device registration unavailable" }, 503);
    }
  } catch {
    return jsonResponse({ error: "Device registration unavailable" }, 503);
  }

  return jsonResponse({ success: true });
}

function parseDeviceRegistration(
  body: Record<string, unknown>,
  appCheckAppId: string | null,
  nowEpochMs: number,
): ParsedDeviceRegistration | null {
  const installationId = sanitizePattern(body.installationId, INSTALLATION_ID_REGEX);
  const fcmToken = sanitizePattern(body.fcmToken, FCM_TOKEN_REGEX);
  const packageName = sanitizePattern(body.packageName, PACKAGE_NAME_REGEX);
  if (!installationId || !fcmToken || !packageName) return null;

  return {
    installationId,
    record: {
      fcmToken,
      timezone: sanitizeTimezone(body.timezone),
      locale: sanitizeLocale(body.locale),
      packageName,
      notificationsEnabled:
        typeof body.notificationsEnabled === "boolean" ? body.notificationsEnabled : true,
      appVersion: sanitizeText(body.appVersion, "unknown"),
      deviceModel: sanitizeText(body.deviceModel, "unknown"),
      reason: sanitizeText(body.reason, "unknown"),
      syncedAtEpochMs: sanitizeEpochMs(body.syncedAtEpochMs, nowEpochMs),
      tokenHash: sanitizeText(body.tokenHash, ""),
      hasToken: typeof body.hasToken === "boolean" ? body.hasToken : true,
      lastRegistrationAttemptAt: sanitizeEpochMs(body.lastAttemptAtEpochMs, nowEpochMs),
      lastRegistrationSuccessAt: sanitizeEpochMs(body.lastSuccessAtEpochMs, nowEpochMs),
      lastRegistrationFailureReason: sanitizeNullableText(body.lastFailureReason),
      adRuntimeWindowStartAt: sanitizeEpochMs(body.adRuntimeWindowStartAtEpochMs, nowEpochMs),
      adRuntimeLastUpdatedAt: sanitizeEpochMs(body.adRuntimeLastUpdatedAtEpochMs, nowEpochMs),
      adRuntimeFunnelCounts: sanitizeNestedNumberMap(body.adRuntimeFunnelCounts),
      adRuntimeSuppressReasonCounts: sanitizeNumberMap(body.adRuntimeSuppressReasonCounts),
      appCheckAppId,
      updatedAt: new Date(nowEpochMs).toISOString(),
    },
  };
}

function looksLikeJson(contentType: string | null): boolean {
  return contentType?.toLowerCase().includes("application/json") ?? false;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function sanitizePattern(value: unknown, pattern: RegExp): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return pattern.test(trimmed) ? trimmed : null;
}

function sanitizeText(value: unknown, fallback: string): string {
  if (typeof value !== "string") return fallback;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, MAX_TEXT_LENGTH) : fallback;
}

function sanitizeNullableText(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, MAX_TEXT_LENGTH) : null;
}

function sanitizeLocale(value: unknown): string {
  if (typeof value !== "string") return "tr-TR";
  const normalized = value.trim();
  return LOCALE_REGEX.test(normalized) ? normalized : "tr-TR";
}

function sanitizeTimezone(value: unknown): string {
  if (typeof value !== "string") return "UTC";
  const timezone = value.trim();
  if (!timezone || timezone.length > 100) return "UTC";
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: timezone }).format(new Date());
    return timezone;
  } catch {
    return "UTC";
  }
}

function sanitizeEpochMs(value: unknown, nowEpochMs: number): number | null {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0) return null;
  if (value > nowEpochMs + MAX_FUTURE_SKEW_MS) return null;
  return Math.floor(value);
}

function sanitizeNumberMap(value: unknown): Record<string, number> {
  if (!isPlainObject(value)) return {};
  const result: Record<string, number> = {};
  for (const [key, raw] of Object.entries(value).slice(0, MAX_MAP_ENTRIES)) {
    if (!MAP_KEY_REGEX.test(key)) continue;
    if (typeof raw !== "number" || !Number.isFinite(raw)) continue;
    const normalized = Math.floor(raw);
    if (normalized <= 0) continue;
    result[key] = normalized;
  }
  return result;
}

function sanitizeNestedNumberMap(value: unknown): Record<string, Record<string, number>> {
  if (!isPlainObject(value)) return {};
  const result: Record<string, Record<string, number>> = {};
  for (const [key, raw] of Object.entries(value).slice(0, MAX_MAP_ENTRIES)) {
    if (!MAP_KEY_REGEX.test(key)) continue;
    const nested = sanitizeNumberMap(raw);
    if (Object.keys(nested).length > 0) result[key] = nested;
  }
  return result;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
