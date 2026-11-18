package seoultech.se.tetris.component;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.BitSet;
import java.util.function.IntConsumer;
import static seoultech.se.tetris.component.Maincontainer.changeColor;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;

import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.AnchorBlock;
import seoultech.se.tetris.blocks.BombBlock;
import seoultech.se.tetris.blocks.ColumnBlock;
import seoultech.se.tetris.blocks.ItemBlock;
import seoultech.se.tetris.blocks.RowBlock;
import seoultech.se.tetris.blocks.BlockGenerator;
import seoultech.se.tetris.blocks.CrossBlock;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.rules.CollisionRules;

public class Board extends JComponent {
	private static final long serialVersionUID = 2434035659171694595L;
	
	protected static final int HEIGHT = 20;
	protected static final int WIDTH = 10;
	protected static final char BORDER_CHAR = 'X';
	protected static final int LINEPOINT = 100;
	protected static final int DOWNPOINT = 1;
	private Gamepanel gamepanel;
    private SecondaryLoop currentLoop;
    private Timer itemAnimationTimer;
    private boolean resumeAfterItemAnimation;
    private boolean anchorTouched;
	private int[][] board;
	private ArrayList<TextChunk> textChunks;
	protected Timer timer;
	private final BlockGenerator blockGenerator = new BlockGenerator();
	private Block curr;
	private boolean isPaused = false;
	private int score = 0;
	private Block next;
	private int erasedLineNum = 0;
    private int pendingItems;
	private int level = 1;
    private final java.util.BitSet clearingRows = new java.util.BitSet(HEIGHT);
    private Timer clearAnimationTimer;
    private long clearAnimationStarted;
	int x = 3; //Default Position.
	int y = 0;
	
	private static final int initInterval = 1000;
	
	public Board(Gamepanel gamepanel) {
		//Board display setting.
		setBackground(changeColor(Color.BLACK));
		CompoundBorder border = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(changeColor(Color.GRAY), 10),
				BorderFactory.createLineBorder(changeColor(Color.DARK_GRAY), 5));
		setBorder(border);
		
		this.gamepanel = gamepanel;
		//Set timer for block drops.
		timer = new Timer(initInterval, new ActionListener() {			
			@Override
			public void actionPerformed(ActionEvent e) {
				gamepanel.automaticDrop();
			}
		});
		
