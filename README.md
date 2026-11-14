# 쉼표 — 차에서 쉬는 모든 시간

현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작. 팀 대방어.

> 졸릴 때 쉴 곳을 제안하고, 정차하면 잠금·창문·조명·공조를 맞춰 쪽잠 자리를 만들어 주는 차 안 휴식 앱

---

## 1. 지금 되는 것 (MVP)

| 단계 | 하는 일 | 쓰는 SDK |
|---|---|---|
| 주행 중 | 마지막 휴식 뒤 운전 시간 누적 (기어 P 구간 제외), 쉼 게이지로 표시. **"지금 쉴 곳 찾기"로 언제든 직접 요청** | AAOS `CarPropertyManager` (GEAR_SELECTION), `CarUxRestrictionsManager` |
| 휴식 제안 | 기준 시간 넘으면 **진행 방향 앞쪽**(±60°) 휴게소를 찾아 "6km 앞 ○○휴게소" 음성 제안. 앞쪽에 없으면 가장 가까운 곳 | NaviHelper `getCurrentLocationInfo` (heading) + TTS |
| 대답 | "응 / 아니 / 잘게 / 5분만 더" 음성 또는 화면 버튼. 못 알아들으면 한 번 더 묻고, 그래도 안 되면 버튼 | STT |
| 경유지 | 휴게소를 경유지로 추가 (목적지 없으면 목적지로) | NaviHelper `addWaypoint` / `requestRoute` |
| 쪽잠 길이 | 10 · 20 · 30분 고르기 (가솔린은 10 · 20분), "5분 더"를 해도 최대 40분 (가솔린 20분) | – |
| 쪽잠 세팅 | 문 잠금 · 창문 10% 틈 · 실내등/무드등 끄기 · 공조 22℃ + 외기 유입 · 백색소음 | Vehicle SDK (Door, Window, Light, HVAC) |
| 쪽잠 중 | 실내 온도 감시 → 너무 덥거나 추우면 공조 조절 (1분에 한 번까지), **운전석 문을 열면 쪽잠 종료** | `registerHvacTemperatureCurrent`, `registerDoorPosition` |
| 깨우기 | **끝나기 1분 전부터** 무드등을 서서히 밝힘 → 끝나면 실내등 켜기, 공조 20℃ | Light, HVAC |
| 출발 브리핑 | 기어 D → 창문 닫기 → "지금 출발하면 부산까지 17:40 도착 예정이에요" | NaviHelper `getDestinationInfo` (remainTime) |
| 기록 | 최근 7일 횟수(지난 7일과 비교), 쉬기 전 운전 중앙값, 다음 제안 시점 (최근 5회 중앙값, 1~2시간) | 기기 안에만 저장 |

파워트레인별 동작:
- **하이브리드**: 공조로 수면 온도 유지 (스테이 모드 안내)
- **가솔린**: 공조는 건드리지 않고 "시동 끄고 쉬세요" 안내 (공회전 일산화탄소 위험), 쪽잠은 최대 20분

화면 원칙: **주행 중에는 큰 글씨 한 화면 + 음성만**, 탭·설정은 정차 중에만 보인다.

---

## 1-1. 디자인 (v0.3: 차 화면 기준)

시안: `design/쉼표_차량화면시안.html` (브라우저로 열기), 이미지: `design/쉼표_차량화면_6종.png`

**Pleos 화면 규격에 맞춤** (Pleos Connect UI 디자인 가이드 · Display Layout)
- Pleos 화면 = 상태 바 + 운전석 쪽 주행 화면(고정) + 앱 창 1~2개 + 하단 GNB
- 앱은 기본이 **세로 창**(17" 기준 840×1184dp, 12.9" 628×855dp), 사용자가 넓히면 가로 창(1700×1184dp)
- 그래서 세로 창을 먼저 설계하고, 창이 1200dp보다 넓으면 두 칸으로 나눈다
- 기어·속도는 Pleos 주행 화면이 이미 보여 주므로 앱 머리에서 뺐다

