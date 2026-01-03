package lv.football.scheduler.service;

import lv.football.scheduler.domain.*;
import lv.football.scheduler.domain.Team.EuropeanCup;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class DataLoaderService {

    public SchedulingSolution loadScheduleProblem(String type) {

        LeagueData data = switch (type) {
            case "epl" -> createEplDemo();
            case "virsliga" -> createVirsligaDemo();
            default -> createSmallDemo();
        };

        List<Timeslot> timeslots = createTimeslots(type);
        int roundsCount = roundsCount(data.teams.size(), data.cycles);
        List<Round> rounds = createRounds(roundsCount, type);
        List<Match> matches = createMatchesRoundRobin(data.teams, data.cycles);

        SchedulingSolution solution = new SchedulingSolution();
        int matchesPerRound = data.teams.size() / 2;
        boolean strictRounds = "epl".equals(type);      // strict only for EPL
        solution.setLeagueRules(new LeagueRules(matchesPerRound, strictRounds));
        solution.setTeams(data.teams);
        solution.setStadiums(data.stadiums);
        solution.setTimeslots(timeslots);
        solution.setRounds(rounds);
        solution.setMatches(matches);
        solution.setEuropeanWeeks(createEuropeanWeeks());

        return solution;
    }

    private List<Timeslot> createTimeslots(String type) {
        List<Timeslot> timeslots = new ArrayList<>();

        timeslots.add(new Timeslot(DayOfWeek.FRIDAY, LocalTime.of(19, 0), false));
        timeslots.add(new Timeslot(DayOfWeek.FRIDAY, LocalTime.of(21, 30), false));

        timeslots.add(new Timeslot(DayOfWeek.SATURDAY, LocalTime.of(12, 30), false));
        timeslots.add(new Timeslot(DayOfWeek.SATURDAY, LocalTime.of(15, 0), false));
        timeslots.add(new Timeslot(DayOfWeek.SATURDAY, LocalTime.of(18, 30), false));
        timeslots.add(new Timeslot(DayOfWeek.SATURDAY, LocalTime.of(21, 0), false));

        timeslots.add(new Timeslot(DayOfWeek.SUNDAY, LocalTime.of(12, 30), false));
        timeslots.add(new Timeslot(DayOfWeek.SUNDAY, LocalTime.of(15, 0), false));
        timeslots.add(new Timeslot(DayOfWeek.SUNDAY, LocalTime.of(18, 30), false));
        timeslots.add(new Timeslot(DayOfWeek.SUNDAY, LocalTime.of(21, 0), false));

        timeslots.add(new Timeslot(DayOfWeek.MONDAY, LocalTime.of(19, 0), false));
        timeslots.add(new Timeslot(DayOfWeek.MONDAY, LocalTime.of(21, 30), false));

        // Midweek only for EPL (Virslīga weekend-only)
        if ("epl".equals(type)) {
            timeslots.add(new Timeslot(DayOfWeek.TUESDAY, LocalTime.of(19, 0), true));
            timeslots.add(new Timeslot(DayOfWeek.TUESDAY, LocalTime.of(21, 30), true));
            timeslots.add(new Timeslot(DayOfWeek.WEDNESDAY, LocalTime.of(19, 0), true));
            timeslots.add(new Timeslot(DayOfWeek.WEDNESDAY, LocalTime.of(21, 30), true));
        }

        return timeslots;
    }

    private int roundsCount(int teamCount, int cycles) {
        int perCycle = (teamCount % 2 == 0) ? (teamCount - 1) : teamCount;
        return cycles * perCycle;
    }

    private List<Round> createRounds(int roundsCount, String type) {
    List<Round> rounds = new ArrayList<>();

    LocalDate start = seasonStartDate(type)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); // keep round start aligned to Monday

    for (int i = 1; i <= roundsCount; i++) {
        rounds.add(new Round(i, start.plusWeeks(i - 1)));
    }
    return rounds;
}

