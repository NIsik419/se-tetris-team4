// package component;

// import component.config.Settings;
// import logic.BoardLogic;
// import org.junit.Before;
// import org.junit.Test;

// import javax.swing.*;
// import java.awt.*;

// import static org.junit.Assert.*;

// public class BoardInitTest {

//     private Board board;

//     @Before
//     public void setup() throws Exception {
//         SwingUtilities.invokeAndWait(() -> {
//             GameConfig config = new GameConfig(GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL, false);
//             board = new Board(config);
//         });
//         // Swing 초기 렌더링 완료 대기
//         Thread.sleep(200);
//     }

//     @Test
//     public void testBoardInitialComponentsExist() {
//         assertNotNull(board);
//         assertNotNull(board.getLogic());
//         assertFalse(board.isRestarting());
//     }

//     @Test
//     public void testFrameProperties() throws Exception {
//         SwingUtilities.invokeAndWait(() -> {
//             assertNotNull(board);
//             assertEquals("SeoulTech SE Tetris", board.getTitle());
//             assertTrue("프레임이 보이는 상태여야 함", board.isVisible());
//             assertFalse("프레임이 종료 시 닫힘 모드여야 함",
//                     board.getDefaultCloseOperation() == JFrame.DO_NOTHING_ON_CLOSE);
//         });
//     }

//     @Test
//     public void testOverlayCreated() {
//         // private overlay, dialogPanel 존재 확인
//         try {
//             var field = Board.class.getDeclaredField("overlay");
//             field.setAccessible(true);
//             Object overlay = field.get(board);
//             assertNotNull(overlay);
//         } catch (Exception e) {
//             fail("Overlay field should exist: " + e);
//         }
//     }
// }
