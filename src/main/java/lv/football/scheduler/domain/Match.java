package lv.football.scheduler.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import java.util.UUID;

@PlanningEntity
public class Match {

    @PlanningId
    private final UUID id;

    private final Team homeTeam;
    private final Team awayTeam;
    private final Round round;

    @PlanningVariable(valueRangeProviderRefs = "timeslotRange")
    private Timeslot timeslot;

    @PlanningVariable(valueRangeProviderRefs = "stadiumRange")
    private Stadium stadium;

    // 🔴 ОБЯЗАТЕЛЕН для Timefold
    protected Match() {
        this.id = UUID.randomUUID();
        this.homeTeam = null;
        this.awayTeam = null;
        this.round = null;
    }

    // 🔵 ОСНОВНОЙ конструктор
    public Match(Team homeTeam, Team awayTeam, Round round) {
        this.id = UUID.randomUUID();
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.round = round;
    }

    // ===== getters =====

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

    public Timeslot getTimeslot() {
        return timeslot;
    }

    public void setTimeslot(Timeslot timeslot) {
        this.timeslot = timeslot;
    }

    public Stadium getStadium() {
        return stadium;
    }

    public void setStadium(Stadium stadium) {
        this.stadium = stadium;
    }
}
