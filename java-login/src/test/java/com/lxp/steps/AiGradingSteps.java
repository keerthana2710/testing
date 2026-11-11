package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.AiGradingPage;
import com.lxp.pages.StudentViewPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI grading: the Assessments Setup switch, the student's assignment
 * submission, and Quick Review's AI suggestion, strictness re-run and
 * approval. Locators live in {@link AiGradingPage}.
 *
 * Steps the run can't get past fail hard (no submission, no suggestion);
 * what the teacher or student sees is checked softly, so one run reports
 * every mismatch (Hooks fails the scenario at the end).
 */
public class AiGradingSteps {

    private static final String STUDENT = "CI Test Student";

    /** Set while this run has AI Auto-Evaluation turned off, so a failed run turns it back on. */
    private static boolean turnedOff;

    private final WebDriver driver = DriverFactory.get();
    private final AiGradingPage page = new AiGradingPage(driver);
    private final StudentViewPage sv = new StudentViewPage(driver);

    /** The Quick Review page in use, to go back to after a browser crash. */
    private String quickReviewUrl;

    /** The last suggestion read: its card, score, feedback and "Last evaluated". */
    private String suggestion = "";
    private double[] suggestedScore;
    private String suggestedFeedback = "";
    private String suggestionStamp = "";
    /** The suggestion before a re-run, to compare with. */
    private double[] earlierScore;
    private String earlierFeedback = "";
    /** Set by a re-run: the next wait is for a suggestion newer than the one read before it. */
    private boolean awaitingRerun;
    /** The grade as approved, for the student's side. */
    private double[] approvedScore;
    private String approvedFeedback = "";
    private Map<String, String> approvedCriteria = Map.of();

    private void course() {
        if (sv.courseUrl() == null) {
            sv.rememberCourse();
            page.rememberCourse();
        }
    }

    // ---- Assessments Setup ----

    @When("I turn AI Auto-Evaluation {word} in Assessments Setup")
    public void i_turn_ai_auto_evaluation(String state) {
        course();
        recover("switching AI Auto-Evaluation " + state, () -> {
            turnAi(state);
            return null;
        });
    }

    private void turnAi(String state) {
        boolean on = state.equals("on");
        sv.openCoursePage();
        boolean was = page.openAssessmentsSetup();
        turnedOff = !on;
        boolean saved = page.setAiAutoEvaluation(on);
        // the save can be lost if the page is left too soon: read it back after a reload, and save once more if it did not stick
        boolean stuck = savedState() == on;
        if (!stuck) {
            System.out.println("[ai grading] AI Auto-Evaluation did not stay " + state + " after Save; saving again");
            page.openAssessmentsSetup();
            saved = page.setAiAutoEvaluation(on);
            stuck = savedState() == on;
        }
        CheckLog.check("AI Auto-Evaluation is switched " + state, "was " + (was ? "on" : "off") + ", now " + state
                + (saved ? " (\"Assignment settings saved\")" : " (no saved message)") + (stuck ? "" : ", but it did not stay " + state), stuck);
    }

    /** Reloads the course page and reads the switch, closing the window without saving. */
    private boolean savedState() {
        sv.openCoursePage();
        boolean on = page.openAssessmentsSetup();
        page.closeAssessmentsSetup();
        return on;
    }

    @Then("AI Auto-Evaluation is {word}")
    public void ai_auto_evaluation_is(String state) {
        boolean on = recover("reading AI Auto-Evaluation", this::savedState);
        boolean ok = on == state.equals("on");
        CheckLog.check("Assessments Setup shows AI Auto-Evaluation " + state + " after reopening", on ? "on" : "off", ok);
        assertThat(ok).withFailMessage("AI Auto-Evaluation is not " + state).isTrue();
    }

