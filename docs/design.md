# Sports Commerce & News Management System — Design Document

## 1. Introduction & Scope

### 1.1 Purpose

The Sports Commerce & News Management System is an offline-first Android application that combines sports news aggregation, content curation, and merchandise commerce into a single on-device experience. All data processing, storage, and business logic execute locally on the Android device with no dependency on external servers or network services at runtime.

### 1.2 Boundaries

- **Platform:** Android 10 (API 29) and above.
- **Architecture:** Fully on-device. No backend server, no REST/GraphQL API endpoints, no cloud database.
- **Storage:** Room (SQLite) for structured data; device file storage for images and backups.
- **Networking:** Limited to RSS/web content ingestion when connectivity is available. All core features function offline.
- **Users:** Three roles — Administrator, Editor/Analyst, Regular User — all managed locally.

### 1.3 Out of Scope

The following are explicitly excluded from this system:

- WeChat login or any third-party social authentication.
- Real-name identity verification.
- SMS or email sending of any kind.
- Push notifications via FCM or any external push service.
- Webhooks or any outbound HTTP callbacks.
- Online payment processing, payment gateway integration, or refund handling.
- Server-side components, APIs, or cloud synchronization.

---

## 2. System Architecture

### 2.1 On-Device Architecture

The application follows a layered architecture with strict separation of concerns. All layers below the ViewModel execute off the main (UI) thread.

```
┌─────────────────────────────────────────────────────┐
│                  Android Views UI                    │
│       (Activities, Fragments, RecyclerView,          │
│        DiffUtil, XML Layouts)                        │
├─────────────────────────────────────────────────────┤
│                  ViewModel Layer                     │
│       (Lifecycle-aware, LiveData/StateFlow,          │
│        UI state management)                          │
├─────────────────────────────────────────────────────┤
│                  UseCase Layer                       │
│       (Business logic, pricing engine,               │
│        validation rules, cart operations)             │
├─────────────────────────────────────────────────────┤
│                 Repository Layer                     │
│       (Data abstraction, caching strategy,           │
│        transaction coordination)                     │
├──────────┬──────────────────┬───────────────────────┤
│  Room DB │  Local File      │  WorkManager          │
│ (SQLite) │  Storage         │  (Scheduled           │
│          │  (Images,        │   ingestion,           │
│          │   Backups)       │   background tasks)    │
├──────────┴──────────────────┴───────────────────────┤
│              Android Keystore                        │
│       (Encryption keys, secure storage)              │
└─────────────────────────────────────────────────────┘
```

### 2.2 Threading Model

- **Main thread:** UI rendering only. No database queries, file I/O, or computation.
- **IO dispatcher:** All Room queries, file reads/writes, and ingestion parsing.
- **Default dispatcher:** Pricing engine calculations, data quality validation, batch processing.
- **WorkManager threads:** Scheduled ingestion jobs with system-managed threading.

### 2.3 Dependency Injection

Koin provides dependency injection across all layers. Modules are organized by feature:

- `appModule` — Application-scoped singletons (database, repositories).
- `viewModelModule` — ViewModel factories.
- `useCaseModule` — Business logic use cases.
- `workerModule` — WorkManager worker factories.

---

## 3. Technology Stack

| Technology | Version / Spec | Purpose |
|---|---|---|
| Android SDK | API 29+ (Android 10+) | Minimum platform target; scoped storage compliance |
| Kotlin | 1.9+ | Primary language; coroutines for async operations |
| Android Views | XML Layouts | UI framework (Activities, Fragments, custom Views) |
| RecyclerView | AndroidX | Efficient scrollable lists with view recycling |
| DiffUtil | AndroidX | Minimal list update calculations for 60fps scrolling |
| Room | AndroidX | SQLite abstraction with compile-time query verification |
| Koin | 3.x | Lightweight dependency injection framework |
| WorkManager | AndroidX | Reliable background task scheduling with system constraints |
| Android Keystore | System | Hardware-backed encryption key storage |
| LRU Cache | `android.util.LruCache` | In-memory image cache with bounded memory (peak under 20MB) |
| SHA-256 | `java.security.MessageDigest` | Password hashing, backup integrity verification |
| Kotlin Coroutines | kotlinx.coroutines | Structured concurrency for off-main-thread operations |
| Kotlin Flow | kotlinx.coroutines.flow | Reactive data streams from Room to UI |

---

## 4. Database Design

All tables are Room entities. Timestamps are stored as `INTEGER` (epoch milliseconds, UTC). Enums use Room `@TypeConverter`. JSON fields use `TEXT` with type converters for serialization/deserialization.

### 4.1 users

Stores local authentication credentials and role assignments.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Internal user identifier |
| username | TEXT | NOT NULL, UNIQUE | Login identifier |
| password_hash | TEXT | NOT NULL | Salted hash of user password |
| salt | TEXT | NOT NULL | Unique per-user salt for hashing |
| role | TEXT | NOT NULL, CHECK(role IN ('ADMIN','EDITOR','USER')) | Role enum |
| status | TEXT | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, SUSPENDED, DELETED |
| created_at | INTEGER | NOT NULL | Account creation timestamp (UTC ms) |
| updated_at | INTEGER | NOT NULL | Last modification timestamp (UTC ms) |

**Indexes:** Unique index on `username`.

### 4.2 source_rules

Defines RSS-like parsing and crawling configuration per content source.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Rule identifier |
| source_url | TEXT | NOT NULL | Base URL of the content source |
| parse_config | TEXT | NOT NULL | JSON: XPath/CSS selectors for title, content, date, author, image |
| allow_domains | TEXT | | JSON array of allowed domains for link following |
| block_domains | TEXT | | JSON array of blocked domains |
| block_keywords | TEXT | | JSON array of keywords to filter out |
| user_agent_pool | TEXT | NOT NULL | JSON array of user-agent strings for rotation |
| pacing_ms | INTEGER | NOT NULL, DEFAULT 3000 | Minimum milliseconds between requests to this source |
| interval_hours | INTEGER | NOT NULL, DEFAULT 6 | Ingestion schedule interval in hours |
| rule_version | INTEGER | NOT NULL, DEFAULT 1 | Incremented on each rule edit for lineage tracking |
| status | TEXT | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, PAUSED, DISABLED |
| created_at | INTEGER | NOT NULL | Creation timestamp (UTC ms) |
| updated_at | INTEGER | NOT NULL | Last modification timestamp (UTC ms) |

