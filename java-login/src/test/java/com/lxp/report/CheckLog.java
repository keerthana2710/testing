package com.lxp.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Expected-vs-actual results recorded by the step definitions while a step
 * runs. {@link ExpectedVsActualReport} collects them after each step.
 *
 * A soft check is recorded but does not stop the scenario; Hooks fails the
 * scenario at the end if any soft check failed. The suite runs one scenario
 * at a time, so plain static lists are enough.
 */
public final class CheckLog {

    /**
     * One expected-vs-actual row. A review row (review = true) is neither a
     * pass nor a fail: it records something only a person can judge (e.g.
     * whether lecture content suits beginners) for reading in the report.
     */
    public record Check(String expected, String actual, boolean passed, boolean review) {
        public Check(String expected, String actual, boolean passed) {
            this(expected, actual, passed, false);
        }
    }

    private static final List<Check> CHECKS = new ArrayList<>();
    private static final List<String> SOFT_FAILURES = new ArrayList<>();

    private CheckLog() {
    }

    public static void check(String expected, String actual, boolean passed) {
        CHECKS.add(new Check(expected, actual, passed));
    }

    public static void soft(String expected, String actual, boolean passed) {
        check(expected, actual, passed);
        if (!passed) {
            SOFT_FAILURES.add(expected + " -- actual: " + actual);
        }
    }

    /** Records something to read and judge by hand; shown as REVIEW, never fails the run. */
    public static void review(String what, String content) {
        CHECKS.add(new Check(what, content, true, true));
    }

    /** The checks recorded since the last call. */
    public static List<Check> takeChecks() {
        List<Check> taken = List.copyOf(CHECKS);
        CHECKS.clear();
        return taken;
    }

    public static List<String> takeSoftFailures() {
        List<String> taken = List.copyOf(SOFT_FAILURES);
        SOFT_FAILURES.clear();
        return taken;
    }
}
