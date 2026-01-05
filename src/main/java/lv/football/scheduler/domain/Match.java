package lv.football.scheduler.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import java.util.UUID;

@PlanningEntity
public class Match {

    @PlanningId
    private UUID id;

    private Team homeTeam;
    private Team awayTeam;

    // Fixed by generator (not planned)
    private Round round;

    // Planned by solver
    @PlanningVariable(valueRangeProviderRefs = "timeslotRange")
    private Timeslot timeslot;

    protected Match() {
        // for Timefold/Jackson
    }

    public Match(Team homeTeam, Team awayTeam, Round round) {
        this.id = UUID.randomUUID();
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.round = round;
    }

    public UUID getId() { return id; }

    public Team getHomeTeam() { return homeTeam; }
    public Team getAwayTeam() { return awayTeam; }

    public void setHomeTeam(Team t) { this.homeTeam = t; }
    public void setAwayTeam(Team t) { this.awayTeam = t; }

    public Round getRound() { return round; }
    public void setRound(Round round) { this.round = round; }

    public Timeslot getTimeslot() { return timeslot; }
    public void setTimeslot(Timeslot timeslot) { this.timeslot = timeslot; }

    public Stadium getStadium() {
        return (homeTeam != null) ? homeTeam.getStadium() : null;
    }
}