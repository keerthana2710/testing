package com.lxp.runner;

import io.cucumber.core.cli.Main;

import java.util.ArrayList;
import java.util.List;

/**
 * The entry point for the whole suite, and the single place the Cucumber
 * options live -- pom.xml launches this class rather than repeating the glue
 * and plugin flags, so the two cannot drift apart.
 *
 * Run it from the IDE with the Run/Debug codelens above main(), or from the
 * command line with `./run.sh` / `mvn test`.
 *
 * Filter by tag with -DTAGS (pom.xml forwards it to this JVM):
 *     ./run.sh -DTAGS=@coursesmoke
 *     mvn test -DTAGS=@smoke
 * Extra arguments are passed straight through too:
 *     java -cp <test-classpath> com.lxp.runner.RunCucumberTest --tags @smoke
 */
public final class RunCucumberTest {

    /** Every .feature on the classpath; nothing to register when you add one. */
    private static final String FEATURES = "classpath:features";

    private static final List<String> OPTIONS = List.of(
            "--glue", "com.lxp.steps",
            "--glue", "com.lxp.hooks",
            "--plugin", "pretty",
            "--plugin", "html:target/cucumber-reports/report.html",
            "--plugin", "json:target/cucumber-reports/report.json",
            "--plugin", "com.lxp.report.ExpectedVsActualReport:target/cucumber-reports/expected-vs-actual.html");

    private RunCucumberTest() {
    }

    public static void main(String[] args) {
        List<String> argv = new ArrayList<>(OPTIONS);
        String tags = System.getProperty("TAGS", "");
        if (!tags.isBlank()) {
            argv.addAll(List.of("--tags", tags));
        }
        // caller's options first: Cucumber wants the feature path last
        argv.addAll(List.of(args));
        argv.add(FEATURES);

        // Main.main exits with the scenario outcome, which is what fails `mvn test`
        Main.main(argv.toArray(new String[0]));
    }
}
