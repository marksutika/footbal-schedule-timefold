package lv.football.scheduler.service;

import lv.football.scheduler.domain.*;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataLoaderService {

    public SchedulingSolution loadScheduleProblem(String type) {

        List<Team> teams = createTeams(type);
        List<Round> rounds = createRounds();
        List<Stadium> stadiums = createStadiums();
        List<Timeslot> timeslots = createTimeslots();
        List<Match> matches = createMatches(teams, rounds);


        SchedulingSolution solution = new SchedulingSolution();
        solution.setTeams(teams);
        solution.setRounds(rounds);
        solution.setStadiums(stadiums);
        solution.setTimeslots(timeslots);
        solution.setMatches(matches);

        return solution;
    }

    // ===== FACT GENERATORS =====

    private List<Team> createTeams(String type) {
        int count = switch (type) {
            case "medium" -> 10;
            case "large" -> 20;
            default -> 6;
        };

        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            teams.add(new Team("Team " + i));
        }
        return teams;
    }

    private List<Round> createRounds() {
        List<Round> rounds = new ArrayList<>();
        LocalDate start = LocalDate.now();

        for (int i = 1; i <= 20; i++) {
            Round r = new Round(i);
            r.setStartDate(start.plusWeeks(i - 1));
            rounds.add(r);
        }
        return rounds;
    }

    private List<Stadium> createStadiums() {
        List<Stadium> stadiums = new ArrayList<>();
        stadiums.add(new Stadium("National Stadium"));
        stadiums.add(new Stadium("City Arena"));
        stadiums.add(new Stadium("Olympic Park"));
        return stadiums;
    }

    private List<Timeslot> createTimeslots() {
        List<Timeslot> timeslots = new ArrayList<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                timeslots.add(createTimeslot(day, LocalTime.of(13, 0), false));
                timeslots.add(createTimeslot(day, LocalTime.of(15, 0), false));
                timeslots.add(createTimeslot(day, LocalTime.of(18, 0), false));
            } else {
                timeslots.add(createTimeslot(day, LocalTime.of(19, 0), true));
            }
        }
        return timeslots;
    }

    private Timeslot createTimeslot(DayOfWeek day, LocalTime time, boolean midweek) {
        Timeslot t = new Timeslot();
        t.setDayOfWeek(day);
        t.setStartTime(time);
        t.setIsMidweek(midweek);
        return t;
    }

    private List<Match> createMatches(List<Team> teams, List<Round> rounds) {
        List<Match> matches = new ArrayList<>();

        int roundIndex = 0;

        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {

                Round round = rounds.get(roundIndex % rounds.size());
                roundIndex++;

                Match match = new Match(
                    teams.get(i),
                    teams.get(j),
                    round
                );

                matches.add(match);
            }
        }

        return matches;
    }

}
