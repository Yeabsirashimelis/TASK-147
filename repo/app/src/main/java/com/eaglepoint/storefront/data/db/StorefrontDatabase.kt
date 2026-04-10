package com.eaglepoint.storefront.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.eaglepoint.storefront.data.db.converter.Converters
import com.eaglepoint.storefront.data.db.dao.ArticleDao
import com.eaglepoint.storefront.data.db.dao.AuditEventDao
import com.eaglepoint.storefront.data.db.dao.OrderDao
import com.eaglepoint.storefront.data.db.dao.CartDao
import com.eaglepoint.storefront.data.db.dao.CartLineItemDao
import com.eaglepoint.storefront.data.db.dao.CatalogItemDao
import com.eaglepoint.storefront.data.db.dao.CouponDao
import com.eaglepoint.storefront.data.db.dao.DataBatchVersionDao
import com.eaglepoint.storefront.data.db.dao.DataLineageDao
import com.eaglepoint.storefront.data.db.dao.IngestionAlertDao
import com.eaglepoint.storefront.data.db.dao.IngestionJobRunDao
import com.eaglepoint.storefront.data.db.dao.InventorySnapshotDao
import com.eaglepoint.storefront.data.db.dao.NotificationDao
import com.eaglepoint.storefront.data.db.dao.NotificationTemplateDao
import com.eaglepoint.storefront.data.db.dao.PriceRuleDao
import com.eaglepoint.storefront.data.db.dao.SourceRuleDao
import com.eaglepoint.storefront.data.db.dao.TaxRateDao
import com.eaglepoint.storefront.data.db.dao.UserDao
import com.eaglepoint.storefront.data.db.dao.ValidationErrorDao
import com.eaglepoint.storefront.data.db.entity.AuditEventEntity
import com.eaglepoint.storefront.data.db.entity.OrderEntity
import com.eaglepoint.storefront.data.db.entity.OrderLineItemEntity
import com.eaglepoint.storefront.data.db.entity.DataBatchVersionEntity
import com.eaglepoint.storefront.data.db.entity.DataLineageEntity
import com.eaglepoint.storefront.data.db.entity.UserEntity
import com.eaglepoint.storefront.data.db.entity.ValidationErrorEntity
import com.eaglepoint.storefront.data.db.entity.ArticleEntity
import com.eaglepoint.storefront.data.db.entity.CartEntity
import com.eaglepoint.storefront.data.db.entity.CartLineItemEntity
import com.eaglepoint.storefront.data.db.entity.CatalogItemEntity
import com.eaglepoint.storefront.data.db.entity.CouponEntity
import com.eaglepoint.storefront.data.db.entity.IngestionAlertEntity
import com.eaglepoint.storefront.data.db.entity.IngestionJobRunEntity
import com.eaglepoint.storefront.data.db.entity.InventorySnapshotEntity
import com.eaglepoint.storefront.data.db.entity.NotificationEntity
import com.eaglepoint.storefront.data.db.entity.PriceRuleEntity
import com.eaglepoint.storefront.data.db.entity.SourceRuleEntity
import com.eaglepoint.storefront.data.db.entity.NotificationTemplateEntity
import com.eaglepoint.storefront.data.db.entity.TaxRateEntity

@Database(
    entities = [
        UserEntity::class,
        AuditEventEntity::class,
        DataBatchVersionEntity::class,
        DataLineageEntity::class,
        SourceRuleEntity::class,
        IngestionJobRunEntity::class,
        ArticleEntity::class,
        IngestionAlertEntity::class,
        CatalogItemEntity::class,
        InventorySnapshotEntity::class,
        CartEntity::class,
        CartLineItemEntity::class,
        PriceRuleEntity::class,
        CouponEntity::class,
        TaxRateEntity::class,
        NotificationEntity::class,
        NotificationTemplateEntity::class,
        ValidationErrorEntity::class,
        OrderEntity::class,
        OrderLineItemEntity::class,
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class StorefrontDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun sourceRuleDao(): SourceRuleDao
    abstract fun ingestionJobRunDao(): IngestionJobRunDao
    abstract fun articleDao(): ArticleDao
    abstract fun dataBatchVersionDao(): DataBatchVersionDao
    abstract fun dataLineageDao(): DataLineageDao
    abstract fun ingestionAlertDao(): IngestionAlertDao
    abstract fun validationErrorDao(): ValidationErrorDao
    abstract fun cartDao(): CartDao
    abstract fun cartLineItemDao(): CartLineItemDao
    abstract fun catalogItemDao(): CatalogItemDao
    abstract fun inventorySnapshotDao(): InventorySnapshotDao
    abstract fun priceRuleDao(): PriceRuleDao
    abstract fun couponDao(): CouponDao
    abstract fun taxRateDao(): TaxRateDao
    abstract fun notificationDao(): NotificationDao
    abstract fun notificationTemplateDao(): NotificationTemplateDao
    abstract fun orderDao(): OrderDao

    companion object {
        const val DATABASE_NAME = "storefront.db"
    }
}
