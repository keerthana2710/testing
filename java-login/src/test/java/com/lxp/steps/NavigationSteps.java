package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.NavigationPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

/**
 * Moving across the tabs and pages after sign-in. The checks are soft: each
 * page is recorded, the run carries on, and Hooks fails the scenario at the
 * end if any page did not show.
 */
public class NavigationSteps {

    private final NavigationPage page = new NavigationPage(DriverFactory.get());

    @When("I open the {string} tab")
    public void i_open_the_tab(String tab) {
        page.openTab(tab);
    }

    @When("I open the Templates page")
    public void i_open_the_templates_page() {
        page.openTemplates();
    }

    @Then("the {string} page is shown")
    public void the_page_is_shown(String name) {
        boolean shown = page.isPageShown(name);
        CheckLog.soft(name + " page is shown", shown ? "shown at " + page.currentPath() : "not shown, at " + page.currentPath(), shown);
    }

    @When("I open the Create Course window")
    public void i_open_the_create_course_window() {
        page.openCreateCourse();
    }

    @Then("the Create Course window offers {string}, {string}, {string} and {string}")
    public void the_create_course_window_offers(String a, String b, String c, String d) {
        for (String option : List.of(a, b, c, d)) {
            boolean shown = page.offers(option);
            CheckLog.soft("Create Course offers " + option, shown ? "shown" : "not shown", shown);
        }
    }

    @Then("each way to create a course opens its panel")
    public void each_way_to_create_a_course_opens_its_panel() {
        for (String way : page.ways()) {
            boolean shown = page.opensPanel(way);
            CheckLog.soft("Create Course → " + way + " opens", shown ? "panel shown" : "panel not shown", shown);
        }
    }

    @When("I close the Create Course window")
    public void i_close_the_create_course_window() {
        page.closeCreateCourse();
    }
}
