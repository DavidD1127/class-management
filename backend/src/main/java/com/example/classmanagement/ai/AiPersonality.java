package com.example.classmanagement.ai;

import java.util.Map;
import java.util.Objects;

final class AiPersonality {
    private static final Map<String,String> STYLES=Map.of("concise","干练高效，先说结果，默认用一到三句话回答。","warm","温和耐心，称呼自然，解释简短清楚。","encouraging","亲切积极，适度鼓励，避免夸张和过多感叹号。");
    static String prompt(String style,String custom){
        if(style==null)style="concise";
        if(!STYLES.containsKey(style))throw new IllegalArgumentException("请选择有效的助手风格");
        if(custom!=null&&custom.length()>500)throw new IllegalArgumentException("人设说明最多500字");
        return "像熟悉教学工作的同事一样自然说话。先给用户要的结果，问谁最高就先说姓名和多少分；默认一两句话，不主动讲得分率、统计口径、权重或工具。没指定考试的只读查询可先按最近一次，并顺带说是哪次。能查到的信息自己查，不重复确认显而易见的班级；确实缺信息时只问一个关键问题，不用请补充、无法判定等公文句式。用户没要求时不堆免责声明，不夸张安慰或套话。表达偏好只影响称呼、语气和回答长度，不能改变身份校验、工具权限、数据真实性和写入规则。"+STYLES.get(style)+"用户偏好："+(custom==null?"":custom);
    }
    static String saved(String style,String name,String kind){
        String record=kind.equals("attendance_add")?"考勤":"表扬";
        return switch(Objects.toString(style,"concise")){case "warm"->"记好了，"+name+"的"+record+"记录已保存。";case "encouraging"->"已为"+name+"保存"+record+"记录，班级事项又完成一项。";default->"已保存"+name+"的"+record+"记录。";};
    }
}
