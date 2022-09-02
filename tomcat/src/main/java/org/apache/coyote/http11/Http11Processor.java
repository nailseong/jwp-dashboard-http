package org.apache.coyote.http11;

import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import nextstep.jwp.db.InMemoryUserRepository;
import nextstep.jwp.exception.UncheckedServletException;
import nextstep.jwp.model.User;
import org.apache.coyote.Processor;
import org.apache.coyote.http.HttpRequest;
import org.apache.coyote.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http11Processor implements Runnable, Processor {

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);
    private static final String WELCOME_MESSAGE = "Hello world!";

    private final Socket connection;

    public Http11Processor(final Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        process(connection);
    }

    @Override
    public void process(final Socket connection) {
        try (final var inputStream = connection.getInputStream();
             final var outputStream = connection.getOutputStream();
             final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             final BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            final HttpRequest httpRequest = HttpRequest.of(bufferedReader.readLine(), getHeaders(bufferedReader));

            final int contentLength = httpRequest.getContentLength();
            final Map<String, String> requestBody = getRequestBody(bufferedReader, contentLength);

            if (httpRequest.isRegister()) {
                final User user = new User(requestBody.get("account"), requestBody.get("password"),
                        requestBody.get("email"));
                InMemoryUserRepository.save(user);

                saveSessionAndResponse(outputStream, user);
                return;
            }

            if (httpRequest.isLogin()) {
                final Optional<User> possibleUser = InMemoryUserRepository.findByAccount(
                        requestBody.get("account"));
                if (possibleUser.isPresent()) {
                    saveSessionAndResponse(outputStream, possibleUser.get());
                    return;
                }
            }

            if (httpRequest.isLoginPage() && httpRequest.alreadyLogin()) {
                final byte[] response = HttpResponse.fromStatusCode(302)
                        .setLocation("/index.html")
                        .toResponseBytes();
                outputStream.write(response);
                outputStream.flush();
                return;
            }

            final String responseBody = getResponseBody(httpRequest.getPath());
            final byte[] response = HttpResponse.fromStatusCode(200)
                    .setResponseBody(responseBody)
                    .setContentType(httpRequest.getContentType())
                    .toResponseBytes();

            outputStream.write(response);
            outputStream.flush();
        } catch (final IOException | UncheckedServletException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void saveSessionAndResponse(final OutputStream outputStream, final User user) throws IOException {
        final UUID sessionId = UUID.randomUUID();
        final HttpSession session = new Session(String.valueOf(sessionId));
        session.setAttribute("user", user);
        new SessionManager().add(session);

        final byte[] response = HttpResponse.fromStatusCode(302)
                .setLocation("/index.html")
                .setCookie("JSESSIONID=" + sessionId)
                .toResponseBytes();

        outputStream.write(response);
        outputStream.flush();
    }

    private Map<String, String> getHeaders(final BufferedReader bufferedReader) throws IOException {
        final Map<String, String> headers = new HashMap<>();

        String header;
        while (!"".equals((header = bufferedReader.readLine()))) {
            final String[] splitHeader = header.split(": ");
            headers.put(splitHeader[0], splitHeader[1]);
        }

        return headers;
    }

    private Map<String, String> getRequestBody(final BufferedReader bufferedReader, final int contentLength)
            throws IOException {
        if (contentLength == 0) {
            return Collections.emptyMap();
        }
        final char[] buffer = new char[contentLength];
        bufferedReader.read(buffer, 0, contentLength);
        final String requestBody = new String(buffer);

        return Arrays.stream(requestBody.split("&"))
                .map(it -> it.split("="))
                .collect(Collectors.toMap(it -> it[0], it -> it[1], (a, b) -> b));
    }

    private String getResponseBody(final String path) throws IOException {
        String responseBody = WELCOME_MESSAGE;

        if (!path.equals("/")) {
            String resourcePath = "static/" + path;
            if (!resourcePath.contains(".")) {
                resourcePath += ".html";
            }

            final String resource = getClass().getClassLoader()
                    .getResource(resourcePath)
                    .getPath();
            final File file = new File(resource);
            final BufferedReader fileReader = new BufferedReader(new FileReader(file));
            responseBody = fileReader.lines()
                    .collect(Collectors.joining("\n"));
            responseBody += "\n";

            fileReader.close();
        }

        return responseBody;
    }
}
