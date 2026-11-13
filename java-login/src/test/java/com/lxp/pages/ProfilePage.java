package com.lxp.pages;

import com.lxp.config.Settings;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** The account menu in the header, and the /profile page behind it. */
public class ProfilePage extends BasePage {

    private static final By ACCOUNT_MENU = By.cssSelector("button[aria-label='Account menu']");
    private static final By PROFILE_ITEM = By.xpath("//*[@role='menuitem'][normalize-space(.)='Profile']");
    private static final By LOGOUT_ITEM = By.xpath("//*[@role='menuitem'][normalize-space(.)='Logout']");

    private static final By NAME = By.id("profile-name-display");
    private static final By EMAIL = By.xpath("//p[normalize-space(.)='Email Address']/following-sibling::div//span");
    private static final By ROLE = By.xpath("//p[normalize-space(.)='Role']/following-sibling::div//span");
    private static final By TIME_ZONE = By.id("timeZone");

    private static final By CURRENT_PASSWORD = By.id("currentPassword");
    private static final By NEW_PASSWORD = By.id("newPassword");
    private static final By CONFIRM_PASSWORD = By.id("confirmPassword");
    private static final By UPDATE_PASSWORD = By.xpath("//button[@type='submit'][normalize-space(.)='Update Password']");

    private static final String APPEARANCE = "//*[normalize-space(.)='Appearance' and @aria-level]/ancestor::div[2]";
    private static final List<String> COLOR_MODES = List.of("Light", "Dark", "System");
    private static final List<String> DISPLAY_SIZES = List.of("Small", "Medium", "Large");

    private static final By TOAST = By.cssSelector("[aria-label^='Notifications'] li[data-state='open']");

    /** What a save toast said, and whether it was an error. */
    public record Toast(String text, boolean error) {
    }

    public ProfilePage(WebDriver driver) {
        super(driver);
    }

    /** The name shown in the header's account menu button, i.e. who is signed in. */
    public String signedInName() {
        // the button reads "KT / Keerthana Teacher / keerthana+t1@..."
        return find(ACCOUNT_MENU).getText().lines()
                .map(String::strip)
                .filter(line -> line.length() > 2 && !line.contains("@"))
                .findFirst()
                .orElse("");
    }

    public ProfilePage openFromAccountMenu() {
        click(ACCOUNT_MENU);
        click(PROFILE_ITEM);
        wait.until(d -> d.getCurrentUrl().contains("/profile"));
        find(NAME);
        return this;
    }

    public ProfilePage reload() {
        driver.navigate().refresh();
        find(NAME);
        return this;
    }

    public String name() {
        return find(NAME).getText().strip();
    }

    public String email() {
        return find(EMAIL).getText().strip();
    }

    /**
     * The signed-in user's role as the app has it for this session: the
     * "tenant" entry the app keeps in localStorage, {"name":"root","role":"ADMIN"}.
     */
    public String signedInRole() {
        Object role = ((JavascriptExecutor) driver).executeScript(
                "for (const read of [() => JSON.parse(localStorage.getItem('tenant')).role,"
                        + " () => JSON.parse(localStorage.getItem('ailms-auth')).state.tenant.role]) {"
                        + "  try { const r = read(); if (r) return r; } catch (e) {} }"
                        + "return '';");
        return role == null ? "" : role.toString().strip();
    }

    public String role() {
        return find(ROLE).getText().strip();
    }

    public String timeZone() {
        return find(TIME_ZONE).getText().strip();
    }

    /**
     * Picks the zone from the dropdown. The page saves it straight away, shows
     * "Time zone updated … Reloading to apply changes…" and then reloads itself,
     * so wait for that reload too: touching the page mid-reload breaks the next step.
     */
    public Optional<Toast> chooseTimeZone(String zone) {
        List<WebElement> earlier = driver.findElements(TOAST);
        WebElement trigger = find(TIME_ZONE);
        if (trigger.getText().strip().equals(zone)) {
            return Optional.empty();
        }

        scrollIntoView(trigger);
        click(TIME_ZONE);
        WebElement option = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='listbox']//*[@role='option'][normalize-space(.)='" + zone + "']")));
        // the list holds ~24 zones and can open above the field, so the option may be off screen
        scrollIntoView(option);
        try {
            wait.until(ExpectedConditions.elementToBeClickable(option)).click();
        } catch (ElementClickInterceptedException e) {
            option.sendKeys(Keys.ENTER);
        }

