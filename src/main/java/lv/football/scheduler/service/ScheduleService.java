package lv.football.scheduler.service;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import lv.football.scheduler.domain.SchedulingSolution;
import org.springframework.stereotype.Service;

@Service
public class ScheduleService {

    private final Solver<SchedulingSolution> solver;

    public ScheduleService() {
        SolverFactory<SchedulingSolution> solverFactory = SolverFactory.createFromXmlResource("solverConfig.xml");
        this.solver = solverFactory.buildSolver();
    }

    public SchedulingSolution solveSchedule(SchedulingSolution problem) {
        return solver.solve(problem);
    }
}
