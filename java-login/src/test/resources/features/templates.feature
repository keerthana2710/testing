@templates
Feature: Course templates
  On the Course Templates page a teacher browses the paged list of
  templates, creates a template from an IMSCC file, creates a course from it,
  edits the template's name, author and Canvas link, and searches and sorts
  the list.

  The IMSCC comes from downloading one of Keerthana's courses first. {now} is
  the run's day and time (e.g. 032205), so every run makes a template and a
  course of its own. A template takes about 10-12 minutes to generate on
  staging, so the whole scenario takes about 15-20 minutes.

  Scenario: Create a template from IMSCC, create a course from it, edit it, search and sort
    Given credentials are configured
    And I am signed in

    # An IMSCC to upload: download one of Keerthana's courses
    When I open the course "Learn Python" with code "PY1027"
    And I download the course as IMSCC

    # Click Templates: the Templates page loads
    When I open the Templates page
    Then the "Templates" page is shown

    # Scroll the templates list: templates visible (12 to a page, then page 2)
    When I scroll through the templates list
    Then templates are visible

    # Upload the IMSCC and enter the details: template created
    When I create a template from the downloaded .imscc named "Learn Python Template {now}" by "Keerthana" with Canvas link "https://canvas.sentellent.com/courses/688" in "Replicate" mode

    # Wait for generation (10-12 min): up to 30 minutes, checking about once a minute
    And I wait for the template to finish generating

    # Return to templates: the generated template is visible
    When I return to the templates list
    Then the new template is visible

    # Open the template and click Create Course: new course created
    When I open the new template
    And I click Create Course from the template with title "Python from Template {now}" and code "PYTPL"

    # Edit name, author and link: changes saved
    When I edit the new template's name to "Learn Python Template {now} edited", author to "Keerthana S" and Canvas link to "https://canvas.sentellent.com/courses/689"
    Then the template shows name "Learn Python Template {now} edited", author "Keerthana S" and Canvas link "https://canvas.sentellent.com/courses/689"

    # Search and sort: the list updates correctly
    When I search the templates for "Learn Python Template {now}"
    Then only templates matching "Learn Python Template {now}" are listed
    When I search the templates for ""
    And I sort the templates by "Name (Z-A)"
    Then the templates are sorted by "Name (Z-A)"
    When I sort the templates by "Name (A-Z)"
    Then the templates are sorted by "Name (A-Z)"
    When I sort the templates by "Newest First"
    Then the templates are sorted by "Newest First"

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
