package com.lxp.pages;

import com.lxp.config.Settings;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exporting a course as IMSCC (Course Tools → Export → As IMSCC → Export All)
 * and importing that file into another course (Course Tools → Import →
 * Import from Canvas → Confirm & Import), plus reading a course's modules and
 * what each holds so the two courses can be compared.
 */
public class ImsccPage extends BasePage {

    /** The course list (1000+ courses) is slow; an export or import of a 4-module course took ~35s and ~2 min. */
    private static final Duration LIST_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration EXPORT_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration IMPORT_TIMEOUT = Duration.ofMinutes(8);

    /** The item types a module lists under each item's title. */
    public static final List<String> ITEM_TYPES = List.of("Page", "Quiz", "Assignment", "Discussion");

    private static final By SEARCH = By.cssSelector("input[placeholder='Search courses...']");
    private static final By PUBLISHED = By.xpath("//main//button[starts-with(normalize-space(.), 'Published')]");
    private static final By UNPUBLISHED = By.xpath("//main//button[starts-with(normalize-space(.), 'Unpublished')]");

    private static final By EXPORT = By.xpath("//button[normalize-space(.)='Export']");
    private static final By AS_IMSCC = By.xpath("//button[normalize-space(.)='As IMSCC']");
    private static final By EXPORT_WINDOW = By.xpath("//div[@role='dialog']//*[normalize-space(text())='Export Modules']");
    private static final By EXPORT_ALL = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Export All']");

    private static final By IMPORT = By.xpath("//button[normalize-space(.)='Import']");
    private static final By IMPORT_WINDOW = By.xpath("//div[@role='dialog']//*[normalize-space(text())='Import from Canvas']");
    private static final By IMPORT_FILE = By.cssSelector("[role='dialog'] input[type='file'][accept='.imscc']");
    private static final By CONFIRM_IMPORT = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Confirm & Import']");

    // "Learn Python | py1027-131 · 4 modules · 45 lessons · Last updated ..."
    private static final Pattern COUNTS = Pattern.compile("(\\d+) modules? · (\\d+) lessons?");
    private static final By MODULE_TITLES = By.xpath(
            "//main//*[self::button or self::h3 or self::h4][starts-with(normalize-space(.), 'Module ')]");
    private static final By ERROR_TOAST = By.cssSelector("[aria-label^='Notifications'] li[data-state='open'].destructive");

    private String courseUrl;

    public ImsccPage(WebDriver driver) {
        super(driver);
    }

