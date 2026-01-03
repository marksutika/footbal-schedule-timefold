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

    // Decision variables:
    @PlanningVariable(valueRangeProviderRefs = "roundRange")
    private Round round;

    @PlanningVariable(valueRangeProviderRefs = "timeslotRange")
    private Timeslot timeslot;

    // Required by Timefold (no-args constructor). Keep it protected.
    protected Match() {
        // Keep fields null; Timefold will set them.
    }

    public Match(UUID id, Team homeTeam, Team awayTeam) {
        this.id = (id != null) ? id : UUID.randomUUID();
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
    }

    public Match(Team homeTeam, Team awayTeam) {
        this(UUID.randomUUID(), homeTeam, awayTeam);
    }

    // ---------- Getters / setters ----------

    public UUID getId() {
        return id;
    }

    public Team getHomeTeam() {
        return homeTeam;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public Round getRound() {
        return round;
    }

    public void setRound(Round round) {
        this.round = round;
    }

    public Timeslot getTimeslot() {
        return timeslot;
    }

    public void setTimeslot(Timeslot timeslot) {
        this.timeslot = timeslot;
    }

    /**
     * Derived: stadium is defined by the home team.
     * If you want stadium to be a planning decision, remove this method and add a planning variable.
     */
    public Stadium getStadium() {
        return (homeTeam != null) ? homeTeam.getStadium() : null;
    }
}