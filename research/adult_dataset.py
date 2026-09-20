#!/usr/bin/env python3
import csv, json, re, sys, time, warnings
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from urllib.parse import urlparse

import requests
import tldextract
from bs4 import BeautifulSoup
from langdetect import detect, DetectorFactory

DetectorFactory.seed = 0
warnings.filterwarnings("ignore")
try:
    import urllib3
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
except Exception:
    pass

TODAY = datetime.now(timezone.utc).strftime("%Y-%m-%d")
TARGET = 1000
BATCH = 220
MAX_CANDIDATES = 6500
TIMEOUT = (3.5, 6.0)
MAX_HTML = 150_000

SOURCE_URLS = {
    "OISD NSFW Small": "https://raw.githubusercontent.com/sjhgvr/oisd/main/domainswild2_nsfw_small.txt",
    "StevenBlack Porn-only": "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
    "BlockListProject Porn": "https://raw.githubusercontent.com/blocklistproject/Lists/main/porn.txt",
    "hostsVN Adult": "https://raw.githubusercontent.com/bigdargon/hostsVN/master/extensions/adult/hosts-VN",
    "Korea list.txt (exclude)": "https://raw.githubusercontent.com/wpzzz/blocked-sites-in-south-korea/main/list.txt",
    "Korea kr.list (exclude)": "https://raw.githubusercontent.com/wpzzz/blocked-sites-in-south-korea/main/kr.list",
}

USER_AGENT = "Mozilla/5.0 (compatible; LR-Academic-Adult-Web-Study/1.0; +research)"
HEADERS = {
    "User-Agent": USER_AGENT,
    "Accept": "text/html,application/xhtml+xml;q=0.9,*/*;q=0.3",
    "Accept-Language": "en,zh-CN;q=0.8,ja;q=0.7,vi;q=0.7",
    "DNT": "1",
}

# Conservative safety/quality exclusions: we do not include exploitative/illegal-themed or non-consensual-looking domains.
RISK_EXCLUDE = re.compile(
    r"(?:^|[.\-_])(?:childporn|child-porn|preteen|pre-teen|underage|pedo|paedo|pthc|"
    r"zoophil|bestial|animalporn|rape|raped|snuff|gore|torture|revengeporn|revenge-porn|"
    r"spycam|hidden-cam|hiddencam|upskirt|leaked|leakporn|stolenpics|incest|"
    r"teen|teens|teenage|youngteen|schoolgirl|schoolboy|lolita|barelylegal)(?:[.\-_]|$)",
    re.I,
)

RISK_PAGE = re.compile(
    r"\b(?:child\s*porn|pre[- ]?teen|underage|pedo(?:phile)?|paedo(?:phile)?|pthc|"
    r"teen(?:age|s)?|young\s+(?:girl|boy|teen)|school\s*(?:girl|boy)|schoolgirl|schoolboy|"
    r"barely\s*legal|lolita|zoophil\w*|bestial\w*|animal\s*porn|rape\w*|incest\w*|"
    r"snuff|gore|torture\w*|revenge\s*porn|hidden\s*cam|spy\s*cam|upskirt|"
    r"leak(?:ed|s|ing)?\s+(?:porn|nude|nudes|video|videos|photo|photos)|stolen\s+(?:pics|photos|nudes))\b",
    re.I,
)

INFRA_PREFIX = re.compile(r"^(?:cdn\d*|static\d*|img\d*|images\d*|media\d*|assets\d*|api|track|tracker|analytics|ads?|pop|pixel|thumbs?|content|files?)\.", re.I)
HOSTING_BASES = {
    "blogspot.com","tumblr.com","wordpress.com","github.io","b-cdn.net","cloudfront.net",
    "amazonaws.com","azureedge.net","netdna-cdn.com","firebaseapp.com","vercel.app"
}

PARKING_TERMS = [
    "domain for sale","this domain is for sale","buy this domain","domain parking","parked domain",
    "sedo domain parking","afternic","hugedomains","dan.com","undeveloped.com",
    "godaddy auctions","namecheap marketplace","domain has expired","website is for sale",
    "coming soon - domain","this website is parked"
]
ADULT_TERMS = [
    "adult","porn","porno","xxx","sex video","adult video","18+","18 +","nsfw",
    "hentai","doujin","erotic","erotica","nude","webcam","live cam","cam girls",
    "jav","av video","adult movies","sex movies","free porn","gay porn","lesbian porn",
    "成人视频","成人影片","成人内容","色情","成人视频","无码","有码","エロ","アダルト","ポルノ",
    "성인","야동","người lớn","phim sex","phim 18","ảnh nóng","sexe","porno gratuit",
    "pornografía","vídeos porno","filmes pornô","porno gratis"
]
STRONG_DOMAIN_TERMS = [
    "porn","porno","xxx","sex","hentai","doujin","jav","adult","erotic","nude",
    "camgirl","webcam","livecam","xhamster","xnxx","xvideos","redtube","youporn",
    "stripchat","chaturbate","rule34","booru","brazzers","onlyfans"
]

