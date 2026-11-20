package br.com.roleout.service;
import br.com.roleout.model.RoleCharacteristics;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
class RoleCharacteristicsTest {
    @Test void validatesKnownChoicesAndRoundTripsOptionalGroups() {
        var value = RoleCharacteristics.validate(new RoleCharacteristics(List.of("Rock"), null, List.of("Familiar")));
        assertEquals(List.of(), value.comida());
        assertEquals(value, RoleCharacteristics.decode(RoleCharacteristics.encode(value)));
        assertNull(RoleCharacteristics.validate(null));
        assertNull(RoleCharacteristics.decode(null));
    }
    @Test void rejectsUnknownDuplicateNullAndExcessiveSelections() {
        for (var values : List.of(List.of("Inventado"), List.of("Rock", "Rock"), Arrays.asList((String)null), java.util.Collections.nCopies(9, "Rock"))) {
            assertThrows(IllegalArgumentException.class, () -> RoleCharacteristics.validate(new RoleCharacteristics(values, null, null)));
        }
        assertThrows(IllegalArgumentException.class, () -> RoleCharacteristics.validate(new RoleCharacteristics(null, List.of("Rock"), null)));
        assertThrows(IllegalArgumentException.class, () -> RoleCharacteristics.validate(new RoleCharacteristics(null, null, List.of("Japonesa"))));
    }
}
