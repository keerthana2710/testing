package com.lxp.pages;

import org.openqa.selenium.WebDriver;

import java.net.URI;

/** Checks that MFA redirected the browser to the home page. */
public class HomePage extends BasePage {

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return wait.until(driver -> {
            String path = URI.create(driver.getCurrentUrl()).getPath();
            return path.equals("/home") || path.startsWith("/home/");
        });
    }
}
