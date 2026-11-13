package com.daebangeo.shimpyo.ui

import android.view.SoundEffectConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── 누르기: 즉시 눌림 표시 + 클릭음 + 진동 (Pleos: 터치에 소리·진동 피드백) ─────

@Composable
private fun rememberFeedbackClick(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    return remember(onClick, view, haptic) {
        {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    }
}

@Composable
private fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable (pressed: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Motion.FEEDBACK_MS, easing = Motion.Ease), label = "press")
    val click = rememberFeedbackClick(onClick)
    Box(
        modifier
            .scale(scale)
            .clickable(interactionSource = source, indication = null, onClick = click),
    ) { content(pressed) }
}

// ── 버튼: 높이 104, 반지름 24. 주 버튼은 화면당 하나 ─────────────────

enum class ButtonKind { PRIMARY, SECONDARY }

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
) {
    val p = P
    Pressable(onClick, modifier.fillMaxWidth().height(104.dp)) { pressed ->
        val bg = when (kind) {
            ButtonKind.PRIMARY -> p.pri
            ButtonKind.SECONDARY -> p.sec
        }
        val fg = if (kind == ButtonKind.PRIMARY) p.onPri else p.t1
        Box(
            Modifier
                .fillMaxSize()
                .background(if (pressed) bg.copy(alpha = bg.alpha * 0.8f) else bg, RoundedCornerShape(Radius.button)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = (if (kind == ButtonKind.PRIMARY) Type.Button else Type.ButtonQuiet).copy(color = fg),
                maxLines = 1,
            )
        }
    }
}

/** 점검 탭용 작은 버튼 (정차 중에만 보임) */
@Composable
fun SmallButton(text: String, onClick: () -> Unit) {
    val p = P
    Pressable(onClick, Modifier.height(72.dp)) { pressed ->
        Box(
            Modifier
                .fillMaxHeight()
                .background(if (pressed) p.raised else p.sec, RoundedCornerShape(Radius.control))
                .padding(horizontal = Space.s24),
            contentAlignment = Alignment.Center,
        ) { Text(text, style = Type.LabelM.copy(color = p.t1), maxLines = 1) }
    }
}

// ── 탭 (정차 중에만) ──────────────────────────────────────────────

@Composable
fun TabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = P
    Row(
        modifier
            .fillMaxWidth()
            .background(p.sec, RoundedCornerShape(Radius.tabGroup))
            .padding(6.dp),
    ) {
        tabs.forEachIndexed { i, title ->
            val on = i == selected
            val bg by animateColorAsState(if (on) p.raised else Color.Transparent, tween(Motion.FEEDBACK_MS, easing = Motion.Ease), label = "tab")
            Pressable({ onSelect(i) }, Modifier.weight(1f).height(72.dp)) {
                Box(Modifier.fillMaxSize().background(bg, RoundedCornerShape(Radius.tab)), contentAlignment = Alignment.Center) {
                    Text(title, style = (if (on) Type.BodyS.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) else Type.BodyS).copy(color = if (on) p.t1 else p.t2))
                }
            }
        }
    }
}

// ── 쪽잠 길이 타일: 큰 숫자 + 작은 단위 ───────────────────────────────

@Composable
fun DurationTiles(options: List<Long>, selected: Long, onSelect: (Long) -> Unit, unitOf: (Long) -> Pair<String, String>) {
    val p = P
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s16)) {
        options.forEach { ms ->
            val on = ms == selected
            val (num, unit) = unitOf(ms)
            Pressable({ onSelect(ms) }, Modifier.weight(1f).height(144.dp)) {
                val shape = RoundedCornerShape(Radius.button)
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(if (on) p.raised else p.sec, shape)
                        .then(if (on) Modifier.border(3.dp, p.t1, shape) else Modifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(num, style = Type.Tile.copy(color = if (on) p.t1 else p.t2))
                    Spacer(Modifier.width(Space.s4))
                    Text(unit, style = Type.BodyM.copy(color = if (on) p.t1 else p.t2), modifier = Modifier.padding(top = 20.dp))
                }
            }
        }
    }
}

// ── 카드 / 행 ────────────────────────────────────────────────────

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(P.card, RoundedCornerShape(Radius.card))
            .padding(horizontal = Space.s32, vertical = Space.s8),
        content = content,
    )
}

