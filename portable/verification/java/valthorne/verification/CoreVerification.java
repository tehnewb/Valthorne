package valthorne.verification;

import java.io.IOException;
import java.nio.file.Path;
import valthorne.Application;
import valthorne.collections.array.SwapOnRemoveArray;
import valthorne.collections.map.StringObjectMap;
import valthorne.collections.stack.IntFastStack;
import valthorne.portable.FrameLoop;

/** Executable shared-engine compatibility checks requiring no native libraries. */
public final class CoreVerification {
    private CoreVerification() {}

    /** Runs all declared cases; the optional first argument selects the JSON report. */
    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length == 0 ? "build/reports/verification/core.json" : args[0]);
        VerificationReport report = new VerificationReport("portable-core", output,
                new VerificationReport.Case("frame-loop-timing-and-pause", 7),
                new VerificationReport.Case("frame-loop-lifecycle-and-invalid-time", 8),
                new VerificationReport.Case("frame-loop-initialization-failure", 4),
                new VerificationReport.Case("map-growth-and-probe-chain-deletion", 2002),
                new VerificationReport.Case("dense-array-and-stack-growth", 103));
        report.run("frame-loop-timing-and-pause", () -> timing(report));
        report.run("frame-loop-lifecycle-and-invalid-time", () -> lifecycle(report));
        report.run("frame-loop-initialization-failure", () -> failedInitialization(report));
        report.run("map-growth-and-probe-chain-deletion", () -> mapGrowth(report));
        report.run("dense-array-and-stack-growth", () -> arrayAndStackGrowth(report));
        report.finish();
    }

    private static void timing(VerificationReport report) {
        RecordingApplication application = new RecordingApplication();
        FrameLoop loop = new FrameLoop(application, 1f / 60, 5);
        loop.start();
        report.require(application.starts == 1, "Initialize once");
        for (int index = 0; index < 20; index++) {
            loop.setPaused(false);
            loop.frame((1f / 60) / 2.0);
        }
        report.require(application.updates == 10, "Repeated pause state preserves fractional time");
        report.require(application.renders == 20, "Render each high-refresh frame");
        loop.setPaused(true);
        loop.frame(100);
        report.require(application.updates == 10, "Paused frames do not advance gameplay");
        loop.setPaused(false);
        loop.frame(100);
        report.require(application.updates == 15, "Limit catch-up after a long frame");
        loop.close();
        loop.close();
        report.require(application.closes == 1, "Dispose exactly once");
        report.expect(IllegalStateException.class, () -> loop.frame(0));
    }

    private static void lifecycle(VerificationReport report) {
        RecordingApplication application = new RecordingApplication();
        FrameLoop loop = new FrameLoop(application, .01f, 5);
        report.expect(IllegalStateException.class, () -> loop.frame(0));
        loop.start();
        report.expect(IllegalStateException.class, loop::start);
        report.expect(IllegalArgumentException.class, () -> loop.frame(Double.NaN));
        report.expect(IllegalArgumentException.class, () -> loop.frame(Double.POSITIVE_INFINITY));
        report.expect(IllegalArgumentException.class, () -> loop.frame(-1));
        report.require(application.updates == 0, "Invalid input cannot reach update");
        report.require(application.renders == 0, "Invalid input cannot reach render");
        loop.close();
        report.require(application.closes == 1, "Dispose after rejected frame input");
    }

    private static void failedInitialization(VerificationReport report) {
        IllegalStateException original = new IllegalStateException("init");
        int[] disposals = {0};
        FrameLoop loop = new FrameLoop(new Application() {
            public void init() { throw original; }
            public void update(float delta) {}
            public void render() {}
            public void dispose() {
                disposals[0]++;
                throw new IllegalStateException("cleanup");
            }
        }, .01f, 2);
        report.require(report.expect(IllegalStateException.class, loop::start) == original,
                "Preserve the initialization failure");
        report.require(original.getSuppressed().length == 1
                        && "cleanup".equals(original.getSuppressed()[0].getMessage()),
                "Attach cleanup failure to the original exception");
        loop.close();
        report.require(disposals[0] == 1, "Failed initialization disposes exactly once");
    }

    private static void mapGrowth(VerificationReport report) {
        StringObjectMap<Integer> map = new StringObjectMap<>(2);
        for (int index = 0; index < 2000; index++) map.put("key" + index, index);
        for (int index = 0; index < 2000; index += 2) {
            report.require(Integer.valueOf(index).equals(map.remove("key" + index)), "Remove key " + index);
        }
        for (int index = 1; index < 2000; index += 2) {
            report.require(Integer.valueOf(index).equals(map.get("key" + index)), "Retain key " + index);
        }
        report.require(map.size() == 1000, "Preserve size after probe-chain deletion");
        map.clear();
        report.require(map.isEmpty(), "Clear a grown map");
    }

    private static void arrayAndStackGrowth(VerificationReport report) {
        SwapOnRemoveArray<Integer> array = new SwapOnRemoveArray<>();
        IntFastStack stack = new IntFastStack(2);
        for (int index = 0; index < 100; index++) {
            array.add(index);
            stack.push(index);
        }
        report.require(array.remove(0) == 0, "Return the removed element");
        report.require(array.get(0) == 99, "Fill the removed slot with the last element");
        for (int index = 99; index >= 0; index--) {
            report.require(stack.pop() == index, "Preserve stack order at " + index);
        }
        report.require(stack.isEmpty(), "Drain a grown stack");
    }

    private static final class RecordingApplication implements Application {
        private int starts, updates, renders, closes;
        public void init() { starts++; }
        public void update(float delta) { updates++; }
        public void render() { renders++; }
        public void dispose() { closes++; }
    }
}
