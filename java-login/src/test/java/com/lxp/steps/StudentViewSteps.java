package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.QuizPage;
import com.lxp.pages.StudentViewPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Student View checks on a course. The flow steps assert (a later step can't
 * run without them); the "what does the student see" checks are soft: each is
 * recorded in the report, the run carries on, and Hooks fails the scenario at
 * the end if any missed.
 */
public class StudentViewSteps {

    /** Student View's built-in student. */
    private static final String STUDENT = "CI Test Student";

    private final WebDriver driver = DriverFactory.get();
    private final StudentViewPage sv = new StudentViewPage(driver);

    /** The teacher's role before Student View, e.g. ADMIN or TEACHER. */
    private String teacherRole = "";
    /** The footer's height on the desktop, to compare with a phone screen. */
    private double desktopFooterHeight;
    private String desktopFooterText = "";
    /** The quiz's address, and the first tab's handle while two tabs are open. */
    private String quizUrl;
    private String firstTab;
    private String firstAttempt;

    private void course() {
        if (sv.courseUrl() == null) {
            sv.rememberCourse();
        }
    }

    // ---- entering, staying, leaving ----

    /**
     * Student View swaps the whole session and reloads the page, and Chrome
     * has been seen to close mid-way ("not connected to DevTools"). The
     * teacher's session and page are saved first, so a closed browser is
     * replaced, signed in again from that session (no new OTP), and Student
     * View is clicked once more.
     */
    @When("I click Student View")
    public void i_click_student_view() {
        course();
        teacherRole = String.valueOf(sv.session().get("role"));
        String page = driver.getCurrentUrl();
        DriverFactory.saveSession();
        try {
            sv.clickStudentView();
        } catch (org.openqa.selenium.WebDriverException e) {
            if (!DriverFactory.isBrowserGone(e)) {
                throw e;
            }
            System.out.println("[student view] the browser closed while starting Student View; reopening " + page + " and trying again");
            DriverFactory.restartKeepingSession();
            driver.get(page);
            sv.clickStudentView();
            CheckLog.check("Student View started after the browser closed and was reopened", "started on " + sv.path(), true);
        }
    }

    @Then("the Student View footer appears at the bottom of the page")
    public void the_student_view_footer_appears_at_the_bottom() {
        Map<String, Object> f = sv.footer();
        boolean shown = !f.isEmpty() && "fixed".equals(f.get("position"));
        double bottom = shown ? num(f.get("y")) + num(f.get("height")) : -1;
        boolean atBottom = shown && Math.abs(bottom - num(f.get("vh"))) <= 2;
        boolean fullWidth = shown && num(f.get("width")) >= num(f.get("vw")) - 2;
        desktopFooterHeight = shown ? num(f.get("height")) : 0;
        desktopFooterText = shown ? String.valueOf(f.get("text")) : "";
        CheckLog.check("the Student View footer is shown", shown ? String.valueOf(f.get("text")) : "no footer", shown);
        CheckLog.soft("the footer is fixed to the bottom, full width",
                shown ? "bottom at " + Math.round(bottom) + " of " + Math.round(num(f.get("vh"))) + ", width " + Math.round(num(f.get("width")))
                        + " of " + Math.round(num(f.get("vw"))) + ", colour " + f.get("background") : "no footer", atBottom && fullWidth);
        CheckLog.soft("the side panel offers Cancel View", sv.cancelViewShown() ? "shown" : "not shown", sv.cancelViewShown());
        assertThat(shown).withFailMessage("No Student View footer").isTrue();
    }

    @Then("the app's role is {string} as {string}")
    public void the_apps_role_is_as(String role, String user) {
        Map<String, Object> s = sv.session();
        boolean ok = role.equalsIgnoreCase(String.valueOf(s.get("role"))) && String.valueOf(s.get("user")).startsWith(user);
        CheckLog.check("the role is " + role + " as " + user, s.get("role") + ", " + s.get("user"), ok);
        assertThat(ok).withFailMessage("Expected role " + role + " as " + user + ", got " + s).isTrue();
    }

