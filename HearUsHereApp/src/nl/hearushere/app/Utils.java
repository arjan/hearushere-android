package nl.hearushere.app;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;

public class Utils {

	public static String stringHash(String mUrl) {
		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] bytes = digest.digest(mUrl.getBytes());
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02X", b));
		}
		return sb.toString();
	}

	public static File getCacheDir(Context context) {
		// Get the directory for the app's private pictures directory.
		File file = new File(
				context.getCacheDir(),
				Constants.CACHE_DIR);
		if (!file.mkdirs()) {
			Log.e("Utils", "Directory not created! " + file.toString());
		}
		return file;
	}

	public static String getUserAgentString(Context context) {
		String version = "?";
		try {
			PackageInfo manager = context.getPackageManager().getPackageInfo(
					context.getPackageName(), 0);
			version = manager.versionName;
		} catch (NameNotFoundException e) {
			// Handle exception
		}

		return context.getPackageName() + "/" + version + " (Android; "
				+ Build.VERSION.RELEASE + ")";
	}
}