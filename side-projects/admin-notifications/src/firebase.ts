import { initializeApp, getApps, type FirebaseApp } from "firebase/app";
import {
  GoogleAuthProvider,
  getAuth,
  setPersistence,
  browserLocalPersistence,
  browserSessionPersistence,
  inMemoryPersistence,
} from "firebase/auth";
import { getFirestore } from "firebase/firestore";

const env = import.meta.env as Record<string, string | undefined>;

function envValue(...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = env[key]?.trim();
    if (value) return value;
  }
  return undefined;
}

function requireFirebaseEnv(
  label: string,
  keys: string[],
  missing: string[],
): string {
  const value = envValue(...keys);
  if (value) return value;
  missing.push(`${label} (${keys.join(" or ")})`);
  return "";
}

const missingFirebaseEnv: string[] = [];
const firebaseConfig = {
  apiKey: requireFirebaseEnv(
    "Firebase API key",
    ["VITE_FIREBASE_API_KEY"],
    missingFirebaseEnv,
  ),
  authDomain: requireFirebaseEnv(
    "Firebase auth domain",
    ["VITE_FIREBASE_AUTH_DOMAIN"],
    missingFirebaseEnv,
  ),
  projectId: requireFirebaseEnv(
    "Firebase project id",
    ["VITE_FIREBASE_PROJECT_ID"],
    missingFirebaseEnv,
  ),
  appId: requireFirebaseEnv(
    "Firebase app id",
    ["VITE_FIREBASE_APP_ID"],
    missingFirebaseEnv,
  ),
  messagingSenderId: envValue("VITE_FIREBASE_MESSAGING_SENDER_ID"),
  storageBucket: envValue("VITE_FIREBASE_STORAGE_BUCKET"),
};

if (missingFirebaseEnv.length > 0) {
  throw new Error(
    [
      "side-projects/admin-notifications Firebase env is incomplete.",
      "Missing:",
      ...missingFirebaseEnv.map((item) => `- ${item}`),
      "Create `side-projects/admin-notifications/.env` from `side-projects/admin-notifications/.env.example`.",
    ].join("\n"),
  );
}

const app: FirebaseApp = getApps()[0] ?? initializeApp(firebaseConfig);

const functionsRegion = envValue("VITE_FIREBASE_FUNCTIONS_REGION") ?? "europe-west1";
const explicitFunctionsBaseUrl = envValue("VITE_FUNCTIONS_BASE_URL");

export const auth = getAuth(app);
export type AuthPersistenceMode = "local" | "session" | "memory";

let authPersistenceMode: AuthPersistenceMode = "memory";

export function getAuthPersistenceMode(): AuthPersistenceMode {
  return authPersistenceMode;
}

export const authPersistenceReady = (async () => {
  try {
    await setPersistence(auth, browserLocalPersistence);
    authPersistenceMode = "local";
    return;
  } catch (localError) {
    console.warn("Firebase Auth local persistence unavailable.", localError);
  }

  try {
    await setPersistence(auth, browserSessionPersistence);
    authPersistenceMode = "session";
    return;
  } catch (sessionError) {
    console.warn("Firebase Auth session persistence unavailable.", sessionError);
  }

  try {
    await setPersistence(auth, inMemoryPersistence);
    authPersistenceMode = "memory";
  } catch (memoryError) {
    console.error("Firebase Auth in-memory persistence setup failed.", memoryError);
  }
})();
export const firestore = getFirestore(app);
export const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: "select_account" });
export const functionsBaseUrl =
  explicitFunctionsBaseUrl ?? `https://${functionsRegion}-${firebaseConfig.projectId}.cloudfunctions.net`;
