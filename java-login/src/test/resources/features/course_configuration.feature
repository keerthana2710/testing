@courseconfig
Feature: Course Setup → Course Configuration
  Signed in as keerthana@sentellent.com (LXP_EMAIL), a new course is created
  (its code gets the run's day and time, e.g. CFGA-041530) and its Course
  Setup is filled in.

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

  The course outline is uploaded as a PDF (src/test/resources/files/
  python_course_outline.pdf) on Outline → Course outline → choose a file,
  and the file must show as saved.

  It is all one signed-in user, so it is one scenario on one course: the
  values are set once, checked across tabs and after closing, and then the
  same course is generated from them and checked.

  Generation uses mode Low (plan about 5 min, course about 40 min for 10
  modules; less for 2-3): allow about 30-60 minutes. Each run leaves its
  course ("Config ...") in the course list: delete it by hand.

  # Autosave: a value in every stage stays the same when tabs are switched and after closing
  # Schedule tab: Clear the dates resets the course dates
  # Outline tab: upload the course outline PDF, the file is saved
  # Then the course is generated from that configuration:
  # Schedule: modules, lecture hours and course duration are translated exactly into the course
  # Details: Target Audience, Prior Knowledge Level, Teaching Style shape the content
  # Assessments & Lectures: number of questions exact, question types and complexity close,
  # lecture structure (book-like) and lecture custom instructions followed
  Scenario: Configure a course in Course Setup and generate it from that configuration
    Given credentials are configured
    And I am signed in
    When I click Create Course
    And I enter the course name "Config advanced theory {now}" and code "CFGA"
    And I click Create
    Then the new course is created
    When I open the new course

    # Course Setup → Upload course outline: 1. Upload PDF → expected: File saved
    And I upload the course outline PDF "python_course_outline.pdf"
    Then the course outline file is saved

    # One value in every stage that has fields (Materials only takes files)
    When I fill in the course details:
      | field     | value               |
      | First day | today               |
      | Last day  | 2 months from today |
    And I set in Course Setup:
      | field                       | value                                                     |
      | Number of modules           | 3                                                         |
      | Lecture hours per week      | 4                                                         |
      | Outline custom instructions | Prefix every module name with Week {now}                  |
      | Hands-on work               | Their own computer                                        |
      | Target Audience             | Graduate Students                                         |
      | Prior Knowledge Level       | Advanced                                                  |
      | Teaching Style              | Theoretical                                               |
      | Details custom instructions | Use examples from retail shops {now}                      |
      | Number of questions         | 8                                                         |
      | Question distribution       | Balanced                                                  |
      | Quiz custom prompt          | Avoid trick questions {now}                               |
      | Lecture structure           | Book Type                                                 |
      | Lecture custom instructions | End every lecture with a section titled "Key Takeaways".  |
      | Institution                 | Lakeside College {now}                                    |
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

    # Generate the course from this configuration
    When I click Generate
    And I wait up to 20 minutes until Approve is shown
    And I click Approve
    Then the course plan is approved
    When I wait up to 90 minutes for the course to be generated

    # Schedule: modules and dates translated exactly
    Then the generated course has 3 modules
    And every due date is inside the course dates
    And Course Setup still shows the configuration after generation

    # Assessments: exact question count; types close to the distribution; complexity to read
    And every standard quiz has 8 questions of a "Balanced" mix

    # Lectures: the custom instruction in every lecture; the flow to read
    And every lecture contains "Key Takeaways"
    And the lecture flow is recorded for review as "Book Type"

    # Details: content to read against audience, level and style
    And the content is recorded for review against "Graduate Students", "Advanced" and "Theoretical"

    When I sign out
    Then I am signed out
