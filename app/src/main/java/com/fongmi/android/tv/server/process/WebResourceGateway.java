package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Nano;
import com.github.catvod.Proxy;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.utils.UrlSafety;
import com.fongmi.android.tv.web.CookieBridge;
import com.fongmi.android.tv.web.HeaderPolicy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class WebResourceGateway implements Process {

    private static final int MAX_REDIRECTS = 5;
    // The gateway target comes from a request parameter, so the resolved address must be checked
    // at connection time: a TTL=0 DNS name answers the pre-flight UrlSafety check with a public
    // address and the actual connection with a rebinded private one (TOCTOU).
    private static final okhttp3.OkHttpClient CLIENT = OkHttp.noRedirect().newBuilder().dns(new com.fongmi.android.tv.utils.FilteringDns()).build();

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/webResource");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (session.getMethod() == NanoHTTPD.Method.OPTIONS) return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, ""), session);
        okhttp3.Response response = null;
        try {
            Map<String, String> params = session.getParms();
            String target = params.get("url");
            if (!UrlSafety.isSafeHttpUrl(target)) return cors(Nano.error(Response.Status.BAD_REQUEST, "Unsupported url"), session);
            Map<String, String> headers = HeaderPolicy.withDefaultUa(HeaderPolicy.parse(params.get("headers")));
            copyRange(session, headers);
            Request.Builder builder = new Request.Builder().url(target).headers(HeaderPolicy.of(headers));
            CookieBridge.apply(builder.build().url(), builder, "include".equals(params.get("credentials")), HeaderPolicy.hasCookie(headers));
            builder.method(getMethod(session), getBody(session, builder.build().headers(), files));
            long start = System.currentTimeMillis();
            response = executeWithRedirectCheck(builder.build());
            SpiderDebug.log("web-resource", "%s %s -> %s in %sms", response.request().method(), response.request().url(), response.code(), System.currentTimeMillis() - start);
            CookieBridge.set(target, response.headers());
            return cors(toResponse(response), session);
        } catch (Throwable e) {
            if (response != null) response.close();
            SpiderDebug.log("web-resource", e);
            return cors(Nano.error(e.getMessage()), session);
        }
    }

    /**
     * Executes the request without OkHttp auto-redirect, then manually follows redirects one hop
     * at a time, re-validating every Location against the SSRF guard so a 30x to an internal
     * address cannot be silently followed.
     */
    private okhttp3.Response executeWithRedirectCheck(Request request) throws Exception {
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            SpiderDebug.log("web-resource", "%s %s", request.method(), request.url());
            okhttp3.Response response = CLIENT.newCall(request).execute();
            int code = response.code();
            boolean redirect = code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
            if (!redirect) return response;
            String location = response.header("Location");
            response.close();
            if (location == null) throw new IllegalStateException("Redirect without Location");
            java.net.URL base = request.url().url();
            String next = new java.net.URL(base, location).toString();
            if (!UrlSafety.isSafeHttpUrl(next)) throw new SecurityException("Unsafe redirect target");
            Request.Builder builder = request.newBuilder().url(next);
            if (code == 303) builder.method("GET", null);
            request = builder.build();
        }
        throw new IllegalStateException("Too many redirects");
    }

    private void copyRange(IHTTPSession session, Map<String, String> headers) {
        String range = session.getHeaders().get("range");
        if (!TextUtils.isEmpty(range)) headers.put("Range", range);
    }

    private String getMethod(IHTTPSession session) {
        String method = session.getMethod().name();
        return "OPTIONS".equals(method) ? "GET" : method;
    }

    private RequestBody getBody(IHTTPSession session, Headers headers, Map<String, String> files) {
        String method = getMethod(session);
        if ("GET".equals(method) || "HEAD".equals(method)) return null;
        String body = session.getParms().get("body");
        if (TextUtils.isEmpty(body)) body = files.get("postData");
        String contentType = headers.get("Content-Type");
        MediaType type = MediaType.parse(contentType == null ? "text/plain; charset=utf-8" : contentType);
        return RequestBody.create(body == null ? "" : body, type);
    }

    private Response toResponse(okhttp3.Response response) {
        InputStream stream = response.body().byteStream();
        String type = getContentType(response);
        Response.Status status = Response.Status.lookup(response.code());
        Response result = NanoHTTPD.newChunkedResponse(status == null ? Response.Status.OK : status, type, stream);
        for (String name : response.headers().names()) if (copyHeader(name)) for (String value : response.headers(name)) result.addHeader(name, value);
        return result;
    }

    private String getContentType(okhttp3.Response response) {
        MediaType type = response.body().contentType();
        return type == null ? NanoHTTPD.MIME_PLAINTEXT : type.toString();
    }

    private boolean copyHeader(String name) {
        return !"Connection".equalsIgnoreCase(name) && !"Transfer-Encoding".equalsIgnoreCase(name) && !"Keep-Alive".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name);
    }

    private Response cors(Response response, IHTTPSession session) {
        String origin = session.getHeaders().get("origin");
        response.addHeader("Access-Control-Allow-Origin", isAllowedOrigin(origin) ? origin : "");
        response.addHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type,Range,X-Requested-With,Authorization");
        response.addHeader("Access-Control-Expose-Headers", "Content-Type,Content-Length,Content-Range,Accept-Ranges");
        response.addHeader("Access-Control-Max-Age", "86400");
        // A remote page served through this gateway must not be framable by an external site:
        // an evil.com iframe would execute with the loopback origin and read same-origin APIs.
        // Same-origin embedding (media elements are unaffected) keeps legitimate page use intact.
        response.addHeader("X-Frame-Options", "SAMEORIGIN");
        response.addHeader("Content-Security-Policy", "frame-ancestors 'self'");
        return response;
    }

    private boolean isAllowedOrigin(String origin) {
        if (TextUtils.isEmpty(origin)) return false;
        if ("null".equals(origin)) return false;
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (host == null) return false;
            // Only an explicit loopback origin on the exact server port may read responses;
            // accepting a missing port (default 80) would admit any local service on port 80.
            return "http".equals(uri.getScheme()) && ("127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host)) && uri.getPort() == Proxy.getPort();
        } catch (Throwable e) {
            return false;
        }
    }
}
