package logic;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SoundManager - 게임 사운드 관리
 * ✅ 빠른 재생 (스레드 풀 사용)
 * ✅ 볼륨 조절
 * ✅ 중복 재생 허용
 */
public class SoundManager {
    
    public enum Sound {
        LINE_CLEAR_1,    // 1줄 클리어 (짧은 띵)
        LINE_CLEAR_2,    // 2줄 클리어 (더블 띵)
        LINE_CLEAR_3,    // 3줄 클리어 (트리플 띵)
        LINE_CLEAR_4,    // 4줄 클리어 (테트리스!)
        COMBO_2,         // 콤보 x2
        COMBO_3,         // 콤보 x3
        COMBO_5,         // 콤보 x5+
        HARD_DROP,       // 하드 드롭
        ROTATE,          // 회전
        MOVE,            // 좌우 이동
        ITEM_PICKUP,     // 아이템 획득
        EXPLOSION,       // 폭발 효과
        LIGHTNING,       // 번개 효과
        GAME_OVER,       // 게임 오버
        VICTORY          // 승리
    }
    
    private static SoundManager instance;
    private final Map<Sound, Clip> soundCache = new HashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    private boolean enabled = true;
    private float masterVolume = 0.7f; // 0.0 ~ 1.0
    
    private SoundManager() {
        preloadSounds();
    }
    
