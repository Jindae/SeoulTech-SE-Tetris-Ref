package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.function.LongSupplier;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngine;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GeneratorBlockSource;
import seoultech.se.tetris.score.ScoreStore;

public class Gamepanel extends JPanel{
    private Board board;
    private final Difficulty difficulty;
    private final GameMode gameMode;
    private Nextblockpanel nextblockpanel;
    private JTextPane scorepanel;
    private StyledDocument docScore;
    private SimpleAttributeSet styleSetScore1;
    private SimpleAttributeSet styleSetScore2;
    private Maincontainer maincontainer;
    private int score;
    private String userName = "PLAYER";
    private StringBuffer userNameBuffer;
    private JLabel setUserNameText;
    private JPanel pausepanel;
    private JPanel gameOverPanel;
    private int HEIGHT;
    private int WIDTH;
    private KeyListener playerKeyListener;
    private final transient GameController controller;

    public Gamepanel(Maincontainer maincontainer) {
        this(maincontainer, "Classic");
    }

    public Gamepanel(Maincontainer maincontainer, String gameMode) {
        this(maincontainer, GameMode.parse(gameMode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown game mode: " + gameMode)));
    }

    public Gamepanel(Maincontainer maincontainer, GameMode gameMode) {
        this(maincontainer, gameMode, new GameEngine(
            new GameConfig(gameMode, maincontainer.getDifficulty()), new GeneratorBlockSource()),
            System::nanoTime);
    }

    Gamepanel(Maincontainer maincontainer, GameMode gameMode, GameEngine engine,
            LongSupplier clock) {
        setLayout(null);
        setBackground(changeColor(Color.LIGHT_GRAY));
        this.maincontainer = maincontainer;
        GameSnapshot initial = java.util.Objects.requireNonNull(engine, "engine").snapshot();
        if (initial.mode() != java.util.Objects.requireNonNull(gameMode, "gameMode")) {
            throw new IllegalArgumentException("gameMode does not match engine config");
        }
        this.gameMode = initial.mode();
        this.difficulty = initial.difficulty();
        HEIGHT = maincontainer.getConfiguredHeight(); WIDTH = maincontainer.getConfiguredWidth();

        board = new Board();
        board.setBounds(10, 10, WIDTH*330/500, HEIGHT*580/600);
        add(board);

        nextblockpanel = new Nextblockpanel();
        nextblockpanel.setBounds(WIDTH*330/500+20, 10, WIDTH*140/500, HEIGHT*140/600);
        add(nextblockpanel);

        scorepanel = new JTextPane();
        scorepanel.setEditable(false);
        scorepanel.setFocusable(false);
        scorepanel.setBounds(WIDTH*330/500+20, HEIGHT*140/600+20, WIDTH*140/500, HEIGHT*50/600);
		scorepanel.setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY), 1));
        scorepanel.setOpaque(false);
        scorepanel.setText("Scores\n" + this.score);
        docScore = scorepanel.getStyledDocument();
        styleSetScore1 = new SimpleAttributeSet();
        styleSetScore2 = new SimpleAttributeSet();
		StyleConstants.setFontSize(styleSetScore1, 18);
		StyleConstants.setFontFamily(styleSetScore1, "Courier");
		StyleConstants.setForeground(styleSetScore1, changeColor(Color.GRAY));
		StyleConstants.setAlignment(styleSetScore1, StyleConstants.ALIGN_CENTER);
		docScore.setParagraphAttributes(0, 1, styleSetScore1, false);
        StyleConstants.setFontSize(styleSetScore2, 18);
		StyleConstants.setFontFamily(styleSetScore2, "Courier");
		StyleConstants.setForeground(styleSetScore2, changeColor(Color.RED));
		StyleConstants.setAlignment(styleSetScore2, StyleConstants.ALIGN_RIGHT);
        docScore.setParagraphAttributes(7, 1, styleSetScore2, false);
        add(scorepanel);

