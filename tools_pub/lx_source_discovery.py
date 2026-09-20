#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


USER_AGENT = "CoolMusic-LxSourceDiscovery/1.0"
MAX_BODY_BYTES = 2 * 1024 * 1024
MAX_REGISTRY_SOURCES = 80
ALLOWED_HEALTHY_CHANNELS = {"kw", "kg", "tx", "wy", "mg", "xm", "json"}
DEFAULT_SEARCH_QUERIES = [
    # 核心JS音源特征
    '"getMusicUrl" "lx.send" extension:js',
    '"lx.EVENT_NAMES.request" "musicUrl" extension:js',
    '"lx.send" "request" "response" extension:js',
    # JSON音源特征
    '"songUrl" "search" "supportedQualitys" extension:json',
    '"searchApiUrl" "songUrlApiUrl" extension:json',
    # 更宽泛的搜索
    '"getMusicUrlCustom" extension:js',
    'lx-music source extension:js',
    'music-source extension:js',
    '音乐源 extension:js',
    '聚合音源 extension:js',
    '音源 extension:json',
    # 更多关键词
    'lxmusic extension:js',
    '落雪音乐 extension:js',
    'luoxue extension:js',
    'music api extension:js',
]
DEFAULT_REPOSITORY_SEARCH_QUERIES = [
    # 核心查询
    "lx-music-source",
    "lxmusic",
    "落雪音乐",
    # 扩展关键词
    'lx-music-source in:name,description,readme',
    'lx source in:name,description,readme',
    '音乐聚合 in:name,description,readme',
    '聚合音源 in:name,description,readme',
    '无损音乐 in:name,description,readme',
    'music source lx in:name,description,readme',
    # 更多关键词
    '星海音源 in:name,description,readme',
    '六音音源 in:name,description,readme',
    '独家音源 in:name,description,readme',
    # 新增查询 - 覆盖更多变体
    'lxmusic source',
    'lx music js',
    '落雪 音源',
    '音乐源 lx',
    'getMusicUrl',
    'lx.send',
    'EVENT_NAMES',
    'music-api',
    'lx-music',
    'lxmusic api',
    'music source js',
    'music json source',
]
DEFAULT_PUBLIC_SOURCE_REPOSITORIES = [
    # 核心仓库
    ("fly-fish76/lx-source", "main"),
    ("guoyue2010/lxmusic-", "main"),
    # 扩展仓库
    ("sundys/lxmusiclist", "main"),
    ("xing2kong/lxmusic-yinyuan", "main"),
    ("LuoXiaohei-2025/LX-music-collection", "main"),
    ("pdone/lx-music-source", "main"),
    ("wzh15802/lxmusic", "main"),
    # 新增仓库
    ("tfappstore/lx-music-source", "main"),
    ("cdyUuu/lx-music-xinghai-source", "main"),
    ("wangxanshen/lx-music-source", "main"),
    ("skxingyu/lx_music-", "main"),
]
SOURCE_PATH_HINTS = ("音源", "source", "lx", "music", "plugin")
IGNORED_PATH_PARTS = {
    ".github",
    "node_modules",
    "vendor",
    "dist",
    "build",
    "test",
    "tests",
    "fixtures",
}
URL_RE = re.compile(r"https?://[^\s'\"<>)]{8,}")


class HttpClient:
    def __init__(self, token: str | None, timeout: int) -> None:
        self.token = token
        self.timeout = timeout

    def get_text(self, url: str, accept: str = "*/*", authenticated: bool = True) -> str:
        headers = {
            "Accept": accept,
            "User-Agent": USER_AGENT,
        }
        if authenticated and self.token and "api.github.com" in url:
            headers["Authorization"] = f"Bearer {self.token}"
            headers["X-GitHub-Api-Version"] = "2022-11-28"
        request = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            data = response.read(MAX_BODY_BYTES + 1)
            if len(data) > MAX_BODY_BYTES:
                raise ValueError("response too large")
            return data.decode("utf-8", errors="replace")

    def get_json(self, url: str, authenticated: bool = True) -> Any:
        return json.loads(
            self.get_text(
                url,
                accept="application/vnd.github+json",
                authenticated=authenticated,
            )
        )


