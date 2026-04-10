#!/usr/bin/env bash
# ============================================================================
# Unit Test Runner for Storefront Android Application
# Executes all unit tests organized by module and reports results.
# ============================================================================

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT_FILE="$SCRIPT_DIR/unit_test_results.txt"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

echo "============================================================================"
echo "  STOREFRONT UNIT TEST SUITE"
echo "  Started: $TIMESTAMP"
echo "============================================================================"
echo ""

# ---- Test categories mapped to Gradle test filter patterns ----
declare -A TEST_MODULES
TEST_MODULES=(
  ["Security_PasswordHasher"]="com.eaglepoint.storefront.security.PasswordHasherTest"
  ["Security_SensitiveFieldMasker"]="com.eaglepoint.storefront.security.SensitiveFieldMaskerTest"
  ["Security_ChecksumUtil"]="com.eaglepoint.storefront.security.ChecksumUtilTest"
  ["Security_SessionManager"]="com.eaglepoint.storefront.security.SessionManagerTest"
  ["Validation_InputValidator"]="com.eaglepoint.storefront.domain.validation.InputValidatorTest"
  ["Auth_LoginUseCase"]="com.eaglepoint.storefront.domain.usecase.LoginUseCaseTest"
  ["Auth_AuditEvent"]="com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCaseTest"
  ["Auth_AuthRepository"]="com.eaglepoint.storefront.data.repository.AuthRepositoryTest"
  ["Auth_AuditRepository"]="com.eaglepoint.storefront.data.repository.AuditRepositoryTest"
  ["Auth_Authorization"]="com.eaglepoint.storefront.domain.usecase.AuthorizationEnforcementTest"
  ["Auth_RoleGuard"]="com.eaglepoint.storefront.domain.usecase.RoleGuardTest"
  ["Backup_BackupUseCase"]="com.eaglepoint.storefront.domain.usecase.BackupUseCaseTest"
  ["Backup_RestoreUseCase"]="com.eaglepoint.storefront.domain.usecase.RestoreUseCaseTest"
  ["Backup_Wiring"]="com.eaglepoint.storefront.domain.usecase.BackupRestoreWiringTest"
  ["Ingestion_FeedParser"]="com.eaglepoint.storefront.ingestion.FeedParserTest"
  ["Ingestion_HtmlScrape"]="com.eaglepoint.storefront.ingestion.HtmlScrapeParserTest"
  ["Ingestion_ContentFilter"]="com.eaglepoint.storefront.ingestion.ContentFilterTest"
  ["Ingestion_UserAgentRotator"]="com.eaglepoint.storefront.ingestion.UserAgentRotatorTest"
  ["Ingestion_RequestPacer"]="com.eaglepoint.storefront.ingestion.RequestPacerTest"
  ["Ingestion_Engine"]="com.eaglepoint.storefront.ingestion.IngestionEngineTest"
  ["Ingestion_SourceRule"]="com.eaglepoint.storefront.domain.usecase.SaveSourceRuleUseCaseTest"
  ["Ingestion_RunIngestion"]="com.eaglepoint.storefront.domain.usecase.RunIngestionUseCaseTest"
  ["Ingestion_Alerts"]="com.eaglepoint.storefront.domain.usecase.CheckIngestionAlertsUseCaseTest"
  ["Ingestion_SourceRepo"]="com.eaglepoint.storefront.data.repository.SourceRuleRepositoryTest"
  ["Quality_BatchValidator"]="com.eaglepoint.storefront.quality.BatchValidatorTest"
  ["Quality_ValidationRule"]="com.eaglepoint.storefront.quality.ValidationRuleTest"
  ["Quality_ValidateBatch"]="com.eaglepoint.storefront.quality.ValidateBatchUseCaseTest"
  ["Quality_CatalogInventory"]="com.eaglepoint.storefront.quality.BatchValidationCatalogInventoryTest"
  ["Article_Search"]="com.eaglepoint.storefront.domain.usecase.SearchArticlesUseCaseTest"
  ["Article_Detail"]="com.eaglepoint.storefront.domain.usecase.GetArticleDetailUseCaseTest"
  ["Article_SaveOffline"]="com.eaglepoint.storefront.domain.usecase.SaveArticleOfflineUseCaseTest"
  ["Article_Curation"]="com.eaglepoint.storefront.domain.usecase.CurateArticleUseCaseTest"
  ["Article_ReviewIngestion"]="com.eaglepoint.storefront.domain.usecase.ReviewIngestionUseCaseTest"
  ["Cart_AddToCart"]="com.eaglepoint.storefront.domain.usecase.AddToCartUseCaseTest"
  ["Cart_MergeCarts"]="com.eaglepoint.storefront.domain.usecase.MergeCartsUseCaseTest"
  ["Checkout_CheckoutUseCase"]="com.eaglepoint.storefront.domain.usecase.CheckoutUseCaseTest"
  ["Checkout_Receipt"]="com.eaglepoint.storefront.domain.usecase.CheckoutReceiptTest"
  ["Pricing_PricingEngine"]="com.eaglepoint.storefront.pricing.PricingEngineTest"
  ["Notification_TemplateRenderer"]="com.eaglepoint.storefront.notification.TemplateRendererTest"
  ["Notification_Dispatcher"]="com.eaglepoint.storefront.notification.NotificationDispatcherTest"
  ["Notification_SendUseCase"]="com.eaglepoint.storefront.notification.SendNotificationUseCaseTest"
  ["UI_ImageLoader"]="com.eaglepoint.storefront.ui.home.ImageLoaderTest"
)

TOTAL=0
PASSED=0
FAILED=0
FAILED_LIST=()

# Initialize report
echo "STOREFRONT UNIT TEST RESULTS" > "$REPORT_FILE"
echo "Run started: $TIMESTAMP" >> "$REPORT_FILE"
echo "============================================" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

# Run all test modules
for MODULE_NAME in $(echo "${!TEST_MODULES[@]}" | tr ' ' '\n' | sort); do
  TEST_CLASS="${TEST_MODULES[$MODULE_NAME]}"
  TOTAL=$((TOTAL + 1))
  printf "  %-45s ... " "$MODULE_NAME"

  if cd "$PROJECT_ROOT" && ./gradlew test --tests "$TEST_CLASS" --console=plain --quiet 2>/dev/null; then
    echo "PASS"
    echo "  [PASS] $MODULE_NAME ($TEST_CLASS)" >> "$REPORT_FILE"
    PASSED=$((PASSED + 1))
  else
    echo "FAIL"
    echo "  [FAIL] $MODULE_NAME ($TEST_CLASS)" >> "$REPORT_FILE"
    FAILED=$((FAILED + 1))
    FAILED_LIST+=("$MODULE_NAME")
  fi
done

echo ""
echo "============================================================================"
echo "  UNIT TEST SUMMARY"
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

# Exit with non-zero if any test failed
if [ $FAILED -gt 0 ]; then
  exit 1
fi
exit 0
