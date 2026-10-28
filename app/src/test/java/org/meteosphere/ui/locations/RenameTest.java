package org.meteosphere.ui.locations;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The custom name the rename screen saves for the text typed, and the place's country and region under it
public class RenameTest {

    // The own name is no custom name: it follows the app's language
    @Test
    public void ownName() {
        assertEquals("", RenameActivity.customName("Rīga", "Rīga"));
    }

    @Test
    public void empty() {
        assertEquals("", RenameActivity.customName("", "Rīga"));
        assertEquals("", RenameActivity.customName("   ", "Rīga"));
    }

    @Test
    public void spacesAround() {
        assertEquals("Home", RenameActivity.customName("  Home ", "Rīga"));
        assertEquals("", RenameActivity.customName(" Rīga  ", "Rīga"));
    }

    @Test
    public void anotherName() {
        assertEquals("Home", RenameActivity.customName("Home", "Rīga"));
        // Only the very same name is the own one
        assertEquals("rīga", RenameActivity.customName("rīga", "Rīga"));
    }

    @Test
    public void area() {
        assertEquals("Latvia, Rīga", RenameActivity.area("Latvia", "Rīga"));
        assertEquals("Latvia", RenameActivity.area("Latvia", ""));
        assertEquals("Latvia", RenameActivity.area("Latvia", null));
    }
}
