import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import { authenticateAdminRequest } from "./adminAuth";

const REGION = "europe-west1";
const REPORTS_COLLECTION = "ad_performance_reports";
const LATEST_REPORT_DOC = "latest";

type FlavorHubSummary = {
    loadedAt: string;
    source: "coverage_reports" | "devices";
    coverage: Record<string, { active: number; total: number }>;
};

type AnalyticsSummary = {
    totalDevices: number;
    activeDevices30d: number;
    notificationsEnabled30d: number;
    devicesByPackage: Array<{ packageName: string; count: number }>;
    recentCoverageReports: Array<{
        id: string;
        days: number;
        generatedAt: string;
        packageCount: number;
        totalActiveDevices: number;
        totalDevices: number;
    }>;
    loadedAt: string;
};

type RevenueSummary = {
    activeSubscriptions: number;
    verifiedSubscriptions: number;
    verifiedInAppPurchases: number;
    monthlyVerifiedPurchases: number;
    monthlyVerifiedRevenueTry: number;
    adRevenueTodayTry: number;
    adRevenueRangeTry: number;
    totalTrackedRevenueTry: number;
    purchasesByPackage: Array<{
        packageName: string;
        count: number;
        revenueTry: number;
    }>;
    adAlerts: unknown[];
    reportGeneratedAt?: string;
    reportRangeLabel?: string;
    loadedAt: string;
};

type CoverageSnapshot = {
    days?: number;
    generatedAt?: string;
    byPackage?: Array<{
        packageName?: string;
        activeDeviceCount?: number;
        totalDeviceCount?: number;
    }>;
};

export const adminGetFlavorHubSummary = onRequest(
    { region: REGION, cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }

        const auth = await authenticateAdminRequest(req.get("authorization"));
        if (!auth.ok) {
            res.status(auth.statusCode).json({ error: auth.error });
            return;
        }

        try {
            const packages = parsePackages(req.body);
            const latestCoverage = await loadLatestCoverageSnapshot();
            if (latestCoverage) {
                const coverage = Object.fromEntries(
                    latestCoverage.byPackage.map((item) => [
                        item.packageName,
                        {
                            active: item.activeDeviceCount,
                            total: item.totalDeviceCount,
                        },
                    ]),
                );

                const payload: FlavorHubSummary = {
                    loadedAt: latestCoverage.generatedAt,
                    source: "coverage_reports",
                    coverage,
                };
                res.status(200).json(payload);
                return;
            }

            const coverage = await loadCoverageFromDevices(packages);
            const payload: FlavorHubSummary = {
                loadedAt: new Date().toISOString(),
                source: "devices",
                coverage,
            };
            res.status(200).json(payload);
        } catch (error) {
            logger.error("adminGetFlavorHubSummary error", { error, uid: auth.uid });
            res.status(500).json({ error: "Failed to load flavor hub summary" });
        }
    },
);

export const adminGetAnalyticsSummary = onRequest(
    { region: REGION, cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }

        const auth = await authenticateAdminRequest(req.get("authorization"));
        if (!auth.ok) {
            res.status(auth.statusCode).json({ error: auth.error });
            return;
        }

        try {
            const packages = parsePackages(req.body);
            const payload = await loadAnalyticsSummary(packages);
            res.status(200).json(payload);
        } catch (error) {
            logger.error("adminGetAnalyticsSummary error", { error, uid: auth.uid });
            res.status(500).json({ error: "Failed to load analytics summary" });
        }
    },
);

