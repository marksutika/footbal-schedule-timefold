package lv.football.scheduler.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Problem fact / planning value: represents a "round/week" in the championship calendar.
 */
public class Round {

    @PlanningId
    private UUID id;

    private int roundNumber;

    /**
     * Start date of the round (e.g. the first day of that round's window).
     * Your constraints can compute actual match dates using this + timeslot.dayOfWeek.
     */
    private LocalDate startDate;

    // Required by Jackson/Timefold
    public Round() {
        this.id = UUID.randomUUID();
    }

    public Round(int roundNumber, LocalDate startDate) {
        this.id = UUID.randomUUID();
        this.roundNumber = roundNumber;
        this.startDate = startDate;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) { // for deserialization if needed
        this.id = id;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    /**
     * Helper: compute the date in this round for a given day-of-week.
     * Assumes startDate is within the same week window as the dayOfWeek used by timeslots.
     */
    public LocalDate dateFor(DayOfWeek dayOfWeek) {
        if (startDate == null || dayOfWeek == null) return null;

        int startDow = startDate.getDayOfWeek().getValue(); // 1..7
        int targetDow = dayOfWeek.getValue();               // 1..7
        return startDate.plusDays(targetDow - startDow);
    }

    @Override
    public String toString() {
        return "Round " + roundNumber + " (" + startDate + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Round round)) return false;
        return Objects.equals(id, round.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}