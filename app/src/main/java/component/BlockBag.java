package component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import blocks.Block;
import blocks.IBlock;
import blocks.JBlock;
import blocks.LBlock;
import blocks.OBlock;
import blocks.SBlock;
import blocks.TBlock;
import blocks.ZBlock;

public class BlockBag {

    public BlockBag() {
        fillBag();
    }

    // 다음 블럭 큐
    private Queue<Block> nextBlocks = new LinkedList<>();

    private void fillBag() {
        List<Block> blocks = new ArrayList<>(Arrays.asList(
                new IBlock(),
                new JBlock(),
                new LBlock(),
                new OBlock(),
                new SBlock(),
                new TBlock(),
                new ZBlock()
            ));
        Collections.shuffle(blocks);
        nextBlocks.addAll(blocks);
    }
    
    public Block next() {
        if (nextBlocks.isEmpty()) {
            fillBag();
        }
        return nextBlocks.poll();
    }
    
    public List<Block> peekNext(int count) {
        if (nextBlocks.size() < count) fillBag();
        return new ArrayList<>(nextBlocks).subList(0, Math.min(count, nextBlocks.size()));
    }
}