### 4.3 ingestion_job_runs

Records each execution of a scheduled or manual ingestion job.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Run identifier |
| source_rule_id | INTEGER | NOT NULL, FK → source_rules(id) | Which source rule was executed |
| rule_version_used | INTEGER | NOT NULL | Snapshot of rule_version at execution time |
| started_at | INTEGER | NOT NULL | Job start timestamp (UTC ms) |
| completed_at | INTEGER | | Job completion timestamp (UTC ms) |
| items_parsed | INTEGER | NOT NULL, DEFAULT 0 | Count of successfully parsed items |
| items_failed | INTEGER | NOT NULL, DEFAULT 0 | Count of items that failed parsing |
| failure_reason | TEXT | | Human-readable failure description |
| status | TEXT | NOT NULL, CHECK(status IN ('RUNNING','SUCCESS','PARTIAL','FAILED')) | Job outcome |
| batch_version_id | INTEGER | FK → data_batch_versions(id) | Associated batch version record |
| created_at | INTEGER | NOT NULL | Record creation timestamp (UTC ms) |

**Indexes:** Index on `source_rule_id`. Index on `started_at` for time-range queries.

### 4.4 data_batch_versions

Tracks each ingestion batch for quality metrics and lineage.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Batch identifier |
| batch_id | TEXT | NOT NULL, UNIQUE | UUID for external reference |
| rule_version | INTEGER | NOT NULL | Rule version that produced this batch |
| source_rule_id | INTEGER | NOT NULL, FK → source_rules(id) | Originating source rule |
| timestamp | INTEGER | NOT NULL | Batch creation timestamp (UTC ms) |
| item_count | INTEGER | NOT NULL, DEFAULT 0 | Total items in batch |
| error_count | INTEGER | NOT NULL, DEFAULT 0 | Items failing quality checks |
| error_rate | REAL | NOT NULL, DEFAULT 0.0 | error_count / item_count as decimal |
| status | TEXT | NOT NULL, DEFAULT 'PENDING' | PENDING, VALIDATED, FLAGGED |

**Indexes:** Index on `source_rule_id`. Index on `timestamp`.

### 4.5 data_lineage

Links each article back to its ingestion batch and source rule for full traceability.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Lineage record identifier |
| batch_version_id | INTEGER | NOT NULL, FK → data_batch_versions(id) | Parent batch |
| source_rule_id | INTEGER | NOT NULL, FK → source_rules(id) | Source rule used |
| article_id | INTEGER | NOT NULL, FK → articles(id) | Resulting article |
| transformation_note | TEXT | | Description of any transformations applied |
| created_at | INTEGER | NOT NULL | Record creation timestamp (UTC ms) |

**Indexes:** Index on `batch_version_id`. Index on `article_id`.

### 4.6 articles

Stores ingested and curated news articles.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Article identifier |
| source_id | TEXT | NOT NULL | Identifier from the originating source |
| title | TEXT | NOT NULL | Article headline |
| content | TEXT | | Full article body |
| author | TEXT | | Author name |
| publish_time | INTEGER | | Original publication timestamp (UTC ms) |
| image_url | TEXT | | Remote image URL |
| local_image_path | TEXT | | Path to locally cached image file |
| saved_offline | INTEGER | NOT NULL, DEFAULT 0 | 1 if user saved for offline reading |
| status | TEXT | NOT NULL, DEFAULT 'DRAFT', CHECK(status IN ('DRAFT','PUBLISHED','RETRACTED')) | Article state |
| curated_by | INTEGER | FK → users(id) | Editor who approved/rejected |
| created_at | INTEGER | NOT NULL | Record creation timestamp (UTC ms) |
| updated_at | INTEGER | NOT NULL | Last modification timestamp (UTC ms) |

**Indexes:** Composite index on `(source_id, publish_time)` — critical for query performance under 50ms on 100K articles. Index on `status`. FTS (Full-Text Search) virtual table on `title` and `content` for search functionality.

### 4.7 catalog_items

Merchandise available for purchase.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Catalog item identifier |
| sku | TEXT | NOT NULL, UNIQUE | Stock Keeping Unit |
| name | TEXT | NOT NULL | Item display name |
| description | TEXT | | Item description |
| team | TEXT | | Associated sports team |
| league | TEXT | | Associated league |
| price_cents | INTEGER | NOT NULL | Price in cents to avoid floating-point issues |
| image_path | TEXT | | Path to local product image |
| status | TEXT | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE, DISCONTINUED, HIDDEN |
| created_at | INTEGER | NOT NULL | Record creation timestamp (UTC ms) |
| updated_at | INTEGER | NOT NULL | Last modification timestamp (UTC ms) |

**Indexes:** Unique index on `sku`. Index on `team`. Index on `league`.

### 4.8 inventory_snapshots

Current stock levels per SKU, used for real-time checkout validation.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Snapshot identifier |
| sku | TEXT | NOT NULL, FK → catalog_items(sku) | Product SKU |
| available_quantity | INTEGER | NOT NULL, DEFAULT 0 | Current available stock (must not be negative) |
| last_updated_at | INTEGER | NOT NULL | Last update timestamp (UTC ms) |

**Indexes:** Unique index on `sku`.

### 4.9 carts

