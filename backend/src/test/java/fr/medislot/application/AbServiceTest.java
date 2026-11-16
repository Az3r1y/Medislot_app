package fr.medislot.application;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class AbServiceTest {
    @Test
    void repartitionStableEtProcheDe50() {
        String s = UUID.randomUUID().toString();
        assertEquals(
                AbService.variantFor(AbService.EXP, s), AbService.variantFor(AbService.EXP, s));
        long b =
                java.util.stream.IntStream.range(0, 10_000)
                        .filter(
                                i ->
                                        AbService.variantFor(AbService.EXP, "visiteur-" + i)
                                                .equals("B"))
                        .count();
        assertTrue(b > 4_700 && b < 5_300, "B=" + b);
    }
}
