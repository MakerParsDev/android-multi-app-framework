import { useEffect, useState } from "react";
import type { User } from "firebase/auth";
import { functionsBaseUrl } from "../firebase";
import {
  fetchAdminFunctionJson,
  formatDateTime,
  isFlavorHubSummary,
  parseFlavorVersions,
  sortedApps,
} from "../helpers";
import type { FlavorHubSummary } from "../types";

const flavorVersions = parseFlavorVersions();
const firebaseConsoleUrl = "https://console.firebase.google.com/project/makerpars-oaslananka-mobil/overview";
const playConsoleBaseUrl = "https://play.google.com/console/u/0/developers";

type FlavorHubPanelProps = {
  user: User;
};

export default function FlavorHubPanel({ user }: FlavorHubPanelProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [state, setState] = useState<FlavorHubSummary | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      setLoading(true);
      setError("");
      try {
        const idToken = await user.getIdToken();
        const payload = await fetchAdminFunctionJson<FlavorHubSummary>({
          endpoint: `${functionsBaseUrl}/adminGetFlavorHubSummary`,
          idToken,
          body: { packages: sortedApps.map((app) => app.package) },
        });

        if (!isFlavorHubSummary(payload)) {
          throw new Error("Flavor Hub summary response is invalid.");
        }

        if (!cancelled) {
          setState(payload);
        }
      } catch (loadError) {
        console.error(loadError);
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Failed to load flavor coverage.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [user]);

  return (
    <div
      className="single-panel-grid"
      id="tabpanel-flavor-hub"
      role="tabpanel"
      aria-labelledby="tab-flavor-hub"
    >
      <main className="panel form-panel" role="main" aria-label="Flavor Hub panel">
        <div className="panel-header">
          <h2>Flavor Hub</h2>
        </div>

        <section className="subsection" aria-label="Flavor inventory">
          <div className="coverage-box">
            <div className="device-preview-header">
              <strong>Android app inventory</strong>
              <span className="muted">
                {loading
                  ? "Loading…"
                  : state
                    ? `${sortedApps.length} flavors · ${state.source === "coverage_reports" ? "coverage_reports" : "devices"}`
                    : `${sortedApps.length} flavors`}
              </span>
            </div>
            <p className="muted">
              Version data comes from <code>VITE_FLAVOR_VERSIONS</code>. If blank, run{" "}
              <code>npm run inject:flavor-versions</code> and append the output to your admin env.
            </p>
            {state && (
              <p className="muted">
                Coverage source <code>{state.source}</code> · loaded {formatDateTime(new Date(state.loadedAt))}
              </p>
            )}
            {error && <p className="inline-error" role="alert">{error}</p>}

            <div className="flavor-grid" role="list" aria-label="Flavor inventory">
              {sortedApps.map((app) => {
                const versionInfo = flavorVersions[app.flavor] ?? {};
                const coverage = state?.coverage[app.package];
                const activeDevices = coverage?.active ?? 0;
                const totalDevices = coverage?.total ?? 0;
                const coverageRate = totalDevices > 0
                  ? `${((activeDevices / totalDevices) * 100).toFixed(1)}%`
                  : "-";
                return (
                  <article key={app.package} className="flavor-card glass-card" role="listitem">
                    <div className="flavor-card-header">
                      <div>
                        <strong>{app.name}</strong>
                        <div className="muted">{app.flavor}</div>
                      </div>
                      <span className="status-pill status-scheduled">Android</span>
                    </div>

                    <dl className="meta-list">
                      <dt>Package</dt>
                      <dd><code>{app.package}</code></dd>
                      <dt>Version</dt>
                      <dd>{versionInfo.versionName ?? "-"}</dd>
                      <dt>Version code</dt>
                      <dd>{versionInfo.versionCode ?? "-"}</dd>
                      <dt>Active devices (30d)</dt>
                      <dd>{activeDevices}</dd>
                      <dt>Total devices</dt>
                      <dd>{totalDevices}</dd>
                      <dt>Push coverage</dt>
                      <dd>{coverageRate}</dd>
                      <dt>AdMob App ID</dt>
                      <dd><code>{app.admob_app_id}</code></dd>
                    </dl>

                    <div className="flavor-links">
                      <a
                        className="btn-secondary"
                        href={`https://play.google.com/store/apps/details?id=${app.package}`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Play Store
                      </a>
                      <a
                        className="btn-secondary"
                        href={firebaseConsoleUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Firebase
                      </a>
                      <a
                        className="btn-secondary"
                        href={playConsoleBaseUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Play Console
                      </a>
                    </div>
                  </article>
                );
              })}
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}
