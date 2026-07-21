import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";
import { authenticateAdminRequest } from "./adminAuth";

type AdMobConfig = {
    clientId: string;
    clientSecret: string;
    refreshToken: string;
    preferredAccountName?: string;
};

type NetworkRow = {
    appId: string;
    appLabel: string;
    format: string;
    earningsMicros: number;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
};

type AggregatedStat = {
    appId: string;
    appLabel: string;
    format: string;
    earningsMicros: number;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
};

type ReportAlert = {
    appId: string;
    appLabel: string;
    format: string;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
    fillRatePct: number;
    showRatePct: number;
    earningsTry: number;
    ecpmTry: number;
    reasons: string[];
};

type ReportStat = {
    appId: string;
    appLabel: string;
    format: string;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
    earningsTry: number;
    ecpmTry: number;
};

type ReportPayload = {
    generatedAt: string;
    rangeStart: string;
    rangeEnd: string;
    source: "admob_api";
    status: "ok" | "misconfigured" | "error";
    thresholds: {
        minRequests: number;
        fillRatePct: number;
        showRatePct: number;
    };
    totals: {
        earningsTry: number;
        ecpmTry: number;
        adRequests: number;
        matchedRequests: number;
        impressions: number;
        fillRatePct: number;
        showRatePct: number;
    };
    stats?: ReportStat[];
    alerts: ReportAlert[];
    issue?: string;
};

type TodayPayload = {
    generatedAt: string;
    date: string;
    source: "admob_api";
    status: "ok" | "misconfigured" | "error";
    totals: {
        earningsTry: number;
        ecpmTry: number;
        adRequests: number;
        matchedRequests: number;
        impressions: number;
        fillRatePct: number;
        showRatePct: number;
    };
    issue?: string;
};

const REGION = "europe-west1";
const REPORTS_COLLECTION = "ad_performance_reports";
const LATEST_DOC_ID = "latest";
const HISTORY_DOC_PREFIX = "weekly_";

const DEFAULT_MIN_REQUESTS = 500;
const DEFAULT_FILL_THRESHOLD = 55;
const DEFAULT_SHOW_THRESHOLD = 20;

const WEEKLY_ALERT_SCHEDULE = "15 7 * * 1"; // Monday 07:15 UTC
const AD_PERFORMANCE_DEPLOY_REVISION = "2026-03-06-env-refresh-2";

export const generateAdPerformanceWeeklyReport = onSchedule(
    {
        schedule: WEEKLY_ALERT_SCHEDULE,
        region: REGION,
        timeZone: "UTC",
        retryCount: 0,
    },
    async () => {
        await generateAndStoreWeeklyReport("scheduler");
    },
);

export const adPerformance = onRequest(
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

        const authResult = await authenticateAdminRequest(req.get("authorization"));
        if (!authResult.ok) {
            res.status(authResult.statusCode).json({ error: authResult.error });
            return;
        }

        const body = isPlainObject(req.body) ? req.body : {};
        const requestType = typeof body.type === "string" ? body.type : "report";

        try {
            // type: "today" → bugünkü rapor
            if (requestType === "today") {
                const report = await generateTodayReport();
                res.status(200).json(report);
                return;
            }

            // type: "refresh" → haftalık raporu yeniden üret
            if (requestType === "refresh") {
                const generated = await generateAndStoreWeeklyReport("manual", {
                    uid: authResult.uid,
                    email: authResult.email,
                });
                res.status(200).json(generated);
                return;
            }

            // Default (type: "report") → son kaydedilmiş raporu getir
            const snap = await admin.firestore()
                .collection(REPORTS_COLLECTION)
                .doc(LATEST_DOC_ID)
                .get();
            if (!snap.exists) {
                res.status(404).json({
                    error: "No ad performance report found yet. Use type: 'refresh' to generate one.",
                });
                return;
            }
            res.status(200).json(snap.data());
        } catch (error) {
            logger.error("adPerformance request failed", {
                uid: authResult.uid,
                email: authResult.email,
                requestType,
                error,
            });
            res.status(500).json({ error: "Failed to load ad performance data" });
        }
    },
);

