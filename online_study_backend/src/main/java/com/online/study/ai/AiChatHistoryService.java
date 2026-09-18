package com.online.study.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.online.study.entity.AiChatMessage;
import com.online.study.mapper.AiChatMessageMapper;
import com.online.study.utils.CurrentUserUtil;
import com.online.study.vo.AiChatMessageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 智能助教的对话记录（落库 / 回放 / 清空）。
 *
 * <h3>解决的问题</h3>
 * 改造前对话只活在前端内存里，刷新即丢。现在每条消息都写进 {@code ai_chat_message}，
 * 换设备登录同一账号也能看到自己的提问历史。
 *
 * <h3>为什么单独抽一个 Service，而不是塞进 AiAssistantService</h3>
 * {@link AiAssistantService} 的职责是「检索 + 调模型 + 调工具」，属于<b>推理链路</b>；
 * 对话记录属于<b>存储链路</b>，两者的失败策略完全不同 ——
 * 模型调用失败必须让用户知道，而记录落库失败不该影响用户拿到答案。
 * 拆开之后 {@link #record} 里那句"吞掉异常只打日志"才不会显得像在掩盖问题。
 *
 * <h3>身份从哪来（安全底线）</h3>
 * {@code userRole} / {@code userId} 一律通过 {@link CurrentUserUtil} 从 JWT 验签结果里取，
 * <b>不接受前端传参</b>。否则只要在请求里改一个 userId，就能读到别人的全部对话。
 * 所有查询与删除都带这两列作为过滤条件，天然做到用户之间互相隔离。
 */
@Service
public class AiChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(AiChatHistoryService.class);

    /** 消息角色：用户提问 */
    public static final String ROLE_USER = "user";

    /** 消息角色：助教回答 */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 一次最多返回多少条历史（约 25 轮问答），避免响应体随使用时长无上限增长 */
    private static final int FETCH_LIMIT = 50;

    /**
     * 每个用户最多保留多少条消息（约 100 轮问答）。
     *
     * <p>超出的部分在每轮问答结束时自动删掉最旧的。不设这个上限的话，
     * 表会随使用时长无限膨胀 —— 个人项目里看不出来，但这是该有的意识：
     * 任何"只写不删"的日志类表最终都要面对容量问题。
     */
    private static final int KEEP_PER_USER = 200;

    private final AiChatMessageMapper mapper;

    public AiChatHistoryService(AiChatMessageMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记录一条消息。
     *
     * <p>刻意<b>不抛异常</b>：记不上只是"这次没留痕"，不该让用户连答案都拿不到。
     * 所以内部捕获并只打一行 warn，调用方（{@link AiAssistantService#ask}）无感。
     */
    public void record(String role, String content) {
        String userRole = CurrentUserUtil.getRole();
        Integer userId = CurrentUserUtil.getId();
        if (userRole == null || userId == null || content == null || content.isBlank()) {
            return;
        }
        try {
            AiChatMessage message = new AiChatMessage();
            message.setUserRole(userRole);
            message.setUserId(userId);
            message.setRole(role);
            message.setContent(content);
            message.setCreateTime(new Date());
            mapper.insert(message);

            // 一轮问答（提问 + 回答）结束才做容量清理，少一次无谓的统计查询
            if (ROLE_ASSISTANT.equals(role)) {
                prune(userRole, userId);
            }
        } catch (Exception e) {
            log.warn("保存对话记录失败（role={}）：{}", role, e.getMessage());
        }
    }

    /**
     * 拉取当前用户最近 {@value #FETCH_LIMIT} 条对话，按时间<b>正序</b>返回。
     *
     * <p>先倒序查（这样能走 {@code (user_role, user_id, message_id)} 索引直接取到最近的几条，
     * 不必全表扫描再排序），再在内存里反转成正序 —— 前端渲染时需要"早的在上、晚的在下"。
     */
    public List<AiChatMessageVO> recent() {
        String userRole = CurrentUserUtil.getRole();
        Integer userId = CurrentUserUtil.getId();
        if (userRole == null || userId == null) {
            return Collections.emptyList();
        }
        List<AiChatMessage> rows = mapper.selectList(
                Wrappers.<AiChatMessage>lambdaQuery()
                        .eq(AiChatMessage::getUserRole, userRole)
                        .eq(AiChatMessage::getUserId, userId)
                        .orderByDesc(AiChatMessage::getMessageId)
                        .last("LIMIT " + FETCH_LIMIT));
        Collections.reverse(rows);

        List<AiChatMessageVO> list = new ArrayList<>(rows.size());
        for (AiChatMessage row : rows) {
            AiChatMessageVO vo = new AiChatMessageVO();
            vo.setMessageId(row.getMessageId());
            vo.setRole(row.getRole());
            vo.setContent(row.getContent());
            vo.setCreateTime(row.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    /** 清空当前用户的对话记录，返回实际删除条数 */
    public int clear() {
        String userRole = CurrentUserUtil.getRole();
        Integer userId = CurrentUserUtil.getId();
        if (userRole == null || userId == null) {
            return 0;
        }
        return mapper.delete(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getUserRole, userRole)
                .eq(AiChatMessage::getUserId, userId));
    }

    /**
     * 容量清理：只保留最近的 {@value #KEEP_PER_USER} 条，更旧的删掉。
     *
     * <p>先取出"第 N 新"那条的 ID 作为分界线，再一条 DELETE 干掉所有比它旧的 ——
     * 比"查出来一堆 ID 再逐个删"少一轮往返。
     */
    private void prune(String userRole, Integer userId) {
        List<AiChatMessage> boundary = mapper.selectList(
                Wrappers.<AiChatMessage>lambdaQuery()
                        .select(AiChatMessage::getMessageId)
                        .eq(AiChatMessage::getUserRole, userRole)
                        .eq(AiChatMessage::getUserId, userId)
                        .orderByDesc(AiChatMessage::getMessageId)
                        .last("LIMIT 1 OFFSET " + (KEEP_PER_USER - 1)));
        if (boundary.isEmpty()) {
            // 总数还没到上限
            return;
        }
        Long oldestKept = boundary.get(0).getMessageId();
        int removed = mapper.delete(Wrappers.<AiChatMessage>lambdaQuery()
                .eq(AiChatMessage::getUserRole, userRole)
                .eq(AiChatMessage::getUserId, userId)
                .lt(AiChatMessage::getMessageId, oldestKept));
        if (removed > 0) {
            log.info("对话记录超出上限，清理 {} 条（{}#{}）", removed, userRole, userId);
        }
    }
}
