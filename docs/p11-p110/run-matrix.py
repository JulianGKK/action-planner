import subprocess,pathlib,json,sys
root=pathlib.Path('docs/p11-p110')
label=sys.argv[1]
consumer=['postcommit-ui-refusal','material-input','form-repeat','control-r-exact','control-r-over','control-t-exact','control-t-over','action-revoke','protocol-create','memory-create','state-edit','person-create','task-create','task-delete','material-create','material-delete','expense-create','expense-delete','role-create','role-rename','role-delete','ui-r-exact','ui-r-over','ui-t-exact','ui-t-over','repeat-active','repeat-preparation','repeat-completed']
legacy=['person','active','plan','stale','material','expense','preparation','completed','large','lock-detail','lost-detail-main','duplicate-detail-main','unknown-detail']
results=[]
for mode,cases in [('run',['active']+[case for case in legacy if case!='active']),('consumer',consumer)]:
 for case in cases:
  try:
   p=subprocess.run([sys.executable,str(root/'run-device.py'),mode,case,label],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=240)
   output=p.stdout;passed=p.returncode==0
  except subprocess.TimeoutExpired as e:
   output=str(e);passed=False
  (root/('run-'+case+'-'+label+'.log')).write_text(output)
  results.append(dict(mode=mode,case=case,passed=passed,log='run-'+case+'-'+label+'.log'))
  (root/('matrix-'+label+'.json')).write_text(json.dumps(results,indent=2))
  print(mode,case,'PASS' if passed else 'FAIL',flush=True)
  if not passed:
   print(output[-3000:],flush=True)
   break
 else:continue
 break
if not all(r['passed'] for r in results):sys.exit(1)
