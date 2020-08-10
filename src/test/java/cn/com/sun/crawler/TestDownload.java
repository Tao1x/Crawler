package cn.com.sun.crawler;

import cn.com.sun.crawler.entity.VideoMetaData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

    @ParameterizedTest()
    @ValueSource(strings = {"http://www.91porn.com/view_video.php?viewkey=d0c8e3017f126335cadb"})
    public void downloadFromViewKey(String url) {
        Crawler crawler = new Crawler();
        VideoMetaData metaData = new VideoMetaData();
        metaData.setPageUrl(url);
        metaData.setTitle(getVideoTitle(url));
        //metaData.setPageUrl(url);
        String singleVideoPage = HttpClient.getHtmlByHttpClient(url);
        String srcUrl = crawler.getVideoUrl(singleVideoPage);
        metaData.setSrcUrl(srcUrl);
        String id = "playvthumb_" + srcUrl.substring(srcUrl.indexOf(".mp4") - 6, srcUrl.indexOf(
            ".mp4"));
        metaData.setId(id);
        if (!crawler.isDownloaded(metaData)) {
            if (HttpClient.downloadVideoToFs(metaData)) crawler.record(metaData);
        }
    }

//    @ParameterizedTest()
//    @ValueSource(strings = {"http://138.128.27.218//mp43/385091
//    .mp4?st=FI67biK6BhbtNaWsRGrCHQ&e=1595838194&f=65d3EX1l2VUNycyzDeWsgWGiamuWh9yupf9N1DdzQixXI
//    +JH3kbv7LCwDgo"})
//    public void downloadFromUrl(String url) {
//        Crawler crawler = new Crawler();
//        VideoMetaData metaData = new VideoMetaData();
//        metaData.setTitle("Test");
//        //metaData.setPageUrl(url);
//        //String singleVideoPage = HttpClient.getHtmlByHttpClient(url);
//        //String srcUrl = crawler.getVideoUrl(singleVideoPage);
//        metaData.setSrcUrl(url);
//        String id = "playvthumb_" + url.substring(url.indexOf(".mp4") - 6, url.indexOf(".mp4"));
//        metaData.setId(id);
//        if (!crawler.isDownloaded(metaData)) {
//            if (HttpClient.downloadVideoToFs(metaData)) crawler.record(metaData);
//        }
//    }

    @Test
    public void record() {
        Crawler crawler = new Crawler();
        String homePage = HttpClient.getHtmlByHttpClient(Config.HOME_PAGE);
        List<VideoMetaData> videoMetaDataList = crawler.getVideoMetaData(homePage);
        for (VideoMetaData metaData : videoMetaDataList) {
            String filePath = Config.FILE_SAVE_PATH + metaData.getTitle() + ".mp4";
            logger.info(filePath);
            if (new File(filePath).exists()) {
                crawler.record(metaData);
            }
        }
    }

    private String getVideoTitle(String url) {
        String html = HttpClient.getHtmlByHttpClient(url);
        Document document = Jsoup.parse(html);
        String title =
            document.selectFirst("#videodetails").selectFirst(".login_register_header").text();
        return title;
    }

}

