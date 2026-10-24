package valthorne.browser;

import java.util.ArrayDeque;

/**
 * Normalizes logical resource names without TeaVM's relative Path.normalize path,
 * which attempts a negative source range for the first segment. Resource paths stay
 * relative, preserving classpath lookup and pages hosted below the origin root.
 * This parse-time operation never touches files or resolves symbolic links.
 */
public final class BrowserPaths {
    /**
     * Prevents constructing stateless resource path processors.
     */
    private BrowserPaths() {
    }

    /**
     * Collapses separators and dot segments, retaining unmatched relative parents.
     * @param path plain workspace or resource path, not a URL
     * @return normalized path with the same absolute/relative interpretation
     */
    public static String normalize(String path) {
        /*
         * A segment stack preserves leading relative parents rather than resolving
         * against an invented origin, which would change embedded resource keys.
         */
        String source = path.replace('\\', '/');
        boolean absolute = source.startsWith("/");
        ArrayDeque<String> segments = new ArrayDeque<>();
        int start = 0;
        for (int i = 0; i <= source.length(); i++) {
            if (i != source.length() && source.charAt(i) != '/') continue;
            String part = source.substring(start, i);
            start = i + 1;
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!segments.isEmpty() && !segments.peekLast().equals("..")) segments.removeLast();
                else if (!absolute) segments.addLast(part);
            } else segments.addLast(part);
        }
        StringBuilder result = new StringBuilder(source.length());
        if (absolute) result.append('/');
        for (String segment : segments) {
            if (!result.isEmpty() && result.charAt(result.length() - 1) != '/') result.append('/');
            result.append(segment);
        }
        return result.toString();
    }
}
