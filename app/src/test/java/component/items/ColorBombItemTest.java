// package component.items;

// import logic.BoardLogic;
// import org.junit.Before;
// import org.junit.Test;

// import java.awt.*;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.TimeUnit;

// import static org.junit.Assert.*;

// /**
//  * 💥 ColorBombItemTest
//  * ---------------------
//  * - 같은 색상의 블록을 전부 제거
//  * - fadeLayer 잔상 남기기
//  * - 중력 및 점수 반영 확인
//  */
// public class ColorBombItemTest {

//     private BoardLogic logic;

//     @Before
//     public void setup() {
//         logic = new BoardLogic(score -> {
//         });
//         logic.setOnFrameUpdate(() -> {
//         });
//         Color[][] board = logic.getBoard();

//         // 윗절반 RED, 아래절반 BLUE로 채움
//         for (int y = 0; y < BoardLogic.HEIGHT / 2; y++)
//             for (int x = 0; x < BoardLogic.WIDTH; x++)
//                 board[y][x] = Color.RED;

//         for (int y = BoardLogic.HEIGHT / 2; y < BoardLogic.HEIGHT; y++)
//             for (int x = 0; x < BoardLogic.WIDTH; x++)
//                 board[y][x] = Color.BLUE;
//     }

//     @Test
//     public void testActivate_RemovesMatchingColorAndLeavesFade() throws Exception {
//         BlockStub base = new BlockStub(Color.RED, new int[][] { { 1 } });
//         ColorBombItem bomb = new ColorBombItem(base);
//         bomb.setTestMode(true); // ✅ 즉시 적용 모드
//         logic.setItemMode(true);

//         int beforeScore = logic.getScore();
//         CountDownLatch latch = new CountDownLatch(1);

//         bomb.activate(logic, latch::countDown);
//         boolean finished = latch.await(2, TimeUnit.SECONDS);
//         assertTrue("ColorBombItem should finish within timeout", finished);

//         // === fadeLayer 잔상 확인 ===
//         Color[][] fade = logic.getFadeLayer();
//         boolean hasFade = false;
//         for (Color[] row : fade) {
//             for (Color c : row) {
//                 if (c != null) {
//                     hasFade = true;
//                     break;
//                 }
//             }
//             if (hasFade)
//                 break;
//         }
//         assertTrue("Fade layer should contain remnants", hasFade);

//         // === 점수 상승 확인 ===
//         assertTrue("Score should increase after activation", logic.getScore() > beforeScore);

//         // === 중력 적용 확인 ===
//         Color[][] board = logic.getBoard();
//         boolean gravityApplied = false;
//         for (int y = BoardLogic.HEIGHT - 2; y < BoardLogic.HEIGHT; y++) {
//             for (int x = 0; x < BoardLogic.WIDTH; x++) {
//                 if (board[y][x] == null) {
//                     gravityApplied = true;
//                     break;
//                 }
//             }
//         }
//         //assertTrue("Gravity should have been applied after clearing", gravityApplied);
//     }

//     /** ✅ 빈 보드에서도 문제없이 종료되는지 확인 */
//     @Test
//     public void testActivate_EmptyBoard_NoCrash() throws Exception {
//         // 완전 빈 보드로 설정
//         Color[][] board = logic.getBoard();
//         for (int y = 0; y < BoardLogic.HEIGHT; y++)
//             for (int x = 0; x < BoardLogic.WIDTH; x++)
//                 board[y][x] = null;

//         BlockStub base = new BlockStub(Color.RED, new int[][] { { 1 } });
//         ColorBombItem bomb = new ColorBombItem(base);

//         CountDownLatch latch = new CountDownLatch(1);
//         bomb.activate(logic, latch::countDown);

//         boolean finished = latch.await(2, TimeUnit.SECONDS);
//         assertTrue("Should safely complete on empty board", finished);
//     }

//     // === 테스트 전용 Block Stub ===
//     private static class BlockStub extends blocks.Block {
//         BlockStub(Color color, int[][] shape) {
//             super(color, shape);
//         }
//     }
// }
