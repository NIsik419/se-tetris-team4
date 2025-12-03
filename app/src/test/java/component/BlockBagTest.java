package component;

import static org.junit.Assert.*;
import org.junit.Test;

import blocks.*;
import component.GameConfig.Difficulty;

import java.util.*;

/**
 * BlockBagTest (RWS 기반 버전)
 * ----------------------------
 * - 난이도별로 RWS 확률 선택이 정상적으로 작동하는지 검증
 * - 모든 블럭 클래스가 정상적으로 생성되는지 확인
 * - EASY/NORMAL/HARD에서 예외 없이 작동하는지 테스트
 */
public class BlockBagTest {

    /** 1. 모든 블럭 클래스가 정상적으로 생성되는지 테스트 */
    @Test
    public void testAllBlockTypesGenerated() {
        BlockBag bag = new BlockBag(Difficulty.NORMAL);
        Set<String> blockNames = new HashSet<>();

        // 7-bag이 아니므로 여러 번 호출
        for (int i = 0; i < 50; i++) {
            blockNames.add(bag.next().getClass().getSimpleName());
        }

        List<String> expected = List.of(
            "IBlock", "JBlock", "LBlock", "OBlock", "SBlock", "TBlock", "ZBlock"
        );

        for (String name : expected) {
            assertTrue("Missing block type: " + name, blockNames.contains(name));
        }
    }

    /** 2. EASY 난이도에서 RWS가 정상 작동하는지 (단순 실행 테스트) */
    @Test
    public void testEasyDifficultySelection() {
        BlockBag bag = new BlockBag(Difficulty.EASY);
        for (int i = 0; i < 100; i++) {
            assertNotNull(bag.next());
        }
    }

    /** 3. HARD 난이도에서도 예외 없이 작동하는지 */
    @Test
    public void testHardDifficultySelection() {
        BlockBag bag = new BlockBag(Difficulty.HARD);
        for (int i = 0; i < 100; i++) {
            assertNotNull(bag.next());
        }
    }

    /** 4. 확률 분포 테스트 (±5% 허용) */
    @Test
    public void testRWSDistribution() {
        BlockBag bag = new BlockBag(Difficulty.EASY);

        Map<String, Integer> counts = new HashMap<>();
        for (String name : List.of("IBlock", "JBlock", "LBlock", "OBlock", "SBlock", "TBlock", "ZBlock")) {
            counts.put(name, 0);
        }

        int trials = 50_000;
        for (int i = 0; i < trials; i++) {
            String name = bag.next().getClass().getSimpleName();
            counts.put(name, counts.get(name) + 1);
        }

        // EASY: IBlock 12, others 10
        Map<String, Double> weights = new HashMap<>();
        for (String key : counts.keySet()) weights.put(key, 10.0);
        weights.put("IBlock", 12.0);

        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        for (String name : counts.keySet()) {
            double expected = trials * (weights.get(name) / total);
            double actual = counts.get(name);
            double diffPercent = Math.abs((actual - expected) / expected) * 100;

            System.out.printf("%s → 기대: %.0f, 실제: %.0f (오차 %.2f%%)%n",
                    name, expected, actual, diffPercent);

            assertTrue(name + " 오차 초과: " + diffPercent + "%",
                    diffPercent <= 5.0);
        }
    }
}
