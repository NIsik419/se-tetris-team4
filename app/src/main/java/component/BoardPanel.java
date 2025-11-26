package component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import component.board.KeyBindingInstaller;
import component.config.Settings;
import component.items.ColorBombItem;
import component.items.LightningItem;
import component.items.LineClearItem;
import component.items.SpinLockItem;
import component.items.WeightItem;
import component.score.NameInputOverlay;
import component.score.ScoreBoard;
import component.score.ScoreboardOverlay;
import component.sidebar.NextBlockPanel;
import logic.BoardLogic;
import logic.SoundManager;
import logic.SoundManager.BGM;

/**
 * BoardPanel
 * - BoardView(보드 렌더링), HUD(스코어/레벨/라인), Overlay(이름입력/스코어보드), PausePanel을 관리
 * - GameFrame 또는 VersusFrame 등 어디에도 붙일 수 있도록 독립형 구성
 */
public class BoardPanel extends JPanel {
    private BoardLogic logic;
    private BoardView boardView = null;
    private GameLoop loop = null;

    private final JLabel scoreLabel = new JLabel("0");
    private final JLabel levelLabel = new JLabel("1");
    private final JLabel linesLabel = new JLabel("0");
    private final NextBlockPanel nextPanel = new NextBlockPanel(95);

    private final ScoreBoard scoreBoard = ScoreBoard.createDefault();
    private PausePanel pausePanel;
    private JPanel overlay;
    private JPanel dialogPanel;
    private NameInputOverlay nameInputOverlay;
    private ScoreboardOverlay scoreboardOverlay;
    public SoundManager soundManager;

    private final GameConfig config;
    private Settings settings;
    private boolean restarting = false;
    private final Runnable onExitToMenu;
    private java.util.function.Consumer<Integer> onGameOver;

    /** 기본 생성자: 키맵(화살표/Space/P) 사용 */
    public BoardPanel(GameConfig config, Runnable onExitToMenu) {
        this(config, onExitToMenu, false, true, null);
    }

    // WASD 모드 / P1용 생성자
    public BoardPanel(GameConfig config,
            Runnable onExitToMenu,
            boolean wasMode,
            Consumer<Integer> onGameOver) {
        this(config, onExitToMenu, wasMode, true, onGameOver);
    }

