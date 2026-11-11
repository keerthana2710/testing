package com.lxp.steps;

import com.lxp.config.Settings;
import com.lxp.driver.DriverFactory;
import com.lxp.pages.ProfilePage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The profile page: who is signed in, time zone, and changing the password. */
public class ProfileSteps {

    private final ProfilePage profile = new ProfilePage(DriverFactory.get());

    /** Read from the header before leaving the home page. */
    private String signedInName;

    /** The signed-in user's role from the app's session, not from the feature. */
    private String signedInRole;

    @When("I open my profile from the account menu")
    public void i_open_my_profile_from_the_account_menu() {
        signedInName = profile.signedInName();
        signedInRole = profile.signedInRole();
        profile.openFromAccountMenu();
    }

    @Then("the profile shows the signed-in user's name")
    public void the_profile_shows_the_signed_in_users_name() {
        String name = profile.name();
        CheckLog.check("Full Name is the signed-in user, " + signedInName, name, name.equals(signedInName));
        assertThat(name).isEqualTo(signedInName);
    }

    @Then("the profile shows my email address")
    public void the_profile_shows_my_email_address() {
        String email = profile.email();
        CheckLog.check("Email Address is " + Settings.EMAIL, email, email.equalsIgnoreCase(Settings.EMAIL));
        assertThat(email).isEqualToIgnoringCase(Settings.EMAIL);
    }

    @Then("the profile shows the signed-in user's role")
    public void the_profile_shows_the_signed_in_users_role() {
        assertThat(signedInRole).withFailMessage("Could not read the signed-in user's role from the session").isNotBlank();
        String actual = profile.role();
        CheckLog.check("Role is the signed-in user's, " + signedInRole, actual, actual.equalsIgnoreCase(signedInRole));
        assertThat(actual).isEqualToIgnoringCase(signedInRole);
    }

    @Then("the profile shows the role {string}")
    public void the_profile_shows_the_role(String role) {
        String actual = profile.role();
        CheckLog.check("Role is " + role, actual, actual.equalsIgnoreCase(role));
        assertThat(actual).isEqualToIgnoringCase(role);
    }

    @When("I change the time zone to {string}")
    public void i_change_the_time_zone_to(String zone) {
        Optional<ProfilePage.Toast> toast = profile.chooseTimeZone(zone);
        boolean error = toast.map(ProfilePage.Toast::error).orElse(false);
        CheckLog.check("time zone change to " + zone + " is accepted",
                toast.map(ProfilePage.Toast::text).orElse("no message shown"), !error);
        assertThat(error)
                .withFailMessage("Changing the time zone failed: %s", toast.map(ProfilePage.Toast::text).orElse(""))
                .isFalse();
    }

    @When("I reload the profile page")
    public void i_reload_the_profile_page() {
        profile.reload();
    }

    @Then("the time zone is {string}")
    public void the_time_zone_is(String zone) {
        String actual = profile.timeZone();
        CheckLog.check("Time Zone is " + zone, actual, actual.equals(zone));
        assertThat(actual).isEqualTo(zone);
    }

    @When("I set the color mode to {string}")
    public void i_set_the_color_mode_to(String mode) {
        profile.chooseColorMode(mode);
    }

    @When("I set the display size to {string}")
    public void i_set_the_display_size_to(String size) {
        profile.chooseDisplaySize(size);
    }

    @Then("the color mode {string} is selected")
    public void the_color_mode_is_selected(String mode) {
        String selected = profile.selectedColorMode();
        CheckLog.check("Color Mode " + mode + " is selected", selected, selected.equals(mode));
        assertThat(selected).isEqualTo(mode);
    }

    /** "light" or "dark": what the page actually looks like, not just the button. */
    @Then("the color mode {string} is selected and the page is {word}")
    public void the_color_mode_is_selected_and_the_page_is(String mode, String look) {
        the_color_mode_is_selected(mode);
        String actual = profile.isDarkThemeApplied() ? "dark" : "light";
        CheckLog.check("the page is shown " + look, "the page is shown " + actual, actual.equals(look));
        assertThat(actual).isEqualTo(look);
    }

    @Then("the display size {string} is selected and the base text size is {string}")
    public void the_display_size_is_selected_and_the_base_text_size_is(String size, String px) {
        String selected = profile.selectedDisplaySize();
        CheckLog.check("Display Size " + size + " is selected", selected, selected.equals(size));
        String actual = profile.baseTextSize();
        CheckLog.check("base text size is " + px, actual, actual.equals(px));
        assertThat(selected).isEqualTo(size);
        assertThat(actual).isEqualTo(px);
    }

    /**
     * Changes the account's password from the one in .env, and on success
     * writes the new one back to .env so later sign-ins use it. When .env
     * already holds the new password there is nothing to change.
     */
    @When("I change my password to {string}")
    public void i_change_my_password_to(String newPassword) {
        // an earlier run already changed it; the app rejects "changing" to the same password
        if (Settings.password().equals(newPassword)) {
            CheckLog.check("password changed to the new one",
                    "already the new one in .env (changed on an earlier run); sign-in below confirms it", true);
            return;
        }
        Optional<ProfilePage.Toast> toast = profile.changePassword(Settings.password(), newPassword);
        boolean accepted = toast.isPresent() && !toast.get().error();
        CheckLog.check("password change is accepted",
                toast.map(ProfilePage.Toast::text).orElse("no message shown"), accepted);
        assertThat(accepted)
                .withFailMessage("Password change was not confirmed: %s",
                        toast.map(ProfilePage.Toast::text).orElse("no message shown"))
                .isTrue();

        Settings.changePassword(newPassword);
        CheckLog.check("LXP_PASSWORD in .env holds the new password", "updated", true);
    }

    @When("I sign out")
    public void i_sign_out() {
        profile.signOut();
    }

    @Then("I am signed out")
    public void i_am_signed_out() {
        boolean out = profile.isSignedOut();
        String url = DriverFactory.get().getCurrentUrl();
        CheckLog.check("signed out, back on the welcome or login page", url, out);
        assertThat(out).withFailMessage("Expected to be signed out, still on " + url).isTrue();
    }

    @When("I close the browser")
    public void i_close_the_browser() {
        DriverFactory.quit();
        CheckLog.check("browser closed", DriverFactory.isRunning() ? "still open" : "closed", !DriverFactory.isRunning());
    }
}
