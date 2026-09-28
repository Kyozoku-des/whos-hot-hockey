package com.whoshot.nhl.datajob.config;

import com.whoshot.nhl.datajob.exception.BackfillValidationException;
import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Entry point for the historical season backfill (contracts/cli-contract.md), activated by the
 * {@code backfill} Spring profile:
 * <pre>
 *   java -jar data-job.jar --spring.profiles.active=backfill --backfill.season=20242025
 * </pre>
 * Mirrors {@link InitialLoadRunner}'s profile-activated {@link CommandLineRunner} pattern. Sets the
 * process exit code (FR-009) so the caller — a maintainer or an automated invocation — can act on
 * the result without parsing log output.
 */
@Slf4j
@Component
@Profile("backfill")
@RequiredArgsConstructor
public class BackfillRunner implements CommandLineRunner {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_VALIDATION_FAILED = 1;
    private static final int EXIT_UPSTREAM_FAILURE = 2;
    private static final int EXIT_ALREADY_RUNNING = 3;

    private final NhlApiService nhlApiService;
    private final SeasonBackfillService seasonBackfillService;
    private final ConfigurableApplicationContext context;

    @Value("${backfill.season:}")
    private String seasonId;

    @Value("${backfill.dry-run:false}")
    private boolean dryRun;

    @Override
    public void run(String... args) {
        exit(execute());
    }

    /**
     * Validates the request, runs the backfill and maps the outcome to a process exit code.
     *
     * @return the exit code defined in contracts/cli-contract.md
     */
    int execute() {
        BackfillRequest request;
        try {
            request = BackfillRequest.validate(seasonId, nhlApiService.getSeasons());
        } catch (BackfillValidationException e) {
            log.error("Backfill request rejected: {}", e.getMessage());
            return EXIT_VALIDATION_FAILED;
        } catch (RuntimeException e) {
            log.error("Fatal: could not load the season list to validate {}: {}", seasonId, e.getMessage(), e);
            return EXIT_UPSTREAM_FAILURE;
        }

        log.info("Starting backfill for season {} (dryRun={})", request.seasonId(), dryRun);

        BackfillSummary summary;
        try {
            summary = seasonBackfillService.run(request, dryRun);
        } catch (SeasonBackfillService.AlreadyRunningException e) {
            log.error(e.getMessage());
            return EXIT_ALREADY_RUNNING;
        } catch (RuntimeException e) {
            log.error("Fatal: backfill for season {} aborted: {}", request.seasonId(), e.getMessage(), e);
            return EXIT_UPSTREAM_FAILURE;
        }

        log.info("\n{}", summary.render());
        return summary.success() ? EXIT_SUCCESS : EXIT_UPSTREAM_FAILURE;
    }

    private void exit(int code) {
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
