package com.lxp.pages;

import com.lxp.config.Settings;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Point;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * A course's People tab: the user table, the Add User dialog and the per-row
 * Actions menu. The tab is client-side state, not a route of its own, so it is
 * reached by clicking the sidebar link rather than by URL.
 */
public class CoursePeoplePage extends BasePage {

    private static final By PUBLISHED = By.xpath(
            "//main//button[starts-with(normalize-space(.), 'Published')]");
    private static final By UNPUBLISHED = By.xpath(
            "//main//button[starts-with(normalize-space(.), 'Unpublished')]");
    private static final By PEOPLE_LINK = By.cssSelector("a[href$='view=people']");
    private static final By LIST_SEARCH = By.cssSelector("input[aria-label='Search users']");
    private static final By ADD_USERS = By.xpath("//main//button[starts-with(normalize-space(.), 'Add Users')]");
    private static final By LIST_ROLE = By.cssSelector("main button[role='combobox'][aria-label='Role']");
    private static final By ROWS = By.cssSelector("main tbody tr");
    private static final By NO_USERS = By.xpath("//main//*[normalize-space(.)='No users found']");
    private static final By ROW_EMAIL = By.cssSelector("td:nth-child(2) span[title]");
    private static final By ROW_ROLE = By.xpath("./td[3]//div");
    private static final By ROW_ACTIONS = By.cssSelector("button[aria-haspopup='menu']");
    private static final By REMOVE_FROM_COURSE = By.xpath(
            "//div[@role='menuitem'][normalize-space(.)='Remove from course']");

    private static final By DIALOG = By.cssSelector("[role='dialog'][data-state='open']");
    private static final By DIALOG_SEARCH = By.cssSelector(
            "[role='dialog'] input[aria-label='Search users by name or email']");
    private static final By DIALOG_ROLE = By.cssSelector(
            "[role='dialog'] button[role='combobox'][aria-label='Role']");
    private static final By DIALOG_ADD_USER = By.cssSelector("[role='dialog'] button[type='submit']");

    // "Invite" appears when the search matches no existing user
    private static final By DIALOG_INVITE = By.xpath(
            "//div[@role='dialog']//button[starts-with(normalize-space(.), 'Invite')]");
    private static final By INVITE_EMAIL = By.id("invite-email");
    private static final By INVITE_FIRST_NAME = By.id("invite-first-name");
    private static final By INVITE_LAST_NAME = By.id("invite-last-name");

    // Removal may ask for confirmation; the confirm button is whichever one isn't Cancel.
    private static final By CONFIRM_DIALOG = By.cssSelector(
            "[role='alertdialog'][data-state='open'], [role='dialog'][data-state='open']");
    private static final By CONFIRM_BUTTON = By.xpath(
            "(//*[@role='alertdialog' or @role='dialog'][@data-state='open']"
                    + "//button[normalize-space(.)!='' and normalize-space(.)!='Cancel'])[last()]");

    private static final By ERROR_TOAST = By.cssSelector(
            "[aria-label^='Notifications'] li[data-state='open'].destructive");

    public CoursePeoplePage(WebDriver driver) {
        super(driver);
    }

    public CoursePeoplePage openFor(String courseTitle) {
        open("/courses");
        By course = By.xpath("//a[starts-with(@href,'/course/')][contains(normalize-space(.), "
                + literal(courseTitle) + ")]");
        // Published and unpublished courses are separate lists, and the page
        // remembers whichever was shown last, so try the other one if needed.
        if (!isVisible(course, Duration.ofSeconds(5))) {
            click(PUBLISHED);
            if (!isVisible(course, Duration.ofSeconds(5))) {
                click(UNPUBLISHED);
            }
        }
        click(course);
        click(PEOPLE_LINK);
        find(LIST_SEARCH);
        return this;
    }

    /** Add Users → search → pick the result with this email → pick the role → Add User. */
    public CoursePeoplePage addUser(String searchText, String email, String role) {
        click(ADD_USERS);
        type(DIALOG_SEARCH, searchText);
        click(By.xpath("//div[@role='dialog']//form//button[.//div[normalize-space(.)="
                + literal(email) + "]]"));
        chooseRoleAndSubmit(email, role);
        return this;
    }

    /**
     * Add Users → search the email → Invite → name and role → Add and Invite.
     * An address can be invited only once: when its account already exists the
     * search finds it instead, and the user is picked and given the role.
     */
    public CoursePeoplePage inviteUser(String email, String firstName, String lastName, String role) {
        click(ADD_USERS);
        type(DIALOG_SEARCH, email);

        By existing = By.xpath("//div[@role='dialog']//form//button[.//div[normalize-space(.)="
                + literal(email) + "]]");
        wait.until(d -> !d.findElements(DIALOG_INVITE).isEmpty() || !d.findElements(existing).isEmpty());
        if (!driver.findElements(existing).isEmpty()) {
            click(existing);
            chooseRoleAndSubmit(email, role);
            return this;
        }

        click(DIALOG_INVITE);

        // the form carries the searched email over; set it anyway in case it didn't
        if (!email.equals(find(INVITE_EMAIL).getDomProperty("value"))) {
            type(INVITE_EMAIL, email);
        }
        type(INVITE_FIRST_NAME, firstName);
        type(INVITE_LAST_NAME, lastName);
        chooseRoleAndSubmit(email, role);
        return this;
    }

