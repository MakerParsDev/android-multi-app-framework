# Nominatim Location Privacy and Runtime Policy

## Scope

The automatic prayer-location action uses the device's last-known location only after Android location permission has been granted and the user explicitly starts location matching. Manual country/city/district selection remains available when permission, network access, or matching fails.

## Third-party transfer

Before calling the public OpenStreetMap Nominatim reverse-geocoding endpoint, latitude and longitude are rounded to three decimal places (approximately 110 metres of coordinate precision). The request is used only to obtain a country, city/state, and district/county candidate.

- Endpoint host: `nominatim.openstreetmap.org`
- Request path: `/reverse`
- Identifying User-Agent: `ParsfiloContentApp/1.0 (Android; +https://parsfilo.com/privacy)`
- Visible attribution: `© OpenStreetMap contributors`
- Usage policy: `https://operations.osmfoundation.org/policies/nominatim/`
- OSMF privacy policy: `https://osmfoundation.org/wiki/Privacy_Policy`

The public service's absolute maximum of one request per second is enforced process-wide. Concurrent requests for the same rounded location are coalesced into one upstream request.

## Cache and retention

The cache is memory-only and is never written to Room, DataStore, files, Firebase, analytics, or Crashlytics.

- Key: latitude/longitude rounded to three decimal places
- Value: resolved country/city/district candidate
- Fresh TTL: 24 hours
- Stale outage fallback: up to seven days
- Maximum entries: 32, least-recently-used eviction
- Invalidation: app-process termination, TTL expiry, or LRU eviction

A stale result is returned only after retryable network, 429, or 5xx failure. A successful response with no usable address does not silently reuse stale data.

## Retry and failure policy

- At most two total attempts per logical lookup
- Minimum interval between upstream request starts: 1,000 ms
- 429: honor numeric `Retry-After`, bounded to 1–60 seconds
- 5xx or I/O failure: one retry after 1,000 ms
- Other 4xx: no retry
- Cancellation: propagated immediately
- Offline/service failure: stale memory cache when eligible, otherwise manual-selection fallback

## Logging policy

Logs contain only:

- fixed endpoint host
- anonymous process-local request correlation ID
- attempt number
- HTTP status or outcome category

Coordinates, complete URLs, query parameters, response bodies, and raw network exception messages are not logged.

## Verification

The module test suite covers rounded request coordinates, identifying headers, global throttling, 429 handling, 5xx/I/O retry, fresh/stale cache behaviour, concurrent request coalescing, invalid-coordinate rejection, and response-body minimisation.

Policy reviewed against the official Nominatim usage policy on 20 July 2026.
