package lv.football.scheduler.benchmark;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lv.football.scheduler.service.DataLoaderService;
import lv.football.scheduler.domain.SchedulingSolution;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        DataLoaderService loader = new DataLoaderService();

        Map<String, String> variants = new LinkedHashMap<>();
        variants.put("tabu", "solverConfig-tabu.xml");
        variants.put("lateAcceptance", "solverConfig-la.xml");
        variants.put("hillClimbing", "solverConfig-hc.xml");
        variants.put("simulatedAnnealing", "solverConfig-sa.xml");
        variants.put("constructionHeuristic", "solverConfig-ch.xml");

        String[] datasets = new String[]{"small", "virsliga", "epl"};

        File out = new File("benchmark-results.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            pw.println("dataset,variant,timeMillis,hardScore,softScore,teams,matches");

            ObjectMapper omGlobal = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            for (String ds : datasets) {
                for (Map.Entry<String, String> e : variants.entrySet()) {
                    System.out.println("Running dataset=" + ds + " variant=" + e.getKey());

                    SchedulingSolution problem = loader.loadScheduleProblem(ds);

                    SolverFactory<SchedulingSolution> factory = SolverFactory.createFromXmlResource(e.getValue());
                    Solver<SchedulingSolution> solver = factory.buildSolver();

                    long start = System.nanoTime();
                    SchedulingSolution solution = solver.solve(problem);
                    long end = System.nanoTime();

                    long millis = (end - start) / 1_000_000;

                    // persist solution JSON for later inspection/explanation (write atomically)
                    try {
                        File solFile = new File(String.format("benchmark-solution-%s-%s.json", ds, e.getKey()));
                        File tmp = new File(solFile.getAbsolutePath() + ".tmp");
                        omGlobal.writerWithDefaultPrettyPrinter().writeValue(tmp, solution);
                        try {
                            Files.move(tmp.toPath(), solFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        } catch (Exception moveEx) {
                            // ATOMIC_MOVE may not be supported on some file systems (OneDrive etc.), fall back to non-atomic move
                            Files.move(tmp.toPath(), solFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to write solution JSON: " + ex);
                    }

                    String hard = "null";
                    String soft = "null";
                    if (solution.getScore() != null) {
                        hard = Integer.toString(solution.getScore().getHardScore());
                        soft = Integer.toString(solution.getScore().getSoftScore());
                    }

                    int teams = solution.getTeams() != null ? solution.getTeams().size() : 0;
                    int matches = solution.getMatches() != null ? solution.getMatches().size() : 0;

                    pw.printf("%s,%s,%d,%s,%s,%d,%d%n", ds, e.getKey(), millis, hard, soft, teams, matches);
                    pw.flush();

                    System.out.println(" -> " + millis + " ms, score=" + hard + "/" + soft);
                }
            }
        }

        System.out.println("Benchmark complete. Results: " + out.getAbsolutePath());
    }
}
