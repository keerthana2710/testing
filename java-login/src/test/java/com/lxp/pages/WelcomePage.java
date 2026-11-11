package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;

/**
 * The public landing page. "Log in" opens the sign-in modal on top of it, and
 * "Forgot password?" swaps that modal to the reset form; "Sign up" goes to
 * /signup.
 */
public class WelcomePage extends BasePage {

    private static final String PATH = "/welcome";

    // The nav has no <header>; the first match is the nav one, the modal adds its own "Sign up" later.
    private static final By HEADER_LOG_IN = By.xpath("(//button[normalize-space()='Log in'])[1]");
    private static final By HEADER_SIGN_UP = By.xpath("(//button[normalize-space()='Sign up'])[1]");

    private static final By DIALOG = By.cssSelector("[role='dialog'][data-state='open']");
    private static final By LOGIN_EMAIL = By.id("login-email");
    private static final By FORGOT_PASSWORD = By.xpath(
            "//*[@role='dialog']//button[normalize-space()='Forgot password?']");

    private static final By RESET_EMAIL = By.id("forgot-password-email");
    private static final By SEND_RESET_LINK = By.xpath(
            "//*[@role='dialog']//button[normalize-space()='Send Reset Link']");

    public WelcomePage(WebDriver driver) {
        super(driver);
    }

    public WelcomePage open() {
        open(PATH);
        find(HEADER_LOG_IN);
        return this;
    }

    public boolean isDisplayed() {
        return isVisible(HEADER_LOG_IN, Duration.ofSeconds(10));
    }

    public void clickLogIn() {
        jsClick(HEADER_LOG_IN);
    }

    public boolean isLoginModalDisplayed() {
        return isVisible(LOGIN_EMAIL, Duration.ofSeconds(10));
    }

    public void clickForgotPassword() {
        click(FORGOT_PASSWORD);
    }

    public boolean isResetFormDisplayed() {
        return isVisible(RESET_EMAIL, Duration.ofSeconds(10));
    }

    public void requestPasswordReset(String email) {
        type(RESET_EMAIL, email);
        click(SEND_RESET_LINK);
    }

    /** Visible text of the open modal, which carries the "check your email" confirmation. */
    public String dialogText() {
        return find(DIALOG).getText().strip();
    }

    public void clickSignUp() {
        jsClick(HEADER_SIGN_UP);
    }

    // The header buttons sit under a sticky nav that can overlap them on load,
    // so a native click is sometimes intercepted.
    private void jsClick(By locator) {
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }
}
