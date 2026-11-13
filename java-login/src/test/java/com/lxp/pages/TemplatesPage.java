package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * The Course Templates page (/templates): the paged list of template cards,
 * search and sort, Create Template from IMSCC, and a template's preview with
 * Edit and Use This Template.
 */
public class TemplatesPage extends BasePage {

    private static final Duration LOAD_TIMEOUT = Duration.ofMinutes(2);
    /** A template from a 4-module course takes 10-12 minutes to finish on staging; allow well over that. */
    private static final Duration CREATE_TIMEOUT = Duration.ofMinutes(30);

    private static final By HEADING = By.xpath("//main//*[self::h1 or self::h2][normalize-space(.)='Course Templates']");
    private static final String CARD_CSS = "main [role='button'][aria-label^='Select ']";
    private static final By CARDS = By.cssSelector(CARD_CSS);
    private static final By SEARCH = By.cssSelector("input[placeholder^='Search templates']");
    private static final By SORT = By.cssSelector("main button[role='combobox'][aria-label='Sort by']");
    private static final By NO_RESULTS = By.xpath("//main//*[starts-with(normalize-space(text()), 'No templates found')]");
    private static final By SPINNER = By.cssSelector("main .animate-spin");
    private static final By NEXT_PAGE = By.xpath("//main//button[normalize-space(.)='Next']");
    // a template still being made shows as a "Generating... 50%" card, not a "Select ..." card
    private static final By GENERATING = By.xpath("//main//*[normalize-space(text())='Generating...']");

    private static final By CREATE_FROM_IMSCC = By.xpath("//main//button[normalize-space(.)='Create Template from IMSCC']");
    private static final By FILE = By.id("file-upload");
    private static final By CUSTOM_NAME = By.id("custom-name");
    private static final By AUTHOR = By.id("author-name");
    private static final By CANVAS_LINK = By.id("canvas-link");
    private static final By UPLOAD_AND_CREATE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Upload & Create Template']");
    private static final By PROCESSING_TOAST = By.xpath(
            "//*[@aria-label[starts-with(., 'Notifications')]]//li[contains(normalize-space(.), 'Processing Started')]");

    private static final By PREVIEW = By.xpath("//div[@role='dialog']//*[normalize-space(text())='Template Preview']");
    private static final By EDIT = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Edit']");
    private static final By EDIT_WINDOW = By.xpath("//div[@role='dialog']//*[normalize-space(text())='Edit Template']");
    private static final By EDIT_NAME = By.id("edit-name");
    private static final By EDIT_AUTHOR = By.id("edit-author-name");
    private static final By EDIT_CANVAS_LINK = By.id("edit-canvas-link");
    private static final By SAVE_CHANGES = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Save Changes']");
    private static final By CANCEL = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Cancel']");
    private static final By USE_TEMPLATE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Use This Template']");

    // Use This Template opens the Courses page's Create Course window on the template's Details step
    private static final String TEMPLATE_PANEL = "//div[@data-testid='create-course-panel-template'][not(@hidden)]";
    private static final By COURSE_TITLE = By.xpath(TEMPLATE_PANEL + "//input[@name='course-title']");
    private static final By COURSE_CODE = By.xpath(TEMPLATE_PANEL + "//input[@name='course-code']");
    private static final By CREATE_COURSE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Create Course'][not(@disabled)]");

    private static final By ERROR_TOAST = By.cssSelector("[aria-label^='Notifications'] li[data-state='open'].destructive");

    public TemplatesPage(WebDriver driver) {
        super(driver);
    }

    public void open() {
        open("/templates");
        new WebDriverWait(driver, LOAD_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(HEADING));
        waitForList();
    }

    /** Waits until the list has settled: cards (finished or generating), or "No templates found", and no spinner. */
    private void waitForList() {
        new WebDriverWait(driver, LOAD_TIMEOUT).until(d -> d.findElements(SPINNER).isEmpty()
                && (!d.findElements(CARDS).isEmpty() || !d.findElements(GENERATING).isEmpty()
                        || !d.findElements(NO_RESULTS).isEmpty()));
    }

    /**
     * The visible card names, in page order. Read in one script call: the list
     * re-renders while templates generate, which leaves element handles stale.
     */
    public List<String> names() {
        return cardTexts("var h = c.querySelector('h3'); return h ? h.innerText.trim() : null;");
    }

    /** Each card's name and subject, the two fields search looks at. */
    public List<String> namesAndSubjects() {
        return cardTexts("return c.innerText.replace(/\\s+/g, ' ').trim();");
    }

    /** Scrolls the list to its last card (the page scrolls inside a panel, not the window). */
    public int scrollToBottom() {
        Object n = js("var cs = document.querySelectorAll(arguments[0]);"
                + "if (cs.length) cs[cs.length - 1].scrollIntoView({block: 'end'});"
                + "return cs.length;", CARD_CSS);
        return ((Number) n).intValue();
    }

