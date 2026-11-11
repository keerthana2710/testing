package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.CreateCoursePage;
import com.lxp.report.CheckLog;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Create a course, fill in Course Setup, generate the AI plan and approve it. */
public class CreateCourseSteps {

    private final CreateCoursePage page = new CreateCoursePage(DriverFactory.get());

    private String name;
    private String code;

    @When("I click Create Course")
    public void i_click_create_course() {
        page.startCreate();
    }

    @When("I enter the course name {string} and code {string}")
    public void i_enter_the_course_name_and_code(String name, String code) {
        // {now} in a name becomes the run's day and time, e.g. "Config autosave 041530"
        name = RunClock.withNow(name);
        this.name = name;
        // A code must be unique within the semester, so a fixed code only works
        // once: CppandA10 -> CppandA10-031543 (day, hour, minute of this run).
        this.code = code + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddHHmm"));
        page.enterNameAndCode(name, this.code);
        CheckLog.check("course code is unique for this run", this.code, true);
    }

    @When("I click Create")
    public void i_click_create() {
        page.create();
    }

    @Then("the new course is created")
    public void the_new_course_is_created() {
        boolean shown = page.headerShows(name);
        CheckLog.check("course \"" + name + "\" is created and opened", shown ? page.courseUrl() : "not shown", shown);
        assertThat(shown).withFailMessage("The new course \"" + name + "\" did not open").isTrue();

        // a taken code gets a suffix such as "-131"; record it rather than fail
        String saved = page.savedCode(code);
        // drop any invisible marks the header text carries before comparing
        String visible = saved.replaceAll("[^\\p{Alnum}_-]", "");
        CheckLog.check("course code starts with " + code, saved.isEmpty() ? "not shown" : saved, visible.startsWith(code));
    }

    @When("I open the new course")
    public void i_open_the_new_course() {
        page.openCourseSetup();
    }

    @When("I fill in the course details:")
    public void i_fill_in_the_course_details(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            page.fill(row.get("field"), row.get("value"));
        }
        if (page.outlineWasThere()) {
            CheckLog.check("course outline is set", "already there (copied from the existing course with this code), kept", true);
        }
        String first = page.dateShown("First day");
        String last = page.dateShown("Last day");
        CheckLog.check("course dates are set", first + " to " + last, !first.startsWith("Pick") && !last.startsWith("Pick"));
    }

    @When("I click Save")
    public void i_click_save() {
        List<String> missing = page.missing();
        CheckLog.check("nothing required is missing", missing.isEmpty() ? "nothing missing" : String.join(", ", missing), missing.isEmpty());
        assertThat(missing).withFailMessage("Course Setup still needs: " + missing).isEmpty();
        page.saveAndClose();
    }

    @Then("the course details are saved")
    public void the_course_details_are_saved() {
        boolean closed = !page.isCourseSetupOpen();
        String error = page.errorText();
        boolean saved = closed && error.isEmpty();
        CheckLog.check("Course Setup saved and closed", saved ? "saved" : (error.isEmpty() ? "still open" : error), saved);
        assertThat(saved).withFailMessage("Course Setup was not saved: " + error).isTrue();
    }

    @When("I click Save & Generate")
    public void i_click_save_and_generate() {
        page.generate();
    }

    /** Generate straight from the Course Setup that is still open after filling it in. */
    @When("I click Generate")
    public void i_click_generate() {
        List<String> missing = page.missing();
        CheckLog.check("nothing required is missing", missing.isEmpty() ? "nothing missing" : String.join(", ", missing), missing.isEmpty());
        assertThat(missing).withFailMessage("Course Setup still needs: " + missing).isEmpty();
        page.generate();
    }

    @When("I wait until Approve is shown")
    public void i_wait_until_approve_is_shown() {
        waitForApprove(page::waitForApprove);
    }

    /** e.g. "I wait up to 7 minutes until Approve is shown"; it moves on as soon as Approve shows. */
    @When("I wait up to {int} minutes until Approve is shown")
    public void i_wait_up_to_minutes_until_approve_is_shown(int minutes) {
        waitForApprove(() -> page.waitForApprove(Duration.ofMinutes(minutes)));
    }

    private void waitForApprove(java.util.function.BooleanSupplier wait) {
        long started = System.currentTimeMillis();
        boolean shown = wait.getAsBoolean();
        long minutes = (System.currentTimeMillis() - started) / 60_000;
        String error = page.errorText();
        CheckLog.check("Approve appears once the AI plan is ready",
                shown ? "shown after ~" + minutes + " min" : (error.isEmpty() ? "not shown after " + minutes + " min" : error), shown);
        assertThat(shown).withFailMessage("Approve did not appear: " + (error.isEmpty() ? "timed out after " + minutes + " min" : error)).isTrue();
    }

    @Then("the course plan is generated")
    public void the_course_plan_is_generated() {
        boolean ready = page.waitForPlan();
        String error = page.errorText();
        CheckLog.check("AI course plan is ready to review", ready ? "ready" : (error.isEmpty() ? "timed out" : error), ready);
        assertThat(ready).withFailMessage("The course plan did not finish: " + (error.isEmpty() ? "timed out" : error)).isTrue();
    }

    @When("I review the course plan")
    public void i_review_the_course_plan() {
        String plan = page.planText();
        boolean hasModules = plan.toLowerCase().contains("module");
        String preview = plan.replaceAll("\\s+", " ");
        CheckLog.check("plan lists the modules", preview.length() > 150 ? preview.substring(0, 150) + "..." : preview, hasModules);
        assertThat(hasModules).withFailMessage("The plan shows no modules: " + preview).isTrue();
    }

    @When("I click Approve")
    public void i_click_approve() {
        page.approve();
    }

    @Then("the course plan is approved")
    public void the_course_plan_is_approved() {
        boolean approved = page.isApproved();
        String error = page.errorText();
        CheckLog.check("plan approved", approved ? "approved" : (error.isEmpty() ? "Approve still shown" : error), approved);
        assertThat(approved).withFailMessage("The plan was not approved: " + error).isTrue();
    }

    @When("I click Stop generation")
    public void i_click_stop_generation() {
        page.stopGeneration();
    }

    @Then("the generation is stopped")
    public void the_generation_is_stopped() {
        boolean stopped = page.isGenerationStopped();
        String error = page.errorText();
        CheckLog.check("course generation stopped", stopped ? "Generating Course panel gone" : (error.isEmpty() ? "still generating" : error), stopped);
        assertThat(stopped).withFailMessage("Generation did not stop: " + (error.isEmpty() ? "still generating" : error)).isTrue();
    }
}
