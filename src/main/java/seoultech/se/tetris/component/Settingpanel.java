package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;
import seoultech.se.tetris.score.ScoreStore;

public class Settingpanel extends JPanel{
    /*
    버튼 추가 시 지침
    1. OptionButton 객체 생성
    - OptionButton(Option 이름, Option 내용(없을 시 null 전달))
    2. protected void isPressed() 오버라이딩 하기
    - 버튼이 눌렸을 때 발생되는 이벤트 함수를 구현하기.
    3. 해당 화면의 menu.add(JPanel parent, OptionButton child)로 버튼 등록하기
    - 버튼이 등록되는 건 추가되는 순서대로 추가됩니다. (기존 동작 유지: 최대 11개 등록 가능)

    기존의 등록 방식을 변경하여 위의 흐름대로만 설정하면 작동할 수 있도록 변경하였습니다.
    기본적으로 OptionButton은 JPanel을 상속하므로, JPanel이 가지는 메소드도 전부 사용 가능합니다.
    같은 방식으로 키 추가 리스트도 추가할 수 있습니다.
     */
    private Maincontainer maincontainer;
    private JPanel settingPanel;
    private OptionButton exitButton;
    private OptionButton windowSizeOption;
    private OptionButton keySettingOption;
    private OptionButton resetScoreOption;
    private OptionButton colorBlindOption;
    private OptionButton difficultyOption;
    private OptionButton timeLimitOption;
    private OptionButton resetSettingOption;
    private PlayerKeyListener playerKeyListener;
    private int HEIGHT;
    private int WIDTH;
    private final MenuButtons<OptionButton> menu = new MenuButtons<>();

