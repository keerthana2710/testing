package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;

public class LoginPage extends BasePage {

    private static final String PATH = "/login";

    private static final By EMAIL = By.id("email");
    private static final By PASSWORD = By.id("password");
    private static final By CONSENT = By.id("inline-consent-checkbox");
    private static final By SIGN_IN = By.cssSelector("form button[type='submit']");

    // Errors arrive as a Radix toast, not inline text.
    private static final By ERROR_TOAST = By.cssSelector(
            "[aria-label^='Notifications'] li[data-state='open'].destructive");
    private static final By ERROR_TITLE = By.cssSelector(
            "[aria-label^='Notifications'] li[data-state='open'].destructive div.font-semibold");
    private static final By ERROR_MESSAGE = By.cssSelector(
            "[aria-label^='Notifications'] li[data-state='open'].destructive div.opacity-90");

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public LoginPage login(String email, String password) {
        open(PATH);

        type(EMAIL, email);
        type(PASSWORD, password);

        // The consent row only renders once you type into the form, and ticking
        // it is the only gate on submit.
        if (isVisible(CONSENT, Duration.ofSeconds(3))) {
            WebElement checkbox = find(CONSENT);
            if (!"true".equals(checkbox.getDomAttribute("aria-checked"))) {
                click(CONSENT);
            }
        }

        waitUntilEnabled(SIGN_IN);
        click(SIGN_IN);

        return this;
    }

    public boolean hasError(Duration timeout) {
        return isVisible(ERROR_TOAST, timeout);
    }

    /** Toast title, e.g. "Login failed". */
    public String errorTitle() {
        return textOrEmpty(ERROR_TITLE);
    }

    /** Toast body, e.g. "Authentication failed". */
    public String errorMessage() {
        return textOrEmpty(ERROR_MESSAGE);
    }

    private String textOrEmpty(By locator) {
        try {
            return driver.findElement(locator).getText().strip();
        } catch (NoSuchElementException e) {
            return "";
        }
    }
}
