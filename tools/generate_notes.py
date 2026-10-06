"""Precompute Gemma 4 field notes (common name + short detail) for every BioCLIP plant label via local Ollama."""
import json, re, sys, time, urllib.request
from concurrent.futures import ThreadPoolExecutor

MODEL = sys.argv[1] if len(sys.argv) > 1 else "gemma4:e4b"
ASSETS = r"app\src\main\assets\bioclip"
OUT = ASSETS + r"\species_notes.json"
labels = json.load(open(ASSETS + r"\taxa_labels.json", encoding="utf8"))
try:
    notes = json.load(open(OUT, encoding="utf8"))
except FileNotFoundError:
    notes = {}

def ask(name):
    prompt = (f'Plant species: {name}.\nReply with ONLY a JSON object: {{"common": "<most widely used English common name, or empty string if none exists>", '
              '"detail": "<factual field-guide description in at most 30 words: appearance, habitat or use. No markdown.>"}')
    body = json.dumps({"model": MODEL, "prompt": prompt, "stream": False, "think": False, "format": "json",
                       "options": {"temperature": 0.2, "num_predict": 160}}).encode()
    for _ in range(3):
        try:
            req = urllib.request.Request("http://localhost:11434/api/generate", body, {"Content-Type": "application/json"})
            r = json.loads(json.load(urllib.request.urlopen(req, timeout=300))["response"])
            detail = re.sub(r"[*_#`>]|\s+", lambda m: " " if m.group().isspace() else "", str(r.get("detail", ""))).strip()
            if len(detail) >= 20:
                return {"common": str(r.get("common", "")).strip().title() if r.get("common") else "", "detail": detail[:260]}
        except Exception as e:
            time.sleep(2)
    return None

todo = [l["scientific"] for l in labels if l["scientific"] not in notes]
print(len(todo), "to do", flush=True)
done = 0
def work(n):
    return n, ask(n)
with ThreadPoolExecutor(4) as ex:
    for n, r in ex.map(work, todo):
        if r: notes[n] = r
        done += 1
        if done % 50 == 0:
            json.dump(notes, open(OUT, "w", encoding="utf8"), ensure_ascii=False, indent=0)
            print(done, len(notes), flush=True)
json.dump(notes, open(OUT, "w", encoding="utf8"), ensure_ascii=False, indent=0)
print("finished", len(notes), "of", len(labels))
