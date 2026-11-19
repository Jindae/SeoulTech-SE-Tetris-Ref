package seoultech.se.tetris.component;

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.score.ScoreStore;

public class Scoreboardpanel extends JPanel{
    private Maincontainer maincontainer;
    private JTextPane scorepanel;
    private PlayerKeyListener playerKeyListener;
    private int HEIGHT;
    private int WIDTH;
    private GameMode selectedMode;
    private final JToggleButton[] modeButtons = new JToggleButton[GameMode.values().length + 1];
    private List<ScoreStore.Score> visibleScores = List.of();
    private ScoreStore.Score highlightedScore;

    public Scoreboardpanel(Maincontainer maincontainer) {
        this(maincontainer, null);
    }

    public Scoreboardpanel(Maincontainer maincontainer, GameMode mode) {
        setLayout(null);
        setBackground(changeColor(Color.LIGHT_GRAY));
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        this.maincontainer = maincontainer;
        selectedMode = mode;

        HEIGHT = maincontainer.getConfiguredHeight();
        WIDTH = maincontainer.getConfiguredWidth();

        scorepanel = new JTextPane();
        scorepanel.setEditable(false);
        scorepanel.setFocusable(false);
        scorepanel.setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY), 1));
        scorepanel.setOpaque(false);
        scorepanel.setMargin(new Insets(10, 10, 10, 10));
        JPanel filters = new JPanel(new GridLayout(1, modeButtons.length, 6, 0));
        filters.setOpaque(false);
        filters.setBounds(10, 10, WIDTH - 20, 32);
        ButtonGroup filterGroup = new ButtonGroup();
        for (int index = 0; index < modeButtons.length; index++) {
            GameMode filterMode = index == 0 ? null : GameMode.values()[index - 1];
            JToggleButton button = new JToggleButton(index == 0 ? "All" : filterMode.displayName());
            MenuButtons.styleActionButton(button);
            button.setOpaque(false);
            button.addItemListener(event -> {
                button.setOpaque(button.isSelected());
                button.repaint();
            });
            button.addActionListener(event -> showMode(filterMode));
            modeButtons[index] = button;
            filterGroup.add(button);
            filters.add(button);
        }
        drawScore();
        JScrollPane rankings = new JScrollPane(scorepanel);
        rankings.getVerticalScrollBar().setFocusable(false);
        rankings.getHorizontalScrollBar().setFocusable(false);
        rankings.setBounds(10, 50, WIDTH - 20, 3 * (HEIGHT - 20) / 4 - 40);
        rankings.setOpaque(false);
        rankings.getViewport().setOpaque(false);

        JPanel buttonpanel = new JPanel();
        buttonpanel.setLayout(new GridLayout());
        buttonpanel.setOpaque(false); // 투명 배경

            JLabel exitbutton = new JLabel("Return to Main", SwingConstants.CENTER);
            exitbutton.setFont(new Font("Courier", Font.BOLD, 16));
            exitbutton.setForeground(changeColor(Color.BLACK));
            exitbutton.setBackground(changeColor(Color.GRAY));
            exitbutton.setOpaque(true);

        Dimension labelSize = exitbutton.getPreferredSize();
        int panelWidth = labelSize.width + 20;
        int panelHeight = labelSize.height + 20;
        buttonpanel.setBounds((WIDTH-panelWidth)/2, (3*(HEIGHT-20)/4+10)+((HEIGHT-10)-(3*(HEIGHT-20)/4+10)-panelHeight)/2, panelWidth, panelHeight);
        buttonpanel.add(exitbutton);

        add(filters);
        add(rankings);
        add(buttonpanel);

        playerKeyListener = new PlayerKeyListener();
		addKeyListener(playerKeyListener);
		setFocusable(true);
		requestFocus();
    }

    public void drawScore() {
        List<ScoreStore.Score> loadedScores = ScoreStore.readRanked(selectedMode);
        int totalusernum = loadedScores.size();
        visibleScores = loadedScores.subList(0, Math.min(totalusernum, 10));
        StringBuffer sb = new StringBuffer();
        int counter = 1;
        for (int j = 0; j < Math.min(totalusernum, 10); j++) {
            ScoreStore.Score loaded = loadedScores.get(j);
            sb.append(counter + ".\t" + scoreLabel(loaded) + "\n");
            counter++;
        }
        for (int k = 0; k < 10 - Math.min(totalusernum, 10); k++) {
            sb.append(counter + ".\t" + "###: 0\n");
            counter++;
        }
        scorepanel.setText(sb.toString());
        StyledDocument doc = scorepanel.getStyledDocument();
        SimpleAttributeSet styleSet = new SimpleAttributeSet();
		StyleConstants.setFontSize(styleSet, 18);
		StyleConstants.setFontFamily(styleSet, "Courier");
        StyleConstants.setBold(styleSet, true);
		StyleConstants.setForeground(styleSet, changeColor(Color.BLACK));
		StyleConstants.setLineSpacing(styleSet, -0.1f);
        doc.setCharacterAttributes(0, doc.getLength(), styleSet, false);
        scorepanel.setStyledDocument(doc);
        int selectedIndex = selectedMode == null ? 0 : selectedMode.ordinal() + 1;
        modeButtons[selectedIndex].setSelected(true);
        applyHighlight();
    }

    private static String scoreLabel(ScoreStore.Score score) {
        String difficulty = score.hasDifficulty() ? " [" + score.difficulty().displayName() + "]" : "";
        String mode = score.hasMode() ? " [" + score.mode().displayName() + "]" : "";
        return score.username() + ": " + score.score() + difficulty + mode;
    }

    private void showMode(GameMode mode) {
        selectedMode = mode;
        drawScore();
        scorepanel.setCaretPosition(0);
        requestFocusInWindow();
    }

    private void cycleMode(int delta) {
        int index = selectedMode == null ? 0 : selectedMode.ordinal() + 1;
        int next = Math.floorMod(index + delta, modeButtons.length);
        showMode(next == 0 ? null : GameMode.values()[next - 1]);
    }

    void highlightScore(String name, int score, Difficulty difficulty) {
        highlightScore(name, score, difficulty, null);
    }

    void highlightScore(String name, int score, Difficulty difficulty, GameMode mode) {
        highlightedScore = new ScoreStore.Score(name, score, difficulty, mode);
        drawScore();
    }

    private void applyHighlight() {
        if (highlightedScore == null) return;
        int index = visibleScores.lastIndexOf(highlightedScore);
        if (index >= 0) {
            int start = 0;
            for (int row = 0; row < index; row++) {
                start += (row + 1 + ".\t" + scoreLabel(visibleScores.get(row)) + "\n").length();
            }
            start += (index + 1 + ".\t").length();
            String label = scoreLabel(visibleScores.get(index));
            SimpleAttributeSet highlight = new SimpleAttributeSet();
            StyleConstants.setBackground(highlight, new Color(255, 225, 100));
            StyleConstants.setBold(highlight, true);
            scorepanel.getStyledDocument().setCharacterAttributes(start, label.length(), highlight, false);
        }
    }

    public class PlayerKeyListener implements KeyListener {
		@Override
		public void keyTyped(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
			switch(e.getKeyCode()) {
                case KeyEvent.VK_ENTER:
                    selectEnter();
                    break;
                case KeyEvent.VK_LEFT:
                    cycleMode(-1);
                    break;
                case KeyEvent.VK_RIGHT:
                    cycleMode(1);
                    break;
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
		}
	}
    protected void selectEnter() {
        this.maincontainer.exitScoreboardEnterStart();
    }
}
