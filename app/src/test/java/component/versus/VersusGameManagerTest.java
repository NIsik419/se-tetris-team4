package component.versus;

import versus.VersusGameManager;
import versus.Player;
import component.BoardPanel;
import component.GameConfig;
import component.GameConfig.Mode;
import logic.BoardLogic;
import component.GameConfig.Difficulty;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

/**
 * VersusGameManager 에 대한 기본 단위 테스트 (JUnit4 버전).
 *
 * - private static 메서드 toPreviewList(int[] masks)를 리플렉션으로 호출해서
 *   비트마스크 → boolean[] 변환 로직을 검증한다.
 * - 추가로, P1 → P2 가비지 전송 / TIME ATTACK 종료 로직 등을
 *   실제 VersusGameManager 인스턴스를 만들어 검증한다.
 */
public class VersusGameManagerTest {

    /**
     * toPreviewList 가 비트마스크를 올바르게 boolean[10] 배열로 변환하는지 테스트.
     *
     * masks[0] = 0b0000000011 -> index 0,1 이 true
     * masks[1] = 0b0000001010 -> index 1,3 이 true
     */
    @Test
    public void testToPreviewList_basicConversion() throws Exception {
        // given
        int[] masks = new int[] {
                0b0000000011,  // ...0011
                0b0000001010   // ...1010
        };

        Method m = VersusGameManager.class.getDeclaredMethod("toPreviewList", int[].class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<boolean[]> result =
                (List<boolean[]>) m.invoke(null, (Object) masks);

        // then
        assertNotNull("결과 리스트는 null 이 아니어야 한다", result);
        assertEquals("마스크 개수만큼 라인이 나와야 한다", 2, result.size());

        boolean[] row0 = result.get(0);
        boolean[] row1 = result.get(1);

        // 보드 가로 10칸(BOARD_COLS = 10) 확인
        assertEquals(10, row0.length);
        assertEquals(10, row1.length);

        // row0: ...0011  → index 0,1 이 true, 나머지 false
        assertTrue("row0[0] 은 true 여야 한다", row0[0]);
        assertTrue("row0[1] 은 true 여야 한다", row0[1]);
        for (int c = 2; c < 10; c++) {
            assertFalse("row0 의 나머지 칸은 false 여야 한다", row0[c]);
        }

        // row1: ...1010 → index 1,3 이 true
        assertFalse("row1[0] 은 false 여야 한다", row1[0]);
        assertTrue("row1[1] 은 true 여야 한다", row1[1]);
        assertFalse("row1[2] 은 false 여야 한다", row1[2]);
        assertTrue("row1[3] 은 true 여야 한다", row1[3]);
        for (int c = 4; c < 10; c++) {
            assertFalse("row1 의 나머지 칸은 false 여야 한다", row1[c]);
        }
    }

    /**
     * toPreviewList 에 빈 배열을 넘기면 빈 리스트가 나오는지 테스트.
     */
    @Test
    public void testToPreviewList_empty() throws Exception {
        int[] masks = new int[0];

        Method m = VersusGameManager.class.getDeclaredMethod("toPreviewList", int[].class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<boolean[]> result =
                (List<boolean[]>) m.invoke(null, (Object) masks);

        assertNotNull(result);
        assertTrue("입력이 비어있으면 결과 리스트도 비어 있어야 한다", result.isEmpty());
    }

    /**
     * toPreviewList 에 null 을 넘기면 빈 리스트가 나오는지 테스트.
     */
    @Test
    public void testToPreviewList_null() throws Exception {
        Method m = VersusGameManager.class.getDeclaredMethod("toPreviewList", int[].class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<boolean[]> result =
                (List<boolean[]>) m.invoke(null, (Object) null);

        assertNotNull(result);
        assertTrue("입력이 null 이면 결과 리스트는 비어 있어야 한다", result.isEmpty());
    }

    // =====================================================================
    // ★ 여기부터 VersusGameManager 실제 인스턴스를 사용하는 추가 테스트들
    // =====================================================================

    /**
     * P1 이 2줄 이상을 마스크로 지웠다고 이벤트를 발생시키면
     * - P2 pending garbage 가 증가하고
     * - P2 가비지 프리뷰 콜백이 제대로 불리는지 테스트.
     */
    @Test
    public void testP1SendsGarbageMasksToP2() throws Exception {
        GameConfig p1Config = new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );
        GameConfig p2Config = new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );

        AtomicInteger lastP2Pending = new AtomicInteger(-1);
        AtomicReference<List<boolean[]>> p2PreviewRef = new AtomicReference<List<boolean[]>>();

        VersusGameManager manager = new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},                              // backToMenu
                cnt -> {},                            // onP1PendingChanged (사용 안 함)
                lastP2Pending::set,                   // onP2PendingChanged
                blocks -> {},                         // onP1Next
                blocks -> {},                         // onP2Next
                result -> {},                         // onGameFinished
                lines -> {},                          // onP1GarbagePreview
                p2PreviewRef::set                     // onP2GarbagePreview
        );

