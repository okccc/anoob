package com.okccc.util;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: okccc
 * @Date: 2022/1/24 14:22:56
 * @Desc: 发送http请求的工具类
 */
public class HttpUtil {

    // 创建HttpClient客户端对象,模拟浏览器
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    // cookie：由于http协议是无状态的,服务器无法识别多个请求是否由同一个用户发起,网站服务器为了辨别用户身份和进行session跟踪,
    // 会在response中返回一段cookie值存储在浏览器上,cookie可以保持登录信息到用户下次与服务器的会话,浏览器存储的cookie一般不超过4k
    private static String cookie = null;
    private static String result = null;
    private static CloseableHttpResponse response = null;

    public static String get() {
        // 创建get请求对象
        HttpGet httpGet = new HttpGet("http://www.gov.cn/premier/2017-03/16/content_5177940.htm");
        // 添加请求头
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)");
        try {
            // 发送get请求
            response = httpClient.execute(httpGet);
            // 获取响应内容
            System.out.println(response.getStatusLine());  // HTTP/1.1 200 OK
            result = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(response);
        }
        // 返回结果
        return result;  // json数据很好处理,html页面得用Jsoup解析,类似python的xpath和BeautifulSoup
    }

    public static String post() {
        // 创建post请求对象
        HttpPost httpPost = new HttpPost("http://fanyi.youdao.com/translate?smartresult=dict&smartresult=rule");
        // 添加请求头
        httpPost.addHeader("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)");
        // 添加请求体
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("i", "hello"));
        params.add(new BasicNameValuePair("doctype", "json"));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Consts.UTF_8);
        httpPost.setEntity(entity);
        try {
            // 发送post请求
            response = httpClient.execute(httpPost);
            // 获取响应内容
            result = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 返回结果
        return result;  // {"type":"EN2ZH_CN","errorCode":0,"elapsedTime":0,"translateResult":[[{"src":"hello","tgt":"你好"}]]}
    }

    public static String login(String envType) {
        // 创建post请求对象(登录页面)
        HttpPost httpPost = new HttpPost("http://apollo.xxx.com/signin");
        // 添加请求头
        httpPost.addHeader("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)");
        // 添加请求体
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("username", "xxx"));
        params.add(new BasicNameValuePair("password", "xxx"));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, Consts.UTF_8);
        httpPost.setEntity(entity);
        try {
            // 发送post请求
            response = httpClient.execute(httpPost);
            // 获取服务器返回的cookie信息,可通过F12开发者工具或者fiddler抓包
            Header header = response.getFirstHeader("Set-Cookie");
            HeaderElement element = header.getElements()[0];
            cookie = element.toString().split(";")[0];
            System.out.println(cookie);  // JSESSIONID=53E8E1F003F836D9492EE232A432FE1A
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(response);
        }

        // 创建get请求对象(详情页面)
        HttpGet httpGet = new HttpGet("http://apollo.xxx.com/apps/bdp_db_conn_config/envs/" + envType + "/clusters/default/namespaces");
        // 添加请求头
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)");
        httpGet.addHeader("Cookie", "NG_TRANSLATE_LANG_KEY=zh-CN; " + cookie);
        try {
            // 发送get请求
            response = httpClient.execute(httpGet);
            // 获取响应内容
            result = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(response);
        }
        // 返回结果
        return result;
    }

    public static void close(CloseableHttpResponse response) {
        // 释放资源
        if (response != null) {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println(get());
//        System.out.println(post());
//        System.out.println(login("PRO"));
    }
}
