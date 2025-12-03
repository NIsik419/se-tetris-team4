package component.network.websocket;

import logic.BoardLogic;
import component.GameConfig.Difficulty;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkTestHelper {

    // NetworkTestHelper.java에 이렇게 구현되어 있어야 함
public static NetworkManager createNetworkManager(
    MockGameClient client, 
    JLabel label, 
    Runnable onConnectionLost,
    Runnable onGameOver) {
    
    return new NetworkManager(
        false, 
        msg -> {}, 
        new MockBoardLogic(), 
        new MockBoardLogic(), 
        label, 
        onConnectionLost,
        onGameOver,
        client
    );
}}
