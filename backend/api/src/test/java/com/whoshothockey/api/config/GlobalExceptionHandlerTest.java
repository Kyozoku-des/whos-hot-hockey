package com.whoshothockey.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404InsteadOf500() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/api/teams", "api/teams");

        ResponseEntity<Map<String, String>> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not found", response.getBody().get("error"));
        assertEquals("api/teams", response.getBody().get("path"));
    }

    @Test
    void handleBadRequest_namesParameterWithoutEchoingInput() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "<script>", long.class, "playerId", null, new NumberFormatException("<script>"));

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("error", "Invalid parameter", "parameter", "playerId"), response.getBody());
    }
}
