import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;

import java.io.File;
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

	private static final String TRANSPORT_SECURITY_STATE_STATIC_JSON = "transport_security_state_static.json";
	private static final String WGET_HSTS = "wget-hsts";

	private static WgetHstsDatabaseUpdater o;

	@BeforeClass
	public static void beforeClass() {
		o = new WgetHstsDatabaseUpdater();
	}

	@Test
	public void testParseChromiumHstsPreloadedList() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
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
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);
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
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);
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
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Map<String, ChromiumHstsPreloadedEntry> map1 = o.parseChromiumHstsPreloadedList(tempFile1.toFile());
		Files.delete(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
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
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Map<String, ChromiumHstsPreloadedEntry> map1 = o.parseChromiumHstsPreloadedList(tempFile1.toFile());
		Files.delete(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
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
		log.log(Level.INFO, "{0}", x);
		x.toFile().deleteOnExit();
		Assert.assertTrue(Files.exists(x));
		Assert.assertTrue(Files.isRegularFile(x));
		Assert.assertThat(Files.size(x), greaterThan(0L));
		Files.delete(x);
	}

	@Test
	public void testRetrieveSourceFileLocal() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final WgetHstsDatabaseUpdater.SourceFile x = o.retrieveSourceFile(tempFile.toString());
		Files.delete(tempFile);
		Assert.assertFalse(x.isTemp());
	}

	@Test
	public void testBackupWgetHstsKnownHostsDatabase() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);

		final File x = o.backupWgetHstsKnownHostsDatabase(tempFile.toFile());
		log.log(Level.INFO, "{0}", x);
		x.deleteOnExit();
		Assert.assertThat(x.getName(), endsWith(".bak"));
		Assert.assertTrue(x.exists());
		Assert.assertTrue(x.isFile());
		Assert.assertThat(x.length(), greaterThan(0L));

		final File y = o.backupWgetHstsKnownHostsDatabase(tempFile.toFile());
		log.log(Level.INFO, "{0}", y);
		y.deleteOnExit();
		Assert.assertThat(y.getName(), endsWith(".bak.1"));
		Assert.assertTrue(y.exists());
		Assert.assertTrue(y.isFile());
		Assert.assertThat(y.length(), greaterThan(0L));

		final File z = o.backupWgetHstsKnownHostsDatabase(tempFile.toFile());
		log.log(Level.INFO, "{0}", z);
		z.deleteOnExit();
		Assert.assertThat(z.getName(), endsWith(".bak.2"));
		Assert.assertTrue(z.exists());
		Assert.assertTrue(z.isFile());
		Assert.assertThat(z.length(), greaterThan(0L));

		x.delete();
		y.delete();
		z.delete();
	}

	@Test
	public void testCreateJsonTempFile() throws IOException {
		final File x;
		try (final InputStream in = getClass().getResourceAsStream('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON)) {
			x = o.createJsonTempFile(in);
			log.log(Level.INFO, "{0}", x);
			x.deleteOnExit();
		}
		final Path y = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		Assert.assertThat(x.getName(), endsWith(".json"));
		Assert.assertEquals(Files.size(y), x.length());
		x.delete();
		Files.delete(y);
	}

	private Path createTempFileFromResource(final String resourceName) throws IOException {
		final Path tempFile = Files.createTempFile(null, null);
		log.log(Level.INFO, "{0}", tempFile);
		tempFile.toFile().deleteOnExit();
		try (final InputStream in = getClass().getResourceAsStream(resourceName)) {
			Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return tempFile;
	}

}
