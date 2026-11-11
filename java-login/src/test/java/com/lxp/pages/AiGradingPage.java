package com.lxp.pages;

import com.lxp.config.Settings;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI grading: the course's Assessments Setup switch, the student's assignment
 * submission, and Quick Review, the teacher's grading page for one student's
 * work on an assignment, quiz or discussion.
 *
 * Quick Review (staging, Nov 2026) is /course/{course}/quickreview/{type}/{item};
 * without ?student= it opens the first student. Its right-hand side:
 * - "THE GRADE": a PENDING GRADE card ("Not recorded yet — approve to record it")
 *   or RECORDED GRADE ("Approved here · date"), and an AI SUGGESTED card
 *   ("7 / 10", "Waiting on you" / "Already reviewed").
 * - Before a suggestion: an "AI EVALUATION" box with a Strictness dropdown
 *   (Liberal / Moderate / Strict), "Run AI Evaluation" and the line
 *   "Run AI Evaluation for suggestions.". While it runs: "Queued for AI
 *   evaluation" or "AI is evaluating this submission" with a percentage.
 * - After: "Rubric breakdown" with the Strictness dropdown, "Re-run", and one
 *   row per criterion (SUGGESTED "2 / 4", an input "{criterion} override score").
 * - FEEDBACK: a rich text editor holding the AI's feedback.
 * - Footer: GRADE, Submit to Canvas, Approve (becomes "Approved").
 * A discussion with no end date asks "Discussion Still Active" → Evaluate Now.
 * Submit to Canvas is never clicked.
 */
public class AiGradingPage extends BasePage {

    private static final Duration LOAD = Duration.ofSeconds(60);

