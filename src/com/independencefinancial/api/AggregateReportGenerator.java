package com.bcfinancial.api;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.*;

/**
 * Generates an aggregate PDF report from multiple completed optimization jobs.
 *
 * <p>Structure:
 * <ol>
 *   <li>Summary page — approach, parameters, high-level statistics</li>
 *   <li>ΔCAGR grid — portfolio × method heat-map</li>
 *   <li>ΔSharpe grid — same layout</li>
 *   <li>Unexpected findings — programmatic analysis</li>
 *   <li>Individual optimization reports (55 × PdfReportGenerator output)</li>
 * </ol>
 */
public class AggregateReportGenerator {

    // ── colours ──────────────────────────────────────────────────────────────
    private static final Color NAVY      = new Color(0x1a, 0x33, 0x5c);
    private static final Color DARK_GREY = new Color(0x44, 0x44, 0x44);
    private static final Color MID_GREY  = new Color(0x88, 0x88, 0x88);
    private static final Color LIGHT_BG  = new Color(0xf5, 0xf5, 0xf5);
    private static final Color CELL_ALT  = new Color(0xea, 0xf1, 0xfb);

    private static final Color GAIN_DARK  = new Color(0x1b, 0x7a, 0x3e);
    private static final Color GAIN_MID   = new Color(0x60, 0xb8, 0x77);
    private static final Color GAIN_LIGHT = new Color(0xc6, 0xef, 0xce);
    private static final Color LOSS_LIGHT = new Color(0xfc, 0xd5, 0xd0);
    private static final Color LOSS_MID   = new Color(0xe0, 0x70, 0x60);
    private static final Color LOSS_DARK  = new Color(0x9c, 0x27, 0x27);

    // ── fonts ────────────────────────────────────────────────────────────────
    private static Font titleFont()    { return FontFactory.getFont(FontFactory.HELVETICA_BOLD,  20, NAVY); }
    private static Font headFont()     { return FontFactory.getFont(FontFactory.HELVETICA_BOLD,  12, NAVY); }
    private static Font subheadFont()  { return FontFactory.getFont(FontFactory.HELVETICA_BOLD,  10, DARK_GREY); }
    private static Font bodyFont()     { return FontFactory.getFont(FontFactory.HELVETICA,        9, DARK_GREY); }
    private static Font smallFont()    { return FontFactory.getFont(FontFactory.HELVETICA,        8, DARK_GREY); }
    private static Font tinyFont()     { return FontFactory.getFont(FontFactory.HELVETICA,        7, MID_GREY); }
    private static Font tableHead()    { return FontFactory.getFont(FontFactory.HELVETICA_BOLD,   8, Color.WHITE); }
    private static Font tableCell()    { return FontFactory.getFont(FontFactory.HELVETICA,        8, DARK_GREY); }
    private static Font tableCellBold(){ return FontFactory.getFont(FontFactory.HELVETICA_BOLD,   8, DARK_GREY); }

    // ── method ordering ──────────────────────────────────────────────────────
    private static final java.util.List<String> METHOD_KEYS = java.util.List.of(
            "powerLog", "sortino", "omega", "convexLoss", "sharpe");
    private static final Map<String, String> METHOD_LABELS = Map.of(
            "powerLog",   "Prospect Theory",
            "sortino",    "Sortino",
            "omega",      "Omega",
            "convexLoss", "Convex Loss",
            "sharpe",     "Sharpe");

    // ── data structure for a single run ──────────────────────────────────────
    private record Run(
            String portfolio, String method,
            double origCagr,  double origSharpe,
            double finalCagr, double finalSharpe, double finalUtility,
            double sp500Cagr, double sp500Sharpe,
            int epochCount, String stoppedReason,
            String[] symbols, Double[] finalWeights) {

        double cagrDelta()   { return finalCagr   - origCagr;   }
        double sharpeDelta() { return finalSharpe  - origSharpe; }
        boolean improved()   { return cagrDelta() > 0.001; }
        boolean beatsSP500() { return finalCagr > sp500Cagr; }
        boolean origBeatsSP500() { return origCagr > sp500Cagr; }

        double maxWeight() {
            if (finalWeights == null || finalWeights.length == 0) return 0;
            return Arrays.stream(finalWeights).mapToDouble(Double::doubleValue).max().orElse(0);
        }
        String maxWeightSymbol() {
            if (symbols == null || finalWeights == null) return "?";
            int idx = 0;
            for (int i = 1; i < finalWeights.length; i++)
                if (finalWeights[i] > finalWeights[idx]) idx = i;
            return symbols[idx];
        }
        String shortPortfolio() {
            return portfolio.replace("Sector-", "");
        }
    }