    /** A failed run must not leave the course with AI grading off. */
    @After(order = 15000)
    public void turnAiBackOnIfLeftOff() {
        if (!turnedOff || !DriverFactory.isRunning()) {
            return;
        }
        try {
            if (!driver.findElements(By.xpath("//button[normalize-space(.)='Exit Student View']")).isEmpty()) {
                sv.exit();
            }
            sv.openCoursePage();
            page.openAssessmentsSetup();
            page.setAiAutoEvaluation(true);
            turnedOff = false;
            System.out.println("[ai grading] AI Auto-Evaluation turned back on after the run");
        } catch (RuntimeException e) {
            System.out.println("[ai grading] could not turn AI Auto-Evaluation back on: turn it on by hand in Assessments Setup (" + e.getMessage() + ")");
        }
    }

    // ---- the student's assignment ----

    @When("I submit the assignment with the response {string}")
    public void i_submit_the_assignment(String response) {
        page.submitAssignment(RunClock.withNow(response));
    }

    @Then("the assignment is submitted")
    public void the_assignment_is_submitted() {
        String result = page.submissionResult();
        boolean submitted = !result.isEmpty() && !result.startsWith("error:");
        String detail = submitted ? result + " (opened with \"" + page.startedWith() + "\")"
                : (result.isEmpty() ? "no submitted message or status" : result) + " (opened with \"" + page.startedWith() + "\"); " + page.describePage();
        if (!submitted) {
            screenshot("AI_assignment_not_submitted_" + RunClock.NOW);
        }
        CheckLog.check("the assignment is submitted", detail, submitted);
        assertThat(submitted).withFailMessage("The assignment was not submitted: " + detail).isTrue();
    }

    // ---- Quick Review ----

    @When("I open Quick Review of the {word} {string} in module {int}")
    public void i_open_quick_review(String type, String title, int module) {
        course();
        String shown = recover("opening Quick Review", () -> {
            sv.openItem(String.valueOf(module), RunClock.withNow(title));
            page.openQuickReviewOfThisItem(type);
            quickReviewUrl = driver.getCurrentUrl();
            return page.showStudent(STUDENT);
        });
        CheckLog.check("Quick Review shows " + STUDENT, shown, shown.equals(STUDENT));
        assertThat(shown).isEqualTo(STUDENT);
    }

    @Then("Quick Review shows the work {string}")
    public void quick_review_shows_the_work(String text) {
        String work = RunClock.withNow(text);
        boolean shown = page.workShows(work);
        CheckLog.check("Quick Review shows \"" + work + "\"", shown ? "shown" : "not shown", shown);
        assertThat(shown).withFailMessage("Quick Review does not show " + work).isTrue();
    }

    @When("I run the AI evaluation")
    public void i_run_the_ai_evaluation() {
        page.runEvaluation();
    }

    @When("I wait up to {int} minute(s) for the AI suggestion")
    public void i_wait_for_the_ai_suggestion(int minutes) {
        String stamp = awaitingRerun ? suggestionStamp : null;
        boolean shown = onQuickReview("waiting for the AI suggestion", () -> page.waitForSuggestion(Duration.ofMinutes(minutes), stamp));
        awaitingRerun = false;
        readSuggestion();
        CheckLog.check("an AI suggestion arrives within " + minutes + " min", shown ? suggestion : "none: " + page.aiEvaluationBox(), shown);
        assertThat(shown).withFailMessage("No AI suggestion within " + minutes + " min: " + page.aiEvaluationBox()).isTrue();
    }

    private void readSuggestion() {
        suggestion = page.suggestedCard();
        suggestedScore = AiGradingPage.score(suggestion);
        if (suggestedScore == null) {
            suggestedScore = page.currentAttemptScore();
        }
        suggestedFeedback = page.feedback();
        suggestionStamp = page.lastEvaluated();
    }

    @Then("an AI suggested score and feedback are shown")
    public void an_ai_suggested_score_and_feedback_are_shown() {
        CheckLog.soft("an AI SUGGESTED score is shown", suggestion.isEmpty() ? "no AI SUGGESTED card" : suggestion, suggestedScore != null);
        CheckLog.soft("the AI's feedback is filled in", suggestedFeedback.isEmpty() ? "empty" : suggestedFeedback, !suggestedFeedback.isEmpty());
        String grade = page.gradeCard();
        boolean waiting = grade.contains("Not recorded yet") || suggestion.contains("Waiting on you");
        CheckLog.soft("the suggestion waits for the teacher (not recorded yet)", grade + " | " + suggestion, waiting);
        CheckLog.review("AI feedback", suggestedFeedback);
    }

