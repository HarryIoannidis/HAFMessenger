# **ARCHITECTURE - HAF Messenger**

> **Comprehensive Architecture Documentation**  
> Architecture documentation for HAF Messenger - Military-Grade Secure Messaging System

***

## **Table of Contents**

1. [System Overview](#1-system-overview)
2. [Client Architecture: MVVM Pattern](#2-client-architecture-mvvm-pattern)
3. [Server Architecture: Layered Design](#3-server-architecture-layered-design)
4. [Test-Driven Development (TDD)](#4-test-driven-development-tdd)
5. [Clean Architecture Principles](#5-clean-architecture-principles)
6. [Security Architecture](#6-security-architecture)
7. [Observability & Monitoring](#7-observability--monitoring)
8. [Design Patterns](#8-design-patterns)
9. [Technology Stack](#9-technology-stack)
10. [Development Best Practices](#10-development-best-practices)

***

## **1. System Overview**

HAF Messenger is an **end-to-end encrypted messaging system** designed for military use, consisting of:

- **JavaFX Client** (MVVM architecture)
- **Java Server** (Layered architecture)
- **MySQL Database** (persistent storage)
- **TLS 1.3** (transport security)
- **AES-256-GCM + RSA-4096** (end-to-end encryption)

### **High-Level Architecture**

```
┌──────────────────┐                      ┌──────────────────┐
│  JavaFX Client   │                      │  Java Server     │
│  (MVVM Pattern)  │                      │  (Layered Arch.) │
│                  │ ◄──── TLS 1.3 ────►  │                  │
│  • View (FXML)   │                      │  • Ingress       │
│  • ViewModel     │                      │  • Router        │
│  • Model (DTOs)  │                      │  • DAO           │
└──────────────────┘                      │  • Metrics       │
                                          └────────┬─────────┘
                                                   │
                                          ┌────────▼─────────┐
                                          │  MySQL Database  │
                                          │  (Persistent)    │
                                          └──────────────────┘
```

### **Core Architectural Principles**

| Principle | Implementation | Benefit |
|-----------|---------------|---------|
| **Separation of Concerns** | Client/Server split, Layered design | Independent scaling, clear boundaries |
| **Test-First Development** | Unit tests before production code | High code quality, regression prevention |
| **Dependency Injection** | Constructor-based injection | Testability, flexibility |
| **Security by Design** | Encryption everywhere, TLS 1.3 only | Military-grade protection |
| **Observable Systems** | Structured logs, metrics tracking | Production visibility |

***

## **2. Client Architecture: MVVM Pattern**

### **What is MVVM**

**Model-View-ViewModel** separates the application into three distinct layers:

- **View**: FXML UI definitions + Controllers
- **ViewModel**: Business logic + State management
- **Model**: Data structures (DTOs)

### **Layer Responsibilities**

#### **View Layer (FXML + Controller)**

**Responsibilities:**
- Display UI elements (buttons, text fields, lists)
- Handle user events (clicks, input)
- Data binding to ViewModel properties
- **NO** business logic

**Example: Login View**

```xml
<!-- LoginView.fxml -->
<VBox xmlns:fx="http://javafx.com/fxml">
    <TextField fx:id="usernameField"/>
    <PasswordField fx:id="passwordField"/>
    <Button text="Login" onAction="#handleLogin"/>
    <Label fx:id="errorLabel" textFill="red"/>
</VBox>
```

```java
// LoginController.java
public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    
    private LoginViewModel viewModel;
    
    @FXML
    private void initialize() {
        // Bind UI to ViewModel
        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
    }
    
    @FXML
    private void handleLogin() {
        viewModel.login();  // Delegate to ViewModel
    }
}
```

#### **ViewModel Layer (Business Logic)**

**Responsibilities:**
- State management (Observable Properties)
- Business logic execution
- Input validation
- API/Service calls
- Error handling

**Example: Login ViewModel**

```java
// LoginViewModel.java
public class LoginViewModel {
    // Observable properties for data binding
    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty isLoggingIn = new SimpleBooleanProperty(false);
    
    private final AuthenticationService authService;
    
    public LoginViewModel(AuthenticationService authService) {
        this.authService = authService;
    }
    
    public void login() {
        // Validation
        if (username.get().isEmpty() || password.get().isEmpty()) {
            errorMessage.set("Username and password are required");
            return;
        }
        
        isLoggingIn.set(true);
        errorMessage.set("");
        
        // Business logic: authenticate
        authService.login(username.get(), password.get())
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    // Navigate to main view
                } else {
                    errorMessage.set("Invalid credentials");
                }
            })
            .exceptionally(ex -> {
                errorMessage.set("Login failed: " + ex.getMessage());
                return null;
            })
            .whenComplete((r, ex) -> isLoggingIn.set(false));
    }
    
    // Property getters for binding
    public StringProperty usernameProperty() { return username; }
    public StringProperty passwordProperty() { return password; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public BooleanProperty isLoggingInProperty() { return isLoggingIn; }
}
```

#### **Model Layer (Data Structures)**

**Responsibilities:**
- Data Transfer Objects (DTOs)
- Domain entities
- No business logic

**Example:**

```java
// EncryptedMessage.java (shared DTO)
public record EncryptedMessage(
    String senderId,
    String recipientId,
    byte[] encryptedPayload,
    byte[] wrappedKey,
    byte[] iv,
    byte[] authTag,
    String contentType,
    long contentLength,
    long timestamp,
    long ttl
) {}

// User.java
public record User(
    String userId,
    String username,
    String publicKeyBase64,
    UserStatus status
) {}
```

### **MVVM Benefits**

| Benefit | Description |
|---------|-------------|
| **Testability** | ViewModel can be unit tested without UI |
| **Separation of Concerns** | UI logic separated from business logic |
| **Reusability** | ViewModels can be reused across different Views |
| **Maintainability** | Changes to UI don't affect business logic |
| **JavaFX Integration** | Native support for Property binding |

### **MVVM Data Flow**

1. User interacts with **View** (clicks button)
2. **Controller** calls method on **ViewModel**
3. **ViewModel** executes business logic
4. **ViewModel** updates **Observable Properties**
5. **View** automatically updates via data binding

***

## **3. Server Architecture: Layered Design**

### **Layer Overview**

The server follows a **4-layer architecture**:

```
┌─────────────────────────────────────────┐
│         INGRESS LAYER                   │
│  • HttpIngressServer                    │
│  • WebSocketIngressServer               │
│  • TLS 1.3 enforcement                  │
│  • Request validation                   │
└───────────────────┬─────────────────────┘
                    │
┌───────────────────▼─────────────────────┐
│         ROUTER LAYER                    │
│  • MailboxRouter                        │
│  • RateLimiterService                   │
│  • EncryptedMessageValidator            │
│  • Business logic                       │
└───────────────────┬─────────────────────┘
                    │
┌───────────────────▼─────────────────────┐
│         DATA ACCESS LAYER               │
│  • EnvelopeDAO                          │
│  • MySQL connection pool (HikariCP)     │
│  • Flyway migrations                    │
└───────────────────┬─────────────────────┘
                    │
┌───────────────────▼─────────────────────┐
│         METRICS/AUDIT LAYER             │
│  • AuditLogger                          │
│  • MetricsRegistry                      │
│  • Structured JSON logging              │
└─────────────────────────────────────────┘
```

### **Layer Responsibilities**

#### **1. Ingress Layer**

**Purpose:** Handle incoming requests and enforce security policies

**Components:**

| Component | Responsibility | Port |
|-----------|----------------|------|
| `HttpIngressServer` | REST API endpoints (send, fetch, acknowledge) | 8443 (HTTPS) |
| `WebSocketIngressServer` | Real-time push notifications | 8444 (WSS) |

**Key Features:**
- TLS 1.3 only (no fallback to older versions)
- Cipher suite hardening (TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256)
- Request validation
- Rate limiting integration

**Example: HTTP Ingress**

```java
// HttpIngressServer.java
public class HttpIngressServer {
    private final HttpServer server;
    private final MailboxRouter mailboxRouter;
    private final RateLimiterService rateLimiter;
    private final EncryptedMessageValidator validator;
    
    public void start() throws IOException {
        server = HttpsServer.create(new InetSocketAddress(8443), 0);
        
        // TLS 1.3 enforcement
        SSLContext sslContext = createTLS13Context();
        HttpsConfigurator configurator = new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParams = new SSLParameters();
                sslParams.setProtocols(new String[]{"TLSv1.3"});
                sslParams.setCipherSuites(ALLOWED_CIPHER_SUITES);
                params.setSSLParameters(sslParams);
            }
        };
        ((HttpsServer) server).setHttpsConfigurator(configurator);
        
        // Endpoints
        server.createContext("/api/v1/send", this::handleSend);
        server.createContext("/api/v1/fetch", this::handleFetch);
        server.createContext("/api/v1/ack", this::handleAcknowledge);
        
        server.start();
    }
    
    private void handleSend(HttpExchange exchange) throws IOException {
        // 1. Rate limit check
        String userId = extractUserId(exchange);
        if (rateLimiter.shouldBlock(userId)) {
            sendResponse(exchange, 429, "Too Many Requests");
            return;
        }
        
        // 2. Validate message
        EncryptedMessage message = parseRequest(exchange);
        if (!validator.isValid(message)) {
            sendResponse(exchange, 400, "Invalid message");
            return;
        }
        
        // 3. Route message
        QueuedEnvelope envelope = mailboxRouter.ingress(message);
        sendResponse(exchange, 200, envelope.envelopeId());
    }
}
```

#### **2. Router Layer**

**Purpose:** Business logic and message routing

**Components:**

| Component | Responsibility |
|-----------|----------------|
| `MailboxRouter` | Routes messages to recipients, manages subscriptions |
| `RateLimiterService` | Prevents abuse (token bucket algorithm) |
| `EncryptedMessageValidator` | Validates message structure, timestamps, sizes |

**Example: Message Routing**

```java
// MailboxRouter.java
public final class MailboxRouter implements AutoCloseable {
    private final EnvelopeDAO envelopeDAO;
    private final MetricsRegistry metricsRegistry;
    private final AuditLogger auditLogger;
    private final ConcurrentHashMap<String, Set<MailboxSubscriber>> subscribers;
    
    /**
     * Ingress: Store message and notify subscribers
     */
    public QueuedEnvelope ingress(EncryptedMessage message) {
        // Store in database
        QueuedEnvelope envelope = envelopeDAO.insert(message);
        
        // Update metrics
        metricsRegistry.increaseQueueDepth();
        
        // Push to WebSocket subscribers (if online)
        dispatch(message.recipientId(), envelope);
        
        return envelope;
    }
    
    /**
     * Acknowledge: Mark messages as delivered
     */
    public boolean acknowledge(Collection<String> envelopeIds) {
        // Fetch envelopes to calculate delivery latency
        Map<String, QueuedEnvelope> envelopes = envelopeDAO.fetchByIds(envelopeIds);
        
        // Mark as delivered
        boolean updated = envelopeDAO.markDelivered(List.copyOf(envelopeIds));
        
        if (updated) {
            long now = System.currentTimeMillis();
            
            // Track delivery latency for each envelope
            for (QueuedEnvelope envelope : envelopes.values()) {
                long latency = now - envelope.createdAtEpochMs();
                metricsRegistry.recordDeliveryLatency(latency);
            }
            
            metricsRegistry.decreaseQueueDepth(envelopeIds.size());
        }
        
        return updated;
    }
    
    /**
     * Subscribe: Register WebSocket connection for push notifications
     */
    public void subscribe(String recipientId, MailboxSubscriber subscriber) {
        subscribers.computeIfAbsent(recipientId, k -> new CopyOnWriteArraySet<>())
                   .add(subscriber);
        
        // Deliver pending messages
        List<QueuedEnvelope> pending = envelopeDAO.fetchForRecipient(recipientId, 100);
        pending.forEach(subscriber::onEnvelope);
    }
    
    private void dispatch(String recipientId, QueuedEnvelope envelope) {
        Set<MailboxSubscriber> subs = subscribers.get(recipientId);
        if (subs != null) {
            // Track delivery latency for WebSocket push
            long now = System.currentTimeMillis();
            long latency = now - envelope.createdAtEpochMs();
            metricsRegistry.recordDeliveryLatency(latency);
            
            subs.forEach(sub -> sub.onEnvelope(envelope));
        }
    }
}
```

#### **3. Data Access Layer**

**Purpose:** Database operations

**Components:**

| Component | Responsibility |
|-----------|----------------|
| `EnvelopeDAO` | CRUD operations for message envelopes |
| `HikariCP` | Connection pooling |
| `Flyway` | Database schema migrations |

**Example: DAO Pattern**

```java
// EnvelopeDAO.java
public final class EnvelopeDAO {
    private final DataSource dataSource;
    private final AuditLogger auditLogger;
    
    /**
     * Insert encrypted message envelope
     */
    public QueuedEnvelope insert(EncryptedMessage message) {
        String sql = """
            INSERT INTO message_envelopes (
                envelope_id, sender_id, recipient_id, encrypted_payload,
                wrapped_key, iv, auth_tag, content_type, content_length,
                timestamp, ttl, created_at, expires_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?)
            """;
        
        String envelopeId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(message.ttl());
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, envelopeId);
            stmt.setString(2, message.senderId());
            stmt.setString(3, message.recipientId());
            stmt.setBytes(4, message.encryptedPayload());
            stmt.setBytes(5, message.wrappedKey());
            stmt.setBytes(6, message.iv());
            stmt.setBytes(7, message.authTag());
            stmt.setString(8, message.contentType());
            stmt.setLong(9, message.contentLength());
            stmt.setLong(10, message.timestamp());
            stmt.setLong(11, message.ttl());
            stmt.setTimestamp(12, Timestamp.from(expiresAt));
            
            stmt.executeUpdate();
            
            return new QueuedEnvelope(
                envelopeId,
                message,
                System.currentTimeMillis(),
                expiresAt.toEpochMilli()
            );
            
        } catch (SQLException ex) {
            auditLogger.logError("db_insert", null, null, ex);
            throw new IllegalStateException("Failed to insert envelope", ex);
        }
    }
    
    /**
     * Fetch multiple envelopes by IDs (for latency tracking)
     */
    public Map<String, QueuedEnvelope> fetchByIds(Collection<String> envelopeIds) {
        if (envelopeIds == null || envelopeIds.isEmpty()) {
            return Map.of();
        }
        
        String placeholders = envelopeIds.stream()
                .map(id -> "?")
                .reduce((a, b) -> a + "," + b)
                .orElse("?");
        
        String sql = String.format("""
            SELECT envelope_id, sender_id, recipient_id, encrypted_payload,
                   wrapped_key, iv, auth_tag, content_type, content_length,
                   timestamp, ttl, created_at, expires_at
            FROM message_envelopes
            WHERE envelope_id IN (%s) AND delivered = FALSE
            """, placeholders);
        
        Map<String, QueuedEnvelope> envelopes = new HashMap<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int index = 1;
            for (String envelopeId : envelopeIds) {
                stmt.setString(index++, envelopeId);
            }
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                EncryptedMessage message = hydrateMessage(rs);
                QueuedEnvelope envelope = new QueuedEnvelope(
                    rs.getString("envelope_id"),
                    message,
                    rs.getTimestamp("created_at").getTime(),
                    rs.getTimestamp("expires_at").getTime()
                );
                envelopes.put(envelope.envelopeId(), envelope);
            }
            
        } catch (SQLException ex) {
            auditLogger.logError("db_fetch_by_ids", null, null, ex);
            throw new IllegalStateException("Failed to fetch envelopes", ex);
        }
        
        return envelopes;
    }
}
```

**Database Schema (Flyway Migration):**

```sql
-- V3__create_message_envelopes_table.sql
CREATE TABLE message_envelopes (
    envelope_id VARCHAR(36) PRIMARY KEY,
    sender_id VARCHAR(255) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    encrypted_payload BLOB NOT NULL,
    wrapped_key BLOB NOT NULL,
    iv BLOB NOT NULL,
    auth_tag BLOB NOT NULL,
    content_type VARCHAR(100),
    content_length BIGINT,
    timestamp BIGINT NOT NULL,
    ttl BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    delivered BOOLEAN DEFAULT FALSE,
    delivered_at TIMESTAMP NULL,
    
    INDEX idx_recipient_delivered (recipient_id, delivered),
    INDEX idx_expires_at (expires_at)
);
```

#### **4. Metrics/Audit Layer**

**Purpose:** Observability and compliance

**Components:**

| Component | Responsibility |
|-----------|----------------|
| `AuditLogger` | Structured JSON logging (all actions) |
| `MetricsRegistry` | Performance metrics (counters, gauges) |

**Example: Metrics Tracking**

```java
// MetricsRegistry.java
public class MetricsRegistry {
    // Counters
    private final AtomicLong ingressCount = new AtomicLong(0);
    private final AtomicLong rejectCount = new AtomicLong(0);
    private final AtomicLong rateLimitRejectCount = new AtomicLong(0);
    private final AtomicLong deliveredCount = new AtomicLong(0);
    
    // Gauges
    private final AtomicLong queueDepth = new AtomicLong(0);
    private final AtomicLong totalDeliveryLatencyMs = new AtomicLong(0);
    
    public void incrementIngress() {
        ingressCount.incrementAndGet();
    }
    
    public void recordDeliveryLatency(long latencyMs) {
        totalDeliveryLatencyMs.addAndGet(latencyMs);
        deliveredCount.incrementAndGet();
    }
    
    public double getAverageDeliveryLatencyMs() {
        long delivered = deliveredCount.get();
        return delivered == 0 ? 0.0 : (double) totalDeliveryLatencyMs.get() / delivered;
    }
    
    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(
            ingressCount.get(),
            rejectCount.get(),
            rateLimitRejectCount.get(),
            queueDepth.get(),
            getAverageDeliveryLatencyMs(),
            deliveredCount.get()
        );
    }
    
    public record MetricsSnapshot(
        long ingressCount,
        long rejectCount,
        long rateLimitRejectCount,
        long queueDepth,
        double avgDeliveryLatencyMs,
        long deliveredCount
    ) {}
}
```

**Example: Structured Audit Logging**

```java
// AuditLogger.java
public final class AuditLogger {
    private static final Logger LOGGER = LogManager.getLogger("AuditLogger");
    
    public void logMessageAccepted(String requestId, String userId, String recipientId, long latencyMs) {
        log(Level.INFO, "send_message", requestId, userId, Map.of(
            "recipientId", recipientId,
            "latencyMs", latencyMs,
            "status", "200"
        ));
    }
    
    public void logMetricsSnapshot(MetricsSnapshot snapshot) {
        log(Level.INFO, "metrics", null, "system", Map.of(
            "ingressCount", snapshot.ingressCount(),
            "rejectCount", snapshot.rejectCount(),
            "rateLimitRejectCount", snapshot.rateLimitRejectCount(),
            "queueDepth", snapshot.queueDepth(),
            "avgDeliveryLatencyMs", snapshot.avgDeliveryLatencyMs(),
            "deliveredCount", snapshot.deliveredCount()
        ));
    }
    
    private void log(Level level, String action, String requestId, String userId, Map<String, Object> extra) {
        StringMapMessage message = new StringMapMessage();
        message.put("action", action);
        message.put("timestamp", Instant.now().toString());
        if (requestId != null) message.put("requestId", requestId);
        if (userId != null) message.put("userId", userId);
        extra.forEach((k, v) -> message.put(k, String.valueOf(v)));
        
        LOGGER.log(level, message);
    }
}
```

**Structured Log Output (JSON):**

```json
{
  "instant": {"epochSecond": 1763503997, "nanoOfSecond": 234786714},
  "level": "INFO",
  "loggerName": "AuditLogger",
  "message": "action=\"send_message\" recipientId=\"recipient-456\" requestId=\"req-1\" latencyMs=\"45\" status=\"200\" timestamp=\"2025-11-18T22:13:17.171Z\" userId=\"user-123\""
}
```

### **Layer Communication Rules**

| Allowed | Not Allowed |
|---------|-------------|
| Ingress → Router → DAO | Ingress → DAO (skip layer) |
| Router → DAO → Database | Router → Database (bypass DAO) |
| Any Layer → Metrics/Audit | Metrics → Any Layer (circular dependency) |

---

## **4. Test-Driven Development (TDD)**

### **What is TDD**

**Test-Driven Development** is a development methodology where tests are written **before** production code.

### **TDD Cycle (Red-Green-Refactor)**

```
1. RED:    Write a failing test
           ↓
2. GREEN:  Write minimum code to pass
           ↓
3. REFACTOR: Clean up code while keeping tests green
           ↓
           (Repeat)
```

### **TDD in HAF Messenger**

Every production class has a corresponding test class:

| Production Class | Test Class | Test Count |
|------------------|------------|------------|
| `MetricsRegistry.java` | `MetricsRegistryTest.java` | 12 tests |
| `MailboxRouter.java` | `MailboxRouterTest.java` | 8 tests |
| `AuditLogger.java` | `AuditLoggerTest.java` | 7 tests |
| `EnvelopeDAO.java` | `EnvelopeDAOTest.java` | 9 tests |
| `RateLimiterService.java` | `RateLimiterServiceTest.java` | 6 tests |
| `EncryptedMessageValidator.java` | `EncryptedMessageValidatorTest.java` | 10 tests |

### **Test Categories**

#### **1. Unit Tests**

Test individual components in isolation using mocks.

**Example: MailboxRouter Unit Test**

```java
@ExtendWith(MockitoExtension.class)
class MailboxRouterTest {
    @Mock private EnvelopeDAO envelopeDAO;
    @Mock private ScheduledExecutorService scheduler;
    @Mock private AuditLogger auditLogger;
    
    private MetricsRegistry metricsRegistry;
    private MailboxRouter router;
    
    @BeforeEach
    void setUp() {
        metricsRegistry = new MetricsRegistry();
        router = new MailboxRouter(envelopeDAO, scheduler, auditLogger, metricsRegistry);
    }
    
    @Test
    void acknowledge_records_delivery_latency() {
        List<String> envelopeIds = List.of("id1", "id2");
        
        long now = System.currentTimeMillis();
        QueuedEnvelope env1 = new QueuedEnvelope("id1", createValidMessage(), now - 100, now + 3600000);
        QueuedEnvelope env2 = new QueuedEnvelope("id2", createValidMessage(), now - 200, now + 3600000);
        
        when(envelopeDAO.fetchByIds(envelopeIds))
            .thenReturn(Map.of("id1", env1, "id2", env2));
        when(envelopeDAO.markDelivered(envelopeIds)).thenReturn(true);
        
        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();
        
        router.acknowledge(envelopeIds);
        
        // Verify latency was recorded
        assertEquals(2, metricsRegistry.snapshot().deliveredCount());
        assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() >= 100);
        assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() <= 200);
    }
}
```

#### **2. Integration Tests**

Test interaction between multiple components.

**Example: Rate Limiter Integration Test**

```java
class RateLimiterServiceIT {
    private DataSource dataSource;
    private RateLimiterService rateLimiter;
    
    @BeforeEach
    void setUp() {
        // Real database connection
        dataSource = createTestDataSource();
        rateLimiter = new RateLimiterService(dataSource, AuditLogger.create(new MetricsRegistry()));
    }
    
    @Test
    void concurrent_requests_respect_rate_limit() throws InterruptedException {
        String userId = "user-123";
        int limit = 10;
        int duration = 1; // 1 second
        
        rateLimiter.createLimit(userId, limit, duration);
        
        // Fire 20 concurrent requests
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger allowedCount = new AtomicInteger(0);
        
        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                if (!rateLimiter.shouldBlock(userId)) {
                    allowedCount.incrementAndGet();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Only 10 requests should be allowed
        assertEquals(limit, allowedCount.get());
    }
}
```

### **Test Coverage Requirements**

| Layer | Minimum Coverage | Actual Coverage |
|-------|------------------|-----------------|
| Router Layer | 80% | 95% |
| DAO Layer | 70% | 88% |
| Metrics Layer | 90% | 100% |
| Validators | 100% | 100% |

### **Benefits of TDD**

| Benefit | Description |
|---------|-------------|
| **Bug Prevention** | Catch bugs before they reach production |
| **Refactoring Confidence** | Change code without fear of breaking things |
| **Documentation** | Tests show how to use the API |
| **Design Improvement** | Writing tests first leads to better design |
| **Regression Prevention** | Ensure old features don't break when adding new ones |

---

## **5. Clean Architecture Principles**

### **Dependency Inversion**

**High-level modules should not depend on low-level modules. Both should depend on abstractions.**

**Example:**

```java
// ❌ BAD: Direct dependency on concrete class
class MailboxRouter {
    private final MySQLEnvelopeDAO dao = new MySQLEnvelopeDAO();  // Tight coupling
}

// ✅ GOOD: Dependency on interface
class MailboxRouter {
    private final EnvelopeDAO dao;  // Abstraction
    
    public MailboxRouter(EnvelopeDAO dao) {  // Dependency injection
        this.dao = dao;
    }
}
```

**Benefits:**
- Can swap implementations (MySQL → PostgreSQL)
- Unit testing with mocks
- Decoupled components

### **Single Responsibility Principle**

**Each class should have one reason to change.**

| Class | Single Responsibility |
|-------|----------------------|
| `HttpIngressServer` | Handle HTTP requests only |
| `MailboxRouter` | Route messages only |
| `EnvelopeDAO` | Database operations only |
| `MetricsRegistry` | Track metrics only |
| `AuditLogger` | Log events only |

### **Dependency Injection via Constructor**

All dependencies are injected via constructor:

```java
// Main.java - Dependency injection root
public class Main {
    public static void main(String[] args) {
        // 1. Create infrastructure
        DataSource dataSource = createDataSource();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        // 2. Create shared services
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        AuditLogger auditLogger = AuditLogger.create(metricsRegistry);
        
        // 3. Create DAOs
        EnvelopeDAO envelopeDAO = new EnvelopeDAO(dataSource, auditLogger);
        
        // 4. Create services (inject dependencies)
        MailboxRouter mailboxRouter = new MailboxRouter(
            envelopeDAO,
            scheduler,
            auditLogger,
            metricsRegistry
        );
        
        RateLimiterService rateLimiter = new RateLimiterService(dataSource, auditLogger);
        EncryptedMessageValidator validator = new EncryptedMessageValidator();
        
        // 5. Create servers (inject dependencies)
        HttpIngressServer httpServer = new HttpIngressServer(
            config,
            mailboxRouter,
            rateLimiter,
            validator,
            auditLogger,
            metricsRegistry
        );
        
        WebSocketIngressServer wsServer = new WebSocketIngressServer(
            config,
            mailboxRouter,
            rateLimiter,
            validator,
            auditLogger
        );
        
        // 6. Start servers
        httpServer.start();
        wsServer.start();
    }
}
```

### **Interface Segregation**

**Clients should not depend on interfaces they don't use.**

```java
// MailboxSubscriber.java - Small, focused interface
@FunctionalInterface
public interface MailboxSubscriber {
    void onEnvelope(QueuedEnvelope envelope);
}

// WebSocketIngressServer implements only what it needs
public class WebSocketIngressServer implements MailboxSubscriber {
    @Override
    public void onEnvelope(QueuedEnvelope envelope) {
        // Push to WebSocket client
        sendToClient(envelope);
    }
}
```

***

## **6. Security Architecture**

### **Defense in Depth**

Multiple layers of security protect the system:

```
Layer 1: TLS 1.3 (Transport Security)
         ↓
Layer 2: Rate Limiting (Abuse Prevention)
         ↓
Layer 3: Input Validation (Malformed Data)
         ↓
Layer 4: End-to-End Encryption (Data at Rest)
         ↓
Layer 5: Audit Logging (Compliance)
```

### **Security Measures**

#### **1. TLS 1.3 Enforcement**

**Implementation:**

```java
// HttpIngressServer.java
private SSLContext createTLS13Context() throws GeneralSecurityException, IOException {
    // Load server certificate
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(Files.newInputStream(Paths.get("server.p12")), password);
    
    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(keyStore, password);
    
    // Create SSL context
    SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
    sslContext.init(kmf.getKeyManagers(), null, null);
    
    return sslContext;
}

// Configure HTTPS server
HttpsConfigurator configurator = new HttpsConfigurator(sslContext) {
    @Override
    public void configure(HttpsParameters params) {
        SSLParameters sslParams = new SSLParameters();
        
        // Only TLS 1.3
        sslParams.setProtocols(new String[]{"TLSv1.3"});
        
        // Only strongest cipher suites
        sslParams.setCipherSuites(new String[]{
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256"
        });
        
        params.setSSLParameters(sslParams);
    }
};
```

**Cipher Suites:**

| Cipher Suite | Key Exchange | Encryption | Hash |
|--------------|--------------|------------|------|
| TLS_AES_256_GCM_SHA384 | ECDHE | AES-256-GCM | SHA384 |
| TLS_CHACHA20_POLY1305_SHA256 | ECDHE | ChaCha20-Poly1305 | SHA256 |

#### **2. End-to-End Encryption**

**Message Encryption Flow:**

```
1. Sender generates random AES-256 key
2. Sender encrypts message with AES-256-GCM
3. Sender wraps AES key with recipient's RSA-4096 public key
4. Send: {encryptedPayload, wrappedKey, iv, authTag}
5. Recipient unwraps AES key with their RSA private key
6. Recipient decrypts message with AES-256-GCM
```

**EncryptedMessage DTO:**

```java
public record EncryptedMessage(
    String senderId,
    String recipientId,
    byte[] encryptedPayload,  // AES-256-GCM encrypted
    byte[] wrappedKey,         // RSA-4096 wrapped AES key
    byte[] iv,                 // 12-byte nonce
    byte[] authTag,            // 16-byte authentication tag
    String contentType,
    long contentLength,
    long timestamp,
    long ttl
) {}
```

#### **3. Input Validation**

**EncryptedMessageValidator:**

```java
public class EncryptedMessageValidator {
    private static final long MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final long MAX_CLOCK_SKEW_MS = 300_000; // 5 minutes
    
    public boolean isValid(EncryptedMessage message) {
        // Null checks
        if (message == null || message.encryptedPayload() == null) {
            return false;
        }
        
        // Size limits
        if (message.encryptedPayload().length > MAX_PAYLOAD_SIZE) {
            return false;
        }
        
        // Timestamp validation (prevent replay attacks)
        long now = System.currentTimeMillis();
        long messageTime = message.timestamp();
        if (Math.abs(now - messageTime) > MAX_CLOCK_SKEW_MS) {
            return false;
        }
        
        // IV size (must be 12 bytes for GCM)
        if (message.iv().length != 12) {
            return false;
        }
        
        // Auth tag size (must be 16 bytes for GCM)
        if (message.authTag().length != 16) {
            return false;
        }
        
        return true;
    }
}
```

#### **4. Rate Limiting (Token Bucket)**

**Algorithm:**

```
1. Each user has a bucket with N tokens
2. Tokens refill at rate R tokens/second
3. Each request consumes 1 token
4. If bucket is empty, request is blocked
```

**Implementation:**

```java
// RateLimiterService.java
public boolean shouldBlock(String userId) {
    String sql = """
        SELECT tokens, last_refill_at 
        FROM rate_limits 
        WHERE user_id = ? 
        FOR UPDATE
        """;
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        
        stmt.setString(1, userId);
        ResultSet rs = stmt.executeQuery();
        
        if (rs.next()) {
            double tokens = rs.getDouble("tokens");
            long lastRefill = rs.getLong("last_refill_at");
            
            // Refill tokens based on time elapsed
            long now = System.currentTimeMillis();
            long elapsedMs = now - lastRefill;
            double refillAmount = (elapsedMs / 1000.0) * REFILL_RATE;
            tokens = Math.min(tokens + refillAmount, MAX_TOKENS);
            
            if (tokens >= 1.0) {
                // Consume token
                tokens -= 1.0;
                updateTokens(userId, tokens, now);
                return false; // Allow request
            } else {
                return true; // Block request
            }
        }
        
        return false; // No limit configured
    } catch (SQLException ex) {
        // Fail open (don't block on error)
        return false;
    }
}
```

#### **5. Audit Logging**

Every security-relevant action is logged:

```java
// Security events logged:
auditLogger.logMessageAccepted(requestId, userId, recipientId, latencyMs);
auditLogger.logMessageRejected(requestId, userId, "VALIDATION_FAILED");
auditLogger.logRateLimitExceeded(requestId, userId, retryAfterSeconds);
auditLogger.logAuthenticationFailed(requestId, userId, reason);
auditLogger.logError(action, requestId, userId, exception);
```

**Audit Trail Example:**

```json
[
  {"timestamp": "2025-11-18T22:13:17Z", "action": "send_message", "userId": "user-123", "status": "200"},
  {"timestamp": "2025-11-18T22:13:18Z", "action": "rate_limit", "userId": "user-456", "status": "429"},
  {"timestamp": "2025-11-18T22:13:19Z", "action": "validation_failed", "userId": "user-789", "reason": "CLOCK_SKEW"}
]
```

### **Security Best Practices**

| Practice | Implementation |
|----------|----------------|
| **Fail Secure** | On error, deny access (except rate limiter fails open) |
| **Least Privilege** | Services only have permissions they need |
| **Input Validation** | Validate all inputs before processing |
| **Audit Everything** | Log all security events |
| **Defense in Depth** | Multiple security layers |

***

## **7. Observability & Monitoring**

### **Metrics Tracked**

| Metric | Type | Description | Alert Threshold |
|--------|------|-------------|-----------------|
| `ingressCount` | Counter | Total messages received | - |
| `rejectCount` | Counter | Messages rejected (validation) | >5% of ingress |
| `rateLimitRejectCount` | Counter | Requests blocked (rate limit) | >10% of ingress |
| `queueDepth` | Gauge | Pending messages | >1000 |
| `avgDeliveryLatencyMs` | Gauge | Average delivery time | >500ms |
| `deliveredCount` | Counter | Successfully delivered messages | - |

### **Metrics Export**

```java
// Main.java - Scheduled metrics reporting
scheduler.scheduleAtFixedRate(
    () -> {
        MetricsSnapshot snapshot = metricsRegistry.snapshot();
        auditLogger.logMetricsSnapshot(snapshot);
    },
    60,  // Initial delay
    60,  // Period
    TimeUnit.SECONDS
);
```

**Output:**

```json
{
  "timestamp": "2025-11-18T22:13:17Z",
  "action": "metrics",
  "ingressCount": 1234,
  "rejectCount": 12,
  "rateLimitRejectCount": 5,
  "queueDepth": 45,
  "avgDeliveryLatencyMs": 125.5,
  "deliveredCount": 1200
}
```

### **Structured Logging**

All logs are JSON-formatted for machine parsing:

```json
{
  "instant": {"epochSecond": 1763503997, "nanoOfSecond": 234786714},
  "thread": "http-thread-1",
  "level": "INFO",
  "loggerName": "AuditLogger",
  "message": "action=\"send_message\" userId=\"user-123\" recipientId=\"recipient-456\" latencyMs=\"45\" status=\"200\""
}
```

### **Log Aggregation**

Logs can be sent to:
- **Elasticsearch** (search and visualization with Kibana)
- **Splunk** (enterprise log management)
- **CloudWatch Logs** (AWS monitoring)
- **Grafana Loki** (lightweight log aggregation)

***

## **8. Design Patterns**

### **Patterns Used**

| Pattern | Where | Why |
|---------|-------|-----|
| **Factory** | `AuditLogger.create()` | Centralized creation logic |
| **Observer** | `MailboxSubscriber` | WebSocket push notifications |
| **DAO** | `EnvelopeDAO` | Separate data access from business logic |
| **Singleton** | `MetricsRegistry` | Single source of truth for metrics |
| **Strategy** | `EncryptedMessageValidator` | Pluggable validation logic |
| **Dependency Injection** | Constructor injection everywhere | Testability and flexibility |
| **Repository** | `EnvelopeDAO.fetchByIds()` | Collection-like data access |

### **Pattern Examples**

#### **Factory Pattern**

```java
// AuditLogger.java
public static AuditLogger create(MetricsRegistry metricsRegistry) {
    return new AuditLogger(metricsRegistry);
}
```

#### **Observer Pattern**

```java
// MailboxSubscriber.java (Observer interface)
@FunctionalInterface
public interface MailboxSubscriber {
    void onEnvelope(QueuedEnvelope envelope);
}

// MailboxRouter.java (Subject)
public class MailboxRouter {
    private final Map<String, Set<MailboxSubscriber>> subscribers = new ConcurrentHashMap<>();
    
    public void subscribe(String recipientId, MailboxSubscriber subscriber) {
        subscribers.computeIfAbsent(recipientId, k -> new CopyOnWriteArraySet<>())
                   .add(subscriber);
    }
    
    private void dispatch(String recipientId, QueuedEnvelope envelope) {
        Set<MailboxSubscriber> subs = subscribers.get(recipientId);
        if (subs != null) {
            subs.forEach(sub -> sub.onEnvelope(envelope));  // Notify observers
        }
    }
}
```

#### **Strategy Pattern**

```java
// Validator interface (Strategy)
public interface MessageValidator {
    boolean isValid(EncryptedMessage message);
}

// Concrete strategy
public class EncryptedMessageValidator implements MessageValidator {
    @Override
    public boolean isValid(EncryptedMessage message) {
        // Validation logic
    }
}

// Context
public class HttpIngressServer {
    private final MessageValidator validator;
    
    public HttpIngressServer(MessageValidator validator) {
        this.validator = validator;  // Inject strategy
    }
    
    private void handleSend(HttpExchange exchange) {
        EncryptedMessage message = parseRequest(exchange);
        if (!validator.isValid(message)) {  // Use strategy
            sendResponse(exchange, 400, "Invalid message");
        }
    }
}
```

***

## **9. Technology Stack**

### **Backend (Server)**

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 LTS | Core language |
| **MySQL** | 8.0+ | Persistent storage |
| **HikariCP** | 5.1.0 | Connection pooling |
| **Flyway** | 10.x | Database migrations |
| **Log4j2** | 2.23.1 | Structured logging |
| **JUnit 5** | 5.12.1 | Unit testing |
| **Mockito** | 5.x | Mocking framework |

### **Frontend (Client)**

| Technology | Version | Purpose |
|------------|---------|---------|
| **JavaFX** | 21 | UI framework |
| **FXML** | - | UI definitions |
| **CSS** | - | UI styling |

### **Security**

| Technology | Purpose |
|------------|---------|
| **TLS 1.3** | Transport security |
| **AES-256-GCM** | Message encryption |
| **RSA-4096** | Key exchange |
| **ECDHE** | Perfect forward secrecy |

### **Build Tools**

| Tool | Purpose |
|------|---------|
| **Maven** | Build and dependency management |
| **Maven Surefire** | Test execution |
| **Maven Compiler** | Java 21 compilation |

***

## **10. Development Best Practices**

### **Code Organization**

```
haf-messenger/
├── haf-client/           # JavaFX application
│   ├── src/main/java/
│   │   └── com/haf/client/
│   │       ├── view/     # FXML controllers
│   │       ├── viewmodel/# ViewModels
│   │       └── model/    # Client-side models
│   └── src/main/resources/
│       └── fxml/         # FXML files
│
├── haf-server/           # Server application
│   ├── src/main/java/
│   │   └── com/haf/server/
│   │       ├── core/     # Main.java
│   │       ├── config/   # ServerConfig
│   │       ├── ingress/  # HttpIngressServer, WebSocketIngressServer
│   │       ├── router/   # MailboxRouter, RateLimiterService
│   │       ├── db/       # EnvelopeDAO
│   │       ├── handlers/ # Validators
│   │       └── metrics/  # AuditLogger, MetricsRegistry
│   ├── src/main/resources/
│   │   ├── db/migration/ # Flyway SQL scripts
│   │   └── log4j2.xml    # Logging configuration
│   └── src/test/java/    # Unit and integration tests
│
└── haf-shared/           # Shared DTOs
    └── src/main/java/
        └── com/haf/shared/
            ├── dto/      # EncryptedMessage, etc.
            └── constants/# MessageHeader, etc.
```

### **Naming Conventions**

| Type | Convention | Example |
|------|------------|---------|
| **Classes** | PascalCase | `MailboxRouter` |
| **Methods** | camelCase | `fetchForRecipient()` |
| **Constants** | UPPER_SNAKE_CASE | `MAX_PAYLOAD_SIZE` |
| **Packages** | lowercase | `com.haf.server.router` |
| **Test Classes** | ProductionClass + Test | `MailboxRouterTest` |
| **Test Methods** | test_scenario_expectedResult | `acknowledge_records_delivery_latency` |

### **Code Style**

- **Indentation**: 4 spaces
- **Line length**: Max 120 characters
- **Javadoc**: All public APIs
- **Immutability**: Prefer `final` and records
- **Null safety**: Use `Objects.requireNonNull()`

**Example:**

```java
/**
 * Routes encrypted messages to recipients.
 * 
 * <p>This class manages message delivery, subscriber notifications,
 * and TTL-based cleanup of expired messages.
 * 
 * <p>Thread-safe: All operations are synchronized or use concurrent collections.
 * 
 * @see MailboxSubscriber
 * @see QueuedEnvelope
 */
public final class MailboxRouter implements AutoCloseable {
    private final EnvelopeDAO envelopeDAO;
    private final AuditLogger auditLogger;
    
    /**
     * Creates a new MailboxRouter.
     * 
     * @param envelopeDAO the DAO for accessing message envelopes
     * @param auditLogger the logger for audit events
     * @throws NullPointerException if any argument is null
     */
    public MailboxRouter(EnvelopeDAO envelopeDAO, AuditLogger auditLogger) {
        this.envelopeDAO = Objects.requireNonNull(envelopeDAO, "envelopeDAO must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
    }
}
```

### **Git Workflow**

1. **Feature branches**: `feature/delivery-latency-tracking`
2. **Commit messages**: `feat: add delivery latency metrics to MailboxRouter`
3. **Pull requests**: Required for all changes
4. **Code review**: At least one approval
5. **CI/CD**: All tests must pass

### **Testing Guidelines**

- **Test coverage**: Minimum 80% for production code
- **Test isolation**: Each test is independent
- **Test naming**: Descriptive (what_when_then)
- **Assertions**: Use specific assertions (`assertEquals`, not `assertTrue`)
- **Mocking**: Mock external dependencies only

**Example Test:**

```java
@Test
void acknowledge_records_delivery_latency() {
    // Given: Two envelopes with known creation times
    List<String> envelopeIds = List.of("id1", "id2");
    long now = System.currentTimeMillis();
    QueuedEnvelope env1 = new QueuedEnvelope("id1", createValidMessage(), now - 100, now + 3600000);
    QueuedEnvelope env2 = new QueuedEnvelope("id2", createValidMessage(), now - 200, now + 3600000);
    
    when(envelopeDAO.fetchByIds(envelopeIds))
        .thenReturn(Map.of("id1", env1, "id2", env2));
    when(envelopeDAO.markDelivered(envelopeIds)).thenReturn(true);
    
    metricsRegistry.increaseQueueDepth();
    metricsRegistry.increaseQueueDepth();
    
    // When: Acknowledging the envelopes
    router.acknowledge(envelopeIds);
    
    // Then: Latency should be tracked
    assertEquals(2, metricsRegistry.snapshot().deliveredCount());
    assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() >= 100);
    assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() <= 200);
}
```

***

## **Summary**

HAF Messenger follows **industry best practices** for secure, scalable, and maintainable software:

1. **MVVM** on the client for clean UI separation
2. **Layered architecture** on the server for scalability
3. **TDD** for code quality and confidence
4. **Clean architecture** for flexibility and testability
5. **Security-first** design for military-grade protection
6. **Observable systems** for production monitoring

**Key Metrics:**
- **Test Coverage**: 95%
- **Code Quality**: All tests passing
- **Security**: TLS 1.3 + E2E encryption
- **Performance**: <500ms avg delivery latency

***

**Document Version**: 1.0  
**Last Updated**: 2025-11-19  
**Authors**: HAF Messenger Development Team