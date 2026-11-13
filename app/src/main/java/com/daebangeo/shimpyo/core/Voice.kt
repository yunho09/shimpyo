package com.daebangeo.shimpyo.core

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * 말하기: Pleos TextToSpeech SDK (문서 기준 v3.3.1).
 * ON_DEVICE 모드는 앱 등록(Client ID/Secret) 없이 동작한다고 문서에 나와 있어서 기본으로 쓴다.
 * Pleos TTS 초기화가 실패하면 안드로이드 표준 TTS로 대신 말한다.
 * 화면에는 항상 자막(lastSpoken)을 띄운다 → 소리가 안 나와도 시연 가능.
 */
class VoiceGuide(private val context: Context, private val log: ApiLog) {

    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    private var pleosReady = false
    private var fallback: android.speech.tts.TextToSpeech? = null
    private var fallbackReady = false
    private val pending = ArrayDeque<String>()

    private val _lastSpoken = MutableStateFlow("")
    val lastSpoken: StateFlow<String> = _lastSpoken.asStateFlow()

    /** 한 문장을 다 말했을 때 (질문 뒤에 바로 듣기 시작하려고) */
    var onDone: (() -> Unit)? = null

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            onDone?.invoke()
        }
        override fun onStop() = Unit
        override fun onError(errorType: TtsErrorType) {
            log.failMsg("TTS.onError", "$errorType")
        }
    }

    fun start() {
        log.guard("TTS.initialize") {
            tts.initialize(object : TtsInitListener {
                override fun onSuccess() {
                    pleosReady = true
                    log.ok("TTS.initialize", "ON_DEVICE")
                    log.guard("TTS.addEventListener") { tts.addEventListener(events) }
                    log.guard("TTS.setLocale") { tts.setLocale(Locale.KOREA) }
                    flush()
                }

                override fun onFailure(errorType: TtsErrorType) {
                    log.failMsg("TTS.initialize", "$errorType → 안드로이드 기본 TTS로 대체")
                    startFallback()
                }
            })
        }
    }

    fun speak(text: String) {
        _lastSpoken.value = text
        when {
            pleosReady -> log.guard("TTS.speak") { tts.speak(text); log.ok("TTS.speak", text) }
            fallbackReady -> speakFallback(text)
            else -> pending.addLast(text)
        }
    }

    fun stopSpeaking() {
        if (pleosReady) log.guard("TTS.stop") { tts.stop() }
        fallback?.stop()
    }

    fun stop() {
        log.guard("TTS.release") {
            tts.removeEventListener(events)
            tts.release()
        }
        fallback?.shutdown()
    }

    private fun flush() {
        while (pending.isNotEmpty()) speak(pending.removeFirst())
    }

    private fun startFallback() {
        if (fallback != null) return
        fallback = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
            fallbackReady = status == android.speech.tts.TextToSpeech.SUCCESS
            if (fallbackReady) {
                fallback?.language = Locale.KOREA
                fallback?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { onDone?.invoke() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = Unit
                })
                log.ok("Android TTS", "대체 TTS 준비")
                flush()
            } else {
                log.failMsg("Android TTS", "초기화 실패 status=$status (자막만 표시)")
            }
        }
    }

    private fun speakFallback(text: String) {
        fallback?.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "shimpyo-${System.nanoTime()}")
    }
}

/**
 * 듣기: Pleos SpeechToText SDK (문서 기준 v3.3.1), ON_DEVICE 모드.
 * 차량 마이크 음성을 외부 음성 인식 서비스로 보내는 건 공모전 규정상 안 됨 → 이 SDK만 쓴다.
 * 못 알아들어도 화면 버튼으로 같은 대답을 할 수 있다.
 */
class VoiceListener(context: Context, private val log: ApiLog) {

    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    private var ready = false

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    /** 최종 인식 결과 */
    var onFinal: ((String) -> Unit)? = null

    private val results = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() {
            _listening.value = true
        }
        override fun onUpdated(stt: String, completed: Boolean) {
            _partial.value = stt
            if (completed) {
                log.ok("STT.onUpdated(final)", stt)
                onFinal?.invoke(stt)
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            _listening.value = false
        }
        override fun onError(errorType: SttErrorType) {
            _listening.value = false
            log.failMsg("STT.onError", "$errorType")
        }
    }

    fun start() {
        log.guard("STT.initialize") {
            stt.initialize(object : SttInitListener {
                override fun onSuccess() {
                    ready = true
                    log.ok("STT.initialize", "ON_DEVICE")
                    log.guard("STT.addListener") { stt.addListener(results) }
                    log.guard("STT.setLocale") { stt.setLocale(Locale.KOREA) }
                }

                override fun onFailure(errorType: SttErrorType) {
                    log.failMsg("STT.initialize", "$errorType (버튼으로 대답)")
                }
            })
        }
    }

    fun listen() {
        if (!ready) return log.failMsg("STT.request", "초기화 안 됨 (버튼으로 대답)")
        _partial.value = ""
        log.guard("STT.request") { stt.request(); log.ok("STT.request") }
    }

    fun cancel() {
        if (ready) log.guard("STT.stop") { stt.stop() }
        _listening.value = false
    }

    fun stop() {
        log.guard("STT.release") {
            stt.removeListener(results)
            stt.release()
        }
    }
}
