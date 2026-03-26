package org.bank.moneytransfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@WebMvcTest(MoneyTransferApplication.class)
@AutoConfigureRestTestClient
class MoneyTransferApplicationTests {
    @Autowired private RestTestClient client;
    @Autowired private MoneyTransferApplication app;

    @BeforeEach void setUp() { app.reset(); }

    @Test
    void testEndToEndTransfer() {
        client.post().uri("/api/accounts").body(new MoneyTransferApplication.CreateReq("Adam", new BigDecimal("100"))).exchange().expectStatus().isCreated();
        client.post().uri("/api/accounts").body(new MoneyTransferApplication.CreateReq("Richard", new BigDecimal("50"))).exchange().expectStatus().isCreated();
        client.post().uri("/api/transfers").body(new MoneyTransferApplication.TransferReq("Adam", "Richard", new BigDecimal("30"))).exchange().expectStatus().isOk();
        assertThat(app.repo.get("Adam").balance).isEqualByComparingTo("70");
        assertThat(app.repo.get("Richard").balance).isEqualByComparingTo("80");
    }

    @Test
    void testErrorTransferNotFound(){
        client.post().uri("/api/accounts").body(new MoneyTransferApplication.CreateReq("Adam", new BigDecimal("100"))).exchange().expectStatus().isCreated();
        client.post().uri("/api/transfers").body(new MoneyTransferApplication.TransferReq("Adam", "Richard", new BigDecimal("30"))).exchange().expectStatus().isNotFound();
    }

    @Test
    void testErrorTransferBadRequest() {
        client.post().uri("/api/accounts").body(new MoneyTransferApplication.CreateReq("Adam", new BigDecimal("10"))).exchange().expectStatus().isCreated();
        client.post().uri("/api/accounts").body(new MoneyTransferApplication.CreateReq("Richard", new BigDecimal("50"))).exchange().expectStatus().isCreated();
        client.post().uri("/api/transfers").body(new MoneyTransferApplication.TransferReq("Adam", "Richard", new BigDecimal("15"))).exchange().expectStatus().isBadRequest();
    }

    @Test
    void testValidationError() {
        client.post().uri("/api/accounts").body(new MoneyTransferApplication.CreateReq("", new BigDecimal("-1"))).exchange().expectStatus().isBadRequest();
    }

    @Test void testConcurrency() throws  Exception {
        app.create(new MoneyTransferApplication.CreateReq("A", new BigDecimal("1000")));
        app.create(new MoneyTransferApplication.CreateReq("B", new BigDecimal("1000")));

        int n = 100;

        ExecutorService ex = Executors.newFixedThreadPool(20);
        CountDownLatch count = new CountDownLatch(n * 2);

        for (int i = 0; i < n; i++) {
            ex.submit(() -> { try { app.transfer(new MoneyTransferApplication.TransferReq("A", "B", BigDecimal.ONE)); } finally { count.countDown(); } });
            ex.submit(() -> { try { app.transfer(new MoneyTransferApplication.TransferReq("B", "A", BigDecimal.ONE)); } finally { count.countDown(); } });
        }
        count.await();
        ex.shutdown();

        assertThat(app.repo.get("A").balance).isEqualByComparingTo("1000");
    }
}
