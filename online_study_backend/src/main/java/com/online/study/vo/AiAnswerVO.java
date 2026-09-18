package com.online.study.vo;

import lombok.Data;

import java.util.List;

/**
 * 智能助教的一次问答结果。
 *
 * <p>除了答案正文，还带回<b>出处</b>（sources）—— 这是 RAG 与传统聊天机器人的
 * 最大区别：每个结论都能点回原资料。前端据此渲染答案下方的引用列表，
 * 同时也是"这句话有没有依据"的唯一凭证。
 */
@Data
public class AiAnswerVO {

    /** 模型生成的答案正文 */
    private String answer;

    /** 本次回答引用的资料片段（按相似度从高到低） */
    private List<Source> sources;

    /** 知识库当前的知识块总数（让用户知道"助教读过多少资料"） */
    private Integer chunkCount;

    /** 实际命中并送进提示词的片段数 */
    private Integer hitCount;

    /** 本次消耗的输入 token */
    private Integer promptTokens;

    /** 本次消耗的输出 token */
    private Integer completionTokens;

    /** 实际使用的对话模型 code */
    private String model;

    /** 知识库是否为空。为空时前端应提示"请先重建知识库"而不是正常展示答案 */
    private Boolean knowledgeEmpty;

    /**
     * 本次回答过程中实际调用过的工具（数据库查询）。
     *
     * <p>空列表 = 这个问题是靠检索静态资料回答的（RAG 路径）；
     * 非空 = 模型判断需要查实时数据，调了工具（工具调用路径）。
     *
     * <p>为什么要把它返回给前端展示：让用户看到"助教查了什么"，
     * 既增加可信度，也方便排查"为什么答得不对"（比如模型挑错了工具）。
     */
    private List<ToolTrace> usedTools;

    /**
     * 一次工具调用的轨迹。
     */
    @Data
    public static class ToolTrace {

        /** 工具名，如 query_my_homework */
        private String name;

        /** 中文展示名，如「我的作业提交情况」 */
        private String label;

        /** 模型给的参数摘要，如「courseName=Java 基础」；无参数时为空串 */
        private String args;

        /** 是否执行成功 */
        private Boolean success;

        public static ToolTrace of(String name, String label, String args, boolean success) {
            ToolTrace trace = new ToolTrace();
            trace.setName(name);
            trace.setLabel(label);
            trace.setArgs(args == null ? "" : args);
            trace.setSuccess(success);
            return trace;
        }
    }

    /**
     * 一条引用出处。
     */
    @Data
    public static class Source {

        /** 知识块编号，例如「作业：Java 基础 / 第 3 次作业#1」，用于唯一标识一块 */
        private String chunkId;

        /** 资料标题，展示用，例如「作业：Java 基础 / 第 3 次作业」 */
        private String source;

        /** 命中片段的正文（截断到 200 字，避免响应过大） */
        private String snippet;

        /** 相似度百分比：0~100 的整数（87 表示 87%），便于前端直接显示 */
        private Integer score;

        public static Source of(String chunkId, String source, String text, double scorePercent) {
            Source vo = new Source();
            vo.setChunkId(chunkId);
            vo.setSource(source);
            vo.setSnippet(snippet(text));
            vo.setScore((int) Math.round(scorePercent));
            return vo;
        }

        private static String snippet(String text) {
            if (text == null) {
                return "";
            }
            String oneLine = text.replace("\r", " ").replace("\n", " ").trim();
            return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "…";
        }
    }
}
