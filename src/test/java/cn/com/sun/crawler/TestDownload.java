package cn.com.sun.crawler;

import cn.com.sun.crawler.entity.VideoMetaData;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Comparator;
import java.util.List;

/**
 * @Description : test
 * @Author :sunchao
 * @Date: 2020-07-19 14:17
 */
public class TestDownload {


    private static final Logger logger = LoggerFactory.getLogger(TestDownload.class);

    @Test
    public void isDownloaded() {
        Crawler crawler = new Crawler();
        crawler.isDownloaded(null);
    }

    @Test
    public void download() {
        VideoMetaData videoMetaData = new VideoMetaData();
        videoMetaData.setSrcUrl("http://198.255.82.92//mp43/383619.mp4?st=Jk-m3BYdUA4r5Z1xyi8KvQ&e=1595199101");
        videoMetaData.setTitle("hahahaha");
        HttpClient.downloadToFs(videoMetaData);
    }

    @Test
    public void record() {
        Crawler crawler = new Crawler();
        String homePage = HttpClient.getHtmlByHttpClient(CrawlerConstants.HOME_PAGE);
        List<VideoMetaData> videoMetaDataList = crawler.getVideoMetaData(homePage);

        videoMetaDataList.sort(Comparator.comparingInt(VideoMetaData::getDuration));
        for (VideoMetaData metaData : videoMetaDataList) {
            String singlePageHtml = HttpClient.getHtmlByHttpClient(metaData.getPageUrl());
            String videoUrl = crawler.getVideoUrl(singlePageHtml);
            metaData.setSrcUrl(videoUrl);
            String filePath = CrawlerConstants.FILE_SAVE_PATH + File.separator + metaData.getTitle() + ".mp4";
            logger.info(filePath);
            if (new File(filePath).exists()) {
                crawler.record(metaData);
            }
        }

//        VideoMetaData videoMetaData = new VideoMetaData();
//        videoMetaData.setSrcUrl("http://198.255.82.92//mp43/383619.mp4?st=Jk-m3BYdUA4r5Z1xyi8KvQ&e=1595199101");
//        //videoMetaData.setId();
//        videoMetaData.setTitle("hahahaha");
    }
}

