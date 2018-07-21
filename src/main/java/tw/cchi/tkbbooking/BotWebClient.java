package tw.cchi.tkbbooking;

import javax.net.ssl.*;
import java.io.*;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;

public class BotWebClient {
    public static boolean PRINT_REQUEST = false;

    private static List<String> cookies;

    static {
        disableSslVerification();
    }

    public BotWebClient() {
        // Make sure cookies is turn on
        CookieHandler.setDefault(new CookieManager());
    }

    public String sendGet(String url) {
        return this.sendGet(url, new HashMap<>());
    }

    public String sendGet(String url, HashMap<String, String> getParams) {
        if (url.substring(url.length() - 1).equals("/")) {
            url = url.substring(0, url.length() - 1);
        }
        url += this.buildParamStr(getParams);
        HttpURLConnection conn;
        if (url.startsWith("https://"))
            conn = getHttpsUrlConnection(url);
        else
            conn = getHttpUrlConnection(url);

        try {
            // default is GET
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);

            // Acts like a browser
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", Config.USER_AGENT);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.6,en;q=0.4");
            conn.setRequestProperty("Connection", "keep-alive");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (cookies != null) {
                for (String cookie : cookies) {
                    conn.addRequestProperty("Cookie", cookie.split(";", 1)[0]);
                }
            }
            if (PRINT_REQUEST) {
                int responseCode = conn.getResponseCode();
                System.out.println("\nSending 'GET' to : " + url);
                System.out.println("Response Code : " + responseCode);
                System.out.println();
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Get the response cookies
            cookies = conn.getHeaderFields().get("Set-Cookie");
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public String sendPost(String url, HashMap<String, String> postParams) throws Exception {
        return sendPost(url, postParams, new HashMap<>());
    }

    public String sendPost(String url, HashMap<String, String> postParams, HashMap<String, String> reqProperties) throws Exception {
        final String[] allowedReqProperties = {"Host", "Origin", "Referer"};

        HttpURLConnection conn;
        String postString = this.buildParamStr(postParams);

        if (url.startsWith("https://"))
            conn = getHttpsUrlConnection(url);
        else
            conn = getHttpUrlConnection(url);

        if (conn == null)
            return null;

        // Acts like a browser
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", Config.USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.6,en;q=0.4");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", Integer.toString(postString.length()));

        for (String reqProperty : allowedReqProperties) {
            if (reqProperties.containsKey(reqProperty))
                conn.setRequestProperty(reqProperty, reqProperties.get(reqProperty));
        }

        if (cookies != null) {
            for (String cookie : cookies) {
                conn.addRequestProperty("Cookie", cookie.split(";", 1)[0]);
            }
        }

        conn.setDoOutput(true);
        conn.setDoInput(true);

        // Send post request
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(postString);
        wr.flush();
        wr.close();

        // Get the response cookies
        cookies = conn.getHeaderFields().get("Set-Cookie");

        int responseCode = conn.getResponseCode();
        if (PRINT_REQUEST) {
            System.out.println("\nSending 'POST' to: " + url);
            System.out.println("Post parameters : " + postString);
            System.out.println("Response Code : " + responseCode);
            System.out.println();
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }

    public void saveStream(String url, String outFile) throws Exception {
        InputStream in = null;
        FileOutputStream out = null;
        // Create the file and dirs if not existed
        File file = new File(outFile);
        file.getParentFile().mkdirs();
        file.createNewFile();

        try {
            in = getHttpsUrlConnection(url).getInputStream();
            out = new FileOutputStream(outFile);
            int c;
            byte[] b = new byte[1024];
            while ((c = in.read(b)) != -1)
                out.write(b, 0, c);
        } finally {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
        }
    }

    private HttpURLConnection getHttpUrlConnection(String url) {
        try {
            return (HttpURLConnection) new URL(url).openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private HttpsURLConnection getHttpsUrlConnection(String url) {
        try {
            return (HttpsURLConnection) new URL(url).openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String buildParamStr(HashMap<String, String> params) {
        // build parameters list
        StringBuilder result = new StringBuilder();
        for (Object key : params.keySet()) {
            if (result.length() == 0) {
                result.append(key).append("=").append(params.get(key));
            } else {
                result.append("&").append(key).append("=").append(params.get(key));
            }
        }
        return result.toString();
    }

    private static void disableSslVerification() {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }
}
