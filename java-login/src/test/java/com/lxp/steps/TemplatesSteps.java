package com.lxp.steps;

import com.lxp.config.Settings;
import com.lxp.driver.DriverFactory;
import com.lxp.pages.TemplatesPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Course Templates: browse the paged list, create a template from an IMSCC,
 * create a course from it, edit it, and search and sort the list.
 *
 * {now} in a feature value is the run's day and time (e.g. 032205), so every
 * run makes a template and course of its own.
 */
public class TemplatesSteps {

    private final TemplatesPage page = new TemplatesPage(DriverFactory.get());
    private final String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddHHmm"));

    /** The template this run created, under its current name. */
    private String templateName;

    @When("I scroll through the templates list")
    public void i_scroll_through_the_templates_list() {
        page.open();
        page.scrollToBottom();
    }

    @Then("templates are visible")
    public void templates_are_visible() {
        List<String> firstPage = page.names();
        boolean shown = page.allCardsShown();
        CheckLog.check("template cards are shown after scrolling", firstPage.size() + " cards, first: "
                + (firstPage.isEmpty() ? "none" : firstPage.get(0)), shown);
        assertThat(shown).withFailMessage("No template cards are shown").isTrue();

        // 12 to a page: the next page must bring a different set
        if (page.hasNextPage()) {
            page.nextPage();
            List<String> secondPage = page.names();
            boolean different = !secondPage.isEmpty() && !secondPage.equals(firstPage);
            CheckLog.check("the next page shows other templates", secondPage.size() + " cards, first: "
                    + (secondPage.isEmpty() ? "none" : secondPage.get(0)), different);
            assertThat(different).withFailMessage("Page 2 shows the same templates as page 1").isTrue();
        }
    }

    @When("I create a template from the downloaded .imscc named {string} by {string} with Canvas link {string} in {string} mode")
    public void i_create_a_template(String name, String author, String canvasLink, String mode) {
        templateName = name.replace("{now}", now);
        page.open();
        boolean started = page.createFromImscc(downloadedImscc(), templateName, author, canvasLink, mode);
        String error = page.errorText();
        CheckLog.check("template \"" + templateName + "\" starts processing", started ? "Processing Started" : error, started);
        assertThat(started).withFailMessage("The template did not start: " + error).isTrue();
    }

    /** A template takes about 10-12 minutes to generate on staging; waits up to 30. */
    @When("I wait for the template to finish generating")
    public void i_wait_for_the_template_to_finish_generating() {
        long started = System.currentTimeMillis();
        boolean done = page.waitUntilGenerated(templateName, progress -> System.out.println(
                "  \"" + templateName + "\" " + progress + " after ~" + (System.currentTimeMillis() - started) / 60_000 + " min"));
        long minutes = (System.currentTimeMillis() - started) / 60_000;
        CheckLog.check("\"" + templateName + "\" finishes generating",
                done ? "generated after ~" + minutes + " min" : "still not generated after " + minutes + " min", done);
        assertThat(done).withFailMessage(templateName + " did not finish generating").isTrue();
    }

    @When("I return to the templates list")
    public void i_return_to_the_templates_list() {
        page.open();
    }

    @Then("the new template is visible")
    public void the_new_template_is_visible() {
        page.search(templateName);
        boolean listed = page.isListed(templateName);
        CheckLog.check("\"" + templateName + "\" is listed", page.names().toString(), listed);
        assertThat(listed).withFailMessage(templateName + " was not listed").isTrue();
    }

    @When("I open the new template")
    public void i_open_the_new_template() {
        page.openTemplate(templateName);
        String preview = page.previewText();
        boolean named = preview.contains(templateName);
        CheckLog.check("the preview is of \"" + templateName + "\"", preview.length() > 120 ? preview.substring(0, 120) + "..." : preview, named);
        assertThat(named).withFailMessage("The preview is not of " + templateName).isTrue();
    }