def utc_now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def normalize_url(url: str) -> str:
    url = url.strip().rstrip(".,;]")
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        return ""
    if parsed.netloc == "github.com":
        parts = parsed.path.strip("/").split("/")
        if len(parts) >= 5 and parts[2] == "blob":
            owner, repo, _, branch = parts[:4]
            path = "/".join(parts[4:])
            parsed = urllib.parse.urlparse(
                f"https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}"
            )
    encoded_path = urllib.parse.quote(
        urllib.parse.unquote(parsed.path),
        safe="/:@-._~!$&'()*+,;=",
    )
    encoded_query = urllib.parse.quote(
        urllib.parse.unquote(parsed.query),
        safe="=&?/:@-._~!$'()*+,;",
    )
    return urllib.parse.urlunparse(
        parsed._replace(path=encoded_path, query=encoded_query, fragment="")
    )


def truncate_text(value: Any, limit: int) -> str:
    return str(value or "").strip()[:limit]


def looks_like_lx_js(body: str) -> bool:
    trimmed = body.lstrip()
    return (
        trimmed.startswith(("const ", "let ", "var ", "async function", "module.exports"))
        or "exports.default" in trimmed
        or "lx.send" in trimmed
        or ("getMusicUrl" in trimmed and "function" in trimmed)
    )


def parse_js_metadata(script: str) -> dict[str, Any]:
    header_match = re.search(r"/\*[\s\S]+?\*/", script)
    header = header_match.group(0) if header_match else ""

    def from_header(tag: str) -> str:
        match = re.search(rf"^\s*\*\s*@{re.escape(tag)}\s+(.+)$", header, re.I | re.M)
        return match.group(1).strip() if match else ""

    def from_literal(*keys: str) -> str:
        for key in keys:
            match = re.search(rf"['\"]?{re.escape(key)}['\"]?\s*:\s*['\"]([^'\"]+)['\"]", script, re.I)
            if match:
                return match.group(1).strip()
        return ""

    source_ids = [source_id for source_id in ["kw", "kg", "tx", "wy", "mg"] if re.search(rf"\b{source_id}\b", script, re.I)]
    return {
        "name": from_header("name") or from_literal("name", "title") or "LX JS Source",
        "description": from_header("description") or from_literal("description", "desc"),
        "author": from_header("author") or from_literal("author"),
        "version": from_header("version") or from_literal("version"),
        "sourceIds": source_ids or ["kw", "kg", "tx", "wy", "mg"],
    }


def parse_json_definition(body: str) -> dict[str, Any] | None:
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        return None
    if not isinstance(root, dict):
        return None
    api = root.get("api")
    if not isinstance(api, dict):
        return None

    def api_url(key: str) -> str:
        raw = api.get(key)
        if isinstance(raw, str):
            return raw.strip()
        if isinstance(raw, dict):
            return str(raw.get("url", "")).strip()
        return ""

    search_url = api_url("search")
    song_url = api_url("songUrl") or api_url("url")
    if not search_url or not song_url:
        return None
    if str(root.get("type", "music") or "music") != "music":
        return None
    qualities = root.get("supportedQualitys") or root.get("supportedQualities") or []
    if isinstance(qualities, dict):
        qualities = [key for key, enabled in qualities.items() if enabled]
    elif isinstance(qualities, str):
        qualities = [qualities]
    elif not isinstance(qualities, list):
        qualities = []
    return {
        "name": str(root.get("name") or "LX Source").strip(),
        "description": str(root.get("description") or "").strip(),
        "author": str(root.get("author") or "").strip(),
        "version": str(root.get("version") or "").strip(),
        "searchApiUrl": search_url,
        "songUrlApiUrl": song_url,
        "supportedQualities": [str(item).lower() for item in qualities if str(item).strip()],
    }


def build_api_url(base_url: str, params: dict[str, str]) -> str:
    parsed = urllib.parse.urlsplit(base_url)
    existing = {key.lower() for key, _ in urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)}
    query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    for key, value in params.items():
        if key.lower() not in existing:
            query.append((key, value))
    return urllib.parse.urlunsplit(parsed._replace(query=urllib.parse.urlencode(query)))


