package com.eaglepoint.storefront.domain.usecase

import android.net.Uri
import com.eaglepoint.storefront.data.repository.BackupRepository
import com.eaglepoint.storefront.domain.model.BackupMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BackupUseCaseTest {

    private lateinit var backupRepository: BackupRepository
    private lateinit var reAuthenticate: ReAuthenticateUseCase
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var roleGuard: RoleGuard
    private lateinit var backupUseCase: BackupUseCase

    private val testUri = mockk<Uri>()
    private val testMetadata = BackupMetadata(
        dbVersion = 1,
        timestamp = System.currentTimeMillis(),
        checksum = "abc123def456",
        appVersion = "1.0.0"
    )

    @BeforeEach
    fun setUp() {
        backupRepository = mockk(relaxed = true)
        reAuthenticate = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        roleGuard = mockk(relaxed = true)

        backupUseCase = BackupUseCase(backupRepository, reAuthenticate, logAuditEvent, roleGuard)
    }

    @Test
    fun `backup requires re-authentication`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns false

        val result = backupUseCase("user-id", "Password1".toCharArray(), testUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
        coVerify(exactly = 0) { backupRepository.createBackup(any()) }
    }

    @Test
    fun `backup succeeds after re-authentication`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.createBackup(testUri) } returns testMetadata

        val result = backupUseCase("user-id", "Password1".toCharArray(), testUri)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(testMetadata)
    }

    @Test
    fun `backup logs audit events`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.createBackup(testUri) } returns testMetadata

        backupUseCase("user-id", "Password1".toCharArray(), testUri)

        coVerify(atLeast = 2) { logAuditEvent(userId = "user-id", action = any(), target = "backup", targetId = null, detail = any()) }
    }

    @Test
    fun `backup failure returns error result`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.createBackup(testUri) } throws RuntimeException("Disk full")

        val result = backupUseCase("user-id", "Password1".toCharArray(), testUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Disk full")
    }
}