Shopping cart header, supporting guest and authenticated users.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Cart identifier |
| user_id | INTEGER | FK → users(id), NULLABLE | NULL for guest carts |
| merged_to_user_id | INTEGER | FK → users(id), NULLABLE | Set when guest cart merges to authenticated user |
| status | TEXT | NOT NULL, DEFAULT 'ACTIVE', CHECK(status IN ('ACTIVE','MERGED','CHECKED_OUT','ABANDONED')) | Cart lifecycle state |
| created_at | INTEGER | NOT NULL | Cart creation timestamp (UTC ms) |
| updated_at | INTEGER | NOT NULL | Last modification timestamp (UTC ms) |

**Indexes:** Index on `user_id`. Index on `status`.

### 4.10 cart_line_items

Individual items within a cart, with price-lock tracking.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Line item identifier |
| cart_id | INTEGER | NOT NULL, FK → carts(id) | Parent cart |
| sku | TEXT | NOT NULL, FK → catalog_items(sku) | Product SKU |
| quantity | INTEGER | NOT NULL, CHECK(quantity > 0) | Requested quantity |
| unit_price_cents_at_add | INTEGER | NOT NULL | Price captured when item was added |
| price_locked_at | INTEGER | | Timestamp when price lock started (UTC ms) |
| price_lock_expires_at | INTEGER | | Timestamp when price lock expires (UTC ms) |

**Indexes:** Composite index on `(cart_id, sku)` — ensures fast lookups and uniqueness per cart. Index on `price_lock_expires_at` for expiry checks.

### 4.11 price_rules

Configurable pricing rules evaluated by the pricing engine.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Rule identifier |
| rule_type | TEXT | NOT NULL | Rule category (e.g., ORDER_TOTAL_DISCOUNT, ITEM_DISCOUNT) |
| condition_json | TEXT | NOT NULL | JSON-encoded conditions (e.g., `{"min_order_cents": 5000}`) |
| discount_type | TEXT | NOT NULL | PERCENTAGE or FIXED_AMOUNT |
| discount_value | INTEGER | NOT NULL | Percentage (e.g., 10 for 10%) or amount in cents |
| priority | INTEGER | NOT NULL, DEFAULT 0 | Lower number = higher priority; determines evaluation order |
| active | INTEGER | NOT NULL, DEFAULT 1 | 1 = active, 0 = inactive |
| created_at | INTEGER | NOT NULL | Record creation timestamp (UTC ms) |
| updated_at | INTEGER | NOT NULL | Last modification timestamp (UTC ms) |

### 4.12 coupons

Single-use or limited-use discount codes.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Coupon identifier |
| code | TEXT | NOT NULL, UNIQUE | User-facing coupon code |
| discount_type | TEXT | NOT NULL | PERCENTAGE or FIXED_AMOUNT |
| discount_value | INTEGER | NOT NULL | Percentage or amount in cents |
| min_order_cents | INTEGER | NOT NULL, DEFAULT 0 | Minimum order total to apply coupon |
| max_uses | INTEGER | NOT NULL, DEFAULT 1 | Maximum total redemptions allowed |
| used_count | INTEGER | NOT NULL, DEFAULT 0 | Current redemption count |
| expires_at | INTEGER | | Expiration timestamp (UTC ms); NULL = no expiry |
| active | INTEGER | NOT NULL, DEFAULT 1 | 1 = active, 0 = inactive |
| created_at | INTEGER | NOT NULL | Record creation timestamp (UTC ms) |

**Indexes:** Unique index on `code`.

### 4.13 state_tax_rates

Tax rates by US state with effective date ranges.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Rate record identifier |
| state_code | TEXT | NOT NULL | Two-letter US state code |
| rate_percent | REAL | NOT NULL | Tax rate as percentage (e.g., 8.25) |
| effective_from | INTEGER | NOT NULL | Start of validity period (UTC ms) |
| effective_to | INTEGER | | End of validity period (UTC ms); NULL = currently active |

**Indexes:** Index on `(state_code, effective_from)`.

### 4.14 notifications

In-app notification delivery records.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Notification identifier |
| recipient_user_id | INTEGER | NOT NULL, FK → users(id) | Target user |
| template_id | INTEGER | FK → notification_templates(id) | Source template, if template-based |
| rendered_content | TEXT | NOT NULL | Final notification text after variable substitution |
| status | TEXT | NOT NULL, DEFAULT 'PENDING', CHECK(status IN ('PENDING','DELIVERED','FAILED')) | Delivery state |
| retry_count | INTEGER | NOT NULL, DEFAULT 0 | Number of delivery retry attempts |
| created_at | INTEGER | NOT NULL | Creation timestamp (UTC ms) |
| delivered_at | INTEGER | | Successful delivery timestamp (UTC ms) |

**Indexes:** Index on `recipient_user_id`. Index on `status` for retry queries.

### 4.15 notification_templates

Reusable notification templates with variable placeholders.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Template identifier |
| name | TEXT | NOT NULL, UNIQUE | Template name (e.g., ORDER_CONFIRMATION) |
| body_template | TEXT | NOT NULL | Template body with `{variable}` placeholders |
| variables | TEXT | NOT NULL | JSON array of expected variable names (e.g., `["orderId","total"]`) |
| created_at | INTEGER | NOT NULL | Record creation timestamp (UTC ms) |
| updated_at | INTEGER | NOT NULL | Last modification timestamp (UTC ms) |

### 4.16 audit_events

Immutable log of all security-relevant and data-modifying actions.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Event identifier |
| user_id | INTEGER | FK → users(id) | Acting user (NULL for system events) |
| action | TEXT | NOT NULL | Action type (e.g., LOGIN, LOGOUT, EDIT_ARTICLE, EXPORT, BACKUP) |
| target_type | TEXT | | Entity type affected (e.g., ARTICLE, USER, SOURCE_RULE) |
| target_id | TEXT | | Identifier of the affected entity |
| detail | TEXT | | JSON with additional context |
| timestamp | INTEGER | NOT NULL | Event timestamp (UTC ms) |

