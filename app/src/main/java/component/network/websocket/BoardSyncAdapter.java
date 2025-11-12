package component.network.websocket;

import logic.BoardLogic;
import java.awt.Color;

/**
 * BoardSyncAdapter
 * --------------------
 * - 내 보드 상태 / 공격 / 게임오버 동기화
 * - 상대 보드 수신 시 oppLogic에 반영
 */
public class BoardSyncAdapter {

    private final BoardLogic myLogic;
    private final BoardLogic oppLogic;  
    private final GameClient client;

    public BoardSyncAdapter(BoardLogic myLogic, BoardLogic oppLogic, GameClient client) {
        this.myLogic = myLogic;
        this.oppLogic = oppLogic;  
        this.client = client;

        // 🔹 라인 클리어 시 공격 마스크 전송
        myLogic.setOnLinesClearedWithMasks(masks -> {
            client.send(new Message(MessageType.LINE_ATTACK, masks));
            System.out.println("[SEND] LINE_ATTACK → " + masks.length + " lines");
        });

        // 🔹 게임오버 시 알림 전송
        myLogic.setOnGameOverCallback(this::sendGameOver);
    }

    /** 🟦 주기적으로 내 보드 상태를 상대에게 전송 */
    public void sendBoardState() {
        client.send(new Message(MessageType.BOARD_STATE, myLogic.getBoard()));
    }

    /** 🟥 내 게임이 끝났음을 상대에게 통보 */
    public void sendGameOver() {
        System.out.println("[SEND] GAME_OVER");
        client.send(new Message(MessageType.GAME_OVER, "over"));
    }

    /** 🟨 수신 메시지 처리 */
    public void handleIncoming(Message msg) {
        switch (msg.type) {
            case BOARD_STATE -> {
                // ✅ 상대방 보드 데이터를 oppLogic에 반영
                Color[][] board = WebSocketUtil.fromJson(msg.data, Color[][].class);
                oppLogic.setBoard(board);  // 직접 보드 교체
            }
            case LINE_ATTACK -> {
                // ✅ 상대의 공격을 내 보드에 반영
                int[] masks = WebSocketUtil.fromJson(msg.data, int[].class);
                myLogic.addGarbageMasks(masks);
            }
            case GAME_OVER -> {
                System.out.println("[RECV] GAME_OVER");
                myLogic.onOpponentGameOver();
            }
            default -> {
                // MOVE, PING 등 무시
            }
        }
    }
}