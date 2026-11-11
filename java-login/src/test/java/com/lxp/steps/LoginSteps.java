package com.lxp.steps;

import com.lxp.config.OtpPrompt;
import com.lxp.config.Settings;
import com.lxp.driver.DriverFactory;
import com.lxp.pages.HomePage;
import com.lxp.pages.LoginPage;
import com.lxp.pages.MfaPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.opentest4j.TestAbortedException;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assertions live here; no raw locators do -- that is what keeps this a
 * Page Object Model suite.
 */
public class LoginSteps {

    private final WebDriver driver = DriverFactory.get();
    private final LoginPage loginPage = new LoginPage(driver);
    private final MfaPage mfaPage = new MfaPage(driver);
    private final HomePage homePage = new HomePage(driver);

    @Given("credentials are configured")
    public void credentials_are_configured() {
        if (!Settings.hasCredentials()) {
            // Cucumber maps TestAbortedException to SKIPPED, so an unconfigured
            // .env leaves the scenario skipped rather than failed.
            throw new TestAbortedException("Set LXP_EMAIL and LXP_PASSWORD in .env");
        }
    }

    @When("I sign in with my email and password")
    public void i_sign_in_with_my_email_and_password() {
        loginPage.login(Settings.EMAIL, Settings.password());
    }

    @Then("no login error is shown")
    public void no_login_error_is_shown() {
        boolean failed = loginPage.hasError(Duration.ofSeconds(6));
        assertThat(failed)
                .withFailMessage(() -> "Credentials rejected for " + Settings.EMAIL + " at " + driver.getCurrentUrl()
                        + ": " + loginPage.errorTitle() + " / " + loginPage.errorMessage())
                .isFalse();
    }

    @Then("the 6-digit MFA prompt is displayed")
    public void the_mfa_prompt_is_displayed() {
        assertThat(mfaPage.isDisplayed())
                .withFailMessage("Expected the 6-digit MFA prompt")
                .isTrue();
    }

    @When("I enter the verification code")
    public void i_enter_the_verification_code() {
        mfaPage.enterVerificationCode(OtpPrompt.ask());
    }

    @When("I click Verify Code")
    public void i_click_verify_code() {
        mfaPage.clickVerifyCode();
    }

    @Then("I am redirected to the home page")
    public void i_am_redirected_to_the_home_page() {
        assertThat(homePage.isDisplayed())
                .withFailMessage("Expected to be redirected to /home")
                .isTrue();
    }
}