/** 아이콘 36 + 라벨 + 값. 아이콘에는 항상 글자 라벨을 붙인다 (Pleos 아이콘 규칙) */
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color = P.t3,
    valueColor: Color = P.t2,
    divider: Boolean = true,
) {
    val p = P
    Column {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.height(88.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(Space.s20))
            Text(label, style = Type.BodyL.copy(color = p.t1, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
            Spacer(Modifier.width(Space.s24))
            Spacer(Modifier.weight(1f))
            Text(value, style = Type.BodyM.copy(color = valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun StatRow(label: String, figure: String, accent: Boolean = false) {
    val p = P
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.card, RoundedCornerShape(Radius.card))
            .padding(horizontal = 36.dp, vertical = Space.s32),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.BodyM.copy(color = p.t2), modifier = Modifier.weight(1f))
        Text(figure, style = Type.Stat.copy(color = if (accent) p.amberText else p.t1), maxLines = 1)
    }
}

@Composable
fun HairDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))

@Composable
fun LiveDot() = Box(Modifier.size(12.dp).background(P.amber, CircleShape))

// ── 순간 1: 쉼 게이지 (운전 중) ──────────────────────────────────────

/**
 * 마지막 휴식부터 쌓이는 운전 시간. 꼭대기(12시)가 "쉴 곳"이고 쉼표 표시가 있다.
 * 시계 방향으로 차오르며 한 바퀴를 채우면 쉴 때. 차의 연료 게이지처럼 한눈에.
 */
@Composable
fun RestGauge(progress: Float, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val p = P
    val f by animateFloatAsState(progress.coerceIn(0f, 1f), tween(Motion.ENTER_MS, easing = Motion.Ease), label = "gauge")
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side: Dp = minOf(maxWidth, 600.dp)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2 + 16.dp.toPx()
                val arc = Size(size.width - inset * 2, size.height - inset * 2)
                val tl = Offset(inset, inset)
                drawArc(p.track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
                if (f > 0f) drawArc(p.t1, -90f, 360f * f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Icon(
                ShimpyoIcons.Comma, contentDescription = "쉴 곳", tint = p.amber,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 4.dp).size(48.dp),
            )
            center()
        }
    }
}

// ── 순간 2: 쪽잠 링 ──────────────────────────────────────────────

/** 시간이 갈수록 호박색 호가 차오른다 (해 뜨듯). 가운데는 남은 시간. */
@Composable
fun NapRing(elapsedFraction: Float, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val p = P
    val f by animateFloatAsState(elapsedFraction.coerceIn(0f, 1f), tween(900, easing = Motion.Ease), label = "ring")
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side: Dp = minOf(maxWidth, 600.dp)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2 + 16.dp.toPx()
                val arc = Size(size.width - inset * 2, size.height - inset * 2)
                val tl = Offset(inset, inset)
                drawArc(p.track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
                if (f > 0f) drawArc(p.amber.copy(alpha = 0.9f), -90f, 360f * f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            center()
        }
    }
}

// ── 세로 경로 띠: 지금(아래) → 쉴 곳(위, 쉼표) ─────────────────────────

@Composable
fun RouteCard(placeName: String, placeDetail: String, nowText: String) {
    val p = P
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.card, RoundedCornerShape(Radius.card))
            .padding(Space.s40),
    ) {
        Box(Modifier.width(40.dp).height(232.dp)) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 40.dp, bottom = 16.dp).width(4.dp).fillMaxHeight().background(p.t2, RoundedCornerShape(2.dp)))
            Icon(ShimpyoIcons.Comma, contentDescription = null, tint = p.amber, modifier = Modifier.align(Alignment.TopCenter).size(40.dp))
            Box(Modifier.align(Alignment.BottomCenter).size(28.dp).background(p.t1, CircleShape))
        }
        Spacer(Modifier.width(Space.s32))
        Column(Modifier.height(232.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("쉴 곳", style = Type.BodyS.copy(color = p.t3))
                Spacer(Modifier.height(Space.s4))
                Text(placeName, style = Type.TitleM.copy(color = p.t1), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.s4))
                Text(placeDetail, style = Type.BodyL.copy(color = p.t2, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
            }
            Column {
                Text("지금", style = Type.BodyS.copy(color = p.t3))
                Spacer(Modifier.height(Space.s4))
                Text(nowText, style = Type.BodyL.copy(color = p.t1))
            }
        }
    }
}

// ── 쪽잠 상태 (아이콘 + 짧은 라벨) ─────────────────────────────────────

@Composable
fun RowScope.StatusChip(icon: ImageVector, text: String, tint: Color) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(Space.s12))
        Text(text, style = Type.LabelM.copy(color = tint), maxLines = 1)
    }
}

// ── 음성 줄: 듣는 중이면 호박색 점 + 마이크, 아니면 마지막 안내 자막 ─────────

@Composable
fun VoiceLine(spoken: String, listening: Boolean, partial: String, idle: String) {
    val p = P
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (listening) {
            LiveDot()
            Spacer(Modifier.width(Space.s12))
            Icon(ShimpyoIcons.Mic, null, tint = p.t2, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(Space.s12))
            Text(
                partial.ifBlank { "“응” 또는 “괜찮아”라고 말해도 돼요" },
                style = Type.BodyS.copy(color = if (partial.isBlank()) p.t2 else p.t1),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Icon(ShimpyoIcons.Volume, null, tint = p.t3, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(Space.s12))
            Text(spoken.ifBlank { idle }, style = Type.BodyS.copy(color = p.t2), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 큰 숫자 + 작은 단위 ("1시간 12분") */
@Composable
fun FigureText(parts: List<Pair<String, String>>, style: TextStyle = Type.Figure) {
    val p = P
    Row(verticalAlignment = Alignment.Bottom) {
        parts.forEach { (num, unit) ->
            Text(num, style = style.copy(color = p.t1))
            Text(unit, style = Type.FigureUnit.copy(color = p.t2), modifier = Modifier.padding(start = 2.dp, end = 10.dp, bottom = 14.dp))
        }
    }
}
