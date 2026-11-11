package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.AssistantPanel;
import com.lxp.pages.BlankCoursePage;
import com.lxp.pages.RolesPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The teacher following the AI Assistant: a blank course from its "Blank
 * Course" button, and the New Program button its program steps point to.
 */
public class TeacherAssistantSteps {

    private final WebDriver driver = DriverFactory.get();
    private final AssistantPanel assistant = new AssistantPanel(driver);
    private final BlankCoursePage blank = new BlankCoursePage(driver);

    /** The course this run created: its address and the title the AI gave it. */
    private String coursePath;
    private String courseTitle;

    /** The button in the assistant's latest answer, e.g. its "Blank Course" card. */
    @When("I click the button in the assistant's answer")
    public void i_click_the_button_in_the_assistants_answer() {
        List<WebElement> actions = assistant.replyActions();
        String reply = driver.findElements(By.cssSelector("[data-message-role='assistant']")).stream()
                .reduce((a, b) -> b).map(e -> e.getText().replaceAll("\\s+", " ").strip()).orElse("");
        boolean found = !actions.isEmpty();
        CheckLog.check("the answer has a button to create the course",
                found ? "\"" + actions.get(0).getText().replaceAll("\\s+", " ").strip() + "\"" : "no button; it said: " + reply, found);
        // at the plan's course limit the assistant explains the limit instead
        assertThat(found).withFailMessage("No button in the answer: " + reply).isTrue();
        actions.get(0).click();
    }

    @Then("the Create Blank Course window is open")
    public void the_create_blank_course_window_is_open() {
        boolean open = blank.isBlankWindowOpen();
        CheckLog.check("the Create Blank Course window is open", open ? "\"What is your course about?\" shown" : "not open", open);
        assertThat(open).withFailMessage("The Create Blank Course window did not open").isTrue();
    }

    @When("I leave the course description blank and click Create Course")
    public void i_leave_the_course_description_blank_and_click_create_course() {
        blank.describe("");
        blank.tryCreate();
    }

    /**
     * The app keeps Create Course disabled while the field is empty, rather
     * than showing a message; either one counts as "required".
     */
    @Then("the course description is required and no course is created")
    public void the_course_description_is_required_and_no_course_is_created() {
        String message = blank.requiredMessage();
        boolean disabled = !blank.createEnabled();
        boolean blocked = disabled || !message.isBlank();
        boolean created = blank.onCoursePage();
        CheckLog.check("the course description is required",
                (disabled ? "Create Course stays disabled" : "Create Course is enabled")
                        + (message.isBlank() ? "; no message shown" : "; says \"" + message + "\""), blocked);
        CheckLog.check("no course is created", created ? "a course page opened" : "still in the window", !created);
        assertThat(blocked).withFailMessage("An empty course description was accepted").isTrue();
        assertThat(created).withFailMessage("A course was created without a description").isFalse();
    }

    @When("I describe the course as {string}")
    public void i_describe_the_course_as(String about) {
        blank.describe(about);
    }

    @When("I create the blank course")
    public void i_create_the_blank_course() {
        String problem = blank.create();
        boolean created = problem.isEmpty();
        String said = blank.createdMessage();
        CheckLog.check("the blank course is created",
                created ? (said.isEmpty() ? "created, on " + driver.getCurrentUrl() : said) : problem, created);
        assertThat(created).withFailMessage("The course was not created: " + problem).isTrue();
    }

    /** The AI names the course from the description, so its title is read from the page. */
    @When("I click Open Course in the assistant")
    public void i_click_open_course_in_the_assistant() {
        blank.openCreatedCourse();
    }

    @Then("the new course's dashboard opens")
    public void the_new_courses_dashboard_opens() {
        coursePath = blank.coursePath();
        courseTitle = blank.courseTitle();
        boolean shown = blank.onCoursePage() && !courseTitle.isEmpty();
        CheckLog.check("the new course's dashboard opens",
                shown ? "\"" + courseTitle + "\" at " + coursePath : "no course header at " + driver.getCurrentUrl(), shown);
        assertThat(shown).withFailMessage("The new course's page did not open").isTrue();
        blank.closeOpenWindow();
    }

    @Then("the new course's card is in My Courses")
    public void the_new_courses_card_is_in_my_courses() {
        boolean listed = new RolesPage(driver).isListedAt(coursePath);
        CheckLog.check("\"" + courseTitle + "\" is in My Courses", listed ? "listed" : "not listed", listed);
        assertThat(listed).withFailMessage(courseTitle + " is not in My Courses").isTrue();
    }

    @Then("New Program is offered")
    public void new_program_is_offered() {
        By newProgram = By.xpath("//main//button[contains(normalize-space(.), 'New Program')]");
        boolean shown;
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver, Duration.ofSeconds(30))
                    .until(d -> !d.findElements(newProgram).isEmpty() && d.findElement(newProgram).isDisplayed());
            shown = true;
        } catch (org.openqa.selenium.TimeoutException e) {
            shown = false;
        }
        CheckLog.check("New Program is offered", shown ? "shown" : "not shown", shown);
        assertThat(shown).withFailMessage("No New Program button on Programs").isTrue();
    }
}
