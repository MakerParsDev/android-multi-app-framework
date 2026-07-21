import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { createHash } from "crypto";
import { google, androidpublisher_v3 } from "googleapis";
import { GoogleAuth } from "google-auth-library";

const REGION = "europe-west1";
const CONTENT_TYPE_JSON = "application/json";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const MAX_FIELD_LENGTH = 256;

type PurchaseType = "subs" | "inapp";

type VerifyPurchaseRequest = {
    packageName?: unknown;
    productId?: unknown;
    purchaseToken?: unknown;
    purchaseType?: unknown;
};

type VerifyPurchaseResponse = {
    verified: boolean;
    purchaseState: string;
    acknowledgementState: string;
    expiryTimeMillis: number | null;
    autoRenewing: boolean;
};

type AuthResult =
    | { ok: true; uid: string; email?: string }
    | { ok: false; statusCode: number; error: string };

type AppCheckResult =
    | { ok: true }
    | { ok: false; statusCode: number; error: string };

type AndroidPublisherClient = androidpublisher_v3.Androidpublisher;

export const verifyPurchase = onRequest(
    { region: REGION, cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }
        if (!looksLikeJsonRequest(req.get("content-type"))) {
            res.status(415).json({ error: "Content-Type must be application/json" });
            return;
        }

        const authResult = await authenticateRequest(req.get("authorization"));
        if (!authResult.ok) {
            res.status(authResult.statusCode).json({ error: authResult.error });
            return;
        }

        const appCheckResult = await verifyAppCheckToken(req.get("x-firebase-appcheck"));
        if (!appCheckResult.ok) {
            res.status(appCheckResult.statusCode).json({ error: appCheckResult.error });
            return;
        }

        const payload = normalizePayload(req.body as VerifyPurchaseRequest);
        if (!payload.ok) {
            res.status(400).json({ error: payload.error });
            return;
        }

        try {
            const play = await createPlayClient();
            const verification = await verifyOnPlay(play, payload.value);

            await upsertVerificationRecord({
                uid: authResult.uid,
                email: authResult.email,
                packageName: payload.value.packageName,
                productId: payload.value.productId,
                purchaseToken: payload.value.purchaseToken,
                purchaseType: payload.value.purchaseType,
                response: verification,
            });

            res.status(200).json(verification);
        } catch (error) {
            logger.error("verifyPurchase failed", {
                uid: authResult.uid,
                packageName: payload.value.packageName,
                productId: payload.value.productId,
                purchaseType: payload.value.purchaseType,
                error,
            });
            const fallback: VerifyPurchaseResponse = {
                verified: false,
                purchaseState: "ERROR",
                acknowledgementState: "UNKNOWN",
                expiryTimeMillis: null,
                autoRenewing: false,
            };
            res.status(502).json(fallback);
        }
    },
);

function looksLikeJsonRequest(contentTypeHeader: string | undefined): boolean {
    if (!contentTypeHeader) return false;
    return contentTypeHeader.toLowerCase().includes(CONTENT_TYPE_JSON);
}

async function authenticateRequest(authorizationHeader: string | undefined): Promise<AuthResult> {
    const bearerPrefix = "Bearer ";
    if (!authorizationHeader || !authorizationHeader.startsWith(bearerPrefix)) {
        return { ok: false, statusCode: 401, error: "Missing Bearer token" };
    }

    const idToken = authorizationHeader.slice(bearerPrefix.length).trim();
    if (!idToken) {
        return { ok: false, statusCode: 401, error: "Missing Bearer token" };
    }

    try {
        const decoded = await admin.auth().verifyIdToken(idToken);
        return {
            ok: true,
            uid: decoded.uid,
            email: decoded.email,
        };
    } catch (error) {
        logger.warn("verifyPurchase auth failed", { error });
        return { ok: false, statusCode: 401, error: "Invalid Firebase Auth token" };
    }
}

async function verifyAppCheckToken(appCheckHeader: string | undefined): Promise<AppCheckResult> {
    const token = appCheckHeader?.trim();
    const requireAppCheck = (process.env.VERIFY_PURCHASE_REQUIRE_APP_CHECK ?? "true") === "true";

    if (!token) {
        if (!requireAppCheck) return { ok: true };
        return { ok: false, statusCode: 401, error: "Missing App Check token" };
    }

    try {
        await admin.appCheck().verifyToken(token);
        return { ok: true };
    } catch (error) {
        logger.warn("verifyPurchase app check failed", { error });
        return { ok: false, statusCode: 401, error: "Invalid App Check token" };
    }
}

function normalizePayload(body: VerifyPurchaseRequest): { ok: true; value: NormalizedPayload } | { ok: false; error: string } {
    const packageName = sanitizeText(body.packageName);
    const productId = sanitizeText(body.productId);
    const purchaseToken = sanitizeText(body.purchaseToken, 2048);
    const purchaseTypeRaw = sanitizeText(body.purchaseType);

    if (!packageName || !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/.test(packageName)) {
        return { ok: false, error: "Invalid packageName" };
    }
    if (!productId) {
        return { ok: false, error: "Invalid productId" };
    }
    if (!purchaseToken) {
        return { ok: false, error: "Invalid purchaseToken" };
    }

    const purchaseType: PurchaseType = purchaseTypeRaw === "inapp" ? "inapp" : "subs";

    return {
        ok: true,
        value: {
            packageName,
            productId,
            purchaseToken,
            purchaseType,
        },
    };
}

