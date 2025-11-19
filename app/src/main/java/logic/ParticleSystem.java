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
     * ⭐ 부스러기 효과 (LineClearItem용)
     * - 작은 파편들이 아래로 떨어짐
     * - 간단하고 깔끔한 효과
     */
    public void createDebrisParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // 3~5개의 작은 부스러기
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            // 약간 옆으로 퍼지면서 아래로 떨어짐
            double vx = (random.nextDouble() - 0.5) * 1.5; // 좌우로 살짝
            double vy = 0.5 + random.nextDouble() * 1.0;   // 아래로

            // 블록 색상 약간 어둡게 (부스러기 느낌)
            Color debrisColor = new Color(
                (int)(blockColor.getRed() * 0.7),
                (int)(blockColor.getGreen() * 0.7),
                (int)(blockColor.getBlue() * 0.7)
            );

            int size = 1 + random.nextInt(2); // 1~2픽셀 (작게)
            int life = 15 + random.nextInt(10); // 15~25프레임

            particles.add(new Particle(centerX, centerY, vx, vy, debrisColor, life, size));
        }
    }

    /**
     * ⭐ 폭발 전용 파티클 생성 (WeightItem, ColorBomb 등)
     * - 더 많은 파티클
     * - 빠른 속도
     * - 방사형 확산
     * - 연기/불꽃 효과
     */
    public void createExplosionParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // 메인 폭발 파티클 (15~20개, 빠르고 강렬하게)
        int mainCount = 15 + random.nextInt(6);
        for (int i = 0; i < mainCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 3.0 + random.nextDouble() * 4.0; // 매우 빠르게

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 2.5; // 위로 많이 튐

            // 밝은 불꽃 색상
            Color fireColor = new Color(
                Math.min(255, 200 + random.nextInt(56)),  // 빨강 강조
                Math.min(255, 100 + random.nextInt(100)), // 주황
                50 + random.nextInt(50)                   // 약간의 파랑
            );

            int size = 3 + random.nextInt(3); // 3~5픽셀 (크게)
            int life = 12 + random.nextInt(10); // 12~22프레임

            particles.add(new Particle(centerX, centerY, vx, vy, fireColor, life, size));
        }

        // 작은 불똥 (10~15개, 더 빠르고 작게)
        int sparkCount = 10 + random.nextInt(6);
        for (int i = 0; i < sparkCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 4.0 + random.nextDouble() * 3.0; // 초고속

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 3.0;

            // 노란색/흰색 불똥
            Color sparkColor = new Color(
                255,
                200 + random.nextInt(56),
                100 + random.nextInt(100)
            );

            int size = 1 + random.nextInt(2); // 1~2픽셀 (작게)
            int life = 8 + random.nextInt(8); // 8~16프레임 (짧게)

            particles.add(new Particle(centerX, centerY, vx, vy, sparkColor, life, size));
        }

        // 연기 파티클 (5~8개, 천천히 위로)
        int smokeCount = 5 + random.nextInt(4);
        for (int i = 0; i < smokeCount; i++) {
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI / 4; // 위쪽 중심
            double speed = 0.5 + random.nextDouble() * 1.0; // 천천히

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;

            // 어두운 연기 색상
            int gray = 60 + random.nextInt(40);
            Color smokeColor = new Color(gray, gray, gray);

            int size = 4 + random.nextInt(3); // 4~6픽셀 (크게)
            int life = 20 + random.nextInt(15); // 20~35프레임 (길게)

            particles.add(new Particle(centerX, centerY, vx, vy, smokeColor, life, size));
        }
    }

    /**
     * 블록이 사라질 때 파티클 생성 (빠르고 적게)
     */
    public void createBlockParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // ⭐ 4~6개만 생성 (빠르게)
        int count = 4 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 2.0 + random.nextDouble() * 2.0; // 빠르게

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 1.5; // 위로 살짝

            // 밝은 색상
            Color particleColor = new Color(
                Math.min(255, blockColor.getRed() + 50),
                Math.min(255, blockColor.getGreen() + 50),
                Math.min(255, blockColor.getBlue() + 50)
            );

            int size = 2 + random.nextInt(2); // 2~3픽셀
            int life = 10 + random.nextInt(8); // 10~18프레임 (짧게)

            particles.add(new Particle(centerX, centerY, vx, vy, particleColor, life, size));
        }
    }

    /**
     * 라인 전체에 대해 파티클 생성 (양끝만)
     */
    public void createLineParticles(int rowY, Color[][] board, int cellSize, int width) {
        for (int x = 0; x < width; x++) {
            if (board[rowY][x] != null) {
                // ⭐ 양끝(테두리)만 파티클 생성
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
        // ⭐ Iterator 사용으로 안전하게 제거
        particles.removeIf(p -> {
            p.update();
            return p.isDead();
        });
    }

    /**
     * 파티클 렌더링용 리스트 반환
     */
    public List<Particle> getParticles() {
        return new ArrayList<>(particles); // ⭐ 복사본 반환 (동시성 안전)
    }

    /**
     * 모든 파티클 즉시 제거
     */
    public void clear() {
        particles.clear();
        System.out.println("[ParticleSystem] Cleared all particles");
    }
    
    /**
     * 현재 파티클 개수
     */
    public int getParticleCount() {
        return particles.size();
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