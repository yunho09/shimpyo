package com.daebangeo.shimpyo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 선 아이콘 한 세트 (lucide 경로, ISC License, https://lucide.dev). 24 그리드, 선 1.5.
 * 색은 Icon(tint = ...)로 글자 역할 색을 준다. 아이콘 자체 색은 없음.
 */
object ShimpyoIcons {
    val Lock = line(
        "lock",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2Z",
        "M7 11V7a5 5 0 0 1 10 0v4",
    )
    val Wind = line(
        "wind",
        "M12.8 19.6A2 2 0 1 0 14 16H2",
        "M17.5 8a2.5 2.5 0 1 1 2 4H2",
        "M9.8 4.4A2 2 0 1 1 11 8H2",
    )
    val Moon = line("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z")
    val Thermometer = line("thermometer", "M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z")
    val Check = line("check", "M20 6 9 17l-5-5")
    val Close = line("x", "M18 6 6 18", "M6 6l12 12")
    val Alert = line(
        "circle-alert",
        "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z",
        "M12 8v4",
        "M12 16h0.01",
    )
    val Minus = line("minus", "M5 12h14")
    val Volume = line(
        "volume-2",
        "M11 4.702a0.705 0.705 0 0 0-1.203-0.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 0.997 0.413l3.383 3.384A0.705 0.705 0 0 0 11 19.298z",
        "M16 9a5 5 0 0 1 0 6",
        "M19.364 18.364a9 9 0 0 0 0-12.728",
    )
    val Mic = line(
        "mic",
        "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z",
        "M19 10v2a7 7 0 0 1-14 0v-2",
        "M12 19v3",
    )

    /** 쉼표 시그니처 마크: 점 하나와 꼬리. 채워진 모양, 호박색으로만 쓴다 */
    val Comma: ImageVector = ImageVector.Builder("comma", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M12 3a6 6 0 0 1 6 6c0 5.2-3.2 9.7-8.3 12.2l-1-1.8c2.6-1.5 4.3-3.6 4.9-6.1A6 6 0 1 1 12 3z"),
            fill = SolidColor(Color.White),
        )
    }.build()

    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
