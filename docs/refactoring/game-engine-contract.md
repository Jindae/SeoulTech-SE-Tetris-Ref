# 게임 엔진 계약 — RF-01 기준

기준 코드: `695e725898ca081ec0b79a138b2f2c87e7de460e` · 작성: 2026-10-31

RF-01에서 고정한 계약을 RF-03~06의 엔진과 화면에 적용했다. Classic/Item 1인 게임을 보존하기 위한 경계이며, 대전·공격·P2P 규격은 포함하지 않는다. 현재 구조는 [구현 안내](README.md), 단계별 결과는 [전달 계획](delivery-plan.md)에서 확인한다.

## 1. 호출과 소유권

```java
GameEngine(GameConfig config, BlockSource blocks);
GameUpdate start();
GameUpdate handle(GameCommand command);
GameUpdate advance(long elapsedNanos);
GameUpdate continueAfterLock(long turnId);
GameUpdate stop();
GameSnapshot snapshot();
```

| 계약 | 고정할 내용 |
|---|---|
| GameConfig | 기존 GameMode(CLASSIC/ITEM), Difficulty. 화면·키·저장소 설정은 제외 |
| GameCommand | LEFT, RIGHT, ROTATE, SOFT_DROP, HARD_DROP, TOGGLE_PAUSE |
| GameState | 엔진 내부만 수정. 고정 셀과 현재 블록 분리, public setter 없음 |
| BlockSource | nextBlock(Difficulty), nextItemBlock(). 운영은 기존 Generator 호출, 각 엔진에 독립 인스턴스·새 Block 공급 |
| GameUpdate | snapshot, 선택적인 lockResolution/gameEnd, 다음 갱신까지의 nextWakeupNanos |
| 실행 주체 | 초기 1인 UI에서는 Controller가 EDT에서 직렬 호출. 엔진은 Swing·파일·소켓을 사용하지 않음 |

생성만으로 게임이나 난수 공급을 시작하지 않는다. 최초 start에서 current/next 두 블록을 받고, 중복 start는 무시한다. 종료 후에는 새 엔진을 만든다. stop은 중복 호출해도 안전하며 효과·미처리 고정·공급 대기를 취소한다. 자연 게임오버 결과는 한 번만 반환하고, 메뉴 종료는 기록 저장을 유발하지 않는다.

`GameUpdate.nextWakeupNanos`는 양수의 상대 간격 또는 없음이다. 멈춘 중력 외에 표시할 효과도 없으면 없음이다. Controller는 결과를 소비·표시한 뒤 필요한 다음 시점에만 타이머를 예약한다. 오래된 세션 콜백은 무시한다.

## 2. 상태와 허용 동작

| 상태 | 허용 동작 |
|---|---|
| NEW | 최초 start / stop |
| ACTIVE, 재생 중 | 이동·회전·드롭·pause, 중력·표시 시간 진행 |
| ACTIVE, pause | pause 해제와 표시 시간 진행. 중력·이동·회전·드롭 금지 |
| RESOLVING | 효과 시간과 pause 변경. 이동·회전·드롭 금지 |
| AWAITING_SPAWN | 유효 turnId의 continueAfterLock. 입력·중력 금지 |
| GAME_OVER / STOPPED | 이후 입력·시간·start·고정 재개는 상태 변화 없음 |

stop은 모든 진행 상태에서 가능하다. pause는 효과 진행 상태와 별도로 보관한다.

일반 블록·Anchor·Bomb는 고정을 일으킨 호출에서 LockResolution을 반환한다. Row·Column·Cross는 효과 시작 시 RESOLVING만 반환하고, 효과를 확정하는 마지막 advance에서 결과를 반환한다. Controller는 결과를 받은 호출 안에서 continueAfterLock을 한 번 실행한다.

순서는 **고정 → 아이템 효과 → 줄 삭제·점수 → 고정 결과 → next 충돌 확인 → current 교체 → 새 next 공급**이다. 충돌·stop·오래된/중복 turnId에서는 추가 블록을 뽑지 않는다. 실제 삭제마다 ClearBatch에 삭제 직전 행·삭제 원인·이번 블록에서 온 셀 위치를 복사한다. 공격량이나 아이템 공격 인정 정책은 넣지 않는다.

## 3. 화면 사본 형식

[실제 값 예시 JSON](game-snapshot-example.json)은 빈 보드에서 O 블록이 시작한 장면이다. 통신 프로토콜이나 직렬화 구현은 아니다. Java에서는 이에 대응하는 불변 값 타입으로 표현한다.