TLD_COUNTRY = {
    "vn":"Vietnam","jp":"Japan","kr":"South Korea","cn":"China","tw":"Taiwan","hk":"Hong Kong",
    "in":"India","id":"Indonesia","th":"Thailand","ph":"Philippines","my":"Malaysia","sg":"Singapore",
    "ru":"Russia","ua":"Ukraine","by":"Belarus","kz":"Kazakhstan","pl":"Poland","cz":"Czechia",
    "sk":"Slovakia","hu":"Hungary","ro":"Romania","bg":"Bulgaria","rs":"Serbia","hr":"Croatia",
    "de":"Germany","fr":"France","it":"Italy","es":"Spain","pt":"Portugal","nl":"Netherlands",
    "be":"Belgium","at":"Austria","ch":"Switzerland","dk":"Denmark","se":"Sweden","no":"Norway",
    "fi":"Finland","uk":"United Kingdom","ie":"Ireland","gr":"Greece","tr":"Türkiye",
    "br":"Brazil","ar":"Argentina","mx":"Mexico","cl":"Chile","co":"Colombia","pe":"Peru",
    "ve":"Venezuela","ca":"Canada","us":"United States","au":"Australia","nz":"New Zealand",
    "za":"South Africa","il":"Israel"
}
GENERICIZED_CCTLD = {"tv","me","cc","io","ai","co","ws","to","fm","la","im","gg","sh","pw","su"}

LANG_REGION = {
    "vi":"Vietnam","ja":"Japan","ko":"South Korea","ru":"Russia/CIS","uk":"Ukraine",
    "de":"German-speaking Europe","fr":"France/Francophone","es":"Spain/Latin America",
    "pt":"Brazil/Portugal","it":"Italy","pl":"Poland","cs":"Czechia","sk":"Slovakia",
    "hu":"Hungary","ro":"Romania","tr":"Türkiye","th":"Thailand","id":"Indonesia",
    "ms":"Malaysia","nl":"Netherlands/Flanders","ar":"Arabic-speaking region",
    "zh-cn":"Greater China","zh-tw":"Taiwan/Greater China","en":"Global/English"
}

COUNTRY_KEYWORDS = [
    (re.compile(r"(?:^|[.\-_])(viet|vietnam|gaigoi|gaixinh|phimsex)(?:[.\-_]|$)", re.I), "Vietnam"),
    (re.compile(r"(?:^|[.\-_])(jav|japan|japanese|jpav)(?:[.\-_]|$)", re.I), "Japan"),
    (re.compile(r"(?:^|[.\-_])(korea|korean)(?:[.\-_]|$)", re.I), "South Korea"),
    (re.compile(r"(?:^|[.\-_])(india|indian|hindi|desi)(?:[.\-_]|$)", re.I), "India"),
    (re.compile(r"(?:^|[.\-_])(thai|thailand)(?:[.\-_]|$)", re.I), "Thailand"),
    (re.compile(r"(?:^|[.\-_])(brasil|brazil)(?:[.\-_]|$)", re.I), "Brazil"),
    (re.compile(r"(?:^|[.\-_])(russia|russian|rusporno)(?:[.\-_]|$)", re.I), "Russia"),
    (re.compile(r"(?:^|[.\-_])(german|deutsch)(?:[.\-_]|$)", re.I), "Germany"),
    (re.compile(r"(?:^|[.\-_])(french|francais)(?:[.\-_]|$)", re.I), "France"),
    (re.compile(r"(?:^|[.\-_])(italian|italia)(?:[.\-_]|$)", re.I), "Italy"),
    (re.compile(r"(?:^|[.\-_])(spanish|espanol)(?:[.\-_]|$)", re.I), "Spain/Latin America"),
    (re.compile(r"(?:^|[.\-_])(arab|arabic)(?:[.\-_]|$)", re.I), "Arabic-speaking region"),
]

