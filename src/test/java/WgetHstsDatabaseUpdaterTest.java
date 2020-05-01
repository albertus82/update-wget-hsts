import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.chromium.net.http.ChromiumHstsPreloadedEntry;
import org.gnu.wget.WgetHstsEntry;
import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import lombok.extern.java.Log;

@Log
public class WgetHstsDatabaseUpdaterTest {

	private static final String TRANSPORT_SECURITY_STATE_STATIC_JSON = "transport_security_state_static.json";
	private static final String WGET_HSTS = "wget-hsts";

	private static final Collection<Path> tempFiles = new TreeSet<>();

	private static WgetHstsDatabaseUpdater instance;

	@BeforeClass
	public static void beforeClass() {
		instance = new WgetHstsDatabaseUpdater();
	}

	@AfterClass
	public static void afterClass() {
		tempFiles.forEach(f -> {
			try {
				Files.delete(f);
				log.log(INFO, "Deleted temp file ''{0}''.", f);
			}
			catch (final IOException e) {
				log.log(WARNING, "Cannot delete temp file ''{0}''!", f);
				f.toFile().deleteOnExit();
			}
		});
	}

	@Test
	public void testParseChromiumHstsPreloadedList() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Map<String, ChromiumHstsPreloadedEntry> x = instance.parseChromiumHstsPreloadedList(tempFile);
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
		final Map<String, WgetHstsEntry> x = instance.parseWgetHstsKnownHostsDatabase(tempFile);
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
		final Map<String, WgetHstsEntry> map1 = instance.parseWgetHstsKnownHostsDatabase(tempFile);
		final Map<String, WgetHstsEntry> x = instance.retrieveWgetHstsPreloadedHosts(map1);
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
		final Map<String, ChromiumHstsPreloadedEntry> map1 = instance.parseChromiumHstsPreloadedList(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsEntry> map = instance.parseWgetHstsKnownHostsDatabase(tempFile2);
		final Map<String, WgetHstsEntry> map2 = instance.retrieveWgetHstsPreloadedHosts(map);

		final Set<String> x = instance.computeHostsToRemove(map1.keySet(), map2.keySet());
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
		final Map<String, ChromiumHstsPreloadedEntry> map1 = instance.parseChromiumHstsPreloadedList(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsEntry> map = instance.parseWgetHstsKnownHostsDatabase(tempFile2);
		final Map<String, WgetHstsEntry> map2 = instance.retrieveWgetHstsPreloadedHosts(map);

		final Set<String> x = instance.computeHostsToUpdate(map1, map2);
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
		final Map<String, ChromiumHstsPreloadedEntry> map1 = instance.parseChromiumHstsPreloadedList(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsEntry> map2 = instance.parseWgetHstsKnownHostsDatabase(tempFile2);
		final Map<String, WgetHstsEntry> map3 = instance.retrieveWgetHstsPreloadedHosts(map2);

		final Set<String> set = instance.computeHostsToUpdate(map1, map3);

		final Collection<ChromiumHstsPreloadedEntry> x = instance.computeEntriesToWrite(map1, map2, set);
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
	public void testCreateEmptyWgetHstsTempFile() throws IOException {
		final Path x = instance.createEmptyWgetHstsTempFile();
		log.log(INFO, "Created temp file ''{0}''", x);
		tempFiles.add(x);
		Assert.assertTrue(Files.exists(x));
		Assert.assertTrue(Files.isRegularFile(x));
		MatcherAssert.assertThat(Files.size(x), greaterThan(0L));
	}

	@Test
	public void testRetrieveSourceFileLocal() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final WgetHstsDatabaseUpdater.SourceFile x = instance.retrieveSourceFile(tempFile.toString());
		Assert.assertFalse(x.isTemp());
		Assert.assertEquals(tempFile, x.getPath());
	}

	@Test
	public void testBackupExistingWgetHstsFile() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);

		final Path x = instance.backupExistingWgetHstsFile(tempFile);
		log.log(INFO, "Created temp file ''{0}''", x);
		tempFiles.add(x);
		MatcherAssert.assertThat(x.toString(), endsWith(".bak.gz"));
		Assert.assertTrue(x.toFile().exists());
		Assert.assertTrue(Files.isRegularFile(x));
		MatcherAssert.assertThat(x.toFile().length(), greaterThan(0L));

		final Path y = instance.backupExistingWgetHstsFile(tempFile);
		log.log(INFO, "Created temp file ''{0}''", y);
		tempFiles.add(y);
		MatcherAssert.assertThat(y.toString(), endsWith(".bak.1.gz"));
		Assert.assertTrue(y.toFile().exists());
		Assert.assertTrue(Files.isRegularFile(y));
		MatcherAssert.assertThat(y.toFile().length(), greaterThan(0L));

		final Path z = instance.backupExistingWgetHstsFile(tempFile);
		log.log(INFO, "Created temp file ''{0}''", z);
		tempFiles.add(z);
		MatcherAssert.assertThat(z.toString(), endsWith(".bak.2.gz"));
		Assert.assertTrue(z.toFile().exists());
		Assert.assertTrue(Files.isRegularFile(z));
		MatcherAssert.assertThat(z.toFile().length(), greaterThan(0L));
	}