    public Settingpanel(Maincontainer maincontainer) {
        this.maincontainer = maincontainer;
        setLayout(null);
        setBackground(changeColor(Color.LIGHT_GRAY));

        HEIGHT = maincontainer.getConfiguredHeight();
        WIDTH = maincontainer.getConfiguredWidth();

        settingPanel = new JPanel();
        settingPanel.setLayout(new GridBagLayout());
        settingPanel.setBounds(10, 10, WIDTH-20, 3*(HEIGHT-20)/4);
        settingPanel.setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY), 1));
        settingPanel.setOpaque(false);

            // 테트리스 게임 화면의 크기를 조절 - 최소 3가지 이상 미리 정의된 크기.
            windowSizeOption = new OptionButton("Window Option: ", maincontainer.setting.getWindowSize()) {
                @Override
                protected void isPressed() {
                    option.setText(maincontainer.setting.nextWindowSize());
                }
            };
            menu.add(settingPanel, windowSizeOption);
            // 게임 조작을 위해 사용될 키를 설정
            keySettingOption = new OptionButton("Change Keys", null) {
                @Override 
                protected void isPressed() {
                    Settingpanel.this.removeKeyListener(playerKeyListener);
                    ChangeKeyPanel changeKeyPanel = new ChangeKeyPanel();
                    changeKeyPanel.setBounds(0, 0, Settingpanel.this.getWidth(), Settingpanel.this.getHeight());
                    Settingpanel.this.add(changeKeyPanel);
                    Settingpanel.this.setComponentZOrder(changeKeyPanel, 0);
                    changeKeyPanel.setFocusable(true);
                    Settingpanel.this.revalidate();
                    Settingpanel.this.repaint();
                    changeKeyPanel.requestFocusInWindow();
                }
            };
            menu.add(settingPanel, keySettingOption);
            // 스코어 보드의 기록을 초기화
            resetScoreOption = new OptionButton("Reset Scoreboard", null) {
                @Override
                protected void isPressed() {
                    ScoreStore.clear();
                }
            };
            menu.add(settingPanel, resetScoreOption);
            // 색상 모드를 정해진 순서대로 변경한다.
            colorBlindOption = new OptionButton("Color Option: ", maincontainer.setting.getColorPalette()) {
                @Override 
                protected void isPressed() {
                    option.setText(maincontainer.setting.nextColorPalette());
                }
            };
            menu.add(settingPanel, colorBlindOption);
            // 난이도를 변경한다.
            difficultyOption = new OptionButton("Difficulty: ", maincontainer.setting.getDifficulty()) {
                @Override 
                protected void isPressed() {
                    option.setText(maincontainer.setting.nextDifficulty());
                }
            };
            menu.add(settingPanel, difficultyOption);
            timeLimitOption = new OptionButton("Time Limit (sec): ", maincontainer.setting.getTimeLimitSeconds()) {
                @Override
                protected void isPressed() {
                    option.setText(maincontainer.setting.nextTimeLimitSeconds());
                }
            };
            menu.add(settingPanel, timeLimitOption);
            // 모든 설정을 기본값으로 되돌린다.
            resetSettingOption = new OptionButton("Reset setting", null) {
                @Override 
                protected void isPressed() {
                    maincontainer.setting.clearSetting();
                    windowSizeOption.option.setText(maincontainer.setting.getWindowSize());
                    colorBlindOption.option.setText(maincontainer.setting.getColorPalette());
                    difficultyOption.option.setText(maincontainer.setting.getDifficulty());
                    timeLimitOption.option.setText(maincontainer.setting.getTimeLimitSeconds());
                    Settingpanel.this.revalidate();
                    Settingpanel.this.repaint();
                }
            };
            menu.add(settingPanel, resetSettingOption);

        add(settingPanel);

        exitButton = new OptionButton("Save and Exit", null) {
            @Override 
            protected void isPressed() {
                maincontainer.setting.exportSetting();
                maincontainer.exitSettingEnterStart();
            }
        };
        int panelWidth = (int) exitButton.getPreferredSize().getWidth();
        int panelHeight = (int) exitButton.getPreferredSize().getHeight();
        exitButton.setBounds((WIDTH-panelWidth)/2, (3*(HEIGHT-20)/4+10)+((HEIGHT-10)-(3*(HEIGHT-20)/4+10)-panelHeight)/2, panelWidth, panelHeight);
        menu.add(this, exitButton);

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

    private class ChangeKeyPanel extends JPanel {

        private OptionButton rotateOption;
        private OptionButton downOption;
        private OptionButton leftOption;
        private OptionButton rightOption;
        private OptionButton pauseOption;
        private OptionButton hardDropOption;
        private OptionButton exitOption;
        private Integer[] currentKey;
        private final MenuButtons<OptionButton> keyMenu = new MenuButtons<>();
        private PlayerKeyListener2 keylistener2;
         
        public ChangeKeyPanel() {
            setLayout(null);
            setBackground(changeColor(Color.LIGHT_GRAY));
            setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY), 1));
            JPanel keyList = new JPanel();
            add(keyList);
            keyList.setBounds(10, 10, settingPanel.getWidth(), settingPanel.getHeight());
            keyList.setLayout(new GridBagLayout());
            currentKey = maincontainer.setting.getKeyCodes();

            rotateOption = new OptionButton("Rotate: ", KeyEvent.getKeyText(currentKey[0])) {
                @Override 
                protected void isPressed() {
                    changeKey(keyMenu.selectedIndex());
                }
            };
            keyMenu.add(keyList, rotateOption);

            downOption = new OptionButton("Down: ", KeyEvent.getKeyText(currentKey[1])) {
                @Override 
                protected void isPressed() {
                    changeKey(keyMenu.selectedIndex());
                }
            };
            keyMenu.add(keyList, downOption);

            leftOption = new OptionButton("Left: ", KeyEvent.getKeyText(currentKey[2])) {
                @Override 
                protected void isPressed() {
                    changeKey(keyMenu.selectedIndex());
                }
            };
            keyMenu.add(keyList, leftOption);

            rightOption = new OptionButton("Right: ", KeyEvent.getKeyText(currentKey[3])) {
                @Override 
                protected void isPressed() {
                    changeKey(keyMenu.selectedIndex());
                }
            };
            keyMenu.add(keyList, rightOption);

            pauseOption = new OptionButton("Pause: ", KeyEvent.getKeyText(currentKey[4])) {
                @Override 
                protected void isPressed() {
                    changeKey(keyMenu.selectedIndex());
                }
            };
            keyMenu.add(keyList, pauseOption);

            hardDropOption = new OptionButton("Hard Drop: ", KeyEvent.getKeyText(currentKey[5])) {
                @Override 
                protected void isPressed() {
                    changeKey(keyMenu.selectedIndex());
                }
            };
            keyMenu.add(keyList, hardDropOption);

            exitOption = new OptionButton("Save and Exit", null) {
                @Override 
                protected void isPressed() {
                    maincontainer.setting.updateKeyCodes(currentKey);
                    Settingpanel.this.removeKeyListener(keylistener2);
                    Settingpanel.this.addKeyListener(Settingpanel.this.playerKeyListener);
                    Settingpanel.this.remove(ChangeKeyPanel.this);
                    Settingpanel.this.revalidate();
                    Settingpanel.this.repaint();
                    Settingpanel.this.setFocusable(true);
                    Settingpanel.this.requestFocusInWindow();
                }
            };
            keyMenu.add(keyList, exitOption);

            keylistener2 = new PlayerKeyListener2();
            this.addKeyListener(keylistener2);
            setFocusable(true);
            requestFocusInWindow();
        }

        private class PlayerKeyListener2 implements KeyListener {
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

        private void selectDown() {
            keyMenu.move(1);
            revalidate();
            repaint();
        }

        private void selectUp() {
            keyMenu.move(-1);
            revalidate();
            repaint();
        }

        private void selectEnter() {
            keyMenu.selected().isPressed();
        }

        private void changeKey(int idx) {
            removeKeyListener(keylistener2);
            JPanel display = new JPanel();
            display.setLayout(new GridLayout());
            this.add(display);
            int w = settingPanel.getWidth();
            int h = settingPanel.getHeight();
            display.setBounds(10+w/4-25, 10+h/4, w/2+50, h/2);
            display.setBackground(changeColor(Color.LIGHT_GRAY));
            JLabel text = new JLabel("Press Key to Change", SwingConstants.CENTER);
            text.setFont(new Font("Courier", Font.BOLD, 16));
            text.setForeground(changeColor(Color.BLACK));
            display.add(text);
            setComponentZOrder(display, 0);
            revalidate();
            repaint();
            KeyListener customKeyListener = new KeyAdapter() {
                @Override 
                public void keyPressed(KeyEvent e) {
                    int newKey = e.getKeyCode();
                    String keyName = KeyEvent.getKeyText(newKey);
                    if (idx == 4 && newKey == KeyEvent.VK_ENTER) {
                        text.setText("Enter Is Reserved for Exit");
                        return;
                    }
                    if (!Arrays.asList(currentKey).contains(newKey) || newKey == currentKey[idx]) {
                        currentKey[idx] = newKey;
                        keyMenu.selected().option.setText(keyName);
                        removeKeyListener(this);
                        addKeyListener(keylistener2);
                        Container parent = display.getParent();
                        parent.remove(display);
                        parent.revalidate();
                        parent.repaint();
                        return;
                    } else {
                        text.setText("Please Enter an Unused Key");
                        display.revalidate();
                        display.repaint();
                        return;
                    }
                }
            };
            addKeyListener(customKeyListener);
            requestFocusInWindow();
        }


    }

    private class OptionButton extends JPanel {

        protected JLabel text;
        protected JLabel option;

        public OptionButton(String optionName, String optionContent) {
            setLayout(new GridLayout(1, 0));
            setBackground(changeColor(Color.GRAY));
            setOpaque(false);

            text = new JLabel(optionName, SwingConstants.CENTER);
            text.setFont(new Font("Courier", Font.BOLD, 16));
            text.setForeground(changeColor(Color.BLACK));
            add(text);
            if (optionContent != null) {
                option = new JLabel(optionContent, SwingConstants.CENTER);
                option.setFont(new Font("Courier", Font.BOLD, 16));
                option.setForeground(changeColor(Color.BLACK));
                add(option);
            }

            Dimension labelSize = text.getPreferredSize();
            int panelWidth = (optionContent != null) ? (labelSize.width * 2 + 20) : (labelSize.width + 20);
            int panelHeight = labelSize.height + 20;
            setPreferredSize(new Dimension(panelWidth, panelHeight));
        }

        protected void isPressed() {}

    }
}
