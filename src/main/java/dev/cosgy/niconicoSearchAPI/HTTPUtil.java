package dev.cosgy.niconicoSearchAPI;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HTTPUtil {
    private String method;
    private String targetAddress;
    private String requestData;
    private Map<String, String> query;
    private Map<String, String> headers;

    private HttpURLConnection connection;
    private URL url;

    public HTTPUtil(String method, String targetAddress, Map<String, String> query, Map<String, String> headers) {
        this.method = method;
        this.targetAddress = targetAddress;
        this.query = query;
        this.headers = headers;
    }

    public HTTPUtil(String method, String targetAddress) {
        this.method = method;
        this.targetAddress = targetAddress;
    }

    public HTTPUtil() {
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
    }

    public void setQueryMap(Map<String, String> query) {
        this.query = query;
    }

    public HTTPUtil addQuery(String key, Object value) {
        if (query == null) this.query = new HashMap<>();
        query.put(key, value.toString());
        return this;
    }

    public Map<String, String> getQuery() {
        return query;
    }

    public HTTPUtil setHeaderMap(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public HTTPUtil addHeader(String key, Object value) {
        if (headers == null) this.headers = new HashMap<>();
        headers.put(key, value.toString());
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getRequestData() {
        return requestData;
    }

    public HTTPUtil setRequestData(String data) {
        this.requestData = data;
        return this;
    }

    public HttpURLConnection getConnection() {
        return connection;
    }

    public URL getUrl() {
        return url;
    }

    public String request() {
        validateRequest();
        try {
            String params = buildParams();
            url = resolveTargetUrl(params);
            connection = (HttpURLConnection) url.openConnection();
            configureConnection();
            writePostBodyIfNeeded(params);
            connection.connect();

            final int responseCode = connection.getResponseCode();
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                return readResponseBody(connection.getInputStream());
            } else {
                System.out.println("Error: " + responseCode + "\n" + readErrorBody());
            }
        } catch (MalformedURLException e) {
            throw new NullPointerException("The URL is invalid: " + e.getLocalizedMessage());
        } catch (ProtocolException e) {
            throw new NullPointerException("The method name is invalid: " + e.getLocalizedMessage());
        } catch (IOException e) {
            System.out.println("An error occurred: " + e.getLocalizedMessage() + "\n" + readErrorBody());
        } finally {
            if (connection != null) connection.disconnect();
        }

        return null;
    }

    private void validateRequest() {
        if (method == null || method.isEmpty()) throw new NullPointerException("The method is not set.");
        if (targetAddress == null || targetAddress.isEmpty()) throw new NullPointerException("The URL is not set.");
    }

    private String buildParams() {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        query.forEach((key, val) -> {
            if (sb.length() == 0) sb.append(key).append("=").append(val);
            else sb.append("&").append(key).append("=").append(val);
        });
        return sb.toString().replaceFirst("&$", "");
    }

    private URL resolveTargetUrl(String params) throws MalformedURLException {
        if (query != null && method.equalsIgnoreCase("GET")) {
            return new URL(targetAddress + (params.isEmpty() ? "" : "?" + params));
        }
        return new URL(targetAddress);
    }

    private void configureConnection() throws ProtocolException {
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        if (headers != null) headers.forEach(connection::addRequestProperty);
    }

    private void writePostBodyIfNeeded(String params) throws IOException {
        if (!method.equalsIgnoreCase("POST")) {
            return;
        }
        String payload = null;
        if (query != null && requestData == null) {
            payload = params;
        } else if (query == null && requestData != null) {
            payload = requestData;
        }
        if (payload == null) {
            return;
        }
        try (PrintWriter pw = new PrintWriter(connection.getOutputStream())) {
            pw.print(payload);
        }
    }

    private String readResponseBody(InputStream response) throws IOException {
        if (response == null) {
            return "";
        }
        try (InputStream in = response;
             InputStreamReader isr = new InputStreamReader(in, connection.getContentEncoding() == null ? "UTF-8" : connection.getContentEncoding());
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            br.lines().forEach(sb::append);
            return sb.toString();
        }
    }

    private String readErrorBody() {
        try {
            InputStream in = connection != null ? connection.getErrorStream() : null;
            if (in == null && connection != null) {
                in = connection.getInputStream();
            }
            if (in == null) {
                return "";
            }
            return readResponseBody(in);
        } catch (Exception ignored) {
            return "";
        }
    }
}