    @Then("the rubric breakdown has a suggested score for every criterion")
    public void the_rubric_breakdown_has_a_suggested_score_for_every_criterion() {
        Map<String, String> criteria = page.criteria();
        boolean all = !criteria.isEmpty() && criteria.values().stream().noneMatch(String::isEmpty);
        CheckLog.soft("every rubric criterion has a SUGGESTED score", criteria.isEmpty() ? "no rubric breakdown" : criteria.toString(), all);
        if (suggestedScore != null && all) {
            double sum = criteria.values().stream().mapToDouble(v -> AiGradingPage.score(v)[0]).sum();
            CheckLog.soft("the criteria add up to the AI SUGGESTED score", sum + " vs " + suggestedScore[0], Math.abs(sum - suggestedScore[0]) < 0.01);
        }
    }

    @Then("no AI suggestion appears within {int} minute(s)")
    public void no_ai_suggestion_appears(int minutes) {
        boolean none = onQuickReview("watching for an AI suggestion", () -> page.noSuggestionFor(Duration.ofMinutes(minutes)));
        CheckLog.soft("no AI evaluation runs or is suggested in " + minutes + " min",
                none ? "none" : (page.evaluating() ? "an evaluation is queued/running: " + page.currentAttemptAi() : page.suggestedCard() + " " + page.currentAttemptAi()), none);
    }

    @Then("the grading form is blank")
    public void the_grading_form_is_blank() {
        String box = page.aiEvaluationBox();
        String current = page.currentAttemptAi();
        // older attempts keep their own AI grades (and an earlier recorded grade stays): only this attempt counts
        boolean noSuggestion = current.isEmpty()
                ? page.suggestedCard().isEmpty() && page.criteria().isEmpty()
                : page.currentAttemptScore() == null;
        CheckLog.soft("this attempt has no AI suggested grade", current.isEmpty()
                ? (noSuggestion ? "none" : page.suggestedCard()) : "current attempt: " + current, noSuggestion);
        boolean boxBlank = box.isEmpty() || (box.contains("Run AI Evaluation") && !page.evaluating());
        CheckLog.soft("no AI evaluation running for it (box left out, or only offers to run it)", box.isEmpty() ? "not shown" : box, boxBlank);
        CheckLog.review("grade card with AI off (may hold a grade approved for an earlier attempt)", page.gradeCard());
    }

    // ---- re-evaluation at another strictness ----

    @When("I re-run the AI evaluation as {string}")
    public void i_rerun_the_ai_evaluation_as(String level) {
        earlierScore = suggestedScore;
        earlierFeedback = suggestedFeedback;
        CheckLog.review("AI suggestion at " + page.strictness(), suggestion + " | " + suggestedFeedback);
        page.chooseStrictness(level);
        awaitingRerun = true;
        page.runEvaluation();
    }

    @Then("a new AI suggestion is shown at {string} strictness")
    public void a_new_ai_suggestion_is_shown_at(String level) {
        String shown = page.strictness();
        CheckLog.soft("Strictness shows " + level, shown, shown.equals(level));
        CheckLog.soft("the new suggestion has a score", suggestion, suggestedScore != null);
        CheckLog.soft("the new suggestion has feedback", suggestedFeedback.isEmpty() ? "empty" : suggestedFeedback, !suggestedFeedback.isEmpty());
        boolean changed = earlierScore == null || suggestedScore == null
                || suggestedScore[0] != earlierScore[0] || !suggestedFeedback.equals(earlierFeedback);
        CheckLog.soft("the score or feedback was updated", "before: " + fmt(earlierScore) + " \"" + earlierFeedback + "\"; after: "
                + fmt(suggestedScore) + " \"" + suggestedFeedback + "\"", changed);
        CheckLog.review("AI suggestion at " + level, suggestion + " | " + suggestedFeedback);
    }

