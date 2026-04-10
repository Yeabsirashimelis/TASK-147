# Sports Commerce & News Management System -- Open Questions

---

## 1. Guest-to-User Cart Merge Strategy

**Question:** When a guest user logs in and already has an existing user cart, how should items with the same SKU be reconciled -- sum the quantities from both carts, or take the higher of the two quantities?

**My Understanding:** Summing quantities is the more common e-commerce pattern (Amazon, Shopify) because it respects the intent behind each separate add-to-cart action. Taking the max would silently discard items the user explicitly added. However, summing could push the combined quantity above available inventory.

**Solution:** Sum quantities from guest and user carts during merge. Immediately after merge, run inventory validation on every merged line item. If the summed quantity exceeds current local catalog stock, clamp the quantity to available inventory and surface an in-app notification explaining the adjustment (e.g., "Quantity for {productName} was reduced to {availableQty} due to limited stock"). Log the merge event, including original quantities from both carts and the final merged result, to the AuditEvent table. The guest cart is deleted after a successful merge.

---

## 2. Price-Lock Expiry and Inventory Reservation

**Question:** During the 30-minute price-lock window after checkout starts, is inventory soft-reserved (decremented from available stock so other users cannot claim it) or is it only validated at the moment of final confirmation with no reservation?

**My Understanding:** Since this is an offline-first, single-device application with a local Room database, there is no multi-user contention over the same inventory in real time. Soft-reservation mechanics (common in server-side e-commerce) add complexity that may not be justified here. The primary risk is the user themselves modifying catalog data or an ingestion job updating inventory mid-checkout.

**Solution:** Use a validate-on-lock, revalidate-on-confirm approach. When checkout starts and the price-lock is created, record a PriceLockSession row containing the locked prices, quantities, and a 30-minute expiry timestamp. Mark the relevant CartItem rows as "locked." Do not decrement inventory at lock time. When the user confirms the order within 30 minutes, re-validate inventory against the local catalog. If inventory is still sufficient, complete the order and decrement stock. If the lock expires, release the lock, notify the user that prices may have changed, and require them to re-initiate checkout. If inventory has dropped below the locked quantity during the window, block confirmation and prompt the user to adjust quantities.

---

## 3. Coupon and Automatic Discount Stacking

**Question:** The pricing engine applies a 10% discount on orders over $50 automatically, and also enforces "one coupon per order." Can a user apply a coupon on top of the automatic 10% discount, or are the automatic discount and coupon mutually exclusive?

**My Understanding:** The 10% off orders over $50 reads like an automatic, rule-based promotion, while coupons are user-entered codes. These are conceptually different mechanisms. Blocking their combination would frustrate users who expect the automatic discount as a baseline. However, stacking could erode margins more than intended.

**Solution:** Allow stacking. The automatic 10% discount and a user-entered coupon are treated as separate PriceRule types: one with trigger "AUTO" (applied when subtotal exceeds $50) and one with trigger "COUPON" (applied when the user enters a valid code). The "one coupon per order" constraint means only one COUPON-type rule can be active, but it does not prevent an AUTO-type rule from also applying. Calculation order: apply the automatic percentage discount to the subtotal first, then apply the coupon to the discounted subtotal. Document this stacking behavior clearly in the Admin pricing configuration UI so Administrators understand the interaction. If the business later wants mutual exclusivity, add a "stackable" boolean flag to PriceRule.

---

## 4. Tax Rate Table Maintenance and Versioning

**Question:** Who is responsible for maintaining state-level tax rate tables -- can Administrators manage them through the app's UI, or are rates loaded via a bulk import? How are changes to rates tracked over time so that historical orders reflect the rate that was in effect at the time of purchase?

**My Understanding:** Tax rates change infrequently (typically once or twice a year per jurisdiction), but accuracy is critical. A full UI editor may be over-engineered for a small, relatively static dataset. However, Admins need some mechanism to update rates without a code change.

**Solution:** Provide both a simple Admin UI for individual rate edits and a CSV bulk-import option for initial setup or mass updates. The TaxRate table includes an effectiveDate and an optional expiryDate, allowing multiple rate versions per state to coexist. When calculating tax for an order, the pricing engine selects the rate where the current date falls within the effective window. Completed orders store the applied tax rate and amount as snapshot fields on the OrderLineItem, so historical orders are never affected by subsequent rate changes. Every TaxRate insert or update is recorded in AuditEvent with the Admin's userId, the previous rate, the new rate, and the effectiveDate. Bulk imports are processed as a single audited batch.

