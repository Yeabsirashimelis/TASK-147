package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.AuditEventDao
import com.eaglepoint.storefront.data.db.entity.AuditEventEntity
import com.eaglepoint.storefront.domain.model.ActorType
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuditRepositoryTest {

    private lateinit var auditEventDao: AuditEventDao
    private lateinit var auditRepository: AuditRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        auditEventDao = mockk(relaxed = true)
        auditRepository = AuditRepository(auditEventDao, testDispatcher)
    }

    @Test
    fun `log inserts event into dao`() = runTest(testDispatcher) {
        val entitySlot = slot<AuditEventEntity>()
        coEvery { auditEventDao.insert(capture(entitySlot)) } returns Unit

        val event = AuditEvent(
            id = "event-1",
            userId = "user-1",
            actorType = ActorType.USER,
            action = AuditAction.LOGIN_SUCCESS,
            target = "auth",
            timestamp = 1000L,
            detail = "Login from device"
        )

        auditRepository.log(event)

        coVerify(exactly = 1) { auditEventDao.insert(any()) }
        val captured = entitySlot.captured
        assertThat(captured.id).isEqualTo("event-1")
        assertThat(captured.action).isEqualTo("LOGIN_SUCCESS")
        assertThat(captured.actorType).isEqualTo("USER")
        assertThat(captured.userId).isEqualTo("user-1")
    }

    @Test
    fun `log persists SYSTEM actor with null userId`() = runTest(testDispatcher) {
        val entitySlot = slot<AuditEventEntity>()
        coEvery { auditEventDao.insert(capture(entitySlot)) } returns Unit

        val event = AuditEvent(
            id = "event-2",
            userId = null,
            actorType = ActorType.SYSTEM,
            action = AuditAction.INGESTION_STARTED,
            target = "source_rule",
            timestamp = 1000L
        )

        auditRepository.log(event)

        val captured = entitySlot.captured
        assertThat(captured.userId).isNull()
        assertThat(captured.actorType).isEqualTo("SYSTEM")
    }

    @Test
    fun `log masks target IDs`() = runTest(testDispatcher) {
        val entitySlot = slot<AuditEventEntity>()
        coEvery { auditEventDao.insert(capture(entitySlot)) } returns Unit

        val event = AuditEvent(
            id = "event-1",
            userId = "user-1",
            action = AuditAction.RECORD_UPDATED,
            target = "article",
            targetId = "550e8400-e29b-41d4-a716-446655440000",
            timestamp = 1000L
        )

        auditRepository.log(event)

        val captured = entitySlot.captured
        assertThat(captured.targetId).isEqualTo("550e****00")
    }

    @Test
    fun `getPage returns ordered results`() = runTest {
        val entities = listOf(
            AuditEventEntity("e1", "u1", "USER", "LOGIN_SUCCESS", null, null, 2000L, null),
            AuditEventEntity("e2", "u1", "USER", "LOGIN_FAILURE", null, null, 1000L, null)
        )
        coEvery { auditEventDao.getPage(50, 0) } returns flowOf(entities)

        val result = auditRepository.getPage(50, 0).first()

        assertThat(result).hasSize(2)
        assertThat(result[0].action).isEqualTo(AuditAction.LOGIN_SUCCESS)
        assertThat(result[1].action).isEqualTo(AuditAction.LOGIN_FAILURE)
    }

    @Test
    fun `getByAction filters correctly`() = runTest {
        val entities = listOf(
            AuditEventEntity("e1", "u1", "USER", "BACKUP_COMPLETED", "backup", null, 1000L, null)
        )
        coEvery { auditEventDao.getByAction("BACKUP_COMPLETED") } returns flowOf(entities)

        val result = auditRepository.getByAction(AuditAction.BACKUP_COMPLETED).first()

        assertThat(result).hasSize(1)
        assertThat(result[0].action).isEqualTo(AuditAction.BACKUP_COMPLETED)
    }

    @Test
    fun `count delegates to dao`() = runTest(testDispatcher) {
        coEvery { auditEventDao.count() } returns 42

        val result = auditRepository.count()

        assertThat(result).isEqualTo(42)
    }
}
