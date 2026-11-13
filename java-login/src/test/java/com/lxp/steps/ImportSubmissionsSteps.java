package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.ImportSubmissionsPage;
import com.lxp.pages.ImportSubmissionsPage.Entry;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;
import org.opentest4j.TestAbortedException;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import Submissions with a real Canvas submissions ZIP (downloaded from
 * Canvas → assignment → Download Submissions, kept in
 * src/test/resources/files and out of git, as it holds student work).
 *
 * TC1 previews that ZIP as downloaded, plus one file for a student who is
 * not on the course. TC2 imports two Matched students' real files with AI
 * Evaluation on. TC3 imports one already-submitted student's real file
 * again, renamed "run&lt;now&gt;-...", first with the overwrite box unchecked
 * (skipped, unchanged) and then checked (replaced).
 *
 * Which list each student is in is read from the server's preview reply.
 */
public class ImportSubmissionsSteps {

    private final WebDriver driver = DriverFactory.get();
    private final ImportSubmissionsPage page = new ImportSubmissionsPage(driver);

    private static final Path ZIP_DIR = Path.of("target", "import-submissions").toAbsolutePath();
    /** Quick Review's AI card once the AI has graded: "AI SUGGESTED 0 / 100". */
    private static final Pattern AI_SCORE = Pattern.compile("AI SUGGESTED\\s*(\\d+(\\.\\d+)?)\\s*/\\s*(\\d+)");

    /** Marks the renamed file of TC3, so the replaced submission is recognised. */
    private final String runMark = "run" + RunClock.NOW + "-";
    private final Entry unknown = new Entry("nobody" + RunClock.NOW + "_99999999_99999_not-on-the-course.txt",
            ("A file for a student who is not on the course (" + RunClock.NOW + ")\n").getBytes(StandardCharsets.UTF_8));

    private String zipName = "";
    private List<Entry> canvasZip = List.of();
    private List<String> respondentsBefore = List.of();
    private int totalBefore;

    /** From the TC1 preview: login → student name, per list. */
    private final Map<String, String> matched = new LinkedHashMap<>();
    private final Map<String, String> alreadySubmitted = new LinkedHashMap<>();
    private final List<String> unmatched = new ArrayList<>();

    /** TC2: the students imported, login → name, and the import reply. */
    private final Map<String, String> imported = new LinkedHashMap<>();
    private int filesImported;
    private Map<String, Object> importReply = Map.of();

    /** TC3: the already-submitted student, and their file renamed with the run's mark. */
    private String targetName;
    private Entry targetFile;
    private boolean caseAImported;
    private Map<String, Object> caseAReply = Map.of();

    // ---- set-up ----

