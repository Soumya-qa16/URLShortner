# Unit Testing Summary — URL Shortener

## What was added

The project had **no test dependencies and no test code** (`src/test/java` was empty,
and `pom.xml` had no `spring-boot-starter-test`). This document now covers two rounds
of work on top of that:

**Round 1 — application-level testing and hardening** (`com.example.util`, `.service`,
`.controller`, `.dto`, `.exception`):
- `spring-boot-starter-test` (test scope) to `pom.xml` — JUnit 5, Mockito, AssertJ,
  Spring's MockMvc.
- A centralized `GlobalExceptionHandler` (`@RestControllerAdvice`) plus two
  domain-specific exceptions (`ShortUrlNotFoundException`, `AliasAlreadyExistsException`)
  and an `ErrorResponse` body shape — replacing the two different situations that
  previously both threw a generic `IllegalArgumentException`. See "Fixed" below.
- A fix for the custom-alias check-then-act race condition (also under "Fixed" below).
- 6 test classes, 40 test methods, covering every class in `com.example` (excluding
  `orchestration`) that contains logic.

**Round 2 — the agentic orchestration engine** (`com.example.orchestration`): a
generic, reusable dependency-graph execution engine (parallel execution + synchronization,
entry/exit gates, bounded retry/fallback/rollback, human approval checkpoints, policy
guardrails, audit-grade observability, dynamic re-planning), plus a concrete 7-stage
SDLC workflow demo built on it. 10 test classes (one per engine concern), 24 test
methods, each in its own package under `com.example.orchestration`.

**Total: 16 test classes, 64 test methods.**

Run them with:
```
mvn test
```

Run the orchestration demo (3 scenarios, prints full audit trail + metrics) with:
```
mvn compile exec:java -Dexec.mainClass=com.example.orchestration.demo.OrchestrationDemo
```

## Coverage by class — URL Shortener application

| Class | Test file | Approach | What's covered |
|---|---|---|---|
| `Base62` | `Base62Test` | Pure unit, no mocks | Zero, single-digit boundary (61→"Z"), base rollover (62→"10", 63→"11"), determinism, `Long.MAX_VALUE`, **negative input** |
| `UrlService` | `UrlServiceTest` | Mockito (`@Mock` repos + `RedisTemplate`/`ValueOperations`) | Custom alias happy path & collision (→`AliasAlreadyExistsException`), **concurrent-insert race → 409, not 500** (see "Fixed" below), blank-alias fallback, key generation from persisted ID, TTL-from-expiry caching, **already-expired expiry skips caching**, cache-hit vs cache-miss reads, **expired-cache-entry returns empty without re-caching**, analytics aggregation (incl. null user-agent → "Unknown"), not-found → `ShortUrlNotFoundException` |
| `AnalyticsService` | `AnalyticsServiceTest` | Mockito | Persists click with correct fields; handles null IP/user-agent |
| `UrlController` | `UrlControllerTest` | Standalone MockMvc (mocked services, real `@Valid`, real `GlobalExceptionHandler`) | 200 + correct JSON shape, 400 with structured field errors, 409 on alias conflict, 302 + `Location` header + click logged, 404 + click **not** logged, analytics 200 payload, **404 on unknown analytics key (fixed — see below)** |
| `GlobalExceptionHandler` | `GlobalExceptionHandlerTest` | Direct unit test, no Spring context | Each handler method's status code + body; explicitly asserts the 500 fallback **never leaks the original exception message** |
| `ShortenRequest` | `ShortenRequestValidationTest` | Direct `Validator`, no Spring context | Valid request, optional fields, blank/null/malformed URL each violate the expected constraint |

## Coverage by package — orchestration engine

| Package | Test class | What's covered |
|---|---|---|
| `orchestration.scheduling` | `OrchestrationSchedulingTest` | Dependency order, real parallel execution (proven via a `CountDownLatch`, not just "didn't error"), join-stage synchronization |
| `orchestration.gates` | `OrchestrationGatesTest` | Entry gate failure, exit gate failure — both treated as stage failure |
| `orchestration.retry` | `OrchestrationRetryTest` | Retryable failure recovers within budget; non-retryable failure never retries |
| `orchestration.fallback` | `OrchestrationFallbackTest` | Fallback agent invoked once retries are exhausted |
| `orchestration.rollback` | `OrchestrationRollbackTest` | Rollback group + downstream safe-stop when retry and fallback both fail |
| `orchestration.approval` | `OrchestrationApprovalTest` | Reject, grant, and the `AutoApprovingApprovalPort` demo stand-in |
| `orchestration.policy` | `OrchestrationPolicyTest` | Denial blocks the stage before the agent runs; denial can't be bypassed via retry/fallback; allow lets a stage proceed |
| `orchestration.replanning` | `OrchestrationReplanningTest` | Stale stage + its dependents reset and re-execute under a new plan revision; context outside the invalidated subgraph is preserved |
| `orchestration.metrics` | `OrchestrationMetricsTest` | Success rate on a mixed outcome, full-success case, non-negative latency |
| `orchestration.definition` | `WorkflowDefinitionValidationTest` | Cycle detection, unknown-dependency detection, duplicate stage id, transitive-dependents computation |

