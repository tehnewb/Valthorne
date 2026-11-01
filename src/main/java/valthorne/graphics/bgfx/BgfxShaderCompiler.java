package valthorne.graphics.bgfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.lwjgl.system.Platform;

/**
 * Compiles portable bgfx source with the packaged, version-pinned shaderc tool.
 * Extraction occurs on first source use, not engine startup. Each cold compilation
 * uses isolated temporary files and a bounded child process; diagnostics never
 * block on an unread pipe. No shell, network, installation or PATH lookup is used.
 * BgfxShaderStage retains successful bytecode across device recovery. Calls belong
 * to the graphics thread. The browser artifact supplies an in-memory WebAssembly
 * implementation of this same boundary.
 */
final class BgfxShaderCompiler {
    /**
     * Maximum compiler diagnostic bytes included in an exception.
     */
    private static final int MAX_DIAGNOSTICS = 65_536;
    /**
     * Lazily extracted compiler directory, confined to the graphics thread.
     */
    private static Path tools;

    /**
     * Prevents constructing the cold compiler service.
     */
    private BgfxShaderCompiler() {
    }

    /**
     * Compiles one stage using the active renderer's portable shader profile.
     *
     * @param source portable bgfx source, including its input/output directives
     * @param varyings optional shared varying declarations; null selects engine defaults
     * @param type compiler stage letter v, f or c
     * @param family compiled backend family
     * @return complete version-matched bgfx container
     */
    static byte[] compile(String source, String varyings, char type, String family) {
        try {
            Path compiler = compiler();
            Path directory = Files.createTempDirectory("valthorne-shader-");
            Path input = directory.resolve("source.sc");
            Path output = directory.resolve("output.bin");
            Path varying = directory.resolve("varying.def.sc");
            Path diagnostics = directory.resolve("diagnostics.txt");
            Throwable failure = null;
            try {
                Files.writeString(input, source, StandardCharsets.UTF_8);
                if (varyings == null) copy("/valthorne/shaders/bgfx/engine/varying.def.sc", varying);
                else Files.writeString(varying, varyings, StandardCharsets.UTF_8);
                String profile = switch (family) {
                    case "glsl" -> "430";
                    case "spirv" -> "spirv";
                    case "dx11" -> "s_5_0";
                    case "essl" -> "300_es";
                    default -> throw new UnsupportedOperationException("No source compiler profile for " + family);
                };
                String platform = family.equals("essl") ? "asm.js" : switch (Platform.get()) {
                    case WINDOWS -> "windows";
                    case MACOSX -> "osx";
                    default -> "linux";
                };
                Process process = new ProcessBuilder(compiler.toString(), "-f", input.toString(), "-o", output.toString(), "--type", Character.toString(type), "--platform", platform, "--profile", profile, "--varyingdef", varying.toString(), "-i", tools.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(diagnostics.toFile())
                        .start();
                try {
                    if (!process.waitFor(30, TimeUnit.SECONDS)) {
                        process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                        throw new IllegalArgumentException("Shader compilation exceeded 30 seconds");
                    }
                } catch (InterruptedException interrupted) {
                    process.destroyForcibly();
                    try {
                        process.waitFor(5, TimeUnit.SECONDS);
                    } catch (InterruptedException repeated) {
                        interrupted.addSuppressed(repeated);
                    } finally {
                        Thread.currentThread().interrupt();
                    }
                    throw new IllegalStateException("Shader compilation interrupted", interrupted);
                }
                if (process.exitValue() != 0) {
                    try (InputStream stream = Files.newInputStream(diagnostics)) {
                        throw new IllegalArgumentException("Could not compile " + type + " shader:\n" + new String(stream.readNBytes(MAX_DIAGNOSTICS), StandardCharsets.UTF_8));
                    }
                }
                return Files.readAllBytes(output);
            } catch (IOException | RuntimeException | Error caught) {
                failure = caught;
                throw caught;
            } finally {
                try {
                    Files.deleteIfExists(input);
                    Files.deleteIfExists(output);
                    Files.deleteIfExists(varying);
                    Files.deleteIfExists(diagnostics);
                    Files.delete(directory);
                } catch (IOException cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not run the packaged shader compiler", failure);
        }
    }

    /**
     * Extracts the current host's pinned executable and standard includes once.
     * Files are deleted when the JVM exits; compiled stage workspaces are shorter lived.
     *
     * @return owner-executable compiler path
     * @throws IOException if extraction fails
     */
    private static Path compiler() throws IOException {
        boolean windows = Platform.get() == Platform.WINDOWS;
        String executable = windows ? "shaderc.exe" : "shaderc";
        if (tools != null) return tools.resolve(executable);
        String os = switch (Platform.get()) {
            case WINDOWS -> "windows";
            case LINUX -> "linux";
            case MACOSX -> "macosx";
            default -> throw new UnsupportedOperationException("No shader compiler for this operating system");
        };
        String architecture = switch (Platform.getArchitecture()) {
            case X64 -> "x64";
            case ARM64 -> "arm64";
            case ARM32 -> "arm32";
            default -> throw new UnsupportedOperationException("No shader compiler for this architecture");
        };
        Path directory = Files.createTempDirectory("valthorne-shaderc-");
        directory.toFile().deleteOnExit();
        Path compiler = directory.resolve(executable);
        compiler.toFile().deleteOnExit();
        copy("/valthorne/shaderc/" + os + "/" + architecture + "/" + executable, compiler);
        if (!compiler.toFile().setExecutable(true, true)) throw new IOException("Could not make the shader compiler executable");
        for (String header : new String[]{"bgfx_shader.sh", "bgfx_compute.sh"}) {
            Path file = directory.resolve(header);
            file.toFile().deleteOnExit();
            copy("/valthorne/shaderc/include/" + header, file);
        }
        tools = directory;
        return compiler;
    }

    /**
     * Copies a required packaged compiler resource without taking caller ownership.
     *
     * @param resource absolute classpath resource
     * @param destination newly created destination path
     * @throws IOException if reading or writing fails
     */
    private static void copy(String resource, Path destination) throws IOException {
        try (InputStream stream = BgfxShaderCompiler.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing packaged shader compiler resource: " + resource);
            Files.copy(stream, destination);
        }
    }
}
