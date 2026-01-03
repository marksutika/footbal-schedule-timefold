package lv.football.scheduler.domain;

public class LeagueRules {
    private final int matchesPerRound;
    private final boolean strictRounds; // true for EPL, false for virsliga

    public LeagueRules(int matchesPerRound, boolean strictRounds) {
        this.matchesPerRound = matchesPerRound;
        this.strictRounds = strictRounds;
    }

    public int getMatchesPerRound() {
        return matchesPerRound;
    }

    public boolean isStrictRounds() {
        return strictRounds;
    }
}