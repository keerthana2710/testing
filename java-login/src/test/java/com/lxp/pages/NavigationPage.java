package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * The places a signed-in user moves between: the header tabs (My Tasks,
 * Courses, Programs), the Course Templates page, and the ways to start a
 * course in the Courses page's Create Course window.
 */
public class NavigationPage extends BasePage {

    /** The course list (1000+ courses on staging) can take over 30s to load. */
    private static final Duration PAGE_TIMEOUT = Duration.ofMinutes(2);

    private static final By CREATE = By.xpath("//main//button[contains(normalize-space(.), 'Create Course')]");
    private static final By CREATE_WINDOW = By.xpath("//div[@role='dialog']//h2[normalize-space(.)='Create Course']");
    private static final String SIDE = "//div[@role='dialog']//nav[@aria-label='Ways to create a course']";
    private static final String SWITCH = "//div[@role='dialog']//nav[@aria-label='Switch how to create']";

    public NavigationPage(WebDriver driver) {
        super(driver);
    }

    /** A header tab: "My Tasks", "Courses" or "Programs". */
    public void openTab(String tab) {
        By link = By.xpath("//a[@href=" + literal(tabPath(tab)) + "][starts-with(normalize-space(.), " + literal(tab) + ")]");
        click(link);
    }

    /** Templates has no header tab; it is reached by its address. */
    public void openTemplates() {
        open("/templates");
    }

    /** Whether the page loaded: the right address and something only that page shows. */
    public boolean isPageShown(String page) {
        By marker = switch (page) {
            case "My Tasks" -> By.id("home-board-title");
            // the search box, or for a student with no courses (no search box) the sort button
            case "Courses" -> By.xpath("//input[@placeholder='Search courses...']"
                    + " | //main//button[normalize-space(.)='Favourites First']");
            case "Programs" -> By.xpath("//main//*[self::h1 or self::h2][normalize-space(.)='Programs']");
            case "Templates" -> By.xpath("//main//*[self::h1 or self::h2][normalize-space(.)='Course Templates']");
            default -> throw new IllegalArgumentException("Unknown page: " + page);
        };
        String path = page.equals("Templates") ? "/templates" : tabPath(page);
        try {
            new WebDriverWait(driver, PAGE_TIMEOUT).until(d -> d.getCurrentUrl().contains(path)
                    && !d.findElements(marker).isEmpty() && d.findElement(marker).isDisplayed());
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    public String currentPath() {
        return driver.getCurrentUrl().replaceFirst("^https?://[^/]+", "");
    }

    /** Courses → + Create. */
    public void openCreateCourse() {
        if (!currentPath().startsWith("/courses")) {
            open("/courses");
        }
        new WebDriverWait(driver, PAGE_TIMEOUT).until(ExpectedConditions.elementToBeClickable(CREATE));
        click(CREATE);
        find(CREATE_WINDOW);
    }

    /** The option cards on the window's first panel, e.g. "Create from Existing". */
    public boolean offers(String option) {
        By card = By.xpath("//div[@data-testid='create-course-panel-home']//button[.//span[normalize-space(.)="
                + literal(option) + "]]");
        return isVisible(card, Duration.ofSeconds(5));
    }

    /** Switches the window to "New", "Existing", "Import" or "Template" and checks that panel's content shows. */
    public boolean opensPanel(String way) {
        String panel = switch (way) {
            case "New" -> "new";
            case "Existing" -> "existing";
            case "Import" -> "import";
            case "Template" -> "template";
            default -> throw new IllegalArgumentException("Unknown way to create: " + way);
        };
        String option = switch (way) {
            case "New" -> "Create New";
            case "Existing" -> "Create from Existing";
            case "Import" -> "Import IMSCC File";
            default -> "Create from Template";
        };
        // a wide window shows the side list ("Create from Existing"); a narrow one
        // swaps it for short tabs ("Existing") — use whichever is showing
        By side = By.xpath(SIDE + "//button[.//span[normalize-space(.)=" + literal(option) + "]]");
        By tabs = By.xpath(SWITCH + "//button[normalize-space(.)=" + literal(way) + "]");
        click(isVisible(side, Duration.ofSeconds(2)) ? side : tabs);
        String shown = "//div[@data-testid='create-course-panel-" + panel + "'][not(@hidden)]";
        By content = By.xpath(switch (way) {
            case "New" -> shown + "//input[@name='course-title']";
            case "Existing" -> shown + "//*[normalize-space(text())='Current Courses']";
            case "Import" -> shown + "//*[contains(normalize-space(text()), 'IMSCC') or contains(normalize-space(text()), '.imscc')]";
            // the template library loads from the server
            default -> shown + "//button[normalize-space(.)='Use Template']";
        });
        return isVisible(content, Duration.ofSeconds(20));
    }

    public List<String> ways() {
        return List.of("New", "Existing", "Import", "Template");
    }

    public void closeCreateCourse() {
        driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(CREATE_WINDOW));
    }

    private static String tabPath(String tab) {
        return switch (tab) {
            case "My Tasks" -> "/home";
            case "Courses" -> "/courses";
            case "Programs" -> "/programs";
            default -> throw new IllegalArgumentException("Unknown tab: " + tab + " (use My Tasks, Courses or Programs)");
        };
    }

    private static String literal(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }
}
