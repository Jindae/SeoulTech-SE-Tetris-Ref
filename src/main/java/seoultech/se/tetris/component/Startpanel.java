package seoultech.se.tetris.component;

import javax.swing.*;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class Startpanel extends JPanel{

    private KeyListener playerKeyListener;
    private Maincontainer maincontainer;
    private int width;
    private int height;
    private final MenuButtons<Button> menu = new MenuButtons<>(new Insets(2, 2, 2, 2));

    public Startpanel(Maincontainer maincontainer) {
        width = maincontainer.getConfiguredWidth(); height = maincontainer.getConfiguredHeight();
        this.maincontainer = maincontainer;
        setPreferredSize(new Dimension(width, height));
		setLayout(new GridBagLayout()); // 컴포넌트 간의 배치를 자유롭게 하기 위해서 GridBagLayout을 사용
		setBackground(changeColor(Color.LIGHT_GRAY)); // background color로 light gray
		setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // padding 값 조절, 필요 시 제거 가능

		// Title 컴포넌트 만들기
        JLabel titlelabel = new JLabel("TETRIS", SwingConstants.CENTER);
        titlelabel.setFont(new Font("Courier", Font.BOLD, 56));
        titlelabel.setForeground(changeColor(Color.WHITE));

        // Title 컴포넌트 위치를 위한 제약사항
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; // grid[0][0]에 위치
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0; gbc.weighty = 0.3; // 수직 화면의 30% 차지하게 조정

        add(titlelabel, gbc);  // 타이틀은 위쪽에

        // Button 컴포넌트 만들기
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout());
        buttonPanel.setOpaque(false); // 투명 배경
        buttonPanel.setPreferredSize(new Dimension(200, 250)); // 컴포넌트 사이즈 지정

        // Button 4개 추가하기
        Button startButton = new Button("CLASSIC") {
            @Override
            protected void isPressed() {
                Startpanel.this.maincontainer.exitStartEnterGame("Classic");
            }
        };
        menu.add(buttonPanel, startButton);
        Button itemButton = new Button("ITEM") {
            @Override
            protected void isPressed() {
                Startpanel.this.maincontainer.exitStartEnterGame("Item");
            }
        };
        menu.add(buttonPanel, itemButton);
        Button timeLimitButton = new Button("TIME LIMIT (TEMP)") {
            @Override
            protected void isPressed() {
                Startpanel.this.maincontainer.exitStartEnterGame("Time Limit");
            }
        };
        menu.add(buttonPanel, timeLimitButton);
        Button menuButton = new Button("MENU") {
            @Override
            protected void isPressed() {
                Startpanel.this.maincontainer.exitStartEnterSetting();
            }
        };
        menu.add(buttonPanel, menuButton);
        Button scoreboardButton = new Button("SCORE") {
            @Override
            protected void isPressed() {
                Startpanel.this.maincontainer.exitStartEnterScoreboard();
            }
        };
        menu.add(buttonPanel, scoreboardButton);
        Button exitButton = new Button("EXIT") {
            @Override 
            protected void isPressed() {
                System.exit(0);
            }
        };
        menu.add(buttonPanel, exitButton);

        // Button 컴포넌트 위치를 위한 제약사항
        gbc.gridy = 1; // grid[0][1]에 위치
        gbc.weighty = 0.7; // 수직 화면의 70% 차지하게 조정
        gbc.insets = new Insets(10, 10, 10, 10);

        add(buttonPanel, gbc); // 버튼들은 중앙에

        playerKeyListener = new PlayerKeyListener();
		addKeyListener(playerKeyListener);
		setFocusable(true);
		requestFocus();
    }
    
    private class PlayerKeyListener implements KeyListener {
		@Override
		public void keyTyped(KeyEvent e) {}

		@Override
		public void keyPressed(KeyEvent e) {
			switch(e.getKeyCode()) {
			case KeyEvent.VK_DOWN:
                selectDown();
                break;
			case KeyEvent.VK_UP:
                selectUp();
				break;
            case KeyEvent.VK_ENTER:
                selectEnter();
                break;
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {}
	}

    private void selectUp() {
        menu.move(-1);
        this.revalidate();
        this.repaint();
    }

    private void selectDown() {
        menu.move(1);
        this.revalidate();
        this.repaint();
    }

    private void selectEnter() {
        menu.selected().isPressed();
    }

    private class Button extends JPanel {

        protected JLabel text;

        public Button(String buttonName) {
            setLayout(new GridBagLayout());
            setBackground(changeColor(Color.GRAY));
            setOpaque(false);

            text = new JLabel(buttonName, SwingConstants.CENTER);
            text.setFont(new Font("Courier", Font.BOLD, 16));
            text.setForeground(changeColor(Color.BLACK));
            text.setHorizontalAlignment(JLabel.CENTER);
            add(text);

            Dimension labelSize = text.getPreferredSize();
            int panelWidth = labelSize.width + 20;
            int panelHeight = labelSize.height + 20;
            setPreferredSize(new Dimension(panelWidth, panelHeight));
        }

        protected void isPressed() {}

    }
}
