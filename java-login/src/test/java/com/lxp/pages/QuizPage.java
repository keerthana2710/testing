package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A course quiz: built from a module's "Add an item", its questions, publishing
 * it, and the course's Student View.
 *
 * In the app: Module "+" (Add an item) → Type Quiz → Title → Create Quiz opens
 * the quiz editor. Questions tab → Add Question → a type adds a blank question
 * at the TOP of the list with its form open; Update Question keeps it, Save Quiz
 * saves the quiz (a Canvas-linked course then asks "Save and sync to Canvas?":
 * No keeps the change in the app). The module list shows the quiz as
 * "M01 - <title>", unpublished until its row's publish button is clicked.
 */
public class QuizPage extends BasePage {

    private static final Duration LOAD = Duration.ofMinutes(1);

    private static final By ADD_ITEM_DIALOG = By.xpath("//div[@role='dialog'][.//*[normalize-space(.)='Add New Item']]");
    private static final By ITEM_TYPE = By.id("type");
    private static final By ITEM_TITLE = By.id("title");
    private static final By CREATE_QUIZ = By.xpath("//div[@role='dialog']//button[normalize-space(.)='Create Quiz']");

    private static final By QUESTIONS_TAB = By.xpath("//main//button[normalize-space(.)='Questions']");
    private static final By ADD_QUESTION = By.xpath("//main//button[@aria-label='Add Question']");
    private static final By UPDATE_QUESTION = By.xpath("//main//button[normalize-space(.)='Update Question']");
    private static final By SAVE_QUIZ = By.xpath("//main//button[normalize-space(.)='Save Quiz']");
    private static final By CANVAS_NO = By.xpath("//*[@role='alertdialog' or @role='dialog'][.//*[contains(normalize-space(.), 'sync to Canvas')]]//button[normalize-space(.)='No']");
    private static final By SAVED_TOAST = By.xpath("//*[@aria-label[starts-with(., 'Notifications')]]//li[contains(normalize-space(.), 'saved successfully')]");
    private static final By TOTAL_QUESTIONS = By.xpath("//main//*[starts-with(normalize-space(.), 'Total Questions :')][not(*)]");

    private static final By STUDENT_VIEW = By.xpath("//button[normalize-space(.)='Student View']");
    private static final By STUDENT_VIEW_BANNER = By.xpath("//*[contains(normalize-space(.), 'You are currently in Student View')][not(*[contains(normalize-space(.), 'You are currently in Student View')])]");
    private static final By EXIT_STUDENT_VIEW = By.xpath("//button[normalize-space(.)='Exit Student View']");

    /** The course page the quiz was made in, without its query. */
    private String courseUrl;

    public QuizPage(WebDriver driver) {
        super(driver);
    }

    /** How the module list and the quiz page title the quiz: "M01 - Python Basics Quiz ...". */
    public static String listedTitle(String title) {
        return "M01 - " + title;
    }

    /** Adds a quiz to the course's first module and opens its editor. Call on the course page. */
    public void createInFirstModule(String title) {
        courseUrl = driver.getCurrentUrl().replaceFirst("\\?.*$", "") + "?type=course";
        click(By.xpath("(//main//button[@aria-label='Add an item'])[1]"));
        find(ADD_ITEM_DIALOG);
        click(ITEM_TYPE);
        click(option("Quiz"));
        type(ITEM_TITLE, title);
        click(CREATE_QUIZ);
        new WebDriverWait(driver, LOAD).until(d -> d.getCurrentUrl().contains("type=quiz"));
        click(QUESTIONS_TAB);
        find(ADD_QUESTION);
        // The footer ("0 questions • 0 points total") shows once the editor has loaded its
        // draft. A question added before that is wiped when the load finishes, so wait for it.
        try {
            new WebDriverWait(driver, LOAD).until(d -> draftCount() >= 0);
        } catch (org.openqa.selenium.TimeoutException e) {
            System.out.println("[quiz] the questions footer did not show; adding questions anyway");
        }
    }

