package com.daebangeo.shimpyo.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daebangeo.shimpyo.ShimpyoViewModel
import com.daebangeo.shimpyo.core.ApiLogEntry
import com.daebangeo.shimpyo.core.ItemStatus
import com.daebangeo.shimpyo.core.SetupItem
import com.daebangeo.shimpyo.domain.Answer
import com.daebangeo.shimpyo.domain.Gear
import com.daebangeo.shimpyo.domain.NapRecord
import com.daebangeo.shimpyo.domain.NapSettings
import com.daebangeo.shimpyo.domain.NapSnapshot
import com.daebangeo.shimpyo.domain.Phase
import com.daebangeo.shimpyo.domain.Powertrain
import com.daebangeo.shimpyo.domain.Question
import com.daebangeo.shimpyo.domain.RecordStats
import com.daebangeo.shimpyo.domain.RestAdvisor
import com.daebangeo.shimpyo.domain.clockAfter
import com.daebangeo.shimpyo.domain.directionParticle
import com.daebangeo.shimpyo.domain.label
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class Screen { DRIVING, SUGGEST, HEADING, READY, PARKED, NAPPING, WAKING, RECORDS, CHECK }

/** 창 너비가 이보다 넓으면 가로 창(두 칸)으로 본다. Pleos 17" 가로 창 1700dp, 세로 창 840dp */
private val WIDE = 1200.dp

@Composable
fun ShimpyoRoot(vm: ShimpyoViewModel) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val restricted by vm.gearMonitor.restricted.collectAsStateWithLifecycle()
    val cabinTemp by vm.vehicle.cabinTemp.collectAsStateWithLifecycle()
    val setup by vm.vehicle.setup.collectAsStateWithLifecycle()
    val spoken by vm.voice.lastSpoken.collectAsStateWithLifecycle()
    val listening by vm.ear.listening.collectAsStateWithLifecycle()
    val partial by vm.ear.partial.collectAsStateWithLifecycle()
    val demo by vm.demoMode.collectAsStateWithLifecycle()
    val powertrain by vm.powertrain.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val logEntries by vm.log.entries.collectAsStateWithLifecycle()
    val settings = vm.settings

    // 운전 중 판단: 기어 + 차가 알려 주는 주행 제한(CarUxRestrictions) 중 하나라도
    val moving = snap.gear == Gear.DRIVE || snap.gear == Gear.REVERSE || snap.gear == Gear.NEUTRAL || restricted
    val night = snap.phase == Phase.NAPPING || snap.phase == Phase.WAKING
    var tab by remember { mutableIntStateOf(0) }
    // 점검·기록 탭에 둔 채 출발해도, 다시 서면 쪽잠 화면(쪽잠 제안)이 보이게
    LaunchedEffect(moving) { if (moving) tab = 0 }

    val screen = when {
        night -> if (snap.phase == Phase.NAPPING) Screen.NAPPING else Screen.WAKING
        moving -> when (snap.phase) {
            Phase.SUGGESTING -> Screen.SUGGEST
            Phase.HEADING_TO_REST -> Screen.HEADING
            else -> Screen.DRIVING
        }
        tab == 1 -> Screen.RECORDS
        tab == 2 -> Screen.CHECK
        snap.phase == Phase.PARKED -> Screen.PARKED
        else -> Screen.READY
    }

    val voice: @Composable (String) -> Unit = { idle -> VoiceLine(spoken, listening, partial, idle) }

    val body: @Composable () -> Unit = {
        val p = P
        BoxWithConstraints(Modifier.fillMaxSize().background(p.page)) {
            val wide = maxWidth >= WIDE
            Column(Modifier.fillMaxSize()) {
                Header(cabinTemp, demo)
                if (!moving && !night) {
                    TabBar(listOf("쪽잠", "기록", "점검"), tab, { tab = it }, Modifier.padding(horizontal = Space.s40))
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(tween(Motion.ENTER_MS, easing = Motion.Ease)) +
                            slideInVertically(tween(Motion.ENTER_MS, easing = Motion.Ease)) { 24 })
                            .togetherWith(fadeOut(tween(Motion.FEEDBACK_MS, easing = Motion.Ease)))
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.DRIVING -> DrivingScreen(snap, settings, demo, wide, vm, voice)
                        Screen.SUGGEST -> SuggestScreen(snap, settings, wide, vm, voice)
                        Screen.HEADING -> HeadingScreen(snap, demo, wide, vm, voice)
                        Screen.READY -> ReadyScreen(snap, settings, demo, powertrain, wide, vm, voice)
                        Screen.PARKED -> ParkedScreen(snap, settings, powertrain, wide, vm, voice)
                        Screen.NAPPING -> NappingScreen(snap, settings, setup, wide, vm)
                        Screen.WAKING -> WakingScreen(snap, settings, wide, vm, voice)
                        Screen.RECORDS -> RecordsScreen(records, demo, wide, vm)
                        Screen.CHECK -> CheckScreen(logEntries, wide, vm)
                    }
                }
            }
        }
    }

    if (night) NapPalette(body) else body()
}

