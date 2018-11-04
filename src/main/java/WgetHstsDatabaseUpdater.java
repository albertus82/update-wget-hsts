import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.chromium.net.http.ChromiumHstsPreloadedEntry;
import org.chromium.net.http.ChromiumHstsPreloadedList;
import org.gnu.wget.WgetHstsDatabaseEntry;

import com.google.gson.Gson;

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

	private void execute(final String destination, String source) throws IOException {
		final File hstsPreloadedListFile = retrieveSourceFile(source);
		System.out.printf("Parsing source file '%s'... ", hstsPreloadedListFile);
		final Map<String, ChromiumHstsPreloadedEntry> hstsPreloadedMap = parseHstsPreloadedList(hstsPreloadedListFile);
		System.out.printf("%d entries found.%n", hstsPreloadedMap.size());

		final File wgetHstsFile = new File(destination);
		final Map<String, WgetHstsDatabaseEntry> existingEntryMap;
		final Set<String> hostsToRemove;
		if (!wgetHstsFile.exists()) {
			existingEntryMap = Collections.emptyMap();
			hostsToRemove = Collections.emptySet();
		}
		else {
			System.out.printf("Parsing destination file '%s'... ", wgetHstsFile);
			existingEntryMap = parseWgetHstsFile(wgetHstsFile);
			System.out.printf("%d entries found.%n", existingEntryMap.size());

			System.out.print("Computing entries to remove... ");
			hostsToRemove = existingEntryMap.values().parallelStream().filter(entry -> entry.getCreated() == Integer.MAX_VALUE && entry.getMaxAge() == 0 && !hstsPreloadedMap.containsKey(entry.getHostname())).map(WgetHstsDatabaseEntry::getHostname).collect(Collectors.toSet());
			System.out.println(hostsToRemove.isEmpty() ? "none." : hostsToRemove.size());
		}

		System.out.print("Computing entries to add... ");
		final Collection<WgetHstsDatabaseEntry> entriesToAdd = hstsPreloadedMap.values().parallelStream().filter(entry -> "force-https".equalsIgnoreCase(entry.getMode()) && !existingEntryMap.containsKey(entry.getName())).map(oe -> {
			final WgetHstsDatabaseEntry ne = new WgetHstsDatabaseEntry();
			ne.setHostname(oe.getName());
			ne.setInclSubdomains(oe.isIncludeSubdomains() || oe.isIncludeSubdomainsForPinning());
			ne.setCreated(Integer.MAX_VALUE);
			ne.setMaxAge(0);
			return ne;
		}).collect(Collectors.toList());
		System.out.println(entriesToAdd.isEmpty() ? "none." : entriesToAdd.size());

		if (!entriesToAdd.isEmpty() || !hostsToRemove.isEmpty()) {
			if (wgetHstsFile.exists()) {
				System.out.printf("Backing up existing file '%s'... ", wgetHstsFile);
				final File backupFile = backupWgetHstsFile(wgetHstsFile);
				System.out.printf("created backup file '%s'.%n", backupFile);
			}
			System.out.print("Collecting entries to write... ");
			final Collection<String> lines = Stream.concat(existingEntryMap.values().stream().filter(entry -> !hostsToRemove.contains(entry.getHostname())), entriesToAdd.parallelStream().sorted((o1, o2) -> o1.getHostname().compareTo(o2.getHostname()))).map(entry -> String.format("%s\t%d\t%d\t%d\t%d", entry.getHostname(), entry.getPort(), entry.isInclSubdomains() ? 1 : 0, entry.getCreated(), entry.getMaxAge())).collect(Collectors.toList());
			System.out.println(lines.size());
			System.out.printf("Updating destination file '%s'... ", wgetHstsFile);
			final File tempFile = createTemporaryWgetHstsFile(wgetHstsFile);
			Files.write(tempFile.toPath(), lines, StandardOpenOption.APPEND);
			Files.move(tempFile.toPath(), wgetHstsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			System.out.println("done.");
		}
		else {
			System.out.println("No entries to write.");
		}
	}

	private File retrieveSourceFile(final String source) throws IOException {
		try {
			final URL url = new URL(source);
			System.out.printf("Downloading '%s'... ", source);
			final URLConnection connection = url.openConnection();
			connection.setRequestProperty("Accept-Encoding", "gzip");
			connection.setRequestProperty("Accept", "application/json,*/*;q=0.9");
			final File file;
			try (final InputStream raw = connection.getInputStream(); final InputStream is = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
				file = createJsonFile(is, "hsts-preload.json");
			}
			System.out.printf("done, written file '%s' (%d kB).%n", file, file.length() / 1024);
			return file;
		}
		catch (final MalformedURLException e) {
			log.log(Level.FINE, e.toString(), e);
			return new File(source);
		}
	}

	private File createTemporaryWgetHstsFile(final File wgetHstsFile) throws IOException {
		File tempFile = new File(wgetHstsFile.getPath() + ".tmp");
		int i = 1;
		while (tempFile.exists()) {
			tempFile = new File(wgetHstsFile.getPath() + ".tmp." + i++);
		}
		return Files.write(tempFile.toPath(), Arrays.asList("# HSTS 1.0 Known Hosts database for GNU Wget.", "# Edit at your own risk.", "# <hostname>\t<port>\t<incl. subdomains>\t<created>\t<max-age>"), StandardOpenOption.CREATE_NEW).toFile();
	}

	private File backupWgetHstsFile(final File wgetHstsFile) throws IOException {
		File backupFile = new File(wgetHstsFile.getPath() + ".bak");
		int i = 1;
		while (backupFile.exists()) {
			backupFile = new File(wgetHstsFile.getPath() + ".bak." + i++);
		}
		return Files.copy(wgetHstsFile.toPath(), backupFile.toPath()).toFile();
	}

	private Map<String, ChromiumHstsPreloadedEntry> parseHstsPreloadedList(final File hstsPreloadedListFile) throws IOException {
		final ChromiumHstsPreloadedList root;
		try (final FileReader fr = new FileReader(hstsPreloadedListFile); final BufferedReader br = new BufferedReader(fr)) {
			root = new Gson().fromJson(br, ChromiumHstsPreloadedList.class);
		}
		return root.getEntries().parallelStream().collect(Collectors.toMap(ChromiumHstsPreloadedEntry::getName, entry -> entry));
	}

	private Map<String, WgetHstsDatabaseEntry> parseWgetHstsFile(final File wgetHstsFile) throws IOException {
		try (final Stream<String> lines = Files.lines(wgetHstsFile.toPath())) {
			return lines.map(String::trim).filter(line -> !line.startsWith("#")).map(line -> line.split("[\\t\\s]+")).filter(array -> array.length == 5).map(array -> {
				final WgetHstsDatabaseEntry entry = new WgetHstsDatabaseEntry();
				entry.setHostname(array[0].trim());
				entry.setPort(Integer.parseInt(array[1].trim()));
				entry.setInclSubdomains("1".equals(array[2].trim()));
				entry.setCreated(Integer.parseInt(array[3].trim()));
				entry.setMaxAge(Integer.parseInt(array[4].trim()));
				return entry;
			}).collect(Collectors.toMap(WgetHstsDatabaseEntry::getHostname, entry -> entry));
		}
	}

	private File createJsonFile(final InputStream in, final String fileName) throws IOException {
		File file = new File(fileName);
		int i = 1;
		while (file.exists()) {
			file = new File(fileName + "." + i++);
		}
		Files.copy(in, file.toPath());
		return file;
	}
}