**Indexes:** Index on `user_id`. Index on `action`. Index on `timestamp`. Composite index on `(target_type, target_id)`.

### 4.17 backups

Metadata for local backup files.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | INTEGER | PRIMARY KEY, AUTOINCREMENT | Backup record identifier |
| file_path | TEXT | NOT NULL | Absolute path to backup file on device |
| checksum_sha256 | TEXT | NOT NULL | SHA-256 hash of the backup file for integrity verification |
| created_at | INTEGER | NOT NULL | Backup creation timestamp (UTC ms) |
| size_bytes | INTEGER | NOT NULL | Backup file size |

### 4.18 Entity-Relationship Summary

```
users ──────────────< audit_events
  │
  ├──────────────────< carts ──────< cart_line_items ──> catalog_items
  │                                                         │
  ├──────────────────< notifications                        │
  │                       │                          inventory_snapshots
  │                notification_templates
  │
source_rules ──────< ingestion_job_runs ──> data_batch_versions
  │                                              │
  └────────────────< data_lineage >──────────────┘
                         │
                      articles
```

---

## 5. Authentication & Security

### 5.1 Password Hashing

- Each user has a unique cryptographically random `salt` (minimum 16 bytes, generated via `SecureRandom`).
- Passwords are hashed using PBKDF2-HMAC-SHA256 with a minimum of 120,000 iterations.
- The stored `password_hash` is the result of `PBKDF2(password, salt, iterations)`.
- Raw passwords are never stored, logged, or retained in memory beyond the hashing operation.

### 5.2 Step-Up Re-Authentication

Certain sensitive operations require the user to re-enter their password even if already logged in:

- **Data export** (any format).
- **Backup creation.**
- **Backup restore.**
- **Role changes** (Admin only).

The re-authentication flow presents a password prompt dialog. On success, a short-lived token (5-minute validity, in-memory only) authorizes the sensitive operation. The token is cleared after use or expiry.

### 5.3 Input Validation

All user inputs are validated to mitigate injection-style payloads:

- **Usernames:** Alphanumeric plus underscore, 3–30 characters.
- **Passwords:** Minimum 8 characters, at least one uppercase, one lowercase, one digit.
- **Text fields:** Stripped of HTML tags and SQL special characters before storage.
- **Numeric fields:** Range-checked at the ViewModel layer before reaching the repository.
- **JSON configuration fields (parse_config, condition_json):** Validated against expected schema before persistence.

### 5.4 Android Keystore Encryption

- A symmetric AES-256-GCM key is generated and stored in Android Keystore on first launch.
- Sensitive database fields (password hashes, salt values, stored identifiers) are encrypted at rest using this key.
- The Keystore key is hardware-backed where available (StrongBox on supported devices).
- Key access requires user authentication context (device lock must be set).

### 5.5 Field Masking

- Password hashes and salt values are never displayed in any UI component.
- User IDs and internal identifiers are masked (e.g., `***456`) in any user-facing log or export.
- Audit event detail fields redact sensitive values before storage.
- Debug/logcat output excludes password hashes, salts, and full user identifiers.

### 5.6 Audit Logging

Every security-relevant action generates an `audit_events` record:

| Action Category | Logged Actions |
|---|---|
| Authentication | LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, STEP_UP_AUTH_SUCCESS, STEP_UP_AUTH_FAILURE |
| User Management | USER_CREATED, USER_ROLE_CHANGED, USER_SUSPENDED, USER_DELETED |
| Content | ARTICLE_CREATED, ARTICLE_EDITED, ARTICLE_PUBLISHED, ARTICLE_RETRACTED |
| Ingestion | SOURCE_RULE_CREATED, SOURCE_RULE_EDITED, INGESTION_JOB_STARTED, INGESTION_JOB_COMPLETED |
| Commerce | CART_CHECKOUT, PRICE_RULE_CHANGED, COUPON_CREATED |
| Data | EXPORT_INITIATED, BACKUP_CREATED, BACKUP_RESTORED |

---

## 6. Role-Based Access

### 6.1 Permission Matrix

| Feature / Action | Admin | Editor/Analyst | Regular User |
|---|---|---|---|
| **Authentication** | | | |
| Login / Logout | Yes | Yes | Yes |
| Manage users (create, suspend, delete) | Yes | No | No |
| Change user roles | Yes | No | No |
| **Ingestion** | | | |
| Create / edit source rules | Yes | No | No |
| Pause / disable source rules | Yes | No | No |
| Trigger manual ingestion | Yes | No | No |
| View ingestion job history | Yes | Yes | No |
| View ingestion failure details | Yes | Yes | No |
| **Batch & Quality** | | | |
| View batch version records | Yes | Yes | No |
| View data lineage | Yes | Yes | No |
| View quality metrics | Yes | Yes | No |
| Acknowledge quality alerts | Yes | Yes | No |
| **Articles** | | | |
| Browse published articles | Yes | Yes | Yes |
| Search / filter articles | Yes | Yes | Yes |
| Save articles for offline reading | Yes | Yes | Yes |
| Curate / approve / reject articles | Yes | Yes | No |
| Tag / categorize articles | Yes | Yes | No |
| Retract articles | Yes | Yes | No |
| **Commerce** | | | |
| Browse catalog | Yes | Yes | Yes |
| Add to cart / modify cart | Yes | Yes | Yes |
| Apply coupons | Yes | Yes | Yes |
| Checkout | Yes | Yes | Yes |
| Manage catalog items | Yes | No | No |
| Manage price rules | Yes | No | No |
| Manage coupons | Yes | No | No |
| Manage tax rate tables | Yes | No | No |
| Update inventory snapshots | Yes | No | No |
| **Notifications** | | | |
| Receive in-app notifications | Yes | Yes | Yes |
| Manage notification templates | Yes | No | No |
| **Backup & Restore** | | | |
| Create backup | Yes | No | No |
| Restore backup | Yes | No | No |
| **Audit** | | | |
| View audit log | Yes | Read-only | No |
| Export audit log | Yes | No | No |

