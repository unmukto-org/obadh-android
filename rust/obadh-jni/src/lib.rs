//! JNI shim over the Obadh engine's C ABI (v2), the Android counterpart of
//! obadh-ios's `ObadhBridge`.
//!
//! Same contract as iOS: UTF-8 buffers and packed little-endian records cross
//! the boundary, nothing else. Handles are opaque `jlong`s. Packed lists are
//! returned as raw `byte[]` and parsed in Kotlin (`ObadhBridgeClient`), exactly
//! as the Swift client parses them. No thread safety here: the Kotlin client
//! holds one lock per handle.

use std::ptr;

use jni::objects::{JByteArray, JClass};
use jni::sys::{jbyteArray, jint, jlong};
use jni::JNIEnv;
use obadh_engine::cabi;

const SCRATCH: usize = 1024;

/// snprintf-style writers return the bytes they need and copy only when the
/// buffer fits: one crossing in the common case, one retry on overflow.
fn read_bytes(write: impl Fn(*mut u8, usize) -> usize) -> Vec<u8> {
    let mut scratch = [0u8; SCRATCH];
    let required = write(scratch.as_mut_ptr(), SCRATCH);
    if required == 0 {
        return Vec::new();
    }
    if required <= SCRATCH {
        return scratch[..required].to_vec();
    }
    let mut large = vec![0u8; required];
    if write(large.as_mut_ptr(), required) != required {
        return Vec::new();
    }
    large
}

fn input(env: &JNIEnv, arr: &JByteArray) -> Vec<u8> {
    env.convert_byte_array(arr).unwrap_or_default()
}

fn out(env: &JNIEnv, bytes: Vec<u8>) -> jbyteArray {
    env.byte_array_from_slice(&bytes)
        .map(|a| a.into_raw())
        .unwrap_or(ptr::null_mut())
}

// ---------------------------------------------------------------- version

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_abiVersion(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    cabi::obadh_abi_version() as jint
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_engineVersion(
    env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
    let bytes = read_bytes(|o, c| unsafe { cabi::obadh_engine_version(o, c) });
    out(&env, bytes)
}

// ----------------------------------------------------------- deterministic

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_engineNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    cabi::obadh_engine_new() as jlong
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_engineFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { cabi::obadh_engine_free(handle as *mut _) }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_transliterate(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    roman: JByteArray,
) -> jbyteArray {
    let roman = input(&env, &roman);
    let bytes = read_bytes(|o, c| unsafe {
        cabi::obadh_transliterate(handle as *const _, roman.as_ptr(), roman.len(), o, c)
    });
    out(&env, bytes)
}

// -------------------------------------------------------------- autocorrect

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autocorrectOpen(
    env: JNIEnv,
    _class: JClass,
    fst_path: JByteArray,
    loanword_path: JByteArray,
) -> jlong {
    let fst = input(&env, &fst_path);
    let loan = input(&env, &loanword_path);
    unsafe {
        cabi::obadh_autocorrect_open(fst.as_ptr(), fst.len(), loan.as_ptr(), loan.len()) as jlong
    }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autocorrectFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { cabi::obadh_autocorrect_free(handle as *mut _) }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autocorrectFingerprint(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { cabi::obadh_autocorrect_fingerprint(handle as *const _) as jlong }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autocorrectWordFrequency(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    word: JByteArray,
) -> jlong {
    let word = input(&env, &word);
    unsafe {
        cabi::obadh_autocorrect_word_frequency(handle as *const _, word.as_ptr(), word.len())
            as jlong
    }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autocorrectSuggestDetailed(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    roman: JByteArray,
    limit: jint,
) -> jbyteArray {
    let roman = input(&env, &roman);
    let bytes = read_bytes(|o, c| unsafe {
        cabi::obadh_autocorrect_suggest_detailed(
            handle as *const _,
            roman.as_ptr(),
            roman.len(),
            limit.max(0) as usize,
            o,
            c,
        )
    });
    out(&env, bytes)
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_composeSuggestions(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    roman: JByteArray,
    limit: jint,
) -> jbyteArray {
    let roman = input(&env, &roman);
    let bytes = read_bytes(|o, c| unsafe {
        cabi::obadh_compose_suggestions(
            handle as *const _,
            roman.as_ptr(),
            roman.len(),
            limit.max(0) as usize,
            o,
            c,
        )
    });
    out(&env, bytes)
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autocorrectWordAlternatives(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    word: JByteArray,
    limit: jint,
) -> jbyteArray {
    let word = input(&env, &word);
    let bytes = read_bytes(|o, c| unsafe {
        cabi::obadh_autocorrect_word_alternatives(
            handle as *const _,
            word.as_ptr(),
            word.len(),
            limit.max(0) as usize,
            o,
            c,
        )
    });
    out(&env, bytes)
}

// -------------------------------------------------------------- autosuggest

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestOpen(
    env: JNIEnv,
    _class: JClass,
    path: JByteArray,
) -> jlong {
    let path = input(&env, &path);
    unsafe { cabi::obadh_autosuggest_open(path.as_ptr(), path.len()) as jlong }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { cabi::obadh_autosuggest_free(handle as *mut _) }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestFingerprint(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { cabi::obadh_autosuggest_fingerprint(handle as *const _) as jlong }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestCommit(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    token: JByteArray,
) -> jint {
    let token = input(&env, &token);
    unsafe { cabi::obadh_autosuggest_commit(handle as *mut _, token.as_ptr(), token.len()) as jint }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestSuggest(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    limit: jint,
) -> jbyteArray {
    let bytes = read_bytes(|o, c| unsafe {
        cabi::obadh_autosuggest_suggest(handle as *mut _, limit.max(0) as usize, o, c)
    });
    out(&env, bytes)
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestSuggestForContext(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    context: JByteArray,
    limit: jint,
) -> jbyteArray {
    let context = input(&env, &context);
    let bytes = read_bytes(|o, c| unsafe {
        cabi::obadh_autosuggest_suggest_for_context(
            handle as *const _,
            context.as_ptr(),
            context.len(),
            limit.max(0) as usize,
            o,
            c,
        )
    });
    out(&env, bytes)
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestClearSession(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { cabi::obadh_autosuggest_clear_session(handle as *mut _) }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestClearPersonal(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { cabi::obadh_autosuggest_clear_personal(handle as *mut _) }
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestExportPersonal(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jbyteArray {
    let bytes = read_bytes(|o, c| unsafe {
        cabi::obadh_autosuggest_export_personal(handle as *const _, o, c)
    });
    out(&env, bytes)
}

#[no_mangle]
pub extern "system" fn Java_org_unmukto_obadh_engine_ObadhNative_autosuggestImportPersonal(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    snapshot: JByteArray,
) -> jint {
    let snapshot = input(&env, &snapshot);
    unsafe {
        cabi::obadh_autosuggest_import_personal(handle as *mut _, snapshot.as_ptr(), snapshot.len())
            as jint
    }
}
