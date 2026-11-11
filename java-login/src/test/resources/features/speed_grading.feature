@speedgrading
Feature: Speed grading - Fast Grade from the gradebook's assessment columns
  Signed in as keerthana@sentellent.com (LXP_EMAIL in .env), the owner of
  "Learn Python" (PY1027). The assessments are ones CI Test Student has
  already done: the assignment "Week 1 Lab 1: Personal Greeting Program",
  the quiz "Python Basics Quiz 040948" and the discussion "Week 1 Discussion
  1" (with the teacher's replies). One sign-in (one OTP) covers it all.

  How it works in the app (staging, Nov 2026; all in SpeedGradingPage):
  - Course → Grades → Grid. Hovering an assessment's column header shows a
    three-dots button ("Column options"); its menu has "Fast Grade" (the
    test cases' "fast grader"), which opens a drawer on the right with the
    student's work on the left and the grade panel on the right.
  - Top of the drawer: Settings (gear, top left), Previous student, the
    student picker (with a search and one row per student), Next student.
  - Grade panel: Status (None / Late / Missing / Excused): Late adds a
    "Days late" box, Excused hides the grade box and the grade reads "EX".
    "Grade out of N", Approve (records the grade in the LXP; Submit to
    Canvas is never clicked), and "Comment to student".
  - Comment Library (button above the comments): "Show suggestions when
    typing", "Show saved comments for" (All comments / This course / This
    assessment), saved comments, and "Add comment to library" + Add to Library.
  - Settings: "Sort student list", "Hide student names in FastGrade",
    "Default Discussion View Options" (Only this student's posts / Their
    posts, in the conversation around them), Save Options. These are the
    teacher's own settings, so the run puts them back afterwards.

  Grades are approved in the LXP only. The status checks (Late, Excused)
  are not approved: the status is set back to None before grading.

  Data: each run adds a comment to the teacher's comment library, changes
  CI Test Student's recorded grade for Week 1 Lab 1 by one point, and
  records a grade of 8 for Week 1 Discussion 1.

  Scenario: Fast Grade a quiz, an assignment and a discussion from the gradebook
    Given credentials are configured
    And I am signed in
    And I open the course "Learn Python" with code "PY1027"
    When I open the teacher's Grades of the course

    # ======== Assignment: the submitted text shows, a grade and a comment can be entered ========
    When I open Fast Grade from the "Week 1 Lab 1: Personal Greeting Program" column
    Then the Fast Grade page opens for that assessment
    When I show the student "CI Test Student"
    Then it shows the student's submitted assignment text
    And the teacher can enter a grade and a comment

    # Already graded: re-enter the grade and submit; the new grade is recorded and in the gradebook
    When I change the recorded grade and approve it
    Then the new grade is recorded
    And the gradebook shows that grade for "CI Test Student"

    # ======== Quiz: the student's responses show, a grade and a comment can be entered ========
    When I open Fast Grade from the "Python Basics Quiz 040948" column
    Then the Fast Grade page opens for that assessment
    When I show the student "CI Test Student"
    Then it shows the student's quiz responses
    And the teacher can enter a grade and a comment

    # ======== Discussion: the student's posts and the teacher's replies show ========
    When I open Fast Grade from the "Week 1 Discussion 1" column
    Then the Fast Grade page opens for that assessment
    When I show the student "CI Test Student"
    Then it shows the student's posts and the teacher's replies
    And the teacher can enter a grade and a comment

    # Student dropdown: picking another student shows that student's work
    When I pick another student from the student dropdown
    Then the student's name and work change

    # Next / Previous: the name and the work change with the student
    When I show the first student in the list
    And I click Next student
    Then the student's name and work change
    When I click Previous student
    Then the student's name and work change

    # Status: Late shows an editable Days late box; Excused shows EX in the grade
    When I show the student "CI Test Student"
    And I set the status to "Late"
    Then the Days late box shows and takes "2"
    When I set the status to "Excused"
    Then the grade shows "EX"
    When I set the status to "None"

    # Comment library: add a comment, it is saved
    When I add the comment "Speed grading library comment {now}: clear variable names and tested output." to the comment library
    Then the comment is saved in the library

    # Suggestions: with suggestions on, typing a comment offers the saved one
    When I turn on suggestions when typing
    Then typing the start of the comment suggests it from the library

    # Filters: the comment is listed for this assessment, this course and overall
    And the library lists the comment for "This assessment"
    And the library lists the comment for "This course"
    And the library lists the comment for "All comments"
    When I close the comment library

    # Settings: sort by first name, hide student names, only the student's own posts
    When I set Fast Grade to sort "by student name (first name)", hide student names and show "Only this student's posts"
    Then student names are hidden
    And only the student's own posts are shown

    # Put the settings back: names shown, posts in their conversation; the list is A to Z
    When I set Fast Grade back to sort "by student name (first name)", show student names and show "Their posts, in the conversation around them"
    Then the student list is in ascending order of first name
    When I show the student "CI Test Student"
    Then the student's posts are shown in the conversation around them

    # Enter a grade and submit: recorded, and shown in the gradebook
    When I enter the grade "8" and approve it
    Then the new grade is recorded
    And the gradebook shows that grade for "CI Test Student"

    # Filters on another assessment: the comment is not "This assessment"'s there, but is the course's
    When I open Fast Grade from the "Python Basics Quiz 040948" column
    And I show the student "CI Test Student"
    Then the library does not list the comment for "This assessment"
    And the library lists the comment for "This course"
    And the library lists the comment for "All comments"
    When I close the comment library
    And I close Fast Grade

    When I sign out
    Then I am signed out
