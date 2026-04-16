package com.bcfinancial.api;

import com.bcfinancial.data.EquityDataLoader;
import com.bcfinancial.data.EquityRepository;
import com.bcfinancial.instruments.Equity;
import com.bcfinancial.data.PersistenceConfig;
import com.bcfinancial.executor.PortfolioParallelExecutor;
import com.bcfinancial.portfolio.*;
import com.bcfinancial.user.User;
import com.bcfinancial.utility.CVaRUtilityFunction;
import com.bcfinancial.utility.ConvexLossUtilityFunction;
import com.bcfinancial.utility.OmegaRatioUtilityFunction;
import com.bcfinancial.utility.SortinoRatioUtilityFunction;
import com.bcfinancial.utility.UtilityFunction;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Single service layer owning all business operations.
 * Persistence is handled by Hibernate 6 + HikariCP via {@link PersistenceConfig}.
 */
public class AppService implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(AppService.class.getName());

    private final SessionFactory sf;
    private final PortfolioParallelExecutor executor;

    public AppService() {
        sf       = PersistenceConfig.buildSessionFactory();
        executor = new PortfolioParallelExecutor();
    }

    // ------------------------------------------------------------------
    // Helper — execute work inside a transaction, roll back on failure
    // ------------------------------------------------------------------

    private <T> T transact(Function<Session, T> work) {
        try (Session s = sf.openSession()) {
            Transaction tx = s.beginTransaction();
            try {
                T result = work.apply(s);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                tx.rollback();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    public User login(String username, String password) throws Exception {
        User user = transact(s -> s.get(User.class, username));
        if (user == null)                         throw new Exception("User not found: " + username);
        if (!user.getPassword().equals(password)) throw new Exception("Invalid credentials");
        return user;
    }

    public User register(String username, String password, String email,
                         double alpha, double beta, double lambda) throws Exception {
        User existing = transact(s -> s.get(User.class, username));
        if (existing != null) throw new Exception("Username already exists: " + username);
        User user = new User(username, password, email, new String[0], alpha, beta, lambda);
        transact(s -> { s.persist(user); return null; });
        return transact(s -> s.get(User.class, username));
    }

    // ------------------------------------------------------------------
    // Portfolios
    // ------------------------------------------------------------------

    public Portfolio getPortfolio(String username, String portfolioName) throws Exception {
        String key = username + "|" + portfolioName;
        Portfolio p = transact(s -> s.get(Portfolio.class, key));
        if (p != null) p.setPortfolioName(portfolioName);
        return p;
    }

    public Portfolio storePortfolio(String username, Portfolio portfolio) throws Exception {
        String originalName = portfolio.getPortfolioName();
        String key          = username + "|" + originalName;
        portfolio.setPortfolioName(key);

        transact(s -> {
            Portfolio existing = s.get(Portfolio.class, key);
            if (existing != null) {
                existing.setSymbols(portfolio.getSymbols());
                existing.setWeights(portfolio.getWeights());
            } else {
                s.persist(portfolio);
                // Add portfolio name to user's list if not already present
                User user = s.get(User.class, username);
                if (user != null) {
                    String[] names = user.getPortfolioNames();
                    boolean alreadyListed = false;
                    for (String n : names) { if (originalName.equals(n)) { alreadyListed = true; break; } }
                    if (!alreadyListed) {
                        String[] updated = java.util.Arrays.copyOf(names, names.length + 1);
                        updated[names.length] = originalName;
                        user.setPortfolioNames(updated);
                    }
                }
            }
            return null;
        });

        Portfolio stored = transact(s -> s.get(Portfolio.class, key));
        if (stored != null) stored.setPortfolioName(originalName);
        return stored;
    }

    public User updateUtility(String username, String password, double alpha, double beta, double lambda) throws Exception {
        login(username, password);
        transact(s -> {
            User u = s.get(User.class, username);
            if (u != null) u.setUtilityFunction(new com.bcfinancial.utility.ProspectTheoryUtilityFunction(alpha, beta, lambda));
            return null;
        });
        return transact(s -> s.get(User.class, username));
    }

    /** Updates the user's utility function to ConvexLossUtilityFunction. */
    public User updateUtilityConvexLoss(String username, String password,
                                        double alpha, double beta, double lambda) throws Exception {
        login(username, password);
        transact(s -> {
            User u = s.get(User.class, username);
            if (u != null) u.setUtilityFunction(new ConvexLossUtilityFunction(alpha, beta, lambda));
            return null;
        });
        return transact(s -> s.get(User.class, username));
    }

    /**
     * Updates the user's utility function to one of the non-ProspectTheory types.
     *
     * @param type one of: "sortino", "cvar", "omega"
     * @param rfAnnual annualised risk-free / threshold rate (e.g. 0.04 = 4%)
     */
    public User updateUtilityByType(String username, String password,
                                    String type, double rfAnnual) throws Exception {
        login(username, password);
        transact(s -> {
            User u = s.get(User.class, username);
            if (u != null) {
                UtilityFunction uf = switch (type.toLowerCase()) {
                    case "sortino" -> new SortinoRatioUtilityFunction(rfAnnual);
                    case "cvar"    -> new CVaRUtilityFunction(rfAnnual);
                    case "omega"   -> new OmegaRatioUtilityFunction(rfAnnual);
                    default        -> throw new RuntimeException("Unknown utility type: " + type);
                };
                u.setUtilityFunction(uf);
            }
            return null;
        });
        return transact(s -> s.get(User.class, username));
    }

    // ------------------------------------------------------------------
    // Portfolio listing
    // ------------------------------------------------------------------

    /** Returns portfolio names for a user by querying the portfolios table directly. */
    public List<String> getPortfolioNames(String username) {
        String prefix = username + "|";
        List<com.bcfinancial.portfolio.Portfolio> ps = transact(s ->
            s.createQuery("FROM Portfolio WHERE portfolioName LIKE :prefix",
                          com.bcfinancial.portfolio.Portfolio.class)
             .setParameter("prefix", prefix + "%")
             .list());
        return ps.stream()
                 .map(p -> p.getPortfolioName().substring(prefix.length()))
                 .collect(java.util.stream.Collectors.toList());
    }

    public void deletePortfolio(String username, String portfolioName) throws Exception {
        String key = username + "|" + portfolioName;
        transact(s -> {
            Portfolio p = s.get(Portfolio.class, key);
            if (p != null) s.remove(p);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Equity metadata
    // ------------------------------------------------------------------

    /**
     * Returns the earliest "latest date" across all symbols — i.e. the most
     * recent date for which data is available in every symbol's table.
     * Returns null if any symbol has no data.
     */
    public Date getLatestCommonDate(List<String> symbols) throws Exception {
        EquityRepository repo = new EquityRepository();
        Date earliest = null;
        for (String symbol : symbols) {
            Date d = repo.findLatestDate(symbol);
            if (d == null) return null;
            if (earliest == null || d.before(earliest)) earliest = d;
        }
        return earliest;
    }

    // ------------------------------------------------------------------
    // Equity loading
    // ------------------------------------------------------------------

    /** Returns {successCount, failCount}. */
    public int[] loadEquity(List<String> symbols, int days, boolean full) {
        EquityDataLoader loader = new EquityDataLoader();
        Calendar from = Calendar.getInstance();
        if (full) {
            from.add(Calendar.YEAR, -5);
        } else {
            from.add(Calendar.DATE, -days);
        }
        Calendar to = Calendar.getInstance();

        int success = 0, failed = 0;
        for (String symbol : symbols) {
            try {
                int rows = loader.loadSymbol(symbol, from, to);
                logger.info(String.format("Loaded %d rows for %s", rows, symbol));
                success++;
            } catch (Exception e) {
                logger.warning("Failed to load " + symbol + ": " + e.getMessage());
                failed++;
            }
        }
        return new int[]{success, failed};
    }

    // ------------------------------------------------------------------
    // Synthetic equity
    // ------------------------------------------------------------------

    /**
     * Creates a synthetic equity by computing the weighted daily return series
     * for the given holdings, then storing the resulting price index (starting
     * at 100) as a new ticker in the equity database.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Fetch {@code adj_close} history for each constituent.</li>
     *   <li>Find dates on which ALL constituents have data.</li>
     *   <li>For each consecutive pair of dates compute the weighted daily
     *       portfolio return: {@code Σ w_i × (p_i(t)/p_i(t-1) − 1)}.</li>
     *   <li>Back-compute a price index: {@code P(t) = P(t-1) × (1 + r(t))},
     *       with P(first date) = 100.</li>
     *   <li>Upsert all rows via {@link EquityRepository#saveAll}.</li>
     * </ol>
     *
     * @param symbol    ticker for the new synthetic instrument (e.g. "FUND-Technology")
     * @param holdings  map of constituent ticker → weight; auto-normalised to sum 1
     * @param startDate earliest date to fetch (null = 10 years back)
     * @param endDate   latest date to fetch (null = today)
     * @return number of price rows stored
     */
    public int createSyntheticEquity(String symbol,
                                     Map<String, Double> holdings,
                                     Date startDate, Date endDate) throws Exception {
        if (holdings == null || holdings.isEmpty())
            throw new Exception("holdings must not be empty");

        // Normalise weights so they sum to 1.0
        double totalWeight = holdings.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0) throw new Exception("Holdings weights must be positive");
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : holdings.entrySet())
            weights.put(e.getKey(), e.getValue() / totalWeight);

        // Default date range
        if (endDate == null) endDate = new Date();
        if (startDate == null) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.YEAR, -10);
            startDate = c.getTime();
        }

        // Fetch price history for each constituent
        EquityRepository repo = new EquityRepository();
        Map<String, Map<Long, Double>> priceByTicker = new LinkedHashMap<>();
        for (String ticker : weights.keySet()) {
            List<Equity> rows = repo.findBySymbolBetween(ticker, startDate, endDate);
            if (rows.isEmpty()) {
                logger.warning("No data for constituent " + ticker + " — skipping");
                continue;
            }
            Map<Long, Double> byDate = new LinkedHashMap<>();
            for (Equity eq : rows) byDate.put(eq.getDate().getTime(), eq.getAdjClose());
            priceByTicker.put(ticker, byDate);
        }
        if (priceByTicker.isEmpty())
            throw new Exception("No price data found for any constituent of " + symbol);

        // Restrict to dates where every retained constituent has data
        Set<Long> commonDates = null;
        for (Map<Long, Double> byDate : priceByTicker.values()) {
            if (commonDates == null) commonDates = new HashSet<>(byDate.keySet());
            else commonDates.retainAll(byDate.keySet());
        }
        if (commonDates == null || commonDates.size() < 2)
            throw new Exception("Fewer than 2 common trading dates across constituents of " + symbol);

        List<Long> sortedTs = new ArrayList<>(commonDates);
        Collections.sort(sortedTs);

        // Build synthetic price index
        List<Equity> synthRows = new ArrayList<>(sortedTs.size());
        double price = 100.0;
        // Emit the anchor row (day 0) at price = 100
        synthRows.add(syntheticRow(symbol, new Date(sortedTs.get(0)), price));

        for (int i = 1; i < sortedTs.size(); i++) {
            long prevTs = sortedTs.get(i - 1);
            long curTs  = sortedTs.get(i);

            double weightedReturn = 0.0;
            double activeWeight   = 0.0;
            for (Map.Entry<String, Double> we : weights.entrySet()) {
                Map<Long, Double> byDate = priceByTicker.get(we.getKey());
                if (byDate == null) continue;
                Double prev = byDate.get(prevTs);
                Double cur  = byDate.get(curTs);
                if (prev != null && cur != null && prev > 0) {
                    weightedReturn += we.getValue() * (cur / prev - 1.0);
                    activeWeight   += we.getValue();
                }
            }
            // If a constituent is missing on this specific date, scale up the others
            if (activeWeight > 0 && activeWeight < 0.9999)
                weightedReturn /= activeWeight;

            price *= (1.0 + weightedReturn);
            synthRows.add(syntheticRow(symbol, new Date(curTs), price));
        }

        repo.createTableIfNotExists(symbol);
        repo.saveAll(symbol, synthRows);

        logger.info(String.format("Synthetic equity '%s' created: %d rows, %d constituents",
                symbol, synthRows.size(), priceByTicker.size()));
        return synthRows.size();
    }

    private static Equity syntheticRow(String symbol, Date date, double price) {
        return new Equity(symbol, date, price, price, price, price, price, 0L);
    }

    // ------------------------------------------------------------------
    // Optimization
    // ------------------------------------------------------------------

    /** Returns the score used for optimization based on the chosen objective. */
    private double score(PortfolioCalculatedData pcd, String optimizeBy) {
        return "sharpe".equalsIgnoreCase(optimizeBy) ? pcd.getSharpeRatio() : pcd.getWeightedUtility();
    }

    /**
     * Result of one hill-climbing run from a single starting portfolio.
     *
     * @param startData     metrics for the starting portfolio (the run's baseline)
     * @param epochResults  sequence of strictly-improving epoch results
     * @param epochLog      per-epoch debug data (improvements + annealing events)
     * @param finalScore    score at the end of the run
     * @param stoppedReason why the run ended: null = max epochs, "maxWeightConstraint",
     *                      "noImprovement", "belowMinImprovement"
     */
    private record SingleStartResult(
            PortfolioCalculatedData startData,
            List<PortfolioCalculatedData> epochResults,
            List<Map<String, Object>> epochLog,
            double finalScore,
            String stoppedReason) {}

    /** Publicly visible optimization result including the stop reason. */
    public record OptimizeResult(PortfolioOptimization optimization, String stoppedReason) {}

    /**
     * Runs portfolio optimization with momentum-based step sizing, simulated
     * annealing, and optional multi-start.
     *
     * <p><b>Momentum</b>: the weight step grows with consecutive improvements
     * ({@code step = 0.01 * min(streak + 1, 5)}) and resets on any non-strict
     * improvement epoch.
     *
     * <p><b>Simulated annealing</b>: when no strict improvement is found the
     * optimiser may still accept the best variant with probability
     * {@code exp(delta / T)}, where {@code T} decays each epoch.  This allows
     * the search to escape local maxima.  Annealed moves do not appear in the
     * epoch-results list (only strict improvements are shown to the user).
     *
     * <p><b>Multi-start</b>: the full epoch loop is run from {@code multiStartCount}
     * starting portfolios — first the user's saved portfolio, then
     * {@code multiStartCount - 1} random portfolios.  The best final score
     * across all starts is kept.
     *
     * @param annealingTemperature initial annealing temperature T0; set to 0 to disable
     * @param annealingDecay       per-epoch decay factor applied to T (e.g. 0.95)
     * @param multiStartCount      number of independent starts; 1 = no multi-start
     */
    public OptimizeResult optimize(User user, String portfolioName,
            Date startDate, Date endDate, int timePeriodDays,
            int maxEpochs, double minImprovementPerEpoch,
            double maxWeightPerInstrument, String optimizeBy,
            double annealingTemperature, double annealingDecay,
            int multiStartCount, double baseStep) throws Exception {

        Portfolio originalPortfolio = getPortfolio(user.getUsername(), portfolioName);
        if (originalPortfolio == null)
            throw new Exception("Portfolio not found: " + user.getUsername() + "|" + portfolioName);

        UtilityFunction uf  = user.getUtilityFunction();
        String optName      = user.getUsername() + "|" + portfolioName + "|" + new Date();

        // --- Debug setup ---
        new File("debug").mkdirs();
        String safe = portfolioName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        String base = "debug/" + ts + "_" + safe;
        String[]  symbols      = originalPortfolio.getSymbols();
        Double[]  startWeights = Arrays.copyOf(originalPortfolio.getWeights(), originalPortfolio.getWeights().length);
        // --- End debug setup ---

        // SP500 benchmark — computed once regardless of multi-start
        PortfolioCalculatedData sp500Data = computeSingle("SP500 Benchmark",
                new Portfolio("SP500", new String[]{"SPY"}, new Double[]{1.0}),
                startDate, endDate, timePeriodDays, uf);

        // Start 0 is always the user's saved portfolio.
        // originalData is fixed to this regardless of which start ends up winning,
        // so the Results "Original" row always reflects the user's actual allocation.
        Random rng = new Random();
        SingleStartResult start0 = runSingleStart(
                originalPortfolio, "Original Portfolio",
                startDate, endDate, timePeriodDays, uf,
                maxEpochs, minImprovementPerEpoch, maxWeightPerInstrument, optimizeBy,
                annealingTemperature, annealingDecay, symbols, baseStep);

        PortfolioCalculatedData originalData = start0.startData();  // always the real original
        SingleStartResult bestResult = start0;

        for (int k = 1; k < multiStartCount; k++) {
            Portfolio randomStart = randomPortfolio(symbols, rng);
            logger.info(String.format("Multi-start %d/%d from random portfolio", k + 1, multiStartCount));
            SingleStartResult candidate = runSingleStart(
                    randomStart, "MultiStart " + k,
                    startDate, endDate, timePeriodDays, uf,
                    maxEpochs, minImprovementPerEpoch, maxWeightPerInstrument, optimizeBy,
                    annealingTemperature, annealingDecay, symbols, baseStep);
            if (candidate.finalScore() > bestResult.finalScore()) {
                logger.info(String.format("Multi-start %d improved best score: %.4f → %.4f",
                        k + 1, bestResult.finalScore(), candidate.finalScore()));
                bestResult = candidate;
            }
        }
        List<PortfolioCalculatedData> epochResults = bestResult.epochResults();
        List<Map<String, Object>> epochLog = bestResult.epochLog();
        double lastScore = bestResult.finalScore();

        // --- Write debug files ---
        double initialScore = score(originalData, optimizeBy);
        try {
            writeDebugText(base + ".txt", portfolioName, symbols, startWeights,
                           initialScore, lastScore, epochLog);
            writeDebugJson(base + ".json", portfolioName, symbols, startWeights,
                           initialScore, lastScore, optimizeBy, epochLog);
            logger.info("Debug files written to " + base + ".{txt,json}");
        } catch (Exception e) {
            logger.warning("Could not write debug files: " + e.getMessage());
        }
        // --- End debug ---

        int n = epochResults.size();
        Portfolio[] portfolioArray               = new Portfolio[2 + n];
        double[][] returnsArray                  = new double[2 + n][];
        PortfolioCalculatedData[] optimizedArray = epochResults.toArray(new PortfolioCalculatedData[0]);

        portfolioArray[0] = sp500Data.getPortfolioCalculationRequest().getPortfolio();
        portfolioArray[1] = originalData.getPortfolioCalculationRequest().getPortfolio();
        returnsArray[0]   = sp500Data.getSortedReturns();
        returnsArray[1]   = originalData.getSortedReturns();
        for (int i = 0; i < n; i++) {
            portfolioArray[i + 2] = epochResults.get(i).getPortfolioCalculationRequest().getPortfolio();
            returnsArray[i + 2]   = epochResults.get(i).getSortedReturns();
        }

        PortfolioOptimization result = new PortfolioOptimization(
                optName, "Finished, " + new Date(),
                sp500Data, originalData, optimizedArray, portfolioArray, returnsArray);

        try {
            transact(s -> { s.persist(result); return null; });
        } catch (Exception e) {
            logger.warning("Could not persist optimization result: " + e.getMessage());
        }

        return new OptimizeResult(result, bestResult.stoppedReason());
    }

    /**
     * Runs one hill-climbing epoch loop from {@code startPortfolio}.
     *
     * <p>Momentum and simulated annealing are applied as described in
     * {@link #optimize}.
     *
     * @param allSymbols the canonical symbol list used for debug logging
     *                   (always the original portfolio's symbols)
     */
    private SingleStartResult runSingleStart(
            Portfolio startPortfolio, String startLabel,
            Date startDate, Date endDate, int timePeriodDays,
            UtilityFunction uf,
            int maxEpochs, double minImprovementPerEpoch,
            double maxWeightPerInstrument, String optimizeBy,
            double annealingTemperature, double annealingDecay,
            String[] allSymbols, double baseStep) throws Exception {

        PortfolioCalculatedData startData = computeSingle(
                startLabel, startPortfolio, startDate, endDate, timePeriodDays, uf);

        Portfolio portfolio  = startPortfolio;
        double lastScore     = score(startData, optimizeBy);
        int epochStreak      = 0;
        String stoppedReason = null;
        List<PortfolioCalculatedData> epochResults = new ArrayList<>();
        List<Map<String, Object>>     epochLog     = new ArrayList<>();
        Random rng = new Random();

        for (int epoch = 0; epoch < maxEpochs; epoch++) {
            // Momentum: grow step with consecutive improvements, capped at 5×
            double step        = baseStep * Math.min(epochStreak + 1, 5);
            double temperature = annealingTemperature * Math.pow(annealingDecay, epoch);

            Double[] epochStartWeights = Arrays.copyOf(portfolio.getWeights(), portfolio.getWeights().length);
            double   epochStartScore   = lastScore;

            List<PortfolioCalculatedData> variants =
                    executor.calculateVariants(portfolio, startDate, endDate, timePeriodDays, uf,
                            maxWeightPerInstrument, step);

            for (PortfolioCalculatedData v : variants) {
                if (v.getException() != null) throw v.getException();
            }

            // Build epoch debug entry
            Map<String, Object> epochData = new LinkedHashMap<>();
            epochData.put("epoch",        epoch);
            epochData.put("startWeights", weightsToMap(allSymbols, epochStartWeights));
            epochData.put("startUtility", epochStartScore);
            epochData.put("optimizeBy",   optimizeBy);
            epochData.put("step",         step);
            epochData.put("epochStreak",  epochStreak);
            epochData.put("temperature",  temperature);
            epochData.put("feasibleCount", variants.size());
            List<Map<String, Object>> variantList = new ArrayList<>();
            for (PortfolioCalculatedData v : variants) {
                Map<String, Object> vd = new LinkedHashMap<>();
                vd.put("symbol",  v.getPortfolioCalculationRequest().getPortfolioName());
                vd.put("weights", weightsToMap(allSymbols, v.getPortfolioCalculationRequest().getPortfolio().getWeights()));
                vd.put("utility", v.getWeightedUtility());
                vd.put("sharpe",  v.getSharpeRatio());
                vd.put("score",   score(v, optimizeBy));
                vd.put("delta",   score(v, optimizeBy) - epochStartScore);
                variantList.add(vd);
            }
            epochData.put("variants", variantList);

            if (variants.isEmpty()) {
                // With proportional reduction, variants are only empty when every
                // instrument is already at the cap.  A single uncapped instrument
                // always produces a feasible variant.
                stoppedReason = "maxWeightConstraint";
                epochData.put("stopped", stoppedReason);
                epochLog.add(epochData);
                break;
            }

            PortfolioCalculatedData best = variants.stream()
                    .max(Comparator.comparingDouble(v -> score(v, optimizeBy)))
                    .get();

            double improvement = score(best, optimizeBy) - lastScore;

            if (improvement > minImprovementPerEpoch) {
                // Strict improvement — accept, update streak, record in results
                epochStreak++;
                lastScore = score(best, optimizeBy);
                best.getPortfolioCalculationRequest().setPortfolioName("Epoch " + epoch);
                epochResults.add(best);
                portfolio = best.getPortfolioCalculationRequest().getPortfolio();

                epochData.put("winner",      best.getPortfolioCalculationRequest().getPortfolioName());
                epochData.put("endWeights",  weightsToMap(allSymbols, portfolio.getWeights()));
                epochData.put("endUtility",  lastScore);
                epochData.put("improvement", improvement);
                epochLog.add(epochData);

                logger.info(String.format("Epoch %d  %s=%.4f  CAGR=%.4f  step=%.4f  streak=%d",
                        epoch, optimizeBy, lastScore, best.getAverageCAGR(), step, epochStreak));

            } else if (annealingTemperature > 0 && temperature > 0) {
                // No strict improvement — try annealing acceptance
                double acceptProb = Math.exp(improvement / temperature);
                if (rng.nextDouble() < acceptProb) {
                    // Accepted by annealing: move but do NOT add to epochResults
                    epochStreak = 0;
                    lastScore   = score(best, optimizeBy);
                    portfolio   = best.getPortfolioCalculationRequest().getPortfolio();

                    epochData.put("annealed",    true);
                    epochData.put("acceptProb",  acceptProb);
                    epochData.put("improvement", improvement);
                    epochData.put("endWeights",  weightsToMap(allSymbols, portfolio.getWeights()));
                    epochLog.add(epochData);

                    logger.info(String.format("Epoch %d  ANNEALED  Δ=%.4f  p=%.4f  T=%.4f",
                            epoch, improvement, acceptProb, temperature));
                } else {
                    // Rejected — stop
                    stoppedReason = improvement <= 0 ? "noImprovement" : "belowMinImprovement";
                    epochData.put("stopped",     improvement <= 0 ? "no_improvement" : "below_min_improvement");
                    epochData.put("bestSymbol",  best.getPortfolioCalculationRequest().getPortfolioName());
                    epochData.put("bestUtility", score(best, optimizeBy));
                    epochData.put("improvement", improvement);
                    epochLog.add(epochData);
                    break;
                }
            } else {
                // No improvement, annealing disabled — stop
                stoppedReason = improvement <= 0 ? "noImprovement" : "belowMinImprovement";
                epochData.put("stopped",     improvement <= 0 ? "no_improvement" : "below_min_improvement");
                epochData.put("bestSymbol",  best.getPortfolioCalculationRequest().getPortfolioName());
                epochData.put("bestUtility", score(best, optimizeBy));
                epochData.put("improvement", improvement);
                epochLog.add(epochData);
                break;
            }
        }

        return new SingleStartResult(startData, epochResults, epochLog, lastScore, stoppedReason);
    }

    /** Generates a random portfolio with the given symbols (weights sum to 1, all > 0). */
    private Portfolio randomPortfolio(String[] symbols, Random rng) {
        int n = symbols.length;
        double[] raw = new double[n];
        double sum = 0.0;
        for (int i = 0; i < n; i++) { raw[i] = rng.nextDouble() + 0.01; sum += raw[i]; }
        Double[] weights = new Double[n];
        for (int i = 0; i < n; i++) weights[i] = raw[i] / sum;
        return new Portfolio("MultiStart", symbols, weights);
    }

    // -----------------------------------------------------------------------
    // Debug helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> weightsToMap(String[] symbols, Double[] weights) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < symbols.length; i++)
            m.put(symbols[i], Math.round(weights[i] * 1e6) / 1e6);
        return m;
    }

    private void writeDebugText(String path, String portfolioName, String[] symbols,
                                Double[] startWeights, double initialUtility, double finalUtility,
                                List<Map<String, Object>> epochs) throws IOException {
        String div = "─".repeat(72);
        try (PrintWriter w = new PrintWriter(new FileWriter(path))) {
            w.println("=== OPTIMIZATION DEBUG: " + portfolioName + " ===");
            w.println("Time:   " + new Date());
            w.print  ("Start:  "); w.println(fmtWeights(symbols, startWeights));
            w.printf ("        utility = %.6f%n", initialUtility);
            w.println();

            for (Map<String, Object> ep : epochs) {
                w.println(div);
                int    epochNum = (int)    ep.get("epoch");
                double su       = (double) ep.get("startUtility");
                double step     = ep.containsKey("step")        ? (double) ep.get("step")        : 0.01;
                int    streak   = ep.containsKey("epochStreak") ? (int)    ep.get("epochStreak") : 0;
                double temp     = ep.containsKey("temperature") ? (double) ep.get("temperature") : 0.0;

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> variants = (List<Map<String, Object>>) ep.get("variants");

                w.printf("Epoch %d  [startUtility=%.6f  feasible=%s  step=%.4f  streak=%d  T=%.4f]%n",
                         epochNum, su, ep.get("feasibleCount"), step, streak, temp);

                for (Map<String, Object> v : variants) {
                    String sym = (String) v.get("symbol");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> wm = (Map<String, Object>) v.get("weights");
                    double util  = (double) v.get("utility");
                    double delta = (double) v.get("delta");
                    w.printf("  %s+%.0f%%  %s  util=%.6f  Δ=%+.6f%n",
                             sym, step * 100, fmtWeightMap(symbols, wm), util, delta);
                }

                if (Boolean.TRUE.equals(ep.get("annealed"))) {
                    w.printf("  ~ ANNEALED: improvement=%+.6f  acceptProb=%.4f%n",
                             ep.get("improvement"), ep.get("acceptProb"));
                } else if (ep.containsKey("winner")) {
                    w.printf("  ★ WINNER: %s  improvement=%+.6f%n",
                             ep.get("winner"), ep.get("improvement"));
                } else {
                    w.printf("  ✗ STOPPED: %s%n", ep.get("stopped"));
                }
            }

            w.println(div);
            w.printf("FINAL: utility=%.6f  (gain=%+.6f  epochs=%d)%n",
                     finalUtility, finalUtility - initialUtility, epochs.size());
        }
    }

    private void writeDebugJson(String path, String portfolioName, String[] symbols,
                                Double[] startWeights, double initialScore, double finalScore,
                                String optimizeBy, List<Map<String, Object>> epochs) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("portfolioName",   portfolioName);
        root.put("symbols",         symbols);
        root.put("startWeights",    weightsToMap(symbols, startWeights));
        root.put("optimizeBy",      optimizeBy);
        root.put("initialUtility",  initialScore);
        root.put("epochs",          epochs);
        root.put("finalUtility",    finalScore);
        root.put("totalGain",       finalScore - initialScore);
        root.put("epochCount",      epochs.size());

        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(new File(path), root);
    }

    private String fmtWeights(String[] symbols, Double[] weights) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < symbols.length; i++) {
            if (i > 0) sb.append("  ");
            sb.append(String.format("%s=%5.1f%%", symbols[i], weights[i] * 100));
        }
        return sb.toString();
    }

    private String fmtWeightMap(String[] symbols, Map<String, Object> wm) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < symbols.length; i++) {
            if (i > 0) sb.append("  ");
            double w = ((Number) wm.get(symbols[i])).doubleValue();
            sb.append(String.format("%s=%5.1f%%", symbols[i], w * 100));
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------

    private PortfolioCalculatedData computeSingle(String name, Portfolio p,
            Date start, Date end, int days, UtilityFunction uf) {
        return executor.calculate(name, p, start, end, days, uf);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void close() {
        executor.close();
        if (sf != null && sf.isOpen()) sf.close();
    }
}
