package versus;

import logic.BoardLogic;

import javax.swing.JComponent;

import component.BoardPanel;
import component.GameConfig;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** 한 명의 플레이어 세션을 감싸는 래퍼 (BoardPanel 기반) */
public class Player {

    public enum Id { P1, P2 }

    public static class Events {
        public Consumer<Integer> onGameOver;                          // 최종 점수
        public IntConsumer       onLineCleared;                       // 이번 턴에 지운 줄 수
        public Consumer<java.util.List<blocks.Block>> onNext;         // NEXT 큐 변경
        // 지워진 줄의 ‘원래 모양(최근 고정 블럭 제외)’을 그대로 전달
        public java.util.function.Consumer<int[]> onLinesClearedWithMasks;
    }

    private final Id id;
    private final GameConfig config;
    private final BoardPanel panel;   // 로직/뷰/루프/키바인딩 포함
    private final BoardLogic logic;   // 편의 참조
    public final Events events;

    // 대전용: 상대에게서 받은 garbage(마스크) 대기열 (맨 앞 = 가장 오래된 = 아래쪽부터 들어갈 줄)
    private final ArrayDeque<Integer> pendingMasks = new ArrayDeque<>();

    public Player(Id id, GameConfig config, Events events, Runnable onExitToMenu, boolean isAI) {
        this.id = id;
        this.config = config;
        this.events = (events != null) ? events : new Events();

        boolean useWASD = (id == Id.P1);
        

        // BoardPanel 내부에서 BoardLogic/Loop/HUD/키바인딩까지 초기화됨
        this.panel = new BoardPanel(
            config,
            onExitToMenu,
            useWASD,
            /* onGameOver(대전용) = */ score -> {
                if (this.events.onGameOver != null) {
                    this.events.onGameOver.accept(score);
                }
            }
        );
        this.logic = panel.getLogic();

        logic.setOnLineCleared(lines -> {
            System.out.printf("[DEBUG %s] cleared %d lines%n", id, lines);
            if (this.events.onLineCleared != null) this.events.onLineCleared.accept(lines);
        });
        // BoardLogic → 외부(versus)로 마스크 이벤트 전달
        logic.setOnLinesClearedWithMasks(masks -> {
            if (this.events.onLinesClearedWithMasks != null) {
                this.events.onLinesClearedWithMasks.accept(masks);
            }
        });
        // 다음 블럭 스폰 직전에 pending 가비지 실제 주입
        logic.setBeforeSpawnHook(this::flushGarbageIfAny);
    }

    /* ===== 외부에서 쓰는 최소 API ===== */

    /** UI에 붙일 컴포넌트 (왼쪽/오른쪽 보드 영역) */
    public JComponent getComponent() { return panel; }

    /** 숫자 기반 가비지(랜덤 구멍) 적재 */
    public synchronized void enqueueGarbage(int lines) {
        if (lines <= 0) return;
        System.out.printf("[PLAYER %s] enqueue g=%d pending=%d%n", id, lines, pendingMasks.size());

        final java.util.Random r = new java.util.Random();
        for (int i = 0; i < lines; i++) {
            int hole = r.nextInt(BoardLogic.WIDTH);
            int mask = 0;
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (x != hole) mask |= (1 << x); // hole만 0, 나머지 1
            }
            pendingMasks.addLast(mask);
        }
    }

    /** 상대가 보낸 '줄 모양(비트마스크들)'을 그대로 보드에 삽입 대기 */
    public synchronized void enqueueGarbageMasks(int[] masks) {
        if (masks == null || masks.length == 0) return;
        System.out.printf("[PLAYER %s] enqueueMasks len=%d pending=%d%n", id, masks.length, pendingMasks.size());

        for (int m : masks) {
                pendingMasks.addLast(m);
            }
    }

    /** 스폰 직전에 호출되어 보드에 garbage 실제 주입 (오래된→최신 순서로 바닥부터) */
    private synchronized void flushGarbageIfAny() {
        if (pendingMasks.isEmpty()) return;

        final int total = pendingMasks.size();
        int[] masks = new int[total];
        for (int i = 0; i < total; i++) {
            masks[i] = pendingMasks.removeFirst();
        }
        logic.addGarbageMasks(masks);
    }

    /** 현재 대기 중인 줄 수 (HUD 표시에 사용) */
    public synchronized int getPendingGarbage() { return pendingMasks.size(); }

    /* ===== 편의 조회 ===== */
    public Id id() { return id; }
    public boolean isGameOver() { return logic.isGameOver(); }
    public int getScore() { return logic.getScore(); }

    public void stop() { panel.stopLoop(); }
    public void start() {panel.startLoop(); }

}
