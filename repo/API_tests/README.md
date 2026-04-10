# API Interface Functional Tests

## Overview

API functional tests exercise the **use case layer** — the application's interface boundary — with complete end-to-end flow verification. Since this is an offline-first Android application (not a server), the use cases serve as the API surface. Each test verifies:

- **Normal requests** with valid parameters
- **Abnormal scenarios**: missing parameters, invalid formats, insufficient permissions
- **Data state changes**: verifying system state before and after operations
- **Error codes and messages**: checking that failures return appropriate error information

## Test Files

All test source files are under `app/src/test/java/com/eaglepoint/storefront/api/`:

| Test File | Flow | Test Count | Coverage |
|---|---|---|---|
| `AuthenticationFlowTest.kt` | Login & User Creation | 14 | Valid login, session start, empty/short/SQL-injection username, password complexity, non-existent user, wrong password, duplicate user, password zeroing, audit logging |
| `CartCheckoutFlowTest.kt` | Cart, Pricing & Checkout | 14 | Add item, not found, inactive, insufficient inventory, zero qty, quantity update validation, coupon one-per-order, invalid/expired coupon, empty cart, checkout inventory check, price lock, expired lock reconfirm, CHECKED_OUT status, coupon usage increment |
| `IngestionQualityFlowTest.kt` | Ingestion & Quality | 10 | Source rule create/invalid URL/blank name, ingestion for missing/inactive/active source, editor/analyst/user access control, pending articles role gating, version bumping |
| `NotificationFlowTest.kt` | Notifications | 11 | Send with/without template, all event types (ingestion failure, batch quality, price lock, admin review), delivery trigger, max retries, read/unread count, mark read/all, error handling |
| `ContentCurationFlowTest.kt` | Content Curation | 12 | Approve, reject with reason, feature/unfeature, tag updates, USER/ANALYST rejection (SecurityException), ADMIN access, audit logging |
| `BackupRestoreFlowTest.kt` | Backup & Restore | 5 | Re-auth failure, backup success, checksum mismatch, restore re-auth, restore success, metadata unreadable |

**Total: 66 API functional test cases**

## Running

```bash
# Run all API functional tests
./API_tests/run_api_tests.sh

# Or via Gradle directly
cd app && ../gradlew test --tests "com.eaglepoint.storefront.api.*"

# Run a specific flow
../gradlew test --tests "com.eaglepoint.storefront.api.CartCheckoutFlowTest"
```

## Test Patterns

Each flow test follows a consistent pattern:
1. **Setup**: Mock repositories and dependencies via MockK
2. **Execute**: Call the use case (API boundary) with specific parameters
3. **Assert**: Verify return value, error messages, state changes, and side effects
4. **Verify**: Check that audit events, notifications, and data mutations occurred correctly