// ── 머리 줄: 쉼표 마크 + 이름, 오른쪽에 실내 온도 (기어·속도는 Pleos 주행 화면이 이미 보여 줌) ──

@Composable
private fun Header(cabinTemp: Float?, demo: Boolean) {
    val p = P
    Row(
        Modifier.fillMaxWidth().height(104.dp).padding(horizontal = Space.s40),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ShimpyoIcons.Comma, contentDescription = null, tint = p.amber, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(Space.s12))
        Text("쉼표", style = Type.TitleS.copy(color = p.t1))
        Spacer(Modifier.weight(1f))
        if (demo) {
            Text("시연 모드", style = Type.LabelM.copy(color = p.t3))
            Spacer(Modifier.width(Space.s24))
        }
        Text("실내", style = Type.LabelM.copy(color = p.t2))
        Spacer(Modifier.width(Space.s8))
        Text(cabinTemp?.let { "%.1f℃".format(it) } ?: "–", style = Type.LabelM.copy(color = p.t1, fontWeight = FontWeight.Medium))
    }
}

// ── 공통 틀: 위는 내용, 아래는 음성 줄 + 버튼 (버튼은 손에 가까운 아래쪽) ──────────

@Composable
private fun AppPage(
    wide: Boolean,
    main: @Composable ColumnScope.() -> Unit,
    side: (@Composable ColumnScope.() -> Unit)? = null,
    foot: (@Composable ColumnScope.() -> Unit)? = null,
    centerMain: Boolean = false,
) {
    if (wide && side != null) {
        Row(Modifier.fillMaxSize().padding(Space.s40), horizontalArrangement = Arrangement.spacedBy(Space.s40)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = if (centerMain) Arrangement.Center else Arrangement.Top,
                    content = main,
                )
                if (foot != null) Column(verticalArrangement = Arrangement.spacedBy(Space.s16), content = foot)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center, content = side)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(Space.s40),
                verticalArrangement = if (centerMain) Arrangement.Center else Arrangement.Top,
            ) {
                main()
                if (side != null) {
                    Spacer(Modifier.height(Space.s40))
                    side()
                }
            }
            if (foot != null) {
                Column(
                    Modifier.fillMaxWidth().padding(start = Space.s40, end = Space.s40, bottom = Space.s40),
                    verticalArrangement = Arrangement.spacedBy(Space.s16),
                    content = foot,
                )
            }
        }
    }
}

// ── 주행 중 ─────────────────────────────────────────────────────

@Composable
private fun DrivingScreen(
    snap: NapSnapshot, settings: NapSettings, demo: Boolean, wide: Boolean, vm: ShimpyoViewModel,
    voice: @Composable (String) -> Unit,
) {
    val p = P
    val left = (settings.restAfterMs - snap.drivenSinceRestMs).coerceAtLeast(0)
    AppPage(
        wide = wide,
        centerMain = true,
        main = {
            RestGauge(progress = snap.drivenSinceRestMs.toFloat() / settings.restAfterMs) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("마지막 휴식 후", style = Type.BodyM.copy(color = p.t2))
                    Spacer(Modifier.height(Space.s12))
                    FigureText(figureParts(snap.drivenSinceRestMs))
                    Spacer(Modifier.height(Space.s16))
                    Text(
                        if (left > 0) "${label(left)} 뒤 쉴 곳을 알려 드려요" else "곧 쉴 곳을 알려 드려요",
                        style = Type.BodyM.copy(color = p.t2),
                    )
                }
            }
        },
        foot = {
            BigButton("지금 쉴 곳 찾기", onClick = vm::requestRest)
            if (demo) BigButton("시연: 기어 P로", onClick = { vm.simulateGear(Gear.PARK) })
            voice("운전 중에는 음성으로 안내해요")
        },
    )
}

