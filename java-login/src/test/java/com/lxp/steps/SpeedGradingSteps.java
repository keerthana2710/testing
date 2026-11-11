package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.AiGradingPage;
import com.lxp.pages.GradesPage;
import com.lxp.pages.SpeedGradingPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Speed grading (Fast Grade) from the teacher's gradebook. Locators live in
 * {@link SpeedGradingPage}; the grade card and Approve are shared with
 * {@link AiGradingPage}. Steps the run can't get past fail hard; what is
 * shown is checked softly (Hooks fails the scenario at the end).
 */
public class SpeedGradingSteps {

    private final WebDriver driver = DriverFactory.get();
    private final SpeedGradingPage fast = new SpeedGradingPage(driver);
    private final AiGradingPage grading = new AiGradingPage(driver);
    private final GradesPage grades = new GradesPage(driver);

    /** The assessment open in Fast Grade, and the work shown before a student change. */
    private String assessment = "";
    private String workBefore = "";
    private String studentBefore = "";
    /** The grade approved last, and the comment added to the library. */
    private String approvedGrade = "";
    private String libraryComment = "";
    /** Settings to put back at the end. */
    private boolean settingsChanged;
    /** Fast Grade lists one student only: switching steps are skipped (and reported once). */
    private boolean onlyOneStudent;

    // ---- opening ----

    @When("I open Fast Grade from the {string} column")
    public void i_open_fast_grade_from_the_column(String column) {
        assessment = RunClock.withNow(column);
        if (fast.open()) {
            fast.close();
        }
        if (!grades.gradebookShown()) {
            grades.openGradebookGrid();
        }
        fast.openFromColumn(assessment);
    }

    @Then("the Fast Grade page opens for that assessment")
    public void the_fast_grade_page_opens() {
        boolean open = fast.open();
        String text = fast.drawerText();
        boolean forIt = text.contains(assessment) || text.replaceAll("^M\\d+ - ", "").contains(assessment.replaceAll("^M\\d+ - ", ""));
        CheckLog.check("Fast Grade opens", open ? "open: " + fast.title() : "not open", open);
        CheckLog.soft("it is for \"" + assessment + "\"", fast.title(), forIt);
        assertThat(open).withFailMessage("Fast Grade did not open for " + assessment).isTrue();
    }

    @When("I show the student {string}")
    public void i_show_the_student(String name) {
        boolean shown = fast.showStudent(name);
        CheckLog.check("Fast Grade shows " + name, fast.student(), shown);
        assertThat(shown).withFailMessage("Could not show " + name + " in Fast Grade; shown: " + fast.student()).isTrue();
    }

    @When("I close Fast Grade")
    public void i_close_fast_grade() {
        fast.close();
    }

    // ---- what it shows (TC1-3) ----

    @Then("it shows the student's quiz responses")
    public void it_shows_the_quiz_responses() {
        String work = fast.work();
        boolean shown = work.contains("Question") && (work.contains("Your Answer") || work.contains("Student's Answer") || work.contains("Selected answer"));
        CheckLog.soft("the student's quiz answers are shown (questions with their answers)", excerpt(work), shown);
    }

    @Then("it shows the student's submitted assignment text")
    public void it_shows_the_assignment_text() {
        String work = fast.work();
        boolean none = work.contains("No submission");
        boolean text = !none && !work.contains("No submission content") && work.length() > 80;
        CheckLog.soft("the student's submitted text is shown", none ? "No submission" : excerpt(work), text);
    }

    @Then("it shows the student's posts and the teacher's replies")
    public void it_shows_posts_and_replies() {
        String work = fast.work();
        String student = fast.student();
        boolean posts = !work.contains("No posts yet") && work.contains(student);
        boolean replies = work.contains("Teacher") || work.contains("Instructor") || work.contains("Keerthana");
        CheckLog.soft("the student's discussion posts are shown", excerpt(work), posts);
        CheckLog.soft("the teacher's replies are shown in the conversation", replies ? "shown" : excerpt(work), replies);
    }

    @Then("the teacher can enter a grade and a comment")
    public void the_teacher_can_enter_a_grade_and_a_comment() {
        boolean grade = fast.gradeEditable("1");
        boolean comment = fast.commentEditable("Speed grading check " + RunClock.NOW);
        CheckLog.soft("a grade can be typed in \"Grade out of …\"", grade ? "typed and read back (then put back)" : "no editable grade box", grade);
        CheckLog.soft("a comment can be typed in \"Comment to student\"", comment ? "typed and read back (then cleared)"
                : "no comment box; editable boxes: " + fast.editableBoxes(), comment);
    }

    // ---- switching students (TC4-5) ----

    @When("I pick another student from the student dropdown")
    public void i_pick_another_student() {
        studentBefore = fast.student();
        workBefore = fast.work();
        String picked = fast.pickAnotherStudent();
        onlyOneStudent = picked.isEmpty();
        CheckLog.soft("another student is picked from the dropdown", picked.isEmpty() ? "only " + studentBefore + " is listed" : picked, !picked.isEmpty());
    }

