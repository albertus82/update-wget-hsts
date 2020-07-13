import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
import java.util.zip.GZIPOutputStream;

import org.chromium.net.http.ChromiumHstsPreloadedEntry;
import org.gnu.wget.WgetHstsEntry;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockServerExtension.class)
class WgetHstsDatabaseUpdaterTest {

	private static final String TRANSPORT_SECURITY_STATE_STATIC_JSON = "transport_security_state_static.json";
	private static final String WGET_HSTS = "wget-hsts";

	private static final Collection<Path> tempFiles = new TreeSet<>();

	private static WgetHstsDatabaseUpdater instance;

	@BeforeAll
	static void beforeAll() {
		instance = new WgetHstsDatabaseUpdater();
	}

	@AfterAll
	static void afterAll(final MockServerClient client) {
		tempFiles.forEach(f -> {
			try {
				Files.delete(f);
				log.info("Deleted temp file '{}'.", f);
			}
			catch (final IOException e) {
				log.debug("Cannot delete temp file '{}'!", f);
				f.toFile().deleteOnExit();
			}
		});
		client.stop();
	}

	@Test
	void testParseChromiumHstsPreloadedList() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Map<String, ChromiumHstsPreloadedEntry> x = instance.parseChromiumHstsPreloadedList(tempFile);
		Assertions.assertNotNull(x);
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
		Assertions.assertEquals(l.size(), x.size());
		x.entrySet().forEach(e -> {
			Assertions.assertTrue(l.contains(e.getKey()));
			Assertions.assertNotNull(e.getValue());
		});
	}

	@Test
	void testParseWgetHstsKnownHostsDatabase() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsEntry> x = instance.parseWgetHstsKnownHostsDatabase(tempFile);
		Assertions.assertNotNull(x);
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
		Assertions.assertEquals(l.size(), x.size());
		x.entrySet().forEach(e -> {
			Assertions.assertTrue(l.contains(e.getKey()));
			Assertions.assertNotNull(e.getValue());
		});
	}

	@Test
	void testRetrieveWgetHstsPreloadedHosts() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsEntry> map1 = instance.parseWgetHstsKnownHostsDatabase(tempFile);
		final Map<String, WgetHstsEntry> x = instance.retrieveWgetHstsPreloadedHosts(map1);
		Assertions.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("pre.https.sub.1");
		l.add("pre.https.sub.2");
		l.add("pre.https.nosub.1");
		l.add("pre.https.nosub.2");
		l.add("pre.tobe.updated.1");
		l.add("pre.tobe.updated.2");
		l.add("tobe.removed.1");
		l.add("tobe.removed.2");
		Assertions.assertEquals(l.size(), x.size());
		x.entrySet().forEach(e -> {
			Assertions.assertTrue(l.contains(e.getKey()));
			Assertions.assertNotNull(e.getValue());
		});
	}

	@Test
	void testComputeHostsToRemove() throws IOException {
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Map<String, ChromiumHstsPreloadedEntry> map1 = instance.parseChromiumHstsPreloadedList(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsEntry> map = instance.parseWgetHstsKnownHostsDatabase(tempFile2);
		final Map<String, WgetHstsEntry> map2 = instance.retrieveWgetHstsPreloadedHosts(map);

		final Set<String> x = instance.computeHostsToRemove(map1.keySet(), map2.keySet());
		Assertions.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("tobe.removed.1");
		l.add("tobe.removed.2");
		Assertions.assertEquals(l.size(), x.size());
		x.forEach(e -> Assertions.assertTrue(l.contains(e)));
	}

	@Test
	void testComputeHostsToUpdate() throws IOException {
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Map<String, ChromiumHstsPreloadedEntry> map1 = instance.parseChromiumHstsPreloadedList(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsEntry> map = instance.parseWgetHstsKnownHostsDatabase(tempFile2);
		final Map<String, WgetHstsEntry> map2 = instance.retrieveWgetHstsPreloadedHosts(map);

		final Set<String> x = instance.computeHostsToUpdate(map1, map2);
		Assertions.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("pre.tobe.updated.1");
		l.add("pre.tobe.updated.2");
		Assertions.assertEquals(l.size(), x.size());
		x.forEach(e -> Assertions.assertTrue(l.contains(e)));
	}

	@Test
	void testComputeEntriesToWrite() throws IOException {
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final Map<String, ChromiumHstsPreloadedEntry> map1 = instance.parseChromiumHstsPreloadedList(tempFile1);

		final Path tempFile2 = createTempFileFromResource('/' + WGET_HSTS);
		final Map<String, WgetHstsEntry> map2 = instance.parseWgetHstsKnownHostsDatabase(tempFile2);
		final Map<String, WgetHstsEntry> map3 = instance.retrieveWgetHstsPreloadedHosts(map2);

		final Set<String> set = instance.computeHostsToUpdate(map1, map3);

		final Collection<ChromiumHstsPreloadedEntry> x = instance.computeEntriesToWrite(map1, map2, set);
		Assertions.assertNotNull(x);
		final List<String> l = new ArrayList<>(x.size());
		l.add("pre.https.sub.9");
		l.add("pre.https.nosub.9");
		l.add("pre.tobe.updated.1");
		l.add("pre.tobe.updated.2");
		Assertions.assertEquals(l.size(), x.size());
		x.stream().map(ChromiumHstsPreloadedEntry::getName).forEach(e -> Assertions.assertTrue(l.contains(e)));
	}

	@Test
	void testCreateEmptyWgetHstsTempFile() throws IOException {
		final Path x = instance.createEmptyWgetHstsTempFile();
		log.info("Created temp file '{}'", x);
		tempFiles.add(x);
		Assertions.assertTrue(Files.exists(x));
		Assertions.assertTrue(Files.isRegularFile(x));
		MatcherAssert.assertThat(Files.size(x), greaterThan(0L));
	}

	@Test
	void testRetrieveSourceFileLocal() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		final WgetHstsDatabaseUpdater.SourceFile x = instance.retrieveSourceFile(tempFile.toString());
		Assertions.assertFalse(x.isTemp());
		Assertions.assertEquals(tempFile, x.getPath());
	}

	@Test
	void testRetrieveSourceFileRemote(final MockServerClient client) throws IOException, URISyntaxException {
		final String uncompressedResponseBody = createUncompressedResponseBody();
		final byte[] compressedResponseBody = createCompressedResponseBody();

		final String uncompressedPath = "/testRetrieveSourceFileRemote/uncompressed.json";
		final String compressedPath = "/testRetrieveSourceFileRemote/compressed.json";

		client.when(new HttpRequest().withMethod("GET").withPath(uncompressedPath)).respond(new HttpResponse().withStatusCode(200).withBody(new JsonBody(uncompressedResponseBody)));
		client.when(new HttpRequest().withMethod("GET").withPath(compressedPath).withHeader("Accept-Encoding", "gzip")).respond(new HttpResponse().withStatusCode(200).withHeader("Content-Encoding", "gzip").withBody(new BinaryBody(compressedResponseBody, MediaType.APPLICATION_JSON_UTF_8)));

		final InetSocketAddress remoteAddress = client.remoteAddress();

		WgetHstsDatabaseUpdater.SourceFile sf1 = instance.retrieveSourceFile(new URI("http", null, remoteAddress.getHostString(), remoteAddress.getPort(), uncompressedPath, null, null).toString());
		Assertions.assertNotNull(sf1);
		Assertions.assertTrue(sf1.isTemp());
		final ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
		Files.copy(sf1.getPath(), baos1);
		Assertions.assertEquals(uncompressedResponseBody, baos1.toString(StandardCharsets.UTF_8.name()));

		WgetHstsDatabaseUpdater.SourceFile sf2 = instance.retrieveSourceFile(new URI("http", null, remoteAddress.getHostString(), remoteAddress.getPort(), compressedPath, null, null).toString());
		Assertions.assertNotNull(sf2);
		Assertions.assertTrue(sf2.isTemp());
		final ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
		Files.copy(sf2.getPath(), baos2);
		Assertions.assertEquals(uncompressedResponseBody, baos2.toString(StandardCharsets.UTF_8.name()));
	}

	@Test
	void testBackupExistingWgetHstsFile() throws IOException {
		final Path tempFile = createTempFileFromResource('/' + WGET_HSTS);

		final Path x = instance.backupExistingWgetHstsFile(tempFile);
		log.info("Created temp file '{}'", x);
		tempFiles.add(x);
		MatcherAssert.assertThat(x.toString(), endsWith(".bak.gz"));
		Assertions.assertTrue(x.toFile().exists());
		Assertions.assertTrue(Files.isRegularFile(x));
		MatcherAssert.assertThat(Files.size(x), greaterThan(0L));

		final Path y = instance.backupExistingWgetHstsFile(tempFile);
		log.info("Created temp file '{}'", y);
		tempFiles.add(y);
		MatcherAssert.assertThat(y.toString(), endsWith(".bak.1.gz"));
		Assertions.assertTrue(y.toFile().exists());
		Assertions.assertTrue(Files.isRegularFile(y));
		MatcherAssert.assertThat(Files.size(y), greaterThan(0L));

		final Path z = instance.backupExistingWgetHstsFile(tempFile);
		log.info("Created temp file '{}'", z);
		tempFiles.add(z);
		MatcherAssert.assertThat(z.toString(), endsWith(".bak.2.gz"));
		Assertions.assertTrue(z.toFile().exists());
		Assertions.assertTrue(Files.isRegularFile(z));
		MatcherAssert.assertThat(Files.size(z), greaterThan(0L));
	}

	@Test
	void testExecuteWithFile() throws IOException {
		final Path tempFile1 = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
		testExecute(tempFile1.toString());
	}

	@Test
	void testExecuteWithURI(final MockServerClient client) throws IOException, URISyntaxException {
		final String uncompressedResponseBody = createUncompressedResponseBody();
		final byte[] compressedResponseBody = createCompressedResponseBody();

		final String uncompressedPath = "/testExecuteWithURI/uncompressed.json";
		final String compressedPath = "/testExecuteWithURI/compressed.json";

		client.when(new HttpRequest().withMethod("GET").withPath(uncompressedPath)).respond(new HttpResponse().withStatusCode(200).withBody(new JsonBody(uncompressedResponseBody)));
		client.when(new HttpRequest().withMethod("GET").withPath(compressedPath).withHeader("Accept-Encoding", "gzip")).respond(new HttpResponse().withStatusCode(200).withHeader("Content-Encoding", "gzip").withBody(new BinaryBody(compressedResponseBody, MediaType.APPLICATION_JSON_UTF_8)));

		final InetSocketAddress remoteAddress = client.remoteAddress();

		testExecute(new URI("http", null, remoteAddress.getHostString(), remoteAddress.getPort(), uncompressedPath, null, null).toString());
		testExecute(new URI("http", null, remoteAddress.getHostString(), remoteAddress.getPort(), compressedPath, null, null).toString());
	}

	@Test
	void testCreateJsonTempFile() throws IOException {
		try (final InputStream in = getClass().getResourceAsStream('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON)) {
			final Path x = instance.createChromiumHstsPreloadedJsonTempFile(in);
			log.info("Created temp file '{}'.", x);
			tempFiles.add(x);
			MatcherAssert.assertThat(x.toString(), endsWith(".json"));
			final Path y = createTempFileFromResource('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON);
			Assertions.assertEquals(Files.size(y), Files.size(x));
		}
	}

	@Test
	void testVersionProvider() {
		final VersionProvider vp = new VersionProvider();
		Assertions.assertNotEquals(0, vp.loadBuildInfo().size());
		Assertions.assertNotEquals(0, vp.getVersion().length);

	}

	private void testExecute(final String source) throws IOException {
		final Path outFile = createTempFileFromResource('/' + WGET_HSTS);

		final long t = System.currentTimeMillis() - 999;
		instance.execute(outFile, source);

		final Path backupFile = Paths.get(outFile.toString() + ".bak.gz");
		final long lastModifiedTime = Files.getLastModifiedTime(backupFile).toMillis();
		if (lastModifiedTime < t) {
			throw new IllegalStateException(lastModifiedTime + " < " + t);
		}
		tempFiles.add(backupFile);
		Assertions.assertTrue(backupFile.toFile().exists());

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

		try (final Stream<String> lines = Files.lines(outFile)) {
			Assertions.assertNotEquals(0, lines.filter(l -> l.startsWith("#")).count(), "Comments lost!");
		}
		try (final Stream<String> lines = Files.lines(outFile)) {
			final Collection<String> x = lines.filter(l -> !l.startsWith("#")).collect(Collectors.toList());
			Assertions.assertEquals(x.size(), y.size());
			Assertions.assertTrue(x.containsAll(y));
			Assertions.assertTrue(y.containsAll(x));
		}
	}

	private Path createTempFileFromResource(final String resourceName) throws IOException {
		final Path tempFile = Files.createTempFile(null, null);
		log.info("Created temp file '{}'.", tempFile);
		tempFiles.add(tempFile);
		try (final InputStream in = getClass().getResourceAsStream(resourceName)) {
			Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return tempFile;
	}

	private byte[] createCompressedResponseBody() throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (final InputStream in = getClass().getResourceAsStream('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON); final Reader isr = new InputStreamReader(in, StandardCharsets.UTF_8); final BufferedReader br = new BufferedReader(isr); final GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
			String str;
			while ((str = br.readLine()) != null) {
				gzos.write(str.getBytes(StandardCharsets.UTF_8));
			}
		}
		return baos.toByteArray();
	}

	private String createUncompressedResponseBody() throws IOException {
		final StringBuilder sb = new StringBuilder();
		try (final InputStream in = getClass().getResourceAsStream('/' + TRANSPORT_SECURITY_STATE_STATIC_JSON); final Reader isr = new InputStreamReader(in, StandardCharsets.UTF_8); final BufferedReader br = new BufferedReader(isr)) {
			String str;
			while ((str = br.readLine()) != null) {
				sb.append(str);
			}
		}
		return sb.toString();
	}

}