    @Then("Student View cannot be started again")
    public void student_view_cannot_be_started_again() {
        boolean offered = sv.offersStudentView();
        CheckLog.soft("no Student View button while in Student View", offered ? "Student View is offered again" : "only Exit / Cancel View", !offered);
    }

    @When("I reload the page")
    public void i_reload_the_page() {
        driver.navigate().refresh();
    }

    @Then("the course is still in Student View")
    public void the_course_is_still_in_student_view() {
        Map<String, Object> s = sv.session();
        boolean footer = sv.footerShown();
        boolean student = "STUDENT".equals(s.get("role"));
        CheckLog.soft("still in Student View on " + s.get("path"),
                (footer ? "footer shown" : "no footer") + ", role " + s.get("role") + ", " + s.get("user"), footer && student);
    }

    @Then("I am back in the teacher's view")
    public void i_am_back_in_the_teachers_view() {
        Map<String, Object> s = sv.session();
        boolean footerGone = sv.footerGone();
        boolean role = !teacherRole.isEmpty() && teacherRole.equals(s.get("role"));
        boolean ended = !Boolean.TRUE.equals(s.get("teacherParked")) && !String.valueOf(s.get("user")).startsWith(STUDENT);
        CheckLog.check("the footer is gone", footerGone ? "gone" : "still shown", footerGone);
        CheckLog.check("the role is back to the teacher's, " + teacherRole, s.get("role") + ", " + s.get("user") + ", on " + s.get("path"), role);
        CheckLog.check("the student session is ended", ended ? "test student signed out, teacher session restored" : "student session still there: " + s, ended);
        CheckLog.soft("\"Exited Student View\" is shown", sv.exitedMessageShown() ? "shown" : "not shown", sv.exitedMessageShown());
        assertThat(footerGone && role && ended).withFailMessage("Not back in the teacher's view: " + s).isTrue();
    }

    // ---- moving around ----

    /** Each part of the feature starts from the course's module list. */
    @When("I go back to the course page")
    public void i_go_back_to_the_course_page() {
        course();
        sv.openCoursePage();
    }

    @When("I open the course's Grades")
    public void i_open_the_courses_grades() {
        course();
        sv.openCoursePage();
        sv.openGrades();
    }

    @Then("My Grades shows only the test student's grades")
    public void my_grades_shows_only_the_test_students_grades() {
        sv.waitForText(STUDENT);
        String text = sv.gradesText();
        boolean mine = sv.myGradesShown() && text.contains(STUDENT);
        boolean noGradebook = !sv.teacherGradebookShown() && !text.contains("students enrolled") && !text.contains("student enrolled");
        CheckLog.soft("My Grades is the test student's", mine ? STUDENT + "'s My Grades" : text, mine);
        CheckLog.soft("no other students or teacher gradebook", noGradebook ? "only " + STUDENT : text, noGradebook);
    }

    @Then("My Grades lists {string} for the test student")
    public void my_grades_lists_for_the_test_student(String item) {
        String title = RunClock.withNow(item);
        // the student's name and the items load after the page
        boolean listed = sv.myGradesShown() && sv.waitForText(STUDENT, title);
        String text = sv.gradesText();
        CheckLog.soft("My Grades (" + STUDENT + ") lists \"" + title + "\"", listed ? "listed" : "not listed: " + text, listed);
    }

    @When("I open the {word} {string} in module {int}")
    public void i_open_the_item_in_module(String type, String title, int module) {
        course();
        sv.openItem(String.valueOf(module), RunClock.withNow(title));
        if (type.equals("quiz")) {
            quizUrl = driver.getCurrentUrl();
        }
    }

    @Then("the page content shows")
    public void the_page_content_shows() {
        String text = sv.pageContent();
        boolean shown = text.length() > 300 && !text.contains("Not Found") && !text.contains("Something went wrong");
        CheckLog.soft("the page's content renders", text.length() + " characters: " + text.substring(0, Math.min(140, text.length())), shown);
    }

    @When("I click Next")
    public void i_click_next() {
        sv.next();
    }

    @Then("the next item {string} opens")
    public void the_next_item_opens(String title) {
        String text = sv.pageContent();
        boolean opened = text.contains(title);
        CheckLog.soft("Next opens \"" + title + "\"", opened ? "opened, " + sv.path() : text.substring(0, Math.min(140, text.length())), opened);
    }

