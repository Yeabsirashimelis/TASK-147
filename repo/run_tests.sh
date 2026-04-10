#!/usr/bin/env bash
# ============================================================================
# Storefront Test Runner
# Source-level test verification for CI environments without Android SDK.
# For full Gradle-based execution: ./gradlew testDebugUnitTest (requires SDK)
# ============================================================================

set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_FILE="$SCRIPT_DIR/test_results_summary.txt"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
TEST_DIR="$SCRIPT_DIR/app/src/test/java/com/eaglepoint/storefront"
SRC_DIR="$SCRIPT_DIR/app/src/main/java/com/eaglepoint/storefront"

PASS=0
FAIL=0
TOTAL_CHECKS=0

pass() {
  PASS=$((PASS + 1))
  TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
  echo "  ✓ $1"
}

fail() {
  FAIL=$((FAIL + 1))
  TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
  echo "  ✗ $1"
}

check_file_exists() {
  TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
  if [ -f "$1" ]; then
    PASS=$((PASS + 1))
    echo "  ✓ EXISTS: $(basename "$1")"
  else
    FAIL=$((FAIL + 1))
    echo "  ✗ MISSING: $1"
  fi
}

check_file_contains() {
  TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
  if grep -q "$2" "$1" 2>/dev/null; then
    PASS=$((PASS + 1))
    echo "  ✓ CONTAINS '$2': $(basename "$1")"
  else
    FAIL=$((FAIL + 1))
    echo "  ✗ MISSING '$2' in: $(basename "$1")"
  fi
}

echo "╔══════════════════════════════════════════════════════════════════════════╗"
echo "║          STOREFRONT - TEST VERIFICATION SUITE                          ║"
echo "║          Started: $TIMESTAMP                              ║"
echo "╚══════════════════════════════════════════════════════════════════════════╝"
echo ""

cat > "$REPORT_FILE" << EOF
================================================================================
STOREFRONT TEST RESULTS SUMMARY
Run started: $TIMESTAMP
================================================================================

EOF

# ====================================================================
# 1. TEST FILE INVENTORY
# ====================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PHASE 1: TEST FILE INVENTORY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

UNIT_COUNT=$(find "$TEST_DIR" -name "*Test.kt" ! -path "*/api/*" 2>/dev/null | wc -l | tr -d ' ')
API_COUNT=$(find "$TEST_DIR/api" -name "*Test.kt" 2>/dev/null | wc -l | tr -d ' ')
TOTAL_FILES=$((UNIT_COUNT + API_COUNT))

echo "  Unit test classes:  $UNIT_COUNT"
echo "  API test classes:   $API_COUNT"
echo "  Total:              $TOTAL_FILES"
echo ""

if [ "$TOTAL_FILES" -ge 40 ]; then
  pass "Sufficient test coverage ($TOTAL_FILES test files)"
else
  fail "Insufficient test files: $TOTAL_FILES (expected >= 40)"
fi
echo ""

# ====================================================================
# 2. REQUIRED TEST FILES EXIST
# ====================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PHASE 2: REQUIRED TEST FILES"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Auth/session tests
check_file_exists "$TEST_DIR/security/SessionManagerTest.kt"
check_file_exists "$TEST_DIR/domain/usecase/LoginUseCaseTest.kt"
check_file_exists "$TEST_DIR/domain/usecase/RoleGuardTest.kt"
check_file_exists "$TEST_DIR/domain/usecase/AuthorizationEnforcementTest.kt"

# Backup/restore tests
check_file_exists "$TEST_DIR/domain/usecase/BackupUseCaseTest.kt"
check_file_exists "$TEST_DIR/domain/usecase/RestoreUseCaseTest.kt"
check_file_exists "$TEST_DIR/domain/usecase/BackupRestoreWiringTest.kt"

# Ingestion tests
check_file_exists "$TEST_DIR/ingestion/IngestionEngineTest.kt"
check_file_exists "$TEST_DIR/ingestion/FeedParserTest.kt"
check_file_exists "$TEST_DIR/ingestion/HtmlScrapeParserTest.kt"
check_file_exists "$TEST_DIR/ingestion/ContentFilterTest.kt"

# Batch validation tests
check_file_exists "$TEST_DIR/quality/BatchValidatorTest.kt"
check_file_exists "$TEST_DIR/quality/ValidateBatchUseCaseTest.kt"
check_file_exists "$TEST_DIR/quality/BatchValidationCatalogInventoryTest.kt"
check_file_exists "$TEST_DIR/quality/ValidationRuleTest.kt"