@Composable
private fun SuggestScreen(
    snap: NapSnapshot, settings: NapSettings, wide: Boolean, vm: ShimpyoViewModel,
    voice: @Composable (String) -> Unit,
) {
    val p = P
    val area = snap.suggestedRestArea
    val km = snap.suggestedDistanceKm?.roundToInt()?.coerceAtLeast(1)
    AppPage(
        wide = wide,
        main = {
            Text("${label(snap.drivenSinceRestMs)} 운전했어요", style = Type.BodyM.copy(color = p.t2))
            Spacer(Modifier.height(Space.s12))
            Text(
                if (area == null) "앞쪽 휴게소를\n찾고 있어요" else "${area.name}에서\n${label(settings.napMs)} 쉬어 갈까요",
                style = Type.HeadlineS.copy(color = p.t1),
            )
        },
        side = if (area == null) null else {
            {
                RouteCard(
                    placeName = area.name,
                    placeDetail = listOfNotNull(area.road, km?.let { if (snap.suggestedAhead) "${it}km 앞" else "${it}km 거리" }).joinToString(" · "),
                    nowText = "${label(snap.drivenSinceRestMs)}째 운전 중",
                )
            }
        },
        foot = if (area == null) null else {
            {
                voice("")
                BigButton("쉬어 갈게요", onClick = { vm.answer(Answer.YES) }, kind = ButtonKind.PRIMARY)
                BigButton("괜찮아요", onClick = { vm.answer(Answer.NO) })
            }
        },
    )
}

@Composable
private fun HeadingScreen(
    snap: NapSnapshot, demo: Boolean, wide: Boolean, vm: ShimpyoViewModel,
    voice: @Composable (String) -> Unit,
) {
    val p = P
    val area = snap.suggestedRestArea
    val name = area?.name ?: "휴게소"
    AppPage(
        wide = wide,
        main = {
            Text("경유지로 넣었어요", style = Type.BodyM.copy(color = p.t2))
            Spacer(Modifier.height(Space.s12))
            Text("$name${directionParticle(name)}\n가는 중", style = Type.HeadlineS.copy(color = p.t1))
            Spacer(Modifier.height(Space.s16))
            Text("도착해서 기어를 P에 놓으면 쪽잠 자리를 만들어 드려요", style = Type.BodyM.copy(color = p.t2))
        },
        side = if (area == null) null else {
            {
                val km = snap.suggestedDistanceKm?.roundToInt()?.coerceAtLeast(1)
                RouteCard(area.name, listOfNotNull(area.road, km?.let { "${it}km" }).joinToString(" · "), "가는 중")
            }
        },
        foot = {
            if (demo) BigButton("시연: 기어 P로", onClick = { vm.simulateGear(Gear.PARK) })
            voice("운전 중에는 음성으로 안내해요")
        },
    )
}

// ── 정차 중 ─────────────────────────────────────────────────────

