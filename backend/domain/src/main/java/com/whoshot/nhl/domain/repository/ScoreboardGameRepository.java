package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.ScoreboardGame;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/**
 * Repository for the score ticker's games.
 */
public interface ScoreboardGameRepository extends JpaRepository<ScoreboardGame, Long> {

    /**
     * Games of one game day with their goals, in start-time order.
     *
     * @param gameDate local game date ({@code yyyy-MM-dd})
     * @return that day's games
     */
    @EntityGraph(attributePaths = "goals")
    List<ScoreboardGame> findByGameDateOrderByStartTimeUtcAscGameIdAsc(String gameDate);

    /**
     * Most recent game date with a game in one of the given states.
     *
     * @param gameStates NHL game states, e.g. LIVE and CRIT
     * @return latest matching game date, or {@code null} if none
     */
    @Query("select max(g.gameDate) from ScoreboardGame g where g.gameState in :gameStates")
    String findLatestGameDateWithState(Collection<String> gameStates);

    /**
     * Removes games of days older than the given date.
     *
     * @param gameDate oldest game date to keep
     * @return number of games removed
     */
    @Modifying
    @Query("delete from ScoreboardGame g where g.gameDate < :gameDate")
    int deleteByGameDateBefore(String gameDate);
}
