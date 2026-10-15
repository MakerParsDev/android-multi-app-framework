// Cache only complete admin summary responses, after every request has passed auth.
// Ephemeral to each Worker isolate: no KV, Durable Object, or paid service.
type CachedSummary = { body: string; expiresAt: number };
type SummaryPayload = { body: string; status: number };

export function createSummaryResponseCache(
  { maxEntries = 8, now = () => Date.now() }: { maxEntries?: number; now?: () => number } = {},
) {
  const entries = new Map<string, CachedSummary>();
  const pending = new Map<string, Promise<SummaryPayload>>();

  const asResponse = ({ body, status }: SummaryPayload): Response =>
    new Response(body, {
      status,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "no-store",
      },
    });

  return async (
    key: string,
    ttlMs: number,
    load: () => Promise<Response>,
  ): Promise<Response> => {
    const cached = entries.get(key);
    if (cached && cached.expiresAt > now()) {
      return asResponse({ body: cached.body, status: 200 });
    }
    entries.delete(key);

    let task = pending.get(key);
    if (!task) {
      task = (async () => {
        const response = await load();
        const result = { body: await response.text(), status: response.status };
        if (response.ok && ttlMs > 0 && maxEntries > 0) {
          // Remove an oldest entry rather than caching an unbounded number
          // of package/catalog combinations.
          while (entries.size >= maxEntries) {
            const oldestKey = entries.keys().next().value;
            if (oldestKey === undefined) break;
            entries.delete(oldestKey);
          }
          entries.set(key, { body: result.body, expiresAt: now() + ttlMs });
        }
        return result;
      })();
      pending.set(key, task);
    }

    try {
      return asResponse(await task);
    } finally {
      if (pending.get(key) === task) pending.delete(key);
    }
  };
}
