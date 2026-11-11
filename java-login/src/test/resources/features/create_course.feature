@createcourse
Feature: Create a course
  A teacher creates a new course from the courses page, fills in Course Setup,
  clicks Generate, waits for the AI course plan until Approve appears (this
  takes several minutes) and approves it. A course code must be unique within
  the semester, so the run's day and time are added to it (CppandA10-031543). Once done it logs out and closes the
  browser.

  Scenario: Create a course, fill its details, generate and approve the plan
    Given credentials are configured
    And I am signed in

    # Create the course
    When I click Create Course
    And I enter the course name "basic of html and javascript" and code "html123"
    And I click Create
    Then the new course is created

    # Fill in its details
    When I open the new course
    # Course Setup needs the dates, the weeks & modules and an outline before it can generate
    And I fill in the course details:
      | field                  | value                |
      | Number of modules      | 4                    |
      | Lecture hours per week | 3                    |
      | First day              | today                |
      | Last day               | 2 months from today  |
      | Course outline         | Basics of C and C++. Outcomes: write, compile and debug C and C++ programs. Topics in order: 1. C syntax, variables, data types and operators. 2. Control flow, functions and arrays. 3. Pointers, memory and structs. 4. C++ classes, objects and the standard library. Assessments: weekly programming exercises and a final project building a small console application. |

    # Generate the plan with AI, wait for it, and approve it
    When I click Generate
    And I wait until Approve is shown
    And I click Approve
    Then the course plan is approved

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
