package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.Platform;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Speed grading (the app calls it Fast Grade) from the teacher's gradebook.
 * Labels are from the app's own code (staging, Nov 2026):
 * - Grades → Grid: each assessment's column header has a three-dots button
 *   ("Column options: Grade this assessment with Fast Grade.") whose menu has
 *   "Fast Grade". It opens a drawer on the right ("Grade student submission")
 *   holding the same page as /course/{id}/quickreview/{type}/{item}.
 * - Header: Settings (gear, top left), "Previous student", the student picker
 *   ("{name} — choose a different student", with "Search students…" and one
 *   button per student), "Next student".
 * - Grade panel: Status (None / Late / Missing / Excused); Late shows a
 *   "Days late" box; Excused hides "Grade out of N" and the grade reads "EX".
 *   Approve records the grade (Submit to Canvas is never clicked).
 * - Comments: the "Comment Library" button (title "Comment Library (N saved)")
 *   opens "Manage Comment Library": a "Show suggestions when typing" switch,
 *   "Show saved comments for" (All comments / This course / This assessment),
 *   "Search comments…", the saved comments, and "Add comment to library" with
 *   "Add to Library". While typing a comment, saved ones are offered under
 *   "Suggested from library".
 * - Settings: "Sort student list" (by student name (first name) / by
 *   submission date / by submission status / randomize …), "Hide student
 *   names in FastGrade", "Default Discussion View Options" (Only this
 *   student's posts / Their posts, in the conversation around them), and
 *   Save Options.
 */
public class SpeedGradingPage extends BasePage {

    private static final Duration LOAD = Duration.ofSeconds(60);

    /** The Fast Grade drawer; everything below is looked for inside it. */
    private static final String DRAWER = "//div[@role='dialog'][.//*[normalize-space(text())='Grade student submission'] or .//button[@aria-label='Next student']]";

    private static final By MENU_FAST_GRADE = By.xpath("//*[@role='menuitem'][normalize-space(.)='Fast Grade']");
    private static final By NEXT = By.xpath(DRAWER + "//button[@aria-label='Next student']");
    private static final By PREVIOUS = By.xpath(DRAWER + "//button[@aria-label='Previous student']");
    private static final By PICKER = By.xpath(DRAWER + "//button[contains(@aria-label, 'choose a different student') or @aria-label='Choose a student']");
    private static final By STUDENT_SEARCH = By.xpath(DRAWER + "//input[@aria-label='Search students']");
    private static final By STATUS = By.xpath(DRAWER + "//button[@aria-label='Status']");
    private static final By DAYS_LATE = By.xpath(DRAWER + "//input[@aria-label='Days late']");
    private static final By GRADE_INPUT = By.xpath(DRAWER + "//input[starts-with(@aria-label, 'Grade out of')]");
    private static final By LIBRARY_BUTTON = By.xpath(DRAWER + "//button[starts-with(@title, 'Comment Library') or @title='Hide Comment Library']");
    private static final By LIBRARY_TITLE = By.xpath(DRAWER + "//*[normalize-space(text())='Manage Comment Library']");
    private static final By LIBRARY_CLOSE = By.xpath(DRAWER + "//button[@aria-label='Close comment library']");
    private static final By SUGGESTIONS_SWITCH = By.xpath(DRAWER + "//*[@id='suggestions-toggle']");
    private static final By LIBRARY_FILTER = By.xpath(DRAWER + "//button[@aria-label='Show saved comments for']");
    private static final By LIBRARY_NEW = By.xpath(DRAWER + "//textarea[@aria-label='Add comment to library']");
    private static final By LIBRARY_ADD = By.xpath(DRAWER + "//button[normalize-space(.)='Add to Library']");
    private static final By SETTINGS = By.xpath(DRAWER + "//button[@aria-label='Settings']");
    private static final By SORT = By.id("quick-review-sort-mode");
    private static final By HIDE_NAMES = By.xpath("//label[.//*[normalize-space(.)='Hide student names in FastGrade']]//input[@type='checkbox']");
    private static final By SAVE_OPTIONS = By.xpath("//button[normalize-space(.)='Save Options']");

    public SpeedGradingPage(WebDriver driver) {
        super(driver);
    }

    // ---- opening it from the gradebook ----

