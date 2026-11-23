package ru.relsokhod.spb.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FeedbackHelper {
    const val FEEDBACK_EMAIL = "bober@craft-bober.ru"

    enum class Kind(
        val id: String,
        val label: String,
        val subjectTag: String
    ) {
        BUG("bug", "Ошибка", "[Рельсоход Android · Ошибка]"),
        SUGGESTION("suggestion", "Предложение", "[Рельсоход Android · Предложение]")
    }

    private fun encodeMailtoParam(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    fun buildMailtoUrl(
        kind: Kind,
        message: String,
        versionName: String,
        ciBuildNumber: Int,
        cityName: String,
        deviceModel: String
    ): String {
        val trimmed = message.trim()
        val subjectSummary = if (trimmed.isNotEmpty()) {
            val singleLine = trimmed.replace(Regex("""\s+"""), " ")
            if (singleLine.length > 48) "${singleLine.take(45)}..." else singleLine
        } else {
            if (kind == Kind.BUG) "Сообщение об ошибке" else "Идея по улучшению"
        }
        val subject = "${kind.subjectTag} $subjectSummary"

        val buildInfo = if (ciBuildNumber > 0) "$versionName (#$ciBuildNumber)" else versionName
        val bodyLines = buildList {
            if (trimmed.isNotEmpty()) {
                add(trimmed)
            } else {
                add(
                    if (kind == Kind.BUG) {
                        "Опишите, пожалуйста, что произошло и на каком экране:"
                    } else {
                        "Опишите вашу идею или предложение:"
                    }
                )
            }
            add("")
            add("---")
            add("Платформа: Android")
            add("Версия: $buildInfo")
            add("Город: $cityName")
            if (deviceModel.isNotBlank()) {
                add("Устройство: $deviceModel")
            }
        }
        val body = bodyLines.joinToString("\n")
        return "mailto:$FEEDBACK_EMAIL?subject=${encodeMailtoParam(subject)}&body=${encodeMailtoParam(body)}"
    }
}