    public boolean allCardsShown() {
        return Boolean.TRUE.equals(js("var cs = Array.from(document.querySelectorAll(arguments[0]));"
                + "return cs.length > 0 && cs.every(c => c.getClientRects().length > 0);", CARD_CSS));
    }

    @SuppressWarnings("unchecked")
    private List<String> cardTexts(String perCard) {
        Object result = js("return Array.from(document.querySelectorAll(arguments[0]))"
                + ".map(function (c) { " + perCard + " }).filter(function (t) { return t; });", CARD_CSS);
        return result == null ? List.of() : List.copyOf((List<String>) result);
    }

    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    /** Templates come 12 to a page; Next shows the following page. */
    public boolean hasNextPage() {
        List<WebElement> next = driver.findElements(NEXT_PAGE);
        return !next.isEmpty() && next.get(0).isEnabled();
    }

    /**
     * Next, then waits until the list has settled on the new page: the cards
     * differ from before, no spinner, and the same cards on two reads 1s apart.
     * The list keeps refreshing (spinner) for up to ~10s after a load, so the
     * first change seen is not always the final page (Nov 13: a read straight
     * after the change still saw page 1's cards).
     */
    public void nextPage() {
        List<String> before = names();
        click(NEXT_PAGE);
        java.util.concurrent.atomic.AtomicReference<List<String>> last = new java.util.concurrent.atomic.AtomicReference<>(List.of());
        new WebDriverWait(driver, LOAD_TIMEOUT, Duration.ofMillis(1000)).until(d -> {
            List<String> now = d.findElements(SPINNER).isEmpty() ? names() : List.of();
            boolean settled = !now.isEmpty() && !now.equals(before) && now.equals(last.get());
            last.set(now);
            return settled;
        });
    }

    public void search(String text) {
        List<String> before = names();
        WebElement box = wait.until(ExpectedConditions.visibilityOfElementLocated(SEARCH));
        box.click();
        Keys selectAll = Platform.getCurrent().is(Platform.MAC) ? Keys.COMMAND : Keys.CONTROL;
        box.sendKeys(Keys.chord(selectAll, "a"), Keys.BACK_SPACE);
        if (text.isEmpty()) {
            // cleared: the full list comes back, which may look the same as before
            waitForList();
            return;
        }
        box.sendKeys(text);
        // The search runs on the server: wait for the list to change, settle on
        // "No templates found", or show a template still generating. If the
        // results happen to equal the list before, carry on; callers check.
        try {
            new WebDriverWait(driver, Duration.ofSeconds(45)).until(d -> d.findElements(SPINNER).isEmpty()
                    && (!d.findElements(NO_RESULTS).isEmpty() || !d.findElements(GENERATING).isEmpty()
                            || (!names().isEmpty() && !names().equals(before))));
        } catch (org.openqa.selenium.TimeoutException sameResults) {
            waitForList();
        }
    }

