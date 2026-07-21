import { Timestamp, type DocumentData } from "firebase/firestore";
import type { User } from "firebase/auth";
import { serverTimestamp } from "firebase/firestore";
import ciApps from "@ciapps";
import {
  WEEKDAYS,
  type AnalyticsSummary,
  type EventStatus,
  type DeviceFinderItem,
  type FlavorVersionInfo,
  type FlavorHubSummary,
  type LocaleKey,
  type RemoteConfigEntry,
  type RemoteConfigTemplateResponse,
  type RevenueSummary,
  type ScheduledEventForm,
  type ScheduledEventRecord,
  type WeekdayKey,
  EVENT_STATUSES,
} from "./types";

type CiAppCatalogEntry = {
  flavor: string;
  package: string;
  name?: string;
  admob_app_id: string;
  ad_units: Record<string, string>;
};

/* ── Formatting ── */

export function formatDateTime(date?: Date | null): string {
  if (!date) return "-";
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

export function formatPercent(value?: number): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return "-";
  return `${value.toFixed(2)}%`;
}

export function formatTry(value?: number): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return "-";
  return `₺${value.toFixed(4)}`;
}

export function scheduleLabel(record: ScheduledEventRecord): string {
  if (record.date) return `Once: ${record.date} @ ${record.localDeliveryTime}`;
  if (record.recurrence) return `${record.recurrence} @ ${record.localDeliveryTime}`;
  return `@ ${record.localDeliveryTime}`;
}

export function packageLabel(packages: string[]): string {
  if (packages.includes("*")) return "All apps (*)";
  return `${packages.length} app(s)`;
}

/* ── Parsing ── */

function toDate(value: unknown): Date | null {
  if (!value) return null;
  if (value instanceof Date) return value;
  if (value instanceof Timestamp) return value.toDate();
  return null;
}

function parseEventStatus(value: unknown): ScheduledEventRecord["status"] {
  if (typeof value === "string" && EVENT_STATUSES.includes(value as EventStatus)) {
    return value as ScheduledEventRecord["status"];
  }
  return "scheduled";
}

function parseLocaleMap(value: unknown): Record<LocaleKey, string> {
  const input = (value && typeof value === "object" ? value : {}) as Record<string, unknown>;
  return {
    tr: typeof input.tr === "string" ? input.tr : "",
    en: typeof input.en === "string" ? input.en : "",
    de: typeof input.de === "string" ? input.de : "",
  };
}

export function parseDeviceFinderItem(docId: string, raw: DocumentData): DeviceFinderItem {
  const token = typeof raw.fcmToken === "string" ? raw.fcmToken.trim() : "";
  return {
    id: docId,
    packageName: typeof raw.packageName === "string" ? raw.packageName : "",
    locale: typeof raw.locale === "string" ? raw.locale : "",
    timezone: typeof raw.timezone === "string" ? raw.timezone : "",
    notificationsEnabled:
      typeof raw.notificationsEnabled === "boolean" ? raw.notificationsEnabled : true,
    deviceModel: typeof raw.deviceModel === "string" ? raw.deviceModel : undefined,
    appVersion: typeof raw.appVersion === "string" ? raw.appVersion : undefined,
    updatedAt: toDate(raw.updatedAt),
    hasValidToken: token.length >= 80,
  };
}

export function parseEvent(docId: string, raw: DocumentData): ScheduledEventRecord {
  return {
    id: docId,
    name: typeof raw.name === "string" ? raw.name : "",
    type: typeof raw.type === "string" ? raw.type : "campaign",
    status: parseEventStatus(raw.status),
    localDeliveryTime:
      typeof raw.localDeliveryTime === "string" ? raw.localDeliveryTime : "21:00",
    targetTimezones: Array.isArray(raw.targetTimezones)
      ? raw.targetTimezones.filter((x: unknown): x is string => typeof x === "string")
      : undefined,
    topic: typeof raw.topic === "string" ? raw.topic : "",
    packages: Array.isArray(raw.packages)
      ? raw.packages.filter((x: unknown): x is string => typeof x === "string")
      : ["*"],
    title: parseLocaleMap(raw.title),
    body: parseLocaleMap(raw.body),
    date: typeof raw.date === "string" ? raw.date : undefined,
    recurrence: typeof raw.recurrence === "string" ? raw.recurrence : undefined,
    sentTimezones: Array.isArray(raw.sentTimezones)
      ? raw.sentTimezones.filter((x: unknown): x is string => typeof x === "string")
      : [],
    lastResetAt: toDate(raw.lastResetAt),
    lastDispatchedAt: toDate(raw.lastDispatchedAt),
    createdAt: toDate(raw.createdAt),
    updatedAt: toDate(raw.updatedAt),
    createdBy: typeof raw.createdBy === "string" ? raw.createdBy : undefined,
    updatedBy: typeof raw.updatedBy === "string" ? raw.updatedBy : undefined,
  };
}

