package component.config;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import component.ColorBlindPalette;
import component.GameConfig;
import component.config.Settings.ScreenSize;
import component.score.ScoreBoard;

public class Settings {

    // 조작 키
    public enum Action { Left, Right, SoftDrop, HardDrop, Rotate }

    public boolean blindMode = false;
    public ColorBlindPalette.Mode colorBlindMode = ColorBlindPalette.Mode.NORMAL; // ✅ 추가
    public final Map<Action, Integer> keymap = new EnumMap<>(Action.class);

    private static final Path PATH = Paths.get("config/settings.properties");
    private final List<Consumer<Settings>> listeners = new ArrayList<>();

    private ScoreBoard scoreBoard;

    public void setScoreBoard(ScoreBoard scoreBoard) {
        this.scoreBoard = scoreBoard;
    }

    // 화면 크기

    public static enum ScreenSize {                     
        SMALL(600, 480),                            
        MEDIUM(900, 720),                             
        LARGE(1200, 840);                               

        public final int width;                        
        public final int height;                       

        ScreenSize(int w, int h) {                     
            this.width  = w;
            this.height = h;
        }

        public java.awt.Dimension toDimension() {       
            return new java.awt.Dimension(width, height);
        }
    }

    public ScreenSize screenSize = ScreenSize.MEDIUM; 
    
    // ===== 조작 키 =====
    // 기본 값 설정
    private void applyDefaults() {
        blindMode = false;
        screenSize = ScreenSize.MEDIUM;
        colorBlindMode = ColorBlindPalette.Mode.NORMAL; 
        keymap.clear();
        keymap.put(Action.Left, 37);
        keymap.put(Action.Right, 39);
        keymap.put(Action.SoftDrop, 40);
        keymap.put(Action.HardDrop, 32);
        keymap.put(Action.Rotate, 38);
    }

    // 로드
    public static Settings load() {
        Settings s = new Settings();
        s.applyDefaults();
        Properties p = new Properties();
        try {
            if (Files.exists(PATH))
                try (InputStream in = Files.newInputStream(PATH)) {p.load(in);}

            s.blindMode = Boolean.parseBoolean(
                p.getProperty("blindMode", String.valueOf(s.blindMode))
            );
            s.screenSize = ScreenSize.valueOf(
                p.getProperty("screenSize", s.screenSize.name())
            );
            s.colorBlindMode = ColorBlindPalette.Mode.valueOf(
                    p.getProperty("colorBlindMode", "NORMAL"));
            s.parseKeymap(p.getProperty("keymap"));
        } catch (Exception ignore) {}
        return s;
    }

    // 저장
    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Properties p = new Properties();
            p.setProperty("blindMode", String.valueOf(blindMode));
            p.setProperty("screenSize", screenSize.name());
            p.setProperty("colorBlindMode", colorBlindMode.name()); 
            p.setProperty("keymap", formatKeymap());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                p.store(out, "Tetris Settings");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatKeymap() {
        StringBuilder sb = new StringBuilder();
        for(var e: keymap.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    public void update(Consumer<Settings> edit) {
        edit.accept(this);
        save();
        listeners.forEach(l -> l.accept(this));
    }
    
    public void onChange(Consumer<Settings> listener) {
        listeners.add(listener);
    }

    // 기본값으로 reset
    public void resetToDefaults() {
        applyDefaults();
        save();
        listeners.forEach(l -> l.accept(this));
    }

    private void parseKeymap(String s) {
        if (s == null || s.isEmpty()) return;
        try {
            for (String part : s.split(",")) {
                String[] kv = part.split("=");
                keymap.put(Action.valueOf(kv[0]), Integer.parseInt(kv[1]));
            }
            
        } catch (Exception ignore) {}
    }

    // ===== 색맹 모드 on/off =====
    public void toggleBlindMode() {
        update(s -> s.blindMode = !s.blindMode);
    }

    // ===== 스크린 사이즈 변경 =====
    public void setScreenScale(ScreenSize size) {
        update(s -> s.screenSize = size);
    }

    // ===== 스코어보드 초기화 =====
    public void resetScoreBoardAll() {
        try {
            if (scoreBoard != null) scoreBoard.resetAll();
            else ScoreBoard.createDefault().resetAll();
            listeners.forEach(l -> l.accept(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 특정 모드/난이도 버킷만 초기화 */
    public void resetScoreBoard(GameConfig.Mode mode, GameConfig.Difficulty diff) {
        try {
            if (scoreBoard != null) scoreBoard.resetBucket(mode, diff);
            else ScoreBoard.createDefault().resetBucket(mode, diff);
            listeners.forEach(l -> l.accept(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Settings() {
        applyDefaults(); 
    }

    public Settings(Settings other) {
        this(); 
        this.blindMode = other.blindMode;
        this.screenSize = other.screenSize;
        this.colorBlindMode = other.colorBlindMode;
        this.keymap.clear();
        this.keymap.putAll(other.keymap);
    }
}
