import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.chromium.net.http.ChromiumHstsPreloadedEntry;
import org.chromium.net.http.ChromiumHstsPreloadedList;
import org.gnu.wget.WgetHstsEntry;

import com.google.gson.Gson;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.java.Log;

@Log
public class WgetHstsDatabaseUpdater {

	private static final String BUILD_INFO_FILE_NAME = "/META-INF/build-info.properties";

	public static void main(final String... args) throws IOException {
		if (args.length == 2) {
			new WgetHstsDatabaseUpdater().execute(args[0], args[1]);
		}
		else {
			final Properties buildInfo = loadBuildInfo();
			System.out.printf("Typical usage: java -jar %s.jar ~/.wget-hsts https://cs.chromium.org/codesearch/f/chromium/src/net/http/transport_security_state_static.json%n", buildInfo.getProperty("project.artifactId"));
		}
	}

	void execute(@NonNull final String destination, @NonNull final String source) throws IOException {
		final SourceFile sourceFile = retrieveSourceFile(source);
		System.out.printf("Parsing source file '%s'... ", sourceFile.getFile());
		final Map<String, ChromiumHstsPreloadedEntry> chromiumHstsPreloadedEntryMap = parseChromiumHstsPreloadedList(sourceFile.getFile());
		if (sourceFile.isTemp()) {
			sourceFile.getFile().delete();
		}
		System.out.printf("%d entries found%n", chromiumHstsPreloadedEntryMap.size());

		final File destinationFile = new File(destination);

		final Map<String, WgetHstsEntry> wgetHstsKnownHostMap;
		final Set<String> hostsToRemove;
		final Set<String> hostsToUpdate;
		if (destinationFile.exists()) {
			System.out.printf("Parsing destination file '%s'... ", destinationFile);
			wgetHstsKnownHostMap = parseWgetHstsKnownHostsDatabase(destinationFile);
			System.out.printf("%d entries found%n", wgetHstsKnownHostMap.size());

			System.out.print("Computing entries to delete... ");
			final Map<String, WgetHstsEntry> wgetHstsPreloadedHostMap = retrieveWgetHstsPreloadedHosts(wgetHstsKnownHostMap);
			hostsToRemove = computeHostsToRemove(chromiumHstsPreloadedEntryMap.keySet(), wgetHstsPreloadedHostMap.keySet());
			System.out.println(hostsToRemove.isEmpty() ? "none" : hostsToRemove.size());

			System.out.print("Computing entries to update... ");
			hostsToUpdate = computeHostsToUpdate(chromiumHstsPreloadedEntryMap, wgetHstsPreloadedHostMap);
			System.out.println(hostsToUpdate.isEmpty() ? "none" : hostsToUpdate.size());
		}
		else {
			wgetHstsKnownHostMap = Collections.emptyMap();
			hostsToRemove = Collections.emptySet();
			hostsToUpdate = Collections.emptySet();
		}

		System.out.print("Computing entries to insert... ");
		final Collection<ChromiumHstsPreloadedEntry> entriesToWrite = computeEntriesToWrite(chromiumHstsPreloadedEntryMap, wgetHstsKnownHostMap, hostsToUpdate);
		final int entriesToInsertCount = entriesToWrite.size() - hostsToUpdate.size();
		System.out.println(entriesToInsertCount == 0 ? "none" : entriesToInsertCount);

		if (!entriesToWrite.isEmpty() || !hostsToRemove.isEmpty()) {
			System.out.print("Collecting entries to write... ");
			final Path tempPath = createTempWgetHstsKnownHostsDatabase();
			try (final FileWriter fw = new FileWriter(tempPath.toFile(), true); final BufferedWriter bw = new BufferedWriter(fw)) {
				final Stream<WgetHstsEntry> retained = wgetHstsKnownHostMap.values().stream().filter(e -> !hostsToRemove.contains(e.getHostname()) && !hostsToUpdate.contains(e.getHostname()));
				final Stream<WgetHstsEntry> updated = entriesToWrite.stream().map(oe -> WgetHstsEntry.builder().hostname(oe.getName()).includeSubdomains(oe.isIncludeSubdomains() || oe.isIncludeSubdomainsForPinning()).created(Integer.MAX_VALUE).maxAge(0).build()).sorted((e1, e2) -> e1.getHostname().compareTo(e2.getHostname()));
				Stream.concat(retained, updated).map(WgetHstsEntry::toString).forEachOrdered(l -> writeLine(bw, l));
			}
			catch (final Exception e) {
				Files.deleteIfExists(tempPath);
				throw e;
			}
			System.out.println("done");

			if (destinationFile.exists()) {
				System.out.printf("Backing up existing file '%s'... ", destinationFile);
				final File backupFile = backupWgetHstsKnownHostsDatabase(destinationFile);
				System.out.printf("-> '%s'%n", backupFile);
			}

			System.out.printf("Updating destination file '%s'... ", destinationFile);
			try {
				Files.move(tempPath, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (final AtomicMoveNotSupportedException e) {
				log.log(Level.FINE, e.toString(), e);
				Files.move(tempPath, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			System.out.println("done");
		}
	}

	Collection<ChromiumHstsPreloadedEntry> computeEntriesToWrite(final Map<String, ChromiumHstsPreloadedEntry> chromiumHstsPreloadedEntryMap, final Map<String, WgetHstsEntry> wgetHstsKnownHostMap, final Set<String> hostsToUpdate) {
		return chromiumHstsPreloadedEntryMap.values().stream().filter(e -> "force-https".equalsIgnoreCase(e.getMode()) && (!wgetHstsKnownHostMap.containsKey(e.getName()) || hostsToUpdate.contains(e.getName()))).collect(Collectors.toList());
	}

	Set<String> computeHostsToUpdate(@NonNull final Map<String, ChromiumHstsPreloadedEntry> chromiumHstsPreloadedEntryMap, @NonNull final Map<String, WgetHstsEntry> wgetHstsPreloadedHostMap) {
		return wgetHstsPreloadedHostMap.entrySet().stream().filter(e -> {
			final ChromiumHstsPreloadedEntry preloadedEntry = chromiumHstsPreloadedEntryMap.get(e.getKey());
			return preloadedEntry != null && (preloadedEntry.isIncludeSubdomains() || preloadedEntry.isIncludeSubdomainsForPinning()) != e.getValue().isIncludeSubdomains();
		}).map(Entry::getKey).collect(Collectors.toSet());
	}

	Set<String> computeHostsToRemove(@NonNull final Set<String> chromiumHstsPreloadedHosts, @NonNull final Set<String> wgetHstsPreloadedHosts) {
		return wgetHstsPreloadedHosts.stream().filter(wgetPreloadedHost -> !chromiumHstsPreloadedHosts.contains(wgetPreloadedHost)).collect(Collectors.toSet());
	}

	Map<String, WgetHstsEntry> retrieveWgetHstsPreloadedHosts(final Map<String, WgetHstsEntry> wgetHstsKnownHostMap) {
		return wgetHstsKnownHostMap.entrySet().stream().filter(e -> e.getValue().getCreated() == Integer.MAX_VALUE && e.getValue().getMaxAge() == 0).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
	}

	SourceFile retrieveSourceFile(@NonNull final String source) throws IOException {
		try {
			final URL url = new URL(source);
			System.out.printf("Downloading '%s'... ", url);
			final URLConnection connection = url.openConnection();
			connection.setRequestProperty("Accept-Encoding", "gzip");
			connection.setRequestProperty("Accept", "application/json,*/*;q=0.9");
			final File file;
			try (final InputStream raw = connection.getInputStream(); final InputStream in = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
				file = createJsonTempFile(in);
			}
			System.out.printf("%d kB fetched%n", file.length() / 1024);
			return new SourceFile(file, true);
		}
		catch (final MalformedURLException e) {
			log.log(Level.FINE, e.toString(), e);
			return new SourceFile(new File(source), false);
		}
	}

	Path createTempWgetHstsKnownHostsDatabase() throws IOException {
		final Path path = Files.createTempFile("wget-hsts-", null);
		return Files.write(path, Arrays.asList("# HSTS 1.0 Known Hosts database for GNU Wget.", "# Edit at your own risk.", "# <hostname>\t<port>\t<incl. subdomains>\t<created>\t<max-age>"), StandardOpenOption.APPEND);
	}

	File backupWgetHstsKnownHostsDatabase(@NonNull final File wgetHstsFile) throws IOException {
		File backupFile = new File(wgetHstsFile.getPath() + ".bak.gz");
		int i = 1;
		while (backupFile.exists()) {
			backupFile = new File(wgetHstsFile.getPath() + ".bak." + i++ + ".gz");
		}
		try (final OutputStream fos = new FileOutputStream(backupFile); final OutputStream gzos = new GZIPOutputStream(fos)) {
			Files.copy(wgetHstsFile.toPath(), gzos);
		}
		return backupFile;
	}

	Map<String, ChromiumHstsPreloadedEntry> parseChromiumHstsPreloadedList(@NonNull final File transportSecurityStateStaticJson) throws IOException {
		final ChromiumHstsPreloadedList root;
		try (final FileReader fr = new FileReader(transportSecurityStateStaticJson); final BufferedReader br = new BufferedReader(fr)) {
			root = new Gson().fromJson(br, ChromiumHstsPreloadedList.class);
		}
		return root.getEntries().stream().collect(Collectors.toMap(ChromiumHstsPreloadedEntry::getName, e -> e));
	}

	Map<String, WgetHstsEntry> parseWgetHstsKnownHostsDatabase(@NonNull final File wgetHstsFile) throws IOException {
		try (final Stream<String> lines = Files.lines(wgetHstsFile.toPath())) {
			return lines.map(String::trim).filter(l -> !l.startsWith("#")).map(l -> l.split("[\\t\\s]+")).filter(a -> a.length == 5).map(a -> WgetHstsEntry.builder().hostname(a[0].trim()).port(Integer.parseInt(a[1].trim())).includeSubdomains("1".equals(a[2].trim())).created(Integer.parseInt(a[3].trim())).maxAge(Integer.parseInt(a[4].trim())).build()).collect(Collectors.toMap(WgetHstsEntry::getHostname, e -> e, (k, v) -> {
				throw new IllegalStateException("Duplicate key " + k);
			}, LinkedHashMap::new));
		}
	}

	File createJsonTempFile(@NonNull final InputStream in) throws IOException {
		final Path path = Files.createTempFile("hsts-", ".json");
		Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
		final File file = path.toFile();
		file.deleteOnExit();
		return file;
	}

	@SneakyThrows(IOException.class)
	private static void writeLine(final BufferedWriter writer, final String line) {
		writer.write(line);
		writer.newLine();
	}

	private static Properties loadBuildInfo() {
		final Properties properties = new Properties();
		try (final InputStream is = WgetHstsDatabaseUpdater.class.getResourceAsStream(BUILD_INFO_FILE_NAME)) {
			if (is != null) {
				properties.load(is);
			}
		}
		catch (final Exception e) {
			log.log(Level.SEVERE, "Cannot read class path resource:" + BUILD_INFO_FILE_NAME, e);
		}
		return properties;
	}

	@Value
	static class SourceFile {
		File file;
		boolean temp;
	}
}
