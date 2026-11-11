package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.AssistantPanel;
import com.lxp.report.CheckLog;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asks the AI Assistant a question and checks the meaning of its answer.
 * The wording changes from one answer to the next, so each expected point is
 * a list of words (matched as word starts, ignoring case), any one of which
 * shows the answer makes that point.
 */
public class AssistantSteps {

    private final AssistantPanel assistant = new AssistantPanel(DriverFactory.get());

    private String answer;

    @When("I open the AI Assistant with its icon")
    public void i_open_the_ai_assistant_with_its_icon() {
        assistant.openWithIcon();
    }

    @Then("the AI Assistant is open")
    public void the_ai_assistant_is_open() {
        boolean open = assistant.isOpen();
        CheckLog.check("the AI Assistant chat is open", open ? "message box shown" : "no message box", open);
        assertThat(open).withFailMessage("The AI Assistant did not open").isTrue();
    }

    /** Asked when the assistant only says it opened its on-screen walkthrough. */
    static final String WRITE_THE_STEPS = "Please write those steps here in the chat as a numbered list.";

    @When("I ask the AI Assistant {string}")
    public void i_ask_the_ai_assistant(String question) {
        this.answer = assistant.ask(question);
        CheckLog.check("a finished answer to \"" + question + "\"", answer, !answer.isBlank());
        if (onlyOpenedTheWalkthrough(answer)) {
            // e.g. "I've opened a walkthrough that will guide you ... on your screen. It starts from the Create button."
            // The steps are then on the page, not in the answer: ask for them in the chat to check them.
            String said = answer;
            this.answer = assistant.ask(WRITE_THE_STEPS);
            CheckLog.check("the steps written out (it opened the on-screen walkthrough instead: \"" + said + "\")",
                    answer, !answer.isBlank());
        }
    }

    /** A short reply about the walkthrough it opened, with no steps of its own. */
    static boolean onlyOpenedTheWalkthrough(String answer) {
        String a = answer.toLowerCase();
        boolean opened = a.contains("walkthrough") && (a.contains("opened") || a.contains("is open") || a.contains("on your screen"));
        return opened && answer.split("\\s+").length < 45;
    }

    /**
     * Soft checks: a missing point is recorded as FAIL in the report but the
     * scenario carries on; Hooks fails the scenario at the end.
     */
    @Then("the answer covers:")
    public void the_answer_covers(DataTable points) {
        String text = answer.toLowerCase();

        for (Map<String, String> point : points.asMaps()) {
            String words = point.get("any of");
            // matched at the start of a word, so "AI" is not found inside "contains"
            Optional<String> found = Arrays.stream(words.split(","))
                    .map(String::strip)
                    .filter(word -> Pattern.compile("\\b" + Pattern.quote(word.toLowerCase()))
                            .matcher(text).find())
                    .findFirst();
            CheckLog.soft(point.get("meaning") + " (any of: " + words + ")",
                    found.map(w -> "says \"" + w + "\"").orElse("none of these words in the answer"),
                    found.isPresent());
        }
    }

    /** "Short": a few sentences, not a full guide. */
    @Then("the answer is at most {int} words")
    public void the_answer_is_at_most_words(int max) {
        int words = answer.isBlank() ? 0 : answer.split("\\s+").length;
        CheckLog.soft("the answer is at most " + max + " words", words + " words", words > 0 && words <= max);
    }

    /** e.g. "learn html — 1 course" or "Artificial Intelligence with Python — ... (1 course)". */
    @Then("the answer lists the program {string} with its course count")
    public void the_answer_lists_the_program_with_its_course_count(String program) {
        Matcher m = Pattern.compile(Pattern.quote(program) + "[^()]{0,300}?\\(?(\\d+) courses?\\b", Pattern.CASE_INSENSITIVE)
                .matcher(answer);
        boolean listed = m.find();
        CheckLog.soft("\"" + program + "\" is listed with its course count",
                listed ? program + " (" + m.group(1) + " course" + (m.group(1).equals("1") ? "" : "s") + ")" : answer, listed);
    }
}
