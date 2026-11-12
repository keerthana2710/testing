package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * A module the AI Assistant plans inside a course (staging, Nov 2026).
 *
 * Asked "Add a new module on ..." on a course page, the assistant adds the
 * module to the course at once with a "Planning" badge and replies
 * "Module <name> created with N items planned" with a View Module Plan
 * button. That button opens the module's "Content Plan" (the module
 * description and "Module Items (N)") with Save, Reject and Approve Plan.
 * Approve Plan generates the planned items (this takes minutes, so the test
 * does not wait for it).
 */
public class ModulePlanPage extends BasePage {

    private static final By VIEW_PLAN = By.xpath("//button[normalize-space(.)='View Module Plan']");
    private static final By CONTENT_PLAN = By.xpath("//main//h2[normalize-space(.)='Content Plan']");
    // the plan's title: the module name, right under Content Plan
    private static final By PLAN_TITLE = By.xpath("(//main//h2[normalize-space(.)='Content Plan']/following::h3)[1]");
    private static final By PLAN_ITEMS = By.xpath("//main//h3[starts-with(normalize-space(.), 'Module Items')]");
    private static final By APPROVE_PLAN = By.xpath("//main//button[normalize-space(.)='Approve Plan']");
    // in case Approve Plan asks to confirm, as the course plan's "Approve the plan" window does
    private static final By CONFIRM = By.xpath("//div[@role='dialog'][@data-state='open']//button[starts-with(normalize-space(.), 'Approve')]");
    private static final By ERROR_TOAST = By.cssSelector("[aria-label^='Notifications'] li[data-state='open'].destructive");

    /** A course outline module row, by module id; it holds an h3 with the module name, and a badge such as "Planning". */
    private static final String ROW = "//main//div[@data-rfd-draggable-id=%s]";

    public ModulePlanPage(WebDriver driver) {
        super(driver);
    }

    /** The View Module Plan button in the assistant's answer; waits for the Content Plan. */
    public void openFromAssistant() {
        List<WebElement> buttons = driver.findElements(VIEW_PLAN);
        if (buttons.isEmpty()) {
            throw new IllegalStateException("No View Module Plan button in the assistant's answer");
        }
        buttons.get(buttons.size() - 1).click();
        new WebDriverWait(driver, Duration.ofSeconds(60)).until(ExpectedConditions.visibilityOfElementLocated(CONTENT_PLAN));
    }

    /**
     * The module's id, from the plan's address (/course/&lt;id&gt;?type=module&amp;module=&lt;id&gt;).
     * Rows are found by it, so a module with the same name from an earlier day is never mistaken for this one.
     */
    public String moduleId() {
        String query = URI.create(driver.getCurrentUrl()).getQuery();
        for (String part : query == null ? new String[0] : query.split("&")) {
            if (part.startsWith("module=")) {
                return part.substring("module=".length());
            }
        }
        throw new IllegalStateException("No module id in " + driver.getCurrentUrl());
    }

    /** The module's name as the outline shows it ("" if the module is not there). */
    public String outlineName(String moduleId) {
        By name = By.xpath(String.format(ROW, xpathLiteral(moduleId)) + "//h3");
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(60))
                    .until(d -> d.findElements(name).stream().findFirst().map(WebElement::getText).orElse(null)).strip();
        } catch (TimeoutException e) {
            return "";
        }
    }

    public boolean hasViewPlanButton() {
        return isVisible(VIEW_PLAN, Duration.ofSeconds(5));
    }

    /** The module the plan is for, e.g. "Python Decorators". */
    public String planTitle() {
        return find(PLAN_TITLE).getText().strip();
    }

    /** "Module Items (9)" → 9; -1 if not shown. */
    public int plannedItems() {
        List<WebElement> h = driver.findElements(PLAN_ITEMS);
        if (h.isEmpty()) {
            return -1;
        }
        String digits = h.get(0).getText().replaceAll("\\D", "");
        return digits.isEmpty() ? -1 : Integer.parseInt(digits);
    }

    public boolean approveOffered() {
        return isVisible(APPROVE_PLAN, Duration.ofSeconds(15)) && driver.findElement(APPROVE_PLAN).isEnabled();
    }

    /** Approve Plan (and its confirmation, if one is asked); waits for the button to go. Returns "" or the error shown. */
    public String approve() {
        click(APPROVE_PLAN);
        if (isVisible(CONFIRM, Duration.ofSeconds(5))) {
            click(CONFIRM);
        }
        try {
            new WebDriverWait(driver, Duration.ofSeconds(90)).until(d ->
                    d.findElements(APPROVE_PLAN).isEmpty() || !d.findElements(ERROR_TOAST).isEmpty());
        } catch (TimeoutException e) {
            return "Approve Plan was still there after 90s";
        }
        List<WebElement> err = driver.findElements(ERROR_TOAST);
        return err.isEmpty() ? "" : err.get(0).getText().replaceAll("\\s+", " ").strip();
    }

    // ---- the course outline ----

    /** The course outline of the course this page is in (/course/<id>?type=course). */
    public void openCourseOutline() {
        String path = URI.create(driver.getCurrentUrl()).getPath();
        open(path + "?type=course");
    }

    /** The names of the modules in the course outline on this page (empty if it shows none). */
    public List<String> moduleNames() {
        By names = By.xpath("//main//div[@data-rfd-draggable-id]//h3");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> !d.findElements(names).isEmpty());
        } catch (TimeoutException e) {
            return List.of();
        }
        return driver.findElements(names).stream().map(e -> e.getText().strip()).toList();
    }

    /**
     * Another module in the course outline than the given one, as {id, name}:
     * the first one listed. Null if the course has no other module.
     */
    public String[] otherModule(String exceptId) {
        By rows = By.xpath("//main//div[@data-rfd-draggable-id][.//h3]");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(60)).until(d -> !d.findElements(rows).isEmpty());
        } catch (TimeoutException e) {
            return null;
        }
        for (WebElement row : driver.findElements(rows)) {
            String id = row.getAttribute("data-rfd-draggable-id");
            if (id != null && !id.equals(exceptId)) {
                return new String[] {id, row.findElement(By.xpath(".//h3")).getText().strip()};
            }
        }
        return null;
    }

    private static String xpathLiteral(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }

}
