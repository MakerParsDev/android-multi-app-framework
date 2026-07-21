import * as admin from "firebase-admin";

// Firebase Admin SDK başlat
admin.initializeApp();

// Function export'ları
// Legacy registerDevice source is intentionally not exported.
// Production registration is owned by the App Check-protected Cloudflare Worker;
// Android clients must never write Firestore /devices directly.
// export { registerDevice } from "./registerDevice";
export { dispatchNotifications } from "./dispatchNotifications";
export { otherAppsFeed } from "./otherAppsFeed";
export { sendTestNotification } from "./sendTestNotification";
export { deviceCoverageReport } from "./deviceCoverageReport";
export { adPerformance, generateAdPerformanceWeeklyReport } from "./adPerformanceReport";
export { adminAccessCheck } from "./adminAccessCheck";
export { adminGetRemoteConfig, adminUpdateRemoteConfig } from "./adminRemoteConfig";
export { adminGetFlavorHubSummary, adminGetAnalyticsSummary, adminGetRevenueSummary } from "./adminSummary";
export { healthCheck } from "./healthCheck";
export { recaptchaVerify } from "./recaptchaVerify";
export { verifyPurchase } from "./verifyPurchase";
