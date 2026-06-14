import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Chicken Road (v slogu Frogger) - poenostavljena.
 *
 * Piščanec mora prečkati cesto (avtomobili) in nato reko (hlodi),
 * da pride do enega od 5 gnezd na vrhu. Ko zapolniš vsa gnezda,
 * gre igra na naslednji nivo (hitreje).
 *
 * Pravila:
 *  - Na cesti: trk z avtom = izguba življenja
 *  - Na reki: če nisi na hlodu, potoneš = izguba življenja
 *  - Na hlodu te ta nosi s seboj (lahko te odnese tudi čez rob)
 *  - Časovnik: če poteče, izgubiš življenje
 *  - Gnezdo: prazno gnezdo = +100 točk, polno gnezdo = izguba življenja (sršeni v gnezdu)
 *
 * Krmiljenje:
 *  - puščice / WSAD -> premik po mreži (eno polje na pritisk)
 *  - R -> ponoven zagon po koncu igre
 */
public class ChickenRoad extends JPanel implements ActionListener, KeyListener {

    static final int TILE = 48;
    static final int COLS = 13;
    static final int ROWS = 13;
    static final int WIDTH = COLS * TILE;
    static final int HEIGHT = ROWS * TILE;

    static final double BASE_TIME = 20.0; // sekund na poskus

    // Vrstice: 0 = gnezda (cilj), 1-5 = reka, 6 = varni otok, 7-11 = cesta, 12 = start
    static final int[] GOAL_COLS = {1, 3, 6, 9, 11};

    // --- Lane (vrstica s premikajočimi se ovirami) ---
    static class Lane {
        int row;
        String type; // "LOG" ali "CAR"
        int dir;     // -1 levo, +1 desno
        double speed;
        double widthPx;
        double[] pos;
        Color bodyColor, accentColor;

        Lane(int row, String type, int dir, double speed, double widthTiles, int num, Color bodyColor, Color accentColor) {
            this.row = row;
            this.type = type;
            this.dir = dir;
            this.speed = speed;
            this.widthPx = widthTiles * TILE;
            this.bodyColor = bodyColor;
            this.accentColor = accentColor;
            pos = new double[num];
            double spacing = (WIDTH + widthPx) / num;
            double offset = (row * 53) % spacing;
            for (int i = 0; i < num; i++) {
                pos[i] = i * spacing + offset - widthPx;
            }
        }

        void update(double speedMul) {
            double v = dir * speed * speedMul;
            for (int i = 0; i < pos.length; i++) {
                pos[i] += v;
                if (dir > 0 && pos[i] > WIDTH) pos[i] -= (WIDTH + widthPx);
                if (dir < 0 && pos[i] < -widthPx) pos[i] += (WIDTH + widthPx);
            }
        }

        Rectangle.Double rect(double p) {
            return new Rectangle.Double(p + 2, row * TILE + 2, widthPx - 4, TILE - 4);
        }
    }

    Lane[] lanes = new Lane[ROWS]; // null za vrstice brez ovir (0,6,12)

    // --- Igralec ---
    double playerX;
    int playerRow;
    boolean facingRight = true;
    int animTimer = 0;

    // --- Igra ---
    boolean[] goalFilled = new boolean[GOAL_COLS.length];
    int filledCount = 0;
    int level = 1;
    double speedMultiplier = 1.0;
    double timer = BASE_TIME;
    int score = 0;
    int lives = 3;
    boolean gameOver = false;
    boolean win = false; // ni "končne" zmage - igra je neskončna, win se ne uporablja za konec

    Timer gameTimer;

