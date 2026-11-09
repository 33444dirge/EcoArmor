from pathlib import Path
from statistics import median
from collections import defaultdict
import re,json,sys
root=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).parent
runs={'a2-v5':'old','b1-v5':'new','b2-v5':'new','a3-v5':'old'}
patterns={name:re.compile(r'\[EA\] '+prefix+r' fixture=(\S+) variant=(old|new) round=(\d+) count=(\d+) ns=(\d+) bytes=(\d+)') for name,prefix in [('scan','BENCH'),('display','DISPLAY_BENCH')]}
data={name:defaultdict(lambda:defaultdict(list)) for name in patterns};per_run={};checks={};measured=0
for label,variant in runs.items():
    text=(root/f'{label}-server.log').read_text(encoding='utf8')
    assert '[EA] FAIL' not in text and '[EA] BENCH_DONE' in text and '[EA] RELOAD_PASS' in text
    assert re.search(r'\[EA\] DISABLED checks=\d+ failures=0',text)
    assert '[EA] DISPLAY comparisons=528 fixtures=33' in text and '[EA] GROUPING cases=1296' in text
    checks[label]={'functional':int(re.search(r'REGRESSION_DONE checks=(\d+)',text)[1]),'all':int(re.search(r'BENCH_DONE checks=(\d+)',text)[1])}
    per_run[label]={}
    for name,pattern in patterns.items():
        rows=pattern.findall(text);assert len(rows)==(144 if name=='scan' else 36),(label,name,len(rows))
        group=defaultdict(list)
        for fixture,actual,round_,count,ns,bytes_ in rows:
            assert actual==variant;count,ns,bytes_=map(int,(count,ns,bytes_));assert bytes_>=0
            sample={'ns':ns/count,'bytes':bytes_/count,'count':count};measured+=count
            data[name][fixture][variant].append(sample);group[fixture].append(sample)
        per_run[label][name]={f:{'ns':median(s['ns'] for s in items),'bytes':median(s['bytes'] for s in items)} for f,items in group.items()}
results={}
for name,fixtures in data.items():
    results[name]={}
    for fixture,variants in fixtures.items():
        row={v:{k:median(s[k] for s in items) for k in ['ns','bytes']} for v,items in variants.items()}
        row['allocationReductionPercent']=(1-row['new']['bytes']/row['old']['bytes'])*100
        row['wallTimeReductionPercent']=(1-row['new']['ns']/row['old']['ns'])*100
        results[name][fixture]=row
        assert row['new']['bytes']<=row['old']['bytes'],('allocation regression',name,fixture,row)
        print(f'{name}/{fixture}: {row["old"]["bytes"]:.1f}->{row["new"]["bytes"]:.1f} B/call ({row["allocationReductionPercent"]:.2f}% less); {row["old"]["ns"]:.1f}->{row["new"]["ns"]:.1f} ns/call ({row["wallTimeReductionPercent"]:.2f}% less)')
report={'measuredCalls':measured,'warmupCalls':4*(9*50000+3*10000),'checks':checks,'results':results,'perRun':per_run}
(root/'results.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print('calls',measured,'functional assertions',sum(v['functional'] for v in checks.values()),'total assertions',sum(v['all'] for v in checks.values()))
