package lv.football.scheduler.service;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import lv.football.scheduler.domain.SchedulingSolution;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScheduleService {

    // Cache factories (creating SolverFactory is relatively expensive).
    private final Map<String, SolverFactory<SchedulingSolution>> solverFactoryByType = new ConcurrentHashMap<>();

    public SchedulingSolution solveSchedule(String type, SchedulingSolution problem) {
        SolverFactory<SchedulingSolution> factory = solverFactoryByType.computeIfAbsent(
                normalizeType(type),
                t -> SolverFactory.createFromXmlResource(configFileForType(t))
        );

        // Build a new solver per call (Solver is not thread-safe)
        Solver<SchedulingSolution> solver = factory.buildSolver();
        return solver.solve(problem);
    }

    /*
    public SchedulingSolution solveSchedule(String type, String strategy, Integer secondsLimit, SchedulingSolution problem) {
    String variant = (strategy == null) ? "default" : strategy;
    String configResource = configFileFor(type, variant);
    SolverFactory<SchedulingSolution> factory = solverFactoryByType.computeIfAbsent(
            configResource,
            k -> SolverFactory.createFromXmlResource(configResource)
    );
    Solver<SchedulingSolution> solver = factory.buildSolver();
    return solver.solve(problem);
}

private String configFileFor(String type, String variant) {
    // variant: "ch", "tabu", "la" (late acceptance)
    if ("tabu".equals(variant)) return "solverConfig-tabu.xml";
    if ("la".equals(variant) || "lateAcceptance".equals(variant)) return "solverConfig-la.xml";
    if ("ch".equals(variant) || "construction".equals(variant)) return "solverConfig-ch.xml";
    // fallback: keep old per-type mapping (if you prefer)
    return switch (type) {
        case "virsliga" -> "solverConfig-virsliga.xml";
        case "epl" -> "solverConfig-epl.xml";
        default -> "solverConfig-small.xml";
    };
}
    */

    private String normalizeType(String type) {
        if (type == null) return "small";
        return switch (type) {
            case "virsliga" -> "virsliga";
            case "epl" -> "epl";
            default -> "small";
        };
    }

    private String configFileForType(String type) {
        return switch (type) {
            case "virsliga" -> "solverConfig-virsliga.xml";
            case "epl" -> "solverConfig-epl.xml";
            default -> "solverConfig-small.xml";
        };
    }
}