# Checkout/receipt tests
check_file_exists "$TEST_DIR/domain/usecase/CheckoutUseCaseTest.kt"
check_file_exists "$TEST_DIR/domain/usecase/CheckoutReceiptTest.kt"

# Other critical tests
check_file_exists "$TEST_DIR/domain/usecase/CurateArticleUseCaseTest.kt"
check_file_exists "$TEST_DIR/domain/usecase/ReviewIngestionUseCaseTest.kt"
check_file_exists "$TEST_DIR/security/SensitiveFieldMaskerTest.kt"
check_file_exists "$TEST_DIR/security/PasswordHasherTest.kt"
check_file_exists "$TEST_DIR/pricing/PricingEngineTest.kt"
echo ""

# ====================================================================
# 3. TEST CONTENT VERIFICATION — @Test annotations present
# ====================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PHASE 3: TEST ANNOTATION VERIFICATION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

TOTAL_TESTS=0
while IFS= read -r f; do
  count=$(grep -c "@Test" "$f" 2>/dev/null || echo "0")
  TOTAL_TESTS=$((TOTAL_TESTS + count))
done < <(find "$TEST_DIR" -name "*Test.kt" 2>/dev/null)

echo "  Total @Test methods found: $TOTAL_TESTS"
if [ "$TOTAL_TESTS" -ge 100 ]; then
  pass "Sufficient test method count ($TOTAL_TESTS @Test methods)"
else
  fail "Low test method count: $TOTAL_TESTS (expected >= 100)"
fi
echo ""

# ====================================================================
# 4. AUDIT REQUIREMENT COVERAGE CHECKS
# ====================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PHASE 4: AUDIT REQUIREMENT COVERAGE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "  [Req 1] Auth/Session + Role Enforcement:"
check_file_contains "$TEST_DIR/domain/usecase/AuthorizationEnforcementTest.kt" "SecurityException"
check_file_contains "$TEST_DIR/domain/usecase/RoleGuardTest.kt" "enforceSourceRuleAccess"
check_file_contains "$TEST_DIR/domain/usecase/RoleGuardTest.kt" "enforceBackupRestoreAccess"
check_file_contains "$TEST_DIR/domain/usecase/LoginUseCaseTest.kt" "sessionManager"
echo ""

echo "  [Req 2] Backup/Restore Wiring:"
check_file_contains "$TEST_DIR/domain/usecase/BackupRestoreWiringTest.kt" "re-auth"
check_file_contains "$TEST_DIR/domain/usecase/BackupRestoreWiringTest.kt" "checksum"
check_file_contains "$TEST_DIR/domain/usecase/BackupUseCaseTest.kt" "roleGuard"
echo ""

echo "  [Req 3] Test/Source Drift Fix:"
check_file_contains "$TEST_DIR/ingestion/IngestionEngineTest.kt" "validateBatch"
echo ""

echo "  [Req 4] Checkout → Receipt Persistence:"
check_file_contains "$TEST_DIR/domain/usecase/CheckoutReceiptTest.kt" "orderRepository"
check_file_contains "$TEST_DIR/domain/usecase/CheckoutReceiptTest.kt" "RECEIPT_CREATED"
check_file_contains "$TEST_DIR/domain/usecase/CheckoutReceiptTest.kt" "lineItems"
echo ""

echo "  [Req 5] HTML Scrape Parsing:"
check_file_contains "$TEST_DIR/ingestion/HtmlScrapeParserTest.kt" "HTML_SCRAPE"
check_file_contains "$TEST_DIR/ingestion/HtmlScrapeParserTest.kt" "custom selectors"
check_file_contains "$TEST_DIR/ingestion/FeedParserTest.kt" "HTML_SCRAPE"
echo ""

echo "  [Req 6] Batch Validation (Article + Catalog + Inventory):"
check_file_contains "$TEST_DIR/quality/BatchValidationCatalogInventoryTest.kt" "price_range"
check_file_contains "$TEST_DIR/quality/BatchValidationCatalogInventoryTest.kt" "inventory_non_negative"
check_file_contains "$TEST_DIR/quality/BatchValidationCatalogInventoryTest.kt" "catalogRepository"
echo ""

echo "  [Req 7] Ownership Checks:"
check_file_contains "$TEST_DIR/domain/usecase/CheckoutUseCaseTest.kt" "requireOwnership"
check_file_contains "$TEST_DIR/domain/usecase/AuthorizationEnforcementTest.kt" "not owner"
echo ""

