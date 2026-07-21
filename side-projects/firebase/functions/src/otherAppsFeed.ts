import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

type OtherApp = {
    appName: string;
    packageName: string;
    appIcon?: string;
    isNew?: boolean;
};

type IconCacheEntry = {
    iconUrl: string;
    expiresAt: number;
};

type FeedCacheEntry = {
    data: OtherApp[];
    expiresAt: number;
};

const SEED_APPS_URL = "https://parsfilo.com/other_apps.json";
const PLAY_DETAILS_URL = "https://play.google.com/store/apps/details?id=";
const PLAY_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36";
const ICON_TTL_MS = 24 * 60 * 60 * 1000;
const FEED_TTL_MS = 10 * 60 * 1000;
const ICON_SIZE_SUFFIX = "w48-h48-rw";

const iconCache = new Map<string, IconCacheEntry>();
let feedCache: FeedCacheEntry | null = null;

export const otherAppsFeed = onRequest(
    { region: "europe-west1", cors: true },
    async (req, res) => {
        if (req.method !== "GET") {
            res.status(405).json({ error: "Method not allowed" });
            return;
        }

        const forceRefresh = req.query.force === "1";

        if (!forceRefresh && feedCache && feedCache.expiresAt > Date.now()) {
            res.set("Cache-Control", "public, max-age=300, s-maxage=1800");
            res.status(200).json(feedCache.data);
            return;
        }

        try {
            const seedApps = await fetchSeedApps();
            const resolvedApps = await Promise.all(
                seedApps.map((app) => resolveIconForApp(app, forceRefresh)),
            );

            feedCache = {
                data: resolvedApps,
                expiresAt: Date.now() + FEED_TTL_MS,
            };

            res.set("Cache-Control", "public, max-age=300, s-maxage=1800");
            res.status(200).json(resolvedApps);
        } catch (error) {
            logger.error("otherAppsFeed failed", { error });
            res.status(500).json({ error: "Unable to build app list feed" });
        }
    },
);

async function fetchSeedApps(): Promise<OtherApp[]> {
    const response = await fetch(SEED_APPS_URL, {
        headers: {
            "Accept": "application/json",
            "User-Agent": PLAY_USER_AGENT,
        },
    });

    if (!response.ok) {
        throw new Error(`Seed app list fetch failed with HTTP ${response.status}`);
    }

    const parsed = (await response.json()) as unknown;
    if (!Array.isArray(parsed)) {
        throw new Error("Seed app list is not an array");
    }

    return parsed
        .filter(isOtherApp)
        .map((app) => ({
            appName: app.appName.trim(),
            packageName: app.packageName.trim(),
            appIcon: normalizeIconUrl(app.appIcon?.trim()),
            isNew: Boolean(app.isNew),
        }));
}

async function resolveIconForApp(app: OtherApp, forceRefresh: boolean): Promise<OtherApp> {
    const packageName = app.packageName;
    const cached = iconCache.get(packageName);
    const now = Date.now();

    if (!forceRefresh && cached && cached.expiresAt > now) {
        return {
            ...app,
            appIcon: normalizeIconUrl(cached.iconUrl),
        };
    }

    const fetchedIcon = await fetchPlayIcon(packageName);
    if (fetchedIcon) {
        const normalized = normalizeIconUrl(fetchedIcon);
        iconCache.set(packageName, {
            iconUrl: normalized,
            expiresAt: now + ICON_TTL_MS,
        });
        return {
            ...app,
            appIcon: normalized,
        };
    }

    if (cached) {
        return {
            ...app,
            appIcon: normalizeIconUrl(cached.iconUrl),
        };
    }

    return {
        ...app,
        appIcon: normalizeIconUrl(app.appIcon),
    };
}

async function fetchPlayIcon(packageName: string): Promise<string | null> {
    const detailsUrl = `${PLAY_DETAILS_URL}${encodeURIComponent(packageName)}&hl=tr&gl=TR`;
    const response = await fetch(detailsUrl, {
        headers: {
            "User-Agent": PLAY_USER_AGENT,
            "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8",
        },
    });

    if (!response.ok) {
        logger.warn("Play details fetch failed", { packageName, status: response.status });
        return null;
    }

    const html = await response.text();
    const ogImage = extractOgImageUrl(html);
    if (!ogImage) {
        logger.warn("Play details parse failed: og:image not found", { packageName });
        return null;
    }
    return normalizeIconUrl(ogImage);
}

function extractOgImageUrl(html: string): string | null {
    const match =
        html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["'][^>]*>/i) ??
        html.match(/<meta\s+content=["']([^"']+)["']\s+property=["']og:image["'][^>]*>/i);
    if (!match || !match[1]) {
        return null;
    }
    return decodeHtmlEntities(match[1]);
}

function normalizeIconUrl(url?: string): string {
    if (!url) {
        return "";
    }

    const trimmed = url.trim();
    if (!trimmed.includes("play-lh.googleusercontent.com")) {
        return trimmed;
    }

    if (/=w\d+-h\d+-rw$/i.test(trimmed)) {
        return trimmed.replace(/=w\d+-h\d+-rw$/i, `=${ICON_SIZE_SUFFIX}`);
    }

    if (/=s\d+(-[a-z0-9]+)?$/i.test(trimmed)) {
        return trimmed.replace(/=s\d+(-[a-z0-9]+)?$/i, `=${ICON_SIZE_SUFFIX}`);
    }

    if (trimmed.includes("=")) {
        return `${trimmed.split("=")[0]}=${ICON_SIZE_SUFFIX}`;
    }

    return `${trimmed}=${ICON_SIZE_SUFFIX}`;
}

function decodeHtmlEntities(value: string): string {
    return value
        .replace(/&amp;/g, "&")
        .replace(/&quot;/g, "\"")
        .replace(/&#39;/g, "'");
}

function isOtherApp(value: unknown): value is OtherApp {
    if (!value || typeof value !== "object") {
        return false;
    }
    const candidate = value as Record<string, unknown>;
    return (
        typeof candidate.appName === "string" &&
        candidate.appName.trim().length > 0 &&
        typeof candidate.packageName === "string" &&
        candidate.packageName.trim().length > 0
    );
}