async function generateAndStoreWeeklyReport(
    source: "scheduler" | "manual",
    actor?: { uid: string; email?: string },
): Promise<ReportPayload> {
    const now = new Date();
    const rangeEndDate = addDays(now, -1);
    const rangeStartDate = addDays(rangeEndDate, -6);

    const baseReport: Omit<ReportPayload, "status" | "totals" | "alerts"> = {
        generatedAt: now.toISOString(),
        rangeStart: formatDate(rangeStartDate),
        rangeEnd: formatDate(rangeEndDate),
        source: "admob_api",
        thresholds: {
            minRequests: readThresholdEnv("AD_HEALTH_MIN_REQUESTS", DEFAULT_MIN_REQUESTS),
            fillRatePct: readThresholdEnv("AD_HEALTH_FILL_RATE_THRESHOLD", DEFAULT_FILL_THRESHOLD),
            showRatePct: readThresholdEnv("AD_HEALTH_SHOW_RATE_THRESHOLD", DEFAULT_SHOW_THRESHOLD),
        },
    };

    try {
        const config = loadAdMobConfigFromEnv();
        if (!config) {
            const report: ReportPayload = {
                ...baseReport,
                status: "misconfigured",
                totals: zeroTotals(),
                alerts: [],
                issue: "Missing AdMob env (ADMOB_CLIENT_ID/SECRET/REFRESH_TOKEN)",
            };
            await persistReport(report, source, actor);
            logger.warn("Ad performance report skipped: env misconfigured");
            return report;
        }

        const accessToken = await fetchAdMobAccessToken(config);
        const accountName = await resolveAccountName(config, accessToken);
        const rows = await fetchNetworkRows(accountName, accessToken, rangeStartDate, rangeEndDate);
        const ecpmBaselineByKey = await loadWeeklyEcpmBaseline(4);
        const report = buildReport(baseReport, rows, ecpmBaselineByKey);
        await persistReport(report, source, actor);
        logger.info("Ad performance weekly report generated", {
            revision: AD_PERFORMANCE_DEPLOY_REVISION,
            source,
            actor,
            accountName,
            alerts: report.alerts.length,
            requests: report.totals.adRequests,
            earningsTry: report.totals.earningsTry,
        });
        return report;
    } catch (error) {
        const report: ReportPayload = {
            ...baseReport,
            status: "error",
            totals: zeroTotals(),
            alerts: [],
            issue: error instanceof Error ? error.message : "Unknown report generation error",
        };
        await persistReport(report, source, actor);
        logger.error("Ad performance report generation failed", { source, actor, error });
        return report;
    }
}

async function generateTodayReport(): Promise<TodayPayload> {
    const now = new Date();
    const reportBase: Omit<TodayPayload, "status" | "totals"> = {
        generatedAt: now.toISOString(),
        date: formatDate(now),
        source: "admob_api",
    };

    try {
        const config = loadAdMobConfigFromEnv();
        if (!config) {
            return {
                ...reportBase,
                status: "misconfigured",
                totals: zeroTotals(),
                issue: "Missing AdMob env (ADMOB_CLIENT_ID/SECRET/REFRESH_TOKEN)",
            };
        }

        const accessToken = await fetchAdMobAccessToken(config);
        const accountName = await resolveAccountName(config, accessToken);
        const rows = await fetchNetworkRows(accountName, accessToken, now, now);
        const totals = rows.reduce(
            (acc, item) => {
                acc.earningsMicros += item.earningsMicros;
                acc.adRequests += item.adRequests;
                acc.matchedRequests += item.matchedRequests;
                acc.impressions += item.impressions;
                return acc;
            },
            { earningsMicros: 0, adRequests: 0, matchedRequests: 0, impressions: 0 },
        );

        return {
            ...reportBase,
            status: "ok",
            totals: {
                earningsTry: round4(totals.earningsMicros / 1_000_000),
                ecpmTry: round4(calculateEcpmTry(totals.earningsMicros, totals.impressions)),
                adRequests: totals.adRequests,
                matchedRequests: totals.matchedRequests,
                impressions: totals.impressions,
                fillRatePct: round2(calculateRate(totals.matchedRequests, totals.adRequests)),
                showRatePct: round2(calculateRate(totals.impressions, totals.matchedRequests)),
            },
        };
    } catch (error) {
        return {
            ...reportBase,
            status: "error",
            totals: zeroTotals(),
            issue: error instanceof Error ? error.message : "Unknown report generation error",
        };
    }
}

