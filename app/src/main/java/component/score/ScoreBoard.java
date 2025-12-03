package component.score;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import component.GameConfig;

public class ScoreBoard {

    private final List<ScoreEntry> entries = new ArrayList<>();
    private final int capacity;
    private final Path storagePath;

    public ScoreBoard(Path storagePath, int capacity) {
        this.storagePath = storagePath;
        this.capacity = Math.max(1, capacity);
        load();
    }

    public static ScoreBoard createDefault() {
        Path p = Paths.get(System.getProperty("user.home"), ".tetris", "scores.csv");
        return new ScoreBoard(p, 999);
    }

    public ScoreBoard(Path storagePath) {
        this(storagePath,10);
    }

    /** 현재 스코어 목록 (읽기 전용) 반환 */
    public synchronized List<ScoreEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    private int rankInBucket(ScoreEntry target, GameConfig.Mode mode, GameConfig.Difficulty diff) {
        int idx = 0;
        for (ScoreEntry e : entries) {
            if (e.mode() == mode && e.difficulty() == diff) {
                if (e.equals(target)) return idx;
                idx++;
            }
        }
        return -1;
    }

    /**
     * 점수 추가 → 정렬 → 용량 초과 시 잘라내기 → 파일 저장
     * @return 추가된 점수의 인덱스(등수)
     */
    public synchronized int addScore(String name, int score, GameConfig.Mode mode, GameConfig.Difficulty diff) {
        ScoreEntry e = new ScoreEntry(name, score, LocalDateTime.now(), mode, diff);
        entries.add(e);

        entries.sort(ScoreEntry::compareTo);
        prunePerBucket();

        if (entries.size() > capacity) entries.subList(capacity, entries.size()).clear();
        save();
        return rankInBucket(e, mode, diff); 
    }

    /** 모든 점수 초기화 */
    public synchronized void reset() {
        entries.clear();
        save();
    }

    /** 파일에서 점수 목록을 불러오기 */
    public synchronized void load() {
        entries.clear();
        try {
            if (Files.notExists(storagePath)) {
                if (storagePath.getParent() != null) {
                    Files.createDirectories(storagePath.getParent());
                }
                Files.createFile(storagePath);
                return;
            }
            List<String> lines = Files.readAllLines(storagePath, StandardCharsets.UTF_8);
            lines.stream()
                 .map(ScoreEntry::fromCsv)
                 .filter(Optional::isPresent)
                 .map(Optional::get)
                 .sorted()
                 .limit(capacity)
                 .forEach(entries::add);
        } catch (IOException e) {
                System.out.println("log test");  
        }
    }

    /**
     * 현재 점수 목록을 CSV 형태로 파일에 저장
     */
    public synchronized void save() {
       try {
           List<String> lines = entries.stream()
                                        .map(ScoreEntry::toCSV)
                                        .collect(Collectors.toList());
        if (storagePath.getParent() != null) {
            Files.createDirectories(storagePath.getParent());
        }
        Files.write(storagePath, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
       } catch (IOException e) {
                System.out.println("log test"); 
       } 
    }

    public synchronized List<ScoreEntry> getEntries(GameConfig.Mode mode, GameConfig.Difficulty diff) {
        return entries.stream()
                .filter(e -> e.mode() == mode && e.difficulty() == diff)
                .sorted() 
                .limit(capacity) 
                .collect(Collectors.toList());
    }

    // 버킷별로 상위 capacity 유지
    private void prunePerBucket() {
        var byBucket = entries.stream()
                .collect(Collectors.groupingBy(se -> se.mode().name() + "::" + se.difficulty().name()));

        List<ScoreEntry> rebuilt = new ArrayList<>();
        for (var list : byBucket.values()) {
            list.sort(ScoreEntry::compareTo);              // 점수 기준 정렬(내림차순 비교 구현)
            rebuilt.addAll(list.subList(0, Math.min(capacity, list.size()))); // 상위 N
        }

        // 다시 한 번 전체 정렬(보기 일관성용)
        rebuilt.sort(ScoreEntry::compareTo);

        entries.clear();
        entries.addAll(rebuilt);
    }

    public synchronized void resetAll() {
        entries.clear();
        save();
    }

    /** 특정 모드/난이도 버킷만 초기화 */
    public synchronized void resetBucket(GameConfig.Mode mode, GameConfig.Difficulty diff) {
        entries.removeIf(e -> e.mode() == mode && e.difficulty() == diff);
        save();
    }
}
