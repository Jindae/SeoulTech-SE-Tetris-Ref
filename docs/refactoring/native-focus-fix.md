# MENU native 입력 검사 후속 진단

작성: 2026-11-01 · 상태: **native 전체 재검사 통과, 이전 실패 원인 미확정**

## 확인한 현상

- 기존 native 검사에서 Classic MENU의 누름 상태가 확인되지 않았다.
- 버튼은 활성·표시 상태였고 중앙 hit target과 MouseListener도 정상이었지만, 누름 이벤트가 관측되지 않았다.
- 클릭 검사를 왼쪽 버튼의 누름·뗌 모두 확인하도록 강화하자, `P`로 일시정지한 직후의 overlay 클릭에서도 실패했다.
- Robot 동기화 설정만 바꿔도 실패했다. 현재는 기존 타이밍 설정으로 되돌렸다.

## 비교 결과와 현재 상태

Gamepanel에 `enableInputMethods(false)`를 적용한 뒤 실제 입력 전체 검사를 다시 실행했다. Start·Settings·키 변경은 통과했지만, Classic의 `P` 일시정지 직후 overlay 클릭에서 같은 실패가 재현됐다. 이번 변경만으로는 문제가 해결되지 않았다.

효과를 확인하지 못한 제품 변경과 그 정책을 고정하는 InputContext 검사는 `fed6d8a`에서 되돌렸다. MENU 취소·정상 클릭 회귀 검사와 native 누름·뗌 확인은 유지한다. OS 설정은 바꾸지 않았다.

OpenJDK의 [macOS mouseDown 처리](https://github.com/openjdk/jdk21u/blob/ba94eace682733f66e623c089bcafcf4a1b8e39c/src/java.desktop/macosx/native/libawt_lwawt/awt/AWTView.m#L165)에는 입력기가 누름을 먼저 소비할 수 있는 경로가 있다. 그러나 이번 실패의 원인이라고 확정할 증거는 없다. 재발 시 원본 누름·뗌 이벤트, 수신 대상·좌표, 포인터, 창의 활성·enabled 상태를 함께 기록하도록 진단을 보강했다.

상세 진단을 포함한 `desktopFocusSmoke --args=game` 재실행은 **Classic·Item 모두 통과**했다. 기존 `P` 키와 제품 코드를 유지한 채 게임 영역 클릭, pause/resume, MENU 누름·드래그 취소, 취소 후 키 입력, Escape 복귀를 확인했다. 실패가 재현되지 않아 실패 이벤트 기록은 생성되지 않았다. 이 결과는 해당 실행의 통과 증거이며, 이전 실패의 원인을 해결했다는 증거는 아니다.

같은 진단 시간에 먼저 시도한 Java 직접 실행은 게임 검사 이전의 창 활성·포커스 조건에서 실패했다. 이 실패를 MENU 문제로 해석하지 않는다. 준비한 F8 비교 검사는 실행하지 않았다.

이후 별도로 실행한 **전체 native 검사도 통과**했다. 시작·설정·키 변경, Classic·Item 게임, 이름 입력·수정·저장, 점수판 텍스트·필터·스크롤을 확인했다. 이 작업에서 추가한 제품 변경은 남아 있지 않으며, 현재 확인된 결과는 기존 실패의 재현 없이 전체 시나리오가 통과했다는 것이다.

## 검증 결과

| 검사 | 결과 |
|---|---|
| JUnit: Classic·Item MENU 취소·정상 클릭·입력 유지 | 통과 |
| 제품 변경 원복 후 JDK 21 `check build` | 통과 |
| JUnit | 154개, 실패·오류·건너뜀 0개 |
| JaCoCo Line Coverage | 1,696 / 1,871 = 90.65% |
| IME 변경 후 native Robot 전체 검사 | 실패: Classic pause overlay의 누름·뗌 확인. 이후 Item·이름 입력·점수판은 미실행 |
| 상세 진단 포함 native 게임 그룹 | 통과: Classic·Item, 기존 P 키. 실패 재현 없음 |
| 이름 입력·점수판 포함 native 전체 재검사 | 통과: `[start, game, scoreboard]`, Gradle 종료 코드 0, 22초 |

IME 정책 검사는 변경 전 실패·변경 후 통과했지만 native 문제 해결의 증거가 되지 못했다. 이 검사를 포함했던 이전 155개 통과 기록과, 원복 후 현재 154개 통과 기록을 구분한다. MENU 누름 상태 검사와 클릭의 누름·뗌 확인 기준은 완화하지 않았다.

전체 재검사는 `8d5cd61`에서 JDK 21로 실행했다. 실제 마우스·키보드를 사용하므로 사용자가 입력을 비우는 시간을 확인한 뒤 실행한다.

```sh
./gradlew --no-daemon --console=plain desktopFocusSmoke
```

```text
PASS native AWT Robot events click/focus groups [start, game, scoreboard].
OS=Mac OS X 26.6.2 Java=21.0.13 LookAndFeel=Mac OS X
BUILD SUCCESSFUL in 22s
```

## 커밋과 증거

- `d2cd797`: pause/resume 후 MENU 취소·정상 클릭 회귀 검사
- `79fc49b`: 게임 조작의 문자 조합 처리 분리 및 수정 전 실패 검사
- `ce4ec2c`: native 클릭의 왼쪽 누름·뗌 확인과 실패 진단 보강
- `fed6d8a`: 효과가 확인되지 않은 IME 제품 변경과 관련 정책 검사 원복
- `3fb6264`: 실패 시 원본 클릭 이벤트·좌표·창 상태 진단 추가

로그와 검사 결과는 작업 공간 `.omx/evidence/menu-native-fix-20261101/`에 보관한다. 독립 버튼 진단은 캡처가 대상 창을 보여주지 않아 원인 판단 근거에서 제외했다. 실제 게임 창의 기록과 수정 전후 회귀 검사만 근거로 사용한다.