function loadAdMobConfigFromEnv(): AdMobConfig | null {
    const clientId = process.env.ADMOB_CLIENT_ID?.trim() ?? "";
    const clientSecret = process.env.ADMOB_CLIENT_SECRET?.trim() ?? "";
    const refreshToken = process.env.ADMOB_REFRESH_TOKEN?.trim() ?? "";
    const publisher = process.env.ADMOB_PUBLISHER_ID?.trim() ?? "";

    if (!clientId || !clientSecret || !refreshToken) {
        return null;
    }

    const preferredAccountName = publisher
        ? (publisher.startsWith("accounts/") ? publisher : `accounts/${publisher}`)
        : undefined;

    return { clientId, clientSecret, refreshToken, preferredAccountName };
}

async function fetchAdMobAccessToken(config: AdMobConfig): Promise<string> {
    const body = new URLSearchParams({
        client_id: config.clientId,
        client_secret: config.clientSecret,
        refresh_token: config.refreshToken,
        grant_type: "refresh_token",
    });

    const response = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body,
    });

    const payload = await response.json() as { access_token?: string; error?: string; error_description?: string };
    if (!response.ok || !payload.access_token) {
        const message = payload.error_description || payload.error || `HTTP ${response.status}`;
        throw new Error(`AdMob token refresh failed: ${message}`);
    }

    return payload.access_token;
}

async function fetchNetworkRows(
    accountName: string,
    accessToken: string,
    startDate: Date,
    endDate: Date,
): Promise<NetworkRow[]> {
    const response = await fetch(`https://admob.googleapis.com/v1/${accountName}/networkReport:generate`, {
        method: "POST",
        headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
        },
        body: JSON.stringify({
            reportSpec: {
                dateRange: {
                    startDate: toAdMobDate(startDate),
                    endDate: toAdMobDate(endDate),
                },
                dimensions: ["APP", "FORMAT"],
                metrics: ["ESTIMATED_EARNINGS", "AD_REQUESTS", "MATCHED_REQUESTS", "IMPRESSIONS", "SHOW_RATE"],
            },
        }),
    });

    const payload = await response.json() as Array<Record<string, unknown>> | { error?: { message?: string } };
    if (!response.ok || !Array.isArray(payload)) {
        const errorMessage = !Array.isArray(payload) ? payload.error?.message : undefined;
        throw new Error(`AdMob report fetch failed: ${errorMessage || `HTTP ${response.status}`}`);
    }

    const rows: NetworkRow[] = [];
    for (const item of payload) {
        if (!("row" in item)) continue;
        const row = (item.row ?? {}) as Record<string, unknown>;
        const dimensions = (row.dimensionValues ?? {}) as Record<string, Record<string, unknown>>;
        rows.push({
            appId: String(dimensions.APP?.value ?? ""),
            appLabel: String(dimensions.APP?.displayLabel ?? dimensions.APP?.value ?? "unknown"),
            format: String(dimensions.FORMAT?.value ?? "unknown"),
            earningsMicros: readMetric(row, "ESTIMATED_EARNINGS"),
            adRequests: readMetric(row, "AD_REQUESTS"),
            matchedRequests: readMetric(row, "MATCHED_REQUESTS"),
            impressions: readMetric(row, "IMPRESSIONS"),
        });
    }

    return rows;
}

