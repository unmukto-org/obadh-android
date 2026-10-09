#!/usr/bin/env python3
"""Safety counterexamples for 16 KB RELRO page rounding; no SDK or emulator needed."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('alignment', Path(__file__).with_name('check-apk-native.py'))
alignment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(alignment)


def load(start, end):
    return (1, 6, 0, start, 0, end-start, end-start, 16384)


def relro(start, end):
    return (0x6474e552, 4, 0, start, 0, end-start, end-start, 1)


class RelroSafety(unittest.TestCase):
    def test_aligned_end_preserves_mutable_tail(self):
        alignment.check_relro([load(0x4100, 0x8100), relro(0x4100, 0x8000)], 'aligned')

    def test_androidx_isolated_mutable_page(self):
        alignment.check_relro([load(0x5b40, 0x6000), load(0x9ed8, 0x9ee8), relro(0x5b40, 0x6000)], 'isolated')

    def test_rounding_end_cannot_protect_mutable_tail(self):
        with self.assertRaises(AssertionError):
            alignment.check_relro([load(0x5b40, 0x7010), relro(0x5b40, 0x6000)], 'tail')

    def test_rounding_start_cannot_protect_mutable_prefix(self):
        with self.assertRaises(AssertionError):
            alignment.check_relro([load(0x5000, 0x6000), relro(0x5100, 0x6000)], 'prefix')


if __name__ == '__main__':
    unittest.main()
