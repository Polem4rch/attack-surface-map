package io.github.surfacemap;

import burp.api.montoya.MontoyaApi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SurfaceMapModel {

    public static final int RESP_CAP = 250_000;

    private static final Path STATE_FILE =
            Paths.get(System.getProperty("user.home"), ".burp_surface_map.json");

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ConcurrentHashMap<String, EndpointEntry> model = new ConcurrentHashMap<>();
    private final MontoyaApi api;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final ScheduledExecutorService saver;

    public SurfaceMapModel(MontoyaApi api) {
        this.api = api;
        load();
        saver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "surface-map-saver");
            t.setDaemon(true);
            return t;
        });
        saver.scheduleWithFixedDelay(this::flushIfDirty, 5, 5, TimeUnit.SECONDS);
    }

    // --- endpoint record -----------------------------------------------------

    public static class EndpointEntry {
        public final String path;
        public final String host;
        public final String firstSeen;
        public volatile boolean isNew;
        public final Set<String> methods  = ConcurrentHashMap.newKeySet();
        public final Set<String> params   = ConcurrentHashMap.newKeySet();
        public volatile String   request  = "";
        public volatile String   response = "";

        EndpointEntry(String path, String host, String firstSeen, boolean isNew) {
            this.path      = path;
            this.host      = host;
            this.firstSeen = firstSeen;
            this.isNew     = isNew;
        }
    }

    // --- public API ----------------------------------------------------------

    public String now() {
        return LocalDateTime.now().format(FMT);
    }

    public boolean record(String scheme, String host, String path,
                          String method, List<String> paramNames,
                          String rawRequest, String rawResponse) {
        String key = scheme + "://" + host + path;
        boolean isNew = false;
        EndpointEntry e = model.get(key);
        if (e == null) {
            e = new EndpointEntry(path, host, now(), true);
            EndpointEntry prev = model.putIfAbsent(key, e);
            if (prev != null) { e = prev; } else { isNew = true; }
        }
        e.methods.add(method);
        e.params.addAll(paramNames);
        if (e.request.isEmpty() && rawRequest != null)
            e.request = rawRequest.length() > 2_000_000 ? rawRequest.substring(0, 2_000_000) : rawRequest;
        if (e.response.isEmpty() && rawResponse != null)
            e.response = rawResponse.length() > RESP_CAP
                    ? rawResponse.substring(0, RESP_CAP) + "\n...[truncated]" : rawResponse;
        dirty.set(true);
        return isNew;
    }

    public void setBaseline() { model.values().forEach(e -> e.isNew = false); dirty.set(true); }
    public void clear()       { model.clear(); dirty.set(true); }
    public int  size()        { return model.size(); }

    public List<EndpointEntry> snapshot() { return new ArrayList<>(model.values()); }

    public String dominantHost() {
        Map<String, Integer> counts = new HashMap<>();
        model.values().forEach(e -> counts.merge(e.host, 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("target");
    }

    // --- import / export -----------------------------------------------------

    public void exportTo(File file) throws IOException {
        Files.writeString(file.toPath(), serialise(model), StandardCharsets.UTF_8);
    }

    public int importFrom(File file) throws IOException {
        String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Map<String, EndpointEntry> loaded = deserialise(json);
        int added = 0;
        for (Map.Entry<String, EndpointEntry> kv : loaded.entrySet()) {
            if (model.putIfAbsent(kv.getKey(), kv.getValue()) == null) added++;
        }
        dirty.set(true);
        return added;
    }

    // --- persistence ---------------------------------------------------------

    private void load() {
        if (!Files.exists(STATE_FILE)) return;
        try {
            String json = Files.readString(STATE_FILE, StandardCharsets.UTF_8);
            Map<String, EndpointEntry> loaded = deserialise(json);
            loaded.values().forEach(e -> e.isNew = false);
            model.putAll(loaded);
            api.logging().logToOutput("Surface Map: loaded " + model.size() + " baseline endpoints.");
        } catch (Exception ex) {
            api.logging().logToError("Surface Map: could not load state - " + ex.getMessage());
        }
    }

    private void flushIfDirty() {
        if (!dirty.compareAndSet(true, false)) return;
        try { Files.writeString(STATE_FILE, serialise(model), StandardCharsets.UTF_8); }
        catch (Exception ex) { api.logging().logToError("Surface Map: save failed - " + ex.getMessage()); }
    }

    public void shutdown() {
        alive.set(false);
        saver.shutdown();
        flushIfDirty();
    }

    // =========================================================================
    // Minimal hand-rolled JSON serialiser (no external deps)
    // =========================================================================

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String serialise(Map<String, EndpointEntry> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, EndpointEntry> kv : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            EndpointEntry e = kv.getValue();
            sb.append('"').append(esc(kv.getKey())).append("\":{");
            sb.append("\"path\":\"").append(esc(e.path)).append("\",");
            sb.append("\"host\":\"").append(esc(e.host)).append("\",");
            sb.append("\"first\":\"").append(esc(e.firstSeen)).append("\",");
            sb.append("\"new\":").append(e.isNew).append(',');
            sb.append("\"methods\":[");
            boolean fm = true;
            for (String m : e.methods) { if (!fm) sb.append(','); fm = false; sb.append('"').append(esc(m)).append('"'); }
            sb.append("],\"params\":[");
            boolean fp = true;
            for (String p : e.params)  { if (!fp) sb.append(','); fp = false; sb.append('"').append(esc(p)).append('"'); }
            sb.append("],\"request\":\"").append(esc(e.request)).append("\",");
            sb.append("\"response\":\"").append(esc(e.response)).append("\"}");
        }
        sb.append('}');
        return sb.toString();
    }

    // =========================================================================
    // Recursive-descent JSON parser
    // Handles arbitrary nesting — works for both our own format and the
    // Jython-exported format which may contain large JSON response bodies.
    // =========================================================================

    /** Mutable cursor used during parsing. */
    private static class Cursor {
        final String s;
        int pos;
        Cursor(String s) { this.s = s; this.pos = 0; }
        char peek() { skipWs(); return pos < s.length() ? s.charAt(pos) : 0; }
        char next() { skipWs(); return pos < s.length() ? s.charAt(pos++) : 0; }
        void skipWs() { while (pos < s.length() && s.charAt(pos) <= ' ') pos++; }
        void expect(char c) { char got = next(); if (got != c) throw new RuntimeException("Expected '" + c + "' at " + (pos-1) + " got '" + got + "'"); }
    }

    private static Object parseValue(Cursor c) {
        char p = c.peek();
        if (p == '"')  return parseString(c);
        if (p == '{')  return parseObject(c);
        if (p == '[')  return parseArray(c);
        if (p == 't')  { c.pos += 4; return Boolean.TRUE; }
        if (p == 'f')  { c.pos += 5; return Boolean.FALSE; }
        if (p == 'n')  { c.pos += 4; return null; }
        // number or other — read until delimiter
        int start = c.pos;
        while (c.pos < c.s.length()) {
            char ch = c.s.charAt(c.pos);
            if (ch == ',' || ch == '}' || ch == ']' || ch <= ' ') break;
            c.pos++;
        }
        return c.s.substring(start, c.pos);
    }

    private static String parseString(Cursor c) {
        c.expect('"');
        StringBuilder sb = new StringBuilder();
        while (c.pos < c.s.length()) {
            char ch = c.s.charAt(c.pos++);
            if (ch == '"') break;
            if (ch == '\\' && c.pos < c.s.length()) {
                char esc = c.s.charAt(c.pos++);
                switch (esc) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (c.pos + 4 <= c.s.length()) {
                            String hex = c.s.substring(c.pos, c.pos + 4); c.pos += 4;
                            try { sb.append((char) Integer.parseInt(hex, 16)); }
                            catch (NumberFormatException e) { sb.append("\\u").append(hex); }
                        }
                        break;
                    default: sb.append(esc);
                }
            } else { sb.append(ch); }
        }
        return sb.toString();
    }

    private static Map<String, Object> parseObject(Cursor c) {
        c.expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        if (c.peek() == '}') { c.pos++; return map; }
        while (true) {
            String key = parseString(c);
            c.expect(':');
            Object val = parseValue(c);
            map.put(key, val);
            char sep = c.next();
            if (sep == '}') break;
            if (sep != ',') throw new RuntimeException("Expected ',' or '}' got '" + sep + "'");
        }
        return map;
    }

    private static List<Object> parseArray(Cursor c) {
        c.expect('[');
        List<Object> list = new ArrayList<>();
        if (c.peek() == ']') { c.pos++; return list; }
        while (true) {
            list.add(parseValue(c));
            char sep = c.next();
            if (sep == ']') break;
            if (sep != ',') throw new RuntimeException("Expected ',' or ']' got '" + sep + "'");
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, EndpointEntry> deserialise(String json) {
        Cursor c = new Cursor(json);
        Map<String, Object> root = parseObject(c);
        Map<String, EndpointEntry> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> kv : root.entrySet()) {
            try {
                Map<String, Object> v = (Map<String, Object>) kv.getValue();
                String path     = str(v, "path");
                String host     = str(v, "host");
                String first    = str(v, "first");
                boolean isNew   = bool(v, "new");
                String request  = str(v, "request");
                String response = str(v, "response");

                EndpointEntry e = new EndpointEntry(path, host, first, isNew);
                e.request  = request;
                e.response = response;
                Object methods = v.get("methods");
                if (methods instanceof List) for (Object m : (List<?>) methods) if (m != null) e.methods.add(m.toString());
                Object params  = v.get("params");
                if (params  instanceof List) for (Object p : (List<?>) params)  if (p != null) e.params.add(p.toString());
                out.put(kv.getKey(), e);
            } catch (Exception ex) {
                // skip malformed entries silently
            }
        }
        return out;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key); return v == null ? "" : v.toString();
    }
    private static boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key); return Boolean.TRUE.equals(v) || "true".equals(v);
    }
}
