package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperClientConfig;
import de.codemorra.sheeper.config.SheeperConfig;

/** Seconds retain update precision and consistent client pulse timing. */
final class ConfigUnitGameTests {
    private ConfigUnitGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("config_seconds_precision", ConfigUnitGameTests::precision));
    }

    private static void precision(GameTestContext context) {
        context.expect(SheeperConfig.secondsToTicks(0) == 0
                && SheeperConfig.secondsToTicks(0.01D) == 1
                && SheeperConfig.secondsToTicks(0.15D) == 3
                && SheeperConfig.secondsToTicks(0.3D) == 6
                && SheeperConfig.secondsToTicks(0.051D) == 2,
                "Seconds must round upward to update precision while preserving zero");
        context.near(Math.pow(SheeperClientConfig.pulseRetention(2.0D), 40), 0.1D,
                "A two-second pulse decay must retain ten percent after two seconds");
        context.near(SheeperClientConfig.pulseRetention(0), 0.0D, "Zero pulse duration must respond immediately");
        context.pass();
    }
}
