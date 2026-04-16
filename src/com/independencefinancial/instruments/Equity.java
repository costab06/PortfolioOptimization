package com.bcfinancial.instruments;

import java.util.Date;
import java.util.Objects;

/**
 * A single daily OHLCV bar for one equity instrument.
 *
 * <p>This is a plain value object — all persistence is handled by
 * {@link com.bcfinancial.data.EquityRepository}.
 *
 * <p>Changes from the legacy version:
 * <ul>
 *   <li>{@code symbol} is now a proper instance field (was {@code static transient},
 *       meaning all instances shared the same symbol — a latent bug).</li>
 *   <li>{@code adjClose} added to support adjusted-close calculations that
 *       account for splits and dividends.</li>
 *   <li>Removed {@code extends Persistable} and the reflection-ORM helper
 *       methods ({@code getKeyFieldName}, {@code getCreateTableString}, {@code init}).</li>
 * </ul>
 */
public class Equity {

    private final String symbol;
    private final Date   date;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final double adjClose;
    private final long   volume;

    public Equity(String symbol, Date date,
                  double open, double high, double low,
                  double close, double adjClose, long volume) {
        this.symbol   = symbol;
        this.date     = date;
        this.open     = open;
        this.high     = high;
        this.low      = low;
        this.close    = close;
        this.adjClose = adjClose;
        this.volume   = volume;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public String getSymbol()   { return symbol; }
    public Date   getDate()     { return date; }
    public double getOpen()     { return open; }
    public double getHigh()     { return high; }
    public double getLow()      { return low; }
    public double getClose()    { return close; }
    /** Adjusted close — accounts for splits and dividends. Preferred over raw close for returns. */
    public double getAdjClose() { return adjClose > 0 ? adjClose : close; }
    public long   getVolume()   { return volume; }

    // ------------------------------------------------------------------
    // Object overrides
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Equity)) return false;
        Equity e = (Equity) o;
        return Objects.equals(symbol, e.symbol) && Objects.equals(date, e.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, date);
    }

    @Override
    public String toString() {
        return String.format("| %s | %s | %.4f | %.4f | %.4f | %.4f | %.4f | %d |",
                symbol, date, open, high, low, close, adjClose, volume);
    }
}
