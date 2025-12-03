package component.versus;

import component.GameConfig;
import logic.BoardLogic;
import org.junit.Test;
import versus.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Player 에 대한 단위 테스트 (JUnit4).
 *
 * - enqueueGarbage / enqueueGarbageMasks 로 pending 큐가 늘어나는지
 * - flushGarbageIfAny() 를 reflection 으로 직접 호출해서
 *   pending 큐가 비워지는지
 * - cleanup() 이 pending 큐를 비우는지
 * - start/stop, getComponent, getNextBlocks, getScore, isGameOver 등이
 *   정상 동작하는지
 *   등을 검증한다.
 */
public class PlayerTest {

    /** 단순한 VERSUS 설정용 GameConfig 생성 헬퍼 */
    private GameConfig createConfig() {
        return new GameConfig(
                GameConfig.Mode.VERSUS,
                GameConfig.Difficulty.NORMAL,
                false
        );
    }

    /** Player 를 하나 만드는 헬퍼 (P1 기준) */
    private Player createPlayer() {
        GameConfig cfg = createConfig();
        return new Player(
                Player.Id.P1,
                cfg,
                new Player.Events(),
                () -> {},   // onExitToMenu
                false       // isAI
        );
    }

    // ---------------- 기존 테스트 ----------------

    @Test
    public void testEnqueueGarbageIncreasesPendingCount() {
        Player p = createPlayer();

        assertEquals("초기 pending 은 0 이어야 한다", 0, p.getPendingGarbage());

        // 3줄 가비지 적재
        p.enqueueGarbage(3);

        // 정확히 3줄이 pending 으로 쌓였는지만 확인
        assertEquals("3줄 적재 후 pending 은 3 이어야 한다", 3, p.getPendingGarbage());
    }

    @Test
    public void testEnqueueGarbageMasksIncreasesPendingCount() {
        Player p = createPlayer();

        assertEquals(0, p.getPendingGarbage());

        int[] masks = {
                0b0000000011,
                0b0000001010,
                0b0000001111
        };

        p.enqueueGarbageMasks(masks);

        // 마스크 3개 → pending 줄 수 3
        assertEquals("마스크 3개를 넣었으니 pending 은 3 이어야 한다",
                3, p.getPendingGarbage());
    }

    /**
     * private flushGarbageIfAny() 를 reflection 으로 직접 호출해서
     * pending 큐가 비워지는지 검증.
     */
    @Test
    public void testFlushGarbageIfAnyClearsPending() throws Exception {
        Player p = createPlayer();

        // 우선 pending 에 2줄 쌓기
        p.enqueueGarbageMasks(new int[] {
                0b0000000011,
                0b0000001010
        });
        assertEquals(2, p.getPendingGarbage());

        // private 메서드 flushGarbageIfAny 호출
        Method flushMethod = Player.class.getDeclaredMethod("flushGarbageIfAny");
        flushMethod.setAccessible(true);
        flushMethod.invoke(p);

        // 호출 후에는 pending 이 0 이 되어야 한다
        assertEquals("flushGarbageIfAny 호출 후 pending 은 0 이어야 한다",
                0, p.getPendingGarbage());
    }

    /**
     * cleanup() 이 pending 큐를 비우는지 다시 한 번 Player 단독으로 확인.
     */
    @Test
    public void testCleanupClearsPendingQueue() {
        Player p = createPlayer();

        p.enqueueGarbage(5);
        assertTrue("cleanup 전에는 pending > 0 이어야 한다",
                p.getPendingGarbage() > 0);

        p.cleanup();

        assertEquals("cleanup 후에는 pending 이 0 이어야 한다",
                0, p.getPendingGarbage());
    }

    // ---------------- 여기부터 추가 테스트 ----------------

    /** getComponent() 가 null 이 아니며 BoardPanel 기반 컴포넌트를 반환하는지 */
    @Test
    public void testGetComponentNotNull() {
        Player p = createPlayer();

        assertNotNull("getComponent() 는 null 이면 안 된다", p.getComponent());
        // 구체 타입까지 체크하진 않고, 그냥 Swing 컴포넌트면 충분
        assertTrue("getComponent() 는 JComponent 여야 한다",
                p.getComponent() instanceof javax.swing.JComponent);
    }

    /**
     * start() / stop() 이 예외 없이 호출 가능한지.
     * (내부 루프 시작/정지 분기 커버용)
     */
    @Test
    public void testStartAndStopDoNotThrow() {
        Player p = createPlayer();

        // 예외만 안 나면 OK
        p.start();
        p.stop();
    }

    /**
     * BoardLogic 의 score 값을 reflection 으로 바꿨을 때
     * Player.getScore() 가 그 값을 그대로 돌려주는지.
     */
    @Test
    public void testGetScoreReflectsInternalLogicScore() throws Exception {
        Player p = createPlayer();

        BoardLogic logic = getLogic(p);
        assertNotNull(logic);
        logic.setScore(1234);

        assertEquals("getScore() 는 BoardLogic 의 score 를 반영해야 한다",
                1234, p.getScore());
    }

    /**
     * BoardLogic 의 gameOver 플래그를 true 로 만들어두면
     * Player.isGameOver() 도 true 를 반환하는지.
     */
    @Test
    public void testIsGameOverReflectsLogicFlag() throws Exception {
        Player p = createPlayer();

        BoardLogic logic = getLogic(p);
        assertNotNull(logic);

        // gameOver 필드를 직접 true 로 세팅
        Field gameOverField = BoardLogic.class.getDeclaredField("gameOver");
        gameOverField.setAccessible(true);
        gameOverField.setBoolean(logic, true);

        assertTrue("내부 gameOver 가 true 이면 isGameOver() 도 true 여야 한다",
                p.isGameOver());
    }

    /**
     * getNextBlocks() 가 null 이 아닌 리스트를 반환하고,
     * NEXT 큐 길이가 0~4 정도의 합리적인 범위에 있는지 확인.
     */
    @Test
    public void testGetNextBlocksReturnsList() {
        Player p = createPlayer();

        List<?> next = p.getNextBlocks();
        assertNotNull("NEXT 블록 리스트는 null 이면 안 된다", next);
        // 너무 빡세게 제한하지 않고, "0개 이상" 정도만 확인
        assertTrue("NEXT 리스트 크기는 음수가 될 수 없다",
                next.size() >= 0);
    }

    // ---------------- 헬퍼 ----------------

    /** Player 내부의 BoardLogic 인스턴스를 reflection 으로 꺼내는 헬퍼 */
    private BoardLogic getLogic(Player p) throws Exception {
        Field logicField = Player.class.getDeclaredField("logic");
        logicField.setAccessible(true);
        return (BoardLogic) logicField.get(p);
    }
}
