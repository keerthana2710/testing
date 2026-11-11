package com.lxp.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;

/** The 6-digit emailed-code step shown after a valid password. */
public class MfaPage extends BasePage {

    private static final By OTP_INPUT = By.cssSelector("input[data-input-otp='true']");
    private static final By VERIFY_CODE = By.xpath(
            "//button[translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')='verify code']");

    public MfaPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return isVisible(OTP_INPUT, Duration.ofSeconds(15));
    }

    public MfaPage enterVerificationCode(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Verification code must contain exactly 6 digits");
        }

        type(OTP_INPUT, code);
        return this;
    }

    public MfaPage clickVerifyCode() {
        waitUntilEnabled(VERIFY_CODE);
        click(VERIFY_CODE);
        return this;
    }
}
