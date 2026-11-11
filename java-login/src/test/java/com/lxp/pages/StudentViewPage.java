package com.lxp.pages;

import com.lxp.config.Settings;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A course's Student View (staging, Nov 2026).
 *
 * - The teacher clicks "Student View" (course side panel, or on an item's
 *   page). The app swaps the session to the course's own test student, "CI
 *   Test Student": localStorage "tenant" turns STUDENT, the teacher's session
 *   is parked in "teacherAuthToken" / "teacherTenant" / "teacherUser".
 * - A blue footer is fixed to the bottom of every page: "You are currently in
 *   Student View ... Exit Student View" (z-index 100). The side panel also
 *   offers "Cancel View".
 * - Exit Student View restores the teacher's session (role back, the
 *   teacher* keys gone) and shows "Exited Student View".
 * - In Student View the teacher tools are gone: no Settings, no drag handles,
 *   no Edit / Responses / Quick Grade, no publish buttons. Grades is "My
 *   Grades" for the test student only. /grades/teacher and
 *   /course/{id}/settings show "Not Found" (without the footer).
 * - A quiz's editor has no address of its own: the quiz's address opens the
 *   student's quiz page in Student View.
 */
public class StudentViewPage extends BasePage {

    private static final Duration LOAD = Duration.ofSeconds(60);

    private static final By STUDENT_VIEW = By.xpath("//button[normalize-space(.)='Student View']");
    private static final By EXIT = By.xpath("//button[normalize-space(.)='Exit Student View']");
    private static final By CANCEL_VIEW = By.xpath("//main//button[normalize-space(.)='Cancel View']");
    private static final By FOOTER_TEXT = By.xpath("//*[contains(normalize-space(.), 'You are currently in Student View')][not(*[contains(normalize-space(.), 'You are currently in Student View')])]");
    private static final By EXITED_TOAST = By.xpath("//*[contains(normalize-space(.), 'Exited Student View')][not(*)]");
    private static final By NOT_FOUND = By.xpath("//*[normalize-space(text())='Not Found']");
    private static final By SETTINGS = By.xpath("//main//button[normalize-space(.)='Settings']");
    private static final By COURSE_SETTINGS = By.xpath("//*[normalize-space(text())='Course Settings' or normalize-space(text())='Course Details']");
    private static final By TEACHER_GRADEBOOK = By.xpath("//main//button[normalize-space(.)='Finalize All'] | //main//*[@role='tab'][normalize-space(.)='Grid']");
    private static final By GRADES_LINK = By.xpath("//main//a[normalize-space(.)='Grades']");
    private static final By MY_GRADES = By.xpath("//main//*[normalize-space(text())='My Grades']");
    private static final By NEXT = By.xpath("//main//button[normalize-space(.)='Next']");
    private static final By ITEM_HEADINGS = By.xpath("//main//h4");
    private static final By DRAGGABLE = By.cssSelector("[data-rfd-drag-handle-draggable-id]");
    private static final By DIALOG = By.cssSelector("[role='dialog'][data-state='open'], [role='alertdialog'][data-state='open']");
    private static final By TAKE = By.xpath("//main//button[normalize-space(.)='Take Quiz' or normalize-space(.)='Retake Quiz' or normalize-space(.)='Resume Quiz']");
    private static final By START_QUIZ = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Start Quiz']");
    private static final By SUBMIT_QUIZ = By.xpath("//main//button[normalize-space(.)='Submit Quiz']");
    private static final By ERROR_TOAST = By.cssSelector("[aria-label^='Notifications'] li[data-state='open'].destructive");
    private static final By EDITOR = By.xpath("//main//div[@role='textbox'][@aria-label='Rich text editor']");
    private static final By POST = By.xpath("//main//button[normalize-space(.)='Post']");

    /** The teacher-only controls on an item page. */
    public static final List<String> TEACHER_ITEM_BUTTONS = List.of("Edit", "Responses", "Quick Grade", "Preview", "Save Quiz");

    private String courseUrl;

    public StudentViewPage(WebDriver driver) {
        super(driver);
    }

    // ---- the course ----

    /** Remembers the course the browser is on (call on any of its pages). */
    public void rememberCourse() {
        // only ".../course/{id}": the page may be deeper in the course, e.g. /course/{id}/quickreview/assignment/{item}
        String url = driver.getCurrentUrl().replaceFirst("\\?.*$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?/course/[0-9a-fA-F-]{36})").matcher(url);
        courseUrl = m.find() ? m.group(1) : url;
    }

