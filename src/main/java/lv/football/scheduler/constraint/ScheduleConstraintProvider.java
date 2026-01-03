package lv.football.scheduler.constraint;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import lv.football.scheduler.domain.EuropeanWeeks;
import lv.football.scheduler.domain.Match;
import lv.football.scheduler.domain.Round;
import lv.football.scheduler.domain.Stadium;
import lv.football.scheduler.domain.Team;
import lv.football.scheduler.domain.Timeslot;
import lv.football.scheduler.domain.LeagueRules;

import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import lv.football.scheduler.domain.SchedulingSolution;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class ScheduleConstraintProvider implements ConstraintProvider {

    private static final DayOfWeek LAST_ROUND_DAY = DayOfWeek.SUNDAY;
    private static final LocalTime LAST_ROUND_TIME = LocalTime.of(15, 0);

    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[]{
                // HARD
                teamPlaysAtMostOncePerDate(cf),       // H1
                noStadiumTimeslotOverlap(cf),         // H2
                maxOneMatchPerStadiumPerDay(cf),      // H3
                exactMatchesPerRoundHardWhenStrict(cf),
                exactMatchesPerRoundSoftWhenNotStrict(cf),
                minimumRestBetweenMatches(cf),        // H8
                noEuropeanTeamsOnEuropeanNights(cf),  // H4 (simplified)
                lastRoundAllMatchesSunday1500(cf),    // H6

                // SOFT
                discourageMidweek(cf),                // Sx
                discourageFridayAndMonday(cf)         // S2
        };
    }

    // ---------------- HARD ----------------

    /**
     * H1: A team cannot play two matches on the same calendar date.
     */
    private Constraint teamPlaysAtMostOncePerDate(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter(this::sharesTeam)
                .filter(this::sameMatchDate)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H1: Team plays twice on same date");
    }

    /**
     * H2: No two matches may overlap in the same stadium at the same date+timeslot.
     */
    private Constraint noStadiumTimeslotOverlap(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> {
                    Stadium s1 = m1.getStadium();
                    Stadium s2 = m2.getStadium();
                    Timeslot t1 = m1.getTimeslot();
                    Timeslot t2 = m2.getTimeslot();
                    if (s1 == null || s2 == null || t1 == null || t2 == null) return false;
                    if (!s1.equals(s2)) return false;
                    if (!t1.equals(t2)) return false;
                    return sameMatchDate(m1, m2);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H2: Stadium overlap same timeslot");
    }

    /**
     * H3: At most 1 match per stadium per date.
     */
    private Constraint maxOneMatchPerStadiumPerDay(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> {
                    Stadium s1 = m1.getStadium();
                    Stadium s2 = m2.getStadium();
                    if (s1 == null || s2 == null) return false;
                    if (!s1.equals(s2)) return false;
                    return sameMatchDate(m1, m2);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H3: Max one match per stadium per day");
    }

    /**
     * H8: Minimum 2 rest days between matches of the same team.
     */
    private Constraint minimumRestBetweenMatches(ConstraintFactory cf) {
        final int minRestDays = 2;

        return cf.forEachUniquePair(Match.class)
                .filter(this::sharesTeam)
                .filter((m1, m2) -> {
                    long days = daysBetweenMatchDates(m1, m2);
                    return days != Long.MAX_VALUE && days < minRestDays;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H8: Min 2 rest days between matches");
    }

    /**
     * H4 (simplified but meaningful):
     * If a team plays in UCL/UEL/UECL, it cannot play a domestic match on Tue/Wed
     * during the corresponding European week (week-of-Monday list).
     */
    private Constraint noEuropeanTeamsOnEuropeanNights(ConstraintFactory cf) {
        return cf.forEach(Match.class)
                .join(cf.forEach(EuropeanWeeks.class))
                .filter((m, weeks) -> {
                    if (m.getRound() == null || m.getTimeslot() == null) return false;

                    DayOfWeek dow = m.getTimeslot().getDayOfWeek();
                    if (!(dow == DayOfWeek.TUESDAY || dow == DayOfWeek.WEDNESDAY)) return false;

                    LocalDate date = matchDate(m);
                    if (date == null) return false;

                    LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

                    return forbiddenForTeam(m.getHomeTeam(), monday, weeks)
                            || forbiddenForTeam(m.getAwayTeam(), monday, weeks);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H4: No league match on European nights for European teams");
    }


    /**
 * H5: Enforce gameweek structure: each round must contain exactly (teams/2) matches.
 * Works for even team counts (Virslīga=10 -> 5, EPL=20 -> 10).
 */

    private Constraint exactMatchesPerRoundHardWhenStrict(ConstraintFactory cf) {
    return cf.forEach(Match.class)
            .filter(m -> m.getRound() != null)
            .groupBy(m -> m.getRound().getRoundNumber(), ConstraintCollectors.count())
            .join(cf.forEach(lv.football.scheduler.domain.LeagueRules.class))
            .filter((roundNumber, count, rules) ->
                    rules.isStrictRounds() && count != rules.getMatchesPerRound())
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H5: Exact matches per round (strict)");
}

private Constraint exactMatchesPerRoundSoftWhenNotStrict(ConstraintFactory cf) {
    return cf.forEach(Match.class)
            .filter(m -> m.getRound() != null)
            .groupBy(m -> m.getRound().getRoundNumber(), ConstraintCollectors.count())
            .join(cf.forEach(lv.football.scheduler.domain.LeagueRules.class))
            .filter((roundNumber, count, rules) ->
                    !rules.isStrictRounds() && count != rules.getMatchesPerRound())
            .penalize(HardSoftScore.ONE_SOFT)
            .asConstraint("S5: Encourage exact matches per round");
}
    /**
     * H6: Last round must be played on Sunday 15:00 (all matches simultaneous).
     *
     * Implementation:
     * - Determine max roundNumber from Round facts using a collector.
     * - Penalize each match in that last round whose timeslot is not Sunday 15:00.
     */
    private Constraint lastRoundAllMatchesSunday1500(ConstraintFactory cf) {
    return cf.forEach(Round.class)
            .groupBy(ConstraintCollectors.max(Round::getRoundNumber))
            .join(cf.forEach(Match.class))
            .filter((maxRoundNumber, match) -> match.getRound() != null
                    && match.getRound().getRoundNumber() == maxRoundNumber)
            .filter((maxRoundNumber, match) -> {
                Timeslot t = match.getTimeslot();
                // If timeslot not assigned yet, treat as violation (hard), but do not crash:
                if (t == null) return true;
                return t.getDayOfWeek() != LAST_ROUND_DAY || !LAST_ROUND_TIME.equals(t.getStartTime());
            })
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H6: Last round all matches Sunday 15:00");
}

    // ---------------- SOFT ----------------

    /**
     * Soft: discourage midweek matches (Tue/Wed slots marked as midweek in DataLoader).
     */
    private Constraint discourageMidweek(ConstraintFactory cf) {
        return cf.forEach(Match.class)
                .filter(m -> m.getTimeslot() != null && m.getTimeslot().getIsMidweek())
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Sx: Discourage midweek matches");
    }

    /**
     * S2: Too many matches on Friday/Monday is undesirable.
     */
    private Constraint discourageFridayAndMonday(ConstraintFactory cf) {
        return cf.forEach(Match.class)
                .filter(m -> m.getTimeslot() != null &&
                        (m.getTimeslot().getDayOfWeek() == DayOfWeek.FRIDAY
                                || m.getTimeslot().getDayOfWeek() == DayOfWeek.MONDAY))
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("S2: Discourage Friday or Monday matches");
    }

    // ---------------- Helpers ----------------

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
        Team h1 = m1.getHomeTeam();
        Team a1 = m1.getAwayTeam();
        if (h1 == null || a1 == null) return false;

        return h1.equals(m2.getHomeTeam()) ||
                h1.equals(m2.getAwayTeam()) ||
                a1.equals(m2.getHomeTeam()) ||
                a1.equals(m2.getAwayTeam());
    }

    private LocalDate matchDate(Match m) {
        if (m.getRound() == null || m.getTimeslot() == null) return null;
        if (m.getRound().getStartDate() == null || m.getTimeslot().getDayOfWeek() == null) return null;
        return m.getRound().dateFor(m.getTimeslot().getDayOfWeek());
    }

    private boolean sameMatchDate(Match m1, Match m2) {
        LocalDate d1 = matchDate(m1);
        LocalDate d2 = matchDate(m2);
        return d1 != null && d1.equals(d2);
    }

    private long daysBetweenMatchDates(Match m1, Match m2) {
        LocalDate d1 = matchDate(m1);
        LocalDate d2 = matchDate(m2);
        if (d1 == null || d2 == null) return Long.MAX_VALUE;
        return Math.abs(ChronoUnit.DAYS.between(d1, d2));
    }
}