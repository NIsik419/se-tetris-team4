package logic;

import component.BlockBag;
import org.junit.Test;
import static org.junit.Assert.*;
import blocks.Block;

/**
 * 아이템 생성 분기 커버용 테스트
 */
public class ItemManagerTest {

    static class MockBag extends BlockBag {
        public MockBag() { super(component.GameConfig.Difficulty.NORMAL); }
        @Override
        public Block next() {
            return new Block() {
                @Override public int[][] getShapeArray() { return new int[][]{{1}}; }
                @Override public int getShape(int x, int y) { return 1; }
                @Override public int width() { return 1; }
                @Override public int height() { return 1; }
                @Override public java.awt.Color getColor() { return java.awt.Color.RED; }
                @Override public void rotate() {}
                @Override public Block clone() { return this; }
            };
        }
    }

    @Test
    public void testGenerateItemBlock() {
        ItemManager manager = new ItemManager(new MockBag());

        // 여러 번 호출해서 다양한 분기 커버
        for (int i = 0; i < 10; i++) {
            Block b = manager.generateItemBlock();
            assertNotNull(b);
        }
    }
}
