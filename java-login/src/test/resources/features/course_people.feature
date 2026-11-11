@coursesmoke
Feature: Manage the people on a course
  From a course's People tab, a teacher adds existing users with a role,
  removes users again, and invites new users, on a published and an
  unpublished course, all in one signed-in session. The app's roles are
  Owner, Course designer and Student.

  The free plan allows one student across all of the account's courses, so
  only one Student exists at a time: everyone else gets another role, and the
  previous student is removed before the next one is added, even when the next
  one goes into another course. The scenario first clears its users and all
  students off both courses, so it can be re-run.

  Scenario: Add users with roles, swap the student, remove a teacher, add people to an unpublished course
    Given credentials are configured
    And I am signed in

    And I open the People tab of the "AI Automation with Java" course
    And the course has no students
    And I open the People tab of the "Artificial Intelligence with Python" course
    And "keerthana+t1@sentellent.com" is not on the course
    And "antony+teach@sentellent.com" is not on the course
    And "aathithyan+s1@sentellent.com" is not on the course
    And "keerthana+s2@sentellent.com" is not on the course
    And the course has no students

    # 1. Add Users -> search users -> pick one -> assign the Course designer role
    #    (keerthana+t1 had no account, so the first run invites her; after that the search finds her)
    When I invite a new user "keerthana+t1@sentellent.com" named "Keerthana" "Teacher" as "course designer"
    Then "keerthana+t1@sentellent.com" is listed on the course as "course designer"

    # 2. Add Users -> search users -> pick one -> assign the Student role (the one student)
    When I add the user found by searching "antony" with email "antony+teach@sentellent.com" as "student"
    Then "antony+teach@sentellent.com" is listed on the course as "student"

    # 3. Add Users -> search by email -> pick the one -> assign the Course designer role
    When I add the user found by searching their email "aathithyan+s1@sentellent.com" as "Course designer"
    Then "aathithyan+s1@sentellent.com" is listed on the course as "Course designer"

    # 4. One student at a time: remove the previous student before adding the next
    When I remove "antony+teach@sentellent.com" from the course with the Actions menu
    Then "antony+teach@sentellent.com" is no longer listed on the course

    # 5. Add Users -> search the email -> Invite -> enter details -> submit
    #    (once her account exists, the search finds her and she is picked instead)
    When I invite a new user "keerthana+s2@sentellent.com" named "Keerthana" "Student" as "Student"
    Then "keerthana+s2@sentellent.com" is listed on the course as "Student"

    # the checks above can read the app's local copy; after a reload they read what was saved
    When I reload the People tab of the "Artificial Intelligence with Python" course
    Then "keerthana+s2@sentellent.com" is listed on the course as "Student"
    And "keerthana+t1@sentellent.com" is listed on the course as "Course designer"
    And "aathithyan+s1@sentellent.com" is listed on the course as "Course designer"
    And "antony+teach@sentellent.com" is no longer listed on the course

    # 6. User list -> Actions -> remove one teacher
    When I remove "keerthana+t1@sentellent.com" from the course with the Actions menu
    Then "keerthana+t1@sentellent.com" is no longer listed on the course
    When I reload the People tab of the "Artificial Intelligence with Python" course
    Then "keerthana+t1@sentellent.com" is no longer listed on the course

    # 7. The one student seat moves to the next course: remove the previous student first
    When I remove "keerthana+s2@sentellent.com" from the course with the Actions menu
    Then "keerthana+s2@sentellent.com" is no longer listed on the course

    # 8. Unpublished course: Add Users -> search the email -> Invite -> enter details -> add with a role
    When I open the People tab of the "AI Automation with Java" course
    And "aathithyan+teacher2@sentellent.com" is not on the course
    And I invite a new user "aathithyan+teacher2@sentellent.com" named "Aathithyan" "Teacher" as "Course designer"
    Then "aathithyan+teacher2@sentellent.com" is listed on the course as "Course designer"
    When I invite a new user "keerthana+st1@sentellent.com" named "Keerthana" "Student" as "Student"
    Then "keerthana+st1@sentellent.com" is listed on the course as "Student"
    When I reload the People tab of the "AI Automation with Java" course
    Then "aathithyan+teacher2@sentellent.com" is listed on the course as "Course designer"
    And "keerthana+st1@sentellent.com" is listed on the course as "Student"
