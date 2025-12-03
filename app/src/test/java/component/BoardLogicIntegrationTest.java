// package component;

// import logic.BoardLogic;
// import org.junit.Before;
// import org.junit.Test;

// import javax.swing.*;
// import java.awt.*;

// import static org.junit.Assert.*;

// public class BoardLogicIntegrationTest {

//     private Board board;

//     @Before
//     public void setup() throws Exception {
//         SwingUtilities.invokeAndWait(() -> {
//             GameConfig config = new GameConfig(GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL, false);
//             board = new Board(config);
//         });
//     }

//     @Test
//     public void testDrawBoardReflectsScoreChange() throws Exception {
//         SwingUtilities.invokeAndWait(() -> {
//             // Board 내부 logic을 가져와 점수 증가시킨 후 drawBoard() 호출
//             BoardLogic logic = board.getLogic();
//             int before = logic.getScore();

//             logic.addScore(120);
//             board.drawBoard();

//             // 리플렉션으로 scoreLabel 값 읽기
//             JLabel scoreLabel = (JLabel) TestUtils.getPrivateField(board, "scoreLabel");
//             assertNotNull(scoreLabel);
//             assertEquals(String.valueOf(before + 120), scoreLabel.getText());
//         });
//     }

//     @Test
//     public void testDrawBoardReflectsLinesAndLevel() throws Exception {
//         SwingUtilities.invokeAndWait(() -> {
//             BoardLogic logic = board.getLogic();

//             // ClearService와 SpeedManager는 내부적으로만 사용되므로
//             // 간접적으로 값만 수정
//             int beforeLevel = logic.getLevel();

//             // 속도 상승 유도: 10줄 클리어 등가
//             for (int i = 0; i < 10; i++) {
//                 logic.addScore(100);
//             }
//             board.drawBoard();

//             JLabel levelLabel = (JLabel) TestUtils.getPrivateField(board, "levelLabel");
//             assertNotNull(levelLabel);
//             assertEquals(String.valueOf(beforeLevel), levelLabel.getText());
//         });
//     }

//     @Test
//     public void testToggleFullScreenTwice() throws Exception {
//         SwingUtilities.invokeAndWait(() -> {
//             try {
//                 var method = Board.class.getDeclaredMethod("toggleFullScreen");
//                 method.setAccessible(true);
//                 method.invoke(board);
//                 method.invoke(board); // 두 번 호출
//             } catch (Exception e) {
//                 fail("toggleFullScreen() 호출 실패: " + e.getMessage());
//             }

//             // full screen 토글 후에도 창은 여전히 visible
//             assertTrue(board.isVisible());
//         });
//     }
// }
