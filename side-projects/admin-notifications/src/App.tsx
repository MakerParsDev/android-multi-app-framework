import { useCallback, useEffect, useState } from "react";
import {
  collection,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  type DocumentData,
} from "firebase/firestore";
import { firestore, functionsBaseUrl } from "./firebase";
import {
  DEFAULT_FORM,
  type AdminTab,
  type DeviceCoverageReport,
  type DeviceFinderItem,
  type AdPerformanceReport,
  type AdPerformanceToday,
  type AdPerformanceTodayLatest,
  type LoadState,
  type ScheduledEventForm,
  type ScheduledEventRecord,
  type TestPushSubTab,
  type TestPushTargetMode,
  type TestPushResult,
  type RemoteConfigEntry,
  type RemoteConfigTemplateResponse,
} from "./types";
import {
  fetchAdminFunctionJson,
  parseEvent,
  parseDeviceFinderItem,
  parseRemoteConfigEntries,
  formFromRecord,
  buildEventPayload,
  validateForm,
  parseTestPushDataInput,
  summarizeApiError,
  sortedApps,
} from "./helpers";

/* ── Components ── */
import Header from "./components/Header";
import FlavorHubPanel from "./components/FlavorHubPanel";
import EventListPanel from "./components/EventListPanel";
import EventFormPanel from "./components/EventFormPanel";
import TestPushPanel from "./components/TestPushPanel";
import RemoteConfigPanel from "./components/RemoteConfigPanel";
import AnalyticsPanel from "./components/AnalyticsPanel";
import RevenuePanel from "./components/RevenuePanel";
import SystemHealthPanel from "./components/SystemHealthPanel";
import AstrolojiPanel from "./components/AstrolojiPanel";

const allPackages = sortedApps.map((a) => a.package);

function readInitialTab(): AdminTab {
  const tab = new URLSearchParams(window.location.search).get("tab");
  if (
    tab === "flavor-hub" ||
    tab === "events" ||
    tab === "test-push" ||
    tab === "remote-config" ||
    tab === "analytics" ||
    tab === "revenue" ||
    tab === "system-health" ||
    tab === "astroloji"
  ) {
    return tab;
  }
  return "flavor-hub";
}

function readInitialSubTab(): TestPushSubTab {
  const subtab = new URLSearchParams(window.location.search).get("subtab");
  if (subtab === "single-device" || subtab === "coverage" || subtab === "ad-health") {
    return subtab;
  }
  return "single-device";
}

