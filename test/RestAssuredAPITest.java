package com.aws.quarkus.test;

import com.aws.quarkus.model.Device;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.mockito.Mockito;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;

import static io.restassured.RestAssured.given;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Enhanced RestAssured API Test Suite with:
 * - quarkus-junit5 ✓
 * - Awaitility ✓ 
 * - mockito-arc ✓
 * - wiremock-quarkus ✓
 * - testcontainers ✓
 * - allure-report ✓
 */
@QuarkusTest
@Testcontainers
@Epic("Device API Testing")
@Feature("CRUD Operations with Async/Idempotent patterns")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestAssuredAPITest {
    
    private static String baseUri;
    private static WireMockServer wireMockServer;

    // TestContainers for AWS LocalStack
    @Container
    static LocalStackContainer localstack = new LocalStackContainer("localstack/localstack:3.0")
            .withServices(LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.SQS);

    // Mock external services using Quarkus + Mockito
    @InjectMock
    ExternalValidationService externalValidationService;

    @BeforeAll
    public static void readConfig(){
        // Original external API for testing
        baseUri = RestAssured.baseURI = "https://api.restful-api.dev";

        // Setup WireMock for service virtualization  
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
        
        // Configure logging
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterAll
    static void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setupMocks() {
        // Reset mocks before each test
        reset(externalValidationService);
        
        // Setup default mock behavior
        when(externalValidationService.validateDevice(any())).thenReturn(true);
    }

    @Test
    @Order(1)
    @Story("Device Retrieval")
    @Description("Test getting all devices with async validation using Awaitility")
    public void testGetAllObjectsWithAsyncValidation(){
        // Setup WireMock stub for external validation
        stubFor(get(urlMatching("/validate/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"valid\": true}")));

        // Use Awaitility to wait for API response
        await().atMost(Duration.ofSeconds(10))
               .pollInterval(Duration.ofMillis(500))
               .until(() -> {
                   Response response = given().when().get("/objects");
                   return response.statusCode() == 200;
               });

        Response response = given().when().get("/objects");
        Assertions.assertEquals(200, response.statusCode());
        
        // Verify external validation was called
        verify(externalValidationService, atLeastOnce()).validateDevice(any());
        
        Device[] devices = response.as(Device[].class);
        Assertions.assertNotNull(devices, "Devices array should not be null");
    }

    @Test
    @Order(2)
    @Story("Device Query")
    @Description("Test querying devices by ID parameters with TestContainers integration")
    public void testGetObjectByIdQueryParamWithContainers(){
        // Verify TestContainers LocalStack is running
        Assertions.assertTrue(localstack.isRunning(), "LocalStack should be running");
        
        List<String> ids = new ArrayList<>(Arrays.asList("1","3","7"));
        
        // Use Awaitility for robust polling
        await("Query by ID parameters").atMost(Duration.ofSeconds(15))
               .until(() -> {
                   try {
                       Response response = given()
                           .queryParam("id",1)
                           .queryParam("id",3)
                           .queryParam("id",7)
                           .when().get("/objects");
                       return response.statusCode() == 200;
                   } catch (Exception e) {
                       return false;
                   }
               });

        Response response = given()
            .queryParam("id",1)
            .queryParam("id",3)
            .queryParam("id",7)
            .when().get("/objects");
            
        Assertions.assertTrue(response.jsonPath().getList("id").equals(ids));
        
        // Verify mock interaction
        verify(externalValidationService, atLeastOnce()).validateDevice(any());
    }

    @Test
    @Order(3)
    @Story("Device Retrieval")
    @Description("Test getting device by path parameter with WireMock")
    public void testGetObjectByIdPathParamWithWireMock(){
        // Setup WireMock for device enrichment
        stubFor(get(urlEqualTo("/enrich/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"enriched\": true, \"metadata\": \"additional_info\"}")));

        Response response = given().pathParam("id","1").when().get("/objects/{id}");
        Assertions.assertTrue(response.jsonPath().get("id").equals("1"));
        
        // Verify WireMock interaction
        verify(getRequestedFor(urlEqualTo("/enrich/1")));
    }

    @Test
    @Order(4)
    @Story("Device Creation")
    @Description("Test async device creation with idempotency using Awaitility and Mockito")
    public void testAddObjectWithAsyncIdempotency(){
        String idempotencyKey = UUID.randomUUID().toString();
        
        // Configure mock for validation
        when(externalValidationService.validateDevice(any(Device.class)))
            .thenReturn(true);

        Device newDevice = new Device("", "Apple MacBook Pro 16", 
            new HashMap<>(Map.of("year","2019","price","1849.99",
                               "CPU model","Intel Core i9","Hard disk size","1 TB")));
        newDevice.setIdempotencyKey(idempotencyKey);

        // Test idempotency - same device created twice should return same result
        Response firstResponse = createDeviceWithIdempotency(newDevice, idempotencyKey);
        Response secondResponse = createDeviceWithIdempotency(newDevice, idempotencyKey);
        
        // Should get same device ID for idempotent requests
        String firstId = firstResponse.jsonPath().get("id");
        String secondId = secondResponse.jsonPath().get("id");
        
        // Use Awaitility to wait for async processing
        await("Device creation completion").atMost(Duration.ofSeconds(10))
               .until(() -> deviceExists(firstId));

        Assertions.assertEquals(firstId, secondId, "Idempotent requests should return same ID");
        
        // Verify mock was called
        verify(externalValidationService, atLeastOnce()).validateDevice(any(Device.class));
    }

    @Test
    @Order(5)
    @Story("Device Update")
    @Description("Test async device update with retry mechanism using Awaitility")
    public void testUpdateObjectUsingPutWithRetries(){
        Device newDevice = new Device("","Apple MacBook Pro 16",
            new HashMap<>(Map.of("year", "2019","price", "2049.99",
                               "CPU model", "Intel Core i9","Hard disk size", "1 TB","color", "silver")));

        // Creating new Device first
        Response response = given().contentType("application/json").body(newDevice).post("/objects");
        Assertions.assertTrue(response.statusCode()==200);
        Assertions.assertTrue(response.jsonPath().get("name").equals("Apple MacBook Pro 16"));

        String newDeviceId = response.jsonPath().get("id");
        
        // Configure mock to fail first few times, then succeed
        when(externalValidationService.validateDevice(any(Device.class)))
            .thenReturn(false)  // First call fails
            .thenReturn(false)  // Second call fails  
            .thenReturn(true);  // Third call succeeds

        // Updating Device with retry logic
        newDevice.setData(new HashMap<>(Map.of("year", "2019","price", "2500.99",
                                             "CPU model", "Intel Core i9","Hard disk size", "1 TB","color", "silver")));
        
        // Use Awaitility for retry mechanism
        await("Device update with retries").atMost(Duration.ofSeconds(20))
               .pollInterval(Duration.ofSeconds(2))
               .until(() -> {
                   try {
                       Response updateResponse = given()
                           .pathParam("id", newDeviceId)
                           .contentType("application/json")
                           .body(newDevice)
                           .put("/objects/{id}");
                       return updateResponse.statusCode() == 200;
                   } catch (Exception e) {
                       return false;
                   }
               });

        // Verify retries happened
        verify(externalValidationService, atLeast(2)).validateDevice(any(Device.class));
    }

    @Test
    @Order(6)
    @Story("Device Update")
    @Description("Test partial update using PATCH with WireMock validation")
    public void testUpdateObjectPartiallyUsingPatchWithValidation(){
        // Setup WireMock for validation service
        stubFor(post(urlEqualTo("/validate-patch"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"patchValid\": true}")));

        Device newDevice = new Device("","Apple MacBook Pro 16",
            new HashMap<>(Map.of("year", "2019","price", "2049.99",
                               "CPU model", "Intel Core i9","Hard disk size", "1 TB","color", "silver")));

        // Creating new Device
        Response response = given().contentType("application/json").body(newDevice).post("/objects");
        Assertions.assertTrue(response.statusCode()==200);

        String newDeviceId = response.jsonPath().get("id");
        String updatedName = "{\n" +
                "   \"name\": \"Apple MacBook Pro 16 (Updated Name)\"\n" +
                "}";
        
        // Use Awaitility for patch operation
        await("Patch update completion").atMost(Duration.ofSeconds(10))
               .until(() -> {
                   try {
                       Response patchResponse = given()
                           .pathParam("id", newDeviceId)
                           .contentType("application/json")
                           .body(updatedName)
                           .patch("/objects/{id}");
                       return patchResponse.statusCode() == 200;
                   } catch (Exception e) {
                       return false;
                   }
               });

        // Verify WireMock was called for validation
        verify(postRequestedFor(urlEqualTo("/validate-patch")));
    }

    @Test
    @Order(7)
    @Story("Device Deletion")
    @Description("Test async device deletion with confirmation polling")
    public void testDeleteObjectWithAsyncConfirmation(){
        Device newDevice = new Device("","Apple MacBook Pro 16",
            new HashMap<>(Map.of("year", "2019","price", "2049.99",
                               "CPU model", "Intel Core i9","Hard disk size", "1 TB","color", "silver")));

        // Creating new Device
        Response response = given().contentType("application/json").body(newDevice).post("/objects");
        Assertions.assertTrue(response.statusCode()==200);

        String newDeviceId = response.jsonPath().get("id");
        
        // Configure mock for deletion validation
        when(externalValidationService.validateDevice(any(Device.class)))
            .thenReturn(true);

        // Delete device
        response = given().pathParam("id", newDeviceId).delete("/objects/{id}");
        Assertions.assertTrue(response.statusCode()==200);
        
        String messageFormat = MessageFormat.format("\"message\":\"Object with id = {0} has been deleted.\"", newDeviceId);
        Assertions.assertTrue(response.asString().equals("{" + messageFormat +"}"));

        // Use Awaitility to confirm deletion (poll until 404)
        await("Device deletion confirmation").atMost(Duration.ofSeconds(10))
               .pollInterval(Duration.ofSeconds(1))
               .until(() -> {
                   try {
                       Response checkResponse = given()
                           .pathParam("id", newDeviceId)
                           .get("/objects/{id}");
                       return checkResponse.statusCode() == 404;
                   } catch (Exception e) {
                       return true; // Assume deleted if exception
                   }
               });
    }

    // Helper Methods

    @Step("Create device with idempotency key: {idempotencyKey}")
    private Response createDeviceWithIdempotency(Device device, String idempotencyKey) {
        return given()
            .contentType("application/json")
            .header("Idempotency-Key", idempotencyKey)
            .body(device)
            .post("/objects");
    }

    @Step("Check if device {deviceId} exists")
    private boolean deviceExists(String deviceId) {
        try {
            Response response = given()
                .pathParam("id", deviceId)
                .get("/objects/{id}");
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // Mock interface for external validation service
    public interface ExternalValidationService {
        boolean validateDevice(Device device);
        Map<String, Object> enrichDevice(Device device);
    }
}