package com.ticketbooking.ticket.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XXL-JOB Admin API 客户端
 * 用于动态创建、更新、删除任务
 */
@Slf4j
@Component
public class XxlJobAdminClient {

    @Value("${xxl.job.admin.addresses:http://localhost:8880/xxl-job-admin}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:default_token}")
    private String accessToken;

    @Value("${xxl.job.executor.appname:ticket-executor}")
    private String executorAppname;

    /**
     * 任务本地缓存 (param -> jobId)，避免频繁查询
     */
    private final ConcurrentHashMap<String, Integer> jobCache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter CRON_FORMATTER = DateTimeFormatter.ofPattern("ss mm HH dd MM ? yyyy");

    /**
     * 任务过期策略：忽略
     */
    private static final String MISFIRE_STRATEGY_DO_NOTHING = "DO_NOTHING";

    /**
     * 路由策略：第一个
     */
    private static final String ROUTE_STRATEGY_FIRST = "FIRST";

    /**
     * 阻塞处理策略：单机串行
     */
    private static final String BLOCK_STRATEGY_SERIAL = "SERIAL_EXECUTION";

    /**
     * 创建或更新预热任务
     * 使用 CRON 表达式实现一次性触发
     *
     * @param jobHandler  任务处理器名称
     * @param param       任务参数
     * @param triggerTime 触发时间
     * @return 任务ID，失败返回 null
     */
    public Integer addOrUpdateOnceJob(String jobHandler, String param, LocalDateTime triggerTime) {
        String jobDesc = buildJobDesc(param);

        try {
            // 1. 先从缓存查找
            Integer jobId = jobCache.get(param);

            // 2. 缓存未命中，查询执行器分组ID
            if (jobId == null) {
                Integer jobGroupId = getJobGroupId();
                if (jobGroupId == null) {
                    log.error("[XXL-JOB] 获取执行器分组失败: appname={}", executorAppname);
                    return null;
                }

                // 3. 查找现有任务
                jobId = findJobByDesc(jobGroupId, jobDesc);
            }

            // 4. 构建一次性 CRON 表达式
            String cronExpression = buildOnceCron(triggerTime);
            log.info("[XXL-JOB] 一次性任务 CRON: {}", cronExpression);

            if (jobId != null) {
                // 更新现有任务
                if (updateJob(jobId, cronExpression, param)) {
                    log.info("[XXL-JOB] 更新预热任务成功: jobId={}, param={}, triggerTime={}", jobId, param, triggerTime);
                    jobCache.put(param, jobId);
                    return jobId;
                } else {
                    // 更新失败，清除缓存重试
                    jobCache.remove(param);
                    return null;
                }
            } else {
                // 创建新任务
                Integer jobGroupId = getJobGroupId();
                if (jobGroupId == null) {
                    return null;
                }
                jobId = addJob(jobGroupId, jobHandler, jobDesc, cronExpression, param);
                if (jobId != null) {
                    log.info("[XXL-JOB] 创建预热任务成功: jobId={}, param={}, triggerTime={}", jobId, param, triggerTime);
                    jobCache.put(param, jobId);
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
    public boolean removeJob(String param) {
        String jobDesc = buildJobDesc(param);
        Integer jobId = jobCache.get(param);

        if (jobId == null) {
            Integer jobGroupId = getJobGroupId();
            if (jobGroupId == null) {
                return false;
            }
            jobId = findJobByDesc(jobGroupId, jobDesc);
        }

        if (jobId == null) {
            log.debug("[XXL-JOB] 任务不存在，无需删除: param={}", param);
            return true;
        }

        try {
            Integer jobGroupId = getJobGroupId();
            if (jobGroupId == null) {
                return false;
            }

            String url = adminAddresses + "/jobinfo/remove";
            Map<String, Object> form = new HashMap<>();
            form.put("id", jobId);
            form.put("jobGroup", jobGroupId);

            HttpResponse response = post(url, form);
            JSONObject json = JSONUtil.parseObj(response.body());

            if (isSuccess(json)) {
                jobCache.remove(param);
                log.info("[XXL-JOB] 删除任务成功: jobId={}, param={}", jobId, param);
                return true;
            } else {
                log.error("[XXL-JOB] 删除任务失败: {}", response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[XXL-JOB] 删除任务异常: jobId={}, param={}", jobId, param, e);
            return false;
        }
    }

    /**
     * 获取执行器分组ID
     */
    private Integer getJobGroupId() {
        try {
            String url = adminAddresses + "/jobgroup/pageList";
            Map<String, Object> form = new HashMap<>();
            form.put("appname", executorAppname);
            form.put("start", 0);
            form.put("length", 1);

            HttpResponse response = post(url, form);
            JSONObject json = JSONUtil.parseObj(response.body());

            if (isSuccess(json)) {
                JSONArray data = json.getJSONArray("data");
                if (data != null && !data.isEmpty()) {
                    return data.getJSONObject(0).getInt("id");
                }
            }

            log.warn("[XXL-JOB] 执行器分组不存在: appname={}", executorAppname);
            return null;
        } catch (Exception e) {
            log.error("[XXL-JOB] 获取执行器分组异常", e);
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
            JSONObject json = JSONUtil.parseObj(response.body());

            if (isSuccess(json)) {
                return json.getInt("content");
            } else {
                log.error("[XXL-JOB] 创建任务失败: {}", response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("[XXL-JOB] 创建任务异常", e);
            return null;
        }
    }

    /**
     * 更新任务
     */
    private boolean updateJob(Integer jobId, String cronExpression, String param) {
        try {
            Integer jobGroupId = getJobGroupId();
            if (jobGroupId == null) {
                return false;
            }

            String url = adminAddresses + "/jobinfo/update";
            Map<String, Object> form = buildJobForm(jobGroupId, "", "", cronExpression, param);
            form.put("id", jobId);

            HttpResponse response = post(url, form);
            JSONObject json = JSONUtil.parseObj(response.body());

            if (!isSuccess(json)) {
                log.error("[XXL-JOB] 更新任务失败: {}", response.body());
            }
            return isSuccess(json);
        } catch (Exception e) {
            log.error("[XXL-JOB] 更新任务异常", e);
            return false;
        }
    }

    /**
     * 根据描述查找任务
     */
    private Integer findJobByDesc(Integer jobGroupId, String jobDesc) {
        try {
            String url = adminAddresses + "/jobinfo/pageList";
            Map<String, Object> form = new HashMap<>();
            form.put("jobGroup", jobGroupId);
            form.put("jobDesc", jobDesc);
            form.put("start", 0);
            form.put("length", 10);

            HttpResponse response = post(url, form);
            JSONObject json = JSONUtil.parseObj(response.body());

            if (isSuccess(json)) {
                JSONArray data = json.getJSONArray("data");
                if (data != null && !data.isEmpty()) {
                    return data.getJSONObject(0).getInt("id");
                }
            }
            return null;
        } catch (Exception e) {
            log.error("[XXL-JOB] 查找任务异常", e);
            return null;
        }
    }

    /**
     * 构建任务表单
     */
    private Map<String, Object> buildJobForm(Integer jobGroupId, String jobHandler,
                                              String jobDesc, String cronExpression, String param) {
        Map<String, Object> form = new HashMap<>();
        form.put("jobGroup", jobGroupId);
        form.put("jobDesc", StrUtil.isNotBlank(jobDesc) ? jobDesc : buildJobDesc(param));
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

    /**
     * 构建一次性触发的 CRON 表达式
     * 格式: ss mm HH dd MM ? yyyy
     */
    private String buildOnceCron(LocalDateTime triggerTime) {
        return triggerTime.format(CRON_FORMATTER);
    }

    /**
     * 构建任务描述
     */
    private String buildJobDesc(String param) {
        return "演唱会缓存预热-" + param;
    }

    /**
     * 发送 POST 请求
     */
    private HttpResponse post(String url, Map<String, Object> form) {
        return HttpRequest.post(url)
                .header("XXL-JOB-ACCESS-TOKEN", accessToken)
                .form(form)
                .timeout(5000)
                .execute();
    }

    /**
     * 判断响应是否成功
     */
    private boolean isSuccess(JSONObject json) {
        return json != null && json.getInt("code") == 200;
    }
}
