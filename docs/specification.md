# 게임 동작 및 아이템 명세

이 문서는 평가자가 게임의 예상 동작을 코드와 대조할 수 있도록 현재 구현 정책을 정리한다.

- 현재 구현 기준 커밋: `154fd097deff2675e7030f607b0ceeb35b9e69c5`
- 제품 동작의 근거: 현재 소스와 자동 테스트
- 사용자와 별도로 확정한 정책: P 아이템의 형태·발동·점수, 5종 균등 선택, 10줄 공급권 누적
- 최종 제출 OS: macOS 26 이상
- 공개 제출 버전: [`v0.1.0-req2.3`](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases/tag/v0.1.0-req2.3)

이 문서는 게임이 어떻게 동작하는지를 정의한다.

자동·수동 검증 결과는 검증 보고서에서 다룬다.

팀의 결정 과정은 프로젝트 관리 기록에서 다룬다.

## 1. 화면과 게임 흐름

### 1.1 시작 화면

프로그램은 `TETRIS` 제목과 다음 메뉴를 표시한다.

| 표시 | 동작 |
|---|---|
| `CLASSIC` | 일반 블록만 사용하는 Classic 게임 시작 |
| `ITEM` | 줄 누계에 따라 아이템을 공급하는 Item 게임 시작 |
| `MENU` | 설정 화면 열기 |
| `SCORE` | 전체·Classic·Item 순위 화면 열기 |
| `EXIT` | 프로그램 종료 |

