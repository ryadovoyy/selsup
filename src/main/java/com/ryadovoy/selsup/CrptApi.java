package com.ryadovoy.selsup;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CrptApi {
    private static final Logger logger = Logger.getLogger(CrptApi.class.getName());

    private static final String BASE_URL = "https://ismp.crpt.ru/api/v3";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final String DOCUMENT_CREATION_PATH = "/lk/documents/create";
    private static final String DOCUMENT_CREATION_ERROR_MESSAGE = "Failed to create document";

    private final HttpClient httpClient;
    private final HttpJsonHelper httpJsonHelper;
    private final RateLimiter rateLimiter;

    public CrptApi(HttpClient httpClient, HttpJsonHelper httpJsonHelper, int requestLimit, TimeUnit timeUnit) {
        this.httpClient = httpClient;
        this.httpJsonHelper = httpJsonHelper;
        this.rateLimiter = new RateLimiter(requestLimit, timeUnit);
    }

    public CompletableFuture<DocumentResponseDto> createDocument(DocumentDto document, String signature) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(createUri(DOCUMENT_CREATION_PATH))
                .POST(httpJsonHelper.createBodyPublisher(document))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .timeout(REQUEST_TIMEOUT)
                .build();

        return sendAsyncRequest(httpRequest, DocumentResponseDto.class, DOCUMENT_CREATION_ERROR_MESSAGE);
    }

    private <T> CompletableFuture<T> sendAsyncRequest(
            HttpRequest httpRequest, Class<T> responseBodyClass, String errorMessage) {
        try {
            rateLimiter.acquire();

            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        int statusCode = response.statusCode();
                        String body = response.body();

                        logger.log(Level.INFO, "Request: {0} {1}, Response: {2} {3}",
                                new Object[]{ httpRequest.method(), httpRequest.uri(), statusCode, body });

                        if (HttpUtils.is2xxSuccessful(statusCode)) {
                            return httpJsonHelper.parseResponseBody(body, responseBodyClass);
                        }

                        ErrorResponseDto errorResponse =
                                httpJsonHelper.parseResponseBody(body, ErrorResponseDto.class);
                        throw new ApiException(errorMessage + ": " + errorResponse.getMessage(), statusCode);
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
    }

    private static URI createUri(String path) {
        return URI.create(BASE_URL + path);
    }

    public static class RateLimiter {
        private final int requestLimit;
        private final Semaphore semaphore;

        public RateLimiter(int requestLimit, TimeUnit timeUnit) {
            if (requestLimit <= 0) {
                throw new IllegalArgumentException("Request limit must be positive");
            }
            this.requestLimit = requestLimit;
            this.semaphore = new Semaphore(requestLimit, true);

            ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
            scheduler.scheduleAtFixedRate(this::resetSemaphore, 1, 1, timeUnit);
        }

        private void resetSemaphore() {
            semaphore.drainPermits();
            semaphore.release(requestLimit);
        }

        public void acquire() throws InterruptedException {
            semaphore.acquire();
        }
    }

    public static class HttpJsonHelper {
        private final ObjectMapper objectMapper;

        public HttpJsonHelper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        public <T> HttpRequest.BodyPublisher createBodyPublisher(T requestBody) {
            try {
                String jsonRequestBody = objectMapper.writeValueAsString(requestBody);
                return HttpRequest.BodyPublishers.ofString(jsonRequestBody);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to create json", e);
            }
        }

        public <T> T parseResponseBody(String responseBody, Class<T> responseBodyClass) {
            try {
                return objectMapper.readValue(responseBody, responseBodyClass);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse response body", e);
            }
        }
    }

    public static class HttpUtils {
        public static boolean is2xxSuccessful(int statusCode) {
            return statusCode / 100 == 2;
        }
    }

    public static class ApiException extends RuntimeException {
        private final int statusCode;

        public ApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            property = "doc_type"
    )
    public abstract static class DocumentDto {
        @JsonProperty("doc_id")
        private String id;

        @JsonProperty("doc_status")
        private String status;

        private DocumentDescriptionDto description;

        private boolean importRequest;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public DocumentDescriptionDto getDescription() {
            return description;
        }

        public void setDescription(DocumentDescriptionDto description) {
            this.description = description;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }
    }

    public static class DocumentDescriptionDto {
        private String participantInn;

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    @JsonTypeName("LP_INTRODUCE_GOODS")
    public static class LpIntroduceGoodsDocumentDto extends DocumentDto {
        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate productionDate;

        @JsonProperty("production_type")
        private String productionType;

        private List<LpIntroduceGoodsProductDto> products;

        @JsonProperty("reg_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate regDate;

        @JsonProperty("reg_number")
        private String regNumber;

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        public List<LpIntroduceGoodsProductDto> getProducts() {
            return products;
        }

        public void setProducts(List<LpIntroduceGoodsProductDto> products) {
            this.products = products;
        }

        public LocalDate getRegDate() {
            return regDate;
        }

        public void setRegDate(LocalDate regDate) {
            this.regDate = regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }

        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }
    }

    public static class LpIntroduceGoodsProductDto {
        @JsonProperty("certificate_document")
        private String certificateDocument;

        @JsonProperty("certificate_document_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate certificateDocumentDate;

        @JsonProperty("certificate_document_number")
        private String certificateDocumentNumber;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate productionDate;

        @JsonProperty("tnved_code")
        private String tnvedCode;

        @JsonProperty("uit_code")
        private String uitCode;

        @JsonProperty("uitu_code")
        private String uituCode;

        public String getCertificateDocument() {
            return certificateDocument;
        }

        public void setCertificateDocument(String certificateDocument) {
            this.certificateDocument = certificateDocument;
        }

        public LocalDate getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        public void setCertificateDocumentDate(LocalDate certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
        }

        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        public void setCertificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getTnvedCode() {
            return tnvedCode;
        }

        public void setTnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
        }

        public String getUitCode() {
            return uitCode;
        }

        public void setUitCode(String uitCode) {
            this.uitCode = uitCode;
        }

        public String getUituCode() {
            return uituCode;
        }

        public void setUituCode(String uituCode) {
            this.uituCode = uituCode;
        }
    }

    public static class DocumentResponseDto {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class ErrorResponseDto {
        @JsonProperty("error_message")
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
