package com.lxp.pages;

import com.lxp.config.Settings;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A course's teacher gradebook and one student's engagement (staging, Nov 2026).
 *
 * - Course side panel → Grades opens the gradebook. It has two views:
 *   Linear (assessments on the left) and Grid (a row per student). Students
 *   are only listed in Grid.
 * - Clicking a student's name in Grid opens a side bar (no dialog role, just
 *   an h2 with the name and a Close button): grade ring "65.85% GRADE",
 *   Missing n, Late n, "Recent Grades" (name + a/b), and a Grades button.
 * - Grades in the side bar opens the student's full grades page, which sits
 *   over the gradebook at the same address: breadcrumb "GRADES / <student>",
 *   h1 "Assessment Grades", "<n> graded · <n> missing", a div table
 *   (Assessment | Due | Submitted | Status | Score), a Total Grade card
 *   (%, GRADED, MISSING, LATE), Return to Course, Course Analytics, Export.
 * - Course Analytics: "ANALYTICS / <student>", Return to Grades Page, View
 *   Grades, and three role=tab buttons. Each tab has header totals and one
 *   Recharts chart:
 *   - Course Grade: overall grade, Missing, Late; one dot per assessment
 *     (x ticks Q1, A1, D1 ...; y Grade %); blue dots graded, white missing.
 *     Hovering a dot shows "<name> / QUIZ / Score: 6/20 (30%)" or "Not submitted".
 *   - Online Activity: Page Views and Participations totals; two line
 *     charts by week ("10/26", "11/2" ...). Tooltip "Week of 11/2 / Page Views / 350".
 *   - Communication: Total Messages, Student, Instructor; a bar chart by
 *     week. Tooltip "Week of 11/2 / Student / 6 / Instructor / 0".
 * - Discussions: each post has a Reply button; Reply puts the one editor at
 *   the bottom in "Replying to <name>" mode, and Post sends the reply.
 */
public class GradesPage extends BasePage {

    private static final Duration LOAD = Duration.ofSeconds(60);
    private static final Pattern COURSE_ID = Pattern.compile("/course/([0-9a-f-]{36})");

    private static final By SIDE_PANEL_GRADES = By.xpath("//main//*[self::a or self::button][normalize-space(.)='Grades']");
    private static final By GRADES_HEADING = By.xpath("//main//h1[normalize-space(.)='Grades'] | //h1[normalize-space(.)='Grades']");
    private static final By GRID = By.xpath("//*[self::button or @role='tab'][normalize-space(.)='Grid']");
    private static final By STUDENT_SEARCH = By.cssSelector("input[placeholder='Search students...']");
    private static final By ASSESSMENT_GRADES = By.xpath("//h1[normalize-space(.)='Assessment Grades']");
    private static final By COURSE_ANALYTICS = By.xpath("//button[normalize-space(.)='Course Analytics'] | //a[normalize-space(.)='Course Analytics']");
    private static final By ANALYTICS_TABS = By.xpath("//button[@role='tab'][contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'COURSE GRADE')]");
    private static final By CHART = By.cssSelector(".recharts-wrapper > svg.recharts-surface");
    private static final By EDITOR = By.xpath("//main//div[@role='textbox'][@aria-label='Rich text editor']");
    /**
     * The composer's send button, just after the editor: "Post" for a new post,
     * "Reply" once Reply is clicked on a post. (Every post also has its own
     * "Reply" button, so it is found after the editor, not anywhere in main.)
     */
    private static final By SEND_REPLY = By.xpath("//main//div[@role='textbox'][@aria-label='Rich text editor']"
            + "/following::button[normalize-space(.)='Reply' or normalize-space(.)='Post'][1]");

    public GradesPage(WebDriver driver) {
        super(driver);
    }

    // ---- the gradebook ----

    /** The course this browser is on, from its address (/course/<id>...). */
    public String courseId() {
        Matcher m = COURSE_ID.matcher(driver.getCurrentUrl());
        if (!m.find()) {
            throw new IllegalStateException("Not on a course page: " + driver.getCurrentUrl());
        }
        return m.group(1);
    }

