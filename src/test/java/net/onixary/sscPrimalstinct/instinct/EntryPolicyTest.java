package net.onixary.sscPrimalstinct.instinct;

import org.junit.Test;
import static org.junit.Assert.*;

public class EntryPolicyTest {
    @Test public void normalEntryNeverRequiresSelection() {
        assertFalse(EntryPolicy.needsSelection(false, false, false));
        assertFalse(EntryPolicy.needsSelection(false, true, true));
    }
    @Test public void enablingChoiceDoesNotForceExistingNormalPlayersToChoose() {
        assertFalse(EntryPolicy.needsSelection(true, true, false));
        assertTrue(EntryPolicy.needsSelection(true, false, false));
    }
    @Test public void previouslySelectedPlayersNeverRepeatInitialization() {
        assertFalse(EntryPolicy.needsSelection(true, false, true));
        assertTrue(EntryPolicy.legacyInitialized(true, 0, false, false));
        assertTrue(EntryPolicy.legacyInitialized(true, 100, true, false));
    }
    @Test public void migrateExistingProgressButNotEmptyComponents() {
        assertTrue(EntryPolicy.legacyInitialized(false, 42, false, false));
        assertTrue(EntryPolicy.legacyInitialized(false, 0, false, true));
        assertFalse(EntryPolicy.legacyInitialized(false, 0, false, false));
    }
}
