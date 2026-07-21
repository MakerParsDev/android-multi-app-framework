export type LocaleKey = "tr" | "en" | "de";

export type EventStatus = "scheduled" | "paused" | "sent" | "expired";

export type ScheduleMode = "once" | "daily" | "weekly";

export type LoadState = "loading" | "ready" | "error";

export type AdminState = "checking" | "authorized" | "unauthorized";
export type AdminTab =
  | "flavor-hub"
  | "events"
  | "test-push"
  | "remote-config"
  | "analytics"
  | "revenue"
  | "system-health";
export type TestPushSubTab = "single-device" | "coverage" | "ad-health";
export type TestPushTargetMode = "installationId" | "token";

export interface ScheduledEventRecord {
  id: string;
  name: string;
  type: string;
  status: EventStatus;
  localDeliveryTime: string;
  targetTimezones?: string[];
  topic: string;
  packages: string[];
  title: Record<LocaleKey, string>;
  body: Record<LocaleKey, string>;
  date?: string;
  recurrence?: string;
  sentTimezones: string[];
  lastResetAt?: Date | null;
  lastDispatchedAt?: Date | null;
  createdAt?: Date | null;
  updatedAt?: Date | null;
  createdBy?: string;
  updatedBy?: string;
}

export interface ScheduledEventForm {
  name: string;
  type: string;
  status: EventStatus;
  localDeliveryTime: string;
  targetTimezonesInput: string;
  topic: string;
  packages: string[];
  scheduleMode: ScheduleMode;
  date: string;
  weeklyDay: WeekdayKey;
  title: Record<LocaleKey, string>;
  body: Record<LocaleKey, string>;
}

export type WeekdayKey =
  | "sunday"
  | "monday"
  | "tuesday"
  | "wednesday"
  | "thursday"
  | "friday"
  | "saturday";

export const WEEKDAYS: WeekdayKey[] = [
  "sunday",
  "monday",
  "tuesday",
  "wednesday",
  "thursday",
  "friday",
  "saturday",
];

export const DEFAULT_FORM: ScheduledEventForm = {
  name: "",
  type: "campaign",
  status: "scheduled",
  localDeliveryTime: "21:00",
  targetTimezonesInput: "",
  topic: "",
  packages: ["*"],
  scheduleMode: "daily",
  date: "",
  weeklyDay: "friday",
  title: { tr: "", en: "", de: "" },
  body: { tr: "", en: "", de: "" },
};

export type DeviceFinderItem = {
  id: string;
  packageName: string;
  locale: string;
  timezone: string;
  notificationsEnabled: boolean;
  deviceModel?: string;
  appVersion?: string;
  updatedAt?: Date | null;
  hasValidToken: boolean;
};

export type CoverageItem = {
  packageName: string;
  activeDeviceCount: number;
  totalDeviceCount: number;
};

export type DeviceCoverageReport = {
  days: number;
  generatedAt: string;
  byPackage: CoverageItem[];
  missingPackages: string[];
  stalePackages: string[];
};

export type AdPerformanceAlert = {
  appId: string;
  appLabel: string;
  format: string;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
  fillRatePct: number;
  showRatePct: number;
  earningsTry: number;
  ecpmTry?: number;
  reasons: string[];
};

export type AdFormatBreakdown = {
  format: string;
  earningsTry: number;
  ecpmTry?: number;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
  fillRatePct: number;
  showRatePct: number;
};

export type AdPerformanceReport = {
  generatedAt: string;
  rangeStart: string;
  rangeEnd: string;
  status: "ok" | "misconfigured" | "error";
  thresholds: {
    minRequests: number;
    fillRatePct: number;
    showRatePct: number;
  };
  totals: {
    earningsTry: number;
    ecpmTry?: number;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
    fillRatePct: number;
    showRatePct: number;
  };
  formatBreakdown?: AdFormatBreakdown[];
  diagnosticReasonCounts?: Record<string, number>;
  alerts: AdPerformanceAlert[];
  issue?: string;
};

export type AdPerformanceToday = {
  generatedAt: string;
  date: string;
  status: "ok" | "misconfigured" | "error";
  totals: {
    earningsTry: number;
    ecpmTry?: number;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
    fillRatePct: number;
    showRatePct: number;
  };
  formatBreakdown?: AdFormatBreakdown[];
  issue?: string;
};

export type AdPerformanceTodayLatestApp = {
  packageName: string;
  appLabel: string;
  liveVersionName: string;
  adRequests: number;
  matchedRequests: number;
  impressions: number;
  earningsTry: number;
  ecpmTry?: number;
  fillRatePct: number;
  showRatePct: number;
};

export type AdPerformanceTodayLatest = {
  generatedAt: string;
  date: string;
  status: "ok" | "misconfigured" | "error";
  totals: {
    earningsTry: number;
    ecpmTry?: number;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
    fillRatePct: number;
    showRatePct: number;
  };
  liveVersionCount: number;
  filteredLegacyRows: number;
  unmappedRows: number;
  formatBreakdown?: AdFormatBreakdown[];
  apps: AdPerformanceTodayLatestApp[];
  issue?: string;
};

export type AdminAccessResponse = {
  authorized?: boolean;
  source?: "firestore" | "allowlist" | "none";
  email?: string | null;
  error?: string;
};

export type RemoteConfigParameterValueType =
  | "boolean"
  | "number"
  | "json"
  | "string";