def parse_search_response(body: str) -> list[dict[str, Any]]:
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        return []
    if isinstance(root, dict) and "code" in root and int(root.get("code") or 0) not in {0, 200}:
        return []
    data = None
    if isinstance(root, dict):
        raw_data = root.get("data")
        if isinstance(raw_data, list):
            data = raw_data
        elif isinstance(raw_data, dict):
            data = raw_data.get("list") or raw_data.get("songs") or raw_data.get("items")
        data = data or root.get("list") or root.get("songs") or root.get("items") or root.get("result")
    if not isinstance(data, list):
        return []
    items: list[dict[str, Any]] = []
    for item in data:
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or item.get("title") or "").strip()
        music_id = str(item.get("musicId") or item.get("id") or item.get("songmid") or "").strip()
        if name and music_id:
            items.append({"name": name, "musicId": music_id})
    return items


def parse_song_url_response(body: str) -> str:
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        return ""
    if isinstance(root, dict) and "code" in root and int(root.get("code") or 0) not in {0, 200}:
        return ""
    url = ""
    if isinstance(root, dict):
        data = root.get("data")
        if isinstance(data, dict):
            url = str(data.get("url") or "").strip()
        elif isinstance(data, str):
            url = data.strip()
        url = url or str(root.get("url") or "").strip()
    return url if url.startswith(("http://", "https://")) else ""


def probe_json_source(definition: dict[str, Any], http: HttpClient) -> list[str]:
    qualities = definition.get("supportedQualities") or []
    quality_order = [quality for quality in ["320k", "128k", "flac", "flac24bit"] if not qualities or quality in qualities]
    for keyword in ["\u6674\u5929 \u5468\u6770\u4f26", "\u6674\u5929"]:
        search_url = build_api_url(
            definition["searchApiUrl"],
            {
                "keyword": keyword,
                "search": keyword,
                "key": keyword,
                "limit": "8",
                "count": "8",
                "page": "1",
                "pagesize": "8",
            },
        )
        try:
            items = parse_search_response(http.get_text(search_url))
        except Exception:
            continue
        for item in items[:4]:
            for quality in quality_order or ["128k"]:
                song_url = build_api_url(
                    definition["songUrlApiUrl"],
                    {
                        "id": item["musicId"],
                        "musicId": item["musicId"],
                        "quality": quality,
                        "br": quality,
                    },
                )
                try:
                    if parse_song_url_response(http.get_text(song_url)):
                        return ["json"]
                except Exception:
                    continue
    return []


def probe_js_source(
    body: str,
    preload_path: Path,
    runner_path: Path,
    node_binary: str | None,
    timeout: int,
) -> list[str]:
    if not node_binary:
        return []
    with tempfile.TemporaryDirectory(prefix="lx-source-") as tmp_dir:
        source_path = Path(tmp_dir) / "source.js"
        source_path.write_text(body, encoding="utf-8")
        probe_env = os.environ.copy()
        for secret_name in ("GITHUB_TOKEN", "GH_TOKEN", "LX_SOURCE_CANDIDATE_URLS"):
            probe_env.pop(secret_name, None)
        completed = subprocess.run(
            [
                node_binary,
                str(runner_path),
                "--source-file",
                str(source_path),
                "--preload",
                str(preload_path),
            ],
            text=True,
            capture_output=True,
            timeout=timeout,
            check=False,
            env=probe_env,
        )
    if completed.returncode != 0:
        reason = completed.stderr.strip().splitlines()[-1] if completed.stderr.strip() else "runner failed"
        print(f"warning: JS probe runner failed: {reason[:300]}", file=sys.stderr)
        return []
    try:
        result = json.loads(completed.stdout.strip().splitlines()[-1])
    except Exception:
        return []
    channels = result.get("healthyChannels")
    healthy = [str(channel) for channel in channels] if isinstance(channels, list) else []
    if not healthy:
        details = result.get("details")
        print(
            "JS probe returned no healthy channel: "
            f"{json.dumps(details, ensure_ascii=False)[:500]}",
            file=sys.stderr,
        )
    return healthy