**차에서 보기 좋게**
- 글꼴: Pleos 시스템 UI 글꼴 **Asta Sans** (Pleos 기본 UI와 같은 글꼴)
- 글자: Pleos 글자 체계 (제목 48 · 본문 26~30 · 라벨 24). 핵심 정보 4mm 이상, 일반 3mm 이상, 한 문장 3줄 이하
- 색: Pleos 그레이스케일 그대로, **낮/밤 모드 자동 전환** (차의 야간 모드를 따름)
- 버튼: 높이 104dp, 창 아래쪽(손에 가까운 쪽)에 몰아서. 누르면 클릭음 + 진동 (Pleos: 터치 피드백)
- 주행 중: 큰 쉼 게이지 하나 + 음성. 버튼은 "지금 쉴 곳 찾기" 하나만
- 주행 판단: 기어 + **CarUxRestrictions** (Pleos 가이드: 주행 제한 상태를 확인해 화면을 줄일 것)
- 쪽잠 중: 낮이어도 아주 어두운 화면. 8초 동안 손대지 않으면 더 어두워지고 창 밝기도 낮춤. 어두울 때 첫 터치는 화면만 밝힘 (잠결에 잘못 누르지 않게)

**방향** (unslop by @heydamir 기준)
- 성격: 조용하고, 차분하고, 조금 따뜻함 / 세계: 밤 고속도로 휴게소, 나트륨 가로등의 호박색
- 순간: 쉼 게이지 (연료 게이지처럼 마지막 휴식부터 차오름, 꼭대기가 "쉴 곳"), 쪽잠 링 (호박색이 해 뜨듯 차오름)
- 시그니처: 쉼표(,) 표시. 로고와 "쉴 곳"에만
- 호박색은 쉼표 표시 · 쪽잠 링 · 다음 제안 숫자 · 듣는 중 점에만

---

## 2. 실행 준비

1. **Android Studio** (JDK 17)
2. **Pleos Connect Emulator v2.1 이상** 설치 → AVD `Pleos_Connect_v2.1` 생성
   - 문서: Pleos Playground → Setting up the development environment → Pleos Connect Emulator Setup
3. 이 폴더를 Android Studio에서 **Open** → Gradle Sync
   - SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 받는다 (settings.gradle.kts에 등록됨)

**확인한 실행 환경** (2026-11-15): Pleos Connect v2.1.0 에뮬레이터 (Android 14 / API 34, x86_64, 화면 2560×1440 · 160dpi, 기기 프로필 `Pleos_Connect_v2_0`, RAM 4GB), JDK 17, Linux.
Android Studio 없이 명령줄로도 된다: SDK 명령줄 도구로 `platform-tools`·`emulator`·`platforms;android-35`를 받고, Pleos 이미지(`crp-sys-img.xml`에 적힌 zip)를 `system-images/android-34/pleos_car_emulator/x86_64`에 풀어서 `avdmanager create avd -d Pleos_Connect_v2_0`.

버전 기준선 (Pleos Vehicle SDK 샘플 앱과 동일):

| 항목 | 버전 |
|---|---|
| Gradle wrapper | 8.10.2 |
| Android Gradle Plugin | 8.8.2 |
| Kotlin | 2.0.0 |
| compileSdk / targetSdk | 35 |
| minSdk | 28 (공모전 요구사항) |
| Vehicle / NaviHelper / TTS / STT | 2.3.2 / 3.0.5 / 3.3.1 / 3.3.1 |

## 3. 실행

```bash
./gradlew installDebug          # 에뮬레이터 켜 둔 상태에서
adb forward tcp:8088 tcp:8088   # Virtual Console 연결
```

브라우저에서 `http://127.0.0.1:8088/` → **Virtual Console**
- 기어: Emulation Controls → **Motion** → Gear
- 공조·창문·문 상태 확인: **Climate**, **Convenience**

---

## 4. PT 시연 순서 (시연 모드 켠 상태, 약 2분)

시연 모드: 2시간 → **20초**, 20분 쪽잠 → **20초**

| # | 하는 것 | 기대 결과 |
|---|---|---|
| 1 | Virtual Console에서 기어 **D** | 주행 화면(쉼 게이지), 운전 시간 증가. 기다리지 않으려면 [지금 쉴 곳 찾기] |
| 2 | 20초 기다리기 | 음성: "20초 운전하셨어요. 6km 앞 안성휴게소에서 20초 쉬어갈까요?" |
| 3 | "응" 말하기 또는 [쉬어 갈게요] | 음성: "안성휴게소를 경유지로 넣었어요…" + 내비 경유지 추가 |
| 4 | 기어 **P** | 음성: "수고하셨어요. 여기서 20초 쪽잠 할까요?", 10·20·30초 고르기 |
| 5 | "잘게" 또는 [20초 잘게요] | 화면이 아주 어두워지고 쪽잠 링 + 카운트다운, 상태(문·창문·조명·공조), 백색소음. 8초 뒤 더 어두워짐 |
| 6 | Virtual Console Climate·Convenience에서 상태 변화 확인 | 문 잠김, 창문 약간 열림, 공조 22℃ |
| 7 | 끝나기 6초 전 | 무드등이 서서히 밝아짐 |
| 8 | 끝 | 음성: "일어날 시간이에요…", 실내등 켜짐 (Virtual Console에서 운전석 문을 열어도 바로 끝남) |
| 9 | 기어 **D** | 창문 닫힘, 음성: "지금 출발하면 ○○:○○ 도착 예정이에요" |
| 10 | 기어 P → **기록** 탭 | 방금 쉰 기록 1줄, 최근 7일 횟수 |

