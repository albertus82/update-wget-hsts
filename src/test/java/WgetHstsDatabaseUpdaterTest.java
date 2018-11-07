import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.chromium.net.http.ChromiumHstsPreloadedEntry;
import org.gnu.wget.WgetHstsKnownHost;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import lombok.extern.java.Log;

@Log
public class WgetHstsDatabaseUpdaterTest {

	public static WgetHstsDatabaseUpdater o;

	@BeforeClass
	public static void beforeClass() {
		o = new WgetHstsDatabaseUpdater();
	}

	@Test
	public void testParseChromiumHstsPreloadedList() throws IOException {
		final Path tempFile = createTempFileFromResource("/transport_security_state_static.json");
		final Map<String, ChromiumHstsPreloadedEntry> x = o.parseChromiumHstsPreloadedList(tempFile.toFile());
		Files.delete(tempFile);
		Assert.assertEquals(8, x.size());
		Assert.assertTrue(x.containsKey("pre.https.sub.1"));
		Assert.assertTrue(x.containsKey("pre.https.sub.2"));
		Assert.assertTrue(x.containsKey("pre.https.sub.3"));
		Assert.assertTrue(x.containsKey("pre.https.nosub.1"));
		Assert.assertTrue(x.containsKey("pre.https.nosub.2"));
		Assert.assertTrue(x.containsKey("pre.nohttps.nosub.1"));
		Assert.assertTrue(x.containsKey("pre.tobe.updated.1"));
		Assert.assertTrue(x.containsKey("pre.tobe.updated.2"));

	}

	@Test
	public void testParseWgetHstsKnownHostsDatabase() throws IOException {
		final Path tempFile = createTempFileFromResource("/wget-hsts");
		final Map<String, WgetHstsKnownHost> x = o.parseWgetHstsKnownHostsDatabase(tempFile.toFile());
		Files.delete(tempFile);
		Assert.assertEquals(12, x.size());
		Assert.assertTrue(x.containsKey("pre.https.sub.1"));
		Assert.assertTrue(x.containsKey("pre.https.sub.2"));
		Assert.assertTrue(x.containsKey("pre.https.nosub.1"));
		Assert.assertTrue(x.containsKey("pre.https.nosub.2"));
		Assert.assertTrue(x.containsKey("nopre.https.sub.1"));
		Assert.assertTrue(x.containsKey("nopre.https.sub.2"));
		Assert.assertTrue(x.containsKey("nopre.https.nosub.1"));
		Assert.assertTrue(x.containsKey("nopre.https.nosub.2"));
		Assert.assertTrue(x.containsKey("pre.tobe.updated.1"));
		Assert.assertTrue(x.containsKey("pre.tobe.updated.2"));
		Assert.assertTrue(x.containsKey("tobe.removed.1"));
		Assert.assertTrue(x.containsKey("tobe.removed.2"));
	}

	@Test
	public void testRetrieveWgetHstsPreloadedHosts() throws IOException {
		final Path tempFile = createTempFileFromResource("/wget-hsts");
		final Map<String, WgetHstsKnownHost> map1 = o.parseWgetHstsKnownHostsDatabase(tempFile.toFile());
		Files.delete(tempFile);
		final Map<String, WgetHstsKnownHost> x = o.retrieveWgetHstsPreloadedHosts(map1);
		Assert.assertEquals(8, x.size());
		Assert.assertTrue(x.containsKey("pre.https.sub.1"));
		Assert.assertTrue(x.containsKey("pre.https.sub.2"));
		Assert.assertTrue(x.containsKey("pre.https.nosub.1"));
		Assert.assertTrue(x.containsKey("pre.https.nosub.2"));
		Assert.assertTrue(x.containsKey("pre.tobe.updated.1"));
		Assert.assertTrue(x.containsKey("pre.tobe.updated.2"));
		Assert.assertTrue(x.containsKey("tobe.removed.1"));
		Assert.assertTrue(x.containsKey("tobe.removed.2"));
	}

	@Test
	public void testComputeHostsToRemove() throws IOException {
		final Path tempFile1 = createTempFileFromResource("/transport_security_state_static.json");
		final Map<String, ChromiumHstsPreloadedEntry> map1 = o.parseChromiumHstsPreloadedList(tempFile1.toFile());
		Files.delete(tempFile1);

		final Path tempFile2 = createTempFileFromResource("/wget-hsts");
		final Map<String, WgetHstsKnownHost> map = o.parseWgetHstsKnownHostsDatabase(tempFile2.toFile());
		Files.delete(tempFile2);
		final Map<String, WgetHstsKnownHost> map2 = o.retrieveWgetHstsPreloadedHosts(map);

		final Set<String> x = o.computeHostsToRemove(map1.keySet(), map2.keySet());
		Assert.assertEquals(2, x.size());
		Assert.assertTrue(x.contains("tobe.removed.1"));
		Assert.assertTrue(x.contains("tobe.removed.2"));
	}

	@Test
	public void testComputeHostsToUpdate() throws IOException {
		final Path tempFile1 = createTempFileFromResource("/transport_security_state_static.json");
		final Map<String, ChromiumHstsPreloadedEntry> map1 = o.parseChromiumHstsPreloadedList(tempFile1.toFile());
		Files.delete(tempFile1);

		final Path tempFile2 = createTempFileFromResource("/wget-hsts");
		final Map<String, WgetHstsKnownHost> map = o.parseWgetHstsKnownHostsDatabase(tempFile2.toFile());
		Files.delete(tempFile2);
		final Map<String, WgetHstsKnownHost> map2 = o.retrieveWgetHstsPreloadedHosts(map);

		final Set<String> x = o.computeHostsToUpdate(map1, map2);
		Assert.assertEquals(2, x.size());
		Assert.assertTrue(x.contains("pre.tobe.updated.1"));
		Assert.assertTrue(x.contains("pre.tobe.updated.2"));
	}

	@Test
	public void testCreateTempWgetHstsKnownHostsDatabase() throws IOException {
		final Path x = o.createTempWgetHstsKnownHostsDatabase();
		x.toFile().deleteOnExit();
		Assert.assertTrue(Files.exists(x));
		Assert.assertTrue(Files.size(x) > 0);
		Files.delete(x);
	}

	@Test
	public void testRetrieveSourceFileLocal() throws IOException {
		final Path tempFile = createTempFileFromResource("/transport_security_state_static.json");
		final WgetHstsDatabaseUpdater.SourceFile x = o.retrieveSourceFile(tempFile.toString());
		Files.delete(tempFile);
		Assert.assertFalse(x.isTemp());
	}

	private static Path createTempFileFromResource(final String resourceName) throws IOException {
		final Path tempFile = Files.createTempFile(null, null);
		log.log(Level.INFO, "{0}", tempFile);
		tempFile.toFile().deleteOnExit();
		try (final InputStream in = WgetHstsDatabaseUpdaterTest.class.getResourceAsStream(resourceName)) {
			Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return tempFile;
	}

}