    /** 오버로드: wasMode=true면 키맵(WASD/F/R) 사용 */
    public BoardPanel(GameConfig config, Runnable onExitToMenu, boolean wasMode,
            boolean enableControls, java.util.function.Consumer<Integer> onGameOver) {
        this.config = config;
        this.onExitToMenu = onExitToMenu;
        this.onGameOver = onGameOver;

        // === 기본 패널 설정 ===
        setLayout(new BorderLayout(10, 0));
        setBackground(new Color(20, 25, 35));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // === 로직 초기화 ===
        this.logic = new BoardLogic(score -> {
            soundManager.stopBGM();
            if (this.onGameOver != null) {
                // 대전 모드: 외부 매니저로 승패 전달
                this.onGameOver.accept(score);
            } else {
                loop.stopLoop();
                SwingUtilities.invokeLater(() -> {
                    boardView.triggerGameOverAnimation(() -> {
                        // 애니메이션 끝 → 점수 표시
                        boardView.showGameOverStats(
                                logic.getScore(),
                                logic.getLinesCleared(),
                                logic.getLevel(),
                                () -> {
                                    // 점수 표시 끝 → 이름 입력창
                                    showNameInputOverlay(score);
                                });
                    });
                }); 
            }
        });

        this.soundManager = SoundManager.getInstance();
        if (config.mode() == GameConfig.Mode.ITEM) {
            soundManager.playBGM(BGM.GAME_ITEM);
        } else {
            soundManager.playBGM(BGM.GAME_NORMAL);
        }
        if (config.mode() == GameConfig.Mode.ITEM) {
            logic.setItemMode(true);
        }

        this.boardView = new BoardView(logic);
        this.loop = new GameLoop(logic, boardView::repaint);

        // 루프 제어 콜백 연결
        logic.setLoopControl(loop::pauseLoop, loop::resumeLoop);

        // ClearService 애니메이션용: 가벼운 repaint만
        logic.setOnFrameUpdate(() -> {
            SwingUtilities.invokeLater(() -> {
                boardView.repaint(); // ← 보드만 빠르게 갱신
            });
        });

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

        // === HUD 업데이트 타이머 (기존 drawBoard 역할) ===
        Timer hudUpdateTimer = new Timer(100, e -> {
            if (!logic.isGameOver()) {
                SwingUtilities.invokeLater(() -> {
                    scoreLabel.setText(String.valueOf(logic.getScore()));
                    levelLabel.setText(String.valueOf(logic.getLevel()));
                    linesLabel.setText(String.valueOf(logic.getLinesCleared()));
                });
            }
        });
        hudUpdateTimer.start();

        // === 초기 포커스 및 루프 시작 ===
        if (enableControls) {
            boardView.setFocusable(true);
            boardView.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> {
                boardView.setFocusable(true);
                boardView.requestFocusInWindow();
                boardView.requestFocus();
            });
            System.out.println("[DEBUG] Focus requested on boardView");
        } else {
            // AI는 절대 포커스 금지
            boardView.setFocusable(false);
            boardView.setRequestFocusEnabled(false);

            boardView.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).clear();
            boardView.getActionMap().clear();
        }
        loop.startLoop();

        // === 키 바인딩 통합 ===
        KeyBindingInstaller installer = new KeyBindingInstaller();

        KeyBindingInstaller.Deps deps = new KeyBindingInstaller.Deps(
                logic,
                boardView::repaint, // 보드 갱신
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
                    // nextPanel.setColorMode(mode);
                },

                // onColorModeChanged: Settings 에 저장
                mode -> {
                    if (settings != null) {
                        settings.colorBlindMode = mode;
                    }
                });
        if (enableControls) {
            if (wasMode) {
                installer.install(boardView, deps, KeyBindingInstaller.KeySet.WASD, false, false);
            } else {
                installer.install(boardView, deps, KeyBindingInstaller.KeySet.ARROWS, true, false);
            }
        } else {
            boardView.setFocusable(false);
        }

        if (enableControls) {
            bindPauseKey();
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
                                    restarting = true;
                                    loop.stopLoop();
                                    onExitToMenu.run();
                                },
                                () -> { // EXIT
                                    restarting = false;
                                    loop.stopLoop();
                                    onExitToMenu.run();
                                });
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
                    restarting = true;
                    loop.stopLoop();
                    JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                    if (frame != null)
                        frame.dispose();
                    new GameFrame(config, false, false, null); // 싱글 모드 새 게임 시작
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

    // 기존 drawBoard는 외부 호출용으로만 사용
    private void drawBoard() {
        SwingUtilities.invokeLater(() -> {
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

    private void bindPauseKey() {
        // boardView 기준으로 WHEN_IN_FOCUSED_WINDOW에 바인딩
        InputMap im = boardView.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = boardView.getActionMap();

        im.put(KeyStroke.getKeyStroke("P"), "togglePause");
        // 원하면 ESC도 같이 묶을 수 있음
        // im.put(KeyStroke.getKeyStroke("ESCAPE"), "togglePause");

        am.put("togglePause", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                togglePause();
            }
        });
    }

    // === Pause 토글 ===
    private void togglePause() {
        if (pausePanel == null) {
            loop.pauseLoop(); // 최소한 루프는 멈추게
            System.out.println("[WARN] togglePause() called before PausePanel init");
            soundManager.pauseBGM();
            return;
        }
        if (pausePanel.isVisible()) {
            loop.resumeLoop();
            soundManager.resumeBGM();
            pausePanel.hidePanel();
        } else {
            loop.pauseLoop();
            soundManager.pauseBGM();
            pausePanel.showPanel();
        }
    }

    // === Getter ===
    public BoardLogic getLogic() {
        return logic;
    }

    public boolean isRestarting() {
        return restarting;
    }

    public void markRestarting() {
        restarting = true;
    }

    public void applySettings(Settings s) {
        this.settings = s;
        if (s == null)
            return;
        boardView.setColorMode(s.colorBlindMode);

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
    // BoardPanel.java

    public void pauseGame() {
        // 1. 렌더링 타이머 정지
        if (boardView != null) {
            boardView.pauseRendering();
        }

        // 2. 게임 루프 정지
        if (loop != null) {
            loop.pauseLoop();
        }

        System.out.println("[PAUSE] Game paused");
    }

    public void resumeGame() {
        // 1. 렌더링 타이머 재개
        if (boardView != null) {
            boardView.resumeRendering();
        }

        // 2. 게임 루프 재개
        if (loop != null && !logic.isGameOver()) {
            loop.resumeLoop();
        }

        System.out.println("[RESUME] Game resumed");
    }

    public void stopGame() {
        if (boardView != null) {
            boardView.stopRendering();
            boardView.cleanup();
        }
        if (loop != null) {
            loop.stopLoop();
        }
        soundManager.stopBGM();

        System.out.println("[STOP] Game stopped");
    }
}