    /**
     * Adds one question: type, question text, and its answer where the type has one.
     * Multiple Choice gets the answer as option 1 (marked correct) and a wrong option 2.
     */
    public void addQuestion(Map<String, String> row) {
        String type = row.get("type").strip();
        String question = row.get("question");
        String answer = row.get("answer") == null ? "" : row.get("answer").strip();

        click(ADD_QUESTION);
        click(option(type));
        // the new question's form opens at the top: work inside the first open form only
        String form = "(//main//button[normalize-space(.)='Update Question'])[1]"
                + "/ancestor::div[.//button[@aria-label='Question Type']][1]";
        find(By.xpath(form + "//button[@aria-label='Question Type'][normalize-space(.)=" + literal(type) + "]"));

        WebElement text = find(By.xpath(form + "//div[@role='textbox'][@aria-label='Rich text editor']"));
        centre(text);
        text.click();
        text.sendKeys(question);

        switch (type) {
            case "Multiple Choice" -> {
                type(By.xpath(form + "//input[@aria-label='Option 1 text']"), answer);
                type(By.xpath(form + "//input[@aria-label='Option 2 text']"), "None of the above");
                clickInView(By.xpath(form + "//button[@aria-label='Mark option 1 as correct']"));
            }
            case "True/False" -> clickInView(By.xpath(form + "//button[normalize-space(.)=" + literal(answer) + "]"));
            case "Short Answer" -> type(By.xpath(form + "//textarea[@aria-label='Sample answer (for reference)']"), answer);
            case "Fill in the Blank" -> type(By.xpath(form + "//textarea[@aria-label='Correct answers (one per line)']"), answer);
            case "Essay" -> { /* no answer key: graded by the teacher */ }
            default -> throw new IllegalArgumentException("No question form for type " + type);
        }
        int open = driver.findElements(UPDATE_QUESTION).size();
        clickInView(By.xpath(form + "//button[normalize-space(.)='Update Question']"));
        wait.until(d -> d.findElements(UPDATE_QUESTION).size() < open);
    }

    /**
     * Is the question in the editor's draft list (closed, i.e. kept with Update Question)?
     * A brand-new quiz's editor can finish loading after the first question is added and
     * reset the list, so the steps check every question before saving.
     */
    public boolean hasDraftQuestion(String question) {
        return !driver.findElements(By.xpath("//main//*[normalize-space(.)=" + literal(question.strip()) + "]"
                + "[not(ancestor::*[@role='textbox'])]")).isEmpty();
    }

    /** The footer's "N questions • X points total" count, or -1 when it is not shown. */
    public int draftCount() {
        for (WebElement e : driver.findElements(By.xpath("//main//*[contains(normalize-space(.), 'points total')][not(*)]"))) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s+questions?").matcher(e.getText());
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    /**
     * The footer's question count once it shows and has stayed the same for 2 seconds
     * (up to 15s), or -1 if the footer never shows.
     */
    public int settledDraftCount() {
        long end = System.currentTimeMillis() + 15_000;
        int last = draftCount();
        long since = System.currentTimeMillis();
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            int now = draftCount();
            if (now != last) {
                last = now;
                since = System.currentTimeMillis();
            } else if (now >= 0 && System.currentTimeMillis() - since >= 2_000) {
                return now;
            }
        }
        return last;
    }

    /** Save Quiz; on a Canvas-linked course answers No to syncing. True when "saved successfully" shows. */
    public boolean save() {
        click(SAVE_QUIZ);
        if (isVisible(CANVAS_NO, Duration.ofSeconds(10))) {
            click(CANVAS_NO);
        }
        return isVisible(SAVED_TOAST, Duration.ofSeconds(30));
    }

    /** The "Total Questions : N" on the quiz page. */
    public int totalQuestions() {
        String t = find(TOTAL_QUESTIONS).getText().replaceAll("\\D+", "");
        return t.isEmpty() ? -1 : Integer.parseInt(t);
    }

    /** From the course page, publishes the quiz with its own row's publish button. */
    public boolean publish(String title) {
        openCoursePage();
        expandFirstModule();
        By row = itemRow(listedTitle(title));
        find(row);
        clickInView(By.xpath(rowXpath(listedTitle(title)) + "//button[starts-with(@aria-label, 'Unpublished')]"));
        return isVisible(By.xpath(rowXpath(listedTitle(title)) + "//button[starts-with(@aria-label, 'Published')]"), Duration.ofSeconds(30));
    }

