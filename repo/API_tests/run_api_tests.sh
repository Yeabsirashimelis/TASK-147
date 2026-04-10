#!/usr/bin/env bash
# ============================================================================
# API Interface Functional Test Runner for Storefront Android Application
# Exercises the use case layer (the API boundary) with full-flow tests.
# ============================================================================

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_FILE="$SCRIPT_DIR/api_test_results.txt"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

echo "============================================================================"
echo "  STOREFRONT API FUNCTIONAL TEST SUITE"
echo "  Started: $TIMESTAMP"
echo "============================================================================"
echo ""

# ---- API test classes under com.eaglepoint.storefront.api ----
declare -A API_TESTS
API_TESTS=(
  ["Auth_LoginCreateFlow"]="com.eaglepoint.storefront.api.AuthenticationFlowTest"
  ["Cart_Checkout_Flow"]="com.eaglepoint.storefront.api.CartCheckoutFlowTest"
  ["Ingestion_Quality_Flow"]="com.eaglepoint.storefront.api.IngestionQualityFlowTest"
  ["Notification_Flow"]="com.eaglepoint.storefront.api.NotificationFlowTest"
  ["Content_Curation_Flow"]="com.eaglepoint.storefront.api.ContentCurationFlowTest"
  ["Backup_Restore_Flow"]="com.eaglepoint.storefront.api.BackupRestoreFlowTest"
)

TOTAL=0
PASSED=0
FAILED=0
FAILED_LIST=()

# Initialize report
echo "STOREFRONT API FUNCTIONAL TEST RESULTS" > "$REPORT_FILE"
echo "Run started: $TIMESTAMP" >> "$REPORT_FILE"
echo "============================================" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

for TEST_NAME in $(echo "${!API_TESTS[@]}" | tr ' ' '\n' | sort); do
  TEST_CLASS="${API_TESTS[$TEST_NAME]}"
  TOTAL=$((TOTAL + 1))
  printf "  %-45s ... " "$TEST_NAME"

  if cd "$PROJECT_ROOT" && ./gradlew test --tests "$TEST_CLASS" --console=plain --quiet 2>/dev/null; then
    echo "PASS"
    echo "  [PASS] $TEST_NAME ($TEST_CLASS)" >> "$REPORT_FILE"
    PASSED=$((PASSED + 1))
  else
    echo "FAIL"
    echo "  [FAIL] $TEST_NAME ($TEST_CLASS)" >> "$REPORT_FILE"
    FAILED=$((FAILED + 1))
    FAILED_LIST+=("$TEST_NAME")
  fi
done

echo ""
echo "============================================================================"
echo "  API FUNCTIONAL TEST SUMMARY"
echo "============================================================================"
echo "  Total Test Classes:  $TOTAL"
echo "  Passed:              $PASSED"
echo "  Failed:              $FAILED"
echo ""

if [ $FAILED -gt 0 ]; then
  echo "  Failed Tests:"
  for ITEM in "${FAILED_LIST[@]}"; do
    echo "    - $ITEM"
  done
  echo ""
fi

echo "  Results written to: $REPORT_FILE"
echo "============================================================================"

# Write summary to report
echo "" >> "$REPORT_FILE"
echo "============================================" >> "$REPORT_FILE"
echo "SUMMARY" >> "$REPORT_FILE"
echo "  Total: $TOTAL | Passed: $PASSED | Failed: $FAILED" >> "$REPORT_FILE"
echo "  Completed: $(date '+%Y-%m-%d %H:%M:%S')" >> "$REPORT_FILE"

if [ $FAILED -gt 0 ]; then
  exit 1
fi
exit 0
