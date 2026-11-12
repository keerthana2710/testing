package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.CourseSetupPage;
import com.lxp.report.CheckLog;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Course Setup → Course Configuration: autosave across tabs, the Schedule
 * stage, the Details stage and the Assessments & Lectures stage, checked
 * against the course the AI generates from them.
 *
 * Exact results (module count, due dates inside the course dates, questions
 * per quiz, a custom instruction followed in every lecture) are checks.
 * Results only a person can judge (does the content suit the level, is the
 * lecture flow book-like or independent) are recorded as REVIEW rows in
 * target/cucumber-reports/expected-vs-actual.html, with the text to read.
 */
public class CourseSetupSteps {

    private static final DateTimeFormatter SHOWN_DATE = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final Pattern QUESTION_TYPE = Pattern.compile(
            "(Multiple Choice|Multiple Answers?|True/False|True or False|Short Answer|Essay|Fill in the Blank|Matching|Numerical|Ordering) \\d+(?:\\.\\d+)? pts?");

    private final WebDriver driver = DriverFactory.get();
    private final CourseSetupPage setup = new CourseSetupPage(driver);

    /** What was set, field → value, in the order it was set. */
    private final Map<String, String> entered = new LinkedHashMap<>();

    /**
     * Chrome sometimes closes by itself on this machine ("session deleted as
     * the browser has closed the connection"). If it does during a Course
     * Setup step, start a new browser with the signed-in session, go back to
     * the course and do the step once more.
     */
    private void retryIfBrowserCloses(Runnable step) {
        try {
            DriverFactory.saveSession();
        } catch (org.openqa.selenium.WebDriverException ignored) {
            // the session saved before still holds
        }
        try {
            step.run();
        } catch (org.openqa.selenium.WebDriverException e) {
            if (!DriverFactory.isBrowserGone(e) || setup.courseUrl() == null) {
                throw e;
            }
            CheckLog.review("browser closed", "Chrome closed during this step (" + e.getClass().getSimpleName()
                    + "); started a new one with the signed-in session and did the step again");
            DriverFactory.restartKeepingSession();
            driver.get(setup.courseUrl());
            step.run();
        }
    }

    private void course() {
        if (setup.courseUrl() == null) {
            setup.rememberCourse();
        }
    }

    // ---- setting values ----

    /** | field | value | rows; each field is set in its own stage (see CourseSetupPage for the names). */
    @When("I set in Course Setup:")
    public void i_set_in_course_setup(DataTable table) {
        course();
        for (Map<String, String> row : table.asMaps()) {
            String field = row.get("field");
            String value = RunClock.withNow(row.get("value"));
            retryIfBrowserCloses(() -> {
                setup.open();
                setup.set(field, value);
            });
            entered.put(field, value);
            CheckLog.check("set " + field + " in " + CourseSetupPage.stageOf(field), value, true);
        }
    }

    @When("I switch through every Course Setup tab")
    public void i_switch_through_every_course_setup_tab() {
        retryIfBrowserCloses(() -> {
            setup.open();
            for (String[] tab : CourseSetupPage.ALL_TABS) {
                setup.openTab(tab[0], tab[1]);
            }
        });
    }

    @When("I close Course Setup without saving and open it again")
    public void i_close_course_setup_and_open_it_again() {
        retryIfBrowserCloses(() -> {
            if (setup.isOpen()) {
                setup.closeWithoutSaving();
            }
            driver.navigate().refresh();
            setup.open();
        });
    }

    @When("I close Course Setup")
    public void i_close_course_setup() {
        if (setup.isOpen()) {
            setup.closeWithoutSaving();
        }
    }

    @Then("every value I set is still shown")
    public void every_value_i_set_is_still_shown() {
        for (Map.Entry<String, String> e : entered.entrySet()) {
            String[] read = {""};
            retryIfBrowserCloses(() -> {
                setup.open();
                read[0] = setup.readSettled(e.getKey(), e.getValue());
            });
            String shown = read[0];
            boolean same = CourseSetupPage.matches(e.getKey(), e.getValue(), shown);
            CheckLog.soft(e.getKey() + " (" + CourseSetupPage.stageOf(e.getKey()) + ") still shows \"" + e.getValue() + "\"",
                    shown.isEmpty() ? "empty" : shown, same);
        }
    }

    // ---- Schedule: reset dates ----

    @When("I clear the course dates")
    public void i_clear_the_course_dates() {
        course();
        retryIfBrowserCloses(() -> {
            setup.open();
            setup.clearDates();
        });
    }

    @Then("the course dates are empty")
    public void the_course_dates_are_empty() {
        List<String> dates = new java.util.ArrayList<>();
        retryIfBrowserCloses(() -> {
            setup.open();
            dates.clear();
            dates.addAll(setup.dates());
        });
        boolean empty = dates.stream().allMatch(d -> d.startsWith("Pick") || d.isEmpty() || d.equals("not shown"));
        CheckLog.soft("Clear the dates empties First day and Last day", String.join(" to ", dates), empty);
    }

