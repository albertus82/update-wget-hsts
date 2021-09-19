package com.github.albertus82.wget.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BuildInfoTest {

	@Test
	void testBuildInfo() {
		Assertions.assertNotEquals(0, BuildInfo.INSTANCE.properties.size());
	}

}
