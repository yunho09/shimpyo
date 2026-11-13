package com.daebangeo.shimpyo.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daebangeo.shimpyo.R

/*
 * 쉼표 v0.3 디자인 토큰
 *
 * 기준: Pleos Connect UI 디자인 가이드 + unslop by @heydamir (CC BY 4.0)
 * - 창: 기본은 세로 창(17" 기준 840×1184dp), 넓히면 가로 창(1700×1184dp). 둘 다 대응
 * - 글꼴: Pleos 시스템 UI 글꼴 Asta Sans (OFL)
 * - 색: Pleos 그레이스케일(basic_00~900) 그대로, 낮/밤 모드 자동 전환
 * - 글자: 핵심 정보 4mm 이상(≈32sp), 일반 3mm 이상(≈24sp). 한 문장 3줄 이하
 * - 터치: 버튼 높이 104dp, 한 손으로
 *
 * 방향
 * - 성격: 조용하고, 차분하고, 조금 따뜻함
 * - 세계: 밤 고속도로 휴게소 주차장, 나트륨 가로등의 호박색 빛
 * - 순간: 쉼 게이지(운전 중), 쪽잠 링(시간이 갈수록 호박색이 차오름)
 * - 시그니처: 쉼표(,) 표시. 로고와 "쉴 곳" 표시에만
 */

@Immutable
data class Palette(
    val page: Color,
    val deep: Color,
    val card: Color,
    val raised: Color,
    val line: Color,
    val track: Color,
    val t1: Color,
    val t2: Color,
    val t3: Color,
    val pri: Color,
    val onPri: Color,
    val sec: Color,
    val amber: Color,
    val amberText: Color,
    val positive: Color,
    val negative: Color,
    val dark: Boolean,
)

object Palettes {
    /** 밤: Pleos dark basic_50 배경, basic_600 주 버튼 */
    val Night = Palette(
        page = Color(0xFF1D1E21),
        deep = Color(0xFF131417),
        card = Color(0xFF2A2B30),
        raised = Color(0xFF313236),
        line = Color(0x14FFFFFF),
        track = Color(0x1AFFFFFF),
        t1 = Color(0xFFF7F8FA),
        t2 = Color(0xFFC7C9CE),
        t3 = Color(0xFFA4A8AE),
        pri = Color(0xFFE0E1E6),
        onPri = Color(0xFF131417),
        sec = Color(0x1AFFFFFF),
        amber = Color(0xFFF0A43A),
        amberText = Color(0xFFF0A43A),
        positive = Color(0xFF3DD68C),
        negative = Color(0xFFFF6B6B),
        dark = true,
    )

    /** 낮: Pleos light basic_50 배경, basic_700 주 버튼 */
    val Day = Palette(
        page = Color(0xFFF7F8FA),
        deep = Color(0xFFEDEEF2),
        card = Color(0xFFFFFFFF),
        raised = Color(0xFFFFFFFF),
        line = Color(0x14131417),
        track = Color(0x1A131417),
        t1 = Color(0xFF131417),
        t2 = Color(0xFF44464E),
        t3 = Color(0xFF686A72),
        pri = Color(0xFF313236),
        onPri = Color(0xFFFFFFFF),
        sec = Color(0x0F131417),
        amber = Color(0xFFE8962A),
        amberText = Color(0xFF9A5B00), // 흰 바탕 글자용 (대비 4.5:1 이상)
        positive = Color(0xFF16A34A),
        negative = Color(0xFFDC2626),
        dark = false,
    )

    /** 쪽잠 중: 낮이어도 항상 아주 어둡게. 글자는 대비 4.5:1은 지킨다 */
    val Nap = Night.copy(
        page = Color(0xFF0B0C0D),
        card = Color(0xFF141517),
        raised = Color(0xFF1A1B1E),
        line = Color(0x0FFFFFFF),
        track = Color(0x14FFFFFF),
        t1 = Color(0xFFC7C9CE),
        t2 = Color(0xFFA4A8AE),
        t3 = Color(0xFF8B8E95),
        sec = Color(0x0FFFFFFF),
    )
}

val LocalPalette = staticCompositionLocalOf { Palettes.Night }

/** 현재 팔레트 */
val P: Palette
    @Composable get() = LocalPalette.current

object Space {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp
    val s56 = 56.dp
    val s64 = 64.dp
}

object Radius {
    val tab = 14.dp
    val tabGroup = 20.dp // 안쪽 14 + 패딩 6
    val control = 16.dp
    val button = 24.dp
    val card = 28.dp
}

object Motion {
    /** 화면 전체에서 하나만 쓰는 곡선 */
    val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    const val FEEDBACK_MS = 120 // Pleos: 입력 후 0.25초 안에 반응
    const val ENTER_MS = 360
}

val AstaSans = FontFamily(
    Font(R.font.asta_sans_regular, FontWeight.Normal),
    Font(R.font.asta_sans_medium, FontWeight.Medium),
    Font(R.font.asta_sans_bold, FontWeight.Bold),
)

/** Pleos 글자 체계 (Headline / Title / Body / Label). 색은 쓰는 곳에서 팔레트로 준다 */
object Type {
    private val base = TextStyle(fontFamily = AstaSans, fontFeatureSettings = "tnum")

    val Figure = base.copy(fontSize = 120.sp, lineHeight = 120.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em)
    val FigureLight = Figure.copy(fontWeight = FontWeight.Normal)
    val FigureUnit = base.copy(fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium)
    val Tile = base.copy(fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)

    val HeadlineS = base.copy(fontSize = 48.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
    val TitleM = base.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
    val TitleS = base.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val Stat = base.copy(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
    val Button = base.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val ButtonQuiet = Button.copy(fontWeight = FontWeight.Medium)

    val BodyL = base.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium)
    val BodyM = base.copy(fontSize = 28.sp, lineHeight = 36.sp)
    val BodyS = base.copy(fontSize = 26.sp, lineHeight = 34.sp)
    val LabelM = base.copy(fontSize = 24.sp, lineHeight = 28.sp)
}

/** 낮/밤은 차가 정한다 (전조등·조도 → AAOS 야간 모드) */
@Composable
fun ShimpyoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) Palettes.Night else Palettes.Day
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalTextStyle provides Type.BodyM.copy(color = palette.t1),
            content = content,
        )
    }
}

/** 쪽잠·깨우기 화면은 낮이어도 어두운 팔레트 */
@Composable
fun NapPalette(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides Palettes.Nap,
        LocalTextStyle provides Type.BodyM.copy(color = Palettes.Nap.t1),
        content = content,
    )
}
