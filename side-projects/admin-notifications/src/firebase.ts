import { initializeApp, getApps, type FirebaseApp } from "firebase/app";
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
  projectId: requireFirebaseEnv(
    "Firebase project id",
    ["VITE_FIREBASE_PROJECT_ID"],
    missingFirebaseEnv,
  ),
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

export const firestore = getFirestore(app);
export const functionsBaseUrl =
  explicitFunctionsBaseUrl ?? `https://${functionsRegion}-${firebaseConfig.projectId}.cloudfunctions.net`;
