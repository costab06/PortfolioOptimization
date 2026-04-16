#!/usr/bin/env python3
"""
Extract per-sector ensemble weights from the 55 debug JSON files,
then call POST /api/equity/synthetic to create synthetic fund tickers,
then store an EnsembleFunds portfolio with the 11 fund tickers.
"""

import glob, json, collections, requests, sys

API = "http://localhost:8080/api"

# -----------------------------------------------------------------------
# 1. Load all 55 debug files and extract final weights per sector
# -----------------------------------------------------------------------

files = sorted(glob.glob("debug/20260413_191809_*Sector-*.json"))
print(f"Found {len(files)} sector debug files")

# sector → list of weight dicts (one per optimisation method)
sector_runs = collections.defaultdict(list)

for f in files:
    with open(f) as fh:
        data = json.load(fh)
    sector = data["portfolioName"]          # e.g. "Sector-Technology"
    symbols = data["symbols"]

    # Take the FINAL epoch weights if epochs exist, else start weights
    epochs = data.get("epochs", [])
    if epochs:
        last_ep = epochs[-1]
        weights_map = last_ep.get("endWeights") or last_ep.get("startWeights")
    else:
        weights_map = data["startWeights"]

    if weights_map:
        w = {sym: weights_map.get(sym, 0.0) for sym in symbols}
        sector_runs[sector].append(w)

for s, runs in sorted(sector_runs.items()):
    print(f"  {s}: {len(runs)} runs")

# -----------------------------------------------------------------------
# 2. Average the weights per sector (ensemble)
# -----------------------------------------------------------------------

fund_map = {}  # sector_label → {ticker: avg_weight}

for sector, runs in sector_runs.items():
    all_symbols = list(runs[0].keys())
    avg = {}
    for sym in all_symbols:
        avg[sym] = sum(r.get(sym, 0.0) for r in runs) / len(runs)
    # Normalise to sum 1
    total = sum(avg.values())
    avg = {k: v / total for k, v in avg.items()}
    # Clean sector label → fund ticker
    fund_ticker = "FUND-" + sector.replace("Sector-", "")
    fund_map[fund_ticker] = avg

print(f"\nEnsemble fund tickers: {sorted(fund_map.keys())}")

# -----------------------------------------------------------------------
# 3. Create synthetic equities via the API
# -----------------------------------------------------------------------

print("\nCreating synthetic equities...")
fund_tickers = []

for fund_ticker, holdings in sorted(fund_map.items()):
    top5 = sorted(holdings.items(), key=lambda x: -x[1])[:5]
    top5_str = "  ".join(f"{s}={100*w:.1f}%" for s, w in top5)
    print(f"  {fund_ticker}: {top5_str}")

    resp = requests.post(f"{API}/equity/synthetic", json={
        "symbol":    fund_ticker,
        "holdings":  holdings,
    })
    if resp.status_code != 200:
        print(f"    ERROR: {resp.status_code} {resp.text[:200]}")
        sys.exit(1)
    result = resp.json()
    rows = result.get("data", {}).get("rows", "?")
    print(f"    -> {rows} rows stored")
    fund_tickers.append(fund_ticker)

# -----------------------------------------------------------------------
# 4. Store EnsembleFunds portfolio (equal weight across 11 sectors)
# -----------------------------------------------------------------------

n = len(fund_tickers)
weights = [1.0 / n] * n

print(f"\nStoring EnsembleFunds portfolio ({n} funds, equal weight)...")
resp = requests.post(f"{API}/portfolios", json={
    "username":      "admin",
    "portfolioName": "EnsembleFunds",
    "symbols":       fund_tickers,
    "weights":       weights,
})
if resp.status_code != 200:
    print(f"ERROR storing portfolio: {resp.status_code} {resp.text[:200]}")
    sys.exit(1)

print("  Done.")
print(f"\nAll {n} synthetic funds created. Portfolio 'EnsembleFunds' stored for user 'admin'.")
print("You can now run /api/optimize on EnsembleFunds with the 5 methods.")
