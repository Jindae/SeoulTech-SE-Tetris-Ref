package seoultech.se.tetris.component;

import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import seoultech.se.tetris.blocks.*;
import seoultech.se.tetris.rules.CollisionRules;
import seoultech.se.tetris.score.ScoreStore;
import sun.misc.Unsafe;

/**
 * 리팩토링 전후의 기존 동작을 확인하는 독립 실행 회귀 테스트. JDK 21 이상 필요.
 * 저장소 루트에서 실행(macOS/Linux):
 *
 *   javac --release 21 -d /tmp/tetris-tests $(find src/main/java -name '*.java') src/test/java/seoultech/se/tetris/component/RefactoringRegressionTest.java
 *   java -cp /tmp/tetris-tests seoultech.se.tetris.component.RefactoringRegressionTest
 *
 * 새 테스트 라이브러리 없이 실행하며, 각 검사는 임시 디렉터리/별도 JVM을 사용한다.
 * 실제 setting.txt와 tetris_score.txt는 읽거나 변경하지 않는다.
 *
 * 현재 UI와 게임 규칙이 결합되어 있어 private 상태 준비에는 reflection을 사용한다.
 * 창 없이 패널을 검사하기 위해 Maincontainer만 생성자를 우회한다(Unsafe).
 * 리팩토링으로 모델이 분리되면 아래 어댑터를 일반 생성자/공개 동작 호출로 교체한다.
 * 실제 창의 크기·포커스·페인트 결과, Windows 실행, 커버리지는 이 파일의 검증 범위 밖이다.
 */
public final class RefactoringRegressionTest {
    private static final String COMPONENT = "seoultech.se.tetris.component.RefactoringRegressionTest";
    private static final String MARKER = ".refactoring-test-sandbox";
    private static final String DEFAULT_KEYS = "ROTATE=38,DOWN=40,LEFT=37,RIGHT=39,PAUSE=80,HARD_DROP=32";
    private static final String CUSTOM_KEYS = "ROTATE=87,DOWN=40,LEFT=37,RIGHT=39,PAUSE=80,HARD_DROP=32";
    private static final String[] START_LABELS = {"CLASSIC", "ITEM", "MENU", "SCORE", "EXIT"};
    private static final String[] SETTING_LABELS = {"Window Option: ", "Change Keys", "Reset Scoreboard", "Color Option: ", "Difficulty: ", "Reset setting", "Save and Exit"};
    private static final String[] KEY_LABELS = {"Rotate: ", "Down: ", "Left: ", "Right: ", "Pause: ", "Hard Drop: ", "Save and Exit"};

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private record Check(String id, String description, CheckedRunnable action) {}

