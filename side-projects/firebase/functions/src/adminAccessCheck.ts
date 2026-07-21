import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import { resolveAdminAccess } from "./adminAuth";

export const adminAccessCheck = onRequest(
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

        const authHeader = req.get("authorization") ?? "";
        if (!authHeader.startsWith("Bearer ")) {
            res.status(401).json({ error: "Missing Bearer token" });
            return;
        }

        const idToken = authHeader.slice("Bearer ".length).trim();
        if (!idToken) {
            res.status(401).json({ error: "Missing Bearer token" });
            return;
        }

        try {
            const decoded = await admin.auth().verifyIdToken(idToken);
            const access = await resolveAdminAccess(decoded.uid, decoded.email);
            res.status(200).json({
                authorized: access.authorized,
                source: access.source,
                uid: access.uid,
                email: access.email ?? null,
            });
        } catch (error) {
            logger.warn("adminAccessCheck failed", { error });
            res.status(401).json({ error: "Invalid Firebase Auth token" });
        }
    },
);

function looksLikeJsonRequest(contentTypeHeader: string | undefined): boolean {
    if (!contentTypeHeader) return false;
    return contentTypeHeader.toLowerCase().includes("application/json");
}
