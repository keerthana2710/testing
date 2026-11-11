@imscc
Feature: Download a course as IMSCC and import it into an existing course
  Downloads one of Keerthana's courses as an IMS Common Cartridge (.imscc),
  checks the file is a valid cartridge, then imports it into another of
  Keerthana's existing courses (Course Tools → Import → Import from Canvas)
  and checks every module, page, quiz, assignment and discussion arrived.

  The import adds new modules and items and updates ones already there, so
  the scenario can be run again on the same target course. The export took
  about 35 seconds and the import about 2 minutes for a 4-module course.

  Scenario: Download a course as IMSCC and import it into an existing course
    Given credentials are configured
    And I am signed in

    # Download Course: valid .imscc file downloaded
    When I open the course "Learn Python" with code "PY1027"
    And I note its modules, quizzes, assignments and discussions
    And I download the course as IMSCC
    Then a valid .imscc file is downloaded

    # Existing course: the import page loads
    When I open the course "basic of c and cpp" with code "CPPANDA10-031828"
    And I click Import in the course
    Then the import page loads

    # Upload the file and submit: course imported
    When I upload the downloaded .imscc file
    And I submit the import
    Then the course is imported

    # Open the course and check its modules, quizzes and assignments
    And all modules, quizzes, assignments and discussions are present

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
