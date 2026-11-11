package com.lxp.driver;

import com.lxp.config.Settings;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.interactions.Interactive;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the browser. One instance is reused for the whole run -- launching a
 * browser per scenario is the slow part.
 *
 * Selenium Manager fetches a matching driver binary automatically, so there is
 * nothing to install by hand.
 */
public final class DriverFactory {

    /** The browser actually running; replaced if it crashes (see restartKeepingSession). */
    private static WebDriver real;

    /**
     * What every page and step holds: it forwards each call to the browser
     * running now, so a restarted browser carries on behind the same object.
     */
    private static final WebDriver HANDLE = (WebDriver) Proxy.newProxyInstance(
            DriverFactory.class.getClassLoader(),
            new Class<?>[] {WebDriver.class, JavascriptExecutor.class, TakesScreenshot.class, Interactive.class,
                    HasCdp.class, HasCapabilities.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> "DriverFactory handle -> " + real;
                    };
                }
                try {
                    return method.invoke(current(), args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });

    /** The signed-in session (the app keeps it in localStorage), saved by saveSession(). */
    private static Map<String, String> savedSession = Map.of();

    // save downloads into target/downloads without asking
    private static final Map<String, Object> DOWNLOAD_PREFS = Map.of(
            "download.default_directory", Settings.DOWNLOAD_DIR.toString(),
            "download.prompt_for_download", false);

    private DriverFactory() {
    }

    public static synchronized WebDriver get() {
        current();
        return HANDLE;
    }

    private static synchronized WebDriver current() {
        if (real == null) {
            real = build(Settings.BROWSER);
            real.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45));
        }
        return real;
    }

    /** False before the first get() and after quit(), so hooks don't launch a browser just to clean it. */
    public static synchronized boolean isRunning() {
        return real != null;
    }

    public static synchronized void quit() {
        if (real != null) {
            try {
                real.quit();
            } catch (WebDriverException ignored) {
                // already gone
            }
            real = null;
        }
    }

    /**
     * Saves the signed-in session (localStorage of this site) so that
     * restartKeepingSession() can carry on after a browser crash. Call it
     * while on a page of the site.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void saveSession() {
        Object items = ((JavascriptExecutor) current()).executeScript(
                "const o = {}; for (const k of Object.keys(localStorage)) o[k] = localStorage.getItem(k); return o;");
        if (items instanceof Map<?, ?> map && !map.isEmpty()) {
            savedSession = new LinkedHashMap<>((Map<String, String>) map);
        }
    }

    /** The browser has crashed or closed: its session is gone. */
    public static boolean isBrowserGone(Throwable e) {
        if (e instanceof NoSuchSessionException) {
            return true;
        }
        String m = String.valueOf(e.getMessage());
        return m.contains("not connected to DevTools") || m.contains("session deleted")
                || m.contains("chrome not reachable") || m.contains("tab crashed") || m.contains("target window already closed");
    }

    /**
     * After a crash: starts a new browser and puts the saved session back, so
     * the run carries on signed in (no new sign-in or OTP). Waits that last
     * many minutes (course plans and generation) use this.
     */
    public static synchronized void restartKeepingSession() {
        quit();
        WebDriver fresh = current();
        fresh.get(Settings.BASE_URL + "/welcome");
        ((JavascriptExecutor) fresh).executeScript(
                "const s = arguments[0]; for (const k of Object.keys(s)) localStorage.setItem(k, s[k]);", savedSession);
        fresh.get(Settings.BASE_URL + "/home");
        System.out.println("[driver] the browser crashed; started a new one and restored the signed-in session ("
                + savedSession.size() + " items)");
    }

    private static WebDriver build(String browser) {
        return switch (browser) {
            case "firefox" -> {
                FirefoxOptions opts = new FirefoxOptions();
                if (Settings.HEADLESS) {
                    opts.addArguments("-headless");
                }
                opts.addArguments("--width=1440", "--height=900");
                opts.addPreference("browser.download.folderList", 2);
                opts.addPreference("browser.download.dir", Settings.DOWNLOAD_DIR.toString());
                opts.addPreference("browser.helperApps.neverAsk.saveToDisk", "application/octet-stream,application/zip");
                yield new FirefoxDriver(opts);
            }
            case "edge" -> {
                EdgeOptions opts = new EdgeOptions();
                if (Settings.HEADLESS) {
                    opts.addArguments("--headless=new");
                }
                opts.addArguments("--window-size=1440,900", "--disable-gpu", "--disable-notifications");
                opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
                opts.setExperimentalOption("prefs", DOWNLOAD_PREFS);
                yield new EdgeDriver(opts);
            }
            case "chrome" -> {
                ChromeOptions opts = new ChromeOptions();
                if (Settings.HEADLESS) {
                    opts.addArguments("--headless=new");
                }
                opts.addArguments("--window-size=1440,900", "--disable-gpu", "--disable-notifications");
                opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
                opts.setExperimentalOption("prefs", DOWNLOAD_PREFS);
                ChromeDriver chrome = new ChromeDriver(opts);
                // headless Chrome ignores the download prefs unless told over DevTools too
                chrome.executeCdpCommand("Browser.setDownloadBehavior",
                        Map.of("behavior", "allow", "downloadPath", Settings.DOWNLOAD_DIR.toString()));
                yield chrome;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported BROWSER '" + browser + "' -- use chrome, firefox or edge");
        };
    }
}
