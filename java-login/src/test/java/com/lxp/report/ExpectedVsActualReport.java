package com.lxp.report;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cucumber plugin that writes one HTML page listing every step of every
 * scenario, with the expected and actual values the steps recorded in
 * {@link CheckLog} and a PASS / FAIL / SKIPPED result for each.
 *
 * Registered in RunCucumberTest; Cucumber opens the output file for us.
 */
public class ExpectedVsActualReport implements ConcurrentEventListener {

    private record StepRow(String text, Status status, String error, List<CheckLog.Check> checks) {
    }

    private record ScenarioRow(String name, List<StepRow> steps, Status[] status) {
    }

    private final OutputStream out;
    private final List<ScenarioRow> scenarios = new ArrayList<>();
    private ScenarioRow current;

    public ExpectedVsActualReport(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, e -> {
            current = new ScenarioRow(e.getTestCase().getName(), new ArrayList<>(), new Status[] {Status.PASSED});
            scenarios.add(current);
            CheckLog.takeChecks();
        });
        publisher.registerHandlerFor(TestStepStarted.class, e -> CheckLog.takeChecks());
        publisher.registerHandlerFor(TestStepFinished.class, this::stepFinished);
        publisher.registerHandlerFor(TestCaseFinished.class,
                e -> current.status()[0] = e.getResult().getStatus());
        publisher.registerHandlerFor(TestRunFinished.class, e -> write());
    }

    private void stepFinished(TestStepFinished e) {
        Result result = e.getResult();
        String error = result.getError() == null ? "" : firstLines(result.getError().getMessage());

        if (e.getTestStep() instanceof PickleStepTestStep step) {
            current.steps().add(new StepRow(step.getStep().getKeyword() + step.getStep().getText(),
                    result.getStatus(), error, CheckLog.takeChecks()));
        } else if (e.getTestStep() instanceof HookTestStep && result.getStatus() == Status.FAILED) {
            // the end-of-scenario hook fails the run when a soft check failed
            current.steps().add(new StepRow("After the scenario", result.getStatus(), error, List.of()));
        }
    }

    private void write() {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Expected vs actual</title>
                <style>
                  body { font: 14px/1.45 -apple-system, Segoe UI, Roboto, sans-serif; margin: 24px; color: #1d1d1f; background: #fff; }
                  h1 { font-size: 20px; margin: 0 0 4px; }
                  h2 { font-size: 16px; margin: 28px 0 8px; }
                  .meta { color: #666; margin-bottom: 16px; }
                  .totals span { display: inline-block; margin-right: 16px; }
                  table { border-collapse: collapse; width: 100%; }
                  th, td { border: 1px solid #ddd; padding: 6px 8px; vertical-align: top; text-align: left; }
                  th { background: #f5f5f7; }
                  td.num { color: #888; width: 2em; }
                  .PASS, .PASSED { color: #0a7d32; font-weight: 600; }
                  .FAIL, .FAILED { color: #c62828; font-weight: 600; }
                  .REVIEW { color: #1f5fbf; font-weight: 600; }
                  .SKIPPED, .UNDEFINED, .PENDING, .AMBIGUOUS, .UNUSED { color: #8a6d00; font-weight: 600; }
                  .err { color: #c62828; white-space: pre-wrap; font-size: 12px; }
                  .muted { color: #999; }
                </style></head><body>
                """);
        html.append("<h1>Expected vs actual</h1>")
                .append("<div class=\"meta\">Generated ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("</div>");

        for (ScenarioRow scenario : scenarios) {
            writeScenario(html, scenario);
        }
        html.append("</body></html>\n");

        try (out) {
            out.write(html.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void writeScenario(StringBuilder html, ScenarioRow scenario) {
        long checks = scenario.steps().stream().flatMap(s -> s.checks().stream()).filter(c -> !c.review()).count();
        long checksPassed = scenario.steps().stream()
                .flatMap(s -> s.checks().stream()).filter(c -> !c.review() && c.passed()).count();
        long toReview = scenario.steps().stream().flatMap(s -> s.checks().stream()).filter(CheckLog.Check::review).count();
        long stepsPassed = scenario.steps().stream().filter(s -> s.status() == Status.PASSED).count();
        long stepsFailed = scenario.steps().stream().filter(s -> s.status() == Status.FAILED).count();
        long stepsSkipped = scenario.steps().stream().filter(s -> s.status() == Status.SKIPPED).count();

        html.append("<h2>").append(esc(scenario.name())).append(" &mdash; <span class=\"")
                .append(scenario.status()[0]).append("\">").append(scenario.status()[0]).append("</span></h2>")
                .append("<div class=\"totals\">")
                .append("<span>Steps: ").append(stepsPassed).append(" passed, ")
                .append(stepsFailed).append(" failed, ").append(stepsSkipped).append(" skipped</span>")
                .append("<span>Checks: ").append(checksPassed).append(" of ").append(checks).append(" matched</span>")
                .append(toReview == 0 ? "" : "<span>To review by hand: " + toReview + "</span>")
                .append("</div>")
                .append("<table><thead><tr><th>#</th><th>Step</th><th>Expected</th><th>Actual</th>")
                .append("<th>Result</th></tr></thead><tbody>");

        int n = 0;
        for (StepRow step : scenario.steps()) {
            n++;
            if (step.checks().isEmpty()) {
                html.append("<tr><td class=\"num\">").append(n).append("</td><td>").append(esc(step.text()))
                        .append(step.error().isEmpty() ? "" : "<div class=\"err\">" + esc(step.error()) + "</div>")
                        .append("</td><td class=\"muted\">&mdash;</td><td class=\"muted\">&mdash;</td>")
                        .append(resultCell(step.status().name()))
                        .append("</tr>");
                continue;
            }
            int rows = step.checks().size();
            for (int i = 0; i < rows; i++) {
                CheckLog.Check c = step.checks().get(i);
                html.append("<tr>");
                if (i == 0) {
                    html.append("<td class=\"num\" rowspan=\"").append(rows).append("\">").append(n)
                            .append("</td><td rowspan=\"").append(rows).append("\">").append(esc(step.text()))
                            .append(step.error().isEmpty() ? "" : "<div class=\"err\">" + esc(step.error()) + "</div>")
                            .append("</td>");
                }
                html.append("<td>").append(esc(c.expected())).append("</td><td>").append(esc(c.actual()))
                        .append("</td>").append(resultCell(c.review() ? "REVIEW" : c.passed() ? "PASS" : "FAIL")).append("</tr>");
            }
        }
        html.append("</tbody></table>");
    }

    private static String resultCell(String result) {
        return "<td class=\"" + result + "\">" + result + "</td>";
    }

    private static String firstLines(String message) {
        if (message == null) {
            return "";
        }
        String[] lines = message.strip().split("\n");
        return String.join("\n", java.util.Arrays.copyOf(lines, Math.min(lines.length, 4)));
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