---

## 5. Ingestion Rule Versioning for In-Flight Jobs

**Question:** When an Administrator updates a SourceRule while an ingestion job for that source is already running or queued in WorkManager, does the in-flight job continue using the rule version it started with, or does it pick up the newly saved rule?

**My Understanding:** Allowing a mid-flight rule swap risks partial ingestion where half the articles are parsed with old rules and half with new rules, leading to inconsistent data and difficult-to-diagnose quality issues. Pinning to the version at job start is safer and more predictable.

**Solution:** Snapshot the SourceRule at job start. When the WorkManager worker begins execution, it reads the current SourceRule and copies the relevant fields (selectors, filters, allow/block lists, version number) into a local in-memory object. The DataBatchVersion row created for that run records the ruleVersion used. Any Admin edits saved while the job is running take effect only on the next scheduled or manually triggered run. The SourceRule table maintains a monotonically incrementing version integer, bumped on every save. If the Admin wants the new rule applied immediately, they can manually trigger a new ingestion run from the Ingestion Console after the current one completes or is cancelled.

---

## 6. Anti-Scraping User-Agent Pool Configuration

**Question:** How many user-agent strings are in the rotation pool, and are they hardcoded in the app or configurable by the Administrator through the Ingestion Console?

**My Understanding:** A hardcoded pool is simpler but becomes stale as browser versions advance, potentially making the agents more detectable. A configurable pool gives Admins flexibility but adds UI complexity and a risk of misconfiguration (e.g., empty pool or syntactically invalid agents).

**Solution:** Ship with a default pool of 10 user-agent strings representing recent versions of Chrome, Firefox, Safari, and Edge on common platforms (Windows, macOS, Android). Store these in a UserAgentPool Room table seeded on first launch. Admins can add, edit, disable, or delete entries through the Ingestion Console UI, but the app enforces a minimum of 3 active agents at all times to maintain effective rotation. Each SourceRule can optionally override the global pool with a source-specific subset if a particular site requires a narrower agent profile. The selection algorithm picks a random agent from the applicable pool for each request. Pool changes are logged in AuditEvent.

---

## 7. Exponential Backoff Intervals and Post-Failure Behavior

**Question:** For the 3 retry attempts over 15 minutes with exponential backoff, what are the exact delay intervals between attempts? After all 3 attempts fail, what happens to the job -- is it silently abandoned, rescheduled, or flagged?

**My Understanding:** A common exponential backoff pattern fitting within 15 minutes would be delays of roughly 1, 4, and 10 minutes (doubling with some jitter). After exhausting retries, the failure needs to be recorded for the 5-in-24-hours alerting threshold.

**Solution:** Use the following intervals: Attempt 1 fires immediately. On failure, wait 2 minutes before Attempt 2. On second failure, wait 5 minutes before Attempt 3. This totals approximately 7 minutes of wait time plus execution time, well within the 15-minute window. Add random jitter of plus or minus 20% to each delay to avoid synchronized retry storms across multiple sources. After all 3 attempts fail, mark the DataBatchVersion record as FAILED with the last error message and increment the source's rolling 24-hour failure counter. The job is not automatically rescheduled -- it will run again at the next regular scheduled interval. If the failure counter reaches 5 within 24 hours, trigger the in-app Admin alert. Admins can also manually trigger an immediate retry from the monitoring log screen.

---

## 8. Offline Article Storage Limits

**Question:** Is there a cap on the number or total size of articles a user can save for offline reading? What happens when the device runs low on storage?

**My Understanding:** Without limits, a user could save thousands of articles with images, consuming gigabytes of storage on a budget device. Android can revoke storage access or degrade performance when the device is critically low on space. A sensible default limit protects the user experience.

**Solution:** Enforce a configurable maximum of 500 saved articles per user (Admin-adjustable via app settings). When the limit is reached, the user is prompted to remove older saved articles before saving new ones -- no automatic deletion. For storage pressure, monitor available disk space before each save operation using Android's StorageStatsManager. If available space drops below 100 MB, block new saves and display a warning directing the user to free space or remove saved articles. Saved article content is stored as compressed text in Room; associated images are cached separately in the LRU image cache (already capped at 20 MB) and are re-fetched if evicted and connectivity is available. Add a "Manage Saved Articles" screen showing per-article storage usage to help users make informed cleanup decisions.

---

