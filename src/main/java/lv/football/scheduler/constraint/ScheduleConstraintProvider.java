package lv.football.scheduler.constraint;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import lv.football.scheduler.domain.Match;
import lv.football.scheduler.domain.Round;
import lv.football.scheduler.domain.Team;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[] {
                // HARD
                teamPlaysOncePerRound(cf),
                noStadiumOverlap(cf),
                maxOneMatchPerStadiumPerDay(cf),
                minimumRestBetweenMatches(cf),

                // SOFT
                avoidLongHomeAwayStreaks(cf),
                balanceMidweekMatches(cf)
        };
    }

    // =========================================================
    // HARD CONSTRAINTS
    // =========================================================

    /**
     * H1: Viena komanda nevar spēlēt divas reizes vienā kārtā
     */
    private Constraint teamPlaysOncePerRound(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> sameRound(m1, m2) &&
                        sharesTeam(m1, m2))
                .penalize(HardSoftScore.ofHard(1000))
                .asConstraint("H1: Team plays twice in same round");
    }

    /**
     * H2: Stadionā tikai viena spēle vienā dienā
     */
    private Constraint maxOneMatchPerStadiumPerDay(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> m1.getStadium() != null &&
                        m1.getStadium().equals(m2.getStadium()) &&
                        sameDay(m1, m2))
                .penalize(HardSoftScore.ofHard(500))
                .asConstraint("H2: Max one match per stadium per day");
    }

    /**
     * H3: Nav spēļu pārklāšanās stadionā (vienkāršots)
     */
    private Constraint noStadiumOverlap(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> m1.getStadium() != null &&
                        m1.getStadium().equals(m2.getStadium()) &&
                        sameRound(m1, m2) &&
                        m1.getTimeslot() != null &&
                        m1.getTimeslot().equals(m2.getTimeslot()))
                .penalize(HardSoftScore.ofHard(500))
                .asConstraint("H3: No stadium overlap");
    }

    /**
     * H4: Minimum 2 dienas starp vienas komandas spēlēm
     */
    private Constraint minimumRestBetweenMatches(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> sharesTeam(m1, m2) &&
                        daysBetween(m1, m2) < 2)
                .penalize(HardSoftScore.ofHard(300))
                .asConstraint("H4: Minimum rest days");
    }

    // =========================================================
    // SOFT CONSTRAINTS
    // =========================================================

    /**
     * S1: Izvairīties no garām mājas/izbraukuma sērijām
     */
    private Constraint avoidLongHomeAwayStreaks(ConstraintFactory cf) {
        return cf.forEachUniquePair(Match.class)
                .filter((m1, m2) -> sameTeamHomeAway(m1, m2) &&
                        daysBetween(m1, m2) <= 14)
                .penalize(HardSoftScore.ofSoft(10))
                .asConstraint("S1: Avoid long home/away streaks");
    }

    /**
     * S2: Balansēt vidus nedēļas spēles
     */
    private Constraint balanceMidweekMatches(ConstraintFactory cf) {
        return cf.forEach(Match.class)
                .filter(m -> m.getTimeslot() != null &&
                        m.getTimeslot().getIsMidweek())
                .reward(HardSoftScore.ofSoft(5))
                .asConstraint("S2: Balance midweek matches");
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private boolean sameRound(Match m1, Match m2) {
        return m1.getRound() != null &&
                m1.getRound().equals(m2.getRound());
    }

    private boolean sameDay(Match m1, Match m2) {
        LocalDate d1 = m1.getRound() != null ? m1.getRound().getStartDate() : null;
        LocalDate d2 = m2.getRound() != null ? m2.getRound().getStartDate() : null;
        return d1 != null && d1.equals(d2);
    }

    private boolean sharesTeam(Match m1, Match m2) {
        Team a = m1.getHomeTeam();
        Team b = m1.getAwayTeam();
        return a.equals(m2.getHomeTeam()) ||
                a.equals(m2.getAwayTeam()) ||
                b.equals(m2.getHomeTeam()) ||
                b.equals(m2.getAwayTeam());
    }

    private long daysBetween(Match m1, Match m2) {
        LocalDate d1 = m1.getRound() != null ? m1.getRound().getStartDate() : null;
        LocalDate d2 = m2.getRound() != null ? m2.getRound().getStartDate() : null;
        if (d1 == null || d2 == null)
            return Long.MAX_VALUE;
        return Math.abs(ChronoUnit.DAYS.between(d1, d2));
    }

    private boolean sameTeamHomeAway(Match m1, Match m2) {
        return m1.getHomeTeam().equals(m2.getHomeTeam()) ||
                m1.getAwayTeam().equals(m2.getAwayTeam());
    }
}
