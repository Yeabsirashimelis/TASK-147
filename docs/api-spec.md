# Sports Commerce & News Management System — Internal Architecture & Interface Specification

**Version:** 1.0
**Date:** 2026-04-10
**Platform:** Android (offline-first, fully on-device)
**Technology Stack:** Android Views, Room (SQLite), Koin DI, WorkManager, Android Keystore, Kotlin Coroutines

---

## Table of Contents

1. [General Conventions](#1-general-conventions)
2. [Authentication Module](#2-authentication-module)
3. [User Management (Admin)](#3-user-management-admin)
4. [Source Rule Management (Admin)](#4-source-rule-management-admin)
5. [Ingestion Engine](#5-ingestion-engine)
6. [Batch Tracking & Quality](#6-batch-tracking--quality)
7. [Article Browsing](#7-article-browsing)
8. [Cart Management](#8-cart-management)
9. [Checkout & Pricing](#9-checkout--pricing)
10. [Catalog & Inventory](#10-catalog--inventory)
11. [Notifications](#11-notifications)
12. [Editor/Analyst Tools](#12-editoranalyst-tools)
13. [Audit](#13-audit)
14. [Backup & Restore](#14-backup--restore)
15. [Price Rules & Tax (Admin)](#15-price-rules--tax-admin)
16. [Health & Monitoring (Admin)](#16-health--monitoring-admin)
17. [Appendix A: Role Permission Matrix](#appendix-a-role-permission-matrix)
18. [Appendix B: Room DAO Query Index Strategy](#appendix-b-room-dao-query-index-strategy)
19. [Appendix C: WorkManager Constraint Configuration](#appendix-c-workmanager-constraint-configuration)
20. [Appendix D: Notification Template Variables](#appendix-d-notification-template-variables)

---

## 1. General Conventions

### 1.1 Architecture Overview

This application follows the **MVVM** (Model-View-ViewModel) architectural pattern with a strict layered data flow:

```
View (Activity/Fragment)
  -> ViewModel (LiveData / StateFlow)
    -> UseCase (single business operation)
      -> Repository (business logic + coordination)
        -> Room DAO (SQLite data access)
```

There is **no backend server, no REST API, no HTTP layer**. All data is stored locally in a Room (SQLite) database on the device. This document specifies the internal UseCase interfaces that the ViewModel layer invokes.

### 1.2 Threading & Coroutines

- All IO operations execute off the main thread using Kotlin coroutines with `Dispatchers.IO`.
- ViewModels launch coroutines via `viewModelScope`.
- UseCases are `suspend` functions or return `Flow<T>` for observable queries.
- Room DAOs annotated with `@Query` that return `Flow` are automatically observed on background threads.

### 1.3 Dependency Injection

- **Koin** modules define all DI bindings.
- Modules are organized per feature: `authModule`, `userModule`, `ingestionModule`, `cartModule`, `checkoutModule`, `catalogModule`, `notificationModule`, `auditModule`, `backupModule`, etc.
- Each module registers its Repository, UseCase(s), and ViewModel.

### 1.4 Error Handling

All UseCase invocations return a `Result<T>` wrapper:

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val code: ErrorCode, val message: String, val cause: Throwable? = null) : Result<Nothing>()
}
```

Common `ErrorCode` values: `VALIDATION_ERROR`, `NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`, `CONFLICT`, `INTERNAL_ERROR`, `STEP_UP_REQUIRED`, `INVENTORY_INSUFFICIENT`, `PRICE_LOCK_EXPIRED`.

### 1.5 Timestamps

- All timestamps are stored as `Long` (epoch milliseconds, UTC) in Room entities.
- Display formatting to local timezone is handled exclusively in the View layer.

### 1.6 Soft Delete

- Entities that support soft deletion include a `deleted_at: Long?` column (null = active).
- All list/query operations exclude soft-deleted records by default unless explicitly including them.

### 1.7 Roles

| Role             | Description                                                        |
|------------------|--------------------------------------------------------------------|
| `ADMIN`          | Full system access: user management, source rules, pricing, backup |
| `EDITOR_ANALYST` | Review ingestion batches, approve/reject/curate articles, view quality metrics |
| `USER`           | Browse articles, manage cart, checkout, view notifications          |

### 1.8 Session & Security

- The current session is held in-memory (`SessionHolder` singleton) and persisted to **EncryptedSharedPreferences** (backed by Android Keystore).
- Passwords are hashed with PBKDF2-HMAC-SHA256 (10,000 iterations) and stored with a per-user random salt.
- Step-up authentication is required for sensitive operations (backup, restore, audit export, password change for other users).

---

## 2. Authentication Module

### 2.1 LoginUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Validate username and password against the local database and establish a session. |
| **Input**         | `username: String`, `password: String` |
| **Return Type**   | `Result<Session>` where `Session(userId: Long, username: String, role: Role, loginAt: Long, token: String)` |
| **Role Restriction** | None (pre-authentication) |
| **Validation**    | Username must be non-blank, 3-64 characters. Password must be non-blank. |
| **Room DAO**      | `UserDao.findByUsername(username: String): UserEntity?` |
| **Logic**         | 1. Look up user by username. 2. Verify user is not deactivated (`deactivated_at IS NULL`). 3. Hash provided password with stored salt and compare to stored hash. 4. On match, generate a session token (random UUID), store session in `SessionHolder` and `EncryptedSharedPreferences`. 5. Update `last_login_at` on the user record. |
| **Error Cases**   | `NOT_FOUND` — username does not exist. `UNAUTHORIZED` — password mismatch. `FORBIDDEN` — user account is deactivated. `VALIDATION_ERROR` — blank or malformed input. |

### 2.2 LogoutUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Clear the current session from memory and encrypted storage. |
| **Input**         | None (operates on current session) |
| **Return Type**   | `Result<Unit>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | Must have an active session. |
| **Room DAO**      | None |
| **Logic**         | 1. Clear `SessionHolder` in-memory state. 2. Remove session data from `EncryptedSharedPreferences`. 3. Log audit event for logout. |
| **Error Cases**   | `UNAUTHORIZED` — no active session to clear. |

### 2.3 ChangePasswordUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Update the authenticated user's password after validating the current password and enforcing complexity rules. |
| **Input**         | `currentPassword: String`, `newPassword: String`, `confirmNewPassword: String` |
| **Return Type**   | `Result<Unit>` |
| **Role Restriction** | Any authenticated user (for own password). Admin can change other users' passwords with step-up auth. |
| **Validation**    | `newPassword` must be >= 8 characters, contain at least one uppercase letter, one lowercase letter, one digit, and one special character. `newPassword` must equal `confirmNewPassword`. `newPassword` must differ from `currentPassword`. |
| **Room DAO**      | `UserDao.findById(userId: Long): UserEntity?`, `UserDao.updatePasswordHash(userId: Long, passwordHash: String, salt: String, updatedAt: Long)` |
| **Logic**         | 1. Verify `currentPassword` matches stored hash. 2. Validate complexity of `newPassword`. 3. Generate new salt, hash `newPassword`. 4. Update user record. 5. Invalidate current session and require re-login. 6. Log audit event. |
| **Error Cases**   | `UNAUTHORIZED` — current password incorrect. `VALIDATION_ERROR` — complexity requirements not met, passwords do not match, or new password same as current. `STEP_UP_REQUIRED` — when Admin is changing another user's password. |

### 2.4 StepUpAuthUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Re-verify the current user's password before performing a sensitive operation. Returns a short-lived step-up token. |
| **Input**         | `password: String` |
| **Return Type**   | `Result<StepUpToken>` where `StepUpToken(token: String, expiresAt: Long)` — valid for 5 minutes. |
| **Role Restriction** | Any authenticated user |
| **Validation**    | Password must be non-blank. Must have an active session. |
| **Room DAO**      | `UserDao.findById(userId: Long): UserEntity?` |
| **Logic**         | 1. Retrieve current user. 2. Hash provided password with stored salt and compare. 3. On match, generate a step-up token (random UUID) with a 5-minute expiry. 4. Store in `SessionHolder`. |
| **Error Cases**   | `UNAUTHORIZED` — password incorrect. `UNAUTHORIZED` — no active session. |

### 2.5 Session Management

Session state is managed by `SessionHolder`:

| Component                    | Detail |
|------------------------------|--------|
| **In-Memory**                | `SessionHolder` object holds `currentSession: Session?` and `stepUpToken: StepUpToken?`. Cleared on logout or process death. |
| **Persistent**               | `EncryptedSharedPreferences` (AES-256, key managed by Android Keystore) stores `userId`, `username`, `role`, `loginAt`, `token`. Restored on app cold start. |
| **Session Expiry**           | Sessions do not expire automatically (offline app). Session is invalidated on explicit logout or password change. |
| **Step-Up Token Expiry**     | 5 minutes from issuance. Checked by `isStepUpValid(): Boolean`. |

---

## 3. User Management (Admin)

### 3.1 CreateUserUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Create a new user account in the local database. |
| **Input**         | `username: String`, `displayName: String`, `password: String`, `role: Role` |
| **Return Type**   | `Result<UserSummary>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Username: 3-64 chars, alphanumeric + underscore, unique. DisplayName: 1-128 chars. Password: same complexity rules as ChangePasswordUseCase. Role: must be a valid `Role` enum value. |
| **Room DAO**      | `UserDao.findByUsername(username: String): UserEntity?` (uniqueness check), `UserDao.insert(user: UserEntity): Long` |
| **Logic**         | 1. Check username uniqueness. 2. Generate random salt, hash password. 3. Insert user entity with `created_at = now()`, `deactivated_at = null`. 4. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `CONFLICT` — username already exists. `VALIDATION_ERROR` — input constraints violated. |

### 3.2 UpdateUserUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Update an existing user's display name or role. |
| **Input**         | `userId: Long`, `displayName: String?`, `role: Role?` |
| **Return Type**   | `Result<UserSummary>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | At least one field must be provided. DisplayName: 1-128 chars if provided. Cannot change the last remaining ADMIN's role to non-ADMIN. |
| **Room DAO**      | `UserDao.findById(userId: Long): UserEntity?`, `UserDao.update(user: UserEntity)`, `UserDao.countByRole(role: Role): Int` |
| **Logic**         | 1. Retrieve user by ID. 2. Validate at least one field changed. 3. If role change from ADMIN, verify at least one other ADMIN exists. 4. Apply updates, set `updated_at = now()`. 5. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `NOT_FOUND` — userId does not exist. `VALIDATION_ERROR` — constraints violated. `CONFLICT` — would remove the last ADMIN. |

### 3.3 DeactivateUserUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Soft-deactivate a user account, preventing future logins. |
| **Input**         | `userId: Long` |
| **Return Type**   | `Result<Unit>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Cannot deactivate self. Cannot deactivate the last remaining ADMIN. User must not already be deactivated. |
| **Room DAO**      | `UserDao.findById(userId: Long): UserEntity?`, `UserDao.countActiveByRole(role: Role): Int`, `UserDao.updateDeactivatedAt(userId: Long, deactivatedAt: Long)` |
| **Logic**         | 1. Verify target is not the caller. 2. If target is ADMIN, verify at least one other active ADMIN remains. 3. Set `deactivated_at = now()`. 4. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `NOT_FOUND` — user does not exist. `CONFLICT` — user already deactivated, or would remove last ADMIN. `VALIDATION_ERROR` — cannot deactivate self. |

### 3.4 ListUsersUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve a paginated list of users, optionally filtered. |
| **Input**         | `role: Role?`, `includeDeactivated: Boolean = false`, `searchQuery: String?`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<UserSummary>>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | `page >= 0`, `pageSize` in 1..100. |
| **Room DAO**      | `UserDao.findAllPaginated(role: Role?, includeDeactivated: Boolean, query: String?, limit: Int, offset: Int): List<UserEntity>`, `UserDao.countAll(role: Role?, includeDeactivated: Boolean, query: String?): Int` |
| **Logic**         | 1. Apply filters. 2. Calculate offset from page/pageSize. 3. Query DAO. 4. Map entities to `UserSummary` DTOs (excludes password hash/salt). |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `VALIDATION_ERROR` — invalid pagination parameters. |

### 3.5 GetUserUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve a single user's details by ID. |
| **Input**         | `userId: Long` |
| **Return Type**   | `Result<UserDetail>` |
| **Role Restriction** | `ADMIN` only (or the user themselves for own profile) |
| **Validation**    | `userId > 0` |
| **Room DAO**      | `UserDao.findById(userId: Long): UserEntity?` |
| **Logic**         | 1. Retrieve user. 2. Map to `UserDetail` DTO (includes created_at, last_login_at, excludes password hash/salt). |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `NOT_FOUND` — user does not exist. |

---

## 4. Source Rule Management (Admin)

### 4.1 CreateSourceRuleUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Define a new source parsing configuration including parsing selectors, allow/block URL patterns, pacing delays, and user-agent rotation pool. |
| **Input**         | `name: String`, `baseUrl: String`, `parsingConfig: ParsingConfig`, `allowPatterns: List<String>`, `blockPatterns: List<String>`, `pacingDelayMs: Long`, `userAgentPool: List<String>`, `enabled: Boolean = true` |
| **Return Type**   | `Result<SourceRule>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Name: 1-128 chars, unique. BaseUrl: valid URL format. PacingDelayMs: >= 1000 (minimum 1 second). UserAgentPool: at least 1 entry. ParsingConfig must include at least a title selector and body selector. AllowPatterns and BlockPatterns: valid regex patterns. |
| **Room DAO**      | `SourceRuleDao.findByName(name: String): SourceRuleEntity?`, `SourceRuleDao.insert(rule: SourceRuleEntity): Long` |
| **Logic**         | 1. Validate uniqueness of name. 2. Validate URL and regex patterns. 3. Serialize `ParsingConfig`, pattern lists, and user-agent pool as JSON strings in Room. 4. Set `rule_version = 1`, `created_at = now()`. 5. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `CONFLICT` — name already exists. `VALIDATION_ERROR` — invalid URL, regex, or missing required config fields. |

**ParsingConfig Structure:**

| Field              | Type     | Description |
|--------------------|----------|-------------|
| `titleSelector`    | String   | CSS/XPath selector for article title |
| `bodySelector`     | String   | CSS/XPath selector for article body |
| `dateSelector`     | String?  | Selector for publish date |
| `dateFormat`       | String?  | Date format pattern (e.g., `yyyy-MM-dd'T'HH:mm:ss`) |
| `authorSelector`   | String?  | Selector for author name |
| `imageSelector`    | String?  | Selector for featured image URL |
| `categorySelector` | String?  | Selector for article category/tags |

### 4.2 UpdateSourceRuleUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Update an existing source rule's configuration. Automatically increments `rule_version`. |
| **Input**         | `ruleId: Long`, `name: String?`, `baseUrl: String?`, `parsingConfig: ParsingConfig?`, `allowPatterns: List<String>?`, `blockPatterns: List<String>?`, `pacingDelayMs: Long?`, `userAgentPool: List<String>?` |
| **Return Type**   | `Result<SourceRule>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Same constraints as CreateSourceRuleUseCase for any provided field. At least one field must change. If name is changed, new name must be unique. |
| **Room DAO**      | `SourceRuleDao.findById(ruleId: Long): SourceRuleEntity?`, `SourceRuleDao.findByName(name: String): SourceRuleEntity?`, `SourceRuleDao.update(rule: SourceRuleEntity)` |
| **Logic**         | 1. Retrieve existing rule. 2. Apply changes. 3. Increment `rule_version`. 4. Set `updated_at = now()`. 5. Log audit event with old and new values. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `NOT_FOUND` — rule does not exist. `CONFLICT` — new name conflicts with existing rule. `VALIDATION_ERROR` — constraint violations. |

### 4.3 ListSourceRulesUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve all source rules, optionally filtered by enabled status. |
| **Input**         | `enabled: Boolean?`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<SourceRuleSummary>>` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST` |
| **Validation**    | `page >= 0`, `pageSize` in 1..100. |
| **Room DAO**      | `SourceRuleDao.findAllPaginated(enabled: Boolean?, limit: Int, offset: Int): List<SourceRuleEntity>`, `SourceRuleDao.countAll(enabled: Boolean?): Int` |
| **Logic**         | 1. Query with optional filter. 2. Map to summary DTOs (excludes full parsing config JSON). |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `VALIDATION_ERROR` — invalid pagination. |

### 4.4 GetSourceRuleUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve full details of a single source rule. |
| **Input**         | `ruleId: Long` |
| **Return Type**   | `Result<SourceRule>` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST` |
| **Validation**    | `ruleId > 0` |
| **Room DAO**      | `SourceRuleDao.findById(ruleId: Long): SourceRuleEntity?` |
| **Logic**         | 1. Retrieve and deserialize full config including parsing config, patterns, user-agent pool. |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `NOT_FOUND` — rule does not exist. |

### 4.5 ToggleSourceRuleUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Enable or disable a source rule. Disabled rules are skipped during ingestion. |
| **Input**         | `ruleId: Long`, `enabled: Boolean` |
| **Return Type**   | `Result<SourceRule>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | `ruleId > 0`. New state must differ from current state. |
| **Room DAO**      | `SourceRuleDao.findById(ruleId: Long): SourceRuleEntity?`, `SourceRuleDao.updateEnabled(ruleId: Long, enabled: Boolean, updatedAt: Long)` |
| **Logic**         | 1. Retrieve rule. 2. Toggle enabled flag. 3. Set `updated_at = now()`. 4. If disabling, cancel any pending WorkManager work for this source. 5. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `NOT_FOUND` — rule does not exist. `CONFLICT` — rule already in requested state. |

---

## 5. Ingestion Engine

### 5.1 ScheduleIngestionUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Configure WorkManager to periodically run ingestion for all enabled sources. Default interval: 6 hours. Constraints: device idle and charging. |
| **Input**         | `intervalHours: Int = 6`, `requiresCharging: Boolean = true`, `requiresIdle: Boolean = true`, `requiresStorageNotLow: Boolean = true` |
| **Return Type**   | `Result<WorkSchedule>` where `WorkSchedule(workId: UUID, intervalHours: Int, constraints: ConstraintsSummary, nextEstimatedRun: Long?)` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | `intervalHours` in 1..24. |
| **Room DAO**      | None (uses WorkManager API directly) |
| **Logic**         | 1. Build `Constraints` with provided flags. 2. Create `PeriodicWorkRequest` with given interval. 3. Enqueue as unique periodic work (`ExistingPeriodicWorkPolicy.UPDATE`). 4. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `VALIDATION_ERROR` — interval out of range. |

### 5.2 RunIngestionUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Execute a single ingestion run for a specific source. Parses content according to the source rule, validates articles, stores them in Room, and records an `IngestionJobRun`. |
| **Input**         | `sourceRuleId: Long` |
| **Return Type**   | `Result<IngestionJobRun>` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST` (manual trigger); WorkManager worker (automatic) |
| **Validation**    | Source rule must exist and be enabled. |
| **Room DAO**      | `SourceRuleDao.findById(ruleId: Long): SourceRuleEntity?`, `ArticleDao.insertAll(articles: List<ArticleEntity>): List<Long>`, `ArticleDao.findByExternalId(externalId: String): ArticleEntity?`, `IngestionJobRunDao.insert(run: IngestionJobRunEntity): Long` |
| **Logic**         | 1. Load source rule and parsing config. 2. Read from local source/file or cached content (offline context). 3. Parse articles using configured selectors. 4. For each article: check for duplicates by `externalId`, validate required fields. 5. Insert new articles (skip duplicates). 6. Create `IngestionJobRun` record: `sourceRuleId`, `startedAt`, `completedAt`, `status` (SUCCESS/PARTIAL/FAILURE), `articlesFound`, `articlesInserted`, `articlesDuplicate`, `errorDetails`. 7. Create `DataBatchVersion` for this run. |
| **Error Cases**   | `NOT_FOUND` — source rule does not exist. `FORBIDDEN` — source rule is disabled. `INTERNAL_ERROR` — parsing failure (details recorded in job run). |

### 5.3 RetryIngestionUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retry a failed ingestion with exponential backoff. Up to 3 attempts over approximately 15 minutes. |
| **Input**         | `jobRunId: Long` |
| **Return Type**   | `Result<IngestionJobRun>` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST` |
| **Validation**    | Job run must exist and have status FAILURE. Retry count must be < 3. |
| **Room DAO**      | `IngestionJobRunDao.findById(jobRunId: Long): IngestionJobRunEntity?`, `IngestionJobRunDao.updateRetryCount(jobRunId: Long, retryCount: Int, nextRetryAt: Long)` |
| **Logic**         | 1. Load failed job run. 2. Calculate backoff: attempt 1 = 1 min, attempt 2 = 4 min, attempt 3 = 10 min. 3. Schedule a OneTimeWorkRequest with initial delay. 4. Increment `retry_count` on the job run. 5. Worker calls `RunIngestionUseCase` upon execution. |
| **Error Cases**   | `NOT_FOUND` — job run does not exist. `CONFLICT` — job run is not in FAILURE status. `VALIDATION_ERROR` — max retries (3) already exhausted. |

### 5.4 GetIngestionLogsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Query ingestion job run history with filters. |
| **Input**         | `sourceRuleId: Long?`, `status: IngestionStatus?`, `dateFrom: Long?`, `dateTo: Long?`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<IngestionJobRun>>` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST` |
| **Validation**    | `page >= 0`, `pageSize` in 1..100. If both `dateFrom` and `dateTo` are provided, `dateFrom <= dateTo`. |
| **Room DAO**      | `IngestionJobRunDao.findFiltered(sourceRuleId: Long?, status: String?, dateFrom: Long?, dateTo: Long?, limit: Int, offset: Int): List<IngestionJobRunEntity>`, `IngestionJobRunDao.countFiltered(...): Int` |
| **Logic**         | 1. Apply filters. 2. Return paginated results ordered by `started_at DESC`. |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `VALIDATION_ERROR` — invalid date range or pagination. |

### 5.5 CheckFailureThresholdUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Count ingestion failures in the last 24 hours and trigger an alert notification if the count reaches or exceeds 5. |
| **Input**         | `sourceRuleId: Long?` (null = check all sources) |
| **Return Type**   | `Result<FailureThresholdResult>` where `FailureThresholdResult(failureCount: Int, thresholdBreached: Boolean, alertCreated: Boolean)` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST`; also invoked automatically by WorkManager after each ingestion run |
| **Validation**    | None beyond optional sourceRuleId existence. |
| **Room DAO**      | `IngestionJobRunDao.countFailuresSince(since: Long, sourceRuleId: Long?): Int` |
| **Logic**         | 1. Query failure count in last 24 hours. 2. If count >= 5, invoke `CreateNotificationUseCase` with template `INGESTION_FAILURE_ALERT` targeting all ADMIN users. 3. Return result. |
| **Error Cases**   | `INTERNAL_ERROR` — database query failure. |

---

## 6. Batch Tracking & Quality

### 6.1 CreateBatchVersionUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Create a `DataBatchVersion` record associated with an ingestion run. Tracks the batch of data that was produced. |
| **Input**         | `jobRunId: Long`, `sourceRuleId: Long`, `ruleVersion: Int`, `articleCount: Int` |
| **Return Type**   | `Result<DataBatchVersion>` |
| **Role Restriction** | Internal (called by `RunIngestionUseCase`). Not directly callable by ViewModels. |
| **Validation**    | `jobRunId` and `sourceRuleId` must reference existing records. `articleCount >= 0`. |
| **Room DAO**      | `DataBatchVersionDao.insert(batch: DataBatchVersionEntity): Long` |
| **Logic**         | 1. Generate `batchId` (UUID). 2. Insert with `created_at = now()`, `status = PENDING_VALIDATION`. |
| **Error Cases**   | `NOT_FOUND` — referenced job run or source rule missing. `VALIDATION_ERROR` — invalid article count. |

### 6.2 RecordLineageUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Record data lineage linking a source rule to a batch version to individual articles, enabling traceability. |
| **Input**         | `batchId: Long`, `articleIds: List<Long>` |
| **Return Type**   | `Result<Int>` (count of lineage records created) |
| **Role Restriction** | Internal (called by `RunIngestionUseCase`). |
| **Validation**    | `batchId` must exist. All `articleIds` must exist. |
| **Room DAO**      | `DataLineageDao.insertAll(lineage: List<DataLineageEntity>): List<Long>` |
| **Logic**         | 1. For each articleId, create a `DataLineageEntity(batchId, articleId, createdAt)`. 2. Bulk insert. |
| **Error Cases**   | `NOT_FOUND` — batch or article IDs invalid. `INTERNAL_ERROR` — insert failure. |

### 6.3 RunQualityValidationUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Validate all articles in a batch against quality rules. |
| **Input**         | `batchId: Long` |
| **Return Type**   | `Result<QualityValidationResult>` where `QualityValidationResult(batchId: Long, totalItems: Int, validItems: Int, invalidItems: Int, errors: List<QualityError>)` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST`; also invoked automatically after ingestion |
| **Validation**    | Batch must exist and have status `PENDING_VALIDATION`. |
| **Room DAO**      | `DataBatchVersionDao.findById(batchId: Long): DataBatchVersionEntity?`, `DataLineageDao.findArticleIdsByBatchId(batchId: Long): List<Long>`, `ArticleDao.findByIds(ids: List<Long>): List<ArticleEntity>`, `DataBatchVersionDao.updateStatus(batchId: Long, status: String, errorRate: Float, validatedAt: Long)` |
| **Quality Rules** | |

| Rule                          | Condition                                   | Error Code               |
|-------------------------------|---------------------------------------------|--------------------------|
| Publish time freshness        | `publishedAt` must be within the last 365 days from now | `STALE_PUBLISH_DATE`     |
| Price range (if merchandise)  | Price must be $0.01 - $9,999.99             | `PRICE_OUT_OF_RANGE`     |
| Inventory non-negative        | Inventory quantity must be >= 0             | `NEGATIVE_INVENTORY`     |
| Required fields present       | Title and body must be non-blank            | `MISSING_REQUIRED_FIELD` |
| No duplicate external IDs     | External ID must be unique within batch     | `DUPLICATE_EXTERNAL_ID`  |

| **Logic**         | 1. Load all articles in batch via lineage. 2. Apply each quality rule. 3. Collect all errors. 4. Calculate error rate: `invalidItems / totalItems`. 5. Update batch status to `VALIDATED` or `VALIDATION_FAILED`. 6. Invoke `CheckQualityAlertUseCase`. |
| **Error Cases**   | `NOT_FOUND` — batch does not exist. `CONFLICT` — batch already validated. `INTERNAL_ERROR` — unexpected validation failure. |

### 6.4 GetBatchMetricsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve quality and count metrics for a specific batch. |
| **Input**         | `batchId: Long` |
| **Return Type**   | `Result<BatchMetrics>` where `BatchMetrics(batchId: Long, totalItems: Int, validItems: Int, invalidItems: Int, errorRate: Float, status: String, createdAt: Long, validatedAt: Long?)` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST` |
| **Validation**    | `batchId > 0` |
| **Room DAO**      | `DataBatchVersionDao.findById(batchId: Long): DataBatchVersionEntity?` |
| **Logic**         | 1. Retrieve batch and map to metrics DTO. |
| **Error Cases**   | `NOT_FOUND` — batch does not exist. `FORBIDDEN` — caller lacks permission. |

### 6.5 CheckQualityAlertUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Check if a batch's error rate exceeds the 2% threshold and, if so, create an alert notification. |
| **Input**         | `batchId: Long` |
| **Return Type**   | `Result<QualityAlertResult>` where `QualityAlertResult(errorRate: Float, thresholdBreached: Boolean, alertCreated: Boolean)` |
| **Role Restriction** | Internal (called by `RunQualityValidationUseCase`). Also callable by `ADMIN`, `EDITOR_ANALYST`. |
| **Validation**    | Batch must exist and have been validated. |
| **Room DAO**      | `DataBatchVersionDao.findById(batchId: Long): DataBatchVersionEntity?` |
| **Logic**         | 1. Retrieve batch. 2. If `errorRate > 0.02` (2%), invoke `CreateNotificationUseCase` with template `QUALITY_ALERT` targeting ADMIN and EDITOR_ANALYST users. |
| **Error Cases**   | `NOT_FOUND` — batch does not exist. `CONFLICT` — batch not yet validated. |

---

## 7. Article Browsing

### 7.1 SearchArticlesUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Search and filter articles with full-text keyword matching, source, team, league, and date range filters. Uses a composite Room index on `(sourceId, publishedAt)` for efficient querying. |
| **Input**         | `sourceId: Long?`, `team: String?`, `league: String?`, `keyword: String?`, `dateFrom: Long?`, `dateTo: Long?`, `sortBy: ArticleSortField = PUBLISHED_AT`, `sortOrder: SortOrder = DESC`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<ArticleSummary>>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `page >= 0`, `pageSize` in 1..50. If both dates are provided, `dateFrom <= dateTo`. `keyword` is trimmed; if provided, must be 1-200 chars. |
| **Room DAO**      | `ArticleDao.searchPaginated(sourceId: Long?, team: String?, league: String?, keyword: String?, dateFrom: Long?, dateTo: Long?, sortBy: String, sortOrder: String, limit: Int, offset: Int): List<ArticleEntity>`, `ArticleDao.countSearch(...): Int` |
| **Logic**         | 1. Build dynamic query conditions. 2. Keyword search uses `LIKE '%keyword%'` on title and body fields. 3. Only return articles with `status = PUBLISHED` and `deleted_at IS NULL`. 4. Map to `ArticleSummary` (id, title, author, source, publishedAt, thumbnailPath, team, league). |
| **Error Cases**   | `VALIDATION_ERROR` — invalid parameters. `UNAUTHORIZED` — not authenticated. |

### 7.2 GetArticleDetailUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve the full content of a single article. |
| **Input**         | `articleId: Long` |
| **Return Type**   | `Result<ArticleDetail>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `articleId > 0`. Article must have `status = PUBLISHED` or caller is `EDITOR_ANALYST`/`ADMIN`. |
| **Room DAO**      | `ArticleDao.findById(articleId: Long): ArticleEntity?` |
| **Logic**         | 1. Retrieve article. 2. Check status/role permissions. 3. Map to `ArticleDetail` (includes full body, metadata, lineage info). |
| **Error Cases**   | `NOT_FOUND` — article does not exist or is soft-deleted. `FORBIDDEN` — article not published and caller is a regular user. |

### 7.3 SaveArticleOfflineUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Mark an article for offline access, ensuring full content is stored locally. |
| **Input**         | `articleId: Long` |
| **Return Type**   | `Result<SavedArticle>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `articleId > 0`. Article must exist and be published. Must not already be saved by this user. |
| **Room DAO**      | `ArticleDao.findById(articleId: Long): ArticleEntity?`, `SavedArticleDao.findByUserAndArticle(userId: Long, articleId: Long): SavedArticleEntity?`, `SavedArticleDao.insert(saved: SavedArticleEntity): Long` |
| **Logic**         | 1. Verify article exists and is published. 2. Check not already saved. 3. Insert `SavedArticleEntity(userId, articleId, savedAt)`. |
| **Error Cases**   | `NOT_FOUND` — article does not exist. `CONFLICT` — already saved. `FORBIDDEN` — article not published. |

### 7.4 ListSavedArticlesUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve all articles saved for offline access by the current user. |
| **Input**         | `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<ArticleSummary>>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `page >= 0`, `pageSize` in 1..50. |
| **Room DAO**      | `SavedArticleDao.findByUserPaginated(userId: Long, limit: Int, offset: Int): List<SavedArticleWithDetail>`, `SavedArticleDao.countByUser(userId: Long): Int` |
| **Logic**         | 1. Query saved articles for current user, joined with article details. 2. Return ordered by `savedAt DESC`. |
| **Error Cases**   | `UNAUTHORIZED` — not authenticated. `VALIDATION_ERROR` — invalid pagination. |

### 7.5 RemoveSavedArticleUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Remove an article from the user's saved/offline list. |
| **Input**         | `articleId: Long` |
| **Return Type**   | `Result<Unit>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `articleId > 0`. |
| **Room DAO**      | `SavedArticleDao.deleteByUserAndArticle(userId: Long, articleId: Long): Int` |
| **Logic**         | 1. Delete the saved article record. 2. Return success even if it was not saved (idempotent). |
| **Error Cases**   | `UNAUTHORIZED` — not authenticated. |

---

## 8. Cart Management

### 8.1 AddToCartUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Add a catalog item to the user's cart. Validates inventory availability. Creates a new `CartLineItem` or updates quantity if the item is already in the cart. |
| **Input**         | `catalogItemId: Long`, `quantity: Int` |
| **Return Type**   | `Result<Cart>` |
| **Role Restriction** | Any authenticated user (or guest with anonymous cart) |
| **Validation**    | `catalogItemId > 0`. `quantity` in 1..99. Catalog item must exist and not be soft-deleted. Available inventory must be >= requested quantity (considering existing cart quantities across all users). |
| **Room DAO**      | `CatalogItemDao.findById(catalogItemId: Long): CatalogItemEntity?`, `InventorySnapshotDao.findByCatalogItemId(catalogItemId: Long): InventorySnapshotEntity?`, `CartLineItemDao.findByCartAndCatalogItem(cartId: Long, catalogItemId: Long): CartLineItemEntity?`, `CartLineItemDao.insert(item: CartLineItemEntity): Long`, `CartLineItemDao.updateQuantity(lineItemId: Long, quantity: Int, updatedAt: Long)`, `CartDao.findOrCreateByUserId(userId: Long): CartEntity` |
| **Logic**         | 1. Retrieve or create cart for user. 2. Check catalog item exists. 3. Check inventory: `available = inventorySnapshot.quantity - reservedQuantity`. 4. If item already in cart, sum quantities and re-validate. 5. Insert or update line item. 6. Return updated cart. |
| **Error Cases**   | `NOT_FOUND` — catalog item does not exist. `INVENTORY_INSUFFICIENT` — not enough stock. `VALIDATION_ERROR` — invalid quantity. |

### 8.2 UpdateCartQuantityUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Update the quantity of an existing cart line item. Re-validates inventory. |
| **Input**         | `lineItemId: Long`, `newQuantity: Int` |
| **Return Type**   | `Result<Cart>` |
| **Role Restriction** | Any authenticated user (must own the cart) |
| **Validation**    | `lineItemId > 0`. `newQuantity` in 1..99. Line item must belong to the caller's cart. |
| **Room DAO**      | `CartLineItemDao.findById(lineItemId: Long): CartLineItemEntity?`, `CartDao.findById(cartId: Long): CartEntity?`, `InventorySnapshotDao.findByCatalogItemId(catalogItemId: Long): InventorySnapshotEntity?`, `CartLineItemDao.updateQuantity(lineItemId: Long, quantity: Int, updatedAt: Long)` |
| **Logic**         | 1. Verify line item exists and belongs to caller's cart. 2. Check inventory for new quantity. 3. Update quantity. 4. Return updated cart. |
| **Error Cases**   | `NOT_FOUND` — line item does not exist. `FORBIDDEN` — line item belongs to a different user's cart. `INVENTORY_INSUFFICIENT` — not enough stock. `VALIDATION_ERROR` — invalid quantity. |

### 8.3 RemoveFromCartUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Remove a line item from the user's cart. |
| **Input**         | `lineItemId: Long` |
| **Return Type**   | `Result<Cart>` |
| **Role Restriction** | Any authenticated user (must own the cart) |
| **Validation**    | `lineItemId > 0`. Line item must belong to the caller's cart. |
| **Room DAO**      | `CartLineItemDao.findById(lineItemId: Long): CartLineItemEntity?`, `CartLineItemDao.delete(lineItemId: Long)` |
| **Logic**         | 1. Verify ownership. 2. Delete line item. 3. Return updated cart. |
| **Error Cases**   | `NOT_FOUND` — line item does not exist. `FORBIDDEN` — not the owner. |

### 8.4 GetCartUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve the current user's cart with all line items and current prices. |
| **Input**         | None (uses current session userId) |
| **Return Type**   | `Result<Cart>` where `Cart(cartId: Long, userId: Long, lineItems: List<CartLineItemDetail>, subtotal: BigDecimal, itemCount: Int, updatedAt: Long)` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | None. |
| **Room DAO**      | `CartDao.findByUserId(userId: Long): CartEntity?`, `CartLineItemDao.findByCartIdWithDetails(cartId: Long): List<CartLineItemWithCatalogItem>` |
| **Logic**         | 1. Load cart and line items with joined catalog item details (name, current price, image). 2. Calculate subtotal. 3. Return cart DTO. If no cart exists, return empty cart. |
| **Error Cases**   | `UNAUTHORIZED` — not authenticated. |

### 8.5 MergeGuestCartUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Merge an anonymous/guest cart into the authenticated user's cart upon first login. Sums quantities for duplicate SKUs and re-validates inventory for all merged items. |
| **Input**         | `guestCartId: Long` |
| **Return Type**   | `Result<Cart>` |
| **Role Restriction** | Any authenticated user (invoked automatically at login) |
| **Validation**    | Guest cart must exist. |
| **Room DAO**      | `CartDao.findById(guestCartId: Long): CartEntity?`, `CartDao.findByUserId(userId: Long): CartEntity?`, `CartLineItemDao.findByCartId(cartId: Long): List<CartLineItemEntity>`, `InventorySnapshotDao.findByCatalogItemId(catalogItemId: Long): InventorySnapshotEntity?`, `CartLineItemDao.updateQuantity(...)`, `CartLineItemDao.insert(...)`, `CartLineItemDao.deleteByCartId(cartId: Long)`, `CartDao.delete(cartId: Long)` |
| **Logic**         | 1. Load guest cart items and authenticated user's cart items. 2. For each guest item: a. If SKU exists in user cart, sum quantities. b. If SKU is new, move to user cart. 3. Re-validate inventory for all affected items. If any exceeds inventory, cap at available quantity and include a warning in the result. 4. Delete guest cart and its remaining items. 5. Return merged cart. |
| **Error Cases**   | `NOT_FOUND` — guest cart does not exist. `INVENTORY_INSUFFICIENT` — inventory exceeded for one or more items (partial merge with warning). |

### 8.6 ClearCartUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Remove all items from the user's cart. |
| **Input**         | None (uses current session userId) |
| **Return Type**   | `Result<Unit>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | None. |
| **Room DAO**      | `CartDao.findByUserId(userId: Long): CartEntity?`, `CartLineItemDao.deleteByCartId(cartId: Long)` |
| **Logic**         | 1. Find user's cart. 2. Delete all line items. 3. Operation is idempotent (succeeds even if cart is empty). |
| **Error Cases**   | `UNAUTHORIZED` — not authenticated. |

---

## 9. Checkout & Pricing

### 9.1 StartCheckoutUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Initiate the checkout process. Locks current prices for 30 minutes, validates all cart item inventory, and calculates subtotal, applicable discounts, and tax. |
| **Input**         | `cartId: Long`, `shippingState: String` |
| **Return Type**   | `Result<CheckoutSession>` where `CheckoutSession(sessionId: Long, cartId: Long, lockedPrices: List<LockedPrice>, subtotal: BigDecimal, discountTotal: BigDecimal, taxAmount: BigDecimal, grandTotal: BigDecimal, priceLockExpiresAt: Long, appliedRules: List<AppliedPriceRule>, warnings: List<String>)` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | Cart must exist, belong to the caller, and have at least one item. `shippingState` must be a valid US state code (2-letter). All items must have sufficient inventory. |
| **Room DAO**      | `CartDao.findById(cartId: Long): CartEntity?`, `CartLineItemDao.findByCartIdWithDetails(cartId: Long): List<CartLineItemWithCatalogItem>`, `InventorySnapshotDao.findByCatalogItemIds(ids: List<Long>): List<InventorySnapshotEntity>`, `CheckoutSessionDao.insert(session: CheckoutSessionEntity): Long`, `LockedPriceDao.insertAll(prices: List<LockedPriceEntity>)` |
| **Logic**         | 1. Validate cart and ownership. 2. Check inventory for all items. 3. Snapshot current prices into `LockedPrice` records with `expires_at = now() + 30 min`. 4. Calculate subtotal from locked prices. 5. Invoke `ApplyPriceRulesUseCase` for discounts. 6. Invoke `CalculateTaxUseCase` for tax. 7. Compute `grandTotal = subtotal - discountTotal + taxAmount`. 8. Create `CheckoutSession` record. |
| **Error Cases**   | `NOT_FOUND` — cart does not exist. `FORBIDDEN` — cart belongs to another user. `VALIDATION_ERROR` — empty cart, invalid state code. `INVENTORY_INSUFFICIENT` — one or more items out of stock (details in error). |

### 9.2 ApplyCouponUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Validate and apply a coupon code to the active checkout session. |
| **Input**         | `checkoutSessionId: Long`, `couponCode: String` |
| **Return Type**   | `Result<CheckoutSession>` (updated totals) |
| **Role Restriction** | Any authenticated user (must own the checkout session) |
| **Validation**    | Coupon must exist in the database. Coupon must not be expired (`expires_at > now()` or null for no expiry). Coupon usage count must be below its limit (`current_uses < max_uses`). Only one coupon per order. Checkout session must not already have a coupon applied. |
| **Room DAO**      | `CouponDao.findByCode(code: String): CouponEntity?`, `CheckoutSessionDao.findById(sessionId: Long): CheckoutSessionEntity?`, `CheckoutSessionDao.updateCoupon(sessionId: Long, couponId: Long, discountAmount: BigDecimal)` |
| **Logic**         | 1. Validate coupon existence and constraints. 2. Calculate discount: if `discount_type = PERCENTAGE`, apply to subtotal; if `FIXED_AMOUNT`, apply fixed value. 3. Discount cannot exceed subtotal. 4. Recalculate tax on post-discount subtotal. 5. Update checkout session. |
| **Error Cases**   | `NOT_FOUND` — coupon code invalid. `CONFLICT` — coupon already applied. `VALIDATION_ERROR` — coupon expired or usage limit reached. `FORBIDDEN` — session belongs to another user. |

### 9.3 RemoveCouponUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Remove a previously applied coupon from the checkout session. |
| **Input**         | `checkoutSessionId: Long` |
| **Return Type**   | `Result<CheckoutSession>` (updated totals) |
| **Role Restriction** | Any authenticated user (must own the checkout session) |
| **Validation**    | Checkout session must exist and have a coupon applied. |
| **Room DAO**      | `CheckoutSessionDao.findById(sessionId: Long): CheckoutSessionEntity?`, `CheckoutSessionDao.removeCoupon(sessionId: Long)` |
| **Logic**         | 1. Remove coupon reference. 2. Recalculate totals (subtotal - price rule discounts + tax). 3. Update session. |
| **Error Cases**   | `NOT_FOUND` — session does not exist. `CONFLICT` — no coupon applied. `FORBIDDEN` — session belongs to another user. |

### 9.4 CalculateTaxUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Look up the applicable state tax rate and calculate tax on the taxable amount (subtotal after discounts). |
| **Input**         | `stateCode: String`, `taxableAmount: BigDecimal` |
| **Return Type**   | `Result<TaxCalculation>` where `TaxCalculation(stateCode: String, rate: BigDecimal, taxableAmount: BigDecimal, taxAmount: BigDecimal)` |
| **Role Restriction** | Internal (called by checkout flow). Not directly callable from ViewModel. |
| **Validation**    | `stateCode` must be a valid 2-letter US state code. `taxableAmount >= 0`. |
| **Room DAO**      | `StateTaxRateDao.findByStateCode(stateCode: String): StateTaxRateEntity?` |
| **Logic**         | 1. Look up state rate. 2. If no rate found, default to 0% (tax-exempt state). 3. `taxAmount = taxableAmount * rate`, rounded to 2 decimal places (HALF_UP). |
| **Error Cases**   | `VALIDATION_ERROR` — invalid state code or negative amount. |

### 9.5 ApplyPriceRulesUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Evaluate all active, configurable price rules against the current cart/checkout and apply matching discounts in priority order. |
| **Input**         | `checkoutSessionId: Long` |
| **Return Type**   | `Result<PriceRuleResult>` where `PriceRuleResult(appliedRules: List<AppliedPriceRule>, totalDiscount: BigDecimal)` |
| **Role Restriction** | Internal (called by checkout flow). |
| **Validation**    | Checkout session must exist. |
| **Room DAO**      | `PriceRuleDao.findAllActiveOrderedByPriority(): List<PriceRuleEntity>`, `CheckoutSessionDao.findById(sessionId: Long): CheckoutSessionEntity?`, `CartLineItemDao.findByCartIdWithDetails(cartId: Long): List<CartLineItemWithCatalogItem>` |
| **Logic**         | 1. Load all active price rules ordered by `priority ASC` (lower number = higher priority). 2. For each rule, evaluate condition (e.g., `subtotal > 50.00`). 3. If condition met, apply discount (e.g., 10% off). 4. Rules are cumulative but total discount cannot exceed subtotal. 5. Record each applied rule. |

**Example Price Rule:**

| Field        | Example Value |
|--------------|---------------|
| `name`       | "10% off orders over $50" |
| `condition`  | `{"field": "subtotal", "operator": "gt", "value": 50.00}` |
| `discount`   | `{"type": "PERCENTAGE", "value": 10.0}` |
| `priority`   | 1 |
| `active`     | true |
| `starts_at`  | 1680000000000 |
| `ends_at`    | null (no end) |

| **Error Cases**   | `NOT_FOUND` — checkout session does not exist. `INTERNAL_ERROR` — rule evaluation failure. |

### 9.6 ConfirmCheckoutUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Finalize the checkout: perform final validation, create an order record, decrement inventory for all items, and clear the cart. |
| **Input**         | `checkoutSessionId: Long` |
| **Return Type**   | `Result<Order>` where `Order(orderId: Long, orderNumber: String, userId: Long, lineItems: List<OrderLineItem>, subtotal: BigDecimal, discountTotal: BigDecimal, taxAmount: BigDecimal, grandTotal: BigDecimal, couponCode: String?, status: OrderStatus, createdAt: Long)` |
| **Role Restriction** | Any authenticated user (must own the checkout session) |
| **Validation**    | Checkout session must exist and be owned by caller. Price lock must not be expired (if > 30 min, return `PRICE_LOCK_EXPIRED` and require `ReconfirmPricesUseCase`). All items must still have sufficient inventory. |
| **Room DAO**      | `CheckoutSessionDao.findById(sessionId: Long): CheckoutSessionEntity?`, `LockedPriceDao.findBySessionId(sessionId: Long): List<LockedPriceEntity>`, `InventorySnapshotDao.findByCatalogItemIds(ids: List<Long>): List<InventorySnapshotEntity>`, `InventorySnapshotDao.decrementQuantity(catalogItemId: Long, amount: Int)`, `OrderDao.insert(order: OrderEntity): Long`, `OrderLineItemDao.insertAll(items: List<OrderLineItemEntity>)`, `CartLineItemDao.deleteByCartId(cartId: Long)`, `CouponDao.incrementUsage(couponId: Long)`, `CheckoutSessionDao.markCompleted(sessionId: Long, completedAt: Long)` |
| **Logic**         | 1. Validate session and price lock. 2. Re-check inventory. 3. Within a Room `@Transaction`: a. Create `OrderEntity` with generated order number. b. Create `OrderLineItemEntity` for each item (with locked prices). c. Decrement `InventorySnapshot` quantities. d. If coupon was applied, increment its usage count. e. Delete all cart line items. f. Mark checkout session as completed. 4. Log audit event. |
| **Error Cases**   | `NOT_FOUND` — session does not exist. `FORBIDDEN` — not the owner. `PRICE_LOCK_EXPIRED` — prices locked more than 30 minutes ago. `INVENTORY_INSUFFICIENT` — stock changed since checkout started. `INTERNAL_ERROR` — transaction failure. |

### 9.7 ReconfirmPricesUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | When a price lock has expired (checkout session older than 30 minutes), recalculate all prices, discounts, and tax, and return updated totals for user confirmation. |
| **Input**         | `checkoutSessionId: Long` |
| **Return Type**   | `Result<PriceReconfirmation>` where `PriceReconfirmation(sessionId: Long, previousTotal: BigDecimal, newTotal: BigDecimal, changedItems: List<PriceChange>, newPriceLockExpiresAt: Long)` |
| **Role Restriction** | Any authenticated user (must own the checkout session) |
| **Validation**    | Session must exist. Price lock must actually be expired. |
| **Room DAO**      | `CheckoutSessionDao.findById(sessionId: Long): CheckoutSessionEntity?`, `LockedPriceDao.findBySessionId(sessionId: Long): List<LockedPriceEntity>`, `CatalogItemDao.findByIds(ids: List<Long>): List<CatalogItemEntity>`, `LockedPriceDao.deleteBySessionId(sessionId: Long)`, `LockedPriceDao.insertAll(prices: List<LockedPriceEntity>)` |
| **Logic**         | 1. Load current catalog prices. 2. Compare with previously locked prices. 3. Delete old locked prices and create new ones with fresh 30-minute expiry. 4. Recalculate discounts and tax. 5. Return comparison showing what changed. |
| **Error Cases**   | `NOT_FOUND` — session does not exist. `CONFLICT` — price lock has not expired yet (no reconfirmation needed). `FORBIDDEN` — not the owner. |

---

## 10. Catalog & Inventory

### 10.1 ListCatalogItemsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Browse merchandise catalog items filtered by team, league, or category. |
| **Input**         | `team: String?`, `league: String?`, `category: String?`, `keyword: String?`, `minPrice: BigDecimal?`, `maxPrice: BigDecimal?`, `inStockOnly: Boolean = false`, `sortBy: CatalogSortField = NAME`, `sortOrder: SortOrder = ASC`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<CatalogItemSummary>>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `page >= 0`, `pageSize` in 1..50. If price range given, `minPrice <= maxPrice` and both >= 0. |
| **Room DAO**      | `CatalogItemDao.searchPaginated(team: String?, league: String?, category: String?, keyword: String?, minPrice: BigDecimal?, maxPrice: BigDecimal?, inStockOnly: Boolean, sortBy: String, sortOrder: String, limit: Int, offset: Int): List<CatalogItemWithInventory>`, `CatalogItemDao.countSearch(...): Int` |
| **Logic**         | 1. Build filtered query. 2. Join with `InventorySnapshot` to get availability. 3. Exclude soft-deleted items. 4. Return summaries with price, availability flag, thumbnail. |
| **Error Cases**   | `VALIDATION_ERROR` — invalid parameters. `UNAUTHORIZED` — not authenticated. |

### 10.2 GetCatalogItemUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve full details of a single catalog item including inventory status. |
| **Input**         | `catalogItemId: Long` |
| **Return Type**   | `Result<CatalogItemDetail>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `catalogItemId > 0`. |
| **Room DAO**      | `CatalogItemDao.findByIdWithInventory(catalogItemId: Long): CatalogItemWithInventory?` |
| **Logic**         | 1. Retrieve item with inventory join. 2. Map to detail DTO (name, description, price, images, team, league, category, SKU, available quantity). |
| **Error Cases**   | `NOT_FOUND` — item does not exist or is soft-deleted. |

### 10.3 UpdateInventoryUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Adjust inventory quantities for a catalog item. Used for manual stock corrections. |
| **Input**         | `catalogItemId: Long`, `newQuantity: Int`, `reason: String` |
| **Return Type**   | `Result<InventorySnapshot>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | `catalogItemId > 0`. `newQuantity >= 0`. `reason` must be 1-500 chars. |
| **Room DAO**      | `CatalogItemDao.findById(catalogItemId: Long): CatalogItemEntity?`, `InventorySnapshotDao.findByCatalogItemId(catalogItemId: Long): InventorySnapshotEntity?`, `InventorySnapshotDao.updateQuantity(catalogItemId: Long, quantity: Int, updatedAt: Long)` |
| **Logic**         | 1. Verify catalog item exists. 2. Update inventory snapshot. 3. Log audit event with previous quantity, new quantity, and reason. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `NOT_FOUND` — catalog item does not exist. `VALIDATION_ERROR` — negative quantity or missing reason. |

### 10.4 CheckInventoryUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Check whether the available inventory for a catalog item meets the requested quantity. |
| **Input**         | `catalogItemId: Long`, `requestedQuantity: Int` |
| **Return Type**   | `Result<InventoryCheck>` where `InventoryCheck(catalogItemId: Long, available: Int, requested: Int, sufficient: Boolean)` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `catalogItemId > 0`, `requestedQuantity > 0`. |
| **Room DAO**      | `InventorySnapshotDao.findByCatalogItemId(catalogItemId: Long): InventorySnapshotEntity?` |
| **Logic**         | 1. Retrieve current inventory. 2. Compare available vs. requested. 3. Return result (does not modify anything). |
| **Error Cases**   | `NOT_FOUND` — catalog item does not exist. `VALIDATION_ERROR` — invalid parameters. |

---

## 11. Notifications

### 11.1 CreateNotificationUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Render a notification from a template with variable substitution and queue it for delivery to one or more users. |
| **Input**         | `templateId: Long`, `recipientUserIds: List<Long>`, `variables: Map<String, String>` |
| **Return Type**   | `Result<List<Notification>>` |
| **Role Restriction** | Internal (called by system processes like ingestion alerts, quality alerts). `ADMIN` can trigger manually. |
| **Validation**    | Template must exist. All required template variables must be provided. All recipient user IDs must reference active users. |
| **Room DAO**      | `NotificationTemplateDao.findById(templateId: Long): NotificationTemplateEntity?`, `UserDao.findByIds(userIds: List<Long>): List<UserEntity>`, `NotificationDao.insertAll(notifications: List<NotificationEntity>): List<Long>` |
| **Logic**         | 1. Load template. 2. Validate all required variables are present. 3. Render title and body by replacing `{{variable_name}}` placeholders. 4. Create a `NotificationEntity` for each recipient with `status = PENDING`, `created_at = now()`. 5. Invoke `DeliverNotificationUseCase` for each. |
| **Error Cases**   | `NOT_FOUND` — template or user IDs not found. `VALIDATION_ERROR` — missing required variables. |

### 11.2 DeliverNotificationUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Mark a notification as delivered. In this offline-first app, "delivery" means the notification is available in the user's inbox. Retries on failure. |
| **Input**         | `notificationId: Long` |
| **Return Type**   | `Result<Notification>` |
| **Role Restriction** | Internal |
| **Validation**    | Notification must exist and have status `PENDING` or `FAILED`. |
| **Room DAO**      | `NotificationDao.findById(notificationId: Long): NotificationEntity?`, `NotificationDao.updateStatus(notificationId: Long, status: String, deliveredAt: Long?)` |
| **Logic**         | 1. Set status to `DELIVERED` and `delivered_at = now()`. 2. On failure (e.g., DB error), set status to `FAILED` and schedule retry via WorkManager (up to 3 attempts). |
| **Error Cases**   | `NOT_FOUND` — notification does not exist. `CONFLICT` — notification already delivered. `INTERNAL_ERROR` — delivery failure (will retry). |

### 11.3 ListNotificationsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve the current user's notification inbox. |
| **Input**         | `unreadOnly: Boolean = false`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<Notification>>` |
| **Role Restriction** | Any authenticated user |
| **Validation**    | `page >= 0`, `pageSize` in 1..50. |
| **Room DAO**      | `NotificationDao.findByUserPaginated(userId: Long, unreadOnly: Boolean, limit: Int, offset: Int): List<NotificationEntity>`, `NotificationDao.countByUser(userId: Long, unreadOnly: Boolean): Int` |
| **Logic**         | 1. Query notifications for current user, ordered by `created_at DESC`. 2. Only include notifications with `status = DELIVERED`. |
| **Error Cases**   | `UNAUTHORIZED` — not authenticated. `VALIDATION_ERROR` — invalid pagination. |

### 11.4 MarkNotificationReadUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Mark a notification as read. |
| **Input**         | `notificationId: Long` |
| **Return Type**   | `Result<Unit>` |
| **Role Restriction** | Any authenticated user (must be the recipient) |
| **Validation**    | Notification must exist and belong to the current user. |
| **Room DAO**      | `NotificationDao.findById(notificationId: Long): NotificationEntity?`, `NotificationDao.markRead(notificationId: Long, readAt: Long)` |
| **Logic**         | 1. Verify ownership. 2. Set `read_at = now()`. Idempotent (re-marking is a no-op). |
| **Error Cases**   | `NOT_FOUND` — notification does not exist. `FORBIDDEN` — notification belongs to another user. |

### 11.5 ManageTemplatesUseCase (Admin)

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Create, read, update, or delete notification templates. |
| **Input (Create)** | `name: String`, `titleTemplate: String`, `bodyTemplate: String`, `requiredVariables: List<String>` |
| **Input (Update)** | `templateId: Long`, `name: String?`, `titleTemplate: String?`, `bodyTemplate: String?`, `requiredVariables: List<String>?` |
| **Input (Delete)** | `templateId: Long` |
| **Input (List)**   | `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<NotificationTemplate>` (Create/Update), `Result<Unit>` (Delete), `Result<PaginatedList<NotificationTemplate>>` (List) |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Name: 1-128 chars, unique. Templates must contain valid `{{variable}}` syntax. Required variables must match placeholders found in title and body templates. |
| **Room DAO**      | `NotificationTemplateDao.insert(...)`, `NotificationTemplateDao.update(...)`, `NotificationTemplateDao.delete(templateId: Long)`, `NotificationTemplateDao.findAllPaginated(limit: Int, offset: Int): List<NotificationTemplateEntity>` |
| **Logic**         | 1. Validate input. 2. For delete: soft-delete (`deleted_at = now()`). Templates referenced by existing notifications cannot be hard-deleted. 3. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `NOT_FOUND` — template does not exist. `CONFLICT` — name already taken. `VALIDATION_ERROR` — template syntax errors or mismatched variables. |

---

## 12. Editor/Analyst Tools

### 12.1 ReviewIngestionBatchUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve detailed quality metrics and individual failure details for an ingestion batch. |
| **Input**         | `batchId: Long` |
| **Return Type**   | `Result<BatchReview>` where `BatchReview(batchId: Long, sourceName: String, ruleVersion: Int, metrics: BatchMetrics, errors: List<QualityError>, articles: List<ArticleSummary>)` |
| **Role Restriction** | `EDITOR_ANALYST`, `ADMIN` |
| **Validation**    | `batchId > 0`. |
| **Room DAO**      | `DataBatchVersionDao.findById(batchId: Long): DataBatchVersionEntity?`, `DataLineageDao.findArticleIdsByBatchId(batchId: Long): List<Long>`, `ArticleDao.findByIds(ids: List<Long>): List<ArticleEntity>`, `SourceRuleDao.findById(sourceRuleId: Long): SourceRuleEntity?` |
| **Logic**         | 1. Load batch with its source rule name and version. 2. Load all articles in the batch via lineage. 3. Include quality validation errors if the batch has been validated. |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `NOT_FOUND` — batch does not exist. |

### 12.2 ApproveArticleUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Set an article's status to `PUBLISHED`, making it visible to regular users. |
| **Input**         | `articleId: Long` |
| **Return Type**   | `Result<ArticleDetail>` |
| **Role Restriction** | `EDITOR_ANALYST`, `ADMIN` |
| **Validation**    | Article must exist and have status `PENDING_REVIEW` or `DRAFT`. Cannot approve an already published or retracted article. |
| **Room DAO**      | `ArticleDao.findById(articleId: Long): ArticleEntity?`, `ArticleDao.updateStatus(articleId: Long, status: String, updatedAt: Long, updatedBy: Long)` |
| **Logic**         | 1. Verify article status allows transition to PUBLISHED. 2. Update status. 3. Set `published_at = now()` if not already set. 4. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `NOT_FOUND` — article does not exist. `CONFLICT` — invalid status transition. |

### 12.3 RejectArticleUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Set an article's status to `RETRACTED`, removing it from user-facing browsing. |
| **Input**         | `articleId: Long`, `reason: String` |
| **Return Type**   | `Result<ArticleDetail>` |
| **Role Restriction** | `EDITOR_ANALYST`, `ADMIN` |
| **Validation**    | Article must exist. `reason` must be 1-1000 chars. Cannot retract an already retracted article. |
| **Room DAO**      | `ArticleDao.findById(articleId: Long): ArticleEntity?`, `ArticleDao.updateStatus(articleId: Long, status: String, updatedAt: Long, updatedBy: Long)`, `ArticleDao.updateRetractionReason(articleId: Long, reason: String)` |
| **Logic**         | 1. Verify article is not already retracted. 2. Set status to `RETRACTED`. 3. Store retraction reason. 4. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `NOT_FOUND` — article does not exist. `CONFLICT` — already retracted. `VALIDATION_ERROR` — missing or too long reason. |

### 12.4 CurateArticleUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Tag, categorize, and feature articles for editorial curation. |
| **Input**         | `articleId: Long`, `tags: List<String>?`, `categories: List<String>?`, `featured: Boolean?` |
| **Return Type**   | `Result<ArticleDetail>` |
| **Role Restriction** | `EDITOR_ANALYST`, `ADMIN` |
| **Validation**    | `articleId > 0`. At least one curation field must be provided. Tags: each 1-50 chars, max 20 tags. Categories: each 1-100 chars, max 10 categories. |
| **Room DAO**      | `ArticleDao.findById(articleId: Long): ArticleEntity?`, `ArticleTagDao.deleteByArticleId(articleId: Long)`, `ArticleTagDao.insertAll(tags: List<ArticleTagEntity>)`, `ArticleCategoryDao.deleteByArticleId(articleId: Long)`, `ArticleCategoryDao.insertAll(categories: List<ArticleCategoryEntity>)`, `ArticleDao.updateFeatured(articleId: Long, featured: Boolean, updatedAt: Long)` |
| **Logic**         | 1. Verify article exists. 2. If tags provided, replace existing tags. 3. If categories provided, replace existing categories. 4. If featured flag provided, update. 5. Set `updated_at = now()`. 6. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `NOT_FOUND` — article does not exist. `VALIDATION_ERROR` — constraint violations. |

### 12.5 ListFailedIngestionsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Retrieve failed ingestion runs with filtering for editorial review. |
| **Input**         | `sourceRuleId: Long?`, `dateFrom: Long?`, `dateTo: Long?`, `errorType: String?`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<FailedIngestionSummary>>` where `FailedIngestionSummary(jobRunId: Long, sourceName: String, startedAt: Long, errorType: String, errorDetails: String, retryCount: Int)` |
| **Role Restriction** | `EDITOR_ANALYST`, `ADMIN` |
| **Validation**    | `page >= 0`, `pageSize` in 1..100. Valid date range if both provided. |
| **Room DAO**      | `IngestionJobRunDao.findFailedFiltered(sourceRuleId: Long?, dateFrom: Long?, dateTo: Long?, errorType: String?, limit: Int, offset: Int): List<IngestionJobRunWithSource>`, `IngestionJobRunDao.countFailed(...): Int` |
| **Logic**         | 1. Query job runs with `status = FAILURE`, applying filters. 2. Join with source rule for name. 3. Order by `started_at DESC`. |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `VALIDATION_ERROR` — invalid parameters. |

---

## 13. Audit

### 13.1 LogAuditEventUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Record an audit trail entry for a significant user or system action. |
| **Input**         | `action: AuditAction`, `targetType: String`, `targetId: Long?`, `detail: String?` |
| **Return Type**   | `Result<Unit>` |
| **Role Restriction** | Internal (called by all other UseCases that require auditing). Not directly invoked from ViewModels. |
| **Validation**    | `action` must be a valid `AuditAction` enum. `targetType` must be non-blank, max 64 chars. `detail` max 2000 chars. |
| **Room DAO**      | `AuditEventDao.insert(event: AuditEventEntity): Long` |
| **Logic**         | 1. Capture current user ID from `SessionHolder` (or "SYSTEM" for automated processes). 2. Create `AuditEventEntity(userId, action, targetType, targetId, detail, ipAddress = null (local app), timestamp = now())`. 3. Insert. |
| **Error Cases**   | `INTERNAL_ERROR` — insert failure (audit logging failures are silently logged to Logcat but never block the original operation). |

**AuditAction Enum Values:**

`LOGIN`, `LOGOUT`, `PASSWORD_CHANGE`, `USER_CREATE`, `USER_UPDATE`, `USER_DEACTIVATE`, `SOURCE_RULE_CREATE`, `SOURCE_RULE_UPDATE`, `SOURCE_RULE_TOGGLE`, `INGESTION_SCHEDULE`, `INGESTION_RUN`, `ARTICLE_APPROVE`, `ARTICLE_REJECT`, `ARTICLE_CURATE`, `ORDER_CREATE`, `INVENTORY_UPDATE`, `COUPON_CREATE`, `COUPON_UPDATE`, `PRICE_RULE_CREATE`, `PRICE_RULE_UPDATE`, `TAX_RATE_UPDATE`, `TEMPLATE_CREATE`, `TEMPLATE_UPDATE`, `TEMPLATE_DELETE`, `BACKUP_CREATE`, `BACKUP_RESTORE`, `AUDIT_EXPORT`

### 13.2 QueryAuditEventsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Search and filter audit events for review. |
| **Input**         | `userId: Long?`, `action: AuditAction?`, `targetType: String?`, `dateFrom: Long?`, `dateTo: Long?`, `page: Int = 0`, `pageSize: Int = 50` |
| **Return Type**   | `Result<PaginatedList<AuditEvent>>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | `page >= 0`, `pageSize` in 1..200. Valid date range if both provided. |
| **Room DAO**      | `AuditEventDao.findFiltered(userId: Long?, action: String?, targetType: String?, dateFrom: Long?, dateTo: Long?, limit: Int, offset: Int): List<AuditEventEntity>`, `AuditEventDao.countFiltered(...): Int` |
| **Logic**         | 1. Apply filters. 2. Order by `timestamp DESC`. 3. Map to DTOs with user display name. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `VALIDATION_ERROR` — invalid parameters. |

### 13.3 ExportAuditEventsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Export filtered audit events to a CSV file on local storage. Requires step-up authentication. |
| **Input**         | `stepUpToken: String`, `userId: Long?`, `action: AuditAction?`, `dateFrom: Long?`, `dateTo: Long?` |
| **Return Type**   | `Result<ExportResult>` where `ExportResult(filePath: String, recordCount: Int, exportedAt: Long)` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Step-up token must be valid and not expired. Date range must be reasonable (max 1 year span). |
| **Room DAO**      | `AuditEventDao.findFilteredAll(userId: Long?, action: String?, dateFrom: Long?, dateTo: Long?): List<AuditEventEntity>` |
| **Logic**         | 1. Validate step-up token via `SessionHolder.isStepUpValid(token)`. 2. Query all matching audit events (no pagination, full export). 3. Write to CSV file in app's `files/exports/` directory with format `audit_export_YYYYMMDD_HHmmss.csv`. 4. CSV columns: `timestamp, user, action, target_type, target_id, detail`. 5. Log audit event for the export itself. |
| **Error Cases**   | `STEP_UP_REQUIRED` — step-up token missing or expired. `FORBIDDEN` — caller is not ADMIN. `VALIDATION_ERROR` — date range exceeds 1 year. `INTERNAL_ERROR` — file write failure. |

---

## 14. Backup & Restore

### 14.1 CreateBackupUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Export the entire Room database to a local file and compute a SHA-256 checksum for integrity verification. Requires step-up authentication. |
| **Input**         | `stepUpToken: String`, `description: String?` |
| **Return Type**   | `Result<BackupRecord>` where `BackupRecord(backupId: Long, filePath: String, fileSizeBytes: Long, sha256Checksum: String, description: String?, createdAt: Long, createdBy: Long)` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Step-up token must be valid and not expired. `description` max 500 chars. Sufficient local storage space (at least 2x current DB size). |
| **Room DAO**      | `BackupRecordDao.insert(record: BackupRecordEntity): Long` |
| **Logic**         | 1. Validate step-up token. 2. Close Room database checkpoint (`PRAGMA wal_checkpoint(FULL)`). 3. Copy database file to `files/backups/backup_YYYYMMDD_HHmmss.db`. 4. Compute SHA-256 of the copied file. 5. Record in `BackupRecord` table with checksum, size, path. 6. Log audit event. |
| **Error Cases**   | `STEP_UP_REQUIRED` — step-up token missing or expired. `FORBIDDEN` — caller is not ADMIN. `INTERNAL_ERROR` — file copy or checksum failure. `VALIDATION_ERROR` — insufficient storage space. |

### 14.2 ListBackupsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | List all available backup records. |
| **Input**         | `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PaginatedList<BackupRecord>>` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | `page >= 0`, `pageSize` in 1..50. |
| **Room DAO**      | `BackupRecordDao.findAllPaginated(limit: Int, offset: Int): List<BackupRecordEntity>`, `BackupRecordDao.countAll(): Int` |
| **Logic**         | 1. Query ordered by `created_at DESC`. 2. Verify each backup file still exists on disk, mark missing ones. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. |

### 14.3 RestoreBackupUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Restore the Room database from a backup file after verifying the SHA-256 checksum. Requires step-up authentication and explicit user confirmation. |
| **Input**         | `stepUpToken: String`, `backupId: Long`, `userConfirmed: Boolean` |
| **Return Type**   | `Result<RestoreResult>` where `RestoreResult(backupId: Long, restoredAt: Long, previousDbSizeBytes: Long, restoredDbSizeBytes: Long)` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Step-up token must be valid. `userConfirmed` must be `true`. Backup file must exist on disk. Checksum must match. |
| **Room DAO**      | `BackupRecordDao.findById(backupId: Long): BackupRecordEntity?` |
| **Logic**         | 1. Validate step-up token. 2. Verify `userConfirmed == true`. 3. Load backup record, verify file exists. 4. Compute SHA-256 of the backup file and compare with stored checksum. 5. Close Room database. 6. Create an automatic pre-restore backup of the current DB. 7. Replace database file with backup. 8. Reopen Room database. 9. Verify integrity (`PRAGMA integrity_check`). 10. Force re-login (clear session). |
| **Error Cases**   | `STEP_UP_REQUIRED` — step-up token missing or expired. `FORBIDDEN` — caller is not ADMIN. `VALIDATION_ERROR` — `userConfirmed` is false, or checksum mismatch. `NOT_FOUND` — backup record or file does not exist. `INTERNAL_ERROR` — restore failure (attempt to rollback to pre-restore backup). |

---

## 15. Price Rules & Tax (Admin)

### 15.1 ManagePriceRulesUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Create, read, update, or delete configurable price rules with priority ordering. |
| **Input (Create)** | `name: String`, `condition: RuleCondition`, `discount: RuleDiscount`, `priority: Int`, `active: Boolean = true`, `startsAt: Long?`, `endsAt: Long?` |
| **Input (Update)** | `ruleId: Long`, `name: String?`, `condition: RuleCondition?`, `discount: RuleDiscount?`, `priority: Int?`, `active: Boolean?`, `startsAt: Long?`, `endsAt: Long?` |
| **Input (Delete)** | `ruleId: Long` |
| **Input (List)**   | `activeOnly: Boolean = false`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<PriceRule>` (Create/Update), `Result<Unit>` (Delete), `Result<PaginatedList<PriceRule>>` (List) |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Name: 1-128 chars, unique. Priority: 1-1000 (lower = higher priority). Condition must have valid field, operator, and value. Discount value: > 0; percentage must be <= 100. If `endsAt` provided, must be > `startsAt`. |
| **Room DAO**      | `PriceRuleDao.insert(rule: PriceRuleEntity): Long`, `PriceRuleDao.update(rule: PriceRuleEntity)`, `PriceRuleDao.softDelete(ruleId: Long, deletedAt: Long)`, `PriceRuleDao.findAllPaginated(activeOnly: Boolean, limit: Int, offset: Int): List<PriceRuleEntity>` |
| **Logic**         | 1. Validate all fields. 2. On priority change, no automatic reordering of other rules (admin manages priorities manually). 3. Soft-delete on deletion. 4. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `NOT_FOUND` — rule does not exist. `CONFLICT` — name already exists. `VALIDATION_ERROR` — constraint violations. |

**RuleCondition Structure:**

| Field      | Type   | Description                        | Example Values            |
|------------|--------|------------------------------------|---------------------------|
| `field`    | String | Cart/order field to evaluate       | `subtotal`, `item_count`, `category` |
| `operator` | String | Comparison operator                | `gt`, `gte`, `lt`, `lte`, `eq`, `contains` |
| `value`    | String | Value to compare against (stringified) | `"50.00"`, `"3"`, `"jerseys"` |

**RuleDiscount Structure:**

| Field   | Type       | Description              |
|---------|------------|--------------------------|
| `type`  | String     | `PERCENTAGE` or `FIXED_AMOUNT` |
| `value` | BigDecimal | Discount value           |

### 15.2 ManageCouponsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Create, read, update, or delete coupon codes. |
| **Input (Create)** | `code: String`, `description: String`, `discountType: DiscountType`, `discountValue: BigDecimal`, `maxUses: Int?`, `expiresAt: Long?` |
| **Input (Update)** | `couponId: Long`, `description: String?`, `discountType: DiscountType?`, `discountValue: BigDecimal?`, `maxUses: Int?`, `expiresAt: Long?`, `active: Boolean?` |
| **Input (Delete)** | `couponId: Long` |
| **Input (List)**   | `activeOnly: Boolean = false`, `page: Int = 0`, `pageSize: Int = 20` |
| **Return Type**   | `Result<Coupon>` (Create/Update), `Result<Unit>` (Delete), `Result<PaginatedList<Coupon>>` (List) |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | Code: 3-32 chars, alphanumeric + hyphens, unique, uppercase. Description: 1-256 chars. DiscountValue: > 0; percentage must be <= 100. MaxUses: if provided, > 0. ExpiresAt: if provided, must be in the future. |
| **Room DAO**      | `CouponDao.insert(coupon: CouponEntity): Long`, `CouponDao.update(coupon: CouponEntity)`, `CouponDao.softDelete(couponId: Long, deletedAt: Long)`, `CouponDao.findByCode(code: String): CouponEntity?`, `CouponDao.findAllPaginated(activeOnly: Boolean, limit: Int, offset: Int): List<CouponEntity>` |
| **Logic**         | 1. Normalize code to uppercase. 2. Validate uniqueness. 3. Soft-delete on deletion. 4. Log audit event. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `CONFLICT` — code already exists. `NOT_FOUND` — coupon does not exist. `VALIDATION_ERROR` — constraints violated. |

### 15.3 ManageStateTaxRatesUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Create, read, update, or delete US state tax rate entries. |
| **Input (Create)** | `stateCode: String`, `stateName: String`, `rate: BigDecimal` |
| **Input (Update)** | `stateCode: String`, `rate: BigDecimal` |
| **Input (Delete)** | `stateCode: String` |
| **Input (List)**   | None (returns all states) |
| **Return Type**   | `Result<StateTaxRate>` (Create/Update), `Result<Unit>` (Delete), `Result<List<StateTaxRate>>` (List) |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | StateCode: exactly 2 uppercase letters, valid US state abbreviation. Rate: 0.0000 - 0.2000 (0% to 20%). StateName: 1-64 chars. |
| **Room DAO**      | `StateTaxRateDao.insert(rate: StateTaxRateEntity): Long`, `StateTaxRateDao.update(rate: StateTaxRateEntity)`, `StateTaxRateDao.delete(stateCode: String)`, `StateTaxRateDao.findAll(): List<StateTaxRateEntity>`, `StateTaxRateDao.findByStateCode(stateCode: String): StateTaxRateEntity?` |
| **Logic**         | 1. Validate state code against known list. 2. Insert/update/delete. 3. Log audit event with old and new rate values. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `CONFLICT` — state code already exists (on create). `NOT_FOUND` — state code not found (on update/delete). `VALIDATION_ERROR` — invalid state code or rate out of range. |

---

## 16. Health & Monitoring (Admin)

### 16.1 GetIngestionHealthUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Provide a summary dashboard of ingestion health: recent run outcomes, failure rates, and next scheduled run. |
| **Input**         | `lookbackHours: Int = 24` |
| **Return Type**   | `Result<IngestionHealth>` where `IngestionHealth(totalRuns: Int, successfulRuns: Int, failedRuns: Int, partialRuns: Int, failureRate: Float, lastRunAt: Long?, nextScheduledRun: Long?, sourceBreakdown: List<SourceHealthSummary>)` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | `lookbackHours` in 1..168 (max 7 days). |
| **Room DAO**      | `IngestionJobRunDao.findSince(since: Long): List<IngestionJobRunEntity>`, `IngestionJobRunDao.countByStatusSince(status: String, since: Long): Int` |
| **Logic**         | 1. Calculate `since = now() - lookbackHours * 3600000`. 2. Query runs since that time. 3. Aggregate by status. 4. Group by source for per-source breakdown. 5. Query WorkManager for next scheduled run info. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `VALIDATION_ERROR` — lookback out of range. |

### 16.2 GetStorageStatsUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Report on-device storage usage: database file size, saved article count, image cache size, backup files size. |
| **Input**         | None |
| **Return Type**   | `Result<StorageStats>` where `StorageStats(dbSizeBytes: Long, dbTableRowCounts: Map<String, Int>, savedArticlesCount: Int, imageCacheSizeBytes: Long, backupFilesSizeBytes: Long, totalAppSizeBytes: Long)` |
| **Role Restriction** | `ADMIN` only |
| **Validation**    | None. |
| **Room DAO**      | `MetadataDao.getTableRowCounts(): Map<String, Int>` (custom query using `SELECT COUNT(*) FROM <table>` for each table), `SavedArticleDao.countAll(): Int` |
| **Logic**         | 1. Get Room DB file size from `context.getDatabasePath()`. 2. Query row counts for all tables. 3. Calculate image cache size from cache directory. 4. Sum backup files in `files/backups/`. 5. Combine. |
| **Error Cases**   | `FORBIDDEN` — caller is not ADMIN. `INTERNAL_ERROR` — file system access failure. |

### 16.3 GetQualityDashboardUseCase

| Field             | Detail |
|-------------------|--------|
| **Purpose**       | Provide batch quality trends and error rate analysis over time. |
| **Input**         | `periodDays: Int = 30`, `sourceRuleId: Long?` |
| **Return Type**   | `Result<QualityDashboard>` where `QualityDashboard(periodDays: Int, totalBatches: Int, averageErrorRate: Float, errorRateTrend: List<DailyErrorRate>, topErrorTypes: List<ErrorTypeCount>, sourceBreakdown: List<SourceQualitySummary>)` |
| **Role Restriction** | `ADMIN`, `EDITOR_ANALYST` |
| **Validation**    | `periodDays` in 1..90. |
| **Room DAO**      | `DataBatchVersionDao.findValidatedSince(since: Long, sourceRuleId: Long?): List<DataBatchVersionEntity>` |
| **Logic**         | 1. Calculate `since = now() - periodDays * 86400000`. 2. Query validated batches. 3. Aggregate error rates by day for trend. 4. Count error types across all batches. 5. Optionally filter by source. |
| **Error Cases**   | `FORBIDDEN` — caller lacks permission. `VALIDATION_ERROR` — period out of range. |

---

## Appendix A: Role Permission Matrix

The following matrix defines which roles can invoke each UseCase. `A` = Allow, `D` = Deny, `S` = Self only, `I` = Internal/System only.

### Authentication Module

| UseCase                | ADMIN | EDITOR_ANALYST | USER |
|------------------------|-------|----------------|------|
| LoginUseCase           | A     | A              | A    |
| LogoutUseCase          | A     | A              | A    |
| ChangePasswordUseCase  | A (any user) | A (self) | A (self) |
| StepUpAuthUseCase      | A     | A              | A    |

### User Management

| UseCase                | ADMIN | EDITOR_ANALYST | USER |
|------------------------|-------|----------------|------|
| CreateUserUseCase      | A     | D              | D    |
| UpdateUserUseCase      | A     | D              | D    |
| DeactivateUserUseCase  | A     | D              | D    |
| ListUsersUseCase       | A     | D              | D    |
| GetUserUseCase         | A     | S              | S    |

### Source Rule Management

| UseCase                  | ADMIN | EDITOR_ANALYST | USER |
|--------------------------|-------|----------------|------|
| CreateSourceRuleUseCase  | A     | D              | D    |
| UpdateSourceRuleUseCase  | A     | D              | D    |
| ListSourceRulesUseCase   | A     | A              | D    |
| GetSourceRuleUseCase     | A     | A              | D    |
| ToggleSourceRuleUseCase  | A     | D              | D    |

### Ingestion Engine

| UseCase                      | ADMIN | EDITOR_ANALYST | USER |
|------------------------------|-------|----------------|------|
| ScheduleIngestionUseCase     | A     | D              | D    |
| RunIngestionUseCase          | A     | A              | D    |
| RetryIngestionUseCase        | A     | A              | D    |
| GetIngestionLogsUseCase      | A     | A              | D    |
| CheckFailureThresholdUseCase | A     | A              | I    |

### Batch Tracking & Quality

| UseCase                       | ADMIN | EDITOR_ANALYST | USER |
|-------------------------------|-------|----------------|------|
| CreateBatchVersionUseCase     | I     | I              | I    |
| RecordLineageUseCase          | I     | I              | I    |
| RunQualityValidationUseCase   | A     | A              | D    |
| GetBatchMetricsUseCase        | A     | A              | D    |
| CheckQualityAlertUseCase      | A     | A              | I    |

### Article Browsing

| UseCase                    | ADMIN | EDITOR_ANALYST | USER |
|----------------------------|-------|----------------|------|
| SearchArticlesUseCase      | A     | A              | A    |
| GetArticleDetailUseCase    | A     | A              | A    |
| SaveArticleOfflineUseCase  | A     | A              | A    |
| ListSavedArticlesUseCase   | A     | A              | A    |
| RemoveSavedArticleUseCase  | A     | A              | A    |

### Cart Management

| UseCase                  | ADMIN | EDITOR_ANALYST | USER |
|--------------------------|-------|----------------|------|
| AddToCartUseCase         | A     | A              | A    |
| UpdateCartQuantityUseCase| A     | A              | A    |
| RemoveFromCartUseCase    | A     | A              | A    |
| GetCartUseCase           | A     | A              | A    |
| MergeGuestCartUseCase    | A     | A              | A    |
| ClearCartUseCase         | A     | A              | A    |

### Checkout & Pricing

| UseCase                   | ADMIN | EDITOR_ANALYST | USER |
|---------------------------|-------|----------------|------|
| StartCheckoutUseCase      | A     | A              | A    |
| ApplyCouponUseCase        | A     | A              | A    |
| RemoveCouponUseCase       | A     | A              | A    |
| CalculateTaxUseCase       | I     | I              | I    |
| ApplyPriceRulesUseCase    | I     | I              | I    |
| ConfirmCheckoutUseCase    | A     | A              | A    |
| ReconfirmPricesUseCase    | A     | A              | A    |

### Catalog & Inventory

| UseCase                  | ADMIN | EDITOR_ANALYST | USER |
|--------------------------|-------|----------------|------|
| ListCatalogItemsUseCase  | A     | A              | A    |
| GetCatalogItemUseCase    | A     | A              | A    |
| UpdateInventoryUseCase   | A     | D              | D    |
| CheckInventoryUseCase    | A     | A              | A    |

### Notifications

| UseCase                     | ADMIN | EDITOR_ANALYST | USER |
|-----------------------------|-------|----------------|------|
| CreateNotificationUseCase   | A     | I              | I    |
| DeliverNotificationUseCase  | I     | I              | I    |
| ListNotificationsUseCase    | A     | A              | A    |
| MarkNotificationReadUseCase | A     | A              | A    |
| ManageTemplatesUseCase      | A     | D              | D    |

### Editor/Analyst Tools

| UseCase                       | ADMIN | EDITOR_ANALYST | USER |
|-------------------------------|-------|----------------|------|
| ReviewIngestionBatchUseCase   | A     | A              | D    |
| ApproveArticleUseCase         | A     | A              | D    |
| RejectArticleUseCase          | A     | A              | D    |
| CurateArticleUseCase          | A     | A              | D    |
| ListFailedIngestionsUseCase   | A     | A              | D    |

### Audit

| UseCase                  | ADMIN | EDITOR_ANALYST | USER |
|--------------------------|-------|----------------|------|
| LogAuditEventUseCase     | I     | I              | I    |
| QueryAuditEventsUseCase  | A     | D              | D    |
| ExportAuditEventsUseCase | A     | D              | D    |

### Backup & Restore

| UseCase               | ADMIN | EDITOR_ANALYST | USER |
|-----------------------|-------|----------------|------|
| CreateBackupUseCase   | A     | D              | D    |
| ListBackupsUseCase    | A     | D              | D    |
| RestoreBackupUseCase  | A     | D              | D    |

### Price Rules & Tax

| UseCase                     | ADMIN | EDITOR_ANALYST | USER |
|-----------------------------|-------|----------------|------|
| ManagePriceRulesUseCase     | A     | D              | D    |
| ManageCouponsUseCase        | A     | D              | D    |
| ManageStateTaxRatesUseCase  | A     | D              | D    |

### Health & Monitoring

| UseCase                     | ADMIN | EDITOR_ANALYST | USER |
|-----------------------------|-------|----------------|------|
| GetIngestionHealthUseCase   | A     | D              | D    |
| GetStorageStatsUseCase      | A     | D              | D    |
| GetQualityDashboardUseCase  | A     | A              | D    |

---

## Appendix B: Room DAO Query Index Strategy

All indices are defined via `@Index` annotations on Room `@Entity` classes. Composite indices are ordered by selectivity and query patterns.

| Table                  | Index Name                          | Columns                             | Unique | Purpose                                            | Target Query Time |
|------------------------|-------------------------------------|--------------------------------------|--------|----------------------------------------------------|-------------------|
| `users`                | `idx_users_username`                | `username`                           | Yes    | Login lookup by username                           | < 1ms             |
| `users`                | `idx_users_role_active`             | `role`, `deactivated_at`             | No     | List users by role, excluding deactivated          | < 5ms             |
| `articles`             | `idx_articles_source_published`     | `source_id`, `published_at`          | No     | Search articles by source within date range        | < 10ms            |
| `articles`             | `idx_articles_status`               | `status`, `deleted_at`               | No     | Filter published/non-deleted articles              | < 5ms             |
| `articles`             | `idx_articles_team_league`          | `team`, `league`                     | No     | Browse articles by team and league                 | < 10ms            |
| `articles`             | `idx_articles_external_id`          | `external_id`                        | Yes    | Deduplication during ingestion                     | < 1ms             |
| `articles`             | `idx_articles_featured`             | `featured`, `published_at`           | No     | Featured articles listing                          | < 5ms             |
| `saved_articles`       | `idx_saved_user_article`            | `user_id`, `article_id`             | Yes    | Check if article is saved by user                  | < 1ms             |
| `saved_articles`       | `idx_saved_user_date`              | `user_id`, `saved_at`               | No     | List saved articles for user, ordered by date      | < 5ms             |
| `source_rules`         | `idx_source_rules_name`             | `name`                               | Yes    | Uniqueness check by name                           | < 1ms             |
| `source_rules`         | `idx_source_rules_enabled`          | `enabled`                            | No     | List active source rules for ingestion             | < 2ms             |
| `ingestion_job_runs`   | `idx_runs_source_started`           | `source_rule_id`, `started_at`       | No     | Query runs by source and date range                | < 10ms            |
| `ingestion_job_runs`   | `idx_runs_status_started`           | `status`, `started_at`               | No     | Count failures in time window                      | < 5ms             |
| `data_batch_versions`  | `idx_batch_source_created`          | `source_rule_id`, `created_at`       | No     | Batch lookup by source and date                    | < 5ms             |
| `data_batch_versions`  | `idx_batch_status`                  | `status`                             | No     | Filter by validation status                        | < 5ms             |
| `data_lineage`         | `idx_lineage_batch`                 | `batch_id`                           | No     | Find all articles in a batch                       | < 5ms             |
| `data_lineage`         | `idx_lineage_article`               | `article_id`                         | No     | Trace article back to batch/source                 | < 2ms             |
| `cart_line_items`      | `idx_cart_items_cart`               | `cart_id`                            | No     | Load all items in a cart                           | < 2ms             |
| `cart_line_items`      | `idx_cart_items_cart_catalog`        | `cart_id`, `catalog_item_id`         | Yes    | Check/update existing item in cart                 | < 1ms             |
| `carts`                | `idx_carts_user`                    | `user_id`                            | Yes    | Find cart by user                                  | < 1ms             |
| `catalog_items`        | `idx_catalog_team_league`           | `team`, `league`                     | No     | Browse catalog by team/league                      | < 10ms            |
| `catalog_items`        | `idx_catalog_category`              | `category`                           | No     | Browse catalog by category                         | < 5ms             |
| `catalog_items`        | `idx_catalog_sku`                   | `sku`                                | Yes    | SKU lookup for cart merge                          | < 1ms             |
| `inventory_snapshots`  | `idx_inventory_catalog`             | `catalog_item_id`                    | Yes    | Inventory lookup by catalog item                   | < 1ms             |
| `orders`               | `idx_orders_user_created`           | `user_id`, `created_at`              | No     | User order history                                 | < 5ms             |
| `orders`               | `idx_orders_number`                 | `order_number`                       | Yes    | Order lookup by number                             | < 1ms             |
| `checkout_sessions`    | `idx_checkout_cart`                 | `cart_id`                            | No     | Find active checkout for cart                      | < 1ms             |
| `locked_prices`        | `idx_locked_session`                | `checkout_session_id`                | No     | Load locked prices for session                     | < 2ms             |
| `coupons`              | `idx_coupons_code`                  | `code`                               | Yes    | Coupon lookup by code                              | < 1ms             |
| `price_rules`          | `idx_price_rules_priority`          | `active`, `priority`                 | No     | Load active rules in priority order                | < 2ms             |
| `state_tax_rates`      | `idx_tax_state`                     | `state_code`                         | Yes    | Tax rate lookup by state                           | < 1ms             |
| `notifications`        | `idx_notifications_user_created`    | `recipient_user_id`, `created_at`    | No     | User notification inbox                            | < 5ms             |
| `notifications`        | `idx_notifications_status`          | `status`                             | No     | Find pending notifications for delivery            | < 5ms             |
| `notification_templates` | `idx_templates_name`              | `name`                               | Yes    | Template lookup by name                            | < 1ms             |
| `audit_events`         | `idx_audit_user_timestamp`          | `user_id`, `timestamp`               | No     | Query audit events by user and date                | < 10ms            |
| `audit_events`         | `idx_audit_action_timestamp`        | `action`, `timestamp`                | No     | Query audit events by action type and date         | < 10ms            |
| `audit_events`         | `idx_audit_target`                  | `target_type`, `target_id`           | No     | Find all audit events for a specific entity        | < 5ms             |
| `backup_records`       | `idx_backups_created`               | `created_at`                         | No     | List backups ordered by date                       | < 2ms             |

---

## Appendix C: WorkManager Constraint Configuration

### Periodic Ingestion Work

| Parameter              | Value                               | Notes |
|------------------------|-------------------------------------|-------|
| **Work Name**          | `periodic_ingestion`                | Unique name for `ExistingPeriodicWorkPolicy.UPDATE` |
| **Repeat Interval**    | 6 hours (configurable: 1-24 hours)  | Default set by `ScheduleIngestionUseCase` |
| **Flex Interval**      | 30 minutes                          | WorkManager may run within this window before the deadline |
| **Requires Charging**  | `true` (default)                    | Configurable by admin |
| **Requires Idle**      | `true` (default)                    | Configurable by admin; device must be idle |
| **Requires Storage Not Low** | `true`                        | Always enforced |
| **Requires Battery Not Low** | `true`                        | Always enforced |
| **Network Type**       | `NOT_REQUIRED`                      | Offline-first: ingestion reads from local/cached sources |
| **Backoff Policy**     | `EXPONENTIAL`                       | Starting at 1 minute |
| **Max Retries**        | 3                                   | Per WorkManager run |
| **Worker Class**       | `IngestionWorker`                   | Extends `CoroutineWorker` |

### Ingestion Retry Work

| Parameter              | Value                               | Notes |
|------------------------|-------------------------------------|-------|
| **Work Name**          | `ingestion_retry_{jobRunId}`        | Unique per job run |
| **Type**               | `OneTimeWorkRequest`                | Single execution |
| **Initial Delay**      | Attempt 1: 1 min, Attempt 2: 4 min, Attempt 3: 10 min | Exponential backoff |
| **Requires Charging**  | `false`                             | Retries should happen regardless |
| **Requires Idle**      | `false`                             | Retries should happen regardless |
| **Requires Storage Not Low** | `true`                        | Always enforced |
| **Network Type**       | `NOT_REQUIRED`                      | Offline-first |
| **Worker Class**       | `IngestionRetryWorker`              | Extends `CoroutineWorker` |

### Notification Delivery Retry Work

| Parameter              | Value                               | Notes |
|------------------------|-------------------------------------|-------|
| **Work Name**          | `notification_retry_{notificationId}` | Unique per notification |
| **Type**               | `OneTimeWorkRequest`                | Single execution |
| **Initial Delay**      | 30 seconds                          | Quick retry |
| **Backoff Policy**     | `LINEAR`                            | 30 second intervals |
| **Max Retries**        | 3                                   | After 3 failures, notification status stays FAILED |
| **Worker Class**       | `NotificationDeliveryWorker`        | Extends `CoroutineWorker` |

### Failure Threshold Check Work

| Parameter              | Value                               | Notes |
|------------------------|-------------------------------------|-------|
| **Work Name**          | `failure_threshold_check`           | Chained after ingestion worker |
| **Type**               | `OneTimeWorkRequest`                | Chained via `WorkManager.beginWith(...).then(...)` |
| **Initial Delay**      | None                                | Runs immediately after ingestion completes |
| **Worker Class**       | `FailureThresholdWorker`            | Extends `CoroutineWorker` |

---

## Appendix D: Notification Template Variables

Templates use `{{variable_name}}` syntax for variable substitution. All variables are resolved at notification creation time.

### Template: `INGESTION_FAILURE_ALERT`

| Variable Name       | Source                              | Example Value                    |
|---------------------|-------------------------------------|----------------------------------|
| `{{source_name}}`   | `SourceRuleEntity.name`             | `"ESPN NFL News"`                |
| `{{failure_count}}` | `IngestionJobRunDao.countFailuresSince()` | `"7"`                      |
| `{{time_window}}`   | Hardcoded                           | `"24 hours"`                     |
| `{{last_error}}`    | Most recent `IngestionJobRunEntity.errorDetails` | `"Parse error: title selector returned empty"` |
| `{{timestamp}}`     | `System.currentTimeMillis()` formatted | `"2026-04-10 14:30 UTC"`     |

### Template: `QUALITY_ALERT`

| Variable Name       | Source                              | Example Value                    |
|---------------------|-------------------------------------|----------------------------------|
| `{{batch_id}}`      | `DataBatchVersionEntity.id`         | `"1042"`                         |
| `{{source_name}}`   | `SourceRuleEntity.name`             | `"CBS Sports MLB"`               |
| `{{error_rate}}`    | `DataBatchVersionEntity.errorRate`  | `"4.7%"`                         |
| `{{threshold}}`     | Hardcoded                           | `"2%"`                           |
| `{{total_items}}`   | `DataBatchVersionEntity.totalItems` | `"150"`                          |
| `{{invalid_items}}` | `DataBatchVersionEntity.invalidItems` | `"7"`                          |
| `{{timestamp}}`     | `System.currentTimeMillis()` formatted | `"2026-04-10 09:15 UTC"`     |

### Template: `ORDER_CONFIRMATION`

| Variable Name          | Source                           | Example Value                    |
|------------------------|----------------------------------|----------------------------------|
| `{{user_name}}`        | `UserEntity.displayName`         | `"John Smith"`                   |
| `{{order_number}}`     | `OrderEntity.orderNumber`        | `"ORD-20260410-0042"`            |
| `{{item_count}}`       | `OrderLineItemDao.countByOrderId()` | `"3"`                         |
| `{{grand_total}}`      | `OrderEntity.grandTotal` formatted | `"$127.49"`                    |
| `{{discount_applied}}` | `OrderEntity.discountTotal` formatted | `"$12.75"`                  |
| `{{timestamp}}`        | `OrderEntity.createdAt` formatted | `"2026-04-10 16:45 UTC"`        |

### Template: `INVENTORY_LOW_STOCK`

| Variable Name        | Source                              | Example Value                    |
|----------------------|-------------------------------------|----------------------------------|
| `{{item_name}}`      | `CatalogItemEntity.name`           | `"Chiefs Super Bowl LVIII Jersey"` |
| `{{sku}}`            | `CatalogItemEntity.sku`            | `"KC-SB58-JRY-L"`               |
| `{{current_stock}}`  | `InventorySnapshotEntity.quantity`  | `"3"`                            |
| `{{threshold}}`      | Configurable low-stock threshold    | `"5"`                            |
| `{{timestamp}}`      | `System.currentTimeMillis()` formatted | `"2026-04-10 11:00 UTC"`     |

### Template: `ACCOUNT_DEACTIVATED`

| Variable Name        | Source                              | Example Value                    |
|----------------------|-------------------------------------|----------------------------------|
| `{{user_name}}`      | `UserEntity.displayName`           | `"Jane Doe"`                     |
| `{{admin_name}}`     | Acting admin's `UserEntity.displayName` | `"Admin User"`              |
| `{{reason}}`         | Provided by admin (if any)         | `"Policy violation"`             |
| `{{timestamp}}`      | `System.currentTimeMillis()` formatted | `"2026-04-10 08:30 UTC"`     |

### Template: `PASSWORD_CHANGED`

| Variable Name        | Source                              | Example Value                    |
|----------------------|-------------------------------------|----------------------------------|
| `{{user_name}}`      | `UserEntity.displayName`           | `"John Smith"`                   |
| `{{changed_by}}`     | Acting user's display name         | `"self"` or `"Admin User"`      |
| `{{timestamp}}`      | `System.currentTimeMillis()` formatted | `"2026-04-10 12:00 UTC"`     |

### Template: `BACKUP_COMPLETED`

| Variable Name         | Source                              | Example Value                    |
|-----------------------|-------------------------------------|----------------------------------|
| `{{admin_name}}`      | Acting admin's `UserEntity.displayName` | `"Admin User"`              |
| `{{file_name}}`       | Backup file name                   | `"backup_20260410_143000.db"`    |
| `{{file_size}}`       | `BackupRecordEntity.fileSizeBytes` formatted | `"24.5 MB"`            |
| `{{checksum}}`        | `BackupRecordEntity.sha256Checksum` (first 12 chars) | `"a1b2c3d4e5f6"` |
| `{{timestamp}}`       | `BackupRecordEntity.createdAt` formatted | `"2026-04-10 14:30 UTC"`  |

---

*End of specification.*
