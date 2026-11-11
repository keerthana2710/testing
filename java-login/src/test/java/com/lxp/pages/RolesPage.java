package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a role can reach: the course list (Create Course, whose courses are
 * listed, the admin's "All creators" filter), the Course Templates page, and
 * a course's Grades.
 *
 * What the app does per role (staging, Nov 2026):
 * - Student: /courses has no Create Course and lists only courses the student
 *   is enrolled in; /templates sends them back to /home; a course they are not
 *   enrolled in shows "Course not found", so its Grades can't be reached.
 * - Teacher: Create Course and Create Template from IMSCC are offered; the
 *   course list holds only the courses they teach, and any other course shows
 *   "Course not found".
 * - Admin: the same, plus every teacher's course: the list has an "All
 *   creators" filter and shows courses "Taught by" other people, which the
 *   admin can open, Grades included.
 */
public class RolesPage extends BasePage {

    /** The course list (1000+ courses for the admin) can take over 30s to load. */
    private static final Duration LIST_TIMEOUT = Duration.ofMinutes(2);

    private static final By SORT = By.xpath("//main//button[normalize-space(.)='Favourites First']");
    private static final By SEARCH = By.cssSelector("input[placeholder='Search courses...']");
    private static final By CREATE_COURSE = By.xpath("//main//button[contains(normalize-space(.), 'Create Course')]");
    private static final By ALL_CREATORS = By.xpath("//main//*[self::button or @role='combobox'][normalize-space(.)='All creators']");
    private static final By PUBLISHED = By.xpath("//main//button[starts-with(normalize-space(.), 'Published')]");
    private static final By UNPUBLISHED = By.xpath("//main//button[starts-with(normalize-space(.), 'Unpublished')]");
    private static final By CARDS = By.cssSelector("main a[href^='/course/']");

    private static final By TEMPLATES_HEADING = By.xpath("//main//*[self::h1 or self::h2][normalize-space(.)='Course Templates']");
    private static final By CREATE_TEMPLATE = By.xpath("//main//button[normalize-space(.)='Create Template from IMSCC']");

    private static final By NOT_FOUND = By.xpath("//main//*[normalize-space(text())='Course not found']");
    private static final By COURSE_HEADER = By.xpath("//main//*[normalize-space(text())='Course Overview']");
    private static final By GRADES = By.xpath("//main//*[self::a or self::button][normalize-space(.)='Grades']");
    /** The Grades view's heading. */
    private static final By GRADES_VIEW = By.xpath(
            "//main//*[self::h1 or self::h2 or self::h3][contains(normalize-space(.), 'Grade')]");

    public RolesPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens /courses and waits until the list has settled: a course card shows,
     * or (a student with no courses) the sort button shows and nothing more
     * arrives for a few seconds.
     */
    public void openCourseList() {
        open("/courses");
        new WebDriverWait(driver, LIST_TIMEOUT).until(d ->
                !d.findElements(CARDS).isEmpty() || !d.findElements(SORT).isEmpty());
        clearLeftoverSearch();
        isVisible(CARDS, Duration.ofSeconds(8));
    }

    /**
     * The app remembers the last search on /courses (e.g. "Learn Python" from opening a
     * course by name), so the list comes back filtered. Empty the box and wait for the
     * list to refill.
     */
    private void clearLeftoverSearch() {
        List<org.openqa.selenium.WebElement> boxes = driver.findElements(SEARCH);
        if (boxes.isEmpty()) {
            return;
        }
        org.openqa.selenium.WebElement box = boxes.get(0);
        String left = box.getDomProperty("value");
        if (left == null || left.isBlank()) {
            return;
        }
        int before = driver.findElements(CARDS).size();
        box.click();
        box.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.COMMAND, "a"), org.openqa.selenium.Keys.BACK_SPACE);
        box.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, "a"), org.openqa.selenium.Keys.BACK_SPACE);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(d ->
                    String.valueOf(d.findElement(SEARCH).getDomProperty("value")).isEmpty()
                            && d.findElements(CARDS).size() > before);
        } catch (TimeoutException e) {
            // fewer courses than before is possible only if the search matched them all; carry on
        }
    }

    public boolean offersCreateCourse() {
        return isVisible(CREATE_COURSE, Duration.ofSeconds(5));
    }

    public boolean offersAllCreatorsFilter() {
        return isVisible(ALL_CREATORS, Duration.ofSeconds(5));
    }

    /** The "Published (302)" / "Unpublished (891)" totals, e.g. "Published (302), Unpublished (891)". */
    public String listTotals() {
        List<String> totals = new ArrayList<>();
        for (By tab : List.of(PUBLISHED, UNPUBLISHED)) {
            driver.findElements(tab).stream().findFirst().ifPresent(b -> totals.add(b.getText().replaceAll("\\s+", " ").strip()));
        }
        return totals.isEmpty() ? "no Published/Unpublished tabs" : String.join(", ", totals);
    }

    /** Everyone named in the shown cards' "Taught by:", e.g. [Sushil, Keerthana, Priyanka]. */
    public Set<String> teachersShown() {
        Object names = ((JavascriptExecutor) driver).executeScript(
                "return [...document.querySelectorAll(\"main a[href^='/course/']\")]"
                        + ".map(a => ((a.innerText || '').match(/Taught by:\\s*([^\\n]+?)(?:\\s+\\+\\d+)?\\s*(?:\\n|Course dates|$)/) || [])[1])"
                        + ".filter(Boolean).map(s => s.trim());");
        Set<String> out = new LinkedHashSet<>();
        if (names instanceof List<?> list) {
            list.forEach(n -> out.add(String.valueOf(n)));
        }
        return out;
    }

    /** One course card in the list: its address, title and "Taught by". */
    public record Card(String path, String title, String taughtBy) {
    }

    /** The cards shown in the course list right now. */
    public List<Card> cardsShown() {
        Object rows = ((JavascriptExecutor) driver).executeScript(
                "return [...document.querySelectorAll(\"main a[href^='/course/']\")].map(a => {"
                        + "  const t = (a.innerText || '').replace(/\\s+/g, ' ');"
                        + "  const title = (t.match(/Course title:\\s*(.+?)\\s+Taught by:/) || [])[1] || '';"
                        + "  const by = (t.match(/Taught by:\\s*(.+?)\\s+(?:Course dates|Students|Modules|Estimated)/) || [])[1] || '';"
                        + "  return [a.getAttribute('href'), title.trim(), by.trim()];"
                        + "});");
        List<Card> out = new ArrayList<>();
        if (rows instanceof List<?> list) {
            for (Object row : list) {
                List<?> r = (List<?>) row;
                out.add(new Card(String.valueOf(r.get(0)), String.valueOf(r.get(1)), String.valueOf(r.get(2))));
            }
        }
        return out;
    }

    /**
     * Searches the course list for the title and looks for the card with this
     * code, in the default view and in the Published and Unpublished tabs.
     */
    public boolean isListed(String title, String code) {
        openCourseList();
        if (driver.findElements(SEARCH).isEmpty()) {
            // a student with no courses gets no search box: nothing is listed
            return !driver.findElements(card(title, code)).isEmpty();
        }
        type(SEARCH, title);
        if (isVisible(card(title, code), Duration.ofSeconds(15))) {
            return true;
        }
        for (By tab : List.of(UNPUBLISHED, PUBLISHED)) {
            if (!driver.findElements(tab).isEmpty()) {
                click(tab);
                if (isVisible(card(title, code), Duration.ofSeconds(10))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether this user's course list has a card linking to this address, e.g. /course/9a8c67e8-... */
    public boolean isListedAt(String path) {
        openCourseList();
        By card = By.cssSelector("main a[href='" + path + "']");
        if (isVisible(card, Duration.ofSeconds(10))) {
            return true;
        }
        for (By tab : List.of(UNPUBLISHED, PUBLISHED)) {
            if (!driver.findElements(tab).isEmpty()) {
                click(tab);
                if (isVisible(card, Duration.ofSeconds(10))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The course's address from its card, e.g. /course/127c1257-..., as listed for this user. */
    public String addressOf(String title, String code) {
        if (!isListed(title, code)) {
            throw new IllegalStateException(title + " (" + code + ") is not in this user's course list");
        }
        String href = driver.findElement(card(title, code)).getDomAttribute("href");
        return URI.create(href).getPath();
    }

    /**
     * Opens the first course in this user's own course list (a teacher sees
     * only the courses they teach). Returns its card text, or "" if the list
     * is empty.
     */
    public String openFirstCourse() {
        openCourseList();
        if (driver.findElements(CARDS).isEmpty()) {
            for (By tab : List.of(UNPUBLISHED, PUBLISHED)) {
                if (!driver.findElements(tab).isEmpty()) {
                    click(tab);
                    if (isVisible(CARDS, Duration.ofSeconds(10))) {
                        break;
                    }
                }
            }
        }
        if (driver.findElements(CARDS).isEmpty()) {
            return "";
        }
        org.openqa.selenium.WebElement card = driver.findElement(CARDS);
        String text = card.getText().replaceAll("\\s+", " ").strip();
        open(card.getDomAttribute("href"));
        new WebDriverWait(driver, LIST_TIMEOUT).until(d ->
                !d.findElements(COURSE_HEADER).isEmpty() || !d.findElements(NOT_FOUND).isEmpty());
        return text;
    }

    /** Opens a course by its address; true if it opened, false on "Course not found". */
    public boolean openCourseAt(String address) {
        open(address);
        try {
            new WebDriverWait(driver, LIST_TIMEOUT).until(d ->
                    !d.findElements(NOT_FOUND).isEmpty() || !d.findElements(COURSE_HEADER).isEmpty());
        } catch (TimeoutException e) {
            return false;
        }
        return driver.findElements(NOT_FOUND).isEmpty();
    }

    /** What the course page says, for the report: "Course not found ..." or the course header. */
    public String courseMessage() {
        String text = driver.findElement(By.tagName("main")).getText().replaceAll("\\s+", " ").strip();
        return text.length() > 140 ? text.substring(0, 140) + "…" : text;
    }

    public boolean offersGrades() {
        return isVisible(GRADES, Duration.ofSeconds(5));
    }

    /**
     * Clicks Grades in the course's side panel and waits for a Grades heading
     * that wasn't there before (it opens inside the course page, same address).
     * Returns the address and heading shown, or "" if nothing opened.
     */
    public String openGrades() {
        int before = driver.findElements(GRADES_VIEW).size();
        String url = driver.getCurrentUrl();
        click(GRADES);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d ->
                    d.findElements(GRADES_VIEW).size() > before || !d.getCurrentUrl().equals(url));
            isVisible(GRADES_VIEW, Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            return "";
        }
        return path() + " " + driver.findElements(GRADES_VIEW).stream().findFirst()
                .map(e -> e.getText().replaceAll("\\s+", " ").strip()).orElse("");
    }

    public boolean gradesShown() {
        return isVisible(GRADES_VIEW, Duration.ofSeconds(5));
    }

    /**
     * Opens /templates. Returns true if the Course Templates page stayed open;
     * false if the app sent the user elsewhere (a student goes to /home).
     */
    public boolean openTemplates() {
        open("/templates");
        try {
            new WebDriverWait(driver, LIST_TIMEOUT).until(d ->
                    !d.findElements(TEMPLATES_HEADING).isEmpty() || !path().startsWith("/templates"));
        } catch (TimeoutException e) {
            return false;
        }
        return path().startsWith("/templates") && !driver.findElements(TEMPLATES_HEADING).isEmpty();
    }

    public boolean offersCreateTemplate() {
        return isVisible(CREATE_TEMPLATE, Duration.ofSeconds(15));
    }

    public String path() {
        return URI.create(driver.getCurrentUrl()).getPath();
    }

    private static By card(String title, String code) {
        return By.xpath("//main//a[starts-with(@href,'/course/')][.//*[normalize-space(text())=" + literal(title) + "]]"
                + "[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), "
                + literal(code.toUpperCase()) + ")]");
    }

    private static String literal(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }
}