### 6.2 Enforcement

- Role checks are performed at the UseCase layer before any state mutation.
- ViewModels expose only the actions permitted for the current user's role.
- UI elements for unauthorized actions are hidden (not merely disabled).
- Any attempt to invoke a restricted action through direct method call logs an `UNAUTHORIZED_ACCESS` audit event.

---

## 7. Core Modules

### 7.a Ingestion Engine

#### 7.a.1 SourceRule Configuration

Administrators define ingestion rules through the Ingestion Console. Each `source_rules` record specifies:

- **source_url:** The root URL to fetch content from.
- **parse_config:** A JSON object defining CSS/XPath selectors for extracting article title, body, author, publish date, and image URL from HTML.
- **allow_domains / block_domains:** JSON arrays that whitelist or blacklist domains encountered during link following. Block takes precedence over allow.
- **block_keywords:** JSON array of keywords. Articles containing any blocked keyword in title or body are discarded during parsing.
- **rule_version:** Auto-incremented integer on each edit. Stored in `ingestion_job_runs` and `data_batch_versions` for lineage tracking.

#### 7.a.2 RSS Parsing

The parser supports RSS 2.0 and Atom feed formats:

1. Fetch the `source_url` feed document.
2. Parse XML to extract `<item>` or `<entry>` elements.
3. For each item, apply `parse_config` selectors to extract structured fields.
4. Run the item through allow/block filters.
5. Deduplicate against existing `articles.source_id` values.
6. Insert new articles with `status = DRAFT`.

#### 7.a.3 Anti-Scraping Measures

- **User-Agent Rotation:** Each source rule stores a pool of user-agent strings (`user_agent_pool`). On each request, a random user-agent is selected from the pool. The pool should contain a minimum of 5 distinct user-agent strings.
- **Per-Source Request Pacing:** The `pacing_ms` value (default 3000ms) enforces a minimum delay between consecutive HTTP requests to the same source. This is implemented as a `delay()` call in the ingestion coroutine per source.

#### 7.a.4 WorkManager Scheduling

- Each active source rule registers a `PeriodicWorkRequest` with the interval from `interval_hours` (default 6 hours).
- **Constraints:** Work is constrained to `NetworkType.CONNECTED`, `requiresDeviceIdle = true`, `requiresCharging = true` for battery balance.
- When a source rule is paused or disabled, its corresponding WorkRequest is cancelled.
- When a source rule's `interval_hours` is modified, the existing WorkRequest is replaced.

#### 7.a.5 Exponential Backoff Retry

On ingestion failure:

1. **Attempt 1:** Immediate retry after 1-minute delay.
2. **Attempt 2:** Retry after 4-minute delay.
3. **Attempt 3:** Retry after 10-minute delay.
4. Total window: approximately 15 minutes across 3 attempts.

If all 3 attempts fail, the job is recorded with `status = FAILED` and the failure reason is logged.

#### 7.a.6 Failure Counting and Alerting

- A rolling 24-hour window counter tracks failures per source rule.
- When a source rule accumulates 5 or more `FAILED` job runs within 24 hours, the system generates an in-app alert notification for all Admin users.
- The alert includes the source rule name, failure count, and most recent failure reason.
- The counter resets after a successful ingestion run or manual acknowledgment.

### 7.b Batch Tracking & Quality

#### 7.b.1 DataBatchVersion Lifecycle

Each ingestion run creates a `data_batch_versions` record:

1. **PENDING:** Created at job start with `item_count = 0`.
2. As items are parsed, `item_count` increments.
3. After parsing completes, the quality validation engine runs.
4. **VALIDATED:** All quality checks pass or error rate is below 2%.
5. **FLAGGED:** Error rate equals or exceeds 2%, triggering an in-app alert.

#### 7.b.2 DataLineage Graph

For each article created during a batch:

- A `data_lineage` record is inserted linking `batch_version_id` → `article_id`.
- The `source_rule_id` is recorded for direct source attribution.
- `transformation_note` captures any modifications applied (e.g., "HTML stripped from body", "Image URL resolved from relative to absolute").

This enables full traceability: given any article, the system can trace back to the exact batch, rule version, and source that produced it.

#### 7.b.3 Validation Rule Engine

After each batch completes parsing, the following quality rules are applied to every item:

| Rule | Condition | Classification |
|---|---|---|
| Publish Time Freshness | `publish_time` must be within 365 days of current time | ERROR if outside range |
| Price Range | `price_cents` must be between 1 ($0.01) and 999999 ($9,999.99) | ERROR if outside range |
| Inventory Non-Negative | `available_quantity` must be >= 0 | ERROR if negative |
| Required Fields | `title` must not be null or empty | ERROR if missing |
| Duplicate Check | `source_id` must not already exist | WARNING (item skipped) |

#### 7.b.4 Error Rate Calculation and Alerting

- `error_rate = error_count / item_count` (as a decimal, e.g., 0.02 = 2%).
- If `error_rate >= 0.02` after validation completes, the batch status is set to `FLAGGED`.
- An in-app notification is sent to all Admin and Editor users with the batch ID, error count, item count, and error rate.

### 7.c Article Browsing & Offline

#### 7.c.1 Search and Filter

The home screen displays a scrollable list of published articles with:

- **Filter by:** Source, date range, team, league, topic tags.
- **Search:** Full-text search on title and content using Room FTS4 virtual table.
- **Sort:** By publish date (default, descending), title alphabetical.

#### 7.c.2 Room Full-Text Search

- An FTS4 virtual table `articles_fts` mirrors `title` and `content` columns from `articles`.
- FTS table is kept in sync via Room `@Fts4` annotation with `contentEntity = Article::class`.
- Search queries use `MATCH` syntax with ranking by relevance.

