package org.hibernate.infra.bot.util;

import java.time.Clock;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ClockProducer {

    @Produces
    @ApplicationScoped
    @Unremovable
    Clock clock() {
        return Clock.systemUTC();
    }

}
