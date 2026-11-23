package it.gformy.nis2.comune;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Orologio iniettabile: le scadenze si calcolano su "adesso" e i test possono fissarlo. */
@Configuration
public class ConfigurazioneOrologio {

    @Bean
    Clock orologio() {
        return Clock.systemUTC();
    }
}
