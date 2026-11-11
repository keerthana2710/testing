@roles
Feature: Each role gets only what it should: student, teacher and admin
  Each scenario signs in as one role, with the account from .env
  (LXP_STUDENT_*, LXP_TEACHER_*, LXP_ADMIN_*; the teacher is
  keerthana+t1@sentellent.com), and asks for that sign-in's
  own OTP. Run one role with -DTAGS=@student, @teacher or @admin.

  Every role first visits each page it can reach and checks it loads: My
  Tasks, Courses, Programs, Course Templates (teacher and admin only; the
  student's refusal is checked separately) and the profile, which must show
  that account's name, email and role. A page that does not load is
  recorded and the run carries on; the scenario fails at the end.

  What the app does for each role (all handled in RolesPage):
  - The role is the one the app stores for the session (localStorage
    "tenant": STUDENT, TEACHER or ADMIN).
  - Student: Courses has no Create Course; /templates sends them back to
    /home; a course they are not enrolled in shows "Course not found", so
    they can't reach its Grades or grade anything.
  - Teacher: Create Course opens the Create Course window; Course Templates
    offers Create Template from IMSCC; the first course they teach opens with
    Grades (the account needs at least one course). The course list holds only
    the courses they teach: no "All creators" filter, every listed course is
    "Taught by" them, and Keerthana's "Learn Python" shows "Course not found".
  - Admin: everything a teacher can do, plus every teacher's courses: the
    "All creators" filter, courses taught by several people, and a course
    taught by someone else (the first one listed) opened with its Grades.
    No other teacher's course is named here: they can be renamed or deleted.

  Nothing is created or changed: the Create Course window is opened and
  closed, and Grades is only viewed. The course addresses below are the
  staging courses' own; update them if those courses are replaced.

  @student
  Scenario: A student cannot create courses, use templates or grade
    Given the "student" account is configured
    And I am signed in as the "student"
    Then my role in the app is "STUDENT"

    # Every page loads: the header tabs and the profile
    When I open the "My Tasks" tab
    Then the "My Tasks" page is shown
    When I open the "Courses" tab
    Then the "Courses" page is shown
    When I open the "Programs" tab
    Then the "Programs" page is shown
    When I open my profile from the account menu
    Then the profile shows the signed-in user's name
    And the profile shows the "student" account's email
    And the profile shows the signed-in user's role

    # Creating courses and templates is closed to students
    When I open the course list
    Then I am not offered Create Course
    And I cannot open Course Templates

    # No grading: courses they are not in (and their Grades) are closed to students
    And the course "Artificial Intelligence with Python" with code "AI_PY101" is not in my course list
    And I cannot open the course "Artificial Intelligence with Python" at "/course/127c1257-700c-42bf-805b-d13b5ce768b1", so I cannot reach its Grades

    When I sign out
    Then I am signed out

  @teacher
  Scenario: A teacher can create courses and templates and grade their own course, but sees only their own courses
    Given the "teacher" account is configured
    And I am signed in as the "teacher"
    Then my role in the app is "TEACHER"

    # Every page loads: the header tabs, Course Templates and the profile
    When I open the "My Tasks" tab
    Then the "My Tasks" page is shown
    When I open the "Courses" tab
    Then the "Courses" page is shown
    When I open the "Programs" tab
    Then the "Programs" page is shown
    When I open the Templates page
    Then the "Templates" page is shown
    When I open my profile from the account menu
    Then the profile shows the signed-in user's name
    And the profile shows the "teacher" account's email
    And the profile shows the signed-in user's role

    # Create a course and a template: both are offered
    When I open the course list
    Then I am offered Create Course
    When I open the Create Course window
    Then the Create Course window offers "Create New", "Create from Existing", "Import IMSCC File" and "Create from Template"
    When I close the Create Course window
    Then I can open Course Templates and create a template from IMSCC

    # Grading in a course they teach
    When I open the first course I teach
    Then I can open the course's Grades

    # Only their own courses: other teachers' courses stay hidden
    And the course list has no "All creators" filter
    And every course in my list is one I teach
    And I cannot open the course "Learn Python" at "/course/9a8c67e8-9e79-4cf3-8b3b-3b37a48cd830", so I cannot reach its Grades

    When I sign out
    Then I am signed out

  @admin
  Scenario: An admin can do everything a teacher can and also sees every teacher's courses
    Given the "admin" account is configured
    And I am signed in as the "admin"
    Then my role in the app is "ADMIN"

    # Every page loads: the header tabs, Course Templates and the profile
    When I open the "My Tasks" tab
    Then the "My Tasks" page is shown
    When I open the "Courses" tab
    Then the "Courses" page is shown
    When I open the "Programs" tab
    Then the "Programs" page is shown
    When I open the Templates page
    Then the "Templates" page is shown
    When I open my profile from the account menu
    Then the profile shows the signed-in user's name
    And the profile shows the "admin" account's email
    And the profile shows the signed-in user's role

    # Create a course and a template: both are offered
    When I open the course list
    Then I am offered Create Course
    When I open the Create Course window
    Then the Create Course window offers "Create New", "Create from Existing", "Import IMSCC File" and "Create from Template"
    When I close the Create Course window
    Then I can open Course Templates and create a template from IMSCC

    # Grading in their own course
    When I open the course "Learn Python" with code "PY1027"
    Then I can open the course's Grades

    # Extra admin access: every teacher's courses
    And the course list has the "All creators" filter
    And the course list shows courses taught by other people
    And the course list shows a course taught by someone else
    When I open that course
    Then I can open the course's Grades

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
