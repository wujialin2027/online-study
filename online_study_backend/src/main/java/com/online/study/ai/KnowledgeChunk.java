package com.online.study.ai;

/**
 * 一个知识块 —— RAG 里最小的检索单位。
 *
 * <p>为什么要把资料切块：一整份课件几万字，既塞不进模型（有上下文长度上限），
 * 也不该塞（无关内容会稀释答案、还白花 token）。
 * 切成小块后，学生问什么就只捞出最相关的那几块，又准又省钱。
 *
 * @param id     唯一标识（重新灌库后会变化，仅用于调试与去重展示）
 * @param source 来源标题，如「课程：Java 程序设计」「作业：第 3 章作业」。用于给答案标注出处
 * @param text   片段原文
 * @param vector 该片段的向量（入库时算好并随块一起存，检索时直接用，不再重复调用接口）
 */
public record KnowledgeChunk(String id, String source, String text, float[] vector) {
}
