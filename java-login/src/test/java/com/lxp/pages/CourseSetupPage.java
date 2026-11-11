package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A course's Course Setup window (staging, Nov 2026) and the course it
 * generates.
 *
 * Course Setup has six stages in its left list: 1 Syllabus & Schedule,
 * 2 Outline, 3 Tools & Labs, 4 Materials, 5 Details, 6 Assessments & Lectures
 * (with the sub-tabs Assessments, Lectures, Structure and Syllabus). A stage
 * shows "Loading ..." for a few seconds after it is opened. Changes are saved
 * as they are made (there is also Save & Close).
 *
 * Fields used here, by stage:
 * - Syllabus & Schedule: Number of modules (1-20), Lecture hours per week,
 *   First day / Last day, and "Clear the dates" (resets both dates).
 * - Outline: Custom instructions (naming and other rules for generation).
 * - Tools & Labs: where hands-on work happens (LXP sandbox labs / Their own
 *   computer / No labs).
 * - Details: Target Audience (multi-select, up to 5), Prior Knowledge Level
 *   (Beginner / Intermediate / Advanced / Expert), Teaching Style (Hands-On /
 *   Theoretical / Case Study / Custom), "Use only uploaded resources",
 *   Custom instructions.
 * - Assessments & Lectures → Assessments: Number of questions (1-100), Type of
 *   Question Distribution (MCQ Only / Balanced / MCQ Heavy / Short Answer
 *   Heavy / True/False Heavy / Essay Heavy), quiz Custom Prompt.
 * - Assessments & Lectures → Lectures: Lecture Structure (Book Type /
 *   Independent), lecture Custom Instructions.
 * - Assessments & Lectures → Syllabus: Institution.
 *
 * The generated course: the course page header reads "N modules · M lessons";
 * items list "Due: Nov 9, 2026, 11:59 PM"; a quiz's Questions tab lists
 * "Questions: N" and each question's type, e.g. "Multiple Choice 1 pt".
 */
public class CourseSetupPage extends BasePage {

    private static final Duration LOAD = Duration.ofSeconds(60);

    private static final String DIALOG = "(//div[@role='dialog'][@data-state='open'][.//h2[normalize-space(.)='Course Setup']])[last()]";
    private static final By SETUP_TITLE = By.xpath(DIALOG + "//h2[normalize-space(.)='Course Setup']");
    private static final By COURSE_SETUP = By.xpath("//main//button[.//*[normalize-space(.)='Course Setup']]");
    private static final By CLOSE = By.xpath("(" + DIALOG + "//button[normalize-space(.)='Close'])[last()]");
    private static final By FIRST_DAY = By.xpath(DIALOG + "//label[starts-with(normalize-space(.), 'First day')]/following::button[1]");
    private static final By LAST_DAY = By.xpath(DIALOG + "//label[starts-with(normalize-space(.), 'Last day')]/following::button[1]");
    private static final By CLEAR_DATES = By.xpath(DIALOG + "//button[@aria-label='Clear the dates' or normalize-space(.)='Clear the dates']");
    private static final By AUDIENCE = By.xpath(DIALOG + "//*[normalize-space(text())='Target Audience']/following::button[1]");
    private static final By GENERATING_COURSE = By.xpath("//*[normalize-space(text())='Generating Course']");
    private static final Pattern COUNTS = Pattern.compile("(\\d+) modules? · (\\d+) lessons?");

