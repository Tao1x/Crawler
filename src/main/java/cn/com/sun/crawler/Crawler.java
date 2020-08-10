package cn.com.sun.crawler;

import cn.com.sun.crawler.entity.VideoMetaData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @Description : 爬虫
 * @Author :sunchao
 * @Date: 2020-07-16 23:32
 */
public class Crawler {

    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private Lock readLock = readWriteLock.readLock();
    private Lock writeLock = readWriteLock.writeLock();

    //1获取首页html
    //2获取首页里面的视频详细地址
    //3获取分享链接html
    //4获取src视频地址
    //5根据下载历史排除已经下载过的
    //6下载
    private Queue<String> homePageQueue = new LinkedBlockingQueue<>();

    private Queue<VideoMetaData> singlePageQueue = new LinkedBlockingQueue<>();

    private Queue<VideoMetaData> sharePageQueue = new LinkedBlockingQueue<>();

    private Queue<VideoMetaData> srcQueue = new LinkedBlockingQueue<>();

    private static Logger logger = LoggerFactory.getLogger(Crawler.class);

    private static ExecutorService executorService;

    static {
        AtomicInteger num = new AtomicInteger(0);
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("download-processor-" + num.incrementAndGet());
            return t;
        };
        executorService = new ThreadPoolExecutor(4, 50, 5, TimeUnit.SECONDS,
            new SynchronousQueue(), threadFactory);
    }

    public static void main(String[] args) throws InterruptedException {
        Crawler crawler = new Crawler();
        // 根据多个指定地址循环获取最新视频
        for (String pageUrl : Config.PAGES) {
            logger.info("crawler page :{} start", pageUrl);
            String pageHtml = HttpClient.getHtmlByHttpClient(pageUrl);
            List<VideoMetaData> videoMetaDataList = crawler.getVideoMetaData(pageHtml);
            // 过滤掉已下载的得到未下载的
            Map<String, String> downloadedMap = crawler.readDownloaded();
            List<VideoMetaData> unDownloadList =
                videoMetaDataList.stream().filter(metaData -> {
                    if (downloadedMap.keySet().contains(metaData.getId())) {
                        logger.info("filter downloaded video:{}", metaData.getTitle());
                        return false;
                    } else {
                        return true;
                    }
                }).collect(Collectors.toList());
            // 根据视频时长从小到大排序
            unDownloadList.sort(Comparator.comparingInt(VideoMetaData::getDuration));
            //logger.info("video ready");
            unDownloadList.forEach(metaData -> logger.info(metaData.toString()));
            for (VideoMetaData metaData : unDownloadList) {
                String singlePageHtml = HttpClient.getHtmlByHttpClient(metaData.getPageUrl());
                String videoUrl = crawler.getVideoUrl(singlePageHtml);
                if ("".equals(videoUrl)) {
                    logger.warn("get {} url by share link failed", metaData.toString());
                    continue;
                }
                metaData.setSrcUrl(videoUrl);
            }
            List<Runnable> downloadTaskList =
                unDownloadList.stream().filter(metaData -> metaData.getSrcUrl() != null &&
                    !metaData.getSrcUrl().isEmpty()).map(metaData -> {
                    Runnable runnable = () -> {
                        if (!crawler.isDownloaded(metaData)) {
                            if (HttpClient.downloadVideoToFs(metaData)) {
                                crawler.record(metaData);
                            }
                        }
                    };
                    return runnable;
                }).collect(Collectors.toList());
            for (Runnable task : downloadTaskList) {
                executorService.submit(task);
            }
            // 每隔3s检测活动线程数量以此来判断是否还有任务执行
            while (true) {
                int activeThreadCount = ((ThreadPoolExecutor) executorService).getActiveCount();
                if (activeThreadCount == 0) {
                    logger.info("page :{} end", pageUrl);
                    break;
                }
                Thread.sleep(3000);
            }
        }
    }

    /**
     * 记录下载成功的文件 未考虑并发执行的同步问题
     *
     * @param metaData
     */
    public void record(VideoMetaData metaData) {
        File recordFile = new File(this.getClass().getResource("downloaded").getPath());
        writeLock.lock();
        try {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(recordFile, true))) {
                bw.write(metaData + "\n");
                bw.flush();
                logger.info("record downloaded file:{}", metaData.getTitle() + ".mp4");
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 是否已下载 考虑并发执行的同步问题
     *
     * @param metaData
     * @return
     */
    public boolean isDownloaded(VideoMetaData metaData) {
        Map<String, String> map = readDownloaded();
        if (map.keySet().contains(metaData.getId())) {
            return true;
        }
        return false;
    }

    /**
     * 读取已下载的文件
     *
     * @return
     */
    private Map<String, String> readDownloaded() {
        Map<String, String> map = new HashMap<>();
        File recordFile = new File(getClass().getResource("downloaded").getPath());
        readLock.lock();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(recordFile))) {
                br.lines().forEach(info -> {
                    String id = "";
                    id = info.substring(0, info.indexOf("|"));
                    map.putIfAbsent(id, info);
                });
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        } finally {
            readLock.unlock();
        }
        return map;
    }


    /**
     * 爬取页面中所有视频信息
     *
     * @param html
     * @return
     */
    public List<VideoMetaData> getVideoMetaData(String html) {
        Document document = Jsoup.parse(html);
        Elements elements = document.select(".well.well-sm");
        List<VideoMetaData> metaDataList = new ArrayList<>();
        for (Element content : elements) {
            VideoMetaData metaData = new VideoMetaData();
            Element a = content.select("a").first();
            // id
            metaData.setId(a.select("div").first().attr("id"));
            // singlePageUrl
            metaData.setPageUrl(a.attr("href"));
            // coverUrl
            metaData.setCoverUrl(a.select("img").first().attr("src"));
            // duration
            String durationStr = a.select(".duration").first().text();
            int minutes = Integer.parseInt(durationStr.split(":")[0]);
            int seconds = Integer.parseInt(durationStr.split(":")[1]);
            //Duration duration = Duration.ofSeconds(minutes * 60 + seconds);
            metaData.setDuration(minutes * 60 + seconds);
            // title
            metaData.setTitle(a.select(".video-title").first().text());
            // author
            String ownText = content.ownText();
            String text = ownText.substring(ownText.lastIndexOf(" 前 ") + 3);
            metaData.setAuthor(text.split(" ")[0]);
            // watchNum
            metaData.setWatchNum(Integer.parseInt(text.split(" ")[1].trim()));
            // storeNum
            metaData.setStoreNum(Integer.parseInt(text.split(" ")[2].trim()));
            metaDataList.add(metaData);
        }
        return metaDataList;
    }

    public String getVideoUrl(String singleVideoPage) {
        Document document = Jsoup.parse(singleVideoPage);
        String videoUrl = "";
        Element source = document.select("#player_one_html5_api source").first();
        if (source != null) {
            videoUrl = source.attr("src");
            logger.info("get video url by page：{}", videoUrl);
        }
        // 分享链接里面取
        if ("".equals(videoUrl)) {
            Element shareLink = document.selectFirst("#linkForm2 #fm-video_link");
            if (shareLink != null) {
                String shareHtml = HttpClient.getHtmlByHttpClient(shareLink.text());
                Element shareDocument = Jsoup.parse(shareHtml);
                videoUrl = shareDocument.selectFirst("source").attr("src");
                logger.info("get video url by share link：{}", videoUrl);
            }
        }
        return videoUrl;
    }
}
