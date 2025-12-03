package component;

import java.util.*;

/**
 * Roulette Wheel Selection via Stochastic Acceptance (Lipowski, 2012)
 * - 각 블럭의 가중치(weight)에 따라 확률적으로 블럭을 선택.
 * - 평균 복잡도 O(1)
 */
public class RouletteWheel<T> {

    private final List<T> items;
    private final List<Double> weights;
    private final double maxWeight;
    private final Random rand = new Random();

    public RouletteWheel(Map<T, Double> weightMap) {
        this.items = new ArrayList<>(weightMap.keySet());
        this.weights = new ArrayList<>(weightMap.values());
        this.maxWeight = Collections.max(weights);
    }

    /** 가중치에 따라 항목을 하나 선택 */
    public T select() {
        while (true) {
            int i = rand.nextInt(items.size());
            double r = rand.nextDouble();
            if (r < weights.get(i) / maxWeight)
                return items.get(i);
        }
    }
}
