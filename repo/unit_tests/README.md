# Unit Tests

## Overview

Unit tests cover all core business logic modules, state transitions, boundary conditions, and exception handling for the Storefront application. Tests are written in Kotlin using JUnit 5, MockK, Turbine, and Google Truth.

## Test Organization

All test source files are located under `app/src/test/java/com/eaglepoint/storefront/` and are organized by module:

### Security (4 tests)
| Test File | Module | Coverage |
|---|---|---|
| `security/PasswordHasherTest.kt` | PBKDF2 hashing | Salt generation, deterministic hashing, verify correct/wrong, key length |
| `security/SensitiveFieldMaskerTest.kt` | Field masking | Hash masking, ID masking, sensitive key detection, audit detail masking |
| `security/ChecksumUtilTest.kt` | SHA-256 checksums | Known hash, stream match, verify pass/fail, tamper detection |
| `security/SessionManagerTest.kt` | Session lifecycle | Start/end session, role checks, ownership, SecurityException on invalid |

### Authentication & Authorization (5 tests)
| Test File | Module | Coverage |
|---|---|---|
| `domain/usecase/LoginUseCaseTest.kt` | Login flow | Success, user not found, wrong password, audit logging, input validation short-circuit |
| `domain/usecase/RoleGuardTest.kt` | Role enforcement | All 4 roles x all permission checks, requireRole pass/throw, session-based enforcement |
| `domain/usecase/AuthorizationEnforcementTest.kt` | Cross-cutting auth | End-to-end authorization across use cases |
| `data/repository/AuthRepositoryTest.kt` | User persistence | Encrypt/decrypt round-trip, find by username, create user |
| `data/repository/AuditRepositoryTest.kt` | Audit persistence | Log with masking, pagination, action filtering |

### Backup & Restore (3 tests)
| Test File | Module | Coverage |
|---|---|---|
| `domain/usecase/BackupUseCaseTest.kt` | Backup flow | Re-auth required, success, audit logging, error handling |
| `domain/usecase/RestoreUseCaseTest.kt` | Restore flow | Re-auth gate, checksum mismatch abort, success, metadata read |
| `domain/usecase/BackupRestoreWiringTest.kt` | DI wiring | Backup/restore dependency injection verification |

### Content Ingestion (6 tests)
| Test File | Module | Coverage |
|---|---|---|
| `ingestion/FeedParserTest.kt` | RSS/Atom parsing | Valid RSS/Atom, empty channel, invalid XML, XXE protection |
| `ingestion/HtmlScrapeParserTest.kt` | HTML scraping | Selector-based extraction |
| `ingestion/ContentFilterTest.kt` | Allow/block lists | Domain/keyword filtering, case insensitivity, combined filters |
| `ingestion/UserAgentRotatorTest.kt` | UA rotation | Non-empty, non-consecutive, valid mobile agents |
| `ingestion/RequestPacerTest.kt` | Rate limiting | First request passthrough, source independence, reset |
| `ingestion/IngestionEngineTest.kt` | Full pipeline | Success/failure flows, pacing, lineage, failure alerting threshold |

### Data Quality (4 tests)
| Test File | Module | Coverage |
|---|---|---|
| `quality/BatchValidatorTest.kt` | Batch validation | Empty batch, valid items, old/future dates, price range, inventory, error rate thresholds |
| `quality/ValidationRuleTest.kt` | Individual rules | PublishTime, PriceRange, InventoryNonNegative with boundaries |
| `quality/ValidateBatchUseCaseTest.kt` | Validation orchestration | Persist errors, update batch, audit logging, alert generation |
| `quality/BatchValidationCatalogInventoryTest.kt` | Catalog/inventory validation | Price and inventory validation integration |

### Article Browsing & Curation (5 tests)
| Test File | Module | Coverage |
|---|---|---|
| `domain/usecase/SearchArticlesUseCaseTest.kt` | Article search | Keyword search, filter delegation, distinct teams/leagues |
| `domain/usecase/GetArticleDetailUseCaseTest.kt` | Article detail | Found, not found, offline save state |
| `domain/usecase/SaveArticleOfflineUseCaseTest.kt` | Offline save | Save, unsave, toggle both directions |
| `domain/usecase/CurateArticleUseCaseTest.kt` | Content curation | Approve, reject with reason, feature/unfeature, tags, role enforcement |
| `domain/usecase/ReviewIngestionUseCaseTest.kt` | Editor review | Job run access, batch quality, pending articles, role gating |

### Cart, Pricing & Checkout (5 tests)
| Test File | Module | Coverage |
|---|---|---|
| `domain/usecase/AddToCartUseCaseTest.kt` | Cart add | Success, item not found, inactive, insufficient inventory, invalid quantity |
| `domain/usecase/MergeCartsUseCaseTest.kt` | Cart merge | Guest-to-user merge, audit logging, failure handling |
| `domain/usecase/CheckoutUseCaseTest.kt` | Checkout flow | Price lock, empty cart, inventory validation, expired lock reconfirm, coupon usage |
| `domain/usecase/CheckoutReceiptTest.kt` | Receipt generation | Order creation with line items during checkout |
| `pricing/PricingEngineTest.kt` | Pricing rules | 10% off >$50, coupon percent/fixed, tax on discounted, combined rules, locked prices, rounding |

### Notifications (3 tests)
| Test File | Module | Coverage |
|---|---|---|
| `notification/TemplateRendererTest.kt` | Variable substitution | Single/multiple vars, unresolved cleanup, renderTemplate |
| `notification/NotificationDispatcherTest.kt` | Delivery & retry | Deliver success, failure retry increment, exhausted status, batch delivery |
| `notification/SendNotificationUseCaseTest.kt` | Send orchestration | With/without template, default content per event type, max retries |

### Input Validation (1 test)
| Test File | Module | Coverage |
|---|---|---|
| `domain/validation/InputValidatorTest.kt` | Input validation | Username/password rules, SQL injection rejection, sanitization |

### UI (1 test)
| Test File | Module | Coverage |
|---|---|---|
| `ui/home/ImageLoaderTest.kt` | Image downsampling | inSampleSize calculation, 20MB cache constant |

## Running

```bash
# Run all unit tests
./unit_tests/run_unit_tests.sh

# Or via Gradle directly
cd app && ../gradlew test

# Run a specific test class
../gradlew test --tests "com.eaglepoint.storefront.security.PasswordHasherTest"
```

## Test Frameworks
- **JUnit 5** (Jupiter) - Test runner and assertions
- **MockK** - Kotlin-first mocking
- **Turbine** - Flow testing
- **Google Truth** - Fluent assertions
- **kotlinx-coroutines-test** - Coroutine test support
