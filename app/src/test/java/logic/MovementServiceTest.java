package logic;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.awt.*;
import blocks.Block;

public class MovementServiceTest {

    private MovementService move;
    private GameState state;

    static class DummyBlock extends Block {
        private final int[][] shape = {{1, 1}, {1, 1}};
        @Override public int getShape(int x, int y) { return shape[y][x]; }
        @Override public int[][] getShapeArray() { return shape; }
        @Override public int width() { return 2; }
        @Override public int height() { return 2; }
        @Override public Color getColor() { return Color.RED; }
        @Override public void rotate() {}
        @Override public Block clone() { return new DummyBlock(); }
    }

    @Before
    public void setup() {
        state = new GameState();
        move = new MovementService(state);
    }

    @Test
    public void testCanMoveBasic() {
        DummyBlock b = new DummyBlock();
        assertTrue(move.canMove(b, 0, 0));
        assertFalse(move.canMove(b, -1, 0)); // out of bounds
        assertFalse(move.canMove(b, 9, 19)); // out of bounds
    }

    @Test
    public void testCanMoveBlockedCell() {
        DummyBlock b = new DummyBlock();
        state.getBoard()[5][5] = Color.BLUE;
        assertFalse(move.canMove(b, 5, 5));
    }

    @Test
    public void testGhostYCalculation() {
        DummyBlock b = new DummyBlock();
        state.setCurr(b);
        int ghostY = move.getGhostY(b);
        assertTrue(ghostY >= 0);
    }

    @Test
    public void testMoveDownLeftRight() {
        state.setPosition(3, 0);
        move.moveDown();
        assertEquals(1, state.getY());
        move.moveLeft();
        assertEquals(2, state.getX());
        move.moveRight();
        assertEquals(3, state.getX());
    }
}
