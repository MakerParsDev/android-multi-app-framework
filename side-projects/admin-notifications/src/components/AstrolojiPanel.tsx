import { useCallback, useEffect, useState } from "react";
import type { User } from "firebase/auth";
import { fetchAdminFunctionJson } from "../helpers";

type AstrolojiPanelProps = {
  user: User;
};

type AstrolojiHealth = {
  status: "ok" | "degraded";
  timestamp: string;
  db: boolean;
  kv: boolean;
  llmProviders: Record<string, string[]>;
};

type AstrolojiLlmTestTaskType = "daily_content" | "deep_reading" | "chat_consultation";

type AstrolojiLlmTestResult = {
  succeeded: boolean;
  providerId: string | null;
  text: string | null;
  usage: { inputTokens: number; outputTokens: number } | null;
  attempts: Array<{ providerId: string; error: string }>;
};

const LLM_TEST_TASK_TYPES: AstrolojiLlmTestTaskType[] = ["daily_content", "deep_reading", "chat_consultation"];

export default function AstrolojiPanel({ user }: AstrolojiPanelProps) {
  const apiBaseUrl = import.meta.env.VITE_ASTROLOJI_API_URL?.trim().replace(/\/$/, "");

  const [health, setHealth] = useState<AstrolojiHealth | null>(null);
  const [healthLoading, setHealthLoading] = useState(false);
  const [healthError, setHealthError] = useState("");

  const [testLoading, setTestLoading] = useState<AstrolojiLlmTestTaskType | null>(null);
  const [testError, setTestError] = useState("");
  const [testResults, setTestResults] = useState<Partial<Record<AstrolojiLlmTestTaskType, AstrolojiLlmTestResult>>>({});

  const loadHealth = useCallback(async () => {
    if (!apiBaseUrl) return;
    setHealthLoading(true);
    setHealthError("");
    try {
      const idToken = await user.getIdToken();
      const payload = await fetchAdminFunctionJson<AstrolojiHealth>({
        endpoint: `${apiBaseUrl}/api/v1/admin/panel/health`,
        idToken,
        method: "GET",
      });
      setHealth(payload);
    } catch (loadError) {
      console.error(loadError);
      setHealthError(loadError instanceof Error ? loadError.message : "Failed to load Astroloji health.");
    } finally {
      setHealthLoading(false);
    }
  }, [apiBaseUrl, user]);

  useEffect(() => {
    loadHealth();
  }, [loadHealth]);

  const runLlmTest = useCallback(async (taskType: AstrolojiLlmTestTaskType) => {
    if (!apiBaseUrl) return;
    setTestLoading(taskType);
    setTestError("");
    try {
      const idToken = await user.getIdToken();
      const payload = await fetchAdminFunctionJson<AstrolojiLlmTestResult>({
        endpoint: `${apiBaseUrl}/api/v1/admin/panel/llm/test`,
        idToken,
        body: { taskType },
      });
      setTestResults((previous) => ({ ...previous, [taskType]: payload }));
    } catch (runError) {
      console.error(runError);
      setTestError(runError instanceof Error ? runError.message : "LLM test request failed.");
    } finally {
      setTestLoading(null);
    }
  }, [apiBaseUrl, user]);

  return (
    <div className="single-panel-grid" id="tabpanel-astroloji" role="tabpanel" aria-labelledby="tab-astroloji">
      <main className="panel form-panel" role="main" aria-label="Astroloji panel">
        <div className="panel-header">
          <h2>Astroloji</h2>
          <button
            className="btn-secondary"
            onClick={loadHealth}
            disabled={!apiBaseUrl || healthLoading}
            aria-label="Refresh Astroloji health"
          >
            Refresh
          </button>
        </div>

        {!apiBaseUrl && (
          <p className="muted">
            Astroloji panel is hidden because <code>VITE_ASTROLOJI_API_URL</code> is not configured for this deploy.
          </p>
        )}

        {apiBaseUrl && (
          <>
            <section className="subsection health-cards" aria-label="Astroloji backend health">
              <h3>Backend Health</h3>
              {healthLoading && <p className="muted">Checking…</p>}
              {healthError && <p className="inline-error" role="alert">{healthError}</p>}
              {health && (
                <div className="health-grid">
                  <div className="health-card glass-card">
                    <div className="health-card-label">Status</div>
                    <div className="health-card-value">{health.status === "ok" ? "🟢 ok" : "🔴 degraded"}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Database</div>
                    <div className="health-card-value">{health.db ? "🟢 reachable" : "🔴 unreachable"}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Cache (KV)</div>
                    <div className="health-card-value">{health.kv ? "🟢 reachable" : "🔴 unreachable"}</div>
                  </div>
                  <div className="health-card glass-card">
                    <div className="health-card-label">Checked</div>
                    <div className="health-card-value">{new Date(health.timestamp).toLocaleTimeString()}</div>
                  </div>
                  {Object.entries(health.llmProviders).map(([taskType, providerIds]) => (
                    <div key={taskType} className="health-card glass-card">
                      <div className="health-card-label">{taskType}</div>
                      <div className="health-card-value">
                        <code>{providerIds.length > 0 ? providerIds.join(" → ") : "none configured"}</code>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="subsection health-cards" aria-label="LLM provider test">
              <h3>LLM Provider Test</h3>
              <p className="muted">
                Sends a small, fixed test prompt through the real provider fallback chain for each task type — confirms a
                provider is reachable without spending a real user's daily budget.
              </p>
              {testError && <p className="inline-error" role="alert">{testError}</p>}
              <div className="service-list">
                {LLM_TEST_TASK_TYPES.map((taskType) => {
                  const result = testResults[taskType];
                  return (
                    <div key={taskType} className="service-card glass-card">
                      <div className="service-card-header">
                        <strong>{taskType}</strong>
                        <button
                          className="btn-secondary"
                          onClick={() => runLlmTest(taskType)}
                          disabled={testLoading === taskType}
                          aria-label={`Run LLM test for ${taskType}`}
                        >
                          {testLoading === taskType ? "Running…" : "Run test"}
                        </button>
                      </div>
                      {result && (
                        <>
                          <div className="service-card-detail">
                            <span className="muted">
                              {result.succeeded ? `🟢 ${result.providerId}` : "🔴 all providers failed"}
                            </span>
                          </div>
                          {result.text && <p className="muted">“{result.text}”</p>}
                          {result.attempts.length > 0 && (
                            <ul className="muted">
                              {result.attempts.map((attempt, index) => (
                                <li key={`${attempt.providerId}-${index}`}>
                                  {attempt.providerId}: {attempt.error}
                                </li>
                              ))}
                            </ul>
                          )}
                        </>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          </>
        )}
      </main>
    </div>
  );
}
