import importlib.util
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "lx_source_discovery.py"
SPEC = importlib.util.spec_from_file_location("lx_source_discovery", SCRIPT_PATH)
assert SPEC and SPEC.loader
DISCOVERY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DISCOVERY)


class FakeHttpClient:
    def get_json(self, url: str, authenticated: bool = True):
        if "/search/repositories?" in url:
            return {
                "items": [
                    {
                        "full_name": "owner/music-sources",
                        "default_branch": "main",
                    }
                ]
            }
        if "/git/trees/" in url:
            return {
                "tree": [
                    {
                        "type": "blob",
                        "path": "sources/星海 音源.js",
                        "size": 1024,
                    },
                    {
                        "type": "blob",
                        "path": "package.json",
                        "size": 200,
                    },
                ]
            }
        raise AssertionError(f"unexpected URL: {url}")

    def get_text(self, url: str, accept: str = "*/*", authenticated: bool = True):
        if "raw.githubusercontent.com" not in url:
            raise AssertionError(f"unexpected URL: {url}")
        return "const source = {}; lx.send('inited'); function getMusicUrl() {}"


class RepositoryDiscoveryTest(unittest.TestCase):
    def test_public_repository_scan_finds_encoded_source_url(self):
        original_repositories = DISCOVERY.DEFAULT_PUBLIC_SOURCE_REPOSITORIES
        DISCOVERY.DEFAULT_PUBLIC_SOURCE_REPOSITORIES = []
        self.addCleanup(
            setattr,
            DISCOVERY,
            "DEFAULT_PUBLIC_SOURCE_REPOSITORIES",
            original_repositories,
        )
        candidates = DISCOVERY.github_repository_candidates(
            FakeHttpClient(),
            max_repositories=1,
            max_files=4,
        )

        self.assertEqual(1, len(candidates))
        url = candidates.pop()
        self.assertIn("owner/music-sources/main/sources/", url)
        self.assertIn("%E6%98%9F%E6%B5%B7%20%E9%9F%B3%E6%BA%90.js", url)

    def test_source_path_scoring_rejects_dependency_metadata(self):
        self.assertLess(DISCOVERY.source_path_score("package.json", 200), 0)
        self.assertLess(DISCOVERY.source_path_score("node_modules/lx/source.js", 200), 0)
        self.assertEqual(DISCOVERY.source_path_score("src/index.js", 200), 0)
        self.assertGreater(DISCOVERY.source_path_score("sources/星海音乐源.js", 200), 0)
        self.assertGreater(
            DISCOVERY.source_path_score("V260716/推荐/星海音源.js", 200),
            DISCOVERY.source_path_score("V260620/其他/fish-music音源.js", 200),
        )

    def test_normalize_url_encodes_unicode_github_blob_path(self):
        url = DISCOVERY.normalize_url(
            "https://github.com/owner/repo/blob/main/sources/星海 音源.js"
        )

        self.assertEqual(
            "https://raw.githubusercontent.com/owner/repo/main/sources/"
            "%E6%98%9F%E6%B5%B7%20%E9%9F%B3%E6%BA%90.js",
            url,
        )


if __name__ == "__main__":
    unittest.main()
