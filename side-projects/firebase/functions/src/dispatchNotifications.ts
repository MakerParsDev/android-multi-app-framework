import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";
import { getMatchingTimezones, getLocalDate, getLocalDayOfWeek, ALL_TIMEZONES } from "./utils/timezone";
import { sendToTokens } from "./utils/fcmSender";

/**
 * Event dokümanının tipi.
 */
interface ScheduledEvent {
    type: string;              // "kandil", "cuma", "ramazan", "gunluk"
    name: string;              // "Miraç Kandili"
    date?: string;             // "2026-02-27" (tek seferlik) veya undefined (tekrarlayan)
    recurrence?: string;       // "weekly:friday", "daily", undefined
    localDeliveryTime: string; // "21:00"
    targetTimezones?: string[]; // boşsa global timezone seti
    topic?: string;            // "dini-bildirim"
    packages: string[];        // ["*"] veya ["com.parsfilo.yasinsuresi"]
    title: Record<string, string>;  // { "tr": "...", "en": "...", "ar": "..." }
    body: Record<string, string>;   // { "tr": "...", "en": "...", "ar": "..." }
    status: string;            // "scheduled", "sent", "expired"
    sentTimezones?: string[];  // Gönderilen timezone'lar
    lastResetAt?: admin.firestore.Timestamp; // Recurrence gönderim penceresi başlangıcı
}

/**
 * Cihaz dokümanının tipi.
 */
interface DeviceDoc {
    fcmToken: string;
    timezone: string;
    locale: string;
    packageName: string;
    notificationsEnabled: boolean;
}

const FIRESTORE_IN_QUERY_LIMIT = 10;
const DELIVERY_WINDOW_MINUTES = 60;

/**
 * dispatchNotifications — Her saat başı çalışır.
 *
 * 1. scheduled_events koleksiyonundan aktif event'leri çeker
 * 2. Her event için hangi timezone'ların teslimat saatine ulaştığını hesaplar
 * 3. O timezone'daki cihazları filtreler
 * 4. Her cihazın locale'ine göre doğru dildeki mesajı seçer
 * 5. FCM batch gönderir
 * 6. Gönderilen timezone'ları işaretler
 */
export const dispatchNotifications = onSchedule(
    {
        schedule: "0 * * * *", // Her saat başı
        region: "europe-west1",
        timeZone: "UTC",
    },
    async () => {
        const db = admin.firestore();

        // 1. Aktif event'leri çek
        const eventsSnap = await db
            .collection("scheduled_events")
            .where("status", "==", "scheduled")
            .get();

        if (eventsSnap.empty) {
            logger.info("No scheduled events found.");
            return;
        }

        logger.info(`Found ${eventsSnap.size} scheduled event(s).`);

        for (const eventDoc of eventsSnap.docs) {
            const event = eventDoc.data() as ScheduledEvent;

            try {
                await processEvent(db, eventDoc.id, event);
            } catch (error) {
                logger.error(`Error processing event ${eventDoc.id}`, { error });
            }
        }
    },
);

/**
 * Tek bir event'i işler: tarih/gün kontrolü, timezone eşleştirmesi, FCM gönderimi.
 */
