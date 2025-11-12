// package component.items;

// import logic.BoardLogic;
// import org.junit.Before;
// import org.junit.Test;

// import java.awt.Color;

// import static org.junit.Assert.*;

// /**
//  * 🧹 LineClearItemTest
//  * ---------------------
//  * - L 위치 줄 삭제 확인
//  * - 점수 가산 확인
//  * - 중력 적용 여부 확인
//  */
// public class LineClearItemTest {

//     private BoardLogic logic;
//     private Color fillColor;

//     @Before
//     public void setup() {
//         logic = new BoardLogic(score -> {});
//         logic.setOnFrameUpdate(() -> {});
//         fillColor = Color.ORANGE;

//         // 기본 보드 채우기 (아래 절반만)
//         Color[][] board = logic.getBoard();
//         for (int y = BoardLogic.HEIGHT / 2; y < BoardLogic.HEIGHT; y++) {
//             for (int x = 0; x < BoardLogic.WIDTH; x++) {
//                 board[y][x] = fillColor;
//             }
//         }
//     }

//     @Test
//     public void testActivate_RemovesTargetLineAndAddsScore() {
//         //  기본 블록 생성 후 LineClearItem으로 래핑
//         BlockStub base = new BlockStub(fillColor);
//         LineClearItem item = new LineClearItem(base);
//         item.setTestMode(true);

//         //  아이템의 L 위치를 기반으로 targetY 계산
//         int startY = logic.getY();
//         int targetY = startY + item.getLY();

//         //  활성화
//         int beforeScore = logic.getScore();
//         item.activate(logic, null);

//         //  줄이 삭제되었는지 확인
//         Color[][] board = logic.getBoard();
//         for (int x = 0; x < BoardLogic.WIDTH; x++) {
//             assertNull("Line at targetY should be cleared", board[targetY][x]);
//         }

//         //  점수 증가 확인
//         int afterScore = logic.getScore();
//         assertTrue("Score should increase after line clear", afterScore > beforeScore);
//         assertEquals(beforeScore + 100, afterScore);

//         //  L 위치가 유효한 블록 좌표인지 확인
//         assertTrue("L position X valid", item.getLX() >= 0);
//         assertTrue("L position Y valid", item.getLY() >= 0);
//     }

//     /** 테스트용 단순 블록 */
//     private static class BlockStub extends blocks.Block {
//         private final Color color;

//         BlockStub(Color color) {
//             super(color, new int[][] {
//                     {1, 1},
//                     {1, 1}
//             });
//             this.color = color;
//         }

//         @Override
//         public Color getColor() {
//             return color;
//         }
//     }
// }
