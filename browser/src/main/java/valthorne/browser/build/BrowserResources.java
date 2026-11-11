package valthorne.browser.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;
import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

/**
 * Registers exported engine and application resources with TeaVM's class loader.
 * Names are generated from the actual resource roots, preserving shared shader,
 * font, image and data loading without hard-coded application asset lists.
 */
public final class BrowserResources implements ResourceSupplier {
    /**
     * Creates the compiler-discovered resource supplier.
     */
    public BrowserResources() {
    }

    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        TreeSet<String> names = new TreeSet<>();
        try {
            for (String manifest : new String[]{"META-INF/valthorne-browser-resources.list", "META-INF/valthorne-application-resources.list"}) {
                var resources = context.getClassLoader().getResources(manifest);
                while (resources.hasMoreElements()) {
                    var connection = resources.nextElement().openConnection();
                    connection.setUseCaches(false);
                    try (var input = connection.getInputStream()) {
                        new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().filter(name -> !name.isBlank()).forEach(names::add);
                    }
                }
            }
            if (names.isEmpty()) throw new IllegalStateException("Missing browser resource manifests");
            return names.toArray(String[]::new);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
