import {writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
// 输出仅用于专用空演示库；不包含账号密码、删表或覆盖语句。
const sql=['SET NAMES utf8mb4;','START TRANSACTION;'];
const q=s=>`'${String(s).replaceAll("'","''")}'`;
const insert=(table,columns,values)=>sql.push(`INSERT INTO ${table}(${columns}) VALUES(${values});`);
let students=0,scores=0,records=0;
for(let c=1;c<=3;c++){
  insert('classes','id,class_name,grade,academic_year,created_at,updated_at',`${c},${q(`演示高一（${c}）班`)},'高一','2026-2027',NOW(),NOW()`);
  for(let n=1;n<=12;n++){
    const id=(c-1)*12+n,no=`DEMO${String(id).padStart(3,'0')}`;
    const name=c===1&&n<=2?'同名演示生':`演示同学${String(id).padStart(2,'0')}`;
    insert('students','id,student_no,name,class_id,status,enrollment_date,created_at,updated_at',`${id},${q(no)},${q(name)},${c},'在读','2026-09-01',NOW(),NOW()`);students++;
  }
  for(let round=1;round<=2;round++){
    const exam=(c-1)*2+round,day=round===1?'2026-10-08':'2026-10-18';
    insert('exams','id,name,class_id,exam_date',`${exam},${q(`演示${round===1?'月考':'阶段测验'}-${c}班`)},${c},${q(day)}`);
    for(let n=1;n<=10;n++){
      const sid=(c-1)*12+n,absent=n===10,score=n===1?0:Math.min(100,45+n*5+round*2);
      insert('scores','student_id,exam_id,subject,score,full_score,status,remark',`${sid},${exam},'数学',${absent?'NULL':score},100,${q(absent?'ABSENT':'RECORDED')},'虚构演示数据'`);scores++;
    }
    const assignment=exam,deadline=round===1?'2026-10-19T18:00:00':'2026-11-01T18:00:00';
    insert('assignments','id,name,class_id,subject,description,assigned_date,deadline',`${assignment},${q(`演示数学作业${round}-${c}班`)},${c},'数学','仅供毕业答辩演示','2026-10-18',${q(deadline)}`);
    for(let n=1;n<=12;n++){
      const state=n<=6?'COMPLETED':n<=8?(round===1?'LATE':'COMPLETED'):n===9?'INCOMPLETE':'PENDING';
      const submitted=n<=8?q(n<=6||round===2?'2026-10-19T17:00:00':'2026-10-19T19:00:00'):'NULL';
      insert('assignment_records','assignment_id,student_id,status,submitted_at,included',`${assignment},${(c-1)*12+n},${q(state)},${submitted},${n===12?'FALSE':'TRUE'}`);records++;
    }
  }
  for(let n=1;n<=6;n++){
    const sid=(c-1)*12+n;
    insert('attendance','student_id,type,occurred_date,remark,recorder',`${sid},${q(['LATE','EARLY_LEAVE','ABSENT'][(n-1)%3])},${q(`2026-10-${String(14+n).padStart(2,'0')}`)},'虚构考勤记录','demo-seed'`);
    insert('praises','student_id,praise_date,reason,remark,recorder',`${sid},'2026-10-20','主动帮助同学（虚构）','仅供演示','demo-seed'`);
  }
}
insert('exams','id,name,class_id,exam_date',"7,'演示AI录入专场',1,'2026-10-21'");
sql.push('COMMIT;');
assert.equal(students,36);assert.equal(scores,60);assert.equal(records,72);
assert(!sql.some(line=>/\b(?:DELETE|DROP|TRUNCATE|REPLACE)\b/i.test(line)));
const output=process.argv[2];if(!output)throw Error('用法: node scripts/Generate-DemoSql.mjs <输出SQL路径>');
writeFileSync(output,sql.join('\n')+'\n','utf8');
console.log(JSON.stringify({students,scores,assignmentRecords:records,classes:3,exams:7,assignments:6,attendance:18,praises:18}));
