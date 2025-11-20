package component;

import java.util.*;
import blocks.*;
import component.GameConfig.Difficulty;

public class BlockBag {

    private final Queue<Block> nextBlocks = new LinkedList<>();
    private final RouletteWheel<Class<? extends Block>> roulette;

    public BlockBag(Difficulty difficulty) {
        // === 1. 블럭별 가중치 설정 ===
        Map<Class<? extends Block>, Double> weights = new HashMap<>();
        weights.put(IBlock.class, 12.0); // I형 20% 증가
        weights.put(JBlock.class, 10.0);
        weights.put(LBlock.class, 10.0);
        weights.put(OBlock.class, 10.0);
        weights.put(SBlock.class, 10.0);
        weights.put(TBlock.class, 10.0);
        weights.put(ZBlock.class, 10.0);

        // 난이도별 조정 (선택적)
        switch (difficulty) {
            case EASY -> weights.put(IBlock.class, 12.0);
            case NORMAL -> weights.put(IBlock.class, 10.0);
            case HARD -> {
                weights.put(IBlock.class, 8.0);
                weights.put(SBlock.class, 12.0);
            }
        }

        this.roulette = new RouletteWheel<>(weights);
        fillBag();
    }

    /** 기존과 동일한 7개 큐 시스템 유지 */
    private void fillBag() {
        List<Block> bag = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            Class<? extends Block> clazz = roulette.select();
            try {
                bag.add(clazz.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("블럭 생성 오류: " + clazz.getSimpleName(), e);
            }
        }

        Collections.shuffle(bag); // 7개 섞어서 넣기
        nextBlocks.addAll(bag);
    }

    /** 다음 블럭 반환 */
    public Block next() {
        if (nextBlocks.isEmpty())
            fillBag();
        return nextBlocks.poll();
    }

    /** 다음 블럭 미리보기 */
    public List<Block> peekNext(int count) {
        if (nextBlocks.size() < count)
            fillBag();
        return new ArrayList<>(nextBlocks).subList(0, Math.min(count, nextBlocks.size()));
    }

    public void reset() {
        nextBlocks.clear();
        refill(); // refill()은 내부적으로 7개 블록을 새로 채우는 함수
    }
    private void refill() {
        fillBag();
    }
}
