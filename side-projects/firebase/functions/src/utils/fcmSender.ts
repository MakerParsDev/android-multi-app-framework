import * as admin from "firebase-admin";

/**
 * FCM batch gönderim yardımcısı.
 * FCM API'si tek seferde en fazla 500 mesaj gönderebildiği için
 * token listesini 500'lük gruplara böler.
 */

const BATCH_SIZE = 500;

interface NotificationContent {
    title: string;
    body: string;
}

interface SendResult {
    successCount: number;
    failureCount: number;
    invalidTokens: string[];
}

/**
 * Belirli bir token listesine FCM bildirimi gönderir.
 * Token'ları 500'lük gruplar halinde gönderir.
 */
export async function sendToTokens(
    tokens: string[],
    notification: NotificationContent,
    data?: Record<string, string>,
): Promise<SendResult> {
    if (tokens.length === 0) {
        return { successCount: 0, failureCount: 0, invalidTokens: [] };
    }

    let totalSuccess = 0;
    let totalFailure = 0;
    const invalidTokens: string[] = [];

    // Token'ları 500'lük gruplara böl
    for (let i = 0; i < tokens.length; i += BATCH_SIZE) {
        const batch = tokens.slice(i, i + BATCH_SIZE);

        const messages: admin.messaging.TokenMessage[] = batch.map((token) => ({
            token,
            notification: {
                title: notification.title,
                body: notification.body,
            },
            data: data ?? {},
            android: {
                priority: "high" as const,
                notification: {
                    channelId: "app_notifications",
                    priority: "default" as const,
                },
            },
        }));

        const response = await admin.messaging().sendEach(messages);

        totalSuccess += response.successCount;
        totalFailure += response.failureCount;

        // Geçersiz token'ları topla (temizlik için)
        response.responses.forEach((resp, idx) => {
            if (resp.error) {
                const code = resp.error.code;
                if (
                    code === "messaging/invalid-registration-token" ||
                    code === "messaging/registration-token-not-registered"
                ) {
                    invalidTokens.push(batch[idx]);
                }
            }
        });
    }

    return { successCount: totalSuccess, failureCount: totalFailure, invalidTokens };
}

/**
 * FCM topic'e bildirim gönderir.
 */
export async function sendToTopic(
    topic: string,
    notification: NotificationContent,
    data?: Record<string, string>,
): Promise<string> {
    const message: admin.messaging.TopicMessage = {
        topic,
        notification: {
            title: notification.title,
            body: notification.body,
        },
        data: data ?? {},
        android: {
            priority: "high",
            notification: {
                channelId: "app_notifications",
                priority: "default",
            },
        },
    };

    return admin.messaging().send(message);
}
