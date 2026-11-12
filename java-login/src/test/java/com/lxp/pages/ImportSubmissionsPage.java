package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * An assignment's Responses tab and its "Import Canvas Submissions" window
 * (staging, Nov 2026).
 *
 * The window: "Select your Canvas submissions .zip" (a hidden
 * input[type=file][accept=.zip]). Canvas's Download Submissions names each
 * file &lt;login&gt;_[LATE_]&lt;Canvas user id&gt;_&lt;submission id&gt;_&lt;file&gt;, e.g.
 * student51494_262_30429_09-lab.zip; the server matches the student by the
 * Canvas user id and shows the part before the first "_" as the login
 * (POST /canvas-submissions/{course}/preview). The preview then shows
 * "N of M submissions will be imported", the lists Matched (green),
 * Already Submitted (orange, each with an "Overwrite submission for
 * &lt;name&gt;" checkbox; they all start checked, with "Select all") and "UNMATCHED (N) — WILL BE SKIPPED" (amber), an
 * "AI Evaluation" switch (on by default), Back and "Import N Submissions"
 * (POST .../import with trigger_ai_eval and overwrite_user_ids).
 *
 * The preview and import replies are read from the page's own requests (a
 * small fetch hook), so the test knows exactly which students the server
 * matched, without guessing from the list's text.
 */
public class ImportSubmissionsPage extends BasePage {

    private static final Duration LOAD = Duration.ofSeconds(60);

    private static final String DIALOG = "(//div[@role='dialog'][.//*[normalize-space(text())='Import Canvas Submissions']])[last()]";
    private static final By WINDOW = By.xpath(DIALOG);
    private static final By OPEN_IMPORT = By.xpath("//main//button[starts-with(normalize-space(.), 'Import Submissions')]");
    private static final By RESPONSES_TAB = By.xpath("//main//button[normalize-space(.)='Responses']");
    private static final By REFRESH = By.xpath("//main//button[starts-with(normalize-space(.), 'Refresh')]");
    private static final By ZIP_INPUT = By.xpath(DIALOG + "//input[@type='file']");
    private static final By REMOVE_FILE = By.xpath(DIALOG + "//button[@aria-label='Remove file' or normalize-space(.)='Remove file']");
    private static final By PREVIEW_SHOWN = By.xpath(DIALOG + "//*[contains(normalize-space(.), 'submissions will be imported')]");
    private static final By AI_SWITCH = By.xpath(DIALOG + "//button[@role='switch'][@aria-label='AI Evaluation']");
    private static final By IMPORT = By.xpath(DIALOG + "//button[starts-with(normalize-space(.), 'Import ')][contains(normalize-space(.), 'Submission')]");
    private static final By CLOSE = By.xpath(DIALOG + "//button[normalize-space(.)='Close' or @aria-label='Close']");
    private static final By DONE = By.xpath(DIALOG + "//button[normalize-space(.)='Done' or normalize-space(.)='Close' or normalize-space(.)='Finish']");

    private static final Pattern TOTAL = Pattern.compile("(\\d+)\\s*Total Responses");

    public ImportSubmissionsPage(WebDriver driver) {
        super(driver);
    }

    // ---- the assignment ----

    /** Courses list → the course whose card title is exactly this (not its "(Copy)"). */
    public void openCourse(String title) {
        open("/courses");
        By search = By.cssSelector("main input[placeholder^='Search']");
        type(search, title);
        By card = By.xpath("//*[@aria-label=" + literal("Open course: " + title) + "]");
        for (String tab : List.of("Published", "Unpublished", "Published")) {
            if (isVisible(card, Duration.ofSeconds(10))) {
                break;
            }
            By t = By.xpath("//main//button[starts-with(normalize-space(.), '" + tab + "')]");
            if (!driver.findElements(t).isEmpty()) {
                click(t);
            }
        }
        click(card);
        new WebDriverWait(driver, LOAD).until(d -> d.getCurrentUrl().contains("/course/"));
    }

