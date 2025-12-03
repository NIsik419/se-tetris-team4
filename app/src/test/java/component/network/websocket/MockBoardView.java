package component.network.websocket;

import component.BoardView;
import logic.BoardLogic;
import component.config.Settings;

import java.awt.*;

public class MockBoardView extends BoardView {

    public MockBoardView() {
        super(new MockBoardLogic(), new Settings());
    }

    @Override
    public void repaint() {
        // 아무것도 하지 않음 (테스트 안전)
    }

    protected void initBackgroundImage(int cell) {
        // 테스트에서 background 생성하지 않음
    }
}
