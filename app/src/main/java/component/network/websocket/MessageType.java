package component.network.websocket;

/**
 * 네트워크 메시지 타입 정의
 */
public enum MessageType {
    // === 기존 메시지 타입 ===
    PLAYER_READY,
    MODE_SELECT,
    GAME_START,
    GAME_OVER,
    
    // 보드 동기화
    BOARD_STATE,        // 레거시: 전체 보드 전송
    
    // 게임 액션
    LINE_ATTACK,        // 라인 클리어 공격
    SCORE_UPDATE,       // 점수 업데이트
    
    // === 델타 전송 ===
    BOARD_DELTA,           // 델타: 변경사항만 전송
    BOARD_DELTA_COMPRESSED, // 압축된 델타 (RLE)
    BOARD_FULL_SYNC,       // 주기적 전체 동기화 (패킷 손실 대비)
    NEXT_BLOCKS,         // 다음 블록 정보 전송
    PLAYER_STATS,       // 플레이어 상태 정보 전송
    
    // === 타임 리밋 모드 (NEW!) ===
    TIME_LIMIT_START,      // 타임 리밋 시작 (서버 시간 전송)
    TIME_LIMIT_SYNC,       // 타이머 동기화 확인
    TIME_LIMIT_SCORE,      // 타임 리밋 모드 점수 전송
    
    // === 네트워크 안정성 ===
    PING,
    PONG,
    
    // === 재시작 ===
    RESTART_READY,
    RESTART_START
}