@Composable
private fun ReadyScreen(
    snap: NapSnapshot, settings: NapSettings, demo: Boolean, powertrain: Powertrain, wide: Boolean, vm: ShimpyoViewModel,
    voice: @Composable (String) -> Unit,
) {
    val p = P
    AppPage(
        wide = wide,
        main = {
            Text("출발 전", style = Type.BodyM.copy(color = p.t2))
            Spacer(Modifier.height(Space.s12))
            Text("${label(settings.restAfterMs)} 운전하면\n앞쪽 휴게소를 알려 드려요", style = Type.HeadlineS.copy(color = p.t1))
            if (snap.gear == Gear.UNKNOWN) {
                Spacer(Modifier.height(Space.s16))
                Text("기어 정보를 아직 못 받았어요. 점검 탭에서 가상 기어로 바꿀 수 있어요", style = Type.BodyS.copy(color = p.t3))
            }
        },
        side = {
            Panel {
                Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("시연 모드", style = Type.BodyL.copy(color = p.t1))
                        Spacer(Modifier.height(Space.s4))
                        Text("2시간과 20분을 20초로 줄여요", style = Type.BodyS.copy(color = p.t3))
                    }
                    Switch(
                        checked = demo,
                        onCheckedChange = vm::setDemoMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = p.positive,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = p.t3,
                            uncheckedTrackColor = p.sec,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                HairDivider()
                Spacer(Modifier.height(Space.s24))
                Text("차량", style = Type.BodyL.copy(color = p.t1))
                Spacer(Modifier.height(Space.s16))
                TabBar(
                    tabs = listOf("하이브리드", "가솔린"),
                    selected = if (powertrain == Powertrain.HYBRID) 0 else 1,
                    onSelect = { vm.setPowertrain(if (it == 0) Powertrain.HYBRID else Powertrain.GASOLINE) },
                )
                Spacer(Modifier.height(Space.s16))
                Text(
                    if (powertrain == Powertrain.HYBRID) "스테이 모드로 공조를 켠 채 쉬어요" else "시동을 끄고 최대 20분까지 쉬어요",
                    style = Type.BodyS.copy(color = p.t3),
                )
                Spacer(Modifier.height(Space.s24))
            }
        },
        foot = {
            BigButton("쪽잠 시작", onClick = { vm.requestNap(null) }, kind = ButtonKind.PRIMARY)
            voice("음성 안내가 여기에 자막으로 나와요")
        },
    )
}

@Composable
private fun ParkedScreen(
    snap: NapSnapshot, settings: NapSettings, powertrain: Powertrain, wide: Boolean, vm: ShimpyoViewModel,
    voice: @Composable (String) -> Unit,
) {
    val p = P
    val options = settings.napOptionsMs
    var choice by remember(options) { mutableLongStateOf(if (settings.napMs in options) settings.napMs else options.first()) }
    val asked = snap.pendingQuestion == Question.NAP
    val place = snap.suggestedRestArea?.name ?: "정차 중"

    AppPage(
        wide = wide,
        main = {
            Text("$place · ${label(snap.drivenSinceRestMs)} 운전", style = Type.BodyM.copy(color = p.t2))
            Spacer(Modifier.height(Space.s12))
            Text(if (asked) "여기서 쪽잠 할까요" else "잠깐 눈 붙이고 갈까요", style = Type.HeadlineS.copy(color = p.t1))
            Spacer(Modifier.height(Space.s40))
            DurationTiles(options, choice, { choice = it }, ::numberAndUnit)
        },
        side = {
            Panel {
                Text("잠들기 전에 맞춰 드려요", style = Type.BodyS.copy(color = p.t3), modifier = Modifier.padding(top = Space.s24, bottom = Space.s8))
                InfoRow(ShimpyoIcons.Lock, "문", "모두 잠금", divider = false)
                InfoRow(ShimpyoIcons.Wind, "창문", "10% 열기")
                InfoRow(ShimpyoIcons.Moon, "조명", "끄기")
                InfoRow(
                    ShimpyoIcons.Thermometer, "공조",
                    if (powertrain == Powertrain.HYBRID) "${settings.sleepTempC.roundToInt()}℃ · 바깥 공기" else "쓰지 않음 · 시동 끄기",
                )
            }
        },
        foot = {
            voice("음성 안내가 여기에 자막으로 나와요")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s16)) {
                if (asked) BigButton("괜찮아요", onClick = { vm.answer(Answer.NO) }, modifier = Modifier.weight(0.6f))
                BigButton("${label(choice)} 잘게요", onClick = { vm.requestNap(choice) }, kind = ButtonKind.PRIMARY, modifier = Modifier.weight(1f))
            }
        },
    )
}

// ── 쪽잠 중: 8초 동안 손대지 않으면 화면을 더 어둡게, 창 밝기도 낮춘다 ─────────────

