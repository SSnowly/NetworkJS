package hu.snowylol.networkjs;

import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FetchBinding {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private static final Gson gson = new Gson();

    public static FetchResponse fetch(String url) {
        return fetch(url, null);
    }

    public static FetchResponse fetch(String url, FetchOptions options) {
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);
            
            if (options != null) {
                if (options.headers != null) {
                    for (Map.Entry<String, String> header : options.headers.entrySet()) {
                        requestBuilder.addHeader(header.getKey(), header.getValue());
                    }
                }
                
                String method = options.method != null ? options.method.toUpperCase() : "GET";
                RequestBody body = null;
                
                if (options.body != null) {
                    String contentType = "application/json";
                    if (options.headers != null && options.headers.containsKey("Content-Type")) {
                        contentType = options.headers.get("Content-Type");
                    }
                    body = RequestBody.create(options.body, MediaType.parse(contentType));
                }
                
                switch (method) {
                    case "GET":
                        requestBuilder.get();
                        break;
                    case "POST":
                        requestBuilder.post(body != null ? body : RequestBody.create("", null));
                        break;
                    case "PUT":
                        requestBuilder.put(body != null ? body : RequestBody.create("", null));
                        break;
                    case "DELETE":
                        requestBuilder.delete(body);
                        break;
                    case "PATCH":
                        requestBuilder.patch(body != null ? body : RequestBody.create("", null));
                        break;
                    default:
                        requestBuilder.method(method, body);
                        break;
                }
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = client.newCall(request).execute()) {
                return new FetchResponse(response);
            }
            
        } catch (IOException e) {
            NetworkJS.LOGGER.error("Fetch request failed for URL: " + url, e);
            throw new RuntimeException("Fetch failed: " + e.getMessage(), e);
        }
    }

    public static CompletableFuture<FetchResponse> fetchAsync(String url) {
        return fetchAsync(url, null);
    }

    public static CompletableFuture<FetchResponse> fetchAsync(String url, FetchOptions options) {
        return CompletableFuture.supplyAsync(() -> fetch(url, options));
    }

    public static class FetchOptions {
        public String method;
        public Map<String, String> headers;
        public String body;
        
        public FetchOptions() {}
        
        public FetchOptions(String method, Map<String, String> headers, Object body) {
            this.method = method;
            this.headers = headers;
            this.body = body != null ? body.toString() : null;
        }
    }

    public static class FetchResponse {
        private final int status;
        private final String statusText;
        private final Map<String, String> headers;
        private final String bodyText;
        private final boolean ok;
        
        public FetchResponse(Response response) throws IOException {
            this.status = response.code();
            this.statusText = response.message();
            this.ok = response.isSuccessful();
            
            this.headers = new java.util.HashMap<>();
            for (String name : response.headers().names()) {
                this.headers.put(name, response.header(name));
            }
            
            ResponseBody body = response.body();
            this.bodyText = body != null ? body.string() : "";
        }
        
        public int getStatus() { return status; }
        public String getStatusText() { return statusText; }
        public boolean isOk() { return ok; }
        public Map<String, String> getHeaders() { return headers; }
        public String getText() { return bodyText; }
        public String text() { return bodyText; }
        
        public JsonElement json() {
            try {
                return JsonParser.parseString(bodyText);
            } catch (Exception e) {
                throw new RuntimeException("Response is not valid JSON: " + e.getMessage(), e);
            }
        }
        
        public <T> T json(Class<T> clazz) {
            try {
                return gson.fromJson(bodyText, clazz);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse JSON to " + clazz.getSimpleName() + ": " + e.getMessage(), e);
            }
        }
    }
}
