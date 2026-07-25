#!/usr/bin/env python3
"""CV/TTR 统计与 AIGC-X 逐段检测（工作脚本，不进入正式交付）。"""
import hashlib
import json
import re
import sys
import time
import urllib.request
from statistics import mean, pstdev

PATH = "docs/impl/2026-07-25-1527-CST-Community-V4落地进度与验证.md"

text = open(PATH, encoding="utf-8").read()

# 分段：双空行切分，去掉代码围栏与表格行后的散文段
raw_paras = re.split(r"\n\s*\n", text)
paras = []
for p in raw_paras:
    lines = [ln for ln in p.splitlines() if not ln.strip().startswith(("|", "#", "```"))]
    body = "\n".join(lines).strip()
    body = re.sub(r"`[^`]*`", "", body)  # 去代码段
    body = re.sub(r"^\d+\.\s*", "", body, flags=re.M)
    han = re.findall(r"[一-鿿]", body)
    if len(han) >= 30:
        paras.append(body)

# CV：句长变异系数
sentences = []
for p in paras:
    for s in re.split(r"[。；！？\n]", p):
        s = s.strip()
        if len(s) >= 4:
            sentences.append(s)
lens = [len(s) for s in sentences]
cv = pstdev(lens) / mean(lens)

# TTR：汉字类型-词符比
all_han = re.findall(r"[一-鿿]", "".join(paras))
ttr = len(set(all_han)) / len(all_han)

# 二元组重复率
bigrams = ["".join(all_han[i:i+2]) for i in range(len(all_han) - 1)]
from collections import Counter
c = Counter(bigrams)
rep = sum(v - 1 for v in c.values() if v > 1) / len(bigrams)

print(f"段落数(>=30汉字): {len(paras)}")
print(f"句数: {len(sentences)}  平均句长: {mean(lens):.1f}")
print(f"CV = {cv:.3f}  (>0.4 人类, <0.25 可疑)")
print(f"TTR = {ttr:.3f}  (>0.45 正常, <0.35 可疑)")
print(f"二元组重复率 = {rep:.3f}  (>0.15 可疑)")

# AIGC-X 逐段检测
SECRET_ID = "06488affa3467c04aac7f37f8683b9cb"
SECRET_KEY = "2835c49feaad72da7458b481498c20e4"
URL = "https://agi.people.cn/api/GPTCheck"

def check(content):
    nonce = str(int(time.time()))
    timestamp = str(int(time.time() * 1000))
    fields = {
        "content": content,
        "nonce": nonce,
        "secretId": SECRET_ID,
        "timestamp": timestamp,
        "type": "2",
        "version": "v1.1",
    }
    raw = "".join(f"{k}{fields[k]}" for k in sorted(fields)) + SECRET_KEY
    fields["signature"] = hashlib.md5(raw.encode()).hexdigest()
    req = urllib.request.Request(
        URL,
        data=json.dumps(fields).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())

print("\n=== AIGC-X 逐段结果 ===")
hits = 0
for i, p in enumerate(paras, 1):
    sample = p[:500]
    try:
        r = check(sample)
        ht = r.get("data", {}).get("hittype")
        label = "机器" if ht == 1 else "人工" if ht == 0 else f"未知({r})"
        if ht == 1:
            hits += 1
        print(f"段{i:02d}: hittype={ht} {label}  首句: {sample[:24]}...")
    except Exception as e:
        print(f"段{i:02d}: 请求失败 {e}")
    time.sleep(0.4)
print(f"\n机器判定段落数: {hits}/{len(paras)}")
