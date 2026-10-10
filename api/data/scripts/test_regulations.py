"""Check legal scope, effective dates and source requirements for publication."""
import copy
import datetime
import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location(
    "enrich_regulations", Path(__file__).with_name("enrich-regulations.py")
)
regulations = importlib.util.module_from_spec(spec)
spec.loader.exec_module(regulations)


class RegulatoryPublicationTests(unittest.TestCase):
    def setUp(self):
        self.checked = datetime.date(2026, 10, 10)
        self.link = "https://regulator.example/food-law"
        self.receipts = {self.link: {"loaded": True}}
        self.record = {
            "status": "e u", "country_bans": [], "restrictions": [],
            "sources": [self.link],
            "notes": ["The permitted food categories were checked in 2026."],
        }

    def render(self):
        return regulations.regulatory_text(self.record, self.checked, self.receipts)[0]

    def test_school_and_food_specific_rules_preserve_approvals(self):
        for jurisdiction, kind, detail in (
            ("West Virginia", "school-use prohibition", "School meals exclude the colour from August 2025."),
            ("European Union", "food-specific prohibition", "Use in jelly mini-cups is prohibited in the rules checked in 2026."),
        ):
            with self.subTest(jurisdiction=jurisdiction):
                self.record["restrictions"] = [{"jurisdiction": jurisdiction, "kind": kind,
                                                "detail": detail, "sources": [self.link]}]
                self.record["notes"] = [detail]
                text = self.render()
                self.assertNotIn("Banned in:", text)
                self.assertIn("Approved in the EU.\nApproved in the US.", text)
                self.assertIn(detail, text)

    def test_future_state_law_does_not_become_a_country_ban(self):
        note = "California's prohibition has a future effective date of 1 January 2027."
        self.record["restrictions"] = [{"jurisdiction": "California", "kind": "future prohibition",
                                        "effective": "2027-01-01", "detail": note, "sources": [self.link]}]
        self.record["notes"] = [note]
        text = self.render()
        self.assertNotIn("Banned in:", text)
        self.assertIn("Approved in the US.", text)
        self.assertIn("2027", text)

    def test_country_ban_cannot_precede_its_effective_date(self):
        self.record["country_bans"] = [{"country": "Canada", "scope": "Food-additive use",
                                        "effective": "2027-01-01", "sources": [self.link]}]
        with self.assertRaisesRegex(ValueError, "future prohibition"):
            self.render()

    def test_expired_future_and_enjoined_provisions_require_reassessment(self):
        for kind in ("future prohibition", "enjoined future provision"):
            with self.subTest(kind=kind):
                self.record["restrictions"] = [{"jurisdiction": "West Virginia", "kind": kind,
                                                "effective": "2025-01-01", "detail": "A dated restriction.",
                                                "sources": [self.link]}]
                with self.assertRaisesRegex(ValueError, "new regulatory assessment"):
                    self.render()

    def test_us_country_ban_cannot_coexist_with_us_approval(self):
        self.record["country_bans"] = [{"country": "United States", "scope": "Food-additive use",
                                        "sources": [self.link]}]
        with self.assertRaisesRegex(ValueError, "contradicts"):
            self.render()
        self.record["status"] = "e"
        self.assertTrue(self.render().startswith("Banned in: United States.\nApproved in the EU."))

    def test_every_explicit_ban_and_restriction_needs_readable_evidence(self):
        unseen = "https://regulator.example/unreadable"
        for key, item in (
            ("country_bans", {"country": "Canada", "scope": "Food-additive use", "sources": [unseen]}),
            ("restrictions", {"jurisdiction": "Canada", "kind": "food-specific prohibition",
                              "detail": "A specific food restriction.", "sources": [unseen]}),
        ):
            original = copy.deepcopy(self.record)
            with self.subTest(key=key):
                self.record[key] = [item]
                with self.assertRaisesRegex(ValueError, "unreadable or unchecked"):
                    self.render()
            self.record = original


if __name__ == "__main__":
    unittest.main()
