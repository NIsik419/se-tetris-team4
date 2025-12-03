package logic;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 파티클 효과 시스템 - 라인 클리어 & 중력 가시성 강화 버전
 */
public class ParticleSystem {
    private final List<Particle> particles = new ArrayList<>();
    private final List<TrailParticle> trailParticles = new ArrayList<>();
    private final Random random = new Random();

    public static class Particle {
        public double x, y;
        public double vx, vy;
        public Color color;
        public int life;
        public int maxLife;
        public int size;

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
            vy += 0.4;
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
     * 🌈 궤적 파티클 (중력 효과용)
     */
    public static class TrailParticle {
        public int x, y;
        public Color color;
        public int life;
        public int maxLife;
        public int cellSize;

        public TrailParticle(int x, int y, Color color, int life, int cellSize) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.life = life;
            this.maxLife = life;
            this.cellSize = cellSize;
        }

        public void update() {
            life--;
        }

        public boolean isDead() {
            return life <= 0;
        }

        public float getAlpha() {
            return (float) life / maxLife * 0.5f; // 최대 50% 투명도
        }
    }

    /**
     * ⚡ 번개 파티클 (LightningItem용)
     */
    public void createLightningParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        int count = 5 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI / 2;
            double speed = 2.5 + random.nextDouble() * 2.5;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;

            Color lightningColor = random.nextBoolean()
                    ? new Color(255, 240, 100)
                    : new Color(255, 255, 255);

            int size = 2;
            int life = 6 + random.nextInt(6);

