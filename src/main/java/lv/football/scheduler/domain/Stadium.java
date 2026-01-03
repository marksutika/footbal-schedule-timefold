package lv.football.scheduler.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.util.Objects;
import java.util.UUID;

public class Stadium {

    @PlanningId
    private UUID id;

    private String name;

    public Stadium() {
        this.id = UUID.randomUUID();
    }

    public Stadium(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) { // for deserialization if needed
        this.id = id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stadium stadium)) return false;
        return Objects.equals(id, stadium.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}