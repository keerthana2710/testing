@signup
Feature: Forgot password and sign up from the welcome page
  From the public welcome page, open the login modal and request a password
  reset link, then come back to the welcome page and fill in the signup
  request form to the end and submit it with the signup user's email.

  Scenario: Request a password reset, then submit a signup request
    Given I am on the welcome page
    When I click Log in
    And I click Forgot password
    And I request a password reset for "keerthana@sentellent.com"
    Then the reset link is reported as sent

    Given I am on the welcome page
    When I click Sign up
    And I enter first name "Keerthana", last name "Test" and email "keerthanasamiraj10@gamil.com"
    And I choose "Both" for what brings me here
    And I enter the course I want to create "AI Automation with Java"
    And I submit the signup request
    Then the signup request is accepted
