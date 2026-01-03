package lv.football.scheduler.domain;

import java.time.LocalDate;
import java.util.Set;

public class EuropeanWeeks {
    private final Set<LocalDate> uclMondays;
    private final Set<LocalDate> uelMondays;
    private final Set<LocalDate> ueclMondays;

    public EuropeanWeeks(Set<LocalDate> uclMondays, Set<LocalDate> uelMondays, Set<LocalDate> ueclMondays) {
        this.uclMondays = uclMondays;
        this.uelMondays = uelMondays;
        this.ueclMondays = ueclMondays;
    }

    public Set<LocalDate> getUclMondays() { return uclMondays; }
    public Set<LocalDate> getUelMondays() { return uelMondays; }
    public Set<LocalDate> getUeclMondays() { return ueclMondays; }
}