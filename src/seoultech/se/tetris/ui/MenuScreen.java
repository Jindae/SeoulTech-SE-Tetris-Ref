package seoultech.se.tetris.ui;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class MenuScreen implements Screen {
    private final AppFrame app;

    private static class MenuItem {
        String label; Runnable action;
        
        // MenuItem을 리스트로 관리 => 새 항목 추가가 쉬움
        MenuItem(String l, Runnable a) { label = l; action = a; }
    }
    private final List<MenuItem> items = new ArrayList<>();
    
    // idx로 현재 선택 인덱스 추적
    private int idx = 0;

    // MenuScreen 객체가 처음 만들어질 때 초기 상태를 세팅한 것
    public MenuScreen(AppFrame app) {
        this.app = app;

        // itmes.add => 메뉴 항목을 리스트에 등록
        items.add(new MenuItem("게임 시작", () -> {
            // TODO: GameScreen 연결 (다음 팀/단계)
        }));
        items.add(new MenuItem("설정", () -> {
            // 아직 PR3에서 구현 예정
            System.out.println("[설정] 화면은 다음 PR에서 추가됩니다.");
        }));
        items.add(new MenuItem("스코어보드", () -> {
            // TODO: 추후 구현
            System.out.println("[스코어보드] 화면은 추후 추가됩니다.");
        }));
        items.add(new MenuItem("종료", () -> System.exit(0)));
    }

    // 매 프레임 화면을 그리는 함수. repaint() 이후 Swing이 호출함
    @Override public void render(Graphics2D g2) {
        // setRenderingHint => 텍스트 안티앨리어싱(부드럽게 표시)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // 글씨 가로 중앙 배치하기위해서 프레임 너비 가져옴
        int w = app.getWidth();

        g2.setFont(new Font("SansSerif", Font.BOLD, 36));
        String title = "TETRIS";
        g2.drawString(title, (w - g2.getFontMetrics().stringWidth(title))/2, 140);

        // 제목 그리기
        g2.setFont(new Font("SansSerif", Font.PLAIN, 22));
        int y = 220;
        for (int i=0;i<items.size();i++){
            String t = (i==idx?"> ":"  ") + items.get(i).label;
            g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t))/2, y);
            y += 40;
        }

        // 메뉴 항목 그리기
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        String hint = "↑/↓ 이동   Enter 선택   Esc 종료   (설정은 다음 PR)";
        g2.drawString(hint, (w - g2.getFontMetrics().stringWidth(hint))/2, app.getHeight()-60);
    }

    @Override public void onKeyPressed(KeyEvent e) {
        switch (e.getKeyCode()){
            // UP, DOWN => idx 변경(원형 이동)
            case KeyEvent.VK_UP -> idx = (idx - 1 + items.size()) % items.size();
            case KeyEvent.VK_DOWN -> idx = (idx + 1) % items.size();
            
            // 현재 항목의 action.run() 실행
            case KeyEvent.VK_ENTER -> items.get(idx).action.run();
            
            // System.exit(0) 종료
            case KeyEvent.VK_ESCAPE -> System.exit(0);
        }
    }
}
