package lv.football.scheduler.service;

import lv.football.scheduler.domain.*;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

@Service
public class ValidationService {

    private static final DayOfWeek LAST_ROUND_DAY = DayOfWeek.SUNDAY;
    private static final LocalTime LAST_ROUND_TIME = LocalTime.of(15, 0);

    public boolean validateSolution(SchedulingSolution solution) {
        return allMatchesPlanned(solution)
                && noTeamPlaysTwiceSameDay(solution)          // H1
                && noStadiumTimeslotOverlap(solution)         // H2
                && maxOneMatchPerStadiumPerDay(solution)      // H3
                && minRestDays(solution, 2)                   // H8
                && noEuropeanTeamsOnEuropeanNights(solution)  // H4 (will be inactive if no Tue/Wed slots)
                && lastRoundAllSunday1500(solution);          // H6 (GW38)
    }

    private boolean allMatchesPlanned(SchedulingSolution solution) {
        for (Match m : solution.getMatches()) {
            if (m.getRound() == null || m.getTimeslot() == null) return false;
        }
        return true;
    }

    private boolean noTeamPlaysTwiceSameDay(SchedulingSolution solution) {
        Set<String> played = new HashSet<>();
        for (Match m : solution.getMatches()) {
            LocalDate date = matchDate(m);
            if (date == null) continue;
            String hk = m.getHomeTeam().getName() + "|" + date;
            String ak = m.getAwayTeam().getName() + "|" + date;
            if (!played.add(hk) || !played.add(ak)) return false;
        }
        return true;
    }

    private boolean noStadiumTimeslotOverlap(SchedulingSolution solution) {
        Set<String> used = new HashSet<>();
        for (Match m : solution.getMatches()) {
            Stadium s = m.getStadium();
            LocalDate date = matchDate(m);
            Timeslot t = m.getTimeslot();
            if (s == null || date == null || t == null) continue;

            String key = s.getName() + "|" + date + "|" + t.getDayOfWeek() + "|" + t.getStartTime();
            if (!used.add(key)) return false;
        }
        return true;
    }

    private boolean maxOneMatchPerStadiumPerDay(SchedulingSolution solution) {
        Set<String> used = new HashSet<>();
        for (Match m : solution.getMatches()) {
            Stadium s = m.getStadium();
            LocalDate date = matchDate(m);
            if (s == null || date == null) continue;

            String key = s.getName() + "|" + date;
            if (!used.add(key)) return false;
        }
        return true;
    }

    private boolean minRestDays(SchedulingSolution solution, int minDays) {
        var matches = solution.getMatches();
        for (int i = 0; i < matches.size(); i++) {
            for (int j = i + 1; j < matches.size(); j++) {
                Match m1 = matches.get(i);
                Match m2 = matches.get(j);
                if (!sharesTeam(m1, m2)) continue;

                LocalDate d1 = matchDate(m1);
                LocalDate d2 = matchDate(m2);
                if (d1 == null || d2 == null) continue;

                long days = Math.abs(ChronoUnit.DAYS.between(d1, d2));
                if (days < minDays) return false;
            }
        }
        return true;
    }

    private boolean noEuropeanTeamsOnEuropeanNights(SchedulingSolution solution) {
        EuropeanWeeks weeks = solution.getEuropeanWeeks();
        if (weeks == null) return true;

        for (Match m : solution.getMatches()) {
            Timeslot t = m.getTimeslot();
            if (t == null || m.getRound() == null) continue;

            DayOfWeek dow = t.getDayOfWeek();
            if (!(dow == DayOfWeek.TUESDAY || dow == DayOfWeek.WEDNESDAY)) continue;

            LocalDate date = matchDate(m);
            if (date == null) continue;

            LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (forbiddenForTeam(m.getHomeTeam(), monday, weeks)) return false;
            if (forbiddenForTeam(m.getAwayTeam(), monday, weeks)) return false;
        }
        return true;
    }

    private boolean lastRoundAllSunday1500(SchedulingSolution solution) {
        // This enforces the “GW38 all Sunday 15:00” rule for EPL.
        // For Virsliga it will also enforce the last round similarly if rounds size is 38+;
        // if you want it only for EPL, tell me and I’ll gate by LeagueRules/teams count.
        int maxRound = 0;
        for (Round r : solution.getRounds()) {
            maxRound = Math.max(maxRound, r.getRoundNumber());
        }

        for (Match m : solution.getMatches()) {
            if (m.getRound() == null || m.getTimeslot() == null) continue;
            if (m.getRound().getRoundNumber() != maxRound) continue;

            Timeslot t = m.getTimeslot();
            if (t.getDayOfWeek() != LAST_ROUND_DAY) return false;
            if (!LAST_ROUND_TIME.equals(t.getStartTime())) return false;
        }
        return true;
    }

    private boolean forbiddenForTeam(Team team, LocalDate monday, EuropeanWeeks weeks) {
        if (team == null) return false;
        return switch (team.getEuropeanCupParticipation()) {
            case UCL -> weeks.getUclMondays().contains(monday);
            case UEL -> weeks.getUelMondays().contains(monday);
            case UECL -> weeks.getUeclMondays().contains(monday);
            default -> false;
        };
    }

    private boolean sharesTeam(Match m1, Match m2) {
        return m1.getHomeTeam().equals(m2.getHomeTeam())
                || m1.getHomeTeam().equals(m2.getAwayTeam())
                || m1.getAwayTeam().equals(m2.getHomeTeam())
                || m1.getAwayTeam().equals(m2.getAwayTeam());
    }

    private LocalDate matchDate(Match m) {
        if (m.getRound() == null || m.getTimeslot() == null) return null;
        return m.getRound().dateFor(m.getTimeslot().getDayOfWeek());
    }
}