export type RemoteConfigEntry = {
  key: string;
  value: string;
  valueType: RemoteConfigParameterValueType;
  description: string;
  groupKey?: string;
  groupLabel?: string;
};

export type RemoteConfigTemplateResponse = {
  parameters?: Record<string, {
    defaultValue?: { value?: string };
    description?: string;
    valueType?: string;
  }>;
  parameterGroups?: Record<string, {
    description?: string;
    parameters?: Record<string, {
      defaultValue?: { value?: string };
      description?: string;
      valueType?: string;
    }>;
  }>;
  conditions?: unknown[];
  version?: unknown;
  etag?: string;
};

export type FlavorHubSummary = {
  loadedAt: string;
  source: "coverage_reports" | "devices";
  coverage: Record<string, { active: number; total: number }>;
};

export type AnalyticsSummary = {
  totalDevices: number;
  activeDevices30d: number;
  notificationsEnabled30d: number;
  devicesWithToken: number;
  devicesWithoutToken: number;
  recentlySynced24h: number;
  staleRegistration7d: number;
  devicesByPackage: Array<{ packageName: string; count: number }>;
  registrationHealthByPackage: Array<{
    packageName: string;
    totalDevices: number;
    withToken: number;
    withoutToken: number;
    recentlySynced24h: number;
    stale7d: number;
  }>;
  runtimeFunnelByFormat: Array<{
    format: string;
    showIntent: number;
    showBlocked: number;
    showNotLoaded: number;
    showStarted: number;
    showImpression: number;
    showDismissed: number;
    showFailed: number;
  }>;
  runtimeSuppressReasonCounts: Record<string, number>;
  runtimeSuppressByPackage: Array<{
    packageName: string;
    totalSuppressions: number;
    topReason: string;
    topReasonCount: number;
  }>;
  runtimeHealthByPackage: Array<{
    packageName: string;
    totalDevices: number;
    withToken: number;
    withoutToken: number;
    tokenCoveragePct: number;
    recentlySynced24h: number;
    syncCoveragePct: number;
    stale7d: number;
    stalePct: number;
    totalSuppressions: number;
    topReason: string;
    topReasonCount: number;
    healthStatus: "healthy" | "warning" | "critical";
    healthNotes: string[];
  }>;
  recentCoverageReports: Array<{
    id: string;
    days: number;
    generatedAt: string;
    packageCount: number;
    totalActiveDevices: number;
    totalDevices: number;
  }>;
  consentHealth: {
    totalSuppressions: number;
    consentBlockedTotal: number;
    noConsent: number;
    consentError: number;
    consentMissing: number;
    consentRetryBackoff: number;
    errorOrMissing: number;
    consentBlockedPct: number;
    errorOrMissingPct: number;
  };
  suppressMix: Array<{
    reason: string;
    count: number;
    sharePct: number;
  }>;
  rewardedFunnel: Array<{
    format: string;
    showIntent: number;
    showBlocked: number;
    showNotLoaded: number;
    showStarted: number;
    showImpression: number;
    showDismissed: number;
    showFailed: number;
    impressionRatePct: number;
    blockedRatePct: number;
    notLoadedRatePct: number;
  }>;
  stalePackages: Array<{
    packageName: string;
    totalDevices: number;
    withToken: number;
    withoutToken: number;
    tokenCoveragePct: number;
    recentlySynced24h: number;
    syncCoveragePct: number;
    stale7d: number;
    stalePct: number;
    totalSuppressions: number;
    topReason: string;
    topReasonCount: number;
    healthStatus: "healthy" | "warning" | "critical";
    healthNotes: string[];
  }>;
  loadedAt: string;
};

export type RevenueSummary = {
  activeSubscriptions: number;
  verifiedSubscriptions: number;
  verifiedInAppPurchases: number;
  monthlyVerifiedPurchases: number;
  monthlyVerifiedRevenueTry: number;
  adRevenueTodayTry: number;
  adRevenueRangeTry: number;
  totalTrackedRevenueTry: number;
  purchasesByPackage: Array<{
    packageName: string;
    count: number;
    revenueTry: number;
  }>;
  adAlerts: AdPerformanceReport["alerts"];
  reportGeneratedAt?: string;
  reportRangeLabel?: string;
  revenueSource?: string;
  latestAdmobTodayDate?: string;
  deliveryRatiosByPackage: Array<{
    packageName: string;
    appLabel: string;
    liveVersionName: string;
    adRequests: number;
    matchedRequests: number;
    impressions: number;
    earningsTry: number;
    ecpmTry?: number;
    fillRatePct: number;
    showRatePct: number;
  }>;
  sourceFreshness: {
    status: "live" | "stale" | "diagnostics_only";
    liveTodayGeneratedAt?: string;
    liveTodayStatus?: "ok" | "misconfigured" | "error";
    liveTodayAgeHours?: number | null;
    liveTodayFreshWithinHours?: boolean;
    weeklyReportGeneratedAt?: string;
    weeklyRangeLabel?: string;
    issues: string[];
  };
  loadedAt: string;
};

export type FlavorVersionInfo = {
  versionCode?: number;
  versionName?: string;
};

export type TestPushResult = {
  messageId: string;
  mode: string;
  targetType: TestPushTargetMode;
  installationId?: string | null;
  packageName?: string | null;
  locale?: string | null;
};

export const EVENT_STATUSES = ["scheduled", "paused", "sent", "expired"] as const;
