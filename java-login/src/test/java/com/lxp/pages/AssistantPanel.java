package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/** The AI Assistant chat panel on the left of every signed-in page. */
public class AssistantPanel extends BasePage {

    private static final By MESSAGE_BOX = By.cssSelector("textarea[aria-label='Chat message']");
    private static final By SEND = By.cssSelector("button[aria-label='Send message']");
    private static final By REPLIES = By.cssSelector("[data-message-role='assistant']");
    private static final By STOP = By.cssSelector("button[aria-label='Stop generating']");
    private static final By OPEN = By.cssSelector("button[aria-label='Open AI Assistant']");
    private static final By CLOSE = By.cssSelector("button[aria-label='Close chat']");

    // Screen-reader status line: blank while a reply streams, then "Assistant replied: <start of it>"
    private static final By STATUS = By.cssSelector("[role='status'][aria-live]");
    private static final String REPLIED = "Assistant replied:";

    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(90);

    public AssistantPanel(WebDriver driver) {
        super(driver);
    }

    /**
     * Clicks the AI Assistant icon. The panel is open after sign-in, so it is
     * closed first and opened again with the icon, which is what a user does.
     */
    public void openWithIcon() {
        if (isVisible(CLOSE, Duration.ofSeconds(5))) {
            click(CLOSE);
            wait.until(d -> d.findElements(MESSAGE_BOX).isEmpty());
        }
        click(OPEN);
    }

    public boolean isOpen() {
        return isVisible(MESSAGE_BOX, Duration.ofSeconds(10));
    }

    /**
     * The action links and buttons in the latest reply (e.g. a "Create blank
     * course" button), leaving out its own Copy / Helpful / Regenerate
     * buttons, the "n steps" trace toggle and links to the user guides.
     */
    public List<WebElement> replyActions() {
        List<WebElement> replies = driver.findElements(REPLIES);
        if (replies.isEmpty()) {
            return List.of();
        }
        return replies.get(replies.size() - 1).findElements(By.xpath(
                ".//a[not(starts-with(@href,'/guides/'))]"
                        + " | .//button[not(@aria-expanded)][not(@aria-label='Copy' or @aria-label='Helpful'"
                        + " or @aria-label='Not helpful' or @aria-label='Regenerate')]"));
    }

    /**
     * The confirm button of a change the assistant asks to approve, e.g. its
     * "Rename module" card (Rename / Cancel). Looked for anywhere in the chat
     * panel, as the card is not always inside the reply. Empty if none shows.
     */
    public java.util.Optional<WebElement> confirmButton() {
        By plain = By.xpath("//textarea[@aria-label='Chat message']/ancestor::*[.//*[@data-message-role]][1]"
                + "//button[not(@disabled)][starts-with(normalize-space(.), 'Rename') or starts-with(normalize-space(.), 'Apply')"
                + " or starts-with(normalize-space(.), 'Approve') or starts-with(normalize-space(.), 'Accept')"
                + " or starts-with(normalize-space(.), 'Confirm') or starts-with(normalize-space(.), 'Save')"
                + " or starts-with(normalize-space(.), 'Update')]");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> d.findElements(plain).stream().anyMatch(WebElement::isDisplayed));
        } catch (org.openqa.selenium.TimeoutException e) {
            return java.util.Optional.empty();
        }
        List<WebElement> shown = driver.findElements(plain).stream().filter(WebElement::isDisplayed).toList();
        return java.util.Optional.of(shown.get(shown.size() - 1));
    }

    /** Sends the question and returns the assistant's finished reply as plain text. */
    public String ask(String question) {
        String before = status();

        type(MESSAGE_BOX, question);
        waitUntilEnabled(SEND);
        click(SEND);

        // the reply bubble (with its Copy button) shows up at once, while still
        // "Thinking…"; it is finished when generating stops and the status says so
        WebDriverWait answering = new WebDriverWait(driver, ANSWER_TIMEOUT);
        answering.withMessage("the AI Assistant never started answering \"" + question + "\"")
                .until(d -> !status().equals(before) || !d.findElements(STOP).isEmpty());
        answering.withMessage("the AI Assistant did not finish answering \"" + question + "\"")
                .until(d -> d.findElements(STOP).isEmpty() && status().startsWith(REPLIED));

        List<WebElement> replies = driver.findElements(REPLIES);
        return answerText(replies.get(replies.size() - 1));
    }

    private String status() {
        return driver.findElements(STATUS).stream()
                // it is visually hidden, and getText() reads only what is shown
                .map(e -> e.getDomProperty("textContent").strip())
                .findFirst()
                .orElse("");
    }

    /** The reply also holds the "n steps" trace and the Copy/Helpful buttons; drop those. */
    private String answerText(WebElement reply) {
        return (String) ((JavascriptExecutor) driver).executeScript(
                "const c = arguments[0].cloneNode(true);"
                        + "c.querySelectorAll('button, span[id]').forEach(e => e.remove());"
                        // a detached copy has no layout, so innerText would be empty
                        + "return c.textContent.replace(/\\s+/g, ' ').trim();",
                reply);
    }
}