#### 7.c.3 Offline Save Mechanism

When a user taps "Save for Offline":

1. Set `articles.saved_offline = 1`.
2. If `image_url` is present and `local_image_path` is null, download the image to app-private storage.
3. Store the local file path in `local_image_path`.
4. The article's full content is already in Room and available offline.

Saved articles are accessible through a dedicated "Saved" tab regardless of connectivity.

#### 7.c.4 Composite Index Strategy

The composite index on `(source_id, publish_time)` in the `articles` table is designed to support the primary query pattern: filtering by source and ordering by date. On a dataset of 100,000 articles, this index ensures query execution under 50ms by enabling the database to perform an index-only scan for the common list query.

### 7.d Cart & Checkout

#### 7.d.1 Cart Lifecycle

```
ACTIVE → CHECKED_OUT   (successful checkout)
ACTIVE → ABANDONED     (user explicitly clears cart, or TTL expiry)
ACTIVE → MERGED        (guest cart merged into authenticated user's cart)
```

#### 7.d.2 Guest-to-User Cart Merge

1. When an unauthenticated user adds items to cart, a `carts` record is created with `user_id = NULL`.
2. A device-local identifier (stored in SharedPreferences) links the guest session to the cart.
3. On first login, the system checks for an active guest cart on the device.
4. If found, all `cart_line_items` from the guest cart are merged into the authenticated user's active cart:
   - If the same SKU exists in both carts, quantities are summed.
   - The guest cart's `unit_price_cents_at_add` is preserved for merged items.
5. The guest cart status is set to `MERGED` and `merged_to_user_id` is set to the authenticated user's ID.

#### 7.d.3 Inventory Validation

Before checkout is permitted:

1. For each `cart_line_items` row, query `inventory_snapshots` for the matching `sku`.
2. If `available_quantity < quantity` for any line item, block checkout and display which items are unavailable.
3. This check is performed:
   - When the user navigates to the cart summary screen.
   - Immediately before finalizing checkout.
   - The check runs in a Room transaction to prevent race conditions.

#### 7.d.4 Price-Lock Mechanism

When the user initiates checkout:

1. For each cart line item, capture the current `price_cents` from `catalog_items`.
2. Set `unit_price_cents_at_add` to the current price.
3. Set `price_locked_at` to the current timestamp.
4. Set `price_lock_expires_at` to current timestamp + 30 minutes.

If the user does not complete checkout within 30 minutes:

1. On next cart access, check if `price_lock_expires_at < now()`.
2. If expired, clear the price lock fields.
3. Re-fetch current prices from `catalog_items`.
4. If any prices changed, display a notification to the user showing the price differences.
5. The user must explicitly reconfirm the cart at the new prices before checkout can proceed.

### 7.e Pricing Engine

#### 7.e.1 Rule Evaluation Order

Price rules are evaluated in priority order (lower number = higher priority):

1. Load all `price_rules` where `active = 1`, ordered by `priority ASC`.
2. For each rule, evaluate `condition_json` against the current cart state.
3. If the condition matches, apply the discount:
   - `PERCENTAGE`: Reduce applicable amount by `discount_value` percent.
   - `FIXED_AMOUNT`: Subtract `discount_value` cents from applicable amount.
4. Rules are applied sequentially; each rule operates on the running total after previous rules.

**Default Rule Example:** 10% off orders over $50 is represented as:
```json
{
  "rule_type": "ORDER_TOTAL_DISCOUNT",
  "condition_json": "{\"min_order_cents\": 5000}",
  "discount_type": "PERCENTAGE",
  "discount_value": 10,
  "priority": 100
}
```

#### 7.e.2 Coupon Application

- Only one coupon may be applied per order.
- Coupon validation checks:
  1. `code` exists and `active = 1`.
  2. `used_count < max_uses`.
  3. `expires_at` is NULL or in the future.
  4. Cart subtotal (after price rule discounts) meets `min_order_cents`.
- If valid, the coupon discount is applied after all price rules.
- On successful checkout, `used_count` is incremented by 1.

#### 7.e.3 Tax Calculation

1. The user's state is determined from their profile or checkout input.
2. Query `state_tax_rates` for the matching `state_code` where `effective_from <= now()` and (`effective_to IS NULL` or `effective_to > now()`).
3. Tax is calculated on the post-discount subtotal: `tax_cents = ROUND(subtotal_cents * rate_percent / 100)`.
4. Tax is displayed as a separate line item in the order summary.

#### 7.e.4 Price-Lock Implementation

All pricing engine calculations are performed off the main thread in the Default coroutine dispatcher. The full calculation chain is:

1. Sum line item prices → subtotal.
2. Apply price rules in priority order → discounted subtotal.
3. Apply coupon (if any) → post-coupon subtotal.
4. Calculate tax → tax amount.
5. Total = post-coupon subtotal + tax.

During price lock (30-minute window), the subtotal is frozen at the locked line item prices. Price rules and tax are recalculated at checkout finalization to ensure accuracy, but the base unit prices remain locked.

### 7.f Editor/Analyst Tools

#### 7.f.1 Ingestion Review

Editors and Analysts can:

- View a list of all `ingestion_job_runs`, filterable by source, status, and date range.
- See summary statistics: total runs, success rate, average items per run.
- Drill down into any job run to see the associated `data_batch_versions` and quality metrics.

#### 7.f.2 Failure Drill-Down

For failed or partial job runs:

- View the `failure_reason` text.
- See `items_parsed` vs. `items_failed` counts.
- Navigate to the `data_lineage` records to identify which specific articles failed and why.
- View the `rule_version_used` to determine if a rule change may have caused the failure.

#### 7.f.3 Content Curation Workflow

Articles follow a state machine:

```
DRAFT → PUBLISHED    (Editor approves)
DRAFT → RETRACTED    (Editor rejects)
PUBLISHED → RETRACTED (Editor retracts published article)
```