async function processEvent(
    db: admin.firestore.Firestore,
    eventId: string,
    event: ScheduledEvent,
): Promise<void> {
    const now = new Date();
    const effectiveEvent = await resetSentTimezonesIfPeriodElapsed(db, eventId, event, now);
    const sentTimezones = effectiveEvent.sentTimezones ?? [];
    const targetTimezones = normalizeTargetTimezones(effectiveEvent.targetTimezones);

    // 2. Hedef teslimat saatine uyan timezone'ları bul
    const matchingTimezones = getMatchingTimezones(
        effectiveEvent.localDeliveryTime,
        now,
        DELIVERY_WINDOW_MINUTES,
    )
        .filter((tz) => targetTimezones.length === 0 || targetTimezones.includes(tz));

    // Daha önce gönderilenleri çıkar
    const unsentTimezones = matchingTimezones.filter(
        (tz) => !sentTimezones.includes(tz),
    );

    if (unsentTimezones.length === 0) {
        return; // Bu saat dilimlerine zaten gönderilmiş veya eşleşen yok
    }

    if (effectiveEvent.topic?.trim()) {
        logger.warn(
            `Event "${effectiveEvent.name}" contains topic="${effectiveEvent.topic}" but dispatch ` +
            "currently uses timezone/device targeting only (topic is metadata-only).",
        );
    }

    // 3. Tarih/gün kontrolü (timezone bazlı)
    const eligibleTimezones = unsentTimezones.filter((timezone) => {
        if (effectiveEvent.date) {
            return getLocalDate(timezone, now) === effectiveEvent.date;
        }
        if (effectiveEvent.recurrence) {
            return matchesRecurrence(effectiveEvent.recurrence, timezone, now);
        }
        return true;
    });

    if (eligibleTimezones.length === 0) {
        return;
    }

    logger.info(`Processing event "${effectiveEvent.name}" for timezone(s): ${eligibleTimezones.join(", ")}`);

    // 4. Bu timezone'lardaki cihazları çek
    const devices = await getDevicesForTimezones(db, eligibleTimezones, effectiveEvent.packages);

    if (devices.length === 0) {
        logger.info(`No devices found for timezones: ${eligibleTimezones.join(", ")}`);
        // Yine de timezone'ları gönderildi olarak işaretle
        await markTimezonesAsSent(db, eventId, eligibleTimezones);
        return;
    }

    // 5. Locale'e göre grupla ve gönder
    const localeGroups = groupByLocale(devices);

    let totalSuccess = 0;
    let totalFailure = 0;
    const allInvalidTokens: string[] = [];

    for (const [locale, tokens] of Object.entries(localeGroups)) {
        const lang = locale.split("-")[0]; // "tr-TR" → "tr"
        const title = effectiveEvent.title[lang] ?? effectiveEvent.title["tr"] ?? effectiveEvent.name;
        const body = effectiveEvent.body[lang] ?? effectiveEvent.body["tr"] ?? "";

        const result = await sendToTokens(tokens, { title, body }, {
            type: effectiveEvent.type,
            eventId: eventId,
        });

        totalSuccess += result.successCount;
        totalFailure += result.failureCount;
        allInvalidTokens.push(...result.invalidTokens);
    }

    logger.info(
        `Event "${effectiveEvent.name}": sent=${totalSuccess}, failed=${totalFailure}, ` +
        `invalidTokens=${allInvalidTokens.length}`,
    );

    // 6. Geçersiz token'ları temizle
    if (allInvalidTokens.length > 0) {
        await cleanupInvalidTokens(db, allInvalidTokens);
    }

    // 7. Gönderilen timezone'ları kaydet
    await markTimezonesAsSent(db, eventId, eligibleTimezones);
}

async function resetSentTimezonesIfPeriodElapsed(
    db: admin.firestore.Firestore,
    eventId: string,
    fallbackEvent: ScheduledEvent,
    now: Date,
): Promise<ScheduledEvent> {
    const eventRef = db.collection("scheduled_events").doc(eventId);
    const nowMillis = now.getTime();
    const nowTimestamp = admin.firestore.Timestamp.fromMillis(nowMillis);

    return db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(eventRef);
        if (!snapshot.exists) {
            return fallbackEvent;
        }

        const currentEvent = snapshot.data() as ScheduledEvent;
        if (!currentEvent.recurrence) {
            return currentEvent;
        }

        const lastResetMillis = currentEvent.lastResetAt?.toMillis();
        const updates: Record<string, unknown> = {};

        if (isRecurrenceResetDue(currentEvent.recurrence, lastResetMillis, nowMillis)) {
            updates.sentTimezones = [];
            updates.lastResetAt = nowTimestamp;
            logger.info(
                `Reset sentTimezones for recurrence event "${currentEvent.name}" (eventId=${eventId}).`,
            );
        } else if (!currentEvent.lastResetAt) {
            updates.lastResetAt = nowTimestamp;
        }

        if (Object.keys(updates).length > 0) {
            transaction.update(eventRef, updates);
        }

        return { ...currentEvent, ...updates } as ScheduledEvent;
    });
}

export function isRecurrenceResetDue(
    recurrence: string,
    lastResetMillis: number | undefined,
    nowMillis: number,
): boolean {
    const periodMillis = recurrencePeriodMillis(recurrence);
    if (periodMillis == null || lastResetMillis == null) {
        return false;
    }
    return nowMillis - lastResetMillis >= periodMillis;
}

export function recurrencePeriodMillis(recurrence: string): number | null {
    if (recurrence === "daily") {
        return 24 * 60 * 60 * 1000;
    }
    if (recurrence.startsWith("weekly:")) {
        return 7 * 24 * 60 * 60 * 1000;
    }
    return null;
}

/**
 * Tekrarlama kuralını kontrol eder.
 */
function matchesRecurrence(recurrence: string, timezone: string, now: Date): boolean {
    if (recurrence === "daily") {
        return true;
    }
    if (recurrence.startsWith("weekly:")) {
        const dayName = recurrence.split(":")[1]; // "friday"
        const dayMap: Record<string, number> = {
            sunday: 0, monday: 1, tuesday: 2, wednesday: 3,
            thursday: 4, friday: 5, saturday: 6,
        };
        const targetDay = dayMap[dayName];
        const currentDay = getLocalDayOfWeek(timezone, now);
        return targetDay === currentDay;
    }
    return false;
}