        controller = new GameController(this, board, engine, clock);
        JButton menuButton = new JButton("MENU");
        MenuButtons.styleActionButton(menuButton);
        menuButton.setOpaque(true);
        menuButton.setBounds(WIDTH*330/500+20, HEIGHT*200/600+30, WIDTH*140/500, 35);
        menuButton.addActionListener(event -> controller.exitToMenu());
        add(menuButton);

        playerKeyListener = new PlayerKeyListener();
		addKeyListener(playerKeyListener);
		setFocusable(true);
		requestFocus();
        setFocusTraversalKeysEnabled(false);
    }
    
    Difficulty getDifficulty() { return difficulty; }
    GameMode getGameMode() { return gameMode; }
    GameController controller() { return controller; }

    void startGame() { controller.start(); }

    void stopGame() { controller.stop(); }

    protected void drawNextBoard(GameSnapshot.Piece next) {
		nextblockpanel.drawNextBoard(next);
	}

    protected void drawScore(int score) {
        this.score = score;
        this.scorepanel.setText("Scores\n" + this.score);
        docScore = scorepanel.getStyledDocument();
		docScore.setParagraphAttributes(0, 1, styleSetScore1, false);
        docScore.setParagraphAttributes(7, 1, styleSetScore2, false);
    }

    void showSnapshot(GameSnapshot snapshot) {
        drawScore(snapshot.score());
        drawNextBoard(snapshot.next());
        if (snapshot.paused() && pausepanel == null) gamePause();
        if (!snapshot.paused() && pausepanel != null) gameRestart();
    }

    protected void gameOver() {
        controller.stop();
        var rankings = ScoreStore.readRanked(gameMode);
        if (rankings.size() >= 10 && score <= rankings.get(9).score()) {
            maincontainer.exitGameEnterScoreboard(null, score, difficulty, gameMode);
            return;
        }
        gameOverPanel = new JPanel() {
		    @Override 
		    protected void paintComponent(Graphics g) {
			    super.paintComponent(g);
			    Graphics2D g2d = (Graphics2D) g.create();
			    g2d.setColor(changeColor(new Color(0, 0, 0, 200)));
			    g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.dispose();
		    }
        };
        gameOverPanel.setOpaque(false);
        gameOverPanel.setBounds(0, 0, WIDTH, HEIGHT);
        gameOverPanel.setLayout(new GridBagLayout());
        
        JPanel gridPanel = new JPanel(new GridLayout(0, 1));
        gridPanel.setOpaque(false);

            JLabel gotext = new JLabel("Game Over", SwingConstants.CENTER);
            gotext.setFont(new Font("Courier", Font.BOLD, 30));
            gotext.setForeground(changeColor(Color.WHITE));

            JLabel sctext = new JLabel("Your score is " + score, SwingConstants.CENTER);
            sctext.setFont(new Font("Courier", Font.BOLD, 16));
            sctext.setForeground(changeColor(Color.WHITE));

            JLabel entext = new JLabel("Enter your name", SwingConstants.CENTER);
            entext.setFont(sctext.getFont());
            entext.setForeground(changeColor(Color.WHITE));

            setUserNameText = new JLabel("PLAYER", SwingConstants.CENTER);
            setUserNameText.setFont(gotext.getFont());
            setUserNameText.setForeground(changeColor(new Color(255, 255, 255, 90)));

        int gridPanelHeight = gotext.getPreferredSize().height + sctext.getPreferredSize().height + entext.getPreferredSize().height + setUserNameText.getPreferredSize().height + 50;
        gridPanel.setPreferredSize(new Dimension(WIDTH, gridPanelHeight));

        gridPanel.add(gotext);
        gridPanel.add(sctext);
        gridPanel.add(entext);
        gridPanel.add(setUserNameText);
        gameOverPanel.add(gridPanel);

        add(gameOverPanel);
        setComponentZOrder(gameOverPanel, 0);
        revalidate();
        repaint();

        userNameBuffer = new StringBuffer();
        removeKeyListener(playerKeyListener);
        UserNameListener userNameListener = new UserNameListener();
        addKeyListener(userNameListener);
        requestFocusInWindow();
    }

    private void saveScore() {
        ScoreStore.append(this.userName, this.score, difficulty, gameMode);
    }

    protected void gamePause() {
        pausepanel = new JPanel() {
		    @Override 
		    protected void paintComponent(Graphics g) {
			    super.paintComponent(g);
			    Graphics2D g2d = (Graphics2D) g.create();
			    g2d.setColor(changeColor(new Color(255, 255, 255, 80)));
			    g2d.fillRect(0, 0, board.getWidth(), board.getHeight());
                g2d.dispose();
		    }
	    };
        pausepanel.setOpaque(false);
        pausepanel.setBounds(10, 10, board.getWidth(), board.getHeight());
        pausepanel.setLayout(new GridBagLayout());
        JPanel textpanel = new JPanel(new GridLayout(3, 1, 0, 10));
        textpanel.setOpaque(false);

            JLabel pause = new JLabel("- PAUSED -", SwingConstants.CENTER);
            pause.setFont(new Font("Courier", Font.BOLD, 20));
            pause.setForeground(changeColor(Color.WHITE));

            JLabel explane = new JLabel("press pause to resume", SwingConstants.CENTER);
            explane.setFont(new Font("Courier", Font.BOLD, 13));
            explane.setForeground(changeColor(Color.WHITE));

            JLabel exit = new JLabel("press enter to exit", SwingConstants.CENTER);
            exit.setFont(new Font("Courier", Font.BOLD, 13));
            exit.setForeground(changeColor(Color.WHITE));

        int h = pause.getPreferredSize().height + explane.getPreferredSize().height + exit.getPreferredSize().height;
        textpanel.setPreferredSize(new Dimension(board.getWidth(), h+20));

        textpanel.add(pause);
        textpanel.add(explane);
        textpanel.add(exit);
        pausepanel.add(textpanel);

        add(pausepanel);
        setComponentZOrder(pausepanel, 0);
        revalidate();
        repaint();
    }

    protected void gameExit() {
        controller.exitToMenu();
    }

    void returnToStartScreen() {
        maincontainer.exitGameEnterStart();
    }

    protected void gameRestart() {
        if (pausepanel != null) remove(pausepanel);
        pausepanel = null;
        revalidate();
        repaint();
    }

    public class PlayerKeyListener implements KeyListener {
		@Override
		public void keyTyped(KeyEvent e) {}

		@Override
		public void keyPressed(KeyEvent e) {
			controller.handleKey(e.getKeyCode());
		}

		@Override
		public void keyReleased(KeyEvent e) {}
	}

    public class UserNameListener implements KeyListener {

        @Override
		public void keyTyped(KeyEvent e) {}

        @Override 
        public void keyPressed(KeyEvent e) {
            char c = e.getKeyChar();
            int i = e.getKeyCode();
            if (((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) && userNameBuffer.length() < 10) { // 영문 대소문자와 숫자 0~9를 최대 10자까지 입력한다.
                userNameBuffer.append(c);
                setUserNameText.setText(userNameBuffer.toString());
                setUserNameText.setForeground(changeColor(Color.WHITE));
            } else if (i == KeyEvent.VK_BACK_SPACE && !userNameBuffer.isEmpty()) {
                userNameBuffer.deleteCharAt(userNameBuffer.length()-1);
                setUserNameText.setText(userNameBuffer.toString());
                if (userNameBuffer.isEmpty()) {
                    setUserNameText.setForeground(changeColor(new Color(255, 255, 255, 90)));
                    setUserNameText.setText("PLAYER");
                }
            } else if (i == KeyEvent.VK_ENTER) {
                userName = setUserNameText.getText();
                removeKeyListener(this);
                saveScore();
                maincontainer.exitGameEnterScoreboard(userName, score, difficulty, gameMode);
            } else e.consume();
        }

        @Override 
        public void keyReleased(KeyEvent e) {}
    }
}