def extract_urls(text: str) -> set[str]:
    return {normalized for raw in URL_RE.findall(text) if (normalized := normalize_url(raw))}


def load_candidate_file(path: Path) -> set[str]:
    if not path.exists():
        return set()
    urls = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        normalized = normalize_url(line)
        if normalized:
            urls.add(normalized)
    return urls


def load_env_candidates() -> set[str]:
    raw = os.environ.get("LX_SOURCE_CANDIDATE_URLS", "")
    urls = set()
    for part in re.split(r"[\n, ]+", raw):
        normalized = normalize_url(part)
        if normalized:
            urls.add(normalized)
    return urls


def load_existing_candidates(path: Path) -> set[str]:
    if not path.exists():
        return set()
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return set()
    urls = set()
    for item in data.get("sources", []):
        if isinstance(item, dict):
            normalized = normalize_url(str(item.get("url") or ""))
            if normalized:
                urls.add(normalized)
    return urls


def github_search_candidates(http: HttpClient, max_files: int) -> set[str]:
    if not http.token or max_files <= 0:
        return set()
    queries_raw = os.environ.get("LX_SOURCE_SEARCH_QUERIES", "")
    queries = [line.strip() for line in queries_raw.splitlines() if line.strip()] or DEFAULT_SEARCH_QUERIES
    candidates: set[str] = set()
    files_seen = 0
    for query in queries:
        if files_seen >= max_files:
            break
        search_url = "https://api.github.com/search/code?" + urllib.parse.urlencode(
            {"q": query, "per_page": min(20, max_files - files_seen)}
        )
        try:
            result = http.get_json(search_url)
        except Exception as error:
            print(f"warning: GitHub code search failed for {query!r}: {error}", file=sys.stderr)
            continue
        for item in result.get("items", []):
            if files_seen >= max_files:
                break
            api_url = item.get("url")
            if not api_url:
                continue
            try:
                content_info = http.get_json(api_url)
                download_url = normalize_url(str(content_info.get("download_url") or ""))
                if not download_url:
                    continue
                files_seen += 1
                body = http.get_text(download_url)
            except Exception:
                continue
            if looks_like_lx_js(body) or parse_json_definition(body):
                candidates.add(download_url)
    print(f"GitHub code search found {len(candidates)} candidate URLs from {files_seen} files")
    return candidates


def source_path_score(path: str, size: Any) -> int:
    normalized = path.replace("\\", "/").strip("/")
    lowered = normalized.lower()
    parts = set(lowered.split("/"))
    if parts & IGNORED_PATH_PARTS:
        return -1
    if not lowered.endswith((".js", ".json")):
        return -1
    if lowered.endswith(("package.json", "package-lock.json", "tsconfig.json")):
        return -1
    if isinstance(size, int) and (size <= 0 or size > MAX_BODY_BYTES):
        return -1

    filename = lowered.rsplit("/", 1)[-1]
    score = 0
    if "音源" in normalized:
        score += 100
    if "/sources/" in f"/{lowered}/" or lowered.startswith("sources/"):
        score += 80
    version_matches = re.findall(r"(?i)v(\d{6})", normalized)
    if version_matches:
        score += max(int(version) for version in version_matches)
    if "推荐" in normalized:
        score += 1_000_000
    for hint in SOURCE_PATH_HINTS:
        if hint in filename:
            score += 30
        elif hint in lowered:
            score += 10
    return score