        Optional<Toast> toast = newToast(earlier, Duration.ofSeconds(10));
        waitForSelfReload(trigger);
        return toast;
    }

    public ProfilePage chooseColorMode(String mode) {
        choose(colorModeButton(mode), () -> mode.equals(selectedColorMode()));
        return this;
    }

    public ProfilePage chooseDisplaySize(String size) {
        choose(displaySizeButton(size), () -> size.equals(selectedDisplaySize()));
        return this;
    }

    /**
     * Clicks a choice and checks it became the selected one. After a reload the
     * Canvas connections list loads late and pushes Appearance down, so a click
     * aimed at a button can land on a Canvas row instead (Nov 13): if the choice
     * is not selected within 5s, scroll to it and click again, by script the last time.
     */
    private void choose(By button, java.util.function.BooleanSupplier selected) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (attempt < 3) {
                clickUnderHeader(button);
            } else {
                WebElement b = find(button);
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", b);
            }
            try {
                new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> selected.getAsBoolean());
                return;
            } catch (TimeoutException notYet) {
                // the click missed; try again
            }
        }
        wait.until(d -> selected.getAsBoolean());
    }

    /**
     * Changing the display size resizes the whole page, which can leave the
     * next button under the sticky header, where the header takes the click.
     * Bring it to the middle of the window first, and fall back to the
     * keyboard if something still covers it.
     */
    private void clickUnderHeader(By locator) {
        WebElement button = find(locator);
        scrollIntoView(button);
        try {
            wait.until(ExpectedConditions.elementToBeClickable(button)).click();
        } catch (ElementClickInterceptedException e) {
            button.sendKeys(Keys.ENTER);
        }
    }

    /** The highlighted Color Mode button: Light, Dark or System. */
    public String selectedColorMode() {
        return selected(COLOR_MODES);
    }

    /** The highlighted Display Size button: Small, Medium or Large. */
    public String selectedDisplaySize() {
        return selected(DISPLAY_SIZES);
    }

    /** The app switches themes with a "dark" class on the html element. */
    public boolean isDarkThemeApplied() {
        String cls = driver.findElement(By.tagName("html")).getDomAttribute("class");
        return cls != null && List.of(cls.split("\\s+")).contains("dark");
    }

    /** Display Size sets the root font size, which everything else scales from. */
    public String baseTextSize() {
        return (String) ((JavascriptExecutor) driver).executeScript(
                "return getComputedStyle(document.documentElement).fontSize;");
    }

    public Optional<Toast> changePassword(String current, String next) {
        List<WebElement> earlier = driver.findElements(TOAST);
        type(CURRENT_PASSWORD, current);
        type(NEW_PASSWORD, next);
        type(CONFIRM_PASSWORD, next);
        waitUntilEnabled(UPDATE_PASSWORD);
        click(UPDATE_PASSWORD);
        return newToast(earlier, Settings.TIMEOUT);
    }

    /** Works from any signed-in page: waits until Logout has navigated away from it. */
    public void signOut() {
        String before = driver.getCurrentUrl();
        click(ACCOUNT_MENU);
        click(LOGOUT_ITEM);
        wait.until(d -> !d.getCurrentUrl().equals(before));
    }

    /** Logout lands on the public welcome or login page. */
    public boolean isSignedOut() {
        return wait.until(d -> {
            String path = URI.create(d.getCurrentUrl()).getPath();
            return path.equals("/") || path.startsWith("/welcome") || path.startsWith("/login");
        });
    }

    private String selected(List<String> choices) {
        for (String choice : choices) {
            By button = COLOR_MODES.contains(choice) ? colorModeButton(choice) : displaySizeButton(choice);
            String cls = find(button).getDomAttribute("class");
            // the chosen button is the filled one; the others are outlined
            if (cls != null && cls.contains("bg-primary")) {
                return choice;
            }
        }
        return "";
    }

    private static By colorModeButton(String mode) {
        return By.xpath(APPEARANCE + "//button[normalize-space(.)='" + mode + "']");
    }

    /** Size buttons read "Aa Small", "Aa Medium", "Aa Large". */
    private static By displaySizeButton(String size) {
        return By.xpath(APPEARANCE + "//button[span[normalize-space(.)='" + size + "']]");
    }

    private void waitForSelfReload(WebElement before) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.stalenessOf(before));
        } catch (TimeoutException e) {
            // no reload this time; the page already shows the saved value
        }
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
        find(TIME_ZONE);
    }

    private void scrollIntoView(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
    }

    /** The first toast that wasn't already showing; the sign-in toast can still be up. */
    private Optional<Toast> newToast(List<WebElement> earlier, Duration timeout) {
        try {
            WebElement t = new WebDriverWait(driver, timeout).until(d -> d.findElements(TOAST).stream()
                    .filter(e -> !earlier.contains(e))
                    .findFirst()
                    .orElse(null));
            String cls = t.getDomAttribute("class");
            return Optional.of(new Toast(t.getText().strip().replaceAll("\\s+", " "),
                    cls != null && cls.contains("destructive")));
        } catch (TimeoutException e) {
            return Optional.empty();
        }
    }
}