    /**
     * The course page → Grades in the side panel → Grid. The app remembers the
     * last course tab for the browser session, so that memory is cleared first.
     */
    public void openGradebookGrid() {
        String id = courseId();
        js("Object.keys(sessionStorage).filter(k => k.startsWith('courseTab_')).forEach(k => sessionStorage.removeItem(k));");
        open("/course/" + id + "?type=course");
        WebElement grades = new WebDriverWait(driver, LOAD).until(d -> d.findElements(SIDE_PANEL_GRADES).stream()
                .filter(WebElement::isDisplayed).reduce((a, b) -> b).orElse(null));
        centre(grades);
        grades.click();
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(GRID));
        click(GRID);
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(STUDENT_SEARCH));
    }

    public boolean gradebookShown() {
        return isVisible(GRADES_HEADING, Duration.ofSeconds(5)) && isVisible(STUDENT_SEARCH, Duration.ofSeconds(5));
    }

    private static By studentCell(String name) {
        // the name is a button ("View analytics for this student") in the row's <th scope="row">
        return By.xpath("//main//table//tbody//th[@scope='row']//button[normalize-space(.)=" + literal(name) + "]");
    }

    /** The student's "Calculated Score" in Grid, e.g. "30.00%"; "" if not shown. */
    public String gridCalculatedScore(String name) {
        Object s = js("const name = arguments[0];"
                + "const t = document.querySelector('main table'); if (!t) return '';"
                + "const heads = [...t.querySelectorAll('thead th')].map(h => h.innerText);"
                + "const col = heads.findIndex(h => h.startsWith('Calculated Score'));"
                + "const row = [...t.querySelectorAll('tbody tr')].find(r => r.cells[0] && r.cells[0].innerText.split('\\n')[0].trim() === name);"
                + "return row && col >= 0 && row.cells[col] ? row.cells[col].innerText.trim() : '';", name);
        return s == null ? "" : s.toString();
    }

    /** Clicks the student's name in Grid and waits for their side bar. */
    public void openStudentSideBar(String name) {
        type(STUDENT_SEARCH, name);
        WebElement cell = new WebDriverWait(driver, LOAD).until(ExpectedConditions.elementToBeClickable(studentCell(name)));
        centre(cell);
        cell.click();
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(ExpectedConditions.visibilityOfElementLocated(sideBarBox(name)));
        waitForSideBarData(name);
    }

    /**
     * The side bar opens with grey placeholders and fills in the grade ("68.18% GRADE",
     * Missing, Late, Recent Grades) a few seconds later. Reading it, or clicking its
     * Grades, before then gives an empty side bar and a grades page that never loads.
     */
    private void waitForSideBarData(String name) {
        try {
            new WebDriverWait(driver, LOAD).until(d -> {
                String text = d.findElement(sideBarBox(name)).getText();
                return text.matches("(?s).*\\d%.*") && text.toUpperCase().contains("MISSING");
            });
        } catch (TimeoutException e) {
            System.out.println("[grades] the side bar's grade did not load within " + LOAD.toSeconds() + "s");
        }
    }

    // ---- the side bar ----

    /** The side bar: the box holding the student's h2, the Close button and the Grades button. */
    private static By sideBarBox(String name) {
        return By.xpath("//h2[normalize-space(.)=" + literal(name) + "]/ancestor::div[.//button[@aria-label='Close'] and .//button[normalize-space(.)='Grades']][1]");
    }

    public boolean sideBarShown(String name) {
        return isVisible(sideBarBox(name), Duration.ofSeconds(10));
    }

    public String sideBarText(String name) {
        return find(sideBarBox(name)).getText().replaceAll("\\s+", " ").strip();
    }

    /**
     * What the side bar shows: grade (e.g. "65.85"), missing, late, and its
     * Recent Grades as name → "a/b". A value it doesn't show is absent.
     */
    public Map<String, Object> sideBar(String name) {
        String text = find(sideBarBox(name)).getText();
        Map<String, Object> out = new LinkedHashMap<>();
        Matcher grade = Pattern.compile("(\\d+(?:\\.\\d+)?)%").matcher(text);
        if (grade.find()) {
            out.put("grade", grade.group(1));
        }
        Matcher missing = Pattern.compile("Missing\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (missing.find()) {
            out.put("missing", Integer.parseInt(missing.group(1)));
        }
        Matcher late = Pattern.compile("Late\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (late.find()) {
            out.put("late", Integer.parseInt(late.group(1)));
        }
        Map<String, String> recent = new LinkedHashMap<>();
        int at = text.toUpperCase().indexOf("RECENT GRADES");
        if (at >= 0) {
            Matcher m = Pattern.compile("^(.+?)\\s+(\\d+(?:\\.\\d+)?\\s*/\\s*\\d+(?:\\.\\d+)?)\\s*$", Pattern.MULTILINE)
                    .matcher(text.substring(at + "RECENT GRADES".length()));
            while (m.find()) {
                recent.put(m.group(1).strip(), m.group(2).replaceAll("\\s+", ""));
            }
        }
        out.put("recent", recent);
        return out;
    }

    /** Grades in the side bar → the student's full grades page. */
    public void openStudentGrades(String name) {
        waitForSideBarData(name);
        clickSideBarGrades(name);
        // the page can stay on its loading placeholders: open it again once from the gradebook
        if (gradeRows().isEmpty()) {
            System.out.println("[grades] the grades table stayed empty; opening it again from the gradebook");
            openGradebookGrid();
            openStudentSideBar(name);
            clickSideBarGrades(name);
        }
    }

    private void clickSideBarGrades(String name) {
        WebElement b = find(By.xpath(xpathOf(sideBarBox(name)) + "//button[normalize-space(.)='Grades']"));
        b.click();
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(ASSESSMENT_GRADES));
        waitForTableRows();
    }

    // ---- the student's full grades page ----

    /** The breadcrumb's student, e.g. "CI Test Student" from "GRADES / CI Test Student"; "" if none. */
    public String breadcrumbStudent(String section) {
        Object s = js("const sec = arguments[0].toUpperCase();"
                + "const el = [...document.querySelectorAll('span, div, p, nav')].find(e => e.offsetParent !== null && e.children.length <= 3"
                + "  && e.innerText.replace(/\\s+/g, ' ').trim().toUpperCase().startsWith(sec + ' /'));"
                + "return el ? el.innerText.replace(/\\s+/g, ' ').trim().replace(/^[^/]+\\/\\s*/, '') : '';", section);
        return s == null ? "" : s.toString();
    }

    public boolean studentGradesShown() {
        return isVisible(ASSESSMENT_GRADES, Duration.ofSeconds(10));
    }

    private void waitForTableRows() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> !gradeRows().isEmpty());
        } catch (TimeoutException ignored) {
            // an empty table is read as such
        }
    }

    /** One row of the student's grades table. */
    public record GradeRow(String name, String module, String due, String submitted, String status, String score) {
        /** "6/20" → 6.0; null when not scored ("—/20"). */
        public Double points() {
            String[] p = score.split("/");
            try {
                return Double.parseDouble(p[0].strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public Double outOf() {
            String[] p = score.split("/");
            try {
                return p.length > 1 ? Double.parseDouble(p[1].strip()) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public boolean hasDue() {
            return !due.isBlank() && !due.equals("—");
        }

        public boolean hasSubmitted() {
            return !submitted.isBlank() && !submitted.equals("—");
        }
    }

    /**
     * Every row of the grades table, read in one script call: the header is
     * the div holding the "Submitted" span, the rows are its siblings, five
     * cells each. The first cell is the name, then the module on its own line.
     */
    public List<GradeRow> gradeRows() {
        Object rows = js("const s = [...document.querySelectorAll('span')].find(e => e.textContent.trim() === 'Submitted' && e.offsetParent !== null"
                + "  && e.parentElement.children.length === 5);"
                + "if (!s) return [];"
                + "return [...s.parentElement.parentElement.children].slice(1).filter(r => r.children.length === 5)"
                + "  .map(r => [...r.children].map(c => c.innerText.trim()));");
        List<GradeRow> out = new ArrayList<>();
        if (rows instanceof List<?> list) {
            for (Object r : list) {
                if (r instanceof List<?> cells && cells.size() == 5) {
                    String[] first = String.valueOf(cells.get(0)).split("\\n", 2);
                    out.add(new GradeRow(first[0].strip(), first.length > 1 ? first[1].replaceAll("\\s+", " ").strip() : "",
                            flat(cells.get(1)), flat(cells.get(2)), flat(cells.get(3)), flat(cells.get(4)).replaceAll("\\s+", "")));
                }
            }
        }
        return out;
    }

    /** The summary under "Assessment Grades", e.g. "9 graded · 3 missing". */
    public String gradesSummary() {
        Object s = js("const h = [...document.querySelectorAll('h1')].find(e => e.innerText.trim() === 'Assessment Grades');"
                + "return h && h.nextElementSibling ? h.nextElementSibling.innerText.replace(/\\s+/g, ' ').trim() : '';");
        return s == null ? "" : s.toString();
    }

    /** The Total Grade card: grade ("65.85"), graded, missing, late. */
    public Map<String, Object> totalGradeCard() {
        Object s = js("const el = [...document.querySelectorAll('*')].find(e => [...e.childNodes].some(n => n.nodeType === 3 && /^total grade$/i.test(n.textContent.trim())));"
                + "if (!el) return '';"
                + "let p = el; for (let i = 0; i < 5 && p.parentElement; i++) { p = p.parentElement; if (/late/i.test(p.innerText) && /missing/i.test(p.innerText)) break; }"
                + "return p.innerText;");
        String text = s == null ? "" : s.toString();
        Map<String, Object> out = new LinkedHashMap<>();
        Matcher grade = Pattern.compile("(\\d+(?:\\.\\d+)?)%").matcher(text);
        if (grade.find()) {
            out.put("grade", grade.group(1));
        }
        for (String key : List.of("graded", "missing", "late")) {
            Matcher m = Pattern.compile("(\\d+)\\s*\\n\\s*" + key, Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) {
                out.put(key, Integer.parseInt(m.group(1)));
            }
        }
        return out;
    }

    // ---- Course Analytics ----

    public void openCourseAnalytics() {
        click(COURSE_ANALYTICS);
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(ANALYTICS_TABS));
        waitForChart();
    }

    /** The analytics tabs shown, e.g. [COURSE GRADE, ONLINE ACTIVITY, COMMUNICATION]. */
    public List<String> analyticsTabs() {
        List<String> out = new ArrayList<>();
        for (WebElement t : driver.findElements(By.xpath("//button[@role='tab']"))) {
            String text = t.getText().strip().toUpperCase();
            if (t.isDisplayed() && !text.equals("LINEAR") && !text.equals("GRID")) {
                out.add(text);
            }
        }
        return out;
    }

    /** Opens the analytics tab "Course Grade", "Online Activity" or "Communication". */
    public void openAnalyticsTab(String tab) {
        By button = By.xpath("//button[@role='tab'][contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), "
                + literal(tab.toUpperCase()) + ")]");
        click(button);
        new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> "true".equals(d.findElement(button).getDomAttribute("aria-selected")));
        waitForChart();
    }

    /**
     * Waits for a chart, then for it to settle: Recharts draws the axes
     * first and redraws (a new svg) when the data arrives, so the same ticks
     * and marks must show on two looks in a row.
     */
    private void waitForChart() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> !d.findElements(CHART).isEmpty());
        } catch (TimeoutException ignored) {
            // no chart: read as such
            return;
        }
        String[] last = {""};
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15), Duration.ofMillis(700)).until(d -> {
                Object sig = js("return [...document.querySelectorAll('.recharts-wrapper > svg.recharts-surface')].map(s =>"
                        + "  s.querySelectorAll('.recharts-cartesian-axis-tick').length + ':'"
                        + "  + s.querySelectorAll('circle, path.recharts-rectangle, .recharts-bar-rectangle').length + ':'"
                        + "  + Math.round(s.getBoundingClientRect().width)).join('|');");
                String now = String.valueOf(sig);
                boolean settled = !now.isEmpty() && now.equals(last[0]);
                last[0] = now;
                return settled;
            });
        } catch (TimeoutException ignored) {
            // still changing after 15s: read it anyway
        }
    }

    public int chartCount() {
        return (int) driver.findElements(By.cssSelector(".recharts-wrapper")).stream().filter(WebElement::isDisplayed).count();
    }

    /** The analytics header's numbers, by label: e.g. {OVERALL GRADE=65.85, MISSING=3, LATE=0}. */
    public Map<String, Double> analyticsTotals() {
        // anchor on the analytics tabs: the gradebook's own Linear / Grid tabs stay in the page underneath
        Object s = js("const tabs = [...document.querySelectorAll(\"button[role='tab']\")].find(t => /course grade/i.test(t.innerText)); if (!tabs) return {};"
                + "const top = tabs.getBoundingClientRect().top; const out = {};"
                + "[...document.querySelectorAll('span, div, p')].filter(e => e.offsetParent !== null && e.children.length === 0"
                + "  && e.getBoundingClientRect().bottom < top && /^[A-Za-z][A-Za-z ]+$/.test(e.textContent.trim()))"
                + ".forEach(label => { const v = label.parentElement.innerText.replace(label.innerText, '').trim().match(/^-?\\d+(?:\\.\\d+)?/);"
                + "  if (v) out[label.textContent.trim().toUpperCase()] = parseFloat(v[0]); });"
                + "return out;");
        Map<String, Double> out = new LinkedHashMap<>();
        if (s instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), v instanceof Number n ? n.doubleValue() : Double.NaN));
        }
        return out;
    }

    /** One plotted point: the tooltip's text, and for the Course Grade dots its fill and y as a %. */
    public record Point(String label, String tooltip, String fill, double percent) {
    }

    /**
     * Course Grade: hovers every dot and reads its tooltip. The y position is
     * turned into a % with the y axis' 0% and 100% ticks.
     */
    public List<Point> courseGradePoints() {
        Object axis = js("const s = document.querySelectorAll('.recharts-wrapper > svg.recharts-surface')[0]; if (!s) return null;"
                + "const ticks = [...s.querySelectorAll('.recharts-yAxis .recharts-cartesian-axis-tick')].map(g => ["
                + "  parseFloat(g.textContent), parseFloat(g.querySelector('text').getAttribute('y'))]);"
                + "const xs = [...s.querySelectorAll('.recharts-xAxis .recharts-cartesian-axis-tick text')].map(t => t.textContent.trim());"
                + "const dots = [...s.querySelectorAll('circle')].filter(c => ['6', '7'].includes(c.getAttribute('r')))"
                + "  .map(c => [parseFloat(c.getAttribute('cx')), parseFloat(c.getAttribute('cy')), c.getAttribute('fill')]);"
                + "return {ticks, xs, dots};");
        List<Point> out = new ArrayList<>();
        if (!(axis instanceof Map<?, ?> a)) {
            return out;
        }
        double y0 = Double.NaN;
        double y100 = Double.NaN;
        for (Object t : (List<?>) a.get("ticks")) {
            List<?> tick = (List<?>) t;
            double v = ((Number) tick.get(0)).doubleValue();
            if (v == 0) {
                y0 = ((Number) tick.get(1)).doubleValue();
            } else if (v == 100) {
                y100 = ((Number) tick.get(1)).doubleValue();
            }
        }
        List<?> xs = (List<?>) a.get("xs");
        List<?> dots = (List<?>) a.get("dots");
        for (int i = 0; i < dots.size(); i++) {
            List<?> d = (List<?>) dots.get(i);
            double cy = ((Number) d.get(1)).doubleValue();
            double pct = Double.isNaN(y0) || Double.isNaN(y100) ? Double.NaN : (y0 - cy) / (y0 - y100) * 100;
            String tip = hoverChart(0, ((Number) d.get(0)).doubleValue(), cy);
            out.add(new Point(i < xs.size() ? String.valueOf(xs.get(i)) : "#" + (i + 1), tip, String.valueOf(d.get(2)), pct));
        }
        return out;
    }

    /**
     * A weekly chart (Online Activity has two, Communication one): hovers each
     * week's column and reads the tooltip, e.g. "Week of 11/2 / Page Views / 350".
     * Returns week label ("11/2") → tooltip text. The positions are read in one
     * script call; the bottom chart of Online Activity carries the week labels
     * for both.
     */
    public Map<String, String> weeklyTooltips(int chart) {
        Object ticks = js("const all = [...document.querySelectorAll('.recharts-wrapper > svg.recharts-surface')];"
                + "const s = all[arguments[0]]; if (!s) return [];"
                + "const l = all[all.length - 1];"
                + "const grid = s.querySelector('.recharts-cartesian-grid') || s;"
                + "const g = grid.getBBox(); const sb = s.getBoundingClientRect();"
                + "return [...l.querySelectorAll('.recharts-xAxis .recharts-cartesian-axis-tick text')].map(t => {"
                + "  const r = t.getBoundingClientRect();"
                + "  return [t.textContent.trim(), r.left + r.width / 2 - sb.left, g.y + g.height / 2]; });", chart);
        Map<String, String> out = new LinkedHashMap<>();
        if (ticks instanceof List<?> list) {
            for (Object t : list) {
                List<?> tick = (List<?>) t;
                String tip = hoverChart(chart, ((Number) tick.get(1)).doubleValue(), ((Number) tick.get(2)).doubleValue());
                out.put(String.valueOf(tick.get(0)), tip);
            }
        }
        return out;
    }

    /**
     * Moves the mouse to (x, y) inside chart n (from its top-left) and returns
     * that chart's tooltip text, "" if none shows.
     *
     * Recharts can replace the chart's svg while the mouse moves over it (the
     * Communication chart does), so no element is kept across the hover: the
     * svg is looked up fresh for each try, the tooltip is read by the chart's
     * index in one script call, and a replaced svg is retried.
     */
    private String hoverChart(int chart, double x, double y) {
        for (int attempt = 1; ; attempt++) {
            try {
                WebElement svg = driver.findElements(CHART).get(chart);
                centre(svg);
                org.openqa.selenium.Rectangle r = svg.getRect();
                int dx = (int) Math.round(x - r.getWidth() / 2.0);
                int dy = (int) Math.round(y - r.getHeight() / 2.0);
                new Actions(driver).moveToElement(svg, dx, dy).perform();
                return tooltipOf(chart);
            } catch (org.openqa.selenium.StaleElementReferenceException | IndexOutOfBoundsException e) {
                if (attempt >= 3) {
                    throw e;
                }
                waitForChart();
            }
        }
    }

    /** Chart n's visible tooltip (only that chart's: synced charts can show theirs too), waiting up to 3s. */
    private String tooltipOf(int chart) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(3)).until(d -> {
                Object t = js("const w = document.querySelectorAll('.recharts-wrapper')[arguments[0]]; if (!w) return null;"
                        + "const tip = w.querySelector('.recharts-tooltip-wrapper'); if (!tip) return null;"
                        + "if (getComputedStyle(tip).visibility === 'hidden') return null;"
                        + "const text = tip.innerText.trim(); return text ? text : null;", chart);
                return t == null ? null : t.toString().replaceAll("\\s*\\n\\s*", " / ");
            });
        } catch (TimeoutException e) {
            return "";
        }
    }

    // ---- discussion (teacher) ----

    /** Reply on the post holding this text, write the reply, Post. */
    public void replyToPost(String postText, String reply) {
        WebElement replyButton = new WebDriverWait(driver, LOAD).until(d -> (WebElement) js("const t = arguments[0];"
                + "const leaf = [...document.querySelectorAll('main *')].find(e => e.children.length === 0 && e.textContent.includes(t)"
                + "  && !e.closest(\"[role='textbox']\"));"
                + "if (!leaf) return null;"
                + "let p = leaf; for (let i = 0; i < 10 && p.parentElement; i++) { p = p.parentElement;"
                + "  const b = [...p.querySelectorAll('button')].find(b => b.innerText.trim() === 'Reply'); if (b) return b; }"
                + "return null;", postText));
        centre(replyButton);
        // a post near the end of the list cannot scroll past the sticky composer bar at the bottom
        try {
            replyButton.click();
        } catch (org.openqa.selenium.ElementClickInterceptedException e) {
            js("arguments[0].click();", replyButton);
        }
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//main//*[starts-with(normalize-space(.), 'Replying to')][not(*) or count(*) <= 2]")));
        WebElement editor = find(EDITOR);
        centre(editor);
        editor.click();
        editor.sendKeys(reply);
        WebElement post = wait.until(ExpectedConditions.elementToBeClickable(SEND_REPLY));
        centre(post);
        try {
            post.click();
        } catch (org.openqa.selenium.ElementClickInterceptedException e) {
            js("arguments[0].click();", post);
        }
    }

    /** The post or reply holding this text: its author line and text, "" if it never shows. */
    public String postWith(String text) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
                Object p = js("const t = arguments[0];"
                        + "const leaf = [...document.querySelectorAll('main *')].find(e => e.children.length === 0 && e.textContent.includes(t)"
                        + "  && !e.closest(\"[role='textbox']\"));"
                        + "if (!leaf) return null;"
                        + "let p = leaf; for (let i = 0; i < 8 && p.parentElement; i++) { p = p.parentElement; if (/\\d{1,2}:\\d{2}/.test(p.innerText) && p.innerText.length > t.length + 10) break; }"
                        + "return p.innerText.replace(/\\s+/g, ' ').trim().slice(0, 300);", text);
                return p == null ? null : p.toString();
            });
        } catch (TimeoutException e) {
            return "";
        }
    }

    // ---- helpers ----

    public String path() {
        return driver.getCurrentUrl().replaceFirst("^https?://[^/]+", "");
    }

    public String site() {
        return Settings.BASE_URL;
    }

    private static String flat(Object o) {
        return String.valueOf(o).replaceAll("\\s+", " ").strip();
    }

    private static String xpathOf(By by) {
        return by.toString().replaceFirst("^By\\.xpath:\\s*", "");
    }

    private void centre(WebElement el) {
        js("arguments[0].scrollIntoView({block: 'center'})", el);
    }

    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    private static String literal(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }
}
