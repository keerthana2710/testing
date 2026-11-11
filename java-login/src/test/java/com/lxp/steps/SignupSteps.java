package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.SignupPage;
import com.lxp.pages.WelcomePage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Forgot password from the welcome page's login modal, then the signup form. */
public class SignupSteps {

    // What the app says once a reset link or a signup request has been sent.
    // Not "reset link": the form's own "Send Reset Link" button would match it.
    private static final Pattern RESET_SENT = Pattern.compile("(?i)check your email");
    private static final Pattern REQUEST_SENT = Pattern.compile("(?i)request sent");

    private final WebDriver driver = DriverFactory.get();
    private final WelcomePage welcomePage = new WelcomePage(driver);
    private final SignupPage signupPage = new SignupPage(driver);

    @Given("I am on the welcome page")
    public void i_am_on_the_welcome_page() {
        welcomePage.open();
        boolean shown = welcomePage.isDisplayed();
        CheckLog.check("welcome page with a Log in button", (shown ? "shown at " : "not shown, at ") + driver.getCurrentUrl(), shown);
        assertThat(shown).withFailMessage("Expected the welcome page").isTrue();
    }

    @When("I click Log in")
    public void i_click_log_in() {
        welcomePage.clickLogIn();
        boolean shown = welcomePage.isLoginModalDisplayed();
        CheckLog.check("login modal opens", shown ? "opened" : "not shown", shown);
        assertThat(shown).withFailMessage("Expected the login modal").isTrue();
    }

    @When("I click Forgot password")
    public void i_click_forgot_password() {
        welcomePage.clickForgotPassword();
        boolean shown = welcomePage.isResetFormDisplayed();
        CheckLog.check("reset password form opens", shown ? "opened" : "not shown", shown);
        assertThat(shown).withFailMessage("Expected the reset password form").isTrue();
    }

    @When("I request a password reset for {string}")
    public void i_request_a_password_reset_for(String email) {
        welcomePage.requestPasswordReset(email);
    }

    @Then("the reset link is reported as sent")
    public void the_reset_link_is_reported_as_sent() {
        String text = waitForText(welcomePage::dialogText, RESET_SENT);
        boolean sent = RESET_SENT.matcher(text).find();
        CheckLog.check("reset link sent confirmation", oneLine(text), sent);
        assertThat(sent).withFailMessage("No reset confirmation, modal says: " + text).isTrue();
    }

    @When("I click Sign up")
    public void i_click_sign_up() {
        welcomePage.clickSignUp();
        boolean shown = signupPage.isDisplayed();
        CheckLog.check("signup form opens", shown ? "on /signup" : driver.getCurrentUrl(), shown);
        assertThat(shown).withFailMessage("Expected the signup form").isTrue();
    }

    @When("I enter first name {string}, last name {string} and email {string}")
    public void i_enter_signup_details(String firstName, String lastName, String email) {
        signupPage.fill(firstName, lastName, email);
    }

    @When("I choose {string} for what brings me here")
    public void i_choose_interest(String interest) {
        signupPage.chooseInterest(interest);
        boolean selected = signupPage.isInterestSelected(interest);
        CheckLog.check(interest + " is selected", selected ? "selected" : "not selected", selected);
        assertThat(selected).withFailMessage(interest + " was not selected").isTrue();
    }

    @When("I enter the course I want to create {string}")
    public void i_enter_course_interest(String course) {
        signupPage.enterCourseInterest(course);
    }

    @When("I submit the signup request")
    public void i_submit_the_signup_request() {
        String honeypot = signupPage.honeypotValue();
        CheckLog.check("hidden website field left empty", honeypot.isEmpty() ? "empty" : honeypot, honeypot.isEmpty());
        signupPage.submit();
    }

    @Then("the signup request is accepted")
    public void the_signup_request_is_accepted() {
        String text = waitForText(signupPage::pageText, REQUEST_SENT);
        boolean accepted = REQUEST_SENT.matcher(text).find();
        CheckLog.check("signup request confirmation", oneLine(text), accepted);
        assertThat(accepted).withFailMessage("No signup confirmation, page says: " + text).isTrue();
    }

    /**
     * Polls until the text matches, or gives up after ~30s and returns the last
     * text seen. The signup API alone takes 5-10s to answer on staging.
     */
    private static String waitForText(java.util.function.Supplier<String> text, Pattern expected) {
        String last = "";
        for (int i = 0; i < 60; i++) {
            try {
                last = text.get();
            } catch (RuntimeException ignored) {
                // the modal or form re-renders while it swaps to the confirmation
            }
            if (expected.matcher(last).find()) {
                return last;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return last;
    }

    private static String oneLine(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() > 120 ? flat.substring(0, 120) + "..." : flat;
    }
}
