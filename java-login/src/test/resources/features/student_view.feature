@studentview
Feature: Student View (SV) of a course
  Signed in as keerthana@sentellent.com (LXP_EMAIL in .env), the owner of
  "Learn Python" (PY1027), which has pages, quizzes, an assignment and a
  discussion, the course is checked as its test student, "CI Test Student",
  through Student View. The teacher account (LXP_TEACHER_*) is not used:
  it has no course with this content and can't open Learn Python.

  It is one scenario with one sign-in (one OTP), in three parts: Student
  View itself, an unpublished module, and the student's quiz / assignment /
  discussion work. Each part starts from the course page. The "Session
  Timeout" test case is not automated: the sign-in tokens last 7 days.

  How Student View works in the app (all handled in StudentViewPage):
  - "Student View" (course side panel, or on an item's page) swaps the
    session to the course's test student: the app's role turns STUDENT, the
    teacher's session is kept aside until Exit Student View brings it back.
  - A blue footer is fixed to the bottom of every page: "You are currently
    in Student View ... Exit Student View". The side panel also has "Cancel
    View".
  - In Student View there is no Settings, no drag handles, no Edit /
    Responses / Quick Grade, no publish buttons; Grades is "My Grades" for
    the test student only. /grades/teacher and the course's /settings
    address show "Not Found". A quiz's editor has no address of its own, so
    the quiz's address opens the student's quiz page.
  - Windows (e.g. Start Quiz) have z-index 50 and the footer 100: a window
    is centred, so they don't meet on a desktop, but the footer would sit on
    top of a window that reaches the bottom of the screen.

  Checks of what the student sees are recorded and the run carries on; any
  missed check fails the scenario at the end, so it can be reported. A step
  the run can't get past (e.g. the quiz can't be created) stops it there.

  Data: part 3 adds a published quiz "M01 - SV Quiz <ddHHmm>" to Module 1,
  with the test student's attempts, and a post in "Week 1 Discussion 1".
  Part 2 unpublishes Module 4 and publishes it again. Delete the quiz and
  the post by hand.

  Scenario: Student View as the course's test student
    Given credentials are configured
    And I am signed in
    And I open the course "Learn Python" with code "PY1027"

    # ======== Part 1: Student View itself ========
    # Successful activation: the footer appears and the role changes to Student
    When I click Student View
    Then the Student View footer appears at the bottom of the page
    And the app's role is "STUDENT" as "CI Test Student"

    # Double activation: no second Student View; a reload keeps Student View
    And Student View cannot be started again
    When I reload the page
    Then the course is still in Student View

    # Persistence on course navigation (Grades link) and grade visibility
    When I open the course's Grades
    Then the course is still in Student View
    And My Grades shows only the test student's grades

    # View content: a Page item renders, Next goes to the next item, still Student View
    When I open the page "Module Objectives" in module 1
    Then the page content shows
    And the course is still in Student View
    When I click Next
    Then the next item "Running Python in the Interactive Interpreter" opens
    And the course is still in Student View

    # Edit content: no Edit button, nothing editable on a Page
    And the page has no Edit button and nothing can be edited

    # Modify module order: no drag handles, and a drag changes nothing
    When I try to drag Module 1 below Module 2
    Then the modules keep their order

    # Content visibility: the footer does not cover the last item of the long module list
    Then the last item of the long module list is not covered by the footer

    # Access course settings: not offered, and its address is blocked
    Then Settings is not offered
    When I open the address "{course}/settings"
    Then the address is blocked

    # Access teacher analytics: /grades/teacher is blocked
    When I open the address "/grades/teacher"
    Then the address is blocked and no teacher gradebook shows

    # Overlapping modals: the Start Quiz window shows above the footer
    When I open the quiz "Week 1 Quiz 1: Python Fundamentals" in module 1
    And I open the Start Quiz window
    Then the window shows above the Student View footer
    When I close the window

    # Mobile view layout: iPhone SE (375 x 667)
    When I switch to an iPhone SE screen
    Then the Student View footer fits the screen
    And the footer does not cover "Back to dashboard", "Open course actions" or "Account menu"
    When I switch back to the desktop screen

    # Cleanup on exit: back to the teacher, the student session ends
    When I exit Student View
    Then I am back in the teacher's view

    # ======== Part 2: an unpublished module ========
    When I go back to the course page
    # Content visibility: unpublish a selected module, the student sees only published ones
    When I unpublish module 4 only
    Then module 4 shows as unpublished
    When I click Student View
    Then the student sees modules 1, 2 and 3 but not module 4
    When I exit Student View
    Then I am back in the teacher's view

    # Put the module back
    When I publish module 4 only
    Then module 4 shows as published

    # ======== Part 3: the student's quiz, assignment and discussion ========
    When I go back to the course page
    # A quiz of this run's own, published in Module 1
    When I create a quiz "SV Quiz {now}" with the questions:
      | type            | question                                    | answer |
      | Multiple Choice | Which keyword defines a function in Python? | def    |
    Then the quiz "SV Quiz {now}" is created with 1 questions
    When I publish the quiz to the course

    # Fresh load state: Student View started on the quiz page shows Take Quiz at once
    When I open the quiz "M01 - SV Quiz {now}" in module 1
    Then the teacher sees "Edit" on the quiz
    When I click Student View
    Then the quiz page reloads in Student View with "Take Quiz" showing at once

    # Edit published item: Edit is hidden, and the quiz's address does not open the editor
    And the quiz has no teacher buttons
    When I open the quiz's own address again
    Then the quiz opens for the student, not in the editor

    # Multiple simultaneous attempts: two tabs on one quiz share one attempt
    When I start the quiz in this tab
    And I open the same quiz in a second tab
    Then the second tab carries on with the same attempt
    When I close the second tab
    And I answer "def" and submit the quiz
    Then the quiz is submitted
    And the quiz lists 1 past attempt

    # Submission persistence: the score is the test student's
    When I open the course's Grades
    Then My Grades lists "SV Quiz {now}" for the test student

    # Submit after exit: an attempt started in Student View can't be submitted after leaving it
    When I open the quiz "M01 - SV Quiz {now}" in module 1
    And I start the quiz in this tab
    And I exit Student View in a second tab
    And I answer "def" and submit the quiz in the first tab
    Then the submission is refused
    When I go back to the teacher's tab

    # The teacher's Responses: only the test student's attempts are recorded
    And I open the responses of the quiz "SV Quiz {now}"
    Then only the test student's attempts are recorded

    # Student submission tab: the assignment offers Start Assignment, no teacher tabs; Grades shows it
    When I click Student View
    And I open the assignment "Week 1 Lab 1: Personal Greeting Program" in module 1
    Then the assignment offers only "Start Assignment" to the student
    When I open the course's Grades
    Then My Grades lists "Week 1 Lab 1: Personal Greeting Program" for the test student

    # Post attribution: a discussion post is marked as from the test student
    When I open the discussion "Week 1 Discussion 1: Share a Task You Would Automate and Respond to a Classmate" in module 1
    And I post "Student View check {now}" in the discussion
    Then the post is shown as from "CI Test Student"

    When I exit Student View
    Then I am back in the teacher's view

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
