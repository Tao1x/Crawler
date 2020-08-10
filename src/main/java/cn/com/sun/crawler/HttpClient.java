package cn.com.sun.crawler;

import cn.com.sun.crawler.entity.VideoMetaData;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        HttpHost proxy = new HttpHost(Config.HTTP_PROXY_HOSTNAME, Config.HTTP_PROXY_PORT);
        requestConfig =
            RequestConfig.custom().setProxy(proxy).setConnectTimeout(Config.CONNECT_TIMEOUT)
                .setSocketTimeout(Config.READ_TIMEOUT).build();
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
     * @param metaData
     * @return
     */
    public static boolean downloadVideoToFs(VideoMetaData metaData) {
        // 请求
        HttpGet request = createHttpGetRequest(metaData.getSrcUrl());
        request.setConfig(RequestConfig.copy(requestConfig).setSocketTimeout(Config.READ_FILE_TIMEOUT).build());
        // 创建对应日期的文件夹
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        File dir = new File(Config.FILE_SAVE_PATH + date);
        if (!dir.exists()) {
            dir.mkdir();
        }
        // 下载
        String fileName = filterBannedChar(metaData.getTitle());
        String filePath = dir.getPath() + File.separator + fileName + ".mp4";
        File file = new File(filePath);
        LocalTime startTime;
        try (CloseableHttpResponse response = httpClient.execute(request); FileOutputStream out =
            new FileOutputStream(file); InputStream in =
                 response.getEntity().getContent()) {
            int bytes = Integer.parseInt(response.getFirstHeader("Content-Length").getValue());
            float fileSize = ((float) bytes) / (1024 * 1024);
            logger.info("download file start: name:{},size:{} {},url:{}", fileName, bytes + "byte"
                , fileSize + "m", metaData.getSrcUrl());
            startTime = LocalTime.now();
            copy(in, out);
        } catch (IOException e) {
            //下载失败之后忽略异常继续执行
            logger.error("download file failed: {}，name:{}", e.getMessage(), fileName);
            return false;
        }
        LocalTime endTime = LocalTime.now();
        String costTime = Duration.between(startTime, endTime).getSeconds() + "s";

        logger.info("download file success: name:{},cost time:{}", fileName, costTime);
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
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147" +
            ".89 Safari/537.36");
        return request;
    }

    private static HttpURLConnection getConnection(String urlStr) {
        HttpURLConnection con = null;
        URL url;
        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                new InetSocketAddress(Config.HTTP_PROXY_HOSTNAME, Config.HTTP_PROXY_PORT));
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
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147" +
                ".89 Safari/537.36");
            con.setConnectTimeout(Config.CONNECT_TIMEOUT);
            con.setReadTimeout(Config.READ_TIMEOUT);
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
        StringBuilder ipBuilder = new StringBuilder("");
        for (int i = 0; i < 4; i++) {
            ipBuilder.append(random.nextInt(255)).append(".");
        }
        String ip = ipBuilder.deleteCharAt(ipBuilder.length() - 1).toString();
        logger.info("request ip:{}", ip);
        return ip;
    }

    private static String filterBannedChar(String string) {
        String regEx = "[<>/\\|\"*?]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(string);
        return m.replaceAll("").trim();
    }
}
