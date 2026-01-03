package lv.football.scheduler.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;

import java.util.List;
import java.util.UUID;

@PlanningSolution
public class SchedulingSolution {

    @PlanningId
    private UUID id;

    @ProblemFactCollectionProperty
    private List<Team> teams;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "roundRange")
    private List<Round> rounds;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeslotRange")
    private List<Timeslot> timeslots;

    @ProblemFactCollectionProperty
    private List<Stadium> stadiums;

    @ProblemFactProperty
    private EuropeanWeeks europeanWeeks;

    @PlanningEntityCollectionProperty
    private List<Match> matches;

    @PlanningScore
    private HardSoftScore score;
    
    @ProblemFactProperty
    private LeagueRules leagueRules;

    public SchedulingSolution() {
        this.id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public List<Team> getTeams() { return teams; }
    public void setTeams(List<Team> teams) { this.teams = teams; }

    public List<Round> getRounds() { return rounds; }
    public void setRounds(List<Round> rounds) { this.rounds = rounds; }

    public List<Timeslot> getTimeslots() { return timeslots; }
    public void setTimeslots(List<Timeslot> timeslots) { this.timeslots = timeslots; }

    public List<Stadium> getStadiums() { return stadiums; }
    public void setStadiums(List<Stadium> stadiums) { this.stadiums = stadiums; }

    public EuropeanWeeks getEuropeanWeeks() { return europeanWeeks; }
    public void setEuropeanWeeks(EuropeanWeeks europeanWeeks) { this.europeanWeeks = europeanWeeks; }

    public List<Match> getMatches() { return matches; }
    public void setMatches(List<Match> matches) { this.matches = matches; }

    public HardSoftScore getScore() { return score; }
    public void setScore(HardSoftScore score) { this.score = score; }

    public LeagueRules getLeagueRules() { return leagueRules; }
    public void setLeagueRules(LeagueRules leagueRules) { this.leagueRules = leagueRules; }
}