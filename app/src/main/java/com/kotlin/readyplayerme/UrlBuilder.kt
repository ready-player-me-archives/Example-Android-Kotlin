package com.kotlin.readyplayerme


data class UrlConfig(
   // var subdomain: String = "demo",
    var clearCache: Boolean = false,
    var quickStart: Boolean = false,
    var gender: Gender = Gender.NONE,
    var bodyType: BodyType = BodyType.SELECTABLE,
    var loginToken: String = "",
    var clientId: String = "",
    var userId: String = "",
    var userName: String = "",
    var title: String = "meghana",
    var themeColor: String = "B8B8FC",

    var language: Language = Language.DEFAULT
)

class UrlBuilder(
    private val urlConfig: UrlConfig = UrlConfig()
) {
    companion object {
        private const val CLEAR_CACHE_PARAM = "clearCache"
        private const val FRAME_API_PARAM = "iframe" // Parameter name
        private const val FRAME_API_VALUE = "true"  // Parameter value
        private const val LOGIN_TOKEN_PARAM = "token"
        private const val SOURCE_PARAM = "source"
        private const val SOURCE_VALUE = "streamojiavatars"

        // --- New Streamoji Params ---
        private const val USER_ID_PARAM = "userid" // Updated to lowercase 'i'
        private const val USER_NAME_PARAM = "userName"
        private const val CLIENT_ID_PARAM = "clientId"
        private const val TITLE_PARAM = "title"
        private const val THEME_COLOR_PARAM = "themeColor"
    }

    fun buildUrl(): String {
        // Base URL updated to the main creator domain
        val baseUrl = "https://avatars.streamoji.com/createAvatar"
        
        val uriBuilder = android.net.Uri.parse(baseUrl).buildUpon()

        // Handle language prefixing if needed (Note: this might need adjustment if it's part of the path)
        if (urlConfig.language != Language.DEFAULT) {
            // If language is a path segment, we append it. 
            // Assuming the structure is base/lang/createAvatar or similar.
            // If it's just a prefix to createAvatar:
            // uriBuilder.path("/${urlConfig.language.stringValue}/createAvatar")
        }

        // Add Query Parameters
        uriBuilder.appendQueryParameter(FRAME_API_PARAM, FRAME_API_VALUE)
        uriBuilder.appendQueryParameter(SOURCE_PARAM, SOURCE_VALUE)
        uriBuilder.appendQueryParameter("thumbnail", "true")

        // 1. Authentication & Identity
        if (urlConfig.loginToken.isNotEmpty()) {
            uriBuilder.appendQueryParameter(LOGIN_TOKEN_PARAM, urlConfig.loginToken)
        }
        if (urlConfig.clientId.isNotEmpty()) {
            uriBuilder.appendQueryParameter(CLIENT_ID_PARAM, urlConfig.clientId)
        }
        if (urlConfig.userId.isNotEmpty()) {
            uriBuilder.appendQueryParameter(USER_ID_PARAM, urlConfig.userId)
        }
        if (urlConfig.userName.isNotEmpty()) {
            uriBuilder.appendQueryParameter(USER_NAME_PARAM, urlConfig.userName)
        }

        // 2. White Labeling (Title and Color)
        if (urlConfig.title.isNotEmpty()) {
            uriBuilder.appendQueryParameter(TITLE_PARAM, urlConfig.title)
        }
        if (urlConfig.themeColor.isNotEmpty()) {
            uriBuilder.appendQueryParameter(THEME_COLOR_PARAM, urlConfig.themeColor.replace("#", ""))
        }

        // 3. Cache and Settings
        if (urlConfig.clearCache) {
            uriBuilder.appendQueryParameter(CLEAR_CACHE_PARAM, "true")
        }

        appendGender(uriBuilder)
        appendBodyType(uriBuilder)

        return uriBuilder.build().toString()
    }

    private fun appendGender(builder: android.net.Uri.Builder) {
        if (urlConfig.gender != Gender.NONE) {
            builder.appendQueryParameter("gender", urlConfig.gender.stringValue)
        }
    }

    private fun appendBodyType(builder: android.net.Uri.Builder) {
        if (urlConfig.bodyType != BodyType.SELECTABLE) {
            // Streamoji expects "Half" or "Full"
            val type = if (urlConfig.bodyType == BodyType.FULLBODY) "Full" else "Half"
            builder.appendQueryParameter("bodyType", type)
        }
    }
}
enum class Language(val stringValue: String) {
    DEFAULT(""),
    CHINESE("ch"),
    GERMAN("de"),
    ENGLISH_IRELAND("en-IE"),
    ENGLISH("en"),
    SPANISH_MEXICO("es-MX"),
    SPANISH("es"),
    FRENCH("fr"),
    ITALIAN("it"),
    JAPANESE("jp"),
    KOREAN("kr"),
    PORTUGUESE_BRAZIL("pt-BR"),
    PORTUGUESE("pt"),
    TURKISH("tr")
}

enum class BodyType(val stringValue: String) {
    SELECTABLE(""),
    FULLBODY("fullbody"),
    HALFBODY("halfbody")
}

enum class Gender(val stringValue: String) {
    NONE(""),
    MALE("male"),
    FEMALE("female")
}
