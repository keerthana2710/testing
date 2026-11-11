package com.lxp.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Asks for the emailed 6-digit verification code in the terminal. The code is
 * never read from .env: every MFA prompt waits for you to type it.
 */
public final class OtpPrompt {

    // One reader for the whole run: a scenario can sign in more than once.
    private static final BufferedReader IN =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private OtpPrompt() {
    }

    public static synchronized String ask() {
        System.out.println();
        System.out.print(">>> Enter the 6-digit OTP from your email and press Enter: ");
        System.out.flush();
        try {
            for (String line; (line = IN.readLine()) != null; ) {
                String code = line.strip();
                if (code.matches("\\d{6}")) {
                    return code;
                }
                System.out.print(">>> That is not 6 digits, try again: ");
                System.out.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the OTP from the terminal", e);
        }
        throw new IllegalStateException("No terminal input: run the tests from a terminal to enter the OTP");
    }
}
