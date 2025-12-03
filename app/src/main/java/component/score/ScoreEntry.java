package component.score;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import component.GameConfig;

public record ScoreEntry(
    String name, 
    int score, 
    LocalDateTime at,
    GameConfig.Mode mode,
    GameConfig.Difficulty difficulty) implements Comparable<ScoreEntry>{

    /** 생성 시 유효성 검사 및 기본값 보정 */
    public ScoreEntry {
        if (name == null || name.isBlank()) name = "PLAYER"; // 이름 비어있으면 기본값
        name = name.strip();
        if (score < 0) score = 0;
        if (at == null) at = LocalDateTime.now();
        if (mode == null) mode = GameConfig.Mode.CLASSIC;          
        if (difficulty == null) difficulty = GameConfig.Difficulty.NORMAL;
    }

    /** 점수 비교 로직 (정렬 기준) */
    @Override
    public int compareTo(ScoreEntry o) {
        int byScore = Integer.compare(o.score, this.score);
        if (byScore != 0) return byScore;
        int byTime = o.at.compareTo(this.at);
        if (byTime != 0) return byTime;
        return this.name.compareToIgnoreCase(o.name);
    }

    // CSV 직렬화 
    public String toCSV() {
        return escapeCsv(name) + "," + score + "," + 
        escapeCsv(at.toString())+ "," +
        mode.name() + "," +
        difficulty.name();
    }

    private static String escapeCsv(String s) {
        String escaped = s.replace("\"", "\"\""); 
        return "\"" + escaped + "\"";            
    }

    // CSV 역직렬화 
    public static Optional<ScoreEntry> fromCsv(String line) {
        try {
             List<String> fields = splitCsvLine(line);
            if (fields.size() != 5) return Optional.empty();

            String name = unquote(fields.get(0));
            int score = Integer.parseInt(unquoteIfQuoted(fields.get(1))); // 숫자는 보통 비인용
            LocalDateTime at = LocalDateTime.parse(unquote(fields.get(2)));
            GameConfig.Mode mode = (fields.size() >= 4)
                    ? GameConfig.Mode.valueOf(fields.get(3))
                    : GameConfig.Mode.CLASSIC;
            GameConfig.Difficulty diff = (fields.size() >= 5)
                    ? GameConfig.Difficulty.valueOf(fields.get(4))
                    : GameConfig.Difficulty.NORMAL;
            return Optional.of(new ScoreEntry(name, score,at, mode, diff));  
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** CSV 한 줄을 필드별로 분리 (따옴표 포함 처리) */
    private static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"'); // 이스케이프된 따옴표
                    i++;             // 다음 따옴표 스킵
                } else {
                    inQuotes = !inQuotes; // 따옴표 토글
                }
            } else if (c == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    /** 양쪽 따옴표 제거 */
    private static String unquote(String s) {
        // 항상 따옴표로 감싼 필드라고 가정
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\"\"", "\"");
        }
        return s;
    }

    /** 따옴표로 감싸져 있으면 unquote, 아니면 그대로 */
    private static String unquoteIfQuoted(String s) {
        return (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) ? unquote(s) : s;
    }
}