private LocalDate seasonStartDate(String type) {
    // Choose a fixed "realistic" season start (you can tune dates).
    // EPL: late Aug / early Sep
    // Virslīga: early March
    int year = LocalDate.now().getYear();

    return switch (type) {
        case "epl" -> LocalDate.of(year, 8, 31);      // around end of August
        case "virsliga" -> LocalDate.of(year, 3, 2);  // early March
        default -> LocalDate.now();
    };
}

    private List<Match> createMatchesRoundRobin(List<Team> teams, int cycles) {
        List<Match> matches = new ArrayList<>();

        // cycle 1
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                matches.add(new Match(teams.get(i), teams.get(j)));
            }
        }

        // cycles 2..N swap home/away
        for (int c = 2; c <= cycles; c++) {
            for (int i = 0; i < teams.size(); i++) {
                for (int j = i + 1; j < teams.size(); j++) {
                    matches.add(new Match(teams.get(j), teams.get(i)));
                }
            }
        }

        return matches;
    }

    private EuropeanWeeks createEuropeanWeeks() {
        Set<LocalDate> ucl = Set.of(
                LocalDate.of(2025, 9, 15), LocalDate.of(2025, 9, 29), LocalDate.of(2025, 10, 20),
                LocalDate.of(2025, 11, 3), LocalDate.of(2025, 11, 24), LocalDate.of(2025, 12, 8),
                LocalDate.of(2026, 1, 19), LocalDate.of(2026, 1, 26),
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 13),
                LocalDate.of(2026, 4, 27), LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 25)
        );

        Set<LocalDate> uel = Set.of(
                LocalDate.of(2025, 9, 22), LocalDate.of(2025, 9, 29), LocalDate.of(2025, 10, 20),
                LocalDate.of(2025, 11, 3), LocalDate.of(2025, 11, 24), LocalDate.of(2025, 12, 8),
                LocalDate.of(2026, 1, 19), LocalDate.of(2026, 1, 26),
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 13),
                LocalDate.of(2026, 5, 18)
        );

        Set<LocalDate> uecl = Set.of(
                LocalDate.of(2025, 9, 29), LocalDate.of(2025, 10, 20), LocalDate.of(2025, 11, 3),
                LocalDate.of(2025, 11, 24), LocalDate.of(2025, 12, 8), LocalDate.of(2025, 12, 15),
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 13),
                LocalDate.of(2026, 5, 25)
        );

        return new EuropeanWeeks(ucl, uel, uecl);
    }

    private record LeagueData(List<Team> teams, List<Stadium> stadiums, int cycles) {}

    private LeagueData createSmallDemo() {
        List<Stadium> stadiums = List.of(
                new Stadium("National Stadium"),
                new Stadium("City Arena"),
                new Stadium("Olympic Park")
        );

        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Team t = new Team("T" + i, "Team " + i);
            t.setStadium(stadiums.get((i - 1) % stadiums.size()));
            t.setEuropeanCupParticipation(EuropeanCup.NONE);
            teams.add(t);
        }

        int cycles = 2;
        return new LeagueData(teams, stadiums, cycles);
    }

    private LeagueData createEplDemo() {
        Map<String, Stadium> stadiumById = new LinkedHashMap<>();
        stadiumById.put("EMI", new Stadium("Emirates Stadium"));
        stadiumById.put("VLP", new Stadium("Villa Park"));
        stadiumById.put("VIT", new Stadium("Vitality Stadium"));
        stadiumById.put("GTC", new Stadium("Gtech Community Stadium"));
        stadiumById.put("AMX", new Stadium("Amex Stadium"));
        stadiumById.put("TUR", new Stadium("Turf Moor"));
        stadiumById.put("STB", new Stadium("Stamford Bridge"));
        stadiumById.put("SLP", new Stadium("Selhurst Park"));
        stadiumById.put("GDS", new Stadium("Goodison Park"));
        stadiumById.put("CRC", new Stadium("Craven Cottage"));
        stadiumById.put("ELN", new Stadium("Elland Road"));
        stadiumById.put("ANF", new Stadium("Anfield"));
        stadiumById.put("ETH", new Stadium("Etihad Stadium"));
        stadiumById.put("OTF", new Stadium("Old Trafford"));
        stadiumById.put("SJP", new Stadium("St James' Park"));
        stadiumById.put("CGR", new Stadium("City Ground"));
        stadiumById.put("SOL", new Stadium("Stadium of Light"));
        stadiumById.put("THS", new Stadium("Tottenham Hotspur Stadium"));
        stadiumById.put("LNS", new Stadium("London Stadium"));
        stadiumById.put("MOL", new Stadium("Molineux Stadium"));

        List<Team> teams = List.of(
                team("ARS","Arsenal","EMI", EuropeanCup.UCL, stadiumById),
                team("AVL","Aston Villa","VLP", EuropeanCup.NONE, stadiumById),
                team("BOU","Bournemouth","VIT", EuropeanCup.NONE, stadiumById),
                team("BRE","Brentford","GTC", EuropeanCup.NONE, stadiumById),
                team("BHA","Brighton","AMX", EuropeanCup.NONE, stadiumById),
                team("BUR","Burnley","TUR", EuropeanCup.NONE, stadiumById),
                team("CHE","Chelsea","STB", EuropeanCup.UCL, stadiumById),
                team("CRY","Crystal Palace","SLP", EuropeanCup.UECL, stadiumById),
                team("EVE","Everton","GDS", EuropeanCup.NONE, stadiumById),
                team("FUL","Fulham","CRC", EuropeanCup.NONE, stadiumById),
                team("LEE","Leeds United","ELN", EuropeanCup.NONE, stadiumById),
                team("LIV","Liverpool","ANF", EuropeanCup.UCL, stadiumById),
                team("MCI","Manchester City","ETH", EuropeanCup.UCL, stadiumById),
                team("MUN","Manchester United","OTF", EuropeanCup.NONE, stadiumById),
                team("NEW","Newcastle United","SJP", EuropeanCup.UCL, stadiumById),
                team("NFO","Nottingham Forest","CGR", EuropeanCup.UEL, stadiumById),
                team("SUN","Sunderland","SOL", EuropeanCup.NONE, stadiumById),
                team("TOT","Tottenham Hotspur","THS", EuropeanCup.NONE, stadiumById),
                team("WHU","West Ham United","LNS", EuropeanCup.NONE, stadiumById),
                team("WOL","Wolverhampton Wanderers","MOL", EuropeanCup.NONE, stadiumById)
        );

        int cycles = 2;
        return new LeagueData(new ArrayList<>(teams), new ArrayList<>(stadiumById.values()), cycles);
    }

    private LeagueData createVirsligaDemo() {
        Map<String, Stadium> stadiumById = new LinkedHashMap<>();
        stadiumById.put("LNK", new Stadium("LNK Sporta Parks"));
        stadiumById.put("SKO", new Stadium("Skonto Stadions"));
        stadiumById.put("DAL", new Stadium("J. Daliņa Stadions"));
        stadiumById.put("LIE_DAUG", new Stadium("Daugavas Stadions Liepājā"));
        stadiumById.put("CEL", new Stadium("Celtnieks"));
        stadiumById.put("DGR", new Stadium("Daugavas Stadions Rīgā"));
        stadiumById.put("TUK", new Stadium("Tukuma Stadions"));
        stadiumById.put("ZOC", new Stadium("Zemgales Olimpiskais Centrs"));

        List<Team> teams = List.of(
                team("RFS","RFS","LNK", EuropeanCup.UCL, stadiumById),
                team("RIG","Riga FC","SKO", EuropeanCup.UECL, stadiumById),
                team("AUD","Auda","SKO", EuropeanCup.UECL, stadiumById),
                team("SUP","SuperNova","LNK", EuropeanCup.NONE, stadiumById),
                team("LIE","Liepāja","LIE_DAUG", EuropeanCup.NONE, stadiumById),
                team("GRO","Grobiņa","LIE_DAUG", EuropeanCup.NONE, stadiumById),
                team("DAU","Daugavpils","CEL", EuropeanCup.UECL, stadiumById),
                team("MET","Metta","DGR", EuropeanCup.NONE, stadiumById),
                team("TUK","Tukums 2000","TUK", EuropeanCup.NONE, stadiumById),
                team("JEL","Jelgava","ZOC", EuropeanCup.NONE, stadiumById)
        );

        int cycles = 4;
        return new LeagueData(new ArrayList<>(teams), new ArrayList<>(stadiumById.values()), cycles);
    }

    private Team team(String code, String name, String stadiumId, EuropeanCup cup, Map<String, Stadium> stadiumById) {
        Team t = new Team(code, name);
        t.setStadium(stadiumById.get(stadiumId));
        t.setEuropeanCupParticipation(cup);
        return t;
    }
}