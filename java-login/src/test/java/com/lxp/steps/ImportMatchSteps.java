package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.ImportSubmissionsPage;
import com.lxp.pages.ImportSubmissionsPage.Entry;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.opentest4j.TestAbortedException;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The preview's match check on its own, with nothing imported: a Canvas
 * submissions ZIP is uploaded through Grades → ⋮ → Import Submissions into
 * the course's first assignment, every student in it must be in the green
 * Matched list, and the window is then closed without importing.
 */
public class ImportMatchSteps {

    private final ImportSubmissionsPage page = new ImportSubmissionsPage(DriverFactory.get());

    private String assignment = "";
    private List<Entry> zip = List.of();
    /** From the preview: login → student name, for students matched with no submission yet (the green list). */
    private final Map<String, String> matched = new LinkedHashMap<>();
    private final Map<String, String> alreadySubmitted = new LinkedHashMap<>();
    private List<String> unmatched = List.of();

    @When("I open the Grades of the course {string}")
    public void i_open_the_grades_of_the_course(String course) {
        page.openCourse(course);
        page.openGrades();
        CheckLog.check("the Grades of \"" + course + "\" open", "Assessments listed", true);
    }

    @When("I open Import Submissions for the first assignment")
    public void i_open_import_submissions_for_the_first_assignment() {
        assignment = page.selectFirstAssignment();
        page.importFromGradesMenu(assignment);
        CheckLog.check("Import Submissions opens for the first assignment", "importing into \"" + assignment + "\"", page.isOpen());
    }

    @When("I upload the Canvas ZIP {string} to preview it")
    @SuppressWarnings("unchecked")
    public void i_upload_the_canvas_zip_to_preview_it(String file) {
        URL url = getClass().getResource("/files/" + file);
        if (url == null) {
            // kept out of git (it comes from Canvas): without it, the scenario is skipped, not failed
            throw new TestAbortedException("Put the Canvas submissions ZIP at src/test/resources/files/" + file);
        }
        Path path;
        try {
            path = Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        zip = ImportSubmissionsPage.readZip(path);
        Map<String, Object> preview = page.upload(path);
        for (Object o : (List<Object>) preview.getOrDefault("matched", List.of())) {
            Map<String, Object> m = (Map<String, Object>) o;
            String login = String.valueOf(m.get("login_id"));
            String name = String.valueOf(m.getOrDefault("student_name", login)).strip();
            (Boolean.TRUE.equals(m.get("has_existing")) ? alreadySubmitted : matched).put(login, name);
        }
        unmatched = ((List<Object>) preview.getOrDefault("unmatched", List.of())).stream()
                .map(o -> String.valueOf(((Map<String, Object>) o).get("login_id"))).toList();
        CheckLog.review("preview of " + file + " (from the server)", "Matched " + matched + "; Already Submitted "
                + alreadySubmitted + "; Unmatched " + unmatched + "; window: " + page.windowText());
    }

    /** "Students whose login IDs match enrolled students appear in the Matched (green) list." */
    @Then("every student in the ZIP is in the green Matched list")
    public void every_student_in_the_zip_is_in_the_green_matched_list() {
        page.showList("Matched");
        for (String login : zip.stream().map(Entry::login).distinct().toList()) {
            page.say("Checking: \"" + login + "\" is in the green Matched list");
            boolean in = matched.containsKey(login);
            String where = in ? "Matched → " + matched.get(login) : alreadySubmitted.containsKey(login) ? "Already Submitted"
                    : unmatched.contains(login) ? "Unmatched" : "not listed";
            CheckLog.soft("\"" + login + "\" is matched to an enrolled student (Matched list)", where, in);
        }
        page.say("Checking: the Matched list is green");
        String colour = page.listColour("Matched");
        CheckLog.soft("the Matched list is green", colour, colour.startsWith("green"));
        CheckLog.soft("nobody in the ZIP is Unmatched", unmatched.isEmpty() ? "none" : String.valueOf(unmatched), unmatched.isEmpty());
        String text = page.windowText();
        String count = matched.size() + " of " + zip.size() + " submissions will be imported";
        CheckLog.soft("the preview says \"" + count + "\"", text, text.contains(count));
        assertThat(matched).withFailMessage("No student in the ZIP was matched: " + page.windowText()).isNotEmpty();
    }

    @Then("I close the import window without importing")
    public void i_close_the_import_window_without_importing() {
        int sent = page.importsSent();
        page.close();
        CheckLog.check("nothing is imported (the window is closed at the preview)",
                sent == 0 ? "closed; no import sent" : sent + " imports sent", sent == 0 && !page.isOpen());
        assertThat(sent).withFailMessage("An import was sent from the preview").isZero();
    }
}
