package com.daebangeo.shimpyo.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random

/**
 * 쪽잠용 브라운 노이즈(백색소음보다 낮고 부드러운 소리)를 직접 만들어 재생한다.
 * 음원 파일·라이선스 없이 동작.
 */
class WhiteNoisePlayer(private val log: ApiLog) {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    val isPlaying: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread({ play() }, "shimpyo-noise").apply { start() }
        log.ok("백색소음", "재생")
    }

    fun stop() {
        if (!running) return
        running = false
        thread?.join(500)
        thread = null
        log.ok("백색소음", "정지")
    }

    private fun play() {
        val rate = 22_050
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(4_096)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            log.fail("백색소음 AudioTrack", t)
            running = false
            return
        }

        val buffer = ShortArray(minBuf / 2)
        val random = Random()
        var last = 0.0
        var gain = 0.0 // 처음 몇 초는 서서히 키운다
        try {
            track.play()
            while (running) {
                for (i in buffer.indices) {
                    last = (last + 0.02 * random.nextGaussian()) / 1.02
                    gain = (gain + 0.00002).coerceAtMost(1.0)
                    val sample = (last * 3.5 * gain).coerceIn(-1.0, 1.0) * VOLUME
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }
                track.write(buffer, 0, buffer.size)
            }
        } catch (t: Throwable) {
            log.fail("백색소음 재생", t)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    companion object {
        private const val VOLUME = 0.35
    }
}
