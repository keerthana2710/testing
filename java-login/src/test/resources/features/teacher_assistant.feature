@assistant
Feature: AI Assistant for a teacher: blank courses and programs
  Signed in as the teacher (LXP_TEACHER_* in .env, keerthana+t1@sentellent.com,
  whose program is "learn html"), the teacher opens the AI
  Assistant with its icon, asks about blank courses and programs, checks
  the New Program button its program steps point to, and creates a blank
  course from the assistant.

  The answers are worded differently every time, so each expected point is
  a list of words ("any of"); one of them in the answer shows the point is
  made. Sometimes the assistant opens its on-screen walkthrough and replies
  only that it did ("I've opened a walkthrough that will guide you..."):
  the steps are then not in the answer, so the test asks "Please write those
  steps here in the chat as a numbered list." and checks that reply. A missing point is recorded and the run carries on; the scenario
  fails at the end. The app's own words are used: the assistant says
  "Create New → Start from scratch" for a blank course and "Add Courses"
  for a program.

  How the app behaves (all handled in AssistantPanel and BlankCoursePage):
  - The chat is open after sign-in; "Close chat" hides it and the "Open AI
    Assistant" icon brings it back.
  - "Create blank course" answers with a "Blank Course" button. It opens a
    "Create Blank Course" window with one field, "What is your course
    about? *": the course title and description go in there, and the AI
    writes the course's name and description from it, so the new course is
    found by its address, not its title. With the field empty, Create
    Course stays disabled (no "required" message is shown). Once created,
    the window closes and the assistant replies "Created an empty course:
    <name>" with an Open Course button, which opens the course.
  - The teacher's Free plan allows 1 active course. At that limit the
    assistant explains the limit instead of showing the button, and the
    blank course scenario fails at "I click the button in the assistant's
    answer". Delete or archive the course an earlier run made to free the slot.

  Each passing run of the second scenario leaves a new course in the
  teacher's account, which uses the plan's one course slot: delete it before
  the next run.

  Scenario: The assistant explains blank courses and programs
    Given the "teacher" account is configured
    And I am signed in as the "teacher"
    When I open the AI Assistant with its icon
    Then the AI Assistant is open

    # What is Blank Course? A short explanation of its purpose and when to use it
    When I ask the AI Assistant "What is Blank Course?"
    Then the answer covers:
      | meaning                                  | any of                                   |
      | it is an empty course                    | empty, blank, shell, scratch             |
      | you build it yourself (when to use it)   | build, yourself, by hand, manual, scratch |
    And the answer is at most 120 words

    # How to create a blank course? Step-by-step, saying where to click.
    When I ask the AI Assistant "How to create a blank course?"
    Then the answer covers:
      | meaning                          | any of                                   |
      | start with Create                | Create                                   |
      | choose the blank way             | Create New, New Course, Start from scratch, Blank Course |
      | enter the course title           | Course Title, title, course details      |
      | finish with Create Course        | Create Course, create the course, create it, create your blank course |

    # How to create a program? Step-by-step, pointing to New Program
    When I ask the AI Assistant "How to create a program?"
    Then the answer covers:
      | meaning                          | any of                     |
      | go to Programs                   | Programs                   |
      | click New Program                | New Program                |
      | give it a name                   | Program Name, name         |
      | finish by creating it            | Create Program, create it  |
    When I open the "Programs" tab
    Then New Program is offered

    # Show my programs: the teacher's programs with their course counts
    When I ask the AI Assistant "Show my programs"
    Then the answer lists the program "learn html" with its course count

    # Adding courses to a program: open the program → Add Courses → choose the courses
    When I ask the AI Assistant "How to add courses to my program 'full stack testing'?"
    Then the answer covers:
      | meaning                          | any of                              |
      | open that program                | full stack testing, Drafts          |
      | click Add Courses                | Add Courses, Add New Courses        |
      | choose the courses               | Course Code, select courses, choose courses |

    # Course vs Program: the difference, and that a program contains courses
    When I ask the AI Assistant "Course vs Program"
    Then the answer covers:
      | meaning                          | any of                                       |
      | a course is where you teach      | modules, materials, lessons, assignments, subject, single |
      | a program contains courses       | groups, contains, multiple courses, collection |

    # What does Regenerate Outline do? Rebuilds the program outline, and warns it overwrites edits
    When I ask the AI Assistant "What does Regenerate Outline do?"
    Then the answer covers:
      | meaning                          | any of                                   |
      | it rebuilds the program outline  | rebuild, regenerat, update, outline       |
      | with AI from the courses         | AI, courses, curriculum                  |
      | warns it overwrites your edits   | replac, overwrit, rewrit, lose, manual edits |

    When I sign out
    Then I am signed out

  Scenario: Create a blank course from the assistant
    Given the "teacher" account is configured
    And I am signed in as the "teacher"
    When I open the AI Assistant with its icon

    # Create blank course: the assistant's Blank Course button opens the blank course window
    And I ask the AI Assistant "Create blank course"
    And I click the button in the assistant's answer
    Then the Create Blank Course window is open

    # Nothing entered: the course details are required, nothing is created
    When I leave the course description blank and click Create Course
    Then the course description is required and no course is created

    # Title and description entered: the course is created, its dashboard opens, its card is in My Courses
    When I describe the course as "FSQA as a Blank Course - Test blank flow"
    And I create the blank course
    And I click Open Course in the assistant
    Then the new course's dashboard opens
    And the new course's card is in My Courses

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
