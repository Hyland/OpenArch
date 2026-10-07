# OpenArch Runtime Idempotency

Idempotency support for OpenArch operations using a key-value store backend.

## Purpose

Ensures that state-mutating operations are executed at most once, even when called multiple times with the same idempotency key. This is essential when operations are protected by retry patterns — idempotency guarantees that retries do not cause duplicate side effects.

## Features

- **Idempotent operation execution** - Cache results based on idempotency keys
- **Failure recording** - Cache and replay exceptions to maintain consistency
- **Configurable TTL** - Control how long results are cached (default: 24 hours)
- **KeyValueService backend** - Uses the existing OpenArch key-value store infrastructure

## Architecture

### Key Components

1. **IdempotencyEntry** - Wraps a cached outcome: pending (claimed), success or failure
2. **IdempotencyHandler** - Interface for idempotent operation execution
3. **KeyValueStoreIdempotencyHandler** - KV store-based implementation, also usable directly with
   a custom store name, namespace and TTL

### Dependency Flow

```
hxpr-commands → openarch-runtime-idempotency → hxpr-runtime-kv
hxpr-async-tasks-aws → openarch-runtime-idempotency → hxpr-runtime-kv
```

## Usage

### Basic Example

```java
// Shared default handler, lazily resolved against the default store, namespace and TTL
IdempotencyHandler handler = IdempotencyHandler.instance();

// Or, for a dedicated store / namespace / TTL
IdempotencyHandler handler = new KeyValueStoreIdempotencyHandler(
    keyValueService,
    "idempotency",
    "idempotency:commands:",
    24 * 60 * 60L
);

String key = clientKey; // Unique key sent by the client to identify the operation

// Execute operation idempotently
IdempotencyHandler.Outcome<String> outcome = handler.execute(key, () -> {
    // Operation that must be idempotent
    return performStateChange();
});
String result = outcome.value();
```

### Integration with Retry Patterns

When combined with retry mechanisms (from HXCS-8624):

```
Retry → Idempotency → Circuit Breaker
```

1. Retry detects transient failures and retries the operation
2. Idempotency ensures the same result is returned or exception is replayed
3. Circuit breaker protects against cascading failures

## Configuration

`IdempotencyHandler.instance()` uses the defaults declared on `IdempotencyHandler`
(`DEFAULT_STORE_NAME`, `DEFAULT_IDEMPOTENCY_NAMESPACE`, `DEFAULT_TTL_SECONDS`); a call site needing
other values constructs a `KeyValueStoreIdempotencyHandler` itself. There is no global feature toggle. The only externally
configurable property today is the tenant id used when building idempotency keys, in
`application.properties`:

```properties
openarch.idempotency.tenant-id=default
```

## Testing

Run unit tests:

```bash
mvn test -pl community/modules/runtime/openarch-runtime-idempotency
```

Tests verify:
- Result success/failure handling
- Cache hit/miss scenarios
- Exception replay behavior

## Implementation Notes

- **Plain Java construction** (not Spring beans) for OSGi compatibility; `IdempotencyHandler.instance()` resolves lazily so it works before the runtime owning `KeyValueService` has started
- **Thread-safe** via KeyValueService's underlying implementation
- **Failure is cached** - Exceptions are also recorded and replayed on duplicate requests
- **Serializable results** - Results must implement `Serializable` for KV store persistence

## See Also

- HXCS-8624: Resilience patterns (retry, circuit breaker)
- HXCS-8625: Pattern 2 Idempotency implementation
- `hxpr-runtime-kv`: KeyValue storage service
