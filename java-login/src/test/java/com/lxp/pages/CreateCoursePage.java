package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creating a course: the courses page's Create dialog ("Create New"), then the
 * Course Setup dialog the new course opens with, and the AI course plan that
 * Generate produces for review and approval.
 */
public class CreateCoursePage extends BasePage {

    /** Planning a course with AI took ~6-10 minutes on staging. */
    private static final Duration PLAN_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofMinutes(2);

    // "+ Create" on /courses; its accessible text is "Create Course"
    private static final By CREATE = By.xpath("//main//button[contains(normalize-space(.), 'Create Course')]");
    private static final By CREATE_NEW = By.xpath(
            "//div[@data-testid='create-course-panel-home']//button[.//span[normalize-space(.)='Create New']]");
    private static final By TITLE = By.name("course-title");
    private static final By CODE = By.name("course-code");
    private static final By CREATE_COURSE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Create Course']");

    private static final By DIALOG = By.cssSelector("[role='dialog'][data-state='open']");
    private static final By COURSE_SETUP = By.xpath("//main//button[.//*[normalize-space(.)='Course Setup']]");
    private static final By SETUP_TITLE = By.xpath("//div[@role='dialog']//h2[normalize-space(.)='Course Setup']");
    private static final By SAVE_AND_CLOSE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Save & Close']");
    private static final By GENERATE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Generate']");
    // Generate asks twice: whether to add more first, then to confirm planning
    private static final By GENERATE_ANYWAY = By.xpath("//button[normalize-space(.)='Generate anyway']");
    private static final By PLAN_THE_COURSE = By.xpath("//button[normalize-space(.)='Plan the course']");

    // the side nav of Course Setup, and a heading only that section shows
    private static final By SCHEDULE_SECTION = By.xpath("(//div[@role='dialog']//button[.//*[normalize-space(.)='Syllabus & Schedule']])[1]");
    private static final By SCHEDULE_SHOWN = By.xpath("//div[@role='dialog']//h3[starts-with(normalize-space(.), 'Weeks & modules')]");
    private static final By OUTLINE_SECTION = By.xpath("(//div[@role='dialog']//button[.//*[normalize-space(.)='Outline']])[1]");
    private static final By OUTLINE_SHOWN = By.xpath("//div[@role='dialog']//*[self::h3 or self::h2][starts-with(normalize-space(.), 'Course outline')]");
    private static final By MODULES = By.id("number-of-modules");
    private static final By HOURS_PER_WEEK = By.id("hours-per-week");
    private static final By FIRST_DAY = By.xpath("//div[@role='dialog']//label[starts-with(normalize-space(.), 'First day')]/following::button[1]");
    private static final By LAST_DAY = By.xpath("//div[@role='dialog']//label[starts-with(normalize-space(.), 'Last day')]/following::button[1]");
    private static final By CALENDAR = By.cssSelector("[data-radix-popper-content-wrapper]");
    private static final By CALENDAR_TODAY = By.xpath("//div[@data-radix-popper-content-wrapper]//button[normalize-space(.)='Today']");
    private static final By CALENDAR_NEXT = By.xpath("//div[@data-radix-popper-content-wrapper]//button[@aria-label='Next month']");
    private static final By CALENDAR_PREV = By.xpath("//div[@data-radix-popper-content-wrapper]//button[@aria-label='Previous month']");
    private static final By OUTLINE_PASTE = By.xpath(
            "(//div[@role='dialog']//h3[normalize-space(.)='Course outline *']/following::button[normalize-space(.)='Paste the text'])[1]");
    private static final By OUTLINE_TEXT = By.cssSelector("textarea[aria-label='Text of the course outline']");
    private static final By OUTLINE_ADD = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Add']");
    private static final By OUTLINE_ADDED = By.cssSelector("button[aria-label='Remove Pasted course outline']");

    // shown while the plan is written, and enabled again once it is ready
    private static final By PLAN_AGAIN = By.xpath("//button[normalize-space(.)='Plan again']");
    // the course page's "Course plan · Waiting for your review" card
    private static final By APPROVE = By.xpath("//button[normalize-space(.)='Approve plan']");
    // Approve plan asks how much to generate: Whole course, First N modules, or Only these modules
    private static final By APPROVE_WINDOW = By.xpath("//div[@role='dialog'][.//*[normalize-space(text())='Approve the plan']]");
    private static final By WHOLE_COURSE = By.xpath(
            "//div[@role='dialog']//label[normalize-space(.)='Whole course'] | //div[@role='dialog']//*[@role='radio'][normalize-space(.)='Whole course' or @value='whole' or @value='all']");
    private static final By APPROVE_AND_GENERATE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Approve & generate course']");
    private static final By GENERATING_COURSE = By.xpath("//*[normalize-space(text())='Generating Course']");
    // Stop on the Generating Course panel asks first: Cancel or Stop Generation
    private static final By STOP = By.xpath("//button[normalize-space(.)='Stop']");
    private static final By STOP_GENERATION = By.xpath("//button[normalize-space(.)='Stop Generation']");
    private static final By PLAN_APPROVED = By.xpath(
            "//*[normalize-space(text())='This plan is approved.' or starts-with(normalize-space(text()), 'This plan is approved')]"
                    + " | //*[@aria-label[starts-with(., 'Notifications')]]//li[contains(normalize-space(.), 'Plan approved')]");

