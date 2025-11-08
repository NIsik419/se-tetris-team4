package component;

import logic.BoardLogic;
import component.config.Settings;
import component.score.ScoreBoard;
import component.items.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import component.score.NameInputOverlay;
import component.score.ScoreboardOverlay;
import component.sidebar.*;
import component.board.KeyBindingInstaller;

/**
 * BoardPanel
 * - BoardView(보드 렌더링), HUD(스코어/레벨/라인), Overlay(이름입력/스코어보드), PausePanel을 관리
 * - GameFrame 또는 VersusFrame 등 어디에도 붙일 수 있도록 독립형 구성
 */
public class BoardPanel extends JPanel {
    private final BoardLogic logic;
    private final BoardView boardView;
    private final GameLoop loop;

    private final JLabel scoreLabel = new JLabel("0");
    private final JLabel levelLabel = new JLabel("1");
    private final JLabel linesLabel = new JLabel("0");
    private final NextPreviewPanel nextPanel = new NextPreviewPanel();

    private final ScoreBoard scoreBoard = ScoreBoard.createDefault();
    private PausePanel pausePanel;
    private JPanel overlay;
    private JPanel dialogPanel;
    private NameInputOverlay nameInputOverlay;
    private ScoreboardOverlay scoreboardOverlay;

    private final GameConfig config;
    private Settings settings;
    private boolean isRestarting = false;
    private final Runnable onExitToMenu;

    public BoardPanel(GameConfig config, Runnable onExitToMenu) {
        this.config = config;
        this.onExitToMenu = onExitToMenu;

        // === 기본 패널 설정 ===
        setLayout(new BorderLayout(10, 0));
        setBackground(new Color(20, 25, 35));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // === 로직 초기화 ===
        logic = new BoardLogic(score -> showNameInputOverlay(score));
        boardView = new BoardView(logic);

        loop = new GameLoop(logic, this::drawBoard);
        logic.setLoopControl(loop::pauseLoop, loop::resumeLoop);

        // === 블록 큐 업데이트 콜백 등록 ===
        logic.setOnNextQueueUpdate(blocks -> SwingUtilities.invokeLater(() -> nextPanel.setBlocks(blocks)));

        // 첫 NEXT 표시 보장
        SwingUtilities.invokeLater(() -> nextPanel.setBlocks(logic.getNextBlocks()));

        // === 레이아웃 구성 ===
        add(centerBoard(boardView), BorderLayout.CENTER);
        add(createHUDPanel(), BorderLayout.EAST);

        // === 보조 UI 초기화 ===
        initPausePanel();
        initOverlay();

        // === 디버그 아이템 바인딩 ===
        bindDebugKeys();

        // === 초기 포커스 및 루프 시작 ===
        boardView.setFocusable(true);
        boardView.requestFocusInWindow();
        SwingUtilities.invokeLater(() -> {
            boardView.setFocusable(true);
            boardView.requestFocusInWindow();
            boardView.requestFocus();
            System.out.println("[DEBUG] Focus forced on boardView → " + boardView.isFocusOwner());
        });
        System.out.println("[DEBUG] Focus requested on boardView");
        loop.startLoop();
        // === 키 바인딩 통합 ===
        KeyBindingInstaller installer = new KeyBindingInstaller();
        System.out.println("[DEBUG] Installing key bindings for " + boardView.getClass().getSimpleName());
        System.out.println("[DEBUG] InputMap keys = " + boardView.getInputMap().keys());
        installer.install(boardView, new KeyBindingInstaller.Deps(
                logic,
                this::drawBoard, // 보드 갱신
                () -> { // 풀스크린 (현재 없음)
                    JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                    if (frame != null)
                        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                },
                () -> { // ESC → 메뉴 복귀
                    onExitToMenu.run();
                },
                () -> pausePanel != null && pausePanel.isVisible(), // 현재 일시정지 여부
                () -> {
                    if (pausePanel != null)
                        pausePanel.showPanel();
                }, // 일시정지 ON
                () -> {
                    if (pausePanel != null)
                        pausePanel.hidePanel();
                }, // 일시정지 OFF
                loop::resumeLoop, // 게임 재개
                loop::pauseLoop, // 게임 정지
                title -> { // 타이틀 설정
                    JFrame f = (JFrame) SwingUtilities.getWindowAncestor(this);
                    if (f != null)
                        f.setTitle(title);
                },
                () -> settings != null ? settings.colorBlindMode : ColorBlindPalette.Mode.NORMAL, // 현재 색맹모드
                mode -> boardView.setColorMode(mode), // 색맹모드 변경 시
                mode -> nextPanel.setColorMode(mode) // nextPanel 동기화
        ));

    }

