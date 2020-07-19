package cn.com.sun.crawler;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Random;
import java.util.zip.GZIPInputStream;

/**
 * @Description : HTTP客户端工具
 * @Author :sunchao
 * @Date: 2020-07-18 00:34
 */
public class HttpClient {

    public static final Logger logger = LoggerFactory.getLogger(HttpClient.class);

    private static CloseableHttpClient httpClient;

    private static RequestConfig requestConfig;

    static {
        PoolingHttpClientConnectionManager conManager = new PoolingHttpClientConnectionManager();
        httpClient = HttpClients.custom().setConnectionManager(conManager).build();
        //发送Get请求
        HttpHost proxy = new HttpHost(CrawlerConstants.HTTP_PROXY_HOSTNAME, CrawlerConstants.HTTP_PROXY_PORT);
        requestConfig = RequestConfig.custom().setProxy(proxy).setConnectTimeout(CrawlerConstants.CONNECT_TIMEOUT)
            .setSocketTimeout(CrawlerConstants.READ_TIMEOUT).build();
    }

    /**
     * 使用HttpURLConnection获取指定url的返回内容
     *
     * @param urlStr
     * @return
     */
    public static String getHtml(String urlStr) {
        String html = "";
        logger.info("request:{} ", urlStr);
        try (InputStream in = getConnection(urlStr).getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            GZIPInputStream gzipIn = new GZIPInputStream(in);
            copy(gzipIn, out);
            html = out.toString();
            logger.debug("response content:{} ", html);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return html;
    }

    /**
     * 使用HttpClient获取指定url的返回
     *
     * @param urlStr
     * @return
     */
    public static String getHtmlByHttpClient(String urlStr) {
        String html = "";
        //发送Get请求
        HttpGet request = createHttpGetRequest(urlStr);
        logger.info("request:{} ", urlStr);
        for (int tryCount = 1; tryCount <= 10; tryCount++) {
            logger.info("try count：" + tryCount);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                // 从响应模型中获取响应实体
                HttpEntity responseEntity = response.getEntity();
                logger.info("response status:{} ", response.getStatusLine());
                if (responseEntity != null) {
                    html = EntityUtils.toString(responseEntity);
                    //logger.debug("response content:{} ", html);
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
                continue;
            }
            logger.info("request success");
            break;
        }
        return html;
    }

    /**
     * 通过http方式将远程资源下载到本地文件
     *
     * @param resourceUrl
     * @param filePath
     * @return
     */
    public static boolean downloadToFs(String resourceUrl, String filePath) {
        HttpGet request = createHttpGetRequest(resourceUrl);
        request.setConfig(RequestConfig.copy(requestConfig).setSocketTimeout(CrawlerConstants.READ_FILE_TIMEOUT).build());
        File file = new File(filePath);
        try (CloseableHttpResponse response = httpClient.execute(request); FileOutputStream out = new FileOutputStream(file); InputStream in =
            response.getEntity().getContent()) {
            //logger.info(response.getFirstHeader("Content-Length").getValue());
            float fileSize = Integer.parseInt(response.getFirstHeader("Content-Length").getValue()) / (1024 * 1024);
            logger.info("file size:{}", fileSize + "M");
            if (file.exists()) {
                logger.info("file:{} exist", filePath);
                return false;
            }
            logger.info("download start: {}", resourceUrl);
            copy(in, out);
        } catch (IOException e) {
            logger.error("download failed: {}", e.getMessage());
            return false;
        }
        logger.info("download success");
        return true;
    }

    private static HttpGet createHttpGetRequest(String urlStr) {
        HttpGet request = new HttpGet(urlStr);
        request.setConfig(requestConfig);
        request.setHeader("Host", request.getURI().getHost());
        request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,zh-TW;q=0.7");
        request.setHeader("Accept-Encoding", "gzip");
        request.setHeader("Referer", urlStr);
        request.setHeader("X-Forwarded-For", randomIp());
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147" +
            ".89 Safari/537.36");
        return request;
    }

    private static HttpURLConnection getConnection(String urlStr) {
        HttpURLConnection con = null;
        URL url;
        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(CrawlerConstants.HTTP_PROXY_HOSTNAME, CrawlerConstants.HTTP_PROXY_PORT));
            url = new URL(urlStr);
            con = (HttpURLConnection) url.openConnection(proxy);
            System.out.println(url.getHost());
            // 请求的目标Host
            con.setRequestProperty("Host", url.getHost());
            // 客户端可理解的语言
            con.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,zh-TW;q=0.7");
            // 客户端可理解的数据压缩方式
            con.setRequestProperty("Accept-Encoding", "gzip");
            // 当前请求页面的来源页面的地址
            con.setRequestProperty("Referer", urlStr);
            String ip = randomIp();
            System.out.println(ip);
            con.setRequestProperty("X-Forwarded-For", ip);
            // 模拟浏览器访问
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147" +
                ".89 Safari/537.36");
            con.setConnectTimeout(CrawlerConstants.CONNECT_TIMEOUT);
            con.setReadTimeout(CrawlerConstants.READ_TIMEOUT);
            con.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return con;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] bytes = new byte[4096];
        int count = 0;
        while ((count = in.read(bytes)) != -1) {
            out.write(bytes, 0, count);
        }
        out.flush();
    }

    private static String randomIp() {
        Random random = new Random();
        StringBuilder ip = new StringBuilder("");
        for (int i = 0; i < 4; i++) {
            ip.append(random.nextInt(255)).append(".");
        }
        return ip.deleteCharAt(ip.length() - 1).toString();
    }
}