    /** /courses → search the title → the card showing this title and code (published or unpublished). */
    public void openCourse(String title, String code) {
        open("/courses");
        new WebDriverWait(driver, LIST_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(SEARCH));
        type(SEARCH, title);
        By card = By.xpath("//a[starts-with(@href,'/course/')][.//*[normalize-space(text())=" + literal(title) + "]]"
                + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), "
                + literal(code.toUpperCase()) + ")]");
        if (!isVisible(card, Duration.ofSeconds(15))) {
            click(UNPUBLISHED);
            if (!isVisible(card, Duration.ofSeconds(15))) {
                click(PUBLISHED);
            }
        }
        click(card);
        wait.until(d -> d.getCurrentUrl().contains("/course/"));
        courseUrl = driver.getCurrentUrl().replaceFirst("\\?.*$", "");
        waitForCounts();
    }

    /** Modules and lessons from the course header, e.g. [4, 45]. */
    public int[] counts() {
        Matcher m = COUNTS.matcher(waitForCounts());
        return m.find() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : new int[] {-1, -1};
    }

    private String waitForCounts() {
        return new WebDriverWait(driver, LIST_TIMEOUT).until(d -> {
            String text = d.findElement(By.tagName("main")).getText();
            return COUNTS.matcher(text).find() ? text : null;
        });
    }

    /**
     * Every module in order, with how many of each item type it lists, e.g.
     * "Module 1: Getting Started with Python" → {Page=7, Quiz=3, Assignment=1, Discussion=1}.
     * Modules start collapsed; each one is opened to read its items.
     */
    public Map<String, Map<String, Integer>> outline() {
        driver.get(courseUrl);
        waitForCounts();
        List<String> titles = new ArrayList<>();
        for (WebElement row : driver.findElements(MODULE_TITLES)) {
            String title = row.getText().lines().findFirst().orElse("").strip();
            if (title.matches("Module \\d+:.*") && !titles.contains(title)) {
                titles.add(title);
            }
        }
        for (String title : titles) {
            WebElement row = driver.findElement(By.xpath("(//main//*[normalize-space(text())=" + literal(title) + "])[1]"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'}); arguments[0].click();", row);
            pause(2500);
        }
        String text = driver.findElement(By.tagName("main")).getText();
        Map<String, Map<String, Integer>> outline = new LinkedHashMap<>();
        for (int i = 0; i < titles.size(); i++) {
            int start = text.indexOf(titles.get(i));
            int end = i + 1 < titles.size() ? text.indexOf(titles.get(i + 1), start + 1) : text.length();
            String section = start < 0 ? "" : text.substring(start, end < 0 ? text.length() : end);
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String type : ITEM_TYPES) {
                counts.put(type, (int) section.lines().map(String::strip).filter(type::equals).count());
            }
            outline.put(titles.get(i), counts);
        }
        return outline;
    }

    /** Export → (As IMSCC, on older builds) → Export Modules → Export All, then waits for the .imscc to finish downloading. */
    public Path exportAsImscc() {
        clearDownloads();
        click(EXPORT);
        // Export used to open a menu (As IMSCC / ...); since Nov 2026 it opens
        // the Export Modules window straight away, so As IMSCC is clicked only if shown
        if (!isVisible(EXPORT_WINDOW, Duration.ofSeconds(5)) && isVisible(AS_IMSCC, Duration.ofSeconds(1))) {
            click(AS_IMSCC);
        }
        find(EXPORT_WINDOW);
        click(EXPORT_ALL);
        // "Preparing export ... 100%", then "Ready — downloading in 5s"
        return new WebDriverWait(driver, EXPORT_TIMEOUT, Duration.ofSeconds(2)).until(d -> {
            File[] done = Settings.DOWNLOAD_DIR.toFile().listFiles((dir, name) -> name.endsWith(".imscc"));
            File[] partial = Settings.DOWNLOAD_DIR.toFile().listFiles((dir, name) -> name.endsWith(".crdownload"));
            return done != null && done.length > 0 && (partial == null || partial.length == 0) ? done[0].toPath() : null;
        });
    }

    public void openImport() {
        click(IMPORT);
    }

    public boolean isImportWindowShown() {
        return isVisible(IMPORT_WINDOW, Duration.ofSeconds(10));
    }

    public void upload(Path file) {
        driver.findElement(IMPORT_FILE).sendKeys(file.toAbsolutePath().toString());
    }

    /** The preview the window shows once a file is chosen, e.g. "4 modules to add · 45 items to add". */
    public String preview() {
        return new WebDriverWait(driver, Duration.ofSeconds(60)).until(d -> {
            String text = d.findElement(By.cssSelector("[role='dialog'][data-state='open']")).getText();
            return text.contains("modules to add") || text.contains("to update") || text.contains("Update") ? text : null;
        });
    }

    /** Confirm & Import, then waits for the window to close ("Merging: ...", "Ensuring course overview"). */
    public boolean confirmImport() {
        waitUntilEnabled(CONFIRM_IMPORT);
        click(CONFIRM_IMPORT);
        try {
            new WebDriverWait(driver, IMPORT_TIMEOUT, Duration.ofSeconds(3)).until(d ->
                    !d.findElements(ERROR_TOAST).isEmpty() || d.findElements(IMPORT_WINDOW).isEmpty());
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
        return driver.findElements(ERROR_TOAST).isEmpty();
    }

    public void reload() {
        driver.get(courseUrl);
        waitForCounts();
    }

    public String errorText() {
        try {
            return driver.findElement(ERROR_TOAST).getText().replaceAll("\\s+", " ").strip();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private void clearDownloads() {
        try {
            Files.createDirectories(Settings.DOWNLOAD_DIR);
            try (var files = Files.list(Settings.DOWNLOAD_DIR)) {
                for (Path f : files.toList()) {
                    Files.deleteIfExists(f);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not clear " + Settings.DOWNLOAD_DIR, e);
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String literal(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }
}
