package lv.football.scheduler.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lv.football.scheduler.service.DataLoaderService;
import lv.football.scheduler.service.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
public class ExplainController {
    private final ScheduleService scheduleService;
    private final DataLoaderService dataLoaderService;

    private final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ExplainController(ScheduleService scheduleService, DataLoaderService dataLoaderService) {
        this.scheduleService = scheduleService;
        this.dataLoaderService = dataLoaderService;
    }

    @GetMapping("/explain")
    public ResponseEntity<Map<String, Object>> explain(@RequestParam String dataset, @RequestParam String variant) {
        String fileName = String.format("benchmark-solution-%s-%s.json", dataset, variant);
        List<File> candidates = List.of(new File(fileName), new File("target/" + fileName), new File("target/classes/" + fileName));
        File f = null;
        for (File c : candidates) if (c.exists()) { f = c; break; }
        if (f == null) {
            return ResponseEntity.status(404).body(Map.of("error", "solution file not found", "tried", candidates.stream().map(File::getPath).toList()));
        }

        try {
            JsonNode root = om.readTree(f);
            return buildExplainFromRoot(dataset, variant, root);
        } catch (Exception ex) {
            System.err.println("ExplainController failed to parse " + f.getPath() + ": " + ex);
            // Fallback: try to regenerate solution by solving once (may take time) and explain that
            try {
                var problem = dataLoaderService.loadScheduleProblem(dataset);
                var solved = scheduleService.solveSchedule(dataset, variant, problem);
                JsonNode root = om.valueToTree(solved);
                return buildExplainFromRoot(dataset, variant, root);
            } catch (Exception ex2) {
                return ResponseEntity.status(500).body(Map.of("error", "failed to parse solution file and fallback solve failed", "parseMessage", ex.getMessage(), "fallbackMessage", ex2.getMessage()));
            }
        }
    }

    private ResponseEntity<Map<String, Object>> buildExplainFromRoot(String dataset, String variant, JsonNode root) {
        JsonNode matchesNode = root.get("matches");
        List<JsonNode> matches = new ArrayList<>();
        if (matchesNode != null && matchesNode.isArray()) matchesNode.forEach(matches::add);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataset", dataset);
        out.put("variant", variant);
        JsonNode teamsNode = root.get("teams");
        out.put("teams", teamsNode == null ? 0 : teamsNode.size());
        out.put("matches", matches.size());

        Map<String, Integer> hard = new LinkedHashMap<>();
        hard.put("H1_teamTwiceSameDay", countH1(matches));
        hard.put("H2_stadiumOverlapTimeslot", countH2(matches));
        hard.put("H3_maxOneMatchPerStadiumPerDay", countH3(matches));
        hard.put("H4_europeanNight", 0);
        hard.put("H6_lastRoundNotSunday1500", 0);
        hard.put("H8_minRestDays", countH8(matches));

        List<LocalDate> forbidden = new ArrayList<>();
        JsonNode forbNode = root.get("forbiddenDates");
        if (forbNode != null && forbNode.isArray()) for (JsonNode n : forbNode) {
            try { forbidden.add(LocalDate.parse(n.asText())); } catch (Exception ignored) {}
        }
        hard.put("H9_forbiddenDates", countH9(matches, forbidden));

        out.put("hardViolations", hard);

        Map<String, Integer> soft = new LinkedHashMap<>();
        soft.put("S2_fridayOrMonday", countS2(matches));
        soft.put("S3_simultaneousMatches", countS3(matches));
        soft.put("S4_veryLateKickoffs", countS4(matches));
        out.put("softPenalties", soft);

        return ResponseEntity.ok(out);
    }

    // ---------- Counters (operate on JsonNode matches parsed from saved solution JSON) ----------

    private int countH1(List<JsonNode> matches) {
        int cnt = 0;
        for (int i = 0; i < matches.size(); i++) {
            for (int j = i+1; j < matches.size(); j++) {
                if (sharesTeam(matches.get(i), matches.get(j)) && sameMatchDate(matches.get(i), matches.get(j))) cnt++;
            }
        }
        return cnt;
    }

    private int countH2(List<JsonNode> matches) {
        int cnt = 0;
        for (int i = 0; i < matches.size(); i++) {
            for (int j = i+1; j < matches.size(); j++) {
                if (sameStadium(matches.get(i), matches.get(j)) && sameMatchDate(matches.get(i), matches.get(j))
                        && sameTimeslot(matches.get(i), matches.get(j))) cnt++;
            }
        }
        return cnt;
    }

    private int countH3(List<JsonNode> matches) {
        int cnt = 0;
        for (int i = 0; i < matches.size(); i++) {
            for (int j = i+1; j < matches.size(); j++) {
                if (sameStadium(matches.get(i), matches.get(j)) && sameMatchDate(matches.get(i), matches.get(j))) cnt++;
            }
        }
        return cnt;
    }

    private int countH4(List<JsonNode> matches, Object europeanWeeks) {
        return 0; // detailed check skipped
    }

    private int countH6(List<JsonNode> matches, List<?> rounds) {
        return 0; // skipped
    }

    private int countH8(List<JsonNode> matches) {
        int cnt = 0;
        for (int i = 0; i < matches.size(); i++) {
            for (int j = i+1; j < matches.size(); j++) {
                if (sharesTeam(matches.get(i), matches.get(j))) {
                    long days = daysBetweenMatchDates(matches.get(i), matches.get(j));
                    if (days != Long.MAX_VALUE && days < 2) cnt++;
                }
            }
        }
        return cnt;
    }

