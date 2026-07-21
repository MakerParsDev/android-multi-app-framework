export type WorkerVersionMetadata = {
  id?: string;
  tag?: string;
  timestamp?: string;
};

export type HealthMetadata = {
  environment?: string;
  gitSha?: string;
  builtAt?: string;
  workerVersion?: WorkerVersionMetadata;
};

function normalizedValue(value: string | undefined, fallback: string): string {
  const normalized = value?.trim();
  return normalized ? normalized : fallback;
}

function optionalValue(value: string | undefined): string | null {
  const normalized = value?.trim();
  return normalized ? normalized : null;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "cache-control": "no-store",
      "content-type": "application/json; charset=utf-8",
    },
  });
}

export function handleHealthCheck(
  request: Request,
  metadata: HealthMetadata,
): Response {
  if (request.method !== "POST" && request.method !== "GET") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  return jsonResponse({
    ok: true,
    service: "cloudflare-admin-api",
    environment: normalizedValue(metadata.environment, "unknown"),
    gitSha: normalizedValue(metadata.gitSha, "unknown"),
    builtAt: normalizedValue(metadata.builtAt, "unknown"),
    workerVersionId: optionalValue(metadata.workerVersion?.id),
    workerVersionTag: optionalValue(metadata.workerVersion?.tag),
    workerVersionTimestamp: optionalValue(metadata.workerVersion?.timestamp),
    region: "global",
    timestamp: new Date().toISOString(),
  });
}
