import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { authenticateAdminRequest } from "./adminAuth";

const FCM_TOKEN_REGEX = /^[A-Za-z0-9:._-]{80,4096}$/;
const INSTALLATION_ID_REGEX = /^[A-Za-z0-9._:-]{8,200}$/;
const MAX_TITLE_LENGTH = 120;
const MAX_BODY_LENGTH = 500;
const MAX_DATA_ENTRIES = 20;
const MAX_DATA_KEY_LENGTH = 64;
const MAX_DATA_VALUE_LENGTH = 256;

type TestPushPayload = {
    token?: string;
    installationId?: string;
    title?: string;
    body?: string;
    data?: Record<string, string>;
    useNotificationPayload?: boolean;
};

type DeviceDoc = {
    fcmToken?: string;
    packageName?: string;
    locale?: string;
    notificationsEnabled?: boolean;
};

class HttpError extends Error {
    constructor(public readonly statusCode: number, message: string) {
        super(message);
    }
}

/**
 * sendTestNotification — Admin panelden tek cihaza test bildirimi gönderir.
 *
 * Güvenlik:
 * - Firebase Auth ID token zorunlu (Authorization: Bearer <idToken>)
 * - `/admins/{uid}` Firestore whitelist kontrolü zorunlu
 */
export const sendTestNotification = onRequest(
    { region: "europe-west1", cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }

        if (!looksLikeJsonRequest(req.get("content-type"))) {
            res.status(415).json({ error: "Content-Type must be application/json" });
            return;
        }

        const authResult = await authenticateAdminRequest(req.get("authorization"));
        if (!authResult.ok) {
            res.status(authResult.statusCode).json({ error: authResult.error });
            return;
        }

        const body = isPlainObject(req.body) ? req.body : null;
        if (!body) {
            res.status(400).json({ error: "Invalid JSON body" });
            return;
        }

        const parsed = parseTestPushPayload(body);
        if (!parsed.ok) {
            res.status(400).json({ error: parsed.error });
            return;
        }

        try {
            const payload = parsed.payload;
            const resolvedTarget = await resolveTargetDevice(payload);
            const title = payload.title?.trim() || "Test Bildirim";
            const bodyText = payload.body?.trim() || "Admin panel test bildirimi";

            const message: admin.messaging.Message = {
                token: resolvedTarget.token,
                data: {
                    title,
                    body: bodyText,
                    ...(payload.data ?? {}),
                },
                android: {
                    priority: "high",
                    notification: payload.useNotificationPayload
                        ? {
                            channelId: "app_notifications",
                            priority: "default",
                        }
                        : undefined,
                },
            };

            if (payload.useNotificationPayload) {
                message.notification = {
                    title,
                    body: bodyText,
                };
            }

            const messageId = await admin.messaging().send(message);

            logger.info("Admin test push sent", {
                uid: authResult.uid,
                email: authResult.email,
                useNotificationPayload: Boolean(payload.useNotificationPayload),
                dataKeys: Object.keys(payload.data ?? {}),
                targetType: resolvedTarget.targetType,
                installationId: resolvedTarget.installationId,
                tokenHint: tokenHint(resolvedTarget.token),
                messageId,
            });

            res.status(200).json({
                success: true,
                messageId,
                mode: payload.useNotificationPayload ? "notification+data" : "data-only",
                targetType: resolvedTarget.targetType,
                installationId: resolvedTarget.installationId ?? null,
                packageName: resolvedTarget.packageName ?? null,
                locale: resolvedTarget.locale ?? null,
            });
        } catch (error) {
            const statusCode = error instanceof HttpError ? error.statusCode : 500;
            const clientMessage = error instanceof HttpError ? error.message : "Failed to send test push";
            logger.error("Admin test push failed", {
                uid: authResult.uid,
                email: authResult.email,
                statusCode,
                error,
            });
            res.status(statusCode).json({ error: clientMessage });
        }
    },
);

type ParsePayloadResult =
    | { ok: true; payload: TestPushPayload }
    | { ok: false; error: string };