소리가 안 나와도 하단 **자막 바**에 안내 문장이 그대로 나온다.

---

## 5. 에뮬레이터에서 먼저 확인할 것 (점검 탭)

정차 상태에서 **점검** 탭 → 버튼 누르고 오른쪽 로그에서 ✅/❌ 확인.
결과는 이 표에 채워서 팀에 공유. **2026-11-15 Pleos Connect v2.1.0 에뮬레이터(API 34, 2560×1440)에서 확인한 결과.**

| 확인할 것 | 버튼 | 성공 기준 | 결과 |
|---|---|---|---|
| Vehicle SDK 초기화 | (앱 켜면 자동) | `Vehicle.initialize` ✅ | ✅ |
| 기어 구독 | Virtual Console에서 기어 변경 | 주행 화면(쉼 게이지) ↔ 정차 화면으로 바뀜 | ✅ P↔D 바로 바뀜 |
| 주행 제한 | Virtual Console에서 속도 올리기 | `CarUxRestrictionsManager.registerListener` ✅, 주행 화면으로 바뀜 | 구독 ✅ (정차 중 `제한=false`). 속도를 올려 보는 시험은 아직 |
| 문 잠금 | 문 → 잠금 | `Door.setDoorLock` ✅ ×4 + Virtual Console에서 잠김 | ✅ 4개 문 **묶음 한 번**(영역 0x55). 문마다 따로는 지원 안 함 → `checkDoorLockCapabilities`로 영역을 받아 쓰게 고침. Virtual Console Door Lock = On |
| 창문 범위 | 창문 → 틈 열기 | `checkWindowPositionCapabilities` 범위 표시 (예: `0..100`) | ✅ 창문 4개 모두 `0..10` → 1로 설정 = Virtual Console 10%, D에서 0% |
| 실내등 | 조명 → 어둡게 / 실내등 켜기 | `Light.setCabinLightSwitch` ✅ | ✅ OFF/ON |
| 무드등 범위 | 조명 → 서서히 밝게 | 범위 표시 + 13단계 ✅ | ✅ `0..100`, 0→100 13단계 |
| 공조 | 공조 → 켜기, 22℃ | Virtual Console Climate에서 22℃ | ✅ 전원·내기 순환은 좌석 5개 **묶음 영역(0x75)**만 지원 → `checkHvacPower/RecirculationCapabilities`로 받게 고침. 온도는 운전석 영역으로 22℃ ✅ |
| 실내 온도 | 공조 → 실내 온도 | `getHvacTemperatureCurrent` 값 (Virtual Console 값과 같은지) | ✅ 19.1℃ (에뮬레이터 고정값) |
| 현재 위치 | 내비 · 음성 → 현재 위치 | `Navi.onCurrentLocationInfo` 위도·경도 | ✅ 37.4127, 127.0947 (성남 수정구). 정차 중이라 heading=0.0만 확인 |
| 남은 시간 단위 | 목적지 설정 후 내비 · 음성 → 남은 시간 | `remainTime` 값이 **초인지 분인지** | ✅ **초**: 내비 화면 15분 ↔ `remainTime=900` |
| TTS | 내비 · 음성 → 말하기 | 소리 남 (`TTS.initialize` ON_DEVICE ✅) | ✅ 초기화·`speak` 에러 없음. 실제 소리는 사람이 들어 봐야 함 |
| STT | 내비 · 음성 → 듣기 후 "응" | `STT.onUpdated(final)` 응 | 초기화 ✅, 녹음+온디바이스 인식 시작 ✅. 말소리 인식은 마이크 입력이 필요해 아직 (에뮬레이터를 `-allow-host-audio`로 켜고 시험) |