    public ChickenRoad() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);

        initLanes();
        resetPlayer();

        gameTimer = new Timer(16, this); // ~60 FPS
        gameTimer.start();
    }

    void initLanes() {
        // Reka (vrstice 1-5) - hlodi
        lanes[1] = new Lane(1, "LOG", 1, 1.0, 3, 2, new Color(120, 80, 40), new Color(90, 60, 25));
        lanes[2] = new Lane(2, "LOG", -1, 1.6, 2, 3, new Color(120, 80, 40), new Color(90, 60, 25));
        lanes[3] = new Lane(3, "LOG", 1, 0.8, 4, 2, new Color(120, 80, 40), new Color(90, 60, 25));
        lanes[4] = new Lane(4, "LOG", -1, 1.3, 2, 3, new Color(120, 80, 40), new Color(90, 60, 25));
        lanes[5] = new Lane(5, "LOG", 1, 1.1, 3, 2, new Color(120, 80, 40), new Color(90, 60, 25));

        // Cesta (vrstice 7-11) - avtomobili
        lanes[7]  = new Lane(7,  "CAR", -1, 2.2, 1, 3, new Color(200, 40, 40), new Color(255, 255, 255));
        lanes[8]  = new Lane(8,  "CAR", 1,  1.6, 2, 2, new Color(40, 90, 200), new Color(255, 255, 0));
        lanes[9]  = new Lane(9,  "CAR", -1, 3.0, 1, 4, new Color(230, 160, 30), new Color(40, 40, 40));
        lanes[10] = new Lane(10, "CAR", 1,  1.8, 1, 3, new Color(100, 180, 100), new Color(255, 255, 255));
        lanes[11] = new Lane(11, "CAR", -1, 2.5, 2, 2, new Color(150, 60, 200), new Color(255, 255, 0));
    }

    void resetPlayer() {
        playerRow = 12;
        playerX = (COLS / 2) * TILE;
        timer = Math.max(10, BASE_TIME - (level - 1) * 1.5);
    }

    void loseLife() {
        lives--;
        if (lives <= 0) {
            gameOver = true;
        } else {
            resetPlayer();
        }
    }

    void resetGame() {
        score = 0;
        lives = 3;
        level = 1;
        speedMultiplier = 1.0;
        Arrays.fill(goalFilled, false);
        filledCount = 0;
        gameOver = false;
        resetPlayer();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameOver) update();
        repaint();
    }

    void update() {
        animTimer++;

        timer -= 16.0 / 1000.0;
        if (timer <= 0) {
            loseLife();
            return;
        }

        for (Lane l : lanes) {
            if (l != null) l.update(speedMultiplier);
        }

        Rectangle.Double playerRect = new Rectangle.Double(playerX + 5, playerRow * TILE + 5, TILE - 10, TILE - 10);

        if (playerRow >= 1 && playerRow <= 5) {
            // Reka - mora biti na hlodu
            Lane lane = lanes[playerRow];
            boolean onLog = false;
            for (double p : lane.pos) {
                if (lane.rect(p).intersects(playerRect)) {
                    onLog = true;
                    playerX += lane.dir * lane.speed * speedMultiplier;
                    break;
                }
            }
            if (!onLog) {
                loseLife();
                return;
            }
            if (playerX < -TILE * 0.4 || playerX > WIDTH - TILE * 0.6) {
                loseLife();
                return;
            }
        } else if (playerRow >= 7 && playerRow <= 11) {
            // Cesta - pazi na avte
            Lane lane = lanes[playerRow];
            for (double p : lane.pos) {
                if (lane.rect(p).intersects(playerRect)) {
                    loseLife();
                    return;
                }
            }
        }
    }

    // --- Premikanje igralca (na pritisk tipke) ---
    void tryMove(int dRow, int dCol) {
        if (gameOver) return;

        if (dCol != 0) {
            double newX = Math.round(playerX / TILE) * TILE + dCol * TILE;
            if (newX < 0 || newX > (COLS - 1) * TILE) return;
            playerX = newX;
            facingRight = dCol > 0;
            return;
        }

        int newRow = playerRow + dRow;
        if (newRow < 0 || newRow > 12) return;

        if (newRow == 0) {
            int col = (int) Math.round(playerX / TILE);
            int slot = -1;
            for (int i = 0; i < GOAL_COLS.length; i++) {
                if (GOAL_COLS[i] == col) { slot = i; break; }
            }
            if (slot == -1) {
                // ni gnezda - "živa meja", ne moreš skozi
                return;
            }
            if (goalFilled[slot]) {
                // sršeni v zasedenem gnezdu
                loseLife();
                return;
            }
            // uspešno doseženo gnezdo
            goalFilled[slot] = true;
            filledCount++;
            score += 100;
            if (filledCount == GOAL_COLS.length) {
                level++;
                speedMultiplier += 0.25;
                Arrays.fill(goalFilled, false);
                filledCount = 0;
                score += 500; // bonus za zaključen nivo
            }
            resetPlayer();
            return;
        }

        playerRow = newRow;
        playerX = Math.round(playerX / TILE) * TILE; // poravnaj na mrežo
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g2);
        drawObstacles(g2);
        drawGoals(g2);
        drawPlayer(g2);
        drawHUD(g2);

        if (gameOver) drawEndScreen(g2);
    }

    void drawBackground(Graphics2D g2) {
        for (int row = 0; row < ROWS; row++) {
            Color c;
            if (row == 0) c = new Color(60, 140, 60);       // gnezda - trava
            else if (row >= 1 && row <= 5) c = new Color(40, 110, 220); // reka
            else if (row == 6) c = new Color(80, 180, 80);  // varni otok
            else if (row >= 7 && row <= 11) c = new Color(60, 60, 60); // cesta
            else c = new Color(80, 180, 80);                // start

            g2.setColor(c);
            g2.fillRect(0, row * TILE, WIDTH, TILE);

            // Črte na cesti
            if (row >= 7 && row <= 11) {
                g2.setColor(new Color(230, 230, 230));
                for (int x = 0; x < WIDTH; x += 40) {
                    g2.fillRect(x, row * TILE + TILE / 2 - 2, 20, 4);
                }
            }
            // Valovi na reki
            if (row >= 1 && row <= 5) {
                g2.setColor(new Color(70, 140, 240));
                for (int x = 0; x < WIDTH; x += 30) {
                    g2.drawArc(x, row * TILE + TILE / 2 - 5, 20, 10, 0, 180);
                }
            }
        }
    }

    void drawObstacles(Graphics2D g2) {
        for (Lane l : lanes) {
            if (l == null) continue;
            for (double p : l.pos) {
                int x = (int) p;
                int y = l.row * TILE;
                int w = (int) l.widthPx;

                if (l.type.equals("LOG")) {
                    g2.setColor(l.bodyColor);
                    g2.fillRoundRect(x, y + 6, w, TILE - 12, 12, 12);
                    g2.setColor(l.accentColor);
                    for (int i = 0; i < w; i += 12) {
                        g2.drawOval(x + i + 2, y + 10, 8, TILE - 20);
                    }
                } else { // CAR
                    g2.setColor(l.bodyColor);
                    g2.fillRoundRect(x, y + 6, w, TILE - 12, 8, 8);
                    g2.setColor(l.accentColor);
                    g2.fillRect(x + (int) (w * 0.2), y + 12, (int) (w * 0.6), TILE - 28);
                    // luči
                    g2.setColor(Color.WHITE);
                    if (l.dir > 0) g2.fillOval(x + w - 6, y + TILE / 2 - 3, 5, 6);
                    else g2.fillOval(x + 1, y + TILE / 2 - 3, 5, 6);
                }
            }
        }
    }

    void drawGoals(Graphics2D g2) {
        for (int i = 0; i < GOAL_COLS.length; i++) {
            int col = GOAL_COLS[i];
            int x = col * TILE;
            int y = 0;
            g2.setColor(new Color(90, 60, 30));
            g2.fillOval(x + 6, y + 6, TILE - 12, TILE - 12);
            if (goalFilled[i]) {
                drawChicken(g2, x, y, true, true);
            }
        }
        // "Živa meja" med gnezdi
        g2.setColor(new Color(20, 90, 20));
        boolean[] isGoal = new boolean[COLS];
        for (int c : GOAL_COLS) isGoal[c] = true;
        for (int col = 0; col < COLS; col++) {
            if (!isGoal[col]) {
                g2.fillRect(col * TILE + 2, 2, TILE - 4, TILE - 4);
            }
        }
    }

    void drawPlayer(Graphics2D g2) {
        drawChicken(g2, (int) playerX, playerRow * TILE, facingRight, false);
    }

    void drawChicken(Graphics2D g2, int x, int y, boolean faceRight, boolean small) {
        int s = small ? TILE - 16 : TILE - 8;
        int offset = (TILE - s) / 2;
        int cx = x + offset;
        int cy = y + offset;

        // noge (rahla animacija)
        if (!small) {
            g2.setColor(Color.ORANGE);
            int legShift = (animTimer / 8) % 2 == 0 ? 2 : -2;
            g2.fillRect(cx + s / 4, cy + s - 4, 4, 6 + legShift);
            g2.fillRect(cx + s / 2 + s / 4 - 4, cy + s - 4, 4, 6 - legShift);
        }

        // telo
        g2.setColor(Color.WHITE);
        g2.fillOval(cx, cy + s / 4, s, (int) (s * 0.75));

        // glava
        g2.setColor(Color.WHITE);
        int headX = faceRight ? cx + s - s / 3 : cx;
        g2.fillOval(headX, cy, s / 2, s / 2);

        // kljun
        g2.setColor(Color.ORANGE);
        if (faceRight) {
            int[] xs = {headX + s / 2, headX + s / 2 + 8, headX + s / 2};
            int[] ys = {cy + s / 4 - 2, cy + s / 4 + 2, cy + s / 4 + 6};
            g2.fillPolygon(xs, ys, 3);
        } else {
            int[] xs = {headX, headX - 8, headX};
            int[] ys = {cy + s / 4 - 2, cy + s / 4 + 2, cy + s / 4 + 6};
            g2.fillPolygon(xs, ys, 3);
        }

        // greben
        g2.setColor(Color.RED);
        g2.fillOval(headX + s / 4 - 2, cy - 4, 8, 8);

        // oko
        g2.setColor(Color.BLACK);
        int eyeX = faceRight ? headX + s / 3 : headX + s / 6;
        g2.fillOval(eyeX, cy + s / 6, 3, 3);
    }

    void drawHUD(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 16));
        g2.drawString("Točke: " + score, 10, HEIGHT - 10);
        g2.drawString("Življenja: " + lives, 130, HEIGHT - 10);
        g2.drawString("Nivo: " + level, 270, HEIGHT - 10);

        // Časovnik
        int barW = 150;
        int barX = WIDTH - barW - 10;
        int barY = HEIGHT - 18;
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(barX, barY, barW, 10);
        double frac = Math.max(0, timer / Math.max(1, Math.max(10, BASE_TIME - (level - 1) * 1.5)));
        g2.setColor(frac > 0.3 ? Color.GREEN : Color.RED);
        g2.fillRect(barX, barY, (int) (barW * frac), 10);
    }

    void drawEndScreen(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 40));
        String msg = "KONEC IGRE";
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(msg);
        g2.drawString(msg, (WIDTH - textW) / 2, HEIGHT / 2 - 30);

        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        String scoreMsg = "Končni rezultat: " + score + " (nivo " + level + ")";
        int scoreW = g2.getFontMetrics().stringWidth(scoreMsg);
        g2.drawString(scoreMsg, (WIDTH - scoreW) / 2, HEIGHT / 2 + 5);

        String sub = "Pritisni R za ponovni zagon";
        int subW = g2.getFontMetrics().stringWidth(sub);
        g2.drawString(sub, (WIDTH - subW) / 2, HEIGHT / 2 + 35);
    }

    // --- KeyListener ---
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> tryMove(0, -1);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> tryMove(0, 1);
            case KeyEvent.VK_UP, KeyEvent.VK_W -> tryMove(-1, 0);
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> tryMove(1, 0);
            case KeyEvent.VK_R -> { if (gameOver) resetGame(); }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}

    // --- main ---
    public static void main(String[] args) {
        JFrame frame = new JFrame("Chicken Road");
        ChickenRoad game = new ChickenRoad();

        frame.add(game);
        frame.pack();
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}