package cn.com.sun.crawler;

/**
 * @Description : 常量
 * @Author :sunchao
 * @Date: 2020-07-18 00:43
 */
public class Config {

    public static final String HTTP_PROXY_HOSTNAME = "localhost";

    public static final int HTTP_PROXY_PORT = 1080;


    public static final String HOME_PAGE = "http://www.91porn.com/index.php";

    public static final String TOP_HOT = "http://www.91porn.com/v.php?category=top&viewtype=basic";

    public static final String TOP_STORE = "http://www.91porn.com/v.php?category=tf&viewtype=basic";

    public static final String HOT_CURRENT = "http://www.91porn.com/v.php?category=hot&viewtype=basic";

    public static final String TOP_HOT_LAST_MONTH = "http://www.91porn.com/v.php?category=top&m=-1&viewtype=basic";

    public static final String[] PAGES = new String[]{HOME_PAGE, TOP_HOT, TOP_STORE, HOT_CURRENT, TOP_HOT_LAST_MONTH};

    public static final int CONNECT_TIMEOUT = 10000;

    public static final int READ_TIMEOUT = 5000;

    public static final int READ_FILE_TIMEOUT = 60000;

    public static final String FILE_SAVE_PATH = "F://Download//crawler//";

}
