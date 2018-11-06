import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.chromium.net.http.ChromiumHstsPreloadedEntry;
import org.chromium.net.http.ChromiumHstsPreloadedList;
import org.gnu.wget.WgetHstsKnownHost;

import com.google.gson.Gson;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.java.Log;

@Log
public class WgetHstsDatabaseUpdater {

	public static void main(final String... args) throws IOException {
		if (args.length == 2) {
			new WgetHstsDatabaseUpdater().execute(args[0], args[1]);
		}
		else {
			System.out.println("Typical usage: java -jar update-wget-hsts-database.jar ~/.wget-hsts https://cs.chromium.org/codesearch/f/chromium/src/net/http/transport_security_state_static.json");
		}
	}

	private void execute(@NonNull final String destination, @NonNull final String source) throws IOException {
		final SourceFile sourceFile = retrieveSourceFile(source);
		System.out.printf("Parsing source file '%s'... ", sourceFile.getFile());
		final Map<String, ChromiumHstsPreloadedEntry> chromiumHstsPreloadedEntryMap = parseChromiumHstsPreloadedList(sourceFile.getFile());
		if (sourceFile.isTemp()) {
			sourceFile.getFile().delete();
		}
		System.out.printf("%d entries found%n", chromiumHstsPreloadedEntryMap.size());

		final File destinationFile = new File(destination);

		final Map<String, WgetHstsKnownHost> wgetHstsKnownHostMap;
		final Set<String> hostsToRemove;
		final Set<String> hostsToUpdate;
		if (destinationFile.exists()) {
			System.out.printf("Parsing destination file '%s'... ", destinationFile);
			wgetHstsKnownHostMap = parseWgetHstsKnownHostsDatabase(destinationFile);
			System.out.printf("%d entries found%n", wgetHstsKnownHostMap.size());

			System.out.print("Computing entries to delete... ");
			final Map<String, WgetHstsKnownHost> wgetHstsPreloadedHostMap = retrieveWgetHstsPreloadedHosts(wgetHstsKnownHostMap);
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
		final Collection<ChromiumHstsPreloadedEntry> entriesToWrite = chromiumHstsPreloadedEntryMap.values().stream().filter(e -> "force-https".equalsIgnoreCase(e.getMode()) && (!wgetHstsKnownHostMap.containsKey(e.getName()) || hostsToUpdate.contains(e.getName()))).collect(Collectors.toList());
		final int entriesToInsertCount = entriesToWrite.size() - hostsToUpdate.size();
		System.out.println(entriesToInsertCount == 0 ? "none" : entriesToInsertCount);

		if (!entriesToWrite.isEmpty() || !hostsToRemove.isEmpty()) {
			System.out.print("Collecting entries to write... ");
			final Path tempPath = createTempWgetHstsKnownHostsDatabase();
			try (final FileWriter fw = new FileWriter(tempPath.toFile(), true); final BufferedWriter bw = new BufferedWriter(fw)) {
				Stream.concat(wgetHstsKnownHostMap.values().stream().filter(e -> !hostsToRemove.contains(e.getHostname()) && !hostsToUpdate.contains(e.getHostname())), entriesToWrite.stream().map(oe -> {
					final WgetHstsKnownHost ne = new WgetHstsKnownHost(oe.getName());
					ne.setIncludeSubdomains(oe.isIncludeSubdomains() || oe.isIncludeSubdomainsForPinning());
					ne.setCreated(Integer.MAX_VALUE);
					ne.setMaxAge(0);
					return ne;
				}).sorted((e1, e2) -> e1.getHostname().compareTo(e2.getHostname()))).map(e -> String.format("%s\t%d\t%d\t%d\t%d", e.getHostname(), e.getPort(), e.isIncludeSubdomains() ? 1 : 0, e.getCreated(), e.getMaxAge())).forEachOrdered(l -> {
					try {
						bw.write(l);
						bw.newLine();
					}
					catch (final IOException e) {
						throw new UncheckedIOException(e);
					}
				});
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

	private static Set<String> computeHostsToUpdate(final Map<String, ChromiumHstsPreloadedEntry> chromiumHstsPreloadedEntryMap, final Map<String, WgetHstsKnownHost> currentWgetPreloadedEntryMap) {
		return currentWgetPreloadedEntryMap.entrySet().stream().filter(e -> {
			final ChromiumHstsPreloadedEntry preloadedEntry = chromiumHstsPreloadedEntryMap.get(e.getKey());
			return preloadedEntry != null && (preloadedEntry.isIncludeSubdomains() || preloadedEntry.isIncludeSubdomainsForPinning()) != e.getValue().isIncludeSubdomains();
		}).map(Entry::getKey).collect(Collectors.toSet());
	}

	private static Set<String> computeHostsToRemove(final Set<String> chromiumPreloadedHosts, final Set<String> wgetPreloadedHosts) {
		return wgetPreloadedHosts.stream().filter(wgetPreloadedHost -> !chromiumPreloadedHosts.contains(wgetPreloadedHost)).collect(Collectors.toSet());
	}

	private static Map<String, WgetHstsKnownHost> retrieveWgetHstsPreloadedHosts(final Map<String, WgetHstsKnownHost> currentWgetHstsEntryMap) {
		return currentWgetHstsEntryMap.entrySet().stream().filter(e -> e.getValue().getCreated() == Integer.MAX_VALUE && e.getValue().getMaxAge() == 0).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
	}

	private static SourceFile retrieveSourceFile(@NonNull final String source) throws IOException {
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

	private static Path createTempWgetHstsKnownHostsDatabase() throws IOException {
		final Path path = Files.createTempFile("wget-hsts-", null);
		return Files.write(path, Arrays.asList("# HSTS 1.0 Known Hosts database for GNU Wget.", "# Edit at your own risk.", "# <hostname>\t<port>\t<incl. subdomains>\t<created>\t<max-age>"), StandardOpenOption.APPEND);
	}

	private static File backupWgetHstsKnownHostsDatabase(@NonNull final File wgetHstsFile) throws IOException {
		File backupFile = new File(wgetHstsFile.getPath() + ".bak");
		int i = 1;
		while (backupFile.exists()) {
			backupFile = new File(wgetHstsFile.getPath() + ".bak." + i++);
		}
		return Files.copy(wgetHstsFile.toPath(), backupFile.toPath()).toFile();
	}

	private static Map<String, ChromiumHstsPreloadedEntry> parseChromiumHstsPreloadedList(@NonNull final File hstsPreloadedListFile) throws IOException {
		final ChromiumHstsPreloadedList root;
		try (final FileReader fr = new FileReader(hstsPreloadedListFile); final BufferedReader br = new BufferedReader(fr)) {
			root = new Gson().fromJson(br, ChromiumHstsPreloadedList.class);
		}
		return root.getEntries().stream().collect(Collectors.toMap(ChromiumHstsPreloadedEntry::getName, e -> e));
	}

	private static Map<String, WgetHstsKnownHost> parseWgetHstsKnownHostsDatabase(@NonNull final File wgetHstsFile) throws IOException {
		try (final Stream<String> lines = Files.lines(wgetHstsFile.toPath())) {
			return lines.map(String::trim).filter(l -> !l.startsWith("#")).map(l -> l.split("[\\t\\s]+")).filter(a -> a.length == 5).map(a -> {
				final WgetHstsKnownHost e = new WgetHstsKnownHost(a[0].trim());
				e.setPort(Integer.parseInt(a[1].trim()));
				e.setIncludeSubdomains("1".equals(a[2].trim()));
				e.setCreated(Integer.parseInt(a[3].trim()));
				e.setMaxAge(Integer.parseInt(a[4].trim()));
				return e;
			}).collect(Collectors.toMap(WgetHstsKnownHost::getHostname, e -> e, (k, v) -> {
				throw new IllegalStateException("Duplicate key " + k);
			}, LinkedHashMap::new));
		}
	}

	private static File createJsonTempFile(@NonNull final InputStream in) throws IOException {
		final Path path = Files.createTempFile("hsts-", ".json");
		Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
		final File file = path.toFile();
		file.deleteOnExit();
		return file;
	}

	@Value
	private static class SourceFile {
		File file;
		boolean temp;
	}
}
