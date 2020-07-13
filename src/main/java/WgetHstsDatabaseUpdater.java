import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;
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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Parameters;

@Log
@SuppressWarnings("java:S106") // "Standard outputs should not be used directly to log anything"
@Command(name = "wget-update-hsts-database", description = "Import preloaded HTTP Strict Transport Security (HSTS) domains into GNU Wget.", footer = "Typical usage: java -jar wget-update-hsts-database.jar ~/.wget-hsts https://github.com/chromium/chromium/raw/master/net/http/transport_security_state_static.json", usageHelpWidth = 255, mixinStandardHelpOptions = true, versionProvider = VersionProvider.class)
public class WgetHstsDatabaseUpdater implements Callable<Integer> {

	public static void main(final String... args) {
		new CommandLine(new WgetHstsDatabaseUpdater()).execute(args);
	}

	@Parameters(index = "0", description = "Destination file")
	private Path destination;

	@Parameters(index = "1", description = "Source file or URL")
	private String source;

	@Override
	public Integer call() throws IOException {
		new WgetHstsDatabaseUpdater().execute(destination, source);
		return 0;
	}

	void execute(@NonNull final Path destinationPath, @NonNull final String source) throws IOException {
		final SourceFile sourceFile = retrieveSourceFile(source);
		System.out.printf("Parsing source file '%s'... ", sourceFile.getPath());
		final Map<String, ChromiumHstsPreloadedEntry> chromiumHstsPreloadedEntryMap = parseChromiumHstsPreloadedList(sourceFile.getPath());
		if (sourceFile.isTemp()) {
			Files.delete(sourceFile.getPath());
		}
		System.out.printf("%d entries found%n", chromiumHstsPreloadedEntryMap.size());

		final Map<String, WgetHstsEntry> wgetHstsKnownHostMap;
		final Set<String> hostsToRemove;
		final Set<String> hostsToUpdate;
		if (destinationPath.toFile().exists()) {
			System.out.printf("Parsing destination file '%s'... ", destinationPath);
			wgetHstsKnownHostMap = parseWgetHstsKnownHostsDatabase(destinationPath);
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
			final Path tempPath = createUpToDateWgetHstsTempFile(wgetHstsKnownHostMap.values(), hostsToRemove, hostsToUpdate, entriesToWrite);
			System.out.println("done");

			if (destinationPath.toFile().exists()) {
				System.out.printf("Backing up existing file '%s'... ", destinationPath);
				final Path backupPath = backupExistingWgetHstsFile(destinationPath);
				System.out.printf("-> '%s'%n", backupPath);
			}

			System.out.printf("Updating destination file '%s'... ", destinationPath);
			try {
				Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (final AtomicMoveNotSupportedException e) {
				log.log(Level.FINE, e.toString(), e);
				Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
			}
			System.out.println("done");
		}
	}

	Collection<ChromiumHstsPreloadedEntry> computeEntriesToWrite(final Map<String, ChromiumHstsPreloadedEntry> chromiumHostMap, final Map<String, WgetHstsEntry> wgetHostMap, final Set<String> hostsToUpdate) {
		return chromiumHostMap.values().stream().filter(chromiumHost -> "force-https".equalsIgnoreCase(chromiumHost.getMode()) && (!wgetHostMap.containsKey(chromiumHost.getName()) || hostsToUpdate.contains(chromiumHost.getName()))).collect(Collectors.toList());
	}

	Set<String> computeHostsToUpdate(@NonNull final Map<String, ChromiumHstsPreloadedEntry> chromiumHostMap, @NonNull final Map<String, WgetHstsEntry> wgetHostMap) {
		return wgetHostMap.values().stream().filter(wgetHost -> {
			final ChromiumHstsPreloadedEntry chromiumHost = chromiumHostMap.get(wgetHost.getHostname());
			return chromiumHost != null && (chromiumHost.isIncludeSubdomains() || chromiumHost.isIncludeSubdomainsForPinning()) != wgetHost.isIncludeSubdomains();
		}).map(WgetHstsEntry::getHostname).collect(Collectors.toSet());
	}

	Set<String> computeHostsToRemove(@NonNull final Set<String> chromiumHosts, @NonNull final Set<String> wgetHosts) {
		return wgetHosts.stream().filter(wgetHost -> !chromiumHosts.contains(wgetHost)).collect(Collectors.toSet());
	}

	Map<String, WgetHstsEntry> retrieveWgetHstsPreloadedHosts(final Map<String, WgetHstsEntry> wgetHostMap) {
		return wgetHostMap.values().stream().filter(wgetHost -> wgetHost.getCreated() == Integer.MAX_VALUE && wgetHost.getMaxAge() == 0).collect(Collectors.toMap(WgetHstsEntry::getHostname, Function.identity()));
	}

	SourceFile retrieveSourceFile(@NonNull final String source) throws IOException {
		try {
			final URL url = new URL(source);
			System.out.printf("Downloading '%s'... ", url);
			final URLConnection connection = url.openConnection();
			connection.setRequestProperty("Accept-Encoding", "gzip");
			connection.setRequestProperty("Accept", "application/json,*/*;q=0.9");
			connection.setRequestProperty("Connection", "close");
			log.log(Level.FINE, "{0}", connection.getRequestProperties());
			final Path path;
			try (final InputStream raw = connection.getInputStream(); final InputStream in = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
				path = createChromiumHstsPreloadedJsonTempFile(in);
			}
			System.out.printf("%d kB fetched%n", Files.size(path) / 1024);
			log.log(Level.FINE, "{0}", connection.getHeaderFields());
			return new SourceFile(path, true);
		}
		catch (final MalformedURLException e) {
			log.log(Level.FINE, e.toString(), e);
			return new SourceFile(Paths.get(source), false);
		}
	}

	Path createEmptyWgetHstsTempFile() throws IOException {
		final Path path = Files.createTempFile("wget-hsts-", null);
		return Files.write(path, Arrays.asList("# HSTS 1.0 Known Hosts database for GNU Wget.", "# Edit at your own risk.", "# <hostname>\t<port>\t<incl. subdomains>\t<created>\t<max-age>"), StandardOpenOption.APPEND);
	}

	Path backupExistingWgetHstsFile(@NonNull final Path wgetHstsPath) throws IOException {
		Path backupPath = Paths.get(wgetHstsPath + ".bak.gz");
		for (int i = 1; backupPath.toFile().exists(); i++) {
			backupPath = Paths.get(wgetHstsPath + ".bak." + i + ".gz");
		}
		try (final OutputStream fos = Files.newOutputStream(backupPath); final OutputStream gzos = new GZIPOutputStream(fos)) {
			Files.copy(wgetHstsPath, gzos);
		}
		return backupPath;
	}

	Map<String, ChromiumHstsPreloadedEntry> parseChromiumHstsPreloadedList(@NonNull final Path transportSecurityStateStaticJson) throws IOException {
		final ChromiumHstsPreloadedList root;
		try (final BufferedReader br = Files.newBufferedReader(transportSecurityStateStaticJson)) {
			root = new Gson().fromJson(br, ChromiumHstsPreloadedList.class);
		}
		return root.getEntries().stream().collect(Collectors.toMap(ChromiumHstsPreloadedEntry::getName, Function.identity()));
	}

	Map<String, WgetHstsEntry> parseWgetHstsKnownHostsDatabase(@NonNull final Path wgetHstsFile) throws IOException {
		final Pattern splitPattern = Pattern.compile("[\\t\\s]+");
		try (final Stream<String> lines = Files.lines(wgetHstsFile)) {
			final Stream<String[]> fieldStream = lines.map(String::trim).filter(line -> !line.startsWith("#")).map(splitPattern::split).filter(fields -> fields.length == 5);
			return fieldStream.map(fields -> WgetHstsEntry.builder().hostname(fields[0].trim()).port(Integer.parseInt(fields[1].trim())).includeSubdomains("1".equals(fields[2].trim())).created(Integer.parseInt(fields[3].trim())).maxAge(Integer.parseInt(fields[4].trim())).build()).collect(Collectors.toMap(WgetHstsEntry::getHostname, Function.identity(), (k, v) -> {
				throw new IllegalStateException("Duplicate key " + k);
			}, LinkedHashMap::new));
		}
	}

	Path createChromiumHstsPreloadedJsonTempFile(@NonNull final InputStream in) throws IOException {
		final Path path = Files.createTempFile("hsts-", ".json");
		Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
		path.toFile().deleteOnExit();
		return path;
	}

	private Path createUpToDateWgetHstsTempFile(final Collection<WgetHstsEntry> wgetHstsKnownHosts, final Collection<String> hostsToRemove, final Collection<String> hostsToUpdate, final Collection<ChromiumHstsPreloadedEntry> entriesToWrite) throws IOException {
		final Path tempPath = createEmptyWgetHstsTempFile();
		final Stream<WgetHstsEntry> retained = wgetHstsKnownHosts.stream().filter(entry -> !hostsToRemove.contains(entry.getHostname()) && !hostsToUpdate.contains(entry.getHostname()));
		final Stream<WgetHstsEntry> updated = entriesToWrite.stream().map(entry -> WgetHstsEntry.builder().hostname(entry.getName()).includeSubdomains(entry.isIncludeSubdomains() || entry.isIncludeSubdomainsForPinning()).created(Integer.MAX_VALUE).maxAge(0).build()).sorted((a, b) -> a.getHostname().compareTo(b.getHostname()));
		final Stream<String> linesStream = Stream.concat(retained, updated).map(WgetHstsEntry::toString);
		try (final BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardOpenOption.APPEND)) {
			linesStream.forEachOrdered(line -> writeLine(writer, line));
		}
		catch (final Exception e) {
			Files.deleteIfExists(tempPath);
			throw e;
		}
		return tempPath;
	}

	@SneakyThrows(IOException.class)
	private void writeLine(@NonNull final BufferedWriter writer, @NonNull final String line) {
		writer.write(line);
		writer.newLine();
	}

	@Value
	static class SourceFile {
		Path path;
		boolean temp;
	}
}

@Log
class VersionProvider implements IVersionProvider {

	private static final String BUILD_INFO_FILE_NAME = "/META-INF/build-info.properties";

	@Override
	public String[] getVersion() throws Exception {
		final Properties buildInfo = loadBuildInfo();
		return new String[] { buildInfo.getProperty("project.artifactId") + " v" + buildInfo.getProperty("project.version") };
	}

	static Properties loadBuildInfo() {
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
}
