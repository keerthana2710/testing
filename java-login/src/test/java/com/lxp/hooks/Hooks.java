package com.lxp.hooks;

import com.lxp.config.Settings;
import com.lxp.driver.DriverFactory;
import com.lxp.report.CheckLog;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Scenario;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Screenshot-on-failure, plus a clean browser between scenarios. */
public class Hooks {

    /**
     * Soft checks let the scenario run to the end; fail it here if any missed,
     * so the run still shows red.
     */
    @After(order = 20000)
    public void failOnSoftChecks() {
        List<String> failed = CheckLog.takeSoftFailures();
        if (!failed.isEmpty()) {
            throw new AssertionError("Soft checks failed:\n- " + String.join("\n- ", failed));
        }
    }

    @After
    public void tearDown(Scenario scenario) {
        if (!DriverFactory.isRunning()) {
            // the scenario closed the browser itself
            return;
        }
        WebDriver driver = DriverFactory.get();

        if (scenario.isFailed()) {
            capture(driver, scenario);
        }

        try {
            driver.manage().deleteAllCookies();
            ((JavascriptExecutor) driver)
                    .executeScript("localStorage.clear(); sessionStorage.clear();");
        } catch (RuntimeException ignored) {
            // a blank page has no storage to clear
        }
    }

    /** One browser for the whole run, closed once at the end. */
    @AfterAll
    public static void closeBrowser() {
        DriverFactory.quit();
    }

    private void capture(WebDriver driver, Scenario scenario) {
        try {
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            scenario.attach(png, "image/png", scenario.getName());

            Files.createDirectories(Settings.SCREENSHOT_DIR);
            String name = "FAIL_" + scenario.getName().replaceAll("[^A-Za-z0-9._-]+", "_") + ".png";
            Path path = Settings.SCREENSHOT_DIR.resolve(name);
            Files.write(path, png);
            System.out.println("[screenshot] " + path.toAbsolutePath() + " (page: " + driver.getCurrentUrl() + ")");
        } catch (Exception ignored) {
            // never let the screenshot hide the real failure
        }
    }
}