    private static final By ERROR_TOAST = By.cssSelector("[aria-label^='Notifications'] li[data-state='open'].destructive");

    private String courseUrl;
    private boolean outlineWasThere;

    public CreateCoursePage(WebDriver driver) {
        super(driver);
    }

    /** /courses → + Create → Create New. */
    public void startCreate() {
        open("/courses");
        // the course list (1000+ courses on staging) can take over 30s to load before + Create shows
        new WebDriverWait(driver, PAGE_LOAD_TIMEOUT).until(ExpectedConditions.elementToBeClickable(CREATE));
        click(CREATE);
        click(CREATE_NEW);
        find(TITLE);
    }

    public void enterNameAndCode(String name, String code) {
        type(TITLE, name);
        type(CODE, code);
    }

    /** Create Course; the app then opens the new course with Course Setup on top. */
    public void create() {
        waitUntilEnabled(CREATE_COURSE);
        click(CREATE_COURSE);
        // stop on the app's error (e.g. the code is taken) instead of waiting it out
        wait.until(d -> d.getCurrentUrl().contains("/course/") || !d.findElements(ERROR_TOAST).isEmpty());
        if (!driver.getCurrentUrl().contains("/course/")) {
            throw new IllegalStateException("Course was not created: " + errorText());
        }
        courseUrl = driver.getCurrentUrl().replaceFirst("\\?.*$", "");
    }

    public String courseUrl() {
        return courseUrl;
    }

    /** The course header, e.g. "basic of c and cpp · CppandA10-131 · 0 modules". */
    public boolean headerShows(String name) {
        By header = By.xpath("//*[normalize-space(.)=" + literal(name) + "]");
        return isVisible(header, Duration.ofSeconds(15));
    }

    /** The app may give the code a suffix such as "-131" when the code is already taken. */
    public String savedCode(String code) {
        By codeText = By.xpath("//*[starts-with(normalize-space(text()), " + literal(code) + ")]");
        try {
            // the header reads "CppandA10-131 · 0 modules · ..."; keep just the code
            String line = find(codeText).getText().replace(' ', ' ').strip();
            return line.split("\\s*·\\s*", 2)[0].strip();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** On the new course with Course Setup open; reopens either if needed. */
    public void openCourseSetup() {
        if (!driver.getCurrentUrl().startsWith(courseUrl)) {
            driver.get(courseUrl);
        }
        // a new course opens Course Setup by itself, and its overlay blocks the page
        // while it animates in, so give it time before reaching for the button
        if (!isVisible(SETUP_TITLE, Duration.ofSeconds(20))) {
            wait.until(ExpectedConditions.invisibilityOfElementLocated(DIALOG));
            click(COURSE_SETUP);
        }
        find(SETUP_TITLE);
    }

    public boolean isCourseSetupOpen() {
        return !driver.findElements(SETUP_TITLE).isEmpty();
    }

    /** One row of the feature's details table. */
    public void fill(String field, String value) {
        switch (field) {
            case "Number of modules" -> {
                showSection(SCHEDULE_SECTION, SCHEDULE_SHOWN);
                type(MODULES, value);
            }
            case "Lecture hours per week" -> {
                showSection(SCHEDULE_SECTION, SCHEDULE_SHOWN);
                type(HOURS_PER_WEEK, value);
            }
            case "First day" -> {
                showSection(SCHEDULE_SECTION, SCHEDULE_SHOWN);
                pickDate(FIRST_DAY, value);
            }
            case "Last day" -> {
                showSection(SCHEDULE_SECTION, SCHEDULE_SHOWN);
                pickDate(LAST_DAY, value);
            }
            case "Course outline" -> {
                showSection(OUTLINE_SECTION, OUTLINE_SHOWN);
                // a course created with a code that already exists comes with that course's outline
                if (isVisible(OUTLINE_ADDED, Duration.ofSeconds(3))) {
                    outlineWasThere = true;
                    return;
                }
                click(OUTLINE_PASTE);
                type(OUTLINE_TEXT, value);
                waitUntilEnabled(OUTLINE_ADD);
                click(OUTLINE_ADD);
                find(OUTLINE_ADDED);
            }
            default -> throw new IllegalArgumentException("Unknown course setup field: " + field
                    + " (use Number of modules, Lecture hours per week, First day, Last day, Course outline)");
        }
    }

    public boolean outlineWasThere() {
        return outlineWasThere;
    }

    /**
     * Opens a Course Setup section and waits until it is really showing. The
     * app moves on to the next unfinished section by itself once one is
     * complete, and a click that lands while it does so is lost, so retry.
     */
    private void showSection(By navButton, By shownHeading) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            if (isVisible(shownHeading, Duration.ofMillis(500))) {
                pause(500);
                // still there after the app has had time to move on by itself
                if (isVisible(shownHeading, Duration.ofMillis(500))) {
                    return;
                }
            }
            click(navButton);
            isVisible(shownHeading, Duration.ofSeconds(5));
        }
        find(shownHeading);
    }