> 공모전 공지: 콘솔에 "준비 중"으로 떠도 개발 문서에 나온 방법으로 에뮬레이터에서 개발·시험 가능.
> 사무국은 특정 API 동작 문의에는 답하지 않으니 이 표로 직접 확인.

---

## 6. 빌드 에러가 나면

**2026-11-15 실제 빌드 통과**: 리눅스 PC, JDK 17 + Gradle 8.10.2 + AGP 8.8.2로 `./gradlew assembleDebug` 성공, `./gradlew testDebugUnitTest` 22개 모두 통과. Compose 화면(ui/)도 그대로 컴파일됐다.

첫 빌드에서 난 에러와 고친 것 (Pleos AAR 실제 클래스를 `javap`로 확인):
- `HvacTemperatureListener`, `DoorPositionListener`는 `VehicleListener`를 상속해서 **`onFailed(Exception)`도 구현해야 한다** → 두 리스너에 추가 (`core/PleosVehicle.kt`)

에뮬레이터(Pleos Connect v2.1.0)에서 돌려 보고 고친 것:
- **문 잠금**: 문마다 따로 잠그면 `area ID: 0x1 not supported for DOOR_LOCK`. 에뮬레이터는 4개 문 묶음 영역(0x55)만 받음 → `checkDoorLockCapabilities`로 지원 영역을 받아 잠그고, 못 받으면 4개 문 묶음으로 시도
- **공조 전원·내기 순환**: 운전석(0x1)으로 부르면 `not supported for HVAC_POWER_ON`. 좌석 5개 묶음(0x75)만 받음 → `checkHvacPowerCapabilities` / `checkHvacRecirculationCapabilities`로 영역을 받음 (온도 설정·실내 온도는 운전석 그대로 됨)
- **경유지**: 목적지가 없어도 `DestinationInfo`가 빈 값(이름 "", 남은 거리 0)으로 와서 `addWaypoint`를 불렀음 → 남은 거리나 이름이 있을 때만 경유지, 아니면 `requestRoute`로 휴게소를 목적지로 (`core/NaviGateway.kt`)
- **화면**: 점검 탭에 둔 채 출발하면 다시 섰을 때도 점검 탭이 보였음 → 출발하면 쪽잠 탭으로 되돌림 (`ui/Screens.kt`)

처음에는 Pleos 개발 문서 화면만 보고 시그니처를 맞췄다 (클라우드 환경이라 Maven 접근 불가).

문서에서 **직접 확인한** 것:
- Door `setDoorLock`, `getDoorLockState`, `registerDoorPosition(area, listener)`, `DoorPositionListener.onPositionUpdated(Int?)` / Window `setWindowPosition`, `checkWindowPositionCapabilities`
- Light `setCabinLightSwitch`, `setAmbientLightBrightness`, `checkAmbientLightBrightnessCapabilities`
- HVAC `setHvacPower`, `setHvacRecirculation`, `setHvacTemperature`, `getHvacTemperatureCurrent`, `register/unregisterHvacTemperatureCurrent`, `HvacTemperatureListener`
- `LightSwitch`, `TemperatureUnit`, `SeatValue`, `DoorValue`, `WindowValue`, `WindowArea(vararg)`, `AreaBounds`
- NaviHelper `initialize(onConnected, onFailed)`, `addListener`, `getCurrentLocationInfo`, `getDestinationInfo`, `addWaypoint`, `requestRoute`, `RequestWaypointInfo`, `RouteInfo`, `CurrentLocationInfo`, `DestinationInfo`
- TTS·STT 초기화·리스너·speak/request 예제 코드

**문서 패턴으로 짐작했다가 빌드로 확인된** 것 (Vehicle 2.3.2 / NaviHelper 3.0.5 / TTS·STT 3.3.1 AAR 기준):
- `DoorArea(DoorValue...)`, `SeatArea(SeatValue...)` 생성자 ✅
- TTS `EventListener` 패키지 `ai.pleos.playground.tts.listener.EventListener` ✅
- STT `ResultListener` 패키지 `ai.pleos.playground.stt.listener.ResultListener` ✅
- STT `setLocale(java.util.Locale)` ✅
- `Door.unregisterDoorPosition(DoorArea, DoorPositionListener)` ✅
- `DestinationInfo.remainTime`은 `Int` ✅, `CurrentLocationInfo.heading`은 `Double` ✅

