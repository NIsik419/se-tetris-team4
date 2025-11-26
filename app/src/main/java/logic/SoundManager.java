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
        // 게임 효과음
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
        VICTORY,         // 승리
        
        // 메뉴 효과음
        MENU_HOVER,      // 버튼 호버
        MENU_CLICK,      // 버튼 클릭 (서브)
        MENU_SELECT,     // 메뉴 선택 (메인)
        MENU_MOVE,       // 커서 이동
        MENU_EXPAND,     // 메뉴 확장
        MENU_COLLAPSE,   // 메뉴 축소
        MENU_BACK        // 뒤로가기
    }
    public enum BGM {
        MENU,            // 메뉴 BGM
        GAME_NORMAL,     // 일반 게임 BGM
        GAME_INTENSE,    // 긴장감 있는 BGM (고속)
        GAME_ITEM,       // 아이템 모드 BGM
        VERSUS           // 대전 모드 BGM
    }
    private static SoundManager instance;
    private final Map<Sound, Clip> soundCache = new HashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    private boolean enabled = true;
    private float masterVolume = 0.6f; // 0.0 ~ 1.0
    private float bgmVolume = 0.2f; // BGM 전용 볼륨
    
    private Clip currentBGM = null;
    private BGM currentBGMType = null;

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
            
            // 메뉴 사운드
            case MENU_HOVER: return 600;    // 부드러운 호버음
            case MENU_CLICK: return 523;    // C5 (클릭)
            case MENU_SELECT: return 659;   // E5 (선택)
            case MENU_MOVE: return 440;     // A4 (이동)
            case MENU_EXPAND: return 698;   // F5 (확장)
            case MENU_COLLAPSE: return 392; // G4 (축소)
            case MENU_BACK: return 349;     // F4 (뒤로)
            
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
            
            // 메뉴 사운드 (짧고 경쾌하게)
            case MENU_HOVER: return 40;
            case MENU_CLICK: return 60;
            case MENU_SELECT: return 100;
            case MENU_MOVE: return 50;
            case MENU_EXPAND: return 80;
            case MENU_COLLAPSE: return 70;
            case MENU_BACK: return 90;
            
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

             // 메뉴 사운드
            case MENU_HOVER: return "cursor.wav";
            case MENU_CLICK: return "menu_click.wav";
            case MENU_SELECT: return "menu_select.wav";
            case MENU_MOVE: return "cursor.wav";
            case MENU_EXPAND: return "menu_expand.wav";
            case MENU_COLLAPSE: return "menu_collapse.wav";
            case MENU_BACK: return "menu_back.wav";
            
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
     /**
     * BGM 재생 (루프)
     */
    public void playBGM(BGM bgm) {
        if (!enabled) return;
        
        // 같은 BGM이 재생 중이면 무시
        if (currentBGM != null && currentBGMType == bgm && currentBGM.isRunning()) {
            return;
        }
        
        // 기존 BGM 정지
        stopBGM();
        
        executor.submit(() -> {
            try {
                currentBGM = loadBGMClip(bgm);
                if (currentBGM == null) return;
                
                setVolume(currentBGM, bgmVolume);
                currentBGM.loop(Clip.LOOP_CONTINUOUSLY);
                currentBGM.start();
                currentBGMType = bgm;
                
                System.out.println("[SoundManager] BGM started: " + bgm);
                
            } catch (Exception e) {
                System.err.println("[SoundManager] Error playing BGM " + bgm + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * BGM 정지
     */
    public void stopBGM() {
        if (currentBGM != null) {
            currentBGM.stop();
            currentBGM.close();
            currentBGM = null;
            currentBGMType = null;
            System.out.println("[SoundManager] BGM stopped");
        }
    }
    
    /**
     * BGM 일시정지
     */
    public void pauseBGM() {
        if (currentBGM != null && currentBGM.isRunning()) {
            currentBGM.stop();
        }
    }
    
    /**
     * BGM 재개
     */
    public void resumeBGM() {
        if (currentBGM != null && !currentBGM.isRunning()) {
            currentBGM.start();
        }
    }
    
    /**
     * BGM 볼륨 설정
     */
    public void setBGMVolume(float volume) {
        this.bgmVolume = Math.max(0.0f, Math.min(1.0f, volume));
        if (currentBGM != null) {
            setVolume(currentBGM, bgmVolume);
        }
    }
    
    public float getBGMVolume() {
        return bgmVolume;
    }
    
    /**
     * BGM Clip 로드
     */
    private Clip loadBGMClip(BGM bgm) {
        try {
            String filename = getBGMFilename(bgm);
            URL url = getClass().getResource("/sounds/bgm/" + filename);
            
            if (url == null) {
                // 파일이 없으면 합성 BGM 생성
                return generateSyntheticBGM(bgm);
            }
            
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            return clip;
            
        } catch (Exception e) {
            return generateSyntheticBGM(bgm);
        }
    }
    
    /**
     * BGM 파일명 매핑
     */
    private String getBGMFilename(BGM bgm) {
        switch (bgm) {
            case MENU: return "Menu.wav";
            case GAME_NORMAL: return "Menu.wav";
            case GAME_INTENSE: return "Menu.wav";
            case GAME_ITEM: return "Menu.wav";
            case VERSUS: return "Menu.wav";
            default: return "Menu.wav";
        }
    }
    
    /**
     * 합성 BGM 생성 (간단한 루프)
     */
    private Clip generateSyntheticBGM(BGM bgm) {
        try {
            int sampleRate = 22050;
            int duration = 4000; // 4초 루프
            int samples = sampleRate * duration / 1000;
            
            byte[] buffer = new byte[samples * 2];
            
            // BGM 타입별 멜로디 패턴
            int[] melody = getMelodyPattern(bgm);
            int noteLength = samples / melody.length;
            
            for (int i = 0; i < samples; i++) {
                int noteIndex = i / noteLength;
                if (noteIndex >= melody.length) noteIndex = melody.length - 1;
                
                int frequency = melody[noteIndex];
                double angle = 2.0 * Math.PI * i * frequency / sampleRate;
                
                // 사인파 + 약간의 하모닉
                double amplitude = 80 * (
                    Math.sin(angle) * 0.6 + 
                    Math.sin(angle * 2) * 0.3 + 
                    Math.sin(angle * 3) * 0.1
                );
                
                // 부드러운 엔벨로프
                double envelope = 1.0;
                int notePos = i % noteLength;
                if (notePos < noteLength * 0.05) {
                    envelope = notePos / (noteLength * 0.05);
                } else if (notePos > noteLength * 0.9) {
                    envelope = (noteLength - notePos) / (noteLength * 0.1);
                }
                
                amplitude *= envelope;
                
                short value = (short) amplitude;
                buffer[i * 2] = (byte) (value & 0xFF);
                buffer[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
            }
            
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            Clip clip = AudioSystem.getClip();
            clip.open(format, buffer, 0, buffer.length);
            return clip;
            
        } catch (Exception e) {
            System.err.println("[SoundManager] Failed to generate BGM: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * BGM별 멜로디 패턴 (주파수 배열)
     */
    private int[] getMelodyPattern(BGM bgm) {
        switch (bgm) {
            case MENU:
                // 차분한 메뉴 테마 (C-E-G-E-C-G-E-C)
                return new int[]{523, 659, 784, 659, 523, 784, 659, 523};
            case GAME_NORMAL:
                // 경쾌한 게임 테마 (C-D-E-G-E-D-C-G)
                return new int[]{523, 587, 659, 784, 659, 587, 523, 784};
            case GAME_INTENSE:
                // 빠르고 긴장감 있는 테마 (E-G-A-B-A-G-E-D)
                return new int[]{659, 784, 880, 988, 880, 784, 659, 587};
            case GAME_ITEM:
                // 재미있는 아이템 테마 (G-A-B-D-B-A-G-E)
                return new int[]{784, 880, 988, 1175, 988, 880, 784, 659};
            case VERSUS:
                // 대결 테마 (A-B-D-E-D-B-A-G)
                return new int[]{880, 988, 1175, 1319, 1175, 988, 880, 784};
            default:
                return new int[]{440, 494, 523, 587, 659, 698, 784, 880};
        }
    }
}