    /** Student View: the app's own test student ("CI Test Student"), no second sign-in. */
    public void enterStudentView() {
        openCoursePage();
        click(STUDENT_VIEW);
        // switching to the test student's session can take over 30s on staging: wait longer,
        // and if the banner is still missing, reload once (the session may be swapped already)
        if (!isVisible(STUDENT_VIEW_BANNER, LOAD)) {
            System.out.println("[student view] no banner after " + LOAD.toSeconds() + "s; reloading " + driver.getCurrentUrl());
            driver.navigate().refresh();
            new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(STUDENT_VIEW_BANNER));
        }
    }

    public void exitStudentView() {
        click(EXIT_STUDENT_VIEW);
        new WebDriverWait(driver, LOAD).until(d -> d.findElements(STUDENT_VIEW_BANNER).isEmpty());
    }

    /** On the course page: is the quiz listed in the first module? Reloads once, the list can lag. */
    public boolean isListedInFirstModule(String title) {
        for (int attempt = 0; attempt < 2; attempt++) {
            openCoursePage();
            expandFirstModule();
            if (isVisible(By.xpath("//main//h4[normalize-space(.)=" + literal(listedTitle(title)) + "]"), Duration.ofSeconds(10))) {
                return true;
            }
        }
        return false;
    }

    // ---- the student's attempt ----

    /** The quiz's type label on the attempt and response pages, e.g. "multiple choice". */
    private static String typeLabel(String type) {
        return switch (type.strip()) {
            case "Multiple Choice" -> "multiple choice";
            case "True/False" -> "true false";
            case "Essay" -> "essay";
            case "Short Answer" -> "short answer";
            case "Fill in the Blank" -> "fill blank";
            default -> throw new IllegalArgumentException("Unknown question type " + type);
        };
    }

    /** From the course page: opens the quiz from Module 1, then Take Quiz → Start Quiz. */
    public void startAttempt(String title) {
        expandFirstModule();
        clickInView(By.xpath("//main//h4[normalize-space(.)=" + literal(listedTitle(title)) + "]"));
        click(By.xpath("//main//button[normalize-space(.)='Take Quiz']"));
        click(By.xpath("//div[@role='dialog']//button[normalize-space(.)='Start Quiz']"));
        new WebDriverWait(driver, LOAD).until(d -> d.getCurrentUrl().contains("mode=attempt"));
        find(By.xpath("//main//button[normalize-space(.)='Submit Quiz']"));
    }

    /** Answers one question, found by its type label: an option for MCQ and True/False, text for the rest. */
    public void answer(String type, String answer) {
        String block = "//main//*[normalize-space(text())=" + literal(typeLabel(type)) + "]"
                + "/ancestor::div[.//*[@role='radiogroup'] or .//textarea][1]";
        switch (type.strip()) {
            case "Multiple Choice" -> clickInView(By.xpath(block + "//label[normalize-space(.)=" + literal(answer) + "]"));
            case "True/False" -> clickInView(By.xpath(block + "//button[@role='radio'][normalize-space(.)=" + literal(answer) + "]"));
            default -> {
                WebElement box = find(By.xpath(block + "//textarea"));
                centre(box);
                box.click();
                box.clear();
                box.sendKeys(answer);
            }
        }
    }

    /** Submit Quiz, then Submit Quiz again in the "Submit Quiz?" window. */
    public void submitAttempt() {
        clickInView(By.xpath("//main//button[normalize-space(.)='Submit Quiz']"));
        click(By.xpath("//div[@role='dialog' or @role='alertdialog'][.//*[normalize-space(.)='Submit Quiz?']]//button[normalize-space(.)='Submit Quiz']"));
    }

    /** "Your score will be evaluated soon." toast, or the quiz showing Submitted with a past attempt. */
    public String submissionMessage() {
        By toast = By.xpath("//*[@aria-label[starts-with(., 'Notifications')]]//li[contains(normalize-space(.), 'evaluated soon')]");
        By submitted = By.xpath("//main//*[normalize-space(text())='Submitted']");
        new WebDriverWait(driver, LOAD).until(d -> !d.findElements(toast).isEmpty() || !d.findElements(submitted).isEmpty());
        List<WebElement> t = driver.findElements(toast);
        if (!t.isEmpty()) {
            // the toast slides in from off-screen: read its DOM text, not its visible text
            String text = t.get(0).getDomProperty("textContent");
            return text == null || text.isBlank() ? "Your score will be evaluated soon." : text.replaceAll("\\s+", " ").strip();
        }
        return isVisible(By.xpath("//main//*[starts-with(normalize-space(.), 'Attempt 1')]"), Duration.ofSeconds(10))
                ? "Submitted, Attempt 1 listed" : "Submitted";
    }

    // ---- the teacher's Responses ----

    /** From the course page: opens the quiz from Module 1 and its Responses tab. */
    public void openResponses(String title) {
        openCoursePage();
        expandFirstModule();
        clickInView(By.xpath("//main//h4[normalize-space(.)=" + literal(listedTitle(title)) + "]"));
        click(By.xpath("//main//button[normalize-space(.)='Responses']"));
        find(By.xpath("//main//*[normalize-space(text())='Quiz Responses']"));
    }

    /** The Responses table's rows for this student, as "status | score" text. */
    public List<String> responseRows(String student) {
        By rows = By.xpath("//main//tr[.//*[normalize-space(text())=" + literal(student) + "]]");
        isVisible(rows, Duration.ofSeconds(30));
        return driver.findElements(rows).stream().map(r -> r.getText().replaceAll("\\s+", " ").strip()).toList();
    }

    /** The "Total Responses" figure. */
    public int totalResponses() {
        String n = find(By.xpath("//main//*[normalize-space(text())='Total Responses']/preceding-sibling::*[1]")).getText().strip();
        return n.matches("\\d+") ? Integer.parseInt(n) : -1;
    }

    /** Opens the student's latest response (View) and returns the Response Details text. */
    public String openResponseDetails(String student) {
        clickInView(By.xpath("(//main//tr[.//*[normalize-space(text())=" + literal(student) + "]]//button[normalize-space(.)='View'])[1]"));
        WebElement details = find(By.xpath("//div[@role='dialog'][.//*[starts-with(normalize-space(.), 'Response Details')]]"));
        new WebDriverWait(driver, LOAD).until(d -> details.getText().contains("Question Breakdown"));
        return details.getText();
    }

    /** Closes the Response Details window, so the page behind it can be used again. */
    public void closeResponseDetails() {
        By details = By.xpath("//div[@role='dialog'][.//*[starts-with(normalize-space(.), 'Response Details')]]");
        if (driver.findElements(details).isEmpty()) {
            return;
        }
        driver.findElement(By.tagName("body")).sendKeys(org.openqa.selenium.Keys.ESCAPE);
        if (!isGone(details)) {
            clickInView(By.xpath("//div[@role='dialog'][.//*[starts-with(normalize-space(.), 'Response Details')]]//button[normalize-space(.)='Close' or @aria-label='Close']"));
            isGone(details);
        }
    }

    private boolean isGone(By locator) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(locator).isEmpty());
            return true;
        } catch (org.openqa.selenium.TimeoutException stillThere) {
            return false;
        }
    }

    /** One question's part of the Response Details text, found by its type label ("multiple choice", ...). */
    public static String breakdownOf(String details, String type) {
        String label = "\n" + typeLabel(type) + "\n";
        int at = details.indexOf(label);
        if (at < 0) {
            return "";
        }
        int start = details.lastIndexOf("\nQuestion ", at);
        int end = details.indexOf("\nQuestion ", at);
        return details.substring(Math.max(start, 0), end < 0 ? details.length() : end);
    }

    public void openCoursePage() {
        if (courseUrl == null) {
            courseUrl = driver.getCurrentUrl().replaceFirst("\\?.*$", "") + "?type=course";
        }
        driver.get(courseUrl);
        new WebDriverWait(driver, LOAD).until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//main//*[starts-with(normalize-space(text()), 'Module 1')]")));
    }

    /** Opens Module 1 if its items are not shown yet. */
    private void expandFirstModule() {
        By firstModule = By.xpath("(//main//*[starts-with(normalize-space(text()), 'Module 1')])[1]");
        By anyItem = By.xpath("//div[@data-rfd-draggable-id][not(.//div[@data-rfd-draggable-id])]//h4 | //main//*[normalize-space(text())='LESSONS' or normalize-space(text())='ASSESSMENTS']");
        if (driver.findElements(anyItem).isEmpty()) {
            WebElement m = find(firstModule);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'})", m);
            m.click();
            isVisible(anyItem, Duration.ofSeconds(15));
        }
    }

    /**
     * The quiz editor keeps a sticky Save Quiz bar over the bottom of the page:
     * scroll the control to the middle first, and if something still covers
     * it, click it with a script.
     */
    private void clickInView(By locator) {
        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        centre(el);
        try {
            wait.until(ExpectedConditions.elementToBeClickable(el)).click();
        } catch (org.openqa.selenium.ElementClickInterceptedException covered) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }

    private void centre(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", el);
    }

    /** The innermost draggable row holding this item (a module row holds its items too). */
    private static String rowXpath(String listed) {
        return "//div[@data-rfd-draggable-id][.//h4[normalize-space(.)=" + literal(listed) + "]][not(.//div[@data-rfd-draggable-id])]";
    }

    private static By itemRow(String listed) {
        return By.xpath(rowXpath(listed));
    }

    private static By option(String text) {
        return By.xpath("//*[@role='option'][normalize-space(.)=" + literal(text) + "]");
    }

    public String errorText() {
        try {
            return driver.findElement(By.cssSelector("[aria-label^='Notifications'] li[data-state='open'].destructive"))
                    .getText().replaceAll("\\s+", " ").strip();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private static String literal(String s) {
        if (!s.contains("'")) {
            return "'" + s + "'";
        }
        return "concat('" + s.replace("'", "', \"'\", '") + "')";
    }
}
