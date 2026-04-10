package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuditRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.eaglepoint.storefront.security.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportAuditUseCase(
    private val auditRepository: AuditRepository,
    private val roleGuard: RoleGuard,
    private val reAuthenticateUseCase: ReAuthenticateUseCase,
    private val logAuditEvent: LogAuditEventUseCase,
    private val sessionManager: SessionManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun export(outputFile: File, password: CharArray): Result<File> {
        // Enforce ADMIN role from session
        roleGuard.enforceAuditExportAccess()

        val userId = sessionManager.requireUserId()

        // Step-up re-authentication
        val reAuthPassed = reAuthenticateUseCase(userId, password)
        if (!reAuthPassed) {
            return Result.failure(SecurityException("Re-authentication failed — export denied"))
        }

        logAuditEvent(
            userId = userId,
            action = AuditAction.EXPORT_INITIATED,
            target = "audit_events",
            detail = "Export to ${outputFile.name}"
        )

        return try {
            val events = withContext(dispatcher) {
                auditRepository.getAll().first()
            }

            withContext(dispatcher) {
                writeExportFile(outputFile, events)
            }

            logAuditEvent(
                userId = userId,
                action = AuditAction.EXPORT_COMPLETED,
                target = "audit_events",
                detail = "Exported ${events.size} events to ${outputFile.name}"
            )

            Result.success(outputFile)
        } catch (e: Exception) {
            logAuditEvent(
                userId = userId,
                action = AuditAction.EXPORT_COMPLETED,
                target = "audit_events",
                detail = "Export failed: ${e.message}"
            )
            Result.failure(e)
        }
    }

    private fun writeExportFile(file: File, events: List<AuditEvent>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        file.bufferedWriter().use { writer ->
            writer.write("id,timestamp,actor_type,user_id,action,target,target_id,detail")
            writer.newLine()
            for (event in events) {
                val ts = dateFormat.format(Date(event.timestamp))
                val line = listOf(
                    event.id,
                    ts,
                    event.actorType.name,
                    event.userId ?: "",
                    event.action.name,
                    event.target ?: "",
                    event.targetId ?: "",
                    (event.detail ?: "").replace(",", ";").replace("\n", " ")
                ).joinToString(",")
                writer.write(line)
                writer.newLine()
            }
        }
    }
}