    /** The assignment in the course outline (its module is opened if needed), then its Responses tab. */
    public void openResponses(String assignment) {
        // the outline shows it with its module prefix, e.g. "M03 - Type Conversion and Expression Exercises"
        // the title is a heading (h3 or h4, depending on the course) inside the item's button
        By item = By.xpath("(//main//*[self::h3 or self::h4][normalize-space(.)=" + literal(assignment)
                + " or substring(normalize-space(.), string-length(normalize-space(.)) - " + (assignment.length() - 1) + ")="
                + literal(assignment) + "]/ancestor::button[1])[1]");
        // module headers only ("Module 6: Shell Scripting Basics"), not items such as "Module Summary & Study Guide-4"
        By modules = By.xpath("//main//div[@data-rfd-draggable-id]//h3[starts-with(normalize-space(.), 'Module ')][contains(., ':')]");
        new WebDriverWait(driver, LOAD).until(d -> !d.findElements(modules).isEmpty());
        // a header click toggles its module, so an open one can close: go round twice at most
        for (int round = 0; round < 2 && !isVisible(item, Duration.ofSeconds(3)); round++) {
            for (WebElement m : driver.findElements(modules)) {
                centre(m);
                m.click();
                if (isVisible(item, Duration.ofSeconds(3))) {
                    break;
                }
            }
        }
        WebElement it = find(item);
        centre(it);
        it.click();
        new WebDriverWait(driver, LOAD).until(d -> d.getCurrentUrl().contains("item="));
        assignmentUrl = driver.getCurrentUrl();
        click(RESPONSES_TAB);
        new WebDriverWait(driver, LOAD).until(d -> TOTAL.matcher(mainText()).find());
        // kept so a new browser can carry on signed in if Chrome closes (see backToResponses)
        com.lxp.driver.DriverFactory.saveSession();
    }

    /** The assignment's address, for coming back to its Responses after a browser restart. */
    private String assignmentUrl;

