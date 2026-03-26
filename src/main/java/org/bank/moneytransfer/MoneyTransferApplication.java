package org.bank.moneytransfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
@RestController
public class MoneyTransferApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoneyTransferApplication.class, args);
    }

    public final Map<String, Account> repo = new HashMap<>();

    @PostMapping("/api/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody @Valid CreateReq r) { repo.put(r.id, new Account(r.id, r.initialBalance)); }

    @GetMapping("/api/accounts/{id}")
    public Account get(@PathVariable String id) { return opt(id); }

    @PostMapping("/api/transfers")
    public void transfer(@RequestBody @Valid TransferReq r) {
        Account sender = opt(r.fromId), receiver = opt(r.toId);
        Account lockFirst = r.fromId.compareTo(r.toId) < 0 ? sender : receiver;
        Account lockSecond = lockFirst == sender ? receiver : sender;

        synchronized (lockFirst) {
            synchronized (lockSecond) {
                sender.withdrawal(r.amount);
                receiver.deposit(r.amount);
            }
        }

    }

    private Account opt(String id) {
        return Optional.ofNullable(repo.get(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + id));
    }

    public void reset() {
        repo.clear();
    }

    public record CreateReq(@NotBlank String id, @NotNull @PositiveOrZero BigDecimal initialBalance) {}
    public record TransferReq(@NotBlank String fromId, @NotBlank String toId, @NotNull @Positive BigDecimal amount) {}

    public static class Account {

        public final String id;
        public BigDecimal balance;

        public Account(String id, BigDecimal balance) {
            this.id = id;
            this.balance = balance.setScale(2, RoundingMode.HALF_EVEN);
        }

        public synchronized void withdrawal(BigDecimal amount) {
            if (balance.compareTo(amount) < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds in account: " + id);
            balance = balance.subtract(amount);
        }

        public synchronized void deposit(BigDecimal amount) {
            balance = balance.add(amount);
        }
    }
}
