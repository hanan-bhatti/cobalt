"""Seed or redistribute dates in a stopped, disposable DEBUG emulator database.
Never targets the production app. No builds, root, credentials or app code edits.
"""
from pathlib import Path
import argparse,datetime,hashlib,io,json,random,sqlite3,subprocess,tarfile,tempfile
from zoneinfo import ZoneInfo

ADB='/home/hanan-bhatti/Android/Sdk/platform-tools/adb'
PACKAGE='com.hanan_bhatti.cobalt.debug'
def main():
 p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--count',type=int,default=500);args=p.parse_args()
 if not args.serial.startswith('emulator-'):raise SystemExit('Disposable emulator required')
 def adb(*a):return subprocess.run([ADB,'-s',args.serial,*a],capture_output=True,check=True).stdout
 adb('shell','run-as',PACKAGE,'id')
 adb('shell','am','force-stop',PACKAGE)
 work=Path(tempfile.mkdtemp(prefix='cobalt-debug-heatmap-'))
 data=adb('exec-out','run-as',PACKAGE,'tar','-cf','-','databases','shared_prefs')
 (work/'before.tar').write_bytes(data)
 with tarfile.open(fileobj=io.BytesIO(data)) as archive:archive.extractall(work/'working',filter='data')
 database=work/'working/databases/cobalt_db'
 db=sqlite3.connect(database)
 version=db.execute('PRAGMA user_version').fetchone()[0]
 if version!=15:raise RuntimeError(f'Expected schema 15; got {version}')
 before=db.execute('SELECT count(*) FROM saved_items').fetchone()[0]
 rng=random.Random(20261110);zone=ZoneInfo('Asia/Karachi')
 now=datetime.datetime.now(zone);today=now.date()
 offsets=sorted(set(rng.sample(range(7,133),78)+list(range(7))))
 weights=[rng.uniform(.2,2.5) for _ in offsets]
 dates=[]
 for i in range(before or args.count):
  offset=offsets[i] if i<len(offsets) else rng.choices(offsets,weights=weights)[0]
  day=today-datetime.timedelta(days=offset)
  when=datetime.datetime.combine(day,datetime.time(rng.randrange(8,22),rng.randrange(60)),zone)
  if offset==0:when=now-datetime.timedelta(minutes=rng.randrange(1,90))
  dates.append(int(when.timestamp()*1000))
 fields=db.execute('PRAGMA table_info(saved_items)').fetchall()
 def record(i,stamp):
  row={}
  for _,name,kind,required,default,_ in fields:
   if required and default is None:row[name]='' if 'TEXT' in kind.upper() else 0
  day=datetime.datetime.fromtimestamp(stamp/1000,zone).strftime('%b %d')
  row.update(id=f'showcase-activity-{i:04}',type='TEXT',title=f'A thought worth keeping · {day}',content='Demo activity for the Cobalt showcase. A small idea, saved for another day.',timestamp=stamp,updatedAt=stamp,orderIndex=float(stamp),foldersJson='[]',genresJson='[]',watchProvidersJson='[]',productionCompaniesJson='[]',bodyLocation='INLINE',preview='A small idea, saved for another day.',byteLength=0,formatVersion=0,bodyMissing=0,isArchived=0,isDeleted=0)
  return row
 original={row[0]:row[1:] for row in db.execute('SELECT id,title,content FROM saved_items')}
 with db:
  if before:
   for i,(ident,) in enumerate(db.execute('SELECT id FROM saved_items ORDER BY id').fetchall()):db.execute('UPDATE saved_items SET timestamp=?,orderIndex=? WHERE id=?',(dates[i],float(dates[i]),ident))
  else:
   for i,stamp in enumerate(dates):
    row=record(i,stamp);keys=list(row)
    db.execute('INSERT INTO saved_items ('+','.join(keys)+') VALUES ('+','.join('?' for _ in keys)+')',[row[k] for k in keys])
 if original!={r[0]:r[1:] for r in db.execute('SELECT id,title,content FROM saved_items') if r[0] in original}:raise RuntimeError('Existing content changed')
 if db.execute('PRAGMA quick_check').fetchone()[0]!='ok':raise RuntimeError('Integrity check failed')
 db.execute('PRAGMA wal_checkpoint(TRUNCATE)');db.close()
 # App remains stopped. The local copy has recovered any rollback journal and
 # checkpointed WAL, so only the complete, verified main DB is installed.
 remote='/data/local/tmp/cobalt-debug-heatmap.db'
 adb('push',str(database),remote)
 adb('shell','run-as',PACKAGE,'cp',remote,'databases/cobalt_db')
 adb('shell','run-as',PACKAGE,'rm','-f','databases/cobalt_db-wal','databases/cobalt_db-shm','databases/cobalt_db-journal')
 adb('shell','rm','-f',remote)
 histogram={}
 for stamp in dates:
  key=datetime.datetime.fromtimestamp(stamp/1000,zone).date().isoformat();histogram[key]=histogram.get(key,0)+1
 result={'package':PACKAGE,'serial':args.serial,'schema':version,'existing_records':before,'total_records':len(dates),'active_days':len(histogram),'date_counts':dict(sorted(histogram.items())),'backup':str(work/'before.tar'),'existing_content_preserved':True,'root_used':False,'apk_built':False}
 report=Path(__file__).with_name('debug-heatmap-result.json');report.write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps({k:result[k] for k in ['package','total_records','active_days','backup','existing_content_preserved','root_used','apk_built']}))
 adb('shell','am','start','-W','-n',PACKAGE+'/com.example.MainActivity')
if __name__=='__main__':main()
