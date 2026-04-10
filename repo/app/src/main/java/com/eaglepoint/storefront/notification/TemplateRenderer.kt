package com.eaglepoint.storefront.notification

import com.eaglepoint.storefront.domain.model.NotificationTemplate

class TemplateRenderer {

    fun render(template: String, variables: Map<String, String>): String {
        var result = template
        for ((key, value) in variables) {
            result = result.replace("{$key}", value)
        }
        // Remove any unresolved placeholders
        result = PLACEHOLDER_PATTERN.replace(result, "")
        return result.trim()
    }

    fun renderTemplate(
        template: NotificationTemplate,
        variables: Map<String, String>
    ): RenderedNotification {
        return RenderedNotification(
            title = render(template.titleTemplate, variables),
            body = render(template.bodyTemplate, variables),
            templateId = template.id,
            eventType = template.eventType
        )
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Regex("\\{[a-zA-Z0-9_.]+}")
    }
}

data class RenderedNotification(
    val title: String,
    val body: String,
    val templateId: String,
    val eventType: com.eaglepoint.storefront.domain.model.NotificationEventType
)