    private static final List<Check> CHECKS = List.of(
        new Check("frame-size", "실제 창 크기와 게임 설정 크기의 독립성", RefactoringRegressionTest::frameSize),
        new Check("start-return", "게임·점수판 왕복 시 이전 패널 제거와 시작 메뉴 초기화", RefactoringRegressionTest::startReturn),
        new Check("menu-navigation", "세 메뉴의 방향키 순환·강조 및 MENU/SCORE 실행", RefactoringRegressionTest::menuNavigation),
        new Check("menu-round-trip", "키 설정 반복 왕복 시 선택 유지·리스너 복원", RefactoringRegressionTest::menuRoundTrip),
        new Check("key-input-mode", "키 입력 대기 중 방향키·예약 키·중복 키 처리", RefactoringRegressionTest::keyInputMode),
        new Check("menu-capacity", "기존 메뉴 등록 상한 11개와 순환 범위", RefactoringRegressionTest::menuCapacity),
        new Check("start-menu-isolation", "시작 화면 인스턴스 간 선택 독립성", RefactoringRegressionTest::startMenuIsolation),
        new Check("setting-menu-isolation", "설정 화면 인스턴스 간 선택 독립성", RefactoringRegressionTest::settingMenuIsolation),
        new Check("key-menu-isolation", "키 변경 화면 인스턴스 간 선택 독립성", RefactoringRegressionTest::keyMenuIsolation),
        new Check("key-change", "중복 키 거부 → 키 변경 → 저장·재로드", RefactoringRegressionTest::keyChange),
        new Check("reset-settings", "사용자 설정을 기본값으로 복원·저장", RefactoringRegressionTest::resetSettings),
        new Check("unknown-setting", "모르는 설정을 무시하고 정상 설정 유지", RefactoringRegressionTest::unknownSetting),
        new Check("settings-isolation", "설정 인스턴스와 기본값 간 독립성", RefactoringRegressionTest::settingsIsolation),
        new Check("key-file-validation", "정상·손상·5키 파일의 기존 허용 및 거절 규칙", RefactoringRegressionTest::keyFileValidation),
        new Check("key-file-round-trip", "허용되는 키 문자열의 원형 유지와 여섯 키 재로드", RefactoringRegressionTest::keyFileRoundTrip),
        new Check("key-edit-staging", "키 편집·서브메뉴 저장·파일 저장의 시점 분리", RefactoringRegressionTest::keyEditStaging),
        new Check("setting-option-cycle", "창 크기·색상 전체 순환 및 저장·재로드", RefactoringRegressionTest::settingOptionCycle),
        new Check("setting-file-boundaries", "누락·빈 파일·알 수 없는 값·공백 처리", RefactoringRegressionTest::settingFileBoundaries),
        new Check("key-array-ownership", "설정 조회·저장 배열과 내부 값의 독립성", RefactoringRegressionTest::keyArrayOwnership),
        new Check("block-initialization", "7종 블록의 초기 모양 전체·색상", RefactoringRegressionTest::blockInitialization),
        new Check("block-rotation", "7종 블록의 칸 수·색·시계 방향 회전", RefactoringRegressionTest::blockRotation),
        new Check("preview-shapes", "불변 Piece 미리보기의 7종·4회전 문자·색", RefactoringRegressionTest::previewShapes),
        new Check("preview-replacement", "두 미리보기 인스턴스의 표시 독립성", RefactoringRegressionTest::previewReplacement),
        new Check("preview-direct", "미리보기의 Piece 사본 경계와 참조 비보유", RefactoringRegressionTest::previewDirect),
        new Check("preview-lifecycle", "이동·회전·pause 중 next 사본 유지", RefactoringRegressionTest::previewLifecycle),
        new Check("score-append-bytes", "점수 추가 저장의 UTF-8·줄바꿈·무개행 기존 기록 보존", RefactoringRegressionTest::scoreAppendBytes),
        new Check("score-file-boundaries", "누락·빈 파일·공백 기록 조회와 누락 파일 초기화", RefactoringRegressionTest::scoreFileBoundaries),
        new Check("score-parse-boundaries", "기존 점수 파싱·정수 경계·동점 순서·손상 뒤 정상 기록", RefactoringRegressionTest::scoreParseBoundaries),
        new Check("score-refresh", "기존 점수판의 저장·초기화·재저장 후 최신 기록 조회", RefactoringRegressionTest::scoreRefresh),
        new Check("score-io-diagnostics", "읽기·저장·초기화 실패의 진단과 원본 보존", RefactoringRegressionTest::scoreIoDiagnostics),
        new Check("score-store-direct", "화면 없는 점수 저장·전체 정렬·목록 독립성과 초기화", RefactoringRegressionTest::scoreStoreDirect),
        new Check("score-ranking", "손상된 기록 무시·내림차순·상위 10개", RefactoringRegressionTest::scoreRanking),
        new Check("score-reset", "설정 메뉴의 기록 초기화·다시 읽기", RefactoringRegressionTest::scoreReset)
    );
    public static void main(String[] args) throws Exception {
        // Windows console encoding can differ from the UTF-8 log reader.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (args.length == 0) {
            System.exit(runAll());
        } else if (args.length == 3 && args[0].equals("--case")) {
            Path sandbox = Path.of(args[2]).toRealPath();
            require(sandbox.equals(Path.of("").toRealPath()) && Files.isRegularFile(sandbox.resolve(MARKER)),
                "개별 검사는 실행기가 만든 임시 폴더에서만 실행할 수 있습니다.");
            Check check = CHECKS.stream().filter(item -> item.id().equals(args[1])).findFirst().orElseThrow();
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        check.action().run();
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                });
                System.exit(0);
            } catch (Throwable failure) {
                while (failure.getCause() != null &&
                        (failure instanceof InvocationTargetException || failure instanceof RuntimeException)) {
                    failure = failure.getCause();
                }
                failure.printStackTrace();
                System.exit(1);
            }
        } else {
            throw new IllegalArgumentException("인수 없이 실행하세요. --case는 내부 실행용입니다.");
        }
    }

    private static int runAll() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
            File.separatorChar == '\\' ? "java.exe" : "java").toString();
        String classpath = String.join(File.pathSeparator, Arrays.stream(
            System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
            .map(entry -> Path.of(entry).toAbsolutePath().toString()).toList());
        Path temporary = Files.createTempDirectory("tetris-refactoring-tests-");
        int failures = 0;
        try {
            for (Check check : CHECKS) {
                Path sandbox = Files.createDirectory(temporary.resolve(check.id()));
                Files.createFile(sandbox.resolve(MARKER));
                Path log = sandbox.resolve("result.log");
                List<String> command = new ArrayList<>();
                command.add(java);
                command.add("-Djava.awt.headless=true");
                String agent = System.getProperty("tetris.jacoco.agent");
                String destination = System.getProperty("tetris.jacoco.destination");
                if (agent != null && !agent.isBlank() && destination != null && !destination.isBlank()) {
                    command.add("-javaagent:" + agent + "=destfile=" + destination
                        + ",append=true,includes=seoultech.se.tetris.*");
                }
                command.add("-cp");
                command.add(classpath);
                command.add(COMPONENT);
                command.add("--case");
                command.add(check.id());
                command.add(sandbox.toString());
                Process child = new ProcessBuilder(command)
                    .directory(sandbox.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try {
                    if (!child.waitFor(20, TimeUnit.SECONDS)) {
                        child.destroyForcibly().waitFor();
                        failures++;
                        System.out.println("FAIL " + check.description() + " (20초 제한 초과)");
                    } else if (child.exitValue() != 0) {
                        failures++;
                        System.out.println("FAIL " + check.description());
                        System.out.print(Files.readString(log));
                    } else {
                        System.out.println("PASS " + check.description());
                    }
                } finally {
                    if (child.isAlive()) child.destroyForcibly().waitFor();
                }
            }
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.printf("RESULT %d/%d passed%n", CHECKS.size() - failures, CHECKS.size());
        return failures == 0 ? 0 : 1;
    }

    private static void frameSize() throws Exception {
        Maincontainer owner = owner("");
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        long widthOffset = unsafe.objectFieldOffset(java.awt.Component.class.getDeclaredField("width"));
        long heightOffset = unsafe.objectFieldOffset(java.awt.Component.class.getDeclaredField("height"));
        // 실제 창 없이 AWT의 실제 크기 필드에 테두리·제목 표시줄을 포함한 값을 준비한다.
        // 픽셀 도색은 별도의 실제 GUI 검증 대상이다.
        for (int[] size : new int[][] {{500, 600}, {625, 750}, {750, 900}}) {
            set(owner, "WIDTH", size[0]);
            set(owner, "HEIGHT", size[1]);
            unsafe.putInt(owner, widthOffset, size[0] + 8);
            unsafe.putInt(owner, heightOffset, size[1] + 28);
            require(owner.getWidth() == size[0] + 8, "창 너비는 설정값이 아닌 AWT의 실제 크기여야 합니다.");
            require(owner.getHeight() == size[1] + 28, "창 높이는 제목 표시줄을 포함한 실제 크기여야 합니다.");
            require(new Startpanel(owner).getPreferredSize().equals(new java.awt.Dimension(size[0], size[1])),
                "게임 화면의 설정 크기에 창 테두리·제목 표시줄 크기가 섞이면 안 됩니다.");
        }
    }

    private static void startReturn() throws Exception {
        Maincontainer owner = owner("");
        var layout = new java.awt.CardLayout();
        JPanel root = new JPanel(layout);
        set(owner, "cardLayout", layout);
        set(owner, "mainPanel", root);
        owner.enterStart();
        for (int i = 0; i < 3; i++) {
            Startpanel previous = (Startpanel) get(owner, "startPanel");
            press(previous, KeyEvent.VK_DOWN); // 복귀 시 이전 MENU 선택이 남으면 안 된다.
            owner.exitStartEnterScoreboard();
            JPanel scores = (JPanel) get(owner, "scoreboardPanel");
            owner.exitScoreboardEnterStart();
            require(root.getComponentCount() == 1 && scores.getParent() == null,
                "점수판 복귀 후 이전 패널이 남으면 안 됩니다.");
            require(get(owner, "startPanel") != previous, "시작 화면은 현재 정책대로 다시 생성해야 합니다.");

            Startpanel start = (Startpanel) get(owner, "startPanel");
            press(start, KeyEvent.VK_ENTER); // 새 메뉴의 기본 선택 CLASSIC이 실제 게임을 열어야 한다.
            Gamepanel game = (Gamepanel) get(owner, "gamePanel");
            require(game != null && game.getParent() == root, "복귀한 메뉴의 CLASSIC 입력으로 게임에 진입해야 합니다.");
            game.stopGame();
            owner.exitGameEnterStart();
            require(root.getComponentCount() == 1 && game.getParent() == null,
                "게임 복귀 후 이전 패널이 남으면 안 됩니다.");
            require(((JPanel) get(owner, "startPanel")).isVisible(), "복귀한 시작 화면이 표시돼야 합니다.");
        }
    }

    private static void menuNavigation() throws Exception {
        Maincontainer owner = owner("");
        var layout = new java.awt.CardLayout();
        JPanel root = new JPanel(layout);
        set(owner, "cardLayout", layout);
        set(owner, "mainPanel", root);
        owner.enterStart();
        Startpanel start = (Startpanel) get(owner, "startPanel");
        checkNavigation(start, menuButtons(start, START_LABELS));
        press(start, KeyEvent.VK_DOWN);
        press(start, KeyEvent.VK_DOWN);
        press(start, KeyEvent.VK_DOWN);
        press(start, KeyEvent.VK_ENTER);
        require(((JPanel) get(owner, "scoreboardPanel")).getParent() == root, "SCORE에서 Enter를 누르면 점수판이 열려야 합니다.");
        owner.exitScoreboardEnterStart();
        start = (Startpanel) get(owner, "startPanel");
        press(start, KeyEvent.VK_DOWN);
        press(start, KeyEvent.VK_DOWN);
        press(start, KeyEvent.VK_ENTER);
        Settingpanel setting = (Settingpanel) get(owner, "settingPanel");
        require(setting.getParent() == root, "MENU에서 Enter를 누르면 설정이 열려야 합니다.");
        setting.setSize(500, 600);
        checkNavigation(setting, menuButtons(setting, SETTING_LABELS));
        press(setting, KeyEvent.VK_DOWN);
        press(setting, KeyEvent.VK_ENTER);
        JPanel keys = keyPanel(setting);
        checkNavigation(keys, menuButtons(keys, KEY_LABELS));
    }

    private static void menuRoundTrip() throws Exception {
        Settingpanel setting = new Settingpanel(owner(""));
        setting.setSize(500, 600);
        List<JPanel> options = menuButtons(setting, SETTING_LABELS);
        press(setting, KeyEvent.VK_DOWN);
        for (int round = 0; round < 3; round++) {
            press(setting, KeyEvent.VK_ENTER);
            JPanel keys = keyPanel(setting);
            require(setting.getKeyListeners().length == 0 && keys.getKeyListeners().length == 1,
                "키 변경 중에는 상위 메뉴가 같은 입력을 처리하면 안 됩니다.");
            assertSelection(menuButtons(keys, KEY_LABELS), 0);
            press(keys, KeyEvent.VK_UP);
            press(keys, KeyEvent.VK_ENTER);
            require(keys.getParent() == null && setting.getKeyListeners().length == 1,
                "복귀하면 키 변경 창은 제거되고 상위 리스너는 하나여야 합니다.");
            assertSelection(options, 1);
        }
        press(setting, KeyEvent.VK_UP);
        assertSelection(options, 0);
    }

    private static void keyInputMode() throws Exception {
        Maincontainer owner = owner("");
        Settingpanel setting = new Settingpanel(owner);
        setting.setSize(500, 600);
        press(setting, KeyEvent.VK_DOWN);
        press(setting, KeyEvent.VK_ENTER);
        JPanel keys = keyPanel(setting);
        List<JPanel> options = menuButtons(keys, KEY_LABELS);
        press(keys, KeyEvent.VK_ENTER);
        press(keys, KeyEvent.VK_DOWN);
        assertSelection(options, 0);
        require(((Integer[]) get(keys, "currentKey"))[0] == KeyEvent.VK_UP && hasLabel(keys, "Please Enter an Unused Key"),
            "중복 방향키는 메뉴를 이동시키거나 기존 키를 변경하면 안 됩니다.");
        press(keys, KeyEvent.VK_V);
        for (int i = 0; i < 4; i++) press(keys, KeyEvent.VK_DOWN);
        assertSelection(options, 4);
        press(keys, KeyEvent.VK_ENTER);
        press(keys, KeyEvent.VK_ENTER);
        require(((Integer[]) get(keys, "currentKey"))[4] == KeyEvent.VK_P && hasLabel(keys, "Enter Is Reserved for Exit"),
            "PAUSE의 Enter 예약 규칙을 유지해야 합니다.");
        press(keys, KeyEvent.VK_P); // 현재와 같은 키는 허용하고 메뉴 이동 모드로 복귀.
        require(keys.getKeyListeners().length == 1 && !hasLabel(keys, "Press Key to Change"),
            "키 입력을 마치면 메뉴 리스너 하나만 복원해야 합니다.");
        press(keys, KeyEvent.VK_DOWN);
        assertSelection(options, 5);
        press(keys, KeyEvent.VK_ENTER);
        press(keys, KeyEvent.VK_RIGHT);
        assertSelection(options, 5);
        require(((Integer[]) get(keys, "currentKey"))[5] == KeyEvent.VK_SPACE,
            "하드드롭 키 변경 시에도 중복 키를 거부해야 합니다.");
        press(keys, KeyEvent.VK_B);
        press(keys, KeyEvent.VK_DOWN);
        press(keys, KeyEvent.VK_ENTER);
        String expected = DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=86").replace("HARD_DROP=32", "HARD_DROP=66");
        require(value(owner.setting, "keySetting").equals(expected), "선택한 Rotate·Hard Drop 키만 변경돼야 합니다.");
        owner.setting.exportSetting();
        require(value(owner.new Setting(), "keySetting").equals(expected), "여섯 키의 저장·재로드가 유지돼야 합니다.");
    }

    private static void startMenuIsolation() throws Exception {
        Maincontainer owner = owner("");
        var layout = new java.awt.CardLayout();
        JPanel root = new JPanel(layout);
        set(owner, "cardLayout", layout);
        set(owner, "mainPanel", root);
        Startpanel first = new Startpanel(owner);
        set(owner, "startPanel", first);
        root.add(first, "StartScreen");
        List<JPanel> firstButtons = menuButtons(first, START_LABELS);
        press(first, KeyEvent.VK_DOWN);
        Startpanel second = new Startpanel(owner);
        List<JPanel> secondButtons = menuButtons(second, START_LABELS);
        press(first, KeyEvent.VK_DOWN);
        press(first, KeyEvent.VK_DOWN);
        assertSelection(firstButtons, 3);
        assertSelection(secondButtons, 0);
        press(first, KeyEvent.VK_ENTER);
        require(((JPanel) get(owner, "scoreboardPanel")).getParent() == root, "첫 화면의 Enter가 첫 화면에서 선택한 SCORE를 실행해야 합니다.");
    }

    private static void settingMenuIsolation() throws Exception {
        Maincontainer owner = owner("");
        Settingpanel first = new Settingpanel(owner);
        List<JPanel> firstButtons = menuButtons(first, SETTING_LABELS);
        press(first, KeyEvent.VK_DOWN);
        Maincontainer secondOwner = owner("");
        Settingpanel second = new Settingpanel(secondOwner);
        List<JPanel> secondButtons = menuButtons(second, SETTING_LABELS);
        press(first, KeyEvent.VK_DOWN);
        assertSelection(firstButtons, 2);
        assertSelection(secondButtons, 0);
        press(first, KeyEvent.VK_UP);
        press(first, KeyEvent.VK_UP);
        press(first, KeyEvent.VK_ENTER);
        require(value(owner.setting, "windowSize").equals("750X625") && value(secondOwner.setting, "windowSize").equals("600X500"),
            "첫 화면의 Enter가 다른 화면의 설정을 바꾸면 안 됩니다.");
    }

    private static void keyMenuIsolation() throws Exception {
        Maincontainer owner = owner("");
        Settingpanel first = new Settingpanel(owner);
        first.setSize(500, 600);
        press(first, KeyEvent.VK_DOWN);
        press(first, KeyEvent.VK_ENTER);
        JPanel firstKeys = keyPanel(first);
        List<JPanel> firstButtons = menuButtons(firstKeys, KEY_LABELS);
        press(firstKeys, KeyEvent.VK_DOWN);
        press(firstKeys, KeyEvent.VK_DOWN);
        Settingpanel second = new Settingpanel(owner);
        second.setSize(500, 600);
        press(second, KeyEvent.VK_DOWN);
        press(second, KeyEvent.VK_ENTER);
        JPanel secondKeys = keyPanel(second);
        List<JPanel> secondButtons = menuButtons(secondKeys, KEY_LABELS);
        press(firstKeys, KeyEvent.VK_DOWN);
        assertSelection(firstButtons, 3);
        assertSelection(secondButtons, 0);
        press(firstKeys, KeyEvent.VK_ENTER);
        press(firstKeys, KeyEvent.VK_B);
        require(((Integer[]) get(firstKeys, "currentKey"))[3] == KeyEvent.VK_B
                && ((Integer[]) get(secondKeys, "currentKey"))[3] == KeyEvent.VK_RIGHT,
            "첫 키 변경 화면의 입력이 다른 키 변경 화면에 적용되면 안 됩니다.");
    }

    @SuppressWarnings("unchecked")
    private static void menuCapacity() throws Exception {
        Startpanel panel = new Startpanel(owner(""));
        JPanel parent = (JPanel) menuButtons(panel, START_LABELS).get(0).getParent();
        Class<?> type = Arrays.stream(Startpanel.class.getDeclaredClasses())
            .filter(item -> item.getSimpleName().equals("Button")).findFirst().orElseThrow();
        var constructor = type.getDeclaredConstructor(Startpanel.class, String.class);
        constructor.setAccessible(true);
        MenuButtons<JPanel> menu = (MenuButtons<JPanel>) get(panel, "menu");
        JPanel rejected = null;
        for (int index = 5; index < 13; index++) {
            JPanel button = (JPanel) constructor.newInstance(panel, "EXTRA " + index);
            menu.add(parent, button);
            rejected = button;
        }
        require(parent.getComponentCount() == 11 && rejected.getParent() == null,
            "기존 동작대로 11개를 등록하고 12번째부터 등록하지 않아야 합니다.");
        List<JPanel> buttons = Arrays.stream(parent.getComponents()).map(JPanel.class::cast).toList();
        assertSelection(buttons, 0);
        press(panel, KeyEvent.VK_UP);
        assertSelection(buttons, 10);
        press(panel, KeyEvent.VK_DOWN);
        assertSelection(buttons, 0);
    }

    private static void keyChange() throws Exception {
        Maincontainer owner = owner("");
        Settingpanel panel = new Settingpanel(owner);
        panel.setSize(500, 600);
        call(panel, "selectDown");
        call(panel, "selectEnter");
        JPanel keyPanel = Arrays.stream(panel.getComponents())
            .filter(item -> item.getClass().getSimpleName().equals("ChangeKeyPanel"))
            .map(JPanel.class::cast).findFirst().orElseThrow();
        call(keyPanel, "selectEnter");
        press(keyPanel, KeyEvent.VK_DOWN); // 기존 DOWN 키와 중복되는 입력은 거부해야 한다.
        press(keyPanel, KeyEvent.VK_A);
        call(keyPanel, "selectUp"); // 첫 항목에서 Save and Exit으로 순환.
        call(keyPanel, "selectEnter");
        String expected = DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=65");
        require(value(owner.setting, "keySetting").equals(expected), "변경하지 않은 키를 포함해 전체 저장 값이 일치해야 합니다.");
        owner.setting.exportSetting();
        require(value(owner.new Setting(), "keySetting").equals(expected), "다시 읽어도 키 설정이 유지되어야 합니다.");
        require(panel.getKeyListeners().length == 1, "키 설정 복귀 후 입력 리스너가 중복되지 않아야 합니다.");
    }

    private static void resetSettings() throws Exception {
        Maincontainer owner = owner("keySetting:" + CUSTOM_KEYS + "\nwindowSize:900X750\ncolorBlind:Tritanopia\n");
        require(value(owner.setting, "keySetting").equals(CUSTOM_KEYS), "초기화 전 사용자 키가 로드되어야 합니다.");
        Settingpanel panel = new Settingpanel(owner);
        for (int i = 0; i < 5; i++) call(panel, "selectDown");
        call(panel, "selectEnter"); // 실제 Reset setting 메뉴 실행.
        owner.setting.exportSetting();
        Maincontainer.Setting loaded = owner.new Setting();
        require(value(loaded, "keySetting").equals(DEFAULT_KEYS), "기본 키 복원 실패");
        require(value(loaded, "windowSize").equals("600X500"), "기본 창 크기 복원 실패");
        require(value(loaded, "colorBlind").equals("Normal"), "기본 색각 모드 복원 실패");
        require(value(loaded, "difficulty").equals("Easy"), "기본 난이도 복원 실패");
    }

    private static void unknownSetting() throws Exception {
        Maincontainer owner = owner("futureOption:anything\nwindowSize:900X750\ncolorBlind:Protanopia\nkeySetting:"
            + CUSTOM_KEYS + "\n");
        require(value(owner.setting, "windowSize").equals("900X750"), "정상 창 크기를 유지해야 합니다.");
        require(value(owner.setting, "colorBlind").equals("Protanopia"), "정상 색각 모드를 유지해야 합니다.");
        require(value(owner.setting, "keySetting").equals(CUSTOM_KEYS), "정상 키 설정을 유지해야 합니다.");
    }

    private static void settingsIsolation() throws Exception {
        Maincontainer owner = owner("");
        Maincontainer.Setting original = owner.setting;
        original.updateKeyCodes(new Integer[] {87, 40, 37, 39, 80, 32});
        Maincontainer.Setting other = owner.new Setting();
        require(value(other, "keySetting").equals(DEFAULT_KEYS), "다른 설정 인스턴스로 사용자 키가 전파되면 안 됩니다.");
        require(value(original, "keySetting").equals(CUSTOM_KEYS), "새 인스턴스가 기존 사용자 키를 바꾸면 안 됩니다.");
        original.clearSetting();
        require(value(original, "keySetting").equals(DEFAULT_KEYS), "기본값 목록이 오염되면 안 됩니다.");
    }

    private static void keyFileValidation() throws Exception {
        List<String> invalid = List.of(
            "", DEFAULT_KEYS.substring(0, DEFAULT_KEYS.lastIndexOf(',')),
            DEFAULT_KEYS + ",EXTRA=65", DEFAULT_KEYS + ",", DEFAULT_KEYS.replace(",DOWN", ",,DOWN"),
            DEFAULT_KEYS.replace("ROTATE=38", "TURN=38"),
            DEFAULT_KEYS.replace("ROTATE=38,DOWN=40", "DOWN=40,ROTATE=38"),
            DEFAULT_KEYS.replace("ROTATE=38", "ROTATE="),
            DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=38=65"),
            DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=abc"),
            DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=2147483648"),
            DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=0"),
            DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=-1"),
            DEFAULT_KEYS.replace("ROTATE=38", "ROTATE= 38"),
            DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=40"),
            DEFAULT_KEYS.replace("PAUSE=80", "PAUSE=10"));
        for (String keys : invalid) {
            Maincontainer owner = owner("windowSize:900X750\ncolorBlind:Tritanopia\nkeySetting:" + keys + "\n");
            require(value(owner.setting, "keySetting").equals(DEFAULT_KEYS), "잘못된 키 파일은 기본 키를 유지해야 합니다: " + keys);
            require(value(owner.setting, "windowSize").equals("900X750")
                    && value(owner.setting, "colorBlind").equals("Tritanopia"), "잘못된 키가 다른 정상 설정을 지우면 안 됩니다.");
        }
        Maincontainer owner = owner("keySetting:" + CUSTOM_KEYS + "\nkeySetting:broken\n");
        require(value(owner.setting, "keySetting").equals(CUSTOM_KEYS), "뒤의 잘못된 줄이 앞의 정상 키를 덮어쓰면 안 됩니다.");
    }

    private static void keyFileRoundTrip() throws Exception {
        for (String keys : List.of(CUSTOM_KEYS,
                "ROTATE=65,DOWN=83,LEFT=68,RIGHT=70,PAUSE=71,HARD_DROP=72",
                DEFAULT_KEYS.replace("HARD_DROP=32", "HARD_DROP=10"),
                DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=+0038"))) {
            Maincontainer owner = owner("keySetting:" + keys + "\n");
            require(value(owner.setting, "keySetting").equals(keys), "허용된 파일의 키 문자열을 임의로 변경하면 안 됩니다.");
            owner.setting.exportSetting();
            require(value(owner.new Setting(), "keySetting").equals(keys), "저장·재로드 후 허용된 키 문자열이 유지되어야 합니다.");
            Settingpanel settings = new Settingpanel(owner);
            press(settings, KeyEvent.VK_DOWN);
            press(settings, KeyEvent.VK_ENTER);
            Integer[] actual = (Integer[]) get(keyPanel(settings), "currentKey");
            String[] parts = keys.split(",");
            for (int i = 0; i < parts.length; i++) {
                require(actual[i] == Integer.parseInt(parts[i].split("=")[1]), "여섯 키의 순서와 번호를 보존해야 합니다.");
            }
        }
    }

    private static void keyEditStaging() throws Exception {
        Maincontainer owner = owner("keySetting:" + DEFAULT_KEYS + "\n");
        byte[] originalFile = Files.readAllBytes(Path.of("setting.txt"));
        Settingpanel settings = new Settingpanel(owner);
        press(settings, KeyEvent.VK_DOWN);
        press(settings, KeyEvent.VK_ENTER);
        JPanel keys = keyPanel(settings);
        press(keys, KeyEvent.VK_ENTER);
        press(keys, KeyEvent.VK_A);
        require(value(owner.setting, "keySetting").equals(DEFAULT_KEYS), "편집 중에는 저장된 키를 변경하면 안 됩니다.");
        Settingpanel another = new Settingpanel(owner);
        press(another, KeyEvent.VK_DOWN);
        press(another, KeyEvent.VK_ENTER);
        require(((Integer[]) get(keyPanel(another), "currentKey"))[0] == KeyEvent.VK_UP,
            "저장 전 새 화면에 미완료 편집이 전파되면 안 됩니다.");
        press(keys, KeyEvent.VK_UP);
        press(keys, KeyEvent.VK_ENTER);
        String expected = DEFAULT_KEYS.replace("ROTATE=38", "ROTATE=65");
        require(value(owner.setting, "keySetting").equals(expected), "키 서브메뉴 저장은 메모리 설정을 갱신해야 합니다.");
        require(Arrays.equals(originalFile, Files.readAllBytes(Path.of("setting.txt"))), "상위 메뉴 저장 전에는 파일을 변경하면 안 됩니다.");
        require(value(owner.new Setting(), "keySetting").equals(DEFAULT_KEYS), "파일 저장 전 재로드는 원래 키여야 합니다.");
        owner.setting.exportSetting();
        require(value(owner.new Setting(), "keySetting").equals(expected), "파일 저장 후에는 변경한 키를 읽어야 합니다.");
    }

    private static void settingOptionCycle() throws Exception {
        Maincontainer owner = owner("");
        Settingpanel settings = new Settingpanel(owner);
        for (String size : List.of("750X625", "900X750", "600X500", "750X625")) {
            press(settings, KeyEvent.VK_ENTER);
            require(value(owner.setting, "windowSize").equals(size) && hasLabel(settings, size), "창 크기 순서와 표시가 같아야 합니다.");
        }
        for (int i = 0; i < 3; i++) press(settings, KeyEvent.VK_DOWN);
        for (String color : List.of("Protanopia", "Deuteranopia", "Tritanopia", "Normal", "Protanopia")) {
            press(settings, KeyEvent.VK_ENTER);
            require(value(owner.setting, "colorBlind").equals(color) && hasLabel(settings, color), "색상 순서와 표시가 같아야 합니다.");
        }
        require(Files.readString(Path.of("setting.txt")).isEmpty(), "선택만 바꿔서는 파일을 저장하면 안 됩니다.");
        owner.setting.exportSetting();
        Maincontainer.Setting loaded = owner.new Setting();
        require(value(loaded, "windowSize").equals("750X625") && value(loaded, "colorBlind").equals("Protanopia"),
            "변경한 창 크기·색상을 저장하고 다시 읽어야 합니다.");
    }

    private static void settingFileBoundaries() throws Exception {
        Maincontainer owner = owner("");
        Files.delete(Path.of("setting.txt"));
        require(value(owner.new Setting(), "keySetting").equals(DEFAULT_KEYS), "파일이 없으면 기본 키여야 합니다.");
        owner = owner("\ninvalid line\nwindowSize:bad\ncolorBlind:Unknown\nfuture:value\n");
        require(value(owner.setting, "windowSize").equals("600X500") && value(owner.setting, "colorBlind").equals("Normal"),
            "알 수 없는 값은 기본 설정을 유지해야 합니다.");
        owner = owner("  windowSize : 900X750  \n  colorBlind : Tritanopia  \n keySetting : " + CUSTOM_KEYS + "  \n");
        require(value(owner.setting, "windowSize").equals("900X750") && value(owner.setting, "colorBlind").equals("Tritanopia")
                && value(owner.setting, "keySetting").equals(CUSTOM_KEYS), "줄 앞뒤와 콜론 주변 공백은 기존처럼 허용해야 합니다.");
    }

    private static void keyArrayOwnership() throws Exception {
        Maincontainer owner = owner("");
        Integer[] first = owner.setting.getKeyCodes();
        Integer[] second = owner.setting.getKeyCodes();
        require(first != second && Arrays.equals(first, second), "조회마다 독립된 편집 배열이어야 합니다.");
        first[0] = KeyEvent.VK_W;
        require(second[0] == KeyEvent.VK_UP && value(owner.setting, "keySetting").equals(DEFAULT_KEYS),
            "반환 배열 변경이 다른 배열이나 저장된 설정에 전파되면 안 됩니다.");
        owner.setting.updateKeyCodes(first);
        first[0] = KeyEvent.VK_A;
        require(value(owner.setting, "keySetting").equals(CUSTOM_KEYS) && owner.setting.getKeyCodes()[0] == KeyEvent.VK_W,
            "저장에 전달한 배열을 나중에 바꿔도 설정값이 바뀌면 안 됩니다.");
    }

    private static void blockInitialization() {
        record Expected(Block block, String shape, java.awt.Color color) {}
        for (Expected expected : List.of(
                new Expected(new IBlock(), "[[1, 1, 1, 1]]", java.awt.Color.CYAN),
                new Expected(new JBlock(), "[[1, 1, 1], [0, 0, 1]]", java.awt.Color.BLUE),
                new Expected(new LBlock(), "[[1, 1, 1], [1, 0, 0]]", java.awt.Color.ORANGE),
                new Expected(new OBlock(), "[[1, 1], [1, 1]]", java.awt.Color.YELLOW),
                new Expected(new SBlock(), "[[0, 1, 1], [1, 1, 0]]", java.awt.Color.GREEN),
                new Expected(new TBlock(), "[[0, 1, 0], [1, 1, 1]]", java.awt.Color.MAGENTA),
                new Expected(new ZBlock(), "[[1, 1, 0], [0, 1, 1]]", java.awt.Color.RED))) {
            Block block = expected.block();
            String name = block.getClass().getSimpleName();
            require(expected.shape().equals(shape(block)), name + "의 초기 모양이 바뀌면 안 됩니다.");
            require(expected.color().equals(block.getColor()), name + "의 초기 색이 바뀌면 안 됩니다.");
        }
    }

    private static void blockRotation() {
        for (Block block : blocks()) {
            String initial = shape(block);
            var color = block.getColor();
            for (int rotation = 0; rotation < 4; rotation++) {
                int occupied = 0;
                for (int y = 0; y < block.height(); y++) {
                    for (int x = 0; x < block.width(); x++) occupied += block.getShape(x, y);
                }
                require(occupied == 4, "각 블록은 회전 후에도 네 칸이어야 합니다.");
                require(color.equals(block.getColor()), "회전으로 색이 바뀌면 안 됩니다.");
                block.rotate();
            }
            require(shape(block).equals(initial), "네 번 회전하면 원래 모양으로 돌아와야 합니다.");
        }
        Block t = new TBlock();
        t.rotate();
        require(shape(t).equals("[[1, 0], [1, 1], [1, 0]]"), "T블록이 시계 방향으로 회전해야 합니다.");
    }

    private static void previewShapes() throws Exception {
        Nextblockpanel panel = new Nextblockpanel();
        for (Block block : blocks()) {
            for (int rotation = 0; rotation < 4; rotation++) {
                var piece = piece(block);
                assertPieceMatchesBlock(piece, block);
                panel.drawNextBoard(piece);
                assertPreview(panel, piece);
                block.rotate();
            }
        }
    }

    private static void previewReplacement() throws Exception {
        Nextblockpanel first = new Nextblockpanel();
        Nextblockpanel second = new Nextblockpanel();
        var secondPiece = piece(new TBlock());
        second.drawNextBoard(secondPiece);
        for (Block block : List.of(new IBlock(), new OBlock(), new TBlock())) {
            var current = piece(block);
            first.drawNextBoard(current);
            assertPreview(first, current);
            require(second.piece() == secondPiece,
                "다른 미리보기의 표시를 바꾸면 안 됩니다.");
            assertPreview(second, secondPiece);
        }
    }

    private static void previewDirect() throws Exception {
        Nextblockpanel panel = new Nextblockpanel();
        require(panel.piece() == null, "초기 미리보기는 비어 있어야 합니다.");
        Block mutable = new LBlock();
        var copied = piece(mutable);
        panel.drawNextBoard(copied);
        var before = panel.piece();
        mutable.rotate();
        require(panel.piece() == before,
            "가변 원본 변경은 이미 그린 Piece에 영향을 주면 안 됩니다.");
        assertPreview(panel, copied);
        for (Field field : Nextblockpanel.class.getDeclaredFields()) {
            require(!Gamepanel.class.isAssignableFrom(field.getType()) && !Block.class.isAssignableFrom(field.getType()),
                "미리보기는 Gamepanel이나 변경 가능한 Block을 필드로 보유하면 안 됩니다.");
        }
    }

    private static void previewLifecycle() throws Exception {
        var source = new seoultech.se.tetris.game.BlockSource() {
            int index;
            final Block[] blocks = {new TBlock(), new OBlock()};
            public Block nextBlock(seoultech.se.tetris.Difficulty ignored) { return blocks[index++]; }
            public Block nextItemBlock() { throw new AssertionError("Classic source"); }
        };
        var engine = new seoultech.se.tetris.game.GameEngine(
            new seoultech.se.tetris.game.GameConfig(seoultech.se.tetris.GameMode.CLASSIC,
                seoultech.se.tetris.Difficulty.NORMAL), source);
        var started = engine.start().snapshot();
        var next = started.next();
        engine.handle(seoultech.se.tetris.game.GameCommand.LEFT);
        engine.handle(seoultech.se.tetris.game.GameCommand.RIGHT);
        engine.handle(seoultech.se.tetris.game.GameCommand.ROTATE);
        engine.handle(seoultech.se.tetris.game.GameCommand.SOFT_DROP);
        engine.handle(seoultech.se.tetris.game.GameCommand.TOGGLE_PAUSE);
        require(engine.snapshot().next().equals(next),
            "이동·회전·낙하·pause는 next Piece를 갱신하면 안 됩니다.");
    }

    private static seoultech.se.tetris.game.GameSnapshot.Piece piece(Block block) {
        var source = new seoultech.se.tetris.game.BlockSource() {
            int index;
            final Block[] blocks = {block, new OBlock()};
            public Block nextBlock(seoultech.se.tetris.Difficulty ignored) { return blocks[index++]; }
            public Block nextItemBlock() { throw new AssertionError("Classic source"); }
        };
        return new seoultech.se.tetris.game.GameEngine(
            new seoultech.se.tetris.game.GameConfig(seoultech.se.tetris.GameMode.CLASSIC,
                seoultech.se.tetris.Difficulty.NORMAL), source).start().snapshot().current();
    }

    private static void assertPreview(
            Nextblockpanel panel, seoultech.se.tetris.game.GameSnapshot.Piece piece) {
        require(panel.piece() == piece,
            "미리보기는 전달받은 불변 Piece 사본을 그대로 표시해야 합니다.");
        require(panel.piece().cells().equals(piece.cells()),
            "미리보기 셀은 전달된 Piece 모양 전체와 같아야 합니다.");
    }

    private static void assertPieceMatchesBlock(
            seoultech.se.tetris.game.GameSnapshot.Piece piece, Block block) {
        require(piece.cells().size() == block.height(), "Piece 높이가 원본 블록과 같아야 합니다.");
        for (int row = 0; row < block.height(); row++) {
            require(piece.cells().get(row).size() == block.width(),
                "Piece 너비가 원본 블록과 같아야 합니다.");
            for (int column = 0; column < block.width(); column++) {
                var cell = piece.cells().get(row).get(column);
                if (block.getShape(column, row) == 0) {
                    require(cell == null, "원본 블록의 빈 칸은 Piece에서도 비어야 합니다.");
                } else {
                    require(cell != null && cell.value() == block.getShape(column, row)
                            && cell.text().equals(block.getText(column, row))
                            && cell.argb() == block.getColor().getRGB(),
                        "Piece의 값·문자·색은 원본 블록과 같아야 합니다.");
                }
            }
        }
    }

    private static void scoreRanking() throws Exception {
        StringBuilder content = new StringBuilder("Broken:no-number\n");
        for (int i = 0; i < 12; i++) content.append("P").append(i).append(":").append(i * 10).append('\n');
        Maincontainer owner = owner("");
        Files.writeString(Path.of("tetris_score.txt"), content);
        Scoreboardpanel panel = new Scoreboardpanel(owner);
        String text = scoreText(panel);
        require(text.lines().count() == 10, "상위 열 개 기록을 표시해야 합니다.");
        for (int i = 0; i < 10; i++) {
            String row = text.lines().toList().get(i);
            int score = 11 - i;
            require(row.equals((i + 1) + ".\tP" + score + ": " + score * 10), "내림차순 순위가 맞아야 합니다: " + row);
        }
        panel.drawScore();
        require(scoreText(panel).equals(text), "손상된 기록이 있어도 재조회 결과가 같아야 합니다.");
    }

    private static void saveScore(String name, int score) {
        ScoreStore.append(name, score, seoultech.se.tetris.Difficulty.NORMAL,
            seoultech.se.tetris.GameMode.CLASSIC);
    }

    private static String rankingRows(String... entries) {
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 10; i++) expected.append(i + 1).append(".\t")
            .append(i < entries.length ? entries[i] : "###: 0").append('\n');
        return expected.toString();
    }

    private static void resetViaMenu(Maincontainer owner) throws Exception {
        Settingpanel panel = new Settingpanel(owner);
        call(panel, "selectDown");
        call(panel, "selectDown");
        call(panel, "selectEnter");
    }

    private static String captureErrors(CheckedRunnable action) throws Exception {
        java.io.PrintStream previous = System.err;
        var bytes = new java.io.ByteArrayOutputStream();
        try (var capture = new java.io.PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setErr(capture);
            try { action.run(); } finally { System.setErr(previous); }
        }
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void scoreAppendBytes() throws Exception {
        Path file = Path.of("tetris_score.txt");
        String newline = System.lineSeparator();
        try (GameFixture fixture = new GameFixture()) {
            saveScore("한글", Integer.MIN_VALUE);
            saveScore("B", Integer.MAX_VALUE);
            require(Files.readString(file).equals("한글:-2147483648:Normal:Classic" + newline + "B:2147483647:Normal:Classic" + newline),
                "R2-14 새 게임 저장은 UTF-8·이름:점수:난이도:모드·플랫폼 줄바꿈을 유지해야 합니다.");
            Files.writeString(file, "Legacy:1");
            saveScore("Next", 2);
            require(Files.readString(file).equals("Legacy:1" + newline + "Next:2:Normal:Classic" + newline),
                "무개행 기존 기록과 새 기록 사이에 줄바꿈을 보충해 기존 점수를 보존해야 합니다.");
            saveScore("Fresh", 3);
            require(scoreText(new Scoreboardpanel(fixture.owner)).equals(rankingRows("Fresh: 3 [Normal] [Classic]", "Next: 2 [Normal] [Classic]", "Legacy: 1")),
                "기존 무개행 기록과 난이도가 포함된 새 기록을 모두 읽을 수 있어야 합니다.");
        }
    }

    private static void scoreFileBoundaries() throws Exception {
        Path file = Path.of("tetris_score.txt");
        Maincontainer owner = owner("");
        require(!Files.exists(file), "누락 파일 시나리오는 점수 파일 없이 시작해야 합니다.");
        Scoreboardpanel panel = new Scoreboardpanel(owner);
        require(scoreText(panel).equals(rankingRows()) && !Files.exists(file),
            "누락 파일 조회는 빈 10순위를 표시하고 파일을 만들지 않아야 합니다.");
        resetViaMenu(owner);
        require(Files.isRegularFile(file) && Files.size(file) == 0, "누락 파일 초기화는 빈 파일을 생성해야 합니다.");
        for (String content : List.of("", "\n \t\n\r\n")) {
            Files.writeString(file, content);
            panel.drawScore();
            require(scoreText(panel).equals(rankingRows()) && Files.readString(file).equals(content),
                "빈 파일·공백 행은 기록이 없고 조회가 파일을 바꾸면 안 됩니다.");
        }
    }

    private static void scoreParseBoundaries() throws Exception {
        Path file = Path.of("tetris_score.txt");
        String content = "\n \t\ninvalid\nBad:not-a-number\nOverflow:2147483648\nUnderflow:-2147483649\n"
            + "Extra:1:2\nMissing:\n: \n Top : +2147483647 \nFirst:50\nSecond:50\nThird:50\n"
            + "Tail:20::\n홍길동:12\n:7\nZero:0\nNegative:-10\nMin:-2147483648\n";
        Files.writeString(file, content);
        Maincontainer owner = owner("");
        String diagnostics = captureErrors(() -> {
            Scoreboardpanel panel = new Scoreboardpanel(owner);
            require(scoreText(panel).equals(rankingRows("Top: 2147483647", "First: 50", "Second: 50", "Third: 50",
                    "Tail: 20", "홍길동: 12", ": 7", "Zero: 0", "Negative: -10", "Min: -2147483648")),
                "trim·split·정수 경계·동점 파일 순서와 기존 허용값을 유지해야 합니다.");
        });
        require(diagnostics.contains("NumberFormatException"), "손상 숫자 행은 진단을 남기고 다음 정상 행을 읽어야 합니다.");
        require(Files.readString(file).equals(content), "기록 조회가 원본 파일을 정리하거나 변경하면 안 됩니다.");
    }

    private static void scoreRefresh() throws Exception {
        Path file = Path.of("tetris_score.txt");
        Files.writeString(file, "Alice:200\nBob:100\n");
        try (GameFixture fixture = new GameFixture()) {
            Scoreboardpanel panel = new Scoreboardpanel(fixture.owner);
            saveScore("New", 300);
            panel.drawScore();
            require(scoreText(panel).equals(rankingRows("New: 300 [Normal] [Classic]", "Alice: 200", "Bob: 100")), "기존 점수판도 새 저장 기록을 다시 읽어야 합니다.");
            resetViaMenu(fixture.owner);
            panel.drawScore();
            require(Files.size(file) == 0 && scoreText(panel).equals(rankingRows()), "초기화 후 이전 목록이 캐시에 남으면 안 됩니다.");
            saveScore("After", 7);
            panel.drawScore();
            require(scoreText(panel).equals(rankingRows("After: 7 [Normal] [Classic]")), "초기화 후 새 기록만 다시 저장·조회할 수 있어야 합니다.");
        }
    }

    private static void scoreIoDiagnostics() throws Exception {
        Path file = Path.of("tetris_score.txt"), marker = file.resolve("keep.txt");
        Files.createDirectory(file);
        Files.writeString(marker, "untouched");
        try (GameFixture fixture = new GameFixture()) {
            String read = captureErrors(() -> require(scoreText(new Scoreboardpanel(fixture.owner)).equals(rankingRows()),
                "읽기 실패는 기존처럼 빈 순위를 표시해야 합니다."));
            String append = captureErrors(() -> saveScore("Failed", 1));
            String clear = captureErrors(() -> resetViaMenu(fixture.owner));
            for (String diagnostics : List.of(read, append, clear)) require(diagnostics.contains("Exception"),
                "파일 IO 실패는 stacktrace 진단을 남겨야 합니다.");
            require(Files.isDirectory(file) && Files.readString(marker).equals("untouched"), "실패 시 기존 디렉터리와 내용을 삭제하면 안 됩니다.");
            Files.delete(marker);
            Files.delete(file);
            byte[] invalid = {(byte) 0xc3, 0x28};
            Files.write(file, invalid);
            String encoding = captureErrors(() -> require(scoreText(new Scoreboardpanel(fixture.owner)).equals(rankingRows()),
                "손상된 UTF-8도 기존 읽기 실패 경로를 유지해야 합니다."));
            require(encoding.contains("MalformedInputException") && Arrays.equals(Files.readAllBytes(file), invalid),
                "인코딩 오류 진단을 유지하고 원본 바이트를 변경하면 안 됩니다.");
        }
    }

    private static void scoreStoreDirect() throws Exception {
        Path file = Path.of("tetris_score.txt");
        require(ScoreStore.readRanked().isEmpty() && !Files.exists(file), "저장소 직접 조회도 누락 파일을 만들면 안 됩니다.");
        for (int i = 0; i < 12; i++) ScoreStore.append("P" + i, i * 10);
        List<ScoreStore.Score> first = ScoreStore.readRanked();
        require(first.size() == 12 && first.get(0).equals(new ScoreStore.Score("P11", 110))
                && first.get(11).equals(new ScoreStore.Score("P0", 0)), "저장소는 상위 10개 표시와 독립적으로 전체 정상 기록을 정렬해야 합니다.");
        first.clear();
        require(ScoreStore.readRanked().size() == 12, "반환 목록 변경이 파일이나 다음 조회를 바꾸면 안 됩니다.");
        ScoreStore.clear();
        require(Files.size(file) == 0 && ScoreStore.readRanked().isEmpty(), "직접 초기화도 파일을 비우고 최신 결과를 반환해야 합니다.");
        ScoreStore.append("After", 5);
        require(ScoreStore.readRanked().equals(List.of(new ScoreStore.Score("After", 5))), "초기화 후 저장에도 과거 기록이 남으면 안 됩니다.");
    }

    private static void scoreReset() throws Exception {
        Maincontainer owner = owner("");
        Files.writeString(Path.of("tetris_score.txt"), "Alice:200\nBob:100\n");
        Settingpanel panel = new Settingpanel(owner);
        call(panel, "selectDown");
        call(panel, "selectDown");
        call(panel, "selectEnter");
        require(Files.size(Path.of("tetris_score.txt")) == 0, "초기화한 점수 파일은 비어 있어야 합니다.");
        String text = scoreText(new Scoreboardpanel(owner));
        require(!text.contains("Alice") && !text.contains("Bob") && text.lines().count() == 10, "초기화 후 재조회하면 기존 기록 없이 빈 순위를 표시해야 합니다.");
    }

    // 아래는 현재 결합된 UI를 테스트하기 위한 준비/호출 어댑터다.
    private static Maincontainer owner(String settings) throws Exception {
        Files.writeString(Path.of("setting.txt"), settings);
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Maincontainer owner = (Maincontainer) ((Unsafe) field.get(null)).allocateInstance(Maincontainer.class);
        owner.setting = owner.new Setting();
        return owner;
    }

    private static final class GameFixture implements AutoCloseable {
        final Maincontainer owner;

        GameFixture() throws Exception {
            owner = owner("");
        }

        @Override public void close() {}
    }

    private static String value(Maincontainer.Setting setting, String name) throws Exception {
        java.util.Map<?, ?> values = (java.util.Map<?, ?>) get(setting, "currentSettingMap");
        return (String) call(values.get(name), "getCurrentSetting");
    }
    private static String scoreText(Scoreboardpanel panel) throws Exception { return ((JTextPane) get(panel, "scorepanel")).getText(); }

    private static List<Block> blocks() { return List.of(new IBlock(), new JBlock(), new LBlock(), new OBlock(), new SBlock(), new TBlock(), new ZBlock()); }

    private static String shape(Block block) {
        int[][] cells = new int[block.height()][block.width()];
        for (int y = 0; y < block.height(); y++) for (int x = 0; x < block.width(); x++) cells[y][x] = block.getShape(x, y);
        return Arrays.deepToString(cells);
    }

    private static JPanel keyPanel(Settingpanel setting) {
        return Arrays.stream(setting.getComponents())
            .filter(item -> item.getClass().getSimpleName().equals("ChangeKeyPanel"))
            .map(JPanel.class::cast).findFirst().orElseThrow();
    }

    private static List<JPanel> menuButtons(Container root, String... labels) {
        List<JPanel> buttons = new ArrayList<>();
        for (String label : labels) {
            JPanel button = findMenuButton(root, label);
            require(button != null, "메뉴 항목을 찾을 수 없습니다: " + label);
            buttons.add(button);
        }
        return buttons;
    }

    private static JPanel findMenuButton(Container root, String label) {
        for (var child : root.getComponents()) {
            if (child instanceof JPanel panel) {
                if (Arrays.stream(panel.getComponents()).anyMatch(item -> item instanceof JLabel text && label.equals(text.getText()))) return panel;
                JPanel found = findMenuButton(panel, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasLabel(Container root, String label) {
        for (var child : root.getComponents()) {
            if (child instanceof JLabel text && label.equals(text.getText())) return true;
            if (child instanceof Container container && hasLabel(container, label)) return true;
        }
        return false;
    }

    private static void assertSelection(List<JPanel> buttons, int selected) {
        for (int index = 0; index < buttons.size(); index++) {
            require(buttons.get(index).isOpaque() == (index == selected),
                "선택 강조는 항목 " + selected + "에만 있어야 합니다(검사 항목 " + index + ").");
        }
    }

    private static void checkNavigation(JPanel panel, List<JPanel> buttons) {
        assertSelection(buttons, 0);
        press(panel, KeyEvent.VK_UP);
        assertSelection(buttons, buttons.size() - 1);
        press(panel, KeyEvent.VK_DOWN);
        assertSelection(buttons, 0);
        for (int index = 1; index <= buttons.size(); index++) {
            press(panel, KeyEvent.VK_DOWN);
            assertSelection(buttons, index % buttons.size());
        }
    }

    private static void press(JPanel target, int code) {
        KeyEvent event = new KeyEvent(target, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : target.getKeyListeners()) listener.keyPressed(event);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
