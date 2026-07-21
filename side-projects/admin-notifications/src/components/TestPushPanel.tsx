import { useEffect, useMemo, useRef } from "react";
import type {
  AdPerformanceReport,
  AdPerformanceToday,
  AdPerformanceTodayLatest,
  DeviceCoverageReport,
  DeviceFinderItem,
  TestPushResult,
  TestPushSubTab,
  TestPushTargetMode,
} from "../types";
import { formatDateTime, formatPercent, formatTry, localTimeZone } from "../helpers";

type TestPushPanelProps = {
  testPushSubTab: TestPushSubTab;
  onSubTabChange: (tab: TestPushSubTab) => void;
  /* single device */
  testPushTargetMode: TestPushTargetMode;
  onTargetModeChange: (mode: TestPushTargetMode) => void;
  testPushInstallationId: string;
  onInstallationIdChange: (val: string) => void;
  testPushToken: string;
  onTokenChange: (val: string) => void;
  testPushTitle: string;
  onTitleChange: (val: string) => void;
  testPushBody: string;
  onBodyChange: (val: string) => void;
  testPushDataInput: string;
  onDataInputChange: (val: string) => void;
  testPushIncludeNotification: boolean;
  onIncludeNotificationChange: (val: boolean) => void;
  testPushLoading: boolean;
  testPushError: string;
  testPushResult: TestPushResult | null;
  onSendTestPush: () => void;
  /* device finder */
  deviceFinderLoading: boolean;
  deviceFinderError: string;
  deviceFinderMessage: string;
  deviceFinderSearch: string;
  onDeviceFinderSearchChange: (val: string) => void;
  deviceFinderResults: DeviceFinderItem[];
  onLookupDevice: () => void;
  onLoadRecentDevices: () => void;
  onUseDevice: (device: DeviceFinderItem) => void;
  /* coverage */
  coverageDays: number;
  onCoverageDaysChange: (val: number) => void;
  coverageLoading: boolean;
  coverageError: string;
  coverageReport: DeviceCoverageReport | null;
  onLoadCoverage: () => void;
  /* ad health */
  adReportLoading: boolean;
  adReportError: string;
  adPerformanceReport: AdPerformanceReport | null;
  adTodayLoading: boolean;
  adTodayError: string;
  adPerformanceToday: AdPerformanceToday | null;
  adTodayLatestLoading: boolean;
  adTodayLatestError: string;
  adPerformanceTodayLatest: AdPerformanceTodayLatest | null;
  onRefreshAdHealth: (forceWeekly: boolean) => void;
};

