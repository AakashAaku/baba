package com.example.core

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.util.UUID

private const val VOICE_TAG = "BabaVoice"

class VoiceRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentOutputFile: File? = null
    private var recordingStartMs: Long = 0L

    fun startRecording(): Result<File> = runCatching {
        stopPlayback()
        val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
        val file = File(dir, "baba_voice_${UUID.randomUUID().toString().take(8)}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(24_000)
            setAudioSamplingRate(22_050)
            setMaxDuration(45_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        currentOutputFile = file
        recordingStartMs = System.currentTimeMillis()
        file
    }.onFailure { e ->
        Log.e(VOICE_TAG, "Unable to start audio recording", e)
        releaseRecorder()
    }

    fun stopRecording(): Pair<String, Long>? {
        val file = currentOutputFile ?: return null
        val elapsedSec = ((System.currentTimeMillis() - recordingStartMs) / 1000L).coerceAtLeast(1L)
        return try {
            recorder?.stop()
            releaseRecorder()
            if (file.exists() && file.length() > 0L) {
                file.absolutePath to elapsedSec
            } else null
        } catch (e: Exception) {
            Log.e(VOICE_TAG, "Error stopping recording", e)
            releaseRecorder()
            null
        }
    }

    fun playFile(path: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        stopPlayback()
        val file = File(path)
        if (!file.exists()) {
            onError("Recorded audio file is only stored on the sender's device.")
            return
        }
        try {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(VOICE_TAG, "MediaPlayer error $what/$extra")
                    stopPlayback()
                    onError("Unable to play voice message.")
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(VOICE_TAG, "Playback failed", e)
            stopPlayback()
            onError(e.localizedMessage ?: "Playback failed.")
        }
    }

    fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {}
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
    }

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
    }

    fun release() {
        releaseRecorder()
        stopPlayback()
    }
}