    @Then("the page has no Edit button and nothing can be edited")
    public void the_page_has_no_edit_button() {
        List<String> teacher = sv.teacherButtonsShown();
        int editable = sv.editableAreas();
        CheckLog.soft("no Edit or other teacher buttons", teacher.isEmpty() ? "none" : "shown: " + teacher, teacher.isEmpty());
        CheckLog.soft("no editing area on the page", editable + " editable areas", editable == 0);
    }

    // ---- module list ----

    @When("I try to drag Module 1 below Module 2")
    public void i_try_to_drag_module_1_below_module_2() {
        course();
        sv.openCoursePage();
        List<String> before = sv.moduleTitles();
        int handles = sv.dragHandles();
        CheckLog.soft("the modules have no drag handles", handles + " drag handles", handles == 0);
        sv.dragFirstModuleOntoSecond();
        driver.navigate().refresh();
        sv.openCoursePage();
        modulesBefore = before;
    }

    private List<String> modulesBefore;

    @Then("the modules keep their order")
    public void the_modules_keep_their_order() {
        List<String> after = sv.moduleTitles();
        boolean same = after.equals(modulesBefore);
        CheckLog.soft("the module order is unchanged after a reload", same ? String.valueOf(after) : modulesBefore + " → " + after, same);
    }

    @Then("the last item of the long module list is not covered by the footer")
    public void the_last_item_is_not_covered_by_the_footer() {
        course();
        Map<String, Object> last = sv.lastItemAtBottom();
        Map<String, Object> f = sv.footer();
        boolean clear = !last.isEmpty() && Boolean.TRUE.equals(last.get("onTop"))
                && (f.isEmpty() || num(last.get("bottom")) <= num(f.get("y")));
        CheckLog.soft("the last item (\"" + last.get("title") + "\") is above the footer",
                last.isEmpty() ? "no items" : "item bottom " + Math.round(num(last.get("bottom"))) + ", footer top "
                        + (f.isEmpty() ? "none" : Math.round(num(f.get("y")))) + (Boolean.TRUE.equals(last.get("onTop")) ? "" : ", covered by " + last.get("coveredBy")),
                clear);
    }

    // ---- blocked teacher access ----

    @Then("Settings is not offered")
    public void settings_is_not_offered() {
        course();
        sv.openCoursePage();
        boolean offered = sv.settingsOffered();
        CheckLog.soft("no Settings in the course panel", offered ? "Settings is offered" : "not offered", !offered);
    }

    @When("I open the address {string}")
    public void i_open_the_address(String path) {
        course();
        sv.openPath(path);
    }

    @Then("the address is blocked")
    public void the_address_is_blocked() {
        String outcome = sv.pageOutcome();
        boolean blocked = outcome.startsWith("Not Found") || sv.path().startsWith("/home") || sv.path().startsWith("/welcome");
        boolean settings = sv.courseSettingsShown();
        CheckLog.soft("the address is denied or redirected", outcome, blocked);
        CheckLog.soft("no Course Settings shown", settings ? "Course Settings shown" : "not shown", !settings);
    }

    @Then("the address is blocked and no teacher gradebook shows")
    public void the_address_is_blocked_and_no_teacher_gradebook() {
        String outcome = sv.pageOutcome();
        boolean blocked = outcome.startsWith("Not Found") || sv.path().startsWith("/home") || sv.path().startsWith("/welcome");
        boolean gradebook = sv.teacherGradebookShown();
        CheckLog.soft("the address is denied or redirected", outcome, blocked);
        CheckLog.soft("no teacher gradebook", gradebook ? "teacher gradebook shown" : "not shown", !gradebook);
    }

    // ---- windows and screens ----

    @When("I open the Start Quiz window")
    public void i_open_the_start_quiz_window() {
        sv.openStartQuizWindow();
    }

