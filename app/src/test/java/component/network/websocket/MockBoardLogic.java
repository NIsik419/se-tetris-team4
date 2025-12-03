package component.network.websocket;

import logic.BoardLogic;
import component.config.Settings;

public class MockBoardLogic extends BoardLogic {
    public MockBoardLogic() {
        super(null, component.GameConfig.Difficulty.NORMAL);
    }
}