export default function App() {
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  /* ── Events state ── */
  const [events, setEvents] = useState<ScheduledEventRecord[]>([]);
  const [eventsState, setEventsState] = useState<LoadState>("loading");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form, setForm] = useState<ScheduledEventForm>(DEFAULT_FORM);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [eventSearchQuery, setEventSearchQuery] = useState("");
  const [eventStatusFilter, setEventStatusFilter] = useState<"all" | ScheduledEventRecord["status"]>("all");

  /* ── Device preview ── */
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewCount, setPreviewCount] = useState<number | null>(null);
  const [previewByPackage, setPreviewByPackage] = useState<Record<string, number>>({});
  const [previewError, setPreviewError] = useState("");

  /* ── Tab state ── */
  const [activeTab, setActiveTab] = useState<AdminTab>(readInitialTab);
  const [testPushSubTab, setTestPushSubTab] = useState<TestPushSubTab>(readInitialSubTab);

  /* ── Test push state ── */
  const [testPushTargetMode, setTestPushTargetMode] = useState<TestPushTargetMode>("installationId");
  const [testPushToken, setTestPushToken] = useState("");
  const [testPushInstallationId, setTestPushInstallationId] = useState("");
  const [testPushTitle, setTestPushTitle] = useState("");
  const [testPushBody, setTestPushBody] = useState("");
  const [testPushDataInput, setTestPushDataInput] = useState("");
  const [testPushIncludeNotification, setTestPushIncludeNotification] = useState(false);
  const [testPushLoading, setTestPushLoading] = useState(false);
  const [testPushError, setTestPushError] = useState("");
  const [testPushResult, setTestPushResult] = useState<TestPushResult | null>(null);

  /* ── Device finder state ── */
  const [deviceFinderResults, setDeviceFinderResults] = useState<DeviceFinderItem[]>([]);
  const [deviceFinderLoading, setDeviceFinderLoading] = useState(false);
  const [deviceFinderError, setDeviceFinderError] = useState("");
  const [deviceFinderMessage, setDeviceFinderMessage] = useState("");
  const [deviceFinderSearch, setDeviceFinderSearch] = useState("");

  /* ── Coverage state ── */
  const [coverageDays, setCoverageDays] = useState(14);
  const [coverageLoading, setCoverageLoading] = useState(false);
  const [coverageError, setCoverageError] = useState("");
  const [coverageReport, setCoverageReport] = useState<DeviceCoverageReport | null>(null);

  /* ── Ad performance state ── */
  const [adReportLoading, setAdReportLoading] = useState(false);
  const [adReportError, setAdReportError] = useState("");
  const [adPerformanceReport, setAdPerformanceReport] = useState<AdPerformanceReport | null>(null);
  const [adTodayLoading, setAdTodayLoading] = useState(false);
  const [adTodayError, setAdTodayError] = useState("");
  const [adPerformanceToday, setAdPerformanceToday] = useState<AdPerformanceToday | null>(null);
  const [adTodayLatestLoading, setAdTodayLatestLoading] = useState(false);
  const [adTodayLatestError, setAdTodayLatestError] = useState("");
  const [adPerformanceTodayLatest, setAdPerformanceTodayLatest] = useState<AdPerformanceTodayLatest | null>(null);

  /* ── Remote Config state ── */
  const [remoteConfigLoading, setRemoteConfigLoading] = useState(false);
  const [remoteConfigSavingKey, setRemoteConfigSavingKey] = useState<string | null>(null);
  const [remoteConfigError, setRemoteConfigError] = useState("");
  const [remoteConfigMessage, setRemoteConfigMessage] = useState("");
  const [remoteConfigEntries, setRemoteConfigEntries] = useState<RemoteConfigEntry[]>([]);
  const [remoteConfigEtag, setRemoteConfigEtag] = useState("");
  const [remoteConfigLoaded, setRemoteConfigLoaded] = useState(false);

  /* ── Derived ── */
  const isCreateMode = selectedId === null;
  const selectedEvent = events.find((e) => e.id === selectedId) ?? null;

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    params.set("tab", activeTab);
    if (activeTab === "test-push") {
      params.set("subtab", testPushSubTab);
    } else {
      params.delete("subtab");
    }
    const next = `${window.location.pathname}?${params.toString()}`;
    window.history.replaceState(null, "", next);
  }, [activeTab, testPushSubTab]);

  /* ════════════════════════════════════════════
   *  EVENTS
   * ════════════════════════════════════════════ */

  const loadEvents = useCallback(async () => {
    setEventsState("loading");
    try {
      const response = await fetch(`${functionsBaseUrl}/adminListScheduledEvents`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: "{}",
      });
      const body = (await response.json()) as { events?: unknown[]; error?: string };
      if (!response.ok) throw new Error(body.error ?? `HTTP ${response.status}`);
      const parsed = (body.events ?? []).map((raw) =>
        parseEvent((raw as { id: string }).id, raw as DocumentData),
      );
      setEvents(parsed);
      setEventsState("ready");
    } catch (err) {
      console.error("Events load error:", err);
      setEventsState("error");
    }
  }, []);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  /* ════════════════════════════════════════════
   *  HANDLERS
   * ════════════════════════════════════════════ */

  /* Event selection */
  const selectEvent = useCallback((event: ScheduledEventRecord) => {
    setSelectedId(event.id);
    setForm(formFromRecord(event));
    setError("");
    setMessage("");
    setPreviewCount(null);
    setPreviewByPackage({});
    setPreviewError("");
  }, []);

  const resetForm = useCallback(() => {
    setSelectedId(null);
    setForm(DEFAULT_FORM);
    setError("");
    setMessage("");
    setPreviewCount(null);
    setPreviewByPackage({});
    setPreviewError("");
  }, []);

  /* Save event */
  const saveEvent = useCallback(async () => {
    const validationError = validateForm(form);
    if (validationError) {
      setError(validationError);
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const payload = buildEventPayload(form);
      const id = isCreateMode ? crypto.randomUUID() : selectedId!;
      const response = await fetch(`${functionsBaseUrl}/adminSaveScheduledEvent`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id, isCreate: isCreateMode, ...payload }),
      });
      const body = (await response.json()) as { error?: string };
      if (!response.ok) throw new Error(body.error ?? `HTTP ${response.status}`);
      setSelectedId(id);
      setMessage(isCreateMode ? "Event created." : "Event saved.");
      await loadEvents();
    } catch (err) {
      console.error("Save event error:", err);
      setError(err instanceof Error ? err.message : "Failed to save event.");
    } finally {
      setSaving(false);
    }
  }, [form, isCreateMode, selectedId, loadEvents]);

  /* Delete event */
  const removeEvent = useCallback(async () => {
    if (!selectedId) return;
    const ok = window.confirm("Delete this event permanently?");
    if (!ok) return;
    setDeleting(true);
    setError("");
    try {
      const response = await fetch(`${functionsBaseUrl}/adminDeleteScheduledEvent`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: selectedId }),
      });
      const body = (await response.json()) as { error?: string };
      if (!response.ok) throw new Error(body.error ?? `HTTP ${response.status}`);
      resetForm();
      setMessage("Event deleted.");
      await loadEvents();
    } catch (err) {
      console.error("Delete event error:", err);
      setError(err instanceof Error ? err.message : "Failed to delete event.");
    } finally {
      setDeleting(false);
    }
  }, [selectedId, resetForm, loadEvents]);

  /* Package checkbox */
  const updatePackages = useCallback((packageName: string, checked: boolean) => {
    setForm((prev) => {
      if (packageName === "*") {
        return { ...prev, packages: checked ? ["*"] : [] };
      }
      let packages = prev.packages.filter((p) => p !== "*");
      if (checked) {
        packages = [...packages, packageName];
      } else {
        packages = packages.filter((p) => p !== packageName);
      }
      return { ...prev, packages };
    });
  }, []);

  /* Preview target devices */
  const previewTargetDevices = useCallback(async () => {
    setPreviewLoading(true);
    setPreviewError("");
    setPreviewCount(null);
    setPreviewByPackage({});
    try {
      const packages = form.packages.includes("*") ? allPackages : form.packages;
      const response = await fetch(`${functionsBaseUrl}/adminPreviewTargetDevices`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ packages }),
      });
      const body = (await response.json()) as { total?: number; byPackage?: Record<string, number>; error?: string };
      if (!response.ok) throw new Error(body.error ?? `HTTP ${response.status}`);
      setPreviewCount(body.total ?? 0);
      setPreviewByPackage(body.byPackage ?? {});
    } catch (err) {
      console.error("Preview error:", err);
      setPreviewError(err instanceof Error ? err.message : "Failed to preview.");
    } finally {
      setPreviewLoading(false);
    }
  }, [form.packages]);

  /* ── Test Push Handlers ── */

  const sendTestPushToSingleDevice = useCallback(async () => {
    const token = testPushToken.trim();
    const installationId = testPushInstallationId.trim();
    const targetValue = testPushTargetMode === "token" ? token : installationId;
    if (!targetValue) {
      setTestPushError(
        testPushTargetMode === "token"
          ? "Device token is required."
          : "Cihaz Kimliği (installationId) is required.",
      );
      setTestPushResult(null);
      return;
    }
    const parsedData = parseTestPushDataInput(testPushDataInput);
    if (parsedData.error) {
      setTestPushError(parsedData.error);
      setTestPushResult(null);
      return;
    }
    setTestPushLoading(true);
    setTestPushError("");
    setTestPushResult(null);
    try {
      const response = await fetch(`${functionsBaseUrl}/sendTestNotification`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...(testPushTargetMode === "token" ? { token } : { installationId }),
          title: testPushTitle,
          body: testPushBody,
          data: parsedData.data ?? undefined,
          useNotificationPayload: testPushIncludeNotification,
        }),
      });
      let responseBody: unknown = null;
      try {
        responseBody = await response.json();
      } catch {
        responseBody = null;
      }
      if (!response.ok) {
        throw new Error(
          summarizeApiError(responseBody, `HTTP ${response.status}: Failed to send test push`),
        );
      }
      const payload = responseBody as {
        messageId?: unknown;
        mode?: unknown;
        targetType?: unknown;
        installationId?: unknown;
        packageName?: unknown;
        locale?: unknown;
      };
      const messageId = typeof payload.messageId === "string" ? payload.messageId : "(unknown)";
      const mode = typeof payload.mode === "string" ? payload.mode : "data-only";
      const targetType =
        payload.targetType === "token" || payload.targetType === "installationId"
          ? payload.targetType
          : testPushTargetMode;
      setTestPushResult({
        messageId,
        mode,
        targetType,
        installationId:
          typeof payload.installationId === "string" || payload.installationId === null
            ? (payload.installationId as string | null)
            : null,
        packageName:
          typeof payload.packageName === "string" || payload.packageName === null
            ? (payload.packageName as string | null)
            : null,
        locale:
          typeof payload.locale === "string" || payload.locale === null
            ? (payload.locale as string | null)
            : null,
      });
    } catch (e) {
      console.error(e);
      const msg = e instanceof Error ? e.message : "Failed to send test push.";
      setTestPushError(msg);
    } finally {
      setTestPushLoading(false);
    }
  }, [testPushToken, testPushInstallationId, testPushTargetMode, testPushTitle, testPushBody, testPushDataInput, testPushIncludeNotification]);

  /* Device finder */
  const lookupDeviceByInstallationId = useCallback(async () => {
    const id = testPushInstallationId.trim();
    if (!id) {
      setDeviceFinderError("Enter an installationId first.");
      return;
    }
    setDeviceFinderLoading(true);
    setDeviceFinderError("");
    setDeviceFinderMessage("");
    try {
      const snap = await getDoc(doc(firestore, "devices", id));
      if (snap.exists()) {
        setDeviceFinderResults([parseDeviceFinderItem(snap.id, snap.data())]);
        setDeviceFinderMessage("Found 1 device.");
      } else {
        setDeviceFinderResults([]);
        setDeviceFinderMessage("No device found with this installationId.");
      }
    } catch (err) {
      console.error(err);
      setDeviceFinderError(err instanceof Error ? err.message : "Lookup failed.");
    } finally {
      setDeviceFinderLoading(false);
    }
  }, [testPushInstallationId]);

  const loadRecentDevices = useCallback(async () => {
    setDeviceFinderLoading(true);
    setDeviceFinderError("");
    setDeviceFinderMessage("");
    try {
      const q = query(collection(firestore, "devices"), orderBy("updatedAt", "desc"), limit(50));
      const snap = await getDocs(q);
      const items = snap.docs.map((d) => parseDeviceFinderItem(d.id, d.data()));
      setDeviceFinderResults(items);
      setDeviceFinderMessage(`Loaded ${items.length} recent device(s).`);
    } catch (err) {
      console.error(err);
      setDeviceFinderError(err instanceof Error ? err.message : "Failed to load devices.");
    } finally {
      setDeviceFinderLoading(false);
    }
  }, []);

  const useDeviceForTestPush = useCallback((device: DeviceFinderItem) => {
    setTestPushInstallationId(device.id);
    setTestPushTargetMode("installationId");
  }, []);

  /* Coverage */
  const loadDeviceCoverageReport = useCallback(async () => {
    const normalizedDays = Math.max(1, Math.min(90, Math.floor(coverageDays || 14)));
    setCoverageLoading(true);
    setCoverageError("");
    try {
      const response = await fetch(`${functionsBaseUrl}/deviceCoverageReport`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          days: normalizedDays,
          packages: allPackages,
        }),
      });
      let responseBody: unknown = null;
      try {
        responseBody = await response.json();
      } catch {
        responseBody = null;
      }
      if (!response.ok) {
        throw new Error(
          summarizeApiError(responseBody, `HTTP ${response.status}: Failed to fetch coverage report`),
        );
      }
      setCoverageReport(responseBody as DeviceCoverageReport);
    } catch (e) {
      console.error(e);
      setCoverageError(e instanceof Error ? e.message : "Failed to fetch coverage report.");
    } finally {
      setCoverageLoading(false);
    }
  }, [coverageDays]);

  /* Ad performance */
  const loadAdPerformanceReport = useCallback(async (forceRefresh: boolean) => {
    setAdReportLoading(true);
    setAdReportError("");
    try {
      const response = await fetch(`${functionsBaseUrl}/adPerformance`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: forceRefresh ? "refresh" : "report" }),
      });
      let responseBody: unknown = null;
      try {
        responseBody = await response.json();
      } catch {
        responseBody = null;
      }
      if (!response.ok) {
        throw new Error(
          summarizeApiError(responseBody, `HTTP ${response.status}: Failed to fetch ad performance report`),
        );
      }
      setAdPerformanceReport(responseBody as AdPerformanceReport);
    } catch (e) {
      console.error(e);
      setAdReportError(e instanceof Error ? e.message : "Failed to fetch ad performance report.");
    } finally {
      setAdReportLoading(false);
    }
  }, []);

  const loadAdPerformanceToday = useCallback(async () => {
    setAdTodayLoading(true);
    setAdTodayError("");
    try {
      const response = await fetch(`${functionsBaseUrl}/adPerformance`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: "today" }),
      });
      let responseBody: unknown = null;
      try {
        responseBody = await response.json();
      } catch {
        responseBody = null;
      }
      if (!response.ok) {
        throw new Error(
          summarizeApiError(responseBody, `HTTP ${response.status}: Failed to fetch today's ad performance`),
        );
      }
      setAdPerformanceToday(responseBody as AdPerformanceToday);
    } catch (e) {
      console.error(e);
      setAdTodayError(e instanceof Error ? e.message : "Failed to fetch today's ad report.");
    } finally {
      setAdTodayLoading(false);
    }
  }, []);

  const loadAdPerformanceTodayLatest = useCallback(async () => {
    setAdTodayLatestLoading(true);
    setAdTodayLatestError("");
    try {
      const response = await fetch(`${functionsBaseUrl}/adPerformance`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: "today_latest",
          catalog: sortedApps.map((app) => ({
            packageName: app.package,
            appName: app.name ?? app.flavor,
          })),
        }),
      });
      let responseBody: unknown = null;
      try {
        responseBody = await response.json();
      } catch {
        responseBody = null;
      }
      if (!response.ok) {
        throw new Error(
          summarizeApiError(responseBody, `HTTP ${response.status}: Failed to fetch latest-version ad performance`),
        );
      }
      setAdPerformanceTodayLatest(responseBody as AdPerformanceTodayLatest);
    } catch (e) {
      console.error(e);
      setAdTodayLatestError(e instanceof Error ? e.message : "Failed to fetch latest-version ad report.");
    } finally {
      setAdTodayLatestLoading(false);
    }
  }, []);

  const refreshAdHealth = useCallback(async (forceWeeklyRefresh: boolean) => {
    await Promise.all([
      loadAdPerformanceToday(),
      loadAdPerformanceTodayLatest(),
      loadAdPerformanceReport(forceWeeklyRefresh),
    ]);
  }, [loadAdPerformanceReport, loadAdPerformanceToday, loadAdPerformanceTodayLatest]);

  const loadRemoteConfig = useCallback(async () => {
    setRemoteConfigLoading(true);
    setRemoteConfigError("");
    try {
      const template = await fetchAdminFunctionJson<RemoteConfigTemplateResponse>({
        endpoint: `${functionsBaseUrl}/adminGetRemoteConfig`,
        method: "GET",
      });
      setRemoteConfigEntries(parseRemoteConfigEntries(template));
      setRemoteConfigEtag(typeof template.etag === "string" ? template.etag : "");
      setRemoteConfigLoaded(true);
    } catch (e) {
      console.error(e);
      setRemoteConfigError(e instanceof Error ? e.message : "Failed to fetch Remote Config.");
      setRemoteConfigLoaded(true);
    } finally {
      setRemoteConfigLoading(false);
    }
  }, []);

  const saveRemoteConfigEntry = useCallback(async (entry: RemoteConfigEntry) => {
    setRemoteConfigSavingKey(entry.key);
    setRemoteConfigError("");
    setRemoteConfigMessage("");
    try {
      const response = await fetch(`${functionsBaseUrl}/adminUpdateRemoteConfig`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          key: entry.key,
          value: entry.value,
          description: entry.description,
          groupName: entry.groupKey,
        }),
      });
      let responseBody: unknown = null;
      try {
        responseBody = await response.json();
      } catch {
        responseBody = null;
      }
      if (!response.ok) {
        throw new Error(
          summarizeApiError(responseBody, `HTTP ${response.status}: Failed to update Remote Config`),
        );
      }

      setRemoteConfigMessage(`Saved ${entry.key}.`);
      await loadRemoteConfig();
    } catch (e) {
      console.error(e);
      setRemoteConfigError(e instanceof Error ? e.message : "Failed to update Remote Config.");
    } finally {
      setRemoteConfigSavingKey(null);
    }
  }, [loadRemoteConfig]);

  useEffect(() => {
    if (activeTab !== "remote-config") return;
    if (remoteConfigLoading || remoteConfigLoaded) return;
    void loadRemoteConfig();
  }, [activeTab, loadRemoteConfig, remoteConfigLoaded, remoteConfigLoading]);

  /* ════════════════════════════════════════════
   *  RENDER
   * ════════════════════════════════════════════ */

  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      <Header activeTab={activeTab} onTabChange={setActiveTab} />

      {(error || message) && (
        <div className={`banner ${error ? "banner-error" : "banner-success"}`} role="status">
          {error || message}
        </div>
      )}

      <div id="main-content">
        {activeTab === "flavor-hub" && <FlavorHubPanel />}

        {activeTab === "events" && (
          <div className="content-grid">
            <EventListPanel
              events={events}
              eventsState={eventsState}
              selectedId={selectedId}
              eventSearchQuery={eventSearchQuery}
              eventStatusFilter={eventStatusFilter}
              onSearchChange={setEventSearchQuery}
              onStatusFilterChange={setEventStatusFilter}
              onSelectEvent={selectEvent}
              onNewEvent={resetForm}
              onRefresh={loadEvents}
            />
            <EventFormPanel
              form={form}
              setForm={setForm}
              selectedEvent={selectedEvent}
              isCreateMode={isCreateMode}
              saving={saving}
              deleting={deleting}
              previewLoading={previewLoading}
              previewCount={previewCount}
              previewByPackage={previewByPackage}
              previewError={previewError}
              onSave={saveEvent}
              onDelete={removeEvent}
              onReset={resetForm}
              onPreviewTargetDevices={previewTargetDevices}
              onUpdatePackages={updatePackages}
            />
          </div>
        )}

        {activeTab === "test-push" && (
          <TestPushPanel
            testPushSubTab={testPushSubTab}
            onSubTabChange={setTestPushSubTab}
            testPushTargetMode={testPushTargetMode}
            onTargetModeChange={setTestPushTargetMode}
            testPushInstallationId={testPushInstallationId}
            onInstallationIdChange={setTestPushInstallationId}
            testPushToken={testPushToken}
            onTokenChange={setTestPushToken}
            testPushTitle={testPushTitle}
            onTitleChange={setTestPushTitle}
            testPushBody={testPushBody}
            onBodyChange={setTestPushBody}
            testPushDataInput={testPushDataInput}
            onDataInputChange={setTestPushDataInput}
            testPushIncludeNotification={testPushIncludeNotification}
            onIncludeNotificationChange={setTestPushIncludeNotification}
            testPushLoading={testPushLoading}
            testPushError={testPushError}
            testPushResult={testPushResult}
            onSendTestPush={sendTestPushToSingleDevice}
            deviceFinderLoading={deviceFinderLoading}
            deviceFinderError={deviceFinderError}
            deviceFinderMessage={deviceFinderMessage}
            deviceFinderSearch={deviceFinderSearch}
            onDeviceFinderSearchChange={setDeviceFinderSearch}
            deviceFinderResults={deviceFinderResults}
            onLookupDevice={lookupDeviceByInstallationId}
            onLoadRecentDevices={loadRecentDevices}
            onUseDevice={useDeviceForTestPush}
            coverageDays={coverageDays}
            onCoverageDaysChange={setCoverageDays}
            coverageLoading={coverageLoading}
            coverageError={coverageError}
            coverageReport={coverageReport}
            onLoadCoverage={loadDeviceCoverageReport}
            adReportLoading={adReportLoading}
            adReportError={adReportError}
            adPerformanceReport={adPerformanceReport}
            adTodayLoading={adTodayLoading}
            adTodayError={adTodayError}
            adPerformanceToday={adPerformanceToday}
            adTodayLatestLoading={adTodayLatestLoading}
            adTodayLatestError={adTodayLatestError}
            adPerformanceTodayLatest={adPerformanceTodayLatest}
            onRefreshAdHealth={refreshAdHealth}
          />
        )}

        {activeTab === "remote-config" && (
          <RemoteConfigPanel
            entries={remoteConfigEntries}
            etag={remoteConfigEtag}
            loading={remoteConfigLoading}
            savingKey={remoteConfigSavingKey}
            error={remoteConfigError}
            message={remoteConfigMessage}
            onRefresh={loadRemoteConfig}
            onSave={saveRemoteConfigEntry}
          />
        )}

        {activeTab === "analytics" && <AnalyticsPanel />}

        {activeTab === "revenue" && <RevenuePanel />}

        {activeTab === "system-health" && <SystemHealthPanel />}

        {activeTab === "astroloji" && <AstrolojiPanel />}
      </div>
    </div>
  );
}
