package component.network.websocket;

public enum MessageType {
    BOARD_STATE,     // 보드 전체 동기화
    LINE_ATTACK,     // 상대에게 줄 공격
    MOVE, ROTATE, DROP, // 입력 명령 전송용
    GAME_OVER,       // 게임 종료 신호
    PING, PONG ,      // 지연 측정용
    PLAYER_READY ,   // 플레이어 준비 신호
    GAME_START ,     // 게임 시작 신호
    MODE_SELECT    // 게임 모드 선택 신호
}
