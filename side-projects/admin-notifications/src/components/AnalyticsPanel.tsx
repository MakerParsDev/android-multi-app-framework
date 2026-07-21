import { useEffect, useState } from "react";
import type { User } from "firebase/auth";
import { functionsBaseUrl } from "../firebase";
import {
  fetchAdminFunctionJson,
  formatDateTime,
  isAnalyticsSummary,
  sortedApps,
} from "../helpers";
import type { AnalyticsSummary } from "../types";

const firebaseProjectId = import.meta.env.VITE_FIREBASE_PROJECT_ID ?? "makerpars-oaslananka-mobil";

type AnalyticsPanelProps = {
  user: User;
};

export default function AnalyticsPanel({ user }: AnalyticsPanelProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      setLoading(true);
      setError("");
      try {
        const idToken = await user.getIdToken();
        const payload = await fetchAdminFunctionJson<AnalyticsSummary>({
          endpoint: `${functionsBaseUrl}/adminGetAnalyticsSummary`,
          idToken,
          body: { packages: sortedApps.map((app) => app.package) },
        });

        if (!isAnalyticsSummary(payload)) {
          throw new Error("Analytics summary response is invalid.");
        }

        if (!cancelled) {
          setSummary(payload);
        }
      } catch (loadError) {
        console.error(loadError);
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load analytics summary.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [user]);

  return (
    <div
      className="single-panel-grid"
      id="tabpanel-analytics"
      role="tabpanel"
      aria-labelledby="tab-analytics"
    >
      <main className="panel form-panel" role="main" aria-label="Analytics panel">
        <div className="panel-header">
          <h2>Analytics</h2>
        </div>

        <section className="subsection" aria-label="Push registration summary">
          <div className="coverage-box">
            <div className="device-preview-header">
              <strong>Push registration summary</strong>
              <span className="muted">
                {loading ? "Loading…" : summary ? `loaded ${formatDateTime(new Date(summary.loadedAt))}` : "not loaded"}
              </span>
            </div>

            <p className="muted">
              Current metrics come from the admin analytics summary endpoint. Deeper DAU, retention and event funnels remain in Firebase Analytics / BigQuery.
            </p>

            {error && <p className="inline-error" role="alert">{error}</p>}

            {summary && !error && (
              <>
                <div className="health-grid analytics-metric-grid">
                  <div className="health-card glass-card">
                    <div className="health-card-label">Total devices</div>
                    <div className="health-card-value">{summary.totalDevices}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Active 30d</div>
                    <div className="health-card-value">{summary.activeDevices30d}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Notifications enabled</div>
                    <div className="health-card-value">{summary.notificationsEnabled30d}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Enable rate</div>
                    <div className="health-card-value">
                      {summary.activeDevices30d > 0
                        ? `${((summary.notificationsEnabled30d / summary.activeDevices30d) * 100).toFixed(1)}%`
                      : "-"}
                    </div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">With token</div>
                    <div className="health-card-value">{summary.devicesWithToken}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Without token</div>
                    <div className="health-card-value">{summary.devicesWithoutToken}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Synced 24h</div>
                    <div className="health-card-value">{summary.recentlySynced24h}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Stale 7d</div>
                    <div className="health-card-value">{summary.staleRegistration7d}</div>
                  </div>
                </div>

                <div className="health-grid analytics-metric-grid">
                  <div className="health-card glass-card">
                    <div className="health-card-label">Consent-blocked suppressions</div>
                    <div className="health-card-value">{summary.consentHealth.consentBlockedTotal}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Consent-blocked share</div>
                    <div className="health-card-value">{summary.consentHealth.consentBlockedPct.toFixed(1)}%</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Consent error</div>
                    <div className="health-card-value">{summary.consentHealth.consentError}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Consent missing</div>
                    <div className="health-card-value">{summary.consentHealth.consentMissing}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Retry backoff</div>
                    <div className="health-card-value">{summary.consentHealth.consentRetryBackoff}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Error + missing share</div>
                    <div className="health-card-value">{summary.consentHealth.errorOrMissingPct.toFixed(1)}%</div>
                  </div>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Package</th>
                        <th>Status</th>
                        <th>Token coverage</th>
                        <th>Synced 24h</th>
                        <th>Stale 7d</th>
                        <th>Suppressions</th>
                        <th>Top reason</th>
                        <th>Notes</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.runtimeHealthByPackage.length > 0 ? (
                        summary.runtimeHealthByPackage.map((item) => (
                          <tr key={item.packageName}>
                            <td><code>{item.packageName}</code></td>
                            <td>
                              <strong>{item.healthStatus}</strong>
                            </td>
                            <td>
                              {item.withToken}/{item.totalDevices} ({item.tokenCoveragePct.toFixed(1)}%)
                            </td>
                            <td>
                              {item.recentlySynced24h}/{item.totalDevices} ({item.syncCoveragePct.toFixed(1)}%)
                            </td>
                            <td>
                              {item.stale7d}/{item.totalDevices} ({item.stalePct.toFixed(1)}%)
                            </td>
                            <td>{item.totalSuppressions}</td>
                            <td><code>{item.topReason || "-"}</code></td>
                            <td>{item.healthNotes.join(" ")}</td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={8} className="muted">No package-level runtime health data found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Package</th>
                        <th>Registered devices</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.devicesByPackage.map((item) => (
                        <tr key={item.packageName}>
                          <td><code>{item.packageName}</code></td>
                          <td>{item.count}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Format</th>
                        <th>Intent</th>
                        <th>Blocked</th>
                        <th>Not loaded</th>
                        <th>Started</th>
                        <th>Impression</th>
                        <th>Dismissed</th>
                        <th>Failed</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.runtimeFunnelByFormat.length > 0 ? (
                        summary.runtimeFunnelByFormat.map((item) => (
                          <tr key={item.format}>
                            <td><code>{item.format}</code></td>
                            <td>{item.showIntent}</td>
                            <td>{item.showBlocked}</td>
                            <td>{item.showNotLoaded}</td>
                            <td>{item.showStarted}</td>
                            <td>{item.showImpression}</td>
                            <td>{item.showDismissed}</td>
                            <td>{item.showFailed}</td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={8} className="muted">No runtime ad funnel data found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Rewarded format</th>
                        <th>Intent</th>
                        <th>Blocked</th>
                        <th>Not loaded</th>
                        <th>Impression</th>
                        <th>Blocked rate</th>
                        <th>Not loaded rate</th>
                        <th>Impression rate</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.rewardedFunnel.length > 0 ? (
                        summary.rewardedFunnel.map((item) => (
                          <tr key={item.format}>
                            <td><code>{item.format}</code></td>
                            <td>{item.showIntent}</td>
                            <td>{item.showBlocked}</td>
                            <td>{item.showNotLoaded}</td>
                            <td>{item.showImpression}</td>
                            <td>{item.blockedRatePct.toFixed(1)}%</td>
                            <td>{item.notLoadedRatePct.toFixed(1)}%</td>
                            <td>{item.impressionRatePct.toFixed(1)}%</td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={8} className="muted">No rewarded funnel data found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Suppress reason</th>
                        <th>Count</th>
                        <th>Share</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.suppressMix.length > 0 ? (
                        summary.suppressMix.map((item) => (
                          <tr key={item.reason}>
                            <td><code>{item.reason}</code></td>
                            <td>{item.count}</td>
                            <td>{item.sharePct.toFixed(1)}%</td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={3} className="muted">No suppress reason data found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Package</th>
                        <th>Total suppressions</th>
                        <th>Top reason</th>
                        <th>Top reason count</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.runtimeSuppressByPackage.length > 0 ? (
                        summary.runtimeSuppressByPackage.map((item) => (
                          <tr key={item.packageName}>
                            <td><code>{item.packageName}</code></td>
                            <td>{item.totalSuppressions}</td>
                            <td><code>{item.topReason || "-"}</code></td>
                            <td>{item.topReasonCount}</td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={4} className="muted">No package-level suppress data found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Package</th>
                        <th>Status</th>
                        <th>Sync coverage</th>
                        <th>Stale</th>
                        <th>Suppressions</th>
                        <th>Top reason</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.stalePackages.length > 0 ? (
                        summary.stalePackages.map((item) => (
                          <tr key={item.packageName}>
                            <td><code>{item.packageName}</code></td>
                            <td><strong>{item.healthStatus}</strong></td>
                            <td>{item.syncCoveragePct.toFixed(1)}%</td>
                            <td>{item.stalePct.toFixed(1)}%</td>
                            <td>{item.totalSuppressions}</td>
                            <td><code>{item.topReason || "-"}</code></td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={6} className="muted">No stale package shortlist found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Package</th>
                        <th>Total</th>
                        <th>With token</th>
                        <th>Without token</th>
                        <th>Synced 24h</th>
                        <th>Stale 7d</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.registrationHealthByPackage.length > 0 ? (
                        summary.registrationHealthByPackage.map((item) => (
                          <tr key={item.packageName}>
                            <td><code>{item.packageName}</code></td>
                            <td>{item.totalDevices}</td>
                            <td>{item.withToken}</td>
                            <td>{item.withoutToken}</td>
                            <td>{item.recentlySynced24h}</td>
                            <td>{item.stale7d}</td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={6} className="muted">No registration health data found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Coverage report</th>
                        <th>Generated</th>
                        <th>Package count</th>
                        <th>Active devices</th>
                        <th>Total devices</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.recentCoverageReports.length > 0 ? (
                        summary.recentCoverageReports.map((report) => (
                          <tr key={report.id}>
                            <td>{report.days}d snapshot</td>
                            <td>{formatDateTime(new Date(report.generatedAt))}</td>
                            <td>{report.packageCount}</td>
                            <td>{report.totalActiveDevices}</td>
                            <td>{report.totalDevices}</td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={5} className="muted">No coverage reports found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>
        </section>

        <section className="subsection health-cards" aria-label="Console links">
          <h3>Console links</h3>
          <div className="flavor-grid">
            {sortedApps.map((app) => (
              <article key={app.package} className="flavor-card glass-card">
                <div className="flavor-card-header">
                  <div>
                    <strong>{app.name ?? app.flavor}</strong>
                    <div className="muted">{app.package}</div>
                  </div>
                </div>
                <div className="flavor-links">
                  <a
                    className="btn-secondary"
                    href={`https://console.firebase.google.com/project/${firebaseProjectId}/analytics/app/android:${app.package}/dashboard`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Analytics
                  </a>
                  <a
                    className="btn-secondary"
                    href={`https://console.firebase.google.com/project/${firebaseProjectId}/crashlytics/app/android:${app.package}/issues`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Crashlytics
                  </a>
                </div>
              </article>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
