import json, urllib.request, urllib.parse, ssl

BASE = "https://zip0.com"
HEADERS = {
    "x-tsr-serverFn": "true",
    "accept": "application/x-tss-framed, application/x-ndjson, application/json",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://zip0.com/",
    "Origin": "https://zip0.com",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "sec-ch-ua": '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": '"Windows"',
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-origin",
}

SEARCH = "924908a6328d92c97055b1d048defe7b4f8102dee6907ac36be30470204c7535"
DETAIL = "75b7f04db7f68591c58cd4cbf1a827b99ed16ccbe2e203c1af2d34358cab97df"
SOURCES = "8fa43bc249007c84c4782b4237f5181449e18cd1723d5656f5f3c45c42e2daab"
CATEGORY = "7e1a065dd0c4f105c2195db3af705adf30b75b211920223f5e3f9e62024916a0"

_ctx = {"next_id": 0}

def ser_string(s):
    return {"t": 1, "s": s}

def ser_number(n):
    return {"t": 0, "s": n}

def ser_bool(b):
    return {"t": 2, "s": 2 if b else 3}

def ser_null():
    return {"t": 2, "s": 0}

def ser_undefined():
    return {"t": 2, "s": 1}

def ser(value):
    if value is None:
        return ser_null()
    if isinstance(value, bool):
        return ser_bool(value)
    if isinstance(value, int) or isinstance(value, float):
        return ser_number(value)
    if isinstance(value, str):
        return ser_string(value)
    if isinstance(value, list):
        return ser_array(value)
    if isinstance(value, dict):
        return ser_object(value)
    raise ValueError(f"unsupported type {type(value)}")

def ser_array(items):
    node_id = _ctx["next_id"]; _ctx["next_id"] += 1
    a = [ser(x) for x in items]
    return {"t": 9, "i": node_id, "a": a, "o": 0}

def ser_object(obj):
    node_id = _ctx["next_id"]; _ctx["next_id"] += 1
    k = list(obj.keys())
    v = [ser(obj[key]) for key in k]
    return {"t": 10, "i": node_id, "p": {"k": k, "v": v}, "o": 0}

def call(fn_id, args, method="GET"):
    _ctx["next_id"] = 0
    # build {data: args}
    data_node = ser_object({"data": args})
    payload_json = json.dumps({"t": data_node, "f": 127, "m": []}, separators=(",", ":"), ensure_ascii=False)
    url = f"{BASE}/_serverFn/{fn_id}"
    if method == "GET":
        url += "?payload=" + urllib.parse.quote(payload_json)
        body = None
    else:
        body = payload_json.encode("utf-8")
    req = urllib.request.Request(url, data=body, headers=HEADERS, method=method)
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=40, context=ctx) as resp:
        raw = resp.read().decode("utf-8")
    code = resp.status
    return code, raw

def deser(node):
    t = node["t"]
    if t == 1:
        return node["s"]
    if t == 0:
        s = node["s"]
        return int(s) if isinstance(s, int) else float(s)
    if t == 2:
        return {0: None, 1: None, 2: True, 3: False}[node["s"]]
    if t == 9:
        return [deser(x) for x in node["a"]]
    if t == 10 or t == 11:
        k = node["p"]["k"]
        v = node["p"]["v"]
        return {k[i]: deser(v[i]) for i in range(len(k))}
    return node

def call_and_parse(fn_id, args, method="GET"):
    code, raw = call(fn_id, args, method)
    node = json.loads(raw)
    obj = deser(node)
    return code, obj

if __name__ == "__main__":
    # 1. sources (no args)
    code, obj = call_and_parse(SOURCES, {}, "GET")
    print("=== SOURCES ===")
    print(code, json.dumps(obj, ensure_ascii=False)[:2000])

    # 2. search
    code, obj = call_and_parse(SEARCH, {"query": "星际穿越", "source": "ffzy", "area": "all", "type": "all", "year": "all"}, "GET")
    print("\n=== SEARCH ===")
    print(code, json.dumps(obj, ensure_ascii=False)[:3000])

    # 3. detail
    code, obj = call_and_parse(DETAIL, {"source": "ffzy", "id": "5497"}, "GET")
    print("\n=== DETAIL ===")
    print(code, json.dumps(obj, ensure_ascii=False)[:2000])

    # 4. category browse
    for args in [
        {"category": "movie", "page": 1, "source": "ffzy"},
        {"category": "movie", "page": 1, "source": "ffzy", "area": "all", "type": "all", "year": "all", "sort": "updated"},
    ]:
        print("\n=== CATEGORY", args, "===")
        try:
            code, obj = call_and_parse(CATEGORY, args, "GET")
            print(code, json.dumps(obj, ensure_ascii=False)[:1500])
        except Exception as ex:
            print("ERR", ex)