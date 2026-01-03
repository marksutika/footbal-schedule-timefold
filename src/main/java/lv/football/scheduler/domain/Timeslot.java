package lv.football.scheduler.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Problem fact: a possible time slot that matches can be assigned to.
 * Recommended: immutable + stable ID.
 */
public class Timeslot {

    @PlanningId
    private UUID id;

    private DayOfWeek dayOfWeek;
    private LocalTime startTime;

    /**
     * True if this slot is considered "midweek" (organizationally less preferred).
     */
    private boolean midweek;

    // Required by Jackson/Timefold
    public Timeslot() {
        this.id = UUID.randomUUID();
    }

    public Timeslot(DayOfWeek dayOfWeek, LocalTime startTime, boolean midweek) {
        this.id = UUID.randomUUID();
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.midweek = midweek;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) { // for deserialization if needed
        this.id = id;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public boolean isMidweek() {
        return midweek;
    }

    // Keep old getter name too (so you don't have to change other code immediately)
    public boolean getIsMidweek() {
        return midweek;
    }

    public void setMidweek(boolean midweek) {
        this.midweek = midweek;
    }

    public void setIsMidweek(boolean midweek) {
        this.midweek = midweek;
    }

    @Override
    public String toString() {
        return dayOfWeek + " " + startTime + (midweek ? " (midweek)" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Timeslot other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}