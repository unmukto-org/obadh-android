// SPDX-License-Identifier: GPL-3.0-only
//! Offline vocabulary derivation only. Runtime Android still uses the unchanged engine C ABI.
use fst::{Map, Streamer};
use obadh_engine::cabi;
use std::{collections::HashMap, env, fs};

fn transliterate(engine: *const obadh_engine::ObadhEngine, roman: &str) -> String {
    let mut output = vec![0u8; 512];
    let length = unsafe {
        cabi::obadh_transliterate(
            engine,
            roman.as_ptr(),
            roman.len(),
            output.as_mut_ptr(),
            output.len(),
        )
    };
    assert!(length < output.len());
    String::from_utf8(output[..length].to_vec()).unwrap()
}
fn inverse(word: &str, consonants: &HashMap<String, String>) -> Option<String> {
    let chars: Vec<char> = word.chars().collect();
    let mut roman = String::new();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        let vowel = match c {
            'অ' => Some("o"),
            'আ' => Some("a"),
            'ই' => Some("i"),
            'ঈ' => Some("I"),
            'উ' => Some("u"),
            'ঊ' => Some("U"),
            'এ' => Some("e"),
            'ঐ' => Some("OI"),
            'ও' => Some("O"),
            'ঔ' => Some("OU"),
            'ঋ' => Some("rri"),
            'া' => Some("a"),
            'ি' => Some("i"),
            'ী' => Some("I"),
            'ু' => Some("u"),
            'ূ' => Some("U"),
            'ে' => Some("e"),
            'ৈ' => Some("OI"),
            'ো' => Some("O"),
            'ৌ' => Some("OU"),
            'ৃ' => Some("rri"),
            'ং' => Some("ng"),
            'ঃ' => Some("H"),
            'ঁ' => Some("qq"),
            '্' => Some(""),
            _ => None,
        };
        if let Some(v) = vowel {
            roman.push_str(v);
            i += 1;
            continue;
        }
        let mut key = c.to_string();
        if i + 1 < chars.len() && chars[i + 1] == '়' {
            key.push('়');
            i += 1;
        }
        let token = consonants.get(&key)?;
        roman.push_str(token);
        // Visible kars attach directly. Explicit conjuncts omit the inherent vowel;
        // otherwise distinguish adjacent Bengali consonants from a conjunct.
        if i + 1 < chars.len()
            && !matches!(
                chars[i + 1],
                'া' | 'ি'
                    | 'ী'
                    | 'ু'
                    | 'ূ'
                    | 'ে'
                    | 'ৈ'
                    | 'ো'
                    | 'ৌ'
                    | 'ৃ'
                    | '্'
                    | 'ং'
                    | 'ঃ'
                    | 'ঁ'
            )
        {
            roman.push('o');
        }
        i += 1;
    }
    Some(roman)
}
fn main() {
    let args: Vec<String> = env::args().collect();
    assert_eq!(args.len(), 3, "gesture-lexicon <bn.fst> <output.tsv>");
    let engine = cabi::obadh_engine_new();
    let mut consonants = HashMap::new();
    // Preferred everyday key paths first; all spellings are verified by the engine.
    for token in
        "k kh g gh Ng c ch j jh NG T Th D Dh N t th d dh n p ph b bh m y r l sh Sh s h R Rh Y w tt"
            .split_whitespace()
    {
        let text = transliterate(engine, token);
        if text.chars().all(|c| matches!(c, '\u{0980}'..='\u{09ff}')) {
            consonants.entry(text).or_insert_with(|| token.to_string());
        }
    }
    let bytes = fs::read(&args[1]).unwrap();
    let map = Map::new(bytes).unwrap();
    let mut stream = map.stream();
    let mut words = Vec::new();
    while let Some((key, frequency)) = stream.next() {
        let word = std::str::from_utf8(key).unwrap();
        if word.chars().count() <= 24 && frequency >= 20 {
            words.push((frequency, word.to_owned()));
        }
    }
    words.sort_by(|a, b| b.cmp(a));
    let mut output = String::new();
    let mut accepted = 0;
    let mut spellings = 0;
    for (frequency, word) in words {
        let Some(roman) = inverse(&word, &consonants) else {
            continue;
        };
        if !(2..=28).contains(&roman.len()) || !roman.bytes().all(|b| b.is_ascii_alphabetic()) {
            continue;
        }
        // Never ship an inferred reverse spelling unless the public C ABI reproduces
        // the exact lexicon word. Case folds affect geometry, not the chosen Bangla.
        if transliterate(engine, &roman) != word {
            continue;
        }
        output.push_str(&format!(
            "{}\t{}\t{}\n",
            roman.to_ascii_lowercase(),
            word,
            frequency
        ));
        spellings += 1;
        // Common everyday aliases use the engine's own rules, never guessed output.
        // Keep at most three extra paths per word, and verify before case-folding.
        for (from, to) in [("bh", "v"), ("ph", "f"), ("y", "z")] {
            let alias = roman.replace(from, to);
            if alias != roman && transliterate(engine, &alias) == word {
                output.push_str(&format!(
                    "{}\t{}\t{}\n",
                    alias.to_ascii_lowercase(),
                    word,
                    frequency
                ));
                spellings += 1;
            }
        }
        accepted += 1;
        if accepted == 60_000 {
            break;
        }
    }
    unsafe { cabi::obadh_engine_free(engine) };
    fs::write(&args[2], output).unwrap();
    println!("Derived {spellings} C-ABI-verified spellings for {accepted} Bangla words.");
}
