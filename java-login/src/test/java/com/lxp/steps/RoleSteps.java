package com.lxp.steps;

import com.lxp.config.OtpPrompt;
import com.lxp.config.Settings;
import com.lxp.driver.DriverFactory;
import com.lxp.pages.HomePage;
import com.lxp.pages.LoginPage;
import com.lxp.pages.MfaPage;
import com.lxp.pages.ProfilePage;
import com.lxp.pages.RolesPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.opentest4j.TestAbortedException;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Role checks: the student, teacher and admin accounts from .env
 * (LXP_STUDENT_*, LXP_TEACHER_*, LXP_ADMIN_*), and what each may reach.
 */
public class RoleSteps {

    private final WebDriver driver = DriverFactory.get();
    private final RolesPage page = new RolesPage(driver);

    @Given("the {string} account is configured")
    public void the_account_is_configured(String role) {
        if (!Settings.account(role).isConfigured()) {
            String key = "LXP_" + role.toUpperCase();
            throw new TestAbortedException("Set " + key + "_EMAIL and " + key + "_PASSWORD in .env");
        }
    }

    @Given("I am signed in as the {string}")
    public void i_am_signed_in_as(String role) {
        Settings.Account account = Settings.account(role);
        LoginPage login = new LoginPage(driver).login(account.email(), account.password());
        boolean rejected = login.hasError(Duration.ofSeconds(6));
        CheckLog.check(role + " " + account.email() + " is accepted",
                rejected ? login.errorTitle() + " / " + login.errorMessage() : "accepted", !rejected);
        assertThat(rejected).withFailMessage("Sign-in rejected for " + account.email()).isFalse();

        MfaPage mfa = new MfaPage(driver);
        assertThat(mfa.isDisplayed()).withFailMessage("Expected the 6-digit MFA prompt").isTrue();
        System.out.println("\n[roles] signing in as the " + role + " (" + account.email() + ")");
        mfa.enterVerificationCode(OtpPrompt.ask()).clickVerifyCode();
        boolean home = new HomePage(driver).isDisplayed();
        CheckLog.check(role + " is signed in and on the home page", home ? "on /home" : driver.getCurrentUrl(), home);
        assertThat(home).withFailMessage("Expected to be redirected to /home").isTrue();
    }

    @Then("my role in the app is {string}")
    public void my_role_in_the_app_is(String expected) {
        String role = new ProfilePage(driver).signedInRole();
        CheckLog.check("the app's role is " + expected, role, expected.equalsIgnoreCase(role));
        assertThat(role).isEqualToIgnoringCase(expected);
    }

    @When("I open the course list")
    public void i_open_the_course_list() {
        page.openCourseList();
    }

    @Then("I am offered Create Course")
    public void i_am_offered_create_course() {
        boolean offered = page.offersCreateCourse();
        CheckLog.check("Create Course is offered", offered ? "shown" : "not shown", offered);
        assertThat(offered).withFailMessage("No Create Course button on /courses").isTrue();
    }

    @Then("I am not offered Create Course")
    public void i_am_not_offered_create_course() {
        boolean offered = page.offersCreateCourse();
        CheckLog.check("Create Course is not offered", offered ? "shown" : "not shown", !offered);
        assertThat(offered).withFailMessage("Create Course is offered to this role").isFalse();
    }

    @Then("I can open Course Templates and create a template from IMSCC")
    public void course_templates_opens_with_create_template() {
        boolean opened = page.openTemplates();
        boolean create = opened && page.offersCreateTemplate();
        CheckLog.check("Course Templates opens", opened ? "on " + page.path() : "sent to " + page.path(), opened);
        CheckLog.check("Create Template from IMSCC is offered", create ? "shown" : "not shown", create);
        assertThat(opened).withFailMessage("Course Templates did not open, ended on " + page.path()).isTrue();
        assertThat(create).withFailMessage("No Create Template from IMSCC button").isTrue();
    }

    @Then("I cannot open Course Templates")
    public void i_am_sent_away_from_course_templates() {
        boolean opened = page.openTemplates();
        CheckLog.check("Course Templates is refused", opened ? "opened /templates" : "sent to " + page.path(), !opened);
        assertThat(opened).withFailMessage("This role can open Course Templates").isFalse();
    }

    @When("I open the first course I teach")
    public void i_open_the_first_course_i_teach() {
        String course = page.openFirstCourse();
        boolean opened = !course.isEmpty();
        CheckLog.check("a course this teacher teaches opens", opened ? course : "no course in the list", opened);
        assertThat(opened).withFailMessage("This account teaches no course yet: create one, or add them to a"
                + " course as Teacher, so there is a course to grade in").isTrue();
    }

    @Then("I can open the course's Grades")
    public void i_can_open_the_courses_grades() {
        boolean offered = page.offersGrades();
        CheckLog.check("the course offers Grades", offered ? "shown" : "not shown", offered);
        assertThat(offered).withFailMessage("No Grades in this course").isTrue();
        String shown = page.openGrades();
        boolean opened = !shown.isBlank() && page.gradesShown();
        CheckLog.check("Grades opens", shown.isBlank() ? "nothing opened" : shown, opened);
        assertThat(opened).withFailMessage("Grades did not open: " + shown).isTrue();
    }

