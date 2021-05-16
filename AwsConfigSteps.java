package io.gentrack.steps;

import com.amazonaws.services.apigateway.model.Method;
import com.amazonaws.services.apigateway.model.MethodResponse;
import com.amazonaws.services.apigateway.model.Resource;
import com.github.structlog4j.ILogger;
import com.github.structlog4j.SLoggerFactory;
import io.cucumber.java8.En;
import org.assertj.core.api.SoftAssertions;
import platform.enums.Product;
import variables.Platform;
import web.services.amazon.APIGateway;
import web.services.amazon.SNS;
import web.services.amazon.lambda.LambdaRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class AwsConfigSteps implements En {
    private static final ILogger log = SLoggerFactory.getLogger(AwsConfigSteps.class);

    private List<String> allQueues;
    private Map<String, String> allAttributes;
    private Integer visibilityTimeOut1;
    private Integer visibilityTimeOut2;
    private Integer visibilityTimeOut3;
    private Integer visibilityTimeOut4;
    private String redrivePolicy1;
    private String redrivePolicy2;
    private String redrivePolicy3;
    private String redrivePolicy4;

    public AwsConfigSteps(Platform platform) {

        And("^Check the API Gateway for Platform Stack is setup with '(.*)' as binary media type$", (String binaryType) -> {

            log.info("Check API Gateway Platform Stack Media Types",
                    "PlatformStack", platform.stack.api,
                    "Status", "Attempt");

            APIGateway productApiGateway = new APIGateway(platform.stack.api);
            List<String> StackBinaryTypes = productApiGateway.getBinaryMediaTypes();

            assertThat(StackBinaryTypes).as("[Binary Types] Expecting the binary types to be setup with " + binaryType + " for platform stack: " + platform.stack.api).contains("application/pdf");

            log.info("API Gateway Platform Stack Media Types",
                    "PlatformStack", platform.stack.api,
                    "BinaryTypesFound", StackBinaryTypes);
        });

        And("^Check the API Gateway for '(.*)' is setup with '(.*)' as binary media type$", (Product product, String binaryType) -> {

            log.info("Check API Gateway Mock Gateway Media Types",
                    "Product", product,
                    "Status", "Attempt");

            APIGateway productApiGateway = new APIGateway(platform.stack.mockCores.get(product));
            List<String> StackBinaryTypes = productApiGateway.getBinaryMediaTypes();

            assertThat(StackBinaryTypes).as("[Binary Types] Expecting the binary types to be setup with " + binaryType + " for product: " + product).contains("application/pdf");

            log.info("API Gateway Mock Gateway Media Types",
                    "Product", product,
                    "BinaryTypesFound", StackBinaryTypes);
        });

        And("^the (.*) Lambda Function is invoked$", (String lambdaFunction) -> {
            LambdaRequest request = new LambdaRequest(platform.stack.prefix + "Serverless", lambdaFunction + "Function");
            request.makeLambdaRequest();
        });

        When("^the (.*) event fails 7 times$", (String eventType) -> {

            log.info("Publish a failed event to SNS",
                    "Status", "Attempt",
                    "StackPrefix", platform.stack.prefix);

            long nowMinus40MinsInMs = Instant.now().minus(40, ChronoUnit.MINUTES).toEpochMilli();
            String message = "{\"appId\":\"" + platform.application.getId() + "\",\"deliveryAttempt\": 1,\"eventId\":\"f762e6f0-e330-426b-a0dc-5b94cfebe2c4\",\"eventType\":\"bill-ready\",\"latency\": 150,\"response\":{\"status\": 500,\"statusText\": \"Server error\"},\"success\": false,\"timestamp\":" + nowMinus40MinsInMs + ",\"firstReceivedTimestamp\":" + nowMinus40MinsInMs + "}";
            String topicName = platform.stack.prefix + "-NotifyResult";

            SNS sns = new SNS();
            sns.snsPublish(message, topicName);
        });

        When("^the (.*) event is successfully delivered$", (String eventType) -> {

            log.info("Publish a recovered event to SNS",
                    "Status", "Attempt",
                    "StackPrefix", platform.stack.prefix);

            long nowMinus20MinsInMs = Instant.now().minus(20, ChronoUnit.MINUTES).toEpochMilli();
            String message = "{\"appId\":\"" + platform.application.getId() + "\",\"deliveryAttempt\": 1,\"eventId\":\"f762e6f0-e330-426b-a0dc-5b94cfebe2c4\",\"eventType\":\"bill-ready\",\"latency\": 150,\"response\":{\"status\": 200,\"statusText\": \"OK\"},\"success\": true,\"timestamp\":" + nowMinus20MinsInMs + ",\"firstReceivedTimestamp\":" + nowMinus20MinsInMs + "}";
            String topicName = platform.stack.prefix + "-NotifyResult";

            SNS sns = new SNS();
            sns.snsPublish(message, topicName);
        });

        And("^Check the API Gateway for Platform Stack is setup with CORS Headers on responses$", () -> {

            APIGateway apiGateway = new APIGateway(platform.stack.api);
            assertAPIGatewayHasCORSEnabledForAllResponses(apiGateway);
        });
    }


    /**
     * Assert that all HTTP Method Responses for an API Gateway have CORS Headers configured.
     * Excludes {@code /status} and {@code proxy} endpoints by path.
     * Proxy Endpoints will have CORS headers set in the service being proxied.
     *
     * @param apiGateway An AWS API Gateway from {@link APIGateway}
     */
    private void assertAPIGatewayHasCORSEnabledForAllResponses(APIGateway apiGateway) {
        SoftAssertions softly = new SoftAssertions();

        apiGateway.resources()
                .stream()
                .filter(resource -> !(resource.getPath().contains("{proxy+}")))
                .forEach(resource -> {
                    getResourceMethods(resource.getAWSResource())
                            .forEach(method -> {
                                getMethodResponses(method.getValue())
                                        .forEach(response -> {
                                            softly.assertThat(methodResponseHasCORSHeadersSpecified(
                                                    method.getKey(),
                                                    response.getValue()))
                                                    .as("CORS Headers Specified for "
                                                            + method.getKey() + " "
                                                            + response.getKey() + " "
                                                            + resource.getPath())
                                                    .isTrue();
                                        });
                            });
                });
        softly.assertAll();
    }

    /**
     * Helper Method to return a non-null collection of AWS Resource Methods.
     *
     * @param resource AWS Resource
     * @return Stream representing non-null AWS Methods for Resource
     */
    @SuppressWarnings("PMD.OnlyOneReturn") //Readable return pattern
    private Stream<Map.Entry<String, Method>> getResourceMethods(Resource resource) {
        if (Objects.isNull(resource.getResourceMethods())) {
            return Stream.empty();
        } else {
            return resource.getResourceMethods().entrySet().stream()
                    .filter(entry -> !Objects.isNull(entry.getKey()) || !Objects.isNull(entry.getValue()));
        }
    }

    /**
     * Helper Method to return a non-null collection of AWS Method Responses.
     *
     * @param method AWS Resource Method
     * @return Stream representing non-null AWS Responses for Method
     */
    @SuppressWarnings("PMD.OnlyOneReturn") //Readable return pattern
    private Stream<Map.Entry<String, MethodResponse>> getMethodResponses(Method method) {
        if (Objects.isNull(method.getMethodResponses())) {
            return Stream.empty();
        } else {
            return method.getMethodResponses().entrySet().stream()
                    .filter(response -> !Objects.isNull(response.getKey()) || !Objects.isNull(response.getValue()));

        }
    }


    /**
     * Check if a Method Response has CORS Headers configured in API Gateway.
     * <p>Note that OPTIONS will not have Expose-Headers</p>
     *
     * @param method         the HTTP Method. Used to exclude headers from OPTIONS.
     * @param methodResponse The AWS Method Response
     * @return Boolean representing if all CORS Headers are present
     */
    private boolean methodResponseHasCORSHeadersSpecified(String method, MethodResponse methodResponse) {

        List<String> headers = new ArrayList<>(Arrays.asList(
                "method.response.header.Strict-Transport-Security",
                "method.response.header.Cache-Control",
                "method.response.header.Access-Control-Allow-Methods",
                "method.response.header.Access-Control-Allow-Headers",
                "method.response.header.Access-Control-Expose-Headers",
                "method.response.header.Access-Control-Allow-Origin"));

        if ("OPTIONS".equals(method)) {
            headers.remove("method.response.header.Access-Control-Expose-Headers");
            headers.remove("method.response.header.Strict-Transport-Security");
            headers.remove("method.response.header.Cache-Control");
        } else if (methodResponse.getStatusCode().startsWith("20")) {
            headers.remove("method.response.header.Access-Control-Allow-Methods");
            headers.remove("method.response.header.Access-Control-Allow-Headers");
            if (!"GET".equals(method)) {
                headers.remove("method.response.header.Access-Control-Expose-Headers");
                headers.remove("method.response.header.Cache-Control");
            }

        } else {
            headers.remove("method.response.header.Access-Control-Allow-Methods");
            headers.remove("method.response.header.Access-Control-Allow-Headers");
            headers.remove("method.response.header.Access-Control-Expose-Headers");
            headers.remove("method.response.header.Cache-Control");
        }


        return !Objects.isNull(methodResponse.getResponseParameters())
                && methodResponse.getResponseParameters().keySet().containsAll(headers);
    }

}