    // =========================================================================
    // Public entry point
    // =========================================================================

    /**
     * @param jobResults  list of completed job result maps (in any order)
     * @param ufLabels    map from method key to display label (e.g. "powerLog" → "Prospect Theory (K-T)")
     */
    public static byte[] generate(java.util.List<Map<String, Object>> jobResults,
                                   Map<String, String> ufLabels) throws Exception {

        java.util.List<Run> runs = parseRuns(jobResults);

        // ── 1. Build the summary/analysis pages (landscape) ──────────────────
        byte[] summaryPdf = buildSummaryPages(runs, jobResults.size());

        // ── 2. Build individual report pages (portrait) ──────────────────────
        //   Sort: group by portfolio, within portfolio sort by method order
        java.util.List<Map<String, Object>> sorted = jobResults.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> m) -> str(m, "portfolioName"))
                        .thenComparingInt(m -> METHOD_KEYS.indexOf(str(m, "optimizeBy"))))
                .collect(Collectors.toList());

        java.util.List<byte[]> individualPdfs = new ArrayList<>();
        for (Map<String, Object> r : sorted) {
            String pName  = str(r, "portfolioName");
            String method = str(r, "optimizeBy");
            String label  = ufLabels.getOrDefault(method, method);
            try {
                individualPdfs.add(PdfReportGenerator.generate(r, pName, label));
            } catch (Exception e) {
                // skip failed reports silently — they will be missing from the output
            }
        }

        // ── 3. Merge all PDFs ─────────────────────────────────────────────────
        return mergePdfs(summaryPdf, individualPdfs);
    }

    // =========================================================================
    // Summary + analysis pages
    // =========================================================================

    private static byte[] buildSummaryPages(java.util.List<Run> runs, int totalJobs) throws Exception {
        Document doc = new Document(PageSize.A4.rotate()); // landscape for tables
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.setMargins(40, 40, 36, 36);
        doc.open();

        addSummaryPage(doc, runs, totalJobs);
        doc.newPage();
        addDeltaTable(doc, runs, true);   // ΔCAGR
        doc.newPage();
        addDeltaTable(doc, runs, false);  // ΔSharpe
        doc.newPage();
        addFindingsPage(doc, runs);

        doc.close();
        return baos.toByteArray();
    }

    // ── page 1: overview ─────────────────────────────────────────────────────
    private static void addSummaryPage(Document doc, java.util.List<Run> runs, int totalJobs)
            throws Exception {

        // Title block
        Paragraph title = new Paragraph("Sector Portfolio Optimization Study", titleFont());
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(4);
        doc.add(title);

        String date = new SimpleDateFormat("MMMM d, yyyy").format(new Date());
        Paragraph sub = new Paragraph("Generated " + date + "  ·  " + runs.size() +
                " of " + totalJobs + " runs completed", bodyFont());
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(18);
        doc.add(sub);

        // Approach paragraph
        doc.add(sectionHead("Approach"));
        doc.add(body(
            "Eleven SPDR Select Sector ETF portfolios, each comprising the top-10 holdings " +
            "by market-capitalisation weight as of April 9 2026, were optimised across five " +
            "utility-function frameworks: Prospect Theory (Kahneman-Tversky), Sortino Ratio, " +
            "Omega Ratio, Convex Loss Utility, and Sharpe Ratio.  Starting allocations mirror " +
            "the index's own market-cap weights.  The hill-climbing optimiser with simulated " +
            "annealing explored single-instrument weight increments of 1 % per epoch, " +
            "redistributing weight only across instruments with positive current allocation.  " +
            "Historical returns were computed on a rolling 45-day window over a 5-year lookback " +
            "period, with outliers trimmed at the 1st and 99th percentiles before scoring.  " +
            "The SP&500 ETF (SPY) serves as the benchmark throughout."));

        // Parameters table
        doc.add(sectionHead("Parameters"));
        doc.add(paramTable());

        // High-level stats
        doc.add(sectionHead("Results at a Glance"));
        doc.add(statsTable(runs));
    }

    private static PdfPTable paramTable() throws Exception {
        PdfPTable t = new PdfPTable(new float[]{140, 120, 140, 120, 140, 120});
        t.setWidthPercentage(85);
        t.setSpacingAfter(12);
        String[][] rows = {
            {"Duration", "5 years",        "Rolling window", "45 days",     "Max epochs", "300"},
            {"Step size", "1% per epoch",  "Max weight",     "90%",         "Annealing T", "0.05 / 0.95 decay"},
            {"Outlier trim", "1%/99%",     "Multi-start",    "1",           "Min Δ utility", "0.000001"},
        };
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                PdfPCell c = new PdfPCell(new Phrase(row[i], i % 2 == 0 ? subheadFont() : tableCell()));
                c.setBorder(0);
                c.setBackgroundColor(i % 2 == 0 ? LIGHT_BG : Color.WHITE);
                c.setPadding(4);
                t.addCell(c);
            }
        }
        return t;
    }

    private static PdfPTable statsTable(java.util.List<Run> runs) throws Exception {
        long improved     = runs.stream().filter(Run::improved).count();
        long beatsSP500   = runs.stream().filter(Run::beatsSP500).count();
        long origBeats    = runs.stream().filter(Run::origBeatsSP500).count();

        OptionalDouble bestDelta = runs.stream().mapToDouble(Run::cagrDelta).max();
        Run bestRun = runs.stream().max(Comparator.comparingDouble(Run::cagrDelta)).orElse(null);
        Run worstRun = runs.stream().min(Comparator.comparingDouble(Run::cagrDelta)).orElse(null);

        // Best method by avg ΔCAGR
        Map<String, Double> methodAvg = new LinkedHashMap<>();
        for (String mk : METHOD_KEYS) {
            OptionalDouble avg = runs.stream()
                    .filter(r -> r.method().equals(mk))
                    .mapToDouble(Run::cagrDelta).average();
            if (avg.isPresent()) methodAvg.put(mk, avg.getAsDouble());
        }
        String bestMethod = methodAvg.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> METHOD_LABELS.getOrDefault(e.getKey(), e.getKey()))
                .orElse("n/a");

        PdfPTable t = new PdfPTable(new float[]{155, 180, 155, 180, 155, 180});
        t.setWidthPercentage(100);
        t.setSpacingAfter(8);
        Object[][] rows = {
            {"Runs improved CAGR",    improved + " / " + runs.size(),
             "Beat SP500 (post-opt)", beatsSP500 + " / " + runs.size(),
             "Beat SP500 (original)", origBeats  + " / " + runs.size()},
            {"Best single result",
             bestRun  != null ? bestRun.shortPortfolio()  + "/" + METHOD_LABELS.getOrDefault(bestRun.method(), "?")
                                + " +" + pct(bestDelta.orElse(0)) : "n/a",
             "Worst single result",
             worstRun != null ? worstRun.shortPortfolio() + "/" + METHOD_LABELS.getOrDefault(worstRun.method(), "?")
                                + " "  + pct(worstRun.cagrDelta()) : "n/a",
             "Best method (avg ΔCAGR)", bestMethod},
        };
        for (Object[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                PdfPCell c = new PdfPCell(new Phrase(row[i].toString(), i % 2 == 0 ? subheadFont() : tableCell()));
                c.setBorder(0);
                c.setBackgroundColor(i % 2 == 0 ? LIGHT_BG : Color.WHITE);
                c.setPadding(5);
                t.addCell(c);
            }
        }
        return t;
    }

    // ── pages 2-3: delta tables ───────────────────────────────────────────────
    private static void addDeltaTable(Document doc, java.util.List<Run> runs, boolean isCagr)
            throws Exception {

        String metric = isCagr ? "CAGR" : "Sharpe Ratio";
        Paragraph heading = new Paragraph(
                "Δ " + metric + " After Optimisation  (optimised − original)",
                headFont());
        heading.setSpacingAfter(8);
        doc.add(heading);
        doc.add(new Paragraph(
                "Green = improvement · Red = regression · — = not available", tinyFont()));

        // Collect unique portfolio names in sorted order
        java.util.List<String> portfolios = runs.stream()
                .map(Run::portfolio).distinct().sorted().collect(Collectors.toList());

        // Build lookup: (portfolio, method) → Run
        Map<String, Run> lookup = new HashMap<>();
        for (Run r : runs) lookup.put(r.portfolio() + "|" + r.method(), r);

        // Table: col 0 = portfolio name, cols 1-5 = methods, col 6 = best method
        float[] widths = new float[METHOD_KEYS.size() + 2];
        widths[0] = 120;
        for (int i = 1; i <= METHOD_KEYS.size(); i++) widths[i] = 85;
        widths[widths.length - 1] = 90;

        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        t.setSpacingAfter(10);

        // Header row
        addHeaderCell(t, "Sector");
        for (String mk : METHOD_KEYS) addHeaderCell(t, METHOD_LABELS.get(mk));
        addHeaderCell(t, "Best");

        // Data rows
        boolean alt = false;
        for (String p : portfolios) {
            Color rowBg = alt ? CELL_ALT : Color.WHITE;
            alt = !alt;

            // Portfolio name cell
            PdfPCell nameCell = new PdfPCell(new Phrase(p.replace("Sector-", ""), tableCellBold()));
            nameCell.setBackgroundColor(rowBg);
            nameCell.setPadding(4);
            nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(nameCell);

            // Method delta cells + find best
            String bestMk = null;
            double bestVal = Double.NEGATIVE_INFINITY;
            for (String mk : METHOD_KEYS) {
                Run r = lookup.get(p + "|" + mk);
                if (r != null) {
                    double delta = isCagr ? r.cagrDelta() : r.sharpeDelta();
                    if (delta > bestVal) { bestVal = delta; bestMk = mk; }
                    t.addCell(deltaCell(delta, rowBg, isCagr));
                } else {
                    PdfPCell na = new PdfPCell(new Phrase("—", tinyFont()));
                    na.setBackgroundColor(rowBg);
                    na.setHorizontalAlignment(Element.ALIGN_CENTER);
                    na.setPadding(4);
                    t.addCell(na);
                }
            }

            // Best method cell
            String bestLabel = bestMk != null ? METHOD_LABELS.get(bestMk) : "—";
            PdfPCell best = new PdfPCell(new Phrase(bestLabel, smallFont()));
            best.setBackgroundColor(bestMk != null ? GAIN_LIGHT : rowBg);
            best.setHorizontalAlignment(Element.ALIGN_CENTER);
            best.setVerticalAlignment(Element.ALIGN_MIDDLE);
            best.setPadding(4);
            t.addCell(best);
        }

        doc.add(t);

        // Legend
        doc.add(legend(isCagr));
    }

    private static PdfPCell deltaCell(double delta, Color rowBg, boolean isCagr) {
        String text = isCagr ? pct(delta) : fmt2(delta);
        Color bg;
        double hi = isCagr ? 0.05 : 0.20;
        double lo = isCagr ? 0.01 : 0.05;
        if      (delta >  hi)  bg = GAIN_DARK;
        else if (delta >  lo)  bg = GAIN_MID;
        else if (delta >  0)   bg = GAIN_LIGHT;
        else if (delta > -lo)  bg = LOSS_LIGHT;
        else if (delta > -hi)  bg = LOSS_MID;
        else                   bg = LOSS_DARK;
        boolean dark = (delta > hi || delta < -lo);
        Font f = dark ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)
                      : tableCell();
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        return c;
    }

    private static Paragraph legend(boolean isCagr) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(2);
        p.setSpacingAfter(4);
        if (isCagr) {
            p.add(new Chunk("Dark green >+5%  ", tinyFont()));
            p.add(new Chunk("Mid green +1–5%  ", tinyFont()));
            p.add(new Chunk("Light green 0–1%  ", tinyFont()));
            p.add(new Chunk("Light red 0 to −1%  ", tinyFont()));
            p.add(new Chunk("Mid/dark red < −1%", tinyFont()));
        } else {
            p.add(new Chunk("Dark green >+0.20  ", tinyFont()));
            p.add(new Chunk("Mid green +0.05–0.20  ", tinyFont()));
            p.add(new Chunk("Light green 0–0.05  ", tinyFont()));
            p.add(new Chunk("Light red 0 to −0.05  ", tinyFont()));
            p.add(new Chunk("Mid/dark red < −0.05", tinyFont()));
        }
        return p;
    }

    // ── page 4: findings ─────────────────────────────────────────────────────
    private static void addFindingsPage(Document doc, java.util.List<Run> runs) throws Exception {
        doc.add(sectionHead("Unexpected and Notable Findings"));
        doc.add(new Paragraph(
            "The following observations were identified programmatically from the 55 optimisation " +
            "results.  Findings are flagged when they deviate from what a well-functioning " +
            "hill-climbing optimiser on broadly diversified, market-cap-weighted sector portfolios " +
            "would be expected to produce.", bodyFont()));

        java.util.List<String[]> findings = collectFindings(runs);
        if (findings.isEmpty()) {
            doc.add(body("No anomalies detected across all runs."));
            return;
        }

        // Group findings by category
        Map<String, java.util.List<String[]>> byCategory = findings.stream()
                .collect(Collectors.groupingBy(f -> f[0]));

        for (Map.Entry<String, java.util.List<String[]>> entry : byCategory.entrySet()) {
            doc.add(subhead(entry.getKey()));
            for (String[] f : entry.getValue()) {
                Paragraph item = new Paragraph("• " + f[1], bodyFont());
                item.setIndentationLeft(16);
                item.setSpacingAfter(3);
                doc.add(item);
            }
        }

        // Method performance summary
        doc.add(subhead("Method Performance Summary"));
        doc.add(methodSummaryTable(runs));
    }

    private static java.util.List<String[]> collectFindings(java.util.List<Run> runs) {
        java.util.List<String[]> findings = new ArrayList<>();
        DecimalFormat p = new DecimalFormat("+0.0%;-0.0%");

        // 1. CAGR regressions
        runs.stream()
            .filter(r -> r.cagrDelta() < -0.005)
            .sorted(Comparator.comparingDouble(Run::cagrDelta))
            .forEach(r -> findings.add(new String[]{
                "CAGR Degradation After Optimisation",
                String.format("%s / %s: CAGR moved %s (%.1f%% → %.1f%%).  " +
                    "The optimiser found a local maximum that trades lower compound " +
                    "growth for a better utility score, suggesting these objectives " +
                    "are in tension for this sector.",
                    r.shortPortfolio(), METHOD_LABELS.getOrDefault(r.method(), r.method()),
                    p.format(r.cagrDelta()), r.origCagr()*100, r.finalCagr()*100)
            }));

        // 2. SP500 outperformed even the optimised result when original beat it
        runs.stream()
            .filter(r -> r.origBeatsSP500() && !r.beatsSP500())
            .forEach(r -> findings.add(new String[]{
                "Optimisation Fell Below SP500 Benchmark",
                String.format("%s / %s: original CAGR %.1f%% beat SPY %.1f%%, " +
                    "but optimised result %.1f%% did not.  " +
                    "The utility-function objective may have shifted weight away from " +
                    "the highest-returning holdings.",
                    r.shortPortfolio(), METHOD_LABELS.getOrDefault(r.method(), r.method()),
                    r.origCagr()*100, r.sp500Cagr()*100, r.finalCagr()*100)
            }));

        // 3. Heavy single-stock concentration post-optimisation (> 55%)
        runs.stream()
            .filter(r -> r.maxWeight() > 0.55)
            .sorted(Comparator.comparingDouble(Run::maxWeight).reversed())
            .forEach(r -> findings.add(new String[]{
                "Extreme Post-Optimisation Concentration",
                String.format("%s / %s: %.0f%% allocated to %s after optimisation.  " +
                    "The optimiser drove all weight to this single instrument, " +
                    "indicating it dominated on the chosen metric over the lookback period.",
                    r.shortPortfolio(), METHOD_LABELS.getOrDefault(r.method(), r.method()),
                    r.maxWeight()*100, r.maxWeightSymbol())
            }));

        // 4. No improvement at all (0 epochs)
        runs.stream()
            .filter(r -> r.epochCount() == 0)
            .forEach(r -> findings.add(new String[]{
                "No Improvement Found",
                String.format("%s / %s: the optimiser made zero improvements (stopped: %s).  " +
                    "The original market-cap weighting may already be near-optimal " +
                    "on this metric, or the annealing temperature was insufficient to " +
                    "escape the initial configuration.",
                    r.shortPortfolio(), METHOD_LABELS.getOrDefault(r.method(), r.method()),
                    r.stoppedReason() != null ? r.stoppedReason() : "first epoch")
            }));

        // 5. Consistent method winner/loser
        Map<String, Long> methodWins = new HashMap<>();
        Map<String, String> portfolioWinner = new HashMap<>();
        for (Run r : runs) {
            String p2 = r.portfolio();
            portfolioWinner.merge(p2, r.method(),
                (current, challenger) -> {
                    Run cur = runs.stream()
                            .filter(x -> x.portfolio().equals(p2) && x.method().equals(current))
                            .findFirst().orElse(null);
                    Run chal = runs.stream()
                            .filter(x -> x.portfolio().equals(p2) && x.method().equals(challenger))
                            .findFirst().orElse(null);
                    if (cur == null) return challenger;
                    if (chal == null) return current;
                    return chal.cagrDelta() > cur.cagrDelta() ? challenger : current;
                });
        }
        for (String winner : portfolioWinner.values()) {
            methodWins.merge(winner, 1L, Long::sum);
        }
        methodWins.entrySet().stream()
            .filter(e -> e.getValue() >= 7)
            .forEach(e -> findings.add(new String[]{
                "Dominant Method Across Sectors",
                String.format("%s produced the best CAGR improvement in %d of %d sector portfolios.  " +
                    "This may reflect alignment between this metric and broad market-cap " +
                    "sector dynamics during the study period.",
                    METHOD_LABELS.getOrDefault(e.getKey(), e.getKey()), e.getValue(),
                    portfolioWinner.size())
            }));

        // 6. Sectors where original market-cap beat all 5 optimised results
        java.util.List<String> portfolios = runs.stream().map(Run::portfolio).distinct().sorted()
                .collect(Collectors.toList());
        for (String p2 : portfolios) {
            java.util.List<Run> sectorRuns = runs.stream()
                    .filter(r -> r.portfolio().equals(p2)).collect(Collectors.toList());
            boolean noneImproved = sectorRuns.stream().noneMatch(Run::improved);
            if (noneImproved && !sectorRuns.isEmpty()) {
                findings.add(new String[]{
                    "Optimisation-Resistant Portfolio",
                    String.format("%s: no method improved CAGR by more than 0.1%%.  " +
                        "Market-cap weighting appears near-efficient for this sector's " +
                        "top-10 holdings over the chosen lookback period.",
                        p2.replace("Sector-", ""))
                });
            }
        }

        return findings;
    }

    private static PdfPTable methodSummaryTable(java.util.List<Run> runs) throws Exception {
        PdfPTable t = new PdfPTable(new float[]{100, 85, 85, 85, 85, 85});
        t.setWidthPercentage(80);
        t.setSpacingBefore(6);
        t.setSpacingAfter(8);

        addHeaderCell(t, "Method");
        addHeaderCell(t, "Avg ΔCAGR");
        addHeaderCell(t, "Avg ΔSharpe");
        addHeaderCell(t, "# Improved");
        addHeaderCell(t, "# Beat SP500");
        addHeaderCell(t, "Avg Epochs");

        boolean alt = false;
        for (String mk : METHOD_KEYS) {
            java.util.List<Run> mRuns = runs.stream()
                    .filter(r -> r.method().equals(mk)).collect(Collectors.toList());
            if (mRuns.isEmpty()) continue;
            Color bg = alt ? CELL_ALT : Color.WHITE;
            alt = !alt;

            double avgCagr  = mRuns.stream().mapToDouble(Run::cagrDelta).average().orElse(0);
            double avgSharpe = mRuns.stream().mapToDouble(Run::sharpeDelta).average().orElse(0);
            long imp   = mRuns.stream().filter(Run::improved).count();
            long beats = mRuns.stream().filter(Run::beatsSP500).count();
            double avgEpoch = mRuns.stream().mapToInt(Run::epochCount).average().orElse(0);

            addDataCell(t, METHOD_LABELS.getOrDefault(mk, mk), bg, true);
            addDataCell(t, pct(avgCagr),                        bg, false);
            addDataCell(t, fmt2(avgSharpe),                     bg, false);
            addDataCell(t, imp   + " / " + mRuns.size(),        bg, false);
            addDataCell(t, beats + " / " + mRuns.size(),        bg, false);
            addDataCell(t, String.format("%.0f", avgEpoch),     bg, false);
        }
        return t;
    }

    // =========================================================================
    // PDF merge
    // =========================================================================

    private static byte[] mergePdfs(byte[] summary, java.util.List<byte[]> individuals)
            throws Exception {
        Document doc = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfCopy copy = new PdfCopy(doc, out);
        doc.open();
        appendPdf(copy, summary);
        for (byte[] pdf : individuals) appendPdf(copy, pdf);
        doc.close();
        return out.toByteArray();
    }

    private static void appendPdf(PdfCopy copy, byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            copy.addPage(copy.getImportedPage(reader, i));
        }
        copy.freeReader(reader);
        reader.close();
    }

    // =========================================================================
    // Parse raw job result maps → Run records
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static java.util.List<Run> parseRuns(java.util.List<Map<String, Object>> jobResults) {
        java.util.List<Run> runs = new ArrayList<>();
        for (Map<String, Object> r : jobResults) {
            try {
                String portfolio = str(r, "portfolioName");
                String method    = str(r, "optimizeBy");

                Map<String, Object> orig = (Map<String, Object>) r.get("original");
                Map<String, Object> sp5  = (Map<String, Object>) r.get("sp500");
                java.util.List<?> epochs = (java.util.List<?>) r.get("epochs");
                Map<String, Object> fin  = epochs != null && !epochs.isEmpty()
                        ? (Map<String, Object>) epochs.get(epochs.size() - 1) : orig;
                Map<String, Object> fp   = (Map<String, Object>) r.get("finalPortfolio");

                double origCagr  = num(orig, "cagr");
                double origSharp = num(orig, "sharpeRatio");
                double finCagr   = fin  != null ? num(fin, "cagr")         : origCagr;
                double finSharp  = fin  != null ? num(fin, "sharpeRatio")   : origSharp;
                double finUtil   = fin  != null ? num(fin, "weightedUtility") : 0;
                double sp5Cagr   = sp5  != null ? num(sp5, "cagr")         : 0;
                double sp5Sharp  = sp5  != null ? num(sp5, "sharpeRatio")   : 0;

                int epochCount = epochs != null ? epochs.size() : 0;
                String stopped = str(r, "stoppedReason");

                String[] syms = null;
                Double[] wts  = null;
                if (fp != null) {
                    java.util.List<?> sl = (java.util.List<?>) fp.get("symbols");
                    java.util.List<?> wl = (java.util.List<?>) fp.get("weights");
                    if (sl != null) syms = sl.stream().map(Object::toString).toArray(String[]::new);
                    if (wl != null) wts  = wl.stream()
                            .map(w -> w instanceof Number ? ((Number)w).doubleValue() : Double.parseDouble(w.toString()))
                            .toArray(Double[]::new);
                }

                runs.add(new Run(portfolio, method,
                        origCagr, origSharp,
                        finCagr, finSharp, finUtil,
                        sp5Cagr, sp5Sharp,
                        epochCount, stopped, syms, wts));
            } catch (Exception ignored) { /* malformed result — skip */ }
        }
        return runs;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void addHeaderCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, tableHead()));
        c.setBackgroundColor(NAVY);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5);
        t.addCell(c);
    }

    private static void addDataCell(PdfPTable t, String text, Color bg, boolean bold) {
        PdfPCell c = new PdfPCell(new Phrase(text, bold ? tableCellBold() : tableCell()));
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(bold ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        t.addCell(c);
    }

    private static Paragraph sectionHead(String text) {
        Paragraph p = new Paragraph(text, headFont());
        p.setSpacingBefore(12);
        p.setSpacingAfter(6);
        return p;
    }

    private static Paragraph subhead(String text) {
        Paragraph p = new Paragraph(text, subheadFont());
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        return p;
    }

    private static Paragraph body(String text) {
        Paragraph p = new Paragraph(text, bodyFont());
        p.setSpacingAfter(8);
        return p;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v != null ? v.toString() : "";
    }

    private static double num(Map<String, Object> m, String key) {
        if (m == null) return 0;
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v != null) try { return Double.parseDouble(v.toString()); } catch (Exception e) { /* fall through */ }
        return 0;
    }

    private static String pct(double v) {
        return String.format("%+.1f%%", v * 100);
    }

    private static String fmt2(double v) {
        return String.format("%+.2f", v);
    }
}
