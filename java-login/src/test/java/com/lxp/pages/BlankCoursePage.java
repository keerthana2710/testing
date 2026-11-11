package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The AI Assistant's "Create Blank Course" window (staging, Nov 2026): the
 * assistant's "Blank Course" button (its answer to "Create blank course")
 * opens it, with one field, "What is your course about? *". The AI writes
 * the course name and description from it.
 *
 * Create Course stays disabled while the field is empty; no "required"
 * message is shown. Once created, the window closes and the assistant says
 * "Created an empty course: <name>" with an Open Course button. At the plan's
 * course limit (the Free plan allows 1 active course) the assistant offers
 * no button, and Create Course opens "Upgrade your plan" instead.
 */
public class BlankCoursePage extends BasePage {

    /** The AI writes the name and description before the course page opens. */
    private static final Duration CREATE_TIMEOUT = Duration.ofMinutes(3);

    // the assistant's Create Blank Course window
    private static final String BLANK = "//div[@role='dialog'][.//h2[normalize-space(.)='Create Blank Course']]";
    private static final By BLANK_WINDOW = By.xpath(BLANK);
    private static final By ABOUT = By.xpath(BLANK + "//textarea[@id='course-description']");

    private static final By CREATE_COURSE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Create Course']");
    private static final By UPGRADE = By.xpath("//div[@role='dialog']//*[normalize-space(text())='Upgrade your plan']");
    private static final By ERROR_TOAST = By.cssSelector("[aria-label^='Notifications'] li[data-state='open'].destructive");
    private static final By DIALOG = By.cssSelector("[role='dialog'][data-state='open']");

    // after the assistant's window: "✅ Created an empty course: <name> — ..." and an Open Course button
    private static final By CREATED_MESSAGE = By.xpath(
            "//*[contains(normalize-space(.), 'Created an empty course')][not(*[contains(normalize-space(.), 'Created an empty course')])]");
    private static final By OPEN_COURSE = By.xpath(
            "//*[contains(normalize-space(.), 'Created an empty course')]/following::button[normalize-space(.)='Open Course'][1]");

    // course page header: "FSQA Blank Course |fsqa-117 · 0 modules · 0 lessons · Last updated ..."
    private static final Pattern HEADER = Pattern.compile("(?m)^\\s*(.+?)\\s*\\|\\s*(\\S+)\\s*·\\s*\\d+ modules?");

    public BlankCoursePage(WebDriver driver) {
        super(driver);
    }

    // ---- the assistant's Create Blank Course window ----

    public boolean isBlankWindowOpen() {
        return isVisible(BLANK_WINDOW, Duration.ofSeconds(15)) && isVisible(ABOUT, Duration.ofSeconds(5));
    }

    /** "What is your course about?" */
    public void describe(String about) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(ABOUT));
        // React keeps its own copy of the value: select all and type over it
        el.sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.BACK_SPACE);
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
        if (!about.isEmpty()) {
            el.sendKeys(about);
        }
    }

    // ---- shared ----

    public boolean createEnabled() {
        return driver.findElement(CREATE_COURSE).isEnabled();
    }

    /**
     * Tries Create Course the way a user would; a disabled button ignores the
     * click (a plain Selenium click would be refused as intercepted).
     */
    public void tryCreate() {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", driver.findElement(CREATE_COURSE));
    }

    /** Any "... required" message in the open window. */
    public String requiredMessage() {
        Object text = ((JavascriptExecutor) driver).executeScript(
                "const w = document.querySelector(\"[role='dialog'][data-state='open']\");"
                        + "if (!w) return '';"
                        + "return [...w.querySelectorAll('p, span, div[role=alert]')]"
                        + "  .filter(e => !e.children.length && /required/i.test(e.textContent))"
                        + "  .map(e => e.textContent.trim()).join(' / ');");
        return text == null ? "" : text.toString();
    }

    public boolean onCoursePage() {
        return driver.getCurrentUrl().contains("/course/");
    }

    /**
     * Clicks Create Course and waits for the outcome: the assistant's
     * "Created an empty course: <name>" with its Open Course button, the new
     * course's page, the plan-limit window, or an error toast. Returns "" when the course was
     * created, else what the app said.
     */
    public String create() {
        waitUntilEnabled(CREATE_COURSE);
        click(CREATE_COURSE);
        try {
            new WebDriverWait(driver, CREATE_TIMEOUT).until(d -> onCoursePage() || !d.findElements(OPEN_COURSE).isEmpty()
                    || !d.findElements(UPGRADE).isEmpty() || !d.findElements(ERROR_TOAST).isEmpty());
        } catch (TimeoutException e) {
            return "nothing happened within " + CREATE_TIMEOUT.toMinutes() + " min, still on " + driver.getCurrentUrl();
        }
        if (onCoursePage() || !driver.findElements(OPEN_COURSE).isEmpty()) {
            return "";
        }
        if (!driver.findElements(UPGRADE).isEmpty()) {
            String text = driver.findElement(DIALOG).getText();
            return "Upgrade your plan: " + text.lines().map(String::strip)
                    .filter(l -> l.contains("limit")).findFirst().orElse("");
        }
        return driver.findElement(ERROR_TOAST).getText().replaceAll("\\s+", " ").strip();
    }

    /** The assistant's confirmation, e.g. "✅ Created an empty course: FSQA Blank Course Workflow — no modules ..."; "" if none. */
    public String createdMessage() {
        return driver.findElements(CREATED_MESSAGE).stream().reduce((a, b) -> b)
                .map(e -> e.getText().replaceAll("\\s+", " ").strip()).orElse("");
    }

    /** The assistant's Open Course button under its confirmation; waits for the course page. */
    public void openCreatedCourse() {
        if (onCoursePage()) {
            return;
        }
        click(OPEN_COURSE);
        new WebDriverWait(driver, Duration.ofSeconds(60)).until(d -> onCoursePage());
    }

    /** The new course's address, e.g. /course/9a8c67e8-... */
    public String coursePath() {
        return URI.create(driver.getCurrentUrl()).getPath();
    }

    /** The course page header's title, e.g. "FSQA Blank Course"; "" if it never shows. */
    public String courseTitle() {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(60)).until(d -> {
                Matcher m = HEADER.matcher(d.findElement(By.tagName("main")).getText());
                return m.find() ? m.group(1).strip() : null;
            });
        } catch (TimeoutException e) {
            return "";
        }
    }

    /** Course Setup opens on top of a new course; close it so the page can be used. */
    public void closeOpenWindow() {
        if (isVisible(DIALOG, Duration.ofSeconds(10))) {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.invisibilityOfElementLocated(DIALOG));
            } catch (TimeoutException ignored) {
                // a window that needs its own Close shows up in the next step
            }
        }
    }
}
