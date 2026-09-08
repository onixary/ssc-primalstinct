package net.onixary.sscPrimalstinct.instinct;

import net.onixary.sscPrimalstinct.data.PrimalLevels;
import net.onixary.sscPrimalstinct.util.TextScrambling;
import org.junit.Test;
import static org.junit.Assert.*;

public class InstinctStagesTest {
    @Test public void initialStageIsOneAndOnlyFullValueIsFive() {
        var levels = PrimalLevels.defaults();
        assertEquals(1, levels.levelForValue(0));
        assertEquals(1, levels.levelForValue(24.999f));
        assertEquals(2, levels.levelForValue(25));
        assertEquals(2, levels.levelForValue(49.999f));
        assertEquals(3, levels.levelForValue(50));
        assertEquals(3, levels.levelForValue(74.999f));
        assertEquals(4, levels.levelForValue(75));
        assertEquals(4, levels.levelForValue(99.999f));
        assertEquals(5, levels.levelForValue(100));
        assertFalse(levels.isMaxValue(99.999f));
        assertTrue(levels.isMaxValue(100));
    }

    @Test public void scramblingIsStableReversibleAndPreservesWhitespace() {
        String original = "Ancient instinct 标牌文字\n";
        for (int i = 0; i < original.length(); i++) {
            int cp = original.charAt(i);
            assertFalse(TextScrambling.selected(i, cp, 0));
            boolean selected = TextScrambling.selected(i, cp, 1);
            assertEquals(selected, TextScrambling.selected(i, cp, 1));
            assertEquals(!Character.isWhitespace(cp), selected);
        }
    }

    @Test public void partialScramblingChangesOnlyTheSelectedFraction() {
        int changed = 0;
        for (int i = 0; i < 10000; i++) if (TextScrambling.selected(i, '本', .15f)) changed++;
        assertTrue(changed > 1300 && changed < 1700);
    }
}
