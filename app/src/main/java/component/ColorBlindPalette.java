package component;

import java.awt.Color;

/**
 * ColorBlindPalette
 * ---------------------
 * - 기본 팔레트: ColorBrewer 기반 7가지 블록 색
 * - 색각 모드 변환 지원: NORMAL / PROTAN / DEUTER / TRITAN
 * - 각 모드에서 색상 간 구분도를 보장하도록 테이블 기반으로 구성
 */
public class ColorBlindPalette {

    public enum Mode { NORMAL, PROTAN, DEUTER, TRITAN }

    // === 기본 팔레트 (ColorBrewer 기반) ===
    public static final Color I = new Color(0x00, 0x96, 0xFF); // 하늘 파랑
    public static final Color J = new Color(0x78, 0x5E, 0xF0); // 보라 파랑
    public static final Color L = new Color(0xFF, 0x7F, 0x0E); // 오렌지
    public static final Color O = new Color(0xFF, 0xD7, 0x00); // 황금 노랑
    public static final Color S = new Color(0x2C, 0xA0, 0x2C); // 청록초록
    public static final Color T = new Color(0xD6, 0x27, 0x28); // 붉은보라
    public static final Color Z = new Color(0x8C, 0x56, 0x4B); // 갈색

    public static final Color[] BASE_COLORS = { I, J, L, O, S, T, Z };

    /**
     * 모드별 고정 팔레트 테이블 반환
     */
    public static Color[] getPalette(Mode mode) {
        return switch (mode) {
            case NORMAL -> new Color[]{
                    new Color(0x00, 0x96, 0xFF), // I - sky blue
                    new Color(0x78, 0x5E, 0xF0), // J - indigo
                    new Color(0xFF, 0x7F, 0x0E), // L - orange
                    new Color(0xFF, 0xD7, 0x00), // O - yellow
                    new Color(0x2C, 0xA0, 0x2C), // S - green
                    new Color(0xD6, 0x27, 0x28), // T - red
                    new Color(0x8C, 0x56, 0x4B)  // Z - brown
            };

            case PROTAN -> new Color[]{
                    new Color(0x3A, 0xB0, 0xFF), // I - 밝은 하늘색
                    new Color(0x9C, 0x5A, 0xC8), // J - 보라
                    new Color(0xFF, 0xA0, 0x00), // L - 진한 오렌지
                    new Color(0xFF, 0xE0, 0x50), // O - 연한 노랑
                    new Color(0x30, 0xB8, 0x70), // S - 민트 초록
                    new Color(0xE0, 0x50, 0x80), // T - 핑크 붉은색
                    new Color(0x90, 0x70, 0x50)  // Z - 베이지 브라운
            };

            case DEUTER -> new Color[]{
                    new Color(0x4A, 0xB0, 0xFF), // I - 밝은 하늘색
                    new Color(0xA0, 0x70, 0xC0), // J - 연보라
                    new Color(0xFF, 0xA8, 0x20), // L - 오렌지 옐로우
                    new Color(0xFF, 0xE4, 0x60), // O - 밝은 노랑
                    new Color(0x20, 0xB0, 0x90), // S - 청록
                    new Color(0xE0, 0x60, 0x90), // T - 마젠타 핑크
                    new Color(0x9C, 0x70, 0x60)  // Z - 브라운
            };

            case TRITAN -> new Color[]{
                    new Color(0x00, 0xA0, 0xFF), // I - 선명한 하늘색
                    new Color(0x7A, 0x60, 0xE0), // J - 인디고
                    new Color(0xFF, 0x90, 0x30), // L - 주황
                    new Color(0xFF, 0xC8, 0x50), // O - 금색
                    new Color(0x40, 0xB0, 0x40), // S - 녹색
                    new Color(0xD0, 0x50, 0x80), // T - 장밋빛
                    new Color(0x85, 0x60, 0x55)  // Z - 갈색 붉은색
            };
        };
    }

    /**
     * 개별 색 변환 (팔레트 테이블 기반)
     */
    public static Color convert(Color original, Mode mode) {
        if (mode == Mode.NORMAL) return original;

        // 기본 색상 인덱스를 찾음 (I, J, L, O, S, T, Z 순서)
        for (int i = 0; i < BASE_COLORS.length; i++) {
            if (original.equals(BASE_COLORS[i])) {
                return getPalette(mode)[i];
            }
        }
        return original; // 기본 팔레트 외의 색은 그대로 유지
    }
}