function sanitizeText(value: unknown, maxLength = MAX_FIELD_LENGTH): string {
    if (typeof value !== "string") return "";
    return value.trim().slice(0, maxLength);
}

type NormalizedPayload = {
    packageName: string;
    productId: string;
    purchaseToken: string;
    purchaseType: PurchaseType;
};

function createPlayClient(): AndroidPublisherClient {
    return google.androidpublisher({
        version: "v3",
        auth: createGoogleAuth(),
    });
}

function createGoogleAuth(): GoogleAuth {
    const inlineJson = process.env.PLAY_ANDROID_PUBLISHER_SERVICE_ACCOUNT_JSON?.trim();
    if (inlineJson) {
        const credentials = JSON.parse(inlineJson) as Record<string, unknown>;
        return new GoogleAuth({
            credentials,
            scopes: [PLAY_SCOPE],
        });
    }

    return new GoogleAuth({ scopes: [PLAY_SCOPE] });
}

async function verifyOnPlay(
    play: AndroidPublisherClient,
    payload: NormalizedPayload,
): Promise<VerifyPurchaseResponse> {
    if (payload.purchaseType === "inapp") {
        return verifyInApp(play, payload);
    }
    return verifySubscription(play, payload);
}

async function verifyInApp(
    play: AndroidPublisherClient,
    payload: NormalizedPayload,
): Promise<VerifyPurchaseResponse> {
    const response = await play.purchases.products.get({
        packageName: payload.packageName,
        productId: payload.productId,
        token: payload.purchaseToken,
    });

    const purchaseStateCode = response.data.purchaseState ?? -1;
    const acknowledgementCode = response.data.acknowledgementState ?? -1;
    const verified = purchaseStateCode === 0;

    return {
        verified,
        purchaseState: toInAppPurchaseState(purchaseStateCode),
        acknowledgementState: toAcknowledgementState(acknowledgementCode),
        expiryTimeMillis: null,
        autoRenewing: false,
    };
}

async function verifySubscription(
    play: AndroidPublisherClient,
    payload: NormalizedPayload,
): Promise<VerifyPurchaseResponse> {
    const response = await play.purchases.subscriptionsv2.get({
        packageName: payload.packageName,
        token: payload.purchaseToken,
    });

    const data = response.data;
    const subscriptionState = data.subscriptionState ?? "SUBSCRIPTION_STATE_UNSPECIFIED";
    const lineItem = data.lineItems?.[0];
    const expiryTimeMillis = parseExpiry(lineItem?.expiryTime);
    const acknowledgementState = data.acknowledgementState ?? "ACKNOWLEDGEMENT_STATE_UNSPECIFIED";
    const autoRenewing = Boolean(lineItem?.autoRenewingPlan?.autoRenewEnabled);

    const verified = [
        "SUBSCRIPTION_STATE_ACTIVE",
        "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    ].includes(subscriptionState);

    return {
        verified,
        purchaseState: subscriptionState,
        acknowledgementState,
        expiryTimeMillis,
        autoRenewing,
    };
}

function parseExpiry(value: string | null | undefined): number | null {
    if (!value) return null;
    const asNumber = Number(value);
    if (Number.isFinite(asNumber) && asNumber > 0) {
        return Math.floor(asNumber);
    }
    const parsed = Date.parse(value);
    if (Number.isNaN(parsed)) return null;
    return parsed;
}

function toInAppPurchaseState(value: number): string {
    switch (value) {
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

function toAcknowledgementState(value: number): string {
    switch (value) {
        case 1:
            return "ACKNOWLEDGED";
        case 0:
            return "PENDING";
        default:
            return "UNKNOWN";
    }
}

type VerificationRecord = {
    uid: string;
    email?: string;
    packageName: string;
    productId: string;
    purchaseToken: string;
    purchaseType: PurchaseType;
    response: VerifyPurchaseResponse;
};

async function upsertVerificationRecord(record: VerificationRecord): Promise<void> {
    const hashSource = [
        record.uid,
        record.packageName,
        record.productId,
        record.purchaseToken,
        record.purchaseType,
    ].join("|");
    const docId = createHash("sha256").update(hashSource).digest("hex");
    await admin.firestore().collection("purchase_verifications").doc(docId).set(
        {
            uid: record.uid,
            email: record.email ?? null,
            packageName: record.packageName,
            productId: record.productId,
            purchaseType: record.purchaseType,
            verified: record.response.verified,
            purchaseState: record.response.purchaseState,
            acknowledgementState: record.response.acknowledgementState,
            expiryTimeMillis: record.response.expiryTimeMillis,
            autoRenewing: record.response.autoRenewing,
            tokenHash: createHash("sha256").update(record.purchaseToken).digest("hex"),
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true },
    );
}
