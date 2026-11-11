@aigrading
Feature: AI grading - suggestions on submitted work, strictness, approval and what the student sees
  Signed in as keerthana@sentellent.com (LXP_EMAIL in .env), the owner of
  "Learn Python" (PY1027). The student is the course's test student,
  "CI Test Student", reached through Student View, so one sign-in (one OTP)
  covers both sides: the student submits, the teacher grades.

  How AI grading works in the app (staging, Nov 2026; all in AiGradingPage):
  - The switch is course side panel -> Assessments Setup -> "AI
    Auto-Evaluation" (the test cases call it "course settings"). Changes save
    at once; Save closes the window. Two other settings matter here: the AI
    needs a rubric (Week 1 Lab 1 and Week 1 Discussion 1 have one), and
    "Grade after the due date" holds submissions until the due date passes
    (both items are past due, so nothing is held).
  - The teacher grades in Quick Review, /course/{id}/quickreview/{type}/{item}:
    an AI SUGGESTED card ("7 / 10", "Waiting on you"), a PENDING GRADE card
    ("Not recorded yet - approve to record it") that becomes RECORDED GRADE on
    Approve, a Rubric breakdown (SUGGESTED score per criterion), FEEDBACK, and
    a Strictness dropdown (Liberal / Moderate / Strict) with Re-run: the test
    cases' "Re-evaluate". Submit to Canvas is never clicked: Approve records
    the grade in the LXP only.
  - A discussion is evaluated per student from Quick Review (Run AI
    Evaluation, then "Discussion Still Active" -> Evaluate Now). The course
    has one student, so "each student gets a separate score" is checked for
    CI Test Student only.
  - Each AI suggestion is given up to 3 minutes (staging can queue them).

  Order: the "AI turned off" case runs first, so the student's next
  assignment attempt (with AI on) is the one the AI grades; the strictness
  re-run, approval and the student's view then use that same attempt.
  If the run stops while AI Auto-Evaluation is off, AiGradingSteps turns it
  back on after the scenario.

  Once the student has a submission, the assignment page opens the
  submission page, and its "Start New Attempt" opens a new response form;
  a new attempt can be submitted while the last one is still Awaiting Review.

  Data: each run adds two attempts at "Week 1 Lab 1", approved LXP grades
  for them, a quiz "M01 - AI Grading Quiz <ddHHmm>" with one attempt, a post
  in "Week 1 Discussion 1" and an approved grade for the discussion. Delete
  the quiz and the post by hand.

  Scenario: AI grading on an assignment, a quiz and a discussion, from suggestion to the student's grade
    Given credentials are configured
    And I am signed in
    And I open the course "Learn Python" with code "PY1027"

    # ======== TC2: AI grading turned off - no suggestion, a blank grading form ========
    When I turn AI Auto-Evaluation off in Assessments Setup
    Then AI Auto-Evaluation is off
    When I open the assignment "Week 1 Lab 1: Personal Greeting Program" in module 1
    And I click Student View
    And I submit the assignment with the response "AI off check {now}: name = input('What is your name? '); print('Hello, ' + name + '!'). I ran it with Ana and it printed Hello, Ana!"
    Then the assignment is submitted
    When I exit Student View
    Then I am back in the teacher's view
    When I open Quick Review of the assignment "Week 1 Lab 1: Personal Greeting Program" in module 1
    Then Quick Review shows the work "AI off check {now}"
    And no AI suggestion appears within 1 minute
    And the grading form is blank

    # ======== TC1: AI grading turned on - a suggested score and feedback appear ========
    When I turn AI Auto-Evaluation on in Assessments Setup
    Then AI Auto-Evaluation is on
    When I open the assignment "Week 1 Lab 1: Personal Greeting Program" in module 1
    And I click Student View
    And I submit the assignment with the response "AI on check {now}: my program stores the name in a variable with name = input('What is your name? ') and greets the user with print('Hello, ' + name + '! Welcome to Python.'). I ran it twice: with Ana it printed Hello, Ana! Welcome to Python. and with Ravi it printed Hello, Ravi! Welcome to Python. so different input gives a different greeting."
    Then the assignment is submitted
    When I exit Student View
    Then I am back in the teacher's view
    When I open Quick Review of the assignment "Week 1 Lab 1: Personal Greeting Program" in module 1
    Then Quick Review shows the work "AI on check {now}"
    When I wait up to 3 minutes for the AI suggestion
    Then an AI suggested score and feedback are shown
    And the rubric breakdown has a suggested score for every criterion

    # ======== TC3: Re-evaluate with a different strictness ========
    When I re-run the AI evaluation as "Strict"
    And I wait up to 3 minutes for the AI suggestion
    Then a new AI suggestion is shown at "Strict" strictness
    And the "Strict" score is not higher than the earlier one
    And the rubric breakdown has a suggested score for every criterion

    # ======== TC6 (part 1): the teacher approves the AI-suggested grade ========
    When I approve the grade
    Then the approved grade is recorded

    # ======== TC6 (part 2): the student sees the approved grade and feedback ========
    When I open the assignment "Week 1 Lab 1: Personal Greeting Program" in module 1
    And I click Student View
    Then the student sees the approved score, percentage, feedback and criteria
    When I exit Student View
    Then I am back in the teacher's view

    # ======== TC4: a quiz - MCQ and True/False auto-scored, the essay gets an AI suggestion ========
    When I go back to the course page
    And I create a quiz "AI Grading Quiz {now}" with the questions:
      | type            | question                                           | answer |
      | Multiple Choice | Which keyword defines a function in Python?        | def    |
      | True/False      | Python lists are mutable.                          | True   |
      | Essay           | Explain the difference between a list and a tuple. |        |
    Then the quiz "AI Grading Quiz {now}" is created with 3 questions
    When I publish the quiz to the course
    And I switch to the course's Student View
    And I open the quiz "AI Grading Quiz {now}"
    And I answer the questions:
      | type            | answer                                                                                                      |
      | Multiple Choice | def                                                                                                         |
      | True/False      | True                                                                                                        |
      | Essay           | A list is mutable: items can be added, removed or changed after it is made. A tuple is immutable: once made it cannot change, so it suits fixed data and can be a dictionary key. |
    And I submit the quiz
    Then the quiz submission success message is shown
    When I exit Student View
    And I open Quick Review of the quiz "M01 - AI Grading Quiz {now}" in module 1
    Then the "Multiple Choice" answer "Which keyword defines a function in Python?" is scored automatically
    And the "True/False" answer "Python lists are mutable." is scored automatically
    When I wait up to 3 minutes for the AI suggestion
    Then the essay "Explain the difference between a list and a tuple." has an AI suggested score waiting for the teacher's approval

    # ======== TC5: a discussion - run AI evaluation, review the criteria, approve one student ========
    When I go back to the course page
    And I open the discussion "Week 1 Discussion 1: Share a Task You Would Automate and Respond to a Classmate" in module 1
    And I click Student View
    And I post "AI grading check {now}: I would automate renaming my monthly report files. A Python script with a for loop over os.listdir() and an if check on the file extension could rename them by date. Replying to a classmate: your idea of sorting downloads could use the same loop." in the discussion
    Then the post is shown as from "CI Test Student"
    When I exit Student View
    Then I am back in the teacher's view
    When I open Quick Review of the discussion "Week 1 Discussion 1: Share a Task You Would Automate and Respond to a Classmate" in module 1
    Then Quick Review shows the work "AI grading check {now}"
    When I run the AI evaluation
    And I wait up to 3 minutes for the AI suggestion
    Then an AI suggested score and feedback are shown
    And the rubric breakdown has a suggested score for every criterion
    When I approve the grade
    Then the approved grade is recorded
    When I open the discussion "Week 1 Discussion 1: Share a Task You Would Automate and Respond to a Classmate" in module 1
    Then the discussion's Scores list the approved grade for "CI Test Student"

    When I sign out
    Then I am signed out
