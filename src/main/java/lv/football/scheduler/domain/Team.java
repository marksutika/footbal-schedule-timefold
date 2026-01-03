package lv.football.scheduler.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.util.Objects;
import java.util.UUID;

public class Team {

    @PlanningId
    private UUID id;

    private String code; // e.g. ARS, RFS
    private String name;
    private Stadium stadium;

    private EuropeanCup europeanCupParticipation = EuropeanCup.NONE;

    public Team() {
        this.id = UUID.randomUUID();
    }

    public Team(String code, String name) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
    }

    // Backwards compatibility for older code
    public Team(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) { // for deserialization if needed
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public Stadium getStadium() {
        return stadium;
    }

    public void setStadium(Stadium stadium) {
        this.stadium = stadium;
    }

    public EuropeanCup getEuropeanCupParticipation() {
        return europeanCupParticipation;
    }

    public void setEuropeanCupParticipation(EuropeanCup europeanCupParticipation) {
        this.europeanCupParticipation = europeanCupParticipation;
    }

    public enum EuropeanCup {
        NONE, UCL, UEL, UECL
    }

    @Override
    public String toString() {
        return (code != null ? code : "") + " " + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Team team)) return false;
        return Objects.equals(id, team.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}