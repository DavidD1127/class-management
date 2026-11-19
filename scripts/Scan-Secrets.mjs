// 只输出位置和类别，不输出匹配原值；启发式扫描不能证明历史绝无秘密。
// 用法：
//   node scripts/Scan-Secrets.mjs                  扫描工作区 + 全部历史，只报告
//   node scripts/Scan-Secrets.mjs --no-history     只扫描当前工作区
//   node scripts/Scan-Secrets.mjs --fail           发现命中时以退出码 1 结束（配合 --no-history 用于 CI）
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
const cliArgs = new Set(process.argv.slice(2));
const skipHistory = cliArgs.has('--no-history');
const failOnFinding = cliArgs.has('--fail');
const git = (...args) => execFileSync('git', args, {encoding:'utf8', maxBuffer:64*1024*1024});
const patterns = [
  ['provider-key', /\bsk-[A-Za-z0-9_-]{20,}\b/g],
  ['private-key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/g],
  ['literal-yaml-jwt', /^\s*secret:\s*[^\s$<]{12,}/g],
  ['literal-bootstrap-password', /encoder\.encode\(\s*"[^"]{6,}"/g],
  ['credential-url', /(?:mysql|https?):\/\/[^\s/:]+:[^\s/@]{8,}@/g],
  ['literal-secret', /(?:api[_-]?key|jwt[._-]?secret|(?:DB|MYSQL|SPRING_DATASOURCE)_PASSWORD)\s*[=:]\s*["']?([A-Za-z0-9+/_!@#$%^&*=-]{16,})/gi]
];
const findings=[];
function scan(content, location) {
  content.split(/\r?\n/).forEach((line,i)=>{
    for(const [kind,regex] of patterns) {
      regex.lastIndex=0;
      if(regex.test(line) && !/test-only|synthetic|test-fixture|placeholder|至少|<|\$\{|\$env:|System\.getenv|Environment|config\[|matches\[|WriteAllText|PtrToStringBSTR|GetBytes|ToBase64String|repeat\(/i.test(line)) findings.push({location,line:i+1,kind});
    }
  });
}
const tracked=git('ls-files','--cached','--others','--exclude-standard','-z').split('\0').filter(Boolean);
for(const file of tracked) {try{scan(readFileSync(file,'utf8'),file);}catch{}}
const objects=skipHistory?[]:git('rev-list','--objects','--all').trim().split('\n');
let blobs=0;
for(const entry of objects){
  if(!entry)continue;
  const [oid,...path]=entry.split(' ');if(!path.length)continue;
  if(git('cat-file','-t',oid).trim()!=='blob')continue;
  blobs++;scan(git('cat-file','-p',oid),`history:${oid}:${path.join(' ')}`);
}
console.log(JSON.stringify({trackedFiles:tracked.length,historyBlobs:blobs,historyScanned:!skipHistory,findings,limit:'启发式扫描，测试占位值需人工复核；不输出秘密原值'},null,2));
if(failOnFinding && findings.length){process.exitCode=1}
