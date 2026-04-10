package com.eaglepoint.storefront.notification

import com.eaglepoint.storefront.domain.model.NotificationEventType
import com.eaglepoint.storefront.domain.model.NotificationTemplate
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TemplateRendererTest {

    private lateinit var renderer: TemplateRenderer

    @BeforeEach
    fun setUp() {
        renderer = TemplateRenderer()
    }

    @Test
    fun `renders single variable`() {
        val result = renderer.render("Hello {name}!", mapOf("name" to "John"))
        assertThat(result).isEqualTo("Hello John!")
    }

    @Test
    fun `renders multiple variables`() {
        val result = renderer.render(
            "Order {orderId} confirmed. Total: {total}.",
            mapOf("orderId" to "ORD-123", "total" to "$99.99")
        )
        assertThat(result).isEqualTo("Order ORD-123 confirmed. Total: $99.99.")
    }

    @Test
    fun `removes unresolved placeholders`() {
        val result = renderer.render(
            "Hello {name}, your {unknown} is ready.",
            mapOf("name" to "John")
        )
        assertThat(result).isEqualTo("Hello John, your  is ready.")
    }

    @Test
    fun `handles empty variables map`() {
        val result = renderer.render("No variables here.", emptyMap())
        assertThat(result).isEqualTo("No variables here.")
    }

    @Test
    fun `handles template with no placeholders`() {
        val result = renderer.render("Static content", mapOf("unused" to "value"))
        assertThat(result).isEqualTo("Static content")
    }

    @Test
    fun `renders same variable multiple times`() {
        val result = renderer.render("{name} said hello to {name}", mapOf("name" to "Alice"))
        assertThat(result).isEqualTo("Alice said hello to Alice")
    }

    @Test
    fun `renderTemplate produces title and body`() {
        val template = NotificationTemplate(
            id = "tpl-1",
            name = "Order Confirmation",
            eventType = NotificationEventType.ORDER_CONFIRMATION,
            titleTemplate = "Order {orderId} Confirmed",
            bodyTemplate = "Thank you! Your order {orderId} totaling {total} has been placed."
        )

        val rendered = renderer.renderTemplate(template, mapOf(
            "orderId" to "ORD-456",
            "total" to "$149.99"
        ))

        assertThat(rendered.title).isEqualTo("Order ORD-456 Confirmed")
        assertThat(rendered.body).isEqualTo("Thank you! Your order ORD-456 totaling $149.99 has been placed.")
        assertThat(rendered.templateId).isEqualTo("tpl-1")
        assertThat(rendered.eventType).isEqualTo(NotificationEventType.ORDER_CONFIRMATION)
    }

    @Test
    fun `handles article title variable`() {
        val result = renderer.render(
            "New article: {articleTitle}",
            mapOf("articleTitle" to "NBA Finals Game 7 Preview")
        )
        assertThat(result).isEqualTo("New article: NBA Finals Game 7 Preview")
    }

    @Test
    fun `handles special characters in values`() {
        val result = renderer.render(
            "Total: {total}",
            mapOf("total" to "$1,234.56")
        )
        assertThat(result).isEqualTo("Total: $1,234.56")
    }
}
