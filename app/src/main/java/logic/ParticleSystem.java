package logic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 파티클 효과 시스템
 * - 라인 클리어 시 가루/파편 효과
 * - 빠르고 간결한 효과
 */
public class ParticleSystem {
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    public static class Particle {
        public double x, y; // 위치
        public double vx, vy; // 속도
        public Color color; // 색상
        public int life; // 남은 수명 (프레임)
        public int maxLife; // 최대 수명
        public int size; // 크기

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
            vy += 0.4; // ✅ 중력 강화 (0.3 → 0.4)
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
     * ⚡ 번개 파티클 (LightningItem용) - 빠르고 간결하게
     */
    public void createLightningParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // ✅ 5~8개로 감소 (8~12 → 5~8)
        int count = 5 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            // 위쪽으로 집중
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI / 2;
            double speed = 2.5 + random.nextDouble() * 2.5; // ✅ 약간 느리게 (3~6 → 2.5~5)

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;

            // 번개 색상 (노란색/흰색)
            Color lightningColor = random.nextBoolean()
                    ? new Color(255, 240, 100)
                    : new Color(255, 255, 255);

            int size = 2; // ✅ 고정 크기 (2~3 → 2)
            int life = 6 + random.nextInt(6); // ✅ 더 짧게 (8~16 → 6~12)

            particles.add(new Particle(centerX, centerY, vx, vy, lightningColor, life, size));
        }

