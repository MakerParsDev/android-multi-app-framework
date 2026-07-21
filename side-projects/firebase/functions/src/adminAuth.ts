import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";

export type AuthResult =
    | { ok: true; uid: string; email?: string }
    | { ok: false; statusCode: number; error: string };

export type AdminAccessStatus = {
    authorized: boolean;
    source: "firestore" | "allowlist" | "none";
    uid: string;
    email?: string;
};

const ADMIN_ALLOWED_EMAILS = new Set(
    (process.env.ADMIN_ALLOWED_EMAILS ?? "")
        .split(/[,\s;]+/g)
        .map((item) => item.trim().toLowerCase())
        .filter(Boolean),
);

function isAllowlistedEmail(email: string | undefined): boolean {
    if (!email) return false;
    return ADMIN_ALLOWED_EMAILS.has(email.trim().toLowerCase());
}

async function upsertAdminDoc(uid: string, email: string | undefined): Promise<void> {
    await admin.firestore().collection("admins").doc(uid).set(
        {
            email: email ?? null,
            role: "admin",
            enabled: true,
            source: "admin_allowed_emails",
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true },
    );
}

export async function resolveAdminAccess(uid: string, email?: string): Promise<AdminAccessStatus> {
    const normalizedEmail = email?.trim().toLowerCase();
    const adminRef = admin.firestore().collection("admins").doc(uid);
    const adminDoc = await adminRef.get();
    if (adminDoc.exists) {
        return {
            authorized: true,
            source: "firestore",
            uid,
            email,
        };
    }

    if (isAllowlistedEmail(normalizedEmail)) {
        await upsertAdminDoc(uid, email);
        logger.info("Admin access granted via ADMIN_ALLOWED_EMAILS fallback", {
            uid,
            email,
        });
        return {
            authorized: true,
            source: "allowlist",
            uid,
            email,
        };
    }

    return {
        authorized: false,
        source: "none",
        uid,
        email,
    };
}

export async function authenticateAdminRequest(
    authorizationHeader: string | undefined,
): Promise<AuthResult> {
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
        const access = await resolveAdminAccess(decoded.uid, decoded.email);
        if (access.authorized) {
            return { ok: true, uid: decoded.uid, email: decoded.email };
        }

        return { ok: false, statusCode: 403, error: "User is not in admins whitelist" };
    } catch (error) {
        logger.warn("Admin auth verification failed", { error });
        return { ok: false, statusCode: 401, error: "Invalid Firebase Auth token" };
    }
}
