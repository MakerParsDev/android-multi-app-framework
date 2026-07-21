import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

const REGION = "europe-west1";
const VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
const TOKEN_REGEX = /^[A-Za-z0-9._-]{20,4096}$/;

type RecaptchaVerifyResponse = {
    success: boolean;
    score?: number;
    action?: string;
    hostname?: string;
    challenge_ts?: string;
    "error-codes"?: string[];
};

export const recaptchaVerify = onRequest(
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
        if (!isAllowedOrigin(req.get("origin"), req.get("referer"))) {
            res.status(403).json({ error: "Origin not allowed" });
            return;
        }

        const body = isPlainObject(req.body) ? req.body : null;
        const token = typeof body?.token === "string" ? body.token.trim() : "";
        const expectedAction = typeof body?.action === "string" ? body.action.trim() : "";
        if (!token || !TOKEN_REGEX.test(token)) {
            res.status(400).json({ error: "Invalid recaptcha token" });
            return;
        }

        const secret = readSecret();
        if (!secret) {
            logger.error("recaptchaVerify misconfigured: GOOGLE_RECAPTCHA_SECRET_KEY missing");
            res.status(500).json({ error: "Server recaptcha secret is not configured" });
            return;
        }

        try {
            const payload = new URLSearchParams({
                secret,
                response: token,
                remoteip: req.ip ?? "",
            });

            const verifyResponse = await fetch(VERIFY_URL, {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: payload,
            });

            const verifyJson = await verifyResponse.json() as RecaptchaVerifyResponse;
            if (!verifyResponse.ok) {
                res.status(502).json({
                    success: false,
                    error: "reCAPTCHA provider error",
                });
                return;
            }

            const actionMatches = !expectedAction || verifyJson.action === expectedAction;
            const success = Boolean(verifyJson.success && actionMatches);

            if (!success) {
                logger.warn("recaptchaVerify validation failed", {
                    expectedAction,
                    resolvedAction: verifyJson.action,
                    hostname: verifyJson.hostname,
                    errorCodes: verifyJson["error-codes"] ?? [],
                });
            }

            res.status(200).json({
                success,
                score: verifyJson.score ?? 0,
                action: verifyJson.action ?? null,
                hostname: verifyJson.hostname ?? null,
                errorCodes: verifyJson["error-codes"] ?? [],
            });
        } catch (error) {
            logger.error("recaptchaVerify failed", { error });
            res.status(500).json({ error: "Failed to verify recaptcha token" });
        }
    },
);

function readSecret(): string {
    return (
        process.env.GOOGLE_RECAPTCHA_SECRET_KEY?.trim()
        || process.env.GOOGLE_reCAPTCHA_SECRET_KEY?.trim()
        || ""
    );
}

function looksLikeJsonRequest(contentTypeHeader: string | undefined): boolean {
    if (!contentTypeHeader) return false;
    return contentTypeHeader.toLowerCase().includes("application/json");
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value);
}

function isAllowedOrigin(originHeader: string | undefined, refererHeader: string | undefined): boolean {
    const originHost = extractHost(originHeader);
    if (originHost && isAllowedHost(originHost)) return true;

    const refererHost = extractHost(refererHeader);
    if (refererHost && isAllowedHost(refererHost)) return true;

    return false;
}

function isAllowedHost(host: string): boolean {
    const value = host.toLowerCase();
    if (value === "localhost" || value === "127.0.0.1") return true;
    if (value === "parsfilo.com" || value.endsWith(".parsfilo.com")) return true;
    if (value === "mobildev.site" || value.endsWith(".mobildev.site")) return true;
    return false;
}

function extractHost(urlLike: string | undefined): string {
    if (!urlLike) return "";
    try {
        return new URL(urlLike).hostname.toLowerCase();
    } catch {
        return "";
    }
}
