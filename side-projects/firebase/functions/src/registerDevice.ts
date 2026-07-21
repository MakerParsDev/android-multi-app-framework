import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

const INSTALLATION_ID_REGEX = /^[A-Za-z0-9._:-]{8,200}$/;
const FCM_TOKEN_REGEX = /^[A-Za-z0-9:._-]{80,4096}$/;
const PACKAGE_NAME_REGEX = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
const LOCALE_REGEX = /^[A-Za-z]{2,8}(?:[-_][A-Za-z0-9]{2,8}){0,2}$/;
const MAX_GENERIC_FIELD_LENGTH = 160;

/**
 * registerDevice — Mobil uygulamadan gelen cihaz kaydını Firestore'a yazar.
 *
 * Mevcut PushRegistrationPayload ile birebir uyumlu:
 * - installationId, fcmToken, packageName, locale, timezone
 * - notificationsEnabled, appVersion, deviceModel, reason
 * - tokenHash, hasToken, lastAttemptAtEpochMs, lastSuccessAtEpochMs, lastFailureReason
 * - adRuntimeWindowStartAtEpochMs, adRuntimeLastUpdatedAtEpochMs
 * - adRuntimeFunnelCounts, adRuntimeSuppressReasonCounts
 *
 * Bu URL, mobil uygulamadaki PUSH_REGISTRATION_URL olarak ayarlanır.
 */
export const registerDevice = onRequest(
    { region: "europe-west1", cors: true },
    async (req, res) => {
        // Sadece POST kabul et
        if (req.method !== "POST") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }

        try {
            if (!looksLikeJsonRequest(req.get("content-type"))) {
                res.status(415).json({ error: "Content-Type must be application/json" });
                return;
            }

            const body = isPlainObject(req.body) ? req.body : null;
            if (!body) {
                res.status(400).json({ error: "Invalid JSON body" });
                return;
            }

            const appCheckVerified = await verifyAppCheckIfConfigured(req.get("x-firebase-appcheck"));
            if (!appCheckVerified.ok) {
                res.status(401).json({ error: appCheckVerified.error });
                return;
            }

            // Zorunlu alanları kontrol et
            const installationId = sanitizeInstallationId(body.installationId);
            const fcmToken = sanitizeFcmToken(body.fcmToken);

            if (!installationId || !fcmToken) {
                res.status(400).json({
                    error: "Invalid or missing required fields: installationId, fcmToken",
                });
                return;
            }

            const timezone = sanitizeTimezone(body.timezone);
            const locale = sanitizeLocale(body.locale);
            const packageName = sanitizePackageName(body.packageName);
            const notificationsEnabled =
                typeof body.notificationsEnabled === "boolean" ? body.notificationsEnabled : true;
            const appVersion = sanitizeText(body.appVersion, "unknown");
            const deviceModel = sanitizeText(body.deviceModel, "unknown");
            const reason = sanitizeText(body.reason, "unknown");
            const syncedAtEpochMs = sanitizeSyncedAtEpochMs(body.syncedAtEpochMs);
            const tokenHash = sanitizeText(body.tokenHash, "");
            const hasToken = typeof body.hasToken === "boolean" ? body.hasToken : fcmToken.length > 0;
            const lastRegistrationAttemptAt = sanitizeSyncedAtEpochMs(body.lastAttemptAtEpochMs);
            const lastRegistrationSuccessAt = sanitizeSyncedAtEpochMs(body.lastSuccessAtEpochMs);
            const lastRegistrationFailureReason = sanitizeText(body.lastFailureReason, "");
            const adRuntimeWindowStartAt = sanitizeSyncedAtEpochMs(body.adRuntimeWindowStartAtEpochMs);
            const adRuntimeLastUpdatedAt = sanitizeSyncedAtEpochMs(body.adRuntimeLastUpdatedAtEpochMs);
            const adRuntimeFunnelCounts = sanitizeNestedNumberMap(body.adRuntimeFunnelCounts);
            const adRuntimeSuppressReasonCounts = sanitizeFlatNumberMap(body.adRuntimeSuppressReasonCounts);

            const deviceData = {
                fcmToken,
                timezone,
                locale,
                packageName,
                notificationsEnabled,
                appVersion,
                deviceModel,
                reason,
                tokenHash,
                hasToken,
                ...(syncedAtEpochMs != null ? { syncedAtEpochMs } : {}),
                ...(lastRegistrationAttemptAt != null ? { lastRegistrationAttemptAt } : {}),
                ...(lastRegistrationSuccessAt != null ? { lastRegistrationSuccessAt } : {}),
                ...(lastRegistrationFailureReason ? { lastRegistrationFailureReason } : {}),
                ...(adRuntimeWindowStartAt != null ? { adRuntimeWindowStartAt } : {}),
                ...(adRuntimeLastUpdatedAt != null ? { adRuntimeLastUpdatedAt } : {}),
                ...(Object.keys(adRuntimeFunnelCounts).length > 0 ? { adRuntimeFunnelCounts } : {}),
                ...(Object.keys(adRuntimeSuppressReasonCounts).length > 0 ? { adRuntimeSuppressReasonCounts } : {}),
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            };

            // Firestore'a upsert (aynı installationId varsa güncelle)
            await admin.firestore()
                .collection("devices")
                .doc(installationId)
                .set(deviceData, { merge: true });

            logger.info("Device registered", {
                installationId,
                timezone,
                packageName,
                reason,
                appCheckVerified: appCheckVerified.verified,
            });

            res.status(200).json({ success: true });
        } catch (error) {
            logger.error("Device registration failed", { error });
            res.status(500).json({ error: "Internal server error" });
        }
    },
);

