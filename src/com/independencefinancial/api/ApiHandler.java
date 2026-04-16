package com.bcfinancial.api;

import com.bcfinancial.portfolio.*;
import com.bcfinancial.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * HTTP handler for all {@code /api/*} requests.
 *
 * <p>Routing is done by inspecting the path and HTTP method.  All responses
 * are JSON.  CORS headers are added on every response so a browser-hosted
 * front end can call the server from any origin during development.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET  /api/health
 *   POST /api/users/register     {username, password, email, alpha, beta, lambda}
 *   POST /api/users/login        {username, password}
 *   GET  /api/portfolios/{user}/{name}
 *   POST /api/portfolios         {username, portfolioName, symbols:[…], weights:[…]}
 *   POST /api/equity/load        {symbols:[…], days:N}  or  {symbols:[…], full:true}
 *   POST /api/optimize           {username, password, portfolioName,
 *                                 endYear, endMonth, endDay,
 *                                 durationYears, timePeriodDays, maxEpochs}
 *   GET  /api/optimize/{jobId}
 * </pre>
 */
public class ApiHandler implements HttpHandler {

    private static final Logger logger = Logger.getLogger(ApiHandler.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> UF_LABELS = Map.of(
            "powerLog",   "Prospect Theory (Kahneman-Tversky)",
            "sortino",    "Sortino Ratio",
            "omega",      "Omega Ratio",
            "convexLoss", "Convex Loss Utility",
            "sharpe",     "Sharpe Ratio",
            "utility",    "Utility"
    );

    private final AppService service;
    private final ConcurrentHashMap<String, JobStatus> jobs = new ConcurrentHashMap<>();
    private final ExecutorService jobPool = Executors.newVirtualThreadPerTaskExecutor();

    public ApiHandler(AppService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------
    // HttpHandler entry point
    // ------------------------------------------------------------------

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // CORS — allow any origin so a local JS dev server can reach us
        ex.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        // Strip /api prefix so routing works on the remaining path
        if (path.startsWith("/api")) path = path.substring(4);
        if (path.isEmpty()) path = "/";

        try {
            route(ex, method, path);
        } catch (Exception e) {
            logger.warning(method + " " + path + " → 500: " + e);
            java.util.logging.Logger.getLogger(ApiHandler.class.getName())
                .log(java.util.logging.Level.WARNING, "Stack trace:", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            sendError(ex, 500, msg);
        }
    }

    // ------------------------------------------------------------------
    // Router
    // ------------------------------------------------------------------

    private void route(HttpExchange ex, String method, String path) throws Exception {
        String[] parts = path.split("/", -1); // parts[0] is always ""

        // GET /health
        if ("GET".equals(method) && "/health".equals(path)) {
            sendOk(ex, Map.of("status", "healthy", "time", new Date().toString()));
            return;
        }

        // /users/register  /users/login  /users/{username}/utility
        if (parts.length >= 3 && "users".equals(parts[1])) {
            if ("POST".equals(method) && parts.length == 3 && "register".equals(parts[2])) { handleRegister(ex); return; }
            if ("POST".equals(method) && parts.length == 3 && "login".equals(parts[2]))    { handleLogin(ex);    return; }
            if ("PUT".equals(method)  && parts.length == 4 && "utility".equals(parts[3]))  { handleUpdateUtility(ex, parts[2]); return; }
        }

        // GET  /portfolios/{user}/{name}
        // GET  /portfolios/{user}/{name}/latest-date
        // POST /portfolios
        if ("portfolios".equals(parts.length > 1 ? parts[1] : "")) {
            if ("POST".equals(method)   && parts.length == 2) { handleStorePortfolio(ex); return; }
            if ("GET".equals(method)    && parts.length == 4) { handleGetPortfolio(ex, parts[2], parts[3]); return; }
            if ("DELETE".equals(method) && parts.length == 4) { handleDeletePortfolio(ex, parts[2], parts[3]); return; }
            if ("GET".equals(method)    && parts.length == 5 && "latest-date".equals(parts[4])) {
                handlePortfolioLatestDate(ex, parts[2], parts[3]); return;
            }
        }

        // POST /equity/load   GET /equity/latest-date   POST /equity/synthetic
        if (parts.length == 3 && "equity".equals(parts[1])) {
            if ("POST".equals(method) && "load".equals(parts[2]))               { handleEquityLoad(ex);        return; }
            if ("GET".equals(method)  && "latest-date".equals(parts[2]))        { handleEquityLatestDate(ex);  return; }
            if ("POST".equals(method) && "synthetic".equals(parts[2]))          { handleSyntheticEquity(ex);   return; }
        }

        // POST /optimize   GET /optimize/{jobId}   GET /optimize/{jobId}/report
        if ("optimize".equals(parts.length > 1 ? parts[1] : "")) {
            if ("POST".equals(method) && parts.length == 2) { handleOptimize(ex);                        return; }
            if ("GET".equals(method)  && parts.length == 3) { handleJobStatus(ex, parts[2]);             return; }
            if ("GET".equals(method)  && parts.length == 4 && "report".equals(parts[3])) {
                handleJobReport(ex, parts[2]); return;
            }
        }

        // POST /report/aggregate   — combined report from a list of job IDs
        if ("POST".equals(method) && parts.length == 3
                && "report".equals(parts[1]) && "aggregate".equals(parts[2])) {
            handleAggregateReport(ex); return;
        }

        // GET /debug          — list debug JSON files
        // GET /debug/{file}   — return a debug JSON file
        if ("debug".equals(parts.length > 1 ? parts[1] : "")) {
            if ("GET".equals(method) && parts.length == 2) { handleDebugList(ex);             return; }
            if ("GET".equals(method) && parts.length == 3) { handleDebugFile(ex, parts[2]);   return; }
        }

        sendError(ex, 404, "Not found: " + method + " /api" + path);
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private void handleRegister(HttpExchange ex) throws Exception {
        Map<String, Object> body = readBody(ex);
        String  username  = require(body, "username");
        String  password  = require(body, "password");
        String  email     = (String)  body.getOrDefault("email",  "");
        double  alpha     = toDouble(body.getOrDefault("alpha",  0.88));
        double  beta      = toDouble(body.getOrDefault("beta",   0.88));
        double  lambda    = toDouble(body.getOrDefault("lambda", 2.25));

        User user = service.register(username, password, email, alpha, beta, lambda);
        sendOk(ex, userToMap(user));
    }

    private void handleUpdateUtility(HttpExchange ex, String username) throws Exception {
        Map<String, Object> body = readBody(ex);
        String password = require(body, "password");
        String type     = (String) body.getOrDefault("type", "powerLog");

        User user;
        if ("powerLog".equalsIgnoreCase(type)) {
            double alpha  = toDouble(body.getOrDefault("alpha",  0.88));
            double beta   = toDouble(body.getOrDefault("beta",   0.88));
            double lambda = toDouble(body.getOrDefault("lambda", 2.25));
            user = service.updateUtility(username, password, alpha, beta, lambda);
        } else if ("convexLoss".equalsIgnoreCase(type)) {
            double alpha  = toDouble(body.getOrDefault("alpha",  0.88));
            double beta   = toDouble(body.getOrDefault("beta",   1.0 / 0.88));
            double lambda = toDouble(body.getOrDefault("lambda", 2.25));
            user = service.updateUtilityConvexLoss(username, password, alpha, beta, lambda);
        } else {
            double rfAnnual = toDouble(body.getOrDefault("rfAnnual", 0.04));
            user = service.updateUtilityByType(username, password, type, rfAnnual);
        }
        sendOk(ex, userToMap(user));
    }

    private void handleLogin(HttpExchange ex) throws Exception {
        Map<String, Object> body = readBody(ex);
        User user = service.login(require(body, "username"), require(body, "password"));
        sendOk(ex, userToMap(user));
    }

    private void handleDeletePortfolio(HttpExchange ex, String username, String name) throws Exception {
        service.deletePortfolio(username, name);
        sendOk(ex, Map.of("deleted", name));
    }

    private void handlePortfolioLatestDate(HttpExchange ex, String username, String name) throws Exception {
        Portfolio p = service.getPortfolio(username, name);
        if (p == null) { sendError(ex, 404, "Portfolio not found: " + username + "/" + name); return; }
        List<String> symbols = Arrays.asList(p.getSymbols());
        java.util.Date latest = service.getLatestCommonDate(symbols);
        if (latest == null) { sendError(ex, 404, "No data in database for symbols: " + symbols); return; }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(latest);
        sendOk(ex, Map.of(
                "year",  cal.get(java.util.Calendar.YEAR),
                "month", cal.get(java.util.Calendar.MONTH) + 1,
                "day",   cal.get(java.util.Calendar.DAY_OF_MONTH)
        ));
    }

    private void handleGetPortfolio(HttpExchange ex, String username, String name) throws Exception {
        Portfolio p = service.getPortfolio(username, name);
        if (p == null) { sendError(ex, 404, "Portfolio not found: " + username + "/" + name); return; }
        sendOk(ex, portfolioToMap(p));
    }

    private void handleStorePortfolio(HttpExchange ex) throws Exception {
        Map<String, Object> body    = readBody(ex);
        String   username           = require(body, "username");
        String   portfolioName      = require(body, "portfolioName");
        List<?>  symbolsList        = (List<?>) body.get("symbols");
        List<?>  weightsList        = (List<?>) body.get("weights");

        String[] symbols  = symbolsList.stream().map(Object::toString).toArray(String[]::new);
        Double[] weights  = weightsList.stream().map(w -> toDouble(w)).toArray(Double[]::new);

        Portfolio portfolio = new Portfolio(portfolioName, symbols, weights);
        Portfolio stored    = service.storePortfolio(username, portfolio);
        sendOk(ex, portfolioToMap(stored));
    }

    private void handleEquityLatestDate(HttpExchange ex) throws Exception {
        String query = ex.getRequestURI().getQuery(); // symbols=VOO,BND,...
        if (query == null || !query.startsWith("symbols=")) {
            sendError(ex, 400, "symbols query parameter required"); return;
        }
        List<String> symbols = Arrays.stream(query.substring("symbols=".length()).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (symbols.isEmpty()) { sendError(ex, 400, "symbols required"); return; }

        java.util.Date latest = service.getLatestCommonDate(symbols);
        if (latest == null) {
            sendError(ex, 404, "No data found for one or more symbols"); return;
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(latest);
        sendOk(ex, Map.of(
                "year",  cal.get(java.util.Calendar.YEAR),
                "month", cal.get(java.util.Calendar.MONTH) + 1,
                "day",   cal.get(java.util.Calendar.DAY_OF_MONTH)
        ));
    }

    private void handleEquityLoad(HttpExchange ex) throws Exception {
        Map<String, Object> body = readBody(ex);
        List<?> raw  = (List<?>) body.get("symbols");
        if (raw == null || raw.isEmpty()) { sendError(ex, 400, "symbols required"); return; }
        List<String> symbols = raw.stream().map(Object::toString).toList();

        boolean full = Boolean.TRUE.equals(body.get("full"));
        int     days = full ? 0 : ((Number) body.getOrDefault("days", 30)).intValue();

        int[] counts = service.loadEquity(symbols, days, full);
        sendOk(ex, Map.of("loaded", counts[0], "failed", counts[1]));
    }

    private void handleOptimize(HttpExchange ex) throws Exception {
        Map<String, Object> body = readBody(ex);
        String username     = require(body, "username");
        String password     = require(body, "password");
        String portfolioName = require(body, "portfolioName");

        Calendar today = Calendar.getInstance();
        int endYear       = toInt(body.getOrDefault("endYear",   today.get(Calendar.YEAR)));
        int endMonth      = toInt(body.getOrDefault("endMonth",  today.get(Calendar.MONTH) + 1));
        int endDay        = toInt(body.getOrDefault("endDay",    today.get(Calendar.DAY_OF_MONTH)));
        int durationYears = toInt(body.getOrDefault("durationYears", 5));
        int    timePeriodDays        = toInt(body.getOrDefault("timePeriodDays", 45));
        int    maxEpochs              = toInt(body.getOrDefault("maxEpochs", 300));
        double minImprovementPerEpoch = toDouble(body.getOrDefault("minImprovementPerEpoch", 0.0));
        double maxWeightPerInstrument = toDouble(body.getOrDefault("maxWeightPerInstrument", 0.9));
        String optimizeBy             = (String) body.getOrDefault("optimizeBy",     "utility");
        // optimizeByType is the UI's raw selection (powerLog/sortino/omega/convexLoss/sharpe)
        // used only for labelling in reports; defaults to optimizeBy when not supplied
        String optimizeByType         = (String) body.getOrDefault("optimizeByType", optimizeBy);
        double annealingTemperature   = toDouble(body.getOrDefault("annealingTemperature", 0.05));
        double annealingDecay         = toDouble(body.getOrDefault("annealingDecay",       0.95));
        int    multiStartCount        = ((Number) body.getOrDefault("multiStartCount",      1)).intValue();
        double stepSize               = toDouble(body.getOrDefault("stepSize", 0.01));

        // Validate credentials immediately — fail fast before queuing the job
        User user = service.login(username, password);

        Calendar endCal = Calendar.getInstance();
        endCal.set(endYear, endMonth - 1, endDay, 0, 0, 0);
        endCal.set(Calendar.MILLISECOND, 0);
        Date endDate = endCal.getTime();

        Calendar startCal = (Calendar) endCal.clone();
        startCal.add(Calendar.YEAR, -durationYears);
        Date startDate = startCal.getTime();

        String jobId = UUID.randomUUID().toString();
        JobStatus job = new JobStatus();
        jobs.put(jobId, job);

        jobPool.submit(() -> {
            try {
                AppService.OptimizeResult optimizeResult =
                        service.optimize(user, portfolioName, startDate, endDate,
                                timePeriodDays, maxEpochs, minImprovementPerEpoch,
                                maxWeightPerInstrument, optimizeBy,
                                annealingTemperature, annealingDecay, multiStartCount, stepSize);
                Map<String, Object> resultMap = optimizationToMap(optimizeResult.optimization());
                resultMap.put("portfolioName", portfolioName);
                resultMap.put("optimizeBy",    optimizeByType);
                if (optimizeResult.stoppedReason() != null) {
                    resultMap.put("stoppedReason", optimizeResult.stoppedReason());
                }
                job.complete(resultMap);
            } catch (Exception e) {
                logger.warning("Optimization job " + jobId + " failed: " + e.getMessage());
                job.fail(e.getMessage());
            }
        });

        sendOk(ex, Map.of("jobId", jobId, "state", "running"));
    }

    private void handleJobStatus(HttpExchange ex, String jobId) throws Exception {
        JobStatus job = jobs.get(jobId);
        if (job == null) { sendError(ex, 404, "Job not found: " + jobId); return; }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId",  jobId);
        data.put("state",  job.state);
        if ("completed".equals(job.state)) data.put("result", job.result);
        if ("failed".equals(job.state))    data.put("error",  job.error);
        sendOk(ex, data);
    }

    private void handleAggregateReport(HttpExchange ex) throws Exception {
        Map<String, Object> body = readBody(ex);
        @SuppressWarnings("unchecked")
        List<?> rawIds = (List<?>) body.get("jobIds");
        if (rawIds == null || rawIds.isEmpty()) {
            sendError(ex, 400, "jobIds required"); return;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Object id : rawIds) {
            String jobId = id.toString();
            JobStatus job = jobs.get(jobId);
            if (job == null)                     { missing.add(jobId + " (not found)"); continue; }
            if (!"completed".equals(job.state)) { missing.add(jobId + " (not completed)"); continue; }
            results.add(job.result);
        }
        if (results.isEmpty()) {
            sendError(ex, 400, "No completed jobs found in provided list"); return;
        }

        byte[] pdf = AggregateReportGenerator.generate(results, UF_LABELS);

        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        ex.getResponseHeaders().set("Content-Type", "application/pdf");
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"aggregate_report_" + ts + ".pdf\"");
        ex.sendResponseHeaders(200, pdf.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(pdf); }
    }

    private void handleDebugList(HttpExchange ex) throws Exception {
        File dir = new File("debug");
        String[] files = dir.list((d, n) -> n.endsWith(".json"));
        if (files == null) files = new String[0];
        Arrays.sort(files, Comparator.reverseOrder());
        sendOk(ex, Map.of("files", Arrays.asList(files)));
    }

    private void handleDebugFile(HttpExchange ex, String filename) throws Exception {
        if (!filename.matches("[\\w\\-. ]+\\.json")) { sendError(ex, 400, "Invalid filename"); return; }
        File f = new File("debug/" + filename);
        if (!f.exists() || !f.isFile()) { sendError(ex, 404, "Not found: " + filename); return; }
        byte[] data = Files.readAllBytes(f.toPath());
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private void handleJobReport(HttpExchange ex, String jobId) throws Exception {
        JobStatus job = jobs.get(jobId);
        if (job == null)                        { sendError(ex, 404, "Job not found: " + jobId); return; }
        if (!"completed".equals(job.state))     { sendError(ex, 400, "Job not yet completed");   return; }

        String portfolioName = (String) job.result.getOrDefault("portfolioName", "Portfolio");
        String optimizeBy    = (String) job.result.getOrDefault("optimizeBy",    "utility");
        String methodLabel   = UF_LABELS.getOrDefault(optimizeBy, optimizeBy);

        byte[] pdf = PdfReportGenerator.generate(job.result, portfolioName, methodLabel);

        String safeName = portfolioName.replaceAll("[^a-zA-Z0-9_-]", "_");
        ex.getResponseHeaders().set("Content-Type", "application/pdf");
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + safeName + "_report.pdf\"");
        ex.sendResponseHeaders(200, pdf.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(pdf); }
    }

    private void handleSyntheticEquity(HttpExchange ex) throws Exception {
        Map<String, Object> body = readBody(ex);
        String symbol = require(body, "symbol");

        @SuppressWarnings("unchecked")
        Map<String, Object> rawHoldings = (Map<String, Object>) body.get("holdings");
        if (rawHoldings == null || rawHoldings.isEmpty()) {
            sendError(ex, 400, "holdings map required"); return;
        }
        Map<String, Double> holdings = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : rawHoldings.entrySet())
            holdings.put(e.getKey(), toDouble(e.getValue()));

        java.util.Date startDate = null, endDate = null;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        if (body.containsKey("startDate")) startDate = sdf.parse((String) body.get("startDate"));
        if (body.containsKey("endDate"))   endDate   = sdf.parse((String) body.get("endDate"));

        int rows = service.createSyntheticEquity(symbol, holdings, startDate, endDate);
        sendOk(ex, Map.of("symbol", symbol, "rows", rows));
    }

    // ------------------------------------------------------------------
    // JSON serialisation helpers
    // ------------------------------------------------------------------

    private Map<String, Object> userToMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username",       u.getUsername());
        m.put("email",          u.getEmailAddress());
        m.put("portfolioNames", service.getPortfolioNames(u.getUsername()));
        // Expose utility function type and parameters so the UI can display/edit them
        com.bcfinancial.utility.UtilityFunction uf = u.getUtilityFunction();
        if (uf instanceof com.bcfinancial.utility.ProspectTheoryUtilityFunction pluf) {
            m.put("utilityType", "powerLog");
            m.put("alpha",  pluf.getAlpha());
            m.put("beta",   pluf.getBeta());
            m.put("lambda", pluf.getLambda());
        } else if (uf instanceof com.bcfinancial.utility.SortinoRatioUtilityFunction suf) {
            m.put("utilityType", "sortino");
            m.put("rfAnnual",    suf.getRfAnnual());
        } else if (uf instanceof com.bcfinancial.utility.CVaRUtilityFunction cuf) {
            m.put("utilityType",   "cvar");
            m.put("tailFraction",  cuf.getTailFraction());
        } else if (uf instanceof com.bcfinancial.utility.OmegaRatioUtilityFunction ouf) {
            m.put("utilityType", "omega");
            m.put("rfAnnual",    ouf.getRfAnnual());
        } else if (uf instanceof com.bcfinancial.utility.ConvexLossUtilityFunction cluf) {
            m.put("utilityType", "convexLoss");
            m.put("alpha",  cluf.getAlpha());
            m.put("beta",   cluf.getBeta());
            m.put("lambda", cluf.getLambda());
        }
        return m;
    }

    private Map<String, Object> portfolioToMap(Portfolio p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("portfolioName", p.getPortfolioName());
        m.put("symbols",       p.getSymbols());
        m.put("weights",       p.getWeights());
        return m;
    }

    private Map<String, Object> optimizationToMap(PortfolioOptimization po) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",     po.getPortfolioOptimizationName());
        m.put("state",    po.getState());
        if (po.getSP500()    != null) m.put("sp500",    pcdToMap(po.getSP500()));
        if (po.getOriginal() != null) m.put("original", pcdToMap(po.getOriginal()));

        List<Map<String, Object>> epochs = new ArrayList<>();
        if (po.getOptimized() != null) {
            for (PortfolioCalculatedData pcd : po.getOptimized()) {
                epochs.add(pcdToMap(pcd));
            }
        }
        m.put("epochs", epochs);

        // Final (best) portfolio from the last epoch, or original if none
        if (!epochs.isEmpty()) {
            m.put("finalPortfolio", portfolioToMap(
                    po.getOptimized()[po.getOptimized().length - 1]
                      .getPortfolioCalculationRequest().getPortfolio()));
        }
        return m;
    }

    private Map<String, Object> pcdToMap(PortfolioCalculatedData pcd) {
        Map<String, Object> m = new LinkedHashMap<>();
        Portfolio p = pcd.getPortfolioCalculationRequest().getPortfolio();
        m.put("name",            pcd.getPortfolioCalculationRequest().getPortfolioName());
        m.put("symbols",         p.getSymbols());
        m.put("weights",         p.getWeights());
        m.put("cagr",            pcd.getFullPeriodCagr());
        m.put("avgCagr",         pcd.getAverageCAGR());
        m.put("weightedUtility", pcd.getWeightedUtility());
        double[] returns = pcd.getSortedReturns();
        if (returns != null && returns.length >= 100) {
            m.put("var95", pcd.get95VAR());
            m.put("var99", pcd.get99VAR());
            m.put("vtg95", pcd.get95VTG());
            m.put("vtg99", pcd.get99VTG());
        }
        m.put("maxDrawdown",  pcd.getMaxDrawdown());
        m.put("sharpeRatio",  pcd.getSharpeRatio());
        return m;
    }

    // ------------------------------------------------------------------
    // HTTP / JSON utilities
    // ------------------------------------------------------------------

    private Map<String, Object> readBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length == 0) return new HashMap<>();
        return mapper.readValue(bytes, new TypeReference<>() {});
    }

    private void sendOk(HttpExchange ex, Object data) throws IOException {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "ok");
        resp.put("data",   data);
        send(ex, 200, mapper.writeValueAsString(resp));
    }

    private void sendError(HttpExchange ex, int code, String message) throws IOException {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status",  "error");
        resp.put("message", message != null ? message : "Unknown error");
        send(ex, code, mapper.writeValueAsString(resp));
    }

    private void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String require(Map<String, Object> m, String key) throws Exception {
        Object v = m.get(key);
        if (v == null || v.toString().isBlank()) throw new Exception("Missing required field: " + key);
        return v.toString();
    }

    private double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }

    // ------------------------------------------------------------------
    // Async job tracking
    // ------------------------------------------------------------------

    private static final class JobStatus {
        volatile String              state  = "running";
        volatile Map<String, Object> result;
        volatile String              error;

        void complete(Map<String, Object> result) { this.result = result; this.state = "completed"; }
        void fail(String error)                    { this.error  = error;  this.state = "failed";    }
    }
}
