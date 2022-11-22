package com.dig.common.utils;


import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: hhz
 * @Date: 2022/6/22 10:13
 */
public class CountryMapUtils {

    /**
     * 根据文件路径读取文件
     *
     * @param filePath 文件路径
     * @return Map<String, String>
     */
    public static ConcurrentHashMap readMapFile(String filePath) throws IOException {
        BufferedReader reader = null;
        FileInputStream fileInputStream = new FileInputStream(filePath);
        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, "UTF-8");
        reader = new BufferedReader(inputStreamReader);
        String tempString = null;
        ConcurrentHashMap map = new ConcurrentHashMap<>();
        while ((tempString = reader.readLine()) != null) {
            String[] s = tempString.split("\\|");
            for (int i = 0; i < s.length; i += 2) {
                map.put(s[i], s[i + 1]);
            }
        }
        return map;
    }

}