메뉴는 위·아래 방향키로 이동하고 Enter로 선택한다.
근거는 [Startpanel](../src/main/java/seoultech/se/tetris/component/Startpanel.java#L28)이다.

### 1.2 게임 화면

- 보드는 20행 × 10열이다.
- 보드와 다음 블록은 고정폭 문자 기반으로 표현한다.
- 보드 경계는 `X`, 일반 점유 칸은 `O`를 사용한다.
- 화면 오른쪽에는 현재 점수, 다음 블록 1개와 `MENU` 버튼을 표시한다.
- 다음 미리보기의 객체가 다음 고정 이후 현재 블록으로 그대로 승계된다.
- 새 블록을 생성 위치에 놓을 수 없으면 게임오버가 된다.

근거: [Board](../src/main/java/seoultech/se/tetris/component/Board.java#L43), [Gamepanel](../src/main/java/seoultech/se/tetris/component/Gamepanel.java#L109), [Nextblockpanel](../src/main/java/seoultech/se/tetris/component/Nextblockpanel.java#L88).

### 1.3 화면 크기

설정 파일의 표기는 **높이X너비** 순서다.
값은 JFrame 제목 표시줄·외곽 테두리가 아닌 게임 내용 영역의 목표 크기다.

| 저장 값 | 내용 영역 너비 × 높이 | 기본값 |
|---|---:|---|
| `600X500` | 500 × 600 | 예 |
| `750X625` | 625 × 750 | 아니오 |
| `900X750` | 750 × 900 | 아니오 |

설정 저장 후 설정 화면에서 나올 때 새 크기를 적용한다.
게임 패널은 선택한 너비와 높이를 유지한다. 제목 표시줄과 작업 표시줄을 제외한 공간에 패널이 들어가지 않으면, 창을 해당 모니터의 작업 영역 안에 배치하고 스크롤로 패널 끝까지 이동할 수 있게 한다.
근거: [Maincontainer 설정 적용](../src/main/java/seoultech/se/tetris/component/Maincontainer.java#L124), [설정 후보](../src/main/java/seoultech/se/tetris/component/Maincontainer.java#L211).

## 2. 모드와 난이도

### 2.1 게임 모드

| 모드 | 블록 구성 | 순위 |
|---|---|---|
| Classic | 일반 테트로미노 7종 | Classic 기록으로 저장·조회 |
| Item | 일반 블록과 10줄 단위로 공급되는 아이템 | Item 기록으로 저장·조회 |

두 모드 모두 설정에서 선택한 Easy·Normal·Hard 난이도를 사용한다.
난이도는 낙하 속도와 일반 블록 생성 확률에 적용된다.
아이템 5종의 선택 확률에는 난이도를 적용하지 않는다.

### 2.2 일반 블록 생성 확률

실제 생성기는 I, J, L, Z, S, T, O 순서의 가중치를 사용한다.

| 난이도 | I 가중치 | 나머지 6종의 각 가중치 | 합계 | I 확률 | 나머지 각 확률 |
|---|---:|---:|---:|---:|---:|
| Easy | 12 | 10 | 72 | 16.6667% | 13.8889% |
| Normal | 1 | 1 | 7 | 14.2857% | 14.2857% |
| Hard | 8 | 10 | 68 | 11.7647% | 14.7059% |

근거: [BlockGenerator](../src/main/java/seoultech/se/tetris/blocks/BlockGenerator.java#L11), [분포 테스트](../src/test/java/seoultech/se/tetris/blocks/BlockDistributionTest.java#L18).

분포 자동검증 정책은 난이도마다 실제 생성기를 100,000회 호출하고, 시드 `20261024`를 사용하며, 각 블록의 관측 확률이 기대 확률에 대해 상대오차 5% 이내인지 판정하는 것이다.

상대오차 계산식은 다음과 같다.

```text
relative error = abs(observed probability - expected probability) / expected probability
```

현재 구현은 상대오차 5%를 기준으로 사용한다.
현재 관측값은 상대오차 5%와 절대 5%p 해석을 모두 만족한다.
실제 결과 수치는 검증 보고서에서 관리한다.

### 2.3 낙하 속도

- 모든 난이도의 레벨 1 시작 간격은 1,000ms다.
- 삭제 줄 누계 10줄마다 레벨이 1 증가한다.
- 레벨 상한은 19다.
- Easy와 Hard는 Normal의 **속도 증가분**에 각각 0.8과 1.2를 곱한다.
  초기 속도 자체를 20% 변경하지 않는다.

레벨 `L`을 1~19로 제한했을 때 구현 산식은 다음과 같다.

```text
normalSeconds = (0.8 - (L - 1) × 0.007) ^ (L - 1)
normalSpeed   = 1 / normalSeconds
speed        = 1 + factor × (normalSpeed - 1)
intervalMs   = max(1, round(1000 / speed))

factor: Easy 0.8, Normal 1.0, Hard 1.2
```

예를 들어 레벨 2/3의 간격은 Easy 827/669ms, Normal 793/618ms, Hard 761/574ms다.
근거: [Difficulty](../src/main/java/seoultech/se/tetris/Difficulty.java#L6), [속도 회귀 테스트](../src/test/java/seoultech/se/tetris/component/Pr15BoardRegressionTest.java#L344).

현재 코드의 설정 초기값은 **Easy**다.
설정 파일이 없거나 난이도 값이 유효하지 않으면 이 초기값을 유지한다.

## 3. 조작과 상태 전환

### 3.1 기본 키

| 기능 | 기본 키 | 변경 가능 |
|---|---|---|
| 시계 방향 90도 회전 | ↑ | 예 |
| 한 칸 아래 이동 | ↓ | 예 |
| 한 칸 왼쪽 이동 | ← | 예 |
| 한 칸 오른쪽 이동 | → | 예 |
| 정지·재개 | P | 예 |
| 하드드롭 | Space | 예 |

- 여섯 키는 서로 중복될 수 없다.
- Pause에는 Enter를 지정할 수 없다.
  일시정지 중 Enter가 게임 종료에 예약되어 있기 때문이다.
- 변경 화면에서 이미 사용 중인 키를 입력하면 적용하지 않는다.
- 벽·바닥·고정 블록과 충돌하는 이동이나 회전은 적용하지 않는다.
  일반 블록 회전은 가능한 위치 보정을 거친다.

근거: [기본 키](../src/main/java/seoultech/se/tetris/component/Maincontainer.java#L22), [키 변경 제약](../src/main/java/seoultech/se/tetris/component/Settingpanel.java#L307), [게임 입력](../src/main/java/seoultech/se/tetris/component/Gamepanel.java#L240).

### 3.2 낙하와 고정

- 타이머 낙하와 ↓ 입력은 성공적으로 한 칸 이동할 때마다 동일한 낙하 점수를 준다.
- 하드드롭은 충돌 직전까지 이동하고 실제 이동한 칸마다 같은 낙하 점수를 준다.
- 더 내려갈 수 없는 상태에서 다음 낙하가 발생하면 현재 블록을 고정하고, 줄과 아이템 효과를 처리한 뒤 미리보기 블록을 현재 블록으로 승계한다.
- 아이템 효과 애니메이션 중에는 이동·회전·하드드롭 입력을 받지 않고 자동 낙하 타이머도 정지한다.

### 3.3 일시정지·종료

- Pause 키는 진행 중 게임을 정지하고 같은 키로 재개한다.
- 일시정지 중 Enter는 현재 게임을 종료하고 시작 화면으로 돌아간다.
- 진행 중 Esc 또는 화면의 `MENU` 버튼도 현재 게임을 종료한다.
  단, Esc가 사용자가 변경한 게임 조작키와 겹치면 해당 조작이 우선한다.
- 자발적으로 종료한 게임의 점수는 저장하지 않는다.
- 게임오버 뒤 현재 모드의 순위 진입 대상일 때만 이름을 입력하고 기록을 저장한다.
- 아이템 애니메이션 도중 정지하면 애니메이션 종료 후에도 정지 상태를 유지한다.
  게임 종료는 진행 중 애니메이션과 공급 대기를 취소한다.

근거: [정지·종료 처리](../src/main/java/seoultech/se/tetris/component/Gamepanel.java#L186), [아이템 애니메이션 상태](../src/main/java/seoultech/se/tetris/component/Board.java#L487).

## 4. 점수·줄·레벨

### 4.1 낙하 점수

```text
한 칸 낙하 점수 = 1 × 현재 레벨
```

자동 낙하, ↓ 이동과 하드드롭의 실제 이동 칸에 같은 산식을 적용한다.
블록이 이미 고정 위치에 있을 때의 고정 동작 자체에는 낙하 점수를 주지 않는다.

### 4.2 행 삭제 점수

한 번의 고정 처리에서 삭제하는 **서로 다른 행 수**에 대해 다음 합계를 적용한다.

```text
1행: 100
2행: 100 + 200 = 300
3행: 100 + 200 + 300 = 600
4행: 100 + 200 + 300 + 400 = 1000
...
```

행 점수에는 레벨 배율을 적용하지 않는다.
삭제한 각 행은 줄 누계에 1씩 더하고, 누계가 10의 배수가 될 때 레벨을 올린다.
일반 완성 행과 아이템 지정 행이 겹치면 같은 행을 한 번만 계산한다.
근거: [행 제거·점수](../src/main/java/seoultech/se/tetris/component/Board.java#L342).

### 4.3 삭제 표현

- 일반 완성 행은 반투명 흰색으로 약 210ms 강조한다.
- L·C·P 효과는 50ms 단위 프레임으로 중앙에서 바깥쪽으로 지워지는 문자 애니메이션을 사용한다.
- 애니메이션 중 자동 낙하와 블록 조작을 차단한다.

이 시간은 Swing 타이머 설정값이며 실제 화면의 정확한 벽시계 시간 보증은 아니다.

## 5. 설정·색상·저장

### 5.1 설정 항목과 초기값

| 항목 | 후보 | 초기값 |
|---|---|---|
| 화면 크기 | `600X500`, `750X625`, `900X750` | `600X500` |
| 조작키 | 회전·아래·왼쪽·오른쪽·정지·하드드롭 | ↑, ↓, ←, →, P, Space |
| 색상 | Normal, Protanopia, Deuteranopia, Tritanopia | Normal |
| 난이도 | Easy, Normal, Hard | Easy |

`Save and Exit`를 선택하면 설정을 `setting.txt`에 저장한 뒤 시작 화면에 적용한다.

`Reset setting`은 화면 크기·키·색상·난이도를 초기값으로 되돌린다.
`Reset Scoreboard`는 모든 모드의 점수 파일을 비운다.
두 초기화는 서로 독립적이다.

### 5.2 색상 모드

Normal은 원래 블록 색을 사용한다.
Protanopia, Deuteranopia와 Tritanopia는 블록과 주요 UI 색상에 각 모드의 색 변환식을 적용한다.
문자 `L`, `C`, `B`, `P`와 아이템 고유 형상도 색상 외 식별 수단으로 사용한다.

네 팔레트의 렌더 변화와 아이템 문자 대비를 자동 테스트로 검사한다.
색각 모드의 검증 결과는 검증 보고서에서 관리한다.
색 변환 근거: [Maincontainer.changeColor](../src/main/java/seoultech/se/tetris/component/Maincontainer.java#L155).

### 5.3 설정 파일 예외

- 설정 파일이 없으면 초기값으로 시작한다.
- 알 수 없는 설정 이름은 무시한다.
- 화면 크기·색상·난이도 값이 후보에 없으면 해당 항목의 현재 초기값을 유지한다.
- 키 설정은 여섯 필드, 양의 키 코드, 중복 없음, Pause≠Enter 조건을 모두 만족할 때만 읽는다.
- 설정 파일 읽기·쓰기 중 IOException은 표준 오류에 stack trace를 출력한다.

근거: [설정 읽기·쓰기](../src/main/java/seoultech/se/tetris/component/Maincontainer.java#L281).

### 5.4 데이터 위치

- 패키지 실행은 사용자 홈의 `.seoultech-tetris` 폴더에 `setting.txt`와 `tetris_score.txt`를 둔다.
- 개발 실행은 별도 `tetris.dataDir` 시스템 속성이 없으면 현재 작업 디렉터리를 사용한다.
- 테스트나 관리 실행에서 `tetris.dataDir`을 지정하면 그 디렉터리를 우선한다.

근거: [DataPaths](../src/main/java/seoultech/se/tetris/storage/DataPaths.java#L11).

## 6. 순위와 기록

### 6.1 진입·이름·동점 정책

- 순위 화면은 All·Classic·Item 필터를 제공하고 각 화면에서 최대 10개를 표시한다.
- 게임오버 점수는 해당 게임 모드의 기록이 10개 미만이면 진입 대상이다.
- 10개 이상이면 현재 점수가 10위 점수보다 **클 때만** 진입한다. 10위와 동점이면 저장하지 않는다.
- 이름은 영문 대·소문자와 숫자를 최대 10자까지 입력한다.
- 아무 문자도 입력하지 않고 Enter를 누르면 `PLAYER`로 저장한다.
- 같은 이름과 같은 점수를 여러 번 저장할 수 있다.
- 점수는 내림차순으로 정렬한다.
  동점은 파일에 먼저 있던 기록의 순서를 유지한다.
- 새로 저장한 기록과 이름·점수·난이도·모드가 모두 같은 항목이 여러 개면 가장 나중 항목을 강조한다.

근거: [게임오버·이름 입력](../src/main/java/seoultech/se/tetris/component/Gamepanel.java#L121), [순위 표시](../src/main/java/seoultech/se/tetris/component/Scoreboardpanel.java#L102).

### 6.2 저장 형식과 구형 호환

현재 기록은 다음 네 필드로 저장한다.

```text
username:score:difficulty:mode
```

예:

```text
NEO:321:Easy:Item
```

구형 파일은 다음과 같이 처리한다.

| 필드 수 | 해석 | All | Classic/Item 필터 |
|---:|---|---|---|
| 2 | 이름·점수 | 표시 | 제외 |
| 3 | 이름·점수·난이도 | 표시 | 제외 |
| 4 | 이름·점수·난이도·모드 | 표시 | 일치 모드에 표시 |

숫자가 아닌 점수, 알 수 없는 난이도·모드, 지원하지 않는 필드 수의 행은 건너뛰고 나머지 유효 기록을 계속 읽는다.
저장·읽기 IOException은 현재 표준 오류에 stack trace만 출력한다.

근거: [ScoreStore](../src/main/java/seoultech/se/tetris/score/ScoreStore.java#L47), [구형 기록 테스트](../src/test/java/seoultech/se/tetris/score/ScoreStoreModeTest.java#L30).

## 7. 아이템 공급 공통 정책

아이템은 Item 모드에서만 공급한다.

1. 게임은 처음에 일반 블록 2개(현재·미리보기)로 시작한다.
2. 한 번의 블록 고정 처리 전후 삭제 줄 누계를 비교한다.
3. 누계가 10줄 경계를 하나 지날 때마다 아이템 공급권 1개를 만든다.
4. 기존 미리보기는 먼저 현재 블록으로 승계하고, 공급권 1개를 소비해 **다음 미리보기**를 아이템으로 만든다.
5. 한 번에 여러 10줄 경계를 지나면 공급권을 모두 보관하고, 이후 블록 고정마다 1개씩 다음 미리보기에 공급한다.
6. 새 게임 시작·게임 종료·게임오버 시 남은 공급권을 버린다.

아이템 종류는 Anchor(O), Bomb(B), Row(L), Column(C), Cross(P) 5종 중 균등하게 선택한다.
각 종류의 기대 확률은 20%다.
난수 자동검증은 실제 생성기를 100,000회 호출하고 시드 `20261025`, 상대오차 5% 기준을 사용한다.

아이템 효과로 행을 삭제하면 그 행도 줄 누계와 행 점수에 포함한다.
C의 열 삭제와 B·O의 블록 제거 자체는 행 누계나 효과 보너스 점수를 직접 만들지 않는다.
B·C 효과 후에는 모든 열의 남은 칸이 각 열 안에서 바닥까지 내려앉는다.
B 정착으로 완성된 행은 기존 행 삭제 정책에 따라 점수·누계·아이템 공급에 반영한다.
C가 지운 열은 빈 상태로 남으므로 C 효과와 정착만으로는 새 완성 행이 생기지 않는다.
근거: [공급 처리](../src/main/java/seoultech/se/tetris/component/Board.java#L231), [공급 경계 테스트](../src/test/java/seoultech/se/tetris/component/ItemSupplyAcceptanceTest.java#L50), [아이템 분포 테스트](../src/test/java/seoultech/se/tetris/blocks/ItemDistributionTest.java#L15).

## 8. 아이템 5종 명세 — R2-P19·R2-P20

원문 최소 의무는 팀 자체 아이템 C·B·P의 형태와 기능을 명세하고 평가팀에 제공하는 것이다.
아래 표는 지정 아이템 L·O까지 같은 형식으로 정리하여 실제 구현을 대조할 수 있게 한다.

### 8.1 L — 행 삭제 아이템

| 항목 | 명세 |
|---|---|
| 분류 | 지정 아이템, 구현 클래스 `RowBlock` |
| 형태 | 일반 테트로미노 7종 중 하나를 무작위로 고르고, 점유 칸 4개 중 하나를 `L`로 표시 |
| 색상 | 기본 블록은 선택된 테트로미노 색, `L` 표시는 흰색 |
| 회전 | 일반 블록처럼 회전하며 `L` 표시도 같은 점유 칸과 함께 회전 |
| 발동 | 바닥 또는 쌓인 블록 위에 최종 고정될 때 1회 |
| 효과 | `L` 표시가 놓인 보드 행 전체를 완성 여부와 관계없이 삭제·압축 |
| 점수·누계 | 해당 행을 1행 삭제로 계산: 단독이면 100점·누계 +1. 동시에 완성된 다른 행과 합쳐 고유 행 수의 콤보 점수 적용 |
| 경계 | 표시 행과 일반 완성 행이 같아도 한 번만 계산 |

가능한 외형은 일반 I/J/L/Z/S/T/O의 현재 방향이며 `L`은 그중 한 점유 칸을 대체한다.
예:

```text
OOLO   OOO   OO    ...
       O      LO
```

실제 형태와 표시 위치는 생성 때 무작위로 결정된다.
근거: [RowBlock](../src/main/java/seoultech/se/tetris/blocks/RowBlock.java#L10), [L 인수 테스트](../src/test/java/seoultech/se/tetris/component/LineItemAcceptanceTest.java#L48).

### 8.2 O — 무게추 아이템

| 항목 | 명세 |
|---|---|
| 분류 | 지정 아이템, 구현 클래스 `AnchorBlock` |
| 문자·색상 | 모든 점유 칸 `O`, 밝은 회색 `#C0C0C0` |
| 크기 | 4열 × 2행, 6칸 점유 |
| 회전 | 불가 |
| 이동 | 최초 바닥/스택 접촉 전 좌우 이동 가능, 최초 접촉 뒤 좌우 이동 잠금 |
| 발동 | 최종 고정 시 1회 |
| 효과 | 현재 위치 아래의 폭 4열 블록을 바닥까지 제거하고 무게추 자체를 바닥으로 강제 이동 |
| 점수·누계 | 제거와 강제 이동은 0점·0줄. 접촉 전 실제 낙하 칸은 일반 낙하 점수 유지 |
| 경계 | 좌우 벽에서도 현재 x부터 폭 4열 안만 제거; 효과 시작 위치 위와 폭 밖 셀은 보존 |

```text
 OO
OOOO
```

일반 O 테트로미노도 `O` 문자를 쓰지만 무게추는 4×2 고유 형상과 밝은 회색으로 구분한다.
근거: [AnchorBlock 색상](../src/main/java/seoultech/se/tetris/blocks/AnchorBlock.java#L16), [무게추 인수 테스트](../src/test/java/seoultech/se/tetris/component/AnchorAcceptanceTest.java#L48).

### 8.3 C — 열 삭제 아이템

| 항목 | 명세 |
|---|---|
| 분류 | 자체 아이템, 구현 클래스 `ColumnBlock` |
| 형태 | 일반 테트로미노 7종 중 하나를 무작위로 고르고, 점유 칸 4개 중 하나를 `C`로 표시 |
| 색상 | 기본 블록은 선택된 테트로미노 색, `C` 표시는 흰색 |
| 회전 | 일반 블록처럼 회전하며 `C` 표시도 같은 점유 칸과 함께 회전 |
| 발동 | 바닥 또는 쌓인 블록 위에 최종 고정될 때 1회 |
| 효과 | `C` 표시가 놓인 **원래 보드 열**의 20칸을 지운 뒤, 모든 열의 남은 칸을 각 열 안에서 바닥까지 정착 |
| 정착 규칙 | 열 간 좌우 이동 없음. 각 열의 세로 순서·문자·색상을 보존. C가 지운 열은 빈 상태로 유지 |
| 점수·누계 | 열 효과는 0점·0줄. 지운 열이 비어 있어 정착으로 새 완성 행은 생기지 않음 |
| 경계 | 첫 열·마지막 열도 해당 열만 삭제한 뒤 모든 열에 같은 정착 규칙 적용 |

예:

```text
OOCO   OOO   OO    ...
       O      CO
```

근거: [ColumnBlock](../src/main/java/seoultech/se/tetris/blocks/ColumnBlock.java#L10), [C 효과·정착 구현](../src/main/java/seoultech/se/tetris/component/Board.java#L456), [C/B 정착 인수 테스트](../src/test/java/seoultech/se/tetris/component/ColumnBombAcceptanceTest.java#L268).

### 8.4 B — 폭발 아이템

| 항목 | 명세 |
|---|---|
| 분류 | 자체 아이템, 구현 클래스 `BombBlock` |
| 문자·색상 | 중심부 한 칸은 흰색 `B`, 나머지 점유 칸은 갈색 `O` |
| 크기 | 3열 × 4행, 10칸 점유 |
| 회전 | 불가 |
| 발동 | 바닥 또는 쌓인 블록 위에 최종 고정될 때 1회 |
| 효과 | 3×4 형상 경계의 위·아래·좌·우 **2칸 여백까지 포함한 직사각형**의 모든 셀을 지운 뒤, 모든 열의 남은 칸을 각 열 안에서 바닥까지 정착 |
| 정착 규칙 | 열 간 좌우 이동 없음. 각 열의 세로 순서·문자·색상을 보존 |
| 점수·누계 | 폭발 효과는 0점·0줄. 실제 낙하 점수는 유지. 정착으로 완성된 행은 기존 행 삭제 규칙으로 점수·누계·아이템 공급에 반영 |
| 경계 | 보드 밖 범위는 잘라내고 보드 안 교집합만 삭제 |

```text
 O
OOO
OBO
OOO
```

중앙 위치에서 효과 범위는 최대 7열 × 8행이며, 벽·바닥에서는 보드 경계에 맞춰 축소된다.
근거: [BombBlock](../src/main/java/seoultech/se/tetris/blocks/BombBlock.java#L7), [폭발·정착 구현](../src/main/java/seoultech/se/tetris/component/Board.java#L269), [정착·점수·공급 인수 테스트](../src/test/java/seoultech/se/tetris/component/ColumnBombAcceptanceTest.java#L268).

### 8.5 P — 행·열 교차 삭제 아이템

| 항목 | 명세 |
|---|---|
| 분류 | 자체 아이템, 구현 클래스 `CrossBlock` |
| 문자·색상 | 네 칸 모두 `P`, 보라색 `#B04CFF` |
| 크기 | 2열 × 2행, 4칸 점유 |
| 회전 | 입력해도 모양이 바뀌지 않음 |
| 발동 | 바닥 또는 쌓인 블록 위 어디든 최종 고정될 때 1회 |
| 효과 | 블록이 차지한 두 행 전체와 두 열 전체를 삭제. 열을 먼저 비운 상태에서 두 행을 제거·압축 |
| 점수·누계 | 두 지정 행은 완성 여부와 무관하게 고유 삭제 행으로 계산하여 기본 300점·누계 +2. 두 열은 별도 점수·누계 없음. 실제 낙하 점수는 별도 유지 |
| 중복 | 두 지정 행과 일반 완성 행의 합집합으로 점수·누계를 계산하므로 같은 행을 중복 계산하지 않음 |
| 경계 | x=0~8, y=0~18의 유효 고정 위치에서 보드 밖 접근 없이 해당 행·열만 처리 |

```text
PP
PP
```

P의 2×2 형태, 바닥·스택 어디서든 고정 발동, 두 행·두 열 삭제, 행 300점·세로 무점수, 실제 낙하 점수 유지와 두 행 누계는 사용자와 확정한 정책이다.
근거: [CrossBlock](../src/main/java/seoultech/se/tetris/blocks/CrossBlock.java#L5), [P 효과 구현](../src/main/java/seoultech/se/tetris/component/Board.java#L406), [P 인수 테스트](../src/test/java/seoultech/se/tetris/component/CrossItemAcceptanceTest.java#L35).

## 9. 아이템 효과의 공통 상태 규칙

- 효과는 블록 고정 경로에서 한 번만 실행한다.
- 효과가 끝나기 전 다음 블록으로 승계하지 않는다.
- L·C·P 애니메이션 중 이동·회전·낙하 입력을 차단한다.
- 효과 중 Pause가 들어오면 자동 낙하 재시작을 보류하고, 재개 시 진행한다.
- 메뉴 종료·게임오버는 애니메이션 타이머와 공급 대기를 정리한다.
- B·C는 효과 범위를 비운 뒤 모든 열의 남은 칸을 각 열 안에서 정착시키며 세로 순서·문자·색상을 보존한다.
- P는 두 열을 먼저 비우고 지정한 두 행을 기존 행 삭제 방식으로 압축하는 현재 동작을 유지한다.
- 아이템 처리와 일반 완성 행 처리가 같은 고정에서 발생하면 효과 후 남은 완성 행을 포함하여 고유 행 집합으로 처리한다.
- 줄 누계가 여러 공급 경계를 넘으면 공급권을 잃지 않고 이후 고정마다 하나씩 소비한다.

관련 회귀 근거: [PR15 보드 회귀 테스트](../src/test/java/seoultech/se/tetris/component/Pr15BoardRegressionTest.java#L65), [공급 인수 테스트](../src/test/java/seoultech/se/tetris/component/ItemSupplyAcceptanceTest.java#L74).

## 10. 검증·배포 정책

이 절은 Q12~Q14의 정책을 기록한다.
실제 통과 결과는 검증 보고서가 정본이다.

| 주제 | 정책 |
|---|---|
| Line Coverage | 직접 작성한 `src/main` 전체를 측정하며 제외 목록 없음. Gradle gate 70%. 평가 기준은 중간 50%, 기말 70% |
| 성능 자동검사 | 빈 20×10 보드에서 이동·회전 핵심 판정 400,000회를 5초 안에 완료 |
| Java | 소스·테스트·패키징 toolchain Java 21 |
| 패키징 | 실행 아이콘과 Java 21 runtime을 포함한 macOS app-image 생성 |

근거: [Gradle 검증 설정](../build.gradle#L58), [성능 테스트](../src/test/java/seoultech/se/tetris/component/PerformanceRegressionTest.java#L20), [패키징 설정](../gradle/packaging.gradle#L42).

최종 제출 대상은 macOS 26 이상이다.

최종 tag와 두 ZIP은 [v0.1.0-req2.3 릴리스](https://github.com/HoroyoiPeach/SeoulTech-SE-Tetris-Ref/releases/tag/v0.1.0-req2.3)에 공개했다.
파일 무결성과 공개 앱의 실행 결과는 [검증 보고서](verification.md)에서 확인한다.

## 11. 정책 근거

다음 표는 정책과 확인 근거를 연결한다.

| 연결 ID | 정책 | 확인 근거 |
|---|---|---|
| Q01 | TETRIS·문자 보드·점수·미리보기 배치 | 구현 |
| Q02 | 6키 변경·중복 금지·Pause의 Enter 금지 | 구현·테스트 |
| Q03 / R2-P26 | 10줄 레벨업·19 상한·0.8/1.0/1.2 산식 | 구현·테스트 |
| Q04 | 낙하 1×레벨, 행 100+200+… | 구현·테스트 |
| Q05 | 다음 블록 1개 | 구현 |
| Q06 | 세 크기·기본 `600X500` | 구현·테스트 |
| Q07 | Normal과 색각 3모드 | 구현·렌더 테스트 |
| Q08 | 모드별 10개·엄격한 10위 초과·동점 안정 정렬·영숫자 10자 | 구현·테스트 |
| Q09 | 자발적 종료 미저장, 진입 대상 게임오버만 저장 | 구현·테스트 |
| Q10 | 설정 초기화와 전체 순위 초기화 분리 | 구현·테스트 |
| Q11 | 파일 없음 기본값·잘못된 행 제외·쓰기 실패 stack trace | 구현·테스트 |
| Q12 | 중간 50%·기말/빌드 gate 70%, main 전체 | 원문·빌드 설정 |
| Q13 | 핵심 판정 400,000회/5초 | 테스트 정책 |
| Q14 | Java 21 runtime 동봉, macOS host-native 생성 | 구현·패키징 설정 |
| R2-P27 | 상대오차 5% | 테스트 정책 |
| R2-P28 | 일반 시드 20261024·아이템 시드 20261025, 각각 100,000회 | 테스트 정책 |
| R2-P29 | 5종 20%, 10줄마다 1권리, 복수 권리 순차 공급 | 구현·테스트·사용자 승인 |
| R2-P30 | Item에도 선택 난이도 적용, 모드 필터와 난이도 라벨 | 구현·테스트 |
| R2-P31 | O/C/B 0점 효과, P 두 행 300점, 낙하 별도 | 구현·테스트·P 사용자 승인 |
| R2-P32 | 일반 210ms, 아이템 50ms 프레임, 효과 중 입력·정지·종료 | 구현·테스트 |
| R2-P33 | 구형 2/3필드 기록은 All 유지, 모드 필터 제외 | 구현·테스트 |

## 관련 문서

- [검증 보고서](verification.md)
- [프로젝트 관리 기록](project-management.md)
