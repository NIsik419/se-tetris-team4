package component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

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

    private boolean showHUD;
    private boolean enableControls = true;
    private boolean wasMode;

    private final ScoreBoard scoreBoard = ScoreBoard.createDefault();
    private PausePanel pausePanel;
    private JPanel overlay;
    private JPanel dialogPanel;
    private NameInputOverlay nameInputOverlay;
    private ScoreboardOverlay scoreboardOverlay;
    public SoundManager soundManager;

    private KeyBindingInstaller installer;
    private KeyBindingInstaller.Deps keyDeps;
    private final boolean useCustomKeymap = true;

    private final GameConfig config;
    private Settings settings;
    private boolean restarting = false;
    private final Runnable onExitToMenu;
    private java.util.function.Consumer<Integer> onGameOver;

// ================= UI THEME CONSTANTS =================
    private static final Color BG_MAIN = new Color(20, 25, 35);         
    private static final Color BG_HUD  = new Color(24, 30, 44);         
    private static final Color BG_STAT = new Color(30, 35, 50);         
    private static final Color TEXT_MUTED = new Color(136, 146, 176);    
    private static final Color TEXT_HINT  = new Color(130, 140, 160);    
    private static final Color ACCENT_CYAN = new Color(100, 255, 218); 


    /** 기본 생성자: 키맵(화살표/Space/P) 사용 */
    public BoardPanel(GameConfig config, Runnable onExitToMenu) {
        this(config, onExitToMenu, false, true, null, true, true);
    }

    // WASD 모드 / P1용 생성자
    public BoardPanel(GameConfig config,
            Runnable onExitToMenu,
            boolean wasMode,
            java.util.function.Consumer<Integer> onGameOver) {
        this(config, onExitToMenu, wasMode, true, onGameOver, (onGameOver == null), true);
    }

    /** 오버로드: wasMode=true면 키맵(WASD/F/R) 사용 */
    public BoardPanel(GameConfig config,
            Runnable onExitToMenu,
            boolean wasMode,
            boolean enableControls,
            java.util.function.Consumer<Integer> onGameOver,
            boolean startBGM,
            boolean showHUD) {
        this.config = config;
        this.onExitToMenu = onExitToMenu;
        this.wasMode = wasMode;
        this.enableControls = enableControls;
        this.onGameOver = onGameOver;
        this.soundManager = SoundManager.getInstance();
        this.showHUD = showHUD;

        // === 기본 패널 설정 ===
        setLayout(new BorderLayout(10, 0));
        setBackground(BG_MAIN);   
        setOpaque(true);     
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
        }, config.difficulty());

        this.soundManager = SoundManager.getInstance();
        if (startBGM) {
            if (config.mode() == GameConfig.Mode.ITEM) {
                soundManager.playBGM(BGM.GAME_ITEM);
            } else {
                soundManager.playBGM(BGM.GAME_NORMAL);
            }
        }

        if (config.mode() == GameConfig.Mode.ITEM) {
            logic.setItemMode(true);
        }

        Settings loadedSettings = Settings.load();
        this.settings = loadedSettings;
        loadedSettings.onChange(updatedSettings -> {
            SwingUtilities.invokeLater(() -> {
                applySettings(updatedSettings);
            });
        });
        this.boardView = new BoardView(logic, settings);
        logic.setBoardView(boardView);
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

        if (showHUD) {
            add(createHUDPanel(), BorderLayout.EAST);
        }

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
        this.installer = new KeyBindingInstaller();

        this.keyDeps = new KeyBindingInstaller.Deps(
                logic,
                boardView::repaint,
                () -> {
                    JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                    if (frame != null)
                        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                },
                () -> { // ESC → 메뉴 복귀
                    onExitToMenu.run();
                },
                () -> pausePanel != null && pausePanel.isVisible(),
                () -> { if (pausePanel != null) pausePanel.showPanel(); },
                () -> { if (pausePanel != null) pausePanel.hidePanel(); },
                loop::resumeLoop,
                loop::pauseLoop,
                title -> {
                    JFrame f = (JFrame) SwingUtilities.getWindowAncestor(this);
                    if (f != null) f.setTitle(title);
                },
                () -> settings != null ? settings.colorBlindMode : ColorBlindPalette.Mode.NORMAL,
                mode -> {
                    boardView.setColorMode(mode);
                    nextPanel.setColorMode(mode);
                },
                mode -> {
                    if (settings != null) {
                        settings.colorBlindMode = mode;
                    }
                });

        if (enableControls) {
            // onGameOver == null 이면 "일반 1인용"이라고 가정
            boolean isSinglePlayer = (onGameOver == null);

            if (isSinglePlayer) {
                // 설정 기반 커스텀 키맵 사용
                installer.installCustom(
                        boardView,
                        keyDeps,
                        settings.keymap,
                        /* enableDebug */ true,
                        /* enablePauseKey */ false  // P는 BoardPanel.bindPauseKey()에서 처리
                );
                
            } else {
                // 멀티/Versus 모드는 기존 프리셋 유지
                if (wasMode) {
                    installer.install(boardView, keyDeps,
                            KeyBindingInstaller.KeySet.WASD,
                            /* enableDebug */ false,
                            /* enablePauseKey */ false);
                } else {
                    installer.install(boardView, keyDeps,
                            KeyBindingInstaller.KeySet.ARROWS,
                            /* enableDebug */ true,
                            /* enablePauseKey */ false);
                }
            }
        } else {
            boardView.setFocusable(false);
        }

        if (enableControls) {
            bindPauseKey();
        }
   // ==== Screen resize support (maintain board center + maintain HUD spacing) ==== 
        addComponentListener(new ComponentAdapter() {                    
            @Override                                                  
            public void componentResized(ComponentEvent e) {            
                revalidate();                                          
                repaint();                                              
            }                                                          
        });                                                             
    }

    // 중앙에 BoardView를 넣고 비율 유지
    private Component centerBoard(JComponent view) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(BG_MAIN);  
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
        hud.setBackground(BG_HUD);                      
        hud.setBorder(new EmptyBorder(10, 16, 10, 16)); 


        JLabel title = new JLabel("TETRIS");
        title.setFont(new Font("Arial", Font.BOLD, 30));        
        title.setForeground(ACCENT_CYAN);                 
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(title);
        hud.add(Box.createRigidArea(new Dimension(0, 15)));

        JLabel nextLabel = new JLabel("NEXT");
        nextLabel.setFont(new Font("Arial", Font.BOLD, 16));  
        nextLabel.setForeground(Color.WHITE);
        nextLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(nextLabel);
        hud.add(Box.createRigidArea(new Dimension(0, 8)));

        // Next panel wrapper (높이 제한)
        JPanel nextWrapper = new JPanel(new BorderLayout());
        nextWrapper.setBackground(BG_HUD);

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
        controls.setForeground(TEXT_HINT);          
        controls.setAlignmentX(Component.CENTER_ALIGNMENT);
        hud.add(Box.createRigidArea(new Dimension(0, 20)));
        hud.add(controls);
        hud.add(Box.createVerticalGlue());
        return hud;
    }

    private JPanel createStatPanel(String label, JLabel value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_STAT);    
        p.setBorder(new EmptyBorder(10, 20, 10, 20));
        p.setMaximumSize(new Dimension(180, 70));

        JLabel name = new JLabel(label);
        name.setFont(new Font("Arial", Font.BOLD, 12));
        name.setForeground(TEXT_MUTED);   
        name.setAlignmentX(Component.CENTER_ALIGNMENT);

        value.setFont(new Font("Consolas", Font.BOLD, 24));     // EDITED (숫자 가독성 ↑)
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
                                    soundManager.resumeBGM();
                                },
                                () -> {
                                    restarting = true;
                                    loop.stopLoop();
                                    soundManager.stopBGM();
                                    onExitToMenu.run();
                                },
                                () -> { // EXIT
                                    restarting = false;
                                    loop.stopLoop();
                                    soundManager.stopBGM();
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
        // 전체 딤 처리용 오버레이 패널
        overlay = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(0, 0, 0, 150));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        overlay.setOpaque(false);
        overlay.setVisible(false);

        dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setOpaque(false);
        dialogPanel.setPreferredSize(null);
        overlay.add(dialogPanel);

        // LayeredPane 추가는 프레임 attach 시
        addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && isDisplayable()) {
                    JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(BoardPanel.this);
                    if (frame != null) {
                        frame.getLayeredPane().add(overlay, JLayeredPane.POPUP_LAYER);
                        relayoutDialog();
                        removeHierarchyListener(this);
                    }
                }
            }
        });

        // 이름 입력 오버레이
        nameInputOverlay = new NameInputOverlay(
                dialogPanel,
                scoreBoard,
                this::showScoreboardOverlay,
                () -> {
                    hideOverlay();
                    // 취소 시 메인으로
                    onExitToMenu.run();
                });

        // 스코어보드 오버레이
        scoreboardOverlay = new ScoreboardOverlay(
                dialogPanel,
                scoreBoard,
                () -> {
                    if (restarting)
                        return;
                    restarting = true;

                    hideOverlay();
                    loop.stopLoop();
                    soundManager.stopBGM();

                    java.awt.Window w = SwingUtilities.getWindowAncestor(BoardPanel.this);
                    if (w instanceof GameFrame gf) {
                        gf.markRestartRequested();
                        gf.dispose();
                    }
                },
                () -> {
                    hideOverlay();
                    // 메인으로
                    onExitToMenu.run();
                });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayoutDialog();
            }
        });
    }

    // dialogPanel을 현재 내용(preferredSize)에 맞춰 중앙에 배치
    private void relayoutDialog() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame == null)
            return;

        int w = frame.getWidth();
        int h = frame.getHeight();
        overlay.setBounds(0, 0, w, h);

        Dimension pref = dialogPanel.getPreferredSize();
        if (pref == null || pref.width <= 0 || pref.height <= 0) {
            // 안전빵 기본값 (이름 입력창 정도 크기)
            pref = new Dimension(320, 180);
        }

        dialogPanel.setBounds(
                (w - pref.width) / 2,
                (h - pref.height) / 2,
                pref.width,
                pref.height);
    }

    // === Overlay 제어 ===
    private void showNameInputOverlay(int finalScore) {
        // 이전 내용 지우고 (혹시 스코어보드 등이 들어있을 수 있으니)
        dialogPanel.removeAll();
        dialogPanel.revalidate();
        dialogPanel.repaint();

        // 오버레이 표시
        overlay.setVisible(true);

        // 실제 이름 입력 모달 보여주기
        nameInputOverlay.show(finalScore, config.mode(), config.difficulty());

        // 내용이 채워진 뒤, 이제 크기가 생겼으니까 중앙 재배치
        relayoutDialog();
    }

    private void showScoreboardOverlay(int highlightIndex) {
        dialogPanel.removeAll();
        dialogPanel.revalidate();
        dialogPanel.repaint();

        overlay.setVisible(true);

        scoreboardOverlay.show(highlightIndex, config.mode(), config.difficulty());

        // 테이블 크기 기준으로 다시 중앙 배치
        relayoutDialog();
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

    // BoardView에 Settings 전달
    if (boardView != null) {
        boardView.updateSettings(s);
        boardView.setColorMode(s.colorBlindMode);
    }

    // 부모 컨테이너 갱신
    revalidate();
    repaint();

    // JFrame 리사이즈
    SwingUtilities.invokeLater(() -> {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            Settings.ScreenSize screenSize = s.screenSize;        
            if (screenSize != null) {                            
                Dimension d = screenSize.toDimension();          
                frame.setMinimumSize(d);                         
                frame.setSize(d);                                 
            } else {                                             
                frame.pack();                                    
            }
            frame.setLocationRelativeTo(null);                    
        this.settings = s;
        if (s == null) return;

        if (boardView != null) {
            boardView.updateSettings(s);
            boardView.setColorMode(s.colorBlindMode);

            // 🔥 현재 게임에도 키 변경 즉시 적용
            if (enableControls /* && useCustomKeymap 같은 조건 */) {
                installer.installCustom(
                        boardView,
                        keyDeps,
                        s.keymap,
                        /* enableDebug */ true,
                        /* enablePauseKey */ false
                );
            }
        }

        // NEXT 패널
        if (nextPanel != null) {
            nextPanel.setColorMode(s.colorBlindMode);
        }

        revalidate();
        repaint();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
            if (frame != null) {
                frame.pack();
                frame.setLocationRelativeTo(null);
            }
        });
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

    public void cleanup() {
        System.out.println("[BoardPanel] Starting cleanup...");

        // 1. 게임 루프 정지
        if (loop != null) {
            loop.cleanup();
        }

        // 2. BGM/효과음 정지
        if (soundManager != null) {
            soundManager.stopBGM();
        }

        // 3. 타이머들 정지
        if (boardView != null) {
            boardView.stopRendering();
        }

        // 4. 키보드 리스너 제거
        for (var listener : getKeyListeners()) {
            removeKeyListener(listener);
        }

        System.out.println("[BoardPanel] Cleanup completed");
    }

    // BoardPanel.java에 추가
    public void hidePausePanel() {
        if (pausePanel != null && pausePanel.isVisible()) {
            pausePanel.hidePanel();
        }
    }

}
