package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/** A course's Settings window: Course Name, Course Code, Description and Update Course. */
public class CourseSettingsPage extends BasePage {

    /** The course list (1000+ courses on staging) can take over 30s to load. */
    private static final Duration LIST_TIMEOUT = Duration.ofMinutes(2);

    private static final By SEARCH = By.cssSelector("input[placeholder='Search courses...']");
    private static final By PUBLISHED = By.xpath("//main//button[starts-with(normalize-space(.), 'Published')]");
    private static final By UNPUBLISHED = By.xpath("//main//button[starts-with(normalize-space(.), 'Unpublished')]");

    private static final By SETTINGS = By.xpath("//main//button[normalize-space(.)='Settings']");
    private static final By SETTINGS_TITLE = By.xpath("//div[@role='dialog']//h2[normalize-space(.)='Course Settings']");
    private static final By NAME = By.id("name");
    private static final By CODE = By.id("code");
    private static final By DESCRIPTION = By.id("description");
    private static final By UPDATE_COURSE = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Update Course']");

    private static final By SAVED_TOAST = By.xpath(
            "//*[@aria-label[starts-with(., 'Notifications')]]//li[contains(normalize-space(.), 'Course settings updated')]");
    private static final By ERROR_TOAST = By.cssSelector("[aria-label^='Notifications'] li[data-state='open'].destructive");

    private String courseUrl;

    public CourseSettingsPage(WebDriver driver) {
        super(driver);
    }

    /**
     * /courses → search the title → open the course whose card title starts
     * with it. The course list (1000+ courses) is heavy, so it is left as soon
     * as the course is found.
     */
    public void openCourse(String title) {
        open("/courses");
        new WebDriverWait(driver, LIST_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(SEARCH));
        type(SEARCH, title);
        By card = By.xpath("//a[starts-with(@href,'/course/')]"
                + "[.//*[starts-with(normalize-space(text()), " + literal(title) + ")]]");
        // Published and unpublished courses are separate lists; try the other one if needed
        if (!isVisible(card, Duration.ofSeconds(15))) {
            click(UNPUBLISHED);
            if (!isVisible(card, Duration.ofSeconds(15))) {
                click(PUBLISHED);
            }
        }
        click(card);
        wait.until(d -> d.getCurrentUrl().contains("/course/"));
        courseUrl = driver.getCurrentUrl().replaceFirst("\\?.*$", "");
    }

    public void openSettings() {
        if (isVisible(SETTINGS_TITLE, Duration.ofSeconds(1))) {
            return;
        }
        new WebDriverWait(driver, LIST_TIMEOUT).until(ExpectedConditions.elementToBeClickable(SETTINGS));
        click(SETTINGS);
        find(SETTINGS_TITLE);
        // the fields fill in from the server just after the window opens
        wait.until(d -> !d.findElement(NAME).getDomProperty("value").isBlank());
    }

    public void update(String name, String code, String description) {
        replace(NAME, name);
        replace(CODE, code);
        replace(DESCRIPTION, description);
    }

    public void clickUpdateCourse() {
        waitUntilEnabled(UPDATE_COURSE);
        click(UPDATE_COURSE);
    }

    /** "Course settings updated successfully", or the app's error (staging said "Project not found" for new courses). */
    public String saveResult() {
        // the toast shows for only about a second, and the window closes once saved
        wait.until(d -> !d.findElements(SAVED_TOAST).isEmpty() || !d.findElements(ERROR_TOAST).isEmpty()
                || d.findElements(SETTINGS_TITLE).isEmpty());
        if (!driver.findElements(ERROR_TOAST).isEmpty()) {
            return "error: " + text(ERROR_TOAST);
        }
        return "saved";
    }

    /** A full browser refresh of the course page; Settings is closed afterwards. */
    public void refresh() {
        driver.navigate().refresh();
        wait.until(d -> d.getCurrentUrl().startsWith(courseUrl));
        new WebDriverWait(driver, LIST_TIMEOUT).until(ExpectedConditions.elementToBeClickable(SETTINGS));
    }

    public String name() {
        return value(NAME);
    }

    public String code() {
        return value(CODE);
    }

    public String description() {
        return value(DESCRIPTION);
    }

    public void openHome() {
        open("/home");
        wait.until(d -> d.getCurrentUrl().contains("/home"));
    }

    public void closeSettings() {
        driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(SETTINGS_TITLE));
    }

    // React keeps its own copy of the value, so clear() alone does not reach it:
    // select everything and delete it the way a user would, then type.
    private void replace(By field, String text) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(field));
        el.click();
        Keys selectAll = Platform.getCurrent().is(Platform.MAC) ? Keys.COMMAND : Keys.CONTROL;
        el.sendKeys(Keys.chord(selectAll, "a"), Keys.BACK_SPACE);
        if (!text.isEmpty()) {
            el.sendKeys(text);
        }
    }

    private String value(By field) {
        String v = find(field).getDomProperty("value");
        return v == null ? "" : v.strip();
    }

    private String text(By locator) {
        try {
            return driver.findElement(locator).getText().replaceAll("\\s+", " ").strip();
        } catch (NoSuchElementException e) {
            return "";
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