@Composable
private fun NappingScreen(
    snap: NapSnapshot, settings: NapSettings, setup: Map<SetupItem, ItemStatus>, wide: Boolean, vm: ShimpyoViewModel,
) {
    val p = P
    val now = snap.lastTickMs ?: System.currentTimeMillis()
    val start = snap.napStartedAtMs ?: now
    val ends = snap.napEndsAtMs ?: now
    val total = (ends - start).coerceAtLeast(1)
    val canExtend = ends + settings.extendMs - start <= settings.maxNapMs

    var dimmed by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(touches) {
        dimmed = false
        delay(8_000)
        dimmed = true
    }
    ScreenBrightness(if (dimmed) 0.05f else 0.3f)
    val chrome by animateFloatAsState(if (dimmed) 0.25f else 1f, tween(800, easing = Motion.Ease), label = "dim")

    Box(Modifier.fillMaxSize()) {
        AppPage(
            wide = wide,
            centerMain = true,
            main = {
                NapRing(elapsedFraction = (now - start).toFloat() / total) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("쪽잠 중", style = Type.BodyM.copy(color = p.t3), modifier = Modifier.alpha(chrome))
                        Spacer(Modifier.height(Space.s12))
                        Text(mmss(ends - now), style = Type.FigureLight.copy(color = p.t1))
                        Spacer(Modifier.height(Space.s16))
                        Text("${clockAfter(ends, 0)}에 깨워 드려요", style = Type.BodyM.copy(color = p.t3), modifier = Modifier.alpha(chrome))
                    }
                }
            },
            side = {
                Column(Modifier.alpha(chrome)) {
                    NapStatusRow(setup, settings)
                    Spacer(Modifier.height(Space.s32))
                    Text("화면을 누르면 잠깐 밝아져요", style = Type.LabelM.copy(color = p.t3), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            },
            foot = {
                Row(Modifier.alpha(chrome), horizontalArrangement = Arrangement.spacedBy(Space.s16)) {
                    if (canExtend) BigButton("${label(settings.extendMs)} 더", onClick = { touches++; vm.requestExtend() }, modifier = Modifier.weight(1f))
                    BigButton("지금 깨워 줘", onClick = vm::requestWake, modifier = Modifier.weight(1f))
                }
            },
        )
        // 어두울 때 첫 터치는 버튼을 누르지 않고 화면만 밝힌다 (잠결에 잘못 누르지 않게)
        if (dimmed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { touches++ },
            )
        }
    }
}