**아직 짐작인** 것 (에뮬레이터에서 확인할 것):
- ~~`DestinationInfo.remainTime` 단위 = 초~~ → 에뮬레이터에서 **초로 확인** (5번 표)
- `CurrentLocationInfo.heading` = 진행 방향(북 0°, 시계 방향). 0.0은 "방향 모름"으로 처리

### Claude Code로 고칠 때 프롬프트

```
이 프로젝트(쉼표, Pleos Connect AAOS 앱)를 Gradle Sync하고 ./gradlew assembleDebug로 빌드해줘.
에러가 나면 Pleos SDK AAR(~/.gradle/caches 안 ai.pleos.playground)의 실제 클래스 시그니처를 확인해서
core/ 폴더의 래퍼 코드만 고쳐. domain/ 로직은 바꾸지 마. ./gradlew testDebugUnitTest도 통과시켜.
키·시크릿은 코드에 넣지 말고, 고친 내용은 README의 "짐작한 것" 목록에 반영해줘.
```

---

## 7. 폴더 구조

```
app/src/main/java/com/daebangeo/shimpyo/
├── domain/              # 안드로이드 의존 없음, 단위 테스트 대상
│   ├── Model.kt         # 상태·이벤트·효과 정의, 설정(실제/시연)
│   ├── NapMachine.kt    # 쪽잠 흐름 상태 머신
│   ├── Text.kt          # 시간 표기, 음성 대답 해석, 휴식 타이밍 추천
│   └── RestAreas.kt     # 휴게소 목록(시연용 근사 좌표) + 가장 가까운 곳
├── core/                # SDK·안드로이드 래퍼
│   ├── PleosVehicle.kt  # 문·창문·조명·공조 + 쪽잠 체크리스트
│   ├── NaviGateway.kt   # 위치·목적지·경유지
│   ├── Voice.kt         # TTS(ON_DEVICE, 실패 시 안드로이드 TTS) / STT(ON_DEVICE)
│   ├── GearMonitor.kt   # AAOS 기어 구독 + 가상 기어
│   ├── WhiteNoisePlayer.kt
│   ├── LocalStore.kt    # 설정·기록 (기기 안 저장)
│   └── ApiLog.kt        # SDK 호출 로그
├── ShimpyoViewModel.kt  # 상태 머신 ↔ SDK 연결
└── ui/
    ├── Theme.kt         # 낮/밤/쪽잠 팔레트, 간격·반지름·글자·움직임 토큰 (Pleos 가이드 값)
    ├── Icons.kt         # 선 아이콘 + 쉼표 마크
    ├── Components.kt    # 큰 버튼(클릭음·진동), 탭, 쪽잠 길이 타일, 행, 쉼 게이지, 쪽잠 링, 경로 카드, 음성 줄
    └── Screens.kt       # 세로/가로 창 대응. 주행 / 제안 / 출발 전 / 정차 / 쪽잠 / 깨우기 / 기록 / 점검
app/src/main/res/font/   # Asta Sans 400·500·700 (Pleos 시스템 글꼴, OFL, 라이선스: assets/licenses)
app/src/test/.../NapMachineTest.kt
design/                  # HTML 시안, 전후 비교 이미지
```

## 저작권 표시

- 디자인 기준: unslop by Damir Sokolovsky (@heydamir), CC BY 4.0
- 글꼴: Asta Sans (42dot), SIL Open Font License 1.1 (`app/src/main/assets/licenses/AstaSans-OFL.txt`)
- 아이콘 경로: lucide, ISC License

## 8. 알고 있는 한계 · 다음 할 일

- 휴게소 좌표는 **시연용 근사값** 10곳 → 한국도로공사 휴게소 공공데이터 + 진행 방향 필터로 교체
- 위치를 못 받으면 시연 모드에서만 안성 부근 좌표를 쓴다
- TTS·STT는 ON_DEVICE 모드 (앱 등록 불필요). HYBRID로 바꾸려면 Client ID/Secret을 `local.properties`에만 넣고 앱 등록 코드 추가 (커밋 금지)
- 앱이 화면에 떠 있을 때만 동작 (백그라운드 서비스는 다음 단계)
- 차크닉 모드, 풀 차박은 로드맵

## 9. 제출용 APK

공모전 규칙: 저장소 `apk/` 폴더에 `팀명_앱이름_release.apk`

```bash
./gradlew assembleRelease   # 서명 설정 필요. 급하면 assembleDebug 결과를 이름만 바꿔서
mkdir -p apk && cp app/build/outputs/apk/release/app-release.apk apk/대방어_쉼표_release.apk
```