        // VersusGameManager 안의 p1 Player 꺼내기
        Player p1 = getPlayer(manager, "p1");

        // P1 이 2줄(마스크 2개)을 지웠다고 이벤트 발생
        int[] masks = new int[] {
                0b0000000011,
                0b0000001010
        };
        p1.events.onLinesClearedWithMasks.accept(masks);

        // ▶ P2 pending garbage 줄 수가 2가 되었는지
        assertEquals("P2 대기 가비지 줄 수는 2여야 한다",
                2, manager.getP2Pending());

        // ▶ HUD 콜백도 2를 전달받았는지
        assertEquals("onP2PendingChanged 콜백도 2를 받아야 한다",
                2, lastP2Pending.get());

        // ▶ 가비지 프리뷰 콜백으로도 2줄이 전달되었는지
        List<boolean[]> preview = p2PreviewRef.get();
        assertNotNull("P2 가비지 프리뷰 리스트는 null 이 아니어야 한다", preview);
        assertEquals("프리뷰 라인 수는 2여야 한다", 2, preview.size());
    }

    /**
     * TIME ATTACK 모드에서 P1 점수가 더 크면
     * winner = P1, loser = P2 로 GameResult 가 전달되는지 테스트.
     */
    @Test
    public void testFinishByTimeAttack_p1Wins() throws Exception {
        GameConfig p1Config = new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );
        GameConfig p2Config = new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<VersusGameManager.GameResult> resultRef =
                new AtomicReference<VersusGameManager.GameResult>();

        VersusGameManager manager = new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},              // backToMenu
                cnt -> {},             // onP1PendingChanged
                cnt -> {},             // onP2PendingChanged
                blocks -> {},          // onP1Next
                blocks -> {},          // onP2Next
                r -> {                 // onGameFinished
                    resultRef.set(r);
                    latch.countDown();
                },
                lines -> {},           // onP1GarbagePreview
                lines -> {}            // onP2GarbagePreview
        );

        // p1/p2 Player 얻기
        Player p1 = getPlayer(manager, "p1");
        Player p2 = getPlayer(manager, "p2");

        // 점수를 인위적으로 세팅 (BoardLogic 내부 score 필드를 reflection 으로 찾아서 설정)
        setPlayerScore(p1, 1000);
        setPlayerScore(p2, 500);

        // TIME ATTACK 종료 호출
        manager.finishByTimeAttack();

        // SwingUtilities.invokeLater 로 콜백이 호출되므로 잠깐 대기
        latch.await(1, TimeUnit.SECONDS);

        VersusGameManager.GameResult result = resultRef.get();
        assertNotNull("onGameFinished 콜백이 호출되어야 한다", result);

        assertEquals("P1 이 이겨야 한다", Player.Id.P1, result.winner);
        assertEquals("P2 가 져야 한다", Player.Id.P2, result.loser);
        assertEquals("P1 점수는 1000 이어야 한다", 1000, result.p1Score);
        assertEquals("P2 점수는 500 이어야 한다", 500, result.p2Score);
    }

    /**
     * TIME ATTACK 모드에서 점수가 같으면
     * winner/loser 가 모두 null 인 무승부로 처리되는지 테스트.
     */
    @Test
    public void testFinishByTimeAttack_draw() throws Exception {
        GameConfig p1Config = new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );
        GameConfig p2Config = new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<VersusGameManager.GameResult> resultRef =
                new AtomicReference<VersusGameManager.GameResult>();

        VersusGameManager manager = new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},              // backToMenu
                cnt -> {},             // onP1PendingChanged
                cnt -> {},             // onP2PendingChanged
                blocks -> {},          // onP1Next
                blocks -> {},          // onP2Next
                r -> {                 // onGameFinished
                    resultRef.set(r);
                    latch.countDown();
                },
                lines -> {},           // onP1GarbagePreview
                lines -> {}            // onP2GarbagePreview
        );

        Player p1 = getPlayer(manager, "p1");
        Player p2 = getPlayer(manager, "p2");

        // 두 플레이어 점수를 동일하게 설정
        setPlayerScore(p1, 700);
        setPlayerScore(p2, 700);

        manager.finishByTimeAttack();

        latch.await(1, TimeUnit.SECONDS);

        VersusGameManager.GameResult result = resultRef.get();
        assertNotNull("onGameFinished 콜백이 호출되어야 한다", result);

        assertNull("무승부이므로 winner 는 null 이어야 한다", result.winner);
        assertNull("무승부이므로 loser 도 null 이어야 한다", result.loser);
        assertEquals(700, result.p1Score);
        assertEquals(700, result.p2Score);
    }

    /**
     * cleanup() 이 호출되면 Player 의 pending garbage 가 모두 비워지는지 테스트.
     */
    @Test
    public void testCleanupClearsPendingGarbage() throws Exception {
        GameConfig p1Config = new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );
        GameConfig p2Config = new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );

        VersusGameManager manager = new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},
                cnt -> {},
                cnt -> {},
                blocks -> {},
                blocks -> {},
                r -> {},
                lines -> {},
                lines -> {}
        );

        Player p1 = getPlayer(manager, "p1");

        // P1 대기 가비지 3줄 적재
        p1.enqueueGarbage(3);
        assertTrue("cleanup 전에는 pending garbage 가 0보다 커야 한다",
                p1.getPendingGarbage() > 0);

        // cleanup 호출
        manager.cleanup();

        assertEquals("cleanup 후에는 pending garbage 가 0 이어야 한다",
                0, p1.getPendingGarbage());
    }

    /**
     * P1이 2줄 이상 클리어했다고 가정하고
     * onLinesClearedWithMasks 이벤트를 직접 호출했을 때
     * - P2의 pending garbage 수가 늘어나고
     * - P2 가비지 프리뷰 콜백이 올바르게 호출되는지 테스트.
     */
    @Test
    public void testP1AttackEnqueuesGarbageToP2() {
        GameConfig p1Config = new GameConfig(Mode.VERSUS, Difficulty.NORMAL, false);
        GameConfig p2Config = new GameConfig(Mode.VERSUS, Difficulty.NORMAL, false);

        AtomicInteger p2PendingHud = new AtomicInteger(-1);
        AtomicReference<List<boolean[]>> p2PreviewRef = new AtomicReference<>();

        VersusGameManager manager = new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},                     // backToMenu (no-op)
                c -> {},                      // P1 pending HUD
                p2PendingHud::set,            // P2 pending HUD
                blocks -> {},                 // P1 next
                blocks -> {},                 // P2 next
                result -> {},                 // onGameFinished
                lines -> {},                  // P1 garbage preview
                lines -> p2PreviewRef.set(lines) // P2 garbage preview
        );

        try {
            // given: 2줄 공격 마스크
            int[] masks = new int[] {
                    0b0000000011,   // index 0,1
                    0b0000001010    // index 1,3
            };

            // when: P1이 라인을 지웠다고 직접 이벤트 호출
            manager.getP1().events.onLinesClearedWithMasks.accept(masks);

            // then: P2 pending == 2
            assertEquals("P2 pending garbage 수", 2, manager.getP2Pending());
            assertEquals("HUD 업데이트 값도 2여야 함", 2, p2PendingHud.get());

            // then: P2 가비지 프리뷰
            List<boolean[]> preview = p2PreviewRef.get();
            assertNotNull("프리뷰 리스트는 null이면 안 됨", preview);
            assertEquals("프리뷰 라인 수", 2, preview.size());

            boolean[] row0 = preview.get(0);
            boolean[] row1 = preview.get(1);

            // row0: ...0011  → index 0,1 이 true
            assertTrue(row0[0]);
            assertTrue(row0[1]);
            for (int x = 2; x < row0.length; x++) {
                assertFalse(row0[x]);
            }

            // row1: ...1010 → index 1,3 이 true
            assertFalse(row1[0]);
            assertTrue(row1[1]);
            assertFalse(row1[2]);
            assertTrue(row1[3]);
            for (int x = 4; x < row1.length; x++) {
                assertFalse(row1[x]);
            }
        } finally {
            manager.cleanup();  // 루프/타이머 정리
        }
    }

        /**
     * p2Config.mode() 가 AI 일 때 isAIMode() 가 true 인지 확인.
     * (AI 초기화 분기를 커버하기 위한 테스트)
     */
    @Test
    public void testIsAIModeTrueWhenP2IsAi() {
        GameConfig p1Config = new GameConfig(Mode.VERSUS, Difficulty.NORMAL, false);
        GameConfig p2Config = new GameConfig(Mode.AI, Difficulty.AI_EASY, false);

        VersusGameManager manager = new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},
                c -> {},
                c -> {},
                blocks -> {},
                blocks -> {},
                result -> {},
                lines -> {},
                lines -> {}
        );

        try {
            assertTrue("AI 모드 플래그가 true 여야 함", manager.isAIMode());
        } finally {
            manager.cleanup();
        }
    }

        /**
     * pauseBoth / resumeBoth 가 예외 없이 호출 가능한지 테스트.
     * (내부 p1.stop()/start(), p2.stop()/start() 경로 커버용)
     */
    @Test
    public void testPauseAndResumeBoth() {
        GameConfig p1Config = new GameConfig(Mode.VERSUS, Difficulty.NORMAL, false);
        GameConfig p2Config = new GameConfig(Mode.VERSUS, Difficulty.NORMAL, false);

        VersusGameManager manager = new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},
                c -> {},
                c -> {},
                blocks -> {},
                blocks -> {},
                result -> {},
                lines -> {},
                lines -> {}
        );

        try {
            manager.pauseBoth();
            manager.resumeBoth();
            // 예외만 안 나면 OK
        } finally {
            manager.cleanup();
        }
    }

    // ============================
    // TIME ATTACK 승패 테스트
    // ============================

    private VersusGameManager createManagerForFinishTest(
            AtomicReference<VersusGameManager.GameResult> resultRef) {

        GameConfig p1Config = new GameConfig(GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL, false);
        GameConfig p2Config = new GameConfig(GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL, false);

        return new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},      // backToMenu
                c -> {},       // HUD1
                c -> {},       // HUD2
                b -> {},       // next1
                b -> {},       // next2
                resultRef::set,
                l -> {},       // garbage1
                l -> {}        // garbage2
        );
    }

    private void setScores(VersusGameManager manager, int p1, int p2) throws Exception {
        Player p1Player = manager.getP1();
        Player p2Player = manager.getP2();

        Field panelField = Player.class.getDeclaredField("panel");
        panelField.setAccessible(true);

        BoardPanel p1Panel = (BoardPanel) panelField.get(p1Player);
        BoardPanel p2Panel = (BoardPanel) panelField.get(p2Player);

        BoardLogic p1Logic = p1Panel.getLogic();
        BoardLogic p2Logic = p2Panel.getLogic();

        p1Logic.setScore(p1);
        p2Logic.setScore(p2);
    }

    private VersusGameManager.GameResult finishAndWait(
            VersusGameManager manager,
            AtomicReference<VersusGameManager.GameResult> ref) throws Exception {

        manager.finishByTimeAttack();
        SwingUtilities.invokeAndWait(() -> {});   // EDT 비우기
        return ref.get();
    }

    @Test
    public void testFinishByTimeAttack_p2Wins() throws Exception {
        AtomicReference<VersusGameManager.GameResult> ref = new AtomicReference<>();
        VersusGameManager manager = createManagerForFinishTest(ref);

        setScores(manager, 200, 800);

        VersusGameManager.GameResult result = finishAndWait(manager, ref);

        assertNotNull(result);
        assertEquals(Player.Id.P2, result.winner);
        assertEquals(Player.Id.P1, result.loser);
    }

    @Test
    public void testIsAIMode_falseWhenP2IsNotAI() {
        AtomicReference<VersusGameManager.GameResult> ref = new AtomicReference<>();
        VersusGameManager manager = createManagerForFinishTest(ref);

        assertFalse(manager.isAIMode());
    }

    /**
     * P2가 AI 모드일 때 pauseBoth / resumeBoth 호출 시
     * 내부 aiTimer까지 멈췄다가 다시 시작하는지 테스트.
     */
    @Test
    public void testPauseAndResumeBothWithAIStopsAiTimer() throws Exception {
        GameConfig p1Config = new GameConfig(Mode.VERSUS, Difficulty.NORMAL, false);
        GameConfig p2Config = new GameConfig(Mode.AI, Difficulty.AI_EASY, false);

        VersusGameManager manager = new VersusGameManager(
                p1Config,
                p2Config,
                () -> {},
                c -> {},
                c -> {},
                blocks -> {},
                blocks -> {},
                result -> {},
                lines -> {},
                lines -> {}
        );

        try {
            // VersusGameManager 안의 aiTimer를 reflection으로 꺼냄
            Field timerField = VersusGameManager.class.getDeclaredField("aiTimer");
            timerField.setAccessible(true);
            javax.swing.Timer aiTimer = (javax.swing.Timer) timerField.get(manager);

            assertNotNull("AI 모드에서는 aiTimer가 null이면 안 됨", aiTimer);
            assertTrue("초기에는 aiTimer가 실행 중이어야 함", aiTimer.isRunning());

            // pauseBoth → aiTimer.stop() 가 호출돼야 함
            manager.pauseBoth();
            assertFalse("pauseBoth 후 aiTimer는 멈춰 있어야 함", aiTimer.isRunning());

            // resumeBoth → aiTimer.start() 다시 호출
            manager.resumeBoth();
            assertTrue("resumeBoth 후 aiTimer는 다시 실행 중이어야 함", aiTimer.isRunning());
        } finally {
            manager.cleanup();
        }
    }

    // =====================================================================
    // 헬퍼 메서드들
    // =====================================================================

    /** VersusGameManager 내부 private 필드(p1/p2)를 reflection 으로 꺼내기 */
    private Player getPlayer(VersusGameManager manager, String fieldName) throws Exception {
        Field f = VersusGameManager.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (Player) f.get(manager);
    }

    /**
     * Player 내부 BoardLogic 의 score 필드를 reflection 으로 찾아
     * 원하는 값으로 세팅한다.
     */
    private void setPlayerScore(Player player, int score) throws Exception {
        Field logicField = Player.class.getDeclaredField("logic");
        logicField.setAccessible(true);
        Object logic = logicField.get(player);

        Field scoreField = null;
        Class<?> clazz = logic.getClass();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getType() == int.class &&
                f.getName().toLowerCase().contains("score")) {
                scoreField = f;
                break;
            }
        }
        assertNotNull("BoardLogic 에 score 라는 int 필드를 찾지 못했다", scoreField);

        scoreField.setAccessible(true);
        scoreField.setInt(logic, score);
    }
}
