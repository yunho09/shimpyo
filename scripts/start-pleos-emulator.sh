#!/bin/bash
# Pleos 에뮬레이터 켜기 (PT·시연용)
#  - PULSE_LATENCY_MSEC: 에뮬레이터 소리 끊김(지직거림) 줄이기
#  - 부팅 끝나면 Virtual Console 포트(8088) 연결
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}; export ANDROID_SDK_ROOT=$ANDROID_HOME
export PULSE_LATENCY_MSEC=${PULSE_LATENCY_MSEC:-120}
A=$ANDROID_HOME/platform-tools/adb
# 창 배율: HiDPI 화면에서 2560×1440 창이 화면 밖으로 넘치지 않게 (0.55 ≈ 1400×800)
INI=$HOME/.android/avd/Pleos_Connect_v2.1.avd/emulator-user.ini
SCALE=${PLEOS_WINDOW_SCALE:-0.55}
if [ -f "$INI" ]; then
  if grep -q '^window.scale' "$INI"; then sed -i "s/^window.scale.*/window.scale = $SCALE/" "$INI"; else echo "window.scale = $SCALE" >> "$INI"; fi
fi
# -gpu host: 자동 선택이면 Intel GPU를 의심해 CPU 렌더링(SwiftShader, 약 5fps)으로 떨어짐 → 호스트 GPU 강제 (약 40fps)
# 스냅샷에서 빠르게 켜면 호스트 GPU(gfxstream) 오류로 에뮬레이터가 죽어서 항상 콜드 부팅
$ANDROID_HOME/emulator/emulator -avd Pleos_Connect_v2.1 -no-boot-anim -no-snapshot-load -gpu host "$@" &
$A wait-for-device
until [ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 2; done
$A shell settings put global auto_time_zone 0
$A shell service call alarm 3 s16 Asia/Seoul >/dev/null
$A forward tcp:8088 tcp:8088
echo "준비됨: 브라우저에서 http://127.0.0.1:8088 (Virtual Console)"
wait
