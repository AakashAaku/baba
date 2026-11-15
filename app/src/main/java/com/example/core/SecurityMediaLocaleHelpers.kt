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

object AppLocaleHelper {
    fun applyLocale(context: Context, localeCode: String) {
        val normalized = if (localeCode == "ar") "ar" else "en"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            lm?.applicationLocales = LocaleList.forLanguageTags(normalized)
        } else {
            val locale = Locale(normalized)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            (context as? Activity)?.recreate()
        }
    }
}