    @Then("I cannot open the course {string} at {string}, so I cannot reach its Grades")
    public void i_cannot_open_the_course_at(String title, String address) {
        boolean opened = page.openCourseAt(address);
        boolean grades = opened && page.offersGrades();
        CheckLog.check("\"" + title + "\" is closed to this role", page.courseMessage(), !opened);
        CheckLog.check("\"" + title + "\" Grades is out of reach", grades ? "Grades shown" : "no Grades", !grades);
        assertThat(opened).withFailMessage(title + " opened for this role").isFalse();
        assertThat(grades).isFalse();
    }

    @Then("the course list has the {string} filter")
    public void the_course_list_has_the_filter(String filter) {
        page.openCourseList();
        boolean shown = page.offersAllCreatorsFilter();
        CheckLog.check("the \"" + filter + "\" filter is offered", shown ? "shown, " + page.listTotals() : "not shown", shown);
        assertThat(shown).withFailMessage("No \"" + filter + "\" filter on /courses").isTrue();
    }

    @Then("the course list has no {string} filter")
    public void the_course_list_has_no_filter(String filter) {
        page.openCourseList();
        boolean shown = page.offersAllCreatorsFilter();
        CheckLog.check("no \"" + filter + "\" filter", shown ? "shown" : "not shown, " + page.listTotals(), !shown);
        assertThat(shown).withFailMessage("This role has the \"" + filter + "\" filter").isFalse();
    }

    @Then("the course list shows courses taught by other people")
    public void the_course_list_shows_courses_taught_by_others() {
        page.openCourseList();
        Set<String> teachers = page.teachersShown();
        boolean many = teachers.size() >= 2;
        CheckLog.check("courses from more than one teacher are listed", "Taught by: " + teachers, many);
        assertThat(many).withFailMessage("Only these teachers are listed: " + teachers).isTrue();
    }

    /** A course from the admin's list taught only by someone else (not "Name +2", which may include the admin). */
    private RolesPage.Card othersCourse;

    @Then("the course list shows a course taught by someone else")
    public void the_course_list_shows_a_course_taught_by_someone_else() {
        String me = new ProfilePage(driver).signedInName();
        page.openCourseList();
        othersCourse = page.cardsShown().stream()
                .filter(c -> !c.taughtBy().isBlank() && !c.taughtBy().contains("+")
                        && !c.taughtBy().equalsIgnoreCase(me))
                .findFirst().orElse(null);
        boolean found = othersCourse != null;
        CheckLog.check("a course taught by someone other than " + me + " is listed",
                found ? "\"" + othersCourse.title() + "\", taught by " + othersCourse.taughtBy() : "none listed", found);
        assertThat(found).withFailMessage("No course taught by someone else is listed").isTrue();
    }

    @When("I open that course")
    public void i_open_that_course() {
        boolean opened = page.openCourseAt(othersCourse.path());
        CheckLog.check("\"" + othersCourse.title() + "\" (" + othersCourse.taughtBy() + "'s) opens",
                opened ? "opened " + othersCourse.path() : page.courseMessage(), opened);
        assertThat(opened).withFailMessage(othersCourse.title() + " did not open").isTrue();
    }

    @Then("every course in my list is one I teach")
    public void every_course_in_my_list_is_one_i_teach() {
        String me = new ProfilePage(driver).signedInName();
        page.openCourseList();
        java.util.List<RolesPage.Card> cards = page.cardsShown();
        java.util.List<String> others = cards.stream()
                .filter(c -> !c.taughtBy().toLowerCase().contains(me.toLowerCase()) && !c.taughtBy().contains("+"))
                .map(c -> c.title() + " (" + c.taughtBy() + ")").toList();
        boolean mine = others.isEmpty();
        CheckLog.check("every listed course is taught by " + me,
                mine ? cards.size() + " course(s), all taught by " + me : "also lists: " + others, mine);
        assertThat(mine).withFailMessage("Courses taught by others are listed: " + others).isTrue();
    }

    @Then("the course {string} with code {string} is in my course list")
    public void the_course_is_in_my_course_list(String title, String code) {
        boolean listed = page.isListed(title, code);
        CheckLog.check("\"" + title + "\" (" + code + ") is listed", listed ? "listed" : "not listed", listed);
        assertThat(listed).withFailMessage(title + " is not in the course list").isTrue();
    }

    @Then("the course {string} with code {string} is not in my course list")
    public void the_course_is_not_in_my_course_list(String title, String code) {
        boolean listed = page.isListed(title, code);
        CheckLog.check("\"" + title + "\" (" + code + ") is not listed", listed ? "listed" : "not listed", !listed);
        assertThat(listed).withFailMessage(title + " is in this role's course list").isFalse();
    }

    @Then("the profile shows the {string} account's email")
    public void the_profile_shows_the_accounts_email(String role) {
        String expected = Settings.account(role).email();
        String email = new ProfilePage(driver).email();
        CheckLog.check("Email Address is " + expected, email, email.equalsIgnoreCase(expected));
        assertThat(email).isEqualToIgnoringCase(expected);
    }
}
