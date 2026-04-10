#!/usr/bin/env bash
# ============================================================================
# Storefront Test Runner - One-Click Test Execution
# Runs both unit_tests/ and API_tests/ suites and produces a summary report.
# ============================================================================

set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_FILE="$SCRIPT_DIR/test_results_summary.txt"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

echo "╔══════════════════════════════════════════════════════════════════════════╗"
echo "║          STOREFRONT - COMPLETE TEST SUITE EXECUTION                     ║"
echo "║          Started: $TIMESTAMP                              ║"
echo "╚══════════════════════════════════════════════════════════════════════════╝"
echo ""

# Initialize report
cat > "$REPORT_FILE" << EOF
================================================================================
STOREFRONT TEST RESULTS SUMMARY
Run started: $TIMESTAMP
================================================================================

EOF

UNIT_EXIT=0
API_EXIT=0

# ---- Phase 1: Unit Tests ----
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PHASE 1: UNIT TESTS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "Running unit tests via Gradle..."
echo ""

cd "$SCRIPT_DIR"
chmod +x ./gradlew 2>/dev/null
if ./gradlew test --tests "com.eaglepoint.storefront.security.*" \
                  --tests "com.eaglepoint.storefront.domain.*" \
                  --tests "com.eaglepoint.storefront.data.*" \
                  --tests "com.eaglepoint.storefront.ingestion.*" \
                  --tests "com.eaglepoint.storefront.notification.*" \
                  --tests "com.eaglepoint.storefront.pricing.*" \
                  --tests "com.eaglepoint.storefront.quality.*" \
                  --tests "com.eaglepoint.storefront.ui.*" \
                  --console=plain 2>&1 | tee -a "$REPORT_FILE"; then
  echo ""
  echo "  ✓ UNIT TESTS: ALL PASSED"
  echo "" >> "$REPORT_FILE"
  echo "UNIT TESTS: ALL PASSED" >> "$REPORT_FILE"
else
  UNIT_EXIT=1
  echo ""
  echo "  ✗ UNIT TESTS: SOME FAILURES"
  echo "" >> "$REPORT_FILE"
  echo "UNIT TESTS: SOME FAILURES (see output above)" >> "$REPORT_FILE"
fi

echo ""

# ---- Phase 2: API Functional Tests ----
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  PHASE 2: API FUNCTIONAL TESTS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "Running API functional tests via Gradle..."
echo ""

if ./gradlew test --tests "com.eaglepoint.storefront.api.*" \
                  --console=plain 2>&1 | tee -a "$REPORT_FILE"; then
  echo ""
  echo "  ✓ API TESTS: ALL PASSED"
  echo "" >> "$REPORT_FILE"
  echo "API TESTS: ALL PASSED" >> "$REPORT_FILE"
else
  API_EXIT=1
  echo ""
  echo "  ✗ API TESTS: SOME FAILURES"
  echo "" >> "$REPORT_FILE"
  echo "API TESTS: SOME FAILURES (see output above)" >> "$REPORT_FILE"
fi

echo ""

# ---- Final Summary ----
echo "╔══════════════════════════════════════════════════════════════════════════╗"
echo "║                        FINAL TEST SUMMARY                              ║"
echo "╚══════════════════════════════════════════════════════════════════════════╝"
echo ""

# Count test files
UNIT_COUNT=$(find "$SCRIPT_DIR/app/src/test/java/com/eaglepoint/storefront" \
  -name "*Test.kt" \
  ! -path "*/api/*" | wc -l | tr -d ' ')
API_COUNT=$(find "$SCRIPT_DIR/app/src/test/java/com/eaglepoint/storefront/api" \
  -name "*Test.kt" 2>/dev/null | wc -l | tr -d ' ')
TOTAL_COUNT=$((UNIT_COUNT + API_COUNT))

echo "  Test Files:"
echo "    Unit test classes:     $UNIT_COUNT"
echo "    API test classes:      $API_COUNT"
echo "    Total test classes:    $TOTAL_COUNT"
echo ""

if [ $UNIT_EXIT -eq 0 ] && [ $API_EXIT -eq 0 ]; then
  FINAL_STATUS="ALL TESTS PASSED"
  FINAL_EXIT=0
elif [ $UNIT_EXIT -eq 0 ]; then
  FINAL_STATUS="UNIT TESTS PASSED, API TESTS FAILED"
  FINAL_EXIT=1
elif [ $API_EXIT -eq 0 ]; then
  FINAL_STATUS="UNIT TESTS FAILED, API TESTS PASSED"
  FINAL_EXIT=1
else
  FINAL_STATUS="BOTH TEST SUITES HAVE FAILURES"
  FINAL_EXIT=1
fi

echo "  Result:  $FINAL_STATUS"
echo ""
echo "  Detailed report: $REPORT_FILE"
echo "  HTML report:     app/build/reports/tests/testDebugUnitTest/index.html"
echo ""
echo "  Completed: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Append final summary to report
cat >> "$REPORT_FILE" << EOF

================================================================================
FINAL SUMMARY
  Unit test classes:     $UNIT_COUNT
  API test classes:      $API_COUNT
  Total test classes:    $TOTAL_COUNT
  Status:                $FINAL_STATUS
  Completed:             $(date '+%Y-%m-%d %H:%M:%S')
================================================================================
EOF

exit $FINAL_EXIT