    @Then("the student's name and work change")
    public void the_students_name_and_work_change() {
        if (onlyOneStudent) {
            CheckLog.review("student switching", "skipped: Fast Grade lists only " + fast.student());
            return;
        }
        String now = fast.student();
        String work = fast.work();
        CheckLog.soft("the student shown changes", studentBefore + " → " + now, !now.equals(studentBefore));
        CheckLog.soft("the work shown is the new student's", excerpt(work), !work.equals(workBefore));
    }

    @When("I show the first student in the list")
    public void i_show_the_first_student_in_the_list() {
        List<String> list = fast.studentList();
        onlyOneStudent = list.size() < 2;
        CheckLog.soft("Fast Grade lists more than one student (needed to switch students)", list.toString(), !onlyOneStudent);
        if (!onlyOneStudent) {
            fast.showStudent(list.get(0));
        }
    }

    /** Fast Grade's settings are the teacher's own: a run that stops after changing them says so. */
    @io.cucumber.java.After(order = 15000)
    public void remindIfSettingsLeftChanged() {
        if (settingsChanged) {
            System.out.println("[speed grading] Fast Grade settings were left changed (hidden names, sort, discussion view): "
                    + "set them back in Fast Grade → Settings");
            settingsChanged = false;
        }
    }

    @When("I click Next student")
    public void i_click_next_student() {
        if (onlyOneStudent) {
            return;
        }
        studentBefore = fast.student();
        workBefore = fast.work();
        boolean can = fast.canGoNext();
        CheckLog.soft("Next student is available", can ? "enabled" : "disabled (last student)", can);
        if (can) {
            fast.next();
        }
    }

    @When("I click Previous student")
    public void i_click_previous_student() {
        if (onlyOneStudent) {
            return;
        }
        studentBefore = fast.student();
        workBefore = fast.work();
        boolean can = fast.canGoPrevious();
        CheckLog.soft("Previous student is available", can ? "enabled" : "disabled (first student)", can);
        if (can) {
            fast.previous();
        }
    }

    // ---- status (TC6) ----

    @When("I set the status to {string}")
    public void i_set_the_status_to(String status) {
        fast.chooseStatus(status);
    }

    @Then("the Days late box shows and takes {string}")
    public void the_days_late_box_shows_and_takes(String days) {
        boolean shown = fast.daysLateShown();
        String held = shown ? fast.typeDaysLate(days) : "";
        CheckLog.soft("\"Days late\" appears for Late", shown ? "shown" : "not shown", shown);
        CheckLog.soft("\"Days late\" can be edited", "holds \"" + held + "\"", held.equals(days));
    }

    @Then("the grade shows {string}")
    public void the_grade_shows(String grade) {
        String footer = fast.footerGrade();
        boolean ex = footer.startsWith(grade) || footer.contains(" " + grade);
        CheckLog.soft("the grade shows " + grade, footer, ex);
        CheckLog.soft("the grade box is hidden while " + fast.status(), fast.gradeBoxShown() ? "still shown" : "hidden", !fast.gradeBoxShown());
    }

    // ---- comment library (TC7-9) ----

    @When("I add the comment {string} to the comment library")
    public void i_add_the_comment_to_the_library(String comment) {
        libraryComment = RunClock.withNow(comment);
        fast.addToLibrary(libraryComment);
    }

    @Then("the comment is saved in the library")
    public void the_comment_is_saved_in_the_library() {
        boolean saved = fast.libraryShows(libraryComment, true);
        CheckLog.soft("\"" + libraryComment + "\" is listed in the library", saved ? "listed" : fast.libraryList(), saved);
    }

    @When("I turn on suggestions when typing")
    public void i_turn_on_suggestions() {
        fast.setSuggestions(true);
        CheckLog.check("\"Show suggestions when typing\" is on", fast.suggestionsOn() ? "on" : "off", fast.suggestionsOn());
    }

    @Then("typing the start of the comment suggests it from the library")
    public void typing_suggests_it() {
        String start = libraryComment.substring(0, Math.min(14, libraryComment.length()));
        String offered = fast.suggestionsFor(start);
        boolean ok = offered.contains(libraryComment) || offered.contains(libraryComment.substring(0, Math.min(30, libraryComment.length())));
        CheckLog.soft("typing \"" + start + "\" offers the saved comment", offered.isEmpty() ? "no suggestions shown" : offered, ok);
    }

    @Then("the library lists the comment for {string}")
    public void the_library_lists_the_comment_for(String filter) {
        fast.filterLibrary(filter);
        boolean listed = fast.libraryShows(libraryComment, true);
        CheckLog.soft("\"" + filter + "\" lists the comment", listed ? "listed" : excerpt(fast.libraryList()), listed);
    }

    @Then("the library does not list the comment for {string}")
    public void the_library_does_not_list_the_comment_for(String filter) {
        fast.filterLibrary(filter);
        boolean hidden = !fast.libraryShows(libraryComment, true);
        CheckLog.soft("\"" + filter + "\" leaves the comment out (it was added on another assessment)", hidden ? "not listed" : "listed", hidden);
    }