## Fixed: centralized exception handling

The original code had **no `@ExceptionHandler`/`@ControllerAdvice` at all**. Both
"custom alias already taken" and "short key not found" threw the same generic
`IllegalArgumentException`, and neither was caught anywhere — an unknown analytics key
surfaced as an opaque 500 instead of a 404.

This is now fixed with:
- `ShortUrlNotFoundException` → **404** (not-found is a different condition from...)
- `AliasAlreadyExistsException` → **409 Conflict** (...an alias collision, so they no
  longer share a status code just because they used to share an exception type)
- `MethodArgumentNotValidException` (from `@Valid`) → **400** with a structured
  `fieldErrors` map instead of Spring's bare default body
- Remaining `IllegalArgumentException` → **400** (defensive fallback for any other
  caller)
- Anything else unhandled → **500** with a generic message, deliberately **not**
  including the original exception's text, so internal details never leak to a client

All of this is exercised end-to-end through `UrlControllerTest` (via
`.setControllerAdvice(new GlobalExceptionHandler())`) and in isolation through
`GlobalExceptionHandlerTest`.

## Fixed: custom-alias check-then-act race condition

`existsByShortKey()` followed by `save()` in `shortenUrl()` had a race window: two
concurrent requests for the same custom alias could both pass the upfront check before
either had inserted a row. The unique DB constraint on `shortKey` would still reject the
second insert, but nothing caught that -- it surfaced as a raw `DataIntegrityViolationException`,
caught only by `GlobalExceptionHandler`'s generic 500 fallback instead of the intended
409 `AliasAlreadyExistsException` path.

`saveMapping()` now catches `DataIntegrityViolationException` around the insert and
re-throws it as `AliasAlreadyExistsException`, so a request that loses the race gets the
same clean 409 a request that failed the upfront check would have gotten. The upfront
`existsByShortKey()` check is kept as-is (it avoids a wasted DB round trip in the common,
non-racing case) -- the catch is the safety net for the window between check and insert,
not a replacement for the check. Covered by
`UrlServiceTest#shortenUrl_concurrentAliasInsertRace_mapsDbConstraintViolationToAliasAlreadyExists`,
which simulates the race by having the mocked repository's `save()` throw
`DataIntegrityViolationException` after `existsByShortKey()` already returned `false`.

## Risks and gaps still open

These aren't hypothetical — each is demonstrated by a passing test that pins down the
class's *current* behavior, so a future fix is a deliberate change, not a silent one.

1. **`Base62.encode()` has no input validation.** A negative input returns `""`
   instead of throwing. IDs from `GenerationType.IDENTITY` shouldn't go negative in
   practice, but nothing guards against it if the ID source ever changes (e.g. a
   distributed ID generator).

2. **Two-phase insert in `shortenUrl()` (auto-generated path).** The method saves a row
   with a placeholder `shortKey = "temp"` to obtain a DB-generated ID, then updates it
   with the real Base62 key. Since `shortKey` has a `unique = true` constraint, **two
   concurrent auto-generated requests would collide on `"temp"`** and one would fail
   with a database constraint violation instead of a clean error. This is a distinct
   risk from the now-fixed custom-alias race above -- the same
   `DataIntegrityViolationException` -> domain-exception pattern would close it too,
   but it's called out separately here rather than bundled into that fix, since the
   right domain exception/status for a `"temp"` collision (an internal implementation
   detail, not a user-facing alias) is a different judgment call: arguably a 503/retry
   rather than a 409. A UUID placeholder or a sequence-based key would sidestep the
   question entirely by removing the collision.

## Limitations of this test suite

- **No `@SpringBootTest`/integration tests.** Everything here is a true unit test with
  mocked collaborators — nothing exercises real Postgres/H2, real Redis, or Spring's
  actual dependency injection/`@Async` proxying. `AnalyticsService.logClickAsync()` in
  particular is tested for *what* it persists, not that it truly runs off the request
  thread — that needs an integration test with a real (or test) `TaskExecutor`.
- **Repositories aren't tested directly.** `UrlMappingRepository`/`UrlClickRepository`
  are Spring Data interfaces with no custom logic beyond derived query methods, so
  there's nothing meaningful to unit-test — a `@DataJpaTest` would be the right tool
  if you want to verify the derived queries against a real (H2) database.
- **Concurrency isn't fully covered.** Risk #2 above (the `"temp"` placeholder
  collision) is an architectural finding from reading the code, not something a
  mocked, single-threaded unit test can exercise. A load test or a targeted
  concurrent-integration test would be needed to actually observe it. The
  custom-alias race (previously the same category of risk) now has a real
  regression test since it's fixed in code, not just documented.
- **`application.properties` has `spring.data.redis.host=local host`** (a stray space)
  — this would fail Redis connectivity at runtime. Out of scope for unit tests (it's a
  config value, not code), but worth an immediate fix regardless.
