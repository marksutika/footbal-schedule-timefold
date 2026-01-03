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

        SchedulingSolution problem = dataLoaderService.loadScheduleProblem(type);

        long id = counter.getAndIncrement();
        Job job = new Job(id, type);
        jobs.put(id, job);

        solverExecutor.submit(() -> {
            try {
                SchedulingSolution solved = scheduleService.solveSchedule(type, problem);
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
}