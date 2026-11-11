Feature: Login to the LXP portal
  The portal requires a six-digit verification code after a successful password
  check. The test asks for the emailed OTP in the terminal, enters it, and
  confirms the home page opens. It then visits every tab and page (My Tasks,
  Courses, Programs, Templates and each way to create a course), edits the "basic of java" course's settings
  (name, code, description) and checks them after a refresh, then checks
  the profile page, updates the time zone, checks the appearance
  settings, and changes the password. Once everything is done it logs out and
  closes the browser.

  @smoke
  Scenario: Sign in, check the profile, time zone, appearance, and change the password
    Given credentials are configured
    When I sign in with my email and password
    Then no login error is shown
    And the 6-digit MFA prompt is displayed
    When I enter the verification code
    And I click Verify Code
    Then I am redirected to the home page

    # Navigation: every tab and page a signed-in user should see. Templates has
    # no header tab, so it is opened by its address (/templates).
    When I open the "Courses" tab
    Then the "Courses" page is shown
    When I open the "Programs" tab
    Then the "Programs" page is shown
    When I open the "My Tasks" tab
    Then the "My Tasks" page is shown
    When I open the Templates page
    Then the "Templates" page is shown
    # Create Course: Create New, Existing Course, Import and Templates
    When I open the Create Course window
    Then the Create Course window offers "Create New", "Create from Existing", "Import IMSCC File" and "Create from Template"
    And each way to create a course opens its panel
    When I close the Create Course window

    # Course settings: open "basic of java", edit its name, code and description
    # in Settings, save, and check all three after a refresh of the course page.
    # After the first run the course is called "basic of java with edit"; it is
    # found by the start of its name, and {now} (the run's date and time) keeps
    # the description different on every run so there is always a change to save.
    When I open the course "basic of java"
    And I click Settings in the course
    And I update the course name to "basic of java with edit", code to "java1210" and description to "Edited by the automated login test on {now}"
    And I save the course settings
    Then the course settings are saved
    When I refresh the course page
    Then the course settings show name "basic of java with edit", code "java1210" and description "Edited by the automated login test on {now}"
    And I go back to the home page

    # Profile: the same user that signed in
    When I open my profile from the account menu
    Then the profile shows the signed-in user's name
    And the profile shows my email address
    And the profile shows the signed-in user's role

    # Time zone: change it, confirm it was saved, then put the original back
    # (courses take their time zone from the owner's profile)
    When I change the time zone to "UTC"
    And I reload the profile page
    Then the time zone is "UTC"
    When I change the time zone to "Asia/Calcutta"
    And I reload the profile page
    Then the time zone is "Asia/Calcutta"

    # Appearance: the choice is highlighted and changes the page, and survives a reload
    When I set the color mode to "Light"
    Then the color mode "Light" is selected and the page is light
    When I set the color mode to "Dark"
    Then the color mode "Dark" is selected and the page is dark
    When I set the display size to "Small"
    Then the display size "Small" is selected and the base text size is "14px"
    When I set the display size to "Large"
    Then the display size "Large" is selected and the base text size is "18px"
    When I reload the profile page
    Then the color mode "Dark" is selected and the page is dark
    And the display size "Large" is selected and the base text size is "18px"
    # put the original appearance back
    When I set the color mode to "System"
    And I set the display size to "Medium"
    Then the color mode "System" is selected
    And the display size "Medium" is selected and the base text size is "16px"

    # Password: change it, save it to .env, and prove it by signing in with it
    When I change my password to "Pass!123"
    And I sign out
    And I sign in with my email and password
    Then no login error is shown
    And the 6-digit MFA prompt is displayed
    When I enter the verification code
    And I click Verify Code
    Then I am redirected to the home page

    # Done: log out and close the browser
    When I sign out
    Then I am signed out
    When I close the browser
