// 화면 공통 인터페이스
package seoultech.se.tetris.ui;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

public interface Screen {
    // 화면 그리기
    void render(Graphics2D g2);

    // 키 입력 처리
    void onKeyPressed(KeyEvent e);

    // 화면 전환 시 훅 (상태 초기화, 타이머 시작/정지 등에 사용)
    default void onShow() {}
    default void onHide() {}
}
