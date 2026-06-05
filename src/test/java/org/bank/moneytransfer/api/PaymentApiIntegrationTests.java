package org.bank.moneytransfer.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiIntegrationTests {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from audit_logs");
        jdbc.update("delete from outbox_events");
        jdbc.update("delete from idempotency_records");
        jdbc.update("delete from ledger_entries");
        jdbc.update("delete from transfers");
        jdbc.update("delete from accounts");
    }

    @Test
    void accountLifecycleUsesPublicRoutesAndCorrelationIds() throws Exception {
        String accountId = createAccount("USD", "25.00");

        mvc.perform(get("/accounts/{accountId}", accountId).header("X-Correlation-Id", "req_test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "req_test"))
                .andExpect(jsonPath("$.accountId", equalTo(accountId)))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.availableBalance", equalTo(25.00)));

        mvc.perform(patch("/accounts/{accountId}/freeze", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("FROZEN")));

        mvc.perform(patch("/accounts/{accountId}/unfreeze", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")));

        mvc.perform(patch("/accounts/{accountId}/close", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CLOSED")));
    }

    @Test
    void transferCommitsBalancesLedgerAuditAndOutboxRows() throws Exception {
        String source = createAccount("USD", "100.00");
        String destination = createAccount("USD", "50.00");

        String transferId = transfer(source, destination, "30.00", "key-success");

        mvc.perform(get("/accounts/{accountId}", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance", equalTo(70.00)));
        mvc.perform(get("/accounts/{accountId}", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance", equalTo(80.00)));
        mvc.perform(get("/transfers/{transferId}", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")));
        mvc.perform(get("/accounts/{accountId}/transactions?page=0&size=10", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].direction", equalTo("DEBIT")))
                .andExpect(jsonPath("$.items[0].transferId", equalTo(transferId)));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from ledger_entries", Long.class)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from outbox_events", Long.class)).isGreaterThanOrEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from audit_logs", Long.class)).isGreaterThanOrEqualTo(4);
    }

    @Test
    void failedTransferDoesNotCreateLedgerEntries() throws Exception {
        String source = createAccount("USD", "10.00");
        String destination = createAccount("USD", "50.00");

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-funds")
                        .content(transferBody(source, destination, "15.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", equalTo("INSUFFICIENT_FUNDS")))
                .andExpect(jsonPath("$.correlationId", startsWith("req_")));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from ledger_entries", Long.class)).isZero();
    }

    @Test
    void idempotencyReplayAndConflict() throws Exception {
        String source = createAccount("USD", "100.00");
        String destination = createAccount("USD", "0.00");

        String transferId = transfer(source, destination, "20.00", "same-key");

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key")
                        .content(transferBody(source, destination, "20.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferId", equalTo(transferId)));

        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key")
                        .content(transferBody(source, destination, "21.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", equalTo("IDEMPOTENCY_CONFLICT")));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from transfers", Long.class)).isEqualTo(1);
    }

    @Test
    void reversalCreatesCompensatingTransferAndMarksOriginalReversed() throws Exception {
        String source = createAccount("USD", "100.00");
        String destination = createAccount("USD", "0.00");
        String transferId = transfer(source, destination, "25.00", "key-reverse");

        mvc.perform(post("/transfers/{transferId}/reverse", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("REVERSED")));

        mvc.perform(get("/accounts/{accountId}", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance", equalTo(100.00)));
        mvc.perform(get("/accounts/{accountId}", destination))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance", equalTo(0.00)));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from ledger_entries", Long.class)).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from transfers", Long.class)).isEqualTo(2);
    }

    private String createAccount(String currency, String initialBalance) throws Exception {
        MvcResult result = mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"%s","initialBalance":"%s"}
                                """.formatted(currency, initialBalance)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId", startsWith("acct_")))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accountId");
    }

    private String transfer(String source, String destination, String amount, String key) throws Exception {
        MvcResult result = mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(transferBody(source, destination, amount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferId", startsWith("trf_")))
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.transferId");
    }

    private String transferBody(String source, String destination, String amount) {
        return """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":"%s","currency":"USD","description":"test"}
                """.formatted(source, destination, amount);
    }
}
