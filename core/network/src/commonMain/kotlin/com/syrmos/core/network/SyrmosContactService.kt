package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/// Posts user feedback / bug reports to /api/contact on the Pi.
///
/// Returns the assigned message id on 2xx, or null on any failure so the
/// UI can show a generic "couldn't send" banner. The iOS app uses its own
/// native URLSession-based equivalent in iosApp/iosApp/Views/Settings/
/// ContactDeveloperView.swift to take advantage of PhotosPicker for the
/// attachment; this KMP implementation submits text only.
class SyrmosContactService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun submit(
        platform: String,
        message: String,
        category: String,
        subject: String?,
        contactEmail: String?,
        appVersion: String?,
        locale: String?,
        userAgent: String?,
    ): Result? = runCatching {
        val response = httpClient.post("$BASE_URL/api/contact") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("platform", platform)
                        append("message", message)
                        append("category", category)
                        if (subject != null) append("subject", subject)
                        if (contactEmail != null) append("contact_email", contactEmail)
                        if (appVersion != null) append("app_version", appVersion)
                        if (locale != null) append("locale", locale)
                        if (userAgent != null) append("user_agent", userAgent)
                    }
                )
            )
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        json.decodeFromString<Result>(response.bodyAsText())
    }.getOrNull()

    @Serializable
    data class Result(val id: Int, val ok: Boolean)

    companion object {
        private const val BASE_URL = "https://api-syrmos.peterdsp.dev"
    }
}
