package lv.football.scheduler.controller;

import lv.football.scheduler.domain.Match;
import lv.football.scheduler.domain.SchedulingSolution;
import lv.football.scheduler.service.DataLoaderService;
import lv.football.scheduler.service.ScheduleService;
import lv.football.scheduler.service.ValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final DataLoaderService dataLoaderService;
    private final ValidationService validationService;
    private final ExecutorService solverExecutor;

    private final AtomicLong counter = new AtomicLong(1);

    // Keep job state in memory.
    private final ConcurrentHashMap<Long, Job> jobs = new ConcurrentHashMap<>();

    public ScheduleController(ScheduleService scheduleService,
                              DataLoaderService dataLoaderService,
                              ValidationService validationService,
                              ExecutorService solverExecutor) {
        this.scheduleService = scheduleService;
        this.dataLoaderService = dataLoaderService;
        this.validationService = validationService;
        this.solverExecutor = solverExecutor;
    }

    public enum JobStatus { SOLVING, SOLVED, FAILED }

    public static final class Job {
        public final long id;
        public final String type;
        public final Instant createdAt;

        public volatile JobStatus status = JobStatus.SOLVING;
        public volatile SchedulingSolution solution;
        public volatile String error;

        public Job(long id, String type) {
            this.id = id;
            this.type = type;
            this.createdAt = Instant.now();
        }
    }

    /**
     * POST /api/schedule/solve
     * Body: { "type": "small" }
     */
    @PostMapping("/solve")
    public ResponseEntity<Map<String, Object>> solve(@RequestBody(required = false) Map<String, String> request) {
        String type = (request == null) ? "small" : request.getOrDefault("type", "small");
        String variant = (request == null) ? null : request.get("variant");

        SchedulingSolution problem = dataLoaderService.loadScheduleProblem(type);

        long id = counter.getAndIncrement();
        Job job = new Job(id, type);
        jobs.put(id, job);

        final String usedVariant = variant;
        solverExecutor.submit(() -> {
            try {
                SchedulingSolution solved = scheduleService.solveSchedule(type, usedVariant, problem);
                job.solution = solved;
                job.status = JobStatus.SOLVED;
            } catch (Exception e) {
                job.status = JobStatus.FAILED;
                job.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        });

        return ResponseEntity.ok(Map.of(
                "scheduleId", id,
                "status", job.status.name()
        ));
    }

    /**
     * GET /api/schedule/status/{id}
     */
    @GetMapping("/status/{id}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable long id) {
        Job job = jobs.get(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.status == JobStatus.SOLVED && job.solution != null) {
            return ResponseEntity.ok(Map.of(
                    "status", "SOLVED",
                    "score", job.solution.getScore() == null ? null : job.solution.getScore().toString(),
                    "valid", validationService.validateSolution(job.solution)
            ));
        }

        if (job.status == JobStatus.FAILED) {
            return ResponseEntity.ok(Map.of(
                    "status", "FAILED",
                    "error", job.error
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "SOLVING"
        ));
    }

    /**
     * GET /api/schedule/result/{id}
     */
    @GetMapping("/result/{id}")
    public ResponseEntity<?> result(@PathVariable long id) {
        Job job = jobs.get(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.status == JobStatus.FAILED) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "FAILED",
                    "error", job.error
            ));
        }

        if (job.status != JobStatus.SOLVED || job.solution == null) {
            // Not ready yet.
            return ResponseEntity.status(202).body(Map.of("status", "SOLVING"));
        }

        List<Match> matches = job.solution.getMatches();
        return ResponseEntity.ok(matches);
    }

    /**
     * Optional: list all jobs (useful for debugging/UI).
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> jobs() {
        return ResponseEntity.ok(
                jobs.values().stream()
                        .sorted((a, b) -> Long.compare(a.id, b.id))
                        .map(j -> Map.<String, Object>of(
                                "id", j.id,
                                "type", j.type,
                                "status", j.status.name(),
                                "createdAt", j.createdAt.toString()
                        ))
                        .toList()
        );
    }

    /**
     * Optional: delete a job to free memory.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        Job removed = jobs.remove(id);
        if (removed == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Explain a solved job from in-memory solution. Returns simple counts per constraint.
     */
    @GetMapping("/explain/{id}")
    public ResponseEntity<?> explainJob(@PathVariable long id) {
        Job job = jobs.get(id);
        if (job == null) return ResponseEntity.notFound().build();
        if (job.status != JobStatus.SOLVED || job.solution == null) return ResponseEntity.status(202).body(Map.of("status","NOT_READY"));

        var matches = job.solution.getMatches();
        Map<String,Integer> hard = new LinkedHashMap<>();
        hard.put("H1_teamTwiceSameDay", countH1(matches));
        hard.put("H2_stadiumOverlapTimeslot", countH2(matches));
        hard.put("H3_maxOneMatchPerStadiumPerDay", countH3(matches));
        hard.put("H8_minRestDays", countH8(matches));
        hard.put("H9_forbiddenDates", countH9(matches, job.solution.getForbiddenDates()));

        Map<String,Integer> soft = new LinkedHashMap<>();
        soft.put("S2_fridayOrMonday", countS2(matches));
        soft.put("S3_simultaneousMatches", countS3(matches));
        soft.put("S4_veryLateKickoffs", countS4(matches));

        return ResponseEntity.ok(Map.of("hardViolations", hard, "softPenalties", soft));
    }

    // --- simple counters for domain Match objects ---
    private int countH1(List<Match> matches) {
        int cnt = 0;
        for (int i=0;i<matches.size();i++) for (int j=i+1;j<matches.size();j++) {
            if (sharesTeam(matches.get(i), matches.get(j)) && sameMatchDate(matches.get(i), matches.get(j))) cnt++;
        }
        return cnt;
    }
    private int countH2(List<Match> matches) {
        int cnt=0; for (int i=0;i<matches.size();i++) for (int j=i+1;j<matches.size();j++) {
            if (sameStadium(matches.get(i), matches.get(j)) && sameMatchDate(matches.get(i), matches.get(j)) && sameTimeslot(matches.get(i), matches.get(j))) cnt++;
        } return cnt;
    }
    private int countH3(List<Match> matches) {
        int cnt=0; for (int i=0;i<matches.size();i++) for (int j=i+1;j<matches.size();j++) {
            if (sameStadium(matches.get(i), matches.get(j)) && sameMatchDate(matches.get(i), matches.get(j))) cnt++;
        } return cnt;
    }
    private int countH8(List<Match> matches) {
        int cnt=0; for (int i=0;i<matches.size();i++) for (int j=i+1;j<matches.size();j++) {
            if (sharesTeam(matches.get(i), matches.get(j))) {
                long days = daysBetweenMatchDates(matches.get(i), matches.get(j));
                if (days!=Long.MAX_VALUE && days<2) cnt++;
            }
        } return cnt;
    }
    private int countH9(List<Match> matches, List<java.time.LocalDate> forbidden) {
        if (forbidden==null) return 0; int cnt=0; for (Match m: matches) { java.time.LocalDate d1 = matchDate(m); java.time.LocalDate d2 = roundStartDate(m); if ((d1!=null && forbidden.contains(d1)) || (d2!=null && forbidden.contains(d2))) cnt++; } return cnt;
    }
    private java.time.LocalDate roundStartDate(Match m) { if (m==null||m.getRound()==null) return null; return m.getRound().getStartDate(); }
    private int countS2(List<Match> matches) { int cnt=0; for (Match m:matches) if (m.getTimeslot()!=null) { var d = m.getTimeslot().getDayOfWeek(); if (d==java.time.DayOfWeek.FRIDAY||d==java.time.DayOfWeek.MONDAY) cnt++; } return cnt; }
    private int countS3(List<Match> matches) { int cnt=0; for (int i=0;i<matches.size();i++) for (int j=i+1;j<matches.size();j++) if (sameMatchDate(matches.get(i), matches.get(j)) && sameTimeslot(matches.get(i), matches.get(j))) cnt++; return cnt; }
    private int countS4(List<Match> matches) { int cnt=0; for (Match m:matches) if (m.getTimeslot()!=null) { var d = m.getTimeslot().getDayOfWeek(); var t = m.getTimeslot().getStartTime(); boolean lateFriMon = ((d==java.time.DayOfWeek.FRIDAY||d==java.time.DayOfWeek.MONDAY) && java.time.LocalTime.of(21,30).equals(t)); boolean lateSun = (d==java.time.DayOfWeek.SUNDAY && java.time.LocalTime.of(21,0).equals(t)); if (lateFriMon||lateSun) cnt++; } return cnt; }

    // reuse helpers already defined in this class file (private methods below)
    private boolean sharesTeam(Match m1, Match m2) {
        var h1 = m1.getHomeTeam(); var a1 = m1.getAwayTeam(); if (h1==null||a1==null) return false; return h1.equals(m2.getHomeTeam())||h1.equals(m2.getAwayTeam())||a1.equals(m2.getHomeTeam())||a1.equals(m2.getAwayTeam());
    }
    private boolean sameMatchDate(Match m1, Match m2) { java.time.LocalDate d1 = matchDate(m1); java.time.LocalDate d2 = matchDate(m2); return d1!=null && d1.equals(d2); }
    private java.time.LocalDate matchDate(Match m) { if (m==null||m.getRound()==null||m.getTimeslot()==null) return null; return m.getRound().dateFor(m.getTimeslot().getDayOfWeek()); }
    private boolean sameStadium(Match m1, Match m2) { if (m1==null||m2==null) return false; if (m1.getStadium()==null||m2.getStadium()==null) return false; return m1.getStadium().equals(m2.getStadium()); }
    private boolean sameTimeslot(Match m1, Match m2) { if (m1==null||m2==null) return false; if (m1.getTimeslot()==null||m2.getTimeslot()==null) return false; return m1.getTimeslot().equals(m2.getTimeslot()); }
    private long daysBetweenMatchDates(Match m1, Match m2) { java.time.LocalDate d1 = matchDate(m1); java.time.LocalDate d2 = matchDate(m2); if (d1==null||d2==null) return Long.MAX_VALUE; return Math.abs(java.time.temporal.ChronoUnit.DAYS.between(d1,d2)); }
}