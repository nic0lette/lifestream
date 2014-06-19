/**
    LifeStream - Instant Photo Sharing
    Copyright (C) 2014 Kayateia

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.kayateia.lifestream;

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import android.util.Log;

// http://stackoverflow.com/questions/4966910/androidhow-to-upload-mp3-file-to-http-server
public class HttpMultipartUpload {
	static final String LOG_TAG = "LifeStream/HttpMultipartUpload";
	static String lineEnd = "\r\n";
	static String twoHyphens = "--";
	static String boundary = "AaB03x87yxdkjnxvi7";

	// All operations should time out within 30 seconds.
	static int TIMEOUT = 30000;

	static void LoadKey(KeyStore trusted, int resId, android.content.Context context) throws Exception {
		// Get the raw resource, which contains the keystore with
		// your trusted certificates (root and any intermediate certs)
		InputStream in = context.getResources().openRawResource(resId);
		try {
			// Initialize the keystore with the provided trusted certificates
			// Also provide the password of the keystore
			trusted.load(in, "storepass".toCharArray());
		} finally {
			in.close();
		}
	}

	// http://nelenkov.blogspot.com/2011/12/using-custom-certificate-trust-store-on.html
	static public HttpURLConnection GetConnection(URL url, android.content.Context context) {
		try {
			// Skip all the mess for HTTP connections.
			if (url.getProtocol().equalsIgnoreCase("http"))
				return (HttpURLConnection)url.openConnection();

			// Get an instance of the Bouncy Castle KeyStore format
			/*KeyStore trusted = KeyStore.getInstance("BKS");
			LoadKey(trusted, R.raw.kayateia, context);
			LoadKey(trusted, R.raw.isisview, context);

			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trusted);

			SSLContext sslCtx = SSLContext.getInstance("TLS");
			sslCtx.init(null, tmf.getTrustManagers(), null);*/

			HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
			//urlConnection.setSSLSocketFactory(sslCtx.getSocketFactory());

			return urlConnection;
		} catch (Exception exc) {
			Log.e(LOG_TAG, "Unable to create SSL client: " + exc);
			return null;
		}
	}

	public static HttpURLConnection Download(URL url, HashMap<String, String> parameters, android.content.Context context)
			throws IOException
	{
		HttpURLConnection conn = HttpMultipartUpload.GetConnection(url, context);
		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setUseCaches(false);
		conn.setRequestMethod("POST");
		conn.setConnectTimeout(TIMEOUT);
		conn.setReadTimeout(TIMEOUT);
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

		DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		if (parameters != null) {
			for (String name : parameters.keySet()) {
				if (!first)
					sb.append("&");
				else
					first = false;
				sb.append(name);
				sb.append("=");
				sb.append(URLEncoder.encode(parameters.get(name)));
			}
		}
		wr.writeBytes(sb.toString());
		wr.flush();
		wr.close();

		return conn;
	}

	public static String DownloadString(URL url, HashMap<String, String> parameters, android.content.Context context)
			throws IOException
	{
		HttpURLConnection conn = Download(url, parameters, context);
		try {
			InputStream is = conn.getInputStream();
			BufferedReader rd = new BufferedReader(new InputStreamReader(is));
			String line;
			StringBuffer sb = new StringBuffer();
			while ((line  = rd.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			rd.close();

			return sb.toString();
		} finally {
			if (conn != null)
				conn.disconnect();
		}
	}

	public static void DownloadFile(URL url, File outputFile, HashMap<String, String> parameters, android.content.Context context)
			throws IOException
	{
		HttpURLConnection conn = Download(url, parameters, context);
		FileOutputStream outStream = null;
		try {
			InputStream is = conn.getInputStream();
			outStream = new FileOutputStream(outputFile);
			byte[] buffer = new byte[4096];
			int readCnt;
			while ((readCnt = is.read(buffer)) != -1)
				outStream.write(buffer, 0, readCnt);
			is.close();
			outStream.close();
		} finally {
			if (outStream != null)
				outStream.close();
			if (conn != null)
				conn.disconnect();
		}
	}

	public static String Upload(URL url, File file, String fileParameterName, HashMap<String, String> parameters,
		android.content.Context context)
		throws IOException
	{
		HttpURLConnection conn = null;
		DataOutputStream dos = null;
		DataInputStream dis = null;
		FileInputStream fileInputStream = null;

		byte[] buffer;
		int maxBufferSize = 20 * 1024;
		try {
			//------------------ CLIENT REQUEST
			fileInputStream = new FileInputStream(file);
			
			// Really quick sanity check
			final long fileSize = file.length();
			if (fileSize <= 0) {
				// Okay... well, nothing to do here >_>
				return null;
			}

			// open a URL connection to the Servlet
			// Open a HTTP connection to the URL
			conn = GetConnection(url, context);
			// Allow Inputs
			conn.setDoInput(true);
			// Allow Outputs
			conn.setDoOutput(true);
			// Don't use a cached copy.
			conn.setUseCaches(false);
			// Use a post method.
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
			conn.setConnectTimeout(TIMEOUT);
			conn.setReadTimeout(TIMEOUT);

			dos = new DataOutputStream(conn.getOutputStream());

			dos.writeBytes(twoHyphens + boundary + lineEnd);
			dos.writeBytes("Content-Disposition: form-data; name=\"" + fileParameterName
					+ "\"; filename=\"" + file.toString() + "\"" + lineEnd);
			dos.writeBytes("Content-Type: text/xml" + lineEnd);
			dos.writeBytes(lineEnd);

			// create a buffer of maximum size
			buffer = new byte[Math.min((int) fileSize, maxBufferSize)];
			int length;
			// read file and write it into form...
			while ((length = fileInputStream.read(buffer)) != -1) {
				dos.write(buffer, 0, length);
				
				// This shouldn't happen, but let's just check anyway... you know, for fun
				if (length <= 0) {
					Log.w(LOG_TAG, "fileInputStream.read() returned " + length);
					break;
				}
			}

			for (String name : parameters.keySet()) {
				dos.writeBytes(lineEnd);
				dos.writeBytes(twoHyphens + boundary + lineEnd);
				dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + lineEnd);
				dos.writeBytes(lineEnd);
				dos.writeBytes(parameters.get(name));
			}

			// send multipart form data necessary after file data...
			dos.writeBytes(lineEnd);
			dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
			dos.flush();
		} finally {
			if (fileInputStream != null) fileInputStream.close();
			if (dos != null) dos.close();
		}

		//------------------ read the SERVER RESPONSE
		try {
			dis = new DataInputStream(conn.getInputStream());
			final BufferedReader br = new BufferedReader(new InputStreamReader(dis));
			final StringBuilder response = new StringBuilder();

			String line;
			while ((line = br.readLine()) != null) {
				response.append(line).append('\n');
			}

			int responseCode = conn.getResponseCode(); 
			if (responseCode != 200)
				throw new IOException("Bad server response: " + responseCode + " " + response);

			return response.toString();
		} finally {
			if (dis != null) dis.close();
		}
	}
}