    /**
     * Grid: the column header whose name contains this text → its three-dots
     * button → Fast Grade. The button only shows on hover, so the header is
     * hovered first and the button clicked by script if still hidden.
     */
    public void openFromColumn(String assessment) {
        By header = By.xpath("//main//table//thead//th[.//*[contains(normalize-space(text()), " + literal(assessment) + ")]]");
        WebElement th = new WebDriverWait(driver, LOAD).until(ExpectedConditions.presenceOfElementLocated(header));
        js("arguments[0].scrollIntoView({block: 'center', inline: 'center'})", th);
        new org.openqa.selenium.interactions.Actions(driver).moveToElement(th).perform();
        WebElement dots = th.findElement(By.xpath(".//button[starts-with(@aria-label, 'Column options')]"));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3)).until(ExpectedConditions.elementToBeClickable(dots)).click();
        } catch (RuntimeException e) {
            js("arguments[0].click();", dots);
        }
        if (!isVisible(MENU_FAST_GRADE, Duration.ofSeconds(5))) {
            // Radix menus open on pointerdown: send one if the click did not open it
            js("arguments[0].dispatchEvent(new PointerEvent('pointerdown', {bubbles: true, button: 0, pointerType: 'mouse'}));", dots);
        }
        click(MENU_FAST_GRADE);
        waitLoaded();
    }

    /** The drawer is up and past "Loading Fast Grade…". */
    public void waitLoaded() {
        new WebDriverWait(driver, LOAD).until(d -> Boolean.TRUE.equals(js(
                "const d = document.evaluate(arguments[0], document, null, 9, null).singleNodeValue;"
                + "return !!d && !d.innerText.includes('Loading Fast Grade') && /THE GRADE/i.test(d.innerText);", DRAWER)));
    }

    public boolean open() {
        return !driver.findElements(By.xpath(DRAWER)).isEmpty();
    }

    /** The drawer's heading line (assessment title …). */
    public String title() {
        return drawerText().split("\n")[0];
    }

    public void close() {
        driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(By.xpath(DRAWER)).isEmpty());
        } catch (TimeoutException e) {
            List<WebElement> x = driver.findElements(By.xpath(DRAWER + "//button[@aria-label='Close' or normalize-space(.)='Close']"));
            if (!x.isEmpty()) {
                js("arguments[0].click();", x.get(0));
            }
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(By.xpath(DRAWER)).isEmpty());
        }
    }

    // ---- what the drawer shows ----

    public String drawerText() {
        Object s = js("const d = document.evaluate(arguments[0], document, null, 9, null).singleNodeValue; return d ? d.innerText : '';", DRAWER);
        return s == null ? "" : s.toString();
    }

    /** The student's work (left side): from the drawer's text, everything before "THE GRADE". */
    public String work() {
        String text = drawerText().replaceAll("\\s+", " ");
        int grade = text.toUpperCase().indexOf("THE GRADE");
        return (grade > 0 ? text.substring(0, grade) : text).strip();
    }

    /** Is the grade box there and does it take a value? Types it, reads it back, and puts the old value back. */
    public boolean gradeEditable(String probe) {
        List<WebElement> in = driver.findElements(GRADE_INPUT);
        if (in.isEmpty() || !in.get(0).isEnabled()) {
            return false;
        }
        WebElement box = in.get(0);
        String old = String.valueOf(box.getDomProperty("value"));
        replace(box, probe);
        boolean took = probe.equals(String.valueOf(box.getDomProperty("value")).strip());
        replace(box, old.equals("null") ? "" : old);
        return took;
    }

    /**
     * The "Comment to student" box: the first editable box (rich text editor
     * or textarea) after that label, leaving out the library and search
     * boxes. If it is folded away behind "Add a comment", that is clicked first.
     */
    private WebElement commentBox() {
        String find = "const d = document.evaluate(arguments[0], document, null, 9, null).singleNodeValue; if (!d) return null;"
                + "const leaves = [...d.querySelectorAll('*')].filter(e => e.children.length === 0);"
                + "const label = leaves.find(e => /^comment to student$/i.test(e.textContent.trim()));"
                + "const skip = ['Add comment to library', 'Search comments', 'Search students', 'Days late', 'HTML source code'];"
                + "const boxes = [...d.querySelectorAll(\"[contenteditable]:not([contenteditable='false']), textarea\")]"
                + "  .filter(b => !skip.includes(b.getAttribute('aria-label')) && !(b.getAttribute('aria-label') || '').startsWith('Grade out of'));"
                + "if (label) { const after = boxes.find(b => label.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING); if (after) return after; }"
                + "return null;";
        Object el = js(find, DRAWER);
        if (el == null) {
            // with earlier comments the editor is behind "Reply to comment"
            List<WebElement> add = driver.findElements(By.xpath(DRAWER + "//button[normalize-space(.)='Reply to comment' or contains(normalize-space(.), 'Add a comment')]"));
            if (!add.isEmpty()) {
                js("arguments[0].scrollIntoView({block: 'center'}); arguments[0].click();", add.get(0));
                pause(1000);
                el = js(find, DRAWER);
            }
        }
        return (WebElement) el;
    }

    /** For a report: the editable boxes in the drawer, by label. */
    public String editableBoxes() {
        Object s = js("const d = document.evaluate(arguments[0], document, null, 9, null).singleNodeValue; if (!d) return 'no drawer';"
                + "return JSON.stringify([...d.querySelectorAll(\"[contenteditable]:not([contenteditable='false']), textarea, input\")]"
                + "  .map(b => b.tagName.toLowerCase() + ':' + (b.getAttribute('aria-label') || b.getAttribute('placeholder') || b.id || '?')));", DRAWER);
        return s == null ? "" : s.toString();
    }

    /** Can a comment be written? Types it, checks it shows, then clears it again (nothing is saved). */
    public boolean commentEditable(String probe) {
        WebElement box = commentBox();
        if (box == null) {
            return false;
        }
        typeInto(box, probe);
        boolean took = box.getText().contains(probe) || String.valueOf(box.getDomProperty("value")).contains(probe);
        clear(box);
        return took;
    }

    // ---- students ----

    public String student() {
        Object s = js("const d = document.evaluate(arguments[0], document, null, 9, null).singleNodeValue; if (!d) return '';"
                + "const b = d.querySelector(\"button[aria-label$=' — choose a different student']\");"
                + "return b ? b.getAttribute('aria-label').replace(/ — choose a different student$/, '') : '';", DRAWER);
        return s == null ? "" : s.toString();
    }

    /** The picker's list: each student's name, in the order shown. */
    public List<String> studentList() {
        openPicker();
        // the search box sits in a wrapper; the student buttons are in its sibling list
        Object names = js("const s = arguments[0];"
                + "const list = s.closest('div').parentElement.parentElement;"
                + "return [...list.querySelectorAll('button')].map(b => b.querySelector('.truncate'))"
                + "  .filter(Boolean).map(e => e.textContent.trim());", find(STUDENT_SEARCH));
        List<String> out = new ArrayList<>();
        if (names instanceof List<?> l) {
            l.stream().map(String::valueOf).distinct().forEach(out::add);
        }
        closePicker();
        return out;
    }

    /** Opens the picker and clicks the first student who is not the one shown; returns who was picked. */
    public String pickAnotherStudent() {
        String now = student();
        openPicker();
        WebElement other = (WebElement) js("const s = arguments[0], now = arguments[1];"
                + "const list = s.closest('div').parentElement.parentElement;"
                + "return [...list.querySelectorAll('button')].find(b => { const n = b.querySelector('.truncate'); return n && n.textContent.trim() !== now; }) || null;",
                find(STUDENT_SEARCH), now);
        if (other == null) {
            closePicker();
            return "";
        }
        String name = other.findElement(By.cssSelector(".truncate")).getText().strip();
        other.click();
        waitForStudentChange(now);
        return name;
    }

    private void openPicker() {
        if (driver.findElements(STUDENT_SEARCH).isEmpty()) {
            WebElement b = find(PICKER);
            js("arguments[0].scrollIntoView({block: 'center'})", b);
            try {
                new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(b)).click();
            } catch (RuntimeException e) {
                js("arguments[0].click();", b);
            }
            find(STUDENT_SEARCH);
        }
    }

    /** The picker button toggles the list. (Escape is never used: it closes the whole Fast Grade drawer.) */
    private void closePicker() {
        for (int i = 0; i < 2 && !driver.findElements(STUDENT_SEARCH).isEmpty(); i++) {
            js("arguments[0].click();", driver.findElement(PICKER));
            try {
                new WebDriverWait(driver, Duration.ofSeconds(3)).until(d -> d.findElements(STUDENT_SEARCH).isEmpty());
            } catch (TimeoutException ignored) {
                // try once more
            }
        }
    }

    public boolean canGoNext() {
        List<WebElement> b = driver.findElements(NEXT);
        return !b.isEmpty() && b.get(0).isEnabled();
    }

    public boolean canGoPrevious() {
        List<WebElement> b = driver.findElements(PREVIOUS);
        return !b.isEmpty() && b.get(0).isEnabled();
    }

    public void next() {
        String now = student();
        click(NEXT);
        waitForStudentChange(now);
    }

    public void previous() {
        String now = student();
        click(PREVIOUS);
        waitForStudentChange(now);
    }

    private void waitForStudentChange(String from) {
        new WebDriverWait(driver, LOAD).until(d -> !student().equals(from));
        // the work and grade load after the name
        pause(1500);
        waitLoaded();
    }

    /** Shows this student (by the picker's search); true if shown. */
    public boolean showStudent(String name) {
        if (student().equals(name)) {
            return true;
        }
        String now = student();
        openPicker();
        WebElement search = find(STUDENT_SEARCH);
        search.sendKeys(name);
        List<WebElement> hit = driver.findElements(By.xpath(DRAWER + "//button[.//*[normalize-space(text())=" + literal(name) + "]]"));
        if (hit.isEmpty()) {
            closePicker();
            return false;
        }
        hit.get(0).click();
        waitForStudentChange(now);
        return student().equals(name);
    }

    // ---- status ----

    public void chooseStatus(String status) {
        WebElement s = find(STATUS);
        js("arguments[0].scrollIntoView({block: 'center'})", s);
        s.click();
        click(By.xpath("//*[@role='option'][normalize-space(.)=" + literal(status) + "]"));
        wait.until(d -> d.findElement(STATUS).getText().strip().equalsIgnoreCase(status));
    }

    public String status() {
        return find(STATUS).getText().strip();
    }

    public boolean daysLateShown() {
        return !driver.findElements(DAYS_LATE).isEmpty() && driver.findElement(DAYS_LATE).isDisplayed();
    }

    /** Types into "Days late" and reads it back. */
    public String typeDaysLate(String days) {
        WebElement box = find(DAYS_LATE);
        replace(box, days);
        return String.valueOf(box.getDomProperty("value")).strip();
    }

    /** Puts this grade in "Grade out of N"; returns what the box holds after. */
    public String setGrade(String grade) {
        WebElement box = find(GRADE_INPUT);
        replace(box, grade);
        return String.valueOf(box.getDomProperty("value")).strip();
    }

    /** What "Grade out of N" holds now ("" if not shown). */
    public String grade() {
        List<WebElement> in = driver.findElements(GRADE_INPUT);
        return in.isEmpty() ? "" : String.valueOf(in.get(0).getDomProperty("value")).strip();
    }

    public boolean gradeBoxShown() {
        return !driver.findElements(GRADE_INPUT).isEmpty();
    }

    /** The GRADE shown in the panel's footer, e.g. "EX", "7", "—". */
    public String footerGrade() {
        Object s = js("const d = document.evaluate(arguments[0], document, null, 9, null).singleNodeValue; if (!d) return '';"
                + "const g = [...d.querySelectorAll('*')].filter(e => e.children.length === 0 && e.textContent.trim().toUpperCase() === 'GRADE').pop();"
                + "if (!g) return ''; let p = g.parentElement; while (p && p.innerText.trim().toUpperCase() === 'GRADE') p = p.parentElement;"
                + "return p ? p.innerText.replace(/\\s+/g, ' ').replace(/^GRADE\\s*/i, '').trim() : '';", DRAWER);
        return s == null ? "" : s.toString();
    }

    // ---- comment library ----

    public void openLibrary() {
        if (driver.findElements(LIBRARY_TITLE).isEmpty()) {
            WebElement b = find(LIBRARY_BUTTON);
            js("arguments[0].scrollIntoView({block: 'center'})", b);
            b.click();
            new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(LIBRARY_TITLE));
        }
    }

    public void closeLibrary() {
        if (!driver.findElements(LIBRARY_CLOSE).isEmpty()) {
            click(LIBRARY_CLOSE);
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(LIBRARY_TITLE).isEmpty());
        }
    }

    /** Writes a comment in "Add comment to library" and clicks Add to Library. */
    public void addToLibrary(String comment) {
        openLibrary();
        WebElement box = find(LIBRARY_NEW);
        replace(box, comment);
        wait.until(d -> d.findElement(LIBRARY_ADD).isEnabled());
        click(LIBRARY_ADD);
        new WebDriverWait(driver, LOAD).until(d -> !d.findElement(LIBRARY_ADD).getText().contains("Adding"));
    }

    /** The saved comments listed in the library now. */
    public String libraryList() {
        Object s = js("const t = [...document.querySelectorAll('*')].find(e => e.children.length === 0 && e.textContent.trim() === 'Manage Comment Library');"
                + "if (!t) return '';"
                + "let p = t; while (p.parentElement && !p.querySelector(\"textarea[aria-label='Add comment to library']\")) p = p.parentElement;"
                + "const scroll = p.querySelector('.overflow-y-auto') || p; return scroll.innerText;");
        return s == null ? "" : s.toString();
    }

    /** Waits up to 10s for the library list to (not) hold this text. */
    public boolean libraryShows(String text, boolean expected) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> libraryList().contains(text) == expected);
            return expected;
        } catch (TimeoutException e) {
            return !expected;
        }
    }

    public boolean suggestionsOn() {
        WebElement s = find(SUGGESTIONS_SWITCH);
        return "true".equals(s.getDomAttribute("aria-checked")) || "checked".equals(s.getDomAttribute("data-state"));
    }

    public void setSuggestions(boolean on) {
        openLibrary();
        if (suggestionsOn() != on) {
            find(SUGGESTIONS_SWITCH).click();
            wait.until(d -> suggestionsOn() == on);
        }
    }

    /** "Show saved comments for": All comments / This course / This assessment. */
    public void filterLibrary(String option) {
        openLibrary();
        click(LIBRARY_FILTER);
        click(By.xpath("//*[@role='option'][normalize-space(.)=" + literal(option) + "]"));
        wait.until(d -> d.findElement(LIBRARY_FILTER).getText().strip().equals(option));
        pause(1500);
    }

    /**
     * Types the start of a saved comment in "Comment to student" and returns
     * what the "Suggested from library" list offers ("" if nothing). The
     * typed text is cleared again afterwards.
     */
    public String suggestionsFor(String typed) {
        closeLibrary();
        WebElement box = commentBox();
        typeInto(box, typed);
        String offered = "";
        try {
            offered = new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
                Object s = js("const h = [...document.querySelectorAll('*')].find(e => e.children.length === 0 && /Suggested from library/i.test(e.textContent));"
                        + "if (!h) return null; let p = h; while (p.parentElement && p.innerText.length < h.textContent.length + 10) p = p.parentElement;"
                        + "return p.innerText.replace(/\\s+/g, ' ').trim();");
                return s == null ? null : s.toString();
            });
        } catch (TimeoutException ignored) {
            // nothing offered
        }
        List<WebElement> closeSuggestions = driver.findElements(By.xpath(DRAWER + "//button[@aria-label='Close suggestions']"));
        if (!closeSuggestions.isEmpty()) {
            js("arguments[0].click();", closeSuggestions.get(0));
        }
        clear(box);
        return offered;
    }

    // ---- settings ----

    public void openSettings() {
        click(SETTINGS);
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(SORT));
    }

    public void sortBy(String option) {
        new Select(find(SORT)).selectByVisibleText(option);
    }

    public void hideNames(boolean hide) {
        WebElement c = find(HIDE_NAMES);
        if (c.isSelected() != hide) {
            js("arguments[0].click();", c);
        }
    }

    /** "Only this student's posts" or "Their posts, in the conversation around them". */
    public void discussionView(String label) {
        WebElement radio = find(By.xpath("//label[.//*[normalize-space(.)=" + literal(label) + "]]//input[@type='radio']"));
        js("arguments[0].click();", radio);
    }

    public void saveOptions() {
        click(SAVE_OPTIONS);
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.invisibilityOfElementLocated(SORT));
        pause(1500);
        waitLoaded();
    }

    // ---- the gradebook grid ----

    /** Grid: the student's cell under the column whose name contains this text; "" if not found. */
    public String gridCell(String student, String assessment) {
        Object s = js("const name = arguments[0], col = arguments[1];"
                + "const t = document.querySelector('main table'); if (!t) return '';"
                + "const heads = [...t.querySelectorAll('thead th')];"
                + "const i = heads.findIndex(h => h.innerText.includes(col)); if (i < 0) return '';"
                + "const row = [...t.querySelectorAll('tbody tr')].find(r => r.cells[0] && r.cells[0].innerText.split('\\n')[0].trim() === name);"
                + "return row && row.cells[i] ? row.cells[i].innerText.replace(/\\s+/g, ' ').trim() : '';", student, assessment);
        return s == null ? "" : s.toString();
    }

    // ---- helpers ----

    private void replace(WebElement box, String text) {
        js("arguments[0].scrollIntoView({block: 'center'})", box);
        box.click();
        Keys mod = Platform.getCurrent().is(Platform.MAC) ? Keys.COMMAND : Keys.CONTROL;
        box.sendKeys(Keys.chord(mod, "a"), Keys.BACK_SPACE);
        if (!text.isEmpty()) {
            box.sendKeys(text);
        }
    }

    private void typeInto(WebElement box, String text) {
        js("arguments[0].scrollIntoView({block: 'center'})", box);
        box.click();
        box.sendKeys(text);
    }

    private void clear(WebElement box) {
        Keys mod = Platform.getCurrent().is(Platform.MAC) ? Keys.COMMAND : Keys.CONTROL;
        box.click();
        box.sendKeys(Keys.chord(mod, "a"), Keys.BACK_SPACE);
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