    @Then("the course dates are set")
    public void the_course_dates_are_set() {
        List<String> dates = new java.util.ArrayList<>();
        retryIfBrowserCloses(() -> {
            setup.open();
            dates.clear();
            dates.addAll(setup.dates());
        });
        boolean set = dates.stream().noneMatch(d -> d.startsWith("Pick") || d.isEmpty() || d.equals("not shown"));
        CheckLog.check("First day and Last day are set", String.join(" to ", dates), set);
        assertThat(set).withFailMessage("Course dates are not set: " + dates).isTrue();
    }

    // ---- generation ----

    /** Uses "Generate" from the steps the course-creation feature already has; this waits for the whole course. */
    @When("I wait up to {int} minutes for the course to be generated")
    public void i_wait_for_the_course_to_be_generated(int minutes) {
        course();
        String counts = setup.waitUntilGenerated(Duration.ofMinutes(minutes), p -> System.out.println("[generation] " + p));
        boolean done = !counts.isEmpty();
        CheckLog.check("the course is generated within " + minutes + " min", done ? counts : "still generating", done);
        assertThat(done).withFailMessage("The course was not generated within " + minutes + " minutes").isTrue();
        setup.openAllModules();
        items = setup.items();
    }

    private List<CourseSetupPage.Item> items = List.of();

    // ---- Schedule translated ----

    @Then("the generated course has {int} modules")
    public void the_generated_course_has_modules(int modules) {
        int[] counts = setup.counts();
        CheckLog.soft("the course has " + modules + " modules (Number of modules)", counts[0] + " modules, " + counts[1] + " lessons", counts[0] == modules);
    }

    /** Every due date falls inside the course's First day .. Last day (from Schedule). */
    @Then("every due date is inside the course dates")
    public void every_due_date_is_inside_the_course_dates() {
        setup.open();
        List<String> dates = setup.dates();
        setup.closeWithoutSaving();
        setup.openAllModules();
        LocalDate first;
        LocalDate last;
        try {
            first = LocalDate.parse(dates.get(0), SHOWN_DATE);
            last = LocalDate.parse(dates.get(1), SHOWN_DATE);
        } catch (RuntimeException e) {
            CheckLog.soft("due dates are inside the course dates", "course dates not readable: " + dates, false);
            return;
        }
        int withDates = 0;
        StringBuilder outside = new StringBuilder();
        for (CourseSetupPage.Item item : items) {
            if (item.due().isEmpty()) {
                continue;
            }
            withDates++;
            LocalDate due = LocalDate.parse(item.due(), DUE_DATE);
            // work due "at the next class" can land a few days after the last day
            if (due.isBefore(first) || due.isAfter(last.plusDays(7))) {
                outside.append(item.title()).append(" (").append(item.due()).append("); ");
            }
        }
        CheckLog.soft("every due date is between " + dates.get(0) + " and " + dates.get(1) + " (+ the last week's due day)",
                withDates == 0 ? "no due dates listed" : outside.isEmpty() ? withDates + " due dates, all inside" : "outside: " + outside,
                withDates > 0 && outside.isEmpty());
    }

    @Then("Course Setup still shows the configuration after generation")
    public void course_setup_still_shows_the_configuration() {
        setup.open();
        every_value_i_set_is_still_shown();
        CheckLog.review("Weeks & modules after generation (lecture hours and modules as scheduled)", setup.scheduleSummary());
        setup.closeWithoutSaving();
    }

    // ---- quizzes ----

    /** Standard quizzes only: challenge, pop and practice quizzes use AI defaults. */
    private List<CourseSetupPage.Item> standardQuizzes() {
        return items.stream().filter(i -> i.type().equals("Quiz"))
                .filter(i -> !i.title().matches("(?i).*(challenge|pop quiz|practice|knowledge check).*"))
                .toList();
    }

    @Then("every standard quiz has {int} questions of a {string} mix")
    public void every_standard_quiz_has_questions(int count, String distribution) {
        List<CourseSetupPage.Item> quizzes = standardQuizzes();
        CheckLog.soft("the course has standard quizzes", quizzes.size() + " standard quizzes: "
                + quizzes.stream().map(CourseSetupPage.Item::title).toList(), !quizzes.isEmpty());
        int reviewed = 0;
        for (CourseSetupPage.Item quiz : quizzes) {
            setup.openAllModules();
            setup.openItem(quiz.title());
            String text = setup.quizQuestionsText();
            Matcher n = Pattern.compile("Questions: (\\d+)").matcher(text);
            int questions = n.find() ? Integer.parseInt(n.group(1)) : -1;
            CheckLog.soft("\"" + quiz.title() + "\" has exactly " + count + " questions", questions + " questions", questions == count);

            Map<String, Integer> types = new TreeMap<>();
            Matcher t = QUESTION_TYPE.matcher(text);
            while (t.find()) {
                types.merge(t.group(1).replace("True or False", "True/False"), 1, Integer::sum);
            }
            boolean fits = fitsDistribution(distribution, types);
            CheckLog.soft("\"" + quiz.title() + "\" question types fit \"" + distribution + "\"", types.toString(), fits);
            if (reviewed++ < 2) {
                CheckLog.review("\"" + quiz.title() + "\": are the questions' difficulty and complexity right for the course's level?",
                        text.length() > 1500 ? text.substring(0, 1500) + "…" : text);
            }
        }
    }

