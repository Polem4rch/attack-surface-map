package io.github.surfacemap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Passive HTTP handler.
 * - Listens only to Proxy traffic.
 * - Filters to Burp scope.
 * - Offloads all model/disk work to a background thread so the handler
 *   returns immediately (per BApp Store acceptance criteria).
 */
public class SurfaceMapHandler implements HttpHandler {

    private final MontoyaApi      api;
    private final SurfaceMapModel model;
    private final SurfaceMapTab   tab;
    private final AtomicBoolean   capturing = new AtomicBoolean(false);

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "surface-map-worker");
        t.setDaemon(true);
        return t;
    });

    public SurfaceMapHandler(MontoyaApi api, SurfaceMapModel model, SurfaceMapTab tab) {
        this.api   = api;
        this.model = model;
        this.tab   = tab;
    }

    public void setCapturing(boolean on) {
        capturing.set(on);
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent req) {
        return RequestToBeSentAction.continueWith(req);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived resp) {
        if (!capturing.get()) return ResponseReceivedAction.continueWith(resp);
        if (!resp.toolSource().isFromTool(ToolType.PROXY)) return ResponseReceivedAction.continueWith(resp);

        // Check scope without blocking – if url() throws we just skip
        try {
            if (!api.scope().isInScope(resp.initiatingRequest().url()))
                return ResponseReceivedAction.continueWith(resp);
        } catch (Exception e) {
            return ResponseReceivedAction.continueWith(resp);
        }

        // Snapshot the data we need before handing off (the objects may not
        // survive after this method returns – per large-project guidance)
        final String method   = resp.initiatingRequest().method();
        final String host     = resp.initiatingRequest().httpService().host();
        final String scheme   = resp.initiatingRequest().httpService().secure() ? "https" : "http";
        final String path     = resp.initiatingRequest().path();
        // strip query from path for the map key
        final String cleanPath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;

        final List<String> params = new ArrayList<>();
        try {
            for (HttpParameter p : resp.initiatingRequest().parameters()) {
                if (p.type() != HttpParameterType.COOKIE) params.add(p.name());
            }
        } catch (Exception ignored) {}

        final String rawReq;
        final String rawResp;
        try { rawReq  = resp.initiatingRequest().toString(); } catch (Exception e) { return ResponseReceivedAction.continueWith(resp); }
        try { rawResp = resp.toString(); } catch (Exception e) { return ResponseReceivedAction.continueWith(resp); }

        worker.submit(() -> {
            try {
                boolean isNew = model.record(scheme, host, cleanPath, method, params, rawReq, rawResp);
                if (isNew) {
                    tab.logAndRefreshCount(method + " " + cleanPath);
                } else {
                    tab.refreshCount();
                }
            } catch (Exception ex) {
                api.logging().logToError("Surface Map worker error: " + ex.getMessage());
            }
        });

        return ResponseReceivedAction.continueWith(resp);
    }

    public void shutdown() {
        worker.shutdown();
        try { worker.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }
}