            particles.add(new Particle(centerX, centerY, vx, vy, lightningColor, life, size));
        }

        int sparkCount = 3 + random.nextInt(3);
        for (int i = 0; i < sparkCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 1.2 + random.nextDouble() * 1.5;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 0.8;

            Color sparkColor = new Color(150, 220, 255);
            int size = 1;
            int life = 8 + random.nextInt(6);

            particles.add(new Particle(centerX, centerY, vx, vy, sparkColor, life, size));
        }
    }

    /**
     * ⭐ 부스러기 효과 (LineClearItem용)
     */
    public void createDebrisParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double vx = (random.nextDouble() - 0.5) * 1.2;
            double vy = 0.4 + random.nextDouble() * 0.8;

            Color debrisColor = new Color(
                    (int) (blockColor.getRed() * 0.7),
                    (int) (blockColor.getGreen() * 0.7),
                    (int) (blockColor.getBlue() * 0.7));

            int size = 1;
            int life = 10 + random.nextInt(8);

            particles.add(new Particle(centerX, centerY, vx, vy, debrisColor, life, size));
        }
    }

    /**
     * ⭐ 폭발 전용 파티클 (WeightItem, ColorBomb 등)
     */
    public void createExplosionParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        int mainCount = 8 + random.nextInt(5);
        for (int i = 0; i < mainCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 2.5 + random.nextDouble() * 3.0;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 2.0;

            Color fireColor = new Color(
                    Math.min(255, 200 + random.nextInt(56)),
                    Math.min(255, 100 + random.nextInt(100)),
                    50 + random.nextInt(50));

            int size = 2 + random.nextInt(2);
            int life = 8 + random.nextInt(6);

            particles.add(new Particle(centerX, centerY, vx, vy, fireColor, life, size));
        }

        int sparkCount = 5 + random.nextInt(4);
        for (int i = 0; i < sparkCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 3.0 + random.nextDouble() * 2.5;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 2.5;

            Color sparkColor = new Color(
                    255,
                    200 + random.nextInt(56),
                    100 + random.nextInt(100));

            int size = 1;
            int life = 6 + random.nextInt(6);

            particles.add(new Particle(centerX, centerY, vx, vy, sparkColor, life, size));
        }

        int smokeCount = 2 + random.nextInt(3);
        for (int i = 0; i < smokeCount; i++) {
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI / 4;
            double speed = 0.4 + random.nextDouble() * 0.6;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;

            int gray = 60 + random.nextInt(40);
            Color smokeColor = new Color(gray, gray, gray);

            int size = 3 + random.nextInt(2);
            int life = 12 + random.nextInt(10);

            particles.add(new Particle(centerX, centerY, vx, vy, smokeColor, life, size));
        }
    }

    /**
     * ✨ 블록이 사라질 때 파티클 생성 (라인 클리어용 - 강화 버전)
     */
    public void createBlockParticles(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        int count = 8 + random.nextInt(5);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 2.5 + random.nextDouble() * 3.0;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 2.0;

            Color particleColor = new Color(
                    Math.min(255, blockColor.getRed() + 80),
                    Math.min(255, blockColor.getGreen() + 80),
                    Math.min(255, blockColor.getBlue() + 80));

            int size = 3 + random.nextInt(2);
            int life = 15 + random.nextInt(10);

            particles.add(new Particle(centerX, centerY, vx, vy, particleColor, life, size));
        }

        int sparkCount = 4 + random.nextInt(3);
        for (int i = 0; i < sparkCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 3.5 + random.nextDouble() * 2.0;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 2.5;

            Color sparkColor = new Color(255, 255, 200);

            int size = 2;
            int life = 10 + random.nextInt(8);

            particles.add(new Particle(centerX, centerY, vx, vy, sparkColor, life, size));
        }
    }

    /**
     * 🌟 라인 전체에 대해 파티클 생성 (모든 블록에서 생성)
     */
    public void createLineParticles(int rowY, Color[][] board, int cellSize, int width) {
        for (int x = 0; x < width; x++) {
            if (board[rowY][x] != null) {
                createBlockParticles(x, rowY, board[rowY][x], cellSize);
            }
        }
    }

    /**
     * 🌊 중력 먼지 파티클 (블록이 떨어질 때)
     */
    public void createGravityDustParticle(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double vx = (random.nextDouble() - 0.5) * 0.5;
            double vy = 0.3 + random.nextDouble() * 0.5;

            int gray = 150 + random.nextInt(50);
            Color dustColor = new Color(gray, gray, gray);

            int size = 1;
            int life = 8 + random.nextInt(6);

            particles.add(new Particle(centerX, centerY, vx, vy, dustColor, life, size));
        }
    }

    /**
     * 🌈 중력 궤적 파티클 생성 (블록이 지나간 자리)
     */
    public void createGravityTrailParticle(int blockX, int blockY, Color blockColor, int cellSize) {
        // 궤적은 정적이므로 TrailParticle 사용
        trailParticles.add(new TrailParticle(blockX, blockY, blockColor, 12, cellSize));
    }

    /**
     * 💫 중력 궤적을 클러스터 전체에 생성
     */
    public void createClusterTrail(List<Point> cluster, Color[][] board, int cellSize) {
        for (Point p : cluster) {
            if (board[p.y][p.x] != null) {
                createGravityTrailParticle(p.x, p.y, board[p.y][p.x], cellSize);
            }
        }
    }

    /**
     * ⚡ 착지 시 충격파 효과
     */
    public void createLandingImpact(int blockX, int blockY, Color blockColor, int cellSize) {
        double centerX = blockX * cellSize + cellSize / 2.0;
        double centerY = blockY * cellSize + cellSize / 2.0;

        // 충격파 파티클 (바깥으로 퍼짐)
        int impactCount = 6 + random.nextInt(4);
        for (int i = 0; i < impactCount; i++) {
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI;
            double speed = 2.0 + random.nextDouble() * 2.0;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed * 0.3; // 주로 옆으로

            Color impactColor = new Color(
                    Math.min(255, blockColor.getRed() + 60),
                    Math.min(255, blockColor.getGreen() + 60),
                    Math.min(255, blockColor.getBlue() + 60));

            int size = 2;
            int life = 8 + random.nextInt(6);

            particles.add(new Particle(centerX, centerY, vx, vy, impactColor, life, size));
        }

        // 밝은 섬광
        int flashCount = 3 + random.nextInt(2);
        for (int i = 0; i < flashCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 1.0 + random.nextDouble() * 1.5;

            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;

            Color flashColor = new Color(255, 255, 255);

            int size = 2;
            int life = 6 + random.nextInt(4);

            particles.add(new Particle(centerX, centerY, vx, vy, flashColor, life, size));
        }
    }

    /**
     * 💥 클러스터 착지 시 전체 충격파
     */
    public void createClusterLandingImpact(List<Point> landedBlocks, Color[][] board, int cellSize) {
        for (Point p : landedBlocks) {
            if (board[p.y][p.x] != null) {
                createLandingImpact(p.x, p.y, board[p.y][p.x], cellSize);
            }
        }
    }

    /**
     * 모든 파티클 업데이트
     */
    public void update() {
        // 일반 파티클 업데이트
        particles.removeIf(p -> {
            p.update();
            return p.isDead();
        });

        // 궤적 파티클 업데이트
        trailParticles.removeIf(t -> {
            t.update();
            return t.isDead();
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
     * 궤적 파티클 렌더링용 리스트 반환
     */
    public List<TrailParticle> getTrailParticles() {
        return new ArrayList<>(trailParticles);
    }

    /**
     * 모든 파티클 즉시 제거
     */
    public void clear() {
        particles.clear();
        trailParticles.clear();
        beamParticles.clear();
    }

    /**
     * 현재 파티클 개수
     */
    public int getParticleCount() {
        return particles.size() + trailParticles.size();
    }

    /**
     * 하드 드롭용 빛 줄기 파티클
     */
    public static class BeamParticle {
        public int x;
        public int startY;
        public int endY;
        public Color color;
        public int life;
        public int maxLife;
        public int width;

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
     * 하드 드롭 넓은 광선 효과
     */
    public void createHardDropBeamWide(int startX, int widthCells, int startY, int endY,
            Color blockColor, int cellSize) {
        int boardHeight = 20;

        int beamCenterX = startX * cellSize + (widthCells * cellSize) / 2;
        int beamWidth = widthCells * cellSize;

        beamParticles.add(new BeamParticle(
                startX * cellSize + (widthCells * cellSize) / 2,
                0,
                boardHeight * cellSize,
                blockColor,
                6,
                beamWidth));

        double centerX = beamCenterX;
        double centerY = endY * cellSize + cellSize;

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
     * 빔 파티클 업데이트
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