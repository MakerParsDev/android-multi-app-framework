const PLAY_URL_BASE = "https://play.google.com/store/apps/details?id=";

export async function fetchApps() {
  const response = await fetch("/other_apps.json", { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`other_apps fetch failed (${response.status})`);
  }
  const data = await response.json();
  if (!Array.isArray(data)) return [];
  return data
    .filter((item) => item && typeof item.packageName === "string" && typeof item.appName === "string")
    .sort((a, b) => a.appName.localeCompare(b.appName, "tr"));
}

export function playUrl(packageName) {
  return `${PLAY_URL_BASE}${encodeURIComponent(packageName)}`;
}

export function slugify(value) {
  return String(value || "")
    .toLocaleLowerCase("tr-TR")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export function appSlug(app) {
  const packageSuffix = String(app.packageName || "").replace(/^com\.parsfilo\./, "");
  const byPackage = slugify(packageSuffix.replace(/_/g, "-"));
  if (byPackage) return byPackage;
  return slugify(app.appName);
}

export function findAppBySlug(apps, slug) {
  const normalized = slugify(slug);
  return (
    apps.find((app) => appSlug(app) === normalized) ||
    apps.find((app) => slugify(app.packageName) === normalized) ||
    null
  );
}
