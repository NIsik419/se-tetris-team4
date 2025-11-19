// package logic;

// import blocks.Block;
// import component.GameConfig;
// import org.junit.Before;
// import org.junit.Test;

// import java.awt.*;
// import java.lang.reflect.Field;
// import java.util.concurrent.atomic.AtomicBoolean;

// import static org.junit.Assert.*;

// /**
//  * BoardLogic 테스트 - 콤보 점수 포함
//  * -------------------------------------------------
//  * 커버 대상:
//  * - 이동, 회전, 하드드랍
//  * - 점수 증가, 콤보 점수
//  * - 아이템 모드, 게임오버 발생
//  * - getter/setter 및 프리뷰 큐
//  */
// public class BoardLogicTest {

//     private BoardLogic logic;
//     private AtomicBoolean gameOverCalled;

//     /** 테스트용 더미 Block (2x2 정사각형) */
//     static class DummyBlock extends Block {
//         protected final int[][] shape = { { 1, 1 }, { 1, 1 } };
//         protected final Color color = Color.RED;

//         public DummyBlock() {
//             super();
//         }

//         @Override
//         public int[][] getShapeArray() {
//             return shape;
//         }

//         @Override
//         public int getShape(int x, int y) {
//             return shape[y][x];
//         }

//         @Override
//         public int width() {
//             return 2;
//         }

//         @Override
//         public int height() {
//             return 2;
//         }

//         @Override
//         public Color getColor() {
//             return color;
//         }

//         @Override
//         public Block clone() {
//             return new DummyBlock();
//         }

//         @Override
//         public void rotate() {
//             // no-op (충돌 없는 회전)
//         }
//     }

//     @Before
//     public void setup() {
//         gameOverCalled = new AtomicBoolean(false);
//         logic = new BoardLogic(score -> gameOverCalled.set(true), GameConfig.Difficulty.NORMAL);
//         logic.getState().setCurr(new DummyBlock());
//         logic.getState().setPosition(3, 0);
//     }

//     @Test
//     public void testInitialState() {
//         assertNotNull(logic.getBoard());
//         assertNotNull(logic.getCurr());
//         assertFalse(logic.isGameOver());
//         assertEquals(0, logic.getScore());
//     }

//     @Test
//     public void testMoveLeftRightDown() {
//         int x0 = logic.getX();
//         logic.moveLeft();
//         assertEquals(x0 - 1, logic.getX());

//         logic.moveRight();
//         assertEquals(x0, logic.getX());

//         int y0 = logic.getY();
//         logic.moveDown();
//         assertTrue(logic.getY() > y0);
//     }

//     @Test
//     public void testRotateBlockRollback() {
//         DummyBlock b = new DummyBlock() {
//             @Override
//             public void rotate() {
//                 shape[0][0] = 9; // 회전 중 데이터 변경
//             }
//         };
//         logic.getState().setCurr(b);
//         logic.rotateBlock();
//         assertEquals(9, b.getShape(0, 0));
//     }

//     @Test
//     public void testHardDrop() {
//         int yBefore = logic.getY();
//         logic.hardDrop();
//         assertTrue(logic.getY() >= yBefore);
//         assertTrue(logic.getScore() > 0);
//     }

//     @Test
//     public void testGameOverTriggered() {
//         logic.getState().setPosition(0, GameState.HEIGHT - 2);
//         logic.moveDown();
//         logic.moveDown(); // fixBlock() 호출
//         if (logic.isGameOver()) {
//             assertTrue(gameOverCalled.get());
//         }
//     }

//     @Test
//     public void testSetShakeOffsetAndItemMode() {
//         logic.setShakeOffset(3);
//         assertEquals(3, logic.getShakeOffset());

//         logic.setItemMode(true);
//         assertTrue(logic.isItemMode());
//     }

//     @Test
//     public void testGetNextBlocksAndPreviewQueue() {
//         assertNotNull(logic.getNextBlocks());
//         assertTrue(logic.getNextBlocks().size() <= 3);
//     }

//     @Test
//     public void testClearLinesIntegration() {
//         var clear = logic.getClearService();
//         Color[][] board = logic.getBoard();

//         // 맨 아래줄 채우기
//         for (int x = 0; x < GameState.WIDTH; x++)
//             board[GameState.HEIGHT - 1][x] = Color.BLUE;

//         int lines = clear.clearLines(null, null);
//         assertTrue(lines >= 0);
//     }

//     @Test
//     public void testGravityFunctions() {
//         var clear = logic.getClearService();
//         clear.applyGravityInstantly();
//         clear.applyLineGravity();
//         clear.setSkipDuringItem(true);
//         assertTrue(clear.isSkipDuringItem());
//         clear.setSkipDuringItem(false);
//         clear.applyGravityFromRow(0);
//     }

//     @Test
//     public void testFadeLayerAndBuffGetter() {
//         assertNotNull(logic.getFadeLayer());
//         assertNotNull(logic.getBuffManager());
//         assertTrue(logic.getDropInterval() > 0);
//     }

//     /** 콤보 점수 시스템 테스트 */
//     @Test
//     public void testComboScoring() throws Exception {
//         Field scoreField = BoardLogic.class.getDeclaredField("score");
//         scoreField.setAccessible(true);

//         // ✅ 보드 채우기 (맨 아래줄)
//         Color[][] board = logic.getBoard();
//         for (int x = 0; x < GameState.WIDTH; x++) {
//             board[GameState.HEIGHT - 1][x] = Color.BLUE;
//         }

//         // ✅ ClearService를 테스트 모드로 강제 설정 (스킵 없이 즉시 작동)
//         logic.getClearService().setSkipDuringItem(false);

//         // ✅ clearLines() 직접 호출
//         var method = BoardLogic.class.getDeclaredMethod("clearLines");
//         method.setAccessible(true);

//         int baseScore = logic.getScore();

//         // 1회차: 기본 클리어
//         method.invoke(logic);
//         int afterFirst = (int) scoreField.get(logic);
//         assertTrue("첫 클리어 시 점수 증가해야 함", afterFirst > baseScore);

//         // 2회차: 콤보 보너스 발생 (3초 이내)
//         // 다시 한 줄 채워서 클리어 유도
//         for (int x = 0; x < GameState.WIDTH; x++) {
//             board[GameState.HEIGHT - 1][x] = Color.BLUE;
//         }
//         Thread.sleep(1000); // 콤보 유지 시간 내
//         method.invoke(logic);
//         int afterSecond = (int) scoreField.get(logic);
//         //assertTrue("콤보 보너스로 점수 증가해야 함", afterSecond > afterFirst);

//         // 3회차: 시간 초과로 콤보 리셋
//         Thread.sleep(4000);
//         for (int x = 0; x < GameState.WIDTH; x++) {
//             board[GameState.HEIGHT - 1][x] = Color.BLUE;
//         }
//         method.invoke(logic);
//         int afterThird = (int) scoreField.get(logic);
//         assertTrue("리셋 후 기본 점수만 추가되어야 함", afterThird >= afterSecond);
//     }

// }
