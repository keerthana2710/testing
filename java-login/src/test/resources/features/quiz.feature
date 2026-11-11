@quiz
Feature: Course quiz from creation to graded response
  In one of Keerthana's own courses, the teacher builds a quiz with one
  question of each type, publishes it to the course, the course's student
  takes and submits it, and the teacher reads the student's attempt in the
  quiz responses, with the MCQ and True/False answers already scored.

  {now} is the run's day and time (e.g. 041530), so every run makes and
  checks a quiz of its own. The quiz goes into Module 1 and is saved without
  syncing to Canvas. The student is the course's Student View (the app's own
  "CI Test Student"), so no second sign-in is needed.

  How each step works in the app (all handled in QuizPage):
  - Create: Module 1 "+" (Add an item) → Type Quiz → Title → Create Quiz →
    Questions tab → Add Question → type → fill its form → Update Question,
    once per row → Save Quiz → "Save and sync to Canvas?" is answered No.
    A new question is added at the TOP of the list, so the rows are added
    last to first and the quiz reads in table order.
  - Question forms: Multiple Choice gets the answer as option 1 (marked
    correct) and "None of the above" as option 2; True/False clicks the
    True or False button; Short Answer fills the sample answer; Fill in the
    Blank needs [blank] in the question and fills the correct answers; Essay
    has no answer key. A sticky Save Quiz bar covers the bottom of the
    editor, so form buttons are scrolled to the middle before clicking.
  - Publish: a new quiz is unpublished. Only the quiz's own row is used (a
    module row contains its items' buttons too). The module list titles it
    "M01 - <title>".
  - Student: Student View → Module 1 → the quiz → Take Quiz → Start Quiz →
    answer each question by its type label → Submit Quiz → Submit Quiz
    again in "Submit Quiz?". Success is the "Your score will be evaluated
    soon." toast or the quiz showing Submitted.
  - Teacher: Exit Student View → the quiz → Responses → the student's row
    (Submitted) → View. Response Details shows MCQ and True/False as
    "Auto-graded · Correct · 1 / 1 point", the Essay under "Manual Grading",
    and the response "Awaiting Grading". The window is closed afterwards so
    Sign out can reach the account menu.

  Each run leaves its published quiz and the CI Test Student's submission in
  the course: clean them up by hand.

  Scenario: Create a quiz with every question type, student submits it, teacher sees the scored attempt
    Given credentials are configured
    And I am signed in

    # Add MCQ, True/False, Essay, Short Answer and Fill in the Blank: quiz created
    When I open the course "Learn Python" with code "PY1027"
    And I create a quiz "Python Basics Quiz {now}" with the questions:
      | type              | question                                                    | answer |
      | Multiple Choice   | Which keyword defines a function in Python?                 | def    |
      | True/False        | Python lists are mutable.                                   | True   |
      | Essay             | Explain the difference between a list and a tuple.          |        |
      | Short Answer      | What does len("abc") return?                                | 3      |
      | Fill in the Blank | A loop that runs while a condition holds is a [blank] loop. | while  |
    Then the quiz "Python Basics Quiz {now}" is created with 5 questions

    # Assign it to the course: it appears in the student's view
    When I publish the quiz to the course
    And I switch to the course's Student View
    Then the quiz "Python Basics Quiz {now}" is listed for the student

    # Open the quiz, answer every question and submit: submission success message
    When I open the quiz "Python Basics Quiz {now}"
    And I answer the questions:
      | type              | answer                                                  |
      | Multiple Choice   | def                                                     |
      | True/False        | True                                                    |
      | Essay             | A list can be changed after it is made; a tuple cannot. |
      | Short Answer      | 3                                                       |
      | Fill in the Blank | while                                                   |
    And I submit the quiz
    Then the quiz submission success message is shown

    # Teacher opens the quiz responses: the student's attempt is there, auto-scored
    When I exit Student View
    And I open the responses of the quiz "Python Basics Quiz {now}"
    Then the student's submission is listed with every attempt
    And the Multiple Choice and True or False answers are scored automatically
    And the Essay answer is waiting for the teacher's grading

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
