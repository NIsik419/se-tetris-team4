package component.items;

import blocks.Block;
import logic.BoardLogic;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * 🔒 SpinLockItemTest
 * --------------------
 * - 회전 불가 여부
 * - activate() 콜백 즉시 실행
 * - getSymbol() 검증
 */
public class SpinLockItemTest {

    private BoardLogic logic;
    private Color color;

    @Before
    public void setup() {
        logic = new BoardLogic(score -> {});
        logic.setOnFrameUpdate(() -> {});
        color = Color.MAGENTA;
    }

    @Test
    public void testCanRotateIsFalseAndRotateDoesNotChangeShape() throws Exception {
        // Block 직접 생성 대신 StubBlock 사용
        StubBlock base = new StubBlock(color, new int[][] {
                {1, 0},
                {1, 1}
        });

        SpinLockItem item = new SpinLockItem(base);

        // 리플렉션으로 canRotate 필드 접근
        Field field = Block.class.getDeclaredField("canRotate");
        field.setAccessible(true);
        boolean canRotateValue = field.getBoolean(item);

        assertFalse("SpinLockItem should not allow rotation", canRotateValue);

        // 원본 모양 복사
        int[][] beforeShape = copyShape(item);

        // 회전 시도
        item.rotate();

        // 회전해도 동일한 모양 유지해야 함
        int[][] afterShape = copyShape(item);
        assertArrayEquals("Shape should not change when rotating SpinLockItem", beforeShape, afterShape);
    }

    @Test
    public void testActivate_CallsOnCompleteImmediately() {
        //  StubBlock 사용
        StubBlock base = new StubBlock(color, new int[][] {{1}});
        SpinLockItem item = new SpinLockItem(base);

        AtomicBoolean called = new AtomicBoolean(false);
        item.activate(logic, () -> called.set(true));

        assertTrue("onComplete should be called immediately", called.get());
    }

    @Test
    public void testGetSymbol_ReturnsLockEmoji() {
        assertEquals("🔒", SpinLockItem.getSymbol());
    }

    //  Block이 abstract라서 별도 Stub 클래스 정의
    private static class StubBlock extends Block {
        StubBlock(Color color, int[][] shape) {
            super(color, shape);
        }
    }

    // 헬퍼 메서드: shape 복사
    private int[][] copyShape(Block block) {
        int h = block.height();
        int w = block.width();
        int[][] copy = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy[y][x] = block.getShape(x, y);
            }
        }
        return copy;
    }
}