export default function TestPushPanel(props: TestPushPanelProps) {
  const {
    testPushSubTab,
    onSubTabChange,
    testPushTargetMode,
    onTargetModeChange,
    testPushInstallationId,
    onInstallationIdChange,
    testPushToken,
    onTokenChange,
    testPushTitle,
    onTitleChange,
    testPushBody,
    onBodyChange,
    testPushDataInput,
    onDataInputChange,
    testPushIncludeNotification,
    onIncludeNotificationChange,
    testPushLoading,
    testPushError,
    testPushResult,
    onSendTestPush,
    deviceFinderLoading,
    deviceFinderError,
    deviceFinderMessage,
    deviceFinderSearch,
    onDeviceFinderSearchChange,
    deviceFinderResults,
    onLookupDevice,
    onLoadRecentDevices,
    onUseDevice,
    coverageDays,
    onCoverageDaysChange,
    coverageLoading,
    coverageError,
    coverageReport,
    onLoadCoverage,
    adReportLoading,
    adReportError,
    adPerformanceReport,
    adTodayLoading,
    adTodayError,
    adPerformanceToday,
    adTodayLatestLoading,
    adTodayLatestError,
    adPerformanceTodayLatest,
    onRefreshAdHealth,
  } = props;

  const filteredDeviceFinderResults = useMemo(() => {
    const needle = deviceFinderSearch.trim().toLowerCase();
    if (!needle) return deviceFinderResults;
    return deviceFinderResults.filter(
      (item) =>
        item.id.toLowerCase().includes(needle) ||
        item.packageName.toLowerCase().includes(needle) ||
        (item.deviceModel ?? "").toLowerCase().includes(needle) ||
        (item.locale ?? "").toLowerCase().includes(needle),
    );
  }, [deviceFinderResults, deviceFinderSearch]);

  const hasTriggeredIndexRecovery = useRef(false);
  const hasTriggeredOAuthRecovery = useRef(false);
  const hasAutoLoadedAdHealth = useRef(false);

  const weeklyIssue = adPerformanceReport?.issue?.toLowerCase() ?? "";
  const todayIssue = adPerformanceToday?.issue?.toLowerCase() ?? "";
  const hasDeletedOAuthIssue = weeklyIssue.includes("oauth client was deleted")
    || todayIssue.includes("oauth client was deleted");

  const renderFormatBreakdown = (
    title: string,
    items: Array<{
      format: string;
      adRequests: number;
      matchedRequests: number;
      impressions: number;
      fillRatePct: number;
      showRatePct: number;
      earningsTry: number;
      ecpmTry?: number;
    }> | undefined,
  ) => {
    if (!items || items.length === 0) return null;
    return (
      <div className="ad-report-subsection">
        <h4>{title}</h4>
        <div className="ad-alert-list">
          {items.map((item) => (
            <div key={item.format} className="ad-alert-item">
              <div className="ad-alert-header">
                <strong>{item.format}</strong>
              </div>
              <div className="ad-alert-metrics">
                requests={item.adRequests} · matched={item.matchedRequests} · impressions={item.impressions}
              </div>
              <div className="ad-alert-metrics">
                fill={formatPercent(item.fillRatePct)} · show={formatPercent(item.showRatePct)} · earnings=
                {formatTry(item.earningsTry)}
              </div>
              <div className="ad-alert-metrics">
                eCPM={formatTry(item.ecpmTry)}
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  };

  const renderDiagnosticReasons = (
    counts: Record<string, number> | undefined,
  ) => {
    const entries = Object.entries(counts ?? {});
    if (entries.length === 0) return null;
    return (
      <div className="ad-report-subsection">
        <h4>Diagnostic reasons</h4>
        <div className="ad-alert-reasons">
          {entries.map(([reason, count]) => (
            <span key={reason} className="status-pill status-paused">
              {reason} ({count})
            </span>
          ))}
        </div>
      </div>
    );
  };

  useEffect(() => {
    if (testPushSubTab !== "ad-health") return;
    if (hasAutoLoadedAdHealth.current) return;
    if (adReportLoading || adTodayLoading || adTodayLatestLoading) return;
    if (adPerformanceReport || adPerformanceToday || adPerformanceTodayLatest) return;

    hasAutoLoadedAdHealth.current = true;
    void onRefreshAdHealth(false);
  }, [
    adPerformanceReport,
    adPerformanceToday,
    adPerformanceTodayLatest,
    adReportLoading,
    adTodayLoading,
    adTodayLatestLoading,
    onRefreshAdHealth,
    testPushSubTab,
  ]);

  useEffect(() => {
    if (testPushSubTab !== "ad-health") return;
    if (!adPerformanceReport || adReportLoading || adTodayLoading || adTodayLatestLoading) return;
    if (hasTriggeredIndexRecovery.current) return;
    if (!weeklyIssue.includes("requires an index")) return;

    hasTriggeredIndexRecovery.current = true;
    void onRefreshAdHealth(true);
  }, [
    adPerformanceReport,
    adReportLoading,
    adTodayLoading,
    adTodayLatestLoading,
    onRefreshAdHealth,
    testPushSubTab,
    weeklyIssue,
  ]);

  useEffect(() => {
    if (testPushSubTab !== "ad-health") return;
    if (adReportLoading || adTodayLoading || adTodayLatestLoading) return;
    if (hasTriggeredOAuthRecovery.current) return;
    if (!hasDeletedOAuthIssue) return;

    hasTriggeredOAuthRecovery.current = true;
    void onRefreshAdHealth(true);
  }, [
    adReportLoading,
    adTodayLoading,
    adTodayLatestLoading,
    hasDeletedOAuthIssue,
    onRefreshAdHealth,
    testPushSubTab,
  ]);

  return (
    <div className="single-panel-grid" id="tabpanel-test-push" role="tabpanel" aria-labelledby="tab-test-push">
      <main className="panel form-panel" role="main">
        <div className="panel-header">
          <h2>Operations</h2>
        </div>

        <div className="sub-tab-nav" role="tablist" aria-label="Operations sections">
          <button
            type="button"
            role="tab"
            className={`sub-tab-btn ${testPushSubTab === "single-device" ? "active" : ""}`}
            aria-selected={testPushSubTab === "single-device"}
            onClick={() => onSubTabChange("single-device")}
          >
            <span className="tab-icon" aria-hidden="true">📱</span> Single Device
          </button>
          <button
            type="button"
            role="tab"
            className={`sub-tab-btn ${testPushSubTab === "coverage" ? "active" : ""}`}
            aria-selected={testPushSubTab === "coverage"}
            onClick={() => onSubTabChange("coverage")}
          >
            <span className="tab-icon" aria-hidden="true">📊</span> Coverage
          </button>
          <button
            type="button"
            role="tab"
            className={`sub-tab-btn ${testPushSubTab === "ad-health" ? "active" : ""}`}
            aria-selected={testPushSubTab === "ad-health"}
            onClick={() => onSubTabChange("ad-health")}
          >
            <span className="tab-icon" aria-hidden="true">💰</span> Ad Health
          </button>
        </div>

        {/* ── Single Device ── */}
        {testPushSubTab === "single-device" && (
          <section className="subsection" aria-label="Single device test push">
            <div className="test-push-box">
              <div className="device-preview-header">
                <strong>Single-device test push</strong>
                <button
                  type="button"
                  className="btn-primary"
                  onClick={onSendTestPush}
                  disabled={testPushLoading}
                >
                  {testPushLoading ? "Sending…" : "Send test push"}
                </button>
              </div>

              <p className="muted device-preview-note">
                Sends a test push to exactly one device via admin-only Cloud Function (FCM token or
                Cihaz Kimliği / installationId).
              </p>

              <div className="test-push-grid">
                <label>
                  <span className="label-text">Target type</span>
                  <select
                    value={testPushTargetMode}
                    onChange={(e) => onTargetModeChange(e.target.value as TestPushTargetMode)}
                  >
                    <option value="installationId">Cihaz Kimliği (installationId)</option>
                    <option value="token">FCM device token</option>
                  </select>
                </label>

                <label className="test-push-token">
                  <span className="label-text">
                    {testPushTargetMode === "token"
                      ? "FCM device token"
                      : "Cihaz Kimliği (installationId)"}
                  </span>
                  <textarea
                    rows={3}
                    value={testPushTargetMode === "token" ? testPushToken : testPushInstallationId}
                    onChange={(e) =>
                      testPushTargetMode === "token"
                        ? onTokenChange(e.target.value)
                        : onInstallationIdChange(e.target.value)
                    }
                    placeholder={
                      testPushTargetMode === "token"
                        ? "FCM registration token"
                        : "Uygulama içindeki 'Cihaz Kimliği' değeri"
                    }
                  />
                </label>

                {/* Device Finder */}
                <div className="device-finder-box">
                  <div className="device-finder-header">
                    <strong>Device finder (Firestore <code>devices</code>)</strong>
                    <div className="device-finder-actions">
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={onLookupDevice}
                        disabled={deviceFinderLoading}
                      >
                        {deviceFinderLoading ? "Checking…" : "Find by Cihaz Kimliği"}
                      </button>
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={onLoadRecentDevices}
                        disabled={deviceFinderLoading}
                      >
                        {deviceFinderLoading ? "Loading…" : "Load recent devices"}
                      </button>
                    </div>
                  </div>

                  <label>
                    <span className="label-text">Filter results</span>
                    <input
                      value={deviceFinderSearch}
                      onChange={(e) => onDeviceFinderSearchChange(e.target.value)}
                      placeholder="installationId / package / model / locale"
                      aria-label="Filter device results"
                    />
                  </label>

                  {deviceFinderError && <p className="inline-error" role="alert">{deviceFinderError}</p>}
                  {deviceFinderMessage && !deviceFinderError && (
                    <p className="muted">{deviceFinderMessage}</p>
                  )}

                  {filteredDeviceFinderResults.length > 0 && (
                    <div className="device-finder-list" role="list" aria-label="Found devices">
                      {filteredDeviceFinderResults.map((device) => (
                        <div key={device.id} className="device-finder-item" role="listitem">
                          <div className="device-finder-item-main">
                            <code>{device.id}</code>
                            <span>
                              {device.packageName || "(no package)"} · {device.locale || "-"} ·{" "}
                              {device.timezone || "-"}
                            </span>
                            <small>
                              {device.deviceModel || "Unknown device"} · v
                              {device.appVersion || "?"} · updated {formatDateTime(device.updatedAt)}
                            </small>
                            {!device.hasValidToken && (
                              <small className="inline-error">
                                Warning: record has no valid FCM token
                              </small>
                            )}
                          </div>
                          <button
                            type="button"
                            className="btn-secondary"
                            onClick={() => onUseDevice(device)}
                          >
                            Use this device
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <label>
                  <span className="label-text">Title</span>
                  <input
                    value={testPushTitle}
                    onChange={(e) => onTitleChange(e.target.value)}
                    placeholder="Test Bildirim"
                  />
                </label>

                <label>
                  <span className="label-text">Body</span>
                  <textarea
                    rows={3}
                    value={testPushBody}
                    onChange={(e) => onBodyChange(e.target.value)}
                    placeholder="Admin panel test bildirimi"
                  />
                </label>

                <label>
                  <span className="label-text">Data payload (key=value per line)</span>
                  <textarea
                    rows={4}
                    value={testPushDataInput}
                    onChange={(e) => onDataInputChange(e.target.value)}
                    placeholder={"type=test\nsource=admin-panel"}
                  />
                </label>

                <label className="checkbox-row test-push-checkbox">
                  <input
                    type="checkbox"
                    checked={testPushIncludeNotification}
                    onChange={(e) => onIncludeNotificationChange(e.target.checked)}
                  />
                  <span>Include notification payload (otherwise data-only)</span>
                  <small>
                    Data-only is recommended for in-app persistence checks. Notification+data tests system UI behavior.
                  </small>
                </label>
              </div>

              {testPushError && <p className="inline-error" role="alert">{testPushError}</p>}
              {testPushResult && !testPushError && (
                <div className="test-push-result" role="status">
                  <span className="result-success">✓ Sent successfully</span>
                  <strong>{testPushResult.mode}</strong>
                  <div>
                    target={testPushResult.targetType}
                    {testPushResult.installationId ? ` · installationId=${testPushResult.installationId}` : ""}
                  </div>
                  {(testPushResult.packageName || testPushResult.locale) && (
                    <div>
                      {testPushResult.packageName ? `pkg=${testPushResult.packageName}` : "pkg=-"}
                      {" · "}
                      {testPushResult.locale ? `locale=${testPushResult.locale}` : "locale=-"}
                    </div>
                  )}
                  <code>{testPushResult.messageId}</code>
                </div>
              )}
            </div>
          </section>
        )}

        {/* ── Coverage ── */}
        {testPushSubTab === "coverage" && (
          <section className="subsection" aria-label="Device coverage report">
            <div className="coverage-box">
              <div className="device-preview-header">
                <strong>Device coverage (last N days)</strong>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={onLoadCoverage}
                  disabled={coverageLoading}
                >
                  {coverageLoading ? "Loading…" : "Refresh coverage"}
                </button>
              </div>

              <label className="coverage-days">
                <span className="label-text">Days</span>
                <input
                  type="number"
                  min={1}
                  max={90}
                  value={coverageDays}
                  onChange={(e) => onCoverageDaysChange(Number(e.target.value || 14))}
                />
              </label>

              {coverageError && <p className="inline-error" role="alert">{coverageError}</p>}

              {coverageReport && !coverageError && (
                <div className="coverage-result">
                  <p className="muted">
                    generatedAt={coverageReport.generatedAt} · days={coverageReport.days}
                  </p>
                  <ul className="coverage-list">
                    {coverageReport.byPackage.map((item) => (
                      <li key={item.packageName}>
                        <code>{item.packageName}</code>
                        <span>
                          active={item.activeDeviceCount} / total={item.totalDeviceCount}
                        </span>
                      </li>
                    ))}
                  </ul>
                  {coverageReport.missingPackages.length > 0 && (
                    <div className="coverage-alert" role="alert">
                      <strong>Missing packages</strong>
                      <div>{coverageReport.missingPackages.join(", ")}</div>
                    </div>
                  )}
                  {coverageReport.stalePackages.length > 0 && (
                    <div className="coverage-alert" role="alert">
                      <strong>Stale packages (no recent device)</strong>
                      <div>{coverageReport.stalePackages.join(", ")}</div>
                    </div>
                  )}
                </div>
              )}
            </div>
          </section>
        )}

        {/* ── Ad Health ── */}
        {testPushSubTab === "ad-health" && (
          <section className="subsection" aria-label="Ad health reports">
            <div className="coverage-box">
              <div className="device-preview-header">
                <strong>Ad health (AdMob)</strong>
                <div className="device-finder-actions">
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => onRefreshAdHealth(false)}
                    disabled={adReportLoading || adTodayLoading || adTodayLatestLoading}
                  >
                    {adReportLoading || adTodayLoading || adTodayLatestLoading ? "Loading…" : "Load latest"}
                  </button>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => onRefreshAdHealth(true)}
                    disabled={adReportLoading || adTodayLoading || adTodayLatestLoading}
                  >
                    {adReportLoading || adTodayLoading || adTodayLatestLoading ? "Refreshing…" : "Force refresh"}
                  </button>
                </div>
              </div>
              <p className="muted">
                Primary: <strong>Today so far</strong> (same day range as AdMob UI). Secondary:
                latest weekly diagnostics.
              </p>

              {/* Today report */}
              {adTodayError && <p className="inline-error" role="alert">{adTodayError}</p>}
              {adPerformanceToday && !adTodayError ? (
                <div className="ad-report-result ad-report-block">
                  <h3>Today so far (primary)</h3>
                  <p className="muted">
                    Today so far ({adPerformanceToday.date}) · generatedAt=
                    {adPerformanceToday.generatedAt} · status=
                    <strong>{adPerformanceToday.status}</strong> · timezone=
                    {localTimeZone}
                  </p>
                  {adPerformanceToday.issue && (
                    <div className="coverage-alert" role="alert">
                      <strong>Issue</strong>
                      <div>{adPerformanceToday.issue}</div>
                    </div>
                  )}
                  <ul className="coverage-list">
                    <li><span>Total earnings</span><strong>{formatTry(adPerformanceToday.totals.earningsTry)}</strong></li>
                    <li><span>Total eCPM</span><strong>{formatTry(adPerformanceToday.totals.ecpmTry)}</strong></li>
                    <li><span>Total ad requests</span><strong>{adPerformanceToday.totals.adRequests}</strong></li>
                    <li><span>Total fill rate</span><strong>{formatPercent(adPerformanceToday.totals.fillRatePct)}</strong></li>
                    <li><span>Total show rate</span><strong>{formatPercent(adPerformanceToday.totals.showRatePct)}</strong></li>
                  </ul>
                  {renderFormatBreakdown("Format funnel", adPerformanceToday.formatBreakdown)}
                </div>
              ) : (
                !adTodayError && <p className="muted">Today report not loaded yet.</p>
              )}

              {/* Latest-version today report */}
              {adTodayLatestError && <p className="inline-error" role="alert">{adTodayLatestError}</p>}
              {adPerformanceTodayLatest && !adTodayLatestError ? (
                <div className="ad-report-result ad-report-block">
                  <h3>Today so far (live latest versions)</h3>
                  <p className="muted">
                    Today so far ({adPerformanceTodayLatest.date}) · generatedAt=
                    {adPerformanceTodayLatest.generatedAt} · status=
                    <strong>{adPerformanceTodayLatest.status}</strong> · liveVersions=
                    <strong>{adPerformanceTodayLatest.liveVersionCount}</strong>
                  </p>
                  {adPerformanceTodayLatest.issue && (
                    <div className="coverage-alert" role="alert">
                      <strong>Issue</strong>
                      <div>{adPerformanceTodayLatest.issue}</div>
                    </div>
                  )}
                  <ul className="coverage-list">
                    <li><span>Total earnings</span><strong>{formatTry(adPerformanceTodayLatest.totals.earningsTry)}</strong></li>
                    <li><span>Total eCPM</span><strong>{formatTry(adPerformanceTodayLatest.totals.ecpmTry)}</strong></li>
                    <li><span>Total ad requests</span><strong>{adPerformanceTodayLatest.totals.adRequests}</strong></li>
                    <li><span>Total fill rate</span><strong>{formatPercent(adPerformanceTodayLatest.totals.fillRatePct)}</strong></li>
                    <li><span>Total show rate</span><strong>{formatPercent(adPerformanceTodayLatest.totals.showRatePct)}</strong></li>
                    <li><span>Filtered legacy rows</span><strong>{adPerformanceTodayLatest.filteredLegacyRows}</strong></li>
                    <li><span>Unmapped rows</span><strong>{adPerformanceTodayLatest.unmappedRows}</strong></li>
                  </ul>
                  {renderFormatBreakdown("Latest-version format funnel", adPerformanceTodayLatest.formatBreakdown)}

                  {adPerformanceTodayLatest.apps.length === 0 ? (
                    <p className="muted">No live latest-version rows were matched yet.</p>
                  ) : (
                    <div className="ad-alert-list">
                      {adPerformanceTodayLatest.apps.slice(0, 8).map((app) => (
                        <div
                          key={`${app.packageName}-${app.liveVersionName}`}
                          className="ad-alert-item"
                        >
                          <div className="ad-alert-header">
                            <strong>{app.appLabel}</strong>
                            <code>{app.liveVersionName}</code>
                          </div>
                          <div className="ad-alert-metrics">
                            requests={app.adRequests} · matched={app.matchedRequests} · impressions=
                            {app.impressions}
                          </div>
                          <div className="ad-alert-metrics">
                            fill={formatPercent(app.fillRatePct)} · show=
                            {formatPercent(app.showRatePct)} · earnings=
                            {formatTry(app.earningsTry)}
                          </div>
                          <div className="ad-alert-metrics">
                            eCPM={formatTry(app.ecpmTry)}
                          </div>
                          <small className="muted">
                            package=<code>{app.packageName}</code>
                          </small>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ) : (
                !adTodayLatestError && <p className="muted">Latest-version report not loaded yet.</p>
              )}

              {/* Weekly report */}
              {adReportError && <p className="inline-error" role="alert">{adReportError}</p>}
              {adPerformanceReport && !adReportError ? (
                <div className="ad-report-result ad-report-block">
                  <h3>Weekly diagnostics (secondary)</h3>
                  <p className="muted">
                    generatedAt={adPerformanceReport.generatedAt} · range=
                    {adPerformanceReport.rangeStart}..{adPerformanceReport.rangeEnd} · status=
                    <strong>{adPerformanceReport.status}</strong>
                  </p>
                  {adPerformanceReport.issue && (
                    <div className="coverage-alert" role="alert">
                      <strong>Issue</strong>
                      <div>{adPerformanceReport.issue}</div>
                    </div>
                  )}
                  <ul className="coverage-list">
                    <li><span>Total earnings</span><strong>{formatTry(adPerformanceReport.totals.earningsTry)}</strong></li>
                    <li><span>Total eCPM</span><strong>{formatTry(adPerformanceReport.totals.ecpmTry)}</strong></li>
                    <li><span>Total ad requests</span><strong>{adPerformanceReport.totals.adRequests}</strong></li>
                    <li><span>Total fill rate</span><strong>{formatPercent(adPerformanceReport.totals.fillRatePct)}</strong></li>
                    <li><span>Total show rate</span><strong>{formatPercent(adPerformanceReport.totals.showRatePct)}</strong></li>
                  </ul>
                  {renderFormatBreakdown("Weekly format funnel", adPerformanceReport.formatBreakdown)}
                  {renderDiagnosticReasons(adPerformanceReport.diagnosticReasonCounts)}

                  <div className="muted">
                    Thresholds: minRequests={adPerformanceReport.thresholds.minRequests}, fillRate=
                    {formatPercent(adPerformanceReport.thresholds.fillRatePct)}, showRate=
                    {formatPercent(adPerformanceReport.thresholds.showRatePct)}
                  </div>

                  {adPerformanceReport.alerts.length === 0 ? (
                    <p className="muted">No app/format alerts in this window.</p>
                  ) : (
                    <div className="ad-alert-list">
                      {adPerformanceReport.alerts.map((alert) => (
                        <div
                          key={`${alert.appId}-${alert.format}`}
                          className="ad-alert-item"
                        >
                          <div className="ad-alert-header">
                            <strong>{alert.appLabel}</strong>
                            <code>{alert.format}</code>
                          </div>
                          <div className="ad-alert-metrics">
                            requests={alert.adRequests} · matched={alert.matchedRequests} · impressions=
                            {alert.impressions}
                          </div>
                          <div className="ad-alert-metrics">
                            fill={formatPercent(alert.fillRatePct)} · show=
                            {formatPercent(alert.showRatePct)} · earnings=
                            {formatTry(alert.earningsTry)}
                          </div>
                          <div className="ad-alert-metrics">
                            eCPM={formatTry(alert.ecpmTry)}
                          </div>
                          <div className="ad-alert-reasons">
                            {alert.reasons.map((reason) => (
                              <span key={reason} className="status-pill status-paused">
                                {reason}
                              </span>
                            ))}
                          </div>
                          <small className="muted">
                            appId=<code>{alert.appId}</code>
                          </small>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ) : (
                !adReportError && <p className="muted">Weekly diagnostics not loaded yet.</p>
              )}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
