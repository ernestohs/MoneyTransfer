package org.bank.moneytransfer.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.bank.moneytransfer.config.CorrelationIds;
import org.bank.moneytransfer.dto.TransferQuoteResponse;
import org.bank.moneytransfer.dto.TransferRequest;
import org.bank.moneytransfer.dto.TransferResponse;
import org.bank.moneytransfer.service.TransferService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers/quote")
    public TransferQuoteResponse quote(@RequestBody @Valid TransferRequest request, HttpServletRequest httpRequest) {
        return transferService.quote(request, CorrelationIds.current(httpRequest));
    }

    @PostMapping(value = "/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestBody @Valid TransferRequest request,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                         HttpServletRequest httpRequest) {
        return transferService.create(request, idempotencyKey, CorrelationIds.current(httpRequest));
    }

    @GetMapping("/transfers/{transferId}")
    public TransferResponse get(@PathVariable String transferId, HttpServletRequest httpRequest) {
        return transferService.get(transferId, CorrelationIds.current(httpRequest));
    }

    @PostMapping("/transfers/{transferId}/cancel")
    public TransferResponse cancel(@PathVariable String transferId, HttpServletRequest httpRequest) {
        return transferService.cancel(transferId, CorrelationIds.current(httpRequest));
    }

    @PostMapping("/transfers/{transferId}/reverse")
    public TransferResponse reverse(@PathVariable String transferId, HttpServletRequest httpRequest) {
        return transferService.reverse(transferId, CorrelationIds.current(httpRequest));
    }
}
