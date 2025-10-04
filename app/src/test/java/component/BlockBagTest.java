package component;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class BlockBagTest {

    @Test
    public void testSevenUniqueBlocks() {
        BlockBag bag = new BlockBag();
        Set<String> blockNames = new HashSet<>();

        for (int i = 0; i < 7; i++) {
            blockNames.add(bag.next().getClass().getSimpleName());
        }
        assertEquals(7, blockNames.size());
    }

    @Test
    public void refill() {
        BlockBag bag = new BlockBag();
        for (int i = 0; i < 7; i++) bag.next();
        assertNotNull(bag.next());
    }
    
}