    private void chooseRoleAndSubmit(String email, String role) {
        click(DIALOG_ROLE);
        // the feature may write the role in any case, e.g. "student" for "Student"
        click(By.xpath("//*[@role='option'][translate(normalize-space(.), "
                + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')="
                + literal(role.toLowerCase()) + "]"));

        // picking a role renders its description, which pushes the buttons down;
        // clicking before that settles hits the empty space they left
        wait.until(d -> d.findElements(By.cssSelector("[role='listbox']")).isEmpty()
                && d.findElement(DIALOG_ROLE).getText().strip().equalsIgnoreCase(role));
        waitUntilEnabled(DIALOG_ADD_USER);
        waitUntilStill(DIALOG_ADD_USER);
        click(DIALOG_ADD_USER);
        // A rejected add keeps the dialog open. Some rejections raise a toast;
        // others (e.g. a student given a faculty role) show nothing at all.
        new WebDriverWait(driver, Settings.TIMEOUT)
                .withMessage("the dialog stayed open -- the app rejected adding "
                        + email + " as " + role)
                .until(d -> d.findElements(DIALOG).isEmpty() || !d.findElements(ERROR_TOAST).isEmpty());
    }

    public CoursePeoplePage remove(String email) {
        removeRow(row(email).orElseThrow(
                () -> new NoSuchElementException(email + " is not on the course")));
        return this;
    }

    /**
     * Removes every Student. The free plan allows one student per course, so a
     * new student can only be added once the seat is empty.
     */
    public CoursePeoplePage removeAllStudents() {
        filter("");
        chooseListRole("Students");
        while (true) {
            Optional<WebElement> student = new WebDriverWait(driver, Settings.TIMEOUT)
                    .ignoring(StaleElementReferenceException.class)
                    .until(d -> {
                        List<WebElement> rows = d.findElements(ROWS).stream()
                                .filter(r -> !emailOf(r).isEmpty())
                                .toList();
                        // until the filter applies, other roles are still on screen
                        if (rows.stream().anyMatch(r -> !"Student".equals(roleText(r)))) {
                            return null;
                        }
                        if (!rows.isEmpty()) {
                            return Optional.of(rows.get(0));
                        }
                        return d.findElements(NO_USERS).isEmpty() ? null : Optional.<WebElement>empty();
                    });
            if (student.isEmpty()) {
                break;
            }
            WebElement row = student.get();
            removeRow(row);
            wait.until(ExpectedConditions.stalenessOf(row));
        }
        chooseListRole("All Roles");
        return this;
    }

    private void removeRow(WebElement row) {
        row.findElement(ROW_ACTIONS).click();
        click(REMOVE_FROM_COURSE);

        if (isVisible(CONFIRM_DIALOG, Duration.ofSeconds(3))) {
            click(CONFIRM_BUTTON);
            wait.until(ExpectedConditions.invisibilityOfElementLocated(CONFIRM_DIALOG));
        }
    }

    private void chooseListRole(String option) {
        click(LIST_ROLE);
        click(By.xpath("//*[@role='option'][normalize-space(.)=" + literal(option) + "]"));
        wait.until(d -> d.findElement(LIST_ROLE).getText().strip().equals(option));
    }

    public boolean isListed(String email) {
        return row(email).isPresent();
    }

    /** The role badge on the user's row, e.g. "Owner" or "Student". */
    public String roleOf(String email) {
        return row(email).map(CoursePeoplePage::roleText).orElse("");
    }

    /** Removal can land a moment after the dialog closes. */
    public boolean waitUntilNotListed(String email) {
        try {
            return wait.until(d -> row(email).isEmpty());
        } catch (TimeoutException e) {
            return false;
        }
    }

    public boolean hasError(Duration timeout) {
        return isVisible(ERROR_TOAST, timeout);
    }

    /**
     * Filters the table down to this email first: it pages at 10 rows, so a
     * user can exist without being on screen.
     */
    private Optional<WebElement> row(String email) {
        filter(email);
        // The table loads, and the search is debounced, so an empty or unfiltered
        // table proves nothing: wait for either this user's row or "No users found".
        return new WebDriverWait(driver, Settings.TIMEOUT)
                .ignoring(StaleElementReferenceException.class)
                .until(d -> {
                    Optional<WebElement> match = d.findElements(ROWS).stream()
                            .filter(r -> emailOf(r).equalsIgnoreCase(email))
                            .findFirst();
                    if (match.isPresent()) {
                        return match;
                    }
                    return d.findElements(NO_USERS).isEmpty() ? null : Optional.<WebElement>empty();
                });
    }

    private void filter(String text) {
        WebElement search = find(LIST_SEARCH);
        if (text.equals(search.getDomProperty("value"))) {
            return;
        }
        // clear() bypasses React's onChange, so the old filter would stay applied
        while (!search.getDomProperty("value").isEmpty()) {
            search.sendKeys(Keys.BACK_SPACE);
        }
        search.sendKeys(text);
    }

    /** Waits out a layout shift: the element is in the same place on two reads in a row. */
    private void waitUntilStill(By locator) {
        Point[] last = {null};
        wait.until(d -> {
            Point now = d.findElement(locator).getLocation();
            boolean still = now.equals(last[0]);
            last[0] = now;
            return still;
        });
    }

    private static String roleText(WebElement row) {
        return row.findElement(ROW_ROLE).getText().strip();
    }

    private static String emailOf(WebElement row) {
        List<WebElement> cells = row.findElements(ROW_EMAIL);
        return cells.isEmpty() ? "" : cells.get(0).getDomAttribute("title");
    }

    private static String literal(String text) {
        return text.contains("'") ? "\"" + text + "\"" : "'" + text + "'";
    }
}