## 9. Ingestion Failure Alert Threshold Scope

**Question:** The 5-failures-in-24-hours alerting threshold -- does it apply per individual source, or is it a global count across all configured sources?

**My Understanding:** A global threshold could mask a single problematic source if many sources are configured (e.g., 10 sources each failing once would not trigger the alert even though 10 failures occurred). Conversely, a per-source threshold is more targeted but could flood the Admin with alerts if multiple sources fail simultaneously.

**Solution:** Apply the threshold per-source. Each source independently tracks its rolling 24-hour failure count. When any single source accumulates 5 failures within 24 hours, fire an in-app alert that identifies the specific source by name. To prevent alert fatigue, consolidate: if multiple sources breach the threshold within the same 10-minute window, group them into a single summary alert listing all affected sources. The Admin can acknowledge and dismiss alerts. Acknowledged alerts for a source are not re-fired unless the source recovers (at least one successful run) and then fails again. Display a dashboard widget on the Ingestion Console showing each source's current 24-hour failure count for at-a-glance monitoring.

---

## 10. Data Quality Publish-Time Validation and Timezone Handling

**Question:** The rule "publish time within 365 days" -- does this mean only past dates within the last year, or does it also allow future dates? How are timezones handled when comparing publish times from sources in different regions?

**My Understanding:** Future-dated articles are a common content management practice (embargoed content), but in a scraping/ingestion context, a future date usually signals a parsing error or a misconfigured source rather than intentional scheduling. Timezone mismatches could cause articles near the boundary to be incorrectly accepted or rejected.

**Solution:** Reject both future dates and dates older than 365 days. The valid window is: (now minus 365 days) to (now plus 2 hours). The 2-hour grace period accommodates minor clock skew between the source server and the local device, and timezone ambiguity in feeds that omit explicit UTC offsets. All publish times are normalized to UTC at ingestion time. If a source provides a timezone offset, use it; if the feed omits timezone information, default to UTC and flag the article with a DATA_QUALITY_WARNING so Editors can review. The SourceRule configuration includes an optional defaultTimezone field the Admin can set for sources known to publish in a specific local time. Articles failing the date check are excluded from the batch and logged with reason PUBLISH_DATE_OUT_OF_RANGE in the DataBatchVersion record.

---

## 11. Inventory Validation Timing in Cart and Checkout Flow

**Question:** Is inventory validated only when an item is added to the cart, only at checkout start, or at both points? If validated only at add-to-cart, what happens if inventory changes (e.g., via a catalog ingestion update) between cart addition and checkout?

**My Understanding:** Validating only at add-to-cart creates a window where the cart can reference stale inventory. Validating only at checkout is a poor user experience because the user discovers problems late. Validating at both points is safest but must handle the case where items become unavailable gracefully.

**Solution:** Validate at three points: (1) when the item is added to the cart -- reject immediately if current inventory is zero; (2) when the checkout screen is opened -- re-validate all cart items against current inventory, surface warnings for any items where requested quantity now exceeds stock, and allow the user to adjust before proceeding; (3) at final order confirmation (within the price-lock window) -- perform a last check before committing the order. If inventory for any item drops to zero between add-to-cart and checkout, mark the item in the cart as "Unavailable" with a visual indicator and exclude it from the checkout total. If inventory is merely reduced (still above zero but below cart quantity), auto-adjust the quantity downward and notify the user. All validation uses the local Room catalog as the source of truth.

---

## 12. Backup Integrity Check Algorithm

**Question:** What algorithm is used for integrity verification during restore -- a SHA-256 hash of the entire database file, per-table checksums, or something else? Where is the hash stored, and how is it protected from tampering?

**My Understanding:** A single SHA-256 hash of the full database file is simple and fast for the expected database sizes (likely under 500 MB). Per-table checksums add granularity for partial-corruption detection but increase complexity. The hash must be stored such that it cannot be trivially modified alongside a tampered backup.

**Solution:** Use SHA-256 over the entire Room database file. When the user initiates a backup, the app exports the database file and computes its SHA-256 hash. The hash, along with metadata (app version, backup timestamp, database schema version, and row counts per key table), is written to a separate JSON manifest file. Both the backup file and the manifest are stored together in the user-selected local directory. The manifest's hash is additionally signed using the Android Keystore-backed key (the same key infrastructure used for encryption at rest), producing an HMAC-SHA256 signature stored in the manifest. On restore, the app first verifies the HMAC signature on the manifest to confirm it has not been tampered with, then recomputes the SHA-256 of the backup file and compares it to the manifest value. If either check fails, the restore is blocked and the user is informed. The row-count metadata in the manifest provides a secondary sanity check after the database is restored and opened.