function parseTestPushPayload(raw: Record<string, unknown>): ParsePayloadResult {
    const token = typeof raw.token === "string" ? raw.token.trim() : "";
    const installationId = typeof raw.installationId === "string" ? raw.installationId.trim() : "";

    if (token && installationId) {
        return { ok: false, error: "Provide either token or installationId, not both" };
    }
    if (!token && !installationId) {
        return { ok: false, error: "Either token or installationId is required" };
    }
    if (token && !FCM_TOKEN_REGEX.test(token)) {
        return { ok: false, error: "Invalid device token" };
    }
    if (installationId && !INSTALLATION_ID_REGEX.test(installationId)) {
        return { ok: false, error: "Invalid installationId" };
    }

    const title = sanitizeOptionalText(raw.title, MAX_TITLE_LENGTH);
    const body = sanitizeOptionalText(raw.body, MAX_BODY_LENGTH);
    const useNotificationPayload = typeof raw.useNotificationPayload === "boolean"
        ? raw.useNotificationPayload
        : false;

    let data: Record<string, string> | undefined;
    if (raw.data != null) {
        if (!isPlainObject(raw.data)) {
            return { ok: false, error: "data must be an object<string,string>" };
        }
        const entries = Object.entries(raw.data);
        if (entries.length > MAX_DATA_ENTRIES) {
            return { ok: false, error: `data supports at most ${MAX_DATA_ENTRIES} entries` };
        }

        const normalized: Record<string, string> = {};
        for (const [key, value] of entries) {
            const normalizedKey = key.trim();
            if (!normalizedKey) {
                return { ok: false, error: "data keys cannot be empty" };
            }
            if (normalizedKey.length > MAX_DATA_KEY_LENGTH) {
                return { ok: false, error: `data key too long: ${normalizedKey}` };
            }
            if (typeof value !== "string") {
                return { ok: false, error: `data value must be string for key: ${normalizedKey}` };
            }
            if (value.length > MAX_DATA_VALUE_LENGTH) {
                return { ok: false, error: `data value too long for key: ${normalizedKey}` };
            }
            normalized[normalizedKey] = value;
        }
        data = normalized;
    }

    return {
        ok: true,
        payload: {
            token: token || undefined,
            installationId: installationId || undefined,
            title,
            body,
            data,
            useNotificationPayload,
        },
    };
}

function sanitizeOptionalText(value: unknown, maxLength: number): string | undefined {
    if (typeof value !== "string") return undefined;
    const trimmed = value.trim();
    if (!trimmed) return undefined;
    return trimmed.slice(0, maxLength);
}

function looksLikeJsonRequest(contentTypeHeader: string | undefined): boolean {
    if (!contentTypeHeader) return false;
    return contentTypeHeader.toLowerCase().includes("application/json");
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value);
}

type ResolvedTargetDevice = {
    token: string;
    targetType: "token" | "installationId";
    installationId?: string;
    packageName?: string;
    locale?: string;
};

async function resolveTargetDevice(payload: TestPushPayload): Promise<ResolvedTargetDevice> {
    if (payload.token) {
        return {
            token: payload.token,
            targetType: "token",
        };
    }

    const installationId = payload.installationId;
    if (!installationId) {
        throw new HttpError(400, "Missing target token or installationId");
    }

    const snapshot = await admin.firestore().collection("devices").doc(installationId).get();
    if (!snapshot.exists) {
        throw new HttpError(404, `Device not found for installationId: ${installationId}`);
    }

    const device = snapshot.data() as DeviceDoc;
    const token = typeof device.fcmToken === "string" ? device.fcmToken.trim() : "";
    if (!token || !FCM_TOKEN_REGEX.test(token)) {
        throw new HttpError(400, `Device record has no valid fcmToken (installationId: ${installationId})`);
    }

    return {
        token,
        targetType: "installationId",
        installationId,
        packageName: typeof device.packageName === "string" ? device.packageName : undefined,
        locale: typeof device.locale === "string" ? device.locale : undefined,
    };
}

function tokenHint(token: string): string {
    return token.length <= 8 ? "***" : `***${token.slice(-8)}`;
}
