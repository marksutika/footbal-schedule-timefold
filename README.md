# Football Schedule – Timefold Solver

Spring Boot + Timefold Solver project for optimizing a football championship match schedule under hard and soft constraints.

## Features
- Generates a schedule for a selected test instance (e.g. `small`)
- Solves using Timefold Solver (`HardSoftScore`)
- REST API endpoints to:
  - start solving
  - poll status (score + validity)
  - fetch final match list
- Simple browser UI at http://localhost:8080/
  - Static HTML
  - Triggers solving
  - Displays results in a table

## Tech Stack
- Java 21
- Maven
- Spring Boot 3.2.x
- Timefold Solver
- H2 in-memory database (default)

## Prerequisites
- JDK 21 installed
- Maven 3.9+ installed (`mvn` available in PATH)

### Verify Installation
```
java -version
javac -version
mvn -v
```

## Run (Windows PowerShell)
Navigate to the Maven project directory (where `pom.xml` is located):
```
cd "C:\path\to\football_schedule_timefold\football-schedule-timefold"
mvn spring-boot:run
```

When you see:
```
Tomcat started on port 8080
Started Application
```

Open:
- UI: http://localhost:8080/
- API base: http://localhost:8080/api/schedule

Stop the server with Ctrl + C.

## REST API
Base path: `/api/schedule`

### 1) Start Solving
POST `/api/schedule/solve`

Request body:
```json
{ "type": "small" }
```

Response:
```json
{ "scheduleId": 1, "status": "SOLVING" }
```

PowerShell example:
```
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/schedule/solve" `
  -ContentType "application/json" `
  -Body '{"type":"small"}'
```

### 2) Poll Status
GET `/api/schedule/status/{id}`

Example response:
```json
{ "status": "SOLVED", "score": "0hard/-115soft", "valid": true }
```

PowerShell example:
```
Invoke-RestMethod "http://localhost:8080/api/schedule/status/1"
```

### 3) Get Result Matches
GET `/api/schedule/result/{id}`

PowerShell example:
```
Invoke-RestMethod "http://localhost:8080/api/schedule/result/1"
```

## Browser UI
Available at:
http://localhost:8080/

The UI:
- Calls POST `/api/schedule/solve`
- Polls GET `/api/schedule/status/{id}`
- Fetches GET `/api/schedule/result/{id}`
- Renders matches in a table

UI file location:
```
src/main/resources/static/index.html
```

## Notes
- Solving runs asynchronously in a background thread.
- Results are stored in memory only.
- Restarting the app clears all scheduleIds and results.

Score interpretation:
- `0hard/...` → no hard constraint violations
- Soft score improves as penalties decrease (closer to 0 is better)

## Troubleshooting

### “release version 21 not supported”
You are running Maven with an older JDK. Install JDK 21 and ensure `java` and `javac` point to it:
```
java -version
javac -version
```

### “No plugin found for prefix 'spring-boot'”
You are not in the directory containing `pom.xml`. `cd` into the correct project folder first.

### Timefold constraint name error
`cannot contain a package separator (/)`

Constraint names must not contain `/`. Use `-` or `or` instead.