@Composable
private fun NapStatusRow(setup: Map<SetupItem, ItemStatus>, settings: NapSettings) {
    val p = P
    Row(Modifier.fillMaxWidth()) {
        SetupItem.entries.forEach { item ->
            val status = setup[item]
            val icon = when (item) {
                SetupItem.LOCK -> ShimpyoIcons.Lock
                SetupItem.VENT -> ShimpyoIcons.Wind
                SetupItem.LIGHTS -> ShimpyoIcons.Moon
                SetupItem.CLIMATE -> ShimpyoIcons.Thermometer
            }
            val done = when (item) {
                SetupItem.LOCK -> "잠김"
                SetupItem.VENT -> "창문 10%"
                SetupItem.LIGHTS -> "조명 꺼짐"
                SetupItem.CLIMATE -> "${settings.sleepTempC.roundToInt()}℃"
            }
            val name = when (item) {
                SetupItem.LOCK -> "문"
                SetupItem.VENT -> "창문"
                SetupItem.LIGHTS -> "조명"
                SetupItem.CLIMATE -> "공조"
            }
            when (status) {
                ItemStatus.Done -> StatusChip(icon, done, p.t2)
                is ItemStatus.Failed -> StatusChip(ShimpyoIcons.Close, "$name 확인", p.negative)
                is ItemStatus.Manual -> StatusChip(ShimpyoIcons.Alert, "$name 직접", p.t2)
                is ItemStatus.Skipped -> StatusChip(ShimpyoIcons.Minus, "$name 안 씀", p.t3)
                ItemStatus.Pending, null -> StatusChip(icon, "맞추는 중", p.t3)
            }
        }
    }
    // 직접 해야 하는 항목은 짧게 한 줄로 안내
    setup.values.filterIsInstance<ItemStatus.Manual>().firstOrNull()?.let {
        Spacer(Modifier.height(Space.s24))
        Text(it.guide, style = Type.BodyS.copy(color = p.t2), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** 창 밝기만 낮춘다 (차 전체 화면 밝기는 건드리지 않음). 화면을 나가면 원래대로 */
@Composable
private fun ScreenBrightness(level: Float) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity, level) {
        val window = activity.window
        val attrs = window.attributes
        attrs.screenBrightness = level
        window.attributes = attrs
        onDispose {
            val a = window.attributes
            a.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = a
        }
    }
}

@Composable
private fun WakingScreen(
    snap: NapSnapshot, settings: NapSettings, wide: Boolean, vm: ShimpyoViewModel,
    voice: @Composable (String) -> Unit,
) {
    val p = P
    val start = snap.napStartedAtMs ?: 0
    val woke = snap.wokeAtMs ?: start
    val canExtend = (snap.lastTickMs ?: woke) + settings.extendMs - start <= settings.maxNapMs
    ScreenBrightness(0.6f)
    AppPage(
        wide = wide,
        centerMain = true,
        main = {
            NapRing(elapsedFraction = 1f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("잘 쉬었어요", style = Type.BodyM.copy(color = p.t3))
                    Spacer(Modifier.height(Space.s12))
                    FigureText(figureParts(woke - start), style = Type.FigureLight)
                    Spacer(Modifier.height(Space.s16))
                    Text("불을 천천히 켜는 중", style = Type.BodyM.copy(color = p.t3))
                }
            }
        },
        side = {
            Text("기어를 D로 바꾸면\n창문을 닫고 도착 시각을 알려 드려요", style = Type.BodyL.copy(color = p.t2, fontWeight = FontWeight.Normal), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        foot = {
            voice("")
            if (canExtend) BigButton("${label(settings.extendMs)}만 더", onClick = { vm.answer(Answer.EXTEND) })
        },
    )
}

// ── 기록 ────────────────────────────────────────────────────────

@Composable
private fun RecordsScreen(records: List<NapRecord>, demo: Boolean, wide: Boolean, vm: ShimpyoViewModel) {
    val p = P
    val fmt = remember { SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA) }
    val now = System.currentTimeMillis()
    val (thisWeek, lastWeek) = RecordStats.weekCounts(records, now)
    val median = RecordStats.medianDrivenMs(records)
    val next = RestAdvisor.recommendedRestAfterMs(records)

    val stats: @Composable ColumnScope.() -> Unit = {
        StatRow("다음 휴식 제안", if (demo) "20초 뒤" else "${label(next)} 뒤", accent = true)
        Spacer(Modifier.height(Space.s16))
        StatRow("최근 7일", "${thisWeek}번 쉼")
        Spacer(Modifier.height(Space.s8))
        Text(RecordStats.weekChange(thisWeek, lastWeek), style = Type.BodyS.copy(color = p.t3), modifier = Modifier.padding(start = 36.dp))
        Spacer(Modifier.height(Space.s16))
        StatRow("쉬기 전 운전 (중앙값)", median?.let { label(it) } ?: "–")
    }
    val list: @Composable ColumnScope.() -> Unit = {
        if (records.isEmpty()) {
            Text("아직 기록이 없어요", style = Type.BodyL.copy(color = p.t1))
            Spacer(Modifier.height(Space.s8))
            Text("쪽잠을 자고 다시 출발하면 여기에 쌓여요", style = Type.BodyS.copy(color = p.t3))
        } else {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(records.reversed()) { r ->
                    HairDivider()
                    Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.format(Date(r.startedAtMs)), style = Type.BodyS.copy(color = p.t3), modifier = Modifier.width(240.dp))
                        Text("운전 ${label(r.drivenBeforeMs)} 뒤", style = Type.BodyM.copy(color = p.t1), modifier = Modifier.weight(1f))
                        Text(label(r.napMs), style = Type.BodyM.copy(color = p.t2))
                    }
                }
            }
            Spacer(Modifier.height(Space.s24))
            SmallButton("기록 지우기", onClick = vm::clearRecords)
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(Space.s40), horizontalArrangement = Arrangement.spacedBy(Space.s40)) {
            Column(Modifier.weight(1f), content = stats)
            Column(Modifier.weight(1f), content = list)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(Space.s40)) {
            stats()
            Spacer(Modifier.height(Space.s32))
            list()
        }
    }
}