/* ── Form ↔ Record ── */

export function formFromRecord(record: ScheduledEventRecord): ScheduledEventForm {
  let scheduleMode: ScheduledEventForm["scheduleMode"] = "daily";
  let weeklyDay: WeekdayKey = "friday";
  if (record.date) {
    scheduleMode = "once";
  } else if (record.recurrence?.startsWith("weekly:")) {
    scheduleMode = "weekly";
    const candidate = record.recurrence.split(":")[1] as WeekdayKey | undefined;
    if (candidate && WEEKDAYS.includes(candidate)) {
      weeklyDay = candidate;
    }
  } else if (record.recurrence === "daily") {
    scheduleMode = "daily";
  }

  return {
    name: record.name,
    type: record.type,
    status: record.status,
    localDeliveryTime: record.localDeliveryTime,
    targetTimezonesInput: (record.targetTimezones ?? []).join(", "),
    topic: record.topic,
    packages: record.packages.length > 0 ? record.packages : ["*"],
    scheduleMode,
    date: record.date ?? "",
    weeklyDay,
    title: { ...record.title },
    body: { ...record.body },
  };
}

export function normalizePackages(packages: string[]): string[] {
  if (packages.includes("*")) return ["*"];
  return [...new Set(packages)].sort();
}

export function parseTargetTimezonesInput(input: string): string[] {
  return Array.from(
    new Set(
      input
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  );
}

export function buildPayload(form: ScheduledEventForm, user: User, isCreate: boolean) {
  const targetTimezones = parseTargetTimezonesInput(form.targetTimezonesInput);
  const base: Record<string, unknown> = {
    name: form.name.trim(),
    type: form.type.trim(),
    status: form.status,
    localDeliveryTime: form.localDeliveryTime.trim(),
    targetTimezones: targetTimezones.length > 0 ? targetTimezones : null,
    topic: form.topic.trim() || null,
    packages: normalizePackages(form.packages),
    title: {
      tr: form.title.tr.trim(),
      en: form.title.en.trim(),
      de: form.title.de.trim(),
    },
    body: {
      tr: form.body.tr.trim(),
      en: form.body.en.trim(),
      de: form.body.de.trim(),
    },
    updatedAt: serverTimestamp(),
    updatedBy: user.uid,
  };

  if (form.scheduleMode === "once") {
    base.date = form.date.trim();
    base.recurrence = null;
  } else if (form.scheduleMode === "daily") {
    base.date = null;
    base.recurrence = "daily";
  } else {
    base.date = null;
    base.recurrence = `weekly:${form.weeklyDay}`;
  }

  if (isCreate) {
    base.sentTimezones = [];
    base.lastResetAt = null;
    base.lastDispatchedAt = null;
    base.createdAt = serverTimestamp();
    base.createdBy = user.uid;
  }

  return base;
}

export function validateForm(form: ScheduledEventForm): string | null {
  if (!form.name.trim()) return "Event name is required.";
  if (!form.type.trim()) return "Type is required.";
  if (!/^\d{2}:\d{2}$/.test(form.localDeliveryTime.trim())) {
    return "Local delivery time must be HH:mm.";
  }
  if (form.scheduleMode === "once" && !form.date.trim()) {
    return "Date is required for one-time events.";
  }
  if (!form.title.tr.trim()) return "TR title is required.";
  if (!form.body.tr.trim()) return "TR body is required.";
  if (normalizePackages(form.packages).length === 0) {
    return "At least one target package or '*' is required.";
  }
  const targetTimezones = parseTargetTimezonesInput(form.targetTimezonesInput);
  const invalidTimezone = targetTimezones.find((item) => !item.includes("/"));
  if (invalidTimezone) {
    return `Invalid timezone format: ${invalidTimezone}`;
  }
  return null;
}

export function parseTestPushDataInput(raw: string): { data: Record<string, string> | null; error: string | null } {
  const lines = raw
    .split(/\r?\n/g)
    .map((line) => line.trim())
    .filter(Boolean);

  if (lines.length === 0) {
    return { data: null, error: null };
  }

  const data: Record<string, string> = {};
  for (const line of lines) {
    const idx = line.indexOf("=");
    if (idx <= 0) {
      return { data: null, error: `Invalid data line (expected key=value): ${line}` };
    }
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1);
    if (!key) {
      return { data: null, error: `Invalid empty data key: ${line}` };
    }
    data[key] = value;
  }

  return { data, error: null };
}

