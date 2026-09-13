package valthorne.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dependency-free harness for committed compatibility consumers. Required case
 * names and minimum assertion counts prevent an empty or incomplete suite passing.
 * The sources are compiled into verification consumers, never engine artifacts.
 */
final class VerificationReport {
    /** Required behavior and minimum number of assertions proving execution. */
    record Case(String name, int minimumChecks) {
        Case {
            if (name.isBlank() || minimumChecks < 1) throw new IllegalArgumentException("Empty case");
        }
    }

    @FunctionalInterface
    interface Operation { void run() throws Throwable; }

    private record Result(String name, int checks, Throwable failure) {}

    private final String suite;
    private final Path output;
    private final List<Case> expected;
    private final List<Result> results = new ArrayList<>();
    private final Set<String> executed = new HashSet<>();
    private int currentChecks;
    private boolean running;

    VerificationReport(String suite, Path output, Case... expected) {
        this.suite = suite;
        this.output = output.toAbsolutePath();
        this.expected = List.of(expected);
        Set<String> names = new HashSet<>();
        for (Case specification : expected) {
            if (!names.add(specification.name())) throw new IllegalArgumentException("Duplicate case");
        }
        if (names.isEmpty()) throw new IllegalArgumentException("Empty suite");
    }

    /** Retains failures and continues so the JSON result covers every declared case. */
    void run(String name, Operation operation) {
        Case specification = expected.stream().filter(value -> value.name().equals(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Undeclared case: " + name));
        if (running || !executed.add(name)) throw new IllegalStateException("Repeated or nested case: " + name);
        currentChecks = 0;
        running = true;
        Throwable failure = null;
        try {
            operation.run();
            if (currentChecks < specification.minimumChecks()) {
                throw new AssertionError("Expected at least " + specification.minimumChecks()
                        + " assertions, executed " + currentChecks);
            }
        } catch (Throwable caught) {
            failure = caught;
            caught.printStackTrace(System.err);
        } finally {
            running = false;
            results.add(new Result(name, currentChecks, failure));
        }
    }

    /** Assertions always execute, independently of the JVM's optional -ea flag. */
    void require(boolean condition, String message) {
        if (!running) throw new IllegalStateException("Assertion outside a case");
        currentChecks++;
        if (!condition) throw new AssertionError(message);
    }

    void near(double actual, double expected, double tolerance, String message) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", received " + actual);
    }

    <T extends Throwable> T expect(Class<T> type, Operation operation) {
        Throwable failure = null;
        try { operation.run(); } catch (Throwable caught) { failure = caught; }
        require(type.isInstance(failure), "Expected " + type.getName() + ", received " + failure);
        return type.cast(failure);
    }

    /** Writes a result even for case failures; missing cases and report errors fail the process. */
    void finish() throws IOException {
        List<String> missing = expected.stream().map(Case::name)
                .filter(name -> !executed.contains(name)).toList();
        long passed = results.stream().filter(result -> result.failure() == null).count();
        int checks = results.stream().mapToInt(Result::checks).sum();
        boolean success = missing.isEmpty() && passed == expected.size();
        StringBuilder json = new StringBuilder("{\n  \"schema\": 1,\n  \"suite\": ")
                .append(quote(suite)).append(",\n  \"status\": ").append(quote(success ? "passed" : "failed"))
                .append(",\n  \"java\": ").append(quote(System.getProperty("java.version")))
                .append(",\n  \"os\": ").append(quote(System.getProperty("os.name")))
                .append(",\n  \"arch\": ").append(quote(System.getProperty("os.arch")))
                .append(",\n  \"expectedCases\": ").append(expected.size())
                .append(",\n  \"executedCases\": ").append(results.size())
                .append(",\n  \"passedCases\": ").append(passed)
                .append(",\n  \"checks\": ").append(checks).append(",\n  \"cases\": [");
        for (int index = 0; index < results.size(); index++) {
            Result result = results.get(index);
            if (index > 0) json.append(',');
            json.append("\n    {\"name\": ").append(quote(result.name()))
                    .append(", \"checks\": ").append(result.checks())
                    .append(", \"status\": ").append(quote(result.failure() == null ? "passed" : "failed"))
                    .append(", \"failure\": ")
                    .append(result.failure() == null ? "null" : quote(result.failure().toString())).append('}');
        }
        json.append("\n  ],\n  \"missingCases\": [");
        for (int index = 0; index < missing.size(); index++) {
            if (index > 0) json.append(", ");
            json.append(quote(missing.get(index)));
        }
        json.append("]\n}\n");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json);
        System.out.println((success ? "VERIFIED " : "FAILED ") + suite + " cases=" + passed
                + "/" + expected.size() + " checks=" + checks + " report=" + output);
        if (!success) {
            AssertionError failure = new AssertionError("Compatibility verification failed: " + suite);
            results.stream().map(Result::failure).filter(value -> value != null).forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static String quote(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) json.append(String.format("\\u%04x", (int) character));
                    else json.append(character);
                }
            }
        }
        return json.append('"').toString();
    }
}
