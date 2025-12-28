package lv.football.scheduler.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class Timeslot {

    private LocalTime startTime;
    private DayOfWeek dayOfWeek;
    private boolean isMidweek;

    public Timeslot() {
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public boolean getIsMidweek() {
        return isMidweek;
    }

    public void setIsMidweek(boolean isMidweek) {
        this.isMidweek = isMidweek;
    }
}
