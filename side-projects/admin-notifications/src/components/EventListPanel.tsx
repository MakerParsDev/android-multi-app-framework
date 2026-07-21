import { useMemo } from "react";
import type { LoadState, ScheduledEventRecord } from "../types";
import { formatDateTime, scheduleLabel, packageLabel } from "../helpers";

type EventListPanelProps = {
  events: ScheduledEventRecord[];
  eventsState: LoadState;
  selectedId: string | null;
  eventSearchQuery: string;
  eventStatusFilter: "all" | ScheduledEventRecord["status"];
  onSearchChange: (query: string) => void;
  onStatusFilterChange: (filter: "all" | ScheduledEventRecord["status"]) => void;
  onSelectEvent: (event: ScheduledEventRecord) => void;
  onNewEvent: () => void;
};

export default function EventListPanel({
  events,
  eventsState,
  selectedId,
  eventSearchQuery,
  eventStatusFilter,
  onSearchChange,
  onStatusFilterChange,
  onSelectEvent,
  onNewEvent,
}: EventListPanelProps) {
  const filteredEvents = useMemo(() => {
    const q = eventSearchQuery.trim().toLowerCase();
    return events.filter((event) => {
      if (eventStatusFilter !== "all" && event.status !== eventStatusFilter) return false;
      if (!q) return true;
      return (
        event.name.toLowerCase().includes(q) ||
        event.type.toLowerCase().includes(q) ||
        event.packages.some((pkg) => pkg.toLowerCase().includes(q))
      );
    });
  }, [events, eventSearchQuery, eventStatusFilter]);

  const eventStats = useMemo(() => {
    const counts = {
      total: events.length,
      scheduled: 0,
      paused: 0,
      sent: 0,
      expired: 0,
      visible: filteredEvents.length,
    };
    for (const event of events) {
      counts[event.status] += 1;
    }
    return counts;
  }, [events, filteredEvents.length]);

  return (
    <aside className="panel list-panel" role="complementary" aria-label="Event list">
      <div className="panel-header">
        <h2>Scheduled events</h2>
        <button className="btn-secondary btn-new" onClick={onNewEvent} aria-label="Create new event">
          <span aria-hidden="true">+</span> New event
        </button>
      </div>

      <div className="event-summary" aria-label="Event status counts">
        <span className="status-pill status-scheduled">
          <span className="status-dot" aria-hidden="true" /> scheduled: {eventStats.scheduled}
        </span>
        <span className="status-pill status-paused">
          <span className="status-dot" aria-hidden="true" /> paused: {eventStats.paused}
        </span>
        <span className="status-pill status-sent">
          <span className="status-dot" aria-hidden="true" /> sent: {eventStats.sent}
        </span>
        <span className="status-pill status-expired">
          <span className="status-dot" aria-hidden="true" /> expired: {eventStats.expired}
        </span>
      </div>

      <div className="event-filters">
        <label>
          <span className="label-text">Search</span>
          <input
            value={eventSearchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="name / type / package"
            aria-label="Search events"
          />
        </label>
        <label>
          <span className="label-text">Status</span>
          <select
            value={eventStatusFilter}
            onChange={(e) =>
              onStatusFilterChange(e.target.value as "all" | ScheduledEventRecord["status"])
            }
            aria-label="Filter by status"
          >
            <option value="all">all</option>
            <option value="scheduled">scheduled</option>
            <option value="paused">paused</option>
            <option value="sent">sent</option>
            <option value="expired">expired</option>
          </select>
        </label>
      </div>

      {eventsState === "loading" && (
        <div className="loading-placeholder" role="status">
          <div className="loading-spinner" aria-hidden="true" />
          <p className="muted">Loading events…</p>
        </div>
      )}
      {eventsState === "error" && <p className="inline-error" role="alert">Failed to load events.</p>}
      {eventsState === "ready" && events.length === 0 && (
        <p className="muted empty-state">No scheduled_events documents yet.</p>
      )}
      {eventsState === "ready" && events.length > 0 && filteredEvents.length === 0 && (
        <p className="muted">
          No events match this filter ({eventStats.visible}/{eventStats.total} shown).
        </p>
      )}

      <div className="event-list" role="listbox" aria-label="Events">
        {filteredEvents.map((event) => (
          <button
            key={event.id}
            type="button"
            role="option"
            aria-selected={selectedId === event.id}
            className={`event-card ${selectedId === event.id ? "active" : ""}`}
            onClick={() => onSelectEvent(event)}
          >
            <div className="event-card-top">
              <strong>{event.name || "(untitled)"}</strong>
              <span className={`status-pill status-${event.status}`}>{event.status}</span>
            </div>
            <div className="event-card-meta">{event.type} · {scheduleLabel(event)}</div>
            <div className="event-card-meta">{packageLabel(event.packages)}</div>
            <div className="event-card-meta">
              sentTimezones={event.sentTimezones.length} · updated {formatDateTime(event.updatedAt)}
            </div>
          </button>
        ))}
      </div>
    </aside>
  );
}