    @When("I close the comment library")
    public void i_close_the_comment_library() {
        fast.filterLibrary("All comments");
        fast.closeLibrary();
    }

    // ---- settings (TC10) ----

    @When("I set Fast Grade to sort {string}, hide student names and show {string}")
    public void i_set_fast_grade_settings(String sort, String view) {
        fast.openSettings();
        fast.sortBy(sort);
        fast.hideNames(true);
        fast.discussionView(view);
        fast.saveOptions();
        settingsChanged = true;
    }

    @Then("student names are hidden")
    public void student_names_are_hidden() {
        String shown = fast.student();
        List<String> list = fast.studentList();
        boolean hidden = !shown.contains("CI Test Student") && list.stream().noneMatch(n -> n.contains("CI Test Student"));
        CheckLog.soft("student names are hidden (e.g. \"Student 1\")", shown + " · list " + list, hidden);
    }

    @Then("only the student's own posts are shown")
    public void only_the_students_own_posts_are_shown() {
        String work = fast.work();
        boolean own = !work.contains("conversation around them") && !work.contains("Keerthana");
        CheckLog.soft("only this student's posts, no conversation around them", excerpt(work), own);
    }

    @When("I set Fast Grade back to sort {string}, show student names and show {string}")
    public void i_set_fast_grade_back(String sort, String view) {
        fast.openSettings();
        fast.sortBy(sort);
        fast.hideNames(false);
        fast.discussionView(view);
        fast.saveOptions();
        settingsChanged = false;
    }

    @Then("the student list is in ascending order of first name")
    public void the_student_list_is_ascending() {
        List<String> list = fast.studentList();
        List<String> sorted = new ArrayList<>(list);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        CheckLog.soft("the student list is sorted by first name, A to Z", list.toString(), !list.isEmpty() && list.equals(sorted));
    }

    @Then("the student's posts are shown in the conversation around them")
    public void posts_in_conversation() {
        String work = fast.work();
        boolean context = work.contains("conversation around them") || work.contains("Keerthana");
        CheckLog.soft("the posts are shown in the conversation around them", excerpt(work), context);
    }

    // ---- grading (TC11-12) ----

    @When("I enter the grade {string} and approve it")
    public void i_enter_the_grade_and_approve(String grade) {
        String held = fast.setGrade(grade);
        CheckLog.check("the grade " + grade + " is entered", "box holds \"" + held + "\"", held.equals(grade));
        boolean approved = grading.approve();
        approvedGrade = grade;
        CheckLog.check("Approve records it", approved ? grading.gradeCard() : "not recorded: " + grading.gradeCard(), approved);
        assertThat(approved).withFailMessage("The grade was not approved: " + grading.gradeCard()).isTrue();
    }

    /** An already graded assessment: changes the grade to one point more (or less, at the maximum) and approves. */
    @When("I change the recorded grade and approve it")
    public void i_change_the_recorded_grade_and_approve_it() {
        String card = grading.gradeCard();
        double[] recorded = AiGradingPage.score(card.replace("—", ""));
        CheckLog.check("the assessment is already graded", card, grading.hasRecordedGrade());
        double max = recorded != null ? recorded[1] : 100;
        double old = recorded != null ? recorded[0] : 0;
        String next = num(old + 1 <= max ? old + 1 : old - 1);
        i_enter_the_grade_and_approve(next);
        CheckLog.review("regrade", num(old) + " → " + next);
    }

    @Then("the new grade is recorded")
    public void the_new_grade_is_recorded() {
        // the card can lag a few seconds behind Approve
        String card = grading.gradeCard();
        double[] recorded = AiGradingPage.score(card.replace("—", ""));
        for (int i = 0; i < 15 && (recorded == null || !num(recorded[0]).equals(approvedGrade)); i++) {
            pause(1000);
            card = grading.gradeCard();
            recorded = AiGradingPage.score(card.replace("—", ""));
        }
        boolean ok = recorded != null && num(recorded[0]).equals(approvedGrade);
        CheckLog.soft("RECORDED GRADE is " + approvedGrade, card, ok);
    }

    @Then("the gradebook shows that grade for {string}")
    public void the_gradebook_shows_that_grade(String student) {
        fast.close();
        grades.openGradebookGrid();
        String cell = "";
        for (int i = 0; i < 10 && !cellHas(cell = fast.gridCell(student, assessment)); i++) {
            pause(2000);
        }
        CheckLog.soft("the gradebook shows " + approvedGrade + " for " + student + " in \"" + assessment + "\"", cell.isEmpty() ? "cell not found" : cell, cellHas(cell));
    }

    private boolean cellHas(String cell) {
        String c = cell.toLowerCase(Locale.ROOT).replace(" ", "");
        return !approvedGrade.isEmpty() && (c.startsWith(approvedGrade) || c.contains(approvedGrade + "/") || c.matches(".*\\b" + approvedGrade + "\\b.*"));
    }

    // ---- helpers ----

    private static String excerpt(String s) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() > 300 ? t.substring(0, 300) + "…" : t;
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
