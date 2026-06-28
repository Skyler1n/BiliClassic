package tv.biliclassic.util;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class NetWorkUtil {

    public static final String USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36";

    public static final ArrayList webHeaders = new ArrayList();

    static {
        // 先放 Cookie（如果有的话）
        String cookie = SharedPreferencesUtil.getString("cookies", "");
        if (cookie != null && cookie.length() > 0) {
            webHeaders.add("Cookie");
            webHeaders.add(cookie);
        }

        webHeaders.add("User-Agent");
        webHeaders.add(USER_AGENT_WEB);
        webHeaders.add("Accept");
        webHeaders.add("application/json, text/plain, */*");
        webHeaders.add("Accept-Language");
        webHeaders.add("zh-CN,zh;q=0.9,en;q=0.8");
        webHeaders.add("Origin");
        webHeaders.add("https://www.bilibili.com");
        webHeaders.add("Referer");
        webHeaders.add("https://www.bilibili.com/");
        webHeaders.add("Connection");
        webHeaders.add("keep-alive");
        webHeaders.add("Sec-Ch-Ua");
        webHeaders.add("\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");
        webHeaders.add("Sec-Ch-Ua-Platform");
        webHeaders.add("\"Windows\"");
        webHeaders.add("Sec-Ch-Ua-Mobile");
        webHeaders.add("?0");
    }

    private static final X509TrustManager TRUST_ALL_CERTS = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    public static final HostnameVerifier TRUST_ALL_HOSTNAMES = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) { return true; }
    };

    public static SSLSocketFactory getTrustAllSSLSocketFactory() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new X509TrustManager[]{TRUST_ALL_CERTS}, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            Log.e("NetWorkUtil", "创建 TrustAll SSLSocketFactory 失败: " + e.getMessage());
            return null;
        }
    }

    public static JSONObject getJson(String url) throws IOException, JSONException {
        String response = get(url);
        if (response == null || response.length() == 0) {
            throw new JSONException("在访问 " + url + " 时返回数据为空");
        }
        return new JSONObject(response);
    }

    public static JSONObject getJson(String url, ArrayList headers) throws IOException, JSONException {
        String response = get(url, headers);
        if (response == null || response.length() == 0) {
            throw new JSONException("在访问 " + url + " 时返回数据为空");
        }
        return new JSONObject(response);
    }

    public static String get(String url) throws IOException {
        return get(url, webHeaders);
    }

    public static String get(String url, ArrayList headers) throws IOException {
        HttpURLConnection conn = null;
        java.io.CharArrayWriter caw = null;
        BufferedReader reader = null;
        try {
            URL requestUrl = new URL(url);
            conn = (HttpURLConnection) requestUrl.openConnection();

            if (url.startsWith("https")) {
                SSLSocketFactory sslFactory = getTrustAllSSLSocketFactory();
                if (sslFactory != null && conn instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
                    ((HttpsURLConnection) conn).setHostnameVerifier(TRUST_ALL_HOSTNAMES);
                }
            }

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setInstanceFollowRedirects(false);

            // ========== 先设置默认请求头 ==========
            conn.setRequestProperty("User-Agent", USER_AGENT_WEB);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.setRequestProperty("Origin", "https://www.bilibili.com");

            // ========== 如果 headers 为空，用 webHeaders ==========
            if (headers == null) {
                headers = webHeaders;
            }

            // ========== 确保 Cookie 是最新的 ==========
            String cookie = SharedPreferencesUtil.getString("cookies", "");
            boolean hasCookie = false;
            for (int i = 0; i < headers.size(); i += 2) {
                if (i + 1 < headers.size()) {
                    String key = (String) headers.get(i);
                    if ("Cookie".equalsIgnoreCase(key)) {
                        headers.set(i + 1, cookie);
                        hasCookie = true;
                        break;
                    }
                }
            }
            if (!hasCookie && cookie != null && cookie.length() > 0) {
                headers.add("Cookie");
                headers.add(cookie);
            }

            // ========== 应用 headers ==========
            for (int i = 0; i < headers.size(); i += 2) {
                if (i + 1 < headers.size()) {
                    String key = (String) headers.get(i);
                    String value = (String) headers.get(i + 1);
                    if (key != null && value != null) {
                        conn.setRequestProperty(key, value);
                    }
                }
            }

            conn.connect();

            int responseCode = conn.getResponseCode();

            if (responseCode == 301 || responseCode == 302 || responseCode == 307) {
                String location = conn.getHeaderField("Location");
                String setCookie = conn.getHeaderField("Set-Cookie");
                if (setCookie != null && setCookie.length() > 0) {
                    String cookiePure = extractCookiePairs(setCookie);
                    if (cookiePure != null && cookiePure.length() > 0) {
                        String existing = getCookieString();
                        if (existing != null && existing.length() > 0) {
                            setCookieString(existing + "; " + cookiePure);
                            SharedPreferencesUtil.putString("cookies", existing + "; " + cookiePure);
                        } else {
                            setCookieString(cookiePure);
                            SharedPreferencesUtil.putString("cookies", cookiePure);
                        }
                        String mid = getInfoFromCookie("DedeUserID", cookiePure);
                        if (mid != null && mid.length() > 0) {
                            try {
                                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                            } catch (NumberFormatException e) {}
                        }
                    }
                }
                if (location != null && location.length() > 0) {
                    conn.disconnect();
                    return get(location, headers);
                }
            }

            InputStream is;
            if (responseCode >= 400) {
                is = conn.getErrorStream();
                Log.e("NetWorkUtil", "HTTP错误: " + responseCode + ", URL=" + url);
            } else {
                is = conn.getInputStream();
            }

            if (is == null) {
                return "";
            }

            caw = new java.io.CharArrayWriter();
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            char[] buffer = new char[4096];
            int len;
            while ((len = reader.read(buffer, 0, buffer.length)) != -1) {
                caw.write(buffer, 0, len);
            }
            reader.close();
            is.close();

            String result = caw.toString();
            caw.close();

            String setCookie = conn.getHeaderField("Set-Cookie");
            if (setCookie != null && setCookie.length() > 0) {
                String cookiePure = extractCookiePairs(setCookie);
                if (cookiePure != null && cookiePure.length() > 0) {
                    String existing = getCookieString();
                    if (existing != null && existing.length() > 0) {
                        setCookieString(existing + "; " + cookiePure);
                        SharedPreferencesUtil.putString("cookies", existing + "; " + cookiePure);
                    } else {
                        setCookieString(cookiePure);
                        SharedPreferencesUtil.putString("cookies", cookiePure);
                    }
                    String mid = getInfoFromCookie("DedeUserID", cookiePure);
                    if (mid != null && mid.length() > 0) {
                        try {
                            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                        } catch (NumberFormatException e) {}
                    }
                }
            }

            return result;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("请求异常: " + e.toString());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception e) {}
            }
            if (caw != null) {
                try { caw.close(); } catch (Exception e) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String extractCookiePairs(String setCookie) {
        if (setCookie == null) return "";
        StringBuffer sb = new StringBuffer();
        String[] parts = setCookie.split(";");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.indexOf("=") != -1 && !part.startsWith("Path") && !part.startsWith("Domain") &&
                    !part.startsWith("Expires") && !part.startsWith("Secure") &&
                    !part.startsWith("HttpOnly") && !part.startsWith("SameSite")) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(part);
            }
        }
        return sb.toString();
    }

    public static String post(String url, String data, List headers) throws IOException {
        return post(url, data, headers, "application/x-www-form-urlencoded");
    }

    public static String postJson(String url, String data, List headers) throws IOException {
        return post(url, data, headers, "application/json");
    }

    public static String post(String url, String data) throws IOException {
        return post(url, data, webHeaders);
    }

    public static String post(String url, String data, List headers, String contentType) throws IOException {
        HttpURLConnection conn = null;
        java.io.CharArrayWriter caw = null;
        BufferedReader reader = null;
        try {
            URL requestUrl = new URL(url);
            conn = (HttpURLConnection) requestUrl.openConnection();

            if (url.startsWith("https")) {
                SSLSocketFactory sslFactory = getTrustAllSSLSocketFactory();
                if (sslFactory != null && conn instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
                    ((HttpsURLConnection) conn).setHostnameVerifier(TRUST_ALL_HOSTNAMES);
                }
            }

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);

            conn.setRequestProperty("Content-Type", contentType + "; charset=utf-8");

            // ========== 先设置默认请求头 ==========
            conn.setRequestProperty("User-Agent", USER_AGENT_WEB);
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.setRequestProperty("Origin", "https://www.bilibili.com");

            // ========== 确保 Cookie 是最新的 ==========
            String cookie = SharedPreferencesUtil.getString("cookies", "");
            boolean hasCookie = false;
            if (headers != null) {
                for (int i = 0; i < headers.size(); i += 2) {
                    if (i + 1 < headers.size()) {
                        String key = (String) headers.get(i);
                        if ("Cookie".equalsIgnoreCase(key)) {
                            headers.set(i + 1, cookie);
                            hasCookie = true;
                            break;
                        }
                    }
                }
                if (!hasCookie && cookie != null && cookie.length() > 0) {
                    headers.add("Cookie");
                    headers.add(cookie);
                }
            } else {
                headers = webHeaders;
                // 同样检查 webHeaders 里有没有 Cookie
                for (int i = 0; i < headers.size(); i += 2) {
                    if (i + 1 < headers.size()) {
                        String key = (String) headers.get(i);
                        if ("Cookie".equalsIgnoreCase(key)) {
                            headers.set(i + 1, cookie);
                            hasCookie = true;
                            break;
                        }
                    }
                }
                if (!hasCookie && cookie != null && cookie.length() > 0) {
                    headers.add("Cookie");
                    headers.add(cookie);
                }
            }

            // ========== 应用 headers ==========
            for (int i = 0; i < headers.size(); i += 2) {
                if (i + 1 < headers.size()) {
                    String key = (String) headers.get(i);
                    String value = (String) headers.get(i + 1);
                    if (key != null && value != null) {
                        conn.setRequestProperty(key, value);
                    }
                }
            }

            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();

            if (responseCode == 301 || responseCode == 302 || responseCode == 307) {
                String location = conn.getHeaderField("Location");
                String setCookie = conn.getHeaderField("Set-Cookie");
                if (setCookie != null && setCookie.length() > 0) {
                    String cookiePure = extractCookiePairs(setCookie);
                    if (cookiePure != null && cookiePure.length() > 0) {
                        String existing = getCookieString();
                        if (existing != null && existing.length() > 0) {
                            setCookieString(existing + "; " + cookiePure);
                            SharedPreferencesUtil.putString("cookies", existing + "; " + cookiePure);
                        } else {
                            setCookieString(cookiePure);
                            SharedPreferencesUtil.putString("cookies", cookiePure);
                        }
                        String mid = getInfoFromCookie("DedeUserID", cookiePure);
                        if (mid != null && mid.length() > 0) {
                            try {
                                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                            } catch (NumberFormatException e) {}
                        }
                    }
                }
                if (location != null && location.length() > 0) {
                    conn.disconnect();
                    return post(location, data, headers, contentType);
                }
            }

            InputStream is = conn.getInputStream();
            if (is == null) {
                return "";
            }

            caw = new java.io.CharArrayWriter();
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            char[] buffer = new char[4096];
            int len;
            while ((len = reader.read(buffer, 0, buffer.length)) != -1) {
                caw.write(buffer, 0, len);
            }
            reader.close();
            is.close();

            String result = caw.toString();
            caw.close();

            String setCookie = conn.getHeaderField("Set-Cookie");
            if (setCookie != null && setCookie.length() > 0) {
                String cookiePure = extractCookiePairs(setCookie);
                if (cookiePure != null && cookiePure.length() > 0) {
                    String existing = getCookieString();
                    if (existing != null && existing.length() > 0) {
                        setCookieString(existing + "; " + cookiePure);
                        SharedPreferencesUtil.putString("cookies", existing + "; " + cookiePure);
                    } else {
                        setCookieString(cookiePure);
                        SharedPreferencesUtil.putString("cookies", cookiePure);
                    }
                    String mid = getInfoFromCookie("DedeUserID", cookiePure);
                    if (mid != null && mid.length() > 0) {
                        try {
                            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                        } catch (NumberFormatException e) {}
                    }
                }
            }

            return result;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("请求异常: " + e.toString());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception e) {}
            }
            if (caw != null) {
                try { caw.close(); } catch (Exception e) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static byte[] readStream(InputStream inStream) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        outStream.close();
        inStream.close();
        return outStream.toByteArray();
    }

    public static String getInfoFromCookie(String name, String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return "";
        }
        String[] cookies = cookie.split("; ");
        for (int i = 0; i < cookies.length; i++) {
            String c = cookies[i];
            if (c.startsWith(name + "=")) {
                return c.substring(name.length() + 1);
            }
        }
        return "";
    }

    public static String getCookieString() {
        for (int i = 0; i < webHeaders.size(); i += 2) {
            if (((String) webHeaders.get(i)).equals("Cookie")) {
                return (String) webHeaders.get(i + 1);
            }
        }
        return "";
    }

    public static void setCookieString(String cookie) {
        for (int i = 0; i < webHeaders.size(); i += 2) {
            if (((String) webHeaders.get(i)).equals("Cookie")) {
                webHeaders.set(i + 1, cookie);
                return;
            }
        }
        webHeaders.add("Cookie");
        webHeaders.add(cookie);
    }

    public static void refreshHeaders() {
        String cookie = SharedPreferencesUtil.getString("cookies", "");
        setCookieString(cookie);
    }

    public static class FormData {
        private final Map data;
        private boolean isUrlParam;

        public FormData() {
            data = new HashMap();
        }

        public FormData remove(String key) {
            data.remove(key);
            return this;
        }

        public FormData put(String key, Object value) {
            data.put(key, String.valueOf(value));
            return this;
        }

        public FormData setUrlParam(boolean isUrlParam) {
            this.isUrlParam = isUrlParam;
            return this;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            if (isUrlParam) {
                sb.append("?");
            }
            try {
                for (Object o : data.keySet()) {
                    String key = (String) o;
                    if (sb.length() > (isUrlParam ? 1 : 0)) {
                        sb.append("&");
                    }
                    sb.append(URLEncoder.encode(key, "UTF-8"));
                    sb.append("=");
                    sb.append(URLEncoder.encode((String) data.get(key), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e.getMessage());
            }
            return sb.toString();
        }
    }
}