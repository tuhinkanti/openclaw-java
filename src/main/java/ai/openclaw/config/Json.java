package ai.openclaw.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared ObjectMapper factory. Every ObjectMapper in the application should
 * come from here so that module registration and serialization settings are
 * consistent.
 */
public final class Json {
    private static final ObjectMapper MAPPER = createMapper();

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Creates a new, independently configured ObjectMapper. */
    public static ObjectMapper createMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
