import { useEffect, useMemo, useState } from "react";
import type { RemoteConfigEntry } from "../types";

type RemoteConfigPanelProps = {
  entries: RemoteConfigEntry[];
  etag: string;
  loading: boolean;
  savingKey: string | null;
  error: string;
  message: string;
  onRefresh: () => void;
  onSave: (entry: RemoteConfigEntry) => void;
};

export default function RemoteConfigPanel({
  entries,
  etag,
  loading,
  savingKey,
  error,
  message,
  onRefresh,
  onSave,
}: RemoteConfigPanelProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [drafts, setDrafts] = useState<Record<string, { value: string; description: string }>>({});

  useEffect(() => {
    const nextDrafts: Record<string, { value: string; description: string }> = {};
    for (const entry of entries) {
      nextDrafts[entry.key] = {
        value: entry.value,
        description: entry.description,
      };
    }
    setDrafts(nextDrafts);
  }, [entries]);

  const filteredEntries = useMemo(() => {
    const needles = searchQuery
      .split(",")
      .map((item) => item.trim().toLowerCase())
      .filter(Boolean);
    if (needles.length === 0) return entries;
    return entries.filter(
      (entry) =>
        needles.some((needle) =>
          entry.key.toLowerCase().includes(needle) ||
          entry.description.toLowerCase().includes(needle) ||
          entry.groupLabel?.toLowerCase().includes(needle),
        ),
    );
  }, [entries, searchQuery]);

  const groupedEntries = useMemo(() => {
    const groups = new Map<string, { label: string; entries: RemoteConfigEntry[] }>();
    for (const entry of filteredEntries) {
      const key = entry.groupKey ?? "__ungrouped__";
      const label = entry.groupLabel ?? "Ungrouped";
      const current = groups.get(key) ?? { label, entries: [] };
      current.entries.push(entry);
      groups.set(key, current);
    }
    return Array.from(groups.entries()).map(([key, group]) => ({
      key,
      label: group.label,
      entries: group.entries,
    }));
  }, [filteredEntries]);

  return (
    <div
      className="single-panel-grid"
      id="tabpanel-remote-config"
      role="tabpanel"
      aria-labelledby="tab-remote-config"
    >
      <main className="panel form-panel" role="main" aria-label="Remote Config panel">
        <div className="panel-header">
          <h2>Remote Config</h2>
          <button
            type="button"
            className="btn-secondary"
            onClick={onRefresh}
            disabled={loading}
          >
            {loading ? "Refreshing…" : "Refresh"}
          </button>
        </div>

        <section className="subsection" aria-label="Remote Config parameters">
          <div className="coverage-box">
            <div className="device-preview-header">
              <strong>Firebase Remote Config</strong>
              <span className="muted">etag={etag || "-"}</span>
            </div>

            <p className="muted">
              Reads and updates default parameter values via admin-only Cloud Function proxy.
            </p>

            <label>
              <span className="label-text">Search parameter</span>
              <input
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="ads_, billing_, notifications_"
              />
            </label>

            {error && <p className="inline-error" role="alert">{error}</p>}
            {message && !error && <p className="muted">{message}</p>}

            {filteredEntries.length === 0 ? (
              <div className="empty-state">
                <p className="muted">
                  {entries.length === 0
                    ? "No Remote Config parameters loaded yet."
                    : "No parameter matched the current search."}
                </p>
              </div>
            ) : (
              <div className="remote-config-groups">
                {groupedEntries.map((group) => (
                  <section key={group.key} className="remote-config-group" aria-label={group.label}>
                    <div className="device-preview-header">
                      <strong>{group.label}</strong>
                      <span className="muted">{group.entries.length} parameter</span>
                    </div>
                    <div className="remote-config-list" role="list" aria-label={`${group.label} parameters`}>
                      {group.entries.map((entry) => {
                        const draft = drafts[entry.key] ?? {
                          value: entry.value,
                          description: entry.description,
                        };
                        const isDirty = draft.value !== entry.value || draft.description !== entry.description;
                        const isSaving = savingKey === entry.key;

                        return (
                          <article key={entry.key} className="remote-config-card glass-card" role="listitem">
                            <div className="remote-config-card-header">
                              <div className="remote-config-meta">
                                <strong>{entry.key}</strong>
                                <span className={`status-pill remote-config-type remote-config-type-${entry.valueType}`}>
                                  {entry.valueType}
                                </span>
                              </div>
                              <button
                                type="button"
                                className="btn-secondary"
                                disabled={!isDirty || isSaving}
                                onClick={() =>
                                  onSave({
                                    key: entry.key,
                                    value: draft.value,
                                    description: draft.description,
                                    valueType: entry.valueType,
                                    groupKey: entry.groupKey,
                                    groupLabel: entry.groupLabel,
                                  })
                                }
                              >
                                {isSaving ? "Saving…" : "Save"}
                              </button>
                            </div>

                            <label>
                              <span className="label-text">Value</span>
                              <textarea
                                rows={draft.value.length > 80 ? 4 : 2}
                                value={draft.value}
                                onChange={(event) =>
                                  setDrafts((prev) => ({
                                    ...prev,
                                    [entry.key]: {
                                      ...draft,
                                      value: event.target.value,
                                    },
                                  }))
                                }
                              />
                            </label>

                            <label>
                              <span className="label-text">Description</span>
                              <input
                                value={draft.description}
                                onChange={(event) =>
                                  setDrafts((prev) => ({
                                    ...prev,
                                    [entry.key]: {
                                      ...draft,
                                      description: event.target.value,
                                    },
                                  }))
                                }
                                placeholder="Optional description"
                              />
                            </label>
                          </article>
                        );
                      })}
                    </div>
                  </section>
                ))}
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}
