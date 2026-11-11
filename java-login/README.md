# LXP Login — Java + Selenium + Cucumber BDD

UI automation for **https://lxp-stg.campusinsights.ai**, structured with the Page
Object Model and written as a Cucumber BDD scenario.

## Layout

```
java-login/
├── pom.xml                              # dependencies + test runner
├── run.sh                               # runs the suite without touching your shell profile
└── src/test/
    ├── java/com/lxp/
    │   ├── config/Settings.java          # reads .env (this folder, then the parent)
    │   ├── driver/DriverFactory.java     # one Chrome/Firefox/Edge for the whole run
    │   ├── pages/BasePage.java           # waits, clicks, typing — shared by all pages
    │   ├── pages/LoginPage.java          # locators + actions for /login
    │   ├── pages/MfaPage.java            # the 6-digit verification prompt
    │   ├── pages/HomePage.java           # confirms the /home redirect
    │   ├── steps/LoginSteps.java         # the assertions; no raw locators
    │   ├── hooks/Hooks.java              # screenshot-on-failure, clean browser, quit at end
    │   └── runner/RunCucumberTest.java   # Cucumber CLI main(); `mvn test` launches it
    └── resources/
        ├── features/login.feature        # the Gherkin scenario
        └── simplelogger.properties       # quiets SLF4J's startup chatter
```

The rule that makes it POM: **locators and page interactions live in `pages/`,
assertions live in `steps/`, and the scenario reads as plain English.**

## The scenario

```gherkin
@smoke
Scenario: Valid credentials and verification code open the home page
  Given credentials are configured
  When I sign in with my email and password
  Then no login error is shown
  And the 6-digit MFA prompt is displayed
  When I enter the verification code
  And I click Verify Code
  Then I am redirected to the home page
```

The portal requires a six-digit verification code after a successful password
check. At each MFA prompt the run pauses and asks for the OTP in the terminal:
type the 6-digit code from your email and press Enter. The code is never read
from `.env`, so run the suite from a terminal.

It skips itself if `LXP_EMAIL` / `LXP_PASSWORD` are blank.

## Setup

JDK 27 and Maven are installed via Homebrew, and Maven has already downloaded
Selenium 4.27, Cucumber 7.20 and JUnit 5. Selenium Manager fetches the matching
chromedriver automatically — nothing to install by hand.

Homebrew's `openjdk` is keg-only, so `java` is not on your `PATH` by default.
Either use `./run.sh` (which sets it itself), run from VS Code's terminal (the
workspace sets it), or add this to `~/.zshrc`:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Credentials

Read from `../.env`:

```
BASE_URL=https://lxp-stg.campusinsights.ai
LXP_EMAIL=your.user@example.com
LXP_PASSWORD=your-password
BROWSER=chrome
HEADLESS=false
TIMEOUT=30
```

Use the **full** email address — the API rejects a domain with no TLD with
*"Login failed / Invalid email format"*, which looks identical to a wrong
password.

## Running

```bash
./run.sh                      # the login test
./run.sh -DHEADLESS=true      # no visible window
./run.sh -DBROWSER=firefox    # another browser

mvn test                      # once JAVA_HOME is set in your shell
```

In VS Code, select **Run Cucumber test suite** in the Run and Debug panel to
launch `RunCucumberTest` directly, or use **Terminal → Run Task → BDD: run the
login test**. The Java extension pack is required. There is no Testing-sidebar
entry: the suite is driven by Cucumber's own CLI, not a JUnit engine, so VS
Code's test explorer has nothing to discover.

An HTML report lands at `target/cucumber-reports/report.html`; failure
screenshots at `target/screenshots/`, and they are attached to the report too.

## Course quiz (`@quiz`)

[`quiz.feature`](src/test/resources/features/quiz.feature) builds a quiz in
"Learn Python" (PY1027) with one Multiple Choice, True/False, Essay, Short
Answer and Fill in the Blank question, publishes it, takes and submits it as
the course's **Student View** student ("CI Test Student"), then checks the
teacher's **Responses**: the submission is listed, MCQ and True/False are
auto-graded, and the Essay awaits manual grading. About 3 minutes.

```bash
./run.sh -DTAGS=@quiz
```

- Locators live in `pages/QuizPage.java`, checks in `steps/QuizSteps.java`.
  The feature's description lists how every step works in the app.
- One OTP: Student View needs no second sign-in.
- The quiz is saved **without** syncing to Canvas (the sync prompt is
  answered No).
- Every run leaves a published quiz named "M01 - Python Basics Quiz <ddHHmm>"
  and a CI Test Student submission in Module 1. Delete them by hand.

