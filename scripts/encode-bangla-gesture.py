#!/usr/bin/env python3
"""Encode Obadh phonetic paths in AOSP static dictionary format 202 (same as English).
No Android runtime dependency. See the pinned foundation's makedict/FormatSpec.java.
The Roman keys describe geometry; Bengali shortcuts are the only allowed output.
"""
import hashlib,json,math,re,struct,sys
from collections import deque
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent

def string(text):
    return b''.join(bytes([ord(c)]) if 32<=ord(c)<256 else ord(c).to_bytes(3,'big') for c in text)+b'\x1f'

def encode(rows):
    root={}
    for roman,word,frequency in rows:
        node=root
        for c in roman:node=node.setdefault(c,{})
        node.setdefault('',[]).append((frequency,word))
    arrays=[];queue=deque([root])
    while queue:
        array=queue.popleft();arrays.append(array)
        for key,node in sorted(array.items()):
            if key and any(c for c in node):queue.append(node)
    body=bytearray();positions={};fixups=[]
    for array in arrays:
        positions[id(array)]=len(body)
        children=[(key,node) for key,node in sorted(array.items()) if key]
        assert len(children)<=26
        body.append(len(children))
        for key,node in children:
            targets=sorted(set(node.get('',[])),reverse=True)[:4]
            has_children=any(k for k in node)
            # Gesture decoders require searchable Roman words. The app maps these
            # private search keys to their Bengali shortcuts before any UI/editor output.
            flags=(0xc0 if has_children else 0)|(0x18 if targets else 0)
            body.extend([flags,ord(key)])
            if targets:
                body.append(min(250,max(1,round(16*math.log2(targets[0][0]+1)))))
            if has_children:fixups.append((len(body),id(node)));body.extend(b'\0\0\0')
            if targets:
                shortcuts=b''.join(bytes([(0x80 if i+1<len(targets) else 0)|min(14,max(1,14-i))])+string(word) for i,(_,word) in enumerate(targets))
                body.extend(struct.pack('>H',len(shortcuts)+2)+shortcuts)
    for offset,child in fixups:
        relative=positions[child]-offset
        assert 0<relative<0x1000000
        body[offset:offset+3]=relative.to_bytes(3,'big')
    attributes={'dictionary':'main:bn-BD','locale':'en_US','version':'obadh-gesture-1','description':'Obadh C ABI verified phonetic gesture vocabulary'}
    header=b''.join(string(k)+string(v) for k,v in attributes.items())
    return struct.pack('>IHHI',0x9bc13afe,202,0,len(header)+12)+header+body

if __name__=='__main__':
    rows=[]
    for line in Path(sys.argv[1]).read_text().splitlines():
        roman,word,freq=line.split('\t');rows.append((roman,word,int(freq)))
    data=encode(rows)
    destination=ROOT/'app/src/main/assets/ObadhGesture/main.dict';destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_bytes(data)
    engine=re.search(r'obadh_engine = \{ version = "=([\d.]+)"',(ROOT/'rust/obadh-jni/Cargo.toml').read_text()).group(1)
    metadata={'format':202,'revision':1,'engine':engine,'abi':2,'words':len({x[1] for x in rows}),'spellings':len(rows),'paths':len({x[0] for x in rows}),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'source_fst_sha256':hashlib.sha256((ROOT/'app/src/main/assets/ObadhModels/autocorrect/bn.fst').read_bytes()).hexdigest()}
    destination.with_name('metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
    print(f'Encoded {len(rows)} spellings / {metadata["paths"]} paths in {len(data):,} bytes.')
