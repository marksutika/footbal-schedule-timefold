package lv.football.scheduler.domain;

public class Team {

    private String name;
    private EuropeanCup europeanCupParticipation = EuropeanCup.NONE;

    public Team() {
    }

    public Team(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public EuropeanCup getEuropeanCupParticipation() {
        return europeanCupParticipation;
    }

    public void setEuropeanCupParticipation(EuropeanCup europeanCupParticipation) {
        this.europeanCupParticipation = europeanCupParticipation;
    }

    public enum EuropeanCup {
        NONE, CHAMPIONS_LEAGUE, EUROPA_LEAGUE
    }
}