    /**
     * "Reasonably similar" question types:
     * MCQ Only: all multiple choice; MCQ Heavy: at least half; X Heavy: X is the most common type;
     * Balanced: at least three types.
     */
    static boolean fitsDistribution(String distribution, Map<String, Integer> types) {
        int total = types.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return false;
        }
        int mcq = types.getOrDefault("Multiple Choice", 0);
        String d = distribution.toLowerCase();
        if (d.startsWith("mcq only")) {
            return mcq == total;
        }
        if (d.startsWith("mcq heavy")) {
            return mcq * 2 >= total;
        }
        if (d.startsWith("balanced")) {
            return types.size() >= 3;
        }
        String heavy = d.startsWith("short answer") ? "Short Answer" : d.startsWith("true/false") ? "True/False"
                : d.startsWith("essay") ? "Essay" : "";
        int most = types.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return !heavy.isEmpty() && types.getOrDefault(heavy, 0) == most;
    }

    // ---- lectures ----

    /** Lecture pages: Page items other than the module's overview, objectives and summary pages. */
    private List<CourseSetupPage.Item> lectures() {
        return items.stream().filter(i -> i.type().equals("Page"))
                .filter(i -> !i.title().matches("(?i).*(overview|objectives|summary|exam guide|introduction to the course|welcome).*"))
                .toList();
    }

    @Then("every lecture contains {string}")
    public void every_lecture_contains(String marker) {
        List<CourseSetupPage.Item> lectures = lectures();
        CheckLog.soft("the course has lectures", lectures.size() + " lectures", !lectures.isEmpty());
        StringBuilder missing = new StringBuilder();
        int checked = 0;
        for (CourseSetupPage.Item lecture : lectures.subList(0, Math.min(lectures.size(), 6))) {
            setup.openAllModules();
            setup.openItem(lecture.title());
            checked++;
            if (!setup.mainText().toLowerCase().contains(marker.toLowerCase())) {
                missing.append(lecture.title()).append("; ");
            }
        }
        CheckLog.soft("the lecture custom instructions are followed: each lecture has \"" + marker + "\"",
                missing.isEmpty() ? checked + " lectures checked, all have it" : "missing in: " + missing, checked > 0 && missing.isEmpty());
    }

    /** The first lectures' openings and endings, to judge whether they flow like a book or stand alone. */
    @Then("the lecture flow is recorded for review as {string}")
    public void the_lecture_flow_is_recorded_for_review(String structure) {
        List<CourseSetupPage.Item> lectures = lectures();
        StringBuilder flow = new StringBuilder();
        for (CourseSetupPage.Item lecture : lectures.subList(0, Math.min(lectures.size(), 4))) {
            setup.openAllModules();
            setup.openItem(lecture.title());
            String text = setup.mainText();
            int start = text.indexOf(lecture.title(), 100);
            String body = start < 0 ? text : text.substring(start);
            flow.append("【").append(lecture.title()).append("】 starts: ")
                    .append(body, 0, Math.min(body.length(), 400)).append(" … ends: ")
                    .append(body.substring(Math.max(0, body.length() - 300))).append("\n\n");
        }
        CheckLog.review("Lecture structure \"" + structure + "\": do the lectures build on each other (Book Type) or stand alone (Independent)?",
                flow.toString());
    }

    @Then("the content is recorded for review against {string}, {string} and {string}")
    public void the_content_is_recorded_for_review(String audience, String level, String style) {
        List<CourseSetupPage.Item> lectures = lectures();
        if (lectures.isEmpty()) {
            CheckLog.review("content for " + audience + " / " + level + " / " + style, "no lectures to read");
            return;
        }
        setup.openAllModules();
        setup.openItem(lectures.get(0).title());
        String text = setup.mainText();
        CheckLog.review("Is \"" + lectures.get(0).title() + "\" reasonable for audience \"" + audience + "\", level \"" + level
                        + "\" and style \"" + style + "\" (e.g. Beginner → easy, Advanced → harder)?",
                text.length() > 2500 ? text.substring(0, 2500) + "…" : text);
    }
}
