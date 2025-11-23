package component.network.websocket;

import logic.GameState;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 보드의 변경사항(델타)만 추적하고 전송하는 클래스
 * Color RGB 값 직렬화 지원
 */
public class BoardDeltaTracker {

    /**
     * 단일 셀의 변경사항
     */
    public static class CellDelta {
        public final int x;
        public final int y;
        public final Integer rgb; // Color의 RGB 값 (null이면 빈 칸)

        public CellDelta() {
            this(0, 0, null);
        }

        public CellDelta(int x, int y, Integer rgb) {
            this.x = x;
            this.y = y;
            this.rgb = rgb;
        }
    }

    /**
     * 보드 변경사항 묶음
     */
    public static class BoardDelta {
        public List<CellDelta> changes;
        public Long timestamp;

        // 추가 메타데이터
        public Integer score;
        public Integer level;
        public Integer incomingLines;

        public BoardDelta() {
            this.changes = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }

        public BoardDelta(List<CellDelta> changes) {
            this.changes = changes;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private Integer[][] previousBoard; // RGB 값 저장
    private final int width;
    private final int height;

    // 이전 상태 캐싱
    private Integer prevScore;
    private Integer prevLevel;
    private Integer prevIncoming;

    public BoardDeltaTracker(int width, int height) {
        this.width = width;
        this.height = height;
        this.previousBoard = new Integer[height][width];

        // 초기화 (모두 null = 빈 칸)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                previousBoard[y][x] = null;
            }
        }
    }

    /**
     * 현재 보드와 이전 보드를 비교하여 델타 생성
     */
    public BoardDelta computeDelta(GameState state) {
        List<CellDelta> changes = new ArrayList<>();
        Color[][] currentBoard = state.getBoard();

        // 1. 보드 셀 변경사항 추적
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Integer currentRgb = colorToRgb(currentBoard[y][x]);
                Integer previousRgb = previousBoard[y][x];

                // null-safe 비교
                if (!rgbEquals(currentRgb, previousRgb)) {
                    changes.add(new CellDelta(x, y, currentRgb));
                    previousBoard[y][x] = currentRgb;
                }
            }
        }

        // 변경사항이 없으면 null 반환 (전송 안함)
        if (changes.isEmpty() && !hasMetadataChanged(state)) {
            return null;
        }

        BoardDelta delta = new BoardDelta(changes);

        // 2. 메타데이터 변경사항
        if (prevScore == null || prevScore != state.getScore()) {
            delta.score = state.getScore();
            prevScore = state.getScore();
        }

        if (prevLevel == null || prevLevel != state.getLevel()) {
            delta.level = state.getLevel();
            prevLevel = state.getLevel();
        }

        if (prevIncoming == null || prevIncoming != state.getIncomingLines()) {
            delta.incomingLines = state.getIncomingLines();
            prevIncoming = state.getIncomingLines();
        }

        return delta;
    }

    /**
     * 전체 보드 동기화 (초기화 또는 동기화 실패시 사용)
     */
    public BoardDelta createFullSync(GameState state) {
        List<CellDelta> allCells = new ArrayList<>();
        Color[][] board = state.getBoard();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Integer rgb = colorToRgb(board[y][x]);
                allCells.add(new CellDelta(x, y, rgb));
                previousBoard[y][x] = rgb;
            }
        }

        BoardDelta delta = new BoardDelta(allCells);
        delta.score = state.getScore();
        delta.level = state.getLevel();
        delta.incomingLines = state.getIncomingLines();

        // 캐시 업데이트
        prevScore = state.getScore();
        prevLevel = state.getLevel();
        prevIncoming = state.getIncomingLines();

        return delta;
    }

    /**
     * 압축된 델타 생성 (연속된 같은 값을 RLE로 압축)
     */
    public static class CompressedDelta {
        public List<CellRun> runs;
        public Integer score;
        public Integer level;
        public Integer incomingLines;

        public CompressedDelta() {
            this.runs = new ArrayList<>();
        }

        public static class CellRun {
            public int startX;
            public int startY;
            public Integer rgb;
            public int count; // 같은 줄에서 연속된 개수

            public CellRun() {
            }

            public CellRun(int startX, int startY, Integer rgb, int count) {
                this.startX = startX;
                this.startY = startY;
                this.rgb = rgb;
                this.count = count;
            }
        }
    }

    /**
     * 델타를 RLE 압축
     */
    public CompressedDelta compressDelta(BoardDelta delta) {
        CompressedDelta compressed = new CompressedDelta();
        compressed.score = delta.score;
        compressed.level = delta.level;
        compressed.incomingLines = delta.incomingLines;

        if (delta == null || delta.changes.isEmpty()) {
            return compressed;
        }

        // 변경사항을 y, x 순으로 정렬
        List<CellDelta> sorted = new ArrayList<>(delta.changes);
        sorted.sort((a, b) -> {
            if (a.y != b.y)
                return Integer.compare(a.y, b.y);
            return Integer.compare(a.x, b.x);
        });

        int i = 0;
        while (i < sorted.size()) {
            CellDelta current = sorted.get(i);
            int count = 1;

            // 같은 줄에서 같은 값이 연속되는지 확인
            while (i + count < sorted.size()) {
                CellDelta next = sorted.get(i + count);
                if (next.y == current.y &&
                        next.x == current.x + count &&
                        rgbEquals(next.rgb, current.rgb)) {
                    count++;
                } else {
                    break;
                }
            }

            compressed.runs.add(new CompressedDelta.CellRun(
                    current.x, current.y, current.rgb, count));
            i += count;
        }

        return compressed;
    }

    // === Helper 메서드 ===

    /**
     * Color를 RGB 정수값으로 변환 (null이면 null 반환)
     */
    private Integer colorToRgb(Color color) {
        return color == null ? null : color.getRGB();
    }

    /**
     * RGB 값 비교 (null-safe)
     */
    private boolean rgbEquals(Integer rgb1, Integer rgb2) {
        if (rgb1 == null && rgb2 == null)
            return true;
        if (rgb1 == null || rgb2 == null)
            return false;
        return rgb1.equals(rgb2);
    }

    private boolean hasMetadataChanged(GameState state) {
        return (prevScore == null || prevScore != state.getScore()) ||
                (prevLevel == null || prevLevel != state.getLevel()) ||
                (prevIncoming == null || prevIncoming != state.getIncomingLines());
    }

    /**
     * 리셋 (게임 재시작시)
     */
    public void reset() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                previousBoard[y][x] = null;
            }
        }
        prevScore = null;
        prevLevel = null;
        prevIncoming = null;
    }

    /**
     * 강제로 현재 상태를 기준으로 업데이트 (이전 상태 무시)
     */
    public void forceUpdate(GameState state) {
        Color[][] currentBoard = state.getBoard();

        // previousBoard를 현재 상태로 업데이트
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                previousBoard[y][x] = colorToRgb(currentBoard[y][x]);
            }
        }

        // 메타데이터도 업데이트
        prevScore = state.getScore();
        prevLevel = state.getLevel();
        prevIncoming = state.getIncomingLines();

        System.out.println("[TRACKER] Force updated to current state");
    }
}