    @Then("the window shows above the Student View footer")
    public void the_window_shows_above_the_student_view_footer() {
        Map<String, Object> w = sv.openWindow();
        Map<String, Object> f = sv.footer();
        List<?> buttons = (List<?>) w.getOrDefault("buttons", List.of());
        boolean usable = !w.isEmpty() && buttons.stream().noneMatch(b -> String.valueOf(b).endsWith("(covered)"));
        CheckLog.soft("the \"" + w.get("title") + "\" window's buttons are not covered", String.valueOf(buttons), usable);
        int windowZ = parseZ(w.get("z"));
        int footerZ = parseZ(f.get("z"));
        CheckLog.soft("the window's z-index is above the footer's", "window " + w.get("z") + ", footer " + f.get("z"), windowZ > footerZ);
    }

    @When("I close the window")
    public void i_close_the_window() {
        sv.closeWindow();
    }

    /** On the course page, where the Back to dashboard and course menu buttons are. */
    @When("I switch to an iPhone SE screen")
    public void i_switch_to_an_iphone_se_screen() {
        course();
        sv.emulateScreen(375, 667);
        // reload the course page at the new size and wait for its modules
        sv.openCoursePage();
        sv.footerShown();
    }

    @Then("the Student View footer fits the screen")
    public void the_student_view_footer_fits_the_screen() {
        Map<String, Object> f = sv.footer();
        boolean fits = !f.isEmpty() && num(f.get("width")) <= num(f.get("vw")) + 1
                && Math.abs(num(f.get("y")) + num(f.get("height")) - num(f.get("vh"))) <= 2;
        String text = String.valueOf(f.get("text"));
        boolean adjusts = !f.isEmpty() && num(f.get("height")) <= desktopFooterHeight + 1
                && (desktopFooterText.isEmpty() || text.length() < desktopFooterText.length());
        CheckLog.soft("the footer fits the " + (f.isEmpty() ? "?" : Math.round(num(f.get("vw"))) + "px") + " screen",
                f.isEmpty() ? "no footer" : Math.round(num(f.get("width"))) + " x " + Math.round(num(f.get("height"))) + ": " + f.get("text"), fits);
        CheckLog.soft("the footer adjusts to the small screen (no taller, shorter text)",
                f.isEmpty() ? "no footer" : Math.round(num(f.get("height"))) + "px high (desktop " + Math.round(desktopFooterHeight)
                        + "px), \"" + text + "\"", adjusts);
    }

    @Then("the footer does not cover {string}, {string} or {string}")
    public void the_footer_does_not_cover(String a, String b, String c) {
        for (String label : List.of(a, b, c)) {
            String cover = sv.coverOf(label);
            CheckLog.soft("\"" + label + "\" is not covered", cover, cover.equals("clear"));
        }
    }

    @When("I switch back to the desktop screen")
    public void i_switch_back_to_the_desktop_screen() {
        sv.stopEmulating();
        sv.footerShown();
    }

    // ---- quiz ----

    @Then("the teacher sees {string} on the quiz")
    public void the_teacher_sees_on_the_quiz(String button) {
        boolean shown = sv.teacherButtonsShown().contains(button);
        CheckLog.check("the teacher's quiz page has " + button, shown ? "shown" : "not shown", shown);
        assertThat(shown).withFailMessage("No " + button + " for the teacher").isTrue();
    }

    @Then("the quiz page reloads in Student View with {string} showing at once")
    public void the_quiz_page_reloads_in_student_view(String button) {
        long start = System.currentTimeMillis();
        String shown = sv.attemptButton();
        long ms = System.currentTimeMillis() - start;
        boolean footer = sv.footerShown();
        boolean samePage = driver.getCurrentUrl().equals(quizUrl);
        CheckLog.check("the quiz page is in Student View", (footer ? "footer shown" : "no footer") + (samePage ? ", same quiz page" : ", now on " + sv.path()), footer && samePage);
        CheckLog.soft("\"" + button + "\" shows right away", shown.isEmpty() ? "no Take/Resume button" : "\"" + shown + "\" after " + ms + " ms",
                shown.equals(button) && ms < 10_000);
        assertThat(footer).withFailMessage("Student View did not start on the quiz page").isTrue();
    }

    @Then("the quiz has no teacher buttons")
    public void the_quiz_has_no_teacher_buttons() {
        List<String> teacher = sv.teacherButtonsShown();
        CheckLog.soft("no Edit / Responses / Quick Grade on the quiz", teacher.isEmpty() ? "none" : "shown: " + teacher, teacher.isEmpty());
    }