async function fetchAccessibleAccountNames(accessToken: string): Promise<string[]> {
    const response = await fetch("https://admob.googleapis.com/v1/accounts", {
        method: "GET",
        headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
        },
    });

    const payload = await response.json() as
        | { account?: Array<{ name?: string }> }
        | { error?: { message?: string } };

    if (!response.ok) {
        const message = "error" in payload ? payload.error?.message : undefined;
        throw new Error(`AdMob accounts fetch failed: ${message || `HTTP ${response.status}`}`);
    }

    const accountEntries = Array.isArray((payload as { account?: Array<{ name?: string }> }).account)
        ? (payload as { account: Array<{ name?: string }> }).account
        : [];

    const accountNames = accountEntries
        .map((item: { name?: string }) => item.name?.trim() ?? "")
        .filter((value: string) => value.length > 0);

    if (accountNames.length === 0) {
        throw new Error("AdMob accounts fetch succeeded but no accessible account was returned");
    }

    return accountNames;
}

async function resolveAccountName(config: AdMobConfig, accessToken: string): Promise<string> {
    const accessibleAccounts = await fetchAccessibleAccountNames(accessToken);

    if (!config.preferredAccountName) {
        const selected = accessibleAccounts[0];
        logger.warn("ADMOB_PUBLISHER_ID missing, using first accessible AdMob account", {
            selectedAccount: selected,
            accountCount: accessibleAccounts.length,
        });
        return selected;
    }

    if (accessibleAccounts.includes(config.preferredAccountName)) {
        return config.preferredAccountName;
    }

    const selected = accessibleAccounts[0];
    logger.warn("ADMOB_PUBLISHER_ID is not accessible by current OAuth token, using fallback account", {
        preferredAccount: config.preferredAccountName,
        selectedAccount: selected,
        accountCount: accessibleAccounts.length,
    });
    return selected;
}

