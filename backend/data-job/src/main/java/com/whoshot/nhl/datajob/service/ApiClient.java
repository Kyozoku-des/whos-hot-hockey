package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.exception.NonRetryableApiClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;

/**
 * Reusable HTTP client wrapper that standardizes API calls, error handling, and retries.
 * <p>
 * Public methods retry on {@link ApiClientException} (3 attempts, 1s then 2s backoff), enabled by
 * {@link com.whoshot.nhl.datajob.config.ResilienceConfig}. 4xx responses raise
 * {@link NonRetryableApiClientException} and fail fast. Retries apply only to calls through the
 * Spring proxy, so {@link #get(String, ParameterizedTypeReference)} delegating to the three-argument
 * overload retries once at the outer call, not twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClient {

    private final RestClient restClient;

    /**
     * Common error handler for 4xx client errors.
     *
     * @param url request URL that produced the error
     * @param httpResponse raw HTTP response used for status extraction
     */
    private void handleClientError(String url, ClientHttpResponse httpResponse) throws IOException {
        log.error("Client error during request to {}: {}", url, httpResponse.getStatusCode());
        throw new NonRetryableApiClientException(
                "Client error: " + httpResponse.getStatusCode() + " for URL: " + url);
    }

    /**
     * Common error handler for 5xx server errors.
     *
     * @param url request URL that produced the error
     * @param httpResponse raw HTTP response used for status extraction
     */
    private void handleServerError(String url, ClientHttpResponse httpResponse) throws IOException {
        log.error("Server error during request to {}: {}", url, httpResponse.getStatusCode());
        throw new ApiClientException(
                "Server error: " + httpResponse.getStatusCode() + " for URL: " + url);
    }

    /**
     * Performs a GET request without custom headers.
     *
     * @param url target endpoint URL
     * @param typeRef expected response body type reference
     * @param <T> response type
     * @return deserialized response body
     * @throws ApiClientException if the request fails after all retries or returns a null body
     */
    @Retryable(
            includes = {ApiClientException.class},
            excludes = {NonRetryableApiClientException.class},
            maxRetries = 2,
            delay = 1000,
            multiplier = 2
    )
    public <T> T get(String url, ParameterizedTypeReference<T> typeRef) {
        return get(url, typeRef, null);
    }

    /**
     * Performs a GET request with custom headers to the specified URL and returns the response body.
     * Makes up to 3 attempts on retryable ApiClientException with exponential backoff.
     *
     * @param url          the URL to send the GET request to
     * @param typeRef      the class type of the expected response
     * @param headers      custom headers to include in the request
     * @param <T>          the type of the response
     * @return the response body
     * @throws ApiClientException if the request fails after all retries
     */
    @Retryable(
            includes = {ApiClientException.class},
            excludes = {NonRetryableApiClientException.class},
            maxRetries = 2,
            delay = 1000,
            multiplier = 2
    )
    public <T> T get(String url, ParameterizedTypeReference<T> typeRef, Map<String, String> headers) {
        try {
            log.debug("GET request to: {}", url);

            var requestSpec = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON);

            if (headers != null && !headers.isEmpty()) {
                requestSpec.headers(httpHeaders -> headers.forEach(httpHeaders::add));
            }

            T response = requestSpec
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (httpRequest, httpResponse) -> handleClientError(url, httpResponse))
                    .onStatus(HttpStatusCode::is5xxServerError, (httpRequest, httpResponse) -> handleServerError(url, httpResponse))
                    .body(typeRef);

            if (response == null) {
                throw new ApiClientException("Received null response from: " + url);
            }

            log.debug("GET request successful: {}", url);
            return response;

        } catch (ApiClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during GET request to {}: {}", url, e.getMessage(), e);
            throw new ApiClientException("Unexpected error for URL: " + url, e);
        }
    }

    /**
     * Performs a POST request with multipart form data (for file uploads).
     * Makes up to 3 attempts on retryable ApiClientException with exponential backoff.
     *
     * @param url          the URL to send the POST request to
     * @param formData     the multipart form data (use MultiValueMap with Resource for files)
     * @param typeRef      the class type of the expected response
     * @param <T>          the type of the response
     * @return the response body
     * @throws ApiClientException if the request fails after all retries
     */
    @Retryable(
            includes = {ApiClientException.class},
            excludes = {NonRetryableApiClientException.class},
            maxRetries = 2,
            delay = 1000,
            multiplier = 2
    )
    public <T> T postMultipart(String url, MultiValueMap<String, Object> formData, ParameterizedTypeReference<T> typeRef) {
        try {
            log.debug("POST multipart request to: {}", url);

            T response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formData)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (httpRequest, httpResponse) -> handleClientError(url, httpResponse))
                    .onStatus(HttpStatusCode::is5xxServerError, (httpRequest, httpResponse) -> handleServerError(url, httpResponse))
                    .body(typeRef);

            if (response == null) {
                throw new ApiClientException("Received null response from: " + url);
            }

            log.debug("POST multipart request successful: {}", url);
            return response;

        } catch (ApiClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during POST multipart request to {}: {}", url, e.getMessage(), e);
            throw new ApiClientException("Unexpected error for URL: " + url, e);
        }
    }
}
