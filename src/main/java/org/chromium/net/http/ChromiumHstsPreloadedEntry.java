package org.chromium.net.http;

import java.net.URI;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class ChromiumHstsPreloadedEntry {

	@SerializedName("name")
	private String name;

	@SerializedName("policy")
	private String policy;

	@SerializedName("include_subdomains")
	private boolean includeSubdomains;

	@SerializedName("include_subdomains_for_pinning")
	private boolean includeSubdomainsForPinning;

	@SerializedName("mode")
	private String mode;

	@SerializedName("pins")
	private String pins;

	@SerializedName("expect_ct")
	private Boolean expectCt;

	@SerializedName("expect_ct_report_uri")
	private URI expectCtReportUri;

}
