package br.com.roleout.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OpeningHoursTest {
    private OpeningHours.Rule rule(String day, String from, String to) { return new OpeningHours.Rule(day, List.of(new OpeningHours.Window(from,to))); }
    @Test void exceptionsOverrideWeeklyAndUnknownDoesNotMeanClosed() {
        var day = LocalDate.parse("2026-10-16");
        var hours = OpeningHours.validate(List.of(rule("5", "09:00", "18:00"), new OpeningHours.Rule(day.toString(), List.of())));
        assertFalse(OpeningHours.fits(hours, day, 600, 660));
        assertTrue(OpeningHours.fits(hours, day.plusWeeks(1), 600, 660));
        assertTrue(OpeningHours.fits(hours, day.plusDays(1), 600, 660));
        assertTrue(OpeningHours.fits(null, day, 600, 660));
        var special = OpeningHours.validate(List.of(new OpeningHours.Rule("5",List.of()), rule(day.toString(),"10:00","12:00")));
        assertTrue(OpeningHours.fits(special, day,600,720));
    }
    @Test void rejectsLunchGapsAndChecksNextDayAcrossMidnight() {
        var day = LocalDate.parse("2026-10-16");
        var hours = OpeningHours.validate(List.of(new OpeningHours.Rule("5",List.of(new OpeningHours.Window("09:00","12:00"),new OpeningHours.Window("14:00","18:00")))));
        assertTrue(OpeningHours.fits(hours, day, 660,720));
        assertFalse(OpeningHours.fits(hours,day,690,750));
        var night = OpeningHours.validate(List.of(rule("5","22:00","24:00"),rule("6","00:00","02:00")));
        assertTrue(OpeningHours.fits(night,day,1380,1560));
        assertFalse(OpeningHours.fits(night,day,1380,1561));
        var closed = OpeningHours.validate(List.of(rule("5","22:00","24:00"),new OpeningHours.Rule("2026-10-17",List.of())));
        assertFalse(OpeningHours.fits(closed,day,1380,1500));
    }
    @Test void validatesDatesClocksDuplicatesOverlapAndRoundTrips() {
        var hours = OpeningHours.validate(List.of(rule("7","10:00","12:00")));
        assertEquals(hours, OpeningHours.decode(OpeningHours.encode(hours)));
        assertTrue(OpeningHours.describe(hours).contains("Domingo"));
        for (var r : List.of(rule("0","10:00","12:00"),rule("2027-02-29","10:00","12:00"),rule("1","24:00","24:00"),rule("1","22:00","02:00"),rule("1","09:60","12:00")))
            assertThrows(IllegalArgumentException.class, () -> OpeningHours.validate(List.of(r)));
        assertThrows(IllegalArgumentException.class, () -> OpeningHours.validate(List.of(rule("1","10:00","12:00"),rule("1","14:00","16:00"))));
        assertThrows(IllegalArgumentException.class, () -> OpeningHours.validate(List.of(new OpeningHours.Rule("1", List.of(new OpeningHours.Window("10:00","12:00"),new OpeningHours.Window("11:00","13:00"))))));
        assertThrows(IllegalArgumentException.class, () -> OpeningHours.validate(Arrays.asList((OpeningHours.Rule)null)));
    }
}