    // 중앙에 BoardView를 넣고 비율 유지
    private Component centerBoard(JComponent view) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(new Color(20, 25, 35));
        wrapper.setFocusable(false); // 포커스 훔치지 않도록
        wrapper.add(view);

        view.setFocusable(true); // view에만 포커스 허용
        view.requestFocusInWindow();
        return wrapper;
    }

    // === HUD 생성 ===
    private JPanel createHUDPanel() {
        JPanel hud = new JPanel();
        hud.setLayout(new BoxLayout(hud, BoxLayout.Y_AXIS));
        hud.setBackground(new Color(20, 25, 35));

        JLabel title = new JLabel("TETRIS");
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(new Color(100, 255, 218));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(title);
        hud.add(Box.createRigidArea(new Dimension(0, 15)));

        hud.add(createStatPanel("SCORE", scoreLabel));
        hud.add(Box.createRigidArea(new Dimension(0, 10)));
        hud.add(createStatPanel("LEVEL", levelLabel));
        hud.add(Box.createRigidArea(new Dimension(0, 10)));
        hud.add(createStatPanel("LINES", linesLabel));
        hud.add(Box.createRigidArea(new Dimension(0, 15)));

        JLabel nextLabel = new JLabel("NEXT");
        nextLabel.setFont(new Font("Arial", Font.BOLD, 18));
        nextLabel.setForeground(Color.WHITE);
        nextLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(nextLabel);
        hud.add(Box.createRigidArea(new Dimension(0, 8)));
        hud.add(nextPanel);
        hud.add(Box.createVerticalGlue());

        JLabel controls = new JLabel("P:Pause | F11:Full | ESC:Exit");
        controls.setFont(new Font("Arial", Font.PLAIN, 11));
        controls.setForeground(new Color(130, 140, 160));
        controls.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(Box.createRigidArea(new Dimension(0, 20)));
        hud.add(controls);
        return hud;
    }

    private JPanel createStatPanel(String label, JLabel value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(30, 35, 50));
        p.setBorder(new EmptyBorder(10, 20, 10, 20));
        p.setMaximumSize(new Dimension(180, 70));

        JLabel name = new JLabel(label);
        name.setFont(new Font("Arial", Font.BOLD, 12));
        name.setForeground(new Color(136, 146, 176));
        name.setAlignmentX(Component.CENTER_ALIGNMENT);

        value.setFont(new Font("Arial", Font.BOLD, 24));
        value.setForeground(Color.WHITE);
        value.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(name);
        p.add(Box.createRigidArea(new Dimension(0, 4)));
        p.add(value);
        return p;
    }

    // === 일시정지 패널 초기화 ===
    private void initPausePanel() {
        addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && isDisplayable()) {
                    JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(BoardPanel.this);
                    if (frame != null) {
                        System.out.println("[DEBUG] PausePanel attach → JFrame detected");
                        pausePanel = new PausePanel(
                                frame,
                                () -> {
                                    loop.resumeLoop();
                                    pausePanel.hidePanel();
                                },
                                () -> {
                                    isRestarting = true;
                                    loop.stopLoop();
                                    frame.dispose();
                                    new GameFrame(config);
                                },
                                onExitToMenu);
                        removeHierarchyListener(this);
                    }
                }
            }
        });
    }

    // === Overlay 초기화 ===
    private void initOverlay() {
        overlay = new JPanel(null);
        overlay.setBackground(new Color(0, 0, 0, 150));
        overlay.setVisible(false);

        dialogPanel = new JPanel(new BorderLayout(8, 8));
        dialogPanel.setBackground(new Color(245, 246, 250));
        dialogPanel.setSize(400, 300);
        overlay.add(dialogPanel);

        addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && isDisplayable()) {
                    JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(BoardPanel.this);
                    if (frame != null) {
                        frame.getLayeredPane().add(overlay, JLayeredPane.POPUP_LAYER);
                        int w = frame.getWidth();
                        int h = frame.getHeight();
                        overlay.setBounds(0, 0, w, h);
                        dialogPanel.setBounds(w / 2 - 200, h / 2 - 150, 400, 300);
                        removeHierarchyListener(this);
                    }
                }
            }
        });

        nameInputOverlay = new NameInputOverlay(
                dialogPanel,
                scoreBoard,
                this::showScoreboardOverlay,
                this::hideOverlay);

        scoreboardOverlay = new ScoreboardOverlay(
                dialogPanel,
                scoreBoard,
                () -> {
                    hideOverlay();
                    isRestarting = true;
                    loop.stopLoop();
                    JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                    if (frame != null)
                        frame.dispose();
                    new GameFrame(config);
                },
                onExitToMenu);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(BoardPanel.this);
                if (frame != null) {
                    int w = frame.getWidth();
                    int h = frame.getHeight();
                    overlay.setBounds(0, 0, w, h);
                    dialogPanel.setBounds(w / 2 - 200, h / 2 - 150, 400, 300);
                }
            }
        });
    }

    // === Overlay 제어 ===
    private void showNameInputOverlay(int score) {
        loop.stopLoop();
        overlay.setVisible(true);
        boardView.repaint();
        nameInputOverlay.show(score, config.mode(), config.difficulty());
    }

    private void showScoreboardOverlay(int highlightIndex) {
        overlay.setVisible(true);
        scoreboardOverlay.show(highlightIndex, config.mode(), config.difficulty());
    }

    private void hideOverlay() {
        overlay.setVisible(false);
    }

    // === 보드 갱신 ===
    private void drawBoard() {
        SwingUtilities.invokeLater(() -> {
            System.out.println("[DEBUG] drawBoard() called - repainting BoardView");
            scoreLabel.setText(String.valueOf(logic.getScore()));
            levelLabel.setText(String.valueOf(logic.getLevel()));
            linesLabel.setText(String.valueOf(logic.getLinesCleared()));
            boardView.repaint();
        });

    }

    // === 디버그 키 ===
    private void bindDebugKeys() {
        bindDebugKey("1", () -> logic.debugSetNextItem(new LineClearItem(logic.getCurr())));
        bindDebugKey("2", () -> logic.debugSetNextItem(new WeightItem()));
        bindDebugKey("3", () -> logic.debugSetNextItem(new SpinLockItem(logic.getCurr())));
        bindDebugKey("4", () -> logic.debugSetNextItem(new ColorBombItem(logic.getCurr())));
        bindDebugKey("5", () -> logic.debugSetNextItem(new LightningItem()));
    }

    private void bindDebugKey(String key, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), "debug_" + key);
        getActionMap().put("debug_" + key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!logic.isItemMode())
                    return;
                action.run();
                drawBoard();
            }
        });
    }

    // === Pause 토글 ===
    private void togglePause() {
        if (pausePanel == null) {
            loop.pauseLoop(); // 최소한 루프는 멈추게
            System.out.println("[WARN] togglePause() called before PausePanel init");
            return;
        }
        if (pausePanel.isVisible()) {
            loop.resumeLoop();
            pausePanel.hidePanel();
        } else {
            loop.pauseLoop();
            pausePanel.showPanel();
        }
    }

    // === Getter ===
    public BoardLogic getLogic() {
        return logic;
    }

    public boolean isRestarting() {
        return isRestarting;
    }

    public void applySettings(Settings s) {
        this.settings = s;
        if (s == null)
            return;
        boardView.setColorMode(s.colorBlindMode);
        nextPanel.setColorMode(s.colorBlindMode);

    }
}
