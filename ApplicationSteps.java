package io.gentrack.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java8.En;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import platform.PlatformApplication;
import platform.PlatformMember;
import platform.enums.Product;
import platform.enums.User;
import utilities.log.CustomLoggerFactory;
import utilities.log.ILogger;
import utilities.retry.RetryException;
import variables.Platform;
import variables.ScenarioVariables;
import web.services.Email;
import web.services.Webhook;
import web.services.portal.Authentication;
import web.services.portal.DeveloperPortal;
import web.services.request.WebException;
import web.services.request.WebResult;
import web.services.request.WebStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"PMD.NcssCount", "PMD.CyclomaticComplexity"}) //Cucumber Steps are all lambdas within the constructor
public class ApplicationSteps implements En {
    private static final ILogger log = CustomLoggerFactory.getLogger(ApplicationSteps.class);
    private ScenarioVariables scenarioVariables;
    private Platform platform;

    public ApplicationSteps(ScenarioVariables scenarioVariables, Platform platform) {
        this.scenarioVariables = scenarioVariables;
        this.platform = platform;

        Given("^create an application for a (.*) '(.*)' event$", (Product product, String eventType) -> {
            DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
            String tenantId = platform.tenants.get(product).id;

            PlatformApplication createApp = portal.applications.createApplicationWithRetry(tenantId, product.toLowerCaseString(), "test", null);

            platform.application = createApp;
            platform.application.setPublicKey(
                    portal.applications.formatPublicKey(createApp.getPublicKey()));

            log.info("Application Created", platform.application,
                    "Product", product,
                    "EventType", eventType);
        });

        Given("^an application that will fail to receive a webhook$", () -> {
            DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
            String tenantId = platform.tenants.get(Product.Velocity).id;

            PlatformApplication createApp = portal.applications.createApplicationWithRetry(tenantId, Product.Velocity.toLowerCaseString(), "fail", null);
            platform.application = createApp;

            platform.application.setPublicKey(
                    portal.applications.formatPublicKey(createApp.getPublicKey()));

            String eventType = "bill-ready";
            String inboxName = "fail/" + platform.organisation.name + "/" + eventType;
            scenarioVariables.event.setWebhook(
                    subscribeApplicationWebhook(inboxName, eventType)
            );
        });

        Given("^the application is subscribed to the '(.*)' webhook$", (String eventType) -> {
            String inboxName = "gcis/" + platform.organisation.name + "/" + platform.application.getName();
            scenarioVariables.event.setWebhook(
                    subscribeApplicationWebhook(inboxName, eventType)
            );
            log.info("Application is subscribed to an event",
                    "Application", platform.application.getId(),
                    "Event", eventType,
                    "Webhook", inboxName);
        });

        And("^create a new alert email address for the application$", () -> {

            int nameLength = RandomUtils.nextInt(4, 7);
            scenarioVariables.newAlertEmailUser = "tester+Test" + RandomStringUtils.randomAlphanumeric(nameLength);
            updateApplicationEmailList(scenarioVariables.newAlertEmailUser + "@sandbox.integration.gentrack.cloud");

        });

        And("^update the '(.*)' webhook for an application$", (String eventType) -> {
            String inboxName = "gcis/" + platform.organisation.name + "/" + eventType;
            scenarioVariables.event.setWebhook(subscribeApplicationWebhook(inboxName, eventType));

            log.info("Update Webhook for Application",
                    "Application", platform.application.getId(),
                    "Webhook", inboxName);
        });

        And("^check if the (.*) received an email notifying that the Application configuration changed$", (String userName) -> {
            User orgAdminUser = User.getUser(userName);
            PlatformMember orgAdmin = platform.members.get(orgAdminUser);

            log.info("Find Org Admin Email about the Application Config Changes",
                    "Status", "Attempt",
                    orgAdmin);

            Email email = new Email(orgAdmin);
            Thread.sleep(3000); //wait for email to arrive
            try {
                email.getAppNewConfigChanges(platform.application.getName());
            } catch (RetryException e) {
                Assert.fail("[Org Admin Email][Status: Failed][User: \"" + orgAdmin + "\"] Failed to get Application Config Changes Email [RetryException: " + e.getMessage() + "]");
            }
        });

        And("^check if the new alert email user received an email indicating that the Application configuration changed$", () -> {

            log.info("Find the Application Config Changes for the new alert email address",
                    "Status", "Attempt",
                    scenarioVariables.newAlertEmailUser);

            Thread.sleep(3000); //wait for email to arrive
            Email email = new Email(scenarioVariables.newAlertEmailUser);

            try {
                email.getAppNewConfigChanges(platform.application.getName());
            } catch (RetryException e) {
                Assert.fail("[New Alert Email Address Email][Status: Failed][User: \"" + scenarioVariables.newAlertEmailUser + "\"] Failed to get Application Config Changes Email [RetryException: " + e.getMessage() + "]");
            }
        });

        And("^create an application for alert emails$", () -> {

            DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
            String tenantId = platform.tenants.get(Product.Alert).id;
            platform.application = portal.applications.createApplicationWithRetry(tenantId, Product.Alert.toLowerCaseString(), "alert", null);

            log.info("Application 'Alert Email' Created", platform.application);
        });

        And("^check if the (.*) received an email notifying that the event delivery failed$", (String userName) -> {
            User orgAdminUser = User.getUser(userName);
            PlatformMember orgAdmin = platform.members.get(orgAdminUser);
            log.info("Find Org Admin Email about the event delivery failure",
                    "Status", "Attempt",
                    orgAdmin);

            Email email = new Email(orgAdmin);
            Thread.sleep(3000); //wait for email to arrive
            try {
                email.getAlertEmailFail(platform.application.getName());

            } catch (RetryException e) {
                Assert.fail("[Org Admin Email][Status: Failed][User: \"" + orgAdmin + "\"] Failed to get Event Delivery Failure Email [RetryException: " + e.getMessage() + "]");
            }
        });

        And("^check if the (.*) received an email notifying that the event delivery recovered$", (String userName) -> {
            User orgAdminUser = User.getUser(userName);
            PlatformMember orgAdmin = platform.members.get(orgAdminUser);
            log.info("Find Org Admin Email about the event delivery recovery",
                    "Status", "Attempt",
                    orgAdmin);

            Email email = new Email(orgAdmin);
            Thread.sleep(3000); //wait for email to arrive
            try {
                email.getAlertEmailRecovered(platform.application.getName());

            } catch (RetryException e) {
                Assert.fail("[Org Admin Email][Status: Failed][User: \"" + orgAdmin + "\"] Failed to get Event Delivery Recovery Email [RetryException: " + e.getMessage() + "]");
            }
        });

        Given("^an alert email application exist and a new alert email address was created against the application$", () -> {
            log.info("Platform Application",
                    "Application", "Alert Application Exist",
                    platform.application);
        });

        And("^create a '(.*)' application for the Core Product '(.*)'$", (String application, Product product) -> {
            DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
            String tenantId = platform.tenants.get(product).id;

            PlatformApplication createApp = portal.applications.createApplicationWithRetry(tenantId, product.toLowerCaseString(), application, null);

            platform.application = createApp;
            platform.application.setPublicKey(
                    portal.applications.formatPublicKey(createApp.getPublicKey()));

            log.info("Application Created", platform.application,
                    "Product", product,
                    "Application", application);
        });

        And("^creating an application with application type '(.*)' for the Core Product '(.*)'$", (String appType, Product product) -> {
            DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
            String tenantId = platform.tenants.get(product).id;

            PlatformApplication createApp = portal.applications.createApplicationWithRetry(tenantId, product.toLowerCaseString(), "MDS", appType);

            platform.application = createApp;
            platform.application.setPublicKey(
                    portal.applications.formatPublicKey(createApp.getPublicKey()));

            log.info("Application Created", platform.application,
                    "Product", product,
                    "ApplicationType", appType);
        });

        And("^subscribed the following events to the webhook$", (DataTable dataTable) -> {

            String inboxName = "gcis/" + platform.organisation.name + "/" + platform.application.getName();
            List<String> event = dataTable.asList(String.class);
            JSONArray eventArray = new JSONArray(event);

            scenarioVariables.event.setWebhook(
                    subscribeProductApplicationWebhook(inboxName, eventArray));

            log.info("Events Subscribed To Webhook", platform.application,
                    "Events", eventArray);
        });

        And("^an application subscribed to (Junifer|Velocity) '(.*)' exists$", this::findSubscribedCustomApplication);
        And("^an application subscribed to (.*) Native '(.*)' exists$", (Product product, String eventType) -> {
            findSubscribedCustomApplication(product, product.toLowerCaseString() + "::" + eventType);
        });

        And("^check if the response include the secret$", () -> {
            log.info("Check Create App Response Include Secret",
                    "Status", "Attempt", "Secret", platform.application.getSecret().isPresent());
            assertThat(platform.application.getSecret()).as("[Application Secret] Create Application didn't return a secret").isNotEmpty();
            log.info("Check Create App Response Include Secret", "Status", "Success");
        });

        Given("^an application doesn't already exist for the application type '(.*)' for the Core Product '(.*)'$", (String appType, Product product) -> {

            platform.application = findInternalApplication(product, appType);

            if (platform.application != null) {
                DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
                portal.applications.removeApplication(platform.application.getId());
                log.info("Removed Application", platform.application,
                        "ApplicationType", appType);
            }
        });

        And("^creating another tenant application with application type '(.*)' is not allowed for the Core Product '(.*)'$", (String appType, Product product) -> {
            DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
            String tenantId = platform.tenants.get(product).id;

            try {
                log.info("Create another application with same application type", platform.application,
                        "TenantId", tenantId,
                        "ApplicationType", appType);
                PlatformApplication createApp = portal.applications.createApplicationWithoutRetry(tenantId, product.toLowerCaseString(), "MDS", appType);
                Assert.assertTrue("Application couldn't be created", false);
            } catch (WebException e) {
                assertThat("An application of the same type already exists").as("[Application Type]").isEqualTo(e.getDetails());
                assertThat(e.getStatus()).as("Status]").isEqualTo(WebStatus.BAD_REQUEST);
            }
        });

        Given("^an application with type '(.*)' exist for the '(.*)' product$", (String appType, Product product) -> {
            DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
            String tenantId = platform.tenants.get(product).id;

            List<PlatformApplication> applicationsForTenant = portal.applications.listApplicationsForTenant(tenantId);
            platform.application = applicationsForTenant.stream()
                    .filter(platformApplication -> platformApplication.getType().contains(appType))
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalArgumentException("[Internal Application] No Application found for " + appType)
                    );

            platform.application.setPublicKey(
                    portal.applications.formatPublicKey(platform.application.getPublicKey()));

            platform.application.setSecret(
                    platform.developerPortalLoggedInAs(User.organisationAdmin).applications.updateApplicationSecret(platform.application.getId()).getString("secret"));

            log.info("Internal Application",
                    platform.application,
                    "Product", product);
        });

