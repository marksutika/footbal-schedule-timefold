package lv.football.scheduler.constraint;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

import lv.football.scheduler.domain.EuropeanWeeks;
import lv.football.scheduler.domain.LeagueRules;
import lv.football.scheduler.domain.Match;
import lv.football.scheduler.domain.Round;
import lv.football.scheduler.domain.Stadium;
import lv.football.scheduler.domain.Team;
import lv.football.scheduler.domain.Timeslot;

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
        return new Constraint[] {
                // HARD
                h1_teamAtMostOncePerDay(cf),
                h2_noStadiumTimeslotOverlap(cf),
                h3_maxOneMatchPerStadiumPerDay(cf),
                h4_noEuropeanTeamsOnEuropeanNights(cf),
                h6_lastRoundAllSunday1500(cf),
                h8_minRestDays(cf),

                // ✅ THIS is the real forbidden-dates rule
                h9_noMatchesOnForbiddenDates(cf),

                // optional extra guard (can keep or remove)
                h9b_hardcodedNoMatchesOnKnownDates(cf),

                // SOFT
                s2_discourageFridayOrMonday(cf),
                s3_discourageTooManySimultaneousMatchesExceptLastRound(cf),
                s4_discourageVeryLateKickoffsSmallPenalty(cf)
        };
    }

    // ---------------- HARD ----------------

    private Constraint h1_teamAtMostOncePerDay(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter(this::sharesTeam)
                .filter(this::sameMatchDate)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H1: Team plays twice same day");
    }

    private Constraint h2_noStadiumTimeslotOverlap(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> {
                    Stadium s1 = m1.getStadium();
                    Stadium s2 = m2.getStadium();
                    Timeslot t1 = m1.getTimeslot();
                    Timeslot t2 = m2.getTimeslot();
                    if (s1 == null || s2 == null || t1 == null || t2 == null)
                        return false;
                    if (!s1.equals(s2))
                        return false;
                    if (!t1.equals(t2))
                        return false;
                    return sameMatchDate(m1, m2);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H2: Stadium overlap same timeslot");
    }

    private Constraint h3_maxOneMatchPerStadiumPerDay(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> {
                    Stadium s1 = m1.getStadium();
                    Stadium s2 = m2.getStadium();
                    if (s1 == null || s2 == null)
                        return false;
                    if (!s1.equals(s2))
                        return false;
                    return sameMatchDate(m1, m2);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H3: Max one match per stadium per day");
    }

    private Constraint h4_noEuropeanTeamsOnEuropeanNights(ConstraintFactory cf) {
        // Only matters if Tue/Wed slots exist.
        return cf.forEach(Match.class)
                .join(cf.forEach(EuropeanWeeks.class))
                .filter((m, weeks) -> {
                    Timeslot t = m.getTimeslot();
                    if (m.getRound() == null || t == null)
                        return false;

                    DayOfWeek dow = t.getDayOfWeek();
                    if (!(dow == DayOfWeek.TUESDAY || dow == DayOfWeek.WEDNESDAY))
                        return false;

                    LocalDate date = matchDate(m);
                    if (date == null)
                        return false;

                    LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    return forbiddenForTeam(m.getHomeTeam(), monday, weeks)
                            || forbiddenForTeam(m.getAwayTeam(), monday, weeks);
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H4: No match on European nights for European teams");
    }

    /**
     * H6 (ALL leagues): last round must be Sunday 15:00.
     * We compute the last round number using a collector.
     */
    private Constraint h6_lastRoundAllSunday1500(ConstraintFactory cf) {
        return cf.forEach(Round.class)
                .groupBy(ConstraintCollectors.max(Round::getRoundNumber))
                .join(cf.forEach(Match.class))
                .filter((maxRoundNumber, match) -> match.getRound() != null
                        && match.getRound().getRoundNumber() == maxRoundNumber)
                .filter((maxRoundNumber, match) -> {
                    Timeslot t = match.getTimeslot();
                    if (t == null)
                        return true;
                    return t.getDayOfWeek() != LAST_ROUND_DAY || !LAST_ROUND_TIME.equals(t.getStartTime());
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H6: Last round Sunday 15:00");
    }

    private Constraint h8_minRestDays(ConstraintFactory cf) {
        final int minRestDays = 2;
        return cf.forEachUniquePair(Match.class)
                .filter(this::sharesTeam)
                .filter((m1, m2) -> {
                    long days = daysBetweenMatchDates(m1, m2);
                    return days != Long.MAX_VALUE && days < minRestDays;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H8: Min rest days");
    }

    /**
     * ✅ H9: Forbid matches on solution.forbiddenDates.
     *
     * IMPORTANT: forbiddenDates MUST be @ProblemFactCollectionProperty in
     * SchedulingSolution
     * (у тебя уже так).
     *
     * We join Match with LocalDate facts by equality on computed match date.
     * This is efficient and correct.
     */
    private Constraint h9_noMatchesOnForbiddenDates(ConstraintFactory cf) {
        return cf.forEach(Match.class)
                .filter(m -> m.getRound() != null && m.getTimeslot() != null)
                .join(LocalDate.class)
                .filter((m, forbidden) -> {
                    LocalDate d1 = matchDate(m); // через dateFor(dayOfWeek)
                    LocalDate d2 = roundStartDate(m); // “сырая” дата раунда (то, что часто и показывается в UI)
                    return (d1 != null && d1.equals(forbidden))
                            || (d2 != null && d2.equals(forbidden));
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H9: No matches on forbidden dates");
    }

    // ---------------- SOFT ----------------

    private Constraint s2_discourageFridayOrMonday(ConstraintFactory cf) {
        return cf.forEach(Match.class)
                .filter(m -> m.getTimeslot() != null &&
                        (m.getTimeslot().getDayOfWeek() == DayOfWeek.FRIDAY
                                || m.getTimeslot().getDayOfWeek() == DayOfWeek.MONDAY))
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("S2: Discourage Friday or Monday");
    }

    private Constraint s3_discourageTooManySimultaneousMatchesExceptLastRound(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> m1.getTimeslot() != null && m2.getTimeslot() != null)
                .filter((m1, m2) -> sameMatchDate(m1, m2) && m1.getTimeslot().equals(m2.getTimeslot()))
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("S3: Discourage simultaneous matches");
    }

    /**
     * S4 (small penalty): only penalize "very late" kickoffs.
     * - Fri 21:30
     * - Mon 21:30
     * - (optional) Sun 21:00
     */
    private Constraint s4_discourageVeryLateKickoffsSmallPenalty(ConstraintFactory cf) {
        return cf.forEach(Match.class)
                .filter(m -> m.getTimeslot() != null)
                .filter(m -> {
                    DayOfWeek d = m.getTimeslot().getDayOfWeek();
                    LocalTime t = m.getTimeslot().getStartTime();

                    boolean lateFriMon = ((d == DayOfWeek.FRIDAY || d == DayOfWeek.MONDAY)
                            && t.equals(LocalTime.of(21, 30)));
                    boolean lateSunday = (d == DayOfWeek.SUNDAY && t.equals(LocalTime.of(21, 0)));

                    return lateFriMon || lateSunday;
                })
                .penalize(HardSoftScore.ofSoft(1))
                .asConstraint("S4: Discourage very late kickoffs");
    }

    // ---------------- Helpers ----------------

    private boolean forbiddenForTeam(Team team, LocalDate monday, EuropeanWeeks weeks) {
        if (team == null)
            return false;
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
        if (h1 == null || a1 == null)
            return false;

        return h1.equals(m2.getHomeTeam()) ||
                h1.equals(m2.getAwayTeam()) ||
                a1.equals(m2.getHomeTeam()) ||
                a1.equals(m2.getAwayTeam());
    }

    private LocalDate matchDate(Match m) {
        if (m.getRound() == null || m.getTimeslot() == null)
            return null;
        return m.getRound().dateFor(m.getTimeslot().getDayOfWeek());
    }

    private LocalDate roundStartDate(Match m) {
        if (m.getRound() == null)
            return null;
        // подставь правильный геттер, если у тебя он иначе называется
        return m.getRound().getStartDate();
    }

    private boolean sameMatchDate(Match m1, Match m2) {
        LocalDate d1 = matchDate(m1);
        LocalDate d2 = matchDate(m2);
        return d1 != null && d1.equals(d2);
    }

    private long daysBetweenMatchDates(Match m1, Match m2) {
        LocalDate d1 = matchDate(m1);
        LocalDate d2 = matchDate(m2);
        if (d1 == null || d2 == null)
            return Long.MAX_VALUE;
        return Math.abs(ChronoUnit.DAYS.between(d1, d2));
    }

    /**
     * Extra guard: some important public holidays should be forbidden regardless of
     * problem facts.
     * Можно удалить, если всё хранится в forbiddenDates.
     */
    private Constraint h9b_hardcodedNoMatchesOnKnownDates(ConstraintFactory cf) {
        return cf.forEach(Match.class)
                .join(cf.forEach(LeagueRules.class))
                .filter((m, rules) -> {
                    if (m.getRound() == null || m.getTimeslot() == null)
                        return false;
                    LocalDate md = matchDate(m);
                    if (md == null)
                        return false;
                    int mth = md.getMonthValue();
                    int day = md.getDayOfMonth();

                    if (rules.isStrictRounds()) {
                        // EPL-style: forbid winter holidays and New Year
                        if (mth == 1 && day == 1)
                            return true;
                        if (mth == 12 && (day == 24 || day == 25 || day == 26))
                            return true;
                        return false;
                    } else {
                        // Virsliga/others: forbid local holidays and test dates (April + Nov 18)
                        if (mth == 11 && day == 18)
                            return true;
                        if (mth == 4 && (day == 3 || day == 5))
                            return true;
                        return false;
                    }
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("H9b: No matches on common forbidden dates (per-league)");
    }
}