    @When("I click Create Course from the template with title {string} and code {string}")
    public void i_create_a_course_from_the_template(String title, String code) {
        String courseTitle = title.replace("{now}", now);
        String courseCode = code + "-" + now;
        String url = page.createCourse(courseTitle, courseCode);
        boolean shown = page.courseHeaderShows(courseTitle);
        CheckLog.check("new course \"" + courseTitle + "\" (" + courseCode + ") is created", shown ? url : "not shown, at " + url, shown);
        assertThat(shown).withFailMessage("The course created from the template did not open").isTrue();
    }

    @When("I edit the new template's name to {string}, author to {string} and Canvas link to {string}")
    public void i_edit_the_new_template(String name, String author, String canvasLink) {
        String newName = name.replace("{now}", now);
        page.openTemplate(templateName);
        page.edit(newName, author, canvasLink);
        String error = page.errorText();
        CheckLog.check("Save Changes is accepted", error.isEmpty() ? "saved" : error, error.isEmpty());
        assertThat(error).withFailMessage("Saving the template failed: " + error).isEmpty();
        templateName = newName;
    }

    @Then("the template shows name {string}, author {string} and Canvas link {string}")
    public void the_template_shows(String name, String author, String canvasLink) {
        String expectedName = name.replace("{now}", now);
        // read it back from a fresh list, not from the window just saved
        page.closeDialogs();
        page.open();
        page.openTemplate(expectedName);
        String[] values = page.editValues();
        CheckLog.soft("Template Name is " + expectedName, values[0], values[0].equals(expectedName));
        CheckLog.soft("Author Name is " + author, values[1], values[1].equals(author));
        CheckLog.soft("Canvas Link is " + canvasLink, values[2], values[2].equals(canvasLink));
        page.closeDialogs();
    }

    @When("I search the templates for {string}")
    public void i_search_the_templates_for(String text) {
        page.open();
        page.search(text.replace("{now}", now));
    }

    /** Search looks at the name and the subject; every card left must match, and ours must be there. */
    @Then("only templates matching {string} are listed")
    public void only_matching_templates_are_listed(String text) {
        String term = text.replace("{now}", now).toLowerCase();
        List<String> cards = page.namesAndSubjects();
        long matching = cards.stream().filter(c -> c.toLowerCase().contains(term)).count();
        CheckLog.soft("every card matches \"" + term + "\"", matching + " of " + cards.size() + " cards", !cards.isEmpty() && matching == cards.size());
        CheckLog.soft("\"" + templateName + "\" is in the results", page.names().toString(), page.isListed(templateName));
    }

    @When("I sort the templates by {string}")
    public void i_sort_the_templates_by(String option) {
        page.sortBy(option);
    }

    @Then("the templates are sorted by {string}")
    public void the_templates_are_sorted_by(String option) {
        List<String> names = page.names();
        boolean sorted = switch (option) {
            case "Name (A-Z)" -> isOrdered(names, nameOrder());
            case "Name (Z-A)" -> isOrdered(names, nameOrder().reversed());
            // this run's template is the newest one there is
            case "Newest First" -> !names.isEmpty() && names.get(0).equals(templateName);
            default -> throw new IllegalArgumentException("No order check for " + option);
        };
        CheckLog.soft("sort shows " + option, page.sortShown(), page.sortShown().equals(option));
        CheckLog.soft("templates are in " + option + " order", names.toString(), sorted);
    }

    /** Case-insensitive, the way the list orders "AI Basics" next to "artificial intelligence...". */
    private static Comparator<String> nameOrder() {
        return String.CASE_INSENSITIVE_ORDER;
    }

    private static boolean isOrdered(List<String> names, Comparator<String> order) {
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(order);
        return !names.isEmpty() && sorted.equals(names);
    }

    private static Path downloadedImscc() {
        File[] files = Settings.DOWNLOAD_DIR.toFile().listFiles((dir, name) -> name.endsWith(".imscc"));
        if (files == null || files.length == 0) {
            throw new IllegalStateException("No .imscc in " + Settings.DOWNLOAD_DIR + " -- download a course as IMSCC first");
        }
        return files[0].toPath();
    }
}
