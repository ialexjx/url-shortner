# ⚡ ScaleLink | Distributed High-Throughput URL Shortener Engine

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Caffeine](https://img.shields.io/badge/L1%20Cache-Caffeine-blue.svg)](https://github.com/ben-manes/caffeine)
[![Redis](https://img.shields.io/badge/L2%20Cache-Redis-red.svg)](https://redis.io/)
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Deployment-Docker%20%7C%20Render-informational.svg)](https://render.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **ScaleLink** is an enterprise-grade, distributed URL shortener engine designed to handle massive write and redirect throughput. Built with modern System Design patterns: **Distributed Range ID Allocator (KGS Pattern)**, **Guava Probabilistic Bloom Filter**, **L1/L2 Dual-Tier Caching**, **Java 21 Project Loom (Virtual Threads)**, and an **Asynchronous Ring Buffer** for telemetry ingestion.

---

## 🌟 Architecture Highlights & System Design

```mermaid
flowchart TD
    Client(["🌐 Client Request"]) --> RateLimiter["🛡️ Token Bucket Rate Limiter (Per IP)"]
    
    subgraph ReadPath["⚡ Lightning Read Path (Redirects)"]
        RateLimiter -->|GET /{shortCode}| Bloom["🔮 Guava Bloom Filter Barrier"]
        Bloom -->|Definite Negative (100% Not in DB)| R404["❌ Instant 404 (0 DB Query)"]
        Bloom -->|Probable Positive| L1["⚡ L1 Caffeine Cache (< 0.1ms)"]
        L1 -->|Cache Hit| Track["📥 Enqueue Async Telemetry"]
        L1 -->|Cache Miss| L2["🌐 L2 Redis Cache (1-2ms)"]
        L2 -->|Cache Hit| WarmL1["Warmup L1"] --> Track
        L2 -->|Cache Miss| DBRead[("🐘 PostgreSQL / H2 Read")]
        DBRead --> WarmCaches["Warmup L1 + L2"] --> Track
        Track --> Redirect["🚀 HTTP 302 Found (Temporary Redirect)"]
    end

    subgraph AsyncAnalytics["📊 High-Throughput Async Analytics Ingestion"]
        Track -.->|Non-blocking offer| RingBuffer["📦 Bounded ArrayBlockingQueue"]
        RingBuffer -->|Every 2s / 200 items batch| LoomWorker["🧵 Virtual Thread Batch Worker"]
        LoomWorker -->|Bulk saveAll| DBClickEvents[("🐘 click_events Table")]
        LoomWorker -->|Aggregated Bulk UPDATE| DBClicksCount[("🐘 short_urls.click_count")]
    end

    subgraph WritePath["📝 Write Path (URL Shortening)"]
        RateLimiter -->|POST /api/v1/shorten| KGS["🎟️ Range ID Generator (KGS)"]
        KGS -->|AtomicLong.increment| Base62["🔤 Base62 Encoder"]
        Base62 --> SaveDB[("🐘 Save ShortUrl to DB")]
        SaveDB --> RegisterBloom["Add to Bloom Filter"]
        SaveDB --> PrewarmL1["Pre-warm L1 & L2 Cache"]
        PrewarmL1 --> Response["✅ JSON Response (HTTP 201)"]
    end
```

---

## 🔬 Core Engineering Innovations

### 1. Key Generation Service (KGS) Range Allocation Pattern
- **Problem**: Auto-incrementing IDs in RDBMS causes heavy write contention and row locks under concurrent load. Snowflake algorithms introduce worker ID coordination complexity in ephemeral container pods (e.g. Render/Kubernetes).
- **ScaleLink Solution**: Each pod atomically reserves a block of **10,000 IDs** in an isolated transaction using pessimistic row locking (`SELECT FOR UPDATE` on `id_range_allocations`).
- **Impact**: 9,999 out of 10,000 URL creations are served entirely in-memory using an `AtomicLong` counter with zero database round-trips.

### 2. Guava Probabilistic Bloom Filter (Negative Caching Barrier)
- **Problem**: Malicious bots or web crawlers querying non-existent short codes cause **Cache Penetration**, bypassing L1/L2 cache and hammering PostgreSQL.
- **ScaleLink Solution**: A memory-efficient Guava Bloom Filter pre-warmed with all active keys on startup.
- **Impact**: 1,000,000 keys with a 1% False Positive Probability (FPP) consume only **~1.2 MB of RAM**. If the Bloom Filter returns false, the request is rejected with a `404 Not Found` immediately—**0 database queries executed**.

### 3. Dual-Tier Caching with Graceful Fallback
- **L1 (Caffeine)**: Local JVM process memory, `< 0.1ms` latency, stores top 50,000 active redirects.
- **L2 (Redis)**: Distributed cache across pod replicas.
- **Graceful Fallback**: If Redis is not available or Render's free tier has no Redis addon, ScaleLink silently degrades to L1 Caffeine without throwing exceptions or crashing.

### 4. High-Throughput Asynchronous Telemetry Ingestion
- **Why HTTP 302 instead of 301?** If we return `301 Moved Permanently`, browsers cache the destination locally and subsequent clicks never reach our server, destroying analytics accuracy. `302 Found` guarantees that every visit reaches ScaleLink.
- **Batch Processing**: Instead of updating the database row on every click, telemetry (`ClickEvent`) is buffered into an in-memory queue. A scheduled Virtual Thread worker flushes up to **200 events at a time** using `saveAll()` and aggregates click counts in memory to run batch in-place SQL updates (`UPDATE short_urls SET click_count = click_count + :delta`).

### 5. Java 21 Virtual Threads (Project Loom)
- Enabled via `spring.threads.virtual.enabled=true`.
- Handles 10,000+ concurrent requests without exhausting OS carrier threads, keeping memory consumption under 200MB.

---

## 🖥️ Interactive Web Dashboard & Live Telemetry

ScaleLink includes a responsive, dark-mode dashboard available at `/`:

1. **Fast Shortener**: Long URL input, optional Custom Alias (`/my-resume`), and TTL expiration dropdown.
2. **Instant QR Code**: On-the-fly QR code generation for mobile scanning.
3. **Real-time Analytics Dashboard**:
   - Total Clicks & Link Status.
   - Interactive charts for **Country Demographics** (Chart.js) and **Device & Browser Distribution**.
   - Live stream of recent audit events with GDPR-safe hashed IPs.
4. **Live System Telemetry Tab**:
   - Active Loom Virtual Threads indicator.
   - L1 Caffeine keys count & hit latency.
   - Guava Bloom Filter element count.
   - Async Ring Buffer queue depth.
   - JVM Heap Memory gauge (Container-aware).

---

## 🚀 REST API Reference

### 1. Shorten a URL
```bash
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "https://github.com/akshat/url-shortener",
    "customAlias": "scalelink-repo",
    "ttlDays": 30
  }'
```

**Response (HTTP 201 Created):**
```json
{
  "shortCode": "scalelink-repo",
  "shortUrl": "http://localhost:8080/scalelink-repo",
  "originalUrl": "https://github.com/akshat/url-shortener",
  "createdAt": "2026-10-02T13:48:28",
  "expiresAt": "2026-11-01T13:48:28",
  "analyticsUrl": "http://localhost:8080/api/v1/analytics/scalelink-repo",
  "customAlias": true
}
```

### 2. Follow Redirect
```bash
curl -i http://localhost:8080/scalelink-repo
# HTTP/1.1 302 Found
# Location: https://github.com/akshat/url-shortener
```

### 3. Fetch Click Analytics & Telemetry
```bash
curl http://localhost:8080/api/v1/analytics/scalelink-repo
```

### 4. Inspect System Telemetry
```bash
curl http://localhost:8080/api/v1/system/status
```

---

## 🚢 Production Deployment to Render

ScaleLink is **100% Render-Ready** with automatic PostgreSQL adaptation and dynamic port binding.

### 1-Click Render Blueprint Setup
1. Push this repository to your GitHub account.
2. Log into [Render.com](https://render.com).
3. Click **New +** -> **Blueprint**.
4. Connect your repository. Render will automatically detect [`render.yaml`](render.yaml) and provision:
   - **Web Service**: Containerized Spring Boot app running on Java 21.
   - **PostgreSQL Database**: Free managed Postgres instance.
5. ScaleLink automatically converts Render's `postgres://` connection string to the appropriate JDBC format via `DatabaseConfig.java`!

---

## 💻 Local Development Setup

### Prerequisites
- **JDK 21** or later
- **Maven 3.9+** (or use included `./mvnw`)

### Quick Start
```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/url-shortener.git
cd url-shortener

# Run all unit and integration tests
./mvnw clean test

# Start the Spring Boot application (Uses zero-config in-memory H2 database by default)
./mvnw spring-boot:run
```

Open your browser at: **`http://localhost:8080`**

---

## 🧪 Automated Test Suite
- `Base62Test`: Bi-directional encoding/decoding, edge cases, negative numbers.
- `BloomFilterServiceTest`: Probabilistic containment, pre-warming, negative rejection.
- `RangeIdGeneratorServiceTest`: Atomic range reservation, sequential generation.
- `UrlShortenerIntegrationTest`: End-to-end HTTP redirect, custom alias collision check, telemetry capture, health check probes.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
