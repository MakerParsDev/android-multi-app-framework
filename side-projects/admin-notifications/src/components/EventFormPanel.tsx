import type { Dispatch, SetStateAction } from "react";
import type { EventStatus, LocaleKey, ScheduleMode, ScheduledEventForm, ScheduledEventRecord, WeekdayKey } from "../types";
import { WEEKDAYS } from "../types";
import { formatDateTime, normalizePackages, sortedApps } from "../helpers";

type EventFormPanelProps = {
  form: ScheduledEventForm;
  setForm: Dispatch<SetStateAction<ScheduledEventForm>>;
  selectedEvent: ScheduledEventRecord | null;
  isCreateMode: boolean;
  saving: boolean;
  deleting: boolean;
  previewLoading: boolean;
  previewCount: number | null;
  previewByPackage: Record<string, number>;
  previewError: string;
  onSave: () => void;
  onDelete: () => void;
  onReset: () => void;
  onPreviewTargetDevices: () => void;
  onUpdatePackages: (packageName: string, checked: boolean) => void;
};

export default function EventFormPanel({
  form,
  setForm,
  selectedEvent,
  isCreateMode,
  saving,
  deleting,
  previewLoading,
  previewCount,
  previewByPackage,
  previewError,
  onSave,
  onDelete,
  onReset,
  onPreviewTargetDevices,
  onUpdatePackages,
}: EventFormPanelProps) {
  return (
    <main className="panel form-panel" role="main" aria-label="Event form" id="tabpanel-events">
      <div className="panel-header">
        <h2>{isCreateMode ? "Create event" : "Edit event"}</h2>
        {!isCreateMode && (
          <button
            className="btn-danger"
            onClick={onDelete}
            disabled={deleting || saving}
            aria-label="Delete event"
          >
            {deleting ? "Deleting…" : "Delete"}
          </button>
        )}
      </div>

      <div className="form-grid">
        <label>
          <span className="label-text">Event name</span>
          <input
            value={form.name}
            onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
            placeholder="Friday reminder"
            aria-required="true"
          />
        </label>

        <label>
          <span className="label-text">Type</span>
          <input
            value={form.type}
            onChange={(e) => setForm((p) => ({ ...p, type: e.target.value }))}
            placeholder="campaign"
            aria-required="true"
          />
        </label>

        <label>
          <span className="label-text">Status</span>
          <select
            value={form.status}
            onChange={(e) => setForm((p) => ({ ...p, status: e.target.value as EventStatus }))}
          >
            <option value="scheduled">scheduled</option>
            <option value="paused">paused</option>
            <option value="sent">sent</option>
            <option value="expired">expired</option>
          </select>
        </label>

        <label>
          <span className="label-text">Local delivery time</span>
          <input
            type="time"
            value={form.localDeliveryTime}
            onChange={(e) => setForm((p) => ({ ...p, localDeliveryTime: e.target.value }))}
            aria-required="true"
          />
        </label>

        <label>
          <span className="label-text">Target timezones (optional, comma-separated)</span>
          <input
            value={form.targetTimezonesInput}
            onChange={(e) => setForm((p) => ({ ...p, targetTimezonesInput: e.target.value }))}
            placeholder="Europe/Istanbul, Europe/Berlin"
          />
          <small className="muted">
            Leave empty to send globally using current timezone matching behavior.
          </small>
        </label>

        <label>
          <span className="label-text">Topic (optional, metadata-only for now)</span>
          <input
            value={form.topic}
            onChange={(e) => setForm((p) => ({ ...p, topic: e.target.value }))}
            placeholder="dini-bildirim"
          />
          <small className="muted">
            Scheduler currently uses timezone/device targeting. Topic is stored for future use / manual sends.
          </small>
        </label>

        <label>
          <span className="label-text">Schedule mode</span>
          <select
            value={form.scheduleMode}
            onChange={(e) =>
              setForm((p) => ({ ...p, scheduleMode: e.target.value as ScheduleMode }))
            }
          >
            <option value="daily">daily</option>
            <option value="weekly">weekly</option>
            <option value="once">once</option>
          </select>
        </label>

        {form.scheduleMode === "weekly" && (
          <label>
            <span className="label-text">Weekly day</span>
            <select
              value={form.weeklyDay}
              onChange={(e) =>
                setForm((p) => ({ ...p, weeklyDay: e.target.value as WeekdayKey }))
              }
            >
              {WEEKDAYS.map((day) => (
                <option key={day} value={day}>{day}</option>
              ))}
            </select>
          </label>
        )}

        {form.scheduleMode === "once" && (
          <label>
            <span className="label-text">Date</span>
            <input
              type="date"
              value={form.date}
              onChange={(e) => setForm((p) => ({ ...p, date: e.target.value }))}
              aria-required="true"
            />
          </label>
        )}
      </div>

      {/* Target Apps */}
      <section className="subsection" aria-label="Target apps">
        <h3>Target apps</h3>
        <div className="checkbox-grid" role="group" aria-label="Package selection">
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.packages.includes("*")}
              onChange={(e) => onUpdatePackages("*", e.target.checked)}
            />
            All apps (*)
          </label>
          {sortedApps.map((app) => {
            const disabled = form.packages.includes("*");
            return (
              <label key={app.package} className={`checkbox-row ${disabled ? "disabled" : ""}`}>
                <input
                  type="checkbox"
                  checked={!disabled && form.packages.includes(app.package)}
                  disabled={disabled}
                  onChange={(e) => onUpdatePackages(app.package, e.target.checked)}
                />
                <span>{app.flavor}</span>
                <small>{app.package}</small>
              </label>
            );
          })}
        </div>

        <div className="device-preview-box">
          <div className="device-preview-header">
            <strong>Target device preview</strong>
            <button
              type="button"
              className="btn-secondary"
              onClick={onPreviewTargetDevices}
              disabled={previewLoading}
            >
              {previewLoading ? "Checking…" : "Preview target device count"}
            </button>
          </div>
          <p className="muted device-preview-note">
            Estimate only: actual dispatch also filters by timezone, schedule date, recurrence,
            and sentTimezones.
          </p>
          {previewError && <p className="inline-error" role="alert">{previewError}</p>}
          {previewCount != null && !previewError && (
            <div className="device-preview-result">
              <div className="device-preview-total">
                <span>Estimated target devices</span>
                <strong>{previewCount}</strong>
              </div>
              {!normalizePackages(form.packages).includes("*") &&
                Object.keys(previewByPackage).length > 0 && (
                  <ul className="device-preview-list">
                    {Object.entries(previewByPackage).map(([pkg, count]) => (
                      <li key={pkg}>
                        <code>{pkg}</code>
                        <span>{count}</span>
                      </li>
                    ))}
                  </ul>
                )}
            </div>
          )}
        </div>
      </section>

      {/* Locale cards */}
      <section className="subsection locale-grid" aria-label="Notification content">
        {(["tr", "en", "de"] as LocaleKey[]).map((locale) => (
          <div key={locale} className="locale-card">
            <h3>{locale.toUpperCase()}</h3>
            <label>
              <span className="label-text">Title</span>
              <input
                value={form.title[locale]}
                onChange={(e) =>
                  setForm((p) => ({
                    ...p,
                    title: { ...p.title, [locale]: e.target.value },
                  }))
                }
                aria-label={`${locale.toUpperCase()} title`}
              />
            </label>
            <label>
              <span className="label-text">Body</span>
              <textarea
                value={form.body[locale]}
                onChange={(e) =>
                  setForm((p) => ({
                    ...p,
                    body: { ...p.body, [locale]: e.target.value },
                  }))
                }
                rows={4}
                aria-label={`${locale.toUpperCase()} body`}
              />
            </label>
          </div>
        ))}
      </section>

      {/* Preview + metadata */}
      <section className="subsection preview-grid" aria-label="Preview and metadata">
        <div>
          <h3>Preview</h3>
          <div className="preview-cards">
            {(["tr", "en", "de"] as LocaleKey[]).map((locale) => (
              <div key={locale} className="preview-card">
                <div className="preview-locale">{locale.toUpperCase()}</div>
                <div className="preview-title">{form.title[locale] || "(empty title)"}</div>
                <div className="preview-body">{form.body[locale] || "(empty body)"}</div>
              </div>
            ))}
          </div>
        </div>
        <div>
          <h3>Dispatch metadata</h3>
          <dl className="meta-list">
            <dt>sentTimezones</dt>
            <dd>{selectedEvent?.sentTimezones.length ?? 0}</dd>
            <dt>lastResetAt</dt>
            <dd>{formatDateTime(selectedEvent?.lastResetAt)}</dd>
            <dt>lastDispatchedAt</dt>
            <dd>{formatDateTime(selectedEvent?.lastDispatchedAt)}</dd>
            <dt>createdAt</dt>
            <dd>{formatDateTime(selectedEvent?.createdAt)}</dd>
            <dt>updatedAt</dt>
            <dd>{formatDateTime(selectedEvent?.updatedAt)}</dd>
            <dt>createdBy</dt>
            <dd>{selectedEvent?.createdBy ?? "-"}</dd>
            <dt>updatedBy</dt>
            <dd>{selectedEvent?.updatedBy ?? "-"}</dd>
          </dl>
        </div>
      </section>

      <div className="form-actions">
        <button className="btn-primary" onClick={onSave} disabled={saving || deleting}>
          {saving ? "Saving…" : isCreateMode ? "Create event" : "Save changes"}
        </button>
        <button className="btn-secondary" onClick={onReset} disabled={saving || deleting}>
          Reset form
        </button>
      </div>
    </main>
  );
}