    /** Where a field lives and how it is set and read. */
    private record Field(String stage, String tab, String id, String kind) {
    }

    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("Number of modules", new Field("Syllabus & Schedule", null, "number-of-modules", "text"));
        FIELDS.put("Lecture hours per week", new Field("Syllabus & Schedule", null, "hours-per-week", "text"));
        FIELDS.put("Outline custom instructions", new Field("Outline", null, "custom-prompt-instructions", "text"));
        FIELDS.put("Hands-on work", new Field("Tools & Labs", null, "hands-on", "radio"));
        FIELDS.put("Target Audience", new Field("Details", null, null, "audience"));
        FIELDS.put("Prior Knowledge Level", new Field("Details", null, "learning-level", "combobox"));
        FIELDS.put("Teaching Style", new Field("Details", null, "teaching-style", "combobox"));
        FIELDS.put("Details custom instructions", new Field("Details", null, "custom-prompt", "text"));
        FIELDS.put("Number of questions", new Field("Assessments & Lectures", "Assessments", "quiz-question-count", "text"));
        FIELDS.put("Question distribution", new Field("Assessments & Lectures", "Assessments", "quiz-distribution", "combobox"));
        FIELDS.put("Quiz custom prompt", new Field("Assessments & Lectures", "Assessments", "quiz-custom-prompt", "text"));
        FIELDS.put("Lecture structure", new Field("Assessments & Lectures", "Lectures", "lecture-format", "combobox"));
        FIELDS.put("Lecture custom instructions", new Field("Assessments & Lectures", "Lectures", "lecture-custom-prompt", "text"));
        FIELDS.put("Institution", new Field("Assessments & Lectures", "Syllabus", "syllabus-institution", "text"));
    }

    /** Every stage and sub-tab, in order, for "switch through all the tabs". */
    public static final List<String[]> ALL_TABS = List.of(
            new String[] {"Syllabus & Schedule", null}, new String[] {"Outline", null},
            new String[] {"Tools & Labs", null}, new String[] {"Materials", null}, new String[] {"Details", null},
            new String[] {"Assessments & Lectures", "Assessments"}, new String[] {"Assessments & Lectures", "Lectures"},
            new String[] {"Assessments & Lectures", "Structure"}, new String[] {"Assessments & Lectures", "Syllabus"});

    private String courseUrl;

    public CourseSetupPage(WebDriver driver) {
        super(driver);
    }

    public void rememberCourse() {
        courseUrl = driver.getCurrentUrl().replaceFirst("\\?.*$", "");
    }

    public String courseUrl() {
        return courseUrl;
    }

    public static boolean knows(String field) {
        return FIELDS.containsKey(field);
    }

    public static String stageOf(String field) {
        Field f = FIELDS.get(field);
        return f.stage() + (f.tab() == null ? "" : " → " + f.tab());
    }

    // ---- the window ----

    public boolean isOpen() {
        return !driver.findElements(SETUP_TITLE).isEmpty();
    }

    /** Opens Course Setup from the course page (a new course opens it by itself). */
    public void open() {
        if (isOpen()) {
            return;
        }
        if (!driver.getCurrentUrl().startsWith(courseUrl)) {
            driver.get(courseUrl);
        }
        if (!isVisible(SETUP_TITLE, Duration.ofSeconds(10))) {
            WebElement b = new WebDriverWait(driver, LOAD).until(ExpectedConditions.elementToBeClickable(COURSE_SETUP));
            centre(b);
            b.click();
        }
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(SETUP_TITLE));
    }

    /** The window's own Close (X), not Save & Close: whatever is kept was saved as it was typed. */
    public void closeWithoutSaving() {
        click(CLOSE);
        new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> d.findElements(SETUP_TITLE).isEmpty());
    }

    /** A stage in the left list (e.g. "Details"), then a sub-tab if given (e.g. "Lectures"). */
    public void openTab(String stage, String tab) {
        By nav = By.xpath(DIALOG + "//nav//button[contains(normalize-space(.), " + literal(stage) + ")]");
        WebElement b = find(nav);
        centre(b);
        b.click();
        waitLoaded();
        if (tab != null) {
            WebElement t = find(By.xpath(DIALOG + "//button[normalize-space(.)=" + literal(tab) + "]"));
            centre(t);
            t.click();
            waitLoaded();
        }
    }

    /** A stage shows "Loading ..." while it fetches its data. */
    private void waitLoaded() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
                String text = d.findElement(By.xpath(DIALOG)).getText();
                return !text.contains("Loading ");
            });
        } catch (TimeoutException ignored) {
            // read what is there
        }
        pause(800);
    }

    // ---- setting and reading fields ----

    /** Opens the field's stage and sets it. */
    public void set(String field, String value) {
        Field f = field(field);
        openTab(f.stage(), f.tab());
        switch (f.kind()) {
            case "text" -> typeOver(By.id(f.id()), value);
            case "combobox" -> choose(By.id(f.id()), value);
            case "radio" -> clickRadio(f.id(), value);
            case "audience" -> chooseAudience(value);
            default -> throw new IllegalStateException(f.kind());
        }
        // let the autosave go out
        pause(1500);
    }

    /** Opens the field's stage and reads what it shows. */
    public String read(String field) {
        Field f = field(field);
        openTab(f.stage(), f.tab());
        return readShown(f);
    }

    /**
     * Opens the field's stage and reads it until it shows what was set, for up
     * to 15 s: a stage first shows its defaults (e.g. 10 questions, empty
     * instructions) and then the saved values. Returns the last value read.
     */
    public String readSettled(String field, String expected) {
        Field f = field(field);
        openTab(f.stage(), f.tab());
        long deadline = System.currentTimeMillis() + 15_000;
        String shown = readShown(f);
        while (!matches(field, expected, shown) && System.currentTimeMillis() < deadline) {
            pause(1000);
            shown = readShown(f);
        }
        return shown;
    }

    private String readShown(Field f) {
        return switch (f.kind()) {
            case "text" -> String.valueOf(find(By.id(f.id())).getDomProperty("value")).strip();
            case "combobox" -> find(By.id(f.id())).getText().replaceAll("\\s+", " ").strip();
            case "radio" -> checkedRadio(f.id());
            case "audience" -> find(AUDIENCE).getText().replaceAll("\\s+", " ").strip();
            default -> throw new IllegalStateException(f.kind());
        };
    }

    /** Whether what a field shows matches what was set: a choice shows its full label ("Beginner - No prior knowledge"). */
    public static boolean matches(String field, String set, String shown) {
        Field f = FIELDS.get(field);
        if (f != null && f.kind().equals("text")) {
            return shown.equals(set);
        }
        return shown.toLowerCase().contains(set.toLowerCase());
    }

    private Field field(String name) {
        Field f = FIELDS.get(name);
        if (f == null) {
            throw new IllegalArgumentException("Unknown Course Setup field: " + name + " (use one of " + FIELDS.keySet() + ")");
        }
        return f;
    }

    // React keeps its own copy of the value: select all and type over it
    private void typeOver(By locator, String value) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
        centre(el);
        el.click();
        el.sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.BACK_SPACE);
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        el.sendKeys(value);
        // leave the field so the change is committed
        el.sendKeys(Keys.TAB);
    }

    /** A Radix select: open it and pick the option whose text starts with the value. */
    private void choose(By locator, String value) {
        WebElement box = find(locator);
        centre(box);
        box.click();
        By option = By.xpath("//*[@role='option'][starts-with(normalize-space(.), " + literal(value) + ")]");
        click(option);
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(By.cssSelector("[role='listbox']")).isEmpty());
    }

    /** Radio cards: ids like hands-on-own or generation-mode-low; picked by the card's visible title. */
    private void clickRadio(String group, String value) {
        By label = By.xpath(DIALOG + "//label[starts-with(@for, " + literal(group + "-") + ")]"
                + "[starts-with(normalize-space(.), " + literal(value) + ")]");
        WebElement l = find(label);
        WebElement r = find(By.id(l.getDomAttribute("for")));
        centre(r);
        r.click();
    }

    private String checkedRadio(String group) {
        Object label = js("const g = arguments[0];"
                + "const r = [...document.querySelectorAll(\"button[role='radio']\")].find(b => b.id.startsWith(g + '-')"
                + "  && b.getAttribute('aria-checked') === 'true');"
                + "if (!r) return '';"
                + "const l = document.querySelector(\"label[for='\" + r.id + \"']\");"
                + "return (l ? l.innerText : r.innerText).replace(/\\s+/g, ' ').trim();", group);
        return label == null ? "" : label.toString();
    }

    /** Target Audience: a list of check boxes (up to 5). Picks each comma-separated name not yet ticked. */
    private void chooseAudience(String names) {
        // the list opens inside the window, under the button, with a "Search options..." box;
        // the same button folds it away again (Escape would close the whole window)
        By search = By.xpath(DIALOG + "//input[@placeholder='Search options...']");
        WebElement box = find(AUDIENCE);
        centre(box);
        box.click();
        find(search);
        for (String name : names.split(",")) {
            By row = By.xpath(DIALOG + "//*[normalize-space(text())=" + literal(name.strip()) + "]"
                    + "[ancestor::*[.//input[@placeholder='Search options...']]]");
            WebElement r = find(row);
            centre(r);
            r.click();
            pause(400);
        }
        WebElement again = find(AUDIENCE);
        centre(again);
        again.click();
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> d.findElements(search).isEmpty());
        } catch (TimeoutException ignored) {
            // some versions keep it open; it does not get in the way
        }
    }

    // ---- dates ----

    public void clearDates() {
        openTab("Syllabus & Schedule", null);
        WebElement b = find(CLEAR_DATES);
        centre(b);
        b.click();
        By confirm = By.xpath("//*[@role='alertdialog' or @role='dialog'][not(.//h2[normalize-space(.)='Course Setup'])]"
                + "//button[normalize-space(.)='Clear' or normalize-space(.)='Clear dates' or normalize-space(.)='Confirm' or normalize-space(.)='Yes']");
        if (isVisible(confirm, Duration.ofSeconds(3))) {
            click(confirm);
        }
        pause(1500);
    }

    /** First and last day as shown, e.g. ["Nov 04, 2026", "Jan 28, 2027"], or "Pick date" when empty. */
    public List<String> dates() {
        openTab("Syllabus & Schedule", null);
        // the stage can still be filling in after "Loading" has gone
        isVisible(FIRST_DAY, Duration.ofSeconds(20));
        List<String> out = new ArrayList<>();
        for (By d : List.of(FIRST_DAY, LAST_DAY)) {
            List<WebElement> found = driver.findElements(d);
            out.add(found.isEmpty() ? "not shown" : found.get(0).getText().strip());
        }
        return out;
    }

    /** The Weeks & modules table as shown: "Module 1 Nov 4 – Nov 10 ...". */
    public String scheduleSummary() {
        openTab("Syllabus & Schedule", null);
        String text = find(By.xpath(DIALOG)).getText().replaceAll("\\s+", " ");
        int i = text.indexOf("Weeks & modules");
        return i < 0 ? "" : text.substring(i, Math.min(text.length(), i + 700));
    }

    // ---- generation ----

    /**
     * Waits until the course has been generated: the course page no longer
     * shows "Generating Course" and its header counts lessons, twice in a row.
     * Waits on the light home page in between (the course page is heavy while
     * it generates and has crashed Chrome).
     */
    public String waitUntilGenerated(Duration timeout, java.util.function.Consumer<String> progress) {
        long start = System.currentTimeMillis();
        long deadline = start + timeout.toMillis();
        int doneInARow = 0;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                driver.get(courseUrl + "?type=course");
                new WebDriverWait(driver, LOAD).until(d -> COUNTS.matcher(d.findElement(By.tagName("main")).getText()).find());
                pause(5000);
                boolean generating = !driver.findElements(GENERATING_COURSE).isEmpty();
                Matcher m = COUNTS.matcher(driver.findElement(By.tagName("main")).getText());
                last = m.find() ? m.group() : "";
                int lessons = last.isEmpty() ? 0 : Integer.parseInt(last.replaceAll(".*· (\\d+) lessons?", "$1"));
                doneInARow = !generating && lessons > 0 ? doneInARow + 1 : 0;
                progress.accept((generating ? "still generating" : "not generating") + ", " + last
                        + " after ~" + (System.currentTimeMillis() - start) / 60_000 + " min");
                if (doneInARow >= 2) {
                    return last;
                }
            } catch (RuntimeException e) {
                if (com.lxp.driver.DriverFactory.isBrowserGone(e)) {
                    // Chrome crashed: a new one with the saved session; generation carries on on the server
                    com.lxp.driver.DriverFactory.restartKeepingSession();
                    progress.accept("the browser crashed and was restarted, still waiting");
                } else {
                    progress.accept("check failed (" + e.getClass().getSimpleName() + "), trying again");
                }
            }
            try {
                open("/home");
                com.lxp.driver.DriverFactory.saveSession();
            } catch (RuntimeException e) {
                if (com.lxp.driver.DriverFactory.isBrowserGone(e)) {
                    com.lxp.driver.DriverFactory.restartKeepingSession();
                }
            }
            pause(60_000);
        }
        return "";
    }

    // ---- the generated course ----

    /** Opens the course page with every module expanded. */
    public void openAllModules() {
        driver.get(courseUrl + "?type=course");
        new WebDriverWait(driver, LOAD).until(d -> COUNTS.matcher(d.findElement(By.tagName("main")).getText()).find());
        pause(2000);
        for (int n = 1; n <= 20; n++) {
            List<WebElement> header = driver.findElements(moduleHeader(n));
            if (header.isEmpty()) {
                break;
            }
            int before = driver.findElements(By.xpath("//main//h4")).size();
            centre(header.get(0));
            header.get(0).click();
            try {
                new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> d.findElements(By.xpath("//main//h4")).size() > before);
            } catch (TimeoutException ignored) {
                // empty, or already open
            }
        }
    }

    /**
     * Module n's header button. Generated modules are named by topic
     * ("Python Values and Expressions"), not "Module 1: ...", so the header is
     * found by position among the draggable module cards.
     */
    private static By moduleHeader(int n) {
        return By.xpath("((//main//div[@data-rfd-droppable-id='modules']/div[@data-rfd-draggable-id])[" + n + "]"
                + "//button[contains(@class,'text-left')])[1]");
    }

    /** The header's counts, e.g. [3, 27] for "3 modules · 27 lessons". */
    public int[] counts() {
        Matcher m = COUNTS.matcher(find(By.tagName("main")).getText());
        return m.find() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : new int[] {-1, -1};
    }

    /** One generated item: its title, type (Page, Quiz, Assignment, Discussion) and due date text. */
    public record Item(String title, String type, String due) {
    }

    /** Every item in the expanded module list. */
    public List<Item> items() {
        Object rows = js("const t = s => (s || '').replace(/\\s+/g, ' ').trim();"
                + "return [...document.querySelectorAll('main h4')].map(h => {"
                + "  let r = h; for (let i = 0; i < 5 && r.parentElement; i++) { if (r.parentElement.querySelectorAll('h4').length > 1) break; r = r.parentElement; }"
                + "  const text = t(r.innerText); const title = t(h.innerText);"
                + "  const rest = text.slice(text.indexOf(title) + title.length).trim();"
                + "  const type = (rest.match(/^(Page|Quiz|Assignment|Discussion|Lab|External Tool|File)/) || [''])[0];"
                + "  const due = (rest.match(/Due: ([A-Z][a-z]{2} \\d{1,2}, \\d{4})/) || ['', ''])[1];"
                + "  return [title, type, due]; });");
        List<Item> out = new ArrayList<>();
        if (rows instanceof List<?> list) {
            for (Object row : list) {
                List<?> r = (List<?>) row;
                out.add(new Item(String.valueOf(r.get(0)), String.valueOf(r.get(1)), String.valueOf(r.get(2))));
            }
        }
        return out;
    }

    /** Opens an item from the expanded module list (call openAllModules first). */
    public void openItem(String title) {
        WebElement h = find(By.xpath("//main//h4[normalize-space(.)=" + literal(title) + "]"));
        centre(h);
        h.click();
        new WebDriverWait(driver, LOAD).until(d -> d.getCurrentUrl().contains("item="));
        find(By.xpath("//main//*[normalize-space(.)='Back']"));
        pause(1500);
    }

    /** On a quiz: its Questions tab text, from which the count and types are read. */
    public String quizQuestionsText() {
        WebElement tab = find(By.xpath("//main//button[normalize-space(.)='Questions']"));
        centre(tab);
        tab.click();
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> d.findElement(By.tagName("main")).getText().contains("Questions:"));
        } catch (TimeoutException ignored) {
            // read what is there
        }
        return find(By.tagName("main")).getText().replaceAll("\\s+", " ");
    }

    /** The open item's text (a lecture page, or a quiz's questions). */
    public String mainText() {
        return find(By.tagName("main")).getText().replaceAll("\\s+", " ").strip();
    }

    // ---- helpers ----

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
