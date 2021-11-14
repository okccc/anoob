package com.okccc.bigdata.flume;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.math.NumberUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;

/**
 * Author: okccc
 * Date: 2020/12/6 19:34
 * Desc: 日志解析工具类
 */
public class LogUtil {

    // 校验日志是否json格式
    public static boolean isJsonFormat(String str) {
        try {
            JSON.parse(str);
            return true;
        } catch (JSONException e) {
//            e.printStackTrace();
            return false;
        }
    }

    // 将字符串参数解析成键值对,类似hive函数str_to_map
    public static HashMap<String, String> strToMap(String args) {
        // orc=grubby&ne=moon&hum=sky&ud=ted
        HashMap<String, String> hashMap = new HashMap<>();
        for (String s : args.split("&")) {
            String[] arr = s.split("=");
            hashMap.put(arr[0], arr[1]);
        }
        return hashMap;
    }

    // url解码
    public static String decode(String str) {
        if (str != null && str.length() > 0) {
            try {
                // java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern
                // url解码,%在url中是特殊字符,要先将单独出现的%替换成编码后的%25,再对整个字符串解码
//            return URLDecoder.decode(str, "utf-8");
                return URLDecoder.decode(str.replaceAll("%(?![0-9a-fA-F]{2})", "%25"), "utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return str;
    }

    // 校验时间戳
    public static boolean isUnixtime(String log) {
        // 1549696569054 | {"cm":{"ln":"-89.2","sv":"V2.0.4","os":"8.2.0"...},"ap":"weather","et":[]}
        String[] content = log.split("\\|");
        // 校验日志长度
        if (content.length != 2) {
            return false;
        }
        // 校验时间戳格式
        return content[0].length() == 13 && NumberUtils.isDigits(content[0]);
    }

    public static void main(String[] args) {
        String str = "{ \"@timestamp\": \"11/Nov/2021:17:26:21 +0800\", \"hostname\": \"prod-bigdata-amp03\", \"remote_addr\": \"10.42.251.112\", \"ip\": \"113.200.212.73\", \"Method\": \"POST\", \"referer\": \"-\", \"request\": \"POST /amplitude HTTP/1.1\", \"request_body\": \"v=3&new_log_flag=true&client=234406d28efbc2041bc1f58a501cad17&e=%5B%7B%22session_id%22%3A1636622228746%2C%22user_properties%22%3A%7B%7D%2C%22language%22%3A%22Chinese%22%2C%22event_type%22%3A%22Soe_Record_Stop%22%2C%22sequence_number%22%3A59950%2C%22user_id%22%3A%22f6fc1a3541c24b5fa03ec3d2ecc77441%22%2C%22country%22%3A%22China%20mainland%22%2C%22api_properties%22%3A%7B%7D%2C%22device_id%22%3A%2235648113-8B81-4F02-B4BB-4460D5DB5405%22%2C%22event_properties%22%3A%7B%22App%22%3A%22JLGL%22%2C%22traceId%22%3A%2216366227725641%22%2C%22Business%22%3A%22GuaEnglish%22%7D%2C%22uuid%22%3A%226EA03AB8-2A5C-4AF6-AB6F-70C9AEBBA65B%22%2C%22device_manufacturer%22%3A%22Apple%22%2C%22version_name%22%3A%2211.10.0%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-ios%22%2C%22version%22%3A%224.10.0%22%7D%2C%22os_name%22%3A%22ios%22%2C%22platform%22%3A%22iOS%22%2C%22event_id%22%3A49617%2C%22carrier%22%3A%22Unknown%22%2C%22timestamp%22%3A1636622778787%2C%22groups%22%3A%7B%7D%2C%22os_version%22%3A%2214.8.1%22%2C%22device_model%22%3A%22iPad13%2C1%22%2C%22group_properties%22%3A%7B%7D%7D%2C%7B%22session_id%22%3A1636622228746%2C%22user_properties%22%3A%7B%7D%2C%22language%22%3A%22Chinese%22%2C%22event_type%22%3A%22QN_Upload_Begin%22%2C%22sequence_number%22%3A59951%2C%22user_id%22%3A%22f6fc1a3541c24b5fa03ec3d2ecc77441%22%2C%22country%22%3A%22China%20mainland%22%2C%22api_properties%22%3A%7B%7D%2C%22device_id%22%3A%2235648113-8B81-4F02-B4BB-4460D5DB5405%22%2C%22event_properties%22%3A%7B%22retry%22%3A%223%22%2C%22traceId%22%3A%2216366227725641%22%2C%22file%22%3A%22%5C%2Fvar%5C%2Fmobile%5C%2FContainers%5C%2FData%5C%2FApplication%5C%2FD5FAD528-ED53-4220-B22D-C1824109F672%5C%2FLibrary%5C%2FCaches%5C%2Fcom.jiliguala.ios.ifly.cache%5C%2Fce2590e405fd20ececae3998851005e8.mp3%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22uuid%22%3A%22C7D65320-F0A8-4381-9A3E-8BD28416926C%22%2C%22device_manufacturer%22%3A%22Apple%22%2C%22version_name%22%3A%2211.10.0%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-ios%22%2C%22version%22%3A%224.10.0%22%7D%2C%22os_name%22%3A%22ios%22%2C%22platform%22%3A%22iOS%22%2C%22event_id%22%3A49618%2C%22carrier%22%3A%22Unknown%22%2C%22timestamp%22%3A1636622778787%2C%22groups%22%3A%7B%7D%2C%22os_version%22%3A%2214.8.1%22%2C%22device_model%22%3A%22iPad13%2C1%22%2C%22group_properties%22%3A%7B%7D%7D%2C%7B%22session_id%22%3A1636622228746%2C%22user_properties%22%3A%7B%7D%2C%22language%22%3A%22Chinese%22%2C%22event_type%22%3A%22Soe_Close_Connect%22%2C%22sequence_number%22%3A59952%2C%22user_id%22%3A%22f6fc1a3541c24b5fa03ec3d2ecc77441%22%2C%22country%22%3A%22China%20mainland%22%2C%22api_properties%22%3A%7B%7D%2C%22device_id%22%3A%2235648113-8B81-4F02-B4BB-4460D5DB5405%22%2C%22event_properties%22%3A%7B%22App%22%3A%22JLGL%22%2C%22traceId%22%3A%2216366227725641%22%2C%22Business%22%3A%22GuaEnglish%22%7D%2C%22uuid%22%3A%22249D2083-093C-4D38-AFAB-A00F503853D5%22%2C%22device_manufacturer%22%3A%22Apple%22%2C%22version_name%22%3A%2211.10.0%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-ios%22%2C%22version%22%3A%224.10.0%22%7D%2C%22os_name%22%3A%22ios%22%2C%22platform%22%3A%22iOS%22%2C%22event_id%22%3A49619%2C%22carrier%22%3A%22Unknown%22%2C%22timestamp%22%3A1636622778965%2C%22groups%22%3A%7B%7D%2C%22os_version%22%3A%2214.8.1%22%2C%22device_model%22%3A%22iPad13%2C1%22%2C%22group_properties%22%3A%7B%7D%7D%2C%7B%22session_id%22%3A1636622228746%2C%22user_properties%22%3A%7B%7D%2C%22language%22%3A%22Chinese%22%2C%22event_type%22%3A%22Soe_On_Result%22%2C%22sequence_number%22%3A59953%2C%22user_id%22%3A%22f6fc1a3541c24b5fa03ec3d2ecc77441%22%2C%22country%22%3A%22China%20mainland%22%2C%22api_properties%22%3A%7B%7D%2C%22device_id%22%3A%2235648113-8B81-4F02-B4BB-4460D5DB5405%22%2C%22event_properties%22%3A%7B%22App%22%3A%22JLGL%22%2C%22traceId%22%3A%2216366227725641%22%2C%22Business%22%3A%22GuaEnglish%22%7D%2C%22uuid%22%3A%22B1E6CB28-74CC-4278-BDD1-A411D1D317BB%22%2C%22device_manufacturer%22%3A%22Apple%22%2C%22version_name%22%3A%2211.10.0%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-ios%22%2C%22version%22%3A%224.10.0%22%7D%2C%22os_name%22%3A%22ios%22%2C%22platform%22%3A%22iOS%22%2C%22event_id%22%3A49620%2C%22carrier%22%3A%22Unknown%22%2C%22timestamp%22%3A1636622778965%2C%22groups%22%3A%7B%7D%2C%22os_version%22%3A%2214.8.1%22%2C%22device_model%22%3A%22iPad13%2C1%22%2C%22group_properties%22%3A%7B%7D%7D%2C%7B%22session_id%22%3A1636622228746%2C%22user_properties%22%3A%7B%7D%2C%22language%22%3A%22Chinese%22%2C%22event_type%22%3A%22QN_Upload_Success%22%2C%22sequence_number%22%3A59954%2C%22user_id%22%3A%22f6fc1a3541c24b5fa03ec3d2ecc77441%22%2C%22country%22%3A%22China%20mainland%22%2C%22api_properties%22%3A%7B%7D%2C%22device_id%22%3A%2235648113-8B81-4F02-B4BB-4460D5DB5405%22%2C%22event_properties%22%3A%7B%22retry%22%3A%223%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%2C%22url%22%3A%22https%3A%5C%2F%5C%2Fqiniucdn.jiliguala.com%5C%2Fprod%5C%2Fupload%5C%2Ffe63f58a9a7142e5922186abfbe50043_20211111052618.mp3%22%2C%22traceId%22%3A%2216366227725641%22%2C%22file%22%3A%22%5C%2Fvar%5C%2Fmobile%5C%2FContainers%5C%2FData%5C%2FApplication%5C%2FD5FAD528-ED53-4220-B22D-C1824109F672%5C%2FLibrary%5C%2FCaches%5C%2Fcom.jiliguala.ios.ifly.cache%5C%2Fce2590e405fd20ececae3998851005e8.mp3%22%7D%2C%22uuid%22%3A%227A114E6F-D89F-4BE5-BDE8-14393DC7746F%22%2C%22device_manufacturer%22%3A%22Apple%22%2C%22version_name%22%3A%2211.10.0%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-ios%22%2C%22version%22%3A%224.10.0%22%7D%2C%22os_name%22%3A%22ios%22%2C%22platform%22%3A%22iOS%22%2C%22event_id%22%3A49621%2C%22carrier%22%3A%22Unknown%22%2C%22timestamp%22%3A1636622779048%2C%22groups%22%3A%7B%7D%2C%22os_version%22%3A%2214.8.1%22%2C%22device_model%22%3A%22iPad13%2C1%22%2C%22group_properties%22%3A%7B%7D%7D%2C%7B%22session_id%22%3A1636622228746%2C%22user_properties%22%3A%7B%7D%2C%22language%22%3A%22Chinese%22%2C%22event_type%22%3A%22Recording%20Evaluation%20Score%22%2C%22sequence_number%22%3A59955%2C%22user_id%22%3A%22f6fc1a3541c24b5fa03ec3d2ecc77441%22%2C%22country%22%3A%22China%20mainland%22%2C%22api_properties%22%3A%7B%7D%2C%22device_id%22%3A%2235648113-8B81-4F02-B4BB-4460D5DB5405%22%2C%22event_properties%22%3A%7B%22App%22%3A%22JLGL%22%2C%22ExerciseID%22%3A%22NA%22%2C%22SublessonID%22%3A%22K2GEF05405%22%2C%22SectionType%22%3A%22speak-new%22%2C%22Score%22%3A34%2C%22Answer%22%3A%22Big%20K%22%2C%22GameID%22%3A%22L2U06W1D4Q5%22%2C%22VolumeLow%22%3Afalse%2C%22Success%22%3Afalse%2C%22PageID%22%3A%22NA%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22Error%22%3A%22NA%22%2C%22TrueScore%22%3A19%2C%22SentenceID%22%3A%22NA%22%2C%22SublessonType%22%3A%22speakingpracticeK%22%2C%22EvaluationCount%22%3A6066%2C%22Version%22%3A%222.5%22%2C%22RecordingTimes%22%3A1%2C%22SectionID%22%3A%22L2U06W1D4Q5sec02%22%2C%22Volume%22%3A%5B31%2C30%2C30%2C31%2C29%2C30%2C31%2C33%2C31%2C30%2C29%2C31%2C31%2C45%2C49%2C51%2C54%2C53%2C53%2C52%2C45%2C39%2C35%2C32%2C32%2C32%2C34%2C30%2C49%2C53%2C58%2C48%2C43%2C43%2C45%2C49%2C51%2C53%2C58%2C54%2C51%2C51%2C47%2C43%2C54%2C51%2C45%2C51%2C53%2C63%2C70%2C65%2C60%2C54%2C49%2C44%2C44%2C42%2C59%2C60%2C59%2C58%2C57%2C51%2C42%2C37%2C37%2C48%2C62%2C65%2C64%2C67%2C62%2C60%2C60%2C59%2C55%2C61%2C65%2C65%2C67%2C66%2C56%2C55%2C47%2C44%2C57%2C59%2C58%2C54%2C54%2C57%2C60%2C62%2C61%2C63%2C64%2C60%2C57%2C57%2C58%2C54%2C53%2C50%2C47%2C45%2C43%2C42%2C36%2C36%2C42%2C62%2C69%2C66%2C59%2C54%2C51%2C46%2C42%2C39%2C37%2C37%2C34%2C37%2C35%2C35%2C37%2C38%2C47%2C53%2C44%2C42%2C48%2C45%2C50%2C57%2C52%2C43%2C42%2C49%2C48%2C42%2C35%2C45%2C50%2C49%2C45%2C44%2C41%2C39%2C36%2C38%2C39%2C38%2C40%2C41%2C34%2C39%2C37%2C37%2C37%2C38%2C38%2C38%5D%7D%2C%22uuid%22%3A%2258035868-8F16-4EA2-A03E-EBBC3B7D0C25%22%2C%22device_manufacturer%22%3A%22Apple%22%2C%22version_name%22%3A%2211.10.0%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-ios%22%2C%22version%22%3A%224.10.0%22%7D%2C%22os_name%22%3A%22ios%22%2C%22platform%22%3A%22iOS%22%2C%22event_id%22%3A49622%2C%22carrier%22%3A%22Unknown%22%2C%22timestamp%22%3A1636622779050%2C%22groups%22%3A%7B%7D%2C%22os_version%22%3A%2214.8.1%22%2C%22device_model%22%3A%22iPad13%2C1%22%2C%22group_properties%22%3A%7B%7D%7D%2C%7B%22session_id%22%3A1636622228746%2C%22user_properties%22%3A%7B%7D%2C%22language%22%3A%22Chinese%22%2C%22event_type%22%3A%22Recording%20Evaluation%20Url%22%2C%22sequence_number%22%3A59956%2C%22user_id%22%3A%22f6fc1a3541c24b5fa03ec3d2ecc77441%22%2C%22country%22%3A%22China%20mainland%22%2C%22api_properties%22%3A%7B%7D%2C%22device_id%22%3A%2235648113-8B81-4F02-B4BB-4460D5DB5405%22%2C%22event_properties%22%3A%7B%22SublessonID%22%3A%22K2GEF05405%22%2C%22Version%22%3A%222.5%22%2C%22App%22%3A%22JLGL%22%2C%22ExerciseID%22%3A%22%22%2C%22SublessonType%22%3A%22speakingpracticeK%22%2C%22PageID%22%3A%22NA%22%2C%22GameID%22%3A%22L2U06W1D4Q5%22%2C%22SectionID%22%3A%22L2U06W1D4Q5sec02_1%22%2C%22SentenceID%22%3A%22NA%22%2C%22SectionType%22%3A%22speak-new%22%2C%22AudioURL%22%3A%22https%3A%5C%2F%5C%2Fqiniucdn.jiliguala.com%5C%2Fprod%5C%2Fupload%5C%2Ffe63f58a9a7142e5922186abfbe50043_20211111052618.mp3%22%2C%22Business%22%3A%22GuaEnglish%22%7D%2C%22uuid%22%3A%22AACCA2F9-964B-4D7F-9E35-00C92BB38B52%22%2C%22device_manufacturer%22%3A%22Apple%22%2C%22version_name%22%3A%2211.10.0%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-ios%22%2C%22version%22%3A%224.10.0%22%7D%2C%22os_name%22%3A%22ios%22%2C%22platform%22%3A%22iOS%22%2C%22event_id%22%3A49623%2C%22carrier%22%3A%22Unknown%22%2C%22timestamp%22%3A1636622779050%2C%22groups%22%3A%7B%7D%2C%22os_version%22%3A%2214.8.1%22%2C%22device_model%22%3A%22iPad13%2C1%22%2C%22group_properties%22%3A%7B%7D%7D%2C%7B%22session_id%22%3A1636622228746%2C%22user_properties%22%3A%7B%7D%2C%22language%22%3A%22Chinese%22%2C%22event_type%22%3A%22Recording%20Radio%20URL%20Receive%22%2C%22sequence_number%22%3A59957%2C%22user_id%22%3A%22f6fc1a3541c24b5fa03ec3d2ecc77441%22%2C%22country%22%3A%22China%20mainland%22%2C%22api_properties%22%3A%7B%7D%2C%22device_id%22%3A%2235648113-8B81-4F02-B4BB-4460D5DB5405%22%2C%22event_properties%22%3A%7B%22Source%22%3A%22SpeakLesson%22%2C%22ApiRet%22%3A%22%7B%5C%22status%5C%22%3A200%2C%5C%22taskId%5C%22%3A%5C%227c76ef20-f4ef-4acc-b240-7466b7f24fa4%5C%22%2C%5C%22data%5C%22%3A%7B%5C%22suggestedScore%5C%22%3A19%2C%5C%22realScore%5C%22%3A19%2C%5C%22mode%5C%22%3A%5C%22en.sent.score%5C%22%2C%5C%22sessionId%5C%22%3A%5C%2216366227725641%5C%22%2C%5C%22seqId%5C%22%3A1%2C%5C%22displayScore%5C%22%3A34%2C%5C%22tag%5C%22%3A%5C%22%E5%85%88%E5%A3%B0_%E8%8B%B1%E6%96%87%E5%8F%A5%E5%AD%90%E8%AF%84%E6%B5%8B_%E9%80%9A%E7%94%A84%5C%22%2C%5C%22refText%5C%22%3A%5C%22Big%20K%5C%22%2C%5C%22words%5C%22%3A%5B%7B%5C%22word%5C%22%3A%5C%22big%5C%22%2C%5C%22reachBoundary%5C%22%3A1%2C%5C%22phoneInfos%5C%22%3A%5B%7B%5C%22phone%5C%22%3A%5C%22b%5C%22%2C%5C%22phoneAccuracy%5C%22%3A0%7D%2C%7B%5C%22phone%5C%22%3A%5C%22%C9%AA%5C%22%2C%5C%22phoneAccuracy%5C%22%3A0%7D%2C%7B%5C%22phone%5C%22%3A%5C%22g%5C%22%2C%5C%22phoneAccuracy%5C%22%3A0%7D%5D%2C%5C%22suggestedScore%5C%22%3A15%2C%5C%22humanVoice%5C%22%3A0%2C%5C%22predictedScore%5C%22%3A0%7D%2C%7B%5C%22word%5C%22%3A%5C%22k%5C%22%2C%5C%22reachBoundary%5C%22%3A1%2C%5C%22phoneInfos%5C%22%3A%5B%7B%5C%22phone%5C%22%3A%5C%22k%5C%22%2C%5C%22phoneAccuracy%5C%22%3A19%7D%2C%7B%5C%22phone%5C%22%3A%5C%22e%5C%22%2C%5C%22phoneAccuracy%5C%22%3A73%7D%5D%2C%5C%22suggestedScore%5C%22%3A52%2C%5C%22humanVoice%5C%22%3A0%2C%5C%22predictedScore%5C%22%3A37%7D%5D%2C%5C%22version%5C%22%3A%5C%220.0.80.2021.9.30.13%3A18%3A24%5C%22%7D%2C%5C%22msgType%5C%22%3A%5C%22Recognize%5C%22%2C%5C%22sessionId%5C%22%3A%5C%2216366227725641%5C%22%2C%5C%22text%5C%22%3A%5C%22Big%20K%5C%22%2C%5C%22flag%5C%22%3A1%7D%22%2C%22SectionID%22%3A%22L2U06W1D4Q5sec02%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22AnswerContent%22%3A%22Big%20K%22%2C%22AudioURL%22%3A%22https%3A%5C%2F%5C%2Fqiniucdn.jiliguala.com%5C%2Fprod%5C%2Fupload%5C%2Ffe63f58a9a7142e5922186abfbe50043_20211111052618.mp3%22%2C%22App%22%3A%22JLGL%22%7D%2C%22uuid%22%3A%22DAE95DCF-BBAB-4263-8AE3-4134AC154AAB%22%2C%22device_manufacturer%22%3A%22Apple%22%2C%22version_name%22%3A%2211.10.0%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-ios%22%2C%22version%22%3A%224.10.0%22%7D%2C%22os_name%22%3A%22ios%22%2C%22platform%22%3A%22iOS%22%2C%22event_id%22%3A49624%2C%22carrier%22%3A%22Unknown%22%2C%22timestamp%22%3A1636622779050%2C%22groups%22%3A%7B%7D%2C%22os_version%22%3A%2214.8.1%22%2C%22device_model%22%3A%22iPad13%2C1%22%2C%22group_properties%22%3A%7B%7D%7D%5D&upload_time=1636622781646&checksum=f1ea601f9e5ce01a7d6c51ddc633c399\", \"status\": \"200\", \"bytes\": \"17\", \"agent\": \"niuwa/11.10.0.2110272024 CFNetwork/1240.0.4 Darwin/20.6.0\", \"x_forwarded\": \"113.200.212.73\"}";
        System.out.println(isJsonFormat(str));  // true
        System.out.println(isJsonFormat(decode(str)));  // false,对整个字符串解码可能会导致数据格式变成非json
        // 只对字符串内部的编码部分进行解码
        JSONObject jsonObject = JSON.parseObject(str);
        String newBody = decode(jsonObject.getString("request_body"));
        jsonObject.put("request_body", newBody);
        System.out.println(isJsonFormat(jsonObject.toJSONString()));  // true
        // 获取内部字段
        HashMap<String, String> hashMap = strToMap(newBody);
        String version = JSON.parseArray(hashMap.get("e")).getJSONObject(0).getString("version_name");
        System.out.println(version);  // 11.10.0
    }
}