function looksLikeJsonRequest(contentTypeHeader: string | undefined): boolean {
    if (!contentTypeHeader) return false;
    return contentTypeHeader.toLowerCase().includes("application/json");
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value);
}

function sanitizeInstallationId(value: unknown): string | null {
    if (typeof value !== "string") return null;
    const trimmed = value.trim();
    if (!INSTALLATION_ID_REGEX.test(trimmed)) return null;
    return trimmed;
}

function sanitizeFcmToken(value: unknown): string | null {
    if (typeof value !== "string") return null;
    const trimmed = value.trim();
    if (!FCM_TOKEN_REGEX.test(trimmed)) return null;
    return trimmed;
}

function sanitizePackageName(value: unknown): string {
    if (typeof value !== "string") return "";
    const trimmed = value.trim();
    if (!trimmed) return "";
    return PACKAGE_NAME_REGEX.test(trimmed) ? trimmed : "";
}

function sanitizeLocale(value: unknown): string {
    if (typeof value !== "string") return "tr-TR";
    const normalized = value.trim();
    if (!normalized || !LOCALE_REGEX.test(normalized)) return "tr-TR";
    return normalized;
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

function sanitizeText(value: unknown, fallback: string): string {
    if (typeof value !== "string") return fallback;
    const trimmed = value.trim();
    if (!trimmed) return fallback;
    return trimmed.slice(0, MAX_GENERIC_FIELD_LENGTH);
}

function sanitizeSyncedAtEpochMs(value: unknown): number | null {
    if (typeof value !== "number" || !Number.isFinite(value)) return null;
    if (value < 0) return null;
    const now = Date.now();
    if (value > now + 24 * 60 * 60 * 1000) {
        return null;
    }
    return Math.floor(value);
}

function sanitizeFlatNumberMap(value: unknown): Record<string, number> {
    if (!isPlainObject(value)) return {};
    const result: Record<string, number> = {};
    for (const [key, raw] of Object.entries(value)) {
        const safeKey = sanitizeMapKey(key);
        if (!safeKey) continue;
        const safeValue = typeof raw === "number" && Number.isFinite(raw) ? Math.max(0, Math.floor(raw)) : null;
        if (safeValue == null || safeValue === 0) continue;
        result[safeKey] = safeValue;
    }
    return result;
}

function sanitizeNestedNumberMap(value: unknown): Record<string, Record<string, number>> {
    if (!isPlainObject(value)) return {};
    const result: Record<string, Record<string, number>> = {};
    for (const [outerKey, rawNested] of Object.entries(value)) {
        const safeOuterKey = sanitizeMapKey(outerKey);
        if (!safeOuterKey || !isPlainObject(rawNested)) continue;
        const nested = sanitizeFlatNumberMap(rawNested);
        if (Object.keys(nested).length === 0) continue;
        result[safeOuterKey] = nested;
    }
    return result;
}

function sanitizeMapKey(value: string): string {
    return value.trim().slice(0, 64);
}

async function verifyAppCheckIfConfigured(appCheckHeader: string | undefined): Promise<{
    ok: boolean;
    verified: boolean;
    error?: string;
}> {
    const requireAppCheck = process.env.REGISTER_DEVICE_REQUIRE_APP_CHECK === "true";
    const token = appCheckHeader?.trim();

    if (!token) {
        if (requireAppCheck) {
            logger.warn("registerDevice rejected: missing App Check token");
            return { ok: false, verified: false, error: "Missing App Check token" };
        }
        return { ok: true, verified: false };
    }

    try {
        await admin.appCheck().verifyToken(token);
        return { ok: true, verified: true };
    } catch (error) {
        logger.warn("registerDevice rejected: invalid App Check token", { error });
        return { ok: false, verified: false, error: "Invalid App Check token" };
    }
}