    @When("I open the quiz's own address again")
    public void i_open_the_quizs_own_address_again() {
        driver.get(quizUrl);
        sv.footerShown();
    }

    @Then("the quiz opens for the student, not in the editor")
    public void the_quiz_opens_for_the_student_not_in_the_editor() {
        String take = sv.attemptButton();
        List<String> teacher = sv.teacherButtonsShown();
        boolean student = !take.isEmpty() && teacher.isEmpty() && sv.editableAreas() == 0;
        CheckLog.soft("the quiz's address shows the student's quiz page, no editor",
                (take.isEmpty() ? "no Take button" : "\"" + take + "\"") + (teacher.isEmpty() ? ", no teacher buttons" : ", teacher buttons: " + teacher), student);
    }

    @When("I start the quiz in this tab")
    public void i_start_the_quiz_in_this_tab() {
        sv.startOrResumeAttempt();
        firstAttempt = sv.attemptAddress();
        firstTab = driver.getWindowHandle();
    }

    @When("I open the same quiz in a second tab")
    public void i_open_the_same_quiz_in_a_second_tab() {
        firstTab = sv.openInNewTab(quizUrl);
        sv.footerShown();
    }

    @Then("the second tab carries on with the same attempt")
    public void the_second_tab_carries_on_with_the_same_attempt() {
        String button = sv.attemptButton();
        CheckLog.soft("the second tab offers to resume the open attempt", button.isEmpty() ? "no button" : "\"" + button + "\"", button.startsWith("Resume"));
        String outcome;
        boolean same;
        try {
            sv.startOrResumeAttempt();
            String second = sv.attemptAddress();
            same = second.equals(firstAttempt);
            outcome = same ? "same attempt: " + responseId(second)
                    : "the second tab started another attempt (" + responseId(firstAttempt) + " and " + responseId(second) + " both open)";
        } catch (RuntimeException e) {
            same = false;
            outcome = "could not open the attempt: " + e.getMessage().lines().findFirst().orElse("");
        }
        CheckLog.soft("both tabs work on one attempt", outcome, same);
    }

    @When("I close the second tab")
    public void i_close_the_second_tab() {
        sv.closeTabAndReturn(firstTab);
    }

    @When("I answer {string} and submit the quiz")
    public void i_answer_and_submit_the_quiz(String answer) {
        new QuizPage(driver).answer("Multiple Choice", answer);
        submitted = sv.submitAndRead();
    }

    private String submitted = "";

    @Then("the quiz is submitted")
    public void the_quiz_is_submitted() {
        boolean ok = submitted.equals("submitted");
        CheckLog.check("the test student's attempt is submitted", submitted, ok);
        assertThat(ok).withFailMessage("The quiz was not submitted: " + submitted).isTrue();
    }

    @Then("the quiz lists {int} past attempt(s)")
    public void the_quiz_lists_past_attempts(int count) {
        driver.get(quizUrl);
        sv.attemptButton();
        int shown = pastAttemptCount();
        CheckLog.soft("the quiz lists " + count + " past attempt(s)", shown + " listed", shown == count);
    }

    /** The attempt's id from its address, e.g. responseId=0159f0c3-... */
    private static String responseId(String address) {
        Matcher m = Pattern.compile("responseId=([\\w-]+)").matcher(address);
        return m.find() ? m.group(1) : address;
    }

    private int pastAttemptCount() {
        sv.waitForPastAttempts();
        String text = sv.pageContent();
        int i = text.indexOf("Past Attempts");
        if (i < 0) {
            return 0;
        }
        Matcher m = Pattern.compile("Attempt (\\d+)").matcher(text.substring(i));
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (m.find()) {
            seen.add(m.group(1));
        }
        return seen.size();
    }

    @When("I exit Student View in a second tab")
    public void i_exit_student_view_in_a_second_tab() {
        firstTab = sv.openInNewTab(sv.courseUrl() + "?type=course");
        sv.footerShown();
        sv.exit();
        secondTab = driver.getWindowHandle();
        sv.switchTo(firstTab);
    }

    private String secondTab;