    @Then("the {string} score is not higher than the earlier one")
    public void the_score_is_not_higher_than_the_earlier_one(String level) {
        boolean ok = earlierScore != null && suggestedScore != null && suggestedScore[0] <= earlierScore[0];
        CheckLog.soft(level + " gives no more than the earlier suggestion", fmt(earlierScore) + " → " + fmt(suggestedScore), ok);
    }

    // ---- approval ----

    /**
     * A new attempt is not accepted while the student's last one is still
     * Awaiting Review (Submit for Grading then does nothing). So before the
     * student submits, the teacher approves any attempt that is waiting: its
     * AI suggestion if there is one, else a grade of 0. LXP only, not Canvas.
     */
    @When("the teacher clears any attempt awaiting review on the {word} {string} in module {int}")
    public void the_teacher_clears_any_attempt_awaiting_review(String type, String title, int module) {
        i_open_quick_review(type, title, module);
        if (!page.submissionAwaitingReview()) {
            CheckLog.check("no earlier attempt is awaiting review", "none waiting: " + page.gradeCard(), true);
            return;
        }
        String grade = page.fillGradeIfEmpty("0");
        boolean approved = page.approve();
        CheckLog.check("the earlier attempt awaiting review is approved first (grade " + grade + ")",
                approved ? page.gradeCard() : "not approved: " + page.gradeCard(), approved);
        assertThat(approved).withFailMessage("Could not approve the earlier attempt: " + page.gradeCard()).isTrue();
    }

    @When("I approve the grade")
    public void i_approve_the_grade() {
        approvedScore = suggestedScore;
        approvedFeedback = page.feedback();
        approvedCriteria = page.criteria();
        boolean approved = page.approve();
        CheckLog.check("Approve records the grade", approved ? page.gradeCard() : "still not recorded: " + page.gradeCard(), approved);
        assertThat(approved).withFailMessage("The grade was not approved: " + page.gradeCard()).isTrue();
    }

    @Then("the approved grade is recorded")
    public void the_approved_grade_is_recorded() {
        String grade = page.gradeCard();
        double[] recorded = AiGradingPage.score(grade);
        boolean ok = page.hasRecordedGrade();
        CheckLog.soft("the grade card shows a RECORDED GRADE with a score", grade, ok);
        CheckLog.soft("the recorded grade is the approved suggestion", fmt(recorded) + " vs " + fmt(approvedScore),
                recorded != null && approvedScore != null && recorded[0] == approvedScore[0]);
    }

    @Then("the discussion's Scores list the approved grade for {string}")
    public void the_discussions_scores_list_the_approved_grade(String student) {
        String row = page.scoresRow(student);
        String expected = approvedScore == null ? "" : fmt(approvedScore).replace(" ", "");
        boolean ok = !row.contains("Not graded") && row.replace(" ", "").contains(expected);
        CheckLog.soft("Scores shows " + student + " at " + expected, row, ok);
    }

    // ---- what the student sees ----

    @Then("the student sees the approved score, percentage, feedback and criteria")
    public void the_student_sees_the_approved_grade() {
        String score = approvedScore == null ? "" : num(approvedScore[0]);
        String text = page.studentGradeText(score);
        boolean scoreShown = approvedScore != null
                && text.replace(" ", "").contains(num(approvedScore[0]) + "/" + num(approvedScore[1]));
        String pct = approvedScore == null ? "" : num(Math.round(approvedScore[0] / approvedScore[1] * 10000) / 100.0);
        boolean pctShown = approvedScore != null && (text.contains(pct + "%") || text.contains(Math.round(approvedScore[0] / approvedScore[1] * 100) + "%"));
        String start = approvedFeedback.length() > 40 ? approvedFeedback.substring(0, 40) : approvedFeedback;
        boolean feedbackShown = !start.isEmpty() && text.contains(start);
        long criteriaShown = approvedCriteria.keySet().stream().filter(text::contains).count();

        CheckLog.soft("the student sees the score " + score + " / " + (approvedScore == null ? "?" : num(approvedScore[1])), scoreShown ? "shown" : text, scoreShown);
        CheckLog.soft("the student sees the percentage " + pct + "%", pctShown ? "shown" : text, pctShown);
        CheckLog.soft("the student sees the approved feedback", feedbackShown ? "shown" : "not found: \"" + start + "\"", feedbackShown);
        CheckLog.soft("the student sees the criteria breakdown (" + approvedCriteria.size() + " criteria)",
                criteriaShown + " of " + approvedCriteria.size() + " criteria shown", !approvedCriteria.isEmpty() && criteriaShown == approvedCriteria.size());
        CheckLog.review("the student's graded assignment", text.length() > 1500 ? text.substring(0, 1500) + "…" : text);
    }