    public String courseUrl() {
        return courseUrl;
    }

    /**
     * The course's module list. The app remembers the last course tab (e.g.
     * Grades) for the browser session and reopens it at the course's address,
     * so that memory is cleared first.
     */
    public void openCoursePage() {
        if (driver.getCurrentUrl().startsWith(Settings.BASE_URL)) {
            js("Object.keys(sessionStorage).filter(k => k.startsWith('courseTab_')).forEach(k => sessionStorage.removeItem(k));");
        }
        driver.get(courseUrl + "?type=course");
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(moduleHeader("1")));
    }

    private static By moduleHeader(String n) {
        return By.xpath("(//main//*[starts-with(normalize-space(text()), 'Module " + n + ":')])[1]");
    }

    /** Module titles shown on the course page, e.g. [Module 1: Getting Started with Python, ...]. */
    public List<String> moduleTitles() {
        Object titles = js("return [...new Set([...document.querySelectorAll('main *')]"
                + ".filter(e => [...e.childNodes].some(n => n.nodeType === 3 && /^\\s*Module \\d+:/.test(n.textContent)))"
                + ".map(e => e.textContent.replace(/\\s+/g, ' ').trim()))];");
        List<String> out = new ArrayList<>();
        if (titles instanceof List<?> list) {
            list.forEach(t -> out.add(String.valueOf(t)));
        }
        return out;
    }

    /** Opens a module (modules start collapsed) so its items show. */
    public void openModule(String n) {
        int before = driver.findElements(ITEM_HEADINGS).size();
        WebElement header = find(moduleHeader(n));
        centre(header);
        header.click();
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> d.findElements(ITEM_HEADINGS).size() > before);
        } catch (TimeoutException ignored) {
            // already open, or empty
        }
    }

    /** Course page → Module n → the item with this title. */
    public void openItem(String module, String title) {
        openCoursePage();
        openModule(module);
        WebElement item = find(By.xpath("//main//h4[normalize-space(.)=" + literal(title) + "]"));
        centre(item);
        item.click();
        new WebDriverWait(driver, LOAD).until(d -> d.getCurrentUrl().contains("item="));
        find(By.xpath("//main//*[normalize-space(.)='Back']"));
    }

    // ---- entering and leaving ----

    public boolean offersStudentView() {
        return isVisible(STUDENT_VIEW, Duration.ofSeconds(5));
    }

    public void clickStudentView() {
        WebElement b = wait.until(ExpectedConditions.elementToBeClickable(STUDENT_VIEW));
        centre(b);
        b.click();
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(FOOTER_TEXT));
    }

    public void exit() {
        click(EXIT);
        new WebDriverWait(driver, LOAD).until(d -> d.findElements(FOOTER_TEXT).isEmpty());
    }

    public boolean exitedMessageShown() {
        return isVisible(EXITED_TOAST, Duration.ofSeconds(10)) || !driver.findElements(EXITED_TOAST).isEmpty();
    }

    public boolean footerShown() {
        return isVisible(FOOTER_TEXT, Duration.ofSeconds(10));
    }

    public boolean footerGone() {
        return driver.findElements(FOOTER_TEXT).isEmpty();
    }

    public boolean cancelViewShown() {
        return isVisible(CANCEL_VIEW, Duration.ofSeconds(5));
    }

    /** The footer's box and style: x, y, width, height, viewport width/height, z-index, position, background, text. */
    public Map<String, Object> footer() {
        // it slides in from below: wait until it stops moving
        Map<String, Object> last = readFooter();
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Map<String, Object> now = readFooter();
            if (now.equals(last)) {
                return now;
            }
            last = now;
        }
        return last;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readFooter() {
        return (Map<String, Object>) js(
                "const t = [...document.querySelectorAll('*')].find(e => e.children.length === 0"
                        + "  && /^\\s*You are currently in Student View\\s*$/.test(e.textContent));"
                        + "if (!t) return {};"
                        + "let f = t; while (f && getComputedStyle(f).position !== 'fixed') f = f.parentElement;"
                        + "if (!f) return {};"
                        + "const r = f.getBoundingClientRect(), s = getComputedStyle(f);"
                        + "return {x: r.x, y: r.y, width: r.width, height: r.height, vw: innerWidth, vh: innerHeight,"
                        + "  z: s.zIndex, position: s.position, background: s.backgroundColor,"
                        + "  text: f.innerText.replace(/\\s+/g, ' ').trim()};");
    }

    /** The app's session: tenant role, the signed-in user's name, and whether the teacher's session is parked. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> session() {
        return (Map<String, Object>) js(
                "const j = k => { try { return JSON.parse(localStorage.getItem(k)); } catch (e) { return null; } };"
                        + "const auth = j('ailms-auth');"
                        + "return {role: (j('tenant') || {}).role || '',"
                        + "  user: auth && auth.state && auth.state.user ? auth.state.user.first_name + ' <' + auth.state.user.email + '>' : '',"
                        + "  teacherParked: localStorage.getItem('teacherAuthToken') !== null,"
                        + "  path: location.pathname};");
    }

    // ---- what the student sees ----

    /** The item's teacher-only buttons that are showing, e.g. [Edit, Responses]. */
    public List<String> teacherButtonsShown() {
        List<String> shown = new ArrayList<>();
        for (String label : TEACHER_ITEM_BUTTONS) {
            if (driver.findElements(By.xpath("//main//button[normalize-space(.)=" + literal(label) + "]")).stream()
                    .anyMatch(WebElement::isDisplayed)) {
                shown.add(label);
            }
        }
        return shown;
    }

    /** Editable areas on the page: rich text editors, text boxes, contenteditable. */
    public int editableAreas() {
        return ((Number) js("return document.querySelectorAll(\"main [contenteditable='true'], main [role='textbox'],"
                + " main textarea, main input[type='text']\").length;")).intValue();
    }

    public boolean settingsOffered() {
        return isVisible(SETTINGS, Duration.ofSeconds(3));
    }

    /** Opens an address of this site, e.g. /grades/teacher or the course's /settings. */
    public void openPath(String path) {
        open(path.replace("{course}", courseUrl.replaceFirst("^https?://[^/]+", "")));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> !d.findElements(NOT_FOUND).isEmpty()
                    || !d.findElements(By.cssSelector("main")).isEmpty());
        } catch (TimeoutException ignored) {
            // read below
        }
        isVisible(NOT_FOUND, Duration.ofSeconds(5));
    }

    /** What an address showed: "Not Found", a redirect, or the page it opened. */
    public String pageOutcome() {
        if (!driver.findElements(NOT_FOUND).isEmpty()) {
            return "Not Found at " + path();
        }
        return "opened " + path() + ": " + mainText(120);
    }

    public boolean courseSettingsShown() {
        return driver.findElements(COURSE_SETTINGS).stream().anyMatch(WebElement::isDisplayed);
    }

    public boolean teacherGradebookShown() {
        return driver.findElements(TEACHER_GRADEBOOK).stream().anyMatch(WebElement::isDisplayed);
    }

    public void openGrades() {
        click(GRADES_LINK);
        find(MY_GRADES);
    }

    /** Waits (up to 30s) until the page shows every one of these texts; false if it never does. */
    public boolean waitForText(String... texts) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
                String main = mainText(20000);
                for (String t : texts) {
                    if (!main.contains(t)) {
                        return false;
                    }
                }
                return true;
            });
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** The My Grades text: whose grades they are. */
    public String gradesText() {
        return mainText(400);
    }

    public boolean myGradesShown() {
        return isVisible(MY_GRADES, Duration.ofSeconds(10));
    }

    /** A page item: its title, how much text it shows, and that it is not an error. */
    public String pageContent() {
        return mainText(4000);
    }

    /** Next at the bottom of an item; returns the next item's title. */
    public String next() {
        String before = driver.getCurrentUrl();
        WebElement next = find(NEXT);
        centre(next);
        next.click();
        new WebDriverWait(driver, LOAD).until(d -> !d.getCurrentUrl().equals(before));
        return itemTitle();
    }

    /** The open item's title, from the breadcrumb row's last part. */
    public String itemTitle() {
        Object t = js("const m = document.querySelector('main'); if (!m) return '';"
                + "const h = [...m.querySelectorAll('h1, h2, h3')].find(e => e.offsetParent !== null && e.innerText.trim());"
                + "return h ? h.innerText.trim() : '';");
        return t == null ? "" : t.toString();
    }

    // ---- module list ----

    public int dragHandles() {
        return driver.findElements(DRAGGABLE).size();
    }

    /** Drags Module 1 onto Module 2 the way a mouse would. */
    public void dragFirstModuleOntoSecond() {
        WebElement from = find(moduleHeader("1"));
        WebElement to = find(moduleHeader("2"));
        centre(from);
        new Actions(driver).clickAndHold(from).pause(Duration.ofMillis(300)).moveByOffset(0, 10)
                .pause(Duration.ofMillis(300)).moveToElement(to).pause(Duration.ofMillis(300)).release().perform();
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Opens every module, scrolls every scrolling area to its end, and reports the last item against the footer. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> lastItemAtBottom() {
        openCoursePage();
        for (String n : List.of("1", "2", "3", "4")) {
            if (!driver.findElements(moduleHeader(n)).isEmpty()) {
                openModule(n);
            }
        }
        js("[...document.querySelectorAll('*')].filter(e => e.scrollHeight > e.clientHeight + 50"
                + "  && /auto|scroll/.test(getComputedStyle(e).overflowY)).forEach(e => e.scrollTop = e.scrollHeight);"
                + "window.scrollTo(0, document.body.scrollHeight);");
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return (Map<String, Object>) js(
                "const hs = [...document.querySelectorAll('main h4')]; const h = hs[hs.length - 1];"
                        + "if (!h) return {};"
                        + "const r = h.getBoundingClientRect();"
                        + "const top = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);"
                        + "return {title: h.innerText.trim(), top: r.y, bottom: r.y + r.height, onTop: h === top || h.contains(top),"
                        + "  coveredBy: top ? (top.innerText || top.tagName).slice(0, 60) : ''};");
    }

    // ---- windows over the footer ----

    /** The open window: its z-index (first positioned ancestor), box, and whether each of its buttons is on top. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> openWindow() {
        return (Map<String, Object>) js(
                "const x = document.querySelector(\"[role='dialog'][data-state='open'], [role='alertdialog'][data-state='open']\");"
                        + "if (!x) return {};"
                        + "let z = 'auto'; for (let p = x; p; p = p.parentElement) { const v = getComputedStyle(p).zIndex; if (v !== 'auto') { z = v; break; } }"
                        + "const r = x.getBoundingClientRect();"
                        + "const buttons = [...x.querySelectorAll('button')].filter(b => b.offsetParent !== null).map(b => {"
                        + "  const q = b.getBoundingClientRect(); const t = document.elementFromPoint(q.x + q.width / 2, q.y + q.height / 2);"
                        + "  return (b.innerText.trim() || b.getAttribute('aria-label') || '?') + (b.contains(t) ? '' : ' (covered)'); });"
                        + "return {z: z, top: r.y, bottom: r.y + r.height, buttons: buttons,"
                        + "  title: (x.querySelector('h2') || x).innerText.split('\\n')[0]};");
    }

    /** Opens the quiz's Start Quiz window (Take / Retake), without starting an attempt. */
    public void openStartQuizWindow() {
        WebElement take = find(TAKE);
        centre(take);
        take.click();
        find(START_QUIZ);
    }

    public void closeWindow() {
        driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(DIALOG).isEmpty());
    }

    // ---- small screen ----

    /** Emulates a phone screen (Chrome's device metrics), e.g. iPhone SE 375 x 667. */
    public void emulateScreen(int width, int height) {
        ((HasCdp) driver).executeCdpCommand("Emulation.setDeviceMetricsOverride",
                Map.of("width", width, "height", height, "deviceScaleFactor", 2, "mobile", true));
        driver.navigate().refresh();
    }

    public void stopEmulating() {
        ((HasCdp) driver).executeCdpCommand("Emulation.clearDeviceMetricsOverride", Map.of());
        driver.navigate().refresh();
    }

    /** Whether the control with this aria-label or text is visible and not covered (by the footer or anything else). */
    public String coverOf(String label) {
        Object r = js("const l = arguments[0];"
                + "const b = [...document.querySelectorAll('button, a')].find(e => e.offsetParent !== null"
                + "  && ((e.getAttribute('aria-label') || '') === l || e.innerText.trim() === l));"
                + "if (!b) return 'not shown; on screen: ' + [...document.querySelectorAll('button, a')]"
                + "  .filter(e => { const q = e.getBoundingClientRect(); return e.offsetParent !== null && q.width > 2 && q.y >= 0 && q.y < innerHeight; })"
                + "  .map(e => (e.getAttribute('aria-label') || e.innerText.trim()).replace(/\\s+/g, ' ').slice(0, 25)).filter(x => x).join(', ');"
                + "const q = b.getBoundingClientRect();"
                + "const t = document.elementFromPoint(q.x + q.width / 2, q.y + q.height / 2);"
                + "if (!t) return 'off screen';"
                + "if (b.contains(t) || t.contains(b)) return 'clear';"
                + "return 'covered by ' + (t.innerText || t.tagName).replace(/\\s+/g, ' ').slice(0, 60);", label);
        return String.valueOf(r);
    }

    // ---- quiz attempts ----

    /** On the quiz page: which of Take Quiz / Retake Quiz / Resume Quiz shows, "" if none within 10s. */
    public String attemptButton() {
        return isVisible(TAKE, Duration.ofSeconds(10)) ? find(TAKE).getText().strip() : "";
    }

    /** On the quiz page: Take / Retake / Resume → Start Quiz (if asked) → the attempt. */
    public void startOrResumeAttempt() {
        WebElement take = find(TAKE);
        centre(take);
        take.click();
        if (isVisible(START_QUIZ, Duration.ofSeconds(5))) {
            click(START_QUIZ);
        }
        new WebDriverWait(driver, LOAD).until(d -> d.getCurrentUrl().contains("mode=attempt"));
        find(SUBMIT_QUIZ);
    }

    /** Whether this tab still shows an attempt with its Submit Quiz button. */
    public boolean attemptStillOpen() {
        return isVisible(SUBMIT_QUIZ, Duration.ofSeconds(5)) && driver.getCurrentUrl().contains("mode=attempt");
    }

    /** The quiz page's "Past Attempts" list loads after its buttons. */
    public void waitForPastAttempts() {
        isVisible(By.xpath("//main//*[normalize-space(text())='Past Attempts']"), Duration.ofSeconds(20));
        isVisible(By.xpath("//main//*[starts-with(normalize-space(text()), 'Attempt ')]"), Duration.ofSeconds(10));
    }

    /** The attempt id in the address, if the app puts one there. */
    public String attemptAddress() {
        return driver.getCurrentUrl().replaceFirst("^https?://[^/]+", "");
    }

    /** The "Past Attempts" rows on the quiz page, e.g. [Attempt 1 1/1 Nov 4, 2026, 02:31 PM]. */
    public List<String> pastAttempts() {
        Object rows = js("return [...document.querySelectorAll('main tr, main li, main div')]"
                + ".filter(e => e.children.length > 0 && /^\\s*Attempt \\d+/.test(e.innerText || '') && !/Attempt \\d+[\\s\\S]*Attempt \\d+/.test(e.innerText))"
                + ".map(e => e.innerText.replace(/\\s+/g, ' ').trim());");
        List<String> out = new ArrayList<>();
        if (rows instanceof List<?> list) {
            list.stream().map(String::valueOf).distinct().forEach(out::add);
        }
        return out;
    }

    /**
     * Submit Quiz, then Submit Quiz in "Submit Quiz?" (if the page still asks).
     * Returns what followed: the success toast, an error, or what the page says.
     */
    public String submitAndRead() {
        WebElement submit = find(SUBMIT_QUIZ);
        centre(submit);
        submit.click();
        By confirm = By.xpath("//div[@role='dialog' or @role='alertdialog'][.//*[normalize-space(.)='Submit Quiz?']]//button[normalize-space(.)='Submit Quiz']");
        if (isVisible(confirm, Duration.ofSeconds(5))) {
            click(confirm);
        }
        By ok = By.xpath("//*[@aria-label[starts-with(., 'Notifications')]]//li[contains(normalize-space(.), 'evaluated soon')]"
                + " | //main//*[normalize-space(text())='Submitted']");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> !d.findElements(ok).isEmpty()
                    || !d.findElements(ERROR_TOAST).isEmpty() || !d.getCurrentUrl().contains("/course/"));
        } catch (TimeoutException e) {
            return "nothing within 30s, still on " + path();
        }
        if (!driver.findElements(ERROR_TOAST).isEmpty()) {
            return "error: " + driver.findElement(ERROR_TOAST).getDomProperty("textContent").replaceAll("\\s+", " ").strip();
        }
        if (!driver.getCurrentUrl().contains("/course/")) {
            return "left the course, now on " + path();
        }
        return "submitted";
    }

    // ---- tabs ----

    /** Opens a new tab on this address and switches to it; returns the original tab's handle. */
    public String openInNewTab(String url) {
        String original = driver.getWindowHandle();
        driver.switchTo().newWindow(org.openqa.selenium.WindowType.TAB);
        driver.get(url);
        return original;
    }

    public void closeTabAndReturn(String handle) {
        driver.close();
        driver.switchTo().window(handle);
    }

    public void switchTo(String handle) {
        driver.switchTo().window(handle);
    }

    // ---- discussion ----

    /** Writes a post in the discussion's editor and clicks Post. */
    public void post(String text) {
        WebElement editor = find(EDITOR);
        centre(editor);
        editor.click();
        editor.sendKeys(text);
        // something on the page can sit over Post: bring it to the middle, and click by script if still covered
        WebElement post = wait.until(ExpectedConditions.elementToBeClickable(POST));
        centre(post);
        try {
            post.click();
        } catch (org.openqa.selenium.ElementClickInterceptedException e) {
            js("arguments[0].click();", post);
        }
    }

    /** The post holding this text: its author line and text, "" if it never shows. */
    public String postWith(String text) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> {
                Object p = js("const t = arguments[0];"
                        + "const leaf = [...document.querySelectorAll('main *')].find(e => e.children.length === 0 && e.textContent.includes(t)"
                        + "  && !e.closest(\"[role='textbox']\"));"
                        + "if (!leaf) return null;"
                        + "let p = leaf; for (let i = 0; i < 8 && p.parentElement; i++) { p = p.parentElement; if (/CI Test Student|Keerthana|\\d{1,2}:\\d{2}/.test(p.innerText) && p.innerText.length > t.length + 10) break; }"
                        + "return p.innerText.replace(/\\s+/g, ' ').trim().slice(0, 300);", text);
                return p == null ? null : p.toString();
            });
        } catch (TimeoutException e) {
            return "";
        }
    }

    // ---- module publishing (teacher) ----

    /** Module n's publish button: "Published - Click to manage", or its unpublished label. */
    private By modulePublishButton(String n) {
        return By.xpath("//*[starts-with(normalize-space(text()), 'Module " + n + ":')]"
                + "/ancestor::div[@data-rfd-draggable-id][1]//button[contains(@aria-label, 'ublished - Click')]");
    }

    public String modulePublishState(String n) {
        WebElement b = find(modulePublishButton(n));
        return b.getDomAttribute("aria-label");
    }

    /** "Unpublish Module Only", "Publish Module Only", ... from module n's publish menu. */
    public void moduleMenu(String n, String option) {
        String want = option.startsWith("Unpublish") ? "Unpublished" : "Published";
        if (modulePublishState(n).startsWith(want)) {
            // already so (e.g. a run that stopped half-way): nothing to change
            return;
        }
        WebElement b = find(modulePublishButton(n));
        centre(b);
        b.click();
        click(By.xpath("//*[@role='menuitem'][normalize-space(.)=" + literal(option) + "]"));
        // the app may ask to confirm or to sync to Canvas: confirm, and don't sync
        By no = By.xpath("//*[@role='alertdialog' or @role='dialog'][.//*[contains(normalize-space(.), 'Canvas')]]//button[normalize-space(.)='No']");
        By confirm = By.xpath("//*[@role='alertdialog' or @role='dialog']//button[normalize-space(.)='Unpublish' or normalize-space(.)='Publish' or normalize-space(.)='Confirm' or normalize-space(.)='Continue']");
        if (isVisible(no, Duration.ofSeconds(3))) {
            click(no);
        } else if (isVisible(confirm, Duration.ofSeconds(2))) {
            click(confirm);
        }
        new WebDriverWait(driver, LOAD).until(d -> {
            List<WebElement> now = d.findElements(modulePublishButton(n));
            return !now.isEmpty() && String.valueOf(now.get(0).getDomAttribute("aria-label")).startsWith(want);
        });
    }

    // ---- helpers ----

    public String path() {
        return driver.getCurrentUrl().replaceFirst("^https?://[^/]+", "");
    }

    private String mainText(int max) {
        List<WebElement> main = driver.findElements(By.tagName("main"));
        String text = (main.isEmpty() ? driver.findElement(By.tagName("body")) : main.get(0)).getText().replaceAll("\\s+", " ").strip();
        return text.length() > max ? text.substring(0, max) + "…" : text;
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
