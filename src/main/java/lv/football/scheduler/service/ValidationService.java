package lv.football.scheduler.service;

import lv.football.scheduler.domain.Match;
import lv.football.scheduler.domain.SchedulingSolution;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class ValidationService {

    public boolean validateSolution(SchedulingSolution solution) {
        return noTeamPlaysTwiceInSameRound(solution);
    }

    /**
     * H1: Vienai komandai vairāk par vienu spēli vienā kārtā
     */
    private boolean noTeamPlaysTwiceInSameRound(SchedulingSolution solution) {
        Set<String> played = new HashSet<>();

        for (Match match : solution.getMatches()) {
            if (match.getRound() == null)
                continue;

            String homeKey = match.getHomeTeam().getName()
                    + "-" + match.getRound().getRoundNumber();
            String awayKey = match.getAwayTeam().getName()
                    + "-" + match.getRound().getRoundNumber();

            if (!played.add(homeKey) || !played.add(awayKey)) {
                return false;
            }
        }
        return true;
    }

    public int getUnplannedMatchesCount(SchedulingSolution solution) {
        int count = 0;
        for (Match match : solution.getMatches()) {
            if (match.getRound() == null) {
                count++;
            }
        }
        return count;
    }
}