	@Test
	public void testExecute() throws IOException {
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);

		final long t = System.currentTimeMillis() - 999;
		instance.execute(tempFile2.toString(), tempFile1.toString());

		final Path backupFile = Paths.get(tempFile2.toString() + ".bak.gz");
		if (backupFile.toFile().lastModified() < t) {
			throw new IllegalStateException(backupFile.toFile().lastModified() + " < " + t);
		}
		tempFiles.add(backupFile);
		Assert.assertTrue(Files.exists(backupFile));

		final Collection<String> y = new ArrayList<>();
		final String mask = "%s\t%d\t%d\t%d\t%d";
		y.add(String.format(mask, "pre.https.sub.1", 0, 1, Integer.MAX_VALUE, 0));
		y.add(String.format(mask, "pre.https.sub.2", 0, 1, Integer.MAX_VALUE, 0));
		y.add(String.format(mask, "pre.https.nosub.1", 0, 0, Integer.MAX_VALUE, 0));
		y.add(String.format(mask, "pre.https.nosub.2", 0, 0, Integer.MAX_VALUE, 0));
		y.add(String.format(mask, "nopre.https.sub.1", 0, 1, 1111111111, 22222222));
		y.add(String.format(mask, "nopre.https.sub.2", 0, 1, 1111111111, 22222222));
		y.add(String.format(mask, "nopre.https.nosub.1", 0, 0, 1111111111, 22222222));
		y.add(String.format(mask, "nopre.https.nosub.2", 0, 0, 1111111111, 22222222));
		y.add(String.format(mask, "pre.https.nosub.9", 0, 0, Integer.MAX_VALUE, 0));
		y.add(String.format(mask, "pre.https.sub.9", 0, 1, Integer.MAX_VALUE, 0));
		y.add(String.format(mask, "pre.tobe.updated.1", 0, 1, Integer.MAX_VALUE, 0));
		y.add(String.format(mask, "pre.tobe.updated.2", 0, 0, Integer.MAX_VALUE, 0));

		try (final Stream<String> lines = Files.lines(tempFile2)) {
			Assert.assertNotEquals("Comments lost!", 0, lines.filter(l -> l.startsWith("#")).count());
		}
		try (final Stream<String> lines = Files.lines(tempFile2)) {
			final Collection<String> x = lines.filter(l -> !l.startsWith("#")).collect(Collectors.toList());
			Assert.assertEquals(x.size(), y.size());
			Assert.assertTrue(x.containsAll(y));
			Assert.assertTrue(y.containsAll(x));
		}
	}

	@Test
	public void testCreateJsonTempFile() throws IOException {
		try (final InputStream in = getClass().getResourceAsStream('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON)) {
			final Path x = instance.createChromiumHstsPreloadedJsonTempFile(in);
			log.log(INFO, "Created temp file ''{0}''.", x);
			tempFiles.add(x);
			MatcherAssert.assertThat(x.toString(), endsWith(".json"));
			final Path y = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
			Assert.assertEquals(Files.size(y), x.toFile().length());
		}
	}

	private Path createTempFileFromResource(final String resourceName) throws IOException {
		final Path tempFile = Files.createTempFile(null, null);
		log.log(INFO, "Created temp file ''{0}''.", tempFile);
		tempFiles.add(tempFile);
		try (final InputStream in = getClass().getResourceAsStream(resourceName)) {
			Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return tempFile;
	}

}
