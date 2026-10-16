package dev.vality.disputes.remote;

import dev.vality.woody.thrift.impl.http.THSpawnClientBuilder;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.springframework.stereotype.Component;

/**
 * Creates clients whose calls made by background processing are bounded by {@link RemoteCallBudget}.
 */
@Component
@RequiredArgsConstructor
public class BudgetedClientFactory {

    private final RemoteCallBudget budget;

    /**
     * Woody builder that spawns a client per call exactly like {@link THSpawnClientBuilder}, but the HTTP client of
     * every call is budgeted. The Woody network timeout stays in force as the per-response limit.
     */
    public THSpawnClientBuilder spawnClientBuilder() {
        return new BudgetedSpawnClientBuilder(budget);
    }

    /**
     * Budgets the calls of a shared client without owning it: closing the result leaves {@code shared} open.
     */
    public CloseableHttpClient wrapShared(CloseableHttpClient shared, BudgetedHttpClient.Limits limits) {
        return new BudgetedHttpClient(shared, budget, limits, false);
    }

    private static final class BudgetedSpawnClientBuilder extends THSpawnClientBuilder {

        private final RemoteCallBudget budget;

        private BudgetedSpawnClientBuilder(RemoteCallBudget budget) {
            this.budget = budget;
        }

        /**
         * Same HTTP client as the default one (minimal, no retries, own connection manager, closed after the
         * call), wrapped so that the budget applies.
         */
        @Override
        protected HttpClient createHttpClient() {
            return new BudgetedHttpClient(
                    HttpClients.createMinimal(new BasicHttpClientConnectionManager()),
                    budget,
                    BudgetedHttpClient.Limits.none(),
                    true);
        }
    }
}
