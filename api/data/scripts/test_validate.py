"""Contract tests for migration validation, with disposable in-repo fixtures."""
import copy
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import validate


class ValidatorContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=validate.ROOT / "development", prefix="validator-fixture-")
        self.root = Path(self.temp.name)
        for directory in ("dist/single", "development/sources"):
            (self.root / directory).mkdir(parents=True)
        self.root_patch = patch.object(validate, "ROOT", self.root)
        self.root_patch.start()
        self.item = {
            "code": "E102", "names": "Tartrazine", "display_name": "E102 (Tartrazine)",
            "type": "color (Yellow)", "status": "e u", "icon": "mdi-alert-circle",
            "more_info": {"banned_in": "Approved in the EU.\nApproved in the US.",
                          "articles": {"A": "https://example.org/a", "B": "https://example.org/b"}},
        }
        self.source = {"code": "E102", "checked": "2026-10-10",
                       "fields": {key: ["https://example.org/a"] for key in
                                  ("code", "names", "display_name", "type", "status", "icon", "banned_in")},
                       "gaps": [], "changes": []}
        self.save()
        (self.root / "development/url-checks.json").write_text(json.dumps({
            "checked": "2026-10-10", "checks": {
                "https://example.org/a": {"loaded": True},
                "https://example.org/b": {"loaded": True},
            },
        }))
        (self.root / "development/ins-summary-original.json").write_text(json.dumps([{"code": "E102"}]))

    def tearDown(self):
        self.root_patch.stop()
        self.temp.cleanup()

    def save(self):
        (self.root / "dist/single/E102.json").write_text(json.dumps(self.item))
        (self.root / "dist/ins-full.json").write_text(json.dumps([self.item]))
        (self.root / "development/sources/E102.json").write_text(json.dumps(self.source))

    def errors(self):
        self.save()
        errors = []
        validate.validate_additive("E102", errors)
        return errors

    def test_valid_record_and_explicit_empty_status(self):
        self.assertEqual(self.errors(), [])
        self.item["status"] = ""
        del self.item["more_info"]["banned_in"]
        self.assertEqual(self.errors(), [])

    def test_every_required_key_is_required(self):
        original = copy.deepcopy(self.item)
        for key in validate.REQUIRED:
            with self.subTest(key=key):
                self.item = copy.deepcopy(original)
                del self.item[key]
                self.assertTrue(self.errors())

    def test_missing_prefix_and_wrong_code(self):
        for value in ("102", "E101", "E101a"):
            with self.subTest(value=value):
                self.item["code"] = value
                self.assertTrue(self.errors())

    def test_all_allowed_flags_and_invalid_flags(self):
        for value in ("e", "u", "e u", ""):
            self.item["status"] = value
            text = "\n".join(sentence for flag, sentence in
                             (("e", "Approved in the EU."), ("u", "Approved in the US."))
                             if flag in value.split())
            if text:
                self.item["more_info"]["banned_in"] = text
            else:
                self.item["more_info"].pop("banned_in", None)
            self.assertEqual(self.errors(), [])
        for value in ("u e", "e  u", "eu", "a", None, []):
            with self.subTest(value=value):
                self.item["status"] = value
                self.assertTrue(self.errors())

    def test_empty_strings_at_nested_locations(self):
        for field in ("origin", "uses", "status"):
            with self.subTest(field=field):
                self.item["more_info"][field] = ""
                self.assertTrue(self.errors())
                del self.item["more_info"][field]
        self.source["gaps"] = [""]
        self.assertTrue(self.errors())

    def test_article_count_and_missing_sources(self):
        del self.item["more_info"]["articles"]["B"]
        self.assertTrue(self.errors())
        (self.root / "development/sources/E102.json").unlink()
        errors = []
        validate.validate_additive("E102", errors)
        self.assertTrue(errors)

    def test_legal_line_format_order_and_flag_agreement(self):
        for text in (
            "Approved in the EU. Approved in the US.", "Approved by the FDA.",
            "Banned in: USA", "Banned in: USA.\nBanned in: Canada.\nApproved in the EU.\nApproved in the US.",
            "Approved in the US.\nApproved in the EU.",
            "Approved in the EU.\nApproved in the US.\nBanned in: Canada.",
            "Approved in the EU.", "Approval is limited.",
        ):
            with self.subTest(text=text):
                self.item["more_info"]["banned_in"] = text
                self.assertTrue(self.errors())
        self.item["more_info"]["banned_in"] = (
            "Banned in: Canada, United States.\nApproved in the EU.\nApproved in the US.\n"
            "Use conditions changed in 2026."
        )
        self.assertEqual(self.errors(), [])

    def test_malformed_json_duplicate_keys_and_nonstandard_constants(self):
        for text in ('{', '{"code":"E102","code":"E102"}', '{"value":NaN}'):
            with self.subTest(text=text):
                path = self.root / "dist/single/E102.json"
                path.write_text(text)
                errors = []
                self.assertIsNone(validate.read_json(path, errors))
                self.assertTrue(errors)

    def test_master_missing_file_fails_default_validation(self):
        (self.root / "development/ins-summary-original.json").write_text(json.dumps([{"code": "E103"}]))
        with patch("sys.argv", ["validate.py"]), contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(validate.main(), 1)

    def test_safety_claim_requires_field_provenance(self):
        self.item["more_info"]["side_effects"] = "Test claim without a source."
        self.assertTrue(self.errors())

    def test_aggregate_missing_duplicate_and_drift(self):
        path = self.root / "dist/ins-full.json"
        for records in ([], [self.item, self.item], [dict(self.item, names="Stale name")]):
            with self.subTest(records=records):
                path.write_text(json.dumps(records))
                errors = []
                validate.validate_aggregate({"E102"}, errors)
                self.assertTrue(errors)

    def test_valid_aggregate_and_batch_validation(self):
        errors = []
        validate.validate_aggregate({"E102"}, errors)
        self.assertEqual(errors, [])
        with patch("sys.argv", ["validate.py", "--codes", "E102"]), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(validate.main(), 0)

    def test_failed_and_missing_url_receipts_fail(self):
        ledger = self.root / "development/url-checks.json"
        for checks in ({}, {"https://example.org/a": {"loaded": False}}):
            with self.subTest(checks=checks):
                ledger.write_text(json.dumps({"checks": checks}))
                errors = []
                validate.validate_loaded_urls({"E102"}, errors)
                self.assertTrue(errors)

    def test_master_input_is_valid_name_provenance_only(self):
        for key in ("names", "type", "display_name"):
            del self.source["fields"][key]
        self.source["inputs"] = {key: "development/ins-summary-original.json"
                                 for key in ("names", "type", "display_name")}
        self.assertEqual(self.errors(), [])
        del self.source["fields"]["status"]
        self.source["inputs"]["status"] = "development/ins-summary-original.json"
        self.assertTrue(self.errors())


if __name__ == "__main__":
    unittest.main()
