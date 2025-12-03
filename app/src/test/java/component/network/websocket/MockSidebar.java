package component.network.websocket;
import java.util.ArrayList;
import java.util.List;
import blocks.Block;
import component.sidebar.HUDSidebar;

class MockSidebar extends HUDSidebar {
    public List<Block> receivedBlocks = new ArrayList<>();
    public Integer receivedScore = null;
    public Integer receivedLevel = null;

    @Override public void setNextBlocks(List<Block> blocks) {
        receivedBlocks = blocks;
    }

    @Override public void setScore(int score) { receivedScore = score; }

    @Override public void setLevel(int level) { receivedLevel = level; }
}