function buildReport(
    base: Omit<ReportPayload, "status" | "totals" | "alerts">,
    rows: NetworkRow[],
    ecpmBaselineByKey: Map<string, number>,
): ReportPayload {
    const grouped = new Map<string, AggregatedStat>();

    for (const row of rows) {
        const key = `${row.appId}::${row.format}`;
        const current = grouped.get(key) ?? {
            appId: row.appId,
            appLabel: row.appLabel,
            format: row.format,
            earningsMicros: 0,
            adRequests: 0,
            matchedRequests: 0,
            impressions: 0,
        };

        current.earningsMicros += row.earningsMicros;
        current.adRequests += row.adRequests;
        current.matchedRequests += row.matchedRequests;
        current.impressions += row.impressions;
        grouped.set(key, current);
    }

    const stats = Array.from(grouped.values());
    const reportStats: ReportStat[] = stats.map((item) => ({
        appId: item.appId,
        appLabel: item.appLabel,
        format: item.format,
        adRequests: item.adRequests,
        matchedRequests: item.matchedRequests,
        impressions: item.impressions,
        earningsTry: round4(item.earningsMicros / 1_000_000),
        ecpmTry: round4(calculateEcpmTry(item.earningsMicros, item.impressions)),
    }));
    const totals = stats.reduce(
        (acc, item) => {
            acc.earningsMicros += item.earningsMicros;
            acc.adRequests += item.adRequests;
            acc.matchedRequests += item.matchedRequests;
            acc.impressions += item.impressions;
            return acc;
        },
        { earningsMicros: 0, adRequests: 0, matchedRequests: 0, impressions: 0 },
    );

    const alerts: ReportAlert[] = stats
        .filter((item) => item.adRequests >= base.thresholds.minRequests)
        .map((item) => {
            const key = `${item.appId}::${item.format}`;
            const fillRatePct = calculateRate(item.matchedRequests, item.adRequests);
            const showRatePct = calculateRate(item.impressions, item.matchedRequests);
            const ecpmTry = calculateEcpmTry(item.earningsMicros, item.impressions);
            const reasons: string[] = [];

            if (fillRatePct < base.thresholds.fillRatePct) {
                reasons.push("low_fill_rate");
            }
            if (showRatePct < base.thresholds.showRatePct) {
                reasons.push("low_show_rate");
            }
            if (item.format === "rewarded" && showRatePct < 1) {
                reasons.push("rewarded_not_shown");
            }
            if (item.adRequests > 0 && item.impressions == 0) {
                reasons.push("requests_without_impressions");
            }
            const baselineEcpmTry = ecpmBaselineByKey.get(key);
            if (baselineEcpmTry != null && baselineEcpmTry > 0) {
                if (ecpmTry <= baselineEcpmTry * 0.6) {
                    reasons.push("ecpm_drop_spike");
                }
            }

            return {
                appId: item.appId,
                appLabel: item.appLabel,
                format: item.format,
                adRequests: item.adRequests,
                matchedRequests: item.matchedRequests,
                impressions: item.impressions,
                fillRatePct: round2(fillRatePct),
                showRatePct: round2(showRatePct),
                earningsTry: round4(item.earningsMicros / 1_000_000),
                ecpmTry: round4(ecpmTry),
                reasons,
            };
        })
        .filter((item) => item.reasons.length > 0)
        .sort((a, b) => b.adRequests - a.adRequests);

    return {
        ...base,
        status: "ok",
        totals: {
            earningsTry: round4(totals.earningsMicros / 1_000_000),
            ecpmTry: round4(calculateEcpmTry(totals.earningsMicros, totals.impressions)),
            adRequests: totals.adRequests,
            matchedRequests: totals.matchedRequests,
            impressions: totals.impressions,
            fillRatePct: round2(calculateRate(totals.matchedRequests, totals.adRequests)),
            showRatePct: round2(calculateRate(totals.impressions, totals.matchedRequests)),
        },
        stats: reportStats,
        alerts,
    };
}

async function persistReport(
    report: ReportPayload,
    source: "scheduler" | "manual",
    actor?: { uid: string; email?: string },
): Promise<void> {
    const db = admin.firestore();
    const generatedDate = report.generatedAt.replace(/[:.]/g, "-");

    const latestRef = db.collection(REPORTS_COLLECTION).doc(LATEST_DOC_ID);
    const historyRef = db.collection(REPORTS_COLLECTION).doc(`${HISTORY_DOC_PREFIX}${generatedDate}`);

    const metadata = {
        generatedBy: source,
        actorUid: actor?.uid ?? null,
        actorEmail: actor?.email ?? null,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await Promise.all([
        latestRef.set({ ...report, ...metadata }, { merge: true }),
        historyRef.set({ ...report, ...metadata }, { merge: true }),
    ]);
}

function readMetric(row: Record<string, unknown>, key: string): number {
    const metricValues = (row.metricValues ?? {}) as Record<string, Record<string, unknown>>;
    const metric = metricValues[key] ?? {};
    const micros = parseMetricNumber(metric.microsValue);
    if (micros != null) return micros;
    const integer = parseMetricNumber(metric.integerValue);
    if (integer != null) return integer;
    const doubleValue = parseMetricNumber(metric.doubleValue);
    if (doubleValue != null) return doubleValue;
    return 0;
}

function parseMetricNumber(value: unknown): number | null {
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim().length > 0) {
        const parsed = Number(value);
        if (Number.isFinite(parsed)) return parsed;
    }
    return null;
}

function toAdMobDate(date: Date): { year: number; month: number; day: number } {
    return {
        year: date.getUTCFullYear(),
        month: date.getUTCMonth() + 1,
        day: date.getUTCDate(),
    };
}

