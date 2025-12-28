package lv.football.scheduler.domain;

import ai.timefold.solver.core.api.domain.solution.*;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.List;

@PlanningSolution
public class SchedulingSolution {

    // ===== PROBLEM FACTS =====

    @ProblemFactCollectionProperty
    private List<Team> teams;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "roundRange")
    private List<Round> rounds;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "stadiumRange")
    private List<Stadium> stadiums;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeslotRange")
    private List<Timeslot> timeslots;

    // ===== PLANNING ENTITIES =====

    @PlanningEntityCollectionProperty
    private List<Match> matches;

    // ===== SCORE =====

    @PlanningScore
    private HardSoftScore score;

    // ===== GETTERS / SETTERS =====

    public List<Team> getTeams() {
        return teams;
    }

    public void setTeams(List<Team> teams) {
        this.teams = teams;
    }

    public List<Round> getRounds() {
        return rounds;
    }

    public void setRounds(List<Round> rounds) {
        this.rounds = rounds;
    }

    public List<Stadium> getStadiums() {
        return stadiums;
    }

    public void setStadiums(List<Stadium> stadiums) {
        this.stadiums = stadiums;
    }

    public List<Timeslot> getTimeslots() {
        return timeslots;
    }

    public void setTimeslots(List<Timeslot> timeslots) {
        this.timeslots = timeslots;
    }

    public List<Match> getMatches() {
        return matches;
    }

    public void setMatches(List<Match> matches) {
        this.matches = matches;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
