const REQUIRED_KEYS = [
  "ADMIN_ALLOWED_EMAILS",
];

const OPTIONAL_KEYS = [
  "GOOGLE_RECAPTCHA_SECRET_KEY",
  "GOOGLE_RECAPTCHA_SITE_KEY",
  "ADMOB_CLIENT_ID",
  "ADMOB_CLIENT_SECRET",
  "ADMOB_REFRESH_TOKEN",
  "ADMOB_PUBLISHER_ID",
  "VITE_FUNCTIONS_BASE_URL",
];

let failed = false;

for (const key of REQUIRED_KEYS) {
  const value = (process.env[key] || "").trim();
  if (!value) {
    console.error(`Missing required environment variable: ${key}`);
    failed = true;
  }
}

for (const key of OPTIONAL_KEYS) {
  const value = (process.env[key] || "").trim();
  if (!value) {
    console.warn(`Optional environment variable is not set: ${key}`);
  }
}

if (failed) {
  process.exit(1);
}

console.log("Firebase functions environment contract is valid.");