    // ---- quiz ----

    @Then("the {string} answer {string} is scored automatically")
    public void the_answer_is_scored_automatically(String type, String question) {
        String block = page.quizQuestion(question);
        boolean auto = block.contains("Auto-graded") && AiGradingPage.score(block) != null;
        CheckLog.soft(type + " \"" + question + "\" is auto-graded", block.isEmpty() ? "question not found" : block, auto);
    }

    @Then("the essay {string} has an AI suggested score waiting for the teacher's approval")
    public void the_essay_has_an_ai_suggested_score(String question) {
        String block = page.quizQuestion(question);
        boolean scored = AiGradingPage.score(block) != null && !block.contains("Auto-graded");
        String grade = page.gradeCard();
        boolean waiting = suggestion.contains("Waiting on you") || grade.contains("Not recorded yet");
        CheckLog.soft("the essay has a score from the AI (not auto-graded)", block.isEmpty() ? "question not found" : block, scored);
        CheckLog.soft("an AI SUGGESTED score is shown", suggestion.isEmpty() ? "none" : suggestion, suggestedScore != null);
        CheckLog.soft("it waits for the teacher's approval", grade + " | " + suggestion, waiting);
    }

    // ---- browser crashes ----

    /**
     * Chrome has been seen to close mid-run on staging ("not connected to
     * DevTools"). Teacher-side steps save the signed-in session first; if the
     * browser goes, a new one is started with that session (no new OTP) and
     * the step is tried once more.
     */
    private <T> T recover(String what, Supplier<T> action) {
        try {
            if (driver.getCurrentUrl().startsWith("http")
                    && driver.findElements(By.xpath("//button[normalize-space(.)='Exit Student View']")).isEmpty()) {
                DriverFactory.saveSession();
            }
        } catch (WebDriverException ignored) {
            // nothing to save on a blank page
        }
        try {
            return action.get();
        } catch (WebDriverException e) {
            if (!DriverFactory.isBrowserGone(e)) {
                throw e;
            }
            System.out.println("[ai grading] the browser closed while " + what + "; starting a new one and trying again");
            DriverFactory.restartKeepingSession();
            CheckLog.review("browser restarted", "Chrome closed while " + what + "; the step was run again in a new browser");
            return action.get();
        }
    }

    /** As recover, for a step on Quick Review: after a crash it goes back to the same Quick Review page. */
    private <T> T onQuickReview(String what, Supplier<T> action) {
        boolean[] restarted = {false};
        return recover(what, () -> {
            if (restarted[0] && quickReviewUrl != null) {
                page.reopen(quickReviewUrl);
            }
            restarted[0] = true;
            return action.get();
        });
    }

    /** Saves a screenshot of this moment to target/screenshots (the end-of-run one shows a later page). */
    private void screenshot(String name) {
        try {
            byte[] png = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
            java.nio.file.Files.createDirectories(com.lxp.config.Settings.SCREENSHOT_DIR);
            java.nio.file.Path path = com.lxp.config.Settings.SCREENSHOT_DIR.resolve(name + ".png");
            java.nio.file.Files.write(path, png);
            System.out.println("[screenshot] " + path.toAbsolutePath());
        } catch (Exception ignored) {
            // never hide the real failure
        }
    }

    private static String fmt(double[] s) {
        return s == null ? "none" : num(s[0]) + " / " + num(s[1]);
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
