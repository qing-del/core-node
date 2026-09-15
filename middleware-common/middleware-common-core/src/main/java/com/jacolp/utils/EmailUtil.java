package com.jacolp.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class EmailUtil {
    
    /**
     * 邮箱正则表达式模式
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    /**
     * 支持的邮箱域名列表
     */
    private static final Set<String> SUPPORTED_EMAIL_DOMAINS = new HashSet<>(Arrays.asList(
        // 国际主流邮箱
        "gmail.com",
        "yahoo.com",
        "outlook.com",
        "hotmail.com",
        "aol.com",
        "icloud.com",
        "mail.com",
        "protonmail.com",
        "zoho.com",
        "yandex.com",
        
        // 国内主流邮箱
        "qq.com",
        "163.com",
        "126.com",
        "sina.com",
        "sina.cn",
        "sohu.com",
        "foxmail.com",
        "139.com",
        "wo.com.cn",
        "189.cn",
        "yeah.net",
        "tom.com",
        "21cn.com",
        "aliyun.com",
        "vip.qq.com",
        "vip.163.com",
        "vip.sina.com",
        "vip.sohu.com",
        
        // 企业邮箱常见域名
        "company.com",  // 示例企业域名
        "enterprise.com" // 示例企业域名
    ));
    
    /**
     * 校验邮箱格式是否有效
     * @param email 待校验的邮箱地址
     * @return 如果邮箱格式有效返回true，否则返回false
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        
        // 使用正则表达式校验基本格式
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * 校验邮箱格式并检查是否在支持的域名列表中
     * @param email 待校验的邮箱地址
     * @return 如果邮箱格式有效且域名受支持返回true，否则返回false
     */
    public static boolean isSupportedEmail(String email) {
        if (!isValidEmail(email)) {
            return false;
        }
        
        // 提取域名部分
        String domain = extractDomain(email);
        if (domain == null) {
            return false;
        }
        
        // 检查域名是否在支持列表中（不区分大小写）
        return SUPPORTED_EMAIL_DOMAINS.contains(domain.toLowerCase());
    }
    
    /**
     * 从邮箱地址中提取域名部分
     * @param email 邮箱地址
     * @return 域名部分，如果提取失败返回null
     */
    public static String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        
        int atIndex = email.lastIndexOf('@');
        if (atIndex == -1 || atIndex == email.length() - 1) {
            return null;
        }
        
        return email.substring(atIndex + 1).toLowerCase();
    }
    
    /**
     * 获取支持的邮箱域名列表
     * @return 支持的邮箱域名集合
     */
    public static Set<String> getSupportedDomains() {
        return new HashSet<>(SUPPORTED_EMAIL_DOMAINS);
    }
    
    /**
     * 添加支持的邮箱域名
     * @param domain 要添加的域名
     */
    public static void addSupportedDomain(String domain) {
        if (domain != null && !domain.isEmpty()) {
            SUPPORTED_EMAIL_DOMAINS.add(domain.toLowerCase());
        }
    }
    
    /**
     * 移除支持的邮箱域名
     * @param domain 要移除的域名
     */
    public static void removeSupportedDomain(String domain) {
        if (domain != null && !domain.isEmpty()) {
            SUPPORTED_EMAIL_DOMAINS.remove(domain.toLowerCase());
        }
    }
}
