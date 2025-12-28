package lv.football.scheduler.controller;

import lv.football.scheduler.domain.SchedulingSolution;
import lv.football.scheduler.domain.Match;
import lv.football.scheduler.service.ScheduleService;
import lv.football.scheduler.service.DataLoaderService;
import lv.football.scheduler.service.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private ValidationService validationService;

    private final ConcurrentHashMap<Long, SchedulingSolution> solvedSchedules = new ConcurrentHashMap<>();

    private final AtomicLong counter = new AtomicLong(1);

    /**
     * POST /api/schedule/solve
     */
    @PostMapping("/solve")
    public ResponseEntity<?> solve(@RequestBody Map<String, String> request) {
        String type = request.getOrDefault("type", "small");

        SchedulingSolution problem = dataLoaderService.loadScheduleProblem(type);

        long id = counter.getAndIncrement();

        new Thread(() -> {
            SchedulingSolution solution = scheduleService.solveSchedule(problem);
            solvedSchedules.put(id, solution);
        }).start();

        return ResponseEntity.ok(Map.of(
                "scheduleId", id,
                "status", "SOLVING"));
    }

    /**
     * GET /api/schedule/status/{id}
     */
    @GetMapping("/status/{id}")
    public ResponseEntity<?> status(@PathVariable long id) {
        if (!solvedSchedules.containsKey(id)) {
            return ResponseEntity.ok(Map.of(
                    "status", "SOLVING"));
        }

        SchedulingSolution solution = solvedSchedules.get(id);

        return ResponseEntity.ok(Map.of(
                "status", "SOLVED",
                "score", solution.getScore().toString(),
                "valid", validationService.validateSolution(solution)));
    }

    /**
     * GET /api/schedule/result/{id}
     */
    @GetMapping("/result/{id}")
    public ResponseEntity<?> result(@PathVariable long id) {
        if (!solvedSchedules.containsKey(id)) {
            return ResponseEntity.notFound().build();
        }

        SchedulingSolution solution = solvedSchedules.get(id);
        List<Match> matches = solution.getMatches();

        return ResponseEntity.ok(matches);
    }
}
