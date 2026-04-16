#!/usr/bin/env python3
# Run portfolio optimization 10 times on VTI/VXUS/BND with different starting weights.
# Each run writes:
#   debug/<timestamp>_<name>.txt   — human-readable epoch-by-epoch trace
#   debug/<timestamp>_<name>.json  — machine-readable path data for visualization

import json
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8080/api"
USER = "debuguser"
PASS = "debugpass"
EMAIL = "debug@example.com"

# 10 starting weight configurations for [VTI, VXUS, BND]
CONFIGS = [
    ([0.3334, 0.3333, 0.3333], "equal_weight"),
    ([0.6000, 0.2000, 0.2000], "vti_heavy"),
    ([0.2000, 0.6000, 0.2000], "vxus_heavy"),
    ([0.2000, 0.2000, 0.6000], "bond_heavy"),
    ([0.5000, 0.3000, 0.2000], "classic_bogleheads"),
    ([0.4000, 0.2000, 0.4000], "balanced"),
    ([0.7000, 0.1500, 0.1500], "aggressive"),
    ([0.1000, 0.1000, 0.8000], "conservative"),
    ([0.1500, 0.7000, 0.1500], "intl_dominant"),
    ([0.4500, 0.4500, 0.1000], "equity_equal"),
]

# ── HTTP helpers ─────────────────────────────────────────────────────────────

def api(method, path, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                  headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read())

def wait_for_job(job_id, max_wait=600):
    elapsed = 0
    while elapsed < max_wait:
        resp = api("GET", f"/optimize/{job_id}")
        state = resp["data"]["state"]
        if state == "completed":
            return "completed", resp
        elif state == "failed":
            return "failed", resp
        print(".", end="", flush=True)
        time.sleep(5)
        elapsed += 5
    return "timeout", {}

# ── Main ─────────────────────────────────────────────────────────────────────

print("=== Bogleheads Debug Run ===")
print(f"Server: {BASE}")
print(f"Debug files will appear in: debug/\n")

# Register debug user (ignore error if already exists)
print("Registering debug user... ", end="")
api("POST", "/users/register", {"username": USER, "password": PASS, "email": EMAIL})
print("ok (or already exists)")

# Load full 5-year history
print("Loading equity data (VTI, VXUS, BND — full 5-year history)... ", end="", flush=True)
load = api("POST", "/equity/load", {"symbols": ["VTI", "VXUS", "BND"], "full": True})
d = load.get("data", {})
print(f"loaded={d.get('loaded')}  failed={d.get('failed')}\n")

summary = []

for run_num, (weights, label) in enumerate(CONFIGS, 1):
    vti, vxus, bnd = weights
    pname = f"bh_debug_{run_num}_{label}"

    print(f"Run {run_num:2d}/10  {label:<22}  VTI={vti:.4f}  VXUS={vxus:.4f}  BND={bnd:.4f}")

    # Create portfolio
    api("POST", "/portfolios", {
        "username": USER,
        "portfolioName": pname,
        "symbols": ["VTI", "VXUS", "BND"],
        "weights": weights,
    })

    # Start optimization
    job_resp = api("POST", "/optimize", {
        "username": USER, "password": PASS,
        "portfolioName": pname,
        "endYear": 2026, "endMonth": 4, "endDay": 1,
        "durationYears": 5, "timePeriodDays": 90,
        "maxEpochs": 2000, "minImprovementPerEpoch": -0.000001,
        "maxWeightPerInstrument": 0.75,
    })
    job_id = job_resp["data"]["jobId"]
    print(f"        jobId={job_id}  waiting", end="", flush=True)

    state, result_resp = wait_for_job(job_id)
    print(f" {state}")

    if state == "completed":
        res = result_resp["data"]["result"]
        epochs = res.get("epochs", [])
        if epochs:
            last = epochs[-1]
            wu = last["weightedUtility"]
            w  = last["weights"]
            n  = len(epochs)
            print(f"        result: util={wu:.6f}  VTI={w[0]*100:.1f}%  VXUS={w[1]*100:.1f}%  BND={w[2]*100:.1f}%  epochs={n}")
            summary.append((run_num, label, weights, wu, w, n, "ok"))
        else:
            orig_wu = res.get("original", {}).get("weightedUtility", "n/a")
            print(f"        result: no improvement  orig_util={orig_wu}")
            summary.append((run_num, label, weights, orig_wu, weights, 0, "no_improvement"))
    else:
        err = result_resp.get("data", {}).get("error", "?")
        print(f"        FAILED: {err}")
        summary.append((run_num, label, weights, "FAILED", weights, 0, "failed"))

    print()

# ── Summary ──────────────────────────────────────────────────────────────────

print("=" * 76)
print("SUMMARY")
print("=" * 76)
print(f"{'Run':<4} {'Label':<22} {'Start weights':<28} {'Final util':<12} {'Final weights':<28} Epochs")
print("-" * 76)
for run_num, label, start_w, wu, final_w, epochs, status in summary:
    sw = f"VTI={start_w[0]*100:.0f}% VXUS={start_w[1]*100:.0f}% BND={start_w[2]*100:.0f}%"
    if isinstance(final_w[0], float):
        fw = f"VTI={final_w[0]*100:.1f}% VXUS={final_w[1]*100:.1f}% BND={final_w[2]*100:.1f}%"
    else:
        fw = str(final_w)
    wu_str = f"{wu:.6f}" if isinstance(wu, float) else str(wu)
    print(f"{run_num:<4} {label:<22} {sw:<28} {wu_str:<12} {fw:<28} {epochs}")

print("=" * 76)
print("\nDebug files written to: debug/")
import os, glob
files = sorted(glob.glob("debug/*.txt"), reverse=True)[:20]
for f in files:
    print(f"  {f}")
