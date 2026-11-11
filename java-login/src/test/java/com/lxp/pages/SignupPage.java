package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.net.URI;
import java.time.Duration;

/** The /signup request-access form. */
public class SignupPage extends BasePage {

    private static final By FIRST_NAME = By.id("signup-first-name");
    private static final By LAST_NAME = By.id("signup-last-name");
    private static final By EMAIL = By.id("signup-email");
    private static final By COURSE_INTEREST = By.id("signup-course-interest");
    // Honeypot: a real user never fills it, so the test must leave it empty.
    private static final By WEBSITE = By.id("signup-website");
    private static final By SUBMIT = By.xpath("//form//button[normalize-space()='Submit request']");

    public SignupPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        boolean onPath = wait.until(d -> URI.create(d.getCurrentUrl()).getPath().startsWith("/signup"));
        return onPath && isVisible(FIRST_NAME, Duration.ofSeconds(10));
    }

    public void fill(String firstName, String lastName, String email) {
        type(FIRST_NAME, firstName);
        type(LAST_NAME, lastName);
        type(EMAIL, email);
    }

    /** "Learn", "Teach" or "Both". */
    public void chooseInterest(String interest) {
        By option = By.id("signup-interest-" + switch (interest.toLowerCase()) {
            case "learn" -> "learner";
            case "teach" -> "educator";
            case "both" -> "both";
            default -> throw new IllegalArgumentException("Unknown interest: " + interest);
        });
        click(option);
    }

    public boolean isInterestSelected(String interest) {
        By option = By.xpath("//button[@role='radio'][normalize-space()='" + interest + "']");
        return "true".equals(find(option).getDomAttribute("aria-checked"));
    }

    /** Only shown once Teach or Both is chosen. */
    public void enterCourseInterest(String course) {
        type(COURSE_INTEREST, course);
    }

    public String honeypotValue() {
        return find(WEBSITE).getDomProperty("value");
    }

    public void submit() {
        waitUntilEnabled(SUBMIT);
        click(SUBMIT);
    }

    /** Visible page text, which carries the confirmation once the request is sent. */
    public String pageText() {
        return find(By.tagName("main")).getText().strip();
    }
}
