import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { authenticateAdminRequest } from "./adminAuth";

const DEFAULT_DAYS = 14;
const MIN_DAYS = 1;
const MAX_DAYS = 90;

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

type CoverageInput = {
    days: number;
    packages: string[];
};

type PackageCoverage = {
    packageName: string;
    activeDeviceCount: number;
    totalDeviceCount: number;
};

export const deviceCoverageReport = onRequest(
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

        const body = isPlainObject(req.body) ? req.body : {};
        const input = parseInput(body);
        const cutoffMs = Date.now() - input.days * 24 * 60 * 60 * 1000;

        const byPackage: PackageCoverage[] = [];
        for (const packageName of input.packages) {
            const snapshot = await admin.firestore()
                .collection("devices")
                .where("packageName", "==", packageName)
                .get();

            let totalDeviceCount = 0;
            let activeDeviceCount = 0;
            snapshot.forEach((doc) => {
                totalDeviceCount += 1;
                const data = doc.data() as { updatedAt?: admin.firestore.Timestamp };
                const updatedAtMs = data.updatedAt?.toMillis?.() ?? 0;
                if (updatedAtMs >= cutoffMs) {
                    activeDeviceCount += 1;
                }
            });

            byPackage.push({
                packageName,
                activeDeviceCount,
                totalDeviceCount,
            });
        }

        const missingPackages = byPackage
            .filter((item) => item.totalDeviceCount === 0)
            .map((item) => item.packageName);
        const stalePackages = byPackage
            .filter((item) => item.totalDeviceCount > 0 && item.activeDeviceCount === 0)
            .map((item) => item.packageName);

        logger.info("Device coverage report generated", {
            uid: authResult.uid,
            email: authResult.email,
            days: input.days,
            packageCount: input.packages.length,
            missingPackagesCount: missingPackages.length,
            stalePackagesCount: stalePackages.length,
        });

        res.status(200).json({
            days: input.days,
            generatedAt: new Date().toISOString(),
            byPackage,
            missingPackages,
            stalePackages,
        });
    },
);

function parseInput(body: Record<string, unknown>): CoverageInput {
    const rawDays = Number(body.days);
    const days = Number.isFinite(rawDays)
        ? Math.max(MIN_DAYS, Math.min(MAX_DAYS, Math.floor(rawDays)))
        : DEFAULT_DAYS;

    const packages = parsePackages(body.packages);
    return { days, packages };
}

function parsePackages(value: unknown): string[] {
    if (!Array.isArray(value)) return DEFAULT_PACKAGES;
    const normalized = value
        .filter((item): item is string => typeof item === "string")
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
    return normalized.length > 0 ? Array.from(new Set(normalized)) : DEFAULT_PACKAGES;
}

function looksLikeJsonRequest(contentTypeHeader: string | undefined): boolean {
    if (!contentTypeHeader) return false;
    return contentTypeHeader.toLowerCase().includes("application/json");
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value);
}
