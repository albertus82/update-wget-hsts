package org.chromium.net.http;

import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class ChromiumHstsPreloadedPinset {

	@SerializedName("name")
	@Expose
	private String name;

	@SerializedName("static_spki_hashes")
	@Expose
	private List<String> staticSpkiHashes = null;

	@SerializedName("report_uri")
	@Expose
	private String reportUri;

	@SerializedName("bad_static_spki_hashes")
	@Expose
	private List<String> badStaticSpkiHashes = null;

}
