package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.CourseSettingsPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/** A course's Settings: change name, code and description, save, and check them after a refresh. */
public class CourseSettingsSteps {

    private final CourseSettingsPage page = new CourseSettingsPage(DriverFactory.get());

    /** The run's date and time, the same in every step of the run. */
    private final String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

    /** Opens the course whose title starts with this, e.g. "basic of java" or "basic of java with edit". */
    @When("I open the course {string}")
    public void i_open_the_course(String title) {
        page.openCourse(title);
    }

    @When("I click Settings in the course")
    public void i_click_settings_in_the_course() {
        page.openSettings();
    }

    @When("I update the course name to {string}, code to {string} and description to {string}")
    public void i_update_the_course(String name, String code, String description) {
        page.update(name, code, withNow(description));
    }

    @When("I save the course settings")
    public void i_save_the_course_settings() {
        page.clickUpdateCourse();
    }

    @Then("the course settings are saved")
    public void the_course_settings_are_saved() {
        String result = page.saveResult();
        boolean saved = result.equals("saved");
        CheckLog.check("\"Course settings updated successfully\" is shown", result, saved);
        assertThat(saved).withFailMessage("Course settings were not saved: " + result).isTrue();
    }

    @When("I refresh the course page")
    public void i_refresh_the_course_page() {
        page.refresh();
    }

    @Then("the course settings show name {string}, code {string} and description {string}")
    public void the_course_settings_show(String name, String code, String description) {
        description = withNow(description);
        page.openSettings();
        String actualName = page.name();
        String actualCode = page.code();
        String actualDescription = page.description();
        page.closeSettings();

        CheckLog.soft("Course Name is " + name, actualName, actualName.equals(name));
        CheckLog.soft("Course Code is " + code, actualCode, actualCode.equals(code));
        CheckLog.soft("Description is \"" + description + "\"", "\"" + actualDescription + "\"", actualDescription.equals(description));
    }

    private String withNow(String text) {
        return text.replace("{now}", now);
    }

    /** The profile steps read the signed-in name from the home page header. */
    @When("I go back to the home page")
    public void i_go_back_to_the_home_page() {
        page.openHome();
    }
}