export const adminGetRevenueSummary = onRequest(
    { region: REGION, cors: true },
    async (req, res) => {
        if (req.method !== "POST") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }

        const auth = await authenticateAdminRequest(req.get("authorization"));
        if (!auth.ok) {
            res.status(auth.statusCode).json({ error: auth.error });
            return;
        }

        try {
            const [purchaseDocs, latestReport] = await Promise.all([
                admin.firestore()
                    .collection("purchase_verifications")
                    .where("verified", "==", true)
                    .get(),
                admin.firestore().collection(REPORTS_COLLECTION).doc(LATEST_REPORT_DOC).get(),
            ]);

            const monthStart = new Date();
            monthStart.setDate(1);
            monthStart.setHours(0, 0, 0, 0);
            const now = Date.now();

            const purchasesByPackage = new Map<string, { count: number; revenueTry: number }>();
            let activeSubscriptions = 0;
            let verifiedSubscriptions = 0;
            let verifiedInAppPurchases = 0;
            let monthlyVerifiedPurchases = 0;
            let monthlyVerifiedRevenueTry = 0;

            for (const doc of purchaseDocs.docs) {
                const data = doc.data() as Record<string, unknown>;
                const packageName = typeof data.packageName === "string" ? data.packageName : "unknown";
                const purchaseType = typeof data.purchaseType === "string" ? data.purchaseType : "inapp";
                const updatedAt = toMillis(data.updatedAt);
                const expiryTimeMillis = toMillis(data.expiryTimeMillis);
                const revenueTry = inferRevenueTry(data);

                if (purchaseType === "subs") {
                    verifiedSubscriptions += 1;
                    if (expiryTimeMillis !== null && expiryTimeMillis > now) {
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

            const latestReportData = latestReport.exists ? latestReport.data() as Record<string, unknown> : null;
            const totals = isRecord(latestReportData?.totals) ? latestReportData.totals : null;
            const today = isRecord(latestReportData?.today) ? latestReportData.today : null;
            const alerts = Array.isArray(latestReportData?.alerts) ? latestReportData.alerts : [];

            const adRevenueRangeTry = typeof totals?.earningsTry === "number" ? totals.earningsTry : 0;
            const adRevenueTodayTry = typeof today?.earningsTry === "number" ? today.earningsTry : 0;

            const payload: RevenueSummary = {
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
                reportGeneratedAt: typeof latestReportData?.generatedAt === "string" ? latestReportData.generatedAt : undefined,
                reportRangeLabel:
                    typeof latestReportData?.rangeStart === "string" && typeof latestReportData?.rangeEnd === "string"
                        ? `${latestReportData.rangeStart} → ${latestReportData.rangeEnd}`
                        : undefined,
                loadedAt: new Date().toISOString(),
            };
            res.status(200).json(payload);
        } catch (error) {
            logger.error("adminGetRevenueSummary error", { error, uid: auth.uid });
            res.status(500).json({ error: "Failed to load revenue summary" });
        }
    },
);

async function loadLatestCoverageSnapshot(): Promise<{
    days: number;
    generatedAt: string;
    byPackage: Array<{ packageName: string; activeDeviceCount: number; totalDeviceCount: number }>;
} | null> {
    const snapshot = await admin.firestore()
        .collection("coverage_reports")
        .orderBy("generatedAt", "desc")
        .limit(5)
        .get();

    if (snapshot.empty) {
        return null;
    }

    const docs = snapshot.docs
        .map((doc) => doc.data() as CoverageSnapshot)
        .map((item) => ({
            days: typeof item.days === "number" ? item.days : 0,
            generatedAt: typeof item.generatedAt === "string" ? item.generatedAt : "",
            byPackage: Array.isArray(item.byPackage)
                ? item.byPackage
                    .filter((entry) => typeof entry.packageName === "string")
                    .map((entry) => ({
                        packageName: entry.packageName as string,
                        activeDeviceCount:
                            typeof entry.activeDeviceCount === "number" ? entry.activeDeviceCount : 0,
                        totalDeviceCount:
                            typeof entry.totalDeviceCount === "number" ? entry.totalDeviceCount : 0,
                    }))
                : [],
        }))
        .filter((item) => item.generatedAt);

    return docs.find((item) => item.days === 30) ?? docs[0] ?? null;
}

async function loadRecentCoverageReports(): Promise<AnalyticsSummary["recentCoverageReports"]> {
    const snapshot = await admin.firestore()
        .collection("coverage_reports")
        .orderBy("generatedAt", "desc")
        .limit(5)
        .get();

    return snapshot.docs.map((doc) => {
        const data = doc.data() as CoverageSnapshot;
        const byPackage = Array.isArray(data.byPackage) ? data.byPackage : [];
        return {
            id: doc.id,
            days: typeof data.days === "number" ? data.days : 0,
            generatedAt: typeof data.generatedAt === "string" ? data.generatedAt : "",
            packageCount: byPackage.length,
            totalActiveDevices: byPackage.reduce(
                (sum, item) => sum + (typeof item.activeDeviceCount === "number" ? item.activeDeviceCount : 0),
                0,
            ),
            totalDevices: byPackage.reduce(
                (sum, item) => sum + (typeof item.totalDeviceCount === "number" ? item.totalDeviceCount : 0),
                0,
            ),
        };
    }).filter((item) => item.generatedAt);
}

async function loadCoverageFromDevices(
    packages: string[],
): Promise<Record<string, { active: number; total: number }>> {
    const devicesRef = admin.firestore().collection("devices");
    const activeSince = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);

    try {
        const entries = await Promise.all(
            packages.map(async (packageName) => {
                const [total, active] = await Promise.all([
                    countQuery(devicesRef.where("packageName", "==", packageName)),
                    countQuery(
                        devicesRef
                            .where("packageName", "==", packageName)
                            .where("updatedAt", ">=", activeSince),
                    ),
                ]);
                return [packageName, { active, total }] as const;
            }),
        );

        return Object.fromEntries(entries);
    } catch (error) {
        logger.warn("loadCoverageFromDevices aggregate failed, using full snapshot fallback", {
            activeSince: activeSince.toISOString(),
            packageCount: packages.length,
            fallbackReason: isMissingIndexError(error) ? "missing-index" : "aggregate-failure",
            error: error instanceof Error ? error.message : String(error ?? "unknown"),
        });

        const packageSet = new Set(packages);
        const coverage = Object.fromEntries(packages.map((packageName) => [packageName, { active: 0, total: 0 }]));
        const snapshot = await devicesRef.get();

        for (const doc of snapshot.docs) {
            const data = doc.data() as Record<string, unknown>;
            const packageName = typeof data.packageName === "string" ? data.packageName : "";
            if (!packageSet.has(packageName)) continue;

            coverage[packageName].total += 1;
            const updatedAt = toMillis(data.updatedAt);
            if (updatedAt !== null && updatedAt >= activeSince.getTime()) {
                coverage[packageName].active += 1;
            }
        }

        return coverage;
    }
}

async function countQuery(query: FirebaseFirestore.Query): Promise<number> {
    const aggregate = await query.count().get();
    return aggregate.data().count;
}

async function loadAnalyticsSummary(packages: string[]): Promise<AnalyticsSummary> {
    const devicesRef = admin.firestore().collection("devices");
    const now = Date.now();
    const activeSince = new Date(now - 30 * 24 * 60 * 60 * 1000);

    try {
        const [
            totalDevices,
            activeDevices30d,
            notificationsEnabled30d,
            devicesByPackage,
            recentCoverageReports,
        ] = await Promise.all([
            countQuery(devicesRef),
            countQuery(devicesRef.where("updatedAt", ">=", activeSince)),
            countQuery(
                devicesRef
                    .where("updatedAt", ">=", activeSince)
                    .where("notificationsEnabled", "==", true),
            ),
            Promise.all(
                packages.map(async (packageName) => ({
                    packageName,
                    count: await countQuery(devicesRef.where("packageName", "==", packageName)),
                })),
            ),
            loadRecentCoverageReports(),
        ]);

        return {
            totalDevices,
            activeDevices30d,
            notificationsEnabled30d,
            devicesByPackage: devicesByPackage
                .filter((item) => item.count > 0)
                .sort((left, right) => right.count - left.count),
            recentCoverageReports,
            loadedAt: new Date().toISOString(),
        };
    } catch (error) {
        logger.warn("loadAnalyticsSummary aggregate failed, using full snapshot fallback", {
            activeSince: activeSince.toISOString(),
            packageCount: packages.length,
            fallbackReason: isMissingIndexError(error) ? "missing-index" : "aggregate-failure",
            error: error instanceof Error ? error.message : String(error ?? "unknown"),
        });
        return buildAnalyticsSummaryFallback(packages, activeSince.getTime());
    }
}

async function buildAnalyticsSummaryFallback(
    packages: string[],
    activeSinceMs: number,
): Promise<AnalyticsSummary> {
    const snapshot = await admin.firestore().collection("devices").get();
    const packageCounts = new Map<string, number>();
    let totalDevices = 0;
    let activeDevices30d = 0;
    let notificationsEnabled30d = 0;

    for (const doc of snapshot.docs) {
        totalDevices += 1;
        const data = doc.data() as Record<string, unknown>;
        const packageName = typeof data.packageName === "string" ? data.packageName : "";
        if (packageName) {
            packageCounts.set(packageName, (packageCounts.get(packageName) ?? 0) + 1);
        }

        const updatedAt = toMillis(data.updatedAt);
        if (updatedAt !== null && updatedAt >= activeSinceMs) {
            activeDevices30d += 1;
            if (data.notificationsEnabled === true) {
                notificationsEnabled30d += 1;
            }
        }
    }

    const recentCoverageReports = await loadRecentCoverageReports();
    return {
        totalDevices,
        activeDevices30d,
        notificationsEnabled30d,
        devicesByPackage: packages
            .map((packageName) => ({
                packageName,
                count: packageCounts.get(packageName) ?? 0,
            }))
            .filter((item) => item.count > 0)
            .sort((left, right) => right.count - left.count),
        recentCoverageReports,
        loadedAt: new Date().toISOString(),
    };
}

function parsePackages(body: unknown): string[] {
    const candidate = isRecord(body) && Array.isArray(body.packages) ? body.packages : [];
    return candidate
        .filter((item): item is string => typeof item === "string")
        .map((item) => item.trim())
        .filter(Boolean);
}

function toMillis(value: unknown): number | null {
    if (typeof value === "object" && value !== null && "toMillis" in value) {
        const toMillisFn = (value as { toMillis?: unknown }).toMillis;
        if (typeof toMillisFn === "function") {
            try {
                const millis = toMillisFn.call(value);
                return typeof millis === "number" && Number.isFinite(millis) ? millis : null;
            } catch {
                return null;
            }
        }
    }
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim() !== "") {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
}

function inferRevenueTry(raw: Record<string, unknown>): number {
    const candidates = [
        raw.priceAmountTry,
        raw.amountTry,
        raw.revenueTry,
        raw.localizedPriceTry,
    ];
    for (const candidate of candidates) {
        if (typeof candidate === "number" && Number.isFinite(candidate)) {
            return candidate;
        }
        if (typeof candidate === "string" && candidate.trim() !== "") {
            const parsed = Number(candidate);
            if (Number.isFinite(parsed)) {
                return parsed;
            }
        }
    }
    return 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
}

function isMissingIndexError(error: unknown): boolean {
    const message = error instanceof Error ? error.message : String(error ?? "");
    return message.includes("The query requires an index") || message.includes("FAILED_PRECONDITION");
}
