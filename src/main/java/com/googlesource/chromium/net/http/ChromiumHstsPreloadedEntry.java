package com.googlesource.chromium.net.http;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class ChromiumHstsPreloadedEntry {

	@SerializedName("name")
	@Expose
	private String name;

	@SerializedName("policy")
	@Expose
	private String policy;

	@SerializedName("include_subdomains")
	@Expose
	private boolean includeSubdomains;

	@SerializedName("include_subdomains_for_pinning")
	@Expose
	private boolean includeSubdomainsForPinning;

	@SerializedName("mode")
	@Expose
	private String mode;

	@SerializedName("pins")
	@Expose
	private String pins;

	@SerializedName("expect_ct")
	@Expose
	private Boolean expectCt;

	@SerializedName("expect_ct_report_uri")
	@Expose
	private String expectCtReportUri;

}
