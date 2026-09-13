package valthorne.website.export;

import java.util.Locale;

/**
 * Creates the landing page's original architectural landscape illustration.
 *
 * <p>All geometry, colors, and composition are authored here. Layered polygonal
 * mountains establish atmospheric depth; an oblique projection gives the stone
 * monument its thickness. Deterministic detail keeps repeated exports identical.
 * The result is original promotional artwork, not a screenshot of a game or a
 * demonstration of Valthorne's real-time renderer.</p>
 *
 * <p>The SVG is self-contained: it references no images, fonts, scripts, or
 * external resources. Its quiet left side and shaded lower edge deliberately
 * leave room for the page's separate, accessible HTML heading and introduction.
 * The artwork includes no lettering or interface elements.</p>
 */
public final class WorldArtwork {
    private WorldArtwork() {}

    /** Returns a deterministic, standalone 1920 by 1080 landscape SVG. */
    public static String svg() {
        StringBuilder out = new StringBuilder(48000);
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1920\" height=\"1080\" viewBox=\"0 0 1920 1080\">")
                .append("<defs>")
                .append("<linearGradient id=\"sky\" x2=\"0\" y2=\"1\"><stop stop-color=\"#bccdce\"/><stop offset=\".65\" stop-color=\"#e6e1cc\"/><stop offset=\"1\" stop-color=\"#f2e7cc\"/></linearGradient>")
                .append("<radialGradient id=\"sun\"><stop stop-color=\"#fff7d9\" stop-opacity=\".66\"/><stop offset=\"1\" stop-color=\"#fff7d9\" stop-opacity=\"0\"/></radialGradient>")
                .append("<linearGradient id=\"haze\" x2=\"0\" y2=\"1\"><stop stop-color=\"#e6dfc5\" stop-opacity=\"0\"/><stop offset=\"1\" stop-color=\"#e6dfc5\" stop-opacity=\".36\"/></linearGradient>")
                .append("<linearGradient id=\"left-shade\"><stop stop-color=\"#081921\" stop-opacity=\".92\"/><stop offset=\".38\" stop-color=\"#0e2531\" stop-opacity=\".75\"/><stop offset=\".70\" stop-color=\"#102935\" stop-opacity=\".12\"/><stop offset=\"1\" stop-color=\"#102935\" stop-opacity=\"0\"/></linearGradient>")
                .append("<linearGradient id=\"bottom-shade\" x2=\"0\" y2=\"1\"><stop offset=\".45\" stop-color=\"#091923\" stop-opacity=\"0\"/><stop offset=\".78\" stop-color=\"#091923\" stop-opacity=\".21\"/><stop offset=\"1\" stop-color=\"#091923\" stop-opacity=\".91\"/></linearGradient>")
                .append("<linearGradient id=\"stone-face\" x2=\"1\" y2=\".5\"><stop stop-color=\"#b6bba6\"/><stop offset=\".5\" stop-color=\"#d9d3b5\"/><stop offset=\"1\" stop-color=\"#9caa9e\"/></linearGradient>")
                .append("<linearGradient id=\"stone-side\" x2=\"1\" y2=\"1\"><stop stop-color=\"#667b7b\"/><stop offset=\"1\" stop-color=\"#3b555e\"/></linearGradient>")
                .append("<linearGradient id=\"ridge\" x2=\"0\" y2=\"1\"><stop stop-color=\"#697f79\"/><stop offset=\"1\" stop-color=\"#203e4b\"/></linearGradient>")
                .append("<clipPath id=\"portal-opening\"><path d=\"M1293 804V472A117 184 0 0 1 1527 472V804Z\"/></clipPath>")
                .append("<clipPath id=\"stone-surface\"><path d=\"M1207 804V472A203 258 0 0 1 1613 472V804H1527V472A117 184 0 0 0 1293 472V804Z\"/></clipPath>")
                .append("</defs>");

        out.append("<path fill=\"url(#sky)\" d=\"M0 0H1920V1080H0Z\"/>")
                .append("<ellipse cx=\"1280\" cy=\"296\" rx=\"650\" ry=\"530\" fill=\"url(#sun)\"/>");
        distantMountains(out);
        middleMountains(out);
        foregroundRidge(out);
        monument(out);
        foregroundDetail(out);