        // ✅ 전기 불꽃 감소 (4~6 → 3~5)
        int sparkCount = 3 + random.nextInt(3);
        for (int i = 0; i < sparkCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 1.2 + random.nextDouble() * 1.5; // ✅ 속도 감소

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 0.8; // ✅ 위로 덜 튐

            Color sparkColor = new Color(150, 220, 255);
            int size = 1;
            int life = 8 + random.nextInt(6); // ✅ 더 짧게 (10~18 → 8~14)

            particles.add(new Particle(centerX, centerY, vx, vy, sparkColor, life, size));
        }
    }

    /**
     * ⭐ 부스러기 효과 (LineClearItem용) - 작고 빠르게
     */
    public void createDebrisParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // ✅ 2~4개로 감소 (3~5 → 2~4)
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double vx = (random.nextDouble() - 0.5) * 1.2; // ✅ 약간 감소
            double vy = 0.4 + random.nextDouble() * 0.8; // ✅ 약간 감소

            Color debrisColor = new Color(
                    (int) (blockColor.getRed() * 0.7),
                    (int) (blockColor.getGreen() * 0.7),
                    (int) (blockColor.getBlue() * 0.7));

            int size = 1; // ✅ 고정 (1~2 → 1)
            int life = 10 + random.nextInt(8); // ✅ 더 짧게 (15~25 → 10~18)

            particles.add(new Particle(centerX, centerY, vx, vy, debrisColor, life, size));
        }
    }

    /**
     * ⭐ 폭발 전용 파티클 (WeightItem, ColorBomb 등) - 빠르고 적게
     */
    public void createExplosionParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // ✅ 메인 폭발 파티클 감소 (15~20 → 8~12)
        int mainCount = 8 + random.nextInt(5);
        for (int i = 0; i < mainCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 2.5 + random.nextDouble() * 3.0; // ✅ 속도 감소 (3~7 → 2.5~5.5)

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 2.0; // ✅ 위로 덜 튐 (2.5 → 2.0)

            Color fireColor = new Color(
                    Math.min(255, 200 + random.nextInt(56)),
                    Math.min(255, 100 + random.nextInt(100)),
                    50 + random.nextInt(50));

            int size = 2 + random.nextInt(2); // ✅ 크기 감소 (3~5 → 2~3)
            int life = 8 + random.nextInt(6); // ✅ 수명 감소 (12~22 → 8~14)

            particles.add(new Particle(centerX, centerY, vx, vy, fireColor, life, size));
        }

        // ✅ 불똥 감소 (10~15 → 5~8)
        int sparkCount = 5 + random.nextInt(4);
        for (int i = 0; i < sparkCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 3.0 + random.nextDouble() * 2.5; // ✅ 속도 감소 (4~7 → 3~5.5)

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 2.5; // ✅ 위로 덜 튐 (3.0 → 2.5)

            Color sparkColor = new Color(
                    255,
                    200 + random.nextInt(56),
                    100 + random.nextInt(100));

            int size = 1; // ✅ 고정 크기 (1~2 → 1)
            int life = 6 + random.nextInt(6); // ✅ 수명 감소 (8~16 → 6~12)

            particles.add(new Particle(centerX, centerY, vx, vy, sparkColor, life, size));
        }

        // ✅ 연기 파티클 제거 또는 최소화 (5~8 → 2~4)
        int smokeCount = 2 + random.nextInt(3);
        for (int i = 0; i < smokeCount; i++) {
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI / 4;
            double speed = 0.4 + random.nextDouble() * 0.6; // ✅ 속도 감소

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;

            int gray = 60 + random.nextInt(40);
            Color smokeColor = new Color(gray, gray, gray);

            int size = 3 + random.nextInt(2); // ✅ 크기 감소 (4~6 → 3~4)
            int life = 12 + random.nextInt(10); // ✅ 수명 감소 (20~35 → 12~22)

            particles.add(new Particle(centerX, centerY, vx, vy, smokeColor, life, size));
        }
    }

    /**
     * 블록이 사라질 때 파티클 생성 (빠르고 적게)
     */
    public void createBlockParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // ✅ 3~5개로 감소 (4~6 → 3~5)
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 1.8 + random.nextDouble() * 1.5; // ✅ 속도 감소 (2~4 → 1.8~3.3)

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 1.2; // ✅ 위로 덜 튐 (1.5 → 1.2)

            Color particleColor = new Color(
                    Math.min(255, blockColor.getRed() + 50),
                    Math.min(255, blockColor.getGreen() + 50),
                    Math.min(255, blockColor.getBlue() + 50));

            int size = 2; // ✅ 고정 크기 (2~3 → 2)
            int life = 8 + random.nextInt(6); // ✅ 수명 감소 (10~18 → 8~14)

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
        updateBeamParticles();
    }

    /**
     * 파티클 렌더링용 리스트 반환
     */
    public List<Particle> getParticles() {
        return new ArrayList<>(particles);
    }

    /**
     * 모든 파티클 즉시 제거
     */
    public void clear() {
        particles.clear();
        beamParticles.clear();
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
        return

        new Color(r, g, b);
    }

    // ParticleSystem.java에 추가할 클래스와 메서드들

    /**
     * 하드 드롭용 빛 줄기 파티클
     */
    public static class BeamParticle {
        public int x; // 열 위치
        public int startY; // 시작 Y (상단)
        public int endY; // 끝 Y (하드드롭 착지점)
        public Color color; // 빔 색상
        public int life; // 남은 수명
        public int maxLife; // 최대 수명
        public int width; // 빔 너비

        public BeamParticle(int x, int startY, int endY, Color color, int life, int width) {
            this.x = x;
            this.startY = startY;
            this.endY = endY;
            this.color = color;
            this.life = life;
            this.maxLife = life;
            this.width = width;
        }

        public void update() {
            life--;
        }

        public boolean isDead() {
            return life <= 0;
        }

        public float getAlpha() {
            return (float) life / maxLife;
        }
    }

    private final List<BeamParticle> beamParticles = new ArrayList<>();

    /**
     * 하드 드롭 빛 줄기 효과
     * 
     * @param columnX    블록의 X 위치 (보드 좌표)
     * @param startY     블록의 시작 Y 위치
     * @param endY       블록의 착지 Y 위치
     * @param blockColor 블록 색상
     * @param cellSize   셀 크기
     */
    /**
     *  하드 드롭 넓은 광선 효과 (블록 전체를 하나의 광선으로)
     */
    public void createHardDropBeamWide(int startX, int widthCells, int startY, int endY,
            Color blockColor, int cellSize) {
        int boardHeight = 20;

        // 블록 전체 너비를 커버하는 하나의 광선
        int beamCenterX = startX * cellSize + (widthCells * cellSize) / 2;
        int beamWidth = widthCells * cellSize;

        Color brightColor = new Color(
                Math.min(255, blockColor.getRed() + 180),
                Math.min(255, blockColor.getGreen() + 180),
                Math.min(255, blockColor.getBlue() + 180));

        beamParticles.add(new BeamParticle(
                startX * cellSize + (widthCells * cellSize) / 2,
                0,
                boardHeight * cellSize,
                brightColor,
                6,
                beamWidth));

        // 착지 지점에 폭발 효과
        double centerX = beamCenterX;
        double centerY = endY * cellSize + cellSize;

        // 불꽃 파티클
        int sparkCount = 8 + random.nextInt(5);
        for (int i = 0; i < sparkCount; i++) {
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI;
            double speed = 3.0 + random.nextDouble() * 4.0;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;

            Color sparkColor = new Color(
                    Math.min(255, 220 + random.nextInt(36)),
                    Math.min(255, 220 + random.nextInt(36)),
                    255);

            particles.add(new Particle(centerX, centerY, vx, vy, sparkColor, 10 + random.nextInt(8), 3));
        }

        // 먼지 파티클
        int dustCount = 6 + random.nextInt(4);
        for (int i = 0; i < dustCount; i++) {
            double angle = random.nextDouble() * Math.PI - Math.PI / 2;
            double speed = 2.0 + random.nextDouble() * 2.5;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed * 0.5;

            int gray = 180 + random.nextInt(50);
            Color dustColor = new Color(gray, gray, gray);

            particles.add(
                    new Particle(centerX, centerY, vx, vy, dustColor, 12 + random.nextInt(8), 3 + random.nextInt(2)));
        }
    }

    /**
     * 빔 파티클 업데이트 (update() 메서드에서 호출)
     */
    private void updateBeamParticles() {
        beamParticles.removeIf(beam -> {
            beam.update();
            return beam.isDead();
        });
    }

    /**
     * 빔 파티클 렌더링용 리스트 반환
     */
    public List<BeamParticle> getBeamParticles() {
        return new ArrayList<>(beamParticles);
    }

}