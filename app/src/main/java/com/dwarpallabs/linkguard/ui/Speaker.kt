package com.dwarpallabs.linkguard.ui

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Reads the warning aloud (roadmap B4). An interface so screens can be tested with a fake. */
interface WarningSpeaker {
    val speaking: Boolean
    fun speak(text: String)
    fun stop()
}

private class TtsSpeaker(private val tts: TextToSpeech) : WarningSpeaker {
    override var speaking by mutableStateOf(false)
        private set

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking = true }
            override fun onDone(utteranceId: String?) { speaking = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speaking = false }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { speaking = false }
        })
    }

    override fun speak(text: String) {
        speaking = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "linkguard-warning")
    }

    override fun stop() {
        tts.stop()
        speaking = false
    }
}

/**
 * The phone's own text-to-speech, in the app's language, or null when it can't help: TalkBack is on
 * (it already reads the screen, so LinkGuard must not talk over it), the phone has no voice for the
 * language, or the only voices need the internet (the warning is spoken on the phone, never sent).
 * Started only once a warning is on screen, so a safe link never pays for it.
 */
@Composable
fun rememberWarningSpeaker(): WarningSpeaker? {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val talkBack = remember { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    var speaker by remember { mutableStateOf<WarningSpeaker?>(null) }
    DisposableEffect(locale, talkBack) {
        if (talkBack) return@DisposableEffect onDispose { }
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val voice = runCatching { engine.voices }.getOrNull().orEmpty()
                .filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
                .minByOrNull { if (it.locale.country == locale.country) 0 else 1 }
            if (voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS) speaker = TtsSpeaker(engine)

        }
        onDispose {
            speaker = null
            tts.stop()
            tts.shutdown()
        }
    }
    return speaker
}