## Roles: student, teacher and admin (`@roles`)

[`roles.feature`](src/test/resources/features/roles.feature) signs in as each
role and checks what it may reach. About 3 minutes; one OTP per sign-in (3).

Each role first visits every page it can reach and checks it loads: My Tasks,
Courses, Programs, Course Templates (teacher and admin) and the profile, which
must show that account's own name, email and role.

| Role | Expected |
|---|---|
| Student | app role STUDENT; no Create Course; /templates sends them to /home; another course's address shows "Course not found", so no Grades/grading |
| Teacher | app role TEACHER; Create Course window and Create Template from IMSCC; Grades in the first course they teach (the account needs one); no "All creators" filter, every listed course is taught by them, another course's address shows "Course not found" |
| Admin | app role ADMIN; everything a teacher has, plus the "All creators" filter, courses taught by several people, and a course taught by someone else (the first one listed) opened with Grades |

```bash
./run.sh -DTAGS=@roles       # all three roles
./run.sh -DTAGS=@student     # just one: @student, @teacher or @admin
```

The accounts are read from `../.env`:

```
LXP_ADMIN_EMAIL=...      LXP_ADMIN_PASSWORD=...
LXP_TEACHER_EMAIL=...    LXP_TEACHER_PASSWORD=...
LXP_STUDENT_EMAIL=...    LXP_STUDENT_PASSWORD=...
```

A role whose email or password is blank is skipped. Locators live in
`pages/RolesPage.java`, checks in `steps/RoleSteps.java`. Nothing is created
or changed. The two course addresses in the feature are the staging courses'
own: update them if those courses are replaced.

## AI Assistant as a teacher (`@assistant`)

[`teacher_assistant.feature`](src/test/resources/features/teacher_assistant.feature)
signs in as the teacher (`LXP_TEACHER_*`, keerthana+t1@sentellent.com), opens the AI Assistant with its
icon and asks about blank courses and programs. It follows the steps given
(Create → Create New shows the form; Programs shows New Program), then
creates a blank course from the assistant. Two scenarios, one OTP each.

```bash
./run.sh -DTAGS=@assistant
```

- Answers vary, so each expected point is a list of words, any one of which
  counts (`the answer covers:`). A missed point is recorded and the run carries on.
- "Create blank course" answers with a **Blank Course** button. It opens a
  window with one field, "What is your course about?". The AI names the
  course from it, then replies "Created an empty course: <name>" with an
  **Open Course** button. While the field is empty, Create Course stays
  disabled and no "required" message is shown.
- The teacher's Free plan allows **1 active course**. A passing run fills
  that slot, so the next run's assistant offers no button and the scenario
  fails. Delete the course the last run made (its card says "Taught by:
  Keerthana Teacher") before running again. `@teacher` in `roles.feature`
  needs the teacher to have a course for its Grades check, so run it while
  that course still exists.
- Locators live in `pages/AssistantPanel.java` and `pages/BlankCoursePage.java`,
  steps in `steps/AssistantSteps.java` and `steps/TeacherAssistantSteps.java`.

## Student View (`@studentview`)

[`student_view.feature`](src/test/resources/features/student_view.feature)
checks "Learn Python" (PY1027) through Student View, signed in as its owner,
keerthana@sentellent.com (`LXP_EMAIL`), not the teacher account. One scenario, one sign-in (one OTP), about 10 minutes, in
three parts, each starting from the course page:

1. Activation, staying in Student View across pages, hidden teacher tools,
   blocked addresses, the footer on a long list and on an iPhone SE screen,
   a window over the footer, and exiting.
2. Module 4 unpublished: the student doesn't see it. It is published again.
3. A quiz of the run's own: Student View started on the quiz page, two
   tabs, submitting, submitting after exiting in another tab, Responses,
   the assignment, and a discussion post.

```bash
./run.sh -DTAGS=@studentview
```

Checks where the app differs from the expected result **fail on purpose**, so
they can be reported. On staging these are: the window's z-index is lower
than the footer's, and a second tab starts a second attempt. The "Session
Timeout" case is not automated: the sign-in tokens last 7 days. Part 3 leaves a published "M01 - SV
Quiz <ddHHmm>" quiz and a discussion post: delete them by hand. Locators live
in `pages/StudentViewPage.java`, steps in `steps/StudentViewSteps.java`.

## Course configuration (`@courseconfig`)

[`course_configuration.feature`](src/test/resources/features/course_configuration.feature)
tests Course Setup on a new course (signed in as `LXP_EMAIL`):

- **Autosave** (a few minutes, no generation): one value in every stage
  (Schedule, Outline, Tools & Labs, Details, Assessments, Lectures, Syllabus)
  is kept when the tabs are switched and after closing without Save & Close;
  **Clear the dates** empties the course dates.
- **Generate from a configuration** (Scenario Outline, one course per
  Examples row, about 30-60 min each in Low mode): modules, lecture hours and
  dates; audience, level and teaching style; questions per quiz, question
  type mix and lecture structure, plus a lecture custom instruction
  ("Key Takeaways"). Checks: the module count, every due date inside the
  course dates, every standard quiz's exact question count and a type mix
  that fits the distribution, the instruction in every lecture.

```bash
./run.sh -DTAGS=@courseconfig
```

What can only be judged by reading (does the content suit the level, do the
lectures flow like a book or stand alone, question difficulty) is recorded
as **REVIEW** rows in `target/cucumber-reports/expected-vs-actual.html`, with
the text to read: REVIEW never passes or fails the run. The test cases'
"stage 4 Details" and "stage 5 Assignments and Lectures" are stages 5 and 6
in the app now (Tools & Labs was added). Every run leaves its "Config ..."
courses: delete them by hand.

## Course grades: student engagement (`@grades`)

[`grades.feature`](src/test/resources/features/grades.feature) checks one
student's engagement from the teacher's gradebook in "Learn Python" (PY1027),
signed in as `LXP_EMAIL`. The student is the course's "CI Test Student",
through Student View, so it needs one OTP.

1. Notes this week's Online Activity and Communication numbers (baseline).
2. The student submits a new quiz, opens a lesson and posts in Week 1
   Discussion 1; the teacher replies to that post.
3. Grades → Grid → the student's name → **side bar** (grade %, Missing,
   Late, Recent Grades) → **Grades** (the "Course Grades" full page:
   Assessment / Due / Submitted / Status / Score) → **Course Analytics**
   (Course Grade, Online Activity, Communication).

