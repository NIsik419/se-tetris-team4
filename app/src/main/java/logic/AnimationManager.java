package logic;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AnimationManager (개선 버전)
 * -----------------
 * - 애니메이션 충돌 방지 (유연한 정책)
 * - ITEM_EFFECT는 LINE_CLEAR를 대기하지 않고 바로 실행 가능
 * - 같은 타입만 카운트 관리
 */
public class AnimationManager {
    
    public enum AnimationType {
        NONE,
        LINE_CLEAR,
        ITEM_EFFECT,
        GRAVITY
    }
    
    private final AtomicInteger lineClearCount = new AtomicInteger(0);
    private final AtomicInteger itemEffectCount = new AtomicInteger(0);
    
    /**
     * 애니메이션 시작
     */
    public synchronized boolean tryStart(AnimationType type) {
        switch (type) {
            case LINE_CLEAR:
                lineClearCount.incrementAndGet();
                System.out.println("[AnimMgr] LINE_CLEAR started (count=" + lineClearCount.get() + ")");
                return true;
                
            case ITEM_EFFECT:
                itemEffectCount.incrementAndGet();
                System.out.println("[AnimMgr] ITEM_EFFECT started (count=" + itemEffectCount.get() + ")");
                return true;
                
            default:
                return true;
        }
    }
    
    /**
     * 애니메이션 종료
     */
    public synchronized void finish(AnimationType type) {
        switch (type) {
            case LINE_CLEAR:
                int remaining = lineClearCount.decrementAndGet();
                if (remaining < 0) lineClearCount.set(0);
                System.out.println("[AnimMgr] LINE_CLEAR finished (remaining=" + lineClearCount.get() + ")");
                break;
                
            case ITEM_EFFECT:
                int itemRemaining = itemEffectCount.decrementAndGet();
                if (itemRemaining < 0) itemEffectCount.set(0);
                System.out.println("[AnimMgr] ITEM_EFFECT finished (remaining=" + itemEffectCount.get() + ")");
                break;
        }
    }
    
    /**
     * 강제 종료
     */
    public synchronized void forceStop() {
        lineClearCount.set(0);
        itemEffectCount.set(0);
        System.out.println("[AnimMgr] Force stopped all animations");
    }
    
    /**
     * 특정 타입의 애니메이션 실행 중인지 확인
     */
    public boolean isAnimating(AnimationType type) {
        switch (type) {
            case LINE_CLEAR:
                return lineClearCount.get() > 0;
            case ITEM_EFFECT:
                return itemEffectCount.get() > 0;
            default:
                return false;
        }
    }
    
    /**
     * 모든 애니메이션 실행 중인지 확인
     */
    public boolean isAnimating() {
        return lineClearCount.get() > 0 || itemEffectCount.get() > 0;
    }
    
    /**
     * 현재 실행 중인 애니메이션 타입 (호환성)
     */
    public AnimationType getCurrentType() {
        if (itemEffectCount.get() > 0) return AnimationType.ITEM_EFFECT;
        if (lineClearCount.get() > 0) return AnimationType.LINE_CLEAR;
        return AnimationType.NONE;
    }
    
    /**
     * 대기 (실제로는 대기하지 않음 - 병렬 실행 허용)
     */
    public void waitForIdle(long timeoutMs) {
        // 더 이상 대기하지 않음 - 병렬 실행 허용
    }
}