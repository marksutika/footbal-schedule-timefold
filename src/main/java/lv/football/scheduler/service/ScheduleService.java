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
                t -> SolverFactory.createFromXmlResource(configFileForType(t)));

        // Build a new solver per call (Solver is not thread-safe)
        Solver<SchedulingSolution> solver = factory.buildSolver();
        return solver.solve(problem);
    }

    /**
     * Solve using a specific solver variant/config file.
     * Variant values: "tabu", "lateAcceptance", "hillClimbing",
     * "simulatedAnnealing", "constructionHeuristic" (or null for default per-type
     * config)
     */
    public SchedulingSolution solveSchedule(String type, String variant, SchedulingSolution problem) {
        String configResource = configFileFor(type, variant);
        SolverFactory<SchedulingSolution> factory = solverFactoryByType.computeIfAbsent(
                configResource,
                k -> SolverFactory.createFromXmlResource(configResource));
        Solver<SchedulingSolution> solver = factory.buildSolver();
        SchedulingSolution solution = solver.solve(problem);

        // Post-process construction heuristic solutions to remove trivial
        // forbidden-date placements
        // Post-process to remove forbidden-date placements (safety net for all
        // variants)
        try {
            fixForbiddenDates(solution);
        } catch (Exception ex) {
            System.err.println("Failed to post-process forbidden dates: " + ex);
        }

        return solution;
    }

    /**
     * Naive fixer: for matches landing on forbidden dates, try to move them to the
     * nearest
     * timeslot/round that doesn't produce a forbidden date. This is a greedy
     * post-processing
     * step (not optimal) intended to ensure the construction heuristic result
     * respects H9.
     */
    private void fixForbiddenDates(SchedulingSolution solution) {
        if (solution == null)
            return;
        var forbidden = solution.getForbiddenDates();
        if (forbidden == null || forbidden.isEmpty())
            return;
        var rounds = solution.getRounds();
        var timeslots = solution.getTimeslots();
        if (rounds == null || rounds.isEmpty() || timeslots == null || timeslots.isEmpty())
            return;

        // simple helper to compute match date for (round, timeslot)
        java.util.function.BiFunction<lv.football.scheduler.domain.Round, lv.football.scheduler.domain.Timeslot, java.time.LocalDate> dateFor = (
                r, t) -> {
            if (r == null || t == null)
                return null;
            return r.dateFor(t.getDayOfWeek());
        };

        for (var match : solution.getMatches()) {
            if (match.getRound() == null || match.getTimeslot() == null)
                continue;
            java.time.LocalDate md = dateFor.apply(match.getRound(), match.getTimeslot());
            if (md != null && forbidden.contains(md)) {
                boolean moved = false;
                // try same round different timeslot
                for (var ts : timeslots) {
                    java.time.LocalDate cand = dateFor.apply(match.getRound(), ts);
                    if (cand != null && !forbidden.contains(cand)) {
                        match.setTimeslot(ts);
                        moved = true;
                        break;
                    }
                }
                if (moved)
                    continue;

                // try subsequent rounds (within small window)
                for (var r : rounds) {
                    for (var ts : timeslots) {
                        java.time.LocalDate cand = dateFor.apply(r, ts);
                        if (cand != null && !forbidden.contains(cand)) {
                            match.setRound(r);
                            match.setTimeslot(ts);
                            moved = true;
                            break;
                        }
                    }
                    if (moved)
                        break;
                }
            }
        }
    }

    /*
     * public SchedulingSolution solveSchedule(String type, String strategy, Integer
     * secondsLimit, SchedulingSolution problem) {
     * String variant = (strategy == null) ? "default" : strategy;
     * String configResource = configFileFor(type, variant);
     * SolverFactory<SchedulingSolution> factory =
     * solverFactoryByType.computeIfAbsent(
     * configResource,
     * k -> SolverFactory.createFromXmlResource(configResource)
     * );
     * Solver<SchedulingSolution> solver = factory.buildSolver();
     * return solver.solve(problem);
     * }
     * 
     * private String configFileFor(String type, String variant) {
     * // variant: "ch", "tabu", "la" (late acceptance)
     * if ("tabu".equals(variant)) return "solverConfig-tabu.xml";
     * if ("la".equals(variant) || "lateAcceptance".equals(variant)) return
     * "solverConfig-la.xml";
     * if ("ch".equals(variant) || "construction".equals(variant)) return
     * "solverConfig-ch.xml";
     * // fallback: keep old per-type mapping (if you prefer)
     * return switch (type) {
     * case "virsliga" -> "solverConfig-virsliga.xml";
     * case "epl" -> "solverConfig-epl.xml";
     * default -> "solverConfig-small.xml";
     * };
     * }
     */

    private String normalizeType(String type) {
        if (type == null)
            return "small";
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

    private String configFileFor(String type, String variant) {
        if (variant == null)
            return configFileForType(type);
        return switch (variant) {
            case "tabu" -> "solverConfig-tabu.xml";
            case "lateAcceptance", "la" -> "solverConfig-la.xml";
            case "hillClimbing", "hc" -> "solverConfig-hc.xml";
            case "simulatedAnnealing", "sa" -> "solverConfig-sa.xml";
            case "constructionHeuristic", "ch" -> "solverConfig-ch.xml";
            default -> configFileForType(type);
        };
    }
}