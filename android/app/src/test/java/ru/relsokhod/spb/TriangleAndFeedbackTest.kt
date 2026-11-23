package ru.relsokhod.spb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.relsokhod.spb.model.FeedbackHelper
import ru.relsokhod.spb.ui.components.TriangleBalanceMath

class TriangleAndFeedbackTest {

    @Test
    fun rejectsCornersOutsideTriangleAndAcceptsInterior() {
        // Center and inside triangle should be accepted
        assertTrue(TriangleBalanceMath.isPointInTriangle(0.50f, 0.56f))
        // Near Tram vertex (top apex)
        assertTrue(TriangleBalanceMath.isPointInTriangle(0.50f, 0.16f))
        // Empty top-left and top-right corners of the square bounding box should be rejected
        assertFalse(TriangleBalanceMath.isPointInTriangle(0.05f, 0.05f))
        assertFalse(TriangleBalanceMath.isPointInTriangle(0.95f, 0.05f))
        assertFalse(TriangleBalanceMath.isPointInTriangle(0.18f, 0.22f))
        assertFalse(TriangleBalanceMath.isPointInTriangle(0.82f, 0.22f))
        assertFalse(TriangleBalanceMath.isPointInTriangle(0.50f, 0.96f))
    }

    @Test
    fun buildsValidMailtoUrlForBugAndSuggestion() {
        val url = FeedbackHelper.buildMailtoUrl(
            kind = FeedbackHelper.Kind.SUGGESTION,
            message = "Добавить ночные рейсы",
            versionName = "2.3.1-native",
            ciBuildNumber = 42,
            cityName = "Санкт-Петербург",
            deviceModel = "Pixel 8"
        )
        assertTrue(url.startsWith("mailto:bober@craft-bober.ru?"))
        assertTrue(url.contains("subject="))
        assertTrue(url.contains("body="))
        assertFalse(url.contains("+"))
    }
}