    /** One of: Name (A-Z), Name (Z-A), Newest First, Oldest First, Recently Updated, ... */
    public void sortBy(String option) {
        List<String> before = names();
        click(SORT);
        click(By.xpath("//*[@role='option'][normalize-space(.)=" + literal(option) + "]"));
        new WebDriverWait(driver, LOAD_TIMEOUT).until(d -> d.findElement(SORT).getText().strip().equals(option));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> d.findElements(SPINNER).isEmpty() && !names().equals(before));
        } catch (org.openqa.selenium.TimeoutException sameOrder) {
            // a search can leave so few cards that the order does not change
        }
        waitForList();
    }

    public String sortShown() {
        return find(SORT).getText().strip();
    }

    /** Create Template from IMSCC: file, name, author, link and generation mode, then Upload & Create Template. */
    public boolean createFromImscc(Path file, String name, String author, String canvasLink, String mode) {
        click(CREATE_FROM_IMSCC);
        driver.findElement(FILE).sendKeys(file.toAbsolutePath().toString());
        type(CUSTOM_NAME, name);
        type(AUTHOR, author);
        type(CANVAS_LINK, canvasLink);
        click(By.id(mode.toLowerCase()));
        waitUntilEnabled(UPLOAD_AND_CREATE);
        click(UPLOAD_AND_CREATE);
        // "Processing Started — Your template is being generated. This may take a few minutes."
        return isVisible(PROCESSING_TOAST, Duration.ofSeconds(30)) && driver.findElements(ERROR_TOAST).isEmpty();
    }

    /**
     * Waits until the template has finished generating: its card is listed as
     * a finished "Select ..." card and no "Generating..." card is left in the
     * search results. Checks once a minute or so, waiting on a light page in
     * between (the templates page is heavy to sit on). Reports each check's
     * progress, e.g. "50%", to {@code progress}.
     */
    public boolean waitUntilGenerated(String name, java.util.function.Consumer<String> progress) {
        // let the upload window close before leaving the page
        try {
            new WebDriverWait(driver, LOAD_TIMEOUT).until(d -> d.findElements(UPLOAD_AND_CREATE).isEmpty());
        } catch (org.openqa.selenium.TimeoutException stillOpen) {
            closeDialogs();
        }
        // keep the signed-in session so a crashed browser can be replaced without signing in again
        com.lxp.driver.DriverFactory.saveSession();
        long deadline = System.currentTimeMillis() + CREATE_TIMEOUT.toMillis();
        By card = card(name);
        while (System.currentTimeMillis() < deadline) {
            try {
                open();
                search(name);
                boolean generating = !driver.findElements(GENERATING).isEmpty();
                List<WebElement> found = driver.findElements(card);
                // done only when our card is a finished card: listed, and nothing still generating
                if (!found.isEmpty() && !generating && !found.get(0).getText().contains("Generating")) {
                    return true;
                }
                progress.accept(generating ? generatingPercent() : "not listed yet");
            } catch (RuntimeException e) {
                if (com.lxp.driver.DriverFactory.isBrowserGone(e)) {
                    // Chrome crashed: start a new one with the saved session; generation carries on on the server
                    com.lxp.driver.DriverFactory.restartKeepingSession();
                    progress.accept("the browser crashed and was restarted, still waiting");
                } else {
                    // a slow load or a card redrawn mid-check: not a reason to stop waiting
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
            pause(45_000);
        }
        return false;
    }

    /** The "Generating Template 50%" figure of the card on show, or "generating" if it has none. */
    private String generatingPercent() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Generating Template\\s+(\\d+%)")
                .matcher(find(By.tagName("main")).getText());
        return m.find() ? m.group(1) : "generating";
    }

    public boolean isListed(String name) {
        return !driver.findElements(card(name)).isEmpty();
    }

    /** Opens the template's preview from the list (searching for it first). */
    public void openTemplate(String name) {
        if (driver.findElements(card(name)).isEmpty()) {
            open();
            search(name);
        }
        click(card(name));
        find(PREVIEW);
    }

    public String previewText() {
        return find(By.cssSelector("[role='dialog'][data-state='open']")).getText().replaceAll("\\s+", " ").strip();
    }

    /** Use This Template → the Create Course window's template Details → Create Course. Returns the new course's address. */
    public String createCourse(String title, String code) {
        click(USE_TEMPLATE);
        new WebDriverWait(driver, LOAD_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(COURSE_TITLE));
        type(COURSE_TITLE, title);
        type(COURSE_CODE, code);
        click(CREATE_COURSE);
        new WebDriverWait(driver, LOAD_TIMEOUT).until(d -> d.getCurrentUrl().contains("/course/") || !d.findElements(ERROR_TOAST).isEmpty());
        if (!driver.getCurrentUrl().contains("/course/")) {
            throw new IllegalStateException("Course was not created: " + errorText());
        }
        return driver.getCurrentUrl().replaceFirst("\\?.*$", "");
    }

    /** The course header, e.g. "Python from Template ... | PYTPL-032201-131 · 0 modules ...". */
    public boolean courseHeaderShows(String title) {
        return isVisible(By.xpath("//main//*[normalize-space(text())=" + literal(title) + "]"), Duration.ofSeconds(30));
    }

    /** Edit → name, author and Canvas link → Save Changes. */
    public void edit(String name, String author, String canvasLink) {
        click(EDIT);
        find(EDIT_WINDOW);
        type(EDIT_NAME, name);
        type(EDIT_AUTHOR, author);
        type(EDIT_CANVAS_LINK, canvasLink);
        waitUntilEnabled(SAVE_CHANGES);
        click(SAVE_CHANGES);
        new WebDriverWait(driver, LOAD_TIMEOUT).until(d ->
                d.findElements(EDIT_WINDOW).isEmpty() || !d.findElements(ERROR_TOAST).isEmpty());
    }

    /** What the Edit window holds now: [name, author, canvas link]; closes it again without saving. */
    public String[] editValues() {
        click(EDIT);
        find(EDIT_WINDOW);
        String[] values = {value(EDIT_NAME), value(EDIT_AUTHOR), value(EDIT_CANVAS_LINK)};
        click(CANCEL);
        return values;
    }

    public void closeDialogs() {
        driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
    }

    public String errorText() {
        try {
            return driver.findElement(ERROR_TOAST).getText().replaceAll("\\s+", " ").strip();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private String value(By field) {
        String v = find(field).getDomProperty("value");
        return v == null ? "" : v.strip();
    }

    private static By card(String name) {
        return By.cssSelector("main [role='button'][aria-label=" + cssString("Select " + name + " template") + "]");
    }

    private static String cssString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