def github_repository_candidates(
    http: HttpClient,
    max_repositories: int,
    max_files: int,
) -> set[str]:
    if max_repositories <= 0 or max_files <= 0:
        return set()

    queries_raw = os.environ.get("LX_SOURCE_REPOSITORY_SEARCH_QUERIES", "")
    queries = (
        [line.strip() for line in queries_raw.splitlines() if line.strip()]
        or DEFAULT_REPOSITORY_SEARCH_QUERIES
    )
    current_repository = os.environ.get("GITHUB_REPOSITORY", "").lower()
    repositories: list[dict[str, str]] = [
        {"full_name": full_name, "default_branch": default_branch}
        for full_name, default_branch in DEFAULT_PUBLIC_SOURCE_REPOSITORIES[:max_repositories]
    ]
    seen_repositories: set[str] = set()
    seen_repositories.update(repository["full_name"].lower() for repository in repositories)

    for query in queries:
        if len(repositories) >= max_repositories:
            break
        search_url = "https://api.github.com/search/repositories?" + urllib.parse.urlencode(
            {
                "q": query,
                "sort": "updated",
                "order": "desc",
                "per_page": min(10, max_repositories - len(repositories)),
            }
        )
        try:
            # Installation tokens only see repositories installed for the app.
            # Public repository search is deliberately unauthenticated here.
            result = http.get_json(search_url, authenticated=False)
        except Exception as error:
            print(f"warning: public repository search failed for {query!r}: {error}", file=sys.stderr)
            continue
        for item in result.get("items", []):
            full_name = str(item.get("full_name") or "").strip()
            default_branch = str(item.get("default_branch") or "").strip()
            repository_key = full_name.lower()
            if (
                not full_name
                or not default_branch
                or repository_key == current_repository
                or repository_key in seen_repositories
            ):
                continue
            repositories.append({"full_name": full_name, "default_branch": default_branch})
            seen_repositories.add(repository_key)
            if len(repositories) >= max_repositories:
                break

    candidates: set[str] = set()
    files_seen = 0
    for repository in repositories:
        if files_seen >= max_files:
            break
        full_name = repository["full_name"]
        branch = repository["default_branch"]
        tree_url = (
            f"https://api.github.com/repos/{urllib.parse.quote(full_name, safe='/')}/git/trees/"
            f"{urllib.parse.quote(branch, safe='')}?recursive=1"
        )
        try:
            tree = http.get_json(tree_url, authenticated=False)
        except Exception as error:
            print(f"warning: repository tree failed for {full_name}: {error}", file=sys.stderr)
            continue

        source_files = []
        for item in tree.get("tree", []):
            if item.get("type") != "blob":
                continue
            path = str(item.get("path") or "")
            score = source_path_score(path, item.get("size"))
            if score > 0:
                source_files.append((score, path))
        source_files.sort(key=lambda value: (-value[0], value[1].lower()))

        for _, path in source_files[:12]:
            if files_seen >= max_files:
                break
            raw_url = (
                f"https://raw.githubusercontent.com/{full_name}/"
                f"{urllib.parse.quote(branch, safe='')}/"
                f"{urllib.parse.quote(path, safe='/')}"
            )
            files_seen += 1
            try:
                body = http.get_text(raw_url)
            except Exception:
                continue
            if looks_like_lx_js(body) or parse_json_definition(body):
                candidates.add(raw_url)

    print(
        f"public repository search found {len(candidates)} candidate URLs "
        f"from {files_seen} files in {len(repositories)} repositories"
    )
    return candidates


def forum_search_candidates(http: HttpClient, max_files: int) -> set[str]:
    """搜索LX Music论坛和Telegram群组分享的音源链接"""
    if max_files <= 0:
        return set()

    candidates: set[str] = set()
    files_seen = 0

    # LX Music官方论坛
    forum_urls = [
        "https://lxmusic.tonebay.cn/",
        "https://www.lxmusic.com/",
        "https://github.com/lyswhut/lx-music-desktop/issues",
    ]

    for forum_url in forum_urls:
        if files_seen >= max_files:
            break
        try:
            body = http.get_text(forum_url, authenticated=False)
            # 从论坛页面中提取GitHub链接
            github_links = re.findall(
                r'https?://github\.com/[^\s\'"<>)\]]+/blob/[^\s\'"<>)\]]+\.js',
                body,
            )
            github_links.extend(
                re.findall(
                    r'https?://github\.com/[^\s\'"<>)\]]+/blob/[^\s\'"<>)\]]+\.json',
                    body,
                )
            )
            # 也提取raw.githubusercontent.com链接
            raw_links = re.findall(
                r'https?://raw\.githubusercontent\.com/[^\s\'"<>)\]]+\.js',
                body,
            )
            raw_links.extend(
                re.findall(
                    r'https?://raw\.githubusercontent\.com/[^\s\'"<>)\]]+\.json',
                    body,
                )
            )

            all_links = set(github_links + raw_links)
            for link in all_links:
                if files_seen >= max_files:
                    break
                # 转换blob链接为raw链接
                raw_url = link.replace("/blob/", "/raw/") if "/blob/" in link else link
                try:
                    content = http.get_text(raw_url, authenticated=False)
                    if looks_like_lx_js(content) or parse_json_definition(content):
                        candidates.add(raw_url)
                        files_seen += 1
                except Exception:
                    continue
        except Exception as error:
            print(f"warning: forum search failed for {forum_url}: {error}", file=sys.stderr)
            continue

    print(
        f"forum search found {len(candidates)} candidate URLs "
        f"from {files_seen} files"
    )
    return candidates


