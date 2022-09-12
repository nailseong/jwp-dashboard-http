package org.apache.coyote.http;

public enum HttpStatusCode {

    OK(200, "OK"),
    FOUND(302, "Found"),
    UNAUTHORIZED(401, "Unauthorized"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    ;

    private final int statusCode;
    private final String message;

    HttpStatusCode(final int statusCode, final String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public String getResponseStartLine() {
        return statusCode + " " + message;
    }

    public String getMessage() {
        return message;
    }
}