    /** "today", or "<n> months from today" which picks the 28th of that month. */
    private void pickDate(By opener, String when) {
        click(opener);
        find(CALENDAR);
        if (when.equalsIgnoreCase("today")) {
            click(CALENDAR_TODAY);
        } else {
            int months = Integer.parseInt(when.replaceAll("\\D+", ""));
            YearMonth target = YearMonth.now().plusMonths(months);
            // page by the month the calendar says it shows, not by counting clicks,
            // since it opens on the chosen date (or the first day) and a quick click can be dropped
            for (int i = 0; i < 36; i++) {
                // read the month before clicking, then wait for the caption to move off it
                YearMonth shown = calendarMonth();
                if (shown.equals(target)) {
                    break;
                }
                click(target.isAfter(shown) ? CALENDAR_NEXT : CALENDAR_PREV);
                wait.until(d -> {
                    YearMonth now = calendarMonthOrNull();
                    return now != null && !now.equals(shown);
                });
            }
            if (!calendarMonth().equals(target)) {
                throw new IllegalStateException("Calendar shows " + calendarMonth() + ", expected " + target);
            }
            // the grid also shows a few days of the next month; the first "28" is this month's
            click(By.xpath("(//div[@data-radix-popper-content-wrapper]//button[normalize-space(.)='28'])[1]"));
        }
        wait.until(ExpectedConditions.invisibilityOfElementLocated(CALENDAR));
    }

    private static final DateTimeFormatter MONTH_CAPTION = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /** The month the open calendar shows, from its caption, e.g. "November 2026". */
    private YearMonth calendarMonth() {
        return wait.until(d -> calendarMonthOrNull());
    }

    private YearMonth calendarMonthOrNull() {
        try {
            String text = driver.findElement(CALENDAR).getText();
            Matcher m = Pattern.compile("(January|February|March|April|May|June|July|August|September|October|November|December) \\d{4}")
                    .matcher(text);
            return m.find() ? YearMonth.parse(m.group(), MONTH_CAPTION) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The date a picker shows, e.g. "Nov 03, 2026", or "Pick date" when empty. */
    public String dateShown(String field) {
        // the dates live on Syllabus & Schedule; the outline row leaves Outline showing
        showSection(SCHEDULE_SECTION, SCHEDULE_SHOWN);
        return find(field.equals("First day") ? FIRST_DAY : LAST_DAY).getText().strip();
    }

    /** The red badges, e.g. "Course dates required"; empty once everything needed is filled. */
    public List<String> missing() {
        WebElement dialog = find(DIALOG);
        return dialog.findElements(By.xpath(".//*[contains(normalize-space(text()), 'required')]")).stream()
                .map(e -> e.getText().strip())
                .filter(t -> !t.isEmpty() && t.length() < 60)
                .distinct()
                .toList();
    }

    public void saveAndClose() {
        click(SAVE_AND_CLOSE);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(SETUP_TITLE));
    }

    /** Course Setup → Generate → Generate anyway → Plan the course. */
    public void generate() {
        openCourseSetup();
        waitUntilEnabled(GENERATE);
        click(GENERATE);
        if (isVisible(GENERATE_ANYWAY, Duration.ofSeconds(5))) {
            click(GENERATE_ANYWAY);
        }
        click(PLAN_THE_COURSE);
    }

    /** Waits until the AI has finished the plan: Approve shows, or Plan again is enabled again. */
    public boolean waitForPlan() {
        try {
            new WebDriverWait(driver, PLAN_TIMEOUT, Duration.ofSeconds(5)).until(d -> {
                if (!d.findElements(ERROR_TOAST).isEmpty()) {
                    return true;
                }
                if (d.findElements(APPROVE).stream().anyMatch(WebElement::isDisplayed)) {
                    return true;
                }
                List<WebElement> again = d.findElements(PLAN_AGAIN);
                return !again.isEmpty() && again.get(0).isEnabled();
            });
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
        return driver.findElements(ERROR_TOAST).isEmpty();
    }

    /**
     * Waits, up to 15 minutes, until the AI plan is written and its Approve
     * button shows. Stops early on an error toast.
     */
    public boolean waitForApprove() {
        return waitForApprove(PLAN_TIMEOUT);
    }

    public boolean waitForApprove(Duration timeout) {
        // Chrome crashed (the whole browser) after 2-5 minutes on the course page
        // while the plan streams in, so wait on the light home page and only look
        // in on the course briefly: the planning carries on on the server.
        // If Chrome crashes anyway, a new one is started with the saved session
        // (saved on the home page each round) and the wait carries on.
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                driver.get(courseUrl);
                if (isVisible(APPROVE, Duration.ofSeconds(15))) {
                    return true;
                }
                if (!driver.findElements(ERROR_TOAST).isEmpty()) {
                    return false;
                }
                open("/home");
                com.lxp.driver.DriverFactory.saveSession();
            } catch (org.openqa.selenium.WebDriverException e) {
                if (!com.lxp.driver.DriverFactory.isBrowserGone(e)) {
                    throw e;
                }
                com.lxp.driver.DriverFactory.restartKeepingSession();
            }
            pause(45_000);
        }
        return false;
    }

