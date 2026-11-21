package tn.ginf.smarthealthcare.rest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Horloge réglable partagée par les tests : mardi 2026-11-17, 10h10 à Tunis par défaut. */
public class HorlogeTest extends Clock {

    public static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    public Instant instant = ZonedDateTime.of(2026, 11, 17, 10, 10, 0, 0, TUNIS).toInstant();  // mardi 17, 10h10

    public void regler(int jour, int heure, int minute) {
        instant = ZonedDateTime.of(2026, 11, jour, heure, minute, 0, 0, TUNIS).toInstant();
    }

    @Override public ZoneId getZone() { return TUNIS; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }

    @TestConfiguration
    public static class Config {
        @Bean @Primary
        public HorlogeTest horlogeTest() { return new HorlogeTest(); }
    }
}