| 필드 | 내용 |
|---|---|
| width / height | 10 / 20 |
| mode / difficulty / phase / paused | 게임 설정과 진행 상태 |
| fixedCells | 높이 20 × 너비 10. 빈칸 null, 나머지는 Cell 값 |
| current / next | Piece 값: kind와 사각형 cells 배열. 도형의 빈칸은 null |
| x / y / ghostY | 현재 블록과 고스트의 원점. NEW에서는 없음 |
| score / clearedLines / level | 점수·누적 삭제 줄·레벨 |
| dropIntervalNanos | 현재 중력 간격 |
| highlightedRows | 일반 삭제 표시 행 번호 목록 |
| effect | 없음 또는 종류·진행 프레임·표시를 덮을 셀 값 목록 |

Cell은 `value`(점유 값), `text`(문자), `argb`(32비트 기본 색상)로 구성한다. effect의 덮어쓰기 셀에는 x/y 좌표를 추가하고 빈 문자도 표현한다. 팔레트·글꼴·화면 크기와 반투명 강조 그리기는 View가 적용한다.

깊은 복사 또는 불변 중첩 목록을 사용한다. 외부 배열·목록 변경이 엔진을 바꿀 수 없어야 하며, 과거 사본은 다음 진행 후에도 변하지 않는다. 가변 Block은 노출하지 않는다.

ACTIVE일 때만 current와 ghost를 별도 합성한다. 고정 뒤에는 해당 셀이 fixedCells에 들어가므로 RESOLVING/AWAITING_SPAWN에서 current를 다시 그리지 않는다. 효과 표시가 있으면 기본 셀 표시 위에 덮어쓴다.

## 4. 보존할 수치와 시간

| 항목 | 기준 |
|---|---|
| 보드·등장 위치 | 10×20, x=3 / y=0 |
| 낙하 점수 | 이동한 칸마다 현재 level점. 자동·수동·hard drop 동일 |
| 행 삭제 점수 | 한 번의 removeRows에서 100 + 200 + …; 겹친 행은 한 번만 계산 |
| 레벨 | 누적 10줄마다 증가, 최대 19. 기존 Difficulty 공식 유지 |
| 초기 중력 | 1,000ms |
| 일반 삭제 표시 | 논리 삭제 즉시 완료, 210ms 뒤 표시 종료. pause 중에도 표시 시간 진행 |
| Row 효과 | 50ms 간격 5개 표시 프레임, 다음 호출에 확정 |
| Column/Cross 효과 | 50ms 간격 10개 표시 프레임, 다음 호출에 확정 |
| Item 공급 | 고정 전/후 삭제 줄의 10줄 경계 차이만큼 대기량 누적, 이후 next 생성에서 순차 소비 |

advance는 음수를 거부하고 0에서는 변화가 없다. 지연된 호출 한 번에 중력 또는 아이템 프레임을 최대 한 번 진행하고 초과 주기를 버린다. 새 효과·다음 블록에 같은 경과시간을 재사용하지 않는다. 하이라이트는 실제 누적 경과시간으로 끝낸다.

입력 때는 직전 시점부터 시간을 한 번 정산하고 명령을 처리한다. 메뉴 종료는 정산 없이 즉시 stop한다. pause/resume와 시간 기반 아이템 효과 전후에는 중력 대기시간을 새 간격으로 시작한다. 일반 수동 고정은 이미 지난 중력 시간을 유지한다. 키 우선순위·이름 입력·순위 판단·기록 저장 시점은 기존 Gamepanel 흐름을 유지한다.

## 5. 다음 단계 연결

- RF-02: 키 명령과 명시적 시작/종료를 Controller로 분리. 기존 Board 표시·규칙 유지.
- RF-03/04: 이 계약의 엔진과 Snapshot 구현. 기존 실행 경로와 섞지 않고 테스트로 검증.
- RF-05: Classic/Item을 함께 연결하고 기존 Board 규칙·타이머 제거.
- RF-06: 두 엔진 독립성과 [기존 테스트 대응표](rf01-test-migration.csv)의 누락 없는 이전 확인.

최종 실행 증거와 미해결 사항은 [RF-05/06 검증 기록](rf05-06-verification.md)에 둔다. 리팩토링 전 기준은 [RF-01 기준선 기록](rf01-baseline.md)에서 확인한다. 계약 변경 시 이 문서·예시·테스트 기대 결과를 함께 검토한다.
