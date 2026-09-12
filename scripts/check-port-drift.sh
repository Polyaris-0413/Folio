#!/usr/bin/env bash
# 防漂移闸门：校验「逐字节搬运」的 legado 文件在 Folio 侧未被改动。
#
# 为什么需要它：本次移植的核心主张是「能搬的代码直接搬、不按理解重写」。只要这些文件
# 与 legado 源文件逐字节一致（仅行尾差异），行为就不可能不同——这比任何测试都更强。
# 一旦有人顺手「改进」了搬过来的代码，本脚本会失败，提醒他把改动放回宿主替身层。
#
# 用法：bash scripts/check-port-drift.sh
#       LEGADO_ROOT=/path/to/legado bash scripts/check-port-drift.sh
set -uo pipefail

LEGADO="${LEGADO_ROOT:-C:/Users/Administrator/Downloads/legado-with-MD3-3.26.15/legado-with-MD3-3.26.15}"
FOLIO="$(cd "$(dirname "$0")/.." && pwd)"

# 逐字节搬运清单（A 类）：仓库根相对的路径，两侧同构，直接拼前缀比对
VERBATIM=(
  "app/src/main/java/io/legado/app/data/entities/TxtTocRule.kt"
  "app/src/main/java/io/legado/app/data/dao/TxtTocRuleDao.kt"
  "app/src/main/java/io/legado/app/data/repository/TxtTocRuleRepository.kt"
  "app/src/main/java/io/legado/app/model/localBook/TextFile.kt"
  "app/src/main/java/io/legado/app/utils/EncodingDetect.kt"
  "app/src/main/java/io/legado/app/utils/Utf8BomUtils.kt"
  "app/src/main/java/io/legado/app/utils/ByteArrayExtensions.kt"
  "app/src/main/java/io/legado/app/exception/NoStackTraceException.kt"
  "app/src/main/java/io/legado/app/exception/EmptyFileException.kt"
  "app/src/main/java/io/legado/app/lib/icu4j/CharsetDetector.java"
  "app/src/main/java/io/legado/app/lib/icu4j/CharsetMatch.java"
  "app/src/main/java/io/legado/app/lib/icu4j/CharsetRecognizer.java"
  "app/src/main/java/io/legado/app/lib/icu4j/CharsetRecog_2022.java"
  "app/src/main/java/io/legado/app/lib/icu4j/CharsetRecog_UTF8.java"
  "app/src/main/java/io/legado/app/lib/icu4j/CharsetRecog_Unicode.java"
  "app/src/main/java/io/legado/app/lib/icu4j/CharsetRecog_mbcs.java"
  "app/src/main/java/io/legado/app/lib/icu4j/CharsetRecog_sbcs.java"
  "app/src/main/assets/defaultData/txtTocRule.json"
)

drift=0
checked=0
skipped=0

echo "legado 源: $LEGADO"
echo "Folio   : $FOLIO"
echo

for rel in "${VERBATIM[@]}"; do
  src="$LEGADO/$rel"
  dst="$FOLIO/$rel"
  if [ ! -f "$src" ]; then
    echo "  跳过(源缺失) $rel"
    skipped=$((skipped + 1))
    continue
  fi
  if [ ! -f "$dst" ]; then
    echo "  缺失!        $rel"
    drift=$((drift + 1))
    continue
  fi
  # 仅忽略行尾差异：仓库受 core.autocrlf 影响检出为 CRLF，legado 源为 LF
  # （Folio 已有的 mobi 移植同样是「仅行尾不同」，故这是本仓库既定的搬运语义）
  if diff --strip-trailing-cr -q "$src" "$dst" > /dev/null; then
    printf '  ok           %s\n' "$(basename "$rel")"
    checked=$((checked + 1))
  else
    printf '  漂移!        %s\n' "$rel"
    diff --strip-trailing-cr "$src" "$dst" | head -40
    drift=$((drift + 1))
  fi
done

echo
echo "一致 $checked 个，跳过 $skipped 个，漂移 $drift 个"
if [ "$drift" -eq 0 ] && [ "$checked" -gt 0 ] && [ "$skipped" -eq 0 ]; then
  echo "✅ 无漂移：逐字节搬运文件与 legado 源一致（仅行尾差异）"
  exit 0
fi
if [ "$drift" -eq 0 ]; then
  echo "⚠️  无漂移，但有文件被跳过或缺失，请检查 LEGADO_ROOT 是否正确"
  exit 1
fi
echo "❌ 检出 $drift 处漂移。搬过来的代码不应被修改；宿主适配请写在替身层"
echo "   说明见 docs/superpowers/plans/2026-09-12-legado-toc-verbatim-port.md"
exit 1