    private static final Pattern COURSE_ID = Pattern.compile("/course/([0-9a-fA-F-]{36})");
    private static final Pattern ITEM_ID = Pattern.compile("[?&]item=([0-9a-fA-F-]{36})");
    private static final Pattern SCORE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)");

    // ---- Assessments Setup ----
    private static final By ASSESSMENTS_SETUP = By.xpath("//button[starts-with(normalize-space(.), 'Assessments Setup')]");
    private static final By SETUP_DIALOG = By.xpath("//div[@role='dialog'][.//*[normalize-space(text())='Assessments Setup']]");
    private static final By AI_SWITCH = By.xpath("//div[@role='dialog']//*[@role='switch'][@aria-label='AI Auto-Evaluation']");
    private static final By SETUP_SAVE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Save']");
    private static final By SETTINGS_SAVED = By.xpath("//*[@aria-label[starts-with(., 'Notifications')]]//li[contains(normalize-space(.), 'settings saved')]");

    // ---- the student's assignment ----
    /**
     * The editor the student writes in. Once there is a submission the page also
     * shows the submitted response in a read-only editor above it, so only an
     * editable one counts, and the last one is the new attempt's.
     */
    private static final By EDITOR = By.xpath("(//main//div[@role='textbox'][@aria-label='Rich text editor'][@contenteditable='true'])[last()]");
    private static final By SUBMIT_FOR_GRADING = By.xpath("//main//button[normalize-space(.)='Submit for Grading']");

    // ---- Quick Review ----
    private static final By STRICTNESS = By.xpath("//button[@aria-label='Strictness']");
    private static final By RUN_AI = By.xpath("//button[normalize-space(.)='Run AI Evaluation' or normalize-space(.)='Re-run AI Evaluation' or normalize-space(.)='Re-run']");
    private static final By EVALUATE_NOW = By.xpath("//*[@role='dialog' or @role='alertdialog']//button[normalize-space(.)='Evaluate Now'"
            + " or normalize-space(.)='Re-run' or normalize-space(.)='Continue' or normalize-space(.)='Confirm' or normalize-space(.)='Yes']");
    private static final By APPROVE = By.xpath("//button[normalize-space(.)='Approve']");
    private static final By APPROVED = By.xpath("//button[normalize-space(.)='Approved']");

    private String courseId;

    public AiGradingPage(WebDriver driver) {
        super(driver);
    }

    /** Remembers the course from the current address (any page of it). */
    public void rememberCourse() {
        Matcher m = COURSE_ID.matcher(driver.getCurrentUrl());
        if (m.find()) {
            courseId = m.group(1);
        }
    }

    // ---- Assessments Setup: AI Auto-Evaluation ----

    /** Course page side panel → Assessments Setup; returns the switch's state. */
    public boolean openAssessmentsSetup() {
        WebElement b = new WebDriverWait(driver, LOAD).until(ExpectedConditions.elementToBeClickable(ASSESSMENTS_SETUP));
        centre(b);
        b.click();
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(SETUP_DIALOG));
        return aiAutoEvaluationOn();
    }

    public boolean aiAutoEvaluationOn() {
        return "true".equals(find(AI_SWITCH).getDomAttribute("aria-checked"));
    }

    /**
     * Sets the switch, then Save. Despite "Changes save automatically", the
     * switch alone sends nothing: Save sends it (PUT /api/projects/{id}) and
     * closes the window before the request is done, so leaving the page at
     * once can lose it. Waits for "Assignment settings saved"; true if seen.
     */
    public boolean setAiAutoEvaluation(boolean on) {
        if (aiAutoEvaluationOn() != on) {
            WebElement s = find(AI_SWITCH);
            centre(s);
            s.click();
            wait.until(d -> aiAutoEvaluationOn() == on);
        }
        click(SETUP_SAVE);
        boolean saved = isVisible(SETTINGS_SAVED, Duration.ofSeconds(30)) || !driver.findElements(SETTINGS_SAVED).isEmpty();
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.invisibilityOfElementLocated(SETUP_DIALOG));
        return saved;
    }

    /** Closes Assessments Setup without saving (to only read it). */
    public void closeAssessmentsSetup() {
        click(By.xpath("//div[@role='dialog']//button[@aria-label='Close' or normalize-space(.)='Close']"));
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.invisibilityOfElementLocated(SETUP_DIALOG));
    }

    // ---- the student's assignment (in Student View, on the assignment's page) ----

    /** The button that opened the submission form, e.g. "Start Assignment" or "New Attempt". */
    private String startedWith = "";

    /**
     * Start Assignment (or, once there is a submission, the button for a new
     * attempt) → write the response → Submit for Grading → answer any
     * "are you sure / new attempt" window.
     */
    public void submitAssignment(String response) {
        openResponseForm();
        WebElement editor = new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(EDITOR));
        centre(editor);
        editor.click();
        editor.sendKeys(response);
        // Submit for Grading does nothing for an empty response: make sure the text is in the editor first
        String head = response.substring(0, Math.min(30, response.length()));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElement(EDITOR).getText().contains(head));
        } catch (TimeoutException e) {
            throw new IllegalStateException("The response did not go into the editor; it holds \"" + driver.findElement(EDITOR).getText() + "\"");
        }
        // The page keeps its own copy of the response (from the editor's input events) and keeps
        // Submit for Grading disabled while that copy is empty; a disabled button ignores clicks
        // without any message. Fire one more input event so the page takes the editor's text,
        // then wait for the button to be enabled.
        js("arguments[0].dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertText', data: ' '}));", driver.findElement(EDITOR));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
                WebElement b = d.findElement(SUBMIT_FOR_GRADING);
                return b.isEnabled() && b.getDomAttribute("disabled") == null;
            });
        } catch (TimeoutException e) {
            throw new IllegalStateException("Submit for Grading stays disabled although the editor holds the response: " + formText());
        }
        WebElement submit = driver.findElement(SUBMIT_FOR_GRADING);
        centre(submit);
        clickOrScript(submit);
        answerConfirmation(Duration.ofSeconds(8));
        // a new attempt's Submit has been seen to do nothing: if the form is still open with
        // no message, click it once more with a real mouse click, then confirm again
        if (!submittedOrFailed() && !driver.findElements(SUBMIT_FOR_GRADING).isEmpty()) {
            System.out.println("[ai grading] Submit for Grading did nothing; form says: " + formText());
            WebElement again = driver.findElement(SUBMIT_FOR_GRADING);
            centre(again);
            new org.openqa.selenium.interactions.Actions(driver).moveToElement(again).pause(Duration.ofMillis(300)).click().perform();
            answerConfirmation(Duration.ofSeconds(8));
        }
    }

    /** A "submitted" or error message is up, or the editable form has closed. */
    private boolean submittedOrFailed() {
        return Boolean.TRUE.equals(js("const t = [...document.querySelectorAll(\"[aria-label^='Notifications'] li\")];"
                + "if (t.some(e => /submitted/i.test(e.textContent) || e.classList.contains('destructive'))) return true;"
                + "return !document.querySelector(\"main div[role='textbox'][aria-label='Rich text editor'][contenteditable='true']\");"));
    }

    /**
     * The new-attempt form's own text (from its heading to Submit for Grading),
     * with any inline warning or validation message: the answer is not in a
     * pop-up window, so look here.
     */
    public String formText() {
        Object s = js("const b = [...document.querySelectorAll('main button')].find(b => b.innerText.trim() === 'Submit for Grading');"
                + "if (!b) return '(no Submit for Grading)';"
                + "let f = b; while (f.parentElement && !f.querySelector(\"[role='textbox'][contenteditable='true']\")) f = f.parentElement;"
                + "const alerts = [...document.querySelectorAll(\"[role='alert'], [aria-live], [aria-invalid='true'], .text-destructive, .text-red-500, .text-red-600\")]"
                + "  .map(e => e.textContent.replace(/\\s+/g, ' ').trim()).filter(Boolean);"
                + "const box = f.querySelector(\"[role='textbox'][contenteditable='true']\");"
                + "return 'form: ' + f.innerText.replace(/\\s+/g, ' ').trim().slice(0, 500)"
                + "  + ' | editor holds ' + (box ? box.innerText.trim().length : 0) + ' chars'"
                + "  + ' | Submit disabled: ' + b.disabled + ' | warnings: ' + JSON.stringify(alerts.slice(0, 8));");
        return s == null ? "" : s.toString();
    }

    /**
     * Gets to an open, editable response form the way a student does. The
     * assignment page's button (Start Assignment / View Submission …) opens the
     * submission page; once there is a submission that page shows it with a
     * "Start New Attempt" button, which opens the form. So: click the best
     * button there is, prefer Start New Attempt, until the editor shows (up to 4 clicks).
     */
    private void openResponseForm() {
        List<String> clicked = new java.util.ArrayList<>();
        for (int i = 0; i < 4 && driver.findElements(EDITOR).isEmpty(); i++) {
            WebElement next;
            try {
                next = new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> (WebElement) js(
                        "const visible = [...document.querySelectorAll('main button')].filter(b => b.offsetParent !== null && !b.disabled);"
                        + "const text = b => b.innerText.replace(/\\s+/g, ' ').trim();"
                        + "const order = [/^Start New Attempt$/i, /^New Attempt$/i, /^(Resubmit|Re-submit|Submit Again|Try Again|Retake|New Submission)$/i,"
                        + "  /^(Start Assignment|Start Attempt|Continue Assignment|Continue Your Response|Edit Submission)$/i,"
                        + "  /^View (Your )?Submission$/i, /^(Start|New|Resubmit|Retake|Continue|View).*(Attempt|Assignment|Submission|Response)/i];"
                        + "for (const re of order) { const b = visible.find(b => re.test(text(b))); if (b) return b; }"
                        + "return null;"));
            } catch (TimeoutException e) {
                break;
            }
            String label = next.getText().replaceAll("\\s+", " ").strip();
            clicked.add(label);
            centre(next);
            clickOrScript(next);
            answerConfirmation(Duration.ofSeconds(3));
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> !d.findElements(EDITOR).isEmpty());
            } catch (TimeoutException ignored) {
                // not open yet: the next round clicks the next button (e.g. Start New Attempt)
            }
        }
        startedWith = clicked.isEmpty() ? "(form already open)" : String.join(" → ", clicked);
        if (driver.findElements(EDITOR).isEmpty()) {
            throw new IllegalStateException("No response form opened after clicking " + clicked + "; " + describePage());
        }
    }

    public String startedWith() {
        return startedWith;
    }

    /** If a window asks to confirm (Submit / Yes / Confirm / Continue / Start…), confirms it. */
    private void answerConfirmation(Duration within) {
        long end = System.currentTimeMillis() + within.toMillis();
        while (System.currentTimeMillis() < end) {
            Object clicked = js("if ([...document.querySelectorAll(\"[aria-label^='Notifications'] li\")].some(e => /submitted/i.test(e.textContent))) return '';"
                    + "const d = [...document.querySelectorAll(\"[role='dialog'],[role='alertdialog']\")].find(e => e.offsetParent !== null || e.getClientRects().length);"
                    + "if (!d) {"
                    + "  const inline = [...document.querySelectorAll('main button')].find(b => b.offsetParent !== null && !b.disabled"
                    + "    && /^(Confirm( Submission| Submit)?|Yes(,? submit.*)?|Submit Anyway|Submit (New )?Attempt|Replace.*)$/i.test(b.innerText.trim()));"
                    + "  if (!inline) return null; const t = inline.innerText.trim(); inline.click(); return t;"
                    + "}"
                    + "const b = [...d.querySelectorAll('button')].find(b => !b.disabled"
                    + "  && /^(Submit|Yes|Confirm|Continue|OK|Start|Proceed)/i.test(b.innerText.trim()) && !/Cancel|Close|Back/i.test(b.innerText));"
                    + "if (!b) return null; const t = b.innerText.trim(); b.click(); return t;");
            if (clicked != null) {
                if (!clicked.toString().isEmpty()) {
                    System.out.println("[ai grading] confirmed \"" + clicked + "\" in a window");
                }
                // a "submitted" message ("") or a confirmed window: done
                return;
            }
            pause(500);
        }
    }

    /** A "submitted" message, a Submitted status, or the app's error ("error: …"); "" if none within a minute. */
    public String submissionResult() {
        try {
            return new WebDriverWait(driver, LOAD).until(d -> {
                Object s = js("const toasts = [...document.querySelectorAll(\"[aria-label^='Notifications'] li\")];"
                        + "const err = toasts.find(e => e.classList.contains('destructive'));"
                        + "if (err) return 'error: ' + err.textContent.replace(/\\s+/g, ' ').trim();"
                        + "const ok = toasts.map(e => e.textContent.replace(/\\s+/g, ' ').trim()).find(t => /submitted/i.test(t));"
                        + "if (ok) return ok;"
                        + "if (document.querySelector(\"main div[role='textbox'][aria-label='Rich text editor'][contenteditable='true']\")) return null;"
                        + "const leaf = [...document.querySelectorAll('main *')].find(e => e.children.length === 0"
                        + "  && /^(Submitted|Graded|Pending Review|Awaiting Review|Awaiting Grading|Reviewed)$/i.test(e.textContent.trim()));"
                        + "return leaf ? leaf.textContent.trim() : null;");
                return s == null ? null : s.toString();
            });
        } catch (TimeoutException e) {
            return "";
        }
    }

    /** What the page shows, to explain a failed submission: buttons, any window, the start of the page. */
    public String describePage() {
        Object s = js("const main = document.querySelector('main') || document.body;"
                + "const buttons = [...main.querySelectorAll('button')].filter(b => b.offsetParent !== null).map(b => b.innerText.replace(/\\s+/g, ' ').trim()).filter(Boolean);"
                + "const dialogs = [...document.querySelectorAll(\"[role='dialog'],[role='alertdialog']\")].map(d => d.innerText.replace(/\\s+/g, ' ').trim());"
                + "const toasts = [...document.querySelectorAll(\"[aria-label^='Notifications'] li\")].map(e => e.textContent.replace(/\\s+/g, ' ').trim());"
                + "return 'buttons: ' + JSON.stringify(buttons.slice(0, 20)) + ' | windows: ' + JSON.stringify(dialogs)"
                + "  + ' | messages: ' + JSON.stringify(toasts) + ' | page: ' + main.innerText.replace(/\\s+/g, ' ').trim().slice(0, 300);");
        return (s == null ? "" : s.toString()) + " | " + formText();
    }

    // ---- Quick Review ----

    /** On an item's page (?type=…&item=…): opens Quick Review for it, in this tab. */
    public void openQuickReviewOfThisItem(String type) {
        Matcher course = COURSE_ID.matcher(driver.getCurrentUrl());
        Matcher item = ITEM_ID.matcher(driver.getCurrentUrl());
        if (!item.find()) {
            throw new IllegalStateException("Not on an item's page: " + driver.getCurrentUrl());
        }
        String id = course.find() ? course.group(1) : courseId;
        driver.get(Settings.BASE_URL + "/course/" + id + "/quickreview/" + type + "/" + item.group(1));
        waitForQuickReview();
    }

    /** Quick Review first shows "Loading Fast Grade…", then the grade panel ("THE GRADE"). */
    private void waitForQuickReview() {
        new WebDriverWait(driver, LOAD).until(d -> Boolean.TRUE.equals(js(
                "const t = document.body.innerText; return !t.includes('Loading Fast Grade') && /THE GRADE/i.test(t);")));
    }

    /** Opens a Quick Review address again (e.g. after a browser restart). */
    public void reopen(String url) {
        driver.get(url);
        waitForQuickReview();
    }

    /** The student Quick Review shows; moves with Next student until it is this one (up to 10). */
    public String showStudent(String name) {
        for (int i = 0; i < 10; i++) {
            String shown = currentStudent();
            if (shown.equals(name)) {
                return shown;
            }
            List<WebElement> next = driver.findElements(By.xpath("//button[@aria-label='Next student' or starts-with(@aria-label, 'Next student')]"));
            if (next.isEmpty() || !next.get(0).isEnabled()) {
                return shown;
            }
            next.get(0).click();
            wait.until(d -> !currentStudent().equals(shown));
        }
        return currentStudent();
    }

    /** The student in Quick Review's header. */
    public String currentStudent() {
        Object s = js("const b = [...document.querySelectorAll('button')].find(b => / — choose a different student$/.test(b.getAttribute('aria-label') || ''));"
                + "if (b) return b.getAttribute('aria-label').replace(/ — choose a different student$/, '');"
                + "const p = [...document.querySelectorAll('button')].find(b => (b.getAttribute('aria-label') || '').startsWith('Previous student'));"
                + "const n = p && p.nextElementSibling; return n ? n.innerText.replace(/^\\w\\s*\\n/, '').replace(/\\s+/g, ' ').trim() : '';");
        return s == null ? "" : s.toString().strip();
    }

    /** Does the student's work on the left show this text? Waits up to a minute. */
    public boolean workShows(String text) {
        try {
            new WebDriverWait(driver, LOAD).until(d -> String.valueOf(js("return document.body.innerText;")).contains(text));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Is an evaluation queued or running? */
    public boolean evaluating() {
        String body = bodyText();
        return body.contains("Queued for AI evaluation") || body.contains("AI is evaluating this submission")
                || currentAttemptAi().matches("(?i).*(Evaluating|Queued|Pending).*");
    }

    /**
     * With several attempts Quick Review lists them (ATTEMPT | SUBMITTED | AI
     * SUGGESTED GRADE), the one shown marked CURRENT, e.g. "7 CURRENT Nov 11,
     * 05:09 PM Evaluating… —". Returns that row's text, or "" when there is
     * no attempts list (one attempt, quizzes, discussions).
     */
    public String currentAttemptAi() {
        Object s = js("const leaf = [...document.querySelectorAll('*')].find(e => e.children.length === 0 && e.textContent.trim().toUpperCase() === 'CURRENT');"
                + "if (!leaf) return '';"
                + "let row = leaf.closest('tr, [role=row]');"
                + "if (!row) { row = leaf; while (row.parentElement && !/\\d{1,2}:\\d{2}/.test(row.innerText)) row = row.parentElement; }"
                + "return row.innerText.replace(/\\s+/g, ' ').trim();");
        return s == null ? "" : s.toString();
    }

    /** The current attempt's AI suggested grade from the attempts list, e.g. [65, 100]; null if none (yet). */
    public double[] currentAttemptScore() {
        String row = currentAttemptAi();
        // drop the submitted time ("05:09 PM") and date before looking for "65 / 100"
        return row.isEmpty() ? null : score(row.replaceAll("\\d{1,2}:\\d{2}", "").replaceAll("[A-Z][a-z]{2} \\d{1,2}, \\d{4}", ""));
    }

    /** The AI SUGGESTED card's text, e.g. "AI SUGGESTED 7 / 10 Waiting on you"; "" when there is none. */
    public String suggestedCard() {
        // the AI SUGGESTED card, not the attempts list's "AI SUGGESTED GRADE" column
        Object s = js("const h = [...document.querySelectorAll('*')].find(e => e.children.length === 0"
                + "  && e.textContent.trim().toUpperCase() === 'AI SUGGESTED' && !e.closest(\"table, thead, [role=table], [role=columnheader], [role=row]\"));"
                + "if (!h) return '';"
                + "let p = h; while (p.parentElement && !/\\d\\s*\\/\\s*\\d|—\\s*\\//.test(p.innerText) && p.innerText.length < 300) p = p.parentElement;"
                + "const t = p.innerText.replace(/\\s+/g, ' ').trim();"
                + "return /ATTEMPT|CURRENT/.test(t) ? '' : t;");
        return s == null ? "" : s.toString();
    }

    /** PENDING GRADE or RECORDED GRADE card. */
    public String gradeCard() {
        String recorded = card("RECORDED GRADE");
        return recorded.isEmpty() ? card("PENDING GRADE") : recorded;
    }

    /** "7 / 10" → [7, 10]; null if the text has no score. */
    public static double[] score(String text) {
        Matcher m = SCORE.matcher(text);
        return m.find() ? new double[] {Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))} : null;
    }

    /** The AI EVALUATION box's text before a suggestion, e.g. "Run AI Evaluation for suggestions."; "" when not shown. */
    public String aiEvaluationBox() {
        Object s = js("const h = [...document.querySelectorAll('*')].find(e => e.children.length === 0 && e.textContent.trim().toUpperCase() === 'AI EVALUATION');"
                + "if (!h) return '';"
                + "let p = h; while (p.parentElement && p.innerText.length < 60) p = p.parentElement;"
                + "return p.innerText.replace(/\\s+/g, ' ').trim();");
        return s == null ? "" : s.toString();
    }

    /** The FEEDBACK editor's text; "" if empty or not shown. */
    public String feedback() {
        Object s = js("const all = [...document.querySelectorAll('*')];"
                + "const label = all.find(e => e.children.length === 0 && e.textContent.trim().toUpperCase() === 'FEEDBACK');"
                + "if (!label) return '';"
                + "const box = [...document.querySelectorAll(\"[role='textbox']\")]"
                + "  .find(b => label.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);"
                + "return box ? box.innerText.replace(/\\s+/g, ' ').trim() : '';");
        return s == null ? "" : s.toString();
    }

    /** Rubric breakdown: criterion → its SUGGESTED score, e.g. "2 / 4" ("" if none). */
    public Map<String, String> criteria() {
        Object rows = js("return [...document.querySelectorAll('input[aria-label$=\" override score\"]')].map(i => {"
                + "  const name = i.getAttribute('aria-label').replace(/ override score$/, '');"
                + "  let row = i; while (row.parentElement && !row.innerText.includes(name)) row = row.parentElement;"
                + "  const m = row.innerText.replace(name, '').match(/(\\d+(?:\\.\\d+)?)\\s*\\/\\s*(\\d+(?:\\.\\d+)?)/);"
                + "  return [name, m ? m[1] + ' / ' + m[2] : ''];"
                + "});");
        Map<String, String> out = new LinkedHashMap<>();
        if (rows instanceof List<?> list) {
            for (Object r : list) {
                if (r instanceof List<?> pair && pair.size() == 2) {
                    out.put(String.valueOf(pair.get(0)), String.valueOf(pair.get(1)));
                }
            }
        }
        return out;
    }

    /** "Last evaluated …" when Quick Review shows it, else "". */
    public String lastEvaluated() {
        Matcher m = Pattern.compile("Last evaluated[^\\n]*").matcher(String.valueOf(js("return document.body.innerText;")));
        return m.find() ? m.group().strip() : "";
    }

    public String strictness() {
        List<WebElement> s = driver.findElements(STRICTNESS);
        return s.isEmpty() ? "" : s.get(0).getText().strip();
    }

    public void chooseStrictness(String level) {
        WebElement s = find(STRICTNESS);
        centre(s);
        s.click();
        click(By.xpath("//*[@role='option'][normalize-space(.)=" + literal(level) + "]"));
        wait.until(d -> strictness().equals(level));
    }

    /** Run AI Evaluation / Re-run, and Evaluate Now if the app asks. */
    public void runEvaluation() {
        WebElement run = find(RUN_AI);
        centre(run);
        clickOrScript(run);
        if (isVisible(EVALUATE_NOW, Duration.ofSeconds(5))) {
            clickOrScript(find(EVALUATE_NOW));
        }
    }

    /**
     * Waits for an AI suggestion, reloading every 30s (the page does not
     * always pick up a finished evaluation). With {@code previous} set (an
     * earlier card + "Last evaluated"), waits for one that is newer: the run
     * was seen in progress, or "Last evaluated" changed.
     */
    public boolean waitForSuggestion(Duration timeout, String previousStamp) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        long nextReload = System.currentTimeMillis() + 30_000;
        boolean sawRun = previousStamp == null;
        while (System.currentTimeMillis() < end) {
            boolean busy = evaluating();
            sawRun |= busy;
            String stamp = lastEvaluated();
            boolean newer = sawRun || (!stamp.isEmpty() && !stamp.equals(previousStamp));
            // with an attempts list, the CURRENT attempt must have its own AI grade too
            boolean currentDone = currentAttemptAi().isEmpty() || currentAttemptScore() != null;
            if (!busy && newer && currentDone && score(suggestedCard()) != null) {
                return true;
            }
            if (System.currentTimeMillis() > nextReload) {
                driver.navigate().refresh();
                waitForQuickReview();
                nextReload = System.currentTimeMillis() + 30_000;
            }
            pause(2000);
        }
        return false;
    }

    /** Watches for a suggestion that should not come; true if none showed in this time. */
    public boolean noSuggestionFor(Duration time) {
        long end = System.currentTimeMillis() + time.toMillis();
        long nextReload = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < end) {
            // only this attempt counts: older attempts keep their own AI grades in the attempts list
            boolean suggested = currentAttemptAi().isEmpty() ? !suggestedCard().isEmpty() : currentAttemptScore() != null;
            if (evaluating() || suggested) {
                return false;
            }
            if (System.currentTimeMillis() > nextReload) {
                driver.navigate().refresh();
                waitForQuickReview();
                nextReload = System.currentTimeMillis() + 30_000;
            }
            pause(5000);
        }
        return true;
    }

    /**
     * Does the student shown have an attempt waiting for the teacher (a
     * submission whose grade is "Not recorded yet")? Quick Review says
     * "No submission" when there is none.
     */
    public boolean submissionAwaitingReview() {
        String body = bodyText();
        if (body.contains("No submission")) {
            return false;
        }
        // waiting = no grade recorded yet: "PENDING GRADE … Not recorded yet", or a RECORDED GRADE of "— / 100"
        return !hasRecordedGrade();
    }

    /** A RECORDED GRADE card with a number in it (not "— / 100"). */
    public boolean hasRecordedGrade() {
        String recorded = card("RECORDED GRADE");
        return !recorded.isEmpty() && score(recorded.replace("—", "")) != null && !recorded.contains("— /");
    }

    /** Fills "Grade out of N" when it is empty (no AI suggestion filled it); returns what it holds after. */
    public String fillGradeIfEmpty(String grade) {
        WebElement input = find(By.xpath("//input[starts-with(@aria-label, 'Grade out of')]"));
        String value = String.valueOf(input.getDomProperty("value")).strip();
        if (value.isEmpty() || value.equals("—") || value.equals("null")) {
            centre(input);
            input.click();
            input.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.COMMAND, "a"), org.openqa.selenium.Keys.BACK_SPACE);
            input.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, "a"), org.openqa.selenium.Keys.BACK_SPACE);
            input.sendKeys(grade);
            value = String.valueOf(input.getDomProperty("value")).strip();
        }
        return value;
    }

    /** Approve; true once the grade shows as recorded. */
    public boolean approve() {
        WebElement a = new WebDriverWait(driver, LOAD).until(ExpectedConditions.elementToBeClickable(APPROVE));
        centre(a);
        clickOrScript(a);
        try {
            new WebDriverWait(driver, LOAD).until(d -> !d.findElements(APPROVED).isEmpty() || hasRecordedGrade());
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** One question's block in a quiz's Question Breakdown, found by its text. */
    public String quizQuestion(String question) {
        String body = String.valueOf(js("return document.body.innerText;")).replaceAll("\\s+", " ");
        int at = body.indexOf(question);
        if (at < 0) {
            return "";
        }
        int next = body.indexOf("Question ", at + question.length());
        int end = body.indexOf("THE GRADE", at);
        int stop = next < 0 ? (end < 0 ? body.length() : end) : (end < 0 ? next : Math.min(next, end));
        return body.substring(at, stop).strip();
    }

    // ---- the discussion's Scores tab (on the discussion's page) ----

    /** Scores tab → the student's row text, e.g. "CI Test Student … 7/10 View & Grade". */
    public String scoresRow(String student) {
        click(By.xpath("//main//button[normalize-space(.)='Scores']"));
        By row = By.xpath("//main//tr[.//*[normalize-space(text())=" + literal(student) + "]]");
        WebElement r = new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(row));
        return r.getText().replaceAll("\\s+", " ").strip();
    }

    // ---- what the student sees (Student View, on the graded item's page) ----

    /**
     * The graded item's page as the student sees it. If the score is not on
     * the page, a "View Feedback / Submission / Grade" button is tried once.
     */
    public String studentGradeText(String scoreHint) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> mainText().contains(scoreHint));
        } catch (TimeoutException e) {
            List<WebElement> more = driver.findElements(By.xpath("//main//button[contains(., 'Feedback') or contains(., 'View Submission')"
                    + " or contains(., 'View Grade') or contains(., 'Submission Details') or contains(., 'Past Attempts')]"));
            if (!more.isEmpty()) {
                clickOrScript(more.get(0));
                pause(3000);
            }
        }
        String dialog = driver.findElements(By.cssSelector("[role='dialog']")).stream()
                .map(WebElement::getText).reduce("", (a, b) -> a + " " + b);
        return (mainText() + " " + dialog).replaceAll("\\s+", " ").strip();
    }

    // ---- helpers ----

    private String card(String title) {
        Object s = js("const t = arguments[0];"
                + "const h = [...document.querySelectorAll('*')].find(e => e.children.length === 0 && e.textContent.trim().toUpperCase() === t);"
                + "if (!h) return '';"
                + "let p = h; while (p.parentElement && !/\\d\\s*\\/\\s*\\d|—\\s*\\//.test(p.innerText) && p.innerText.length < 300) p = p.parentElement;"
                + "return p.innerText.replace(/\\s+/g, ' ').trim();", title);
        return s == null ? "" : s.toString();
    }

    private String bodyText() {
        return String.valueOf(js("return document.body.innerText;"));
    }

    private String mainText() {
        List<WebElement> main = driver.findElements(By.tagName("main"));
        return (main.isEmpty() ? driver.findElement(By.tagName("body")) : main.get(0)).getText().replaceAll("\\s+", " ");
    }

    private void clickOrScript(WebElement el) {
        try {
            el.click();
        } catch (org.openqa.selenium.ElementClickInterceptedException e) {
            js("arguments[0].click();", el);
        }
    }

    private void centre(WebElement el) {
        js("arguments[0].scrollIntoView({block: 'center'})", el);
    }

    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String literal(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }
}
