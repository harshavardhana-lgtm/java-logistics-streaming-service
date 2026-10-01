# Logistics Stream Service (Java 21, Spring Boot 4.1.1)

A WebSocket backend that streams live shipment telemetry to connected clients.
Every 500 ms the service polls a source, diffs it against the previous state,
and pushes only what changed — plus enough context for a client to recover if it
missed something.

## Why diffs rather than snapshots

A full snapshot per tick is simple and wasteful: at 60 shipments and 2 ticks a
second it re-sends the entire dataset forever, mostly unchanged. Instead the
service computes a diff against the last known state and emits
`added` / `updated` / `removed`. A tick that changes nothing emits nothing at
all, which keeps idle traffic at zero.

The comparison is field-by-field rather than whole-record equality:

- strings are trimmed and compared normalised, so `" DELAYED"` and `"DELAYED"`
  are not a change
- coordinates round to two decimals before comparison, so GPS jitter does not
  generate phantom updates
- a record is only considered updated if a tracked field actually differs

Version numbers come from an `AtomicLong`, so every diff carries a strictly
increasing version a client can use to detect gaps.

## Handling slow clients

The failure mode this service is built around: a client that stops reading must
not block anyone else.

- **Bounded per-session outbox** (256 by default). Once full, further messages
  are dropped and counted instead of queued without limit. Memory stays bounded
  at `sessions x outbox_limit`.
- **Fixed writer pool** (8 threads). Writers drain outboxes rather than
  holding locks while sending.
- **Synchronized sends per session.** The peek-send-pop sequence is serialized
  per session, which is what guarantees a client sees each message exactly once
  and in order. Without this, two writers can read the same head message and
  send it twice.
- **Staleness eviction.** A session with queued data and no progress for 10
  seconds is dropped, cleaning up clients that vanish without closing.
- **Session cap.** Connections beyond the configured maximum are closed with
  `SERVICE_OVERLOAD` rather than accepted and starved.

## Client protocol

| Direction | Frame | Purpose |
| --------- | ----- | ------- |
| server → client | `{"type":"snapshot","version":N,"payload":{...}}` | full state on connect or resync |
| server → client | `{"type":"diff","version":N,"payload":{...}}` | incremental change |
| client → server | `{"type":"ping"}` | keepalive, answered with `{"type":"pong"}` |
| client → server | `{"type":"resync"}` | request a fresh full snapshot |
| client → server | `{"type":"stats"}` | connection and drop counters |

Inbound frames are parsed as JSON and dispatched on the `type` field. Earlier
versions of this handler matched on substrings like `"type":"ping"`, which
silently failed on any client that emitted a space after the colon — worth
avoiding.

## Verified behaviour

Against a running instance with a simulated source:

```
CONNECTED
SNAPSHOT version=55 count=40
DIFF v=56 +51 ~40 -0
DIFF v=57 +0  ~68 -23
DIFF v=58 +0  ~64 -4
DIFF v=59 +20 ~64 -0
RESULT snapshots=1 diffs=7 monotonic=True
RESYNC type=snapshot
PONG type=pong
```

Boot time ~2.9 s, 500 ms poll interval, and versions arrive strictly
increasing.

## Build and run

```bash
mvn package
java -jar target/logistics-stream-1.0.0.jar
mvn test
```

Useful overrides:

```bash
java -jar target/logistics-stream-1.0.0.jar \
  --server.port=8099 \
  --logistics.poll-interval-ms=200 \
  --logistics.outbox-limit=128 \
  --logistics.stale-after-ms=5000 \
  --logistics.max-sessions=500
```

Connect a client to `ws://localhost:8090/ws`.

## Configuration

| Property | Default | Meaning |
| -------- | ------- | ------- |
| `logistics.source` | `simulated` | data source; only the simulator ships here |
| `logistics.poll-interval-ms` | `500` | diff interval |
| `logistics.outbox-limit` | `256` | per-session queue ceiling |
| `logistics.stale-after-ms` | `10000` | eviction threshold |
| `logistics.max-sessions` | `2000` | connection cap |
| `logistics.writer-threads` | `8` | outbox drain pool size |

## Tests

`ShipmentDiffServiceTest` covers no-op ticks, additions, removals, status and
position changes, sub-centimetre coordinate noise, whitespace/null
normalisation, version monotonicity, and the empty-initial-state case.

## Layout

```
model/     Shipment, ShipmentDiff, ShipmentSnapshot, ShipmentDiffEvent
service/   ShipmentDiffService, ShipmentPoller, ShipmentSource, simulated source
ws/        ShipmentStreamHandler, ClientMessage
config/    WebSocketConfig
```

## Note on scope

The data source is a simulator so the service runs standalone. Pointing it at a
real feed means implementing `ShipmentSource`; the polling, diffing, and
delivery layers do not change. Two things a production version would still need
that this does not have: authentication and per-tenant authorization (the
WebSocket allows any origin), and metrics or traces beyond the counters exposed
in the `stats` frame. Dropping messages for a slow client is a deliberate
choice here; a deployment that cannot tolerate loss would want a durable replay
path instead, trading latency and storage for delivery guarantees.
