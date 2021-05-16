Feature: Alert Emails

  As an Organisation Administrator
  I want to be add other users to the alert email list
  So that alert emails also be send to others rather than just to the Organisation Administration when the Event Subscription changed

  Background:
    Given A Platform Stack Configuration
    And A Platform Organisation Configuration
    And logged into the Developer Portal as 'Organisation Admin' user
    And an alert tenant is setup for the Organisation
    # A Production tenant is required for an alert email to be sent.
    And create an application for alert emails
    And the application is subscribed to the 'bill-ready' webhook
    And create a new alert email address for the application

  Scenario:  Send alert emails when the Event Subscription changed for a Junifer Application
    Given an alert email application exist and a new alert email address was created against the application
    When update the 'bill-ready' webhook for an application
    Then check if the Organisation Admin received an email notifying that the Application configuration changed
    And check if the new alert email user received an email indicating that the Application configuration changed
    And update the Alert production tenant to be a test tenant

  Scenario:  Send an email when an event delivery was a failure
    Given an alert email application exist and a new alert email address was created against the application
    When the bill-ready event fails 7 times
    Then check if the Organisation Admin received an email notifying that the event delivery failed
    When the bill-ready event is successfully delivered
    And the AlertMonitor Lambda Function is invoked
    Then check if the Organisation Admin received an email notifying that the event delivery recovered
    And update the Alert production tenant to be a test tenant
