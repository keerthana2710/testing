@grades
Feature: Course Grades - a student's engagement from the teacher's gradebook
  Signed in as keerthana@sentellent.com (LXP_EMAIL in .env), the owner of
  "Learn Python" (PY1027). The student checked is the course's test student,
  "CI Test Student", reached through Student View, so one sign-in (one OTP)
  covers both sides: the student submits work, the teacher reads it back.

  Path under test (staging, Nov 2026):
  course -> Grades -> Grid -> click the student's name -> side bar
    (grade %, Missing, Late, Recent Grades, a "Grades" button)
  -> Grades -> the student's grades page: "Assessment Grades" table
    (Assessment | Due | Submitted | Status | Score) and a Total Grade card
  -> Course Analytics -> tabs Course Grade, Online Activity, Communication.
  The test cases call the side bar's "Grades" button "Course Grades"; Course
  Analytics is a link at the top of that grades page, not in the side bar.

  The numbers are cross-checked, not hard-coded: the side bar must agree with
  the grades table, every graph point with the table, and Online Activity and
  Communication must go up by what the student and teacher did in this run
  (compared with the numbers noted at the start).

  Checks are recorded and the run carries on; any missed check fails the
  scenario at the end (see target/cucumber-reports/expected-vs-actual.html).

  Data: the run adds a published quiz "M01 - Grades Quiz <ddHHmm>" to
  Module 1 with one CI Test Student attempt, a CI Test Student post in
  "Week 1 Discussion 1" and the teacher's reply to it. Delete them by hand.

  Scenario: The student side bar, grades page and Course Analytics agree with the student's work
    Given credentials are configured
    And I am signed in
    And I open the course "Learn Python" with code "PY1027"

    # Baseline for TC5 / TC6: this week's activity and messages before the run
    When I note the Course Analytics of "CI Test Student"
    And I go back to the course page

    # ======== Set-up: the student submits an assessment and posts ========
    # TC3 (part 1): on the student's side, submit an assessment
    When I create a quiz "Grades Quiz {now}" with the questions:
      | type            | question                                    | answer |
      | Multiple Choice | Which keyword defines a function in Python? | def    |
    Then the quiz "Grades Quiz {now}" is created with 1 questions
    When I publish the quiz to the course
    And I open the quiz "M01 - Grades Quiz {now}" in module 1
    And I click Student View
    And I start the quiz in this tab
    And I answer "def" and submit the quiz
    Then the quiz is submitted

    # TC5 / TC6 (part 1): the student visits a lesson and posts in a discussion
    When I open the page "Module Objectives" in module 1
    And I open the discussion "Week 1 Discussion 1: Share a Task You Would Automate and Respond to a Classmate" in module 1
    And I post "Grades check {now}" in the discussion
    Then the post is shown as from "CI Test Student"
    When I exit Student View
    Then I am back in the teacher's view

    # TC6 (part 1): the instructor replies to the student's post
    When I open the discussion "Week 1 Discussion 1: Share a Task You Would Automate and Respond to a Classmate" in module 1
    And I reply "Instructor reply {now}" to the post "Grades check {now}"
    Then the reply "Instructor reply {now}" is shown as from "Keerthana"

    # ======== TC1: Grades -> click the student's name -> side bar ========
    When I open the teacher's Grades of the course
    And I click the student "CI Test Student" in the gradebook
    Then the student side bar opens for "CI Test Student"
    And the side bar shows the student's missing and late work and their recent grades
    And the side bar shows the student's grade as a percentage

    # ======== TC2: side bar -> Grades (Course Grades full page) ========
    When I click Grades in the side bar
    Then the grades page opens for "CI Test Student"
    And the grades table lists every assessment by its correct name
    And every row of the grades table shows a due date
    And every row of the grades table shows the correct current status
    And the side bar's grade, missing, late and recent grades match the grades page
    And the gradebook's Calculated Score for the student matches the grades page

    # ======== TC3 (part 2): the submitted assessment is in the table ========
    Then the grades table lists "Grades Quiz {now}" with today's submitted date and a submitted status

    # ======== TC4: Course Analytics -> Course Grade graph ========
    When I click Course Analytics on the grades page
    Then the Course Analytics graphs are shown for "CI Test Student"
    When I open the "Course Grade" graph
    Then the Course Grade graph's overall grade, missing and late match the grades page
    And the Course Grade graph plots one point per assessment in the grades table
    And each Course Grade point matches the assessment's score in the grades table
    And the Course Grade graph plots "Grades Quiz {now}" at 100%

    # ======== TC5: Course Analytics -> Online Activity graph ========
    When I open the "Online Activity" graph
    Then the Online Activity graph shows page views and participations by week
    And the weekly page views and participations add up to the totals
    And this week's page views went up since the start of the run
    And this week's participations went up by at least 2 since the start of the run

    # ======== TC6 (part 2): Course Analytics -> Communication graph ========
    When I open the "Communication" graph
    Then the Communication graph shows the student's messages and the instructor's replies by week
    And the weekly messages add up to the totals
    And this week's student messages went up by 1 since the start of the run
    And this week's instructor replies went up by 1 since the start of the run

    # Done
    When I sign out
    Then I am signed out
