package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.QuizPage;
import com.lxp.report.CheckLog;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Course quiz: the teacher builds a quiz with one question of each type and
 * publishes it, the student (the course's Student View) submits it, and the
 * teacher reads the scored attempt in the quiz's Responses.
 *
 * {now} in a feature value is the run's day and time (e.g. 041530), so every
 * run makes a quiz of its own.
 */
public class QuizSteps {

    private final QuizPage page = new QuizPage(DriverFactory.get());

    /** Student View's built-in student. */
    private static final String STUDENT = "CI Test Student";

    /** The quiz this run created. */
    private String quizName;

    /** The Response Details text of the student's submission. */
    private String details = "";

    @When("I create a quiz {string} with the questions:")
    public void i_create_a_quiz_with_the_questions(String name, DataTable questions) {
        quizName = withNow(name);
        page.createInFirstModule(quizName);
        List<Map<String, String>> rows = questions.asMaps();
        // each new question goes on top: add them last to first so the quiz reads in table order
        for (int i = rows.size() - 1; i >= 0; i--) {
            page.addQuestion(rows.get(i));
        }
        // a new quiz's editor can finish loading after the first question and drop it: re-add what is missing
        for (int attempt = 1; attempt <= 2; attempt++) {
            // the draft can be reset a moment after the questions show: wait for the footer's count
            // to settle, and if it says 0 every question is missing even if its text still shows
            int count = page.settledDraftCount();
            List<Map<String, String>> missing = count == 0 ? rows : rows.stream()
                    .filter(r -> !page.hasDraftQuestion(r.get("question"))).toList();
            if (missing.isEmpty()) break;
            System.out.println("[quiz] not in the draft after adding, adding again: "
                    + missing.stream().map(r -> r.get("type")).toList());
            for (int i = missing.size() - 1; i >= 0; i--) {
                page.addQuestion(missing.get(i));
            }
        }
        int draft = page.draftCount();
        CheckLog.check("the draft holds all " + rows.size() + " questions before saving",
                draft + " questions in the draft", draft == rows.size() || draft == -1);
        boolean saved = page.save();
        String error = page.errorText();
        CheckLog.check("quiz \"" + quizName + "\" is saved", saved ? "saved" : "not saved " + error, saved);
        assertThat(saved).withFailMessage("The quiz was not saved: " + error).isTrue();
    }

    @Then("the quiz {string} is created with {int} questions")
    public void the_quiz_is_created_with_questions(String name, int count) {
        int total = page.totalQuestions();
        CheckLog.check("\"" + withNow(name) + "\" has " + count + " questions", "Total Questions : " + total, total == count);
        assertThat(total).isEqualTo(count);
    }

    @When("I publish the quiz to the course")
    public void i_publish_the_quiz_to_the_course() {
        boolean published = page.publish(quizName);
        CheckLog.check("\"" + quizName + "\" is published", published ? "published" : "still unpublished", published);
        assertThat(published).withFailMessage(quizName + " was not published").isTrue();
    }

    @When("I switch to the course's Student View")
    public void i_switch_to_student_view() {
        page.enterStudentView();
    }

    @When("I exit Student View")
    public void i_exit_student_view() {
        page.exitStudentView();
    }

    @Then("the quiz {string} is listed for the student")
    public void the_quiz_is_listed_for_the_student(String name) {
        boolean listed = page.isListedInFirstModule(withNow(name));
        CheckLog.check("the student sees \"" + withNow(name) + "\"", listed ? "listed in Module 1" : "not listed", listed);
        assertThat(listed).withFailMessage("The student does not see " + withNow(name)).isTrue();
    }

    @When("I open the quiz {string}")
    public void i_open_the_quiz(String name) {
        page.startAttempt(withNow(name));
    }

    @When("I answer the questions:")
    public void i_answer_the_questions(DataTable answers) {
        for (Map<String, String> row : answers.asMaps()) {
            page.answer(row.get("type"), row.get("answer"));
        }
    }

    @When("I submit the quiz")
    public void i_submit_the_quiz() {
        page.submitAttempt();
    }

    @Then("the quiz submission success message is shown")
    public void the_quiz_submission_success_message_is_shown() {
        String message = page.submissionMessage();
        boolean shown = message.contains("evaluated soon") || message.startsWith("Submitted");
        CheckLog.check("the submission is confirmed", message, shown);
        assertThat(shown).withFailMessage("No submission message: " + message).isTrue();
    }

    @When("I open the responses of the quiz {string}")
    public void i_open_the_responses_of_the_quiz(String name) {
        page.openResponses(withNow(name));
    }

    /** This run's quiz is new, so the student's one submission is every attempt there is. */
    @Then("the student's submission is listed with every attempt")
    public void the_students_submission_is_listed_with_every_attempt() {
        List<String> rows = page.responseRows(STUDENT);
        int total = page.totalResponses();
        boolean listed = !rows.isEmpty() && rows.stream().allMatch(r -> r.contains("Submitted") || r.contains("Graded"));
        CheckLog.check(STUDENT + "'s submission is listed", rows.toString(), listed);
        CheckLog.check("Total Responses counts every attempt", total + " responses, " + rows.size() + " rows", total == rows.size());
        assertThat(listed).withFailMessage("No submitted response from " + STUDENT + ": " + rows).isTrue();
        assertThat(total).isEqualTo(rows.size());
        details = page.openResponseDetails(STUDENT);
    }

    @Then("the Multiple Choice and True or False answers are scored automatically")
    public void the_mcq_and_true_false_answers_are_scored_automatically() {
        for (String type : List.of("Multiple Choice", "True/False")) {
            String q = QuizPage.breakdownOf(details, type);
            boolean auto = q.contains("Auto-graded") && q.contains("Correct") && q.contains("1 / 1 point");
            CheckLog.soft(type + " is auto-graded and scored", q.replaceAll("\\s+", " ").strip(), auto);
        }
    }

    @Then("the Essay answer is waiting for the teacher's grading")
    public void the_essay_answer_is_waiting_for_grading() {
        String q = QuizPage.breakdownOf(details, "Essay");
        boolean manual = q.contains("Manual Grading") && details.contains("Awaiting Grading");
        page.closeResponseDetails();
        CheckLog.check("the Essay answer awaits the teacher's grading", q.replaceAll("\\s+", " ").strip(), manual);
        assertThat(manual).withFailMessage("The Essay answer is not waiting for grading").isTrue();
    }

    private String withNow(String s) {
        return RunClock.withNow(s);
    }

}
