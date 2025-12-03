package component.versus;

import org.junit.Test;
import versus.GarbagePreviewPanel;

import javax.swing.JLabel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * GarbagePreviewPanel 에 대한 JUnit4 단위 테스트.
 *
 * - setGarbageLines() 가 내부 lines 리스트와 countLabel 을
 *   어떻게 갱신하는지 검증
 * - paintComponent() 호출 시 예외 없이 정상 동작하는지만 확인
 */
public class GarbagePreviewPanelTest {

    /** 내부 private 필드 lines 리스트를 꺼내오는 헬퍼 */
    @SuppressWarnings("unchecked")
    private List<boolean[]> getLinesField(GarbagePreviewPanel panel) throws Exception {
        Field f = GarbagePreviewPanel.class.getDeclaredField("lines");
        f.setAccessible(true);
        return (List<boolean[]>) f.get(panel);
    }

    /** 내부 private 필드 countLabel 을 꺼내오는 헬퍼 */
    private JLabel getCountLabel(GarbagePreviewPanel panel) throws Exception {
        Field f = GarbagePreviewPanel.class.getDeclaredField("countLabel");
        f.setAccessible(true);
        return (JLabel) f.get(panel);
    }

    @Test
    public void testSetGarbageLines_nullOrEmpty() throws Exception {
        GarbagePreviewPanel panel = new GarbagePreviewPanel("TEST");

        // 1) null 넣었을 때
        panel.setGarbageLines(null);
        List<boolean[]> lines = getLinesField(panel);
        JLabel countLabel = getCountLabel(panel);

        assertNotNull(lines);
        assertTrue("null 넣으면 내부 리스트는 비어 있어야 한다", lines.isEmpty());
        assertEquals("0", countLabel.getText());

        // 2) 빈 리스트 넣었을 때
        panel.setGarbageLines(new ArrayList<>());
        lines = getLinesField(panel);
        countLabel = getCountLabel(panel);

        assertNotNull(lines);
        assertTrue("빈 리스트 넣으면 내부 리스트는 비어 있어야 한다", lines.isEmpty());
        assertEquals("0", countLabel.getText());
    }

    @Test
    public void testSetGarbageLines_truncateToMaxRowsAndCopy() throws Exception {
        GarbagePreviewPanel panel = new GarbagePreviewPanel("TEST");

        // COLS = 10, MAX_ROWS = 6 이라고 가정
        int cols = 10;
        int totalLines = 8; // MAX_ROWS(6) 보다 큰 값

        List<boolean[]> src = new ArrayList<>();
        for (int i = 0; i < totalLines; i++) {
            boolean[] row = new boolean[cols];
            // 각 줄마다 다른 위치에 블록 하나만 세팅해서 구분 가능하게
            row[i % cols] = true;
            src.add(row);
        }

        panel.setGarbageLines(src);

        List<boolean[]> lines = getLinesField(panel);
        JLabel countLabel = getCountLabel(panel);

        // countLabel 은 "전체 줄 수" 를 표시해야 함
        assertEquals(String.valueOf(totalLines), countLabel.getText());

        // 내부에 저장된 것은 마지막 MAX_ROWS(6) 줄만이어야 함
        int maxRows = 6;
        assertEquals("내부에는 최대 6줄만 저장되어야 한다", maxRows, lines.size());

        int start = totalLines - maxRows; // 8 - 6 = 2

        for (int i = 0; i < maxRows; i++) {
            boolean[] expected = src.get(start + i);
            boolean[] actual = lines.get(i);

            assertEquals("각 줄의 길이는 COLS 와 같아야 한다", cols, actual.length);

            for (int c = 0; c < cols; c++) {
                assertEquals(
                        "복사된 라인의 셀이 원본과 같아야 한다 (row " + i + ", col " + c + ")",
                        expected[c],
                        actual[c]
                );
            }
        }
    }

    @Test
    public void testPaintComponent_doesNotThrow() {
        GarbagePreviewPanel panel = new GarbagePreviewPanel("TEST");

        // 약간의 데이터 넣기 (그림이 실제로 그려지도록)
        int cols = 10;
        List<boolean[]> src = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            boolean[] row = new boolean[cols];
            row[i] = true;
            src.add(row);
        }
        panel.setGarbageLines(src);

        // 패널 크기 설정
        panel.setSize(160, 320);

        // 오프스크린 버퍼에 그려보기
        BufferedImage img = new BufferedImage(
                panel.getWidth(),
                panel.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = img.createGraphics();
        try {
            // 예외 없이 호출만 되면 OK (렌더링 자체는 시각적으로 검증하지 않음)
            panel.paint(g2);
        } finally {
            g2.dispose();
        }
    }
}
