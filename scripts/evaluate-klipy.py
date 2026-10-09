#!/usr/bin/env python3
"""Read-only provider evaluation. KLIPY_APP_KEY comes from the environment; never logs credentials.
Does not download or retain artwork, nor add provider results to our offline collection.
"""
import json,os,time,urllib.request,urllib.parse,urllib.error
key=os.environ.get('KLIPY_APP_KEY')
if not key: raise SystemExit('Set KLIPY_APP_KEY in the environment for the Obadh Android test key.')
results=[]
for query in ('bangla','বাংলা','bangla hello','gifgari'):
    params=urllib.parse.urlencode(dict(q=query,page=1,per_page=8,customer_id='obadh-provider-evaluation',locale='bd',content_filter='high',format_filter='png,webp'))
    url='https://api.klipy.com/api/v1/'+urllib.parse.quote(key,safe='')+'/stickers/search?'+params
    start=time.monotonic()
    result={'query':query}
    try:
        req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0 Obadh-Provider-Evaluation/0.2'})
        with urllib.request.urlopen(req,timeout=15) as response:
            payload=response.read(2*1024*1024+1)
            if len(payload)>2*1024*1024: raise ValueError('Oversized response')
            data=json.loads(payload)
        nested=data.get('data',{});items=nested.get('data',[]) if isinstance(nested,dict) else []
        result.update(success=data.get('result'),count=len(items),titles=[x.get('title') for x in items],
            formats=sorted({f for x in items for size in x.get('file',{}).values() if isinstance(size,dict) for f in size}),
            advertisers=sum(bool(x.get('advertisement') or x.get('is_ad')) for x in items))
    except urllib.error.HTTPError as e:
        result['http_status']=e.code
        # A raw exception URL embeds the key. Never log it or the untrusted response body.
    except Exception as e:result['error_type']=type(e).__name__
    result['seconds']=round(time.monotonic()-start,3);results.append(result)
print(json.dumps(results,ensure_ascii=False,indent=2))
