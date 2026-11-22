#!/usr/bin/env bash
set -euo pipefail

# Reproducible fetcher for prebuilt Android libmpv + FFmpeg shared libraries and matching C headers.
# Source binaries: dev.jdtech.mpv:libmpv:1.1.0 on Maven Central (mpv v0.42.0, ffmpeg 9.0.2, minSdk 26)
# Source headers:  mpv-player/mpv tag v0.42.0

LIBMPV_AAR_VERSION="1.1.0"
LIBMPV_AAR_URL="https://repo1.maven.org/maven2/dev/jdtech/mpv/libmpv/${LIBMPV_AAR_VERSION}/libmpv-${LIBMPV_AAR_VERSION}.aar"
LIBMPV_AAR_SHA256="20fd972e70d8e73bf8900d911f3ae0eeae354606baeb0fe58caa1a6380e196aa"

MPV_TAG="v0.42.0"
MPV_HEADERS_BASE_URL="https://raw.githubusercontent.com/mpv-player/mpv/${MPV_TAG}/include/mpv"

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
REQUIRED_LIBS=(
  "libc++_shared.so"
  "libavutil.so"
  "libswresample.so"
  "libswscale.so"
  "libavcodec.so"
  "libavformat.so"
  "libavfilter.so"
  "libavdevice.so"
  "libmpv.so"
)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
JNI_LIBS_DIR="${REPO_ROOT}/engine/src/main/jniLibs"
INCLUDE_MPV_DIR="${REPO_ROOT}/engine/src/main/cpp/include/mpv"

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

sha256_verify() {
  local expected="$1"
  local file="$2"
  local actual
  if command -v shasum >/dev/null 2>&1; then
    actual="$(shasum -a 256 "${file}" | awk '{print $1}')"
  elif command -v sha256sum >/dev/null 2>&1; then
    actual="$(sha256sum "${file}" | awk '{print $1}')"
  else
    echo "ERROR: neither shasum nor sha256sum found" >&2
    exit 1
  fi
  if [[ "${actual}" != "${expected}" ]]; then
    echo "ERROR: SHA256 mismatch for ${file}" >&2
    echo "  expected: ${expected}" >&2
    echo "  actual:   ${actual}" >&2
    exit 1
  fi
}

header_expected_sha256() {
  case "$1" in
    client.h)    echo "3da9b8c61c3fb4e2d76bd248cebf72f3ee830331411b71a76adbd5e05660cc50" ;;
    render.h)    echo "f2550aa29b2e9b20988bd954b4383e9fa05fd9e40d1669fbc6edf2afac603f15" ;;
    render_gl.h) echo "48662c0ed9872a14dd9e1684105c97f69f94a0414709c254c5d372adc41d2e69" ;;
    stream_cb.h) echo "188e58b6d14383e15a5dffdc4ecebbdfbf2e412b9c099ff21b571f747a8ce32d" ;;
    *)           return 1 ;;
  esac
}

abi_file_pattern() {
  case "$1" in
    arm64-v8a)   echo "ELF 64-bit LSB shared object, ARM aarch64" ;;
    armeabi-v7a) echo "ELF 32-bit LSB shared object, ARM" ;;
    x86_64)      echo "ELF 64-bit LSB shared object, x86-64" ;;
    *)           return 1 ;;
  esac
}

is_android_system_lib() {
  case "$1" in
    libc.so|libm.so|libdl.so|libz.so|liblog.so|libandroid.so|libmediandk.so|libOpenSLES.so|libEGL.so|libGLESv1_CM.so|libGLESv2.so|libGLESv3.so|libvulkan.so|libnativewindow.so|libjnigraphics.so|libaaudio.so)
      return 0 ;;
    *)
      return 1 ;;
  esac
}

