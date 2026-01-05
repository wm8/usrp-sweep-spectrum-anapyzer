package jspectrumanalyzer;

import java.net.URLEncoder;

public class Version
{
	public static final String version	= "0.4.0-BETA";

	public static String getHeaderVersion() {
		String versionHeaders;
		try {
			versionHeaders = URLEncoder.encode(version, "UTF-8");
		} catch (Exception e) {
			versionHeaders = version;
		}
		return versionHeaders;
	}
}
