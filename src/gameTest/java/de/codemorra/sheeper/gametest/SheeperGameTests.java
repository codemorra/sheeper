package de.codemorra.sheeper.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.xml.parsers.ParserConfigurationException;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Registers required gameplay regressions in separate, sequential batches.
 * Each batch has one scene so settings and explosions cannot affect another
 * test.
 * The shared test_arena template is 24 x 9 x 24 blocks with a bedrock floor.
 */
@GameTestHolder("sheeper")
public final class SheeperGameTests {
    private static final int PREPARATION_TICKS = 100;

    private SheeperGameTests() {
    }

    /** A named behavioral contract and its maximum time in game ticks. */
    record Scenario(String name, int timeout, Consumer<GameTestContext> test) {
        Scenario(String name, Consumer<GameTestContext> test) {
            this(name, 240, test);
        }
    }

    @GameTestGenerator
    public static Collection<TestFunction> suite() {
        List<Scenario> scenarios = new ArrayList<>();
        AdultGameTests.add(scenarios);
        WoolGameTests.add(scenarios);
        CarpetGameTests.add(scenarios);
        MuttonGameTests.add(scenarios);
        LifecycleGameTests.add(scenarios);
        ElementalGameTests.add(scenarios);
        LightningGameTests.add(scenarios);
        LightningTeleportGameTests.add(scenarios);
        WaterGameTests.add(scenarios);
        WaterCombatGameTests.add(scenarios);
        WaterPreparationGameTests.add(scenarios);
        WaterProximityGameTests.add(scenarios);
        WaterProjectileGameTests.add(scenarios);
        WaterAttackGameTests.add(scenarios);
        WaterSalvoGameTests.add(scenarios);
        WaterFountainGameTests.add(scenarios);
        BossWaterGameTests.add(scenarios);
        WaterWaveGameTests.add(scenarios);
        DataGameTests.add(scenarios);
        ConfigUnitGameTests.add(scenarios);

        List<TestFunction> tests = scenarios.stream().map(scenario -> {
            String name = "sheeper." + scenario.name();
            return new TestFunction(name, name, "sheeper:test_arena", scenario.timeout() + PREPARATION_TICKS, 0, true,
                    helper -> helper.startSequence()
                            .thenWaitUntil(() -> GameTestContext.requireReady(helper))
                            .thenExecute(() -> {
                                GameTestContext context = new GameTestContext(helper);
                                try {
                                    scenario.test().accept(context);
                                } catch (RuntimeException failure) {
                                    context.close();
                                    throw failure;
                                }
                            }));
        }).toList();
        try {
            Path directory = Path.of(System.getProperty("sheeper.gameTestReportDirectory"));
            Files.createDirectories(directory);
            Files.write(directory.resolve("expected-tests.txt"),
                    tests.stream().map(TestFunction::getTestName).toList(), StandardCharsets.UTF_8);
            GlobalTestReporter.replaceWith(new SuiteReporter(directory));
        } catch (IOException | ParserConfigurationException failure) {
            throw new IllegalStateException("Cannot initialize the GameTest result report", failure);
        }
        return tests;
    }

    /**
     * Retains console diagnostics and writes machine-readable results, including
     * failures.
     */
    private static final class SuiteReporter implements TestReporter {
        private final LogTestReporter log = new LogTestReporter();
        private final JUnitLikeTestReporter xml;
        private boolean finished;

        SuiteReporter(Path directory) throws ParserConfigurationException {
            xml = new JUnitLikeTestReporter(directory.resolve("results.xml").toFile());
        }

        @Override
        public void onTestFailed(GameTestInfo test) {
            GameTestContext.closeActive();
            log.onTestFailed(test);
            xml.onTestFailed(test);
            finish();
            test.getLevel().getServer().halt(false);
        }

        @Override
        public void onTestSuccess(GameTestInfo test) {
            log.onTestSuccess(test);
            xml.onTestSuccess(test);
        }

        @Override
        public void finish() {
            if (!finished) {
                finished = true;
                GameTestContext.closeActive();
                xml.finish();
            }
        }
    }
}