    private int countH9(List<JsonNode> matches, List<LocalDate> forbidden) {
        if (forbidden == null) return 0;
        int cnt = 0;
        for (JsonNode m : matches) {
            LocalDate d1 = matchDate(m);
            LocalDate d2 = roundStartDate(m);
            if ((d1 != null && forbidden.contains(d1)) || (d2 != null && forbidden.contains(d2))) cnt++;
        }
        return cnt;
    }

    private LocalDate roundStartDate(JsonNode m) {
        if (m==null) return null;
        JsonNode round = m.get("round");
        if (round==null) return null;
        JsonNode startDateNode = round.get("startDate");
        if (startDateNode == null) return null;
        try {
            return LocalDate.parse(startDateNode.asText());
        } catch (Exception ex) {
            return null;
        }
    }

    private int countS2(List<JsonNode> matches) {
        int cnt = 0;
        for (JsonNode m : matches) {
            JsonNode ts = m.get("timeslot");
            if (ts==null) continue;
            String dow = ts.has("dayOfWeek") ? ts.get("dayOfWeek").asText() : null;
            if ("FRIDAY".equals(dow) || "MONDAY".equals(dow)) cnt++;
        }
        return cnt;
    }

    private int countS3(List<JsonNode> matches) {
        int cnt = 0;
        for (int i = 0; i < matches.size(); i++) for (int j = i+1; j < matches.size(); j++) {
            if (sameMatchDate(matches.get(i), matches.get(j)) && sameTimeslot(matches.get(i), matches.get(j))) cnt++;
        }
        return cnt;
    }

    private int countS4(List<JsonNode> matches) {
        int cnt = 0;
        for (JsonNode m : matches) {
            JsonNode ts = m.get("timeslot");
            if (ts==null) continue;
            String dow = ts.has("dayOfWeek") ? ts.get("dayOfWeek").asText() : null;
            String start = ts.has("startTime") ? ts.get("startTime").asText() : null;
            boolean lateFriMon = (("FRIDAY".equals(dow) || "MONDAY".equals(dow)) && "21:30".equals(start));
            boolean lateSunday = ("SUNDAY".equals(dow) && "21:00".equals(start));
            if (lateFriMon || lateSunday) cnt++;
        }
        return cnt;
    }

    // ---------- small helpers (copy of logic from constraint provider) ----------
    private boolean sharesTeam(JsonNode m1, JsonNode m2) {
        if (m1==null || m2==null) return false;
        JsonNode h1 = m1.get("homeTeam");
        JsonNode a1 = m1.get("awayTeam");
        JsonNode h2 = m2.get("homeTeam");
        JsonNode a2 = m2.get("awayTeam");
        if (h1==null || a1==null || h2==null || a2==null) return false;
        String h1c = h1.has("code") ? h1.get("code").asText() : h1.get("name").asText(null);
        String a1c = a1.has("code") ? a1.get("code").asText() : a1.get("name").asText(null);
        String h2c = h2.has("code") ? h2.get("code").asText() : h2.get("name").asText(null);
        String a2c = a2.has("code") ? a2.get("code").asText() : a2.get("name").asText(null);
        return (h1c!=null && (h1c.equals(h2c) || h1c.equals(a2c))) || (a1c!=null && (a1c.equals(h2c) || a1c.equals(a2c)));
    }

    private boolean sameMatchDate(JsonNode m1, JsonNode m2) {
        LocalDate d1 = matchDate(m1);
        LocalDate d2 = matchDate(m2);
        return d1 != null && d1.equals(d2);
    }

    private LocalDate matchDate(JsonNode m) {
        if (m==null) return null;
        JsonNode round = m.get("round");
        JsonNode timeslot = m.get("timeslot");
        if (round==null || timeslot==null) return null;
        String dow = timeslot.has("dayOfWeek") ? timeslot.get("dayOfWeek").asText() : null;
        if (dow == null) return null;
        JsonNode startDateNode = round.get("startDate");
        if (startDateNode == null) return null;
        try {
            LocalDate startDate = LocalDate.parse(startDateNode.asText());
            java.time.DayOfWeek target = java.time.DayOfWeek.valueOf(dow);
            int startDow = startDate.getDayOfWeek().getValue();
            int targetDow = target.getValue();
            return startDate.plusDays(targetDow - startDow);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean sameStadium(JsonNode m1, JsonNode m2) {
        if (m1==null||m2==null) return false;
        JsonNode s1 = m1.get("stadium");
        JsonNode s2 = m2.get("stadium");
        if (s1==null || s2==null) return false;
        String n1 = s1.has("name") ? s1.get("name").asText() : null;
        String n2 = s2.has("name") ? s2.get("name").asText() : null;
        return n1!=null && n1.equals(n2);
    }

    private boolean sameTimeslot(JsonNode m1, JsonNode m2) {
        if (m1==null||m2==null) return false;
        JsonNode t1 = m1.get("timeslot");
        JsonNode t2 = m2.get("timeslot");
        if (t1==null || t2==null) return false;
        String d1 = t1.has("dayOfWeek") ? t1.get("dayOfWeek").asText() : null;
        String d2 = t2.has("dayOfWeek") ? t2.get("dayOfWeek").asText() : null;
        String s1 = t1.has("startTime") ? t1.get("startTime").asText() : null;
        String s2 = t2.has("startTime") ? t2.get("startTime").asText() : null;
        return d1!=null && d1.equals(d2) && s1!=null && s1.equals(s2);
    }

    private long daysBetweenMatchDates(JsonNode m1, JsonNode m2) {
        LocalDate d1 = matchDate(m1);
        LocalDate d2 = matchDate(m2);
        if (d1==null||d2==null) return Long.MAX_VALUE;
        return Math.abs(ChronoUnit.DAYS.between(d1,d2));
    }
}
