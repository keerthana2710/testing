package com.lxp.steps;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * {now} in a feature value: the run's day and time (e.g. 041530), the same
 * in every step class for the whole run, so a name typed in one step is
 * found again in another.
 */
public final class RunClock {

    public static final String NOW = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddHHmm"));

    private RunClock() {
    }

    public static String withNow(String s) {
        return s.replace("{now}", NOW);
    }
}