    /** The plan's visible text, to review before approving. */
    public String planText() {
        Object text = ((JavascriptExecutor) driver).executeScript(
                "const d=[...document.querySelectorAll(\"[role='dialog'][data-state='open']\")].pop();"
                        + "return (d||document.querySelector('main')).innerText;");
        return text == null ? "" : text.toString().strip();
    }

    /**
     * Approve plan opens an "Approve the plan" window: Whole course (the
     * default), First N modules, or Only these modules. Picks Whole course and
     * confirms with Approve &amp; generate course.
     */
    public void approve() {
        click(APPROVE);
        if (isVisible(APPROVE_WINDOW, Duration.ofSeconds(10))) {
            List<WebElement> whole = driver.findElements(WHOLE_COURSE);
            if (!whole.isEmpty()) {
                whole.get(0).click();
            }
            click(APPROVE_AND_GENERATE);
            wait.until(ExpectedConditions.invisibilityOfElementLocated(APPROVE_WINDOW));
        }
    }

    /** Approve is gone and no error toast came up. */
    /**
     * The "Plan approved" toast, the card saying "This plan is approved.", or
     * the "Generating Course" panel that approval starts. That generation keeps
     * the page so busy that Chrome can miss a 45s reply, so keep asking for up
     * to 2 minutes, then leave for the home page: generation carries on in the
     * background, and signing out works there.
     */
    public boolean isApproved() {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
        boolean approved = false;
        while (!approved && System.currentTimeMillis() < deadline) {
            try {
                approved = !driver.findElements(PLAN_APPROVED).isEmpty()
                        || !driver.findElements(GENERATING_COURSE).isEmpty();
            } catch (org.openqa.selenium.WebDriverException busy) {
                // renderer timeout while the course starts generating; ask again
            }
            if (!approved) {
                pause(2000);
            }
        }
        if (approved) {
            open("/home");
        }
        return approved;
    }

    /**
     * Opens the course and stops its generation: Stop on the Generating Course
     * panel, then Stop Generation to confirm. The panel is there only once the
     * plan is approved; nothing can be stopped while the plan is being written.
     */
    public void stopGeneration() {
        driver.get(courseUrl);
        new WebDriverWait(driver, PAGE_LOAD_TIMEOUT).until(ExpectedConditions.elementToBeClickable(STOP));
        click(STOP);
        click(STOP_GENERATION);
    }

    /** The Generating Course panel goes away ("Stopping..." first); keeps asking while the page is busy. */
    public boolean isGenerationStopped() {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                if (driver.findElements(GENERATING_COURSE).isEmpty()) {
                    return driver.findElements(ERROR_TOAST).isEmpty();
                }
            } catch (org.openqa.selenium.WebDriverException busy) {
                // renderer timeout while it stops; ask again
            }
            pause(2000);
        }
        return false;
    }

    public String errorText() {
        try {
            return driver.findElement(ERROR_TOAST).getText().replaceAll("\\s+", " ").strip();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** An XPath string literal, safe for text with quotes. */
    private static String literal(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }
}
