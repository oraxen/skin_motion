package dev.th0rgal.skinmotion.core.animation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameCursorTest {

    private static List<Integer> changes(FrameCursor cursor, int ticks, int frames, int duration, String mode) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            int index = cursor.tick(frames, duration, mode);
            if (index != FrameCursor.UNCHANGED) {
                result.add(index);
            }
        }
        return result;
    }

    @Test
    void loopAdvancesOncePerDurationAndWraps() {
        FrameCursor cursor = new FrameCursor();
        assertEquals(FrameCursor.UNCHANGED, cursor.tick(3, 2, FrameCursor.LOOP));
        assertEquals(1, cursor.tick(3, 2, FrameCursor.LOOP));
        assertEquals(List.of(2, 0, 1, 2), changes(cursor, 8, 3, 2, FrameCursor.LOOP));
    }

    @Test
    void onceStopsOnLastFrameWithoutResending() {
        FrameCursor cursor = new FrameCursor();
        assertEquals(List.of(1, 2), changes(cursor, 20, 3, 1, FrameCursor.ONCE));
        assertEquals(2, cursor.getFrameIndex());
    }

    @Test
    void pingPongBouncesBetweenEnds() {
        FrameCursor cursor = new FrameCursor();
        assertEquals(List.of(1, 2, 3, 2, 1, 0, 1, 2), changes(cursor, 8, 4, 1, FrameCursor.PING_PONG));
    }

    @Test
    void pingPongWithTwoFramesToggles() {
        FrameCursor cursor = new FrameCursor();
        assertEquals(List.of(1, 0, 1, 0), changes(cursor, 4, 2, 1, FrameCursor.PING_PONG));
    }

    @Test
    void unknownModeLoops() {
        FrameCursor cursor = new FrameCursor();
        assertEquals(List.of(1, 0, 1), changes(cursor, 3, 2, 1, "bogus"));
    }

    @Test
    void singleFrameNeverChanges() {
        FrameCursor cursor = new FrameCursor();
        assertEquals(List.of(), changes(cursor, 50, 1, 1, FrameCursor.LOOP));
    }

    @Test
    void nonPositiveDurationIsTreatedAsOneTick() {
        FrameCursor cursor = new FrameCursor();
        assertEquals(List.of(1, 0), changes(cursor, 2, 2, 0, FrameCursor.LOOP));
    }

    @Test
    void shrinkingAnimationRestartsFromFirstFrame() {
        FrameCursor cursor = new FrameCursor();
        changes(cursor, 4, 5, 1, FrameCursor.LOOP);
        assertEquals(4, cursor.getFrameIndex());
        assertEquals(1, cursor.tick(2, 1, FrameCursor.LOOP));
    }
}
