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
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @Description : 爬虫
 * @Author :sunchao
 * @Date: 2020-07-16 23:32
 */
public class Crawler {

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

    public static void main(String[] args) {
        Crawler crawler = new Crawler();
        String homePage = HttpClient.getHtmlByHttpClient(CrawlerConstants.HOME_PAGE);
        List<VideoMetaData> videoMetaDataList = crawler.getVideoMetaData(homePage);

        videoMetaDataList.sort(Comparator.comparingInt(VideoMetaData::getDuration));
        for (VideoMetaData metaData : videoMetaDataList) {
            String singlePageHtml = HttpClient.getHtmlByHttpClient(metaData.getPageUrl());
            String videoUrl = crawler.getVideoUrl(singlePageHtml);
            metaData.setSrcUrl(videoUrl);
        }
        videoMetaDataList.forEach(metaData -> logger.info(metaData.toString()));
        for (VideoMetaData metaData : videoMetaDataList) {
            if (null == metaData.getSrcUrl() || metaData.getSrcUrl().isEmpty()) {
                logger.warn(metaData.toString() + "have no url");
                continue;
            }
            if (!crawler.isDownloaded(metaData)) {
                if (HttpClient.downloadToFs(metaData)) {
                    crawler.record(metaData);
                }
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
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(recordFile, true))) {
            bw.write(metaData + "\t\n");
            bw.flush();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 是否已下载 考虑并发执行的同步问题
     *
     * @param metaData
     * @return
     */
    public boolean isDownloaded(VideoMetaData metaData) {
        Map<String, String> map = new HashMap<>();
        //URL downloadedUrl = getClass().getResource("downloaded");
        File recordFile = new File(getClass().getResource("downloaded").getPath());
        try (BufferedReader br = new BufferedReader(new FileReader(recordFile))) {
            br.lines().forEach(info -> {
                String id = "";
                if (info.split("||").length == 2) {
                    id = info.split("||")[0];
                } else {
                    RuntimeException exception = new RuntimeException("parse " + info + " failed");
                    logger.error(exception.getMessage(), exception);
                }
                map.putIfAbsent(id, info);
            });
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        if (map.keySet().contains(metaData.getId())) {
            return true;
        }
        return false;
    }


    public List<VideoMetaData> getVideoMetaData(String html) {
        Document document = Jsoup.parse(html);
        Elements elements = document.select(".well");
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
            String shareHtml = HttpClient.getHtmlByHttpClient(shareLink.text());
            Element shareDocument = Jsoup.parse(shareHtml);
            videoUrl = shareDocument.selectFirst("source").attr("src");
            logger.info("get video url by share link：{}", videoUrl);
        }
        return videoUrl;
    }

}
