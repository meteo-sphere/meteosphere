package org.meteosphere.data.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaceNameTest {

    // The name the place was given, or its own when it has none
    @Test
    public void displayName() {
        Place place = new Place();
        place.name = "Riga";
        assertEquals("Riga", place.displayName());
        place.customName = "";
        assertEquals("Riga", place.displayName());
        place.customName = "Home";
        assertEquals("Home", place.displayName());
    }
}
