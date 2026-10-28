package org.meteosphere.ui.widgets;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The weather widgets' line of the wind, in the Russian strings
public class WidgetWindTest {

    private static final String[] DIRECTIONS = {"Безветренно", "С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
    private static final String WITH_DIRECTION = "%1$s\u00a0%2$s, %3$s";
    private static final String UP_TO = "до %1$s\u00a0%2$s";

    private static String wind(int speed, int direction, boolean dayHighest) {
        return WidgetFrame.windText(DIRECTIONS, WITH_DIRECTION, UP_TO, speed, direction, dayHighest, String.valueOf(speed), "м/с");
    }

    // No wind: the calm's word, whatever the direction, the day's highest too
    @Test
    public void calm() {
        assertEquals("Безветренно", wind(0, 0, false));
        assertEquals("Безветренно", wind(0, 3, false));
        assertEquals("Безветренно", wind(0, 2, true));
    }

    // A speed without a direction, or with one the list hasn't: the speed and its unit alone, which no line breaks
    @Test
    public void withoutDirection() {
        assertEquals("3\u00a0м/с", wind(3, 0, false));
        assertEquals("3\u00a0м/с", wind(3, 9, false));
        assertEquals("3\u00a0м/с", wind(3, -1, false));
    }

    @Test
    public void withDirection() {
        assertEquals("3\u00a0м/с, СВ", wind(3, 2, false));
        assertEquals("12\u00a0м/с, СЗ", wind(12, 8, false));
    }

    // The 2x1's day: up to its highest wind, without the direction, which its arrow shows
    @Test
    public void dayHighest() {
        assertEquals("до 3\u00a0м/с", wind(3, 2, true));
        assertEquals("до 7\u00a0м/с", wind(7, 0, true));
    }

    // The speed and its unit as given, in the user's unit, where the pattern puts them
    @Test
    public void speedAndUnitAsGiven() {
        assertEquals("NE 11 km/h", WidgetFrame.windText(new String[]{"Calm", "N", "NE"}, "%3$s %1$s %2$s",
                "up to %1$s %2$s", 3, 2, false, "11", "km/h"));
    }
}
