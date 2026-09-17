package com.online.study.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页数字卡片
 *
 * <p>首页采用「后端聚合数据 + 前端按数据渲染」的方式：后端不做任何展示逻辑，
 * 只把「标签 / 数值 / 单位 / 说明」算好，前端统一渲染成卡片。
 * 好处是不同角色（管理员 / 教师 / 学员）可以复用同一套前端代码，
 * 以后新增角色只需在后端加一份数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatCardVO {

    /** 卡片标题，如「已报名课程」 */
    private String label;

    /** 数值（用 String 是为了兼容「暂无」「--」这类展示，避免前端再做转换） */
    private String value;

    /** 单位，如「门」「人」「分」 */
    private String unit;

    /** 副说明，如「其中 1 门待审核」 */
    private String tip;
}