// ── 점검 (SDK 동작 확인, 정차 중에만) ─────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckScreen(entries: List<ApiLogEntry>, wide: Boolean, vm: ShimpyoViewModel) {
    val p = P
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.KOREA) }
    val buttons: @Composable ColumnScope.() -> Unit = {
        Group("기어 (가상)") {
            SmallButton("P", onClick = { vm.simulateGear(Gear.PARK) })
            SmallButton("D", onClick = { vm.simulateGear(Gear.DRIVE) })
        }
        Group("문") {
            SmallButton("잠금", onClick = { vm.vehicle.lockDoors(true) })
            SmallButton("해제", onClick = { vm.vehicle.lockDoors(false) })
            SmallButton("상태 읽기", onClick = vm.vehicle::readDoorLock)
        }
        Group("창문") {
            SmallButton("틈 열기", onClick = vm.vehicle::openVent)
            SmallButton("닫기", onClick = vm.vehicle::closeVent)
        }
        Group("조명") {
            SmallButton("어둡게", onClick = vm.vehicle::dimLights)
            SmallButton("실내등 켜기", onClick = { vm.vehicle.setCabinLight(true) })
            SmallButton("서서히 밝게", onClick = { vm.vehicle.rampAmbientUp() })
        }
        Group("공조") {
            SmallButton("켜기", onClick = { vm.vehicle.setHvacPower(true) })
            SmallButton("끄기", onClick = { vm.vehicle.setHvacPower(false) })
            SmallButton("22℃", onClick = { vm.vehicle.setTemperature(22f) })
            SmallButton("실내 온도", onClick = vm.vehicle::readCabinTemp)
        }
        Group("내비 · 음성") {
            SmallButton("현재 위치", onClick = vm.navi::requestLocation)
            SmallButton("남은 시간", onClick = vm.navi::requestDestination)
            SmallButton("말하기", onClick = vm::testSpeak)
            SmallButton("듣기", onClick = vm::testListen)
        }
        Group("흐름") {
            SmallButton("쪽잠 시작", onClick = { vm.requestNap(null) })
            SmallButton("깨우기", onClick = vm::requestWake)
            SmallButton("로그 지우기", onClick = vm.log::clear)
        }
    }
    val log: @Composable ColumnScope.() -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(p.card, RoundedCornerShape(Radius.card))
                .padding(Space.s24),
        ) {
            Text("호출 기록", style = Type.BodyS.copy(color = p.t3))
            Spacer(Modifier.height(Space.s8))
            if (entries.isEmpty()) Text("버튼을 누르면 결과가 여기에 쌓여요", style = Type.BodyS.copy(color = p.t3))
            LazyColumn {
                items(entries) { e ->
                    Row(Modifier.padding(vertical = Space.s8)) {
                        Box(Modifier.padding(top = 12.dp).size(10.dp).background(if (e.ok) p.positive else p.negative, CircleShape))
                        Spacer(Modifier.width(Space.s12))
                        Column {
                            Text("${fmt.format(Date(e.timeMs))}  ${e.api}", style = Type.LabelM.copy(color = p.t1))
                            if (e.detail.isNotBlank()) Text(e.detail, style = Type.LabelM.copy(color = if (e.ok) p.t3 else p.negative))
                        }
                    }
                }
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(Space.s40), horizontalArrangement = Arrangement.spacedBy(Space.s40)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s24), content = buttons)
            Column(Modifier.weight(1f).fillMaxHeight(), content = log)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(Space.s40)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.s24),
                content = buttons,
            )
            Spacer(Modifier.height(Space.s24))
            Column(Modifier.weight(1f), content = log)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = Type.BodyS.copy(color = P.t3))
        Spacer(Modifier.height(Space.s8))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.s12),
            verticalArrangement = Arrangement.spacedBy(Space.s12),
        ) { content() }
    }
}

// ── 공통 ────────────────────────────────────────────────────────

/** 1시간 12분 → [("1","시간"),("12","분")], 45초 → [("45","초")] */
private fun figureParts(ms: Long): List<Pair<String, String>> {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    if (totalSec < 60) return listOf("$totalSec" to "초")
    val totalMin = totalSec / 60
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h == 0L) listOf("$m" to "분") else listOf("$h" to "시간", "$m" to "분")
}

/** 쪽잠 길이 타일용: 20분 → ("20","분"), 20초 → ("20","초") */
private fun numberAndUnit(ms: Long): Pair<String, String> {
    val sec = ms / 1000
    return if (sec < 60) "$sec" to "초" else "${sec / 60}" to "분"
}

private fun mmss(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
