# Football Schedule – Timefold Solver

Spring Boot + Timefold Solver project for optimizing a football championship match schedule under hard and soft constraints.

This repo contains:

- A REST API for solving schedules and retrieving results
- A simple browser UI for running demo solves and exporting CSV
- Demo datasets: `small`, `virsliga`, `epl`

---

## Quick start (Windows PowerShell)

Prereqs:
- JDK 21
- Maven 3.9+

Open a terminal in the project directory (where `pom.xml` is) and run:

```powershell
mvn -DskipTests spring-boot:run
```

After the app starts, open the UI at:

http://localhost:8080/

API base: `http://localhost:8080/api/schedule`

Stop with Ctrl+C.

---

## REST API (important endpoints)

1) Start solving
POST /api/schedule/solve
Body: `{ "type": "virsliga", "variant": "tabu" }`
- `type` : `small` | `virsliga` | `epl` (defaults to `small`)
- `variant` (optional): `tabu`, `lateAcceptance`, `hillClimbing`, `simulatedAnnealing`, `constructionHeuristic`

Example (PowerShell):
```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/schedule/solve" -ContentType "application/json" -Body '{"type":"virsliga","variant":"constructionHeuristic"}'
```

2) Poll status
GET `/api/schedule/status/{scheduleId}`

3) Fetch result matches
GET `/api/schedule/result/{scheduleId}`

4) Explain a running/completed in-memory solution
GET `/api/schedule/explain/{scheduleId}`

5) Explain a saved benchmark solution (reads `benchmark-solution-<dataset>-<variant>.json`)
GET `/explain?dataset=<dataset>&variant=<variant>`

If the saved JSON is corrupted, the explain endpoint attempts a fallback solve (may be slow).

---

## Features

- Generate schedules for demo datasets (`small`, `virsliga`, `epl`)
- Multiple solver variants (tabu, late acceptance, hill climbing, simulated annealing, construction heuristic)
- Per-constraint explainability for saved and in-memory solutions
- Benchmark runner that writes CSV and per-variant JSON solutions

---

## Constraints (overview)

Hard constraints (must be satisfied):

- H1: A team cannot play more than once on the same date
- H2: No two matches in same stadium at same timeslot on same date
- H3: Max 1 match per stadium per day
- H4: European competition teams avoid Tue/Wed European nights
- H6: Last round matches should be on Sunday 15:00
- H8: Minimum rest days between matches (configurable)

Soft constraints (quality optimization):

- Penalize Friday/Monday matches and very late kickoffs
- Discourage too many simultaneous matches (except last round)

---

## Tech stack

- Java 21
- Maven
- Spring Boot 3.x
- Timefold Solver (HardSoftScore)
- Jackson (with JavaTime module)

---

## Browser UI

- Main UI: `src/main/resources/static/index.html` — choose dataset and optimizer variant, run solver, view/export results.
- Benchmark UI: `src/main/resources/static/benchmark.html` — view benchmark charts and open saved explains. The benchmark page includes a per-dataset variant selector so you can choose which saved variant to explain.

---

## Benchmark runner

There is a `BenchmarkRunner` class that executes multiple dataset × variant solves and writes `benchmark-results.csv` and `benchmark-solution-<dataset>-<variant>.json` files to the project root.

Run it (example):
```powershell
mvn exec:java "-Dexec.mainClass=lv.football.scheduler.benchmark.BenchmarkRunner" "-Dexec.jvmArgs=-Xmx4G"
```

Notes:
- The runner now writes JSON files atomically (write to a `.tmp` file then move), to avoid truncated files.
- The project includes Jackson JavaTime support so `LocalDate` values are serialized/deserialized correctly.

---

## Solver configuration

Per-dataset solver configs are located under `src/main/resources`:
- `solverConfig-small.xml`
- `solverConfig-virsliga.xml`
- `solverConfig-epl.xml`

Variants map to additional config files in the same folder (tabu, la, hc, sa, ch).

You can force a variant from the UI or via the `variant` field in the `POST /api/schedule/solve` body.

---

## Forbidden dates and fixes

- Forbidden dates are provided per-dataset by the loader and exposed as problem facts (`SchedulingSolution.forbiddenDates`).
- The main constraint `H9` joins each `Match` with `LocalDate` facts and penalizes matches scheduled on those dates.
- A backup guard `H9b` forbids common public holidays per-league (EPL-style winter/New Year or Virsliga local dates).
- Construction-heuristic (CH) results are post-processed to move trivial forbidden-date placements to nearby valid slots; this is a simple greedy fixer and may be replaced with a stronger solver pass if needed.

---

## Troubleshooting

- If `/explain` returns a parse error, re-run the benchmark runner to regenerate JSONs or use the in-memory explain by running a solve from the UI/API and then calling `/api/schedule/explain/{id}`.
- If the UI dropdown text is hard to read on some systems, try a different browser or adjust `index.html` select styling.

---

## License & Attribution

This repository is provided as-is for demonstration and research purposes.

