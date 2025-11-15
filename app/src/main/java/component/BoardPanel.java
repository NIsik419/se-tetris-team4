package component;

import logic.BoardLogic;
import component.config.Settings;
import component.score.ScoreBoard;
import component.items.*;
import component.ColorBlindPalette;
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
    private java.util.function.Consumer<Integer> onGameOver;

    /** 기본 생성자: 키맵(화살표/Space/P) 사용 */
    public BoardPanel(GameConfig config, Runnable onExitToMenu) {
        this(config, onExitToMenu, false, null);
    }

    /** 오버로드: wasMode=true면 키맵(WASD/F/R) 사용 */
    public BoardPanel(GameConfig config, Runnable onExitToMenu, boolean wasMode,
            java.util.function.Consumer<Integer> onGameOver) {
        this.config = config;
        this.onExitToMenu = onExitToMenu;
        this.onGameOver = onGameOver;

        // === 기본 패널 설정 ===
        setLayout(new BorderLayout(10, 0));
        setBackground(new Color(20, 25, 35));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // === 로직 초기화 ===
        this.logic = new BoardLogic(score -> {
            if (this.onGameOver != null) {
                // 대전 모드: 외부 매니저로 승패 전달
                this.onGameOver.accept(score);
            } else {
                // 싱글 모드: 이름 입력/스코어보드
                showNameInputOverlay(score);
            }
        });

        if (config.mode() == GameConfig.Mode.ITEM) {
            logic.setItemMode(true);
        }

        this.boardView = new BoardView(logic);
        this.loop = new GameLoop(logic, this::drawBoard);

        // 루프 제어 콜백 연결
        logic.setLoopControl(loop::pauseLoop, loop::resumeLoop);

        // 프레임 업데이트 콜백(사용 중이면 유지)
        logic.setOnFrameUpdate(this::drawBoard);

        // NEXT 큐 변경 시 HUD 갱신
        logic.setOnNextQueueUpdate(blocks -> {
            System.out.println("[DEBUG] onNextQueueUpdate fired, blocks=" + blocks.size());
            SwingUtilities.invokeLater(() -> {
                nextPanel.setBlocks(blocks);
                nextPanel.repaint();
            });
        });

        // 첫 렌더에서도 NEXT 보장
        SwingUtilities.invokeLater(() -> nextPanel.setBlocks(logic.getNextBlocks()));

        // === 레이아웃 구성 ===
        add(centerBoard(boardView), BorderLayout.CENTER);
        add(createHUDPanel(), BorderLayout.EAST);

        // === 보조 UI 초기화 ===
        initPausePanel();
        initOverlay();

        // === (중복 방지) 디버그 아이템 키는 KeyBindingInstaller에서 설치하므로 비활성화 ===
        // bindDebugKeys();

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

        KeyBindingInstaller.Deps deps = new KeyBindingInstaller.Deps(
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
                loop::resumeLoop, // 일시정지 해제 시 루프 재개
                loop::pauseLoop, // 일시정지 시 루프 정지
                title -> { // 타이틀 설정
                    JFrame f = (JFrame) SwingUtilities.getWindowAncestor(this);
                    if (f != null)
                        f.setTitle(title);
                },
                () -> settings != null ? settings.colorBlindMode : ColorBlindPalette.Mode.NORMAL, // 현재 색맹모드
                mode -> {
                    boardView.setColorMode(mode);
                    nextPanel.setColorMode(mode);
                },

                // onColorModeChanged: Settings 에 저장
                mode -> {
                    if (settings != null) {
                        settings.colorBlindMode = mode;
                    }
                });

        if (wasMode) {
            installer.install(boardView, deps, KeyBindingInstaller.KeySet.WASD, false, false); // P1 (WASD)
        } else {
            installer.install(boardView, deps, KeyBindingInstaller.KeySet.ARROWS, true, false); // P2 (방향키)
        }
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

        JLabel nextLabel = new JLabel("NEXT");
        nextLabel.setFont(new Font("Arial", Font.BOLD, 18));
        nextLabel.setForeground(Color.WHITE);
        nextLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(nextLabel);
        hud.add(Box.createRigidArea(new Dimension(0, 8)));

        // Next panel wrapper (높이 제한)
        JPanel nextWrapper = new JPanel(new BorderLayout());
        nextWrapper.setBackground(new Color(20, 25, 35));

        // 원하는 높이 지정
        int nextHeight = 110;

        nextWrapper.setPreferredSize(new Dimension(200, nextHeight));
        nextWrapper.setMaximumSize(new Dimension(200, nextHeight));
        nextWrapper.setMinimumSize(new Dimension(200, nextHeight));

        nextWrapper.add(nextPanel, BorderLayout.CENTER);
        hud.add(nextWrapper);

        // 아래 여백
        hud.add(Box.createRigidArea(new Dimension(0, 20)));

        hud.add(createStatPanel("SCORE", scoreLabel));
        hud.add(Box.createRigidArea(new Dimension(0, 10)));
        hud.add(createStatPanel("LEVEL", levelLabel));
        hud.add(Box.createRigidArea(new Dimension(0, 10)));
        hud.add(createStatPanel("LINES", linesLabel));
        hud.add(Box.createRigidArea(new Dimension(0, 15)));

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
                                    new GameFrame(config, false, false);
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
                    new GameFrame(config, false, false);
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

    public void startLoop() {
        loop.startLoop();
    }

    public void stopLoop() {
        if (loop != null)
            loop.stopLoop();
    }

    public void pauseLoop() {
        if (loop != null)
            loop.pauseLoop();
    }

}
