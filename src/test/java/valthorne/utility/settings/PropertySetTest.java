package valthorne.utility.settings;

import org.junit.jupiter.api.Test;
import valthorne.io.buffer.DynamicByteBuffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertySetTest {

    private enum Quality {LOW, HIGH}

    @Test
    void managesPropertiesAndProducesImmutableSnapshots() {
        PropertySet properties = new PropertySet(Map.of("lives", 3, "title", "Valthorne"));

        assertEquals(2, properties.size());
        assertTrue(properties.contains("lives"));
        assertEquals(3, properties.<Integer>get("lives").asInt());
        assertEquals("Valthorne", properties.getRequired("title", String.class).asString());
        assertTrue(properties.<String>find("title").isPresent());
        assertEquals("fallback", properties.getOrDefault("missing", "fallback").asString());

        Map<String, PropertyValue<?>> snapshot = properties.asMap();
        properties.set("lives", 4);
        properties.remove("title");

        assertEquals(3, snapshot.get("lives").asInt());
        assertTrue(snapshot.containsKey("title"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("new", new PropertyValue<>(1)));
        assertThrows(IllegalArgumentException.class, () -> properties.getRequired("missing"));
        assertThrows(ClassCastException.class, () -> properties.get("lives", String.class));

        properties.clear();
        assertTrue(properties.isEmpty());
    }

    @Test
    void convertsPrimitiveAndNumericValues() {
        PropertyValue<String> integral = new PropertyValue<>(" 42 ");

        assertEquals((byte) 42, integral.asByte());
        assertEquals((short) 42, integral.asShort());
        assertEquals(42, integral.asInt());
        assertEquals(42L, integral.asLong());
        assertEquals(42.0f, integral.asFloat());
        assertEquals(42.0d, integral.asDouble());
        assertEquals(BigInteger.valueOf(42), integral.asBigInteger());
        assertEquals(new BigDecimal("42"), integral.asBigDecimal());
        assertEquals('x', new PropertyValue<>('x').asChar());

        assertTrue(new PropertyValue<>("YES").asBoolean());
        assertTrue(new PropertyValue<>(-1).asBoolean());
        assertFalse(new PropertyValue<>(" off ").asBoolean());
        assertThrows(IllegalArgumentException.class, () -> new PropertyValue<>("perhaps").asBoolean());
        assertThrows(IllegalArgumentException.class, () -> new PropertyValue<>("xy").asChar());
        assertThrows(ArithmeticException.class, () -> new PropertyValue<>(new BigDecimal("1.5")).asBigInteger());
    }

    @Test
    void convertsStructuredValues() throws Exception {
        UUID uuid = UUID.randomUUID();
        Instant instant = Instant.parse("2026-09-21T12:30:00Z");

        assertEquals(Quality.HIGH, new PropertyValue<>("high").asEnum(Quality.class));
        assertEquals(uuid, new PropertyValue<>(uuid.toString()).asUuid());
        assertEquals(Path.of("assets", "game.dat"), new PropertyValue<>("assets/game.dat").asPath());
        assertEquals(URI.create("https://example.com/game"), new PropertyValue<>("https://example.com/game").asUri());
        assertEquals("https://example.com/game", new PropertyValue<>("https://example.com/game").asUrl().toString());
        assertEquals(Duration.ofMinutes(5), new PropertyValue<>("PT5M").asDuration());
        assertEquals(Period.ofDays(2), new PropertyValue<>("P2D").asPeriod());
        assertEquals(instant, new PropertyValue<>(instant.toString()).asInstant());
        assertEquals(LocalDate.of(2026, 9, 21), new PropertyValue<>("2026-09-21").asLocalDate());
        assertEquals(LocalTime.of(12, 30), new PropertyValue<>("12:30:00").asLocalTime());
        assertEquals(LocalDateTime.parse("2026-09-21T12:30:00"), new PropertyValue<>("2026-09-21T12:30:00").asLocalDateTime());
        assertEquals(OffsetDateTime.parse("2026-09-21T12:30:00-05:00"), new PropertyValue<>("2026-09-21T12:30:00-05:00").asOffsetDateTime());
        assertEquals(ZonedDateTime.parse("2026-09-21T12:30:00-05:00[America/Chicago]"), new PropertyValue<>("2026-09-21T12:30:00-05:00[America/Chicago]").asZonedDateTime());
        assertEquals(Locale.US, new PropertyValue<>("en_US").asLocale());
        assertEquals(Currency.getInstance("USD"), new PropertyValue<>("usd").asCurrency());
        assertEquals(StandardCharsets.UTF_8, new PropertyValue<>("UTF-8").asCharset());
    }

    @Test
    void exposesAndChecksTheOriginalValue() {
        Object value = new Object();
        PropertyValue<Object> property = new PropertyValue<>(value);

        assertSame(value, property.asObject());
        assertSame(value, property.as(Object.class));
        assertTrue(property.is(Object.class));
        assertFalse(property.is(String.class));
        assertInstanceOf(Object.class, property.value());
        assertThrows(ClassCastException.class, () -> property.as(String.class));
        assertThrows(NullPointerException.class, () -> new PropertyValue<>(null));
        assertThrows(NullPointerException.class, () -> new PropertySet().set("null", null));
    }

    @Test
    void persistsTypedValuesThroughEveryStorageOption() throws Exception {
        PropertySet original = new PropertySet(Map.of("name", "Valthorne", "attempts", 7, "enabled", true, "timeout", Duration.ofSeconds(30), "identifier", UUID.fromString("7d78586d-9514-4a7f-9fa8-851f67c63f48")));

        byte[] encoded = original.toBytes();
        PropertySet fromBytes = PropertySet.fromBytes(encoded);
        assertEquals("Valthorne", fromBytes.getRequired("name", String.class).value());
        assertEquals(7, fromBytes.getRequired("attempts", Integer.class).value());
        assertEquals(Duration.ofSeconds(30), fromBytes.getRequired("timeout", Duration.class).value());

        DynamicByteBuffer buffer = new DynamicByteBuffer();
        original.write(buffer);
        PropertySet fromBuffer = PropertySet.read(new DynamicByteBuffer(buffer.toTrimmedWriteArray()));
        assertEquals(true, fromBuffer.getRequired("enabled", Boolean.class).value());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        original.write(output);
        PropertySet fromStream = PropertySet.read(new ByteArrayInputStream(output.toByteArray()));
        assertEquals(original.asMap(), fromStream.asMap());
    }

    @Test
    void rejectsInvalidAndUnsupportedPersistenceData() {
        PropertySet unsupported = new PropertySet();
        unsupported.set("object", new Object());

        assertThrows(IllegalArgumentException.class, unsupported::toBytes);
        assertThrows(java.io.IOException.class, () -> PropertySet.fromBytes(new byte[]{1, 2, 3}));
    }

    @Test
    void persistsReadableTypedTextAndEscapedCharacters() throws Exception {
        PropertySet original = new PropertySet();
        original.set("title", "Valthorne");
        original.set("window:width", 1920);
        original.set("#multiline=value", "first\nsecond\\third");
        original.set("timeout", Duration.ofSeconds(30));

        String text = original.toText();
        assertTrue(text.contains("title:string=Valthorne"));
        assertTrue(text.contains("window\\:width:integer=1920"));
        assertEquals(original.asMap(), PropertySet.fromText(text).asMap());

        Path file = java.nio.file.Files.createTempFile("valthorne-properties-", ".txt");
        try {
            original.writeText(file);
            assertEquals(original.asMap(), PropertySet.readText(file).asMap());
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }

        String edited = "# user settings\nfullscreen:boolean=true\nquality:string=high\n";
        PropertySet parsed = PropertySet.fromText(edited);
        assertTrue(parsed.getRequired("fullscreen", Boolean.class).value());
        assertEquals("high", parsed.getRequired("quality", String.class).value());
        assertThrows(java.io.IOException.class,
                () -> PropertySet.fromText("broken:unknown=value\n"));
    }

    @Test
    void supportsAnyNumberOfHeadersAndInlineComments() throws Exception {
        PropertySet properties = new PropertySet(Map.of(
                "title", "Valthorne # literal",
                "width", 1920,
                "fullscreen", true
        ));
        PropertyTextOptions options = new PropertyTextOptions(
                java.util.List.of("Display settings", "Change these before launching"),
                Map.of("width", "Horizontal resolution", "fullscreen", "Use the whole display")
        );

        String text = properties.toText(options);
        assertTrue(text.startsWith("# Display settings\n# Change these before launching\n"));
        assertTrue(text.contains("width:integer=1920 # Horizontal resolution"));
        assertTrue(text.contains("title:string=Valthorne \\# literal"));
        assertEquals(properties.asMap(), PropertySet.fromText(text).asMap());

        String manual = "# First header\n! Second header\n\n"
                + "name:string=Example # visible label\n"
                + "enabled:boolean=true ! feature toggle\n";
        PropertySet parsed = PropertySet.fromText(manual);
        assertEquals("Example", parsed.getRequired("name", String.class).value());
        assertTrue(parsed.getRequired("enabled", Boolean.class).value());
        assertThrows(IllegalArgumentException.class,
                () -> PropertyTextOptions.withHeaders("invalid\nheader"));
    }
}
