@courseconfig
Feature: Course Setup → Course Configuration
  Signed in as keerthana@sentellent.com (LXP_EMAIL), a new course is created
  for each scenario (its code gets the run's day and time, e.g.
  CFGA-041530) and its Course Setup is filled in.

  Course Setup now has six stages: 1 Syllabus & Schedule, 2 Outline, 3 Tools
  & Labs, 4 Materials, 5 Details, 6 Assessments & Lectures (sub-tabs
  Assessments, Lectures, Structure, Syllabus). The test cases' "stage 4
  (Details)" and "stage 5 (Assignments and Lectures)" are stages 5 and 6 here,
  as Tools & Labs was added. Field names and choices are in CourseSetupPage.

  Exact results are checks: values kept across tabs, Clear the dates, the
  number of modules, due dates inside the course dates, questions per quiz, a
  lecture custom instruction followed. Results that can only be judged by
  reading (does the content suit the level, is the lecture flow book-like or
  independent, are the question types "reasonably similar") are checked
  where a rule fits, and the text is also recorded as REVIEW rows in
  target/cucumber-reports/expected-vs-actual.html to read.

  Generation uses mode Low (plan about 5 min, course about 40 min for 10
  modules; less for 2-3), and each combination is a full course: allow about
  30-60 minutes per Examples row. Add rows to try more combinations. Each run
  leaves its courses ("Config ...") in the course list: delete them by hand.

  Background:
    Given credentials are configured
    And I am signed in

  # Check if all the fields get autosaved: a value in every stage stays the same when tabs are switched
  # Schedule tab: Clear the dates resets the course dates
  Scenario: Every Course Setup stage keeps its values across tabs and after closing, and Clear the dates resets the dates
    When I click Create Course
    And I enter the course name "Config autosave {now}" and code "CFGA"
    And I click Create
    Then the new course is created
    When I open the new course
    And I fill in the course details:
      | field                  | value               |
      | First day              | today               |
      | Last day               | 2 months from today |
    Then the course dates are set

    # One value in every stage that has fields (Materials only takes files)
    When I set in Course Setup:
      | field                       | value                                         |
      | Number of modules           | 3                                             |
      | Lecture hours per week      | 4                                             |
      | Outline custom instructions | Prefix every module name with Week {now}      |
      | Hands-on work               | Their own computer                            |
      | Target Audience             | Undergraduate Students                        |
      | Prior Knowledge Level       | Intermediate                                  |
      | Teaching Style              | Case Study                                    |
      | Details custom instructions | Use examples from retail shops {now}          |
      | Number of questions         | 7                                             |
      | Question distribution       | MCQ Heavy                                     |
      | Quiz custom prompt          | Avoid trick questions {now}                   |
      | Lecture structure           | Book Type                                     |
      | Lecture custom instructions | Open each lecture with a question {now}       |
      | Institution                 | Lakeside College {now}                        |
    And I switch through every Course Setup tab
    Then every value I set is still shown

    # Saved as they were typed: closing without Save & Close keeps them
    When I close Course Setup without saving and open it again
    Then every value I set is still shown

    # Reset date button
    When I clear the course dates
    Then the course dates are empty
    When I fill in the course details:
      | field     | value               |
      | First day | today               |
      | Last day  | 2 months from today |
    Then the course dates are set

    When I close Course Setup
    And I sign out
    Then I am signed out

  # Schedule tab: modules, lecture hours and course duration are translated exactly into the course
  # Details tab: Target Audience, Prior Knowledge Level, Teaching Style shape the content
  # Assessments & Lectures tab: number of questions exact, question types and complexity close,
  # lecture structure (book-like / independent) and lecture custom instructions followed
  Scenario Outline: Generate a course from a configuration: <combination>
    When I click Create Course
    And I enter the course name "Config <combination> {now}" and code "<code>"
    And I click Create
    Then the new course is created
    When I open the new course
    And I fill in the course details:
      | field                  | value                                |
      | Number of modules      | <modules>                            |
      | Lecture hours per week | <hours>                              |
      | First day              | today                                |
      | Last day               | <months> months from today           |
      | Course outline         | Introduction to Python programming. Outcomes: write, run and debug small Python programs. Topics in order: 1. Variables, data types and operators. 2. Control flow and functions. 3. Lists, dictionaries and files. Assessments: weekly quizzes and short programming exercises. |
    And I set in Course Setup:
      | field                       | value                                                        |
      | Target Audience             | <audience>                                                   |
      | Prior Knowledge Level       | <level>                                                      |
      | Teaching Style              | <style>                                                      |
      | Number of questions         | <questions>                                                  |
      | Question distribution       | <distribution>                                               |
      | Lecture structure           | <structure>                                                  |
      | Lecture custom instructions | End every lecture with a section titled "Key Takeaways".     |

    When I click Generate
    And I wait up to 20 minutes until Approve is shown
    And I click Approve
    Then the course plan is approved
    When I wait up to 90 minutes for the course to be generated

    # Schedule: modules and dates translated exactly
    Then the generated course has <modules> modules
    And every due date is inside the course dates
    And Course Setup still shows the configuration after generation

    # Assessments: exact question count; types close to the distribution; complexity to read
    And every standard quiz has <questions> questions of a "<distribution>" mix

    # Lectures: the custom instruction in every lecture; the flow to read
    And every lecture contains "Key Takeaways"
    And the lecture flow is recorded for review as "<structure>"

    # Details: content to read against audience, level and style
    And the content is recorded for review against "<audience>", "<level>" and "<style>"

    When I sign out
    Then I am signed out

    Examples:
      | combination              | code | modules | hours | months | audience               | level        | style       | questions | distribution | structure   |
      | beginner, hands-on, MCQ  | CFGB | 2       | 3     | 1      | High School Students   | Beginner     | Hands-On    | 5         | MCQ Only     | Independent |
      | advanced, theory, mixed  | CFGC | 3       | 4     | 2      | Graduate Students      | Advanced     | Theoretical | 8         | Balanced     | Book Type   |