find_llvm_readelf() {
  local sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Library/Android/sdk}}"
  if [[ -d "${sdk_dir}/ndk" ]]; then
    local latest_ndk
    latest_ndk="$(ls -1d "${sdk_dir}/ndk/"* 2>/dev/null | sort -V | tail -n 1 || true)"
    if [[ -n "${latest_ndk}" ]]; then
      local candidate
      for candidate in "${latest_ndk}/toolchains/llvm/prebuilt/"*/bin/llvm-readelf; do
        if [[ -x "${candidate}" ]]; then
          echo "${candidate}"
          return 0
        fi
      done
    fi
  fi
  if command -v llvm-readelf >/dev/null 2>&1; then
    command -v llvm-readelf
    return 0
  fi
  return 1
}

echo "[1/4] Downloading dev.jdtech.mpv:libmpv:${LIBMPV_AAR_VERSION}..."
AAR_PATH="${TMP_DIR}/libmpv-${LIBMPV_AAR_VERSION}.aar"
curl -fsSL "${LIBMPV_AAR_URL}" -o "${AAR_PATH}"
sha256_verify "${LIBMPV_AAR_SHA256}" "${AAR_PATH}"

echo "[2/4] Extracting shared libraries for ABIs: ${ABIS[*]}..."
EXTRACT_DIR="${TMP_DIR}/aar"
unzip -q -o "${AAR_PATH}" "jni/*" -d "${EXTRACT_DIR}"

for abi in "${ABIS[@]}"; do
  dest_dir="${JNI_LIBS_DIR}/${abi}"
  rm -rf "${dest_dir}"
  mkdir -p "${dest_dir}"
  for lib in "${REQUIRED_LIBS[@]}"; do
    src_lib="${EXTRACT_DIR}/jni/${abi}/${lib}"
    if [[ ! -f "${src_lib}" ]]; then
      echo "ERROR: Missing ${lib} for ABI ${abi} in AAR" >&2
      exit 1
    fi
    cp "${src_lib}" "${dest_dir}/${lib}"
  done
done

echo "[3/4] Downloading mpv ${MPV_TAG} C headers..."
rm -rf "${INCLUDE_MPV_DIR}"
mkdir -p "${INCLUDE_MPV_DIR}"
for header in client.h render.h render_gl.h stream_cb.h; do
  dest_header="${INCLUDE_MPV_DIR}/${header}"
  curl -fsSL "${MPV_HEADERS_BASE_URL}/${header}" -o "${dest_header}"
  expected_hash="$(header_expected_sha256 "${header}")"
  sha256_verify "${expected_hash}" "${dest_header}"
done

echo "[4/4] Verifying extracted binaries and headers..."
if ! grep -q "mpv_client_api_version" "${INCLUDE_MPV_DIR}/client.h"; then
  echo "ERROR: ${INCLUDE_MPV_DIR}/client.h does not define mpv_client_api_version" >&2
  exit 1
fi

READELF_BIN="$(find_llvm_readelf || true)"
if [[ -z "${READELF_BIN}" ]]; then
  echo "WARNING: llvm-readelf not found in NDK; skipping DT_NEEDED check." >&2
else
  echo "Using llvm-readelf: ${READELF_BIN}"
fi

for abi in "${ABIS[@]}"; do
  pattern="$(abi_file_pattern "${abi}")"
  for lib in "${REQUIRED_LIBS[@]}"; do
    lib_path="${JNI_LIBS_DIR}/${abi}/${lib}"
    file_out="$(file "${lib_path}")"
    if [[ "${file_out}" != *"${pattern}"* ]]; then
      echo "ERROR: Architecture mismatch for ${lib_path}: ${file_out}" >&2
      exit 1
    fi
    if [[ -n "${READELF_BIN}" ]]; then
      while IFS= read -r needed; do
        if is_android_system_lib "${needed}"; then
          continue
        fi
        if [[ ! -f "${JNI_LIBS_DIR}/${abi}/${needed}" ]]; then
          echo "ERROR: Unresolved DT_NEEDED '${needed}' in ${lib_path}" >&2
          exit 1
        fi
      done < <("${READELF_BIN}" -d "${lib_path}" | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p')
    fi
  done
done

echo "Success: prebuilt libmpv (${LIBMPV_AAR_VERSION} / mpv ${MPV_TAG}) installed and verified."
