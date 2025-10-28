// package logic;

// import blocks.IBlock;
// import component.items.*;
// import org.junit.Before;
// import org.junit.Test;

// import java.awt.*;

// import static org.junit.Assert.*;

// /**
//  * BoardLogic - 아이템 관련 로직 테스트 (JUnit 4 버전)
//  */
// public class BoardLogicTest {

//     private BoardLogic logic;

//     @Before
//     public void setUp() {
//         logic = new BoardLogic(score -> {
//         });
//         logic.setItemMode(true);
//     }

//     /** 테스트용 블록 강제 배치 */
//     private void forceSpawn(BoardLogic logic, ItemBlock item, int x, int y) {
//         Color[][] board = logic.getBoard();
//         if (y >= 0 && y < BoardLogic.HEIGHT && x >= 0 && x < BoardLogic.WIDTH) {
//             board[y][x] = item.getColor();
//         }

//         try {
//             var field = BoardLogic.class.getDeclaredField("state");
//             field.setAccessible(true);
//             GameState state = (GameState) field.get(logic);
//             state.setCurr(item);
//             state.setPosition(x, y);
//         } catch (Exception e) {
//             e.printStackTrace();
//             fail("forceSpawn() reflection failed: " + e.getMessage());
//         }
//     }

//     /** 🔹 LineClearItem - 줄 삭제 후 중력 확인 */
//     @Test
//     public void testLineClearItemClearsRow() throws Exception {
//         Color[][] board = logic.getBoard();

//         // 위 블록 1개 배치
//         board[4][0] = Color.RED;
//         board[5][0] = Color.RED;

//         LineClearItem item = new LineClearItem(new IBlock());
//         item.setTestMode(true);
//         item.activate(logic, null);
//         forceSpawn(logic, item, 0, 5);

//         item.activate(logic, null);
//         Thread.sleep(400); // 애니메이션 대기

//         assertNull("해당 줄은 삭제되어야 함", board[5][0]);
//     }

//     // @Test
//     // public void testWeightItemDestroysBelowBlocks() {
//     //     BoardLogic logic = new BoardLogic(score -> {
//     //     });
//     //     WeightItem item = new WeightItem();
//     //     item.setTestMode(true);

//     //     var board = logic.getBoard();

//     //     // 아래쪽 절반 채워두기
//     //     for (int y = BoardLogic.HEIGHT / 2; y < BoardLogic.HEIGHT; y++)
//     //         for (int x = 0; x < BoardLogic.WIDTH; x++)
//     //             board[y][x] = Color.RED;

//     //     item.activate(logic, null);

//     //     // 아래 절반이 모두 null로 변했는지 확인
//     //     for (int y = BoardLogic.HEIGHT / 2; y < BoardLogic.HEIGHT; y++)
//     //         for (int x = 0; x < BoardLogic.WIDTH; x++)
//     //             assertNull("WeightItem은 하단 블록들을 모두 제거해야 함", board[y][x]);
//     // }

//     /** 🔹 DoubleScoreItem - 점수 2배 적용 확인 */
// //     @Test
// //     public void testDoubleScoreItemAppliesBonus() {
// //         int before = logic.getScore();

// //         DoubleScoreItem item = new DoubleScoreItem();
// //         forceSpawn(logic, item, 0, 0);

// //         item.activate(logic, null);
// //         logic.addScore(100);

// //         assertTrue("점수가 2배로 증가해야 함",
// //                 logic.getScore() >= before + 200);
// //     }
// // }
