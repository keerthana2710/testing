package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.GradesPage;
import com.lxp.pages.GradesPage.GradeRow;
import com.lxp.pages.GradesPage.Point;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Course Grades: a student's side bar, grades page and Course Analytics, read
 * from the teacher's gradebook. Locators live in {@link GradesPage}.
 *
 * Navigation steps fail hard (the rest can't run without them); the checks
 * of what is shown are soft, so one scenario reports every mismatch.
 */
public class GradesSteps {

    private final GradesPage page = new GradesPage(DriverFactory.get());

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("MMM d h:mm a yyyy", Locale.ENGLISH);
    /** The course's time zone, as Learn Python shows it ("Mountain Time (Denver)"). */
    private static final ZoneId COURSE_ZONE = ZoneId.of("America/Denver");
    private static final Pattern SCORE = Pattern.compile("Score:\\s*([\\d.]+)\\s*/\\s*([\\d.]+)\\s*\\(([\\d.]+)%\\)");
    private static final Pattern LAST_NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*$");

    private String student;
    private Map<String, Object> sideBar = Map.of();
    private List<GradeRow> rows = List.of();
    private Map<String, Object> card = Map.of();

    /** This week's numbers noted at the start: pageViews, participations, student, instructor. */
    private final Map<String, Double> before = new LinkedHashMap<>();
    private final Map<String, Double> after = new LinkedHashMap<>();
    private Map<String, String> pageViews = Map.of();
    private Map<String, String> participations = Map.of();
    private Map<String, String> messages = Map.of();

    // ---- baseline ----

    @When("I note the Course Analytics of {string}")
    public void i_note_the_course_analytics_of(String name) {
        student = name;
        page.openGradebookGrid();
        page.openStudentSideBar(name);
        page.openStudentGrades(name);
        page.openCourseAnalytics();
        page.openAnalyticsTab("Online Activity");
        readOnlineActivity(before);
        page.openAnalyticsTab("Communication");
        readCommunication(before);
        CheckLog.check("noted this week's activity of " + name + " before the run", before.toString(), true);
    }

    // ---- the instructor's reply ----

    private String reply;

    @When("I reply {string} to the post {string}")
    public void i_reply_to_the_post(String text, String post) {
        reply = RunClock.withNow(text);
        page.replyToPost(RunClock.withNow(post), reply);
    }

    @Then("the reply {string} is shown as from {string}")
    public void the_reply_is_shown_as_from(String text, String author) {
        String shown = page.postWith(RunClock.withNow(text));
        boolean ok = shown.contains(author);
        CheckLog.soft("the reply is posted as from " + author, shown.isEmpty() ? "reply not shown" : shown, ok);
    }

    // ---- TC1: the side bar ----

    @When("I open the teacher's Grades of the course")
    public void i_open_the_teachers_grades() {
        page.openGradebookGrid();
        boolean shown = page.gradebookShown();
        CheckLog.check("the gradebook opens in Grid", shown ? "shown at " + page.path() : "not shown", shown);
        assertThat(shown).withFailMessage("The gradebook's Grid did not open").isTrue();
    }

    @When("I click the student {string} in the gradebook")
    public void i_click_the_student(String name) {
        student = name;
        gridScore = page.gridCalculatedScore(name);
        page.openStudentSideBar(name);
    }

    private String gridScore = "";

    @Then("the student side bar opens for {string}")
    public void the_student_side_bar_opens_for(String name) {
        boolean shown = page.sideBarShown(name);
        CheckLog.check("the side bar opens for " + name, shown ? page.sideBarText(name) : "no side bar", shown);
        assertThat(shown).withFailMessage("No side bar for " + name).isTrue();
        sideBar = page.sideBar(name);
    }

    @Then("the side bar shows the student's missing and late work and their recent grades")
    public void the_side_bar_shows_missing_late_and_recent() {
        boolean missing = sideBar.get("missing") instanceof Integer;
        boolean late = sideBar.get("late") instanceof Integer;
        Map<?, ?> recent = (Map<?, ?>) sideBar.get("recent");
        CheckLog.soft("the side bar shows the Missing count", missing ? "Missing " + sideBar.get("missing") : "not shown", missing);
        CheckLog.soft("the side bar shows the Late count", late ? "Late " + sideBar.get("late") : "not shown", late);
        CheckLog.soft("the side bar lists Recent Grades (graded work with its score)",
                recent.isEmpty() ? "none listed" : recent.toString(), !recent.isEmpty());
    }

    @Then("the side bar shows the student's grade as a percentage")
    public void the_side_bar_shows_the_grade_as_a_percentage() {
        Object grade = sideBar.get("grade");
        boolean ok = grade != null && pct(grade) >= 0 && pct(grade) <= 100;
        CheckLog.soft("the side bar shows the grade as a %", ok ? grade + "%" : "no %", ok);
    }

    // ---- TC2: the grades page ----

    @When("I click Grades in the side bar")
    public void i_click_grades_in_the_side_bar() {
        page.openStudentGrades(student);
        rows = page.gradeRows();
        card = page.totalGradeCard();
    }

    @Then("the grades page opens for {string}")
    public void the_grades_page_opens_for(String name) {
        String crumb = page.breadcrumbStudent("Grades");
        boolean ok = page.studentGradesShown() && crumb.equals(name);
        CheckLog.check("the grades page is " + name + "'s", "\"Assessment Grades\", breadcrumb GRADES / " + crumb, ok);
        assertThat(ok).withFailMessage("Not " + name + "'s grades page: " + crumb).isTrue();
    }

    @Then("the grades table lists every assessment by its correct name")
    public void the_grades_table_lists_every_assessment() {
        boolean listed = !rows.isEmpty();
        CheckLog.check("the grades table lists the assessments", rows.size() + " rows", listed);
        assertThat(listed).withFailMessage("The grades table is empty").isTrue();
        List<String> bad = rows.stream().filter(r -> r.name().isBlank() || r.name().equals("—") || r.outOf() == null)
                .map(r -> r.name() + " (" + r.score() + ")").toList();
        CheckLog.soft("every row has an assessment name and points possible", bad.isEmpty() ? rows.size() + " rows named" : "bad: " + bad, bad.isEmpty());
        CheckLog.review("assessments on the grades page (check the names against the course)",
                String.join("; ", rows.stream().map(r -> r.name() + " [" + r.module() + "]").toList()));
    }

    @Then("every row of the grades table shows a due date")
    public void every_row_shows_a_due_date() {
        List<String> none = rows.stream().filter(r -> !r.hasDue()).map(GradeRow::name).toList();
        CheckLog.soft("every assessment shows its due date", none.isEmpty() ? "all " + rows.size() + " have one" : "no due date: " + none, none.isEmpty());
    }

    /**
     * The current status must fit the row: submitted → Submitted / Graded /
     * Late / To grade; not submitted and past due → Missing; not submitted and
     * not due yet → no status (or "—"). A due date within a day of now is not
     * judged: the course's time zone (Denver) may differ from this machine's.
     */
    @Then("every row of the grades table shows the correct current status")
    public void every_row_shows_the_correct_status() {
        LocalDateTime now = LocalDateTime.now(COURSE_ZONE);
        List<String> wrong = new ArrayList<>();
        for (GradeRow r : rows) {
            String status = r.status().toLowerCase(Locale.ROOT);
            LocalDateTime due = due(r.due(), now);
            String expected;
            boolean ok;
            if (r.hasSubmitted()) {
                expected = "Submitted / Graded / Late / To grade";
                ok = status.contains("submitted") || status.contains("graded") || status.contains("late") || status.contains("to grade");
            } else if (due != null && due.isBefore(now.minusDays(1))) {
                expected = "Missing";
                ok = status.contains("missing");
            } else if (due != null && due.isAfter(now.plusDays(1))) {
                expected = "no status yet";
                ok = status.isBlank() || status.equals("—");
            } else {
                continue;
            }
            if (!ok) {
                wrong.add(r.name() + ": due " + r.due() + ", submitted " + r.submitted() + ", status \"" + r.status() + "\", expected " + expected);
            }
        }
        CheckLog.soft("every row's status fits its due date and submission", wrong.isEmpty() ? "all " + rows.size() + " fit" : String.join("; ", wrong), wrong.isEmpty());
    }

    @Then("the side bar's grade, missing, late and recent grades match the grades page")
    public void the_side_bar_matches_the_grades_page() {
        long missingRows = rows.stream().filter(r -> r.status().equalsIgnoreCase("Missing")).count();
        long lateRows = rows.stream().filter(r -> r.status().toLowerCase(Locale.ROOT).contains("late")).count();
        long gradedRows = rows.stream().filter(r -> r.status().equalsIgnoreCase("Graded")).count();
        String summary = page.gradesSummary();

        same("side bar grade % = the Total Grade %", sideBar.get("grade"), card.get("grade"));
        same("side bar Missing = Missing rows in the table", sideBar.get("missing"), (int) missingRows);
        same("side bar Missing = the Total Grade card's MISSING", sideBar.get("missing"), card.get("missing"));
        same("side bar Late = Late rows in the table", sideBar.get("late"), (int) lateRows);
        same("side bar Late = the Total Grade card's LATE", sideBar.get("late"), card.get("late"));
        same("the card's GRADED = Graded rows in the table", card.get("graded"), (int) gradedRows);
        boolean summaryOk = summary.contains(gradedRows + " graded") && summary.contains(missingRows + " missing");
        CheckLog.soft("the summary reads \"" + gradedRows + " graded · " + missingRows + " missing\"", summary, summaryOk);

        Map<?, ?> recent = (Map<?, ?>) sideBar.get("recent");
        for (Map.Entry<?, ?> e : recent.entrySet()) {
            String name = String.valueOf(e.getKey());
            GradeRow row = row(name);
            String inTable = row == null ? "not in the table" : row.score();
            CheckLog.soft("Recent Grades \"" + name + "\" " + e.getValue() + " = the table's score", inTable,
                    row != null && sameScore(String.valueOf(e.getValue()), row.score()));
        }
    }

    @Then("the gradebook's Calculated Score for the student matches the grades page")
    public void the_gradebook_calculated_score_matches() {
        same("Grid's Calculated Score = the grades page's Total Grade", gridScore.replace("%", ""), card.get("grade"));
    }

    // ---- TC3: the submitted assessment ----

    @Then("the grades table lists {string} with today's submitted date and a submitted status")
    public void the_grades_table_lists_with_todays_date(String item) {
        String title = RunClock.withNow(item);
        GradeRow r = row(title);
        CheckLog.soft("the grades table lists \"" + title + "\"", r == null ? "not listed" : r.toString(), r != null);
        if (r == null) {
            return;
        }
        List<String> today = List.of(LocalDate.now().format(MONTH_DAY), LocalDate.now(COURSE_ZONE).format(MONTH_DAY));
        boolean dated = today.stream().anyMatch(d -> r.submitted().startsWith(d + " "));
        boolean status = r.status().equalsIgnoreCase("Graded") || r.status().equalsIgnoreCase("Submitted");
        CheckLog.soft("\"" + title + "\" was submitted today (" + today.get(0) + ")", r.submitted(), dated);
        CheckLog.soft("\"" + title + "\" shows a submitted status", r.status() + ", score " + r.score(), status);
        CheckLog.soft("\"" + title + "\" is scored 1/1 (the right answer)", r.score(), sameScore("1/1", r.score()));
    }

    // ---- TC4: Course Analytics → Course Grade ----

    @When("I click Course Analytics on the grades page")
    public void i_click_course_analytics() {
        page.openCourseAnalytics();
    }

    @Then("the Course Analytics graphs are shown for {string}")
    public void the_course_analytics_graphs_are_shown(String name) {
        String crumb = page.breadcrumbStudent("Analytics");
        List<String> tabs = page.analyticsTabs();
        boolean ok = crumb.equals(name) && tabs.containsAll(List.of("COURSE GRADE", "ONLINE ACTIVITY", "COMMUNICATION")) && page.chartCount() > 0;
        CheckLog.check("Course Analytics shows " + name + "'s graphs", "ANALYTICS / " + crumb + ", tabs " + tabs + ", " + page.chartCount() + " chart(s)", ok);
        assertThat(ok).withFailMessage("Course Analytics did not show: " + crumb + " " + tabs).isTrue();
    }

    @When("I open the {string} graph")
    public void i_open_the_graph(String tab) {
        page.openAnalyticsTab(tab);
    }

    @Then("the Course Grade graph's overall grade, missing and late match the grades page")
    public void the_course_grade_header_matches() {
        Map<String, Double> totals = page.analyticsTotals();
        same("Overall Grade = the Total Grade %", totals.get("OVERALL GRADE"), card.get("grade"));
        same("Missing = the Total Grade card's MISSING", totals.get("MISSING"), card.get("missing"));
        same("Late = the Total Grade card's LATE", totals.get("LATE"), card.get("late"));
    }

    private List<Point> points = List.of();

    @Then("the Course Grade graph plots one point per assessment in the grades table")
    public void the_course_grade_graph_plots_one_point_per_assessment() {
        points = page.courseGradePoints();
        CheckLog.soft("one point per assessment", points.size() + " points, " + rows.size() + " rows in the table", points.size() == rows.size());
        long graded = points.stream().filter(p -> SCORE.matcher(p.tooltip()).find()).count();
        long gradedRows = rows.stream().filter(r -> r.points() != null).count();
        CheckLog.soft("scored points = scored rows in the table", graded + " scored points, " + gradedRows + " scored rows", graded == gradedRows);
    }

    @Then("each Course Grade point matches the assessment's score in the grades table")
    public void each_course_grade_point_matches() {
        List<String> wrong = new ArrayList<>();
        for (Point p : points) {
            String name = p.tooltip().split(" / ", 2)[0].strip();
            GradeRow r = row(name);
            Matcher m = SCORE.matcher(p.tooltip());
            if (r == null) {
                wrong.add(p.label() + " \"" + name + "\" is not in the table");
            } else if (m.find()) {
                String plotted = m.group(1) + "/" + m.group(2);
                double tipPct = Double.parseDouble(m.group(3));
                if (!sameScore(plotted, r.score())) {
                    wrong.add(p.label() + " " + name + ": plotted " + plotted + ", table " + r.score());
                }
                double expect = r.points() != null && r.outOf() != null && r.outOf() > 0 ? r.points() / r.outOf() * 100 : -1;
                if (Math.abs(p.percent() - expect) > 2 || Math.abs(tipPct - expect) > 1) {
                    wrong.add(p.label() + " " + name + ": point at " + Math.round(p.percent()) + "% (tooltip " + tipPct + "%), table " + Math.round(expect) + "%");
                }
            } else if (r.points() != null) {
                wrong.add(p.label() + " " + name + ": not scored on the graph, table " + r.score());
            } else if (Math.abs(p.percent()) > 2) {
                wrong.add(p.label() + " " + name + ": not scored, but plotted at " + Math.round(p.percent()) + "%");
            }
        }
        CheckLog.soft("every point is at the assessment's score", wrong.isEmpty() ? points.size() + " points match the table" : String.join("; ", wrong), wrong.isEmpty());
    }

    @Then("the Course Grade graph plots {string} at {int}%")
    public void the_course_grade_graph_plots_at(String item, int percent) {
        String title = RunClock.withNow(item);
        Point p = points.stream().filter(x -> x.tooltip().startsWith(title + " / ")).findFirst().orElse(null);
        boolean ok = p != null && Math.abs(p.percent() - percent) <= 2;
        CheckLog.soft("\"" + title + "\" is plotted at " + percent + "%",
                p == null ? "not plotted" : p.label() + " at " + Math.round(p.percent()) + "%: " + p.tooltip(), ok);
    }

    // ---- TC5: Online Activity ----

    @Then("the Online Activity graph shows page views and participations by week")
    public void the_online_activity_graph_shows() {
        readOnlineActivity(after);
        boolean ok = page.chartCount() == 2 && !pageViews.isEmpty() && !participations.isEmpty();
        CheckLog.soft("Online Activity plots page views and participations by week", "page views " + pageViews + "; participations " + participations, ok);
    }

    @Then("the weekly page views and participations add up to the totals")
    public void the_weekly_activity_adds_up() {
        Map<String, Double> totals = page.analyticsTotals();
        same("weekly page views add up to PAGE VIEWS", sum(pageViews, null), totals.get("PAGE VIEWS"));
        same("weekly participations add up to PARTICIPATIONS", sum(participations, null), totals.get("PARTICIPATIONS"));
    }

    @Then("this week's page views went up since the start of the run")
    public void this_weeks_page_views_went_up() {
        went_up("page views", "pageViews", 1);
    }

    @Then("this week's participations went up by at least {int} since the start of the run")
    public void this_weeks_participations_went_up(int by) {
        went_up("participations (quiz submitted, discussion post)", "participations", by);
    }

    // ---- TC6: Communication ----

    @Then("the Communication graph shows the student's messages and the instructor's replies by week")
    public void the_communication_graph_shows() {
        readCommunication(after);
        Map<String, Double> totals = page.analyticsTotals();
        boolean ok = page.chartCount() == 1 && !messages.isEmpty() && totals.containsKey("STUDENT") && totals.containsKey("INSTRUCTOR");
        CheckLog.soft("Communication plots student and instructor messages by week", "totals " + totals + "; weeks " + messages, ok);
        same("Total Messages = Student + Instructor", totals.get("TOTAL MESSAGES"),
                totals.getOrDefault("STUDENT", Double.NaN) + totals.getOrDefault("INSTRUCTOR", Double.NaN));
    }

    @Then("the weekly messages add up to the totals")
    public void the_weekly_messages_add_up() {
        Map<String, Double> totals = page.analyticsTotals();
        same("weekly student messages add up to STUDENT", sum(messages, "Student"), totals.get("STUDENT"));
        same("weekly instructor messages add up to INSTRUCTOR", sum(messages, "Instructor"), totals.get("INSTRUCTOR"));
    }

    @Then("this week's student messages went up by {int} since the start of the run")
    public void this_weeks_student_messages_went_up(int by) {
        went_up_exactly("student messages (the run's post)", "student", by);
    }

    @Then("this week's instructor replies went up by {int} since the start of the run")
    public void this_weeks_instructor_replies_went_up(int by) {
        went_up_exactly("instructor replies (the run's reply)", "instructor", by);
    }

    // ---- reading the weekly graphs ----

    private void readOnlineActivity(Map<String, Double> into) {
        pageViews = page.weeklyTooltips(0);
        participations = page.weeklyTooltips(1);
        String week = thisWeek(pageViews.keySet());
        into.put("pageViews", lastNumber(pageViews.get(week)));
        into.put("participations", lastNumber(participations.get(week)));
        weekLabel = week;
    }

    private void readCommunication(Map<String, Double> into) {
        messages = page.weeklyTooltips(0);
        String week = thisWeek(messages.keySet());
        into.put("student", named(messages.get(week), "Student"));
        into.put("instructor", named(messages.get(week), "Instructor"));
        weekLabel = week;
    }

    private String weekLabel;

    /** The week this run falls in: the last week label ("11/9") on or before today. */
    private static String thisWeek(Iterable<String> labels) {
        LocalDate today = LocalDate.now(COURSE_ZONE);
        String best = null;
        LocalDate bestDate = null;
        for (String l : labels) {
            String[] md = l.split("/");
            try {
                LocalDate d = MonthDay.of(Integer.parseInt(md[0].strip()), Integer.parseInt(md[1].strip())).atYear(today.getYear());
                if (d.isAfter(today.plusMonths(6))) {
                    d = d.minusYears(1);
                }
                if (!d.isAfter(today) && (bestDate == null || d.isAfter(bestDate))) {
                    best = l;
                    bestDate = d;
                }
            } catch (RuntimeException ignored) {
                // not a week label
            }
        }
        return best;
    }

    private void went_up(String what, String key, int by) {
        Double was = before.get(key);
        Double now = after.get(key);
        boolean ok = was != null && now != null && !was.isNaN() && !now.isNaN() && now - was >= by;
        CheckLog.soft("this week's (" + weekLabel + ") " + what + " went up by at least " + by, "before " + fmt(was) + ", after " + fmt(now), ok);
    }

    private void went_up_exactly(String what, String key, int by) {
        Double was = before.get(key);
        Double now = after.get(key);
        boolean ok = was != null && now != null && !was.isNaN() && !now.isNaN() && now - was == by;
        CheckLog.soft("this week's (" + weekLabel + ") " + what + " went up by " + by, "before " + fmt(was) + ", after " + fmt(now), ok);
    }

    /** "Week of 11/2 / Page Views / 350" → 350; NaN when there is no tooltip. */
    private static double lastNumber(String tooltip) {
        if (tooltip == null) {
            return Double.NaN;
        }
        Matcher m = LAST_NUMBER.matcher(tooltip.strip());
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    /** "Week of 11/2 / Student / 6 / Instructor / 0" → Student 6; a series the tooltip leaves out counts 0. */
    private static double named(String tooltip, String series) {
        if (tooltip == null || tooltip.isBlank()) {
            return Double.NaN;
        }
        Matcher m = Pattern.compile(series + "\\s*/\\s*(\\d+(?:\\.\\d+)?)").matcher(tooltip);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    private static double sum(Map<String, String> weeks, String series) {
        double total = 0;
        for (String t : weeks.values()) {
            double v = series == null ? lastNumber(t) : named(t, series);
            if (Double.isNaN(v)) {
                return Double.NaN;
            }
            total += v;
        }
        return total;
    }

    // ---- helpers ----

    private GradeRow row(String name) {
        return rows.stream().filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }

    /** "Nov 9 11:59 PM" → that date this course year; null for "—". */
    private static LocalDateTime due(String text, LocalDateTime now) {
        if (text == null || text.isBlank() || text.equals("—")) {
            return null;
        }
        try {
            LocalDateTime d = LocalDateTime.parse(text.strip() + " " + now.getYear(), DUE);
            return d.isAfter(now.plusMonths(6)) ? d.minusYears(1) : d;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean sameScore(String a, String b) {
        String[] x = a.replace(" ", "").split("/");
        String[] y = b.replace(" ", "").split("/");
        try {
            return x.length == 2 && y.length == 2 && Double.parseDouble(x[0]) == Double.parseDouble(y[0])
                    && Double.parseDouble(x[1]) == Double.parseDouble(y[1]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Two shown values agree: numbers within 0.01, or equal text. */
    private static void same(String what, Object a, Object b) {
        boolean ok;
        if (a == null || b == null) {
            ok = false;
        } else {
            double x = pct(a);
            double y = pct(b);
            ok = !Double.isNaN(x) && !Double.isNaN(y) ? Math.abs(x - y) < 0.01 : Objects.equals(String.valueOf(a), String.valueOf(b));
        }
        CheckLog.soft(what, fmt(a) + " vs " + fmt(b), ok);
    }

    private static double pct(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).replace("%", "").strip());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String fmt(Object o) {
        if (o == null) {
            return "not shown";
        }
        if (o instanceof Double d) {
            return d.isNaN() ? "not read" : d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
        }
        return String.valueOf(o);
    }
}
