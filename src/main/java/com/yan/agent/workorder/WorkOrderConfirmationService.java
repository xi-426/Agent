package com.yan.agent.workorder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Service
public class WorkOrderConfirmationService {

    private static final String KEY_PREFIX = "work-order:pending:";

    private final StringRedisTemplate redisTemplate;
    private final WorkOrderRepository workOrderRepository;
    private final Duration confirmationTtl;

    public WorkOrderConfirmationService(
            StringRedisTemplate redisTemplate,
            WorkOrderRepository workOrderRepository,
            @Value("${app.work-order.confirmation-ttl-minutes}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.workOrderRepository = workOrderRepository;
        this.confirmationTtl = Duration.ofMinutes(ttlMinutes);
    }

    public String prepare(
            Long userId,
            Long sessionId,
            String title,
            String description,
            WorkOrder.Priority priority) {
        // 把待创建字段写入 Redis Hash，设置 TTL，并返回确认提示。
        String key = buildKey(
                userId,
                sessionId);

        redisTemplate.opsForHash()
                .putAll(
                        key,
                        Map.of(
                                "title", title,
                                "description", description,
                                "priority", priority.name()));

        redisTemplate.expire(
                key,
                confirmationTtl);

        return """
                准备创建工单：
                标题：%s
                描述：%s
                优先级：%s
                请回复“确认创建工单”完成创建，或回复“取消创建工单”取消。
                """.formatted(
                title,
                description,
                priority);
    }

    @Transactional
    public String confirm(
            Long userId,
            Long sessionId) {
        // 读取待确认 Hash；不存在则提示；存在则创建工单、删除 Key 并返回编号。
        String key = buildKey(
                userId,
                sessionId);

        Map<Object, Object> pending = redisTemplate.opsForHash()
                .entries(key);

        if (pending.isEmpty()) {
            return "当前没有等待确认的工单，可能已经取消或超过10分钟。";
        }

        String title = String.valueOf(
                pending.get("title"));

        String description = String.valueOf(
                pending.get("description"));

        WorkOrder.Priority priority = WorkOrder.Priority.valueOf(
                String.valueOf(
                        pending.get("priority")));

        WorkOrder workOrder = new WorkOrder(
                userId,
                title,
                description,
                priority);

        //它比普通 save() 多一步：立即把 INSERT 发送给数据库确认，而不是等事务结束才发送。确认数据库写入没有问题后，再删除 Redis 待确认内容。
        WorkOrder savedWorkOrder = workOrderRepository.saveAndFlush(
                workOrder);

        redisTemplate.delete(key);

        return "工单创建成功，工单编号："
                + savedWorkOrder.getId();
    }

    public void cancel(
            Long userId,
            Long sessionId) {
        redisTemplate.delete(buildKey(userId, sessionId));
    }

    public boolean hasPending(
            Long userId,
            Long sessionId) {
        Boolean exists = redisTemplate.hasKey(
                buildKey(userId, sessionId));
        return Boolean.TRUE.equals(exists);
    }

    private String buildKey(
            Long userId,
            Long sessionId) {
        return KEY_PREFIX + userId + ":" + sessionId;
    }
}