---

## 13. Step-Up Re-Authentication Session Duration

**Question:** After a user completes step-up re-authentication for sensitive operations (exports, backup/restore), does the elevated session authorize only the single requested action, or does it remain valid for a time window allowing multiple sensitive operations without re-prompting?

**My Understanding:** A single-action scope is the most secure but can be annoying if the user needs to perform several sensitive operations in sequence (e.g., export data and then create a backup). A short time window (a few minutes) balances security and usability.

**Solution:** Use a 5-minute time window. After successful step-up re-authentication, record a stepUpAuthTimestamp on the local session. Any sensitive operation initiated within 5 minutes of that timestamp proceeds without re-prompting. After 5 minutes, the elevated session expires and the next sensitive operation requires re-authentication. The timer is not extended by subsequent sensitive actions within the window -- it always counts from the original re-auth moment to prevent indefinite session extension. If the app is backgrounded or the screen is locked at any point, the step-up session is invalidated immediately regardless of remaining time. The step-up event (grant and expiry) is recorded in AuditEvent. Sensitive operations covered: data export, backup creation, backup restore, and any future operations the team designates as elevated-privilege.

---

## 14. Editor Content Curation and Article Lifecycle

**Question:** Can Editors unpublish or retract articles that are already visible to Regular Users? What states can an article move through, and what transitions are allowed per role?

**My Understanding:** Editors in a CMS context typically have the authority to control content visibility. Without a clear state machine, it is ambiguous whether "curate content" means Editors can only promote/feature articles or also remove them from the user-facing feed. A defined lifecycle prevents invalid transitions and supports auditability.

**Solution:** Define the following article states and transitions:

- **INGESTED**: Initial state after a successful ingestion parse. Not visible to Regular Users. Auto-transitions to PUBLISHED if auto-publish is enabled on the SourceRule; otherwise remains here for review.
- **PUBLISHED**: Visible to Regular Users in browse/search. Transition from INGESTED by Editor (manual publish) or system (auto-publish).
- **FEATURED**: A promoted subset of PUBLISHED articles shown in a highlighted section. Transition from PUBLISHED by Editor only.
- **RETRACTED**: Removed from user-facing feeds. Transition from PUBLISHED or FEATURED by Editor or Admin. The article remains in the database for audit purposes but is excluded from Regular User queries.
- **ARCHIVED**: End-of-life state for old content. Transition from any state by Admin only. Archived articles are excluded from all feeds but retained for data lineage.

Editors can perform: INGESTED to PUBLISHED, PUBLISHED to FEATURED, FEATURED to PUBLISHED (un-feature), PUBLISHED to RETRACTED, FEATURED to RETRACTED. Admins can perform all transitions. Regular Users cannot change article state. Every state transition is logged in AuditEvent with the userId, article ID, previous state, new state, and an optional reason field (required for RETRACTED). Articles saved offline by users before retraction remain accessible in their saved list with a visual "Retracted" badge.

---

## 15. WorkManager Constraints and Stale Data Fallback

**Question:** Ingestion is constrained to idle and charging states. If a user's device never meets both conditions simultaneously (e.g., a phone always in use during charging), how stale can the content become? Is there a fallback to ensure data freshness?

**My Understanding:** The combination of idle AND charging can be a high bar on actively used devices. A phone that is only charged overnight while the user sleeps may meet the criteria, but a tablet used while plugged in may never go idle. Content could become days or weeks stale, degrading the app's value.

**Solution:** Implement a staleness fallback. The primary WorkManager job retains the idle+charging constraint for battery-friendly operation. However, introduce a secondary "freshness guard" periodic WorkManager job with relaxed constraints (charging OR battery above 50%, no idle requirement) that runs every 24 hours. This secondary job checks each source's last successful ingestion timestamp. If any source has not been refreshed in over 48 hours, the guard job executes a lightweight ingestion run for only those stale sources, limited to the most recent 50 items per source to minimize resource usage. Additionally, the user can manually trigger a full ingestion refresh from the app's settings screen at any time, regardless of device state, with a warning about potential battery impact. The app displays a "Last updated: {timestamp}" indicator on the article feed so users are aware of data freshness. If data is older than 72 hours, a subtle banner appears suggesting a manual refresh.