The values are cross-checked against each other: the side bar against the
table and Total Grade card, every Course Grade point (read from its
tooltip) against the table's score, the weekly charts against their totals,
and this week's numbers against the baseline. Checks are soft, so the run
reports every mismatch.

```bash
./run.sh -DTAGS=@grades
```

- In the app, the side bar's button is "Grades" (not "Course Grades"), and
  Course Analytics is on that grades page, not in the side bar.
- Gradebook tabs (Linear / Grid) and the analytics tabs are Radix tabs:
  they switch on a real mouse click, not on a script `.click()`.
- Every run leaves a quiz "M01 - Grades Quiz <ddHHmm>", its attempt, a post
  and a reply. Delete them by hand. Locators live in `pages/GradesPage.java`,
  steps in `steps/GradesSteps.java`.

## Course templates (`@templates`)

[`templates.feature`](src/test/resources/features/templates.feature) creates a
template from a downloaded IMSCC, waits for it to generate, creates a course
from it, edits it, and searches and sorts the list.

```bash
./run.sh -DTAGS=@templates
```

- `I wait for the template to finish generating` waits up to **30 minutes**
  (`CREATE_TIMEOUT` in `TemplatesPage`); generation takes 10-12 minutes on
  staging. It checks about once a minute, prints the card's progress
  (e.g. `50% after ~4 min`), and waits on /home in between because the
  templates page is heavy. A slow load or a redrawn card during one check is
  logged and retried instead of failing the wait.
- The card list re-renders while templates generate, so card names are read
  in one script call to avoid stale elements.

## Three quirks of this login page

All handled inside `LoginPage`:

1. **The consent checkbox is the only gate on submit.** `#inline-consent-checkbox`
   ("I agree to the Terms and Privacy Policy") must be ticked or Sign in stays
   `disabled`. It is a Radix control — a `button[role=checkbox]`, not an
   `<input>` — so its state is read from `aria-checked`.
2. **That consent row isn't in the DOM on a fresh load.** It renders as soon as you
   type into the form, so the button looks enabled on first paint, flips to
   disabled when the row appears, then re-enables on consent. Always
   `waitUntilEnabled` before clicking instead of trusting first paint.
3. **Errors arrive as a toast, not inline text.** A failed login renders a Radix
   toast `li[data-state='open'].destructive` with title *Login failed* and body
   *Authentication failed*. There is no field-level error to assert on.

## Adding a page

```java
public class HomePage extends BasePage {
    private static final By HEADING = By.cssSelector("h1");

    public HomePage(WebDriver driver) { super(driver); }

    public HomePage load() { open("/home"); return this; }
}
```

Then new it up in a step class the way `LoginSteps` does, and keep the locators
out of the step definitions.