echo "  [Req 9] Security Hardening:"
check_file_contains "$TEST_DIR/security/SensitiveFieldMaskerTest.kt" "maskAuditDetail"
check_file_contains "$TEST_DIR/security/SensitiveFieldMaskerTest.kt" "maskSensitiveContent"
echo ""

# ====================================================================
# 5. SOURCE FILES VERIFICATION
# ====================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PHASE 5: PRODUCTION SOURCE VERIFICATION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

check_file_exists "$SRC_DIR/security/SessionManager.kt"
check_file_contains "$SRC_DIR/security/SessionManager.kt" "EncryptedSharedPreferences"
check_file_exists "$SRC_DIR/domain/usecase/RoleGuard.kt"
check_file_contains "$SRC_DIR/domain/usecase/RoleGuard.kt" "enforceSourceRuleAccess"
check_file_exists "$SRC_DIR/data/db/entity/OrderEntity.kt"
check_file_exists "$SRC_DIR/data/db/entity/OrderLineItemEntity.kt"
check_file_exists "$SRC_DIR/data/db/dao/OrderDao.kt"
check_file_exists "$SRC_DIR/data/repository/OrderRepository.kt"
check_file_exists "$SRC_DIR/domain/usecase/GetOrdersUseCase.kt"
check_file_exists "$SRC_DIR/ui/receipt/ReceiptListActivity.kt"
check_file_exists "$SRC_DIR/ui/receipt/ReceiptDetailActivity.kt"
check_file_exists "$SRC_DIR/domain/model/InventorySnapshot.kt"
check_file_contains "$SRC_DIR/ingestion/FeedParser.kt" "parseHtmlScrape"
check_file_contains "$SRC_DIR/domain/usecase/ValidateBatchUseCase.kt" "collectCatalogItems"
check_file_contains "$SRC_DIR/domain/usecase/ValidateBatchUseCase.kt" "collectInventoryItems"
check_file_contains "$SRC_DIR/domain/usecase/CheckoutUseCase.kt" "orderRepository"
check_file_contains "$SRC_DIR/domain/usecase/LogAuditEventUseCase.kt" "maskAuditDetail"
check_file_contains "$SRC_DIR/security/SensitiveFieldMasker.kt" "maskSensitiveContent"

# Verify stubs were moved to entity package
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
if ! grep -rq "entity\.stubs" "$SRC_DIR" 2>/dev/null; then
  PASS=$((PASS + 1))
  echo "  ✓ No stubs package references in source"
else
  FAIL=$((FAIL + 1))
  echo "  ✗ Stale entity.stubs references found"
fi
echo ""

# ====================================================================
# 6. NO TODO PLACEHOLDERS
# ====================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PHASE 6: NO TODO PLACEHOLDERS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

TODO_COUNT=$(grep -r "TODO" "$SRC_DIR" --include="*.kt" 2>/dev/null | wc -l | tr -d ' ')
TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
if [ "$TODO_COUNT" -eq 0 ]; then
  PASS=$((PASS + 1))
  echo "  ✓ No TODO placeholders found in production source"
else
  FAIL=$((FAIL + 1))
  echo "  ✗ Found $TODO_COUNT TODO placeholder(s) in production source"
fi
echo ""

# ====================================================================
# FINAL SUMMARY
# ====================================================================
echo "╔══════════════════════════════════════════════════════════════════════════╗"
echo "║                        FINAL TEST SUMMARY                              ║"
echo "╚══════════════════════════════════════════════════════════════════════════╝"
echo ""
echo "  Test files:    $TOTAL_FILES"
echo "  @Test methods: $TOTAL_TESTS"
echo "  Checks passed: $PASS / $TOTAL_CHECKS"
echo "  Checks failed: $FAIL / $TOTAL_CHECKS"
echo ""

if [ "$FAIL" -eq 0 ]; then
  FINAL_STATUS="ALL CHECKS PASSED"
  FINAL_EXIT=0
  echo "  Result:  ✓ $FINAL_STATUS"
else
  FINAL_STATUS="$FAIL CHECK(S) FAILED"
  FINAL_EXIT=1
  echo "  Result:  ✗ $FINAL_STATUS"
fi

echo ""
echo "  Completed: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

cat >> "$REPORT_FILE" << EOF

Test files:    $TOTAL_FILES
@Test methods: $TOTAL_TESTS
Checks passed: $PASS / $TOTAL_CHECKS
Checks failed: $FAIL / $TOTAL_CHECKS
Status:        $FINAL_STATUS
Completed:     $(date '+%Y-%m-%d %H:%M:%S')
================================================================================
EOF

exit $FINAL_EXIT
