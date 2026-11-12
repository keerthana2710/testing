package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.AssistantPanel;
import com.lxp.pages.BlankCoursePage;
import com.lxp.pages.ModulePlanPage;
import com.lxp.pages.RolesPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI Assistant changing a course's content: a module it plans, approved
 * with Approve Plan, and an edit instruction that renames another module
 * already in the same course.
 *
 * Runs on a course taken from the teacher's own course list (nothing is
 * hard-coded), and finds the module by its id, so modules left by earlier
 * days' runs never get in the way.
 */
public class ChatAssistantContentSteps {

    private final WebDriver driver = DriverFactory.get();
    private final AssistantPanel assistant = new AssistantPanel(driver);
    private final ModulePlanPage plan = new ModulePlanPage(driver);

    /** The module the assistant planned: its id and name. */
    private String moduleId;
    private String module;

    /** The existing module that is renamed: its id and name. */
    private String renamedId;
    private String renamed;

    /** The first course in the teacher's own course list (a teacher sees only the courses they teach). */
    @When("I open one of my courses")
    public void i_open_one_of_my_courses() {
        String card = new RolesPage(driver).openFirstCourse();
        BlankCoursePage course = new BlankCoursePage(driver);
        String title = card.isEmpty() ? "" : course.courseTitle();
        course.closeOpenWindow();
        boolean opened = !title.isEmpty();
        CheckLog.check("one of the teacher's courses opens", opened ? "\"" + title + "\"" : "the course list is empty", opened);
        assertThat(opened).withFailMessage("The teacher has no course to open").isTrue();
    }

    /** Topics for the new module; each run takes one the course does not have yet. */
    private static final List<String[]> TOPICS = List.of(
            new String[] {"Python decorators", "decorator"},
            new String[] {"Python generators", "generator"},
            new String[] {"Python context managers", "context manager"},
            new String[] {"Python list comprehensions", "comprehension"},
            new String[] {"Python error handling", "error"},
            new String[] {"Python unit testing", "test"},
            new String[] {"Python file handling", "file"},
            new String[] {"Python dataclasses", "dataclass"},
            new String[] {"Python async programming", "async"},
            new String[] {"Python regular expressions", "regular expression"});

    /**
     * "Add a new module on &lt;topic&gt;": the topic changes from run to run
     * (starting at the day of the year) and is one no module in the course is
     * already about, so the assistant makes a new module, not a variation of an old one.
     */
    @When("I ask the AI Assistant to add a new module on a topic the course does not have yet")
    public void i_ask_the_ai_assistant_to_add_a_new_module_on_a_new_topic() {
        List<String> names = plan.moduleNames().stream().map(String::toLowerCase).toList();
        int start = java.time.LocalDate.now().getDayOfYear();
        String topic = TOPICS.get(start % TOPICS.size())[0];
        for (int i = 0; i < TOPICS.size(); i++) {
            String[] t = TOPICS.get((start + i) % TOPICS.size());
            if (names.stream().noneMatch(n -> n.contains(t[1]))) {
                topic = t[0];
                break;
            }
        }
        String question = "Add a new module on " + topic;
        String answer = assistant.ask(question);
        CheckLog.check("a finished answer to \"" + question + "\"", answer, !answer.isBlank());
    }

    @When("I open the module plan from the assistant's answer")
    public void i_open_the_module_plan_from_the_assistants_answer() {
        boolean offered = plan.hasViewPlanButton();
        CheckLog.check("the answer has a View Module Plan button", offered ? "shown" : "no button", offered);
        assertThat(offered).withFailMessage("No View Module Plan button in the assistant's answer").isTrue();
        plan.openFromAssistant();
        moduleId = plan.moduleId();
        module = plan.planTitle();
    }

    @Then("the module plan is waiting for approval")
    public void the_module_plan_is_waiting_for_approval() {
        int items = plan.plannedItems();
        boolean approve = plan.approveOffered();
        CheckLog.check("the plan for \"" + module + "\" lists its items", "Module Items (" + items + ")", items > 0);
        CheckLog.check("Approve Plan is offered", approve ? "shown" : "not shown", approve);
        assertThat(approve).withFailMessage("No Approve Plan on the plan for " + module).isTrue();
    }

    @When("I approve the module plan")
    public void i_approve_the_module_plan() {
        String problem = plan.approve();
        CheckLog.check("Approve Plan is accepted", problem.isEmpty() ? "approved" : problem, problem.isEmpty());
        assertThat(problem).withFailMessage("Approve Plan failed: " + problem).isEmpty();
    }

    /**
     * Update existing items: another module already in the same course (not
     * the one just planned, whose content is still being generated) is
     * renamed with an edit instruction in the chat. If the assistant asks to
     * confirm the change (its "Rename module" card, or Apply / Approve /
     * Accept / Confirm), that button is clicked to accept it. {now} in the name makes it new
     * every day.
     */
    @When("I ask the AI Assistant to rename another module in the course to {string}")
    public void i_ask_the_ai_assistant_to_rename_another_module_in_the_course_to(String name) {
        plan.openCourseOutline();
        String[] other = plan.otherModule(moduleId);
        if (other == null) {
            // the course has only the module just planned: rename that one instead
            CheckLog.review("module to rename", "the course has no other module, so \"" + module + "\" is renamed");
            other = new String[] {moduleId, plan.outlineName(moduleId)};
        }
        renamedId = other[0];
        renamed = other[1];
        assistant.openWithIcon();
        String answer = assistant.ask("Rename the module \"" + renamed + "\" to \"" + RunClock.withNow(name) + "\"");
        CheckLog.check("the assistant answers the edit instruction", answer, !answer.isBlank());
        // the assistant asks to confirm with a card, e.g. "Rename module ... [Rename] [Cancel]": accept it
        assistant.confirmButton().ifPresent(button -> {
            String label = button.getText().replaceAll("\\s+", " ").strip();
            button.click();
            CheckLog.check("the change is accepted in the chat", "clicked \"" + label + "\"", true);
        });
    }

    @Then("the course outline shows that module as {string}")
    public void the_course_outline_shows_that_module_as(String name) {
        String expected = RunClock.withNow(name);
        // the rename is saved just after Rename is clicked: look again for up to a minute
        String now = "";
        for (int tries = 0; tries < 6 && !now.equals(expected); tries++) {
            if (tries > 0) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            plan.openCourseOutline();
            now = plan.outlineName(renamedId);
        }
        boolean updated = now.equals(expected);
        CheckLog.check("\"" + renamed + "\" is renamed to \"" + expected + "\" in the course",
                now.isEmpty() ? "the module is not in the course outline" : "it is called \"" + now + "\"", updated);
        assertThat(updated).withFailMessage("\"" + renamed + "\" was not renamed to " + expected + "; it is \"" + now + "\"").isTrue();
    }
}
