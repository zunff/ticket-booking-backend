package com.ticketbooking.ticket.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ticketbooking.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * XXL-JOB Admin API 客户端
 * 用于动态创建、更新、删除任务
 *
 * 注意：XXL-Job 管理端 API 需要先登录获取 Cookie
 * Cookie 存储在 Redis 中，支持多实例共享
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XxlJobAdminClient {

    @Value("${xxl.job.admin.addresses:http://localhost:8880/xxl-job-admin}")
    private String adminAddresses;

    @Value("${xxl.job.executor.appname:ticket-executor}")
    private String executorAppname;

    @Value("${xxl.job.admin.username:admin}")
    private String adminUsername;

    @Value("${xxl.job.admin.password:123456}")
    private String adminPassword;

    private final RedisUtils redisUtils;

    private static final DateTimeFormatter CRON_FORMATTER = DateTimeFormatter.ofPattern("ss mm HH dd MM ? yyyy");

    private static final String MISFIRE_STRATEGY_DO_NOTHING = "DO_NOTHING";
    private static final String ROUTE_STRATEGY_FIRST = "FIRST";
    private static final String BLOCK_STRATEGY_SERIAL = "SERIAL_EXECUTION";

    // Redis Key 常量
    private static final String REDIS_KEY_COOKIE = "xxl-job:admin:cookie";
    private static final String REDIS_KEY_JOB_GROUP_ID = "xxl-job:admin:jobGroupId";
    // Cookie 有效期 25 分钟（XXL-JOB 默认 30 分钟过期）
    private static final long COOKIE_TTL_MINUTES = 25;

    /**
     * 创建或更新预热任务
     */
    public Integer addOrUpdateOnceJob(String jobHandler, String param, LocalDateTime triggerTime, Integer existingJobId) {
        String jobDesc = buildJobDesc(param);

        try {
            Integer jobGroupId = getJobGroupId();
            if (jobGroupId == null) {
                log.warn("[XXL-JOB] 获取执行器分组失败: appname={}", executorAppname);
                return null;
            }

            String cronExpression = buildOnceCron(triggerTime);
            log.debug("[XXL-JOB] 一次性任务 CRON: {}", cronExpression);

            if (existingJobId != null) {
                if (updateJob(existingJobId, jobGroupId, cronExpression, param, jobHandler)) {
                    log.info("[XXL-JOB] 更新预热任务成功: jobId={}, param={}, triggerTime={}", existingJobId, param, triggerTime);
                    return existingJobId;
                } else {
                    log.warn("[XXL-JOB] 更新任务失败，尝试创建新任务");
                    return addJob(jobGroupId, jobHandler, jobDesc, cronExpression, param);
                }
            } else {
                Integer jobId = addJob(jobGroupId, jobHandler, jobDesc, cronExpression, param);
                if (jobId != null) {
                    log.info("[XXL-JOB] 创建预热任务成功: jobId={}, param={}, triggerTime={}", jobId, param, triggerTime);
                }
                return jobId;
            }
        } catch (Exception e) {
            log.error("[XXL-JOB] 创建/更新任务异常: param={}", param, e);
            return null;
        }
    }

    /**
     * 删除预热任务
     */
    public boolean removeJob(Integer jobId) {
        if (jobId == null) {
            log.debug("[XXL-JOB] 任务ID为空，无需删除");
            return true;
        }

        try {
            Integer jobGroupId = getJobGroupId();
            if (jobGroupId == null) {
                return true;
            }

            String url = adminAddresses + "/jobinfo/remove";
            Map<String, Object> form = new HashMap<>();
            form.put("id", jobId);
            form.put("jobGroup", jobGroupId);

            HttpResponse response = post(url, form);
            JSONObject json = parseJsonSafely(response.body());
            if (json == null) {
                log.warn("[XXL-JOB] 删除任务响应解析失败, body={}", truncate(response.body(), 500));
                return true;
            }

            if (isSuccess(json)) {
                log.info("[XXL-JOB] 删除任务成功: jobId={}", jobId);
                return true;
            } else {
                log.warn("[XXL-JOB] 删除任务失败: {}", response.body());
                return true;
            }
        } catch (Exception e) {
            log.warn("[XXL-JOB] 删除任务异常: jobId={}", jobId, e);
            return true;
        }
    }

    /**
     * 登录并获取 Cookie（存储在 Redis 中）
     */
    private String login() {
        // 先从 Redis 获取缓存的 Cookie
        String cachedCookie = redisUtils.get(REDIS_KEY_COOKIE);
        if (StrUtil.isNotBlank(cachedCookie)) {
            return cachedCookie;
        }

        // 同步锁，防止并发登录
        synchronized (this) {
            // Double check
            cachedCookie = redisUtils.get(REDIS_KEY_COOKIE);
            if (StrUtil.isNotBlank(cachedCookie)) {
                return cachedCookie;
            }

            try {
                String url = adminAddresses + "/login";
                Map<String, Object> form = new HashMap<>();
                form.put("userName", adminUsername);
                form.put("password", adminPassword);

                HttpResponse response = HttpRequest.post(url)
                        .form(form)
                        .timeout(5000)
                        .execute();

                if (response.isOk()) {
                    String setCookieHeader = response.header("Set-Cookie");
                    if (StrUtil.isNotBlank(setCookieHeader)) {
                        String cookie = setCookieHeader.split(";")[0];
                        // 存储到 Redis，设置过期时间
                        redisUtils.set(REDIS_KEY_COOKIE, cookie, COOKIE_TTL_MINUTES, TimeUnit.MINUTES);
                        log.info("[XXL-JOB] 登录成功，Cookie 已缓存到 Redis: {}", cookie);
                        return cookie;
                    } else {
                        log.warn("[XXL-JOB] 登录响应中没有 Cookie，body={}", truncate(response.body(), 200));
                    }
                } else {
                    log.warn("[XXL-JOB] 登录失败，status={}, body={}", response.getStatus(), truncate(response.body(), 200));
                }
            } catch (Exception e) {
                log.warn("[XXL-JOB] 登录异常: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 获取执行器分组ID（从 Redis 缓存）
     */
    private Integer getJobGroupId() {
        // 先从 Redis 获取
        String cachedId = redisUtils.get(REDIS_KEY_JOB_GROUP_ID);
        if (StrUtil.isNotBlank(cachedId)) {
            return Integer.parseInt(cachedId);
        }

        try {
            String url = adminAddresses + "/jobgroup/pageList";
            Map<String, Object> form = new HashMap<>();
            form.put("appname", executorAppname);
            form.put("start", 0);
            form.put("length", 10);

            HttpResponse response = post(url, form);
            String body = response.body();

            JSONObject json = parseJsonSafely(body);
            if (json == null) {
                log.warn("[XXL-JOB] 响应不是有效JSON, body={}", truncate(body, 500));
                return null;
            }

            // pageList 接口返回格式: {"recordsFiltered": 1, "data": [...]}
            JSONArray data = json.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                Integer jobGroupId = data.getJSONObject(0).getInt("id");
                if (jobGroupId != null) {
                    // 缓存到 Redis，不过期（执行器分组ID不会变化）
                    redisUtils.set(REDIS_KEY_JOB_GROUP_ID, String.valueOf(jobGroupId));
                    return jobGroupId;
                }
            }

            log.warn("[XXL-JOB] 执行器分组不存在: appname={}", executorAppname);
            return null;
        } catch (Exception e) {
            log.warn("[XXL-JOB] 获取执行器分组异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 添加任务
     */
    private Integer addJob(Integer jobGroupId, String jobHandler, String jobDesc,
                           String cronExpression, String param) {
        try {
            String url = adminAddresses + "/jobinfo/add";
            Map<String, Object> form = buildJobForm(jobGroupId, jobHandler, jobDesc, cronExpression, param);

            HttpResponse response = post(url, form);
            JSONObject json = parseJsonSafely(response.body());
            if (json == null) {
                log.warn("[XXL-JOB] 创建任务响应解析失败, body={}", truncate(response.body(), 500));
                return null;
            }

            if (isSuccess(json)) {
                return json.getInt("content");
            } else {
                log.warn("[XXL-JOB] 创建任务失败: {}", response.body());
                return null;
            }
        } catch (Exception e) {
            log.warn("[XXL-JOB] 创建任务异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新任务
     */
    private boolean updateJob(Integer jobId, Integer jobGroupId, String cronExpression, String param, String jobHandler) {
        try {
            String url = adminAddresses + "/jobinfo/update";
            Map<String, Object> form = buildJobForm(jobGroupId, jobHandler, buildJobDesc(param), cronExpression, param);
            form.put("id", jobId);

            HttpResponse response = post(url, form);
            JSONObject json = parseJsonSafely(response.body());
            if (json == null) {
                log.warn("[XXL-JOB] 更新任务响应解析失败, body={}", truncate(response.body(), 500));
                return false;
            }

            if (!isSuccess(json)) {
                log.warn("[XXL-JOB] 更新任务失败: {}", response.body());
            }
            return isSuccess(json);
        } catch (Exception e) {
            log.warn("[XXL-JOB] 更新任务异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 发送 POST 请求（带登录 Cookie）
     */
    private HttpResponse post(String url, Map<String, Object> form) {
        String cookie = login();
        HttpRequest request = HttpRequest.post(url)
                .form(form)
                .timeout(5000);

        if (StrUtil.isNotBlank(cookie)) {
            request.header("Cookie", cookie);
        }

        return request.execute();
    }

    /**
     * 安全解析 JSON
     */
    private JSONObject parseJsonSafely(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(body);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    /**
     * 构建任务表单
     */
    private Map<String, Object> buildJobForm(Integer jobGroupId, String jobHandler,
                                              String jobDesc, String cronExpression, String param) {
        Map<String, Object> form = new HashMap<>();
        form.put("jobGroup", jobGroupId);
        form.put("jobDesc", jobDesc);
        form.put("author", "system");
        form.put("scheduleType", "CRON");
        form.put("scheduleConf", cronExpression);
        form.put("misfireStrategy", MISFIRE_STRATEGY_DO_NOTHING);
        form.put("executorRouteStrategy", ROUTE_STRATEGY_FIRST);
        form.put("executorHandler", jobHandler);
        form.put("executorParam", param);
        form.put("executorBlockStrategy", BLOCK_STRATEGY_SERIAL);
        form.put("executorTimeout", 60);
        form.put("executorFailRetryCount", 3);
        form.put("glueType", "BEAN");
        form.put("triggerStatus", 1);
        return form;
    }

    private String buildOnceCron(LocalDateTime triggerTime) {
        return triggerTime.format(CRON_FORMATTER);
    }

    private String buildJobDesc(String param) {
        return "演唱会缓存预热-" + param;
    }

    /**
     * 判断接口是否成功
     * - pageList 等列表接口返回 {"data": [...], "recordsFiltered": N}
     * - add/update/remove 等操作接口返回 {"code": 200, "msg": "success"}
     */
    private boolean isSuccess(JSONObject json) {
        if (json == null) {
            return false;
        }
        // 操作接口返回 code
        Integer code = json.getInt("code");
        if (code != null) {
            return code == 200;
        }
        // 列表接口返回 data
        return json.containsKey("data");
    }
}