        And("^get the application token$", () -> {
            log.info("Get the Application Access Token");

            String applicationSecret = platform.application.getSecret().orElseThrow(
                    () -> new IllegalArgumentException("[ProxyAPI][Failed] Application must have a set Secret to login with")
            );
            scenarioVariables.GCISAccessToken = Authentication.issueAuthenticationTokenForApplication(platform.stack.api, platform.application.getId(), applicationSecret);
        });

        And("^check that the GCIS App can't be deleted$", () -> {

            DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
            WebResult request = portal.applications.removeApplication(platform.application.getId());

            assertThat(request.getStatus())
                    .as("Status is not equal to BAD REQUEST")
                    .isEqualTo(WebStatus.BAD_REQUEST);

            log.info("Remove Application",
                    "OrganisationID", platform.organisation.id,
                    "ApplicationID", platform.application.getId(),
                    "ApplicationName", platform.application.getName());
        });
    }

    /**
     * Find a Custom application that is subscribed to an event for a product.
     * Will throw an exception if no subscribed application exists.
     *
     * @param product   the core Product
     * @param eventType the subscribed event.
     * @throws WebException may be thrown by interacting the API.
     */
    private void findSubscribedCustomApplication(Product product, String eventType) throws WebException {
        DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
        String tenantId = platform.tenants.get(product).id;

        List<PlatformApplication> applicationsForTenant = portal.applications.listApplicationsForTenant(tenantId);
        platform.application = applicationsForTenant.stream()
                .filter(platformApplication -> platformApplication.getType().equals("Custom"))
                .filter(platformApplication -> platformApplication.getEvents().contains(eventType))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("[Application] No Application found subscribed to " + eventType + " for " + product + "/" + tenantId)
                );

        String inboxName = "gcis/" + platform.organisation.name + "/" + platform.application.getName();
        Webhook endpoint = new Webhook(inboxName);
        scenarioVariables.event.setWebhook(endpoint);

        platform.application.setPublicKey(
                portal.applications.formatPublicKey(platform.application.getPublicKey()));

        log.info("Application Subscription",
                platform.application,
                "Product", product);
    }

    /**
     * Subscribe an Application to listen for an event with a Webhook.
     *
     * @param inboxName the inbox to use for the webhook
     * @param eventType the event to subscribe to
     * @return {@link Webhook}
     * @throws WebException may be thrown by interacting with the API
     * @see web.services.portal.Applications#updateApplicationWebhook(String, String, String)
     */
    private Webhook subscribeApplicationWebhook(String inboxName, String eventType) throws WebException {
        DeveloperPortal portal = scenarioVariables.getLoggedIn().developerPortal;
        Webhook endpoint = new Webhook(inboxName);
        JSONObject result = portal.applications.updateApplicationWebhook(platform.application.getId(), endpoint.inboxURL, eventType);
        JSONArray events = result.getJSONArray("events");
        log.info("Update Application Webhook",
                "Status", "Success",
                "ApplicationID", platform.application.getId(),
                "Events", events,
                endpoint);
        return endpoint;
    }

    private Webhook subscribeProductApplicationWebhook(String inboxName, JSONArray eventArray) throws WebException {
        DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
        Webhook endpoint = new Webhook(inboxName);
        JSONObject result = portal.applications.updateProductApplicationWebhook(platform.application.getId(), endpoint.inboxURL, eventArray);
        JSONArray events = result.getJSONArray("events");
        log.info("Update Application Webhook",
                "Status", "Success",
                "ApplicationID", platform.application.getId(),
                "Events", events,
                "EventArray", eventArray,
                endpoint);
        return endpoint;
    }

    /**
     * Update email list on an Application.
     *
     * @return {@link Webhook}
     * @throws WebException may be thrown by interacting with the API
     * @see web.services.portal.Applications#updateApplicationEmailList
     */
    private JSONArray updateApplicationEmailList(String emailAddress) throws WebException {
        DeveloperPortal portal = scenarioVariables.getLoggedIn().developerPortal;

        JSONObject result = portal.applications.updateApplicationEmailList(platform.application.getId(), emailAddress);
        JSONArray emailList = result.getJSONArray("emailList");
        log.info("Update Application Email List",
                "Status", "Success",
                "ApplicationID", platform.application.getId(),
                "EmailList", emailList);
        return emailList;
    }

    /**
     * Update the Webhook on an Application.
     *
     * @param inboxName the inbox to use for the webhook
     * @param eventType the event to subscribe to
     * @return {@link Webhook}
     * @throws WebException may be thrown by interacting with the API
     * @see web.services.portal.Applications#updateApplicationWebhook(String, String, String)
     */
    private Webhook updateApplicationWebhook(String inboxName, String eventType, String newWebhookUrl) throws WebException {
        DeveloperPortal portal = scenarioVariables.getLoggedIn().developerPortal;
        Webhook endpoint = new Webhook(inboxName);
        JSONObject result = portal.applications.updateApplicationWebhook(platform.application.getId(), newWebhookUrl, eventType);
        JSONArray events = result.getJSONArray("events");
        log.info("Update Application Webhook",
                "Status", "Success",
                "ApplicationID", platform.application.getId(),
                "Events", events,
                endpoint);
        return endpoint;
    }

    /**
     * Find a MDS application (type = Meter data services)
     * Will throw an exception if no application exists.
     *
     * @param product the core Product
     * @param appType the application type
     * @throws WebException may be thrown by interacting the API.
     */
    public PlatformApplication findInternalApplication(Product product, String appType) throws WebException {
        DeveloperPortal portal = platform.developerPortalLoggedInAs(User.organisationAdmin);
        String tenantId = platform.tenants.get(product).id;

        List<PlatformApplication> applicationsForTenant = portal.applications.listApplicationsForTenant(tenantId);

        platform.application = applicationsForTenant.stream()
                .filter(platformApplication -> platformApplication.getType().contains(appType))
                .findAny().orElse(null);
        return platform.application;
    }

}
