@importsubmissions
Feature: Import Submissions from a Canvas submissions ZIP
  Signed in to the LXP as keerthana@sentellent.com (LXP_EMAIL), on
  "Unix/linux mirror course 2026" (UL-001, its students come from Canvas),
  the teacher imports a real Canvas submissions ZIP into "Lab: Script:
  Automate a File Processing Task" from the course's Grades page: the
  assignment in Assessments → ⋮ (More actions) → Import Submissions →
  "Choose assignment…" → the assignment → Continue. The results are checked
  on the assignment's Responses tab and in Quick Review (its View).
  No Canvas login is needed.

  First, the match check alone, with nothing imported: on "Testing staging
  course" (ST-001), the first assignment in Grades ("Configure and
  Smoke-Test a Local Staging Example") previews the Canvas ZIP downloaded
  for it (src/test/resources/files/staging_submissions.zip, 1 file from
  "1student"); every student in it must be in the green Matched list, and
  the window is closed without importing.

  Watch mode: with WATCH_MS in .env (default 1500 ms; 0 turns it off) the
  run outlines in red what it clicks or checks, says it in a yellow banner
  at the top of the page, and pauses, so the run can be followed.

  The other ZIP is Canvas → "Lab: Set Permissions for a Simulated Company Tree" →
  Download Submissions (22 files from 21 students), saved as
  src/test/resources/files/canvas_submissions.zip. It holds student work,
  so it is kept out of git (.gitignore): on another machine, put it there,
  or the scenario is skipped.

  How the app behaves (staging, Nov 2026; all in ImportSubmissionsPage):
  - Canvas names each file <login>_[LATE_]<Canvas user id>_<submission
    id>_<file>; the app matches the student by the Canvas user id.
  - The preview says "N of M submissions will be imported" and lists
    Matched (green), Already Submitted (orange, each with an "Overwrite
    submission for <name>" box) and "Unmatched (N) — will be skipped"
    (amber). "AI Evaluation" ("Auto-grade submissions with AI after
    import") is on by default. Nothing is saved until "Import N Submissions".
  - The overwrite boxes start CHECKED ("Select all"). The test case only
    says what happens when the box is left unchecked or checked, so the
    starting state is recorded for review, and the test unchecks the box
    itself for case A.

  Built to run every day: each run imports 2 more students (3 with case
  A), so the ZIP's 21 students run out after about 7 runs; the run then
  reports "nothing new to import" for review. Switch to another assignment
  with no submissions yet (Grades shows 0/29: "Process Analysis Report",
  "Lab: Capstone: Automate a Multi-step Task").

  Scenario: Preview, import with AI evaluation, then skip and overwrite an already-submitted student
    Given credentials are configured
    And the Canvas submissions ZIP "canvas_submissions.zip" is in the test files
    And I am signed in

    # TC1 on "Testing staging course": students whose login IDs match enrolled students
    # appear in the Matched (green) list (preview only, nothing is imported)
    When I open the Grades of the course "Testing staging course"
    And I open Import Submissions for the first assignment
    And I upload the Canvas ZIP "staging_submissions.zip" to preview it
    Then every student in the ZIP is in the green Matched list
    And I close the import window without importing

    When I open the Responses of the assignment "Lab: Script: Automate a File Processing Task" in the course "Unix/linux mirror course 2026"

    # TC1: Preview correctly matches students before importing
    And I open Import Submissions
    And I upload the Canvas ZIP with one file added for a student who is not on the course
    Then the preview puts the students in the Matched, Already Submitted and Unmatched lists
    And students without a response are Matched and students with one are Already Submitted
    And nothing is imported yet
    And how the already-submitted students' overwrite boxes start is recorded

    # TC2: Submissions are imported and AI evaluation is triggered
    When I import 2 of the Matched students with AI Evaluation on
    Then the result shows how many submissions were imported
    When I close the import window
    Then each imported student's submission is in the responses
    And an AI score appears for an imported submission within 5 minutes

    # TC3: Already-submitted student is skipped unless the teacher checks the overwrite box
    # Case A: overwrite box unchecked → skipped, the original submission is unchanged
    When I import an already-submitted student's file again with the overwrite box unchecked
    Then that student is counted as skipped and their submission is unchanged
    # Case B: overwrite box checked → the old submission is replaced with the new file
    When I import that file again with the overwrite box checked
    Then that student's submission is replaced with the new file

    When I sign out
    Then I am signed out