    /**
     * The first tab still shows the attempt started in Student View. If the
     * app has already closed it (it noticed the session change), that is
     * recorded as the outcome: there is nothing left to submit.
     */
    @When("I answer {string} and submit the quiz in the first tab")
    public void i_answer_and_submit_the_quiz_in_the_first_tab(String answer) {
        if (!sv.attemptStillOpen()) {
            submitted = "the attempt page was closed when Student View ended; now showing " + sv.path()
                    + (sv.footerGone() ? " as the teacher" : " in Student View");
            return;
        }
        try {
            i_answer_and_submit_the_quiz(answer);
        } catch (RuntimeException e) {
            submitted = "could not answer or submit: " + e.getMessage().lines().findFirst().orElse("");
        }
    }

    @Then("the submission is refused")
    public void the_submission_is_refused() {
        boolean refused = !submitted.equals("submitted");
        CheckLog.soft("a submission after leaving Student View is refused", submitted, refused);
    }

    @When("I go back to the teacher's tab")
    public void i_go_back_to_the_teachers_tab() {
        sv.closeTabAndReturn(secondTab);
    }

    @Then("only the test student's attempts are recorded")
    public void only_the_test_students_attempts_are_recorded() {
        QuizPage quiz = new QuizPage(driver);
        List<String> rows = quiz.responseRows(STUDENT);
        int total = quiz.totalResponses();
        boolean only = !rows.isEmpty() && total == rows.size();
        CheckLog.soft("Responses hold only " + STUDENT + "'s attempts", total + " response(s), " + rows.size() + " from " + STUDENT + ": " + rows, only);
    }

    // ---- assignment and discussion ----

    @Then("the assignment offers only {string} to the student")
    public void the_assignment_offers_only_to_the_student(String button) {
        boolean offered = sv.pageContent().contains(button);
        List<String> teacher = sv.teacherButtonsShown();
        CheckLog.soft("the assignment offers \"" + button + "\"", offered ? "shown" : "not shown", offered);
        CheckLog.soft("no teacher tabs (Edit, Responses, Quick Grade)", teacher.isEmpty() ? "none" : "shown: " + teacher, teacher.isEmpty());
    }

    @When("I post {string} in the discussion")
    public void i_post_in_the_discussion(String text) {
        postText = RunClock.withNow(text);
        sv.post(postText);
    }

    private String postText;

    @Then("the post is shown as from {string}")
    public void the_post_is_shown_as_from(String author) {
        String post = sv.postWith(postText);
        boolean mine = post.contains(author);
        CheckLog.soft("the post is marked as from " + author, post.isEmpty() ? "post not shown" : post, mine);
    }

    // ---- module visibility ----

    @When("I {word} module {int} only")
    public void i_change_module_only(String action, int module) {
        course();
        sv.openCoursePage();
        String option = action.equalsIgnoreCase("unpublish") ? "Unpublish Module Only" : "Publish Module Only";
        sv.moduleMenu(String.valueOf(module), option);
    }

    @Then("module {int} shows as {word}")
    public void module_shows_as(int module, String state) {
        String shown = sv.modulePublishState(String.valueOf(module));
        boolean ok = shown.toLowerCase().startsWith(state.toLowerCase());
        CheckLog.check("Module " + module + " is " + state, shown, ok);
        assertThat(ok).withFailMessage("Module " + module + " shows " + shown).isTrue();
    }

    @Then("the student sees modules {int}, {int} and {int} but not module {int}")
    public void the_student_sees_modules_but_not(int a, int b, int c, int hidden) {
        sv.openCoursePage();
        List<String> titles = sv.moduleTitles();
        for (int n : List.of(a, b, c)) {
            boolean seen = titles.stream().anyMatch(t -> t.startsWith("Module " + n + ":"));
            CheckLog.soft("the student sees Module " + n, seen ? "listed" : "missing from " + titles, seen);
        }
        boolean gone = titles.stream().noneMatch(t -> t.startsWith("Module " + hidden + ":"));
        CheckLog.soft("the student does not see unpublished Module " + hidden, gone ? "not listed: " + titles : "listed: " + titles, gone);
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static int parseZ(Object z) {
        try {
            return Integer.parseInt(String.valueOf(z));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