def validate_candidate(
    url: str,
    http: HttpClient,
    preload_path: Path,
    runner_path: Path,
    node_binary: str | None,
    js_timeout: int,
) -> dict[str, Any] | None:
    if urllib.parse.urlparse(url).scheme != "https":
        print(f"skip {url}: registry candidates must use HTTPS", file=sys.stderr)
        return None
    try:
        body = http.get_text(url)
    except Exception as error:
        print(f"skip {url}: fetch failed: {error}", file=sys.stderr)
        return None

    now = utc_now_iso()
    if looks_like_lx_js(body):
        meta = parse_js_metadata(body)
        healthy = probe_js_source(body, preload_path, runner_path, node_binary, js_timeout)
        kind = "js"
    else:
        definition = parse_json_definition(body)
        if not definition:
            return None
        meta = definition
        healthy = probe_json_source(definition, http)
        kind = "json"

    if not healthy:
        print(f"skip {url}: no healthy channel", file=sys.stderr)
        return None

    healthy = list(dict.fromkeys(channel for channel in healthy if channel in ALLOWED_HEALTHY_CHANNELS))
    if not healthy:
        return None

    return {
        "name": truncate_text(meta.get("name") or "LX Source", 200),
        "kind": kind,
        "url": url,
        "description": truncate_text(meta.get("description"), 500),
        "author": truncate_text(meta.get("author"), 200),
        "version": truncate_text(meta.get("version"), 80),
        "sourcePage": "",
        "healthyChannels": healthy,
        "lastValidatedAt": now,
    }


def validate_registry_data(registry: Any) -> None:
    if not isinstance(registry, dict):
        raise ValueError("registry root must be an object")
    if registry.get("schemaVersion") != 1:
        raise ValueError("unsupported registry schemaVersion")
    if registry.get("minimumHealthyChannels") != 1:
        raise ValueError("minimumHealthyChannels must be 1")
    sources = registry.get("sources")
    if not isinstance(sources, list) or len(sources) > MAX_REGISTRY_SOURCES:
        raise ValueError("sources must be a bounded array")
    generated_at = registry.get("generatedAt")
    if not isinstance(generated_at, str) or (sources and not generated_at):
        raise ValueError("generatedAt is required when sources are present")
    seen_urls: set[str] = set()
    for index, item in enumerate(sources):
        if not isinstance(item, dict):
            raise ValueError(f"sources[{index}] must be an object")
        url = item.get("url")
        parsed = urllib.parse.urlparse(url if isinstance(url, str) else "")
        if parsed.scheme != "https" or not parsed.netloc or len(url) > 2048:
            raise ValueError(f"sources[{index}].url must be a valid HTTPS URL")
        if url in seen_urls:
            raise ValueError(f"duplicate source URL: {url}")
        seen_urls.add(url)
        if item.get("kind") not in {"js", "json"}:
            raise ValueError(f"sources[{index}].kind is invalid")
        if not isinstance(item.get("name"), str) or not item["name"] or len(item["name"]) > 200:
            raise ValueError(f"sources[{index}].name is invalid")
        channels = item.get("healthyChannels")
        if (
            not isinstance(channels, list)
            or not channels
            or len(channels) > len(ALLOWED_HEALTHY_CHANNELS)
            or any(channel not in ALLOWED_HEALTHY_CHANNELS for channel in channels)
        ):
            raise ValueError(f"sources[{index}].healthyChannels is invalid")
        for field, limit in (("description", 500), ("author", 200), ("version", 80)):
            value = item.get(field, "")
            if not isinstance(value, str) or len(value) > limit:
                raise ValueError(f"sources[{index}].{field} is invalid")


