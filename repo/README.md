# Storefront — Offline Android Sports Commerce & News Management System

An offline-first Android application for sports news ingestion, article curation, merchandise commerce, and administrative management.

## Prerequisites

- **Android Studio** Hedgehog (2023.1) or newer
- **JDK 17**
- **Android SDK** with compile SDK 35, min SDK 29
- Gradle 8.7 (included via wrapper — `gradlew`, `gradlew.bat`, `gradle/wrapper/`)

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug
```

## Run Tests

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.eaglepoint.storefront.ingestion.IngestionEngineTest"
```

## Architecture

```
app/
├── data/
│   ├── db/          # Room database, entities, DAOs, converters
��   └── repository/  # Repository layer (offline-first, Room-backed)
├── di/              # Koin dependency injection modules
├── domain/
│   ├── model/       # Domain models and enums
│   ├── usecase/     # Business logic use cases
│   └── validation/  # Input validation
├── ingestion/       # Feed ingestion engine (RSS, Atom, HTML scrape)
├── notification/    # Notification dispatch and templating
├── pricing/         # Pricing engine with rules and coupons
├── quality/         # Batch data quality validation
├── security/        # Keystore, encryption, hashing, session management, masking
└── ui/              # Activities, ViewModels, Adapters (Android Views + MVVM)
```

### Key Patterns

- **Offline-first**: All data persisted in Room (SQLite). No network dependency for core flows.
- **DI**: Koin for dependency injection (modules: `databaseModule`, `securityModule`, `appModule`, `viewModelModule`).
- **Async**: Coroutines + Flow for all I/O. Pricing and heavy work dispatched off main thread.
- **WorkManager**: Used for scheduled background ingestion jobs.

## Roles & Security

### User Roles

| Role | Capabilities |
|------|-------------|
| **ADMIN** | Full access: source rules, ingestion console, audit logs/export, backup/restore, user management, curation, cart/checkout |
| **EDITOR** | Content curation (approve/reject/feature), ingestion log viewing, audit log viewing |
| **ANALYST** | Ingestion log viewing, audit log viewing, batch quality reports |
| **USER** | Article browsing, cart/checkout, order history/receipts, notifications |

### Session Management

- `SessionManager` stores authenticated `userId` and `role` in **EncryptedSharedPreferences** backed by Android Keystore (AES-256-GCM).
- Session is established at login/account creation and persisted across app restarts.
- Role is read from the session — not from intent extras — for all privileged operations.
- Use-case boundaries enforce role checks via `RoleGuard` before executing sensitive operations.
- Object-level ownership checks (e.g., cart, orders, notifications) verify that the current session user owns the resource.

### Password & Encryption

- Passwords are hashed with PBKDF2 (via `PasswordHasher`) using a per-user random salt.
- Hash and salt are encrypted at rest using AES-256-GCM via `FieldEncryptor` backed by Android Keystore.
- Stored as Base64-encoded ciphertext in Room.
- Passwords are cleared from memory (`CharArray.fill('\0')`) after use.

### Audit Logging

- All sensitive operations are audit-logged with userId, action, target, and masked detail.
- `SensitiveFieldMasker` strips password/token/secret values from audit detail strings.
- Audit log access restricted to ADMIN, EDITOR, and ANALYST roles.

## Backup & Restore Flow

1. **Backup**: User selects destination file → re-authentication dialog → password verified → database backup created with checksum → metadata written alongside.
2. **Restore**: User selects backup file → checksum verification → restore confirmation dialog (shows backup date, DB version) → re-authentication dialog → password verified → database restored → app restarts.
3. Both operations require **ADMIN** role and step-up re-authentication.
4. Failure at any stage (auth, verification, I/O) surfaces a user-visible error toast and is audit-logged.

## Data Ingestion

- Supports **RSS**, **Atom**, and **HTML scrape** feed types.
- HTML scrape uses configurable CSS-like selectors (e.g., `container=article;title=h2;url=a;summary=p;date=time`).
- Ingestion runs produce `DataBatchVersion` records for lineage tracking.
- Post-ingestion batch validation checks articles (publish-time), catalog items (price-range), and inventory snapshots (non-negative).
- Failure alerting triggers after 5+ failures in 24 hours per source.

## Order/Receipt Flow

- Checkout persists an `Order` with `OrderLineItem` records snapshotting prices, quantities, tax, and discounts at time of purchase.
- After successful checkout, the user is navigated to the receipt detail screen.
- Users can view order history via the receipt list screen.
- Receipt creation and viewing are audit-logged.
