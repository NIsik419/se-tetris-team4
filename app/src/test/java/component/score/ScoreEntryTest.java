package component.score;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import component.GameConfig;

public class ScoreEntryTest {

    @Test
    public void compareTo_higherScoreFirst_thenRecentTime_thenName() {
        LocalDateTime t1 = LocalDateTime.of(2025, 10, 26, 10, 0);
        LocalDateTime t2 = t1.plusMinutes(1);

        // 더 높은 점수 우선
        ScoreEntry a = new ScoreEntry("AAA", 300, t1, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);
        ScoreEntry b = new ScoreEntry("BBB", 200, t2, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);
        assertTrue(a.compareTo(b) < 0); // a가 앞

        // 동점이면 더 "최근 시간"이 앞 
        ScoreEntry c = new ScoreEntry("CCC", 300, t2, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL); // a보다 더 최근
        assertTrue(c.compareTo(a) < 0); // c가 a보다 앞

        // 점수/시간 모두 같으면 이름 사전순(대소문자 무시) 
        ScoreEntry d = new ScoreEntry("aaa", 300, t2, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);
        assertTrue(d.compareTo(c) < 0); // "aaa" > "CCC" (대소문자 무시 비교)
    }

    @Test
    public void csv_roundTrip_withQuotesAndCommas() {
        LocalDateTime t = LocalDateTime.of(2025, 10, 26, 12, 34, 56);
        ScoreEntry e = new ScoreEntry("Lee, \"JJ\"", 1234, t, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);
        String csv = e.toCSV();

        var parsedOpt = ScoreEntry.fromCsv(csv);
        assertTrue(parsedOpt.isPresent());
        ScoreEntry p = parsedOpt.get();

        assertEquals("Lee, \"JJ\"", p.name());
        assertEquals(1234, p.score());
        assertEquals(t, p.at());
    }

    @Test
    public void constructor_defaults_areApplied() {
        ScoreEntry e1 = new ScoreEntry(null, -10, null, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);
        assertEquals("PLAYER", e1.name());
        assertEquals(0, e1.score());
        assertNotNull(e1.at());

        ScoreEntry e2 = new ScoreEntry("  Alice  ", 10, null, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);
        assertEquals("Alice", e2.name()); 
    }
}