def validate_registry_file(path: Path) -> None:
    if not path.is_file() or path.stat().st_size > MAX_BODY_BYTES:
        raise ValueError("registry file is missing or too large")
    validate_registry_data(json.loads(path.read_text(encoding="utf-8")))


def write_registry(path: Path, sources: list[dict[str, Any]]) -> None:
    registry = {
        "schemaVersion": 1,
        "generatedAt": utc_now_iso(),
        "minimumHealthyChannels": 1,
        "probeSong": {
            "name": "\u6674\u5929",
            "artist": "\u5468\u6770\u4f26",
            "album": "\u53f6\u60e0\u7f8e",
        },
        "sources": sorted(sources, key=lambda item: (item["name"].lower(), item["url"])),
    }
    validate_registry_data(registry)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    tmp_path.write_text(json.dumps(registry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    tmp_path.replace(path)


def main() -> int:
    parser = argparse.ArgumentParser(description="Discover and verify LX Music source URLs.")
    parser.add_argument("--candidate-file", default="tools_pub/lx_source_candidates.txt")
    parser.add_argument("--output", default="docs/online-sources.json")
    parser.add_argument("--preload", default="app/src/main/assets/script/user-api-preload.js")
    parser.add_argument("--js-runner", default="tools_pub/lx_js_probe_runner.js")
    parser.add_argument("--max-candidates", type=int, default=80)
    parser.add_argument("--max-github-files", type=int, default=40)
    parser.add_argument("--max-repositories", type=int, default=12)
    parser.add_argument("--max-repository-files", type=int, default=30)
    parser.add_argument("--timeout", type=int, default=15)
    parser.add_argument("--js-timeout", type=int, default=80)
    parser.add_argument("--no-github-search", action="store_true")
    parser.add_argument("--validate-only", help="Validate an existing registry file and exit.")
    args = parser.parse_args()

    root = Path.cwd()
    if args.validate_only:
        validate_registry_file(root / args.validate_only)
        print(f"registry is valid: {root / args.validate_only}")
        return 0
    output_path = root / args.output
    candidate_file = root / args.candidate_file
    preload_path = root / args.preload
    runner_path = root / args.js_runner
    http = HttpClient(
        token=os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN"),
        timeout=args.timeout,
    )
    node_binary = shutil.which("node")

    existing_candidates = load_existing_candidates(output_path)
    file_candidates = load_candidate_file(candidate_file)
    env_candidates = load_env_candidates()
    candidates = existing_candidates | file_candidates | env_candidates
    code_search_candidates: set[str] = set()
    repository_candidates: set[str] = set()
    forum_candidates: set[str] = set()
    if not args.no_github_search:
        code_search_candidates = github_search_candidates(http, args.max_github_files)
        repository_candidates = github_repository_candidates(
            http,
            max_repositories=args.max_repositories,
            max_files=args.max_repository_files,
        )
        forum_candidates = forum_search_candidates(http, args.max_repository_files)
        candidates.update(code_search_candidates)
        candidates.update(repository_candidates)
        candidates.update(forum_candidates)

    print(
        "candidate sources: "
        f"existing={len(existing_candidates)} "
        f"file={len(file_candidates)} "
        f"env={len(env_candidates)} "
        f"code_search={len(code_search_candidates)} "
        f"repository_search={len(repository_candidates)} "
        f"forum_search={len(forum_candidates)}"
    )

    ordered_candidates = sorted(candidates)[: max(args.max_candidates, 0)]
    print(f"validating {len(ordered_candidates)} candidate source URLs")
    sources = []
    seen = set()
    for url in ordered_candidates:
        entry = validate_candidate(
            url=url,
            http=http,
            preload_path=preload_path,
            runner_path=runner_path,
            node_binary=node_binary,
            js_timeout=args.js_timeout,
        )
        if not entry or entry["url"] in seen:
            continue
        sources.append(entry)
        seen.add(entry["url"])

    write_registry(output_path, sources)
    print(f"wrote {len(sources)} verified sources to {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
