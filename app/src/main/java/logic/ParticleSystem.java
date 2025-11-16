package logic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 파티클 효과 시스템
 * - 라인 클리어 시 가루/파편 효과
 */
public class ParticleSystem {
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    public static class Particle {
        public double x, y;           // 위치
        public double vx, vy;         // 속도
        public Color color;           // 색상
        public int life;              // 남은 수명 (프레임)
        public int maxLife;           // 최대 수명
        public int size;              // 크기

        public Particle(double x, double y, double vx, double vy, Color color, int life, int size) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.life = life;
            this.maxLife = life;
            this.size = size;
        }

        public void update() {
            x += vx;
            y += vy;
            vy += 0.3; // 중력
            life--;
        }

        public boolean isDead() {
            return life <= 0;
        }

        public float getAlpha() {
            return (float) life / maxLife;
        }
    }

    /**
     * 블록이 사라질 때 파티클 생성
     * @param blockX 블록 X 좌표 (보드 좌표)
     * @param blockY 블록 Y 좌표 (보드 좌표)
     * @param blockColor 블록 색상
     * @param cellSize 셀 크기 (픽셀)
     */
    public void createBlockParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        // 블록 중심점 (픽셀 좌표)
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // 8~12개의 파티클 생성
        int count = 8 + random.nextInt(5);
        for (int i = 0; i < count; i++) {
            // 랜덤한 방향으로 튀어나감
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 1.5 + random.nextDouble() * 2.5; // 속도 랜덤

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 2; // 위쪽으로 약간 더 튀김

            // 색상 변형 (밝게 또는 어둡게)
            Color particleColor = adjustBrightness(blockColor, 0.8 + random.nextDouble() * 0.4);

            // 크기 랜덤 (2~4픽셀)
            int size = 2 + random.nextInt(3);

            // 수명 랜덤 (15~25프레임)
            int life = 15 + random.nextInt(11);

            particles.add(new Particle(centerX, centerY, vx, vy, particleColor, life, size));
        }
    }

    /**
     * 라인 전체에 대해 파티클 생성 (양끝만)
     */
    public void createLineParticles(int rowY, Color[][] board, int cellSize, int width) {
        for (int x = 0; x < width; x++) {
            if (board[rowY][x] != null) {
                // 양끝(테두리)만 파티클 생성
                boolean isEdge = (x == 0 || x == width - 1 || 
                                 board[rowY][x - 1] == null || 
                                 (x < width - 1 && board[rowY][x + 1] == null));
                
                if (isEdge) {
                    createBlockParticles(x, rowY, board[rowY][x], cellSize);
                }
            }
        }
    }

    /**
     * 모든 파티클 업데이트
     */
    public void update() {
        particles.removeIf(p -> {
            p.update();
            return p.isDead();
        });
    }

    /**
     * 파티클 렌더링용 리스트 반환
     */
    public List<Particle> getParticles() {
        return particles;
    }

    /**
     * 모든 파티클 제거
     */
    public void clear() {
        particles.clear();
    }

    /**
     * 색상 밝기 조정
     */
    private Color adjustBrightness(Color color, double factor) {
        int r = (int) Math.min(255, color.getRed() * factor);
        int g = (int) Math.min(255, color.getGreen() * factor);
        int b = (int) Math.min(255, color.getBlue() * factor);
        return new Color(r, g, b);
    }
}