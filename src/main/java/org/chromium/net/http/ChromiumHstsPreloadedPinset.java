package org.chromium.net.http;

import java.util.List;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class ChromiumHstsPreloadedPinset {

	@SerializedName("name")
	private String name;

	@SerializedName("static_spki_hashes")
	private List<String> staticSpkiHashes = null;

	@SerializedName("report_uri")
	private String reportUri;

	@SerializedName("bad_static_spki_hashes")
	private List<String> badStaticSpkiHashes = null;

}