def fetch_text(url, timeout=35):
    for attempt in range(3):
        try:
            r = requests.get(url, headers=HEADERS, timeout=timeout)
            r.raise_for_status()
            return r.text
        except Exception as e:
            if attempt == 2:
                raise
            time.sleep(1.2 * (attempt + 1))
    return ""

def normalize_list(text):
    out=set()
    for raw in text.splitlines():
        x=raw.strip().lower()
        if not x or x.startswith("#") or x.startswith("!"):
            continue
        x=re.sub(r"^(?:0\.0\.0\.0|127\.0\.0\.1|::1)\s+", "", x)
        x=re.sub(r"^\|\|", "", x)
        x=re.sub(r"\^.*$", "", x)
        x=re.sub(r"^https?://", "", x)
        x=x.split("/",1)[0].strip().strip(".")
        if x.startswith("*."):
            x=x[2:]
        if x.startswith("www."):
            x=x[4:]
        if ":" in x and not re.match(r"^[a-z0-9.-]+$", x):
            x=x.split(":",1)[0]
        if re.match(r"^(?=.{4,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9-]{2,63}$", x):
            out.add(x)
    return out

def source_sets():
    data={}
    for name,url in SOURCE_URLS.items():
        print("FETCH", name, url, flush=True)
        data[name]=normalize_list(fetch_text(url))
        print(" ->", len(data[name]), "domains", flush=True)
    return data

def base_domain(d):
    e=tldextract.extract(d)
    if not e.suffix:
        return d
    return ".".join([x for x in [e.domain,e.suffix] if x])

def is_infra_or_hosted(d):
    if INFRA_PREFIX.search(d):
        return True
    b=base_domain(d)
    if b in HOSTING_BASES:
        return True
    return False

def candidate_domains(data):
    include_names=["OISD NSFW Small","StevenBlack Porn-only","BlockListProject Porn","hostsVN Adult"]
    korea=data["Korea list.txt (exclude)"] | data["Korea kr.list (exclude)"]
    membership=defaultdict(list)
    for name in include_names:
        for d in data[name]:
            membership[d].append(name)
    c=[]
    for d,srcs in membership.items():
        if len(srcs)<2:
            continue
        if d in korea:
            continue
        if RISK_EXCLUDE.search(d):
            continue
        if is_infra_or_hosted(d):
            continue
        c.append((d,srcs))
    # Highest agreement first; within same score prefer cleaner/shorter hostnames.
    c.sort(key=lambda x:(-len(x[1]), x[0].count("."), len(x[0]), x[0]))
    return c[:MAX_CANDIDATES], korea

def read_html_limited(resp):
    chunks=[]
    total=0
    try:
        for chunk in resp.iter_content(chunk_size=16384):
            if not chunk:
                continue
            chunks.append(chunk)
            total += len(chunk)
            if total >= MAX_HTML:
                break
    except Exception:
        pass
    return b"".join(chunks)[:MAX_HTML]

def page_features(raw):
    if not raw:
        return "", "", ""
    try:
        soup=BeautifulSoup(raw, "html.parser")
        title=(soup.title.get_text(" ", strip=True) if soup.title else "")[:300]
        html_lang=""
        if soup.html and soup.html.get("lang"):
            html_lang=str(soup.html.get("lang")).strip().lower()[:20]
        for tag in soup(["script","style","noscript","svg"]):
            tag.decompose()
        text=" ".join(soup.stripped_strings)
        return title, text[:12000], html_lang
    except Exception:
        s=raw.decode("utf-8","ignore")
        m=re.search(r"<title[^>]*>(.*?)</title>", s, re.I|re.S)
        title=re.sub(r"\s+"," ",m.group(1)).strip()[:300] if m else ""
        text=re.sub(r"<[^>]+>"," ",s)
        return title, re.sub(r"\s+"," ",text)[:12000], ""

def adult_signal(domain, title, text):
    hay=(domain+" "+title+" "+text[:8000]).lower()
    terms=[t for t in ADULT_TERMS if t.lower() in hay]
    strong=[t for t in STRONG_DOMAIN_TERMS if t in domain.lower()]
    return terms, strong

def parking_signal(title,text,final_url):
    hay=(title+" "+text[:5000]+" "+final_url).lower()
    return [t for t in PARKING_TERMS if t in hay]

def language_guess(title,text,html_lang):
    if html_lang:
        h=html_lang.split("-")[0]
        if html_lang.startswith("zh"):
            h="zh-tw" if ("tw" in html_lang or "hant" in html_lang) else "zh-cn"
        if h in LANG_REGION:
            return h
    sample=(title+" "+text[:2500]).strip()
    if len(sample)<60:
        return ""
    try:
        return detect(sample)
    except Exception:
        return ""

