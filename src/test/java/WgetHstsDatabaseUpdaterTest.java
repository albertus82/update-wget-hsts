import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
		Assert.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("pre.https.sub.1");
		l.add("pre.https.sub.2");
		l.add("pre.https.sub.9");
		l.add("pre.https.nosub.1");
		l.add("pre.https.nosub.2");
		l.add("pre.https.nosub.9");
		l.add("pre.nohttps.nosub.1");
		l.add("pre.nohttps.nosub.2");
		l.add("pre.tobe.updated.1");
		l.add("pre.tobe.updated.2");
		Assert.assertEquals(l.size(), x.size());
		x.entrySet().forEach(e -> {
			Assert.assertTrue(l.contains(e.getKey()));
			Assert.assertNotNull(e.getValue());
		});
	}

	@Test
	public void testParseWgetHstsKnownHostsDatabase() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsKnownHost> x = o.parseWgetHstsKnownHostsDatabase(tempFile.toFile());
		Files.delete(tempFile);
		Assert.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("pre.https.sub.1");
		l.add("pre.https.sub.2");
		l.add("pre.https.nosub.1");
		l.add("pre.https.nosub.2");
		l.add("nopre.https.sub.1");
		l.add("nopre.https.sub.2");
		l.add("nopre.https.nosub.1");
		l.add("nopre.https.nosub.2");
		l.add("pre.tobe.updated.1");
		l.add("pre.tobe.updated.2");
		l.add("tobe.removed.1");
		l.add("tobe.removed.2");
		Assert.assertEquals(l.size(), x.size());
		x.entrySet().forEach(e -> {
			Assert.assertTrue(l.contains(e.getKey()));
			Assert.assertNotNull(e.getValue());
		});
	}

	@Test
	public void testRetrieveWgetHstsPreloadedHosts() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsKnownHost> map1 = o.parseWgetHstsKnownHostsDatabase(tempFile.toFile());
		Files.delete(tempFile);
		final Map<String, WgetHstsKnownHost> x = o.retrieveWgetHstsPreloadedHosts(map1);
		Assert.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("pre.https.sub.1");
		l.add("pre.https.sub.2");
		l.add("pre.https.nosub.1");
		l.add("pre.https.nosub.2");
		l.add("pre.tobe.updated.1");
		l.add("pre.tobe.updated.2");
		l.add("tobe.removed.1");
		l.add("tobe.removed.2");
		Assert.assertEquals(l.size(), x.size());
		x.entrySet().forEach(e -> {
			Assert.assertTrue(l.contains(e.getKey()));
			Assert.assertNotNull(e.getValue());
		});
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
		Assert.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("tobe.removed.1");
		l.add("tobe.removed.2");
		Assert.assertEquals(l.size(), x.size());
		x.forEach(e -> Assert.assertTrue(l.contains(e)));
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
		Assert.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("pre.tobe.updated.1");
		l.add("pre.tobe.updated.2");
		Assert.assertEquals(l.size(), x.size());
		x.forEach(e -> Assert.assertTrue(l.contains(e)));
	}

	@Test
	public void testComputeEntriesToWrite() throws IOException {
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Map<String, ChromiumHstsPreloadedEntry> map1 = o.parseChromiumHstsPreloadedList(tempFile1.toFile());
		Files.delete(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsKnownHost> map2 = o.parseWgetHstsKnownHostsDatabase(tempFile2.toFile());
		Files.delete(tempFile2);
		final Map<String, WgetHstsKnownHost> map3 = o.retrieveWgetHstsPreloadedHosts(map2);

		final Set<String> set = o.computeHostsToUpdate(map1, map3);

		final Collection<ChromiumHstsPreloadedEntry> x = o.computeEntriesToWrite(map1, map2, set);
		Assert.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("pre.https.sub.9");
		l.add("pre.https.nosub.9");
		l.add("pre.tobe.updated.1");
		l.add("pre.tobe.updated.2");
		Assert.assertEquals(l.size(), x.size());
		x.stream().map(ChromiumHstsPreloadedEntry::getName).forEach(e -> Assert.assertTrue(l.contains(e)));
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
		Assert.assertEquals(tempFile.toFile(), x.getFile());
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
	public void testExecute() throws IOException {
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);

		o.execute(tempFile2.toString(), tempFile1.toString());

		Files.delete(tempFile1);

		final List<String> y = new ArrayList<>();
		y.add("pre.https.sub.1");
		y.add("pre.https.sub.2");
		y.add("pre.https.nosub.1");
		y.add("pre.https.nosub.2");
		y.add("nopre.https.sub.1");
		y.add("nopre.https.sub.2");
		y.add("nopre.https.nosub.1");
		y.add("nopre.https.nosub.2");
		y.add("pre.https.nosub.9");
		y.add("pre.https.sub.9");
		y.add("pre.tobe.updated.1");
		y.add("pre.tobe.updated.2");

		try (final Stream<String> lines = Files.lines(tempFile2)) {
			final List<String> x = lines.filter(l -> !l.startsWith("#")).map(l -> l.substring(0, l.indexOf('\t'))).collect(Collectors.toList());
			Assert.assertEquals(x.size(), y.size());
			Assert.assertTrue(x.containsAll(y));
			Assert.assertTrue(y.containsAll(x));
		}

		Files.delete(tempFile2);
	}

	@Test
	public void testCreateJsonTempFile() throws IOException {
		final File x;
		try (final InputStream in = getClass().getResourceAsStream('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON)) {
			x = o.createJsonTempFile(in);
			log.log(Level.INFO, "{0}", x);
			x.deleteOnExit();
		}
		Assert.assertThat(x.getName(), endsWith(".json"));
		final Path y = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
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
