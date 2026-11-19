package com.example.classmanagement.ai;

import java.util.List;
import java.util.Map;

/**
 * 仅在 AiController 内部动态追加的工具定义。
 * 与 ReadOnlyTools / WriteTools 的定义分开，避免把权限相关段落混进通用工具表。
 */
final class AiToolSchemas {
    private AiToolSchemas() {}

    /** 管理员回收站查询工具定义 */
    static Map<String, Object> recycleBinQuery() {
        return Map.of("type","function","function",Map.of("name","query_recycle_bin","description","查询管理员回收站，返回可恢复的真实回收编号id、原recordId、type、label、删除来源source及batchCount。可按type分类及q姓名、学号、班级关键词搜索；多个匹配须用户选择。恢复工具的recycleId使用这里的id而非原recordId。","parameters",Map.of("type","object","properties",Map.of("type",Map.of("type","string","enum",com.example.classmanagement.recycle.RecycleBinService.TYPES.keySet()),"q",Map.of("type","string","maxLength",100)),"additionalProperties",false)));
    }

    /** 普通聊天直接回复工具定义 */
    static Map<String, Object> replyToUser() {
        return Map.of("type","function","function",Map.of("name","reply_to_user","description","直接回复普通聊天、问候、一般知识、写作、建议，或不涉及业务事实断言的必要澄清。保留上下文和人设，不强行追问班级。不能用来回答数据库中的实际记录或宣称业务已保存、修改、删除；这些请求仍须业务工具。只返回面向用户的自然回复，不解释工具调用。","parameters",Map.of("type","object","properties",Map.of("answer",Map.of("type","string","maxLength",16000)),"required",List.of("answer"),"additionalProperties",false)));
    }

}