    /** After Chrome closed by itself: a new browser with the signed-in session, back on Responses. */
    private void backToResponses() {
        com.lxp.driver.DriverFactory.restartKeepingSession();
        driver.get(assignmentUrl);
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.elementToBeClickable(RESPONSES_TAB));
        click(RESPONSES_TAB);
        new WebDriverWait(driver, LOAD).until(d -> TOTAL.matcher(mainText()).find());
    }

    /** "20 Total Responses" → 20; -1 if not shown. */
    public int totalResponses() {
        Matcher m = TOTAL.matcher(mainText());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** The student names listed under All Responses. */
    public List<String> respondents() {
        return driver.findElements(By.xpath("//main//tr[td]/td[1]")).stream()
                .map(td -> td.getText().strip().split("\\n")[0].strip())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Refresh re-reads the responses from the server. */
    public void refresh() {
        if (!driver.findElements(REFRESH).isEmpty()) {
            click(REFRESH);
        } else {
            driver.navigate().refresh();
            click(RESPONSES_TAB);
        }
        new WebDriverWait(driver, LOAD).until(d -> TOTAL.matcher(mainText()).find());
        pause(2000);
    }

    /** The student's row in All Responses, as text ("" if not listed). */
    public String row(String student) {
        List<WebElement> rows = driver.findElements(By.xpath("//main//tr[td][.//*[normalize-space(text())=" + literal(student) + "]]"));
        return rows.isEmpty() ? "" : rows.get(0).getText().replaceAll("\\s+", " ").strip();
    }

    /**
     * The student's grading view: View in their row opens Quick Review in a
     * new tab ("SUBMITTED FILES &lt;file&gt;", the "AI SUGGESTED n / 100" card and
     * "Last evaluated ..." once the AI has graded it). Returns its text,
     * closes that tab and comes back to Responses. "" if the student has no row.
     */
    public String responseOf(String student) {
        try {
            return readResponse(student);
        } catch (org.openqa.selenium.WebDriverException e) {
            if (!com.lxp.driver.DriverFactory.isBrowserGone(e) || assignmentUrl == null) {
                throw e;
            }
            // Chrome closed by itself (it does on this machine): start again where we were, once
            System.out.println("[import] the browser closed while reading " + student + "'s response; restarting it");
            backToResponses();
            return readResponse(student);
        }
    }

    private String readResponse(String student) {
        By view =By.xpath("(//main//tr[td][.//*[normalize-space(text())=" + literal(student) + "]]//button[normalize-space(.)='View'])[1]");
        if (driver.findElements(view).isEmpty()) {
            return "";
        }
        String responses = driver.getWindowHandle();
        java.util.Set<String> before = driver.getWindowHandles();
        clickPastToasts(view);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> d.getWindowHandles().size() > before.size()
                    || d.getCurrentUrl().contains("/quickreview/"));
        } catch (TimeoutException e) {
            return "";
        }
        boolean newTab = driver.getWindowHandles().size() > before.size();
        if (newTab) {
            String tab = driver.getWindowHandles().stream().filter(h -> !before.contains(h)).findFirst().orElseThrow();
            driver.switchTo().window(tab);
        }
        String shown = "";
        try {
            new WebDriverWait(driver, LOAD).until(d -> d.findElement(By.tagName("body")).getText().contains("SUBMITTED FILES")
                    || d.findElement(By.tagName("body")).getText().matches("(?s).*No submission.*"));
            pause(1500);
            shown = driver.findElement(By.tagName("body")).getText();
        } catch (TimeoutException e) {
            shown = driver.findElement(By.tagName("body")).getText();
        } finally {
            if (newTab) {
                driver.close();
                driver.switchTo().window(responses);
            } else {
                driver.navigate().back();
                new WebDriverWait(driver, LOAD).until(d -> TOTAL.matcher(mainText()).find());
            }
        }
        return shown.replaceAll("\\s+", " ").strip();
    }

    /**
     * A real click (a script click does not open the new tab), after the
     * corner notices ("Success · Responses refreshed") have gone: they sit
     * over the lower rows' View buttons.
     */
    private void clickPastToasts(By target) {
        By toasts = By.cssSelector("[aria-label^='Notifications'] li[data-state='open']");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(toasts).isEmpty());
        } catch (TimeoutException e) {
            for (WebElement close : driver.findElements(By.cssSelector("[aria-label^='Notifications'] li[data-state='open'] button"))) {
                try {
                    close.click();
                } catch (RuntimeException ignored) {
                    // it went by itself
                }
            }
        }
        for (int attempt = 1; ; attempt++) {
            WebElement el = driver.findElement(target);
            centre(el);
            try {
                el.click();
                return;
            } catch (org.openqa.selenium.ElementClickInterceptedException e) {
                if (attempt == 3) {
                    throw e;
                }
                pause(3000);
            }
        }
    }

    /**
     * One file of a Canvas submissions ZIP, named
     * &lt;login&gt;_[LATE_]&lt;Canvas user id&gt;_&lt;submission id&gt;_&lt;file&gt;, e.g.
     * student51494_262_30429_09-lab-set-permissions.zip.
     */
    public record Entry(String name, byte[] data) {
        private String[] parts() {
            return name.split("_", -1);
        }

        /** The part before the first "_", which the preview shows as the login (e.g. "student51494"). */
        public String login() {
            return parts()[0];
        }

        private int fileStart() {
            // login, [LATE], user id, submission id, then the file name (which may hold "_" itself)
            return "LATE".equals(parts()[1]) ? 4 : 3;
        }

        /** The student's own file name, e.g. "09-lab-set-permissions.zip". */
        public String file() {
            String[] p = parts();
            return String.join("_", java.util.Arrays.copyOfRange(p, fileStart(), p.length));
        }

        /** The same file for the same student, with its file name starting with the given text. */
        public Entry renamed(String prefix) {
            String[] p = parts();
            String head = String.join("_", java.util.Arrays.copyOfRange(p, 0, fileStart()));
            return new Entry(head + "_" + prefix + file(), data);
        }
    }

    /** The files in a ZIP, in order. */
    public static List<Entry> readZip(Path zip) {
        List<Entry> out = new ArrayList<>();
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                if (!e.isDirectory()) {
                    out.add(new Entry(e.getName(), in.readAllBytes()));
                }
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return out;
    }

    /** Writes the files into a new ZIP (the upload for one step). */
    public static Path writeZip(Path dir, String name, List<Entry> entries) {
        try {
            Files.createDirectories(dir);
            Path zip = dir.resolve(name);
            try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
                for (Entry e : entries) {
                    z.putNextEntry(new ZipEntry(e.name()));
                    z.write(e.data());
                    z.closeEntry();
                }
            }
            return zip;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Back from the preview to the upload step, with the file taken off. */
    public void backToUpload() {
        By back = By.xpath(DIALOG + "//button[normalize-space(.)='Back']");
        if (!driver.findElements(back).isEmpty()) {
            click(back);
        }
        if (!driver.findElements(REMOVE_FILE).isEmpty()) {
            click(REMOVE_FILE);
        }
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.presenceOfElementLocated(ZIP_INPUT));
    }

    /** Whether "Overwrite submission for &lt;name&gt;" is checked; null if there is no such box. */
    public Boolean overwriteChecked(String student) {
        By box = By.xpath(DIALOG + "//*[@aria-label=" + literal("Overwrite submission for " + student) + "]");
        List<WebElement> found = driver.findElements(box);
        if (found.isEmpty()) {
            return null;
        }
        WebElement b = found.get(0);
        return "true".equals(b.getDomAttribute("aria-checked")) || b.isSelected()
                || "checked".equals(b.getDomAttribute("data-state"));
    }

    // ---- the import window ----

    public void openImport() {
        hookRequests();
        WebElement b = find(OPEN_IMPORT);
        centre(b);
        b.click();
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.presenceOfElementLocated(ZIP_INPUT));
    }

    public boolean isOpen() {
        return !driver.findElements(WINDOW).isEmpty();
    }

    /** Uploads the ZIP and waits for the preview; returns the server's preview reply ({matched, unmatched, total_files}). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> upload(Path zip) {
        if (!driver.findElements(REMOVE_FILE).isEmpty()) {
            click(REMOVE_FILE);
        }
        int before = replies("preview").size();
        driver.findElement(ZIP_INPUT).sendKeys(zip.toAbsolutePath().toString());
        new WebDriverWait(driver, LOAD).until(d -> replies("preview").size() > before
                && !d.findElements(PREVIEW_SHOWN).isEmpty());
        List<Map<String, Object>> all = replies("preview");
        Object data = all.get(all.size() - 1).get("data");
        return data instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** The window's text, e.g. "3 of 4 submissions will be imported ... MATCHED (2) ...". */
    public String windowText() {
        return driver.findElements(WINDOW).stream().map(WebElement::getText).findFirst().orElse("")
                .replaceAll("\\s+", " ").strip();
    }

    /**
     * The colour of the list whose heading starts with this text ("Matched",
     * "Already Submitted", "Unmatched"): "green", "orange", "amber", "red",
     * or "" if the list is not shown. Read from the icon of the list's first
     * row as drawn (the app's own colour names, such as text-success, do not
     * say "green"), by its hue.
     */
    public String listColour(String heading) {
        By h = By.xpath(DIALOG + "//*[not(*)][starts-with(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), "
                + literal(heading.toUpperCase()) + ")]");
        List<WebElement> found = driver.findElements(h);
        if (found.isEmpty()) {
            return "";
        }
        String rgb = (String) ((JavascriptExecutor) driver).executeScript(
                // climb from the heading to the block that holds the list's rows, then take the first row's icon
                "let e = arguments[0], box = null;"
                        + "for (let i = 0; i < 4 && e && !box; i++, e = e.parentElement) box = e.querySelector('[class*=\"divide-y\"]');"
                        + "const icon = box && box.querySelector('svg');"
                        + "return icon ? getComputedStyle(icon).color : '';", found.get(0));
        Matcher m = Pattern.compile("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)").matcher(rgb == null ? "" : rgb);
        if (!m.find()) {
            return "unknown (no icon)";
        }
        float[] hsb = java.awt.Color.RGBtoHSB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), null);
        float hue = hsb[0] * 360;
        if (hsb[1] < 0.2f) {
            return "grey (" + rgb + ")";
        }
        String name = hue < 15 || hue >= 330 ? "red" : hue < 33 ? "orange" : hue < 60 ? "amber" : hue < 175 ? "green" : "blue";
        return name + " (" + rgb + ")";
    }

    public boolean aiEvaluationOn() {
        List<WebElement> s = driver.findElements(AI_SWITCH);
        return !s.isEmpty() && "true".equals(s.get(0).getDomAttribute("aria-checked"));
    }

    /** Ticks or unticks "Overwrite submission for &lt;name&gt;"; false if there is no such box. */
    public boolean setOverwrite(String student, boolean on) {
        By box = By.xpath(DIALOG + "//*[@aria-label=" + literal("Overwrite submission for " + student) + "]");
        List<WebElement> found = driver.findElements(box);
        if (found.isEmpty()) {
            return false;
        }
        WebElement b = found.get(0);
        boolean checked = "true".equals(b.getDomAttribute("aria-checked")) || b.isSelected()
                || "checked".equals(b.getDomAttribute("data-state"));
        if (checked != on) {
            centre(b);
            b.click();
        }
        return true;
    }

    /** Whether "Import N Submissions" can be clicked (it is disabled when nothing would be imported). */
    public boolean importEnabled() {
        List<WebElement> b = driver.findElements(IMPORT);
        return !b.isEmpty() && b.get(0).isEnabled() && !b.get(0).getText().matches("(?s).*\\bImport 0 Submission.*");
    }

    /** Import N Submissions; waits for the result and returns the server's import reply. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> importNow() {
        int before = replies("import").size();
        WebElement b = find(IMPORT);
        new WebDriverWait(driver, LOAD).until(d -> b.isEnabled());
        centre(b);
        b.click();
        new WebDriverWait(driver, Duration.ofMinutes(3)).until(d -> replies("import").size() > before);
        pause(1500);
        List<Map<String, Object>> all = replies("import");
        Object data = all.get(all.size() - 1).get("data");
        return data instanceof Map<?, ?> m ? (Map<String, Object>) m : all.get(all.size() - 1);
    }

    public void close() {
        if (!driver.findElements(DONE).isEmpty()) {
            driver.findElements(DONE).get(0).click();
        } else if (!driver.findElements(CLOSE).isEmpty()) {
            driver.findElements(CLOSE).get(0).click();
        }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(WINDOW).isEmpty());
        } catch (TimeoutException e) {
            driver.findElement(By.tagName("body")).sendKeys(org.openqa.selenium.Keys.ESCAPE);
        }
    }

    // ---- the ZIP ----

    // ---- the page's own requests ----

    /** Records the replies to /canvas-submissions/... requests in window.__lxpImport. */
    private void hookRequests() {
        ((JavascriptExecutor) driver).executeScript(
                "if (window.__lxpImportHooked) return;"
                        + "window.__lxpImportHooked = true; window.__lxpImport = [];"
                        + "const f = window.fetch;"
                        + "window.fetch = async (...a) => {"
                        + "  const r = await f(...a);"
                        + "  try {"
                        + "    const u = String((a[0] && a[0].url) || a[0]);"
                        + "    if (u.includes('/canvas-submissions/')) {"
                        + "      r.clone().json().then(j => window.__lxpImport.push({kind: u.split('/').pop(), status: r.status, body: j}))"
                        + "        .catch(() => window.__lxpImport.push({kind: u.split('/').pop(), status: r.status, body: {}}));"
                        + "    }"
                        + "  } catch (e) {}"
                        + "  return r;"
                        + "};");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> replies(String kind) {
        Object all = ((JavascriptExecutor) driver).executeScript("return window.__lxpImport || [];");
        List<Map<String, Object>> out = new ArrayList<>();
        if (all instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && String.valueOf(m.get("kind")).startsWith(kind)) {
                    Object body = m.get("body");
                    out.add(body instanceof Map<?, ?> b ? (Map<String, Object>) b : Map.of());
                }
            }
        }
        return out;
    }

    // ---- helpers ----

    private String mainText() {
        return driver.findElements(By.tagName("main")).stream().map(WebElement::getText).findFirst().orElse("");
    }

    private void centre(WebElement e) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", e);
    }

    private static String literal(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