function formatDate(date: Date): string {
    const year = date.getUTCFullYear();
    const month = String(date.getUTCMonth() + 1).padStart(2, "0");
    const day = String(date.getUTCDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function addDays(base: Date, days: number): Date {
    const copy = new Date(base);
    copy.setUTCDate(copy.getUTCDate() + days);
    return copy;
}

function calculateRate(numerator: number, denominator: number): number {
    if (denominator <= 0) return 0;
    return (numerator / denominator) * 100;
}

function calculateEcpmTry(earningsMicros: number, impressions: number): number {
    if (impressions <= 0) return 0;
    const earningsTry = earningsMicros / 1_000_000;
    return (earningsTry / impressions) * 1000;
}

function round2(value: number): number {
    return Math.round(value * 100) / 100;
}

function round4(value: number): number {
    return Math.round(value * 10_000) / 10_000;
}

function zeroTotals(): ReportPayload["totals"] {
    return {
        earningsTry: 0,
        ecpmTry: 0,
        adRequests: 0,
        matchedRequests: 0,
        impressions: 0,
        fillRatePct: 0,
        showRatePct: 0,
    };
}

function readThresholdEnv(key: string, fallback: number): number {
    const raw = Number(process.env[key]);
    if (!Number.isFinite(raw)) return fallback;
    return Math.max(0, Math.floor(raw));
}

function looksLikeJsonRequest(contentTypeHeader: string | undefined): boolean {
    if (!contentTypeHeader) return false;
    return contentTypeHeader.toLowerCase().includes("application/json");
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value);
}

async function loadWeeklyEcpmBaseline(weeks: number): Promise<Map<string, number>> {
    const reportCollection = admin.firestore().collection(REPORTS_COLLECTION);
    const documentRefs = await reportCollection.listDocuments();
    const candidateRefs = documentRefs
        .filter((ref) => ref.id.startsWith(HISTORY_DOC_PREFIX))
        .sort((a, b) => b.id.localeCompare(a.id))
        .slice(0, 60);

    if (candidateRefs.length === 0) {
        return new Map<string, number>();
    }

    const snapshotDocs = await admin.firestore().getAll(...candidateRefs);

    let consideredWeeks = 0;
    const accumulator = new Map<string, { total: number; count: number }>();
    for (const docSnap of snapshotDocs) {
        const data = docSnap.data() as { stats?: unknown; alerts?: unknown };
        const stats = Array.isArray(data.stats) ? data.stats : (Array.isArray(data.alerts) ? data.alerts : []);
        if (stats.length === 0) {
            continue;
        }

        consideredWeeks += 1;
        for (const stat of stats) {
            if (!isPlainObject(stat)) continue;
            const appId = typeof stat.appId === "string" ? stat.appId : "";
            const format = typeof stat.format === "string" ? stat.format : "";
            if (!appId || !format) continue;

            let ecpm = Number(stat.ecpmTry);
            if (!Number.isFinite(ecpm) || ecpm <= 0) {
                const earningsTry = Number(stat.earningsTry);
                const impressions = Number(stat.impressions);
                if (!Number.isFinite(earningsTry) || !Number.isFinite(impressions) || impressions <= 0) {
                    continue;
                }
                ecpm = (earningsTry / impressions) * 1000;
            }
            if (!Number.isFinite(ecpm) || ecpm <= 0) continue;

            const key = `${appId}::${format}`;
            const current = accumulator.get(key) ?? { total: 0, count: 0 };
            current.total += ecpm;
            current.count += 1;
            accumulator.set(key, current);
        }

        if (consideredWeeks >= weeks) {
            break;
        }
    }

    const baseline = new Map<string, number>();
    for (const [key, value] of accumulator.entries()) {
        if (value.count > 0) {
            baseline.set(key, value.total / value.count);
        }
    }
    return baseline;
}
