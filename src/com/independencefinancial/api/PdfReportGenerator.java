package com.bcfinancial.api;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Generates a PDF optimization report from the result map produced by
 * {@link ApiHandler#optimizationToMap}.
 *
 * <p>Sections:
 * <ol>
 *   <li>Header — portfolio name, method, date, epoch count
 *   <li>Performance Comparison — SP500 / Original / Optimized side-by-side
 *   <li>Final Portfolio Allocation — symbol weights with delta vs original
 *   <li>Optimization Progress — per-epoch utility, Sharpe, CAGR
 * </ol>
 */
public class PdfReportGenerator {

    // ── Palette ─────────────────────────────────────────────────────────────
    private static final Color NAVY       = new Color(0x1e, 0x3a, 0x6e);
    private static final Color ACCENT     = new Color(0x4f, 0x7e, 0xf8);
    private static final Color LIGHT_GRAY = new Color(0xf2, 0xf4, 0xfa);
    private static final Color WHITE      = Color.WHITE;
    private static final Color GREEN      = new Color(0x18, 0x78, 0x48);
    private static final Color RED        = new Color(0xb0, 0x28, 0x28);
    private static final Color DARK       = new Color(0x1a, 0x1d, 0x2e);
    private static final Color MUTED      = new Color(0x55, 0x60, 0x7a);
    private static final Color BORDER_CLR = new Color(0xcc, 0xd0, 0xe0);

    // ── Fonts ────────────────────────────────────────────────────────────────
    private static final Font TITLE    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  20, Font.BOLD,   NAVY);
    private static final Font SECTION  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  11, Font.BOLD,   NAVY);
    private static final Font META_LBL = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   8, Font.BOLD,   MUTED);
    private static final Font META_VAL = FontFactory.getFont(FontFactory.HELVETICA,        10, Font.NORMAL, DARK);
    private static final Font HDR      = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   9, Font.BOLD,   WHITE);
    private static final Font BODY     = FontFactory.getFont(FontFactory.HELVETICA,         9, Font.NORMAL, DARK);
    private static final Font BOLD9    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   9, Font.BOLD,   DARK);
    private static final Font POS      = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   9, Font.BOLD,   GREEN);
    private static final Font NEG      = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   9, Font.BOLD,   RED);

    // ── Public entry point ───────────────────────────────────────────────────

    /**
     * @param result       result map from {@code ApiHandler.optimizationToMap()}
     *                     (must also contain {@code "portfolioName"} and {@code "optimizeBy"})
     * @param portfolioName human-readable portfolio name
     * @param methodLabel  display label for the optimization method (e.g. "Prospect Theory")
     * @return PDF as a byte array
     */
    public static byte[] generate(Map<String, Object> result,
                                   String portfolioName,
                                   String methodLabel) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 45, 45, 50, 50);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        addHeader(doc, portfolioName, methodLabel, result);
        addPerformanceTable(doc, result);
        addWeightsTable(doc, result);
        addEpochTable(doc, result);

        doc.close();
        return baos.toByteArray();
    }

    // ── Section: header ──────────────────────────────────────────────────────

    private static void addHeader(Document doc, String portfolioName, String methodLabel,
                                   Map<String, Object> result) throws DocumentException {
        Paragraph title = new Paragraph("Portfolio Optimization Report", TITLE);
        title.setSpacingAfter(4);
        doc.add(title);

        doc.add(new Chunk(new LineSeparator(2f, 100f, ACCENT, Element.ALIGN_LEFT, -2f)));
        doc.add(new Paragraph(" "));

        @SuppressWarnings("unchecked")
        List<?> epochs = (List<?>) result.get("epochs");
        int n = epochs != null ? epochs.size() : 0;

        PdfPTable meta = new PdfPTable(new float[]{1.4f, 2.8f, 1.4f, 2.8f});
        meta.setWidthPercentage(100);
        meta.setSpacingAfter(16);
        addMetaPair(meta, "Portfolio",      portfolioName,
                          "Generated",      new SimpleDateFormat("MMMM d, yyyy  HH:mm").format(new Date()));
        addMetaPair(meta, "Optimized By",   methodLabel,
                          "Improving Epochs", n + (n == 1 ? " epoch" : " epochs"));
        doc.add(meta);
    }

    // ── Section: performance comparison ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void addPerformanceTable(Document doc,
                                             Map<String, Object> result) throws DocumentException {
        doc.add(sectionPara("Performance Comparison"));

        Map<String,Object> sp   = (Map<String,Object>) result.get("sp500");
        Map<String,Object> orig = (Map<String,Object>) result.get("original");
        List<Map<String,Object>> epochs = (List<Map<String,Object>>) result.get("epochs");
        Map<String,Object> opt = (epochs != null && !epochs.isEmpty())
                ? epochs.get(epochs.size() - 1) : orig;

        PdfPTable t = new PdfPTable(new float[]{2.4f, 1.5f, 1.5f, 1.5f});
        t.setWidthPercentage(90);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingBefore(4);
        t.setSpacingAfter(14);

        for (String h : new String[]{"Metric", "S&P 500", "Original", "Optimized"})
            t.addCell(hdrCell(h));

        boolean alt = false;
        addPerfRow(t, "Full-Period CAGR",  sp, orig, opt, "cagr",           alt=!alt, true,  true);
        addPerfRow(t, "Avg Period CAGR",   sp, orig, opt, "avgCagr",        alt=!alt, true,  true);
        addPerfRow(t, "Sharpe Ratio",      sp, orig, opt, "sharpeRatio",    alt=!alt, true,  false);
        addPerfRow(t, "Max Drawdown",      sp, orig, opt, "maxDrawdown",    alt=!alt, false, true);
        addPerfRow(t, "VaR 95%",           sp, orig, opt, "var95",          alt=!alt, false, true);
        addPerfRow(t, "VaR 99%",           sp, orig, opt, "var99",          alt=!alt, false, true);
        addPerfRow(t, "Utility Score",     sp, orig, opt, "weightedUtility",alt=!alt, true,  false);

        doc.add(t);
    }

    // ── Section: final weights ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void addWeightsTable(Document doc,
                                         Map<String, Object> result) throws DocumentException {
        Map<String,Object> finalPortfolio = (Map<String,Object>) result.get("finalPortfolio");
        if (finalPortfolio == null) return;

        doc.add(sectionPara("Final Portfolio Allocation"));

        // Build original weights lookup
        Map<String, Double> origWeights = new LinkedHashMap<>();
        Map<String,Object> orig = (Map<String,Object>) result.get("original");
        if (orig != null) {
            Object[] syms = toArr(orig.get("symbols"));
            Object[] wts  = toArr(orig.get("weights"));
            for (int i = 0; i < syms.length; i++)
                origWeights.put(syms[i].toString(), dbl(wts[i]));
        }

        Object[] symbols = toArr(finalPortfolio.get("symbols"));
        Object[] weights = toArr(finalPortfolio.get("weights"));

        // Sort by final weight descending
        Integer[] idx = new Integer[symbols.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(dbl(weights[b]), dbl(weights[a])));

        PdfPTable t = new PdfPTable(new float[]{1.5f, 1.3f, 1.3f, 1.3f});
        t.setWidthPercentage(55);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingBefore(4);
        t.setSpacingAfter(14);

        for (String h : new String[]{"Symbol", "Original", "Final", "Change"})
            t.addCell(hdrCell(h));

        boolean alt = false;
        for (int i : idx) {
            String sym   = symbols[i].toString();
            double orig_ = origWeights.getOrDefault(sym, dbl(weights[i]));
            double fin   = dbl(weights[i]);
            double delta = fin - orig_;
            Color bg = (alt = !alt) ? LIGHT_GRAY : WHITE;

            t.addCell(bodyCell(sym,       BOLD9, bg, Element.ALIGN_LEFT));
            t.addCell(bodyCell(pct(orig_), BODY,  bg, Element.ALIGN_RIGHT));
            t.addCell(bodyCell(pct(fin),   BODY,  bg, Element.ALIGN_RIGHT));

            Font df = Math.abs(delta) < 5e-4 ? BODY : (delta > 0 ? POS : NEG);
            t.addCell(bodyCell((delta >= 0 ? "+" : "") + pct(delta), df, bg, Element.ALIGN_RIGHT));
        }
        doc.add(t);
    }

    // ── Section: epoch progress ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void addEpochTable(Document doc,
                                       Map<String, Object> result) throws DocumentException {
        List<Map<String,Object>> epochs = (List<Map<String,Object>>) result.get("epochs");
        if (epochs == null || epochs.isEmpty()) return;

        doc.add(sectionPara("Optimization Progress  (" + epochs.size()
                + " improving epoch" + (epochs.size() == 1 ? "" : "s") + ")"));

        Map<String,Object> orig = (Map<String,Object>) result.get("original");

        PdfPTable t = new PdfPTable(new float[]{1f, 1.8f, 1.4f, 1.4f});
        t.setWidthPercentage(65);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingBefore(4);

        for (String h : new String[]{"Epoch", "Utility", "Sharpe", "Avg CAGR"})
            t.addCell(hdrCell(h));

        boolean alt = false;
        if (orig != null) {
            Color bg = (alt = !alt) ? LIGHT_GRAY : WHITE;
            t.addCell(bodyCell("Original",                 BOLD9, bg, Element.ALIGN_CENTER));
            t.addCell(bodyCell(num4(orig, "weightedUtility"), BODY,  bg, Element.ALIGN_RIGHT));
            t.addCell(bodyCell(num2(orig, "sharpeRatio"),     BODY,  bg, Element.ALIGN_RIGHT));
            t.addCell(bodyCell(pctM(orig, "avgCagr"),         BODY,  bg, Element.ALIGN_RIGHT));
        }

        for (int i = 0; i < epochs.size(); i++) {
            Map<String,Object> ep = epochs.get(i);
            Color bg = (alt = !alt) ? LIGHT_GRAY : WHITE;
            t.addCell(bodyCell(String.valueOf(i + 1),         BODY, bg, Element.ALIGN_CENTER));
            t.addCell(bodyCell(num4(ep, "weightedUtility"),   BODY, bg, Element.ALIGN_RIGHT));
            t.addCell(bodyCell(num2(ep, "sharpeRatio"),       BODY, bg, Element.ALIGN_RIGHT));
            t.addCell(bodyCell(pctM(ep, "avgCagr"),           BODY, bg, Element.ALIGN_RIGHT));
        }
        doc.add(t);
    }

    // ── Helpers: section and row builders ───────────────────────────────────

    private static Paragraph sectionPara(String text) {
        Paragraph p = new Paragraph(text, SECTION);
        p.setSpacingBefore(10);
        p.setSpacingAfter(2);
        return p;
    }

    private static void addMetaPair(PdfPTable t,
                                     String l1, String v1,
                                     String l2, String v2) {
        t.addCell(metaLabel(l1)); t.addCell(metaValue(v1));
        t.addCell(metaLabel(l2)); t.addCell(metaValue(v2));
    }

    private static void addPerfRow(PdfPTable t, String label,
                                    Map<String,Object> sp, Map<String,Object> orig,
                                    Map<String,Object> opt, String key,
                                    boolean alt, boolean higherBetter,
                                    boolean isPercent) {
        Color bg    = alt ? LIGHT_GRAY : WHITE;
        double sv   = numD(sp,   key);
        double ov   = numD(orig, key);
        double optv = numD(opt,  key);
        String spFmt   = isPercent ? pct(sv)   : num2str(sv);
        String origFmt = isPercent ? pct(ov)   : num2str(ov);
        String optFmt  = isPercent ? pct(optv) : num2str(optv);

        boolean improved = higherBetter ? optv > ov : optv < ov;
        Font optFont = Math.abs(optv - ov) < 5e-5 ? BODY : (improved ? POS : NEG);

        t.addCell(bodyCell(label,   BOLD9,   bg, Element.ALIGN_LEFT));
        t.addCell(bodyCell(spFmt,   BODY,    bg, Element.ALIGN_RIGHT));
        t.addCell(bodyCell(origFmt, BODY,    bg, Element.ALIGN_RIGHT));
        t.addCell(bodyCell(optFmt,  optFont, bg, Element.ALIGN_RIGHT));
    }

    // ── Cell factories ───────────────────────────────────────────────────────

    private static PdfPCell hdrCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, HDR));
        c.setBackgroundColor(NAVY);
        c.setPadding(5);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderColor(NAVY);
        return c;
    }

    private static PdfPCell metaLabel(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, META_LBL));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingBottom(5);
        return c;
    }

    private static PdfPCell metaValue(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, META_VAL));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingBottom(5);
        return c;
    }

    private static PdfPCell bodyCell(String text, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "—", font));
        c.setBackgroundColor(bg);
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        c.setBorderColor(BORDER_CLR);
        return c;
    }

    // ── Formatting ───────────────────────────────────────────────────────────

    private static String pct(double v)  { return String.format("%.2f%%", v * 100); }
    private static String pctM(Map<String,Object> m, String k) {
        return m != null && m.containsKey(k) ? pct(dbl(m.get(k))) : "—";
    }
    private static String num2str(double v) { return String.format("%.3f", v); }
    private static String num4(Map<String,Object> m, String k) {
        return m != null && m.containsKey(k) ? String.format("%.4f", dbl(m.get(k))) : "—";
    }
    private static String num2(Map<String,Object> m, String k) {
        return m != null && m.containsKey(k) ? String.format("%.3f", dbl(m.get(k))) : "—";
    }

    // ── Data extraction ──────────────────────────────────────────────────────

    private static double numD(Map<String,Object> m, String key) {
        if (m == null || !m.containsKey(key)) return 0.0;
        return dbl(m.get(key));
    }

    private static double dbl(Object o) {
        if (o == null)          return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private static Object[] toArr(Object o) {
        if (o instanceof List)     return ((List<?>) o).toArray();
        if (o instanceof Object[]) return (Object[]) o;
        return new Object[0];
    }
}
