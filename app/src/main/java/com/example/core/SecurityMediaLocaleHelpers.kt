package com.example.core

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object SecurityTokenHelper {
    fun sha256Hex(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

sealed interface MediaValidationResult {
    data class Valid(
        val mimeType: String,
        val byteSize: Int,
        val payloadBase64: String,
        val scanStatus: String = "validated"
    ) : MediaValidationResult

    data class Invalid(val reason: String) : MediaValidationResult
}

object MediaValidator {
    const val MAX_MEDIA_BYTES = 420_000
    private val ALLOWED_MIME_TYPES = setOf("audio/mp4", "audio/m4a", "image/jpeg", "image/png", "image/webp")

    fun validateBytes(mimeType: String, bytes: ByteArray): MediaValidationResult {
        val normalizedMime = mimeType.lowercase().trim()
        if (normalizedMime !in ALLOWED_MIME_TYPES) {
            return MediaValidationResult.Invalid("Unsupported file type: $mimeType. Allowed: audio/mp4, image/jpeg, png, webp.")
        }
        if (bytes.isEmpty()) {
            return MediaValidationResult.Invalid("Media file is empty.")
        }
        if (bytes.size > MAX_MEDIA_BYTES) {
            return MediaValidationResult.Invalid("File exceeds safe limit (${bytes.size / 1024} KB > ${MAX_MEDIA_BYTES / 1024} KB).")
        }
        if (containsDisallowedExecutableSignature(bytes)) {
            return MediaValidationResult.Invalid("Security scan blocked active or executable file content.")
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return MediaValidationResult.Valid(
            mimeType = normalizedMime,
            byteSize = bytes.size,
            payloadBase64 = encoded
        )
    }

    internal fun containsDisallowedExecutableSignature(bytes: ByteArray): Boolean {
        if (bytes.size >= 2 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'Z'.code.toByte()) return true
        if (bytes.size >= 4 && bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte() &&
            bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) return true
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()
        ) return true
        val headText = bytes.take(256).toByteArray().toString(Charsets.UTF_8).lowercase()
        return headText.contains("<script") || headText.contains("<svg") || headText.startsWith("#!")
    }

    fun readAudioFileValidated(file: File): MediaValidationResult {
        if (!file.exists()) return MediaValidationResult.Invalid("Recorded audio file was not found.")
        return validateBytes("audio/mp4", file.readBytes())
    }

    fun compressImageUriValidated(context: Context, uri: Uri): MediaValidationResult {
        return try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return MediaValidationResult.Invalid("Unable to open selected image.")
            if (containsDisallowedExecutableSignature(rawBytes)) {
                return MediaValidationResult.Invalid("Security scan blocked unsafe file signature.")
            }
            val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return MediaValidationResult.Invalid("Unsupported or corrupted image file.")

            val maxDim = 720
            val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val targetW = (original.width * scale).toInt().coerceAtLeast(1)
            val targetH = (original.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(original, targetW, targetH, true) else original

            var quality = 82
            var jpegBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                jpegBytes = out.toByteArray()
                quality -= 12
            } while (jpegBytes.size > MAX_MEDIA_BYTES && quality >= 30)

            if (scaled !== original) scaled.recycle()
            original.recycle()

            validateBytes("image/jpeg", jpegBytes)
        } catch (e: Exception) {
            MediaValidationResult.Invalid(e.localizedMessage ?: "Failed to process selected photo.")
        }
    }

    fun decodeBase64ToCacheFile(context: Context, mediaId: String, mimeType: String, base64: String): File {
        val ext = when {
            mimeType.contains("audio") -> "m4a"
            mimeType.contains("png") -> "png"
            else -> "jpg"
        }
        val dir = File(context.cacheDir, "media_cache").apply { mkdirs() }
        val file = File(dir, "$mediaId.$ext")
        if (!file.exists() || file.length() == 0L) {
            file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
        }
        return file
    }

    fun decodeBase64Bitmap(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val regionLabel: String,
    val isRtl: Boolean
) {
    EN("en", "English", "English", "Default", false),
    NE("ne", "नेपाली", "Nepali", "Nepal", false),
    HI("hi", "हिन्दी", "Hindi", "India", false),
    BN("bn", "বাংলা", "Bengali", "Bangladesh", false),
    UR("ur", "اردو", "Urdu", "Pakistan", true),
    AR("ar", "العربية", "Arabic", "Arabic", true);

    companion object {
        fun fromCode(code: String?): AppLanguage {
            val clean = code?.trim()?.lowercase()?.substringBefore("-")?.substringBefore("_")
            return entries.firstOrNull { it.code == clean } ?: EN
        }
    }
}

object LocalePreferenceStore {
    private const val PREFS_NAME = "baba_prefs"
    private const val KEY_LOCALE = "selected_locale"
    private const val KEY_PRELOGIN_PENDING = "prelogin_locale_pending"
    private const val KEY_WORK_TZ = "father_work_timezone"
    private const val KEY_HOME_TZ = "family_home_timezone"

    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedLocale(context: Context): String {
        return AppLanguage.fromCode(prefs(context).getString(KEY_LOCALE, "en")).code
    }

    fun saveLocale(context: Context, localeCode: String, markPreLoginPending: Boolean = false) {
        val normalized = AppLanguage.fromCode(localeCode).code
        val editor = prefs(context).edit()
        editor.putString(KEY_LOCALE, normalized)
        if (markPreLoginPending) {
            editor.putBoolean(KEY_PRELOGIN_PENDING, true)
        }
        editor.commit()
    }

    fun hasPendingPreLoginSelection(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRELOGIN_PENDING, false)

    fun clearPendingPreLoginSelection(context: Context) {
        prefs(context)
            .edit()
            .putBoolean(KEY_PRELOGIN_PENDING, false)
            .commit()
    }

    fun getWorkTimezone(context: Context): String =
        prefs(context).getString(KEY_WORK_TZ, "Asia/Dubai") ?: "Asia/Dubai"

    fun saveWorkTimezone(context: Context, zoneId: String) {
        prefs(context).edit().putString(KEY_WORK_TZ, zoneId).commit()
    }

    fun getHomeTimezone(context: Context, defaultZone: String): String =
        prefs(context).getString(KEY_HOME_TZ, defaultZone) ?: defaultZone

    fun saveHomeTimezone(context: Context, zoneId: String) {
        prefs(context).edit().putString(KEY_HOME_TZ, zoneId).commit()
    }
}

private class LocalizedActivityContextWrapper(
    base: Context,
    private val localized: Context
) : android.content.ContextWrapper(base) {
    override fun getResources(): android.content.res.Resources = localized.resources
    override fun getAssets(): android.content.res.AssetManager = localized.assets
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
        return localized.createConfigurationContext(overrideConfiguration)
    }
}

object AppLocaleHelper {
    private fun unwrapLocalizedWrapper(context: Context): Context {
        var current = context
        while (current is LocalizedActivityContextWrapper) {
            current = current.baseContext
        }
        return current
    }

    fun applyLocale(context: Context, localeCode: String, markPreLoginPending: Boolean = false): String {
        val targetContext = unwrapLocalizedWrapper(context)
        val lang = AppLanguage.fromCode(localeCode)
        LocalePreferenceStore.saveLocale(targetContext, lang.code, markPreLoginPending = markPreLoginPending)
        val locale = Locale.Builder().setLanguage(lang.code).build()
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val lm = (targetContext.applicationContext ?: targetContext).getSystemService(LocaleManager::class.java)
                lm?.applicationLocales = LocaleList.forLanguageTags(lang.code)
            }
        }
        val config = Configuration(targetContext.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        targetContext.resources.updateConfiguration(config, targetContext.resources.displayMetrics)
        return lang.code
    }

    fun localizedContext(base: Context, localeCode: String): Context {
        val unwrapped = unwrapLocalizedWrapper(base)
        val lang = AppLanguage.fromCode(localeCode)
        val locale = Locale.Builder().setLanguage(lang.code).build()
        val config = Configuration(unwrapped.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return unwrapped.createConfigurationContext(config)
    }

    fun localizedActivityContext(base: Context, localeCode: String): Context {
        val unwrapped = unwrapLocalizedWrapper(base)
        val localized = localizedContext(unwrapped, localeCode)
        return LocalizedActivityContextWrapper(unwrapped, localized)
    }
}

