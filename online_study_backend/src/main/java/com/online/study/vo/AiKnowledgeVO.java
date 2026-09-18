package com.online.study.vo;

import lombok.Data;

import java.util.List;

/**
 * 智能助教的知识库状态。
 *
 * <p>前端用它渲染页面顶部的"资料状态条"，也是排查问题时第一眼看的地方：
 * 块数为 0 说明没灌库、维度为 0 说明向量接口没调通、
 * configured 为 false 说明 Key 没配。
 */
@Data
public class AiKnowledgeVO {

    /** API Key 是否已配置。false 时所有 AI 功能都不可用 */
    private Boolean configured;

    /** 向量库中的知识块总数 */
    private Integer chunkCount;

    /** 向量维度（text-embedding-v4 为 1024）。0 表示还没灌过库 */
    private Integer dimension;

    /** 最近一次灌库时间，如「2026-09-17T20:30:15」，未灌库时为 null */
    private String loadedAt;

    /** 手工补充的资料份数 */
    private Integer extraDocCount;

    /** 手工资料标题清单 */
    private List<String> extraDocTitles;

    /** 当前使用的对话模型 code */
    private String chatModel;

    /** 当前使用的向量模型 code */
    private String embeddingModel;
}