		//Initialize board for the game.
		board = new int[HEIGHT][WIDTH];
		textChunks = new ArrayList<>();	
	}

	protected void boardStart() {
        pendingItems = 0;
		curr = blockGenerator.nextBlock(gamepanel.getDifficulty());
		next = blockGenerator.nextBlock(gamepanel.getDifficulty());
		this.gamepanel.drawNextBoard(next);
		StringBuffer sb = new StringBuffer();
		for(int t=0; t<WIDTH+2; t++) sb.append(BORDER_CHAR); // 윗쪽 보더
		sb.append("\n");
		for(int i=0; i < board.length; i++) {
			sb.append(BORDER_CHAR); // 왼쪽 보더
			for(int j=0; j < board[i].length; j++) sb.append(" ");
			sb.append(BORDER_CHAR); // 오른쪽 보더
			sb.append("\n");
		}
		for(int t=0; t<WIDTH+2; t++) sb.append(BORDER_CHAR); // 아래쪽 보더
		this.setText(sb.toString());
		placeBlock();
		drawBoard();
		timer.start();
	}

	protected void togglePause() {
		if (timer == null) return;
		if (isPaused) {
			isPaused = false;
            if (itemAnimationTimer == null) timer.start();
            else resumeAfterItemAnimation = true;
			gamepanel.gameRestart();
		} else {
			isPaused = true;
			resumeAfterItemAnimation = false;
			this.timer.stop();
			gamepanel.gamePause();
		}
	}

    private boolean acceptsMovement() {
        return timer != null && !isPaused && itemAnimationTimer == null;
    }

    // Called with the active block removed from the collision grid.
    private boolean anchorMovementLocked() {
        if (!(curr instanceof AnchorBlock)) return false;
        if (!canDown(x, y)) anchorTouched = true;
        return anchorTouched;
    }

	private void placeBlock() {
		int dy = y; int dx = x;
		while(canDown(dx, dy)) dy++;
		for(int j=0; j<curr.height(); j++) {
			for(int i=0; i<curr.width(); i++) {
				if (curr.getShape(i, j) > 0 && textChunks.get((dy+j+1)*(WIDTH+3)+dx+i+1).text.equals(" ")) {
					textChunks.set((dy+j+1)*(WIDTH+3)+dx+i+1, new TextChunk("O", Color.DARK_GRAY));
				}
			}
		}
		for(int j=0; j<curr.height(); j++) {
			for(int i=0; i<curr.width(); i++) {
				if (curr.getShape(i, j) > 0) {
					board[y+j][x+i] += curr.getShape(i, j);
					String marker = curr.getText(i, j);
					textChunks.set((y+j+1)*(WIDTH+3)+x+i+1, new TextChunk(marker,
                        marker.equals("O") || marker.equals("P") ? curr.getColor() : Color.WHITE));
				}
			}
		}
	}
	
	private void eraseCurr() {
		for(int j=0; j<curr.height(); j++) {
			for(int i=0; i<curr.width(); i++) {
				if (curr.getShape(i, j) > 0) {
					board[j+y][i+x] = 0;
					textChunks.set((y+j+1)*(WIDTH+3)+x+i+1, new TextChunk(" ", Color.WHITE));
				}
			}
		}
		int dy = y; int dx = x;
		while(canDown(dx, dy)) {
			dy++;
		}
		for(int j=0; j<curr.height(); j++) {
			for(int i=0; i<curr.width(); i++) {
				if (curr.getShape(i, j) > 0) {
					textChunks.set((dy+j+1)*(WIDTH+3)+dx+i+1, new TextChunk(" ", Color.WHITE));
				}
			}
		}
	}

	protected boolean canDown(int x, int y) {
		return CollisionRules.canDown(board, curr, x, y);
	}

	protected boolean canRight() {
		return CollisionRules.canRight(board, curr, x, y);
	}

	protected boolean canLeft() {
		return CollisionRules.canLeft(board, curr, x, y);
	}

	protected boolean canRotate() {
		return rotationPosition() != null;
	}

	private int[] rotationPosition() {
		if (curr instanceof AnchorBlock || curr instanceof BombBlock) return null;
		return CollisionRules.rotationPosition(board, curr, x, y);
	}

	protected boolean moveDown() {
		if (!acceptsMovement()) return false;
		eraseCurr();
		if (canDown(x, y)) {
			addScore(DOWNPOINT * level);
			y++;
			if (curr instanceof AnchorBlock && !canDown(x, y)) anchorTouched = true;
			placeBlock();
			return true;
		}
		lockAndAdvanceBlock();
		return false;
	}

	protected void hardDrop() {  // 하드 드롭 기능 구현
		if (!acceptsMovement()) return;
		eraseCurr();
		while (canDown(x, y)) {
			y++;
			addScore(DOWNPOINT * level);
		}
		if (!lockAndAdvanceBlock()) return;
		drawBoard();
	}

	private boolean lockAndAdvanceBlock() {
        int clearedBefore = erasedLineNum;
		placeBlock();
        if (curr instanceof ItemBlock && !useItem()) return false;
		eraseLine();

		if (isgameover()) return false;
		curr = next;
        anchorTouched = false;
        if (gamepanel.getGameMode() == GameMode.ITEM) {
            pendingItems += erasedLineNum / 10 - clearedBefore / 10;
        }
        if (gamepanel.getGameMode() == GameMode.ITEM && pendingItems > 0) {
            next = blockGenerator.nextItemBlock();
            pendingItems--;
        } else {
            next = blockGenerator.nextBlock(gamepanel.getDifficulty());
        }
		this.gamepanel.drawNextBoard(next);

		x = 3;
		y = 0;
		placeBlock();
		return true;
	}

	protected boolean useItem() {
		if (curr instanceof AnchorBlock) {
			eraseCurr();
			for (int i = y+curr.height(); i<HEIGHT; i++) {
				for (int j = x; j < x+curr.width(); j++) {
					board[i][j] = 0;
					int idx = (i+1)*(WIDTH+3)+j+1;
					textChunks.set(idx, new TextChunk(" ", Color.WHITE));
				}
			}
			y = HEIGHT - curr.height();
			placeBlock();
		} else if (curr instanceof BombBlock) {
			int startX = Math.max(0, x-2); int endX = Math.min(x+curr.width()+1, WIDTH-1);
			int startY = Math.max(0, y-2); int endY = Math.min(y+curr.height()+1, HEIGHT-1);
			for (int i = startY; i<=endY; i++) {
				for (int j = startX; j<=endX; j++) {
					board[i][j] = 0;
					int idx = (i+1)*(WIDTH+3)+j+1;
					textChunks.set(idx, new TextChunk(" ", Color.WHITE));
				}
			}
			settleColumns();
		} else if (curr instanceof CrossBlock) {
            return eraseCrossLines();
		} else if (curr instanceof RowBlock) {
			int itemY = ((RowBlock)curr).getItemY();
			return eraseRowLine(y + itemY);
		} else if (curr instanceof ColumnBlock) {
			int itemX = ((ColumnBlock)curr).getItemX();
			return eraseColumnLine(x + itemX);
		}
        return true;
	}

	protected void moveRight() {
		if (!acceptsMovement()) return;
		eraseCurr();
		if (!anchorMovementLocked() && canRight()) x++;
		placeBlock();
	}

	protected void moveLeft() {
		if (!acceptsMovement()) return;
		eraseCurr();
		if (!anchorMovementLocked() && canLeft()) x--;
		placeBlock();
	}

	protected void rotate() {
		if (!acceptsMovement()) return;
		eraseCurr();
        anchorMovementLocked();
		int[] position = rotationPosition();
		if (position != null) {
			x = position[0];
			y = position[1];
			curr.rotate();
		}
		placeBlock();
	}

	protected void drawBoard() {
		this.repaint();
	}
	
	protected void reset() {
		this.board = new int[HEIGHT][WIDTH];
        pendingItems = 0;
	}

	protected void eraseLine() {
        BitSet fullRows = completedRows();
        if (fullRows.isEmpty()) return;
        animateClearedRows(fullRows);
        removeRows(fullRows);
    }

    private BitSet completedRows() {
        BitSet fullRows = new BitSet(HEIGHT);
        for (int row = 0; row < HEIGHT; row++) {
            if (Arrays.stream(board[row]).allMatch(cell -> cell != 0)) fullRows.set(row);
        }
        return fullRows;
    }

    private void removeRows(BitSet rows) {
        gamepanel.publishLinesCleared(rows.cardinality());
        int destination = HEIGHT - 1;
        for (int source = HEIGHT - 1; source >= 0; source--) {
            if (rows.get(source)) continue;
            board[destination] = board[source];
            for (int column = 0; column < WIDTH; column++) {
                textChunks.set((destination + 1) * (WIDTH + 3) + column + 1,
                    textChunks.get((source + 1) * (WIDTH + 3) + column + 1));
            }
            destination--;
        }
        while (destination >= 0) {
            board[destination] = new int[WIDTH];
            for (int column = 0; column < WIDTH; column++) {
                textChunks.set((destination + 1) * (WIDTH + 3) + column + 1,
                    new TextChunk(" ", Color.WHITE));
            }
            destination--;
        }
        for (int combo = 1; combo <= rows.cardinality(); combo++) {
            addScore(LINEPOINT * combo);
            if (++erasedLineNum % 10 == 0) levelUp();
        }
    }

    private void animateClearedRows(java.util.BitSet rows) {
        stopClearAnimation();
        clearingRows.or(rows);
        clearAnimationStarted = System.nanoTime();
        clearAnimationTimer = new Timer(30, event -> {
            if (event.getSource() != clearAnimationTimer) return;
            if (System.nanoTime() - clearAnimationStarted >= 210_000_000L) stopClearAnimation();
            repaint();
        });
        clearAnimationTimer.start();
    }

    private void stopClearAnimation() {
        if (clearAnimationTimer != null) clearAnimationTimer.stop();
        clearAnimationTimer = null;
        clearingRows.clear();
    }

    void stopAnimations() {
        stopClearAnimation();
        pendingItems = 0;
        if (itemAnimationTimer != null) itemAnimationTimer.stop();
        itemAnimationTimer = null;
        resumeAfterItemAnimation = false;
        if (currentLoop != null) currentLoop.exit();
        currentLoop = null;
    }

    private boolean eraseRowLine(int row) {
        BitSet rows = completedRows();
        rows.set(row);
        if (!eraseRowAnimation(row)) return false;
        BitSet normalRows = (BitSet) rows.clone();
        normalRows.clear(row);
        if (!normalRows.isEmpty()) animateClearedRows(normalRows);
        removeRows(rows);
        return true;
    }

    private boolean eraseCrossLines() {
        int firstRow = y;
        int firstColumn = x;
        BitSet rows = completedRows();
        rows.set(firstRow, firstRow + 2);
        if (!eraseCrossAnimation(firstRow, firstColumn)) return false;
        // Columns refer to the original grid; compact rows only after their cells are cleared.
        for (int row = 0; row < HEIGHT; row++) {
            for (int column = firstColumn; column < firstColumn + 2; column++) {
                board[row][column] = 0;
                textChunks.set((row + 1) * (WIDTH + 3) + column + 1,
                    new TextChunk(" ", Color.WHITE));
            }
        }
        BitSet normalRows = (BitSet) rows.clone();
        normalRows.clear(firstRow, firstRow + 2);
        if (!normalRows.isEmpty()) animateClearedRows(normalRows);
        removeRows(rows);
        return true;
    }

    private boolean eraseCrossAnimation(int firstRow, int firstColumn) {
        for (int row = 0; row < HEIGHT; row++) {
            for (int column = 0; column < WIDTH; column++) {
                if ((row >= firstRow && row < firstRow + 2)
                        || (column >= firstColumn && column < firstColumn + 2)) {
                    textChunks.set((row + 1) * (WIDTH + 3) + column + 1,
                        new TextChunk("O", Color.WHITE));
                }
            }
        }
        return runItemAnimation(HEIGHT / 2, frame -> {
            if (frame < WIDTH / 2) {
                for (int row = firstRow; row < firstRow + 2; row++) {
                    for (int column = WIDTH / 2 - frame - 1; column <= WIDTH / 2 + frame; column++) {
                        textChunks.set((row + 1) * (WIDTH + 3) + column + 1,
                            new TextChunk(" ", Color.WHITE));
                    }
                }
            }
            for (int column = firstColumn; column < firstColumn + 2; column++) {
                for (int row : new int[] {HEIGHT / 2 - frame - 1, HEIGHT / 2 + frame}) {
                    textChunks.set((row + 1) * (WIDTH + 3) + column + 1,
                        new TextChunk(" ", Color.WHITE));
                }
            }
        });
    }

    private boolean eraseColumnLine(int column) {
        if (!eraseColumnAnimation(column)) return false;
        for (int row = 0; row < HEIGHT; row++) {
            board[row][column] = 0;
            textChunks.set((row + 1) * (WIDTH + 3) + column + 1, new TextChunk(" ", Color.WHITE));
        }
        settleColumns();
        return true;
    }

    // Item gravity moves individual occupied cells, preserving their order and appearance.
    private void settleColumns() {
        for (int column = 0; column < WIDTH; column++) {
            int destination = HEIGHT - 1;
            for (int source = HEIGHT - 1; source >= 0; source--) {
                if (board[source][column] == 0) continue;
                board[destination][column] = board[source][column];
                textChunks.set((destination + 1) * (WIDTH + 3) + column + 1,
                    textChunks.get((source + 1) * (WIDTH + 3) + column + 1));
                destination--;
            }
            while (destination >= 0) {
                board[destination][column] = 0;
                textChunks.set((destination + 1) * (WIDTH + 3) + column + 1,
                    new TextChunk(" ", Color.WHITE));
                destination--;
            }
        }
    }

    protected boolean eraseRowAnimation(int row) {
        int start = (row + 1) * (WIDTH + 3) + 1;
        int end = start + WIDTH;
        Collections.fill(textChunks.subList(start, end), new TextChunk("O", Color.WHITE));
        return runItemAnimation(WIDTH / 2, frame -> {
            int left = start + WIDTH / 2 - frame - 1;
            int right = start + WIDTH / 2 + frame + 1;
            Collections.fill(textChunks.subList(left, right), new TextChunk(" ", Color.WHITE));
        });
    }

    private boolean eraseColumnAnimation(int column) {
        for (int row = 0; row < HEIGHT; row++) {
            textChunks.set((row + 1) * (WIDTH + 3) + column + 1, new TextChunk("O", Color.WHITE));
        }
        return runItemAnimation(HEIGHT / 2, frame -> {
            int top = HEIGHT / 2 - frame - 1;
            int bottom = HEIGHT / 2 + frame;
            textChunks.set((top + 1) * (WIDTH + 3) + column + 1, new TextChunk(" ", Color.WHITE));
            textChunks.set((bottom + 1) * (WIDTH + 3) + column + 1, new TextChunk(" ", Color.WHITE));
        });
    }

    private boolean runItemAnimation(int frames, IntConsumer paintFrame) {
        if (timer == null || itemAnimationTimer != null) return false;
        Timer dropTimer = timer;
        resumeAfterItemAnimation = dropTimer.isRunning();
        dropTimer.stop();
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        currentLoop = loop;
        boolean[] completed = {false};
        int[] frame = {0};
        Timer animation = new Timer(50, event -> {
            if (event.getSource() != itemAnimationTimer) return;
            if (frame[0] == frames) {
                completed[0] = true;
                ((Timer) event.getSource()).stop();
                loop.exit();
            } else {
                paintFrame.accept(frame[0]++);
                drawBoard();
            }
        });
        itemAnimationTimer = animation;
        animation.start();
        try {
            loop.enter();
        } finally {
            animation.stop();
            if (itemAnimationTimer == animation) itemAnimationTimer = null;
            if (currentLoop == loop) currentLoop = null;
        }
        boolean active = completed[0] && timer == dropTimer;
        if (active && resumeAfterItemAnimation && !isPaused) dropTimer.start();
        resumeAfterItemAnimation = false;
        return active;
    }

	private void levelUp() {
		if (level < 19) level++;
        Difficulty difficulty = gamepanel == null ? Difficulty.NORMAL : gamepanel.getDifficulty();
		timer.setDelay(difficulty.dropIntervalMillis(level));
	}

	protected void addScore(int POINT) {
		this.score += POINT;
		this.gamepanel.publishScore(this.score);
		this.gamepanel.drawScore();
	}

	protected boolean isgameover() {
		for (int i = 0; i < next.height(); i++) {
			for (int j = 0; j < next.width(); j++) {
				if (board[i][j+3] + next.getShape(j, i) > 1) {
					if (timer != null) {
						timer.stop();
                        stopAnimations();
						timer = null;
						gamepanel.gameOver();
					}
					return true;
				}
			}
		}
		return false;
	}

	@Override 
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (textChunks.isEmpty()) return;

		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// 1. 현재 설정된 보더의 두께(여백) 정보 가져오기
		Insets insets = getInsets();
        int availableWidth = getWidth() - insets.left - insets.right;
        int availableHeight = getHeight() - insets.top - insets.bottom;

        if (availableWidth <= 0 || availableHeight <= 0) return;

        // 2. 기준이 될 대형 고정 폰트 설정 (100포인트)
        Font baseFont = new Font("Monospaced", Font.BOLD, 100);
        FontMetrics fm = g2d.getFontMetrics(baseFont);

        // 3. [개행 호환 핵심] 가상의 100포인트 상태에서 전체 다중 행의 가로/세로 총 크기 계산
        int maxOriginalWidth = fm.stringWidth(" ".repeat(WIDTH+2));
        int singleLineHeight = (int) (fm.getHeight() * 0.8);
        int totalOriginalHeight = singleLineHeight * (HEIGHT+2); // 모든 줄의 높이 합산

        // 4. 컴포넌트 실제 크기 대비 늘려야 할 비율 계산
        double scaleX = (double) availableWidth / maxOriginalWidth;
        double scaleY = (double) availableHeight / totalOriginalHeight;

        // 5. 그래픽 변형(Scale) 적용 및 그리기
        AffineTransform oldTransform = g2d.getTransform();
        g2d.setFont(baseFont);
        g2d.setColor(changeColor(Color.BLACK));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.translate(insets.left, insets.top); // 원점 이동
        g2d.scale(scaleX, scaleY);               // 통째로 확대/축소

        // 6. 반복문을 돌며 한 줄씩 Y 좌표를 내려가며 그리기
        int currentY = fm.getAscent(); 
		int currentX = 0;
        for (TextChunk chunk : textChunks) {
			if ("\n".equals(chunk.text)) {
				currentX = 0; // 한 줄 안에서 글자가 이어질 X 좌표 변수
				currentY += singleLineHeight;
				continue;
			}
			g2d.setColor(changeColor(chunk.color)); // 콕 집은 그 색상으로 변경 🎨
			g2d.drawString(chunk.text, currentX, currentY); // 그리기
			currentX += fm.stringWidth(chunk.text); 
        }
        if (!clearingRows.isEmpty()) {
            g2d.setColor(new Color(255, 255, 255, 160));
            for (int row = clearingRows.nextSetBit(0); row >= 0; row = clearingRows.nextSetBit(row + 1)) {
                g2d.fillRect(fm.stringWidth(" "), (row + 1) * singleLineHeight,
                    fm.stringWidth(" ".repeat(WIDTH)), singleLineHeight);
            }
        }
        g2d.setTransform(oldTransform); // 그래픽 상태 복원
    }

	public void setText(String text) {
		if (text == null || text.isEmpty()) {
			this.textChunks = new ArrayList<>();
		} else {
			for (String str : text.split("")) {
				textChunks.add(new TextChunk(str, Color.WHITE));
			}
		}
		repaint();
	}

	public class TextChunk {
		String text = " ";
		Color color = Color.WHITE;

		public TextChunk(String text, Color color) {
			this.text = text;
			this.color = color;
		}
	}

	protected int getScore() {
		return score;
	}

	protected Block getNextBlock() {
		return next;
	}

	protected boolean getIsPaused() {
		return isPaused;
	}
}
