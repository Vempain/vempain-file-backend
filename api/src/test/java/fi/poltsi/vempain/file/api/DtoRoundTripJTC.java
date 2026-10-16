package fi.poltsi.vempain.file.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reflection-driven JSON contract of every request/response DTO of the api module: each one serializes with the Jackson 3 mapper to snake_case keys only, and
 * the ones that are deserializable read back equal to the instance that was written.
 */
class DtoRoundTripJTC {

    private static final String BASE_PACKAGE = "fi.poltsi.vempain.file.api";
    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z0-9]+(_[a-z0-9]+)*$");

    private final JsonMapper mapper = JsonMapper.builder()
        .build();

    static List<Class<?>> dtoClasses() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        List<Class<?>> classes = new ArrayList<>();

        for (String subPackage : List.of("", ".request", ".response", ".response.files")) {
            for (var definition : scanner.findCandidateComponents(BASE_PACKAGE + subPackage)) {
                try {
                    Class<?> clazz = Class.forName(definition.getBeanClassName());

                    if (clazz.isEnum()
                        || clazz.isInterface()
                        || Modifier.isAbstract(clazz.getModifiers())
                        || clazz.isAnnotation()
                        || clazz.isMemberClass()
                        || isTestClass(clazz)
                        || classes.contains(clazz)) {
                        continue;
                    }

                    classes.add(clazz);
                } catch (ClassNotFoundException e) {
                    fail("Could not load " + definition.getBeanClassName(), e);
                }
            }
        }

        classes.sort((a, b) -> a.getName()
            .compareTo(b.getName()));
        return classes;
    }

    private static boolean isTestClass(Class<?> clazz) {
        String name = clazz.getSimpleName();
        return name.endsWith("JTC")
               || name.endsWith("UTC")
               || name.endsWith("CTC");
    }

    @Test
    void theScanFindsTheKnownDtos() {
        var names = dtoClasses().stream()
            .map(Class::getSimpleName)
            .toList();
        assertTrue(names.contains("GeoCoordinate"), names.toString());
        assertTrue(names.contains("PublishAclRequest"), names.toString());
        assertTrue(names.contains("MetadataSummaryResponse"), names.toString());
        assertTrue(names.contains("MusicFileResponse"), names.toString());
        assertTrue(names.contains("FileGroupRequest"), names.toString());
        assertTrue(names.size() >= 40, "Expected every DTO of the api module, found " + names.size());
    }

    @TestFactory
    Stream<DynamicTest> everyDtoWritesSnakeCaseKeysAndReadsBack() {
        return dtoClasses().stream()
            .map(clazz -> DynamicTest.dynamicTest(clazz.getSimpleName(), () -> roundTrip(clazz)));
    }

    private void roundTrip(Class<?> clazz) throws Exception {
        Object instance = instantiate(clazz);
        String json = mapper.writeValueAsString(instance);
        JsonNode tree = mapper.readTree(json);
        assertTrue(tree.isObject(), clazz.getSimpleName() + " should serialize as an object: " + json);
        assertSnakeCaseKeys(clazz.getSimpleName(), tree);

        if (hasNoArgConstructor(clazz)) {
            Object readBack = mapper.readValue(json, clazz);
            assertEquals(instance, readBack, clazz.getSimpleName() + " did not survive the JSON round trip: " + json);
            assertEquals(instance.hashCode(), readBack.hashCode());
            assertFalse(instance.toString()
                .isBlank());
        }
    }

    private static void assertSnakeCaseKeys(String name, JsonNode node) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                assertTrue(SNAKE_CASE.matcher(property.getKey())
                    .matches(), name + " writes a non snake_case key: " + property.getKey());
                assertSnakeCaseKeys(name, property.getValue());
            }
        } else if (node.isArray()) {
            node.forEach(child -> assertSnakeCaseKeys(name, child));
        }
    }

    private static boolean hasNoArgConstructor(Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0
                && Modifier.isPublic(constructor.getModifiers())) {
                return true;
            }
        }

        return false;
    }

    private static Object instantiate(Class<?> clazz) throws Exception {
        if (hasNoArgConstructor(clazz)) {
            return clazz.getConstructor()
                .newInstance();
        }

        // Lombok builders: the public builder() returns a public (abstract for @SuperBuilder) builder type; the concrete impl may be private
        Method builderMethod = clazz.getMethod("builder");
        Object builder = builderMethod.invoke(null);
        return builderMethod.getReturnType()
            .getMethod("build")
            .invoke(builder);
    }
}
