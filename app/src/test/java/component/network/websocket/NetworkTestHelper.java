package component.network.websocket;

import logic.BoardLogic;
import component.GameConfig.Difficulty;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkTestHelper {

    public static NetworkManager createNetworkManager(
            MockGameClient client,
            JLabel lagLabel,
            Runnable disconnectHandler
    ) {
        return new NetworkManager(
                false,
                m -> {},
                new BoardLogic(score -> {}, Difficulty.NORMAL),
                new BoardLogic(score -> {}, Difficulty.NORMAL),
                lagLabel,
                disconnectHandler,
                () -> {},
                client
        );
    }
}