    public static synchronized SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }
    
    /**
     * 사운드 재생 (논블로킹)
     */
    public void play(Sound sound) {
        if (!enabled) return;
        
        executor.submit(() -> {
            try {
                playSync(sound, masterVolume);
            } catch (Exception e) {
                System.err.println("[SoundManager] Error playing " + sound + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * 사운드 재생 (커스텀 볼륨)
     */
    public void play(Sound sound, float volume) {
        if (!enabled) return;
        
        executor.submit(() -> {
            try {
                playSync(sound, volume);
            } catch (Exception e) {
                System.err.println("[SoundManager] Error playing " + sound + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * 동기 재생 (내부용)
     */
    private void playSync(Sound sound, float volume) throws Exception {
        // 매번 새 Clip 생성 (중복 재생 허용)
        Clip clip = loadClip(sound);
        if (clip == null) return;
        
        // 볼륨 설정
        setVolume(clip, volume);
        
        // 재생
        clip.setFramePosition(0);
        clip.start();
        
        // 재생 완료 후 자동 해제
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                clip.close();
            }
        });
    }
    
    /**
     * 사운드 파일 로드
     */
    private Clip loadClip(Sound sound) {
        try {
            // 실제 파일에서 로드하는 경우
            String filename = getSoundFilename(sound);
            URL url = getClass().getResource("/sounds/" + filename);
            
            if (url == null) {
                // 파일이 없으면 합성음 생성
                return generateSyntheticSound(sound);
            }
            
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            return clip;
            
        } catch (Exception e) {
            // 에러 시 합성음으로 대체
            return generateSyntheticSound(sound);
        }
    }
    
    /**
     * 볼륨 설정 (dB 단위)
     */
    private void setVolume(Clip clip, float volume) {
        try {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log(Math.max(0.0001f, volume)) / Math.log(10.0) * 20.0);
            gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(dB, gainControl.getMaximum())));
        } catch (Exception e) {
            // 볼륨 조절 불가능한 경우 무시
        }
    }
    
    /**
     * 합성음 생성 (파일이 없을 때 대체용)
     */
    private Clip generateSyntheticSound(Sound sound) {
        try {
            int sampleRate = 22050;
            int duration = getDuration(sound);
            int samples = sampleRate * duration / 1000;
            
            byte[] buffer = new byte[samples * 2];
            int frequency = getFrequency(sound);
            
            for (int i = 0; i < samples; i++) {
                double angle = 2.0 * Math.PI * i * frequency / sampleRate;
                double amplitude = 127 * Math.sin(angle);
                
                // 페이드 아웃
                if (i > samples * 0.7) {
                    amplitude *= (samples - i) / (samples * 0.3);
                }
                
                short value = (short) amplitude;
                buffer[i * 2] = (byte) (value & 0xFF);
                buffer[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
            }
            
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            Clip clip = AudioSystem.getClip();
            clip.open(format, buffer, 0, buffer.length);
            return clip;
            
        } catch (Exception e) {
            System.err.println("[SoundManager] Failed to generate sound: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 사운드별 주파수 설정
     */
    private int getFrequency(Sound sound) {
        switch (sound) {
            case LINE_CLEAR_1: return 440;  // A4
            case LINE_CLEAR_2: return 554;  // C#5
            case LINE_CLEAR_3: return 659;  // E5
            case LINE_CLEAR_4: return 880;  // A5
            case COMBO_2: return 523;       // C5
            case COMBO_3: return 659;       // E5
            case COMBO_5: return 784;       // G5
            case HARD_DROP: return 220;     // A3 (낮은 음)
            case ROTATE: return 330;        // E4
            case MOVE: return 294;          // D4
            case ITEM_PICKUP: return 700;   // 높은 음
            case EXPLOSION: return 100;     // 낮은 폭발음
            case LIGHTNING: return 1000;    // 높은 찌릿음
            case GAME_OVER: return 196;     // G3 (낮은 음)
            case VICTORY: return 1047;      // C6 (높은 음)
            default: return 440;
        }
    }
    
    /**
     * 사운드별 지속시간 (ms)
     */
    private int getDuration(Sound sound) {
        switch (sound) {
            case LINE_CLEAR_1: return 100;
            case LINE_CLEAR_2: return 150;
            case LINE_CLEAR_3: return 200;
            case LINE_CLEAR_4: return 300;
            case COMBO_2:
            case COMBO_3:
            case COMBO_5: return 150;
            case HARD_DROP: return 80;
            case ROTATE:
            case MOVE: return 50;
            case ITEM_PICKUP: return 120;
            case EXPLOSION: return 200;
            case LIGHTNING: return 150;
            case GAME_OVER: return 500;
            case VICTORY: return 400;
            default: return 100;
        }
    }
    
    /**
     * 사운드 파일명 매핑
     */
    private String getSoundFilename(Sound sound) {
        switch (sound) {
            case LINE_CLEAR_1: return "lineclear.wav";
            case LINE_CLEAR_2: return "lineclear.wav";
            case LINE_CLEAR_3: return "lineclear.wav";
            case LINE_CLEAR_4: return "lineclear.wav";
            case COMBO_2: return "combo.wav";
            case COMBO_3: return "combo.wav";
            case COMBO_5: return "combo.wav";
            case HARD_DROP: return "harddrop.wav";
            case ROTATE: return "rotate.wav";
            case MOVE: return "rotate.wav";
            //case ITEM_PICKUP: return "item.wav";
            case EXPLOSION: return "explosion.wav";
            case LIGHTNING: return "lightning.wav";
            case GAME_OVER: return "gameover.wav";
            case VICTORY: return "victory.wav";
            default: return "victory.wav";
        }
    }
    
    /**
     * 사운드 프리로드 (선택적)
     */
    private void preloadSounds() {
        // 자주 쓰는 사운드만 미리 로드
        // 필요 시 구현
    }
    
    // ============================================
    // 설정
    // ============================================
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
    }
    
    public float getMasterVolume() {
        return masterVolume;
    }
    
    /**
     * 모든 사운드 정지
     */
    public void stopAll() {
        soundCache.values().forEach(clip -> {
            if (clip.isRunning()) {
                clip.stop();
            }
        });
    }
    
    /**
     * 리소스 해제
     */
    public void dispose() {
        stopAll();
        soundCache.values().forEach(Clip::close);
        soundCache.clear();
        executor.shutdown();
    }
}