def country_guess(domain, language, title, text):
    ext=tldextract.extract(domain)
    suffix=(ext.suffix or "").lower()
    tld=suffix.split(".")[-1] if suffix else ""
    if tld in TLD_COUNTRY and tld not in GENERICIZED_CCTLD:
        return TLD_COUNTRY[tld], "ccTLD", "High"
    hay=(domain+" "+title+" "+text[:1500]).lower()
    for pat,country in COUNTRY_KEYWORDS:
        if pat.search(hay):
            return country, "domain/page regional keyword", "Medium"
    if language in LANG_REGION:
        region=LANG_REGION[language]
        conf="Medium" if language not in {"en","es","pt","de","fr","ar"} else "Low"
        return region, "page language", conf
    return "Global/Unknown", "insufficient public signal", "Low"

def content_class(domain,title,text):
    hay=(domain+" "+title+" "+text[:5000]).lower()
    rules=[
        ("Adult illustration/comics", ["hentai","manga","doujin","comic","toon","booru","anime"]),
        ("Live adult streaming/cams", ["webcam","camgirl","live cam","livecam","cams ","cam ","stripchat","chaturbate","live sex","livesex"]),
        ("Adult dating/escort", ["escort","dating","hookup","personals","gaigoi","callboy","companionship"]),
        ("Adult stories/text", ["erotica","erotic stories","sex stories","story","stories","novel"]),
        ("Adult games/VR", ["sex game","adult game","xxx game","3dsex","3d sex","vr porn","virtual sex"]),
        ("Adult images/galleries", ["gallery","galleries","pics","photo","images","gif","nude pics"]),
        ("Adult search/directory", ["porn search","finder","directory","top sites","porn links","adult links","index of porn"]),
        ("Adult retail", ["sex toy","adult store","adult shop","lingerie","sex doll"]),
        ("Adult video", ["video","videos","tube","movie","movies","jav","porn","porno","xxx"]),
    ]
    for label,keys in rules:
        if any(k in hay for k in keys):
            return label
    return "Mixed adult content"

def purpose_behavior(domain,title,text,ctype):
    hay=(domain+" "+title+" "+text[:6000]).lower()
    if ctype=="Live adult streaming/cams":
        purpose="Real-time adult entertainment"
        behavior="Live streaming / interactive chat"
    elif ctype=="Adult dating/escort":
        purpose="Adult dating or contact marketplace"
        behavior="Profiles / contact / messaging"
    elif ctype=="Adult illustration/comics":
        purpose="Adult illustration/comics catalog"
        behavior="Browse / read"
    elif ctype=="Adult stories/text":
        purpose="Adult textual content"
        behavior="Read / browse"
    elif ctype=="Adult games/VR":
        purpose="Adult interactive entertainment"
        behavior="Interactive / game"
    elif ctype=="Adult images/galleries":
        purpose="Adult image/gallery publishing"
        behavior="Image browsing"
    elif ctype=="Adult search/directory":
        purpose="Adult content discovery/aggregation"
        behavior="Search / outbound navigation"
    elif ctype=="Adult retail":
        purpose="Adult goods retail"
        behavior="E-commerce"
    else:
        purpose="Adult video/content publishing"
        behavior="On-demand streaming / browsing"
    if "torrent" in hay or "download" in hay:
        behavior += " + download"
    if any(k in hay for k in ["upload","user generated","user-generated"]):
        behavior += " + UGC/upload"
    if any(k in hay for k in ["live chat","chat now","messaging"]):
        behavior += " + messaging"
    return purpose, behavior

def access_signals(title,text):
    hay=(title+" "+text[:7000]).lower()
    flags=[]
    pairs=[
        ("Age gate",["18+","18 +","age verification","over 18","adult only"]),
        ("Login/account",["log in","login","sign in","register","create account","sign up"]),
        ("Paid/premium",["premium","subscribe","subscription","membership","buy credits","tokens"]),
        ("Free/ad-supported",["free porn","free videos","watch free","free adult"]),
        ("Download",["download"]),
        ("Live/chat",["live chat","live cam","webcam","chat now"]),
        ("Upload/UGC",["upload video","upload photo","submit video"]),
    ]
    for label,keys in pairs:
        if any(k in hay for k in keys):
            flags.append(label)
    return ", ".join(flags) if flags else "No clear signal from homepage"

def check_domain(item):
    domain, sources=item
    if RISK_EXCLUDE.search(domain):
        return {"domain":domain,"decision":"EXCLUDE","reason":"risk-keyword exclusion","sources":sources}
    last_error=""
    for scheme in ("https","http"):
        url=f"{scheme}://{domain}/"
        try:
            resp=requests.get(url, headers=HEADERS, timeout=TIMEOUT, allow_redirects=True, stream=True, verify=False)
            code=resp.status_code
            final_url=resp.url
            ct=(resp.headers.get("content-type") or "").lower()
            raw=b""
            if code < 500 and ("text/" in ct or "html" in ct or not ct):
                raw=read_html_limited(resp)
            resp.close()
            title,text,html_lang=page_features(raw)
            parking=parking_signal(title,text,final_url)
            adult_terms,strong_terms=adult_signal(domain,title,text)
            final_host=(urlparse(final_url).hostname or "").lower().removeprefix("www.")
            final_base=base_domain(final_host) if final_host else ""
            orig_base=base_domain(domain)
            redirect_changed=bool(final_base and final_base!=orig_base)
            source_count=len(sources)

            if parking:
                return {
                    "domain":domain,"decision":"EXCLUDE","reason":"parking/domain-sale page",
                    "http_status":code,"final_url":final_url,"title":title,"sources":sources
                }
            if RISK_EXCLUDE.search(domain) or RISK_PAGE.search((title+" "+text[:4500]).lower()):
                return {
                    "domain":domain,"decision":"EXCLUDE","reason":"risk/exploitative-theme signal",
                    "http_status":code,"final_url":final_url,"title":title,"sources":sources
                }
            if redirect_changed and not adult_terms and not strong_terms:
                return {
                    "domain":domain,"decision":"EXCLUDE","reason":"redirected to unrelated domain without adult signal",
                    "http_status":code,"final_url":final_url,"title":title,"sources":sources
                }

            reachable = (200 <= code < 400) or code in (401,403,429,451)
            if not reachable:
                last_error=f"HTTP {code}"
                continue

            direct_content_confirm = bool(adult_terms) and code < 400
            protected_confirm = code in (401,403,429,451) and (source_count>=3 or (source_count>=2 and strong_terms))
            multi_source_confirm = source_count>=3 and code<400
            strong_source_confirm = source_count>=2 and code<400 and bool(strong_terms)

            if not (direct_content_confirm or protected_confirm or multi_source_confirm or strong_source_confirm):
                return {
                    "domain":domain,"decision":"EXCLUDE","reason":"reachable but adult content not confirmed from homepage",
                    "http_status":code,"final_url":final_url,"title":title,"sources":sources
                }

            lang=language_guess(title,text,html_lang)
            country,basis,cconf=country_guess(domain,lang,title,text)
            ctype=content_class(domain,title,text)
            purpose,behavior=purpose_behavior(domain,title,text,ctype)
            signals=access_signals(title,text)
            if direct_content_confirm:
                tier="A — homepage adult-content signal"
            elif protected_confirm:
                tier="B — endpoint responds; multi-source adult classification"
            else:
                tier="B — multi-source current adult classification + live endpoint"

            return {
                "domain":domain,
                "decision":"KEEP",
                "verification_tier":tier,
                "http_status":code,
                "scheme":scheme.upper(),
                "final_host":final_host,
                "page_title":title[:220],
                "language":lang or "Unknown",
                "country_region":country,
                "country_basis":basis,
                "country_confidence":cconf,
                "content_type":ctype,
                "site_purpose":purpose,
                "behavior":behavior,
                "access_signals":signals,
                "source_count":source_count,
                "sources":"; ".join(sources),
                "adult_signals":"; ".join((adult_terms[:8] + strong_terms[:5]))[:500],
                "checked_date_utc":TODAY,
            }
        except requests.RequestException as e:
            last_error=str(e)[:180]
        except Exception as e:
            last_error=str(e)[:180]
    return {"domain":domain,"decision":"EXCLUDE","reason":"unreachable from research runner","error":last_error,"sources":"; ".join(sources) if isinstance(sources,list) else sources}

def write_csv(path, rows, fields):
    with open(path,"w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=fields,extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)

def main():
    data=source_sets()
    candidates,korea=candidate_domains(data)
    print("CANDIDATES after multi-source/exclusion:",len(candidates),flush=True)
    kept=[]
    excluded=[]
    tested=0

    for start in range(0,len(candidates),BATCH):
        if len(kept)>=TARGET:
            break
        batch=candidates[start:start+BATCH]
        if not batch:
            break
        print(f"CHECK batch {start}-{start+len(batch)-1}; kept={len(kept)}",flush=True)
        with ThreadPoolExecutor(max_workers=44) as ex:
            futs={ex.submit(check_domain,item):item for item in batch}
            for fut in as_completed(futs):
                tested += 1
                try:
                    row=fut.result()
                except Exception as e:
                    d,src=futs[fut]
                    row={"domain":d,"decision":"EXCLUDE","reason":"checker exception","error":str(e),"sources":"; ".join(src)}
                if row.get("decision")=="KEEP":
                    kept.append(row)
                else:
                    excluded.append(row)
        kept.sort(key=lambda r:(0 if str(r.get("verification_tier","")).startswith("A") else 1, -int(r.get("source_count",0)), r["domain"]))
        if len(kept)>=TARGET:
            kept=kept[:TARGET]
            break

    # Deduplicate final domain just in case.
    uniq={}
    for r in kept:
        uniq.setdefault(r["domain"],r)
    kept=list(uniq.values())[:TARGET]

    fields=[
        "domain","verification_tier","http_status","scheme","final_host","page_title","language",
        "country_region","country_basis","country_confidence","content_type","site_purpose","behavior",
        "access_signals","source_count","sources","adult_signals","checked_date_utc"
    ]
    ex_fields=["domain","decision","reason","http_status","final_url","title","error","sources"]
    write_csv("verified_adult_sites.csv",kept,fields)
    write_csv("excluded_or_unconfirmed.csv",excluded[:2000],ex_fields)

    by_country=Counter(r["country_region"] for r in kept)
    by_content=Counter(r["content_type"] for r in kept)
    by_behavior=Counter(r["behavior"] for r in kept)
    by_tier=Counter(r["verification_tier"] for r in kept)
    by_lang=Counter(r["language"] for r in kept)
    source_combo=Counter(r["sources"] for r in kept)
    stats={
        "generated_utc":datetime.now(timezone.utc).isoformat(),
        "target":TARGET,
        "verified_count":len(kept),
        "tested_count":tested,
        "excluded_count_recorded":len(excluded),
        "korean_list_domains_excluded_from_candidate_pool":len(korea),
        "methodology":{
            "candidate_rule":"Present in at least 2 current adult/NSFW lists; absent from the two user-provided Korean lists.",
            "live_rule":"One lightweight homepage request (HTTPS then HTTP only if HTTPS fails); accept 2xx/3xx or protected 401/403/429/451 with strong multi-source confirmation.",
            "false_positive_rule":"Reject domain-sale/parking pages, unrelated redirects, reachable pages without adult signals unless 3-source agreement, and exploitative/illegal-themed keywords.",
            "country_rule":"ccTLD first; otherwise regional keywords/page language; unknown remains Global/Unknown. This is not server geolocation or legal domicile.",
            "ethical_note":"Homepage HTML only; no media downloaded. Dataset is for aggregate research and does not assess legality in any jurisdiction."
        },
        "source_counts":{k:len(v) for k,v in data.items()},
        "by_country_region":dict(by_country.most_common()),
        "by_content_type":dict(by_content.most_common()),
        "by_behavior":dict(by_behavior.most_common()),
        "by_verification_tier":dict(by_tier.most_common()),
        "by_language":dict(by_lang.most_common()),
        "top_source_combinations":dict(source_combo.most_common(20)),
        "source_urls":SOURCE_URLS,
    }
    with open("stats.json","w",encoding="utf-8") as f:
        json.dump(stats,f,ensure_ascii=False,indent=2)

    with open("README_DATASET.md","w",encoding="utf-8") as f:
        f.write("# Adult-site research dataset\\n\\n")
        f.write(f"Generated: {TODAY} UTC\\n\\n")
        f.write(f"Verified rows: {len(kept)}\\n\\n")
        f.write("This dataset intentionally excludes the two Korean lists supplied by the researcher, domain parking/sale pages, unconfirmed domains, and exploitative/illegal-themed domains identified by conservative keyword filters. Country/region is an inference, not a claim of legal domicile.\\n")

    print(json.dumps({
        "verified":len(kept),
        "tested":tested,
        "excluded":len(excluded),
        "countries":by_country.most_common(12),
        "content":by_content.most_common()
    },ensure_ascii=False),flush=True)

    if len(kept)<850:
        print("ERROR: insufficient verified rows",file=sys.stderr)
        sys.exit(2)

if __name__=="__main__":
    main()
