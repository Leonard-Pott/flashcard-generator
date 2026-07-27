package de.leonard.flashcards.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /**
     * Als Bean, damit die Wiederholungslogik in Tests mit einem festen Datum arbeiten
     * kann. Bewusst die Systemzeitzone: die Faelligkeit richtet sich nach dem Kalendertag
     * des Nutzers, nicht nach UTC.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
