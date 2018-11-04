package com.googlesource.chromium.net.http;

import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class ChromiumHstsPreloadedList {

	@SerializedName("pinsets")
	@Expose
	private List<ChromiumHstsPreloadedPinset> pinsets = null;

	@SerializedName("entries")
	@Expose
	private List<ChromiumHstsPreloadedEntry> entries = null;

}