        // These overlays belong to the artwork, so its intended contrast is
        // retained when it is viewed outside the website or resized by CSS.
        out.append("<path fill=\"url(#left-shade)\" d=\"M0 0H1920V1080H0Z\"/>")
                .append("<path fill=\"url(#bottom-shade)\" d=\"M0 0H1920V1080H0Z\"/>")
                .append("</svg>");
        return out.toString();
    }

    /** Broad, low-contrast silhouettes keep the sky and distant valley calm. */
    private static void distantMountains(StringBuilder out) {
        polygon(out, "#a6b4b2", "0,614 105,557 191,585 304,471 401,515 527,440 641,515 778,474 929,576 1048,489 1152,524 1263,472 1340,511 1479,424 1572,454 1685,363 1799,431 1920,390 1920,1080 0,1080");
        polygon(out, "#95a8a8", "1090,633 1220,551 1327,574 1479,424 1430,564 1592,554 1685,363 1747,507 1821,494 1920,390 1920,751");
        polygon(out, "#b2bcaf", "1220,551 1340,511 1479,424 1386,552 1327,574");
        polygon(out, "#bcc4b6", "1685,363 1618,514 1702,475 1747,507");
        polygon(out, "#aebcb5", "304,471 271,575 368,550 401,515");
        polygon(out, "#839ba0", "0,783 171,661 242,687 379,565 477,619 591,536 713,639 788,609 950,740 1110,623 1232,671 1328,606 1491,706 1633,591 1744,641 1856,535 1920,562 1920,1080 0,1080");
        polygon(out, "#9eaea8", "379,565 345,692 430,649 477,619");
        polygon(out, "#a6b3a7", "591,536 554,654 629,615 713,639");
        polygon(out, "#6d8b95", "950,740 1110,623 1083,730 1180,779 1328,606 1284,762 1491,706 1434,841");
        polygon(out, "#98a99f", "1856,535 1802,679 1920,620 1920,562");
        out.append("<path fill=\"url(#haze)\" d=\"M0 452H1920V871H0Z\"/>");
    }

    /** Larger angular planes describe a mountain valley, without repetitive noise. */
    private static void middleMountains(StringBuilder out) {
        polygon(out, "#607e8a", "0,824 110,712 211,761 356,594 478,670 587,465 661,503 742,684 804,631 909,810 990,750 1114,861 1190,797 1311,903 1409,821 1512,873 1644,720 1780,784 1920,697 1920,1080 0,1080");
        polygon(out, "#8ba099", "587,465 556,623 630,570 661,503");
        polygon(out, "#adc0ac", "587,465 579,528 605,509 627,553 630,570 661,503");
        polygon(out, "#496c7c", "587,465 478,670 513,833 552,733 556,623");
        polygon(out, "#78958f", "630,570 668,668 688,632 742,684 661,503");
        polygon(out, "#496b7c", "552,733 630,570 610,813 742,684 714,891 909,810 835,963 624,987");
        polygon(out, "#88a098", "356,594 326,755 396,698 478,670");
        polygon(out, "#527789", "356,594 211,761 280,872 326,755");
        polygon(out, "#406577", "396,698 326,755 350,956 513,833 478,670");
        polygon(out, "#75918b", "804,631 790,726 835,724 909,810");
        polygon(out, "#3e6579", "742,684 804,631 790,726 835,724 835,963 714,891");
        polygon(out, "#78928a", "1644,720 1561,877 1719,835 1780,784");
        polygon(out, "#476c7a", "1644,720 1611,850 1512,873 1557,1001 1719,835");
        polygon(out, "#95a296", "1920,697 1813,851 1920,811");
        polygon(out, "#345869", "0,943 146,830 274,899 412,861 578,974 775,943 973,1019 1160,935 1356,1011 1514,947 1701,977 1920,886 1920,1080 0,1080");
        // A narrow, distant watercourse carries the eye toward the monument.
        polygon(out, "#a8b4a4", "927,910 1040,882 998,915 1107,930 995,936 916,970 789,1001 734,1006 867,960 954,930");
        polygon(out, "#6f979a", "927,918 1007,899 977,917 1043,931 979,932 910,963 846,976 922,941 973,927");
    }

    /** The near ridge provides a broad, weighty footing for the architecture. */
    private static void foregroundRidge(StringBuilder out) {
        polygon(out, "url(#ridge)", "948,1080 1036,929 1158,845 1251,828 1346,776 1489,757 1571,788 1678,815 1793,868 1920,895 1920,1080");
        polygon(out, "#7e8e7b", "1158,845 1251,828 1346,776 1489,757 1434,815 1304,835 1217,875");
        polygon(out, "#a1a38a", "1346,776 1489,757 1434,789 1371,803 1304,835");
        polygon(out, "#607b75", "1489,757 1571,788 1678,815 1566,839 1434,815");
        polygon(out, "#426775", "1158,845 1217,875 1147,1011 1036,929");
        polygon(out, "#335967", "1217,875 1304,835 1272,975 1356,1025 1250,1080 1147,1011");
        polygon(out, "#6c8177", "1304,835 1434,815 1385,912 1272,975");
        polygon(out, "#466570", "1434,815 1566,839 1498,976 1385,912");
        polygon(out, "#254e60", "1272,975 1385,912 1498,976 1470,1080 1356,1025");
        polygon(out, "#7b8877", "1566,839 1678,815 1793,868 1732,892");
        polygon(out, "#365c6a", "1566,839 1732,892 1661,1027 1498,976");
        polygon(out, "#536e70", "1732,892 1793,868 1920,895 1870,985 1771,1007");
        polygon(out, "#284c5b", "1661,1027 1732,892 1771,1007 1920,1080");
    }

    /**
     * Draws an original stone portal as an obliquely projected arch. Individual
     * voussoirs, inner reveals, and modest bevels express its depth and scale.
     */
    private static void monument(StringBuilder out) {
        final double cx = 1410, cy = 472, rx = 203, ry = 258, ix = 117, iy = 184;
        final double dx = 89, dy = -31, foot = 804;

        // Rear silhouette and foundation are painted before the near surfaces.
        arch(out, cx + dx, cy + dy, rx, ry, ix, iy, foot + dy, "#6e827c");
        polygon(out, "#657b74", "1173,802 1262,771 1718,771 1630,802");
        polygon(out, "#b5b394", "1173,802 1630,802 1630,823 1173,823");
        polygon(out, "#536d70", "1630,802 1718,771 1718,792 1630,823");
        polygon(out, "#8a9987", "1194,791 1283,760 1697,760 1608,791");
        polygon(out, "#d0c5a2", "1194,791 1608,791 1608,804 1194,804");
        polygon(out, "#74897f", "1608,791 1697,760 1697,773 1608,804");

        final int stones = 13;
        for (int i = 0; i < stones; i++) {
            double a = Math.PI * i / stones, b = Math.PI * (i + 1) / stones;
            double ax = cx - rx * Math.cos(a), ay = cy - ry * Math.sin(a);
            double bx = cx - rx * Math.cos(b), by = cy - ry * Math.sin(b);
            quad(out, i < 6 ? "#b7baa0" : "#7e9185", ax, ay, bx, by, bx + dx, by + dy, ax + dx, ay + dy);
            line(out, "#5c746f", .42, 1.3, ax, ay, ax + dx, ay + dy);
        }
        // Only the front opening can reveal the inner thickness. The clip
        // prevents hidden rear edges from leaking across the outside silhouette.
        out.append("<g clip-path=\"url(#portal-opening)\">");
        for (int i = 0; i < stones; i++) {
            double a = Math.PI * i / stones, b = Math.PI * (i + 1) / stones;
            double iax = cx - ix * Math.cos(a), iay = cy - iy * Math.sin(a);
            double ibx = cx - ix * Math.cos(b), iby = cy - iy * Math.sin(b);
            quad(out, "#526e73", iax, iay, ibx, iby, ibx + dx, iby + dy, iax + dx, iay + dy);
        }
        // The long inner reveal of the left jamb is visible through the opening.
        quad(out, "url(#stone-side)", cx - ix, cy, cx - ix + dx, cy + dy, cx - ix + dx, foot + dy, cx - ix, foot);
        out.append("</g>");
        quad(out, "#506d70", cx + rx, cy, cx + rx + dx, cy + dy, cx + rx + dx, foot + dy, cx + rx, foot);

        arch(out, cx, cy, rx, ry, ix, iy, foot, "url(#stone-face)");
        for (int i = 0; i < stones; i++) {
            double a = Math.PI * i / stones, b = Math.PI * (i + 1) / stones;
            double ax = cx - rx * Math.cos(a), ay = cy - ry * Math.sin(a);
            double bx = cx - rx * Math.cos(b), by = cy - ry * Math.sin(b);
            double iax = cx - ix * Math.cos(a), iay = cy - iy * Math.sin(a);
            double ibx = cx - ix * Math.cos(b), iby = cy - iy * Math.sin(b);
            String[] tones = {"#b5b8a0", "#c2c2a6", "#d5ccb0", "#d8cdb0", "#e0d2b0", "#d8cbaa", "#ddcda8", "#d5c8a9", "#c8c3a6", "#c4c1a4", "#b6bba1", "#b9bba3", "#a7b4a0"};
            quad(out, tones[i], ax, ay, bx, by, ibx, iby, iax, iay);
            line(out, "#727f74", .72, 1.3, ax, ay, iax, iay);
            // A shallow bevel catches the same warm light as the brand's metal.
            line(out, "#f1dfb6", .64, 1.8, ax, ay, bx, by);
            if (i % 3 == 1) {
                double mx = (ax + bx) / 2, my = (ay + by) / 2;
                triangle(out, "#b1b6a0", .24, mx, my, ibx, iby, iax, iay);
            }
        }

        // Separate stone courses avoid the appearance of a smooth molded prop.
        for (int course = 0; course < 5; course++) {
            double y = cy + (foot - cy) * course / 5;
            double next = cy + (foot - cy) * (course + 1) / 5;
            String left = course % 2 == 0 ? "#b7bca5" : "#aeb69f";
            String right = course % 2 == 0 ? "#c1c1a8" : "#b8bda5";
            quad(out, left, cx - rx, y, cx - ix, y, cx - ix, next, cx - rx, next);
            quad(out, right, cx + ix, y, cx + rx, y, cx + rx, next, cx + ix, next);
            line(out, "#737f74", .56, 1.2, cx - rx, next, cx - ix, next);
            line(out, "#737f74", .50, 1.2, cx + ix, next, cx + rx, next);
        }
        // Inset panels are deliberately quiet: no symbols, emissive motifs,
        // lettering, or copied architecture from an existing game.
        polygon(out, "#a0ae9c", "1225,499 1275,499 1275,776 1225,776");
        polygon(out, "#b8baa0", "1537,499 1589,499 1589,776 1537,776");
        line(out, "#e7d3a6", .65, 2, 1225, 499, 1225, 776);
        line(out, "#e6d0a0", .60, 2, 1537, 499, 1537, 776);
        line(out, "#61796f", .56, 1.5, 1275, 499, 1275, 776);
        line(out, "#758576", .48, 1.5, 1589, 499, 1589, 776);
        out.append("<path d=\"M1207 548l23 14 8 28 21 9M1613 631l-20 13-12 35-30 17M1358 225l11 21-9 17\" fill=\"none\" stroke=\"#65776c\" stroke-width=\"2\" opacity=\".5\"/>");
        // The thin cornices connect the masonry with the opening's spring line.
        polygon(out, "#d6c9a6", "1198,466 1301,466 1301,480 1198,480");
        polygon(out, "#b7bba0", "1519,466 1621,466 1621,480 1519,480");
        polygon(out, "#6d837b", "1621,466 1710,435 1710,449 1621,480");
        line(out, "#f0dbad", .8, 1.5, 1198, 466, 1301, 466);
        line(out, "#ebd5a8", .65, 1.5, 1519, 466, 1621, 466);
        stoneGrain(out);
    }

    /** Small mineral inclusions add restrained surface texture to the masonry. */
    private static void stoneGrain(StringBuilder out) {
        out.append("<g clip-path=\"url(#stone-surface)\" fill=\"#445d58\" opacity=\".105\">");
        long seed = 820193;
        for (int i = 0; i < 420; i++) {
            seed = (seed * 1664525 + 1013904223L) & 0xffffffffL;
            double x = 1206 + seed % 408;
            seed = (seed * 1664525 + 1013904223L) & 0xffffffffL;
            double y = 214 + seed % 590;
            double length = 1 + seed % 4;
            out.append("<path d=\"M").append(n(x)).append(' ').append(n(y))
                    .append('h').append(n(length)).append("l-1 1h-").append(n(length)).append("Z\"/>");
        }
        out.append("</g>");
    }

    /** Small authored and deterministic details anchor the clean large shapes. */
    private static void foregroundDetail(StringBuilder out) {
        polygon(out, "#778b78", "1164,823 1189,809 1225,814 1241,834 1218,846 1174,839");
        polygon(out, "#a1a58a", "1189,809 1225,814 1210,825 1164,823");
        polygon(out, "#526e69", "1632,818 1665,799 1700,811 1713,834 1671,850");
        polygon(out, "#93a087", "1665,799 1700,811 1664,825 1632,818");
        polygon(out, "#4c6868", "1124,880 1147,865 1176,876 1160,898 1133,900");
        polygon(out, "#758a77", "1147,865 1176,876 1152,882 1124,880");
        polygon(out, "#788774", "1740,865 1768,851 1795,857 1809,878 1772,885");
        // Mineral seams follow rock planes instead of covering the scene in
        // random texture. Values are fixed so exported artwork is reproducible.
        out.append("<g fill=\"none\" stroke=\"#9aab96\" stroke-width=\"1.3\" opacity=\".27\">")
                .append("<path d=\"M1292 849l-39 34-12 51M1396 848l-17 33-42 30M1505 849l-35 48 4 27M1581 886l-22 27-6 53M1765 909l33 11 24 43\"/>")
                .append("</g>");
        long seed = 73179;
        for (int i = 0; i < 44; i++) {
            seed = (seed * 1664525 + 1013904223L) & 0xffffffffL;
            double x = 1130 + (seed % 725);
            seed = (seed * 1664525 + 1013904223L) & 0xffffffffL;
            double y = 842 + (seed % 102);
            if (x < 1250 && y < 876) continue;
            double size = 1.5 + (seed % 5) * .6;
            triangle(out, "#c4bd94", .25, x, y, x + size, y - size * .6, x + size * 1.8, y + .7);
        }
        // Closest silhouette gives the valley a natural frame and shaded base.
        polygon(out, "#15343f", "0,925 123,864 221,906 290,902 420,953 552,958 665,1048 783,1080 0,1080");
        polygon(out, "#2a4850", "0,925 123,864 105,934 221,906 180,979 290,902 420,953 289,1008 187,993 0,1030");
        polygon(out, "#1f3e48", "123,864 155,870 221,906 105,934");
    }

    private static void arch(StringBuilder out, double cx, double cy, double rx, double ry,
                             double ix, double iy, double foot, String fill) {
        out.append("<path fill=\"").append(fill).append("\" d=\"M").append(n(cx - rx)).append(' ').append(n(foot))
                .append('V').append(n(cy)).append('A').append(n(rx)).append(' ').append(n(ry)).append(" 0 0 1 ")
                .append(n(cx + rx)).append(' ').append(n(cy)).append('V').append(n(foot)).append('H').append(n(cx + ix))
                .append('V').append(n(cy)).append('A').append(n(ix)).append(' ').append(n(iy)).append(" 0 0 0 ")
                .append(n(cx - ix)).append(' ').append(n(cy)).append('V').append(n(foot)).append("Z\"/>");
    }

    private static void polygon(StringBuilder out, String fill, String points) {
        out.append("<polygon fill=\"").append(fill).append("\" points=\"").append(points).append("\"/>");
    }

    private static void quad(StringBuilder out, String fill, double ax, double ay, double bx, double by,
                             double cx, double cy, double dx, double dy) {
        polygon(out, fill, n(ax) + ',' + n(ay) + ' ' + n(bx) + ',' + n(by) + ' '
                + n(cx) + ',' + n(cy) + ' ' + n(dx) + ',' + n(dy));
    }

    private static void triangle(StringBuilder out, String fill, double opacity,
                                 double ax, double ay, double bx, double by, double cx, double cy) {
        out.append("<polygon fill=\"").append(fill).append("\" opacity=\"").append(n(opacity)).append("\" points=\"")
                .append(n(ax)).append(',').append(n(ay)).append(' ').append(n(bx)).append(',').append(n(by))
                .append(' ').append(n(cx)).append(',').append(n(cy)).append("\"/>");
    }

    private static void line(StringBuilder out, String color, double opacity, double width,
                             double x1, double y1, double x2, double y2) {
        out.append("<path fill=\"none\" stroke=\"").append(color).append("\" opacity=\"").append(n(opacity))
                .append("\" stroke-width=\"").append(n(width)).append("\" d=\"M").append(n(x1)).append(' ')
                .append(n(y1)).append('L').append(n(x2)).append(' ').append(n(y2)).append("\"/>");
    }

    /** Locale-neutral precision keeps SVG valid under every export machine's locale. */
    private static String n(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
