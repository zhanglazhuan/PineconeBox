# PineCone Log Ingestion API

> Backend implementation reference — implement in your language of choice.
> Base URL: `https://api.pineconeos.com/api/v1`

## Endpoints

### POST /logs/batch

Accepts batched log events from devices.

**Headers:**
- `Authorization: Bearer <device_token>` — JWT issued at device registration
- `Content-Type: application/json`
- `X-Device-ID: pinecone-abc12345`
- `X-Client-Version: v0.1.0`

**Request Body:**
```json
{
  "device_id": "pinecone-abc12345",
  "account_uuid": "550e8400-e29b-41d4-a716-446655440000",
  "app_version": "v0.1.0",
  "android_version": "14",
  "batch_seq": 42,
  "events": [
    { "ts": 1722000123456, "sid": "uuid", "t": "item_click", "itemId": 1001, "cat": "教育部", "tab": "网站", "pos": 3 },
    { "ts": 1722000123789, "sid": "uuid", "t": "tab_switch", "from": "网站", "to": "App", "dwell": 45000 }
  ]
}
```

**Constraints:**
- Max 500 events per batch
- Single event max 4 KB
- `batch_seq` is monotonically increasing per device

**Response 200:**
```json
{
  "received": 487,
  "duplicates": 0,
  "errors": [],
  "next_expected_seq": 43
}
```

**Error responses:**
- `400` — batch too large (>500), malformed JSON, missing required fields
- `401` — invalid/expired token
- `429` — rate limited (100 req/min/device), `Retry-After` header set

### POST /devices/register

Registers a device and returns a JWT token.

**Request:**
```json
{
  "device_id": "pinecone-abc12345",
  "account_uuid": "550e8400-...",
  "app_version": "v0.1.0"
}
```

**Response 200:**
```json
{
  "token": "eyJhbGciOi...",
  "expires_at": "2026-10-24T00:00:00Z"
}
```

### POST /devices/refresh

Refreshes an expiring token. Same request body, with current token in `Authorization` header. Same response as register.

### GET /logs/stats

Parental usage report.

**Query params:** `account_uuid` (required), `date` (YYYY-MM-DD, required)

**Response 200:**
```json
{
  "date": "2026-07-26",
  "total_screen_time_min": 135,
  "peak_hour": 20,
  "categories": [{"name": "纪录片", "clicks": 12, "duration_min": 45}],
  "top_domains": ["smartedu.cn", "bilibili.com"],
  "crash_count": 0,
  "sessions": [{"start": "2026-07-26T16:05:00Z", "duration_min": 90}]
}
```

### GET /logs/health

Health check. Returns `{"status": "ok"}` with 200.

## Server-Side Architecture

```
Nginx (TLS termination + rate limiting: 100 req/min/device)
  → App Server (validate JWT → write ClickHouse + S3)
  → ClickHouse (90-day hot storage, MergeTree partitioned by month)
  → S3 / minIO (cold backup, path: device_id/yyyy/mm/dd/)
  → Redis (dedup: SETEX log:dedup:<device_id>:<batch_seq> 604800 "1")
```

## ClickHouse Schema

```sql
CREATE TABLE log_events (
    ts              DateTime64(3),
    server_ts       DateTime DEFAULT now(),
    device_id       String,
    account_uuid    String,
    app_version     String,
    event_type      LowCardinality(String),
    session_id      String,
    batch_seq       UInt32,
    payload         String,
    cat_name        String DEFAULT '',
    tab_name        String DEFAULT '',
    crash_class     String DEFAULT ''
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(ts)
ORDER BY (device_id, ts, event_type)
TTL ts + INTERVAL 90 DAY;
```

## Event Type Reference

| t | Event | Key Fields |
|---|-------|------------|
| `item_click` | Content card clicked | itemId, cat, tab, pos |
| `tab_switch` | Tab switched | from, to, dwell |
| `sidebar_select` | Sidebar category selected | cat, group, depth |
| `search` | Search performed | results, tab |
| `app_launch` | External app launched | pkg, src |
| `web_browsing` | Web browsing ended | dur, domain |
| `session_summary` | Session ended | dur, tabs, clicks, apps, topCats, topDomains |
| `daily_summary` | Daily aggregate | date, screenMs, peakHr, catDist |
| `crash` | App crashed | clazz, trace, src, ver, android |
| `jank` | UI jank detected | dur, desc, stack |
| `guard` | Anti-addiction trigger | action, remainMin, credit |
| `system` | System event | event, meta |
| `network_error` | Network request failed | endpoint, code, dur, msg |
