# Football Schedule – Timefold Solver

Spring Boot + Timefold Solver project for optimizing a football championship match schedule under hard and soft constraints.

This project includes:
- a REST API for solving schedules,
- a simple browser UI for running demos,
- built-in demo datasets (small, virsliga, epl).

---

## Features
- Generates a schedule for a selected dataset
- Solves using Timefold Solver (HardSoftScore)
- REST API endpoints to:
  - start solving
  - poll status (score + validity)
  - fetch final match list
- Browser UI at http://localhost:8080/
  - dataset selector
  - solve button
  - round filter + “last round” shortcut
  - CSV export

---

## Datasets
Supported type values for /api/schedule/solve:
- small – 6 teams, 2 cycles (demo)
- virsliga – 10 teams, 4 cycles (shared stadiums, starts early March)
- epl – 20 teams, 2 cycles (starts late August)

---

## Constraints (overview)
Hard constraints (must be satisfied):
- H1: A team cannot play more than once on the same date
- H2: Stadium overlap forbidden (same stadium + same date + same timeslot)
- H3: Max 1 match per stadium per day
- H4 (simplified): European competition teams cannot play domestic matches on Tue/Wed during their European weeks
- H6: Last round matches must be played on Sunday 15:00
- H8: Minimum 2 rest days between matches of the same team

Soft constraints (quality optimization):
- Penalize midweek matches (Tue/Wed) (EPL only – Virslīga is weekend-only)
- Penalize Friday/Monday matches

---

## Tech Stack
- Java 21
- Maven
- Spring Boot 3.2.x
- Timefold Solver
- H2 in-memory database (default)

---

## Prerequisites
- JDK 21 installed
- Maven 3.9+ installed (mvn available in PATH)

Verify installation:
java -version
javac -version
mvn -v

---

## Run (Windows PowerShell)
Navigate to the Maven project directory (where pom.xml is located):

cd "C:\path\to\football_schedule_timefold\footbal-schedule-timefold"
mvn spring-boot:run

When you see:
Tomcat started on port 8080
Started Application

Open:
UI: http://localhost:8080/
API base: http://localhost:8080/api/schedule

Stop the server with Ctrl + C.

---

## REST API
Base path: /api/schedule

1) Start solving
POST /api/schedule/solve
Request body: { "type": "virsliga" }
Response: { "scheduleId": 1, "status": "SOLVING" }

PowerShell example:
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/schedule/solve" -ContentType "application/json" -Body '{"type":"virsliga"}'

2) Poll status
GET /api/schedule/status/{id}
Example response: { "status": "SOLVED", "score": "0hard/-50soft", "valid": true }

3) Get result matches
GET /api/schedule/result/{id}

---

## Browser UI
Available at: http://localhost:8080/
The UI calls API endpoints, renders matches in a table and allows CSV export.
UI file location: src/main/resources/static/index.html

---

## Solver configuration (per dataset)
Different datasets use different solver time limits via separate config files:
src/main/resources/solverConfig-small.xml
src/main/resources/solverConfig-virsliga.xml
src/main/resources/solverConfig-epl.xml

---

## Notes
- Solving runs asynchronously in a background thread.
- Results are stored in memory only (restart clears all data).
- Score interpretation: 0hard/... means no violations (feasible).