    @Given("the Canvas submissions ZIP {string} is in the test files")
    public void the_canvas_submissions_zip_is_in_the_test_files(String file) {
        URL url = getClass().getResource("/files/" + file);
        if (url == null) {
            // it is kept out of git (student work): without it, the scenario is skipped, not failed
            throw new TestAbortedException("Put the Canvas submissions ZIP at src/test/resources/files/" + file);
        }
        try {
            canvasZip = ImportSubmissionsPage.readZip(Path.of(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        zipName = file;
        long students = canvasZip.stream().map(Entry::login).distinct().count();
        CheckLog.check("the Canvas ZIP \"" + file + "\" is read", canvasZip.size() + " files from " + students + " students", !canvasZip.isEmpty());
    }

    @When("I open the Responses of the assignment {string} in the course {string}")
    public void i_open_the_responses(String assignment, String course) {
        page.openCourse(course);
        page.openResponses(assignment);
        totalBefore = page.totalResponses();
        respondentsBefore = page.respondents();
        CheckLog.check("the Responses of \"" + assignment + "\" open", totalBefore + " responses", totalBefore >= 0);
    }

    // ---- TC1: the preview ----

    @When("I open Import Submissions")
    public void i_open_import_submissions() {
        page.openImport();
        CheckLog.check("the Import Canvas Submissions window opens", page.isOpen() ? "open" : "not open", page.isOpen());
        assertThat(page.isOpen()).withFailMessage("The Import Canvas Submissions window did not open").isTrue();
    }

    @When("I upload the Canvas ZIP with one file added for a student who is not on the course")
    @SuppressWarnings("unchecked")
    public void i_upload_the_canvas_zip() {
        List<Entry> files = new ArrayList<>(canvasZip);
        files.add(unknown);
        Map<String, Object> preview = page.upload(ImportSubmissionsPage.writeZip(ZIP_DIR, "preview_" + RunClock.NOW + "_" + zipName, files));
        matched.clear();
        alreadySubmitted.clear();
        unmatched.clear();
        for (Object o : (List<Object>) preview.getOrDefault("matched", List.of())) {
            Map<String, Object> m = (Map<String, Object>) o;
            String login = String.valueOf(m.get("login_id"));
            String name = String.valueOf(m.getOrDefault("student_name", login));
            (Boolean.TRUE.equals(m.get("has_existing")) ? alreadySubmitted : matched).put(login, name);
        }
        for (Object o : (List<Object>) preview.getOrDefault("unmatched", List.of())) {
            unmatched.add(String.valueOf(((Map<String, Object>) o).get("login_id")));
        }
        CheckLog.review("preview (from the server)", matched.size() + " Matched " + matched.values() + "; "
                + alreadySubmitted.size() + " Already Submitted " + alreadySubmitted.values() + "; Unmatched " + unmatched);
    }

    @Then("the preview puts the students in the Matched, Already Submitted and Unmatched lists")
    public void the_preview_lists_the_students() {
        for (String login : canvasZip.stream().map(Entry::login).distinct().toList()) {
            String where = matched.containsKey(login) ? "Matched" : alreadySubmitted.containsKey(login) ? "Already Submitted"
                    : unmatched.contains(login) ? "Unmatched" : "not listed";
            CheckLog.soft("\"" + login + "\" (on the course) is Matched or Already Submitted", where,
                    matched.containsKey(login) || alreadySubmitted.containsKey(login));
        }
        CheckLog.soft("\"" + unknown.login() + "\" (not on the course) is in the Unmatched list",
                unmatched.contains(unknown.login()) ? "Unmatched" : "not in Unmatched", unmatched.contains(unknown.login()));

        String text = page.windowText();
        String total = "of " + (canvasZip.size() + 1) + " submissions will be imported";
        CheckLog.soft("the preview counts every file (\"... " + total + "\")", text, text.contains(total));
        if (!matched.isEmpty()) {
            String c = page.listColour("Matched");
            CheckLog.soft("the Matched list is green", c, c.startsWith("green"));
        }
        if (!alreadySubmitted.isEmpty()) {
            String c = page.listColour("Already Submitted");
            CheckLog.soft("the Already Submitted list is orange", c, c.startsWith("orange"));
        }
        String c = page.listColour("Unmatched");
        CheckLog.soft("the Unmatched list is amber", c, c.startsWith("amber"));
    }

    @Then("students without a response are Matched and students with one are Already Submitted")
    public void matched_and_already_submitted_follow_the_responses() {
        matched.values().forEach(name -> CheckLog.soft("\"" + name + "\" (Matched) had no response yet",
                respondentsBefore.contains(name) ? "listed under All Responses" : "not listed", !respondentsBefore.contains(name)));
        alreadySubmitted.values().forEach(name -> CheckLog.soft("\"" + name + "\" (Already Submitted) is listed under All Responses",
                respondentsBefore.contains(name) ? "listed" : "not listed", respondentsBefore.contains(name)));
    }

    @Then("nothing is imported yet")
    public void nothing_is_imported_yet() {
        // the preview only reads the ZIP: no import request has gone out (the Responses count is checked after the import)
        int sent = page.importsSent();
        CheckLog.check("no submission is saved at the preview step", sent == 0 ? "no import sent, only the preview" : sent + " imports sent", sent == 0);
    }

    /**
     * How the overwrite boxes start is recorded, not judged: the test case
     * only says what happens when the box is left unchecked (case A) or
     * checked (case B). The app starts them all checked ("Select all"), so
     * the test unchecks the box itself for case A.
     */
    @Then("how the already-submitted students' overwrite boxes start is recorded")
    public void the_overwrite_boxes_start_state_is_recorded() {
        alreadySubmitted.values().forEach(name -> {
            Boolean checked = page.overwriteChecked(name);
            CheckLog.review("\"Overwrite submission for " + name + "\" when the preview opens",
                    checked == null ? "no overwrite box" : checked ? "checked (Select all is on)" : "unchecked");
        });
    }

    // ---- TC2: import with AI evaluation ----

    @When("I import {int} of the Matched students with AI Evaluation on")
    public void i_import_matched_students(int count) {
        page.backToUpload();
        List<String> picked = matched.keySet().stream().limit(count).toList();
        if (picked.isEmpty()) {
            CheckLog.review("nothing new to import", "every student in the ZIP already has a submission on this assignment: "
                    + "use an assignment with no submissions yet (Grades shows 0/29)");
            return;
        }
        List<Entry> files = canvasZip.stream().filter(e -> picked.contains(e.login())).toList();
        page.upload(ImportSubmissionsPage.writeZip(ZIP_DIR, "import_" + RunClock.NOW + ".zip", files));
        boolean ai = page.aiEvaluationOn();
        CheckLog.check("\"Auto-grade with AI\" (AI Evaluation) is on", ai ? "on" : "off", ai);
        importReply = page.importNow();
        picked.forEach(l -> imported.put(l, matched.get(l)));
        filesImported = files.size();
        CheckLog.review("import reply (from the server)", importReply.toString());
    }

    @Then("the result shows how many submissions were imported")
    public void the_result_shows_how_many_were_imported() {
        if (imported.isEmpty()) {
            return;
        }
        int count = number(importReply.get("imported"));
        String text = page.windowText();
        CheckLog.check(filesImported + " files are imported (" + imported.size() + " students)", count + " imported", count == filesImported);
        CheckLog.soft("the result screen shows how many were imported", text, text.matches("(?i).*\\b" + count + "\\b.*imported.*"));
        assertThat(count).withFailMessage("Imported " + count + ", expected " + filesImported).isEqualTo(filesImported);
    }

    @When("I close the import window")
    public void i_close_the_import_window() {
        page.close();
        page.refresh();
    }

    @Then("each imported student's submission is in the responses")
    public void each_imported_submission_is_in_the_responses() {
        if (!imported.isEmpty()) {
            // only the imported students were added: so the preview before saved nothing
            int now = page.totalResponses();
            CheckLog.check("Total Responses grew by the " + imported.size() + " imported students only",
                    totalBefore + " → " + now, now == totalBefore + imported.size());
        }
        imported.forEach((login, name) -> {
            String file = canvasZip.stream().filter(e -> e.login().equals(login)).findFirst().map(Entry::file).orElse("");
            String row = page.row(name);
            String shown = page.responseOf(name);
            boolean has = !row.isEmpty() && (shown.contains(file) || shown.contains(stem(file)));
            CheckLog.soft("\"" + name + "\" now has the imported submission (" + file + ")",
                    row.isEmpty() ? "no row under All Responses" : has ? "shows " + file : shorten(shown), has);
        });
    }

    /**
     * AI evaluation is queued on import: Quick Review (View) then shows
     * "AI SUGGESTED n / 100" and "Last evaluated ...". The Responses row says
     * "Not graded" until the teacher approves, so the row is not used.
     */
    @Then("an AI score appears for an imported submission within {int} minutes")
    public void an_ai_score_appears(int minutes) {
        if (imported.isEmpty()) {
            return;
        }
        String name = imported.values().iterator().next();
        long until = System.currentTimeMillis() + minutes * 60_000L;
        String shown = page.responseOf(name);
        while (!(AI_SCORE.matcher(shown).find() && shown.contains("Last evaluated")) && System.currentTimeMillis() < until) {
            pause(20_000);
            page.refresh();
            shown = page.responseOf(name);
        }
        java.util.regex.Matcher m = AI_SCORE.matcher(shown);
        boolean scored = m.find() && shown.contains("Last evaluated");
        CheckLog.soft("the AI suggests a score for \"" + name + "\"'s imported submission within " + minutes + " min",
                scored ? "AI SUGGESTED " + m.group(1) + " / " + m.group(3) : shorten(shown), scored);
        if (scored) {
            int i = shown.indexOf("AI SUGGESTED");
            CheckLog.review("AI evaluation for \"" + name + "\" (Quick Review)", shorten(shown.substring(i)));
        }
    }

    // ---- TC3: an already-submitted student, overwrite off then on ----

    /**
     * Case A: an already-submitted student's real file, renamed with the
     * run's mark, with the overwrite box unchecked. A Matched student's file
     * goes along when there is one, so Import can be clicked.
     */
    @When("I import an already-submitted student's file again with the overwrite box unchecked")
    public void i_import_an_already_submitted_file_unchecked() {
        String login = !alreadySubmitted.isEmpty() ? alreadySubmitted.keySet().iterator().next()
                : !imported.isEmpty() ? imported.keySet().iterator().next() : null;
        if (login == null) {
            CheckLog.review("overwrite", "no student with a submission in the ZIP, so the overwrite cases were not tried");
            return;
        }
        targetName = alreadySubmitted.getOrDefault(login, imported.get(login));
        targetFile = canvasZip.stream().filter(e -> e.login().equals(login)).findFirst().orElseThrow().renamed(runMark);

        List<Entry> files = new ArrayList<>(List.of(targetFile));
        matched.keySet().stream().filter(l -> !imported.containsKey(l)).findFirst()
                .ifPresent(l -> canvasZip.stream().filter(e -> e.login().equals(l)).forEach(files::add));
        page.openImport();
        page.upload(ImportSubmissionsPage.writeZip(ZIP_DIR, "overwrite_off_" + RunClock.NOW + ".zip", files));
        boolean box = page.setOverwrite(targetName, false);
        CheckLog.check("\"" + targetName + "\" is in Already Submitted with an overwrite box, left unchecked",
                box ? "unchecked" : "no overwrite box", box);
        if (page.importEnabled()) {
            caseAReply = page.importNow();
            caseAImported = true;
            CheckLog.review("import reply, overwrite unchecked (from the server)", caseAReply.toString());
        } else {
            CheckLog.check("with nothing else to import, Import is not offered", "Import is disabled: nothing is imported", true);
        }
        page.close();
        page.refresh();
    }

    @Then("that student is counted as skipped and their submission is unchanged")
    public void that_student_is_skipped_and_unchanged() {
        if (targetName == null) {
            return;
        }
        if (caseAImported) {
            int skipped = number(caseAReply.get("skipped_already_submitted"));
            CheckLog.soft("\"" + targetName + "\" is counted under Skipped", skipped + " skipped", skipped >= 1);
        }
        String shown = page.responseOf(targetName);
        boolean unchanged = !shown.contains(runMark);
        CheckLog.check("\"" + targetName + "\"'s submission is unchanged", unchanged ? "still the earlier file" : "shows " + targetFile.file(), unchanged);
        assertThat(unchanged).withFailMessage(targetName + "'s submission was overwritten with the box unchecked").isTrue();
    }

    @When("I import that file again with the overwrite box checked")
    public void i_import_that_file_with_the_box_checked() {
        if (targetName == null) {
            return;
        }
        page.openImport();
        page.upload(ImportSubmissionsPage.writeZip(ZIP_DIR, "overwrite_on_" + RunClock.NOW + ".zip", List.of(targetFile)));
        boolean box = page.setOverwrite(targetName, true);
        CheckLog.check("\"" + targetName + "\"'s overwrite box is checked", box ? "checked" : "no overwrite box", box);
        assertThat(box).withFailMessage("No \"Overwrite submission for " + targetName + "\" box").isTrue();
        CheckLog.review("import reply, overwrite checked (from the server)", page.importNow().toString());
        page.close();
        page.refresh();
    }

    @Then("that student's submission is replaced with the new file")
    public void that_submission_is_replaced() {
        if (targetName == null) {
            return;
        }
        String shown = page.responseOf(targetName);
        boolean replaced = shown.contains(runMark);
        CheckLog.check("\"" + targetName + "\"'s submission is replaced with the new file",
                replaced ? "shows " + targetFile.file() : shorten(shown), replaced);
        assertThat(replaced).withFailMessage(targetName + "'s submission was not replaced").isTrue();
    }

    // ---- helpers ----

    /** "09-lab-set-permissions.zip" → "09-lab-set-permissions": the page may show the name without its extension. */
    private static String stem(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    private static int number(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o).strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String shorten(String s) {
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
