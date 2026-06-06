package org.bank.moneytransfer.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {
    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String accountId() { return "acct_" + token(); }
    public String transferId() { return "trf_" + token(); }
    public String ledgerId() { return "txn_" + token(); }
    public String eventId() { return "evt_" + token(); }
    public String auditId() { return "aud_" + token(); }

    private String token() {
        char[] chars = new char[18];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }
}