- Editors see a queue of `DRAFT` articles for review.
- On approval, `status` is set to `PUBLISHED` and `curated_by` is set to the editor's user ID.
- On rejection or retraction, `status` is set to `RETRACTED`.
- All state transitions generate `audit_events` records.

#### 7.f.4 Article Tagging and Categorization

Editors can assign metadata to articles:

- **Team:** Associates the article with a sports team (free-text or from a managed list).
- **League:** Associates with a league (e.g., NFL, NBA, MLB).
- **Topic:** Custom topic tags for content organization.

These tags are stored as article metadata and are searchable/filterable in the browse interface.

### 7.g Notification System

#### 7.g.1 Template Rendering

Notification templates use `{variable}` syntax for dynamic content:

```
Template: "Your order {orderId} totaling {total} has been confirmed."
Variables: ["orderId", "total"]
Rendered: "Your order ORD-1234 totaling $54.99 has been confirmed."
```

The rendering engine:

1. Loads the `notification_templates` record by `template_id`.
2. Iterates through the `variables` JSON array.
3. Replaces each `{variableName}` placeholder in `body_template` with the provided value.
4. Stores the result in `notifications.rendered_content`.

#### 7.g.2 Delivery Tracking

Each notification progresses through states:

1. **PENDING:** Created and queued for display.
2. **DELIVERED:** Successfully displayed to the user (UI confirmed receipt).
3. **FAILED:** Could not be displayed (e.g., user session ended before display).

`delivered_at` is set only on transition to DELIVERED.

#### 7.g.3 Retry Policy

For notifications in `FAILED` state:

- Retry up to 3 times with 1-minute intervals.
- `retry_count` is incremented on each attempt.
- After 3 failed retries, the notification remains in `FAILED` state permanently.
- Failed notifications are visible in the Admin notification management screen.

---

## 8. Backup & Restore

### 8.1 User-Initiated Flow

Backup and restore are manual operations available only to Administrators, requiring step-up re-authentication.

**Backup Process:**

1. Admin navigates to Settings → Backup & Restore.
2. System prompts for password re-entry (step-up auth).
3. On successful auth, the system:
   a. Closes all active database connections.
   b. Copies the Room database file to a timestamped backup file.
   c. Copies associated local image files to the backup directory.
   d. Computes SHA-256 checksum of the backup file.
   e. Creates a `backups` record with file path, checksum, timestamp, and size.
4. Backup file is stored in app-scoped external storage (`Context.getExternalFilesDir()`), compliant with Android 10+ scoped storage.

**Restore Process:**

1. Admin navigates to Settings → Backup & Restore.
2. Selects a backup file from the list (or picks via file chooser).
3. System prompts for password re-entry (step-up auth).
4. System displays a confirmation dialog: "Restoring will replace all current data. This action cannot be undone. Continue?"
5. On confirmation:
   a. Compute SHA-256 of the selected backup file.
   b. Compare against stored `checksum_sha256` in `backups` record (or against the embedded checksum if restoring from a file-only backup).
   c. If checksums do not match, abort and display an integrity error.
   d. If checksums match, close all database connections, replace the database file, and restart the application.

### 8.2 Integrity Verification

- SHA-256 is computed using `java.security.MessageDigest`.
- The checksum is stored alongside the backup metadata.
- On restore, the checksum is recomputed from the file and compared before any data replacement occurs.
- Any mismatch aborts the restore and logs an `audit_events` entry with action `BACKUP_INTEGRITY_FAILURE`.

### 8.3 Scoped Storage Compliance

- Backups are written to `Context.getExternalFilesDir("backups")`, which does not require `MANAGE_EXTERNAL_STORAGE` permission on Android 10+.
- The backup directory is app-private; other apps cannot access it.
- If the app is uninstalled, backup files are removed by the system.

---

## 9. Performance Design

### 9.1 RecyclerView + DiffUtil for 60fps Scrolling

- All list screens (articles, catalog, cart, notifications) use `RecyclerView` with `ListAdapter`.
- `DiffUtil.ItemCallback` implementations define `areItemsTheSame` (by primary key) and `areContentsTheSame` (by data equality) for each entity.
- Diff calculation runs on a background thread via `AsyncListDiffer`.
- `ViewHolder` binding is kept lightweight — no layout inflation or complex computation during `onBindViewHolder`.
- `RecyclerView.setHasFixedSize(true)` is set wherever the list dimensions do not change.

### 9.2 LRU Image Cache

- `android.util.LruCache<String, Bitmap>` with a maximum size of 20MB (calculated from available heap memory, capped at 20MB).
- Cache key is the local image file path or a hash of the remote URL.
- On cache miss, images are loaded from disk on the IO dispatcher.
- Image downsampling: `BitmapFactory.Options.inSampleSize` is calculated based on the target `ImageView` dimensions to avoid loading full-resolution images into memory.
- Recycled bitmaps are not held in the cache.

### 9.3 Room Index Strategy

Critical indexes for query performance on 100K+ records:

| Table | Index | Purpose |
|---|---|---|
| articles | `(source_id, publish_time)` | Primary browse/filter query |
| articles | FTS4 on `title, content` | Full-text search |
| articles | `status` | Filter by published/draft/retracted |
| cart_line_items | `(cart_id, sku)` | Cart item lookups |
| ingestion_job_runs | `started_at` | Time-range job history queries |
| audit_events | `timestamp` | Audit log pagination |
| audit_events | `(target_type, target_id)` | Entity-specific audit trail |
| notifications | `(recipient_user_id, status)` | User notification inbox queries |

### 9.4 Off-Main-Thread Architecture