export function summarizeApiError(errorPayload: unknown, fallback: string): string {
  if (
    errorPayload &&
    typeof errorPayload === "object" &&
    "error" in errorPayload &&
    typeof (errorPayload as { error?: unknown }).error === "string"
  ) {
    return (errorPayload as { error: string }).error;
  }
  return fallback;
}

export async function fetchAdminFunctionJson<T>(
  input: {
    endpoint: string;
    idToken: string;
    body?: Record<string, unknown>;
    method?: "GET" | "POST";
  },
): Promise<T> {
  const { endpoint, idToken, body, method = body ? "POST" : "GET" } = input;
  const init: RequestInit = {
    method,
    headers: {
      Authorization: `Bearer ${idToken}`,
    },
  };

  if (method !== "GET") {
    init.headers = {
      ...init.headers,
      "Content-Type": "application/json",
    };
    init.body = JSON.stringify(body ?? {});
  }

  const response = await fetch(endpoint, init);
  let responseBody: unknown = null;
  try {
    responseBody = await response.json();
  } catch {
    responseBody = null;
  }

  if (!response.ok) {
    throw new Error(
      summarizeApiError(responseBody, `HTTP ${response.status}: Request failed`),
    );
  }

  return responseBody as T;
}

function inferRemoteConfigValueType(value: string): RemoteConfigEntry["valueType"] {
  const trimmed = value.trim();
  if (trimmed === "true" || trimmed === "false") return "boolean";
  if (trimmed !== "" && Number.isFinite(Number(trimmed))) return "number";
  if (
    (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
    (trimmed.startsWith("[") && trimmed.endsWith("]"))
  ) {
    try {
      JSON.parse(trimmed);
      return "json";
    } catch {
      return "string";
    }
  }
  return "string";
}

export function parseRemoteConfigEntries(
  template: RemoteConfigTemplateResponse | null,
): RemoteConfigEntry[] {
  const entries = new Map<string, RemoteConfigEntry>();
  const addEntries = (
    parameters: RemoteConfigTemplateResponse["parameters"] | undefined,
    groupKey?: string,
    groupLabel?: string,
  ) => {
    if (!parameters) return;
    for (const [key, parameter] of Object.entries(parameters)) {
      const value = parameter.defaultValue?.value ?? "";
      entries.set(key, {
        key,
        value,
        valueType: inferRemoteConfigValueType(value),
        description: parameter.description ?? "",
        groupKey,
        groupLabel,
      });
    }
  };

  addEntries(template?.parameters);

  for (const [groupKey, group] of Object.entries(template?.parameterGroups ?? {})) {
    addEntries(group.parameters, groupKey, group.description ?? groupKey);
  }

  return Array.from(entries.values()).sort((a, b) => {
    const leftGroup = a.groupLabel ?? "";
    const rightGroup = b.groupLabel ?? "";
    return leftGroup.localeCompare(rightGroup) || a.key.localeCompare(b.key);
  });
}

export function isFlavorHubSummary(value: unknown): value is FlavorHubSummary {
  return typeof value === "object" && value !== null && "coverage" in value;
}

export function isAnalyticsSummary(value: unknown): value is AnalyticsSummary {
  return typeof value === "object" && value !== null && "totalDevices" in value;
}

export function isRevenueSummary(value: unknown): value is RevenueSummary {
  return typeof value === "object" && value !== null && "activeSubscriptions" in value;
}

export function parseFlavorVersions(): Record<string, FlavorVersionInfo> {
  const raw = import.meta.env.VITE_FLAVOR_VERSIONS as string | undefined;
  if (!raw) return {};

  try {
    const parsed = JSON.parse(raw) as Record<string, FlavorVersionInfo>;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

/* ── Shared Constants ── */

export const sortedApps = [...(ciApps as CiAppCatalogEntry[])].sort((a, b) =>
  a.flavor.localeCompare(b.flavor),
);
export const appBuildId = (import.meta.env.VITE_APP_BUILD as string | undefined) ?? "dev-local";
export const appBuildTimeRaw = (import.meta.env.VITE_APP_BUILD_TIME as string | undefined) ?? "";
export const appBuildTime = appBuildTimeRaw ? formatDateTime(new Date(appBuildTimeRaw)) : "-";
export const localTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || "local";
