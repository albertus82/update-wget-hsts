package com.github.albertus82.wget;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BuildInfoTest {

	private static final String[] expectedPropertyNames = new String[] { "project.artifactId", "project.version", "version.timestamp" };

	@Test
	void testBuildInfo() {
		Assertions.assertNotEquals(0, BuildInfo.INSTANCE.properties.size());
		Assertions.assertNull(BuildInfo.getProperty(UUID.randomUUID().toString()));
		Assertions.assertThrows(NullPointerException.class, () -> BuildInfo.getProperty(null));
	}

	@Test
	void testExpectedProperties() {
		for (final String name : expectedPropertyNames) {
			final String value = BuildInfo.getProperty(name);
			Assertions.assertNotNull(value);
			Assertions.assertNotEquals(0, value.length());
			Assertions.assertFalse(value.contains("$"));
		}
	}

}
