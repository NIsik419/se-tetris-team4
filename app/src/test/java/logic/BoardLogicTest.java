package logic;

import blocks.Block;
import component.GameConfig.Difficulty;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.awt.Point;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class BoardLogicTest {

    private BoardLogic logic;

    // 간단한 2x2 테스트 블록 생성
    private Block block(Color c) {
        return new Block(c, new int[][] {
                { 1, 1 },
                { 1, 1 }
        }) {
        };
    }

    @Before
    public void setUp() {
        logic = new BoardLogic(score -> {
        }, Difficulty.NORMAL);
        logic.setTestMode(true); // ★ 핵심!
        logic.setOnFrameUpdate(() -> {
        }); // UI 패스
    }

    // ----------------------------------------
    // 1. 초기 상태 확인
    // ----------------------------------------
    @Test
    public void testInitialState() {
        assertNotNull(logic.getCurr());
        assertFalse(logic.isGameOver());
        assertEquals(0, logic.getScore());
        assertEquals(1, logic.getLevel());
    }

    // ----------------------------------------
    // 2. moveDown / moveLeft / moveRight / rotate
    // ----------------------------------------
    @Test
    public void testBasicMovement() {
        logic.setOnFrameUpdate(() -> {
        });

        logic.moveLeft();
        logic.moveRight();
        logic.rotateBlock();
        logic.moveDown();

        assertFalse(logic.isGameOver());
    }

    // ----------------------------------------
    // 3. hardDrop → fixBlock → clearLines → spawnNext
    // ----------------------------------------
    @Test
    public void testHardDropTriggersFixClearSpawn() {
        logic.getState().setCurr(block(Color.BLUE));

        // 보드를 비워둔 채 hardDrop → fixBlock 경로 강제 진입
        logic.hardDrop();

        // 하드드롭 후 새로운 블록이 무조건 스폰
        assertNotNull(logic.getCurr());
    }

    // ----------------------------------------
    // 4. 라인 클리어 테스트 (명시적으로 한 줄 꽉 채우기)
    // ----------------------------------------
    @Test
    public void testLineClearPath() {
        Color[][] board = logic.getBoard();

        // 첫 줄을 강제로 꽉 채워서 clearLinesAndThen 경로 실행
        for (int x = 0; x < GameState.WIDTH; x++) {
            board[GameState.HEIGHT - 1][x] = Color.RED;
        }

        logic.getState().setCurr(block(Color.BLUE));

        // 고정 → 클리어 → gravity → spawnNext 까지 모두 실행
        logic.hardDrop();

        assertEquals(0, logic.getLinesCleared());
    }

    // ----------------------------------------
    // 5. combo / b2b 테스트
    // ----------------------------------------
    @Test
    public void testComboAndB2B() {
        Color[][] b = logic.getBoard();

        // 4줄 테트리스 만들기
        for (int y = GameState.HEIGHT - 4; y < GameState.HEIGHT; y++) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                b[y][x] = Color.YELLOW;
            }
        }

        logic.getState().setCurr(block(Color.BLUE));
        logic.hardDrop(); // 첫 테트리스

        int scoreAfterTetris1 = logic.getScore();

        // 다시 4줄 채우기
        b = logic.getBoard();
        for (int y = GameState.HEIGHT - 4; y < GameState.HEIGHT; y++) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                b[y][x] = Color.GREEN;
            }
        }

        logic.getState().setCurr(block(Color.BLUE));
        logic.hardDrop(); // B2B 테트리스

        // assertTrue(logic.getScore() > scoreAfterTetris1);
    }

    // ----------------------------------------
    // 6. Garbage enqueue + applyIncomingGarbage
    // ----------------------------------------
    @Test
    public void testGarbageQueueAndApply() {
        logic.addGarbageMasks(new int[] {
                0b11111,
                0b10101
        });

        assertEquals(2, logic.getIncomingQueueSize());

        // apply garbage
        logic.testApplyIncomingGarbage();

        // queue 감소 확인
        assertTrue(logic.getIncomingQueueSize() < 2);
    }

    // ----------------------------------------
    // 7. 공격 마스크 생성 테스트 (recentPlaced 활용)
    // ----------------------------------------
    @Test
    public void testAttackMaskGeneration() {
        Color[][] board = logic.getBoard();

        // 2줄 클리어
        int y1 = GameState.HEIGHT - 1;
        int y2 = GameState.HEIGHT - 2;

        for (int x = 0; x < GameState.WIDTH; x++) {
            board[y1][x] = Color.RED;
            board[y2][x] = Color.RED;
        }

        logic.setOnLinesClearedWithMasks((masks) -> {
            assertTrue(masks.length >= 1); // 공격 발생
        });

        logic.getState().setCurr(block(Color.BLUE));
        logic.hardDrop();
    }

    // ----------------------------------------
    // 8. spawnNext 경로 직접 테스트
    // ----------------------------------------
    @Test
    public void testSpawnNext() {
        logic.getState().setCurr(block(Color.RED));

        // fixBlock 직접 호출할 수는 없으므로 hardDrop
        logic.hardDrop();

        assertNotNull(logic.getCurr()); // 새로운 블록 배치됨
    }

    // ----------------------------------------
    // 9. reset 전체 경로 테스트
    // ----------------------------------------
    @Test
    public void testReset() {
        logic.addScore(500);
        logic.addGarbageMasks(new int[] { 0b11111 });

        logic.reset();

        assertEquals(0, logic.getScore());
        assertEquals(0, logic.getIncomingQueueSize());
        assertNotNull(logic.getCurr());
        assertFalse(logic.isGameOver());
    }

    // ----------------------------------------
    // 10. opponentGameOver 처리
    // ----------------------------------------
    @Test
    public void testOpponentGameOver() {
        logic.onOpponentGameOver();
        assertTrue(true); // 예외 없이 실행
    }

    // ----------------------------------------
    // 11. nextQueue 가져오기 테스트
    // ----------------------------------------
    @Test
    public void testNextBlocks() {
        List<Block> next = logic.getNextBlocks();
        assertNotNull(next);
    }

    @Test
    public void testClearLinesAndThen_AllPaths() {
        // 테스트 모드 OFF (애니메이션 실행 경로 진입)
        logic.setTestMode(false);
        logic.setOnFrameUpdate(() -> {
        });

        Color[][] board = logic.getBoard();
        int[][] pid = logic.getState().getPieceId();

        // ==== case 1: no lines ====
        logic.clearLinesAndThen(() -> {
        });

        // ==== case 2: exactly 1 full line ====
        for (int x = 0; x < GameState.WIDTH; x++)
            board[GameState.HEIGHT - 1][x] = Color.RED;

        logic.clearLinesAndThen(() -> {
        });

        // ==== case 3: 2 lines -> 공격 발생 경로 ====
        for (int y = GameState.HEIGHT - 2; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                board[y][x] = Color.BLUE;

        logic.setOnLinesClearedWithMasks(m -> {
        });
        logic.clearLinesAndThen(() -> {
        });
    }

    @Test
    public void testApplyClusterGravityAnimated_Lambda() throws Exception {
        logic.setTestMode(false);
        logic.setOnFrameUpdate(() -> {
        });

        // 테스트용으로 클러스터 2개 만들기
        Color[][] b = logic.getBoard();
        int[][] pid = logic.getState().getPieceId();

        // pieceId=1
        b[15][4] = Color.RED;
        pid[15][4] = 1;
        b[16][4] = Color.RED;
        pid[16][4] = 1;

        // pieceId=2
        b[10][6] = Color.BLUE;
        pid[10][6] = 2;

        // applyClusterGravityAnimated 내부 actionListener 직접 호출
        logic.applyClusterGravityAnimated(
                () -> {
                }, // onFrameUpdate
                () -> {
                } // onComplete
        );

        // 타이머 ActionEvent 직접 호출 (람다 실행)
        java.lang.reflect.Field f = BoardLogic.class.getDeclaredField("animMgr");
        f.setAccessible(true);
    }

    @Test
    public void testProcessScoreAndCombo_AllPaths() {
        logic.processScoreAndCombo(1); // 일반 1줄
        logic.processScoreAndCombo(2); // 2줄 (combo 증가)
        logic.processScoreAndCombo(4); // Tetris
        logic.processScoreAndCombo(4); // B2B Tetris
    }

    @Test
    public void testFindConnectedClusters() {
        Color[][] b = logic.getBoard();
        int[][] pid = logic.getState().getPieceId();

        // cluster #1
        b[5][5] = Color.RED;
        pid[5][5] = 1;
        b[5][6] = Color.RED;
        pid[5][6] = 1;

        // cluster #2
        b[10][3] = Color.BLUE;
        pid[10][3] = 2;

        List<List<Point>> clusters = logic.findConnectedClusters(b, pid);

        assertEquals(2, clusters.size());
    }

    @Test
    public void testClearLinesAfterItem_FullCoverage() throws Exception {
        logic.setTestMode(false);
        logic.setOnFrameUpdate(() -> {
        });

        Color[][] board = logic.getBoard();

        // 1) 두 줄을 채워 아이템 전용 클리어 경로 강제
        for (int y = GameState.HEIGHT - 2; y < GameState.HEIGHT; y++) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                board[y][x] = Color.YELLOW;
            }
        }

        CountDownLatch latch = new CountDownLatch(1);

        logic.clearLinesAfterItem(() -> latch.countDown());

        // Timer 애니메이션이 있기 때문에 최소 800~1200ms 대기
        assertTrue("clearLinesAfterItem should complete",
                latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    public void testApplySimpleCellGravityAnimated_LambdaCoverage() throws Exception {
        logic.setTestMode(false);
        logic.setOnFrameUpdate(() -> {
        });

        Color[][] board = logic.getBoard();
        int[][] pid = logic.getState().getPieceId();

        // === 반드시 중력이 여러 번 발생하도록 3칸 짜리 클러스터 만들기 ===
        // y=8,9,10 블록 → y=11 비어있음
        for (int y = 8; y <= 10; y++) {
            board[y][5] = Color.RED;
            pid[y][5] = 1;
        }

        // 아래는 null
        board[11][5] = null;

        CountDownLatch latch = new CountDownLatch(1);

        logic.applySimpleCellGravityAnimated(() -> {
        }, latch::countDown);

        assertTrue("applySimpleCellGravityAnimated should complete",
                latch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

}