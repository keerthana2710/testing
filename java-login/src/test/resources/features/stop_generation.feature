@stopgeneration
Feature: Stop a course generation
  A teacher creates a new course, fills in Course Setup, clicks Generate,
  approves the AI plan once it is ready (planning takes up to 7 minutes) and then stops the
  generation of the course content. A course code must be unique within the
  semester, so the run's day and time are added to it (pyai1012-031543).
  Once done it logs out and closes the browser.

  Scenario: Create a course, generate it and stop the generation
    Given credentials are configured
    And I am signed in

    # Create a new course
    When I click Create Course
    And I enter the course name "basic of python with Ai" and code "pyai1012"
    And I click Create
    Then the new course is created

    # Course Setup: add the details it needs before it can generate
    When I open the new course
    And I fill in the course details:
      | field                  | value                |
      | Number of modules      | 4                    |
      | Lecture hours per week | 3                    |
      | First day              | today                |
      | Last day               | 2 months from today  |
      | Course outline         | Basics of Python with AI. Outcomes: write and debug Python programs and use them to build simple AI applications. Topics in order: 1. Python syntax, variables, data types and operators. 2. Control flow, functions and modules. 3. Lists, dictionaries, files and libraries such as NumPy and pandas. 4. Introduction to AI and machine learning with scikit-learn. Assessments: weekly programming exercises and a final project building a small AI application in Python. |

    # Save & Generate. Stop only shows once the AI plan is approved and the
    # course content starts generating, so approve the plan first.
    When I click Generate
    And I wait up to 7 minutes until Approve is shown
    And I click Approve
    Then the course plan is approved

    # Stop generation: Stop, then confirm with Stop Generation
    When I click Stop generation
    Then the generation is stopped

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
