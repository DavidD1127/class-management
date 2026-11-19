package com.example.classmanagement.ai;

import com.example.classmanagement.business.ScoreService;
import com.example.classmanagement.classroom.ClassroomService;
import com.example.classmanagement.student.StudentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** 只处理目标明确的短指令；其余交给模型，写入仍走同一套预览、校验和确认。 */
@Service
public class AiLocalCommands {
    private final ClassroomService classes;private final StudentService students;private final ScoreService scores;private final WriteTools writes;private final AiRosterService roster;private final ObjectMapper json;
    public AiLocalCommands(ClassroomService c,StudentService s,ScoreService sc,WriteTools w,AiRosterService r,ObjectMapper j){classes=c;students=s;scores=sc;writes=w;roster=r;json=j;}
    private static String canonical(String text){String value=text.replaceAll("[\\s（）()]","");String digits="一二三四五六七八九";for(int i=0;i<digits.length();i++)value=value.replace(digits.charAt(i)+"班",(i+1)+"班");return value;}
    private static boolean namesClass(String message,Map<String,Object> c){
        if(message.contains(canonical(c.get("className").toString())))return true;
        var number=Pattern.compile("[（(]([0-9一二三四五六七八九十]+)[）)]班").matcher(c.get("className").toString());
        return number.find()&&Pattern.compile("(?<![0-9十百])"+Pattern.quote(canonical(number.group(1)))+"班").matcher(message).find();
    }
    public Map<String,Object> handle(AiController.Input input,Consumer<String> progress){
        if(input.attachment()!=null&&!input.attachment().isBlank())return null;
        String text=input.message().trim();if(text.length()>160||text.matches("(?s).*(不要|别查|别记|不记录|不用记|假如|如果|举例|示例|怎么判断|如何计算).*"))return null;
        boolean highest=text.matches("(?s).*(谁|哪位|哪个同学|哪个学生|最高分).*")&&text.matches("(?s).*(最高|第一|成绩最好).*");
        boolean attendance=text.matches("(?s).*(记上|记一下|记录|登记|添加|录入).*")&&text.matches("(?s).*(迟到|早退|缺勤).*");
        if(highest&&attendance)return null;
        if(!highest&&!attendance)return null;
        var all=classes.all();String normalized=canonical(text);var named=all.stream().filter(c->namesClass(normalized,c)).toList();
        if(named.isEmpty()&&text.matches("(?s).*(我们班|我班|全班|本班|班里|[0-9一二三四五六七八九十]+班).*"))return null;
        Long classId=named.size()==1?((Number)named.get(0).get("id")).longValue():null;if(named.size()>1&&named.size()!=all.size())return null;
        if(highest){
            if(text.matches("(?s).*(删除|恢复|记上|记一下|记录|登记|录入|添加|修改|导入|进步|得分率|百分比|平均|均分|哪个班|觉得|认为|怎么|如何|为什么|明天|前天|历史|学期|今年|去年|几次|上次|上一次|上个月|本月|这个月|月初|月底|上旬|中旬|下旬).*"))return null;
            var subjects=List.of("语文","数学","英语").stream().filter(text::contains).toList();if(subjects.size()>1)return null;
            if(text.contains("单科")&&subjects.isEmpty())return null;
            var rosterPage=students.search("",classId,0,100);if(rosterPage.hasNext()||rosterPage.getContent().stream().anyMatch(s->text.contains(s.name)||Pattern.compile("(?<![A-Za-z0-9_-])"+Pattern.quote(s.studentNo)+"(?![A-Za-z0-9_-])").matcher(text).find()))return null;
            progress.accept("正在查本地成绩……");Map<String,Object> result;
            try{
                String subject=subjects.isEmpty()?null:subjects.get(0),date=null;Integer month=null,year=null;
                var iso=Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(text);if(iso.find())date=LocalDate.parse(iso.group()).toString();
                var day=Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]?").matcher(text);
                if(date==null&&day.find())date=LocalDate.of(day.group(1)==null?LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear():Integer.parseInt(day.group(1)),Integer.parseInt(day.group(2)),Integer.parseInt(day.group(3))).toString();
                if(date==null&&text.matches("(?s).*月.*[日号].*"))return null;
                if(text.contains("今天"))date=LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();if(text.contains("昨天"))date=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
                var monthMatch=Pattern.compile("(?<![0-9])(1[0-2]|0?[1-9]|十一|十二|十|一|二|三|四|五|六|七|八|九)月").matcher(text);
                if(monthMatch.find()){String value=monthMatch.group(1);month=value.matches("[0-9]+")?Integer.parseInt(value):List.of("一","二","三","四","五","六","七","八","九","十","十一","十二").indexOf(value)+1;}
                else if(text.matches("(?s).*([0-9]+|[一二三四五六七八九十]+)月.*"))return null;
                var yearMatch=Pattern.compile("([0-9]{4})年").matcher(text);if(yearMatch.find())year=Integer.parseInt(yearMatch.group(1));
                String keyword=List.of("月考","期中","期末","阶段检测","阶段测验","周测","单元测验").stream().filter(text::contains).findFirst().orElse(null);
                if(date==null&&month==null&&year==null&&keyword==null)result=scores.leaders(classId,null,subject);
                else{
                    result=null;
                    for(var exam:scores.exams(classId)){
                        LocalDate examDate=LocalDate.parse(exam.get("examDate").toString());
                        if(date!=null&&!date.equals(examDate.toString())||month!=null&&month!=examDate.getMonthValue()||year!=null&&year!=examDate.getYear()||keyword!=null&&!exam.get("name").toString().contains(keyword))continue;
                        try{result=scores.leaders(classId,((Number)exam.get("id")).longValue(),subject);break;}catch(IllegalArgumentException ignored){}
                    }
                    if(result==null)return null;
                }
            }catch(IllegalArgumentException|java.time.DateTimeException e){return null;}
            var ranks=(List<Map<String,Object>>)result.get("ranking");
            String answer;
            if(ranks.isEmpty())answer="这场考试还没有录齐成绩的学生，暂时没有最高分。";
            else{
                var top=ranks.stream().filter(r->Integer.valueOf(1).equals(r.get("rank"))).toList();
                String people=String.join("、",top.stream().map(r->r.get("studentName")+(classId==null?"（"+r.get("className").toString().replace("演示","")+"）":"")).toList());
                String exam=result.get("examName").toString().replaceFirst("^\\d{4}-\\d{4}学年高一","");
                answer=people+(top.size()>1?"并列最高，都是":"最高，")+((BigDecimal)top.get(0).get("score")).stripTrailingZeros().toPlainString()+"分。（"+exam+"，"+result.get("subject")+"）";
            }
            return Map.of("answer",answer,"queryResults",List.of(Map.of("kind","score_leaders","result",Map.of("data",result,"range",result.get("examDate")))));
        }
        if(text.matches("(?s).*(取消|是否|会不会|有没有|查询|看看|能不能|可以吗).*"))return null;
        var dates=Pattern.compile("今天|昨天|\\d{4}-\\d{2}-\\d{2}").matcher(text);if(!dates.find())return null;String date=dates.group();if(dates.find())return null;
        var types=List.of("迟到","早退","缺勤").stream().filter(text::contains).toList();if(types.size()!=1)return null;
        var page=students.search("",classId,0,100);if(page.hasNext())return null;
        var matches=page.getContent().stream().filter(s->text.contains(s.name)||Pattern.compile("(?<![A-Za-z0-9_-])"+Pattern.quote(s.studentNo)+"(?![A-Za-z0-9_-])").matcher(text).find()).toList();if(matches.size()!=1)return null;
        var student=matches.get(0);String remainder=normalized.replace(canonical(student.name),"").replace(canonical(student.studentNo),"").replace(date,"");
        for(var c:named){remainder=remainder.replace(canonical(c.get("className").toString()),"");var m=Pattern.compile("[（(]([0-9一二三四五六七八九十]+)[）)]班").matcher(c.get("className").toString());if(m.find())remainder=remainder.replace(canonical(m.group(1))+"班","");}
        var time=Pattern.compile("(?<![0-9])([01]?[0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?(?![0-9])").matcher(text);String timeText=null;
        if(time.find()){timeText=time.group();remainder=remainder.replace(timeText,"");if(time.find())return null;}
        remainder=remainder.replaceAll("帮我|给我|请|把|学生|同学|学号|迟到|早退|缺勤|记上|记一下|记录|登记|添加|录入|一下|的|了","").replaceAll("[\\p{P}\\s]","");
        if(!remainder.isEmpty())return null;
        Map<String,Object> args=new LinkedHashMap<>();args.put("kind","attendance_add");args.put("studentNo",student.studentNo);args.put("date",date);args.put("type",Map.of("迟到","LATE","早退","EARLY_LEAVE","缺勤","ABSENT").get(types.get(0)));if(classId!=null)args.put("classId",classId);
        if(timeText!=null){String[] parts=timeText.split(":");args.put("time",LocalTime.of(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),parts.length==3?Integer.parseInt(parts[2]):0).toString());}
        progress.accept("正在核对学生和记录……");var node=json.valueToTree(args);var operation=writes.prepare(node);String answer="已找到"+student.name+"，请核对下方记录后确认。";
        if(Boolean.TRUE.equals(input.autoRecord())&&roster.canAutoRecord(text,node,operation)){
            operation=writes.confirm(operation.get("id").toString());answer=operation.get("status").equals("SUCCEEDED")?AiPersonality.saved(input.style(),student.name,"attendance_add"):"这条没记成功，请看下方原因。";
        }
        return Map.of("answer",answer,"operation",operation,"queryResults",List.of());
    }
}