| Operation | Dispatcher | Mechanism |
|---|---|---|
| Room queries | `Dispatchers.IO` | Suspend functions in DAO |
| File I/O (images, backups) | `Dispatchers.IO` | Repository-layer coroutines |
| Pricing engine calculations | `Dispatchers.Default` | UseCase-layer coroutines |
| Data quality validation | `Dispatchers.Default` | UseCase-layer coroutines |
| Ingestion parsing | WorkManager thread | Worker implementation |
| UI state updates | `Dispatchers.Main` | ViewModel `LiveData`/`StateFlow` |

All ViewModel coroutines are launched in `viewModelScope` and cancelled on ViewModel clear. Repository operations use `withContext()` to switch dispatchers as needed.

### 9.5 WorkManager Constraints

Ingestion work requests are configured with:

```
Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .setRequiresDeviceIdle(true)
    .setRequiresCharging(true)
    .build()
```

This ensures ingestion jobs only run when the device is connected, idle, and charging — minimizing battery impact and user disruption.

---

## 10. Audit Trail

### 10.1 AuditEvent Model

The `audit_events` table provides an append-only, immutable log of system activity. Records are never updated or deleted.

### 10.2 Logged Actions

| Category | Actions |
|---|---|
| Authentication | LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, STEP_UP_AUTH_SUCCESS, STEP_UP_AUTH_FAILURE |
| User Management | USER_CREATED, USER_ROLE_CHANGED, USER_SUSPENDED, USER_DELETED |
| Ingestion | SOURCE_RULE_CREATED, SOURCE_RULE_EDITED, SOURCE_RULE_PAUSED, INGESTION_STARTED, INGESTION_COMPLETED, INGESTION_FAILED |
| Content | ARTICLE_APPROVED, ARTICLE_REJECTED, ARTICLE_RETRACTED, ARTICLE_TAGGED |
| Commerce | CART_CREATED, CART_CHECKOUT, PRICE_RULE_CREATED, PRICE_RULE_UPDATED, COUPON_CREATED, COUPON_APPLIED |
| Notifications | NOTIFICATION_SENT, NOTIFICATION_FAILED, TEMPLATE_CREATED, TEMPLATE_UPDATED |
| Data Management | EXPORT_INITIATED, BACKUP_CREATED, BACKUP_RESTORED, BACKUP_INTEGRITY_FAILURE |
| Security | UNAUTHORIZED_ACCESS, INPUT_VALIDATION_FAILURE |

### 10.3 Queryable Dimensions

The audit log supports filtering by:

- **User:** `WHERE user_id = ?` — all actions by a specific user.
- **Action:** `WHERE action = ?` — all occurrences of a specific action type.
- **Time range:** `WHERE timestamp BETWEEN ? AND ?` — actions within a time window.
- **Target entity:** `WHERE target_type = ? AND target_id = ?` — all actions affecting a specific entity.
- **Combined:** Any combination of the above using AND clauses.

Pagination is implemented using `LIMIT/OFFSET` on the `timestamp` index for consistent ordering.

---

## 11. Android Platform Compliance

### 11.1 Scoped Storage (Android 10+)

- All file operations use scoped storage APIs: `Context.getFilesDir()` for internal files, `Context.getExternalFilesDir()` for backups.
- No `MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE` permissions are requested.
- Image files for articles and catalog items are stored in app-private internal storage.
- Backup files are stored in app-private external storage for user accessibility without broad permissions.

### 11.2 Background Restrictions

- The application does not use foreground services for ingestion.
- All background work is routed through WorkManager, which respects system background restrictions.
- No `AlarmManager` exact alarms or persistent background services.
- WorkManager handles doze mode, app standby, and battery optimization automatically.

### 11.3 WorkManager Battery Optimization

- Ingestion jobs are constrained to idle and charging states (see Section 9.5).
- Default ingestion interval is 6 hours, reducing wake frequency.
- Failed jobs use exponential backoff rather than aggressive retry loops.
- Per-source pacing (`pacing_ms`) prevents burst network activity that could trigger system throttling.

---

## 12. Data Model Conventions

### 12.1 Timestamps

- All timestamps are stored as `INTEGER` type representing epoch milliseconds in UTC.
- Conversion to local time zones is performed only at the UI layer for display purposes.
- `System.currentTimeMillis()` is used for timestamp generation.
- Room DAO queries use millisecond-precision comparisons for time range filtering.

### 12.2 Soft Delete

The following entities use soft delete via a `status` field rather than physical row deletion:

| Entity | Soft Delete Status | Hard Delete Policy |
|---|---|---|
| users | `DELETED` | Never hard deleted; preserved for audit trail |
| articles | `RETRACTED` | Never hard deleted; preserved for lineage |
| source_rules | `DISABLED` | Never hard deleted; referenced by job history |
| carts | `ABANDONED` | May be purged after 90 days by maintenance task |
| catalog_items | `DISCONTINUED` | Never hard deleted; referenced by cart history |

Entities that are **not** soft-deleted (append-only or immutable):

- `audit_events` — never deleted.
- `data_lineage` — never deleted.
- `data_batch_versions` — never deleted.
- `ingestion_job_runs` — never deleted.
- `backups` — metadata record deleted only if backup file is confirmed removed.

### 12.3 Room Type Converters

A `Converters` class registered at the `@Database` level handles:

| Conversion | Strategy |
|---|---|
| Enum ↔ String | `@TypeConverter` methods mapping enum `.name` to/from `TEXT` |
| JSON String ↔ List/Map | Kotlinx Serialization for `parse_config`, `condition_json`, `variables`, `allow_domains`, `block_domains`, `block_keywords`, `user_agent_pool` |
| Long ↔ Date | Direct mapping — `Long` epoch millis stored, converted to `Date` or `Instant` only when needed at the use case or UI layer |

Type converters are defined as a single class to maintain consistency:

```kotlin
class Converters {
    @TypeConverter
    fun fromRoleEnum(role: UserRole): String = role.name

    @TypeConverter
    fun toRoleEnum(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromStringList(list: List<String>): String = Json.encodeToString(list)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)

    // Additional converters for each enum and JSON field type
}
```
