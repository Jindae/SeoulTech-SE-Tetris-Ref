// 메인 윈도우 + 화면 전환기
package seoultech.se.tetris.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class AppFrame extends JFrame {
    private Screen current;

    public AppFrame() {
        setTitle("TETRIS");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(480, 720);
        setLocationRelativeTo(null);
        setResizable(false);

        // keyPressed 발생 시 current.onKeyPressed(e) 호출
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (current != null) current.onKeyPressed(e);
                repaint();
            }
        });

        showScreen(new MenuScreen(this));
    }

    // 이전 화면 onHide() -> 새 화면 대입 -> 새 화면 onShow() -> repaint()
    public void showScreen(Screen next) {
        if (current != null) current.onHide();
        current = next;
        current.onShow();

        // Swing 내부에 "이 영역을 다시 그려야 한다"는 이벤트를 등록
        repaint();
    }

    // 오버라이드 -> current.render() 호출해서 화면을 그리게 함
    @Override public void paint(Graphics g) {
        super.paint(g);
        if (current != null) current.render((Graphics2D) g);
    }
}