/**
 * Belirli timezone'lardaki cihazları Firestore'dan çeker.
 * Paket filtresi gerekiyorsa, Firestore'da ikinci bir `in` filtresi kullanmamak için
 * uygulama tarafında filtrelenir.
 */
async function getDevicesForTimezones(
    db: admin.firestore.Firestore,
    timezones: string[],
    packages: string[],
): Promise<Array<{ token: string; locale: string }>> {
    const devices: Array<{ token: string; locale: string }> = [];
    const hasPackageFilter = packages.length > 0 && !packages.includes("*");
    const allowedPackages = hasPackageFilter ? new Set(packages) : null;
    const seenTokens = new Set<string>();

    // Firestore `in` sorgusu en fazla 10 değer kabul eder.
    const chunkSize = FIRESTORE_IN_QUERY_LIMIT;
    for (let i = 0; i < timezones.length; i += chunkSize) {
        const tzChunk = timezones.slice(i, i + chunkSize);

        const query: admin.firestore.Query = db
            .collection("devices")
            .where("timezone", "in", tzChunk)
            .where("notificationsEnabled", "==", true);

        const snap = await query.get();

        snap.forEach((doc) => {
            const data = doc.data() as DeviceDoc;
            if (allowedPackages && !allowedPackages.has(data.packageName)) {
                return;
            }
            if (!data.fcmToken || seenTokens.has(data.fcmToken)) {
                return;
            }
            seenTokens.add(data.fcmToken);
            devices.push({
                token: data.fcmToken,
                locale: data.locale,
            });
        });
    }

    return devices;
}

/**
 * Cihazları locale'e göre gruplar.
 */
function groupByLocale(
    devices: Array<{ token: string; locale: string }>,
): Record<string, string[]> {
    const groups: Record<string, string[]> = {};
    for (const device of devices) {
        const locale = device.locale || "tr-TR";
        if (!groups[locale]) {
            groups[locale] = [];
        }
        groups[locale].push(device.token);
    }
    return groups;
}

/**
 * Gönderilen timezone'ları event dokümanına yazar.
 * Tüm timezone'lara gönderildiyse status'u "sent" yapar.
 */
async function markTimezonesAsSent(
    db: admin.firestore.Firestore,
    eventId: string,
    newlySent: string[],
): Promise<void> {
    const eventRef = db.collection("scheduled_events").doc(eventId);
    await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(eventRef);
        if (!snapshot.exists) {
            return;
        }

        const event = snapshot.data() as ScheduledEvent;
        const existingSent = event.sentTimezones ?? [];
        const allSent = [...new Set([...existingSent, ...newlySent])];

        const updateData: Record<string, unknown> = {
            sentTimezones: allSent,
        };

        const targetTimezones = normalizeTargetTimezones(event.targetTimezones);
        const requiredTimezoneCount =
            targetTimezones.length > 0 ? targetTimezones.length : ALL_TIMEZONES.length;

        // Tek seferlik event ve hedeflenen timezone'lara gönderim tamamlandıysa → "sent"
        if (!event.recurrence && allSent.length >= requiredTimezoneCount) {
            updateData.status = "sent";
            logger.info(`Event "${event.name}" marked as SENT (targeted timezones covered).`);
        }

        transaction.update(eventRef, updateData);
    });
}

/**
 * Geçersiz FCM token'larına sahip cihazları Firestore'dan siler.
 * Firestore `in` sorgusu en fazla 30 değer kabul ettiğinden token'ları 30'luk gruplara böler.
 */
async function cleanupInvalidTokens(
    db: admin.firestore.Firestore,
    invalidTokens: string[],
): Promise<void> {
    let totalCleaned = 0;

    for (let i = 0; i < invalidTokens.length; i += 30) {
        const chunk = invalidTokens.slice(i, i + 30);
        const snap = await db
            .collection("devices")
            .where("fcmToken", "in", chunk)
            .get();

        if (!snap.empty) {
            const batch = db.batch();
            snap.forEach((doc) => batch.delete(doc.ref));
            await batch.commit();
            totalCleaned += snap.size;
        }
    }

    if (totalCleaned > 0) {
        logger.info(`Cleaned up ${totalCleaned} invalid device(s).`);
    }
}

function normalizeTargetTimezones(value: unknown): string[] {
    if (!Array.isArray(value)) return [];
    return Array.from(
        new Set(
            value
                .filter((item): item is string => typeof item === "string")
                .map((item) => item.trim())
                .filter((item) => item.length > 0),
        ),
    );
}
