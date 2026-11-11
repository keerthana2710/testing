package com.lxp.steps;

import com.lxp.config.OtpPrompt;
import com.lxp.config.Settings;
import com.lxp.driver.DriverFactory;
import com.lxp.pages.CoursePeoplePage;
import com.lxp.pages.HomePage;
import com.lxp.pages.LoginPage;
import com.lxp.pages.MfaPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Adding people to a course with a role, and removing them again. */
public class CoursePeopleSteps {

    private final WebDriver driver = DriverFactory.get();
    private final LoginPage loginPage = new LoginPage(driver);
    private final MfaPage mfaPage = new MfaPage(driver);
    private final HomePage homePage = new HomePage(driver);
    private final CoursePeoplePage peoplePage = new CoursePeoplePage(driver);

    @Given("I am signed in")
    public void i_am_signed_in() {
        loginPage.login(Settings.EMAIL, Settings.password());
        assertThat(mfaPage.isDisplayed())
                .withFailMessage("Expected the 6-digit MFA prompt")
                .isTrue();
        mfaPage.enterVerificationCode(OtpPrompt.ask()).clickVerifyCode();
        boolean home = homePage.isDisplayed();
        CheckLog.check("signed in and on the home page", home ? "on /home" : driver.getCurrentUrl(), home);
        assertThat(home)
                .withFailMessage("Expected to be redirected to /home")
                .isTrue();
    }

    @Given("I open the People tab of the {string} course")
    public void i_open_the_people_tab(String courseTitle) {
        peoplePage.openFor(courseTitle);
    }

    @Given("{string} is not on the course")
    public void is_not_on_the_course(String email) {
        if (peoplePage.isListed(email)) {
            peoplePage.remove(email);
            assertThat(peoplePage.waitUntilNotListed(email))
                    .withFailMessage("Could not clear %s off the course before the test", email)
                    .isTrue();
        }
    }

    @Given("the course has no students")
    public void the_course_has_no_students() {
        peoplePage.removeAllStudents();
        CheckLog.check("no Student on the course", "no Student on the course", true);
    }

    @When("I add the user found by searching {string} with email {string} as {string}")
    public void i_add_the_user_found_by_searching(String searchText, String email, String role) {
        peoplePage.addUser(searchText, email, role);
        assertNoErrorToast("Add " + email + " as " + role);
    }

    @When("I add the user found by searching their email {string} as {string}")
    public void i_add_the_user_found_by_email(String email, String role) {
        i_add_the_user_found_by_searching(email, email, role);
    }

    @When("I invite a new user {string} named {string} {string} as {string}")
    public void i_invite_a_new_user(String email, String firstName, String lastName, String role) {
        peoplePage.inviteUser(email, firstName, lastName, role);
        assertNoErrorToast("Invite " + email + " as " + role);
    }

    /** Re-reads the list from the server, so the checks after it see what was saved. */
    @When("I reload the People tab of the {string} course")
    public void i_reload_the_people_tab(String courseTitle) {
        driver.navigate().refresh();
        peoplePage.openFor(courseTitle);
    }

    @When("I remove {string} from the course with the Actions menu")
    public void i_remove_from_the_course(String email) {
        peoplePage.remove(email);
        assertNoErrorToast("Remove " + email);
    }

    @Then("{string} is listed on the course as {string}")
    public void is_listed_on_the_course_as(String email, String role) {
        String actual = peoplePage.roleOf(email);
        CheckLog.check(email + " listed as " + role,
                actual.isEmpty() ? "not on the course" : email + " listed as " + actual,
                actual.equalsIgnoreCase(role));
        assertThat(actual)
                .withFailMessage("%s is on the course as '%s', expected '%s'",
                        email, actual.isEmpty() ? "nothing (not listed)" : actual, role)
                .isEqualToIgnoringCase(role);
    }

    @Then("{string} is no longer listed on the course")
    public void is_no_longer_listed(String email) {
        boolean gone = peoplePage.waitUntilNotListed(email);
        CheckLog.check(email + " not on the course",
                gone ? email + " not on the course" : email + " still listed as " + peoplePage.roleOf(email),
                gone);
        assertThat(gone)
                .withFailMessage("%s is still on the course after removal", email)
                .isTrue();
    }

    private void assertNoErrorToast(String action) {
        boolean error = peoplePage.hasError(Duration.ofSeconds(3));
        CheckLog.check(action + " is accepted", error ? "error toast shown" : "accepted, no error", !error);
        assertThat(error)
                .withFailMessage("%s showed an error toast", action)
                .isFalse();
    }
}
