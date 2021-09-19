package com.github.albertus82.wget.picocli;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VersionProviderTest {

	@Test
	void testVersionProvider() {
		final VersionProvider vp = new VersionProvider();
		Assertions.assertNotEquals(0, vp.getVersion().length);
	}

}
