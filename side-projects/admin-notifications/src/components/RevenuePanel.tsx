import { useEffect, useState } from "react";
import type { User } from "firebase/auth";
import { functionsBaseUrl } from "../firebase";
import {
  fetchAdminFunctionJson,
  formatDateTime,
  formatPercent,
  formatTry,
  isRevenueSummary,
  sortedApps,
} from "../helpers";
import type { RevenueSummary } from "../types";

const admobAccountUrl =
  "https://admob.google.com/v2/home?utm_source=admin-panel&utm_medium=deep-link";
const playConsoleUrl =
  "https://play.google.com/console/u/0/developers/makerpars-oaslananka-mobil";

type RevenuePanelProps = {
  user: User;
};

export default function RevenuePanel({ user }: RevenuePanelProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [summary, setSummary] = useState<RevenueSummary | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      setLoading(true);
      setError("");
      try {
        const idToken = await user.getIdToken();
        const payload = await fetchAdminFunctionJson<RevenueSummary>({
          endpoint: `${functionsBaseUrl}/adminGetRevenueSummary`,
          idToken,
          body: {
            catalog: sortedApps.map((app) => ({
              packageName: app.package,
              appName: app.name ?? app.flavor,
            })),
          },
        });

        if (!isRevenueSummary(payload)) {
          throw new Error("Revenue summary response is invalid.");
        }

        if (!cancelled) {
          setSummary(payload);
        }
      } catch (loadError) {
        console.error(loadError);
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load revenue data.");
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
      id="tabpanel-revenue"
      role="tabpanel"
      aria-labelledby="tab-revenue"
    >
      <main className="panel form-panel" role="main" aria-label="Revenue panel">
        <div className="panel-header">
          <h2>Revenue</h2>
        </div>

        <section className="subsection" aria-label="Revenue summary">
          <div className="coverage-box">
            <div className="device-preview-header">
              <strong>Tracked monetization summary</strong>
              <span className="muted">
                {loading
                  ? "Loading…"
                  : summary
                    ? `loaded ${formatDateTime(new Date(summary.loadedAt))}`
                    : "not loaded"}
              </span>
            </div>

            <p className="muted">
              Subscription metrics come from the admin revenue summary endpoint. Ad revenue source-of-truth is the AdMob network report plus the latest-version live snapshot.
            </p>

            {error && <p className="inline-error" role="alert">{error}</p>}

            {summary && !error && (
              <>
                <div className="health-grid analytics-metric-grid">
                  <div className="health-card glass-card">
                    <div className="health-card-label">Active subscriptions</div>
                    <div className="health-card-value">{summary.activeSubscriptions}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Verified subscriptions</div>
                    <div className="health-card-value">{summary.verifiedSubscriptions}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Verified in-app</div>
                    <div className="health-card-value">{summary.verifiedInAppPurchases}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">This month purchases</div>
                    <div className="health-card-value">{summary.monthlyVerifiedPurchases}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">This month purchase revenue</div>
                    <div className="health-card-value">{formatTry(summary.monthlyVerifiedRevenueTry)}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Ad revenue snapshot</div>
                    <div className="health-card-value">{formatTry(summary.adRevenueRangeTry)}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Live source</div>
                    <div className="health-card-value">{summary.sourceFreshness.status}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Latest AdMob today date</div>
                    <div className="health-card-value">{summary.latestAdmobTodayDate ?? "-"}</div>
                  </div>
                </div>

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Package</th>
                        <th>Verified purchases</th>
                        <th>Tracked purchase revenue</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.purchasesByPackage.map((item) => (
                        <tr key={item.packageName}>
                          <td><code>{item.packageName}</code></td>
                          <td>{item.count}</td>
                          <td>{formatTry(item.revenueTry)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {summary.adAlerts.length > 0 && (
                  <div className="ad-alert-list revenue-alert-list">
                    {summary.adAlerts.slice(0, 8).map((alert) => (
                      <article key={`${alert.appId}-${alert.format}`} className="ad-alert-item glass-card">
                        <div className="ad-alert-header">
                          <strong>{alert.appLabel}</strong>
                          <span className="status-pill status-paused">
                            <span className="status-dot" />
                            {alert.format}
                          </span>
                        </div>
                        <div className="ad-alert-metrics">
                          fill {formatPercent(alert.fillRatePct)} · show {formatPercent(alert.showRatePct)} · eCPM {formatTry(alert.ecpmTry)}
                        </div>
                        <div className="ad-alert-reasons">
                          {alert.reasons.map((reason) => (
                            <span key={reason} className="status-pill status-expired">
                              <span className="status-dot" />
                              {reason}
                            </span>
                          ))}
                        </div>
                      </article>
                    ))}
                  </div>
                )}

                <div className="analytics-table-wrap">
                  <table className="analytics-table">
                    <thead>
                      <tr>
                        <th>Package</th>
                        <th>Live version</th>
                        <th>Requests</th>
                        <th>Matched</th>
                        <th>Impressions</th>
                        <th>Fill</th>
                        <th>Show</th>
                        <th>Earnings</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.deliveryRatiosByPackage.length > 0 ? (
                        summary.deliveryRatiosByPackage.map((item) => (
                          <tr key={item.packageName}>
                            <td><code>{item.packageName}</code></td>
                            <td>{item.liveVersionName}</td>
                            <td>{item.adRequests}</td>
                            <td>{item.matchedRequests}</td>
                            <td>{item.impressions}</td>
                            <td>{formatPercent(item.fillRatePct)}</td>
                            <td>{formatPercent(item.showRatePct)}</td>
                            <td>{formatTry(item.earningsTry)}</td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={8} className="muted">No live AdMob delivery ratios found.</td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="meta-list revenue-meta-list">
                  <dt>Ad snapshot generated</dt>
                  <dd>{summary.reportGeneratedAt ? formatDateTime(new Date(summary.reportGeneratedAt)) : "-"}</dd>
                  <dt>Ad snapshot range</dt>
                  <dd>{summary.reportRangeLabel ?? "-"}</dd>
                  <dt>Revenue source</dt>
                  <dd>{summary.revenueSource ?? "-"}</dd>
                  <dt>Live today status</dt>
                  <dd>{summary.sourceFreshness.liveTodayStatus ?? "-"}</dd>
                  <dt>Live today generated</dt>
                  <dd>
                    {summary.sourceFreshness.liveTodayGeneratedAt
                      ? formatDateTime(new Date(summary.sourceFreshness.liveTodayGeneratedAt))
                      : "-"}
                  </dd>
                  <dt>Live today age</dt>
                  <dd>
                    {typeof summary.sourceFreshness.liveTodayAgeHours === "number"
                      ? `${summary.sourceFreshness.liveTodayAgeHours.toFixed(2)}h`
                      : "-"}
                  </dd>
                  <dt>Today ad revenue</dt>
                  <dd>{formatTry(summary.adRevenueTodayTry)}</dd>
                  <dt>Combined tracked revenue</dt>
                  <dd>{formatTry(summary.totalTrackedRevenueTry)}</dd>
                </div>

                {summary.sourceFreshness.issues.length > 0 && (
                  <div className="ad-alert-list revenue-alert-list">
                    {summary.sourceFreshness.issues.map((issue) => (
                      <article key={issue} className="ad-alert-item glass-card">
                        <div className="ad-alert-header">
                          <strong>Source freshness issue</strong>
                          <span className="status-pill status-expired">
                            <span className="status-dot" />
                            {summary.sourceFreshness.status}
                          </span>
                        </div>
                        <div className="ad-alert-metrics">{issue}</div>
                      </article>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </section>

        <section className="subsection health-cards" aria-label="Revenue console links">
          <h3>Revenue console links</h3>
          <div className="flavor-grid">
            <article className="flavor-card glass-card">
              <div className="flavor-card-header">
                <div>
                  <strong>AdMob</strong>
                  <div className="muted">Global AdMob and app revenue dashboard</div>
                </div>
              </div>
              <div className="flavor-links">
                <a className="btn-secondary" href={admobAccountUrl} target="_blank" rel="noopener noreferrer">
                  Open AdMob
                </a>
              </div>
            </article>

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
                    href={`${playConsoleUrl}/app/${app.package}/monetize`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Play Console
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
