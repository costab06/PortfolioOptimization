#!/usr/bin/env python3
"""
Run all 5 optimization methods on EnsembleFunds and generate an aggregate report.
"""

import requests, time, sys

API     = "http://localhost:8080/api"
USER    = "admin"
PASS    = "admin"
PORT    = "EnsembleFunds"

METHODS = [
    {"label": "powerLog",   "optimizeBy": "utility",  "optimizeByType": "powerLog"},
    {"label": "sortino",    "optimizeBy": "utility",  "optimizeByType": "sortino"},
    {"label": "convexLoss", "optimizeBy": "utility",  "optimizeByType": "convexLoss"},
    {"label": "omega",      "optimizeBy": "utility",  "optimizeByType": "omega"},
    {"label": "sharpe",     "optimizeBy": "sharpe",   "optimizeByType": "sharpe"},
]

# Base parameters - appropriate for 11 instruments
BASE_PARAMS = {
    "username":              USER,
    "password":              PASS,
    "portfolioName":         PORT,
    "durationYears":         5,
    "timePeriodDays":        45,
    "maxEpochs":             300,
    "minImprovementPerEpoch": 0.0,
    "maxWeightPerInstrument": 0.9,
    "annealingTemperature":  0.05,
    "annealingDecay":        0.95,
    "multiStartCount":       1,
    "stepSize":              0.01,
}

def set_utility(method_label):
    if method_label == "powerLog":
        requests.put(f"{API}/users/{USER}/utility", json={
            "password": PASS, "type": "powerLog",
            "alpha": 0.88, "beta": 0.88, "lambda": 2.25
        })
    elif method_label in ("sortino", "omega"):
        requests.put(f"{API}/users/{USER}/utility", json={
            "password": PASS, "type": method_label, "rfAnnual": 0.04
        })
    elif method_label == "convexLoss":
        requests.put(f"{API}/users/{USER}/utility", json={
            "password": PASS, "type": "convexLoss",
            "alpha": 0.88, "beta": 1.136, "lambda": 2.25
        })
    elif method_label == "sharpe":
        requests.put(f"{API}/users/{USER}/utility", json={
            "password": PASS, "type": "powerLog",
            "alpha": 0.88, "beta": 0.88, "lambda": 2.25
        })

print("Submitting 5 jobs...")
job_ids = []

for m in METHODS:
    set_utility(m["label"])
    params = dict(BASE_PARAMS)
    params["optimizeBy"]     = m["optimizeBy"]
    params["optimizeByType"] = m["optimizeByType"]

    resp = requests.post(f"{API}/optimize", json=params)
    if resp.status_code != 200:
        print(f"  ERROR submitting {m['label']}: {resp.status_code} {resp.text[:200]}")
        sys.exit(1)
    job_id = resp.json()["data"]["jobId"]
    job_ids.append(job_id)
    print(f"  {m['label']:12s} -> {job_id}")

print(f"\nSubmitted {len(job_ids)} jobs — polling...")

def poll(job_ids):
    while True:
        states = {}
        for jid in job_ids:
            r = requests.get(f"{API}/optimize/{jid}").json()["data"]
            states[jid] = r["state"]
        completed = sum(1 for s in states.values() if s == "completed")
        running   = sum(1 for s in states.values() if s == "running")
        failed    = sum(1 for s in states.values() if s == "failed")
        ts = time.strftime("%H:%M:%S")
        print(f"{ts}  completed={completed}  running={running}  failed={failed}")
        if failed > 0:
            for jid, st in states.items():
                if st == "failed":
                    r = requests.get(f"{API}/optimize/{jid}").json()["data"]
                    print(f"  FAILED {jid}: {r.get('error','?')}")
        if running == 0:
            return states
        time.sleep(20)

states = poll(job_ids)
completed = [jid for jid, s in states.items() if s == "completed"]
failed    = [jid for jid, s in states.items() if s == "failed"]
print(f"\nDone. completed={len(completed)} failed={len(failed)}")

if not completed:
    print("No completed jobs — aborting")
    sys.exit(1)

# -----------------------------------------------------------------------
# Generate aggregate report
# -----------------------------------------------------------------------
print("\nGenerating aggregate report...")
resp = requests.post(f"{API}/report/aggregate", json={"jobIds": completed})
if resp.status_code != 200:
    print(f"ERROR {resp.status_code}: {resp.text[:500]}")
    sys.exit(1)

ts = time.strftime("%Y%m%d_%H%M%S")
fname = f"EnsembleFunds_report_{ts}.pdf"
with open(fname, "wb") as fh:
    fh.write(resp.content)
print(f"Report saved to {fname} ({len(resp.content):,} bytes)")

# Also print per-method final portfolios
print("\nFinal weights per method:")
for jid in completed:
    r = requests.get(f"{API}/optimize/{jid}").json()["data"]["result"]
    method  = r.get("optimizeBy", "?")
    epochs  = r.get("epochs", [])
    stopped = r.get("stoppedReason", "maxEpochs")
    if epochs:
        final = epochs[-1]
        syms    = final["symbols"]
        wts     = final["weights"]
        sw = sorted(zip(syms, wts), key=lambda x: -x[1])
        top3 = "  ".join(f"{s}={100*w:.1f}%" for s, w in sw[:3])
        print(f"  {method:12s}: {top3}  (epochs={len(epochs)}, stopped={stopped})")
    else:
        print(f"  {method:12s}: 0 epochs  (stopped={stopped})")
