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

        int roundsCount = roundsCount(data.teams.size(), data.cycles);
        List<Round> rounds = createRounds(roundsCount, type);

        List<Timeslot> timeslots = createTimeslots(type);

        List<Match> matches = createRoundRobinSchedule(data.teams, rounds, data.cycles);

        SchedulingSolution solution = new SchedulingSolution();
        solution.setTeams(data.teams);
        solution.setStadiums(data.stadiums);
        solution.setTimeslots(timeslots);
        solution.setRounds(rounds);
        solution.setMatches(matches);
        solution.setEuropeanWeeks(createEuropeanWeeks());
        solution.setForbiddenDates(createForbiddenDates(type));

        // Optional fact (still useful for reporting/debug)
        solution.setLeagueRules(new LeagueRules(data.teams.size() / 2, "epl".equals(type)));

        return solution;
    }

    // ---------------- Timeslots ----------------

    private List<Timeslot> createTimeslots(String type) {
        List<Timeslot> timeslots = new ArrayList<>();

        // Fri
        timeslots.add(new Timeslot(DayOfWeek.FRIDAY, LocalTime.of(19, 0), false));
        timeslots.add(new Timeslot(DayOfWeek.FRIDAY, LocalTime.of(21, 30), false));

        // Sat
        timeslots.add(new Timeslot(DayOfWeek.SATURDAY, LocalTime.of(12, 30), false));
        timeslots.add(new Timeslot(DayOfWeek.SATURDAY, LocalTime.of(15, 0), false));
        timeslots.add(new Timeslot(DayOfWeek.SATURDAY, LocalTime.of(18, 30), false));
        timeslots.add(new Timeslot(DayOfWeek.SATURDAY, LocalTime.of(21, 0), false));

        // Sun
        timeslots.add(new Timeslot(DayOfWeek.SUNDAY, LocalTime.of(12, 30), false));
        timeslots.add(new Timeslot(DayOfWeek.SUNDAY, LocalTime.of(15, 0), false)); // needed for last round rule
        timeslots.add(new Timeslot(DayOfWeek.SUNDAY, LocalTime.of(18, 30), false));
        timeslots.add(new Timeslot(DayOfWeek.SUNDAY, LocalTime.of(21, 0), false));

        // Mon
        timeslots.add(new Timeslot(DayOfWeek.MONDAY, LocalTime.of(19, 0), false));
        timeslots.add(new Timeslot(DayOfWeek.MONDAY, LocalTime.of(21, 30), false));

        // No Tue/Wed for EPL/Virsliga by your decision.

        return timeslots;
    }

    // ---------------- Rounds ----------------

    private int roundsCount(int teamCount, int cycles) {
        int perCycle = (teamCount % 2 == 0) ? (teamCount - 1) : teamCount;
        return cycles * perCycle;
    }

    private List<Round> createRounds(int roundsCount, String type) {
        List<Round> rounds = new ArrayList<>();

        LocalDate seasonStart = seasonStartDate(type)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        for (int i = 1; i <= roundsCount; i++) {
            rounds.add(new Round(i, seasonStart.plusWeeks(i - 1)));
        }
        return rounds;
    }

    private LocalDate seasonStartDate(String type) {
        int year = LocalDate.now().getYear();
        return switch (type) {
            case "epl" -> LocalDate.of(year, 8, 31);
            case "virsliga" -> LocalDate.of(year, 3, 2);
            default -> LocalDate.now();
        };
    }

    // ---------------- Round-robin schedule (circle method) ----------------

    private List<Match> createRoundRobinSchedule(List<Team> teams, List<Round> rounds, int cycles) {
        if (teams.size() % 2 != 0) {
            throw new IllegalArgumentException("Odd number of teams not supported in this project.");
        }

        int n = teams.size();
        int roundsPerCycle = n - 1;
        if (rounds.size() != cycles * roundsPerCycle) {
            throw new IllegalStateException("Rounds list size doesn't match cycles*(N-1).");
        }

        List<Match> matches = new ArrayList<>();
        List<Team> list = new ArrayList<>(teams);

        Team fixed = list.get(0);
        List<Team> rotating = new ArrayList<>(list.subList(1, n));

        for (int cycle = 0; cycle < cycles; cycle++) {
            boolean swapHomeAway = (cycle % 2 == 1);

            for (int r = 0; r < roundsPerCycle; r++) {
                Round round = rounds.get(cycle * roundsPerCycle + r);

                List<Team> left = new ArrayList<>();
                List<Team> right = new ArrayList<>();

                left.add(fixed);
                left.addAll(rotating.subList(0, (n / 2) - 1));

                right.addAll(rotating.subList((n / 2) - 1, rotating.size()));
                Collections.reverse(right);

                for (int i = 0; i < n / 2; i++) {
                    Team t1 = left.get(i);
                    Team t2 = right.get(i);

                    Team home = swapHomeAway ? t2 : t1;
                    Team away = swapHomeAway ? t1 : t2;

                    matches.add(new Match(home, away, round));
                }

                // rotate
                rotating.add(0, rotating.remove(rotating.size() - 1));
            }
        }

        enforceNoSharedHomeStadiumInLastRound(matches, rounds);

        return matches;
    }

    /**
     * Ensures last round has no duplicate home stadiums (important for Virsliga shared stadiums),
     * so that "all matches same day/time" doesn't violate H3.
     */
    private void enforceNoSharedHomeStadiumInLastRound(List<Match> matches, List<Round> rounds) {
        int lastRoundNumber = rounds.stream().mapToInt(Round::getRoundNumber).max().orElse(0);

        List<Match> lastRoundMatches = matches.stream()
                .filter(m -> m.getRound() != null && m.getRound().getRoundNumber() == lastRoundNumber)
                .toList();

        Set<Stadium> usedHomeStadiums = new HashSet<>();

        for (Match m : lastRoundMatches) {
            Stadium s = m.getStadium();
            if (s == null) continue;

            if (!usedHomeStadiums.add(s)) {
                // swap home/away to move match to the other stadium
                Team oldHome = m.getHomeTeam();
                Team oldAway = m.getAwayTeam();
                m.setHomeTeam(oldAway);
                m.setAwayTeam(oldHome);

                // register the new home stadium as well
                Stadium newHomeStadium = m.getStadium();
                if (newHomeStadium != null) {
                    usedHomeStadiums.add(newHomeStadium);
                }
            }
        }
    }

    // ---------------- European weeks ----------------

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

    // ---------------- Hardcoded datasets ----------------

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

        return new LeagueData(teams, stadiums, 2);
    }

    private List<java.time.LocalDate> createForbiddenDates(String type) {
    int year = java.time.LocalDate.now().getYear();
    java.util.List<java.time.LocalDate> list = new java.util.ArrayList<>();
    if ("epl".equals(type)) {
        // EPL: forbid New Year and Christmas windows
        list.add(java.time.LocalDate.of(year, 1, 1));
        list.add(java.time.LocalDate.of(year + 1, 1, 1));
        list.add(java.time.LocalDate.of(year, 12, 24));
        list.add(java.time.LocalDate.of(year, 12, 25));
        list.add(java.time.LocalDate.of(year, 12, 26));
        list.add(java.time.LocalDate.of(year + 1, 12, 24));
        list.add(java.time.LocalDate.of(year + 1, 12, 25));
        list.add(java.time.LocalDate.of(year + 1, 12, 26));
        // keep Nov 18 as well (national day)
        list.add(java.time.LocalDate.of(year, 11, 18));
        list.add(java.time.LocalDate.of(year + 1, 11, 18));
    } else if ("virsliga".equals(type)) {
        // Virsliga: forbid local holidays and test April dates
        list.add(java.time.LocalDate.of(year, 11, 18));
        list.add(java.time.LocalDate.of(year + 1, 11, 18));
        list.add(java.time.LocalDate.of(year, 4, 3));
        list.add(java.time.LocalDate.of(year, 4, 5));
        list.add(java.time.LocalDate.of(year + 1, 4, 3));
        list.add(java.time.LocalDate.of(year + 1, 4, 5));
    } else {
        // small/default: include a minimal set
        list.add(java.time.LocalDate.of(year, 11, 18));
        list.add(java.time.LocalDate.of(year, 4, 3));
        list.add(java.time.LocalDate.of(year, 4, 5));
    }
    return list;
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

        return new LeagueData(new ArrayList<>(teams), new ArrayList<>(stadiumById.values()), 2);
    }

    private LeagueData createVirsligaDemo() {
        Map<String, Stadium> stadiumById = new LinkedHashMap<>();
        stadiumById.put("LNK", new Stadium("LNK Sporta Parks"));
        stadiumById.put("SKO", new Stadium("Skonto Stadions"));
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

        return new LeagueData(new ArrayList<>(teams), new ArrayList<>(stadiumById.values()), 4);
    }

    private Team team(String code, String name, String stadiumId, EuropeanCup cup, Map<String, Stadium> stadiumById) {
        Team t = new Team(code, name);
        t.setStadium(stadiumById.get(stadiumId));
        t.setEuropeanCupParticipation(cup);